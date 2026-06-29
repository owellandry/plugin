package com.evidex.detection.player

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionCheck
import com.evidex.detection.DetectionUtils
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.block.Block
import org.bukkit.entity.Player

class XRayCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "xray"
    override val category = ViolationCategory.PLAYER

    fun checkBreak(player: Player, profile: PlayerProfile, block: Block): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return null

        val material = block.type
        if (!DetectionUtils.isValuableOre(material)) return null

        val exposed = DetectionUtils.isBlockExposed(block)
        if (exposed) return null

        profile.recordHiddenOreBreak()
        val hiddenCount = profile.hiddenOreBreaksRecent()
        val minHidden = config.getCheckMinHiddenOres(name)
        if (hiddenCount < minHidden) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 4),
            info = mapOf(
                "block" to material.name,
                "hidden" to "true",
                "count" to hiddenCount.toString(),
                "x" to block.x.toString(),
                "y" to block.y.toString(),
                "z" to block.z.toString()
            )
        )
    }

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? = null
}