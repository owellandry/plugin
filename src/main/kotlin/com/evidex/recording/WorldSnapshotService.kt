package com.evidex.recording

import com.evidex.config.ConfigManager
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import kotlin.math.hypot

class WorldSnapshotService(private val config: ConfigManager) {

    data class BlockCoord(val x: Int, val y: Int, val z: Int)

    fun capture(player: Player): WorldSnapshot {
        val loc = player.location
        return captureAround(
            player.world,
            loc.blockX,
            loc.blockY,
            loc.blockZ,
            config.getRecordingWorldRadius(),
            config.getRecordingWorldHeight()
        )
    }

    fun captureAround(
        world: World,
        centerX: Int,
        centerY: Int,
        centerZ: Int,
        radius: Int = config.getRecordingWorldRadius(),
        heightRange: Int = config.getRecordingWorldHeight()
    ): WorldSnapshot {
        val blockMap = HashMap<Long, WorldBlock>(estimateBlockCapacity(radius, heightRange))
        captureAroundInto(blockMap, world, centerX, centerY, centerZ, radius, heightRange)
        return WorldSnapshot(
            worldName = world.name,
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            radius = radius,
            blocks = blockMap.values.toList()
        )
    }

    fun captureAroundInto(
        blockMap: MutableMap<Long, WorldBlock>,
        world: World,
        centerX: Int,
        centerY: Int,
        centerZ: Int,
        radius: Int = config.getRecordingWorldRadius(),
        heightRange: Int = config.getRecordingWorldHeight()
    ) {
        captureAroundSlice(blockMap, world, centerX, centerY, centerZ, radius, heightRange, -radius, radius)
    }

    /**
     * Captures a horizontal slice (dz range) of the world snapshot. Used to spread work across ticks.
     */
    fun captureAroundSlice(
        blockMap: MutableMap<Long, WorldBlock>,
        world: World,
        centerX: Int,
        centerY: Int,
        centerZ: Int,
        radius: Int,
        heightRange: Int,
        dzStart: Int,
        dzEnd: Int
    ) {
        val minY = (centerY - heightRange).coerceAtLeast(world.minHeight)
        val maxY = (centerY + heightRange).coerceAtMost(world.maxHeight - 1)
        val radiusSq = radius * radius

        for (dx in -radius..radius) {
            val dxSq = dx * dx
            if (dxSq > radiusSq) continue
            for (dz in dzStart..dzEnd) {
                if (dz < -radius || dz > radius) continue
                if (dxSq + dz * dz > radiusSq) continue
                val wx = centerX + dx
                val wz = centerZ + dz
                val chunkX = wx shr 4
                val chunkZ = wz shr 4
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.loadChunk(chunkX, chunkZ)
                }
                for (wy in minY..maxY) {
                    val type = world.getBlockAt(wx, wy, wz).type
                    if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) continue
                    blockMap.putIfAbsent(blockKey(wx, wy, wz), WorldBlock(wx, wy, wz, type.name))
                }
            }
        }
    }

    /**
     * Returns capture points along the recorded path. Used for async enrichment after stop.
     */
    fun computePathSamplePoints(frames: List<PlayerFrame>): List<BlockCoord> {
        if (frames.isEmpty()) return emptyList()

        val radius = config.getRecordingWorldRadius()
        val maxSamples = config.getPathSnapshotMaxSamples()
        val sampleStep = (frames.size / maxSamples).coerceAtLeast(1)
        val minDistance = radius.toDouble().coerceAtLeast(16.0)

        val points = mutableListOf<BlockCoord>()
        var lastX = Int.MIN_VALUE
        var lastZ = Int.MIN_VALUE

        frames.forEachIndexed { index, frame ->
            val bx = frame.position.x.toInt()
            val by = frame.position.y.toInt()
            val bz = frame.position.z.toInt()

            val shouldSample = index == 0 ||
                index == frames.lastIndex ||
                index % sampleStep == 0 ||
                lastX == Int.MIN_VALUE ||
                hypot((bx - lastX).toDouble(), (bz - lastZ).toDouble()) >= minDistance

            if (!shouldSample) return@forEachIndexed

            points.add(BlockCoord(bx, by, bz))
            lastX = bx
            lastZ = bz

            if (points.size >= maxSamples) return@forEachIndexed
        }

        return points
    }

    fun buildSnapshotFromMap(
        worldName: String,
        centerX: Int,
        centerY: Int,
        centerZ: Int,
        radius: Int,
        blockMap: Map<Long, WorldBlock>
    ): WorldSnapshot {
        return WorldSnapshot(
            worldName = worldName,
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            radius = radius,
            blocks = blockMap.values.toList()
        )
    }

    fun filterAround(
        snapshot: WorldSnapshot,
        centerX: Int,
        centerY: Int,
        centerZ: Int,
        radius: Int = config.getRecordingWorldRadius(),
        heightRange: Int = config.getRecordingWorldHeight()
    ): WorldSnapshot {
        val radiusSq = radius * radius
        val filtered = snapshot.blocks.filter { block ->
            val dx = block.x - centerX
            val dz = block.z - centerZ
            val dy = kotlin.math.abs(block.y - centerY)
            (dx * dx + dz * dz) <= radiusSq && dy <= heightRange
        }
        return snapshot.copy(
            centerX = centerX,
            centerY = centerY,
            centerZ = centerZ,
            radius = radius,
            blocks = filtered
        )
    }

    fun blockKey(x: Int, y: Int, z: Int): Long {
        return ((x.toLong() and 0x3FFFFFF) shl 38) or
            ((z.toLong() and 0x3FFFFFF) shl 12) or
            (y.toLong() and 0xFFF)
    }

    private fun estimateBlockCapacity(radius: Int, heightRange: Int): Int {
        val area = (Math.PI * radius * radius).toInt()
        return (area * (heightRange * 2 + 1) / 3).coerceAtLeast(4096)
    }
}