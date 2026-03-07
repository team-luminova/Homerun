package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.chlod.minecraft.homerun.Homerun
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.permissions.Permission

class HomerunCommand(val plugin: Homerun) : IHomerunCommand {

    private val luminovaColored =
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

    val commands = listOf(
        ResetSubcommand(plugin),
        ReloadConfigSubcommand(plugin),
        ReloadChunkCacheSubcommand(plugin),
        LockoutSubcommand(),
        TpworldSubcommand(),
    )

    override val name: String
        get() = "homerun"
    override val description: String
        get() = "Help command for Homerun"
    override val permission: Permission
        get() = Permission("homerun.command")

    fun createCommand(dispatcher: CommandDispatcher<CommandSourceStack>): LiteralCommandNode<CommandSourceStack> {
        var cmd = Commands.literal(name)
            .requires { it.sender.hasPermission(permission) }

        for (command in commands) {
            cmd = cmd.then(command.commandNode)
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
                                ClickEvent.openUrl("https://github.com/team-luminova/Homerun")
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

                for (command in commands) {
                    if (!ctx.source.sender.hasPermission(command.permission)) {
                        continue
                    }
                    val usageText = dispatcher.getSmartUsage(command.commandNode, ctx.source).values
                    if (usageText.size > 1) {
                        for (usage in usageText) {
                            helpText = helpText
                                .append(
                                    Component
                                        .text("/$name ${command.name} $usage")
                                        .color(TextColor.color(0x4766ff))
                                        .clickEvent(ClickEvent.suggestCommand("/$name ${command.name}"))
                                )
                                .appendNewline()
                        }
                        helpText = helpText
                            .append(
                                Component
                                    .text(" " + command.description)
                                    .color(TextColor.color(0xffffff))
                            )
                            .appendNewline()
                    } else {
                        val usage = if (usageText.isNotEmpty()) " ${usageText.first()}" else ""
                        helpText = helpText
                            .append(
                                Component
                                    .text("/$name ${command.name}$usage")
                                    .color(TextColor.color(0x4766ff))
                                    .clickEvent(ClickEvent.suggestCommand("/$name ${command.name}"))
                            )
                            .append(
                                Component.text(" - ")
                                    .color(TextColor.color(0x9c9c9c))
                            )
                            .append(
                                Component
                                    .text(command.description)
                                    .color(TextColor.color(0xffffff))
                            )
                            .appendNewline()
                    }
                }

                helpText = helpText
                    .append(
                        Component.text("-".repeat(53)).color(TextColor.color(0x038cfc))
                    )

                ctx.source.sender.sendMessage(helpText)
                Command.SINGLE_SUCCESS
            }

        return cmd.build()
    }

}