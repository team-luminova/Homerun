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
import net.chlod.minecraft.homerun.listeners.PlayerLockoutListener
import net.chlod.minecraft.homerun.listeners.PlayerUpgradeListener
import net.chlod.minecraft.homerun.tasks.ResetLoadTask
import net.chlod.minecraft.homerun.tasks.WorldPostloadTask
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Homerun : JavaPlugin() {

    val keys = HomerunNamespacedKeys(this)

    var appliedResetLocks = mutableListOf<ResetLock>()

    override fun onLoad() {
        ConfigurationSerialization.registerClass(ResetRule::class.java)

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
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
