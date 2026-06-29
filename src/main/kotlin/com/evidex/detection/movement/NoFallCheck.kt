package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player

class NoFallCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "nofall"
    override val category = ViolationCategory.MOVEMENT

    fun checkLand(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return null

        val fallDist = profile.pendingFallDistance
        profile.pendingFallDistance = 0.0
        val minFall = config.getCheckMinFallDistance(name)
        if (fallDist < minFall) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 3),
            info = mapOf("fall" to String.format("%.1f", fallDist), "min" to minFall.toString())
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}