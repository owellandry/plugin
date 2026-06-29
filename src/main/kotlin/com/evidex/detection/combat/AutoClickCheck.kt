package com.evidex.detection.combat

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player

class AutoClickCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "autoclick"
    override val category = ViolationCategory.COMBAT

    fun checkSwing(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        profile.recordSwing()
        val cps = profile.cps()
        val maxCps = config.getCheckMaxCps(name)
        if (cps < maxCps) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 2),
            info = mapOf("cps" to cps.toString(), "max" to maxCps.toString())
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}