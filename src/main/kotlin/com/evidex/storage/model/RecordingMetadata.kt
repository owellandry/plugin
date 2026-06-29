package com.evidex.storage.model

data class RecordingMetadata(
    val id: Long = 0,
    val playerName: String,
    val startTimestamp: Long,
    val endTimestamp: Long? = null,
    val world: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val frameCount: Int = 0,
    val filePath: String = "",
    val worldFilePath: String = "",
    val videoFilePath: String = "",
    val videoStatus: String = "pending",
    val status: String = "recording",
    val source: String = "MANUAL",
    val triggerCheck: String? = null,
    val peakVl: Int = 0,
    val violationCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
