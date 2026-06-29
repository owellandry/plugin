package com.evidex.playback

import com.evidex.recording.BlockChange
import org.bukkit.Material
import org.bukkit.World

/**
 * Reaplica cambios de bloques grabados durante la sesión en el momento correcto del replay.
 * Corrige bloques del mundo vivo que quedaron visibles desde el frame 0.
 */
class ReplayBlockTimeline(
    private val world: World,
    private val snapshotMaterials: Map<Long, Material>,
    private val changes: List<BlockChange>
) {
    private val originals = mutableMapOf<Long, Material>()
    private val affectedKeys = changes.map { ReplayBlockKeys.pack(it.x, it.y, it.z) }.toSet()
    private var changeIndex = 0

    fun hasChanges(): Boolean = changes.isNotEmpty()

    fun initializeBaseState() {
        changeIndex = 0
        for (key in affectedKeys) {
            setBlock(key, baseMaterial(key))
        }
    }

    fun applyUpTo(playheadMs: Long) {
        while (changeIndex < changes.size && changes[changeIndex].timestamp <= playheadMs) {
            val change = changes[changeIndex]
            val key = ReplayBlockKeys.pack(change.x, change.y, change.z)
            val material = runCatching { Material.valueOf(change.material) }.getOrNull() ?: Material.AIR
            setBlock(key, material)
            changeIndex++
        }
    }

    fun reapplyUpTo(playheadMs: Long) {
        for (key in affectedKeys) {
            setBlock(key, baseMaterial(key))
        }
        changeIndex = 0
        applyUpTo(playheadMs)
    }

    fun restore() {
        originals.forEach { (key, material) ->
            val block = world.getBlockAt(
                ReplayBlockKeys.unpackX(key),
                ReplayBlockKeys.unpackY(key),
                ReplayBlockKeys.unpackZ(key)
            )
            block.type = material
        }
        originals.clear()
        changeIndex = 0
    }

    private fun baseMaterial(key: Long): Material = snapshotMaterials[key] ?: Material.AIR

    private fun setBlock(key: Long, material: Material) {
        val block = world.getBlockAt(
            ReplayBlockKeys.unpackX(key),
            ReplayBlockKeys.unpackY(key),
            ReplayBlockKeys.unpackZ(key)
        )
        originals.putIfAbsent(key, block.type)
        block.type = material
    }
}