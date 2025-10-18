package net.chlod.minecraft.homerun

import net.chlod.minecraft.homerun.command.ResetCommand
import net.chlod.minecraft.homerun.data.ResetData
import net.chlod.minecraft.homerun.listeners.PlayerJoinListener
import net.chlod.minecraft.homerun.offline.ChunkTransferUtil
import org.bukkit.plugin.java.JavaPlugin

class Homerun : JavaPlugin() {

    var lockedDown = false

    override fun onLoad() {
        // Load logic
        val resetData = ResetData.find(this)
        if (resetData != null) {
            componentLogger.info("Found existing reset data for world ${resetData.sourceWorld} to ${resetData.targetWorld}")
            componentLogger.info("Running chunk transplant...")
            ChunkTransferUtil(resetData).transferChunks()
            componentLogger.info("Finished chunk transplant")
            resetData.delete()
        } else {
            componentLogger.info("No existing reset data found")
        }
    }

    override fun onEnable() {
        saveDefaultConfig()

        registerCommand("reset", ResetCommand(this))
        server.pluginManager.registerEvents(PlayerJoinListener(this), this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
