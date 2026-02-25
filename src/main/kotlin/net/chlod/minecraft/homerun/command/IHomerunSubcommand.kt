package net.chlod.minecraft.homerun.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack

interface IHomerunSubcommand : IHomerunCommand {

    val commandNode: LiteralCommandNode<CommandSourceStack>

}