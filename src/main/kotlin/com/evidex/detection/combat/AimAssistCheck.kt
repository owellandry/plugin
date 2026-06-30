package com.evidex.detection.combat

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.DetectionUtils
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

class AimAssistCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "aimassist"
    override val category = ViolationCategory.COMBAT

    fun checkAttack(player: Player, profile: PlayerProfile, target: Entity): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        val yawDelta = angleDiff(player.location.yaw, profile.lastYaw)
        val pitchDelta = kotlin.math.abs(player.location.pitch - profile.lastPitch)
        val snapThreshold = config.getCheckSnapDegrees(name)

        val angleNow = DetectionUtils.angleToEntity(player, target)
        val wasLookingAway = profile.lastAngleToTarget?.let { it > snapThreshold * 2 } ?: false
        val snapped = (yawDelta > snapThreshold || pitchDelta > snapThreshold) && angleNow < 15.0

        profile.lastYaw = player.location.yaw
        profile.lastPitch = player.location.pitch
        profile.lastAngleToTarget = angleNow

        if (!snapped && !wasLookingAway) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 2),
            info = mapOf(
                "yawDelta" to String.format("%.1f", yawDelta),
                "pitchDelta" to String.format("%.1f", pitchDelta),
                "angle" to String.format("%.1f", angleNow)
            )
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null

    /** Diferencia angular menor entre dos yaw, manejando el wrap en ±180°. */
    private fun angleDiff(a: Float, b: Float): Float {
        var d = kotlin.math.abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }
}