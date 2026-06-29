package com.evidex.detection.combat

import com.evidex.config.ConfigManager
import com.evidex.util.BukkitExtensions
import com.evidex.detection.DetectionCheck
import com.evidex.detection.DetectionUtils
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

class KillAuraCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "killaura"
    override val category = ViolationCategory.COMBAT

    fun checkAttack(player: Player, profile: PlayerProfile, target: Entity): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        profile.recordAttack(target.uniqueId)

        val maxAngle = config.getCheckMaxAngle(name)
        val angle = DetectionUtils.angleToEntity(player, target)
        val multiTarget = profile.distinctRecentTargets() >= config.getCheckMaxTargets(name)
        val noLos = !player.hasLineOfSight(target)
        val behind = angle > maxAngle

        if (!multiTarget && !behind && !noLos) return null

        val reasons = mutableListOf<String>()
        if (multiTarget) reasons.add("targets=${profile.distinctRecentTargets()}")
        if (behind) reasons.add("angle=${String.format("%.1f", angle)}")
        if (noLos) reasons.add("noLOS=true")

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, if (multiTarget) 4 else 3),
            info = mapOf(
                "reason" to reasons.joinToString(","),
                "target" to BukkitExtensions.entityLabel(target)
            )
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}