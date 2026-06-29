package com.evidex.service

import com.evidex.EvidexPlugin
import com.evidex.config.ConfigManager
import com.evidex.storage.repository.BlockChangeRepository
import com.evidex.storage.repository.FrameRepository
import com.evidex.storage.repository.RecordingRepository
import com.evidex.storage.repository.WorldRepository

class CleanupService(
    private val plugin: EvidexPlugin,
    private val recordingRepository: RecordingRepository,
    private val frameRepository: FrameRepository,
    private val worldRepository: WorldRepository,
    private val blockChangeRepository: BlockChangeRepository,
    private val config: ConfigManager
) {
    private var task: org.bukkit.scheduler.BukkitTask? = null

    fun start() {
        if (!config.isCleanupEnabled()) {
            plugin.log.debug("Limpieza automática desactivada")
            return
        }

        val interval = config.getCleanupInterval() * 20L
        task = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            runCleanup()
        }, interval, interval)

        plugin.log.debug(
            "Limpieza automática activa (retención: ${config.getCleanupRetentionDays()} días)"
        )
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
                    worldRepository.deleteFile(metadata.worldFilePath)
                    blockChangeRepository.deleteFile(metadata.id)
                } catch (e: Exception) {
                    plugin.log.warn("No se pudieron borrar archivos de ${metadata.id}: ${e.message}")
                }
            }

            if (expired.isNotEmpty()) {
                plugin.log.info("Limpieza: ${expired.size} grabación(es) expirada(s) eliminada(s)")
            }
        } catch (e: Exception) {
            plugin.log.warn("Error en limpieza automática", e)
        }
    }
}