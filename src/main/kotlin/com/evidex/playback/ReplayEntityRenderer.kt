package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.config.ConfigManager
import com.evidex.recording.NearbyEntityFrame
import com.evidex.recording.NearbyEntityKind
import com.github.juliarn.npclib.api.Platform
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

class ReplayEntityRenderer(
    plugin: EvidexPlugin,
    platform: Platform<World, Player, ItemStack, Plugin>,
    viewer: Player,
    world: World,
    private val config: ConfigManager
) {
    private val playerRenderer = ReplayNpcRenderer(plugin, platform, viewer, world)
    private val mobRenderer = ReplayMobMarkerRenderer(plugin, viewer, world)

    fun setWorld(newWorld: World) {
        playerRenderer.setWorld(newWorld)
        mobRenderer.setWorld(newWorld)
    }

    fun update(entities: List<NearbyEntityFrame>) {
        val players = entities.filter { NearbyEntityKind.from(it.entityType) == NearbyEntityKind.PLAYER }
        val mobsAndItems = entities.filter { NearbyEntityKind.from(it.entityType) != NearbyEntityKind.PLAYER }

        if (config.isReplayShowPlayerNpcs()) {
            playerRenderer.update(players)
        } else {
            playerRenderer.clear()
        }

        if (config.isReplayShowMobMarkers()) {
            mobRenderer.update(mobsAndItems)
        } else {
            mobRenderer.clear()
        }
    }

    fun clear() {
        playerRenderer.clear()
        mobRenderer.clear()
    }
}