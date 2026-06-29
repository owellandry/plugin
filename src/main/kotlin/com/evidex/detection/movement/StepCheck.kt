package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player

class StepCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "step"
    override val category = ViolationCategory.MOVEMENT

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return null
        if (player.isInsideVehicle || player.isFlying || player.isGliding) return null

        val from = profile.lastLocation ?: return null
        val to = player.location
        if (from.world?.uid != to.world?.uid) return null

        val deltaY = to.y - from.y
        val maxStep = config.getCheckMaxStepHeight(name)
        if (deltaY <= maxStep || deltaY > 1.25) return null
        if (player.velocity.y > 0.35) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 2),
            info = mapOf("deltaY" to String.format("%.3f", deltaY), "max" to maxStep.toString())
        )
    }
}