package com.evidex.detection

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Cola de escritura asíncrona de violaciones.
 *
 * El hilo principal del servidor **nunca** debe bloquearse en I/O de base de
 * datos. [AlertService] encola el registro (operación O(1) sin bloqueo) y un
 * task async lo persiste en lotes vía [flush]. La cola está acotada: si se
 * llena (DB caída/lenta), se descartan los más nuevos y se cuenta el descarte
 * en vez de hacer crecer la memoria sin límite (sin truncado silencioso).
 *
 * No depende de Bukkit: el scheduling lo hace quien lo usa, lo que mantiene esta
 * clase totalmente testeable.
 */
class ViolationWriter(
    private val capacity: Int,
    private val batchMax: Int,
    private val sink: (List<ViolationRecord>) -> Unit,
    private val onDrop: (Long) -> Unit = {}
) {

    private val queue = ArrayBlockingQueue<ViolationRecord>(capacity.coerceAtLeast(1))
    private val dropped = AtomicLong(0)

    /** Encola sin bloquear. Si la cola está llena, descarta y cuenta. */
    fun enqueue(record: ViolationRecord) {
        if (!queue.offer(record)) {
            onDrop(dropped.incrementAndGet())
        }
    }

    /** Drena hasta [batchMax] registros y los persiste vía [sink]. No-op si vacío. */
    fun flush() {
        if (queue.isEmpty()) return
        val batch = ArrayList<ViolationRecord>(minOf(batchMax, queue.size))
        queue.drainTo(batch, batchMax)
        if (batch.isNotEmpty()) sink(batch)
    }

    /** Drena todo lo pendiente (usado en el apagado para no perder registros). */
    fun flushAll() {
        while (queue.isNotEmpty()) {
            val batch = ArrayList<ViolationRecord>(minOf(batchMax, queue.size))
            queue.drainTo(batch, batchMax)
            if (batch.isEmpty()) break
            sink(batch)
        }
    }

    fun pending(): Int = queue.size
    fun droppedCount(): Long = dropped.get()
}
