package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.tasks.ResetPrepareTask

class ResetCommand {

    companion object {

        fun createCommand(plugin: Homerun, commandName: String): LiteralCommandNode<CommandSourceStack> {
            return Commands.literal(commandName)
                .requires { it.sender.hasPermission("homerun.commands.reset") }
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

    }

}