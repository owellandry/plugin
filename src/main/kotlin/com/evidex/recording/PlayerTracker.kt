package com.evidex.recording

import com.evidex.state.DeltaState
import com.evidex.state.PlayerState

class PlayerTracker {
    private var lastState: PlayerState? = null

    private val TELEPORT_THRESHOLD = 8.0
    private val CORRECTION_INTERVAL = 100

    private var ticksSinceLastCorrection = 0

    fun reset() {
        lastState = null
        ticksSinceLastCorrection = 0
    }

    fun computeDelta(state: PlayerState): DeltaState {
        val prev = lastState
        lastState = state
        ticksSinceLastCorrection++

        if (prev == null) {
            return DeltaState.ALL
        }

        val needsTeleport = ticksSinceLastCorrection >= CORRECTION_INTERVAL ||
                state.position.distanceTo(prev.position) > TELEPORT_THRESHOLD
        if (needsTeleport) ticksSinceLastCorrection = 0

        return DeltaState(
            needsTeleport = needsTeleport,
            positionChanged = prev.position != state.position,
            rotationChanged = prev.yaw != state.yaw || prev.pitch != state.pitch,
            headYawChanged = prev.headYaw != state.headYaw,
            velocityChanged = prev.velocity != state.velocity,
            equipmentChanged = prev.equipment != state.equipment,
            animationChanged = prev.handSwinging != state.handSwinging,
            sleepingChanged = prev.isSleeping != state.isSleeping,
            vehicleChanged = prev.vehicleId != state.vehicleId
        )
    }
}
