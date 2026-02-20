package net.chlod.minecraft.homerun.math

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AdjacencyTests {

    /**
     * Create an x,y position pair from a string.
     */
    fun createFlagPositionMapFromStrings(strings: List<String>): Pair<Set<Pair<Int, Int>>, Set<Pair<Int, Int>>> {
        val positions = mutableSetOf<Pair<Int, Int>>()
        val flaggedPositions = mutableSetOf<Pair<Int, Int>>()

        val width = strings[0].length
        if (strings.any { string -> string.length != width }) {
            throw IllegalArgumentException("All strings must have the same length")
        }

        for (x in 0..<width) {
            for (z in 0..<strings.size) {
                positions.add(Pair(x, z))
                println("x: ${x}, z: ${z}, val: ${strings[x][z]}")
                when (val char = strings[x][z]) {
                    ' ' -> {
                        /* Do nothing */
                    }

                    else -> flaggedPositions.add(Pair(x, z))
                }
            }
        }

        return Pair(positions, flaggedPositions)
    }

    @Test
    fun `test adjacency`() {
        val map = listOf(
            // x (column) increases to the right,
            // z (row) increases downwards.
            /*        N         */
            /*   */ "X Y", /*   */
            /* W */ " ZA", /* E */
            /*   */ "B  "  /*   */
            /*        S         */
        )
        val (positions, flaggedPositions) = createFlagPositionMapFromStrings(
            map
        )

        println(positions)
        println(flaggedPositions)

        val result = Adjacency.findAdjacentFlaggedPositions(positions, flaggedPositions)

        println("Result: $result")

        assertEquals(4, result.size)

        val unretainedChunk1 = result[Pair(0, 1)]
        assertNotNull(unretainedChunk1)
        assertEquals(4, unretainedChunk1.size)
        assertContains(unretainedChunk1, Adjacency.Direction.NORTH)
        assertContains(unretainedChunk1, Adjacency.Direction.SOUTH)
        assertContains(unretainedChunk1, Adjacency.Direction.SOUTHEAST)
        assertContains(unretainedChunk1, Adjacency.Direction.EAST)

        val unretainedChunk2 = result[Pair(1, 0)]
        assertNotNull(unretainedChunk2)
        assertEquals(3, unretainedChunk2.size)
        assertContains(unretainedChunk2, Adjacency.Direction.WEST)
        assertContains(unretainedChunk2, Adjacency.Direction.EAST)
        assertContains(unretainedChunk2, Adjacency.Direction.SOUTH)

        val unretainedChunk3 = result[Pair(2, 1)]
        assertNotNull(unretainedChunk3)
        assertEquals(3, unretainedChunk3.size)
        assertContains(unretainedChunk3, Adjacency.Direction.NORTH)
        assertContains(unretainedChunk3, Adjacency.Direction.WEST)
        assertContains(unretainedChunk3, Adjacency.Direction.SOUTHWEST)

        val unretainedChunk4 = result[Pair(2, 2)]
        assertNotNull(unretainedChunk4)
        assertEquals(2, unretainedChunk4.size)
        assertContains(unretainedChunk4, Adjacency.Direction.WEST)
        assertContains(unretainedChunk4, Adjacency.Direction.NORTHWEST)
    }

}