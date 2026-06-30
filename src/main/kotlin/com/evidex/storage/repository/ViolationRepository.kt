package com.evidex.storage.repository

import com.evidex.detection.ViolationCategory
import com.evidex.detection.ViolationRecord
import com.evidex.detection.ViolationSeverity
import com.evidex.storage.database.Database

class ViolationRepository(private val db: Database) {

    fun create(record: ViolationRecord): ViolationRecord {
        val sql = """
            INSERT INTO violations
                (player_uuid, player_name, check_name, category, vl_added, vl_total,
                 severity, info_json, recording_id, world, x, y, z, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        db.executeUpdate(sql, listOf(
            record.playerUuid,
            record.playerName,
            record.checkName,
            record.category.name,
            record.vlAdded,
            record.vlTotal,
            record.severity.name,
            record.infoJson,
            record.recordingId,
            record.world,
            record.x,
            record.y,
            record.z,
            record.timestamp
        ))
        val id = db.executeQuery(
            "SELECT MAX(id) as id FROM violations WHERE player_uuid = ? AND timestamp = ?",
            listOf(record.playerUuid, record.timestamp)
        ).firstOrNull()?.get("id")
        return record.copy(id = (id as? Number)?.toLong() ?: 0)
    }

    /** Inserta varias violaciones en un solo lote (usado por el writer async). */
    fun createBatch(records: List<ViolationRecord>) {
        if (records.isEmpty()) return
        val sql = """
            INSERT INTO violations
                (player_uuid, player_name, check_name, category, vl_added, vl_total,
                 severity, info_json, recording_id, world, x, y, z, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        db.executeBatch(sql, records.map { r ->
            listOf(
                r.playerUuid, r.playerName, r.checkName, r.category.name,
                r.vlAdded, r.vlTotal, r.severity.name, r.infoJson,
                r.recordingId, r.world, r.x, r.y, r.z, r.timestamp
            )
        })
    }

    fun findRecent(limit: Int): List<ViolationRecord> {
        val results = db.executeQuery(
            "SELECT * FROM violations ORDER BY timestamp DESC LIMIT ?",
            listOf(limit.coerceIn(1, 500))
        )
        return results.map { mapRow(it) }
    }

    fun findByPlayer(playerName: String, limit: Int = 100): List<ViolationRecord> {
        val results = db.executeQuery(
            "SELECT * FROM violations WHERE player_name = ? ORDER BY timestamp DESC LIMIT ?",
            listOf(playerName, limit.coerceIn(1, 500))
        )
        return results.map { mapRow(it) }
    }

    fun findByRecordingId(recordingId: Long): List<ViolationRecord> {
        val results = db.executeQuery(
            "SELECT * FROM violations WHERE recording_id = ? ORDER BY timestamp ASC",
            listOf(recordingId)
        )
        return results.map { mapRow(it) }
    }

    fun countSince(timestamp: Long): Int {
        val row = db.executeQuery(
            "SELECT COUNT(*) as cnt FROM violations WHERE timestamp >= ?",
            listOf(timestamp)
        ).firstOrNull()
        return (row?.get("cnt") as? Number)?.toInt() ?: 0
    }

    fun countDistinctPlayersSince(timestamp: Long): Int {
        val row = db.executeQuery(
            "SELECT COUNT(DISTINCT player_name) as cnt FROM violations WHERE timestamp >= ?",
            listOf(timestamp)
        ).firstOrNull()
        return (row?.get("cnt") as? Number)?.toInt() ?: 0
    }

    private fun mapRow(row: Map<String, Any?>): ViolationRecord {
        return ViolationRecord(
            id = (row["id"] as? Number)?.toLong() ?: 0,
            playerUuid = row["player_uuid"] as? String ?: "",
            playerName = row["player_name"] as? String ?: "",
            checkName = row["check_name"] as? String ?: "",
            category = runCatching {
                ViolationCategory.valueOf(row["category"] as? String ?: "MOVEMENT")
            }.getOrDefault(ViolationCategory.MOVEMENT),
            vlAdded = (row["vl_added"] as? Number)?.toInt() ?: 0,
            vlTotal = (row["vl_total"] as? Number)?.toInt() ?: 0,
            severity = runCatching {
                ViolationSeverity.valueOf(row["severity"] as? String ?: "LOW")
            }.getOrDefault(ViolationSeverity.LOW),
            infoJson = row["info_json"] as? String ?: "{}",
            recordingId = (row["recording_id"] as? Number)?.toLong(),
            world = row["world"] as? String,
            x = (row["x"] as? Number)?.toDouble(),
            y = (row["y"] as? Number)?.toDouble(),
            z = (row["z"] as? Number)?.toDouble(),
            timestamp = (row["timestamp"] as? Number)?.toLong() ?: 0
        )
    }
}