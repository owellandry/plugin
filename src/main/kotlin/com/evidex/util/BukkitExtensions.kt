@file:Suppress("DEPRECATION")

package com.evidex.util

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

object BukkitExtensions {

    private val LEGACY_COLOR = Regex("§.")

    fun entityLabel(entity: Entity): String = entity.name

    @Suppress("DEPRECATION")
    fun isOnGround(player: Player): Boolean = player.isOnGround

    fun maxHealth(entity: LivingEntity): Double =
        entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0

    @Suppress("DEPRECATION")
    fun readCustomName(entity: LivingEntity): String? {
        val raw = entity.getCustomName() ?: return null
        if (raw.isBlank()) return null
        return stripLegacyColors(raw)
    }

    fun livingEntityLabel(entity: LivingEntity): String =
        readCustomName(entity) ?: entity.type.name

    @Suppress("DEPRECATION")
    fun setCustomName(entity: LivingEntity, name: String?) {
        entity.setCustomName(name)
    }

    fun sendMessage(sender: CommandSender, message: String) {
        sender.sendMessage(message)
    }

    fun sendActionBar(player: Player, message: String) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(message))
    }

    private fun stripLegacyColors(text: String): String = text.replace(LEGACY_COLOR, "")
}