package com.evidex.playback

import com.evidex.EvidexPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Oculta entidades del servidor en vivo para el admin viewer.
 * Solo él ve el replay; los NPCs/marcadores se muestran explícitamente después.
 */
class ReplayEntityIsolation(
    private val plugin: EvidexPlugin,
    private val viewer: Player
) : Listener {

    private val hiddenEntities = mutableSetOf<java.util.UUID>()
    private val hiddenPlayers = mutableSetOf<java.util.UUID>()
    private var active = false

    fun activate() {
        if (active) return
        active = true
        plugin.server.pluginManager.registerEvents(this, plugin)
        hideAllLiveEntities()
    }

    fun deactivate() {
        if (!active) return
        active = false
        HandlerList.unregisterAll(this)
        restore()
    }

    fun showReplayEntity(entity: Entity) {
        if (!active || !entity.isValid) return
        viewer.showEntity(plugin, entity)
        hiddenEntities.remove(entity.uniqueId)
    }

    private fun hideAllLiveEntities() {
        for (world in Bukkit.getWorlds()) {
            for (entity in world.entities) {
                hideFromViewer(entity)
            }
        }
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.uniqueId == viewer.uniqueId) continue
            viewer.hidePlayer(plugin, player)
            player.hidePlayer(plugin, viewer)
            hiddenPlayers.add(player.uniqueId)
        }
    }

    private fun hideFromViewer(entity: Entity) {
        if (!active) return
        if (entity.uniqueId == viewer.uniqueId) return
        if (!viewer.canSee(entity)) return
        viewer.hideEntity(plugin, entity)
        hiddenEntities.add(entity.uniqueId)
    }

    private fun restore() {
        for (uuid in hiddenEntities) {
            Bukkit.getEntity(uuid)?.let { viewer.showEntity(plugin, it) }
        }
        hiddenEntities.clear()

        for (uuid in hiddenPlayers) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            viewer.showPlayer(plugin, player)
            player.showPlayer(plugin, viewer)
        }
        hiddenPlayers.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntitySpawn(event: EntitySpawnEvent) {
        hideFromViewer(event.entity)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val joiner = event.player
        if (joiner.uniqueId == viewer.uniqueId) return
        viewer.hidePlayer(plugin, joiner)
        joiner.hidePlayer(plugin, viewer)
        hiddenPlayers.add(joiner.uniqueId)
        hideFromViewer(joiner)
    }
}