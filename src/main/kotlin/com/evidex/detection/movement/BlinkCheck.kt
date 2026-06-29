package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player

class BlinkCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "blink"
    override val category = ViolationCategory.MOVEMENT

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return null
        if (player.isInsideVehicle) return null

        val from = profile.lastLocation ?: return null
        val to = player.location
        if (from.world?.uid != to.world?.uid) return null

        val dist = from.distance(to)
        val maxDist = config.getCheckMaxBlinkDistance(name)
        if (dist <= maxDist) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 4),
            info = mapOf("dist" to String.format("%.2f", dist), "max" to maxDist.toString())
        )
    }
}