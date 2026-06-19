package com.evidex.service

import com.evidex.EvidexPlugin
import com.evidex.config.ConfigManager
import com.evidex.storage.repository.FrameRepository
import com.evidex.storage.repository.RecordingRepository

class CleanupService(
    private val plugin: EvidexPlugin,
    private val recordingRepository: RecordingRepository,
    private val frameRepository: FrameRepository,
    private val config: ConfigManager
) {
    private var task: org.bukkit.scheduler.BukkitTask? = null

    fun start() {
        if (!config.isCleanupEnabled()) {
            plugin.logger.info("Cleanup service disabled")
            return
        }

        val interval = config.getCleanupInterval() * 20L
        task = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            runCleanup()
        }, interval, interval)

        plugin.logger.info("Cleanup service started (retention: ${config.getCleanupRetentionDays()}d, interval: ${config.getCleanupInterval()}s)")
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun runCleanup() {
        try {
            val cutoff = System.currentTimeMillis() - (config.getCleanupRetentionDays() * 86_400_000L)
            val expired = recordingRepository.deleteExpired(cutoff)

            for (metadata in expired) {
                try {
                    frameRepository.deleteFile(metadata.filePath)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to delete frame file: ${metadata.filePath} - ${e.message}")
                }
            }

            if (expired.isNotEmpty()) {
                plugin.logger.info("Cleanup removed ${expired.size} expired recordings")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Cleanup error: ${e.message}")
        }
    }
}
