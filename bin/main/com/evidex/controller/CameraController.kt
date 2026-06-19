package com.evidex.controller

import com.evidex.math.Angle
import com.evidex.math.Vec3d

sealed interface CameraController {
    fun update(deltaTicks: Float)
    fun reset()
}

open class ControllerState(
    var position: Vec3d = Vec3d.ZERO,
    var yaw: Angle = Angle(0f),
    var pitch: Angle = Angle(0f),
    var roll: Angle = Angle(0f)
)
