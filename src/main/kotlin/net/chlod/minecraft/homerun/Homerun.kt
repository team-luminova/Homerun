package net.chlod.minecraft.homerun

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.chlod.minecraft.homerun.command.ResetCommand
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.data.ResetData
import net.chlod.minecraft.homerun.listeners.PlayerJoinListener
import net.chlod.minecraft.homerun.tasks.WorldPostloadTask
import net.chlod.minecraft.homerun.tasks.WorldPostpareTask
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.WorldCreator
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class Homerun : JavaPlugin() {

    val nskNeedsRespawn = NamespacedKey(this, "needs_respawn")

    var appliedResetData: ResetData? = null
    var lockedDown = false

    override fun onLoad() {
        ConfigurationSerialization.registerClass(ResetRule::class.java)

        // Load logic
        val resetData = ResetData.find(this)
        if (resetData != null) {
            componentLogger.info("Found existing reset data for world ${resetData.sourceWorld} to ${resetData.targetWorld}")
            // Directly run the task because we want this to be a blocking operation.
            // Otherwise, the server will load the world before we're done processing.
            WorldPostpareTask(this, resetData)
                .run()
            appliedResetData = resetData
            resetData.delete()
        } else {
            componentLogger.info("No existing reset data found")
        }
    }

    override fun onEnable() {
        saveDefaultConfig()

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
        server.pluginManager.registerEvents(PlayerJoinListener(this), this)

        if (appliedResetData != null) {
            WorldPostloadTask(this, appliedResetData!!)
                .run()
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
