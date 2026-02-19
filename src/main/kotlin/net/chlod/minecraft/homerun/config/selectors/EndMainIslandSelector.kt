package net.chlod.minecraft.homerun.config.selectors

import org.bukkit.World

class EndMainIslandSelector : FromWorldOriginSelector {

    companion object {
        const val MAIN_ISLAND_DIAMETER_BLOCKS = 288
        const val MAIN_ISLAND_DIAMETER_CHUNKS = MAIN_ISLAND_DIAMETER_BLOCKS / 16

        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): FromWorldOriginSelector {
            val expression = args["end_main_island"]
            if (expression == true) {
                return EndMainIslandSelector()
            }
            throw IllegalArgumentException(
                "Couldn't deserialize EndMainIslandSelector: must be set to true (e.g. 'end_main_island: true')"
            )
        }
    }

    constructor() : super(MAIN_ISLAND_DIAMETER_CHUNKS, listOf(World.Environment.THE_END))

    override fun getHumanReadableDescription(): String {
        return "the central island of The End (${MAIN_ISLAND_DIAMETER_BLOCKS}x${MAIN_ISLAND_DIAMETER_BLOCKS})"
    }

    override fun serialize(): Map<String?, Any?> {
        return mutableMapOf<String?, Any?>(Pair("end_main_island", true))
    }

}