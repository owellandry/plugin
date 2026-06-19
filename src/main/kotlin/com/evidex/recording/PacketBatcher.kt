package com.evidex.recording

import com.evidex.state.DeltaState
import com.evidex.state.PlayerState

class PacketBatcher {
    private val pendingPackets = mutableListOf<Any>()

    fun reset() {
        pendingPackets.clear()
    }

    fun generatePackets(state: PlayerState, delta: DeltaState): List<Any> {
        pendingPackets.clear()
        if (!delta.hasAnyChange) return emptyList()

        // TODO: Generate actual MC packets based on delta
        // - EntityPositionS2CPacket if needsTeleport
        // - EntityS2CPacket.RotateAndMoveRelative if position/rotation changed
        // - EntitySetHeadYawS2CPacket if headYawChanged
        // - EntityVelocityUpdateS2CPacket if velocityChanged
        // - EntityEquipmentUpdateS2CPacket if equipmentChanged
        // - EntityAnimationS2CPacket if animationChanged
        // - EntityAttachS2CPacket if vehicleChanged

        return pendingPackets.toList()
    }
}
