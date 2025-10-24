package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Location
import org.bukkit.WorldCreator
import org.bukkit.entity.Entity
import org.bukkit.entity.Player


@Suppress("UnstableApiUsage")
class TpworldCommand {

    companion object {
        fun createCommand(commandName: String): LiteralCommandNode<CommandSourceStack> {
            return Commands.literal(commandName)
                .then(
                    Commands.argument("world", StringArgumentType.string())
                        .executes { ctx ->
                            val worldName = ctx.getArgument("world", String::class.java)
                            val sender: Entity? = ctx.getSource().executor

                            val world = ctx.source.sender.server.createWorld(WorldCreator(worldName))
                            if (world == null) {
                                ctx.source.sender.sendMessage("World '$worldName' does not exist.")
                                return@executes 0
                            }

                            if (sender is Player) {
                                ctx.source.sender.sendMessage("Teleporting to '$worldName'...")
                                sender.teleport(
                                    Location(
                                        world,
                                        sender.location.x, sender.location.y, sender.location.z,
                                        sender.location.yaw, sender.location.pitch
                                    )
                                )
                            }

                            Command.SINGLE_SUCCESS
                        }
                )
                .build()
        }
    }

}