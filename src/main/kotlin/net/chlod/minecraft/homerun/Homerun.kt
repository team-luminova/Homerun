package net.chlod.minecraft.homerun

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.chlod.minecraft.homerun.command.ResetCommand
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.data.HomerunNamespacedKeys
import net.chlod.minecraft.homerun.data.PlayerLockout
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.listeners.PlayerJoinListener
import net.chlod.minecraft.homerun.tasks.ResetLoadTask
import net.chlod.minecraft.homerun.tasks.WorldPostloadTask
import org.bukkit.Location
import org.bukkit.WorldCreator
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.entity.Player
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
        server.pluginManager.registerEvents(PlayerJoinListener(this), this)

        // Registering commands
        registerCommand("reset", ResetCommand(this))
        registerCommand("tpworld", object : BasicCommand {
            override fun execute(
                commandSourceStack: CommandSourceStack,
                args: Array<out String>
            ) {
                val world = server.createWorld(WorldCreator(args[0]))
                if (world != null && commandSourceStack.sender is Player) {
                    val player = commandSourceStack.sender as Player
                    player.teleport(
                        Location(
                            world,
                            player.location.x, player.location.y, player.location.z,
                            player.location.yaw, player.location.pitch
                        )
                    )
                    commandSourceStack.sender.sendMessage("Teleported to world ${world.name}")
                } else {
                    commandSourceStack.sender.sendMessage("World ${args[0]} not found")
                }
            }
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
