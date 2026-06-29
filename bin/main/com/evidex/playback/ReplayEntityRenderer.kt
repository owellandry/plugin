package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.config.ConfigManager
import com.evidex.recording.NearbyEntityFrame
import org.bukkit.World
import org.bukkit.entity.Player

class ReplayEntityRenderer(
    plugin: EvidexPlugin,
    viewer: Player,
    world: World,
    private val config: ConfigManager,
    private val entityIsolation: ReplayEntityIsolation? = null
) {
    private val serverEntityRenderer = ReplayServerEntityRenderer(plugin, viewer, world)

    fun update(entities: List<NearbyEntityFrame>) {
        if (config.isReplayShowPlayerNpcs() || config.isReplayShowMobMarkers()) {
            serverEntityRenderer.update(entities)
        } else {
            serverEntityRenderer.clear()
        }
    }

    fun clear() {
        serverEntityRenderer.clear()
    }
}