package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.RecordingData
import org.bukkit.entity.Player

class ReplayManager(private val plugin: EvidexPlugin) {

    private val sessions = mutableMapOf<String, ReplaySession>()

    fun startReplay(viewer: Player, recording: RecordingData) {
        stopReplay(viewer)
        val session = ReplaySession(plugin, viewer, recording)
        sessions[viewer.name] = session
        session.start()
    }

    fun stopReplay(viewer: Player) {
        val session = sessions.remove(viewer.name) ?: return
        session.stop()
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.clear()
    }
}
