package com.evidex.recording

import com.evidex.EvidexPlugin
import com.evidex.storage.model.RecordingMetadata
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
    private val worldSnapshotService: WorldSnapshotService
) {

    private val sessions = mutableMapOf<String, RecordingSession>()
    private val initialSnapshots = mutableMapOf<Long, InitialWorldSnapshotTask>()
    private val pathSnapshotEnricher = PathSnapshotEnricher(
        plugin, worldSnapshotService, worldRepository, recordingRepository
    )

    fun register() {}

    fun startRecording(player: Player) {
        if (sessions.containsKey(player.name)) return

        val session = RecordingSession(plugin, player) {
            if (sessions.containsKey(player.name)) {
                plugin.logger.info("Auto-stopping recording for ${player.name} (max duration reached)")
                stopRecording(player)
            }
        }
        sessions[player.name] = session

        val loc = player.location
        val metadata = RecordingMetadata(
            playerName = player.name,
            startTimestamp = session.data.startTimestamp,
            world = loc.world?.name,
            x = loc.x,
            y = loc.y,
            z = loc.z,
            status = "recording"
        )
        val recordingId = recordingRepository.create(metadata)
        val world = player.world

        initialSnapshots.remove(recordingId)?.cancel()
        val snapshotTask = InitialWorldSnapshotTask(
            plugin,
            worldSnapshotService,
            worldRepository,
            recordingRepository,
            recordingId,
            player.name,
            world,
            loc.blockX,
            loc.blockY,
            loc.blockZ
        )
        initialSnapshots[recordingId] = snapshotTask
        snapshotTask.start()
        plugin.logger.info("Started sliced world capture for ${player.name} (radius ${plugin.configManager.getRecordingWorldRadius()})")
    }

    fun stopRecording(player: Player) {
        val session = sessions.remove(player.name) ?: return
        session.stop()

        val metadataList = recordingRepository.findByPlayer(player.name)
            .filter { it.status == "recording" }
        if (metadataList.isEmpty()) return

        val metadata = metadataList.first()
        saveSession(metadata, session)
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.forEach { (name, session) ->
            val metadataList = recordingRepository.findByPlayer(name)
                .filter { it.status == "recording" }
            if (metadataList.isNotEmpty()) {
                saveSession(metadataList.first(), session)
            }
        }
        sessions.clear()
    }

    private fun saveSession(metadata: RecordingMetadata, session: RecordingSession) {
        val frames = session.data.frames
        val filePath = frameRepository.writeFrames(
            metadata.id,
            frames,
            plugin.configManager.getMaxEntitiesPerFrameWrite()
        )
        val totalEntities = frames.sumOf { it.nearbyEntities.size }

        recordingRepository.update(metadata.copy(
            endTimestamp = if (frames.isEmpty()) null else frames.last().timestamp,
            frameCount = frames.size,
            filePath = filePath,
            worldFilePath = metadata.worldFilePath,
            status = "saved"
        ))

        plugin.logger.info(
            "Recording saved for ${session.player.name}: ${frames.size} frames, " +
                "${session.data.duration}ms, $totalEntities entity snapshots"
        )

        if (frames.isNotEmpty() && !metadata.world.isNullOrBlank()) {
            val world = Bukkit.getWorld(metadata.world!!)
            if (world != null) {
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

    fun cancelPathSnapshots() {
        pathSnapshotEnricher.cancelAll()
    }

    fun cancelInitialSnapshots() {
        initialSnapshots.values.forEach { it.cancel() }
        initialSnapshots.clear()
    }

    fun isRecording(player: Player) = sessions.containsKey(player.name)

    fun getRecording(playerName: String): RecordingData? {
        val metadata = recordingRepository.findByPlayer(playerName)
            .firstOrNull { it.status == "saved" }
            ?: return null

        val frames = frameRepository.readFrames(metadata.filePath)
        return RecordingData(
            playerName = playerName,
            startTimestamp = metadata.startTimestamp,
            worldName = metadata.world,
            frames = frames.toMutableList()
        )
    }

    fun getRecordingNames(): List<String> {
        return recordingRepository.findByStatus("saved")
            .map { it.playerName }
            .distinct()
    }

    fun getRecordingList(): List<RecordingMetadata> {
        return recordingRepository.findByStatus("saved")
    }

    fun getActiveRecordingNames(): List<String> = sessions.keys.toList()

    fun getSession(playerName: String): RecordingSession? = sessions[playerName]

    fun getAllRecordings(): List<RecordingData> {
        return recordingRepository.findByStatus("saved").mapNotNull { meta ->
            val frames = frameRepository.readFrames(meta.filePath)
            RecordingData(
                playerName = meta.playerName,
                startTimestamp = meta.startTimestamp,
                worldName = meta.world,
                frames = frames.toMutableList()
            )
        }
    }

    fun getRecordingById(id: Long): RecordingData? {
        val metadata = recordingRepository.findById(id) ?: return null
        if (metadata.status != "saved") return null
        val frames = frameRepository.readFrames(metadata.filePath)
        return RecordingData(
            playerName = metadata.playerName,
            startTimestamp = metadata.startTimestamp,
            worldName = metadata.world,
            frames = frames.toMutableList()
        )
    }

    fun getRecordingMetadata(id: Long): RecordingMetadata? {
        return recordingRepository.findById(id)
    }

    fun getAllRecordingMetadata(): List<RecordingMetadata> {
        return recordingRepository.findByStatus("saved")
    }

    fun deleteRecording(id: Long): Boolean {
        val metadata = recordingRepository.findById(id) ?: return false
        if (metadata.filePath.isNotBlank()) {
            frameRepository.deleteFile(metadata.filePath)
        }
        if (metadata.worldFilePath.isNotBlank()) {
            worldRepository.deleteFile(metadata.worldFilePath)
        }
        recordingRepository.delete(id)
        return true
    }
}