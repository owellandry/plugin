package com.evidex.detection.combat

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.entity.Player

class InvalidRotationCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "badrotation"
    override val category = ViolationCategory.COMBAT

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null

        val pitch = player.location.pitch
        val maxPitch = config.getCheckMaxPitch(name)
        if (kotlin.math.abs(pitch) <= maxPitch) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 2),
            info = mapOf("pitch" to String.format("%.1f", pitch), "max" to maxPitch.toString())
        )
    }
}