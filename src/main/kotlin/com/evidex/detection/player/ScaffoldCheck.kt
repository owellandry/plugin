package com.evidex.detection.player

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.block.Block
import org.bukkit.entity.Player

class ScaffoldCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "scaffold"
    override val category = ViolationCategory.PLAYER

    fun checkPlace(player: Player, profile: PlayerProfile, block: Block): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        profile.recordPlace()
        val maxPlaces = config.getCheckMaxPlacesPerSecond(name)
        if (profile.placesPerSecond() <= maxPlaces) return null

        val below = player.location.clone().subtract(0.0, 1.0, 0.0).block
        val bridgingDown = block.y <= below.y && player.velocity.y <= 0.0

        if (!bridgingDown && profile.placesPerSecond() < maxPlaces + 2) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 3),
            info = mapOf(
                "placesPerSec" to profile.placesPerSecond().toString(),
                "max" to maxPlaces.toString()
            )
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}