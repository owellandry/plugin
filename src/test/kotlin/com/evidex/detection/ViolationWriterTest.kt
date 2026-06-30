package com.evidex.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViolationWriterTest {

    private fun record(check: String = "speed") = ViolationRecord(
        playerUuid = "u", playerName = "p", checkName = check,
        category = ViolationCategory.MOVEMENT, vlAdded = 1, vlTotal = 1,
        severity = ViolationSeverity.LOW, infoJson = "{}"
    )

    @Test
    fun `flush drena en lotes y vacia la cola`() {
        val batches = mutableListOf<List<ViolationRecord>>()
        val w = ViolationWriter(capacity = 100, batchMax = 500, sink = { batches.add(it) })
        repeat(3) { w.enqueue(record()) }
        assertEquals(3, w.pending())

        w.flush()
        assertEquals(1, batches.size)
        assertEquals(3, batches[0].size)
        assertEquals(0, w.pending())
    }

    @Test
    fun `flush respeta batchMax`() {
        val batches = mutableListOf<List<ViolationRecord>>()
        val w = ViolationWriter(capacity = 100, batchMax = 2, sink = { batches.add(it) })
        repeat(5) { w.enqueue(record()) }

        w.flush() // drena 2
        assertEquals(2, batches[0].size)
        assertEquals(3, w.pending())
    }

    @Test
    fun `overflow descarta y cuenta sin crecer memoria`() {
        var lastDrop = 0L
        val w = ViolationWriter(capacity = 2, batchMax = 10, sink = {}, onDrop = { lastDrop = it })
        repeat(5) { w.enqueue(record()) }

        assertEquals(2, w.pending())          // la cola nunca excede capacity
        assertEquals(3, w.droppedCount())     // 5 - 2 descartados
        assertEquals(3, lastDrop)
    }

    @Test
    fun `flushAll drena todo en varios lotes`() {
        val batches = mutableListOf<List<ViolationRecord>>()
        val w = ViolationWriter(capacity = 100, batchMax = 2, sink = { batches.add(it) })
        repeat(5) { w.enqueue(record()) }

        w.flushAll()
        assertEquals(0, w.pending())
        assertEquals(5, batches.sumOf { it.size })
        assertTrue(batches.size >= 3) // 2 + 2 + 1
    }
}
