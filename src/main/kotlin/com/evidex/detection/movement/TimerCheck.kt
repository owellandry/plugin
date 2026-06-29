package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player

class TimerCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "timer"
    override val category = ViolationCategory.MOVEMENT

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        val maxMoves = config.getCheckMaxMovesPerSecond(name)
        val mps = profile.movesPerSecond()
        if (mps <= maxMoves) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 2),
            info = mapOf("movesPerSec" to mps.toString(), "max" to maxMoves.toString())
        )
    }
}