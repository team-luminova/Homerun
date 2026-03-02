package net.chlod.minecraft.homerun.extern

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.helpers.DurationHandler
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.Player
import kotlin.math.floor

class HomerunPlaceholderExpansion(val plugin: Homerun) : PlaceholderExpansion() {

    override fun getAuthor(): String {
        return "Luminova"
    }

    override fun getIdentifier(): String {
        return "homerun"
    }

    override fun getVersion(): String {
        @Suppress("UnstableApiUsage")
        return plugin.pluginMeta.version
    }

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val world = player?.location?.world
        return processWorldPlaceholders(world, params)
    }

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        return processWorldPlaceholders(player?.location?.world, params)
    }

    fun processWorldPlaceholders(playerWorld: World?, params: String): String? {
        val mainWorld = plugin.server.worlds.firstOrNull() ?: return null
        val world = playerWorld ?: mainWorld
        val resetRules = plugin.resetRules.filter { it.affectsWorld(plugin, world.name) }
        if (resetRules.isEmpty())
            return null
        val soonestResetTime = resetRules.mapNotNull { plugin.timeUntilNextResetCache[it] }.minOrNull() ?: return null
        val soonestResetTimestamp = System.currentTimeMillis() + soonestResetTime

        return when (params) {
            "countdown" -> {
                DurationHandler(plugin).asCountdown(soonestResetTime)
            }

            "countdown_text" -> {
                DurationHandler(plugin).asTextString(soonestResetTime)
            }

            "next_reset_seconds" -> {
                floor(soonestResetTime / 1000.0).toInt().toString()
            }

            "next_reset_timestamp" -> {
                soonestResetTimestamp.toString()
            }

            "next_reset_date" -> {
                formatSoonestResetDateTime(soonestResetTimestamp, "yyyy-MM-dd")
            }

            "next_reset_time" -> {
                formatSoonestResetDateTime(soonestResetTimestamp, "HH:mm z")
            }

            "next_reset_datetime" -> {
                formatSoonestResetDateTime(soonestResetTimestamp, "yyyy-MM-dd HH:mm z")
            }

            else -> {
                if (params.startsWith("next_reset_")) {
                    val format = params.substringAfter("next_reset_").replace("_", " ")
                    formatSoonestResetDateTime(soonestResetTimestamp, format)
                } else {
                    null
                }
            }
        }
    }

    fun formatSoonestResetDateTime(timestamp: Long, format: String): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat(format)
        return formatter.format(date)
    }

}