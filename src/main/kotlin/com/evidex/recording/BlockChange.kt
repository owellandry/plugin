package com.evidex.recording

data class BlockChange(
    val timestamp: Long,
    val x: Int,
    val y: Int,
    val z: Int,
    val material: String
)