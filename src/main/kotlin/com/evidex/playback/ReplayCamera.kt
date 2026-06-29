package com.evidex.playback

import com.evidex.recording.PlayerFrame
import org.bukkit.Location
import org.bukkit.World
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Posicionamiento de cámara alineado con Minecraft vanilla.
 *
 * Referencia (Java Edition):
 * - Altura del jugador de pie: 1.8 bloques
 * - Altura de ojos de pie: ~1.62 bloques desde los pies
 * - Agachado: ojos ~1.27, hitbox 1.5
 * - Tercera persona (F5): 4 bloques detrás, misma altura que los ojos, sin offset vertical extra
 */
object ReplayCamera {

    /** Distancia por defecto de la cámara F5 (atributo camera_distance en 1.21). */
    const val VANILLA_THIRD_PERSON_DISTANCE = 4.0

    /** Altura de ojos con el jugador de pie (valor estándar de Bukkit/Paper). */
    const val EYE_HEIGHT_STANDING = 1.6215625

    /** Altura de ojos agachado. */
    const val EYE_HEIGHT_SNEAKING = 1.26953125

    /** Altura de ojos nadando / volando con elytra / crawl. */
    const val EYE_HEIGHT_SWIMMING = 0.4

    fun eyeHeight(frame: PlayerFrame): Double {
        return when {
            frame.isSneaking -> EYE_HEIGHT_SNEAKING
            frame.isFlying -> EYE_HEIGHT_STANDING
            else -> EYE_HEIGHT_STANDING
        }
    }

    /** Primera persona: pies en el suelo; el cliente coloca la cámara en los ojos automáticamente. */
    fun firstPersonLocation(frame: PlayerFrame, world: World): Location {
        return Location(
            world,
            frame.position.x,
            frame.position.y,
            frame.position.z,
            frame.yaw.degrees,
            frame.pitch.degrees
        )
    }

    /**
     * Tercera persona estilo F5: cámara detrás del jugador a altura de ojos,
     * mirando hacia la cabeza del grabado (no copia el pitch — evita atravesar techos).
     */
    fun thirdPersonLocation(
        frame: PlayerFrame,
        world: World,
        distance: Double = VANILLA_THIRD_PERSON_DISTANCE,
        heightOffset: Double = 0.0
    ): Location {
        val feetY = frame.position.y
        val eyeY = feetY + eyeHeight(frame)
        val yawRad = Math.toRadians(frame.yaw.degrees.toDouble())

        val camX = frame.position.x - sin(yawRad) * distance
        val camZ = frame.position.z + cos(yawRad) * distance
        val camY = eyeY + heightOffset

        val camera = Location(world, camX, camY, camZ)
        val lookTarget = Location(world, frame.position.x, eyeY, frame.position.z)
        val (yaw, pitch) = lookAt(camera, lookTarget)
        camera.yaw = yaw
        camera.pitch = pitch
        return camera
    }

    /** Calcula yaw/pitch para que [from] mire hacia [to]. */
    fun lookAt(from: Location, to: Location): Pair<Float, Float> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val distXZ = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, distXZ)).toFloat().coerceIn(-90f, 90f)
        return yaw to pitch
    }
}