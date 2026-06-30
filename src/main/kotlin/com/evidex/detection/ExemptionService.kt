package com.evidex.detection

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Razón de una exención transitoria (ventana de gracia). */
enum class ExemptReason {
    TELEPORT,
    RESPAWN,
    JOIN,
    VEHICLE_EXIT,
    WORLD_CHANGE
}

/**
 * Registro central de exenciones transitorias.
 *
 * Tras eventos que rompen las suposiciones de los checks (teleport, respawn,
 * join, salir de un vehículo, cambio de mundo) se otorga una ventana de gracia
 * por jugador durante la cual NO se evalúan detecciones. Esto elimina, en un
 * único lugar, los falsos positivos que antes se parcheaban check por check.
 *
 * Thread-safe: se escribe/lee desde el hilo principal del servidor, pero se usa
 * [ConcurrentHashMap] por robustez ante accesos del dashboard.
 */
class ExemptionService {

    private data class Grace(val until: Long, val reason: ExemptReason)

    private val grace = ConcurrentHashMap<UUID, Grace>()

    /** Otorga (o extiende) una ventana de gracia. Conserva el vencimiento más lejano. */
    fun grant(uuid: UUID, reason: ExemptReason, durationMs: Long, now: Long = System.currentTimeMillis()) {
        if (durationMs <= 0) return
        val candidate = Grace(now + durationMs, reason)
        grace.merge(uuid, candidate) { old, new -> if (new.until >= old.until) new else old }
    }

    /** True si el jugador está dentro de una ventana de gracia activa. */
    fun isExempt(uuid: UUID, now: Long = System.currentTimeMillis()): Boolean {
        val g = grace[uuid] ?: return false
        if (now >= g.until) {
            grace.remove(uuid, g)
            return false
        }
        return true
    }

    /** Razón activa de la exención, o null si no hay. */
    fun activeReason(uuid: UUID, now: Long = System.currentTimeMillis()): ExemptReason? {
        val g = grace[uuid] ?: return null
        return if (now < g.until) g.reason else null
    }

    fun clear(uuid: UUID) {
        grace.remove(uuid)
    }
}
