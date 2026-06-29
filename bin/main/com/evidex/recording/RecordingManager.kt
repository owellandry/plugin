package com.evidex.recording

import com.evidex.EvidexPlugin
import com.evidex.storage.model.RecordingMetadata
import com.evidex.storage.repository.BlockChangeRepository
import com.evidex.storage.repository.FrameRepository
import com.evidex.storage.repository.RecordingRepository
import com.evidex.storage.repository.WorldRepository
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class RecordingManager(
    private val plugin: EvidexPlugin,
    private val recordingRepository: RecordingRepository,
    private val frameRepository: FrameRepository,
    private val worldRepository: WorldRepository,
    private val blockChangeRepository: BlockChangeRepository,
    private val worldSnapshotService: WorldSnapshotService
) {

    private val sessions = mutableMapOf<String, RecordingSession>()
    private val activeRecordingIds = mutableMapOf<String, Long>()
    private val recordingSources = mutableMapOf<String, String>()
    private val sessionPeakVl = mutableMapOf<String, Int>()
    private val sessionViolationCount = mutableMapOf<String, Int>()
    private val sessionTriggerCheck = mutableMapOf<String, String?>()
    private val initialSnapshots = mutableMapOf<Long, InitialWorldSnapshotTask>()
    private val pathSnapshotEnricher = PathSnapshotEnricher(
        plugin, worldSnapshotService, worldRepository, recordingRepository
    )
    private val preRecordService = PreRecordService(plugin)

    init {
        preRecordService.start()
    }

    fun shutdown() {
        preRecordService.stop()
    }

    fun startRecording(
        player: Player,
        source: String = "MANUAL",
        triggerCheck: String? = null
    ) {
        if (sessions.containsKey(player.name)) return

        val prebuffer = resolvePreBuffer(player, source)
        val session = RecordingSession(plugin, player, {
            if (sessions.containsKey(player.name)) {
                plugin.log.info("Grabación de ${player.name} detenida (duración máxima alcanzada)")
                stopRecording(player)
            }
        }, prebuffer)
        sessions[player.name] = session

        val loc = player.location
        val metadata = RecordingMetadata(
            playerName = player.name,
            startTimestamp = session.data.startTimestamp,
            world = loc.world?.name,
            x = loc.x,
            y = loc.y,
            z = loc.z,
            status = "recording",
            source = source,
            triggerCheck = triggerCheck
        )
        val recordingId = recordingRepository.create(metadata)
        activeRecordingIds[player.name] = recordingId
        recordingSources[player.name] = source
        sessionPeakVl[player.name] = 0
        sessionViolationCount[player.name] = 0
        sessionTriggerCheck[player.name] = triggerCheck

        initialSnapshots.remove(recordingId)?.cancel()
        val snapshotTask = InitialWorldSnapshotTask(
            plugin,
            worldSnapshotService,
            worldRepository,
            recordingRepository,
            recordingId,
            player.name,
            player.world,
            loc.blockX,
            loc.blockY,
            loc.blockZ
        )
        initialSnapshots[recordingId] = snapshotTask
        snapshotTask.start()

        val preMsg = if (prebuffer.isNotEmpty()) " (+${prebuffer.size} frames pre-buffer)" else ""
        plugin.log.info(
            "Grabación iniciada: ${player.name} en ${loc.world?.name} " +
                "(radio ${plugin.configManager.getRecordingWorldRadius()})$preMsg"
        )
    }

    private fun resolvePreBuffer(player: Player, source: String): List<PlayerFrame> {
        if (!plugin.configManager.isPreBufferEnabled()) return emptyList()
        if (plugin.configManager.isPreBufferAutoOnly() && source != "AUTO") return emptyList()
        return preRecordService.takeBuffer(player.name)
    }

    fun stopRecording(player: Player) {
        val session = sessions.remove(player.name) ?: return
        session.stop()

        val metadata = recordingRepository.findByPlayer(player.name)
            .firstOrNull { it.status == "recording" } ?: return

        saveSession(metadata, session)
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.forEach { (name, session) ->
            val metadata = recordingRepository.findByPlayer(name)
                .firstOrNull { it.status == "recording" }
            if (metadata != null) saveSession(metadata, session)
        }
        sessions.clear()
    }

    private fun saveSession(metadata: RecordingMetadata, session: RecordingSession) {
        val frames = session.data.frames
        val filePath = frameRepository.writeFrames(
            metadata.id,
            frames,
            plugin.configManager.getMaxEntitiesPerFrame()
        )
        val totalEntities = frames.sumOf { it.nearbyEntities.size }

        if (session.data.blockChanges.isNotEmpty()) {
            blockChangeRepository.writeChanges(metadata.id, session.data.blockChanges)
        }

        recordingRepository.update(metadata.copy(
            endTimestamp = frames.lastOrNull()?.timestamp,
            frameCount = frames.size,
            filePath = filePath,
            worldFilePath = metadata.worldFilePath,
            status = "saved",
            source = recordingSources[session.player.name] ?: metadata.source,
            triggerCheck = sessionTriggerCheck[session.player.name] ?: metadata.triggerCheck,
            peakVl = sessionPeakVl[session.player.name] ?: metadata.peakVl,
            violationCount = sessionViolationCount[session.player.name] ?: metadata.violationCount
        ))
        activeRecordingIds.remove(session.player.name)
        recordingSources.remove(session.player.name)
        sessionPeakVl.remove(session.player.name)
        sessionViolationCount.remove(session.player.name)
        sessionTriggerCheck.remove(session.player.name)

        plugin.log.info(
            "Grabación guardada: ${session.player.name} — " +
                "${frames.size} frames, ${session.data.duration}ms, $totalEntities entidades"
        )

        val worldName = metadata.world
        if (frames.isNotEmpty() && !worldName.isNullOrBlank()) {
            Bukkit.getWorld(worldName)?.let { world ->
                pathSnapshotEnricher.schedule(
                    recordingId = metadata.id,
                    playerName = session.player.name,
                    world = world,
                    frames = frames,
                    existingWorldFilePath = metadata.worldFilePath
                )
            }
        }
    }

    fun cancelPathSnapshots() = pathSnapshotEnricher.cancelAll()

    fun cancelInitialSnapshots() {
        initialSnapshots.values.forEach { it.cancel() }
        initialSnapshots.clear()
    }

    fun isRecording(player: Player) = sessions.containsKey(player.name)

    fun getRecording(playerName: String): RecordingData? {
        val metadata = recordingRepository.findByPlayer(playerName)
            .firstOrNull { it.status == "saved" } ?: return null

        return loadRecordingData(metadata)
    }

    fun getRecordingNames(): List<String> =
        recordingRepository.findByStatus("saved").map { it.playerName }.distinct()

    fun getActiveRecordingNames(): List<String> = sessions.keys.toList()

    fun getSession(playerName: String): RecordingSession? = sessions[playerName]

    fun getActiveRecordingId(playerName: String): Long? = activeRecordingIds[playerName]

    fun getRecordingSource(playerName: String): String? = recordingSources[playerName]

    fun tagSuspiciousEvent(playerName: String, eventType: String, eventDetail: String) {
        sessions[playerName]?.captureTaggedEvent(eventType, eventDetail)
    }

    fun noteViolationForPlayer(playerName: String, checkName: String, vlTotal: Int) {
        if (!sessions.containsKey(playerName)) return
        sessionViolationCount[playerName] = (sessionViolationCount[playerName] ?: 0) + 1
        val peak = sessionPeakVl[playerName] ?: 0
        if (vlTotal > peak) sessionPeakVl[playerName] = vlTotal
        if (sessionTriggerCheck[playerName] == null) {
            sessionTriggerCheck[playerName] = checkName
        }
    }

    fun getRecordingById(id: Long): RecordingData? {
        val metadata = recordingRepository.findById(id) ?: return null
        if (metadata.status != "saved") return null
        return loadRecordingData(metadata)
    }

    fun getAllRecordingMetadata(): List<RecordingMetadata> =
        recordingRepository.findByStatus("saved")

    fun deleteRecording(id: Long): Boolean {
        val metadata = recordingRepository.findById(id) ?: return false
        if (metadata.filePath.isNotBlank()) frameRepository.deleteFile(metadata.filePath)
        if (metadata.worldFilePath.isNotBlank()) worldRepository.deleteFile(metadata.worldFilePath)
        blockChangeRepository.deleteFile(id)
        recordingRepository.delete(id)
        return true
    }

    private fun loadRecordingData(metadata: RecordingMetadata): RecordingData {
        val frames = frameRepository.readFrames(metadata.filePath)
        val blockChanges = blockChangeRepository.readChanges(metadata.id).toMutableList()
        return RecordingData(
            playerName = metadata.playerName,
            startTimestamp = metadata.startTimestamp,
            worldName = metadata.world,
            recordingId = metadata.id,
            worldFilePath = metadata.worldFilePath,
            frames = frames.toMutableList(),
            blockChanges = blockChanges
        )
    }
}