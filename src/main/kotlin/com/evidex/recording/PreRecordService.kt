package com.evidex.recording

import com.evidex.EvidexPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap

class PreRecordService(private val plugin: EvidexPlugin) {

    private val buffers = ConcurrentHashMap<String, PreRecordBuffer>()
    private var task: BukkitTask? = null

    fun start() {
        if (!plugin.configManager.isPreBufferEnabled()) return
        val interval = plugin.configManager.getPreBufferTickInterval().coerceAtLeast(1).toLong()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { sampleAll() }, interval, interval)
        plugin.log.debug(
            "Pre-buffer activo: ${plugin.configManager.getPreBufferSeconds()}s " +
                "(intervalo $interval ticks)"
        )
    }

    fun stop() {
        task?.cancel()
        task = null
        buffers.clear()
    }

    fun takeBuffer(playerName: String): List<PlayerFrame> {
        val buffer = buffers[playerName] ?: return emptyList()
        val snapshot = buffer.snapshot()
        buffer.clear()
        if (snapshot.isEmpty()) return emptyList()
        return rebaseForRecording(snapshot)
    }

    private fun rebaseForRecording(frames: List<PlayerFrame>): List<PlayerFrame> {
        val ordered = frames.sortedBy { it.timestamp }
        val base = ordered.first().timestamp
        return ordered.map { frame ->
            frame.copy(timestamp = frame.timestamp - base)
        }
    }

    private fun sampleAll() {
        if (!plugin.configManager.isPreBufferEnabled()) return
        if (!plugin.hasDetection()) return

        val now = System.currentTimeMillis()
        for (player in Bukkit.getOnlinePlayers()) {
            if (!plugin.detectionManager.canCheck(player)) {
                buffers.remove(player.name)
                continue
            }
            if (plugin.recordingManager.isRecording(player)) continue

            val frame = FrameCapture.capture(plugin, player, now)
            val buffer = buffers.computeIfAbsent(player.name) {
                PreRecordBuffer(plugin.configManager.getPreBufferSeconds() * 1000L)
            }
            buffer.add(frame)
        }
    }
}