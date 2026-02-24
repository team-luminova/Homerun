package net.chlod.minecraft.homerun

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.chlod.minecraft.homerun.command.HomerunCommand
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.data.*
import net.chlod.minecraft.homerun.data.world.WorldCopyLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldRenameLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.chlod.minecraft.homerun.helpers.MinecraftVersion
import net.chlod.minecraft.homerun.helpers.RetainedChunkCache
import net.chlod.minecraft.homerun.listeners.*
import net.chlod.minecraft.homerun.tasks.*
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

class Homerun : JavaPlugin() {

    val keys = HomerunNamespacedKeys(this)
    val messages = Messages(this)
    val extraData = ExtraData(this)

    val resetRules = mutableListOf<ResetRule>()
    val retainedChunkCache = RetainedChunkCache(this, resetRules)
    private var appliedResetLocks = mutableListOf<ResetLock>()
    private var conditionCheckTask: BukkitTask? = null
    private var borderCheckTask: BukkitTask? = null

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
        ConfigurationSerialization.registerClass(WorldCopyLoadInstruction::class.java)
        ConfigurationSerialization.registerClass(WorldRenameLoadInstruction::class.java)

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
        // Saving default configuration
        saveDefaultConfig()

        // Registering event listeners
        server.pluginManager.registerEvents(EndPillarCleanupListener(this), this)
        server.pluginManager.registerEvents(PlayerUpgradeListener(this), this)
        server.pluginManager.registerEvents(PlayerLockoutListener(this), this)
        server.pluginManager.registerEvents(PlayerNotifyListener(this), this)
        server.pluginManager.registerEvents(PlayerBorderListener(this), this)
        server.pluginManager.registerEvents(RetainedChunkCacheListener(this), this)
        server.pluginManager.registerEvents(DimensionSpawnFixListener(this), this)

        // Registering commands
        @Suppress("UnstableApiUsage")
        this.lifecycleManager.registerEventHandler(
            LifecycleEvents.COMMANDS,
            LifecycleEventHandler { commands: ReloadableRegistrarEvent<Commands> ->
                commands.registrar().register(
                    HomerunCommand.createCommand(this, "homerun"),
                    "Manage Homerun configuration, worlds, and more."
                )
            })


        // Process any applied reset locks
        for (appliedResetLock in appliedResetLocks) {
            WorldPostloadTask(this, appliedResetLock).run()
        }

        // Caching retained chunks for player notifications
        retainedChunkCache.flushCaches(true)
        RetainedChunkCacheRefreshTask(retainedChunkCache)
            .runTaskTimer(this, 0, 20 * 60 * 5L) // Refresh every 5 minutes

        // Unlock player joins
        PlayerLockout.global.unlock()

        // Start processing new reset rules
        conditionCheckTask = ConditionCheckTask(this).runTaskTimer(this, 0, 1L)
        borderCheckTask = BorderCheckTask(this).runTaskTimer(this, 0, 4L)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        for (task in arrayOf(conditionCheckTask, borderCheckTask)) {
            if (task != null && !task.isCancelled) {
                task.cancel()
            }
        }
    }

    fun reload() {
        reloadConfig()
        messages.reload()
        loadResetRules()
        retainedChunkCache.flushCaches(true)
    }

    fun loadResetRules() {
        resetRules.clear()

        val resetRulesRaw = config.getList("reset_rules") ?: return
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
            }
        }
    }

}
