package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player


@Suppress("UnstableApiUsage")
class TpworldCommand {

    companion object {
        fun createCommand(commandName: String): LiteralCommandNode<CommandSourceStack> {
            return Commands.literal(commandName)
                .then(
                    Commands.argument("world", ArgumentTypes.world())
                        .executes { ctx ->
                            val world = ctx.getArgument("world", World::class.java)
                            val sender: Entity? = ctx.getSource().executor

                            if (sender is Player) {
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