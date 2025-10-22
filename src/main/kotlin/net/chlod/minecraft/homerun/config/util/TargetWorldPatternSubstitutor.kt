package net.chlod.minecraft.homerun.config.util

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import org.bukkit.World
import org.bukkit.persistence.PersistentDataType
import java.util.regex.Pattern

class TargetWorldPatternSubstitutor(val plugin: Homerun, val rule: ResetRule) {

    companion object {
        private val replacers = mapOf<String, TargetWorldVariable>(
            "world" to TargetWorldVariable { _, _, world ->
                world.name },
            "timestamp" to TargetWorldVariable { _, _, _ ->
                System.currentTimeMillis().toString() },
            "source_world" to TargetWorldVariable { _, _, world ->
                world.name },
            "source_seed" to TargetWorldVariable { _, _, world ->
                world.seed.toString() },
            "reset_count" to TargetWorldVariable { plugin, _, world ->
                world.persistentDataContainer.get(plugin.keys.resetCount, PersistentDataType.INTEGER).toString() },
        )
    }

    fun substitute(sourceWorld: World, targetWorldPattern: String): String {
        return Pattern.compile("\\$\\{(\\w+)}").matcher(targetWorldPattern)
            .replaceAll {
                replacers[it.group(1)]?.get(plugin, rule, sourceWorld) ?: it.group(0)
            }
    }

}