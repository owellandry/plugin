package com.evidex.event

import com.evidex.math.Angle
import com.evidex.math.Vec3d

sealed interface CameraEvent {
    data class PositionChanged(val position: Vec3d) : CameraEvent
    data class RotationChanged(val yaw: Angle, val pitch: Angle, val roll: Angle) : CameraEvent
    data class RollChanged(val roll: Angle) : CameraEvent
    data object SpectateEntity : CameraEvent
    data object SpectateCamera : CameraEvent
    data object ModeChanged : CameraEvent
}
