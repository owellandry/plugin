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

class WallHitCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "wallhit"
    override val category = ViolationCategory.COMBAT

    fun checkAttack(player: Player, profile: PlayerProfile, target: Entity): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null
        if (player.hasLineOfSight(target)) return null

        val blocks = DetectionUtils.countSolidBlocksBetween(player, target)
        val maxBlocks = config.getCheckMaxWallBlocks(name)
        if (blocks < maxBlocks) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 4),
            info = mapOf(
                "blocks" to blocks.toString(),
                "target" to BukkitExtensions.entityLabel(target)
            )
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}