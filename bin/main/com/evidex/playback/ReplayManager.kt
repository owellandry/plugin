package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.RecordingData
import com.evidex.storage.repository.WorldRepository
import org.bukkit.entity.Player

class ReplayManager(
    private val plugin: EvidexPlugin,
    private val worldRepository: WorldRepository
) {

    private val sessions = mutableMapOf<String, ReplaySession>()

    fun startReplay(viewer: Player, recording: RecordingData) {
        stopReplay(viewer)
        val session = ReplaySession(plugin, worldRepository, viewer, recording)
        sessions[viewer.name] = session
        session.start()
    }

    fun stopReplay(viewer: Player) {
        val session = sessions.remove(viewer.name) ?: return
        session.stop()
    }

    fun getSession(viewer: Player): ReplaySession? = sessions[viewer.name]

    fun getSession(viewerName: String): ReplaySession? = sessions[viewerName]

    fun pause(viewer: Player) = getSession(viewer)?.pause()

    fun resume(viewer: Player) = getSession(viewer)?.resume()

    fun setSpeed(viewer: Player, speed: Double) = getSession(viewer)?.setSpeed(speed)

    fun skipToNextFlag(viewer: Player) = getSession(viewer)?.skipToNextFlag()

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.clear()
    }

    fun getActiveSessions(): Collection<ReplaySession> = sessions.values
}