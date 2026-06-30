package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.util.BukkitExtensions
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType

class FlightCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "flight"
    override val category = ViolationCategory.MOVEMENT

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return null
        if (player.allowFlight || player.isFlying) return null
        if (player.isInsideVehicle) return null
        if (player.isGliding || player.isSwimming || player.isRiptiding) return null
        if (player.location.block.isLiquid) return null
        // Efectos legítimos que mantienen al jugador en el aire -> no es fly hack.
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) return null
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return null

        val maxAir = config.getCheckMaxAirTicks(name)
        if (profile.airTicks < maxAir) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 2),
            info = mapOf(
                "airTicks" to profile.airTicks.toString(),
                "onGround" to BukkitExtensions.isOnGround(player).toString()
            )
        )
    }
}