package net.chlod.minecraft.homerun.command

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.tasks.WorldPrepareTask
import net.kyori.adventure.text.Component

class ResetCommand(val plugin: Homerun): BasicCommand {

    override fun execute(
        commandSourceStack: CommandSourceStack,
        args: Array<out String>
    ) {
        plugin.lockedDown = true
        // Kick all players and prevent new logins
        commandSourceStack.location.world.players.forEach { player ->
            player.kick(Component.text("Server is resetting the world. Please reconnect shortly."))
        }

        WorldPrepareTask(plugin, commandSourceStack.location.world)
            .runTaskTimer(plugin, 0L, 20L)
    }

}