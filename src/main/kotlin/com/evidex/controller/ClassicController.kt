package com.evidex.controller

import com.evidex.math.Angle
import com.evidex.math.Vec3d

class ClassicController(
    private val state: ControllerState
) : CameraController {

    private val speed = 5.0
    private val sensitivity = 0.5f

    var forward: Boolean = false
    var backward: Boolean = false
    var left: Boolean = false
    var right: Boolean = false
    var up: Boolean = false
    var down: Boolean = false
    var yawDelta: Float = 0f
    var pitchDelta: Float = 0f
    var sprinting: Boolean = false

    override fun update(deltaTicks: Float) {
        val multiplier = if (sprinting) 2.0 else 1.0
        val step = speed * multiplier * deltaTicks.toDouble() / 20.0

        val yawRad = state.yaw.radians
        val forwardVec = Vec3d(
            -kotlin.math.sin(yawRad).toDouble() * step,
            0.0,
            kotlin.math.cos(yawRad).toDouble() * step
        )
        val rightVec = Vec3d(
            kotlin.math.cos(yawRad).toDouble() * step,
            0.0,
            kotlin.math.sin(yawRad).toDouble() * step
        )

        var dx = 0.0
        var dy = 0.0
        var dz = 0.0

        if (forward) { dx += forwardVec.x; dz += forwardVec.z }
        if (backward) { dx -= forwardVec.x; dz -= forwardVec.z }
        if (left) { dx -= rightVec.x; dz -= rightVec.z }
        if (right) { dx += rightVec.x; dz += rightVec.z }
        if (up) dy += step
        if (down) dy -= step

        state.position = Vec3d(
            state.position.x + dx,
            state.position.y + dy,
            state.position.z + dz
        )

        state.yaw = Angle(Angle.wrapDegrees(state.yaw.degrees + yawDelta * sensitivity))
        state.pitch = Angle(
            (state.pitch.degrees + pitchDelta * sensitivity).coerceIn(-90f, 90f)
        )

        yawDelta = 0f
        pitchDelta = 0f
    }

    override fun reset() {
        forward = false
        backward = false
        left = false
        right = false
        up = false
        down = false
        yawDelta = 0f
        pitchDelta = 0f
        sprinting = false
    }
}
