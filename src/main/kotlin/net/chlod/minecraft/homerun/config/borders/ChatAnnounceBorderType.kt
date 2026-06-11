package net.chlod.minecraft.homerun.config.borders

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import java.util.*

class ChatAnnounceBorderType(
    tickPeriod: Long? = null,
    val notifyEnter: Boolean? = null,
    val notifyExit: Boolean? = null,
) : ResetBorder(BorderType.CHAT_ANNOUNCE, tickPeriod) {

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ChatAnnounceBorderType {
            val tickPeriod = (args["period"] as Int?)?.toLong()

            val notifyEnter = (args["notify_enter"] as Boolean?) ?: true;
            val notifyExit = (args["notify_exit"] as Boolean?) ?: true;

            return ChatAnnounceBorderType(
                tickPeriod,
                notifyEnter,
                notifyExit
            )
        }
    }

    val inRetainedChunkCache = mutableMapOf<UUID, Boolean>();

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "notify_enter" to this.notifyEnter,
            "notify_exit" to this.notifyExit,
        )
    }

    override fun doBorderUpdate(
        plugin: Homerun,
        resetRule: ResetRule,
        event: PlayerMoveEvent,
        from: Location,
        to: Location
    ) {
        val inRetainedChunk = plugin.retainedChunkCache.getRetainedChunks(event.player.world.name)
            ?.get(resetRule)
            ?.contains(
                Pair(
                    event.player.chunk.x,
                    event.player.chunk.z
                )
            ) ?: false
        if (inRetainedChunkCache[event.player.uniqueId] == null) {
            inRetainedChunkCache[event.player.uniqueId] = inRetainedChunk
            return
        }
        if (inRetainedChunk != inRetainedChunkCache[event.player.uniqueId]) {
            notify(plugin, event.player, inRetainedChunk)
            inRetainedChunkCache[event.player.uniqueId] = inRetainedChunk
        }
    }

    fun notify(plugin: Homerun, player: Player, entering: Boolean) {
        if (entering && notifyEnter == true) {
            player.sendMessage(plugin.messages.get("border-enter"))
        } else if (!entering && notifyExit == true) {
            player.sendMessage(plugin.messages.get("border-exit"))
        }
    }
}