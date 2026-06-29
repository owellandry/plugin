package com.evidex.detection.player

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemConsumeEvent

class FastEatCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "fasteat"
    override val category = ViolationCategory.PLAYER

    fun checkConsume(player: Player, profile: PlayerProfile, event: PlayerItemConsumeEvent): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        val now = System.currentTimeMillis()
        val last = profile.lastEatMs
        profile.lastEatMs = now
        if (last <= 0) return null

        val elapsed = now - last
        val minMs = config.getCheckMinEatMs(name)
        if (elapsed >= minMs) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 2),
            info = mapOf("elapsedMs" to elapsed.toString(), "minMs" to minMs.toString())
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}