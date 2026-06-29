package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.util.BukkitExtensions
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType

class SpeedCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "speed"
    override val category = ViolationCategory.MOVEMENT

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return null
        if (player.isInsideVehicle || player.isGliding) return null

        val from = profile.lastLocation ?: return null
        val to = player.location
        if (from.world?.uid != to.world?.uid) return null

        val horizontal = profile.horizontalSpeed(from, to)
        var maxSpeed = config.getCheckMaxSpeed(name)
        if (player.isSprinting) maxSpeed *= 1.3
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            val amp = player.getPotionEffect(PotionEffectType.SPEED)?.amplifier ?: 0
            maxSpeed *= 1.0 + 0.2 * (amp + 1)
        }
        if (!BukkitExtensions.isOnGround(player)) maxSpeed *= 1.15

        if (horizontal <= maxSpeed) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 1),
            info = mapOf(
                "speed" to String.format("%.3f", horizontal),
                "max" to String.format("%.3f", maxSpeed),
                "sprinting" to player.isSprinting.toString()
            )
        )
    }
}