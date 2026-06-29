package com.evidex.recording

import com.evidex.EvidexPlugin
import com.evidex.storage.repository.RecordingRepository
import com.evidex.storage.repository.WorldRepository
import org.bukkit.World
import org.bukkit.scheduler.BukkitTask

/**
 * Captures the initial world snapshot in slices so /evidex record does not freeze the server.
 */
class InitialWorldSnapshotTask(
    private val plugin: EvidexPlugin,
    private val worldSnapshotService: WorldSnapshotService,
    private val worldRepository: WorldRepository,
    private val recordingRepository: RecordingRepository,
    private val recordingId: Long,
    private val playerName: String,
    private val world: World,
    private val centerX: Int,
    private val centerY: Int,
    private val centerZ: Int
) {
    private var task: BukkitTask? = null
    private val blockMap = HashMap<Long, WorldBlock>(131072)
    private var sliceIndex = 0

    fun start() {
        val radius = plugin.configManager.getRecordingWorldRadius()
        val heightRange = plugin.configManager.getRecordingWorldHeight()
        val slices = plugin.configManager.getWorldSnapshotSlices().coerceIn(4, 32)
        val tickInterval = plugin.configManager.getWorldSnapshotSliceInterval().coerceAtLeast(1).toLong()

        val dzRanges = buildDzRanges(radius, slices)
        if (dzRanges.isEmpty()) return

        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (sliceIndex >= dzRanges.size) {
                task?.cancel()
                task = null
                finish(radius)
                return@Runnable
            }

            val (dzStart, dzEnd) = dzRanges[sliceIndex]
            sliceIndex++

            worldSnapshotService.captureAroundSlice(
                blockMap, world, centerX, centerY, centerZ, radius, heightRange, dzStart, dzEnd
            )
        }, 1L, tickInterval)
    }

    private fun finish(radius: Int) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val snapshot = worldSnapshotService.buildSnapshotFromMap(
                    worldName = world.name,
                    centerX = centerX,
                    centerY = centerY,
                    centerZ = centerZ,
                    radius = radius,
                    blockMap = blockMap
                )
                val worldPath = worldRepository.writeSnapshot(recordingId, snapshot)
                recordingRepository.updateWorldFilePath(recordingId, worldPath)
                plugin.log.info(
                    "Snapshot inicial: $playerName — ${snapshot.blocks.size} bloques (radio $radius)"
                )
            } catch (e: Exception) {
                plugin.log.warn("Snapshot inicial fallido ($playerName): ${e.message}")
            }
        })
    }

    fun cancel() {
        task?.cancel()
        task = null
    }

    private fun buildDzRanges(radius: Int, slices: Int): List<Pair<Int, Int>> {
        val total = radius * 2 + 1
        val chunk = (total + slices - 1) / slices
        val ranges = mutableListOf<Pair<Int, Int>>()
        var dz = -radius
        while (dz <= radius) {
            val end = (dz + chunk - 1).coerceAtMost(radius)
            ranges.add(dz to end)
            dz = end + 1
        }
        return ranges
    }
}