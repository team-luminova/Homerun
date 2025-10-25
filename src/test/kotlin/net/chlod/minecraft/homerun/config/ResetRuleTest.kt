package net.chlod.minecraft.homerun.config

import net.chlod.minecraft.homerun.config.conditions.AlwaysResetCondition
import net.chlod.minecraft.homerun.config.conditions.CronResetCondition
import net.chlod.minecraft.homerun.config.selectors.FromWorldSpawnSelector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResetRuleTest {

    @Test
    fun `test serialize with AlwaysResetCondition and minimal parameters`() {
        // Create a simple reset rule with minimal parameters
        val condition = AlwaysResetCondition()
        val parameters = createMinimalResetParameters()
        val rule = ResetRule(
            conditions = listOf(condition),
            parameters = parameters,
            name = null
        )

        // Serialize the rule
        val serialized = rule.serialize()

        // Verify the serialized structure
        assertNotNull(serialized["conditions"])
        assertNotNull(serialized["parameters"])
        assertNull(serialized["name"])

        val conditions = serialized["conditions"] as List<*>
        assertEquals(1, conditions.size)

        val conditionMap = conditions[0] as Map<*, *>
        assertEquals(true, conditionMap["always"])
    }

    @Test
    fun `test serialize with CronResetCondition`() {
        // Create a reset rule with cron condition
        val condition = CronResetCondition("0 0 * * *") // Every day at midnight
        val parameters = createMinimalResetParameters()
        val rule = ResetRule(
            conditions = listOf(condition),
            parameters = parameters,
            name = "Daily Reset"
        )

        // Serialize the rule
        val serialized = rule.serialize()

        // Verify the serialized structure
        assertNotNull(serialized["conditions"])
        assertNotNull(serialized["parameters"])
        assertEquals("Daily Reset", serialized["name"])

        val conditions = serialized["conditions"] as List<*>
        assertEquals(1, conditions.size)

        val conditionMap = conditions[0] as Map<*, *>
        assertEquals("0 0 * * *", conditionMap["cron"])
    }

    @Test
    fun `test serialize with multiple conditions`() {
        // Create a reset rule with multiple conditions
        val condition1 = AlwaysResetCondition()
        val condition2 = CronResetCondition("0 12 * * *") // Every day at noon
        val parameters = createMinimalResetParameters()
        val rule = ResetRule(
            conditions = listOf(condition1, condition2),
            parameters = parameters,
            name = "Multi-condition Reset"
        )

        // Serialize the rule
        val serialized = rule.serialize()

        // Verify the serialized structure
        val conditions = serialized["conditions"] as List<*>
        assertEquals(2, conditions.size)
    }

    @Test
    fun `test deserialize with AlwaysResetCondition`() {
        // Create a map representing a serialized reset rule
        val serializedMap = mapOf<String, Any>(
            "conditions" to listOf(
                mapOf("always" to true)
            ),
            "parameters" to createMinimalSerializedParameters()
        )

        // Deserialize the rule
        val rule = ResetRule.deserialize(serializedMap)

        // Verify the deserialized rule
        assertEquals(1, rule.conditions.size)
        assertTrue(rule.conditions[0] is AlwaysResetCondition)
        assertNotNull(rule.parameters)
        assertNull(rule.name)
    }

    @Test
    fun `test deserialize with CronResetCondition`() {
        // Create a map representing a serialized reset rule with cron condition
        val serializedMap = mapOf<String, Any>(
            "conditions" to listOf(
                mapOf("cron" to "0 0 * * *")
            ),
            "parameters" to createMinimalSerializedParameters(),
            "name" to "Daily Reset"
        )

        // Deserialize the rule
        val rule = ResetRule.deserialize(serializedMap)

        // Verify the deserialized rule
        assertEquals(1, rule.conditions.size)
        assertTrue(rule.conditions[0] is CronResetCondition)
        assertEquals("Daily Reset", rule.name)
    }

    @Test
    fun `test deserialize with multiple conditions`() {
        // Create a map representing a serialized reset rule with multiple conditions
        val serializedMap = mapOf<String, Any>(
            "conditions" to listOf(
                mapOf("always" to true),
                mapOf("cron" to "0 12 * * *")
            ),
            "parameters" to createMinimalSerializedParameters(),
            "name" to "Multi-condition Reset"
        )

        // Deserialize the rule
        val rule = ResetRule.deserialize(serializedMap)

        // Verify the deserialized rule
        assertEquals(2, rule.conditions.size)
        assertTrue(rule.conditions[0] is AlwaysResetCondition)
        assertTrue(rule.conditions[1] is CronResetCondition)
        assertEquals("Multi-condition Reset", rule.name)
    }

    @Test
    fun `test serialize and deserialize round trip with minimal data`() {
        // Create a reset rule
        val originalRule = ResetRule(
            conditions = listOf(AlwaysResetCondition()),
            parameters = createMinimalResetParameters(),
            name = null
        )

        // Serialize then deserialize
        val serialized = originalRule.serialize()

        @Suppress("UNCHECKED_CAST")
        val deserialized = ResetRule.deserialize(serialized as Map<String, Any>)

        // Verify the round trip
        assertEquals(originalRule.conditions.size, deserialized.conditions.size)
        assertEquals(originalRule.name, deserialized.name)
        assertTrue(deserialized.conditions[0] is AlwaysResetCondition)
    }

    @Test
    fun `test serialize and deserialize round trip with full data`() {
        // Create a reset rule with comprehensive data
        val originalRule = ResetRule(
            conditions = listOf(
                AlwaysResetCondition(),
                CronResetCondition("0 0 * * MON")
            ),
            parameters = createFullResetParameters(),
            name = "Weekly Monday Reset"
        )

        // Serialize then deserialize
        val serialized = originalRule.serialize()

        @Suppress("UNCHECKED_CAST")
        val deserialized = ResetRule.deserialize(serialized as Map<String, Any>)

        // Verify the round trip
        assertEquals(originalRule.conditions.size, deserialized.conditions.size)
        assertEquals(originalRule.name, deserialized.name)
        assertTrue(deserialized.conditions[0] is AlwaysResetCondition)
        assertTrue(deserialized.conditions[1] is CronResetCondition)

        // Verify parameters
        assertNotNull(deserialized.parameters)
        assertEquals(originalRule.parameters.world, deserialized.parameters.world)
        assertEquals(originalRule.parameters.targetWorldPattern, deserialized.parameters.targetWorldPattern)
        assertEquals(originalRule.parameters.modifyServerProperties, deserialized.parameters.modifyServerProperties)
        assertEquals(originalRule.parameters.restart, deserialized.parameters.restart)
        assertEquals(originalRule.parameters.outsidePlayerBehavior, deserialized.parameters.outsidePlayerBehavior)
        assertEquals(originalRule.parameters.netherBehavior, deserialized.parameters.netherBehavior)
        assertEquals(originalRule.parameters.endBehavior, deserialized.parameters.endBehavior)
    }

    @Test
    fun `test deserialize throws exception when conditions is not a list`() {
        val serializedMap = mapOf<String, Any>(
            "conditions" to "not a list",
            "parameters" to createMinimalSerializedParameters()
        )

        assertThrows<IllegalArgumentException> {
            ResetRule.deserialize(serializedMap)
        }
    }

    @Test
    fun `test deserialize throws exception when parameters is not a map`() {
        val serializedMap = mapOf<String, Any>(
            "conditions" to listOf(
                mapOf("always" to true)
            ),
            "parameters" to "not a map"
        )

        assertThrows<IllegalArgumentException> {
            ResetRule.deserialize(serializedMap)
        }
    }

    @Test
    fun `test deserialize throws exception when parameters map has non-string keys`() {
        val serializedMap = mapOf<String, Any>(
            "conditions" to listOf(
                mapOf("always" to true)
            ),
            "parameters" to mapOf(
                1 to "value" // Non-string key
            )
        )

        assertThrows<IllegalArgumentException> {
            ResetRule.deserialize(serializedMap)
        }
    }

    @Test
    fun `test serialize with name includes name in output`() {
        val rule = ResetRule(
            conditions = listOf(AlwaysResetCondition()),
            parameters = createMinimalResetParameters(),
            name = "Test Rule"
        )

        val serialized = rule.serialize()

        assertEquals("Test Rule", serialized["name"])
    }

    @Test
    fun `test serialize without name excludes name from output`() {
        val rule = ResetRule(
            conditions = listOf(AlwaysResetCondition()),
            parameters = createMinimalResetParameters(),
            name = null
        )

        val serialized = rule.serialize()

        assertNull(serialized["name"])
    }

    // Helper functions to create test data

    private fun createMinimalResetParameters(): ResetParameters {
        return ResetParameters(
            retainedChunks = listOf(
                createFromWorldSpawnSelector(10)
            ),
            world = null,
            targetWorldPattern = null,
            modifyServerProperties = null,
            restart = null,
            outsidePlayerBehavior = null,
            netherBehavior = null,
            endBehavior = null
        )
    }

    private fun createFullResetParameters(): ResetParameters {
        return ResetParameters(
            retainedChunks = listOf(
                createFromWorldSpawnSelector(10)
            ),
            world = "world",
            targetWorldPattern = "world_%timestamp%",
            modifyServerProperties = true,
            restart = true,
            outsidePlayerBehavior = ResetParameters.OutsidePlayerBehavior.SPAWN,
            netherBehavior = ResetParameters.DimensionResetBehavior.NORMAL,
            endBehavior = ResetParameters.DimensionResetBehavior.NORMAL
        )
    }

    private fun createMinimalSerializedParameters(): Map<String, Any> {
        return mapOf(
            "retained_chunks" to listOf(
                mapOf("from_world_spawn" to 10)
            )
        )
    }

    private fun createFromWorldSpawnSelector(range: Int): FromWorldSpawnSelector {
        return FromWorldSpawnSelector.deserialize(
            mapOf("from_world_spawn" to range)
        )
    }
}

