package com.evidex.detection

import com.evidex.EvidexPlugin
import com.evidex.util.BukkitExtensions
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class AlertService(
    private val plugin: EvidexPlugin,
    private val violationWriter: ViolationWriter,
    private val buffer: ViolationBuffer,
    private val gate: DetectionGate
) {

    fun handle(
        player: Player,
        profile: PlayerProfile,
        result: ViolationResult
    ): ViolationRecord? {
        if (gate.isInJoinGrace(profile)) return null

        val scaled = result.copy(vl = gate.scaledVl(result.vl))
        val maxVl = buffer.maxVl(scaled.checkName)
        val vlTotal = profile.addVl(scaled.checkName, scaled.vl, maxVl)
        if (!buffer.shouldFlag(scaled.checkName, vlTotal)) return null
        if (!gate.canAlert(profile, scaled.checkName)) return null

        var recordingId = plugin.recordingManager.getActiveRecordingId(player.name)
        if (buffer.shouldAutoRecord(scaled.checkName, vlTotal) && recordingId == null) {
            plugin.recordingManager.startRecording(
                player,
                source = "AUTO",
                triggerCheck = scaled.checkName
            )
            recordingId = plugin.recordingManager.getActiveRecordingId(player.name)
        }

        val loc = player.location
        val infoJson = scaled.info.entries.joinToString(",") { """"${it.key}":"${it.value}"""" }
            .let { if (it.isEmpty()) "{}" else "{$it}" }

        val record = ViolationRecord(
            playerUuid = player.uniqueId.toString(),
            playerName = player.name,
            checkName = scaled.checkName,
            category = scaled.category,
            vlAdded = scaled.vl,
            vlTotal = vlTotal,
            severity = buffer.severity(scaled.checkName, vlTotal),
            infoJson = infoJson,
            recordingId = recordingId,
            world = loc.world?.name,
            x = loc.x,
            y = loc.y,
            z = loc.z
        )

        // Persistencia asíncrona: encolar es O(1) y no bloquea el hilo principal.
        violationWriter.enqueue(record)
        plugin.recordingManager.noteViolationForPlayer(player.name, scaled.checkName, vlTotal)
        plugin.recordingManager.tagSuspiciousEvent(
            player.name,
            "flag:${scaled.checkName}",
            infoJson
        )
        notifyStaff(record)
        return record
    }

    private fun notifyStaff(record: ViolationRecord) {
        if (!plugin.configManager.isAlertStaffEnabled()) return

        val detail = parseInfoSummary(record.infoJson)
        val msg = buildString {
            append("§6[Evidex] §e${record.playerName} §c— ${record.checkName} §7(VL: ${record.vlTotal})")
            if (detail.isNotBlank()) append(" §8— $detail")
            if (record.recordingId != null) append(" §a— Grabando")
        }
        for (staff in Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("evidex.alerts") || staff.hasPermission("evidex.admin")) {
                BukkitExtensions.sendMessage(staff, msg)
            }
        }
        plugin.log.info(
            "Flag: ${record.playerName} ${record.checkName} VL=${record.vlTotal} $detail"
        )
    }

    private fun parseInfoSummary(infoJson: String): String {
        if (infoJson.isBlank() || infoJson == "{}") return ""
        return infoJson.trim('{', '}')
            .split(",")
            .mapNotNull { part ->
                val kv = part.split(":", limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].trim().trim('"')
                    val v = kv[1].trim().trim('"')
                    "$k: $v"
                } else null
            }
            .joinToString(", ")
    }
}