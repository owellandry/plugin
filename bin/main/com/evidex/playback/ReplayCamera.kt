package com.evidex.playback

import com.evidex.recording.PlayerFrame
import org.bukkit.Location
import org.bukkit.World

/** Posición de cámara en primera persona (pies en el suelo; el cliente añade ~1.62 bloques a los ojos). */
object ReplayCamera {

    fun firstPersonLocation(frame: PlayerFrame, world: World): Location =
        Location(
            world,
            frame.position.x,
            frame.position.y,
            frame.position.z,
            frame.yaw.degrees,
            frame.pitch.degrees
        )
}