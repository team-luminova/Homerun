package net.chlod.minecraft.homerun.listeners

import kotlin.test.Test
import kotlin.test.assertContains

class EndPillarCleanupListenerTests {

    @Test
    fun `test end crystal spawn locations`() {
        val expectedLocations = listOf(
            Pair(-42, -1),
            Pair(-34, -25),
            Pair(-34, 24),
            Pair(-13, -40),
            Pair(-13, 39),
            Pair(12, -40),
            Pair(12, 39),
            Pair(33, -25),
            Pair(33, 24),
            Pair(42, 0),
        )

        val actualLocations = EndPillarCleanupListener.endCrystalSpawnLocations

        assert(expectedLocations.size == actualLocations.size) { "Expected ${expectedLocations.size} locations, but got ${actualLocations.size}" }

        for (expectedLocation in expectedLocations) {
            assertContains(actualLocations, expectedLocation, expectedLocation.toString())
        }
    }

}