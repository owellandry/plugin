package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.PlayerFrame
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

/**
 * NPC del jugador grabado — visible solo para el admin en modo tercera persona.
 */
class ReplayGhostRenderer(
    private val plugin: EvidexPlugin,
    private val platform: Platform<World, Player, ItemStack, Plugin>,
    private val viewer: Player,
    private var world: World,
    private val recordedPlayerName: String,
    recordedPlayerUuid: UUID?
) {
    private var npc: Npc<World, Player, ItemStack, Plugin>? = null
    private var spawning = false
    private val profileUuid = recordedPlayerUuid
        ?: org.bukkit.Bukkit.getOfflinePlayer(recordedPlayerName).uniqueId

    fun setWorld(newWorld: World) {
        if (world.uid == newWorld.uid) return
        clear()
        world = newWorld
        ensureSpawned()
    }

    fun update(frame: PlayerFrame) {
        val ghost = npc
        if (ghost == null) {
            if (!spawning) ensureSpawned()
            return
        }
        applyFrame(ghost, frame)
    }

    fun clear() {
        npc?.unlink()
        npc = null
        spawning = false
    }

    private fun ensureSpawned() {
        if (npc != null || spawning) return
        spawning = true

        val profile = Profile.resolved(recordedPlayerName, profileUuid)
        val spawnLoc = BukkitLocation(world, 0.0, 0.0, 0.0)

        plugin.server.scheduler.runTask(plugin, Runnable {
            val builder = platform.newNpcBuilder()
                .position(BukkitPlatformUtil.positionFromBukkitLegacy(spawnLoc))
                .profile(profile)

            builder.npcSettings { settings ->
                settings.trackingRule(NpcTrackingRule.onlyExplicitlyIncludedPlayers())
            }
            val built = builder.build()
            built.addIncludedPlayer(viewer)
            built.forceTrackPlayer(viewer)
            npc = built
            spawning = false
        })
    }

    private fun applyFrame(npc: Npc<World, Player, ItemStack, Plugin>, frame: PlayerFrame) {
        if (!npc.tracksPlayer(viewer)) {
            npc.forceTrackPlayer(viewer)
        }

        val yaw = frame.yaw.degrees
        val pitch = frame.pitch.degrees

        val packetApi = PacketEvents.getAPI() ?: return
        val peLocation = Location(
            frame.position.x,
            frame.position.y,
            frame.position.z,
            yaw,
            pitch
        )
        packetApi.playerManager.sendPacketSilently(
            viewer,
            WrapperPlayServerEntityTeleport(npc.entityId(), peLocation, true)
        )

        npc.rotate(yaw, pitch).schedule(viewer)

        val pose = if (frame.isSneaking) EntityPose.CROUCHING else EntityPose.STANDING
        npc.changeMetadata(EntityMetadataFactory.entityPoseMetaFactory(), pose).schedule(viewer)
        npc.changeMetadata(EntityMetadataFactory.sneakingMetaFactory(), frame.isSneaking).schedule(viewer)
    }
}