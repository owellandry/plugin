package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType

class NoFallCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "nofall"
    override val category = ViolationCategory.MOVEMENT

    // Bloques que anulan o reducen el daño de caída de forma legítima
    // (coincidencia por substring para cubrir variantes versionadas).
    private val safeLanding = setOf(
        "WATER", "BUBBLE_COLUMN", "SLIME_BLOCK", "HONEY_BLOCK", "HAY_BLOCK",
        "COBWEB", "POWDER_SNOW", "SWEET_BERRY_BUSH", "LADDER", "VINE",
        "SCAFFOLDING", "TWISTING_VINES", "WEEPING_VINES", "CAVE_VINES"
    )

    fun checkLand(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return null

        val fallDist = profile.pendingFallDistance
        profile.pendingFallDistance = 0.0
        val minFall = config.getCheckMinFallDistance(name)
        if (fallDist < minFall) return null

        // Exenciones de no-daño legítimo.
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return null
        if (hasFeatherFalling(player)) return null
        if (landedOnSafeBlock(player)) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 3),
            info = mapOf("fall" to String.format("%.1f", fallDist), "min" to minFall.toString())
        )
    }

    private fun hasFeatherFalling(player: Player): Boolean {
        val boots = player.inventory.boots
        return !boots.type.isAir && boots.enchantments.keys.any { it.key.key.equals("feather_falling", ignoreCase = true) }
    }

    private fun landedOnSafeBlock(player: Player): Boolean {
        val feet = player.location.block
        val below = feet.getRelative(BlockFace.DOWN)
        for (block in listOf(feet, below)) {
            val n = block.type.name
            if (safeLanding.any { n.contains(it) }) return true
        }
        return false
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}
