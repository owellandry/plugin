package com.evidex.detection.lag

import com.evidex.EvidexPlugin
import com.evidex.playback.PacketEventsService
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mide la latencia real (RTT) de cada jugador con transacciones de PacketEvents:
 * envía un `Ping` con un id propio y cronometra el `Pong` del cliente. Es más
 * fiable que el ping de keepalive para alinear acciones con el estado del
 * servidor.
 *
 * Todo está protegido: si PacketEvents no está disponible o algo falla, el
 * tracker simplemente no entrega RTT y [LagCompensator] cae al ping de Bukkit.
 * Nunca debe romper el plugin.
 */
class TransactionLatencyTracker(
    private val plugin: EvidexPlugin
) : PacketListenerAbstract(PacketListenerPriority.MONITOR) {

    private data class Pending(val id: Int, val sentNanos: Long)

    // Base alta para no colisionar con pings que pueda emitir el propio servidor.
    private val idCounter = AtomicInteger(1_000_000)
    private val pending = ConcurrentHashMap<UUID, Pending>()
    private val rtt = ConcurrentHashMap<UUID, Int>()

    private var task: BukkitTask? = null
    private var active = false

    fun start() {
        if (active) return
        if (!PacketEventsService.isReady()) {
            plugin.log.debug("Lag: PacketEvents no disponible; RTT por transacciones deshabilitado (se usará ping de Bukkit)")
            return
        }
        try {
            PacketEvents.getAPI().eventManager.registerListener(this)
        } catch (e: Exception) {
            plugin.log.warn("Lag: no se pudo registrar el listener de transacciones: ${e.message}")
            return
        }
        val interval = plugin.configManager.getLagTransactionIntervalTicks()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { pingAll() }, interval, interval)
        active = true
        plugin.log.debug("Lag: RTT por transacciones activo (cada $interval ticks)")
    }

    fun stop() {
        task?.cancel()
        task = null
        if (active) {
            try {
                PacketEvents.getAPI().eventManager.unregisterListener(this)
            } catch (_: Exception) {
            }
        }
        active = false
        pending.clear()
        rtt.clear()
    }

    /** RTT medido en ms, o null si aún no hay medición. */
    fun rttMillis(uuid: UUID): Int? = rtt[uuid]

    fun remove(uuid: UUID) {
        pending.remove(uuid)
        rtt.remove(uuid)
    }

    private fun pingAll() {
        val api = PacketEvents.getAPI() ?: return
        for (player in Bukkit.getOnlinePlayers()) {
            try {
                val id = idCounter.incrementAndGet()
                pending[player.uniqueId] = Pending(id, System.nanoTime())
                api.playerManager.sendPacket(player, WrapperPlayServerPing(id))
            } catch (_: Exception) {
                // ignorar; el jugador simplemente no tendrá RTT por transacción
            }
        }
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        if (event.packetType != PacketType.Play.Client.PONG) return
        try {
            val uuid = event.user.uuid ?: return
            val p = pending[uuid] ?: return
            val pong = WrapperPlayClientPong(event)
            if (pong.id != p.id) return
            pending.remove(uuid)
            val ms = ((System.nanoTime() - p.sentNanos) / 1_000_000L).toInt().coerceIn(0, 10_000)
            rtt[uuid] = ms
        } catch (_: Exception) {
            // ignorar pong malformado / versión sin soporte
        }
    }
}
