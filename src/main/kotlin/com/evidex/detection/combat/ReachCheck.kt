package com.evidex.detection.combat

import com.evidex.config.ConfigManager
import com.evidex.util.BukkitExtensions
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

class ReachCheck(
    private val config: ConfigManager,
    private val lagBuffer: (Player) -> Double = { 0.0 }
) : DetectionCheck {

    override val name = "reach"
    override val category = ViolationCategory.COMBAT

    fun checkAttack(player: Player, profile: PlayerProfile, target: Entity, distance: Double): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        profile.lastAttackDistance = distance
        profile.lastAttackTargetUuid = target.uniqueId
        profile.recordAttack(target.uniqueId)

        val maxReach = config.getCheckMaxReach(name) + lagBuffer(player)
        if (distance <= maxReach) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 3),
            info = mapOf(
                "dist" to String.format("%.2f", distance),
                "max" to String.format("%.2f", maxReach),
                "target" to BukkitExtensions.entityLabel(target)
            )
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}