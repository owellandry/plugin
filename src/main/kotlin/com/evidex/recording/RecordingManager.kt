package com.evidex.recording

import com.evidex.EvidexPlugin
import com.evidex.recording.PlayerFrame
import com.evidex.storage.model.RecordingMetadata
import com.evidex.storage.repository.FrameRepository
import com.evidex.storage.repository.RecordingRepository
import org.bukkit.entity.Player

class RecordingManager(
    private val plugin: EvidexPlugin,
    private val recordingRepository: RecordingRepository,
    private val frameRepository: FrameRepository
) {

    private val sessions = mutableMapOf<String, RecordingSession>()

    fun register() {}

    fun startRecording(player: Player) {
        if (sessions.containsKey(player.name)) return
        val session = RecordingSession(plugin, player)
        sessions[player.name] = session

        val metadata = RecordingMetadata(
            playerName = player.name,
            startTimestamp = session.data.startTimestamp,
            status = "recording"
        )
        recordingRepository.create(metadata)
    }

    fun stopRecording(player: Player) {
        val session = sessions.remove(player.name) ?: return
        session.stop()

        val metadataList = recordingRepository.findByPlayer(player.name)
            .filter { it.status == "recording" }
        if (metadataList.isEmpty()) return

        val metadata = metadataList.first()
        val recordingId = metadata.id

        val frames = session.data.frames
        val filePath = frameRepository.writeFrames(recordingId, frames)

        recordingRepository.update(metadata.copy(
            endTimestamp = if (frames.isEmpty()) null else frames.last().timestamp,
            frameCount = frames.size,
            filePath = filePath,
            status = "saved"
        ))

        plugin.logger.info("Recording saved for ${player.name}: ${frames.size} frames, ${session.data.duration}ms")
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.forEach { (name, session) ->
            saveInMemoryRecording(name, session)
        }
        sessions.clear()
    }

    private fun saveInMemoryRecording(playerName: String, session: RecordingSession) {
        session.stop()
        val metadataList = recordingRepository.findByPlayer(playerName)
            .filter { it.status == "recording" }
        if (metadataList.isEmpty()) return

        val metadata = metadataList.first()
        val frames = session.data.frames
        val filePath = frameRepository.writeFrames(metadata.id, frames)

        recordingRepository.update(metadata.copy(
            endTimestamp = if (frames.isEmpty()) null else frames.last().timestamp,
            frameCount = frames.size,
            filePath = filePath,
            status = "saved"
        ))
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
            frames = frames.toMutableList()
        )
    }

    fun getFrames(id: Long): List<PlayerFrame> {
        val metadata = recordingRepository.findById(id) ?: return emptyList()
        if (metadata.status != "saved" || metadata.filePath.isBlank()) return emptyList()
        return frameRepository.readFrames(metadata.filePath)
    }

    fun getAllRecordingMetadata(): List<RecordingMetadata> {
        return recordingRepository.findByStatus("saved")
    }

    fun deleteRecording(id: Long): Boolean {
        val metadata = recordingRepository.findById(id) ?: return false
        if (metadata.filePath.isNotBlank()) {
            frameRepository.deleteFile(metadata.filePath)
        }
        recordingRepository.delete(id)
        return true
    }
}
