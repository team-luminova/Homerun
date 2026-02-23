package net.chlod.minecraft.homerun.helpers

import org.bukkit.Bukkit

internal data class MinecraftVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<MinecraftVersion> {

    override fun compareTo(other: MinecraftVersion): Int {
        val a = listOf(major, minor, patch)
        val b = listOf(other.major, other.minor, other.patch)
        return a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: 0
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val MC_IN_VERSION_REGEX = Regex("\\(MC: (\\d+\\.\\d+(?:\\.\\d+)?)\\)")

        fun parseOrNull(raw: String?): MinecraftVersion? {
            if (raw.isNullOrBlank()) return null

            val base = raw.trim()
                .substringBefore(' ') // defensive: drop trailing text
                .substringBefore('-') // drop -R0.1-SNAPSHOT, etc.
                .trim()

            val parts = base.split('.')
            if (parts.size < 2) return null

            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            return MinecraftVersion(major, minor, patch)
        }

        fun detectRuntimeOrNull(): MinecraftVersion? {
            // Modern Paper/Spigot gives a clean string here.
            parseOrNull(runCatching { Bukkit.getMinecraftVersion() }.getOrNull())?.let { return it }

            // Often like: 1.21.4-R0.1-SNAPSHOT
            parseOrNull(runCatching { Bukkit.getBukkitVersion() }.getOrNull())?.let { return it }

            // Often like: git-Paper-XXX (MC: 1.21.4)
            val version = runCatching { Bukkit.getVersion() }.getOrNull()
            val match = version?.let { MC_IN_VERSION_REGEX.find(it) }
            return parseOrNull(match?.groupValues?.getOrNull(1))
        }
    }
}

