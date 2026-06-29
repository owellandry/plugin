package com.evidex.detection.player

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.InventoryUtils
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

class ChestStealerCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "cheststealer"
    override val category = ViolationCategory.PLAYER

    fun checkClick(player: Player, profile: PlayerProfile, event: InventoryClickEvent): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE) return null

        val top = event.view.topInventory
        if (!InventoryUtils.isStorageContainer(top.type)) return null
        if (event.rawSlot >= top.size) return null

        profile.recordContainerClick()
        val windowMs = config.getCheckWindowMs(name)
        val maxClicks = config.getCheckMaxClicksPerWindow(name)
        val clicks = profile.containerClicksInWindow(windowMs)
        if (clicks < maxClicks) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 3),
            info = mapOf(
                "clicks" to clicks.toString(),
                "windowMs" to windowMs.toString(),
                "container" to top.type.name
            )
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}