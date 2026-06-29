package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.NearbyEntityFrame
import com.evidex.util.BukkitExtensions
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack

/**
 * Marcadores de mobs/ítems durante el replay. Solo visibles para el admin que revisa la evidencia.
 */
class ReplayMobMarkerRenderer(
    private val plugin: EvidexPlugin,
    private val viewer: Player,
    private val world: World,
    private val entityIsolation: ReplayEntityIsolation? = null
) : Listener {
    private val slots = mutableListOf<MobSlot>()

    private class MobSlot(
        var identityKey: String = "",
        var stand: ArmorStand? = null
    )

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun update(entities: List<NearbyEntityFrame>) {
        while (slots.size < entities.size) slots.add(MobSlot())
        while (slots.size > entities.size) {
            val removed = slots.removeAt(slots.lastIndex)
            removed.stand?.remove()
        }

        entities.forEachIndexed { index, entity ->
            val slot = slots[index]
            val key = entity.entityUuid ?: entity.name ?: "${entity.entityType}-$index"

            if (slot.stand == null || slot.identityKey != key || !slot.stand!!.isValid) {
                slot.stand?.remove()
                slot.identityKey = key
                slot.stand = spawnMarker(entity)
            } else {
                moveMarker(slot.stand!!, entity)
            }
        }
    }

    fun clear() {
        slots.forEach { it.stand?.remove() }
        slots.clear()
        HandlerList.unregisterAll(this)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val joiner = event.player
        if (joiner.uniqueId == viewer.uniqueId) return
        slots.forEach { slot ->
            slot.stand?.let { stand ->
                if (stand.isValid) joiner.hideEntity(plugin, stand)
            }
        }
    }

    private fun spawnMarker(entity: NearbyEntityFrame): ArmorStand {
        val stand = world.spawn(entityLocation(entity), ArmorStand::class.java) { marker ->
            marker.isVisible = true
            marker.setGravity(false)
            marker.isMarker = true
            marker.setBasePlate(false)
            marker.isSmall = entity.isBaby
            marker.isCustomNameVisible = true
            BukkitExtensions.setCustomName(marker, displayName(entity))
            marker.isSilent = true
            marker.setArms(false)
            val head = mobHeadMaterial(entity.entityType)
            if (head != null) {
                marker.equipment.setHelmet(ItemStack(head))
            }
        }
        entityIsolation?.showReplayEntity(stand)
        hideFromOthers(stand)
        return stand
    }

    private fun moveMarker(stand: ArmorStand, entity: NearbyEntityFrame) {
        val loc = entityLocation(entity)
        stand.teleport(loc)
        stand.isSmall = entity.isBaby
        BukkitExtensions.setCustomName(stand, displayName(entity))
        val head = mobHeadMaterial(entity.entityType)
        if (head != null) {
            stand.equipment.setHelmet(ItemStack(head))
        }
        hideFromOthers(stand)
    }

    private fun hideFromOthers(stand: ArmorStand) {
        viewer.showEntity(plugin, stand)
        for (player in world.players) {
            if (player.uniqueId != viewer.uniqueId) {
                player.hideEntity(plugin, stand)
            }
        }
    }

    private fun displayName(entity: NearbyEntityFrame): String {
        val label = entity.name ?: entity.entityType
        return "§e[$label]"
    }

    private fun entityLocation(entity: NearbyEntityFrame): Location {
        return Location(
            world,
            entity.position.x,
            entity.position.y,
            entity.position.z,
            entity.yaw.degrees,
            entity.pitch.degrees
        )
    }

    private fun mobHeadMaterial(entityTypeName: String): Material? {
        val type = runCatching { EntityType.valueOf(entityTypeName.uppercase()) }.getOrNull() ?: return null
        return when (type) {
            EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED -> Material.ZOMBIE_HEAD
            EntityType.SKELETON, EntityType.STRAY -> Material.SKELETON_SKULL
            EntityType.WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL
            EntityType.CREEPER -> Material.CREEPER_HEAD
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE -> Material.PIGLIN_HEAD
            EntityType.ENDER_DRAGON -> Material.DRAGON_HEAD
            EntityType.PLAYER -> Material.PLAYER_HEAD
            else -> null
        }
    }
}