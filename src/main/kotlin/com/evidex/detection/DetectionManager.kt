package com.evidex.detection

import com.evidex.EvidexPlugin
import com.evidex.detection.combat.AimAssistCheck
import com.evidex.detection.combat.AutoClickCheck
import com.evidex.detection.combat.InvalidRotationCheck
import com.evidex.detection.combat.KillAuraCheck
import com.evidex.detection.combat.ReachCheck
import com.evidex.detection.combat.VelocityCheck
import com.evidex.detection.combat.WallHitCheck
import com.evidex.detection.movement.BlinkCheck
import com.evidex.detection.movement.FlightCheck
import com.evidex.detection.movement.JesusCheck
import com.evidex.detection.movement.NoFallCheck
import com.evidex.detection.movement.SpeedCheck
import com.evidex.detection.movement.SpiderCheck
import com.evidex.detection.movement.StepCheck
import com.evidex.detection.movement.TimerCheck
import com.evidex.detection.player.ChestStealerCheck
import com.evidex.detection.player.FastBreakCheck
import com.evidex.detection.player.FastEatCheck
import com.evidex.detection.player.FastInventoryCheck
import com.evidex.detection.player.ScaffoldCheck
import com.evidex.detection.player.XRayCheck
import com.evidex.detection.lag.LagCompensator
import com.evidex.detection.lag.TransactionLatencyTracker
import com.evidex.storage.repository.ViolationRepository
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DetectionManager(
    val plugin: EvidexPlugin,
    val violationRepository: ViolationRepository
) {
    private val profiles = ConcurrentHashMap<UUID, PlayerProfile>()
    private var decayTask: BukkitTask? = null
    private var writerTask: BukkitTask? = null

    /** Escritura async de violaciones: el hilo principal nunca bloquea en la DB. */
    val violationWriter = ViolationWriter(
        capacity = 10_000,
        batchMax = 500,
        sink = { batch ->
            try {
                violationRepository.createBatch(batch)
            } catch (e: Exception) {
                plugin.log.warn("No se pudieron persistir ${batch.size} violaciones: ${e.message}")
            }
        },
        onDrop = { n -> if (n % 100L == 0L) plugin.log.warn("Cola de violaciones llena; descartadas=$n (¿DB lenta o caída?)") }
    )

    val gate = DetectionGate(plugin.configManager)
    val buffer = ViolationBuffer(plugin.configManager)
    lateinit var alert: AlertService
        private set

    /** Exenciones transitorias (teleport/respawn/join/…). Consultado por el listener. */
    val exemptions = ExemptionService()

    /** Medición de RTT por transacciones de PacketEvents (con fallback a ping). */
    val latencyTracker = TransactionLatencyTracker(plugin)

    /** Compensación de lag (RTT + TPS) usada por los checks. */
    val lagCompensator = LagCompensator(
        plugin.configManager,
        latencyTracker
    ) { plugin.server.tps.firstOrNull() ?: 20.0 }

    val flightCheck = FlightCheck(plugin.configManager)
    val speedCheck = SpeedCheck(plugin.configManager)
    val jesusCheck = JesusCheck(plugin.configManager)
    val noFallCheck = NoFallCheck(plugin.configManager)
    val stepCheck = StepCheck(plugin.configManager)
    val spiderCheck = SpiderCheck(plugin.configManager)
    val timerCheck = TimerCheck(plugin.configManager)
    val blinkCheck = BlinkCheck(plugin.configManager)
    val reachCheck = ReachCheck(plugin.configManager) { lagCompensator.reachBuffer(it) }
    val killAuraCheck = KillAuraCheck(plugin.configManager)
    val autoClickCheck = AutoClickCheck(plugin.configManager)
    val aimAssistCheck = AimAssistCheck(plugin.configManager)
    val wallHitCheck = WallHitCheck(plugin.configManager)
    val invalidRotationCheck = InvalidRotationCheck(plugin.configManager)
    val velocityCheck = VelocityCheck(plugin.configManager)
    val xRayCheck = XRayCheck(plugin.configManager)
    val fastBreakCheck = FastBreakCheck(plugin.configManager)
    val scaffoldCheck = ScaffoldCheck(plugin.configManager)
    val fastEatCheck = FastEatCheck(plugin.configManager)
    val chestStealerCheck = ChestStealerCheck(plugin.configManager)
    val fastInventoryCheck = FastInventoryCheck(plugin.configManager)

    fun start() {
        if (!isEnabled()) return
        alert = AlertService(plugin, violationWriter, buffer, gate)
        plugin.server.pluginManager.registerEvents(DetectionListener(this), plugin)
        latencyTracker.start()
        // Flush async de violaciones cada 2 s (40 ticks), fuera del hilo principal.
        writerTask = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin, Runnable { violationWriter.flush() }, 40L, 40L
        )
        val interval = plugin.configManager.getDecayIntervalTicks().coerceAtLeast(20L)
        decayTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable { decayAll() }, interval, interval)
        plugin.log.debug("Detección activa — ${enabledCheckCount()} checks habilitados")
    }

    fun enabledCheckCount(): Int = enabledCheckNames().size

    private fun enabledCheckNames(): List<String> =
        listOf(
            "flight", "speed", "reach", "killaura", "autoclick", "aimassist",
            "xray", "fastbreak", "nofall", "jesus", "step", "spider", "timer",
            "blink", "scaffold", "fasteat", "wallhit", "badrotation", "velocity",
            "cheststealer", "fastinventory"
        ).filter { plugin.configManager.isCheckEnabled(it) }

    fun shutdown() {
        decayTask?.cancel()
        decayTask = null
        writerTask?.cancel()
        writerTask = null
        latencyTracker.stop()
        // Persistir lo que quede en cola antes de apagar (no perder evidencia).
        try {
            violationWriter.flushAll()
        } catch (e: Exception) {
            plugin.log.warn("Flush final de violaciones falló: ${e.message}")
        }
        profiles.clear()
    }

    fun isEnabled(): Boolean = plugin.configManager.isDetectionEnabled()

    fun canCheck(player: Player): Boolean = gate.canRun(player)

    fun isExempt(player: Player): Boolean = !gate.canRun(player)

    fun getOrCreate(player: Player): PlayerProfile {
        profileNameSync(player)
        return profiles.computeIfAbsent(player.uniqueId) {
            PlayerProfile(player.uniqueId, player.name)
        }
    }

    fun getProfile(uuid: UUID): PlayerProfile? = profiles[uuid]

    fun remove(uuid: UUID) {
        profiles.remove(uuid)
        exemptions.clear(uuid)
        latencyTracker.remove(uuid)
    }

    fun getLiveVl(): List<LivePlayerVl> =
        Bukkit.getOnlinePlayers()
            .filter { canCheck(it) }
            .map { player ->
                val profile = getOrCreate(player)
                LivePlayerVl(
                    playerName = player.name,
                    playerUuid = player.uniqueId.toString(),
                    totalVl = profile.totalVl(),
                    checks = profile.vlByCheck.toMap(),
                    isRecording = plugin.recordingManager.getActiveRecordingId(player.name) != null,
                    recordingSource = plugin.recordingManager.getRecordingSource(player.name)
                )
            }

    private fun profileNameSync(player: Player) {
        profiles[player.uniqueId]?.name = player.name
    }

    private fun decayAll() {
        val factor = plugin.configManager.getVlDecayFactor()
        for (profile in profiles.values) {
            profile.decayVl(factor)
        }
    }
}