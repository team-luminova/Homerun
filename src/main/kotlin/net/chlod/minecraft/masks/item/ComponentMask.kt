package net.chlod.minecraft.masks.item

import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.inventory.ItemStack

interface ComponentMask<T> : ConfigurationSerializable {

    fun testItem(itemStack: ItemStack): Boolean
    fun test(data: T): Boolean

}