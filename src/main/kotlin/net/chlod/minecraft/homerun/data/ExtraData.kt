package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader

class ExtraData {

    public val minecraftVersionMax: String

    constructor(plugin: Homerun) {
        val extrasFile = Homerun::class.java.getResourceAsStream("/extras.yml")
        val extrasScanner = InputStreamReader(extrasFile!!)
        val extrasConfiguration = YamlConfiguration.loadConfiguration(extrasScanner)

        minecraftVersionMax = extrasConfiguration.getString("minecraft-version-max")!!
    }

}