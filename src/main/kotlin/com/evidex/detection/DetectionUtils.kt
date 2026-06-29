package com.evidex.detection

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import kotlin.math.acos
import kotlin.math.sqrt

object DetectionUtils {

    private val VALUABLE_ORES = setOf(
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.ANCIENT_DEBRIS, Material.NETHER_GOLD_ORE,
        Material.NETHER_QUARTZ_ORE
    )

    fun angleToLocation(eye: Location, target: Location): Double {
        val dir = eye.direction.clone().normalize()
        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 0.01) return 0.0
        val dot = (dir.x * dx / len + dir.y * dy / len + dir.z * dz / len).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(dot))
    }

    fun angleToEntity(player: Player, entity: Entity): Double {
        val target = entity.location.add(0.0, entity.height * 0.5, 0.0)
        return angleToLocation(player.eyeLocation, target)
    }

    fun isValuableOre(material: Material): Boolean = material in VALUABLE_ORES

    fun isBlockExposed(block: Block): Boolean {
        for (face in BlockFace.values()) {
            if (face == BlockFace.SELF) continue
            val relative = block.getRelative(face)
            val type = relative.type
            if (!type.isSolid || type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                return true
            }
        }
        return false
    }

    fun isOverLiquid(player: Player): Boolean {
        val loc = player.location
        val below = loc.clone().subtract(0.0, 0.1, 0.0).block
        return below.type == Material.WATER || below.type == Material.LAVA
    }

    fun countSolidBlocksBetween(player: Player, target: Entity): Int {
        val eye = player.eyeLocation
        val targetCenter = target.location.add(0.0, target.height * 0.5, 0.0)
        val dir = targetCenter.toVector().subtract(eye.toVector())
        val distance = dir.length()
        if (distance < 0.5) return 0
        dir.normalize()

        var count = 0
        var traveled = 0.5
        while (traveled < distance - 0.5) {
            val sample = eye.clone().add(dir.clone().multiply(traveled))
            val block = sample.block
            if (block.type.isSolid && block.type != Material.AIR) count++
            traveled += 0.5
        }
        return count
    }
}