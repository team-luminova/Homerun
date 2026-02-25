package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.tasks.ResetPrepareTask
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault

class ResetSubcommand(private val plugin: Homerun) : IHomerunSubcommand {

    override val name: String
        get() = "reset"
    override val description: String
        get() = "Reset the world immediately given a specific reset rule."
    override val permission: Permission
        get() = Permission("homerun.commands.reset", description, PermissionDefault.OP)
    override val commandNode: LiteralCommandNode<CommandSourceStack>
        get() = Commands.literal(name)
            .requires { it.sender.hasPermission(permission) }
            .then(
                Commands.argument("rule", StringArgumentType.string())
                    .suggests { _, builder ->
                        plugin.resetRules
                            .filter { it.name != null }
                            .forEach { builder.suggest(it.name) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                        val ruleName = ctx.getArgument("rule", String::class.java)

                        val rule = plugin.resetRules.find { it.name == ruleName }
                        if (rule != null) {
                            plugin.componentLogger.info("Starting reset with rule: $ruleName")
                            ctx.source.sender.sendMessage("Starting reset with rule: $ruleName")
                            ResetPrepareTask(plugin, rule)
                                .runTaskTimer(plugin, 0L, 20L)
                        } else {
                            ctx.source.sender.sendMessage("Could not find rule with name: $ruleName")
                            ctx.source.sender.sendMessage(
                                "Available rules: " +
                                        plugin.resetRules.joinToString { it.name.toString() }
                            )
                        }

                        Command.SINGLE_SUCCESS
                    }
            )
            .build()

}