package com.evidex.detection.combat

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

class VelocityCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "velocity"
    override val category = ViolationCategory.COMBAT

    fun onKnockback(player: Player, profile: PlayerProfile, damage: Double) {
        profile.expectedKnockback = damage.coerceAtLeast(0.1)
        profile.lastVelocityMs = System.currentTimeMillis()
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        val since = System.currentTimeMillis() - profile.lastVelocityMs
        if (since > 500 || profile.lastVelocityMs <= 0) return null

        // Estados que amortiguan legítimamente el knockback -> no es anti-KB.
        if (player.isBlocking || player.isInWater || player.isClimbing ||
            player.location.block.type == Material.COBWEB ||
            player.hasPotionEffect(PotionEffectType.SLOWNESS)
        ) {
            profile.lastVelocityMs = 0
            return null
        }

        val vel = player.velocity
        val horizontal = Vector(vel.x, 0.0, vel.z).length()
        val minKb = profile.expectedKnockback * config.getCheckVelocityMinRatio(name)

        if (horizontal >= minKb) {
            profile.lastVelocityMs = 0
            return null
        }

        profile.lastVelocityMs = 0
        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 3),
            info = mapOf(
                "kb" to String.format("%.3f", horizontal),
                "expected" to String.format("%.3f", minKb)
            )
        )
    }
}