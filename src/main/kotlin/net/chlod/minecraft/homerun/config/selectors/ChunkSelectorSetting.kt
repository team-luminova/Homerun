package net.chlod.minecraft.homerun.config.selectors

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.World
import org.bukkit.configuration.serialization.ConfigurationSerializable

interface ChunkSelectorSetting: ConfigurationSerializable {

    fun getHumanReadableDescription(): String
    fun getRetainedChunks(plugin: Homerun, world: World): Set<Pair<Int, Int>>

}