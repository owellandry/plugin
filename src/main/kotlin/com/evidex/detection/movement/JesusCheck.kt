package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.util.BukkitExtensions
import com.evidex.detection.DetectionCheck
import com.evidex.detection.DetectionUtils
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player

class JesusCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "jesus"
    override val category = ViolationCategory.MOVEMENT

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return null
        if (player.isFlying || player.isInsideVehicle || player.isSwimming || player.isInWater) return null
        if (!BukkitExtensions.isOnGround(player)) return null
        if (!DetectionUtils.isOverLiquid(player)) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 3),
            info = mapOf("liquid" to "true")
        )
    }
}