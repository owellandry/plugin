package com.evidex.recording

import com.evidex.config.ConfigManager
import com.evidex.math.Angle
import com.evidex.math.Vec3d
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector

object EntityCapture {

    fun capture(
        recordedPlayer: Player,
        config: ConfigManager
    ): List<NearbyEntityFrame> {
        val radius = config.getEntityCaptureRadius().toDouble()
        val maxEntities = config.getMaxEntitiesPerFrame()
        val origin = recordedPlayer.eyeLocation
        val candidates = mutableListOf<Pair<Double, NearbyEntityFrame>>()

        if (config.isCapturePlayersEnabled()) {
            for (other in recordedPlayer.world.players) {
                if (other.uniqueId == recordedPlayer.uniqueId) continue
                val distance = horizontalDistance(origin, other.location)
                if (distance > radius) continue
                val frame = fromPlayer(other)
                if (config.isCaptureVisibleOnly() && !isInViewCone(recordedPlayer, other.location, config.getCaptureFovDegrees())) {
                    continue
                }
                candidates.add(distance to frame)
            }
        }

        if (config.isCaptureMobsEnabled() || config.isCaptureItemsEnabled()) {
            val nearby = recordedPlayer.world.getNearbyEntities(origin, radius, radius, radius)
            for (entity in nearby) {
                if (entity is Player) continue
                if (!entity.isValid) continue

                val distance = origin.distance(entity.location)
                if (distance > radius) continue

                val frame = when {
                    config.isCaptureMobsEnabled() && entity is LivingEntity && !entity.isDead ->
                        fromLivingEntity(entity)
                    config.isCaptureItemsEnabled() && isDroppedItem(entity) ->
                        fromGenericEntity(entity)
                    else -> null
                } ?: continue

                if (config.isCaptureVisibleOnly() && !isInViewCone(recordedPlayer, entity.location, config.getCaptureFovDegrees())) {
                    continue
                }
                candidates.add(distance to frame)
            }
        }

        return candidates
            .sortedBy { it.first }
            .take(maxEntities)
            .map { it.second }
    }

    private fun isDroppedItem(entity: Entity): Boolean {
        val typeName = entity.type.name
        return typeName == "ITEM" || typeName.endsWith("_ITEM")
    }

    private fun horizontalDistance(origin: org.bukkit.Location, target: org.bukkit.Location): Double {
        if (origin.world?.uid != target.world?.uid) return Double.MAX_VALUE
        val dx = target.x - origin.x
        val dz = target.z - origin.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    private fun isInViewCone(
        viewer: Player,
        target: org.bukkit.Location,
        fovDegrees: Double
    ): Boolean {
        val eye = viewer.eyeLocation
        val toTarget = Vector(
            target.x - eye.x,
            target.y - eye.y,
            target.z - eye.z
        )
        if (toTarget.lengthSquared() < 0.01) return true
        toTarget.normalize()

        val look = eye.direction.clone().normalize()
        val dot = look.dot(toTarget).coerceIn(-1.0, 1.0)
        val angle = Math.toDegrees(kotlin.math.acos(dot))
        return angle <= fovDegrees / 2.0
    }

    private fun fromPlayer(player: Player): NearbyEntityFrame {
        val loc = player.location
        return NearbyEntityFrame(
            entityType = "PLAYER",
            name = player.name,
            playerUuid = player.uniqueId.toString(),
            entityUuid = player.uniqueId.toString(),
            position = Vec3d(loc.x, loc.y, loc.z),
            yaw = Angle(loc.yaw),
            pitch = Angle(loc.pitch),
            isSneaking = player.isSneaking,
            isBaby = false
        )
    }

    private fun fromLivingEntity(entity: LivingEntity): NearbyEntityFrame {
        val loc = entity.location
        val displayName = entity.customName()?.let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        } ?: entity.type.name
        return NearbyEntityFrame(
            entityType = entity.type.name,
            name = displayName,
            playerUuid = null,
            entityUuid = entity.uniqueId.toString(),
            position = Vec3d(loc.x, loc.y, loc.z),
            yaw = Angle(loc.yaw),
            pitch = Angle(loc.pitch),
            isSneaking = false,
            isBaby = entity is org.bukkit.entity.Ageable && !entity.isAdult
        )
    }

    private fun fromGenericEntity(entity: Entity): NearbyEntityFrame {
        val loc = entity.location
        return NearbyEntityFrame(
            entityType = entity.type.name,
            name = entity.type.name,
            entityUuid = entity.uniqueId.toString(),
            position = Vec3d(loc.x, loc.y, loc.z),
            yaw = Angle(loc.yaw),
            pitch = Angle(loc.pitch)
        )
    }
}