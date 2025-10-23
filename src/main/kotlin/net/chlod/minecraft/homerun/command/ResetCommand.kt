package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.tasks.ResetPrepareTask

class ResetCommand {

    companion object {

        fun createCommand(plugin: Homerun, commandName: String): LiteralCommandNode<CommandSourceStack> {
            return Commands.literal(commandName)
                .then(
                    Commands.argument("rule", StringArgumentType.string())
                        .executes { ctx ->
                            val ruleName = ctx.getArgument("rule", String::class.java)

                            val config = plugin.config.getList("reset_rules")
                            if (config != null) {
                                for (rule in config) {
                                    if (rule is ResetRule && rule.name == ruleName) {
                                        plugin.componentLogger.info("Forcing reset with rule: $ruleName")
                                        ResetPrepareTask(plugin, rule)
                                            .runTaskTimer(plugin, 0L, 20L)
                                        break
                                    }
                                }
                            }

                            Command.SINGLE_SUCCESS
                        }
                )
                .build()
        }

    }

}