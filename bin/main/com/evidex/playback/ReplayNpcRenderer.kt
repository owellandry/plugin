package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.NearbyEntityFrame
import com.github.juliarn.npclib.api.Npc
import com.github.juliarn.npclib.api.Platform
import com.github.juliarn.npclib.api.profile.Profile
import com.github.juliarn.npclib.api.protocol.enums.EntityPose
import com.github.juliarn.npclib.api.protocol.meta.EntityMetadataFactory
import com.github.juliarn.npclib.api.settings.NpcTrackingRule
import com.github.juliarn.npclib.bukkit.util.BukkitPlatformUtil
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.world.Location
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import org.bukkit.Location as BukkitLocation
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ReplayNpcRenderer(
    private val plugin: EvidexPlugin,
    private val platform: Platform<World, Player, ItemStack, Plugin>,
    private val viewer: Player,
    private val world: World
) {
    private val slots = mutableListOf<NpcSlot>()
    private val pendingStates = ConcurrentHashMap<String, NearbyEntityFrame>()

    private class NpcSlot(
        var identityKey: String = "",
        var npc: Npc<World, Player, ItemStack, Plugin>? = null,
        var spawning: Boolean = false
    )

    fun update(entities: List<NearbyEntityFrame>) {
        while (slots.size < entities.size) slots.add(NpcSlot())
        while (slots.size > entities.size) {
            val removed = slots.removeAt(slots.lastIndex)
            removed.npc?.unlink()
            removed.identityKey.let { pendingStates.remove(it) }
        }

        entities.forEachIndexed { index, entity ->
            val slot = slots[index]
            val identityKey = entityIdentity(entity, index)
            pendingStates[identityKey] = entity

            if (slot.npc == null || slot.identityKey != identityKey) {
                slot.npc?.unlink()
                slot.identityKey = identityKey
                slot.npc = null
                slot.spawning = true
                spawnNpc(entity, index, slot)
            } else {
                applyEntityState(slot.npc!!, entity)
            }
        }
    }

    fun clear() {
        slots.forEach { it.npc?.unlink() }
        slots.clear()
        pendingStates.clear()
    }

    private fun entityIdentity(entity: NearbyEntityFrame, index: Int): String {
        return entity.entityUuid
            ?: entity.playerUuid
            ?: entity.name
            ?: "unknown-$index"
    }

    private fun spawnNpc(entity: NearbyEntityFrame, index: Int, slot: NpcSlot) {
        val location = entityLocation(entity)
        val profile = resolveProfile(entity, index)
        val identityKey = entityIdentity(entity, index)

        platform.newNpcBuilder()
            .position(BukkitPlatformUtil.positionFromBukkitLegacy(location))
            .profile(profile)
            .thenAccept { builder ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (slot.identityKey != identityKey) return@Runnable

                    builder.npcSettings { settings ->
                        settings.trackingRule(NpcTrackingRule.onlyExplicitlyIncludedPlayers())
                    }

                    val npc = builder.build()
                    npc.addIncludedPlayer(viewer)
                    npc.forceTrackPlayer(viewer)
                    slot.npc = npc
                    slot.spawning = false

                    val latest = pendingStates[identityKey] ?: entity
                    applyEntityState(npc, latest)
                })
            }
    }

    private fun resolveProfile(entity: NearbyEntityFrame, index: Int): Profile {
        val uuid = (entity.playerUuid ?: entity.entityUuid)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        val name = entity.name?.takeIf { it.isNotBlank() }

        if (uuid != null && name != null) return Profile.resolved(name, uuid)
        if (uuid != null) return Profile.unresolved(uuid)
        if (name != null) return Profile.unresolved(name)
        return Profile.unresolved("Jugador$index")
    }

    private fun entityLocation(entity: NearbyEntityFrame): BukkitLocation {
        return BukkitLocation(
            world,
            entity.position.x,
            entity.position.y,
            entity.position.z,
            entity.yaw.degrees,
            entity.pitch.degrees
        )
    }

    private fun applyEntityState(npc: Npc<World, Player, ItemStack, Plugin>, entity: NearbyEntityFrame) {
        if (!npc.tracksPlayer(viewer)) {
            npc.addIncludedPlayer(viewer)
            npc.forceTrackPlayer(viewer)
        }

        val yaw = entity.yaw.degrees
        val pitch = entity.pitch.degrees

        val packetApi = PacketEvents.getAPI() ?: return
        val peLocation = Location(
            entity.position.x,
            entity.position.y,
            entity.position.z,
            yaw,
            pitch
        )
        packetApi.playerManager.sendPacketSilently(
            viewer,
            WrapperPlayServerEntityTeleport(npc.entityId(), peLocation, true)
        )

        npc.rotate(yaw, pitch).schedule(viewer)

        val pose = if (entity.isSneaking) EntityPose.CROUCHING else EntityPose.STANDING
        npc.changeMetadata(EntityMetadataFactory.entityPoseMetaFactory(), pose).schedule(viewer)
        npc.changeMetadata(EntityMetadataFactory.sneakingMetaFactory(), entity.isSneaking).schedule(viewer)
    }
}