package com.evidex.recording

import com.evidex.math.Angle
import com.evidex.math.Vec3d

data class PlayerFrame(
    val timestamp: Long,
    val position: Vec3d,
    val yaw: Angle,
    val pitch: Angle,
    val onGround: Boolean,
    val isSneaking: Boolean,
    val isSprinting: Boolean,
    val isFlying: Boolean,
    val handSwing: Boolean,
    val equipment: EquipmentFrame
)

data class EquipmentFrame(
    val mainHand: ItemFrame? = null,
    val offHand: ItemFrame? = null,
    val helmet: ItemFrame? = null,
    val chestplate: ItemFrame? = null,
    val leggings: ItemFrame? = null,
    val boots: ItemFrame? = null
)

data class ItemFrame(
    val material: String,
    val count: Int = 1
)

data class RecordingData(
    val playerName: String,
    val startTimestamp: Long,
    val frames: MutableList<PlayerFrame> = mutableListOf()
) {
    val duration: Long
        get() = if (frames.isEmpty()) 0 else frames.last().timestamp - frames.first().timestamp
}
