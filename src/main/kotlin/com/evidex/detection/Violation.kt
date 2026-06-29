package com.evidex.detection

enum class ViolationCategory {
    MOVEMENT,
    COMBAT,
    PLAYER
}

enum class ViolationSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    companion object {
        fun fromVlRatio(ratio: Double): ViolationSeverity = when {
            ratio >= 0.85 -> CRITICAL
            ratio >= 0.6 -> HIGH
            ratio >= 0.35 -> MEDIUM
            else -> LOW
        }
    }
}

data class ViolationResult(
    val checkName: String,
    val category: ViolationCategory,
    val vl: Int,
    val info: Map<String, String> = emptyMap()
)

data class ViolationRecord(
    val id: Long = 0,
    val playerUuid: String,
    val playerName: String,
    val checkName: String,
    val category: ViolationCategory,
    val vlAdded: Int,
    val vlTotal: Int,
    val severity: ViolationSeverity,
    val infoJson: String,
    val recordingId: Long? = null,
    val world: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class LivePlayerVl(
    val playerName: String,
    val playerUuid: String,
    val totalVl: Int,
    val checks: Map<String, Int>,
    val isRecording: Boolean,
    val recordingSource: String? = null
)