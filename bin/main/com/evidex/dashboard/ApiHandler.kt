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
            jsonResponse("""{"success":true,"token":"${result.token}","mustChange":${result.mustChange},"username":"${result.username}"}""")
        } else {
            errorResponse(result.error ?: "Error de autenticación")
        }
    }

    fun logout(token: String?): NanoHTTPD.Response {
        authManager?.logout(token)
        return jsonResponse("""{"success":true}""")
    }

    fun me(session: AuthManager.SessionInfo?): NanoHTTPD.Response {
        if (session == null) return unauthorized()
        return jsonResponse("""{"success":true,"username":"${session.username}"}""")
    }

    fun changePassword(session: AuthManager.SessionInfo?, current: String, newPass: String): NanoHTTPD.Response {
        if (session == null) return unauthorized()
        val result = authManager!!.changePassword(session.username, current, newPass)
        return if (result.success) {
            jsonResponse("""{"success":true}""")
        } else {
            errorResponse(result.error ?: "Error al cambiar contraseña")
        }
    }

    fun forceChangePassword(session: AuthManager.SessionInfo?, newPass: String): NanoHTTPD.Response {
        if (session == null) return unauthorized()
        val result = authManager!!.forceSetPassword(session.username, newPass)
        return if (result.success) {
            jsonResponse("""{"success":true}""")
        } else {
            errorResponse(result.error ?: "Error")
        }
    }

    // --- API ---

    fun getPlayers(): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val liveVl = if (plugin.hasDetection()) {
                plugin.detectionManager.getLiveVl().associateBy { it.playerName }
            } else emptyMap()
            val players = Bukkit.getOnlinePlayers().map { player ->
                val vl = liveVl[player.name]
                val checksJson = vl?.checks?.entries?.joinToString(",") {
                    """"${it.key}":${it.value}"""
                } ?: ""
                val checks = if (checksJson.isEmpty()) "{}" else "{$checksJson}"
                """{"name":"${escapeJson(player.name)}","ping":${player.ping},"totalVl":${vl?.totalVl ?: 0},"checks":$checks,"isRecording":${vl?.isRecording ?: false},"recordingSource":"${vl?.recordingSource ?: ""}"}"""
            }
            future.complete("[${players.joinToString(",")}]")
        })
        return jsonResponse(future.get())
    }

    fun getStats(): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val todayStart = startOfTodayMillis()
            val alertsToday = if (plugin.hasViolationRepository()) {
                plugin.violationRepository.countSince(todayStart)
            } else 0
            val flaggedPlayers = if (plugin.hasViolationRepository()) {
                plugin.violationRepository.countDistinctPlayersSince(todayStart)
            } else 0
            val autoRecordings = plugin.recordingManager.getAllRecordingMetadata()
                .count { it.source == "AUTO" }
            future.complete(
                """{"alertsToday":$alertsToday,"flaggedPlayers":$flaggedPlayers,"autoRecordings":$autoRecordings}"""
            )
        })
        return jsonResponse(future.get())
    }

    fun getAdmins(): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val admins = Bukkit.getOnlinePlayers()
                .filter { it.hasPermission(ADMIN_PERMISSION) }
                .map { it.name }
            future.complete(admins.joinToString(",", "[", "]") { "\"${it}\"" })
        })
        return jsonResponse(future.get())
    }

    fun getRecordings(): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val metas = plugin.recordingManager.getAllRecordingMetadata()
            val list = metas.map { meta ->
                val duration = meta.endTimestamp?.let { it / 1000.0 } ?: 0.0
                """{"id":"${meta.id}","targetPlayer":"${escapeJson(meta.playerName)}","duration":$duration,"frameCount":${meta.frameCount},"world":"${escapeJson(meta.world ?: "")}","createdAt":${meta.createdAt},"source":"${meta.source}","triggerCheck":"${escapeJson(meta.triggerCheck ?: "")}","peakVl":${meta.peakVl},"violationCount":${meta.violationCount}}"""
            }
            future.complete("[${list.joinToString(",")}]")
        })
        return jsonResponse(future.get())
    }

    fun getActiveRecordings(): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val active = plugin.recordingManager.getActiveRecordingNames().map { name ->
                val session = plugin.recordingManager.getSession(name)
                val frames = session?.data?.frames?.size ?: 0
                """{"targetPlayer":"$name","frameCount":$frames}"""
            }
            future.complete("[${active.joinToString(",")}]")
        })
        return jsonResponse(future.get())
    }

    fun startRecording(playerName: String): NanoHTTPD.Response {
        if (playerName.isBlank()) return errorResponse("Nombre de jugador requerido")

        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = Bukkit.getPlayer(playerName)
            if (player == null) {
                future.complete("""{"success":false,"error":"Jugador no encontrado"}""")
                return@Runnable
            }
            if (plugin.recordingManager.isRecording(player)) {
                future.complete("""{"success":false,"error":"Ya se está grabando"}""")
                return@Runnable
            }
            plugin.recordingManager.startRecording(player)
            future.complete("""{"success":true}""")
        })
        return jsonResponse(future.get())
    }

    fun stopRecording(playerName: String): NanoHTTPD.Response {
        if (playerName.isBlank()) return errorResponse("Nombre de jugador requerido")

        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = Bukkit.getPlayer(playerName)
            if (player == null) {
                future.complete("""{"success":false,"error":"Jugador no encontrado"}""")
                return@Runnable
            }
            if (!plugin.recordingManager.isRecording(player)) {
                future.complete("""{"success":false,"error":"No se está grabando"}""")
                return@Runnable
            }
            plugin.recordingManager.stopRecording(player)
            future.complete("""{"success":true}""")
        })
        return jsonResponse(future.get())
    }

    fun startReplayById(recordingId: Long, viewerName: String): NanoHTTPD.Response {
        if (viewerName.isBlank()) return errorResponse("Selecciona un admin online")

        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val viewer = Bukkit.getPlayer(viewerName)
            if (viewer == null) {
                future.complete("""{"success":false,"error":"El admin no está en línea"}""")
                return@Runnable
            }
            if (!viewer.hasPermission(ADMIN_PERMISSION)) {
                future.complete("""{"success":false,"error":"Solo admins con permiso evidex.admin pueden ver replays"}""")
                return@Runnable
            }
            val recording = plugin.recordingManager.getRecordingById(recordingId)
            if (recording == null) {
                future.complete("""{"success":false,"error":"Grabación no encontrada"}""")
                return@Runnable
            }
            plugin.replayManager.startReplay(viewer, recording)
            future.complete("""{"success":true,"message":"Replay iniciado en el juego para ${viewer.name}"}""")
        })
        return jsonResponse(future.get())
    }

    fun stopReplay(viewerName: String): NanoHTTPD.Response {
        if (viewerName.isBlank()) return errorResponse("Nombre requerido")

        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val viewer = Bukkit.getPlayer(viewerName)
            if (viewer == null) {
                future.complete("""{"success":false,"error":"Jugador no encontrado"}""")
                return@Runnable
            }
            if (!viewer.hasPermission(ADMIN_PERMISSION)) {
                future.complete("""{"success":false,"error":"Solo admins pueden detener replays"}""")
                return@Runnable
            }
            plugin.replayManager.stopReplay(viewer)
            future.complete("""{"success":true}""")
        })
        return jsonResponse(future.get())
    }

    fun getReplayStatus(viewerName: String?): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val sessions = plugin.replayManager.getActiveSessions()
            val filtered = if (viewerName.isNullOrBlank()) {
                sessions
            } else {
                sessions.filter { it.viewer.name.equals(viewerName, ignoreCase = true) }
            }
            val list = filtered.map { sessionToJson(it) }
            future.complete("[${list.joinToString(",")}]")
        })
        return jsonResponse(future.get())
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

        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val viewer = Bukkit.getPlayer(viewerName)
            if (viewer == null) {
                future.complete("""{"success":false,"error":"El admin no está en línea"}""")
                return@Runnable
            }
            if (!viewer.hasPermission(ADMIN_PERMISSION)) {
                future.complete("""{"success":false,"error":"Solo admins con permiso evidex.admin"}""")
                return@Runnable
            }
            val session = plugin.replayManager.getSession(viewer)
            if (session == null || !session.isActive) {
                future.complete("""{"success":false,"error":"No hay replay activo para ${viewer.name}"}""")
                return@Runnable
            }
            block(viewer)
            future.complete("""{"success":true,"session":${sessionToJson(session)}}""")
        })
        return jsonResponse(future.get())
    }

    private fun sessionToJson(session: com.evidex.playback.ReplaySession): String =
        """{"viewer":"${escapeJson(session.viewer.name)}","targetPlayer":"${escapeJson(session.recording.playerName)}","recordingId":${session.recording.recordingId},"frameIndex":${session.frameIndex},"frameCount":${session.frameCount},"paused":${session.isPaused},"speed":${session.playbackSpeed},"active":${session.isActive}}"""

    fun deleteRecording(id: Long): NanoHTTPD.Response {
        if (id <= 0) return errorResponse("ID inválido")

        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val ok = plugin.recordingManager.deleteRecording(id)
            if (ok) {
                future.complete("""{"success":true}""")
            } else {
                future.complete("""{"success":false,"error":"No se pudo eliminar"}""")
            }
        })
        return jsonResponse(future.get())
    }

    fun getViolations(limit: Int): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val list = if (plugin.hasViolationRepository()) {
                plugin.violationRepository.findRecent(limit.coerceIn(1, 200))
            } else emptyList()
            future.complete("[${list.joinToString(",") { violationToJson(it) }}]")
        })
        return jsonResponse(future.get())
    }

    fun getViolationsLive(): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val list = if (plugin.hasDetection()) {
                plugin.detectionManager.getLiveVl()
            } else emptyList()
            val json = list.joinToString(",") { vl ->
                val checks = vl.checks.entries.joinToString(",") { """"${it.key}":${it.value}""" }
                val checksObj = if (checks.isEmpty()) "{}" else "{$checks}"
                """{"playerName":"${escapeJson(vl.playerName)}","playerUuid":"${vl.playerUuid}","totalVl":${vl.totalVl},"checks":$checksObj,"isRecording":${vl.isRecording},"recordingSource":"${vl.recordingSource ?: ""}"}"""
            }
            future.complete("[$json]")
        })
        return jsonResponse(future.get())
    }

    fun getViolationsForPlayer(playerName: String): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val list = if (plugin.hasViolationRepository()) {
                plugin.violationRepository.findByPlayer(playerName)
            } else emptyList()
            future.complete("[${list.joinToString(",") { violationToJson(it) }}]")
        })
        return jsonResponse(future.get())
    }

    fun getViolationsForRecording(recordingId: Long): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val list = if (plugin.hasViolationRepository()) {
                plugin.violationRepository.findByRecordingId(recordingId)
            } else emptyList()
            future.complete("[${list.joinToString(",") { violationToJson(it) }}]")
        })
        return jsonResponse(future.get())
    }

    private fun violationToJson(v: com.evidex.detection.ViolationRecord): String =
        """{"id":${v.id},"playerName":"${escapeJson(v.playerName)}","checkName":"${escapeJson(v.checkName)}","category":"${v.category.name}","vlAdded":${v.vlAdded},"vlTotal":${v.vlTotal},"severity":"${v.severity.name}","info":${v.infoJson.ifBlank { "{}" }},"recordingId":${v.recordingId ?: "null"},"world":"${escapeJson(v.world ?: "")}","timestamp":${v.timestamp}}"""

    private fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun jsonResponse(json: String, status: NanoHTTPD.Response.Status = NanoHTTPD.Response.Status.OK): NanoHTTPD.Response {
        val response = NanoHTTPD.newFixedLengthResponse(status, "application/json", json)
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Cookie")
        return response
    }

    private fun errorResponse(message: String): NanoHTTPD.Response {
        return jsonResponse("""{"success":false,"error":"$message"}""", NanoHTTPD.Response.Status.BAD_REQUEST)
    }

    private fun unauthorized(): NanoHTTPD.Response {
        return jsonResponse("""{"success":false,"error":"No autenticado"}""", NanoHTTPD.Response.Status.UNAUTHORIZED)
    }
}