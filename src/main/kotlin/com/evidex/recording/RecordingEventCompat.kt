package com.evidex.recording

import com.evidex.EvidexPlugin
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority

/** Registra eventos de pickup solo si existen en el servidor (Spigot vs Paper). */
object RecordingEventCompat {

    private val PICKUP_EVENTS = listOf(
        "org.bukkit.event.player.PlayerAttemptPickupItemEvent",
        "org.bukkit.event.entity.EntityPickupItemEvent"
    )

    fun registerPickupListener(
        plugin: EvidexPlugin,
        owner: RecordingSession,
        player: Player,
        onPickup: () -> Unit
    ) {
        val eventClass = findPickupEventClass() ?: return
        plugin.server.pluginManager.registerEvent(
            eventClass,
            owner,
            EventPriority.MONITOR,
            { _, event ->
                val picker = extractPlayer(eventClass, event) ?: return@registerEvent
                if (picker.uniqueId != player.uniqueId) return@registerEvent
                onPickup()
            },
            plugin,
            true
        )
    }

    private fun findPickupEventClass(): Class<out Event>? {
        for (name in PICKUP_EVENTS) {
            try {
                @Suppress("UNCHECKED_CAST")
                return Class.forName(name).asSubclass(Event::class.java)
            } catch (_: Exception) {
                // Probar siguiente evento compatible
            }
        }
        return null
    }

    private fun extractPlayer(eventClass: Class<out Event>, event: Event): Player? {
        return try {
            if (eventClass.simpleName == "EntityPickupItemEvent") {
                (eventClass.getMethod("getEntity").invoke(event) as? Player)
            } else {
                eventClass.getMethod("getPlayer").invoke(event) as? Player
            }
        } catch (_: Exception) {
            null
        }
    }
}