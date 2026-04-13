package net.chlod.minecraft.masks

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.chlod.minecraft.masks.item.ArmorTrimComponentMask
import net.chlod.minecraft.masks.item.ComponentMask
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * An item mask. Matches an item, or a specific component of an item.
 */
@Suppress("UnstableApiUsage")
class ItemMask(
    val item: String,
    val components: List<ComponentMask<*>> = listOf()
) : Mask<ItemStack>("ITEM") {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ItemMask {
            if (args["item"] !is String) {
                throw IllegalArgumentException("Item mask type must be a string")
            }
            val item = args["item"] as String

            val components = mutableListOf<ComponentMask<*>>()
            if (args["components"] != null) {
                if (args["components"] !is Map<*, *>) {
                    throw IllegalArgumentException("Item mask components must be a map")
                }
                if ((args["components"] as Map<*, *>).keys.any { it !is String }) {
                    throw IllegalArgumentException("Item mask component keys must be a string")
                }
                @Suppress("UNCHECKED_CAST")
                val componentsRaw = args["components"] as Map<String, *>
                for ((componentKey, componentMask) in componentsRaw) {
                    if (componentMask !is Map<*, *>) {
                        throw IllegalArgumentException("Item component masks must be a map")
                    }
                    if (componentMask.keys.any { it !is String }) {
                        throw IllegalArgumentException("Item component mask keys must be a string")
                    }
                    @Suppress("UNCHECKED_CAST")
                    val typedComponentMask = componentMask as Map<String, Any>

                    val key = Key.key(componentKey)
                    val type = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.DATA_COMPONENT_TYPE)
                        .get(key)
                    when (type) {
                        DataComponentTypes.TRIM -> {
                            components.add(ArmorTrimComponentMask.deserialize(typedComponentMask))
                        }

                        // Because I'm too lazy to implement EVERYTHING right now, when it's not easily iterable.
                        // Or at least not with Paper's existing data component API.
                        else -> throw IllegalArgumentException("Due to budget cuts, this component mask is not supported. If you want to see this implemented, give the developers a heads up!")
                    }
                }
            }

            return ItemMask(item, components)
        }
    }

    init {
        val wildcardCount = item.count { it == '*' }
        if (wildcardCount > 1) {
            throw IllegalArgumentException("Item mask cannot have two wildcards")
        } else if (wildcardCount == 1 && !item.endsWith("*")) {
            throw IllegalArgumentException("Item mask wildcard can only be at the end")
        }
    }

    override fun test(testItem: ItemStack): Boolean {
        val materialKey = testItem.type.key.toString()
        val itemHasNamespace = item.contains(':')
        if (item.endsWith("*")) {
            // Prefix check
            if (itemHasNamespace) {
                if (!materialKey.startsWith(item.dropLast(1))) {
                    return false
                }
            } else {
                // Add "minecraft:" prefix
                if (!materialKey.startsWith("minecraft:" + item.dropLast(1))) {
                    return false
                }
            }
        } else if (materialKey != Material.matchMaterial(item)?.key.toString()) {
            // Exact check
            return false
        }

        // Check for matching components
        for (component in components) {
            if (!component.testItem(testItem)) {
                return false
            }
        }

        return true
    }


}