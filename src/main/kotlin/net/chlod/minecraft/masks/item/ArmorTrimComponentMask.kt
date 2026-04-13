package net.chlod.minecraft.masks.item

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemArmorTrim
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.trim.TrimMaterial
import org.bukkit.inventory.meta.trim.TrimPattern

@Suppress("UnstableApiUsage")
class ArmorTrimComponentMask private constructor(
    val pattern: TrimPattern,
    val material: TrimMaterial
) : ComponentMask<ItemArmorTrim> {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ArmorTrimComponentMask {
            if (args["pattern"] !is String) {
                throw IllegalArgumentException("Pattern must be a string")
            }
            val patternKey = args["pattern"] as String
            val pattern = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_PATTERN)
                .get(Key.key(patternKey)) ?: throw IllegalArgumentException("Pattern '$patternKey' does not exist")
            val materialKey = args["material"] as String
            val material = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_MATERIAL)
                .get(Key.key(materialKey)) ?: throw IllegalArgumentException("Material '$materialKey' does not exist")

            return ArmorTrimComponentMask(pattern, material)
        }
    }

    override fun testItem(itemStack: ItemStack): Boolean {
        return test(
            itemStack.getData(DataComponentTypes.TRIM) ?: return false
        )
    }

    override fun test(data: ItemArmorTrim): Boolean {
        return data.armorTrim().pattern == pattern && data.armorTrim().material == material
    }

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "pattern" to RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_PATTERN)
                .getKey(pattern)
                .toString(),
            "material" to RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_MATERIAL)
                .getKey(material)
                .toString(),
        )
    }

}