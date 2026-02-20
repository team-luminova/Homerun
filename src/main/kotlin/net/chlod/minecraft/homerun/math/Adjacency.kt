package net.chlod.minecraft.homerun.math

class Adjacency {

    enum class Direction(val dx: Int, val dz: Int) {
        // Minecraft coordinate system: X increases to the EAST, Z increases to the SOUTH
        SOUTH(0, 1),
        SOUTHEAST(1, 1),
        EAST(1, 0),
        NORTHEAST(1, -1),
        NORTH(0, -1),
        NORTHWEST(-1, -1),
        WEST(-1, 0),
        SOUTHWEST(-1, 1)
    }

    companion object {
        fun findAdjacentFlaggedPositions(
            allPositions: Set<Pair<Int, Int>>,
            flaggedPositions: Set<Pair<Int, Int>>
        ): Map<Pair<Int, Int>, List<Direction>> {
            val nonflaggedPositions = allPositions - flaggedPositions
            return nonflaggedPositions.mapNotNull { position ->
                val flaggedDirections = Direction.entries.filter { direction ->
                    val neighbor = Pair(
                        position.first + direction.dx,
                        position.second + direction.dz
                    )
                    neighbor in flaggedPositions
                }

                if (flaggedDirections.isNotEmpty()) {
                    position to flaggedDirections
                } else {
                    null
                }
            }.toMap()
        }
    }

}