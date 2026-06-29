package com.evidex.playback

import com.evidex.recording.NearbyEntityFrame
import com.evidex.recording.NearbyEntityKind
import org.bukkit.entity.EntityType

object ReplayEntitySpawnMapper {

    fun entityTypeForReplay(entity: NearbyEntityFrame): EntityType {
        val kind = NearbyEntityKind.from(entity.entityType)
        return when (kind) {
            NearbyEntityKind.PLAYER -> EntityType.ZOMBIE
            NearbyEntityKind.MOB -> resolveMobType(entity.entityType)
            NearbyEntityKind.OTHER -> EntityType.ARMOR_STAND
        }
    }

    private fun resolveMobType(entityType: String): EntityType {
        return runCatching { EntityType.valueOf(entityType.uppercase()) }.getOrNull()
            ?: EntityType.ZOMBIE
    }
}
