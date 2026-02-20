package net.chlod.minecraft.homerun.config

import net.minecraft.ChatFormatting
import org.bukkit.Color

class ColorParser {

    companion object {
        @JvmStatic
        fun parseColor(value: Any?): Color? {
            return if (value is String) {
                if (value.startsWith("#")) {
                    if (value.length != 7) {
                        throw IllegalArgumentException("Hex color must be in the format #RRGGBB")
                    }
                    try {
                        Color.fromRGB(
                            Integer.valueOf(value.substring(1, 3), 16),
                            Integer.valueOf(value.substring(3, 5), 16),
                            Integer.valueOf(value.substring(5, 7), 16)
                        )
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("Invalid hex color format: ${value}")
                    }
                } else {
                    val chatColor = ChatFormatting.getByName(value)
                        ?: throw IllegalArgumentException("Invalid hex color format: ${value}")
                    Color.fromRGB(chatColor.color!!)
                }
            } else if (value is Int) {
                val chatColor = ChatFormatting.getById(value)
                    ?: throw IllegalArgumentException("Invalid color number: ${value}")
                Color.fromRGB(chatColor.color!!)
            } else if (value == null) {
                null
            } else {
                throw IllegalArgumentException("Color must be either a color name, hex, or ID (integer)")
            }
        }
    }

}