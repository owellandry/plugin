package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.WorldBlock
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.scheduler.BukkitTask

/**
 * Aplica el snapshot de bloques grabado sobre el mundo vivo y restaura al terminar el replay.
 */
class ReplayWorldOverlay(
    private val plugin: EvidexPlugin,
    private val world: World,
    private val blocks: List<WorldBlock>,
    private val blocksPerTick: Int
) {
    private val originals = mutableMapOf<Long, Material>()
    private var task: BukkitTask? = null
    private var cursor = 0

    fun isEmpty(): Boolean = blocks.isEmpty()

    fun begin(onReady: () -> Unit) {
        if (blocks.isEmpty()) {
            onReady()
            return
        }
        cursor = 0
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val end = (cursor + blocksPerTick).coerceAtMost(blocks.size)
            for (i in cursor until end) {
                applyBlock(blocks[i])
            }
            cursor = end
            if (cursor >= blocks.size) {
                task?.cancel()
                task = null
                onReady()
            }
        }, 1L, 1L)
    }

    fun restore() {
        task?.cancel()
        task = null
        originals.forEach { (key, material) ->
            val block = world.getBlockAt(
                ReplayBlockKeys.unpackX(key),
                ReplayBlockKeys.unpackY(key),
                ReplayBlockKeys.unpackZ(key)
            )
            block.type = material
        }
        originals.clear()
    }

    private fun applyBlock(snapshot: WorldBlock) {
        val material = runCatching { Material.valueOf(snapshot.material) }.getOrNull() ?: return
        val block = world.getBlockAt(snapshot.x, snapshot.y, snapshot.z)
        val key = ReplayBlockKeys.pack(snapshot.x, snapshot.y, snapshot.z)
        originals.putIfAbsent(key, block.type)
        block.type = material
    }
}