package com.evidex.storage.repository

import com.evidex.storage.database.Database
import com.evidex.storage.model.RecordingMetadata

class RecordingRepository(private val db: Database) {

    fun create(metadata: RecordingMetadata): Long {
        val sql = """
            INSERT INTO recording_metadata 
                (player_name, start_timestamp, end_timestamp, world, x, y, z, frame_count, file_path,
                 world_file_path, video_file_path, video_status, status, source, trigger_check,
                 peak_vl, violation_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        db.executeUpdate(sql, listOf(
            metadata.playerName,
            metadata.startTimestamp,
            metadata.endTimestamp,
            metadata.world,
            metadata.x,
            metadata.y,
            metadata.z,
            metadata.frameCount,
            metadata.filePath,
            metadata.worldFilePath,
            metadata.videoFilePath,
            metadata.videoStatus,
            metadata.status,
            metadata.source,
            metadata.triggerCheck,
            metadata.peakVl,
            metadata.violationCount,
            metadata.createdAt
        ))

        val result = db.executeQuery(
            "SELECT MAX(id) as id FROM recording_metadata WHERE player_name = ?",
            listOf(metadata.playerName)
        )
        return (result.firstOrNull()?.get("id") as? Number)?.toLong() ?: 0
    }

    fun update(metadata: RecordingMetadata) {
        val sql = """
            UPDATE recording_metadata SET
                end_timestamp = ?, frame_count = ?, file_path = ?, world_file_path = ?,
                video_file_path = ?, video_status = ?, status = ?, source = ?,
                trigger_check = ?, peak_vl = ?, violation_count = ?
            WHERE id = ?
        """
        db.executeUpdate(sql, listOf(
            metadata.endTimestamp,
            metadata.frameCount,
            metadata.filePath,
            metadata.worldFilePath,
            metadata.videoFilePath,
            metadata.videoStatus,
            metadata.status,
            metadata.source,
            metadata.triggerCheck,
            metadata.peakVl,
            metadata.violationCount,
            metadata.id
        ))
    }

    fun updateVideoStatus(id: Long, status: String, filePath: String) {
        db.executeUpdate(
            "UPDATE recording_metadata SET video_status = ?, video_file_path = ? WHERE id = ?",
            listOf(status, filePath, id)
        )
    }

    fun updateFilePath(id: Long, filePath: String) {
        db.executeUpdate("UPDATE recording_metadata SET file_path = ? WHERE id = ?", listOf(filePath, id))
    }

    fun updateWorldFilePath(id: Long, worldFilePath: String) {
        db.executeUpdate("UPDATE recording_metadata SET world_file_path = ? WHERE id = ?", listOf(worldFilePath, id))
    }

    fun findByPlayer(playerName: String): List<RecordingMetadata> {
        val results = db.executeQuery(
            "SELECT * FROM recording_metadata WHERE player_name = ? ORDER BY created_at DESC",
            listOf(playerName)
        )
        return results.map { mapToMetadata(it) }
    }

    fun findAll(): List<RecordingMetadata> {
        val results = db.executeQuery("SELECT * FROM recording_metadata ORDER BY created_at DESC", emptyList())
        return results.map { mapToMetadata(it) }
    }

    fun findByStatus(status: String): List<RecordingMetadata> {
        val results = db.executeQuery(
            "SELECT * FROM recording_metadata WHERE status = ? ORDER BY created_at DESC",
            listOf(status)
        )
        return results.map { mapToMetadata(it) }
    }

    fun delete(id: Long) {
        db.executeUpdate("DELETE FROM recording_metadata WHERE id = ?", listOf(id))
    }

    fun findById(id: Long): RecordingMetadata? {
        val results = db.executeQuery("SELECT * FROM recording_metadata WHERE id = ?", listOf(id))
        return results.firstOrNull()?.let { mapToMetadata(it) }
    }

    fun deleteExpired(before: Long): List<RecordingMetadata> {
        val expired = db.executeQuery(
            "SELECT * FROM recording_metadata WHERE created_at < ? AND status != 'recording'",
            listOf(before)
        )
        val metadatas = expired.map { mapToMetadata(it) }
        for (m in metadatas) {
            db.executeUpdate("DELETE FROM recording_metadata WHERE id = ?", listOf(m.id))
        }
        return metadatas
    }

    private fun mapToMetadata(row: Map<String, Any?>): RecordingMetadata {
        return RecordingMetadata(
            id = (row["id"] as? Number)?.toLong() ?: 0,
            playerName = row["player_name"] as? String ?: "",
            startTimestamp = (row["start_timestamp"] as? Number)?.toLong() ?: 0,
            endTimestamp = (row["end_timestamp"] as? Number)?.toLong(),
            world = row["world"] as? String,
            x = (row["x"] as? Number)?.toDouble(),
            y = (row["y"] as? Number)?.toDouble(),
            z = (row["z"] as? Number)?.toDouble(),
            frameCount = (row["frame_count"] as? Number)?.toInt() ?: 0,
            filePath = row["file_path"] as? String ?: "",
            worldFilePath = row["world_file_path"] as? String ?: "",
            videoFilePath = row["video_file_path"] as? String ?: "",
            videoStatus = row["video_status"] as? String ?: "pending",
            status = row["status"] as? String ?: "recording",
            source = row["source"] as? String ?: "MANUAL",
            triggerCheck = row["trigger_check"] as? String,
            peakVl = (row["peak_vl"] as? Number)?.toInt() ?: 0,
            violationCount = (row["violation_count"] as? Number)?.toInt() ?: 0,
            createdAt = (row["created_at"] as? Number)?.toLong() ?: 0
        )
    }
}
