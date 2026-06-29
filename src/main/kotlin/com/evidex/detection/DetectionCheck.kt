package com.evidex.detection

import org.bukkit.entity.Player

interface DetectionCheck {
    val name: String
    val category: ViolationCategory
    fun check(player: Player, profile: PlayerProfile): ViolationResult?
}