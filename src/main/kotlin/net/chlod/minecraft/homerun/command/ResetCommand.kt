package net.chlod.minecraft.homerun.command

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.conditions.AlwaysResetCondition
import net.chlod.minecraft.homerun.config.selectors.FromWorldSpawnSelector
import net.chlod.minecraft.homerun.data.PlayerLockout
import net.chlod.minecraft.homerun.tasks.ResetPrepareTask

class ResetCommand(val plugin: Homerun): BasicCommand {

    override fun execute(
        commandSourceStack: CommandSourceStack,
        args: Array<out String>
    ) {
        val world = commandSourceStack.location.world
        // Kick all players and prevent new logins
        PlayerLockout.of(world).lock()
        PlayerLockout.of(world).kickAll()

        ResetPrepareTask(plugin, ResetRule(
            listOf(AlwaysResetCondition()),
            ResetParameters(
                listOf(FromWorldSpawnSelector(256 / 8))
            ),
            "homerun_reset_command_${System.currentTimeMillis()}"
        ))
            .runTaskTimer(plugin, 0L, 20L)
    }

}