package com.evidex.playback

import com.evidex.EvidexPlugin
import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder

object PacketEventsService {

    private var loaded = false
    private var initialized = false

    fun load(plugin: EvidexPlugin) {
        if (loaded) return
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin))
        PacketEvents.getAPI().load()
        loaded = true
        plugin.logger.info("[Evidex] PacketEvents cargado (NPCs y paquetes de replay)")
    }

    fun init(plugin: EvidexPlugin) {
        if (!loaded) load(plugin)
        if (initialized) return
        PacketEvents.getAPI().init()
        initialized = true
        plugin.logger.info("[Evidex] PacketEvents iniciado")
    }

    fun shutdown() {
        if (!initialized && !loaded) return
        try {
            PacketEvents.getAPI()?.terminate()
        } catch (_: Exception) {
            // already terminated
        }
        initialized = false
        loaded = false
    }

    fun isReady(): Boolean = initialized && PacketEvents.getAPI() != null
}