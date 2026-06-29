package com.evidex.recording

import com.evidex.math.Angle
import com.evidex.math.Vec3d

data class NearbyEntityFrame(
    val entityType: String = "PLAYER",
    val name: String? = null,
    val playerUuid: String? = null,
    val entityUuid: String? = null,
    val position: Vec3d,
    val yaw: Angle,
    val pitch: Angle,
    val isSneaking: Boolean = false,
    val isBaby: Boolean = false
)

enum class NearbyEntityKind {
    PLAYER,
    MOB,
    OTHER;

    companion object {
        fun from(entityType: String): NearbyEntityKind {
            return when (entityType.uppercase()) {
                "PLAYER" -> PLAYER
                "ITEM", "DROPPED_ITEM" -> OTHER
                else -> if (entityType.uppercase().endsWith("_ITEM")) OTHER else MOB
            }
        }
    }
}

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
    val equipment: EquipmentFrame,
    val health: Float = 20f,
    val food: Int = 20,
    val hotbarSlot: Int = 0,
    val nearbyEntities: List<NearbyEntityFrame> = emptyList()
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
    val worldName: String? = null,
    val frames: MutableList<PlayerFrame> = mutableListOf()
) {
    val duration: Long
        get() = if (frames.isEmpty()) 0 else frames.last().timestamp - frames.first().timestamp
}
