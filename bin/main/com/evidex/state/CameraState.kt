package com.evidex.state

import com.evidex.math.Angle
import com.evidex.math.Vec3d

data class CameraState(
    val position: Vec3d = Vec3d.ZERO,
    val yaw: Angle = Angle(0f),
    val pitch: Angle = Angle(0f),
    val roll: Angle = Angle(0f)
) {
    fun lerp(target: CameraState, alpha: Float): CameraState {
        val a = alpha.toDouble().coerceIn(0.0, 1.0)
        return CameraState(
            position = position.lerp(target.position, a),
            yaw = yaw.lerp(target.yaw, alpha),
            pitch = pitch.lerp(target.pitch, alpha),
            roll = roll.lerp(target.roll, alpha)
        )
    }

    fun withYaw(yaw: Angle) = copy(yaw = yaw)
    fun withPitch(pitch: Angle) = copy(pitch = pitch)
    fun withRoll(roll: Angle) = copy(roll = roll)
    fun withPosition(position: Vec3d) = copy(position = position)

    companion object {
        val ZERO = CameraState()
    }
}
