package com.evidex.dashboard

import com.evidex.EvidexPlugin
import fi.iki.elonen.NanoHTTPD
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture

class ApiHandler(
    private val plugin: EvidexPlugin,
    private val authManager: AuthManager? = null
) {

    companion object {
        private const val ADMIN_PERMISSION = "evidex.admin"
    }

    // --- Auth ---

    fun login(username: String, password: String): NanoHTTPD.Response {
        if (authManager == null) return errorResponse("Auth not enabled")
        val result = authManager.login(username, password)
        return if (result.success) {
            okJson(Json.obj(
                "success" to true,
                "token" to result.token,
                "mustChange" to result.mustChange,
                "username" to result.username
            ))
        } else {
            errorResponse(result.error ?: "Error de autenticación")
        }
    }

    fun logout(token: String?): NanoHTTPD.Response {
        authManager?.logout(token)
        return okJson(Json.obj("success" to true))
    }

    fun me(session: AuthManager.SessionInfo?): NanoHTTPD.Response {
        if (session == null) return unauthorized()
        return okJson(Json.obj("success" to true, "username" to session.username))
    }

    fun changePassword(session: AuthManager.SessionInfo?, current: String, newPass: String): NanoHTTPD.Response {
        if (session == null) return unauthorized()
        val result = authManager!!.changePassword(session.username, current, newPass)
        return if (result.success) okJson(Json.obj("success" to true))
        else errorResponse(result.error ?: "Error al cambiar contraseña")
    }

    fun forceChangePassword(session: AuthManager.SessionInfo?, newPass: String): NanoHTTPD.Response {
        if (session == null) return unauthorized()
        val result = authManager!!.forceSetPassword(session.username, newPass)
        return if (result.success) okJson(Json.obj("success" to true))
        else errorResponse(result.error ?: "Error")
    }

    // --- API ---

    fun getPlayers(): NanoHTTPD.Response = onMainThreadJson {
        val liveVl = if (plugin.hasDetection()) {
            plugin.detectionManager.getLiveVl().associateBy { it.playerName }
        } else emptyMap()
        Bukkit.getOnlinePlayers().map { player ->
            val vl = liveVl[player.name]
            Json.obj(
                "name" to player.name,
                "ping" to player.ping,
                "totalVl" to (vl?.totalVl ?: 0),
                "checks" to (vl?.checks ?: emptyMap<String, Int>()),
                "isRecording" to (vl?.isRecording ?: false),
                "recordingSource" to (vl?.recordingSource ?: "")
            )
        }
    }

    fun getStats(): NanoHTTPD.Response = onMainThreadJson {
        val todayStart = startOfTodayMillis()
        val alertsToday = if (plugin.hasViolationRepository())
            plugin.violationRepository.countSince(todayStart) else 0
        val flaggedPlayers = if (plugin.hasViolationRepository())
            plugin.violationRepository.countDistinctPlayersSince(todayStart) else 0
        val autoRecordings = plugin.recordingManager.getAllRecordingMetadata().count { it.source == "AUTO" }
        Json.obj(
            "alertsToday" to alertsToday,
            "flaggedPlayers" to flaggedPlayers,
            "autoRecordings" to autoRecordings
        )
    }

    fun getAdmins(): NanoHTTPD.Response = onMainThreadJson {
        Bukkit.getOnlinePlayers()
            .filter { it.hasPermission(ADMIN_PERMISSION) }
            .map { it.name }
    }

    fun getRecordings(): NanoHTTPD.Response = onMainThreadJson {
        plugin.recordingManager.getAllRecordingMetadata().map { meta ->
            Json.obj(
                "id" to meta.id.toString(),
                "targetPlayer" to meta.playerName,
                "duration" to (meta.endTimestamp?.let { it / 1000.0 } ?: 0.0),
                "frameCount" to meta.frameCount,
                "world" to (meta.world ?: ""),
                "createdAt" to meta.createdAt,
                "source" to meta.source,
                "triggerCheck" to (meta.triggerCheck ?: ""),
                "peakVl" to meta.peakVl,
                "violationCount" to meta.violationCount
            )
        }
    }

    fun getActiveRecordings(): NanoHTTPD.Response = onMainThreadJson {
        plugin.recordingManager.getActiveRecordingNames().map { name ->
            val session = plugin.recordingManager.getSession(name)
            Json.obj(
                "targetPlayer" to name,
                "frameCount" to (session?.data?.frames?.size ?: 0)
            )
        }
    }

    fun startRecording(playerName: String): NanoHTTPD.Response {
        if (playerName.isBlank()) return errorResponse("Nombre de jugador requerido")
        return onMainThreadJson {
            val player = Bukkit.getPlayer(playerName)
                ?: return@onMainThreadJson fail("Jugador no encontrado")
            if (plugin.recordingManager.isRecording(player)) return@onMainThreadJson fail("Ya se está grabando")
            plugin.recordingManager.startRecording(player)
            Json.obj("success" to true)
        }
    }

    fun stopRecording(playerName: String): NanoHTTPD.Response {
        if (playerName.isBlank()) return errorResponse("Nombre de jugador requerido")
        return onMainThreadJson {
            val player = Bukkit.getPlayer(playerName)
                ?: return@onMainThreadJson fail("Jugador no encontrado")
            if (!plugin.recordingManager.isRecording(player)) return@onMainThreadJson fail("No se está grabando")
            plugin.recordingManager.stopRecording(player)
            Json.obj("success" to true)
        }
    }

    fun startReplayById(recordingId: Long, viewerName: String): NanoHTTPD.Response {
        if (viewerName.isBlank()) return errorResponse("Selecciona un admin online")
        return onMainThreadJson {
            val viewer = Bukkit.getPlayer(viewerName)
                ?: return@onMainThreadJson fail("El admin no está en línea")
            if (!viewer.hasPermission(ADMIN_PERMISSION))
                return@onMainThreadJson fail("Solo admins con permiso evidex.admin pueden ver replays")
            val recording = plugin.recordingManager.getRecordingById(recordingId)
                ?: return@onMainThreadJson fail("Grabación no encontrada")
            plugin.replayManager.startReplay(viewer, recording)
            Json.obj("success" to true, "message" to "Replay iniciado en el juego para ${viewer.name}")
        }
    }

    fun stopReplay(viewerName: String): NanoHTTPD.Response {
        if (viewerName.isBlank()) return errorResponse("Nombre requerido")
        return onMainThreadJson {
            val viewer = Bukkit.getPlayer(viewerName)
                ?: return@onMainThreadJson fail("Jugador no encontrado")
            if (!viewer.hasPermission(ADMIN_PERMISSION))
                return@onMainThreadJson fail("Solo admins pueden detener replays")
            plugin.replayManager.stopReplay(viewer)
            Json.obj("success" to true)
        }
    }

    fun getReplayStatus(viewerName: String?): NanoHTTPD.Response = onMainThreadJson {
        val sessions = plugin.replayManager.getActiveSessions()
        val filtered = if (viewerName.isNullOrBlank()) sessions
        else sessions.filter { it.viewer.name.equals(viewerName, ignoreCase = true) }
        filtered.map { sessionMap(it) }
    }

    fun pauseReplay(viewerName: String): NanoHTTPD.Response =
        replayControl(viewerName) { viewer -> plugin.replayManager.pause(viewer) }

    fun resumeReplay(viewerName: String): NanoHTTPD.Response =
        replayControl(viewerName) { viewer -> plugin.replayManager.resume(viewer) }

    fun setReplaySpeed(viewerName: String, speed: Double): NanoHTTPD.Response =
        replayControl(viewerName) { viewer -> plugin.replayManager.setSpeed(viewer, speed) }

    fun skipReplayFlag(viewerName: String): NanoHTTPD.Response =
        replayControl(viewerName) { viewer -> plugin.replayManager.skipToNextFlag(viewer) }

    private fun replayControl(
        viewerName: String,
        block: (org.bukkit.entity.Player) -> Unit
    ): NanoHTTPD.Response {
        if (viewerName.isBlank()) return errorResponse("Selecciona un admin online")
        return onMainThreadJson {
            val viewer = Bukkit.getPlayer(viewerName)
                ?: return@onMainThreadJson fail("El admin no está en línea")
            if (!viewer.hasPermission(ADMIN_PERMISSION))
                return@onMainThreadJson fail("Solo admins con permiso evidex.admin")
            val session = plugin.replayManager.getSession(viewer)
            if (session == null || !session.isActive)
                return@onMainThreadJson fail("No hay replay activo para ${viewer.name}")
            block(viewer)
            Json.obj("success" to true, "session" to sessionMap(session))
        }
    }

    private fun sessionMap(session: com.evidex.playback.ReplaySession): Map<String, Any?> =
        Json.obj(
            "viewer" to session.viewer.name,
            "targetPlayer" to session.recording.playerName,
            "recordingId" to session.recording.recordingId,
            "frameIndex" to session.frameIndex,
            "frameCount" to session.frameCount,
            "paused" to session.isPaused,
            "speed" to session.playbackSpeed,
            "active" to session.isActive
        )

    fun deleteRecording(id: Long): NanoHTTPD.Response {
        if (id <= 0) return errorResponse("ID inválido")
        return onMainThreadJson {
            if (plugin.recordingManager.deleteRecording(id)) Json.obj("success" to true)
            else fail("No se pudo eliminar")
        }
    }

    fun getViolations(limit: Int): NanoHTTPD.Response = onMainThreadJson {
        val list = if (plugin.hasViolationRepository())
            plugin.violationRepository.findRecent(limit.coerceIn(1, 200)) else emptyList()
        list.map { violationMap(it) }
    }

    fun getViolationsLive(): NanoHTTPD.Response = onMainThreadJson {
        val list = if (plugin.hasDetection()) plugin.detectionManager.getLiveVl() else emptyList()
        list.map { vl ->
            Json.obj(
                "playerName" to vl.playerName,
                "playerUuid" to vl.playerUuid,
                "totalVl" to vl.totalVl,
                "checks" to vl.checks,
                "isRecording" to vl.isRecording,
                "recordingSource" to (vl.recordingSource ?: "")
            )
        }
    }

    fun getViolationsForPlayer(playerName: String): NanoHTTPD.Response = onMainThreadJson {
        val list = if (plugin.hasViolationRepository())
            plugin.violationRepository.findByPlayer(playerName) else emptyList()
        list.map { violationMap(it) }
    }

    fun getViolationsForRecording(recordingId: Long): NanoHTTPD.Response = onMainThreadJson {
        val list = if (plugin.hasViolationRepository())
            plugin.violationRepository.findByRecordingId(recordingId) else emptyList()
        list.map { violationMap(it) }
    }

    private fun violationMap(v: com.evidex.detection.ViolationRecord): Map<String, Any?> =
        Json.obj(
            "id" to v.id,
            "playerName" to v.playerName,
            "checkName" to v.checkName,
            "category" to v.category.name,
            "vlAdded" to v.vlAdded,
            "vlTotal" to v.vlTotal,
            "severity" to v.severity.name,
            "info" to Json.embed(v.infoJson),
            "recordingId" to v.recordingId,
            "world" to (v.world ?: ""),
            "timestamp" to v.timestamp
        )

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Marca un fallo de operación; serializado como `{"success":false,"error":...}`. */
    private fun fail(message: String): Map<String, Any?> = Json.obj("success" to false, "error" to message)

    /**
     * Ejecuta [producer] en el hilo principal (Bukkit API safe), serializa su
     * resultado a JSON y responde. El productor devuelve cualquier valor
     * serializable (Map/List/primitivo).
     */
    private fun onMainThreadJson(producer: () -> Any?): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                future.complete(Json.stringify(producer()))
            } catch (e: Exception) {
                future.complete(Json.stringify(fail(e.message ?: "Error interno")))
            }
        })
        return jsonResponse(future.get())
    }

    private fun okJson(value: Any?): NanoHTTPD.Response = jsonResponse(Json.stringify(value))

    private fun jsonResponse(
        json: String,
        status: NanoHTTPD.Response.Status = NanoHTTPD.Response.Status.OK
    ): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(status, "application/json", json)

    private fun errorResponse(message: String): NanoHTTPD.Response =
        jsonResponse(Json.stringify(fail(message)), NanoHTTPD.Response.Status.BAD_REQUEST)

    private fun unauthorized(): NanoHTTPD.Response =
        jsonResponse(Json.stringify(fail("No autenticado")), NanoHTTPD.Response.Status.UNAUTHORIZED)
}
