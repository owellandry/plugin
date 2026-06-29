package com.evidex.recording

import java.util.ArrayDeque

class PreRecordBuffer(private val maxAgeMs: Long) {

    private val frames = ArrayDeque<PlayerFrame>()

    fun add(frame: PlayerFrame) {
        frames.addLast(frame)
        trim(frame.timestamp)
    }

    private fun trim(latestMs: Long) {
        while (frames.isNotEmpty() && latestMs - frames.first().timestamp > maxAgeMs) {
            frames.removeFirst()
        }
    }

    fun snapshot(): List<PlayerFrame> = frames.toList()

    fun clear() {
        frames.clear()
    }

    fun size(): Int = frames.size
}