package com.evidex.detection

import com.evidex.config.ConfigManager
import org.bukkit.GameMode
import org.bukkit.entity.Player

class DetectionGate(private val config: ConfigManager) {

    fun canRun(player: Player): Boolean {
        if (!config.isDetectionEnabled()) return false
        if (player.hasPermission(config.getExemptPermission())) return false
        if (config.isExemptCreative() && player.gameMode == GameMode.CREATIVE) return false
        if (config.isExemptSpectator() && player.gameMode == GameMode.SPECTATOR) return false
        val world = player.world.name
        if (config.getDetectionDisabledWorlds().any { it.equals(world, ignoreCase = true) }) return false
        if (config.getDetectionEnabledWorlds().isNotEmpty() &&
            config.getDetectionEnabledWorlds().none { it.equals(world, ignoreCase = true) }
        ) return false
        return true
    }

    fun isInJoinGrace(profile: PlayerProfile): Boolean {
        val grace = config.getJoinGraceSeconds() * 1000L
        return grace > 0 && System.currentTimeMillis() - profile.joinedAt < grace
    }

    fun lagReachBuffer(player: Player): Double {
        if (!config.isLagCompensationEnabled()) return 0.0
        val ping = player.ping.coerceAtLeast(0)
        return (ping / 1000.0) * config.getLagReachPerMs()
    }

    fun scaledVl(base: Int): Int {
        val mult = config.getSensitivityMultiplier()
        return (base * mult).toInt().coerceAtLeast(1)
    }

    fun canAlert(profile: PlayerProfile, checkName: String): Boolean {
        val cooldown = config.getAlertCooldownMs()
        if (cooldown <= 0) return true
        val last = profile.lastAlertMs[checkName] ?: 0L
        val now = System.currentTimeMillis()
        if (now - last < cooldown) return false
        profile.lastAlertMs[checkName] = now
        return true
    }
}