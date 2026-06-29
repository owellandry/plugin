package com.evidex.recording

import com.evidex.EvidexPlugin
import com.evidex.storage.repository.RecordingRepository
import com.evidex.storage.repository.WorldRepository
import org.bukkit.World
import org.bukkit.scheduler.BukkitTask

/**
 * Enriches the initial world snapshot along the recorded path without blocking the server thread.
 */
class PathSnapshotEnricher(
    private val plugin: EvidexPlugin,
    private val worldSnapshotService: WorldSnapshotService,
    private val worldRepository: WorldRepository,
    private val recordingRepository: RecordingRepository
) {
    private val activeTasks = mutableMapOf<Long, BukkitTask>()

    fun schedule(
        recordingId: Long,
        playerName: String,
        world: World,
        frames: List<PlayerFrame>,
        existingWorldFilePath: String?
    ) {
        if (!plugin.configManager.isPathSnapshotEnabled()) return
        if (frames.isEmpty()) return

        val samplePoints = worldSnapshotService.computePathSamplePoints(frames)
        if (samplePoints.size <= 1) return

        activeTasks.remove(recordingId)?.cancel()

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val blockMap = HashMap<Long, WorldBlock>(131072)
            if (!existingWorldFilePath.isNullOrBlank()) {
                try {
                    worldRepository.loadBlocksInto(existingWorldFilePath, blockMap) { x, y, z ->
                        worldSnapshotService.blockKey(x, y, z)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to preload world for path snapshot ($playerName): ${e.message}")
                }
            }

            plugin.server.scheduler.runTask(plugin, Runnable {
                startCaptureLoop(
                    recordingId, playerName, world, frames, samplePoints, blockMap,
                    skipFirst = !existingWorldFilePath.isNullOrBlank()
                )
            })
        })
    }

    private fun startCaptureLoop(
        recordingId: Long,
        playerName: String,
        world: World,
        frames: List<PlayerFrame>,
        samplePoints: List<WorldSnapshotService.BlockCoord>,
        blockMap: HashMap<Long, WorldBlock>,
        skipFirst: Boolean
    ) {
        val radius = plugin.configManager.getRecordingWorldRadius()
        val heightRange = plugin.configManager.getRecordingWorldHeight()
        val tickInterval = plugin.configManager.getPathSnapshotTickInterval().coerceAtLeast(1).toLong()
        val first = frames.first()
        var pointIndex = if (skipFirst) 1 else 0

        lateinit var task: BukkitTask
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (pointIndex >= samplePoints.size) {
                task.cancel()
                activeTasks.remove(recordingId)
                finishAsync(recordingId, playerName, world.name, first, radius, blockMap)
                return@Runnable
            }

            val point = samplePoints[pointIndex]
            pointIndex++

            worldSnapshotService.captureAroundInto(
                blockMap,
                world,
                point.x,
                point.y,
                point.z,
                radius,
                heightRange
            )
        }, tickInterval, tickInterval)

        activeTasks[recordingId] = task
        plugin.logger.info(
            "Scheduled path snapshot for $playerName: ${samplePoints.size - pointIndex} sample points " +
                "(every ${tickInterval} tick(s), non-blocking)"
        )
    }

    private fun finishAsync(
        recordingId: Long,
        playerName: String,
        worldName: String,
        first: PlayerFrame,
        radius: Int,
        blockMap: HashMap<Long, WorldBlock>
    ) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val snapshot = worldSnapshotService.buildSnapshotFromMap(
                    worldName = worldName,
                    centerX = first.position.x.toInt(),
                    centerY = first.position.y.toInt(),
                    centerZ = first.position.z.toInt(),
                    radius = radius,
                    blockMap = blockMap
                )
                val worldPath = worldRepository.writeSnapshot(recordingId, snapshot)
                val metadata = recordingRepository.findById(recordingId)
                if (metadata != null) {
                    recordingRepository.update(metadata.copy(worldFilePath = worldPath))
                }
                plugin.logger.info(
                    "World path snapshot for $playerName: ${snapshot.blocks.size} blocks (async complete)"
                )
            } catch (e: Exception) {
                plugin.logger.warning("Failed async path snapshot for $playerName: ${e.message}")
            }
        })
    }

    fun cancelAll() {
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()
    }
}