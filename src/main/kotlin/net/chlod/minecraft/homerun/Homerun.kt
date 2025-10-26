package net.chlod.minecraft.homerun

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.chlod.minecraft.homerun.command.ResetCommand
import net.chlod.minecraft.homerun.command.TpworldCommand
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.data.HomerunNamespacedKeys
import net.chlod.minecraft.homerun.data.PlayerLockout
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.world.WorldCopyLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldRenameLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.chlod.minecraft.homerun.listeners.PlayerLockoutListener
import net.chlod.minecraft.homerun.listeners.PlayerUpgradeListener
import net.chlod.minecraft.homerun.tasks.ResetLoadTask
import net.chlod.minecraft.homerun.tasks.ResetPrepareTask
import net.chlod.minecraft.homerun.tasks.WorldPostloadTask
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Homerun : JavaPlugin() {

    val keys = HomerunNamespacedKeys(this)

    val resetRules = mutableListOf<ResetRule>()
    private var appliedResetLocks = mutableListOf<ResetLock>()
    private var conditionCheckTask: Int? = null

    override fun onLoad() {
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
            it.delete()
        }
    }

    override fun onEnable() {
        // Saving default configuration
        saveDefaultConfig()

        // Registering event listeners
        server.pluginManager.registerEvents(PlayerUpgradeListener(this), this)
        server.pluginManager.registerEvents(PlayerLockoutListener(this), this)

        // Registering commands
        @Suppress("UnstableApiUsage")
        this.lifecycleManager.registerEventHandler(
            LifecycleEvents.COMMANDS,
            LifecycleEventHandler { commands: ReloadableRegistrarEvent<Commands> ->
                commands.registrar().register(
                    TpworldCommand.createCommand("tpworld"),
                    "Teleports the player to a world"
                )
                commands.registrar().register(
                    ResetCommand.createCommand(this, "reset"),
                    "Forces a reset with the specified rule"
                )
            })


        // Process any applied reset locks
        for (appliedResetLock in appliedResetLocks) {
            WorldPostloadTask(this, appliedResetLock).run()
        }
        // Unlock player joins
        PlayerLockout.global.unlock()

        // Start processing new reset rules
        conditionCheckTask = server.scheduler
            .scheduleSyncRepeatingTask(this, {
                resetRules.forEachIndexed { index, resetRule ->
                    if (resetRule.enabled != true)
                        return@forEachIndexed

                    for (condition in resetRule.conditions) {
                        if (condition.isSatisfied(this)) {
                            componentLogger.warn(
                                "Reset condition satisfied for rule ${resetRule.name ?: index}, executing reset."
                            )
                            ResetPrepareTask(this, resetRule)
                                .runTaskTimer(this, 0L, 20L)
                            break
                        } else {
                            val timeUntilResetMillis = condition.getTimeUntilNextReset(this) ?: continue
                            val world = if (resetRule.parameters.world == null)
                                server.worlds.firstOrNull() ?: continue
                            else
                                (server.getWorld(resetRule.parameters.world) ?: continue)

                            resetRule.warnings?.forEach {
                                it.displayWarningMessage(
                                    this,
                                    world,
                                    resetRule,
                                    condition,
                                    timeUntilResetMillis
                                )
                            }
                        }
                    }
                }
            }, 0, 1L)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        if (conditionCheckTask == null || conditionCheckTask == -1) {
            return
        }
        server.scheduler.cancelTask(conditionCheckTask!!)
    }

    private fun loadResetRules() {
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
