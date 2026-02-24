package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.Homerun
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

class HomerunCommand {

    companion object {
        val luminovaColored =
            Component
                .text("L")
                .color(TextColor.color(0xff0000))
                .clickEvent(
                    ClickEvent.openUrl("https://lmnv.net/")
                )
                .append(
                    Component
                        .text("u")
                        .color(TextColor.color(0xff4747))
                )
                .append(
                    Component
                        .text("m")
                        .color(TextColor.color(0xffff00))
                )
                .append(
                    Component
                        .text("i")
                        .color(TextColor.color(0x00ff00))
                )
                .append(
                    Component
                        .text("n")
                        .color(TextColor.color(0x00ff7f))
                )
                .append(
                    Component
                        .text("o")
                        .color(TextColor.color(0x42a0ff))
                )
                .append(
                    Component
                        .text("v")
                        .color(TextColor.color(0x3676ff))
                )
                .append(
                    Component
                        .text("a")
                        .color(TextColor.color(0xb259ff))
                )

        fun createCommand(plugin: Homerun, commandName: String): LiteralCommandNode<CommandSourceStack> {
            var cmd = Commands.literal(commandName)

            val commands = listOf(
                Pair(
                    ResetCommand.createCommand(plugin, "reset"),
                    "Reset the world immediately given a specific reset rule."
                ),
                Pair(
                    TpworldCommand.createCommand("tpworld"),
                    "Teleport to a world by name."
                ),
                Pair(
                    LockoutCommand.createCommand("lockout"),
                    "Manage player lockouts, which prevent players from joining during resets."
                ),
                Pair(
                    ReloadConfigCommand.createCommand(plugin, "reload"),
                    "Reload the configuration for Homerun without restarting the server."
                ),
                Pair(
                    ReloadChunkCacheCommand.createCommand(plugin, "reloadchunkcache"),
                    "Reload the retained chunk cache used for player notifications without restarting the server."
                ),
            )

            for ((command) in commands) {
                cmd = cmd.then(command)
            }

            cmd = cmd
                .executes { ctx ->
                    var helpText = Component
                        .text("-".repeat(53)).color(TextColor.color(0x038cfc))
                        .appendNewline()
                        .append(
                            Component
                                .text("Homerun")
                                .color(TextColor.color(0x038cfc))
                                .clickEvent(
                                    ClickEvent.openUrl("https://github.com/luminova-osu/Homerun")
                                )
                        )
                        .append(
                            Component
                                .text(" - Reset your Minecraft server world periodically.")
                                .color(TextColor.color(0xfff459))
                        )
                        .appendNewline()
                        .append(
                            Component
                                .text("v${plugin.pluginMeta.version}, \uD83D\uDEE0 with \u2764 by ")
                                .color(TextColor.color(0x9c9c9c))
                        )
                        .append(luminovaColored)
                        .appendNewline()
                        .appendNewline()
                        .append(
                            Component
                                .text("Commands:")
                                .color(TextColor.color(0xffbc47))
                                .decorate(TextDecoration.UNDERLINED)
                        )
                        .appendNewline()

                    for ((command, description) in commands) {
                        helpText = helpText
                            .append(
                                Component
                                    .text("/$commandName ${command.name}")
                                    .color(TextColor.color(0x4766ff))
                            )
                            .append(
                                Component.text(" - ")
                                    .color(TextColor.color(0x9c9c9c))
                            )
                            .append(
                                Component
                                    .text(description)
                                    .color(TextColor.color(0xffffff))
                            )
                            .appendNewline()
                    }

                    helpText = helpText
                        .append(
                            Component.text("-".repeat(53)).color(TextColor.color(0x038cfc))
                        )

                    ctx.source.sender.sendMessage(helpText)
                    return@executes 0
                }

            return cmd.build()
        }
    }

}