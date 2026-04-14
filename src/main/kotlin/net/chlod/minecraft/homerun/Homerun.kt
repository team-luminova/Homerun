package net.chlod.minecraft.homerun

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.chlod.minecraft.homerun.command.HomerunCommand
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import net.chlod.minecraft.homerun.data.*
import net.chlod.minecraft.homerun.data.world.WorldCopyLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldRenameLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.chlod.minecraft.homerun.extern.HomerunPlaceholderExpansion
import net.chlod.minecraft.homerun.helpers.MinecraftVersion
import net.chlod.minecraft.homerun.helpers.RetainedChunkCache
import net.chlod.minecraft.homerun.listeners.*
import net.chlod.minecraft.homerun.tasks.*
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

class Homerun : JavaPlugin() {

    // Constants
    val keys = HomerunNamespacedKeys(this)
    val messages = Messages(this)
    val extraData = ExtraData(this)
    val mainCommand = HomerunCommand(this)

    // Refreshable state
    val resetRules = mutableListOf<ResetRule>()
    val retainedChunkCache = RetainedChunkCache(this, resetRules)
    val timeUntilNextResetCache = mutableMapOf<ResetRule, Long>()

    // One-time use state
    private var appliedResetLocks = mutableListOf<ResetLock>()

    // Timers
    private var conditionCheckTask: BukkitTask? = null
    private var borderCheckTask: BukkitTask? = null

    // Config variables
    var tickPeriod: Long = 1L
        private set

    override fun onLoad() {
        // Version check
        val runtime = MinecraftVersion.detectRuntimeOrNull()
        val highestSupported = MinecraftVersion.parseOrNull(extraData.minecraftVersionMax)

        if (runtime != null && highestSupported != null && runtime > highestSupported) {
            val warning =
                "This server is running Minecraft $runtime, but this build of Homerun was only tested up to $highestSupported."
            logger.warning("=".repeat(warning.length))
            logger.warning(warning)
            logger.warning("Things may break, especially when data moves around.")
            logger.warning("Please update Homerun if a newer version is available.")
            logger.warning("=".repeat(warning.length))
        }

        ConfigurationSerialization.registerClass(ResetRule::class.java)
        ConfigurationSerialization.registerClass(WorldResetLoadInstruction::class.java)
        ConfigurationSerialization.registerClass(WorldResetLoadInstruction.SubDimensionInfo::class.java)
        ConfigurationSerialization.registerClass(WorldCopyLoadInstruction::class.java)
        ConfigurationSerialization.registerClass(WorldRenameLoadInstruction::class.java)

        initFromConfig()
        loadResetRules()

        // Load in the configuration
        ResetLock.findAll(this).forEach {
            componentLogger.info("Found existing reset lock: ${it.id} (${Date(it.time)})")

            // Lock player joins
            PlayerLockout.global.lock()

            // Directly run the task because we want this to be a blocking operation.
            // Otherwise, the server will load the world before we're done processing.
            ResetLoadTask(this, it).run()
            appliedResetLocks.add(it)
            it.delete()
        }
    }

    override fun onEnable() {
        // Load in target worlds, so that we can perform postload tasks on them.
        if (Bukkit.isTickingWorlds()) {
            componentLogger.error("Server is already ticking worlds, bad things are about to happen!")
        }
        componentLogger.info("Loading target worlds for any applied reset locks...")
        val wasUnloaded = mutableListOf<String>()
        appliedResetLocks.forEach { lock ->
            lock.resetInstructions.forEach {
                if (it is WorldResetLoadInstruction) {
                    if (server.getWorld(it.targetWorld) == null) {
                        componentLogger.info("Loading target world ${it.targetWorld}...")
                        server.createWorld(WorldCreator(it.targetWorld))
                        wasUnloaded.add(it.targetWorld)
                    }
                }
            }
        }

        // Saving default configuration
        saveDefaultConfig()

        // Registering event listeners
        server.pluginManager.registerEvents(EndSpikeCleanupListener(this), this)
        server.pluginManager.registerEvents(PlayerUpgradeListener(this), this)
        server.pluginManager.registerEvents(PlayerLockoutListener(this), this)
        server.pluginManager.registerEvents(PlayerNotifyListener(this), this)
        server.pluginManager.registerEvents(PlayerBorderListener(this), this)
        server.pluginManager.registerEvents(RetainedChunkCacheListener(this), this)
        server.pluginManager.registerEvents(DimensionSpawnFixListener(this), this)
        server.pluginManager.registerEvents(ConsumableBorderConsumeListener(this), this)

        // Registering commands
        this.lifecycleManager.registerEventHandler(
            LifecycleEvents.COMMANDS,
            LifecycleEventHandler { commands: ReloadableRegistrarEvent<Commands> ->
                val registrar = commands.registrar()
                @Suppress("UnstableApiUsage")
                registrar.register(
                    mainCommand.createCommand(registrar.dispatcher),
                    mainCommand.description
                )
            })

        // Process any applied reset locks
        for (appliedResetLock in appliedResetLocks) {
            WorldPostloadTask(this, appliedResetLock).run()
        }

        // Re-unload those worlds.
        wasUnloaded.forEach {
            componentLogger.info("Unloading world $it...")
            server.unloadWorld(it, false)
        }

        // Caching retained chunks for player notifications
        retainedChunkCache.flushCaches(true)
        RetainedChunkCacheRefreshTask(retainedChunkCache)
            .runTaskTimer(this, 0, 20 * 60 * 5L) // Refresh every 5 minutes

        // Unlock player joins
        PlayerLockout.global.unlock()

        // Start processing new reset rules
        startTimers()

        // PlaceholderAPI expansion registration
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            logger.info("PlaceholderAPI detected, registering Homerun placeholders...")
            HomerunPlaceholderExpansion(this).register()
        } else {
            logger.info("PlaceholderAPI not detected.")
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
        for (task in arrayOf(conditionCheckTask, borderCheckTask)) {
            if (task != null && !task.isCancelled) {
                task.cancel()
            }
        }

        for (resetRule in resetRules) {
            if (!(resetRule.enabled ?: true)) {
                continue
            }

            for (border in (resetRule.borders ?: emptyList())) {
                if (border is ConsumableBorderType) {
                    border.disposeAllBossBars()
                }
            }
        }
    }

    @Suppress("UnstableApiUsage")
    fun reload() {
        reloadConfig()
        initFromConfig()
        startTimers()
        messages.reload()
        loadResetRules()
        retainedChunkCache.flushCaches(true)

        // Run border updates immediately
        for (resetRule in resetRules) {
            if (!(resetRule.enabled ?: true)) {
                continue
            }

            server.onlinePlayers.forEach { player ->
                resetRule.borders?.forEach { border ->
                    border.doBorderUpdate(
                        this,
                        resetRule,
                        PlayerMoveEvent(
                            player,
                            player.location.clone(),
                            player.location.clone()
                        ),
                        player.location.clone(),
                        player.location.clone()
                    )
                }
            }
        }
    }

    fun initFromConfig() {
        tickPeriod = config.getLong("tick_period")
    }

    /**
     * Starts (or restarts) timers.
     */
    fun startTimers(period: Long = tickPeriod) {
        if (conditionCheckTask != null && !conditionCheckTask!!.isCancelled) {
            conditionCheckTask!!.cancel()
        }
        conditionCheckTask = ConditionCheckTask(this).runTaskTimer(this, 0, period)
        if (borderCheckTask != null && !borderCheckTask!!.isCancelled) {
            borderCheckTask!!.cancel()
        }
        borderCheckTask = BorderCheckTask(this).runTaskTimer(this, 0, period)
    }

    fun loadResetRules() {
        resetRules.clear()

        val resetRulesRaw = config.getList("reset_rules") ?: return
        var hasConsumableBorder = false
        resetRulesRaw.forEachIndexed { index, maybeRule ->
            @Suppress("UNCHECKED_CAST")
            val rule = maybeRule as? ResetRule
                ?: try {
                    if (maybeRule !is Map<*, *>) {
                        throw IllegalArgumentException("Invalid reset rule format (not a Map): #$index")
                    }
                    if (maybeRule.keys.any { key -> key !is String }) {
                        throw IllegalArgumentException("Invalid reset rule format (non-string key): #$index")
                    }
                    ResetRule.deserialize(maybeRule as Map<String, Any>)
                } catch (ex: Exception) {
                    componentLogger.error("Failed to deserialize reset rule from config", ex)
                    null
                }

            if (rule != null) {
                resetRules.add(rule)

                if (rule.borders?.any { it is ConsumableBorderType } ?: false) {
                    if (hasConsumableBorder) {
                        throw IllegalArgumentException("Multiple consumable borders are not allowed")
                    } else {
                        hasConsumableBorder = true
                    }
                }
            }
        }
    }

}
