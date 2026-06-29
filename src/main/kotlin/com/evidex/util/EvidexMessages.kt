package com.evidex.util

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Mensajes in-game con estilo consistente (§-codes, compatible Spigot/Paper). */
object EvidexMessages {

    private const val PREFIX = "§6Evidex§8 "

    fun send(sender: CommandSender, text: String, color: String = "§7") {
        BukkitExtensions.sendMessage(sender, "$PREFIX$color$text")
    }

    fun success(sender: CommandSender, text: String) = send(sender, text, "§a")

    fun error(sender: CommandSender, text: String) = send(sender, text, "§c")

    fun warn(sender: CommandSender, text: String) = send(sender, text, "§e")

    fun info(sender: CommandSender, text: String) = send(sender, text, "§7")

    fun actionBar(player: Player, text: String) {
        BukkitExtensions.sendActionBar(player, "§7$text")
    }
}