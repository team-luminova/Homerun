package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.Homerun
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault

class ReloadConfigSubcommand(private val plugin: Homerun) : IHomerunSubcommand {

    override val name: String
        get() = "reload"
    override val description: String
        get() = "Reload the configuration for Homerun without restarting the server."
    override val permission: Permission
        get() = Permission("homerun.commands.reload", description, PermissionDefault.OP)
    override val commandNode: LiteralCommandNode<CommandSourceStack>
        get() = Commands.literal(name)
            .requires { it.sender.hasPermission(permission) }
            .executes { ctx ->
                plugin.reload()
                ctx.source.sender.sendMessage("Homerun configuration reloaded successfully.")

                Command.SINGLE_SUCCESS
            }
            .build()

}