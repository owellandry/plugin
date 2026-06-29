package com.evidex.recording

data class WorldBlock(
    val x: Int,
    val y: Int,
    val z: Int,
    val material: String
)

data class WorldSnapshot(
    val version: Int = 1,
    val worldName: String,
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val radius: Int,
    val blocks: List<WorldBlock>
)