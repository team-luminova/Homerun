package net.chlod.minecraft.masks

import org.bukkit.configuration.serialization.ConfigurationSerializable

abstract class Mask<T>(val type: String) : ConfigurationSerializable {

    abstract fun test(testItem: T): Boolean

    /**
     * When overriding this function, ALWAYS prepend the supermethod!
     */
    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "type" to type,
        )
    }

}