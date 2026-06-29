package com.evidex.detection.player

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

class FastInventoryCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "fastinventory"
    override val category = ViolationCategory.PLAYER

    fun checkClick(player: Player, profile: PlayerProfile, event: InventoryClickEvent): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        profile.recordInventoryClick()
        val maxCps = config.getCheckMaxInventoryCps(name)
        val cps = profile.inventoryClicksPerSecond()
        if (cps < maxCps) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 2),
            info = mapOf("clicksPerSec" to cps.toString(), "max" to maxCps.toString())
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}