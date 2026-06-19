package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.RecordingData
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask

class ReplaySession(
    private val plugin: EvidexPlugin,
    val viewer: Player,
    val recording: RecordingData
) {
    private var task: BukkitTask? = null
    private var currentFrameIndex = 0
    private var isPlaying = false
    private var originalGameMode: GameMode = viewer.gameMode
    private var originalAllowFlight: Boolean = viewer.allowFlight
    private var originalFlying: Boolean = viewer.isFlying

    fun start() {
        if (recording.frames.isEmpty()) {
            viewer.sendMessage("§6[Evidex] §cRecording has no frames")
            return
        }

        originalGameMode = viewer.gameMode
        originalAllowFlight = viewer.allowFlight
        originalFlying = viewer.isFlying

        viewer.gameMode = GameMode.SPECTATOR
        viewer.allowFlight = true

        val firstFrame = recording.frames.first()
        viewer.teleport(org.bukkit.Location(
            viewer.world,
            firstFrame.position.x,
            firstFrame.position.y + 1.62,
            firstFrame.position.z,
            firstFrame.yaw.degrees,
            firstFrame.pitch.degrees
        ))

        isPlaying = true
        currentFrameIndex = 1
        startTimestamp = System.currentTimeMillis()

        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            tick()
        }, 1L, 1L)

        viewer.sendMessage("§6[Evidex] §aReplay started - ${recording.frames.size} frames, ${recording.duration}ms")
        viewer.sendMessage("§6[Evidex] §eUse /evidex stop replay to stop")
    }

    private var startTimestamp = 0L

    private fun tick() {
        if (!isPlaying) return
        if (currentFrameIndex >= recording.frames.size) {
            stop()
            viewer.sendMessage("§6[Evidex] §aReplay finished")
            return
        }

        val frame = recording.frames[currentFrameIndex]
        val elapsed = System.currentTimeMillis() - startTimestamp

        if (elapsed >= frame.timestamp) {
            viewer.teleport(org.bukkit.Location(
                viewer.world,
                frame.position.x,
                frame.position.y + 1.62,
                frame.position.z,
                frame.yaw.degrees,
                frame.pitch.degrees
            ))
            currentFrameIndex++
        }
    }

    fun stop() {
        isPlaying = false
        task?.cancel()
        task = null

        viewer.gameMode = originalGameMode
        viewer.allowFlight = originalAllowFlight
        viewer.isFlying = originalFlying
    }
}
