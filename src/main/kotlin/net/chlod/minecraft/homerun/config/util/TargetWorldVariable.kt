package net.chlod.minecraft.homerun.config.util

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import org.bukkit.World

@FunctionalInterface
fun interface TargetWorldVariable {

    fun get(plugin: Homerun, rule: ResetRule, world: World): String

}