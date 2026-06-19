package com.evidex.controller

import com.evidex.math.Angle
import com.evidex.math.Vec3d

class SpectatorController(
    private val state: ControllerState
) : CameraController {

    var targetPosition: Vec3d = Vec3d.ZERO
    var targetYaw: Angle = Angle(0f)
    var targetPitch: Angle = Angle(0f)

    override fun update(deltaTicks: Float) {
        state.position = targetPosition
        state.yaw = targetYaw
        state.pitch = targetPitch
    }

    override fun reset() {
        targetPosition = Vec3d.ZERO
        targetYaw = Angle(0f)
        targetPitch = Angle(0f)
    }
}
