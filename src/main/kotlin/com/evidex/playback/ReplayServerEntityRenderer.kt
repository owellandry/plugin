package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.NearbyEntityFrame
import com.evidex.util.BukkitExtensions
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

class ReplayServerEntityRenderer(
    private val plugin: EvidexPlugin,
    private val viewer: Player,
    private val world: World
) {
    private val slots = mutableListOf<ReplayEntitySlot>()

    private class ReplayEntitySlot(
        var identityKey: String = "",
        var entity: Entity? = null
    )

    fun update(entities: List<NearbyEntityFrame>) {
        while (slots.size < entities.size) slots.add(ReplayEntitySlot())
        while (slots.size > entities.size) {
            val removed = slots.removeAt(slots.lastIndex)
            removed.entity?.remove()
        }

        entities.forEachIndexed { index, entity ->
            val slot = slots[index]
            val key = entity.entityUuid ?: entity.playerUuid ?: entity.name ?: "${entity.entityType}-$index"

            if (slot.entity == null || slot.identityKey != key || !slot.entity!!.isValid) {
                slot.entity?.remove()
                slot.identityKey = key
                slot.entity = spawnEntity(entity)
            } else {
                moveEntity(slot.entity!!, entity)
            }
        }
    }

    fun clear() {
        slots.forEach { it.entity?.remove() }
        slots.clear()
    }

    private fun spawnEntity(entity: NearbyEntityFrame): Entity {
        val type = ReplayEntitySpawnMapper.entityTypeForReplay(entity)
        val spawned = world.spawnEntity(entityLocation(entity), type)
        configureSpawnedEntity(spawned, entity)
        return spawned
    }

    private fun moveEntity(entity: Entity, frame: NearbyEntityFrame) {
        entity.teleport(entityLocation(frame))
        if (entity is LivingEntity) {
            entity.setAI(false)
            entity.isCollidable = false
            entity.isInvisible = false
            entity.setCustomNameVisible(true)
            BukkitExtensions.setCustomName(entity, displayName(frame))
        }
        if (entity is ArmorStand) {
            entity.setGravity(false)
            entity.isVisible = true
            entity.isMarker = true
            entity.setBasePlate(false)
        }
        hideFromOthers(entity)
    }

    private fun configureSpawnedEntity(entity: Entity, frame: NearbyEntityFrame) {
        entity.teleport(entityLocation(frame))
        entity.setCustomName(displayName(frame))
        entity.isCustomNameVisible = true
        if (entity is LivingEntity) {
            entity.setAI(false)
            entity.isCollidable = false
            entity.setRemoveWhenFarAway(false)
            entity.canPickupItems = false
            entity.isInvisible = false
            entity.setSilent(true)
            entity.setCustomName(displayName(frame))
        }
        if (entity is ArmorStand) {
            entity.setGravity(false)
            entity.isVisible = true
            entity.isMarker = true
            entity.setBasePlate(false)
        }
        hideFromOthers(entity)
    }

    private fun hideFromOthers(entity: Entity) {
        viewer.showEntity(plugin, entity)
        for (player in world.players) {
            if (player.uniqueId != viewer.uniqueId) {
                player.hideEntity(plugin, entity)
            }
        }
    }

    private fun displayName(frame: NearbyEntityFrame): String {
        val label = frame.name ?: frame.entityType
        return "§e[$label]"
    }

    private fun entityLocation(frame: NearbyEntityFrame): Location {
        return Location(
            world,
            frame.position.x,
            frame.position.y,
            frame.position.z,
            frame.yaw.degrees,
            frame.pitch.degrees
        )
    }
}
