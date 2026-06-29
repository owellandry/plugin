package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.util.EvidexLog
import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder

object PacketEventsService {

    private var loaded = false
    private var initialized = false

    fun load(plugin: EvidexPlugin, log: EvidexLog) {
        if (loaded) return
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin))
        PacketEvents.getAPI().load()
        loaded = true
        log.debug("PacketEvents cargado")
    }

    fun init(plugin: EvidexPlugin, log: EvidexLog) {
        if (!loaded) load(plugin, log)
        if (initialized) return
        PacketEvents.getAPI().init()
        initialized = true
        log.debug("PacketEvents iniciado")
    }

    fun shutdown() {
        if (!initialized && !loaded) return
        try {
            PacketEvents.getAPI()?.terminate()
        } catch (_: Exception) {
            // ya terminado
        }
        initialized = false
        loaded = false
    }

    fun isReady(): Boolean = initialized && PacketEvents.getAPI() != null
}