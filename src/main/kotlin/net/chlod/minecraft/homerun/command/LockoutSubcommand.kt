package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.data.PlayerLockout
import org.bukkit.WorldCreator
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import java.util.concurrent.CompletableFuture

class LockoutSubcommand : IHomerunSubcommand {

    companion object {
        fun suggestWorldNames(
            context: CommandContext<CommandSourceStack>,
            builder: SuggestionsBuilder
        ): CompletableFuture<Suggestions> {
            for (world in context.source.sender.server.worlds) {
                builder.suggest(world.name);
            }
            return builder.buildFuture()
        }
    }

    override val name: String
        get() = "lockout"
    override val description: String
        get() = "Manage player lockouts, which prevent players from joining during resets."
    override val permission: Permission
        get() = Permission("homerun.commands.lockout", description, PermissionDefault.OP)
    override val commandNode: LiteralCommandNode<CommandSourceStack>
        get() = Commands.literal(name)
            .requires { it.sender.hasPermission(permission) }
            .then(
                Commands.literal("enable")
                    .then(
                        Commands
                            .argument("world", StringArgumentType.string())
                            .suggests(LockoutSubcommand::suggestWorldNames)
                            .executes { ctx -> toggleLockout(true, ctx) }
                    )
            )
            .then(
                Commands.literal("disable")
                    .then(
                        Commands
                            .argument("world", StringArgumentType.string())
                            .suggests(LockoutSubcommand::suggestWorldNames)
                            .executes { ctx -> toggleLockout(false, ctx) }
                    )
            )
            .build()

    fun toggleLockout(enable: Boolean, ctx: CommandContext<CommandSourceStack>): Int {
        val worldName = ctx.getArgument("world", String::class.java)

        val world = ctx.source.sender.server.createWorld(WorldCreator(worldName))
        if (world == null) {
            ctx.source.sender.sendMessage("World '$worldName' does not exist.")
            return 0
        }

        if (enable) {
            PlayerLockout.of(world).lock()
            ctx.source.sender.sendMessage("Enabled lockout for world '$worldName'.")
        } else {
            PlayerLockout.of(world).unlock()
            ctx.source.sender.sendMessage("Disabled lockout for world '$worldName'.")
        }

        return Command.SINGLE_SUCCESS
    }

}