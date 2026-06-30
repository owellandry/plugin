package com.evidex.detection.lag

import com.evidex.config.ConfigManager
import org.bukkit.entity.Player

/**
 * Compensación de lag para los checks de detección.
 *
 * - Provee la latencia (ms) del jugador: usa el RTT medido por transacciones de
 *   PacketEvents si está disponible ([TransactionLatencyTracker]); si no, cae al
 *   ping de Bukkit.
 * - [reachBuffer]: margen extra de alcance proporcional a la latencia.
 * - [shouldSoften]: indica si las condiciones de red/servidor (ping alto o TPS
 *   bajos) hacen poco fiable detectar, para saltar/relajar checks y no marcar
 *   lag como trampa.
 *
 * La lógica de decisión vive en funciones puras del companion para poder
 * testearla sin un servidor.
 */
class LagCompensator(
    private val config: ConfigManager,
    private val tracker: TransactionLatencyTracker?,
    private val tpsSupplier: () -> Double
) {

    /** Latencia efectiva del jugador en ms (RTT por transacciones o ping de Bukkit). */
    fun pingMillis(player: Player): Int {
        if (config.isLagUseTransactions() && tracker != null) {
            tracker.rttMillis(player.uniqueId)?.let { return it }
        }
        return player.ping.coerceAtLeast(0)
    }

    fun reachBuffer(player: Player): Double =
        reachBuffer(config.isLagCompensationEnabled(), pingMillis(player), config.getLagReachPerMs())

    fun shouldSoften(player: Player): Boolean =
        shouldSoften(
            enabled = config.isLagCompensationEnabled(),
            tps = tpsSupplier(),
            minTps = config.getLagMinTps(),
            pingMs = pingMillis(player),
            maxPingMs = config.getLagMaxPingMs()
        )

    companion object {
        /** Margen de alcance (bloques) por latencia. Pura. */
        fun reachBuffer(enabled: Boolean, pingMs: Int, reachPerMs: Double): Double =
            if (!enabled) 0.0 else (pingMs.coerceAtLeast(0) / 1000.0) * reachPerMs

        /** Decisión de relajar checks por red/servidor. Pura. */
        fun shouldSoften(enabled: Boolean, tps: Double, minTps: Double, pingMs: Int, maxPingMs: Int): Boolean {
            if (!enabled) return false
            if (tps < minTps) return true
            if (pingMs > maxPingMs) return true
            return false
        }
    }
}
