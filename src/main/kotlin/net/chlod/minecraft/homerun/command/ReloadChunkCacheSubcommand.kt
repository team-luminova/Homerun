package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.Homerun
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault

class ReloadChunkCacheSubcommand(private val plugin: Homerun) : IHomerunSubcommand {

    override val name: String
        get() = "reloadchunkcache"
    override val description: String
        get() = "Reload the retained chunk cache used for player notifications without restarting the server."
    override val permission: Permission
        get() = Permission("homerun.commands.reloadchunkcache", description, PermissionDefault.OP)
    override val commandNode: LiteralCommandNode<CommandSourceStack>
        get() = Commands.literal(name)
            .requires { it.sender.hasPermission(permission) }
            .executes { ctx ->
                plugin.retainedChunkCache.flushCaches(true)
                ctx.source.sender.sendMessage("Reloaded retained chunk cache.")
                Command.SINGLE_SUCCESS
            }
            .build()

}