package com.evidex.util

import org.bukkit.Bukkit

object ServerCompatibility {

    /** Legacy 1.21.x branch (Paper/Spigot). */
    private val MIN_LEGACY = MinecraftRelease(1, 21, 0)
    /** New versioning branch starting at 26.1 (Chaos / game drops). */
    private val MIN_MODERN = MinecraftRelease(26, 1, 0)
    private val MAX_TESTED = MinecraftRelease(26, 2, 0)

    data class MinecraftRelease(val major: Int, val minor: Int, val patch: Int) : Comparable<MinecraftRelease> {
        override fun compareTo(other: MinecraftRelease): Int {
            major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
            minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
            return patch.compareTo(other.patch)
        }

        fun label(): String = if (major >= 26) {
            if (patch == 0) "$major.$minor" else "$major.$minor.$patch"
        } else {
            "$major.$minor.$patch"
        }
    }

    fun detectRelease(): MinecraftRelease? {
        val raw = runCatching { Bukkit.getServer().minecraftVersion }.getOrNull()
            ?: Bukkit.getBukkitVersion().substringBefore('-')
        return parseRelease(raw)
    }

    fun parseRelease(raw: String): MinecraftRelease? {
        val parts = raw.trim().split('.')
        if (parts.size < 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return MinecraftRelease(major, minor, patch)
    }

    private fun isSupported(release: MinecraftRelease): Boolean {
        if (release.major >= 26) return release >= MIN_MODERN
        return release >= MIN_LEGACY
    }

    data class ValidationResult(
        val ok: Boolean,
        val release: MinecraftRelease?,
        val platform: String,
        val bukkitVersion: String,
        val warnings: List<String> = emptyList()
    )

    fun validate(log: EvidexLog): ValidationResult {
        val release = detectRelease()
        val bukkitVersion = Bukkit.getBukkitVersion()
        val impl = Bukkit.getServer().name
        val warnings = mutableListOf<String>()

        if (release == null) {
            warnings += "No se pudo detectar la version de Minecraft ($bukkitVersion)"
            return ValidationResult(ok = true, release = null, platform = impl, bukkitVersion = bukkitVersion, warnings = warnings)
        }

        val label = release.label()

        if (!isSupported(release)) {
            log.error("Evidex requiere Minecraft 1.21+ o 26.1+. Version detectada: $label")
            return ValidationResult(ok = false, release = release, platform = impl, bukkitVersion = bukkitVersion)
        }

        if (release > MAX_TESTED) {
            warnings += "Probado hasta ${MAX_TESTED.label()}; version detectada: $label"
        }

        if (release.major >= 26) {
            val javaVersion = System.getProperty("java.version") ?: "desconocida"
            val javaMajor = javaVersion.substringBefore('.').toIntOrNull()
                ?: javaVersion.removePrefix("1.").substringBefore('.').toIntOrNull()
            if (javaMajor != null && javaMajor < 25) {
                warnings += "Minecraft $label requiere Java 25+ (servidor: Java $javaVersion)"
            }
        }

        warnings += "No uses /reload — reinicia con /stop"

        return ValidationResult(ok = true, release = release, platform = impl, bukkitVersion = bukkitVersion, warnings = warnings)
    }
}