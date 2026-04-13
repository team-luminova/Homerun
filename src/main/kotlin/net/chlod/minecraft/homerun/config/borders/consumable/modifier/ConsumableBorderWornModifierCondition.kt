package net.chlod.minecraft.homerun.config.borders.consumable.modifier

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import net.chlod.minecraft.masks.ItemMask
import org.bukkit.entity.Player

class ConsumableBorderWornModifierCondition private constructor(
    val mask: ItemMask,
    val findAll: Boolean,
    val requireHelmet: Boolean,
    val requireChest: Boolean,
    val requireLeggings: Boolean,
    val requireBoots: Boolean,
) : ConsumableBorderModifierCondition {

    companion object {
        const val TYPE = "worn"

        private fun deserializeOptionalBoolean(
            value: Any?,
            default: Boolean = false,
            deserializeFailMessage: String = "Optional boolean value must be a boolean or null"
        ): Boolean {
            return if (value == null) {
                default
            } else value as? Boolean ?: throw IllegalArgumentException(deserializeFailMessage)
        }

        @JvmStatic
        fun deserialize(args: Map<String, Any>): ConsumableBorderWornModifierCondition {
            val mask = ItemMask.deserialize(args)

            val hasRequire = args.keys.any { it.startsWith("require_") }
            val findAll = deserializeOptionalBoolean(
                args["find_all"],
                false,
                "'find_all' must be a boolean or null"
            )
            val requireHelmet = deserializeOptionalBoolean(
                args["require_helmet"],
                !hasRequire,
                "'require_helmet' must be a boolean or null"
            )
            val requireChest = deserializeOptionalBoolean(
                args["require_chest"],
                !hasRequire,
                "'require_chest' must be a boolean or null"
            )
            val requireLeggings = deserializeOptionalBoolean(
                args["require_leggings"],
                !hasRequire,
                "'require_leggings' must be a boolean or null"
            )
            val requireBoots = deserializeOptionalBoolean(
                args["require_boots"],
                !hasRequire,
                "'require_boots' must be a boolean or null"
            )

            return ConsumableBorderWornModifierCondition(
                mask,
                findAll,
                requireHelmet,
                requireChest,
                requireLeggings,
                requireBoots
            )
        }
    }

    override fun onTick(
        plugin: Homerun,
        resetRule: ResetRule,
        border: ConsumableBorderType,
        player: Player
    ): Boolean {
        val helmet = player.inventory.helmet
        val chestplate = player.inventory.chestplate
        val leggings = player.inventory.leggings
        val boots = player.inventory.boots

        val armorChecks = listOf(
            if (requireHelmet) {
                if (helmet != null) mask.test(helmet) else false
            } else findAll,
            if (requireChest) {
                if (chestplate != null) mask.test(chestplate) else false
            } else findAll,
            if (requireLeggings) {
                if (leggings != null) mask.test(leggings) else false
            } else findAll,
            if (requireBoots) {
                if (boots != null) mask.test(boots) else false
            } else findAll
        )
        return when (findAll) {
            true -> armorChecks.all { it }
            false -> armorChecks.any { it }
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "type" to TYPE
        ) + mask.serialize()
    }

}