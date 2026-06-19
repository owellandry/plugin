package com.evidex.state

import com.evidex.math.Angle
import com.evidex.math.Vec3d

data class PlayerState(
    val position: Vec3d = Vec3d.ZERO,
    val yaw: Angle = Angle(0f),
    val pitch: Angle = Angle(0f),
    val headYaw: Angle = Angle(0f),
    val velocity: Vec3d = Vec3d.ZERO,
    val onGround: Boolean = false,
    val handSwinging: Boolean = false,
    val handSwingTicks: Int = 0,
    val isUsingItem: Boolean = false,
    val isSleeping: Boolean = false,
    val activeHand: Int = 0,
    val vehicleId: Int = -1,
    val equipment: EquipmentSnapshot = EquipmentSnapshot.EMPTY
)

data class EquipmentSnapshot(
    val head: ItemStackRef? = null,
    val chest: ItemStackRef? = null,
    val legs: ItemStackRef? = null,
    val feet: ItemStackRef? = null,
    val mainHand: ItemStackRef? = null,
    val offHand: ItemStackRef? = null
) {
    companion object {
        val EMPTY = EquipmentSnapshot()
    }
}

data class ItemStackRef(
    val itemId: String,
    val count: Int,
    val components: Map<String, Any> = emptyMap()
)

data class DeltaState(
    val needsTeleport: Boolean = false,
    val positionChanged: Boolean = false,
    val rotationChanged: Boolean = false,
    val headYawChanged: Boolean = false,
    val velocityChanged: Boolean = false,
    val equipmentChanged: Boolean = false,
    val animationChanged: Boolean = false,
    val sleepingChanged: Boolean = false,
    val vehicleChanged: Boolean = false
) {
    val hasAnyChange: Boolean
        get() = needsTeleport || positionChanged || rotationChanged || headYawChanged ||
                velocityChanged || equipmentChanged || animationChanged ||
                sleepingChanged || vehicleChanged

    companion object {
        val NONE = DeltaState()
        val ALL = DeltaState(
            needsTeleport = true, positionChanged = true, rotationChanged = true,
            headYawChanged = true, velocityChanged = true, equipmentChanged = true,
            animationChanged = true, sleepingChanged = true, vehicleChanged = true
        )
    }
}
