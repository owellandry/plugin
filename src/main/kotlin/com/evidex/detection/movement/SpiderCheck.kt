package com.evidex.detection.movement

import com.evidex.config.ConfigManager
import com.evidex.util.BukkitExtensions
import com.evidex.detection.DetectionCheck
import com.evidex.detection.PlayerProfile
import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationResult
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player

class SpiderCheck(private val config: ConfigManager) : DetectionCheck {

    override val name = "spider"
    override val category = ViolationCategory.MOVEMENT

    private val horizontalFaces = listOf(
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    )

    override fun check(player: Player, profile: PlayerProfile): ViolationResult? {
        if (!config.isCheckEnabled(name)) return null
        if (player.gameMode == GameMode.CREATIVE || player.isFlying || player.isClimbing) return null
        if (player.isInWater || player.isSwimming) return null
        // En el suelo, subir = saltar/escalón, no spider. Spider sube por una pared en el aire.
        if (BukkitExtensions.isOnGround(player)) return null

        val from = profile.lastLocation ?: return null
        val to = player.location
        val deltaY = to.y - from.y
        if (deltaY <= 0.1) return null

        val against = player.location.block
        val climbingBlock = against.type == Material.LADDER ||
            against.type == Material.VINE ||
            against.type.name.contains("SCAFFOLDING")

        if (climbingBlock) return null

        // Spider real necesita una pared sólida adyacente para "trepar".
        // Sin pared, subir lento en el aire es un salto normal -> no flag.
        if (!touchingWall(player)) return null

        val horizontal = profile.horizontalSpeed(from, to)
        if (horizontal > 0.15) return null

        return ViolationResult(
            checkName = name,
            category = category,
            vl = config.getCheckVlAdd(name, 3),
            info = mapOf("deltaY" to String.format("%.2f", deltaY))
        )
    }

    /** True si hay un bloque sólido pegado horizontalmente a los pies o la cabeza. */
    private fun touchingWall(player: Player): Boolean {
        val feet = player.location.block
        val head = feet.getRelative(BlockFace.UP)
        for (block in listOf(feet, head)) {
            for (face in horizontalFaces) {
                if (block.getRelative(face).type.isSolid) return true
            }
        }
        return false
    }
}
