package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.Homerun


@Suppress("UnstableApiUsage")
class ReloadChunkCacheCommand {

    companion object {
        fun createCommand(plugin: Homerun, commandName: String): LiteralCommandNode<CommandSourceStack> {
            return Commands.literal(commandName)
                .requires { it.sender.hasPermission("homerun.commands.reloadchunkcache") }
                .executes { ctx ->
                    plugin.retainedChunkCache.flushCaches()
                    ctx.source.sender.sendMessage("Reloaded retained chunk cache.")
                    Command.SINGLE_SUCCESS
                }
                .build()
        }
    }

}