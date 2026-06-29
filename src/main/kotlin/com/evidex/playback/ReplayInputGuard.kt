package com.evidex.playback

import com.evidex.EvidexPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/** Bloquea interacción del admin mientras revisa evidencia. */
class ReplayInputGuard(
    private val plugin: EvidexPlugin,
    private val viewer: Player
) : Listener {

    private var active = false

    fun activate() {
        if (active) return
        active = true
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun deactivate() {
        if (!active) return
        active = false
        HandlerList.unregisterAll(this)
    }

    private fun isViewer(player: Player): Boolean =
        active && player.uniqueId == viewer.uniqueId

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (isViewer(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (isViewer(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (isViewer(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (isViewer(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        if (isViewer(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        if (isViewer(attacker)) event.isCancelled = true
    }
}