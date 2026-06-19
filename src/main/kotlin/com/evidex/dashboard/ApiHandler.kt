package com.evidex.dashboard

import com.evidex.EvidexPlugin
import com.evidex.storage.model.RecordingMetadata
import fi.iki.elonen.NanoHTTPD
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture

class ApiHandler(
    private val plugin: EvidexPlugin,
    private val authManager: AuthManager? = null
) {

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

    // --- Existing API ---

    fun getPlayers(): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val players = Bukkit.getOnlinePlayers().map { player ->
                player.name
            }
            future.complete(players.joinToString(",", "[", "]") { "\"${it}\"" })
        })
        return jsonResponse(future.get())
    }

    fun getRecordings(): NanoHTTPD.Response {
        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val metas = plugin.recordingManager.getAllRecordingMetadata()
            val list = metas.map { meta ->
                val duration = meta.endTimestamp?.let { (it - meta.startTimestamp) / 1000.0 } ?: 0.0
                """{"id":"${meta.id}","targetPlayer":"${meta.playerName}","caseName":"","duration":$duration,"frameCount":${meta.frameCount},"world":"${meta.world ?: ""}","createdAt":${meta.createdAt}}"""
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
        if (viewerName.isBlank()) return errorResponse("Nombre del staff requerido")

        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val viewer = Bukkit.getPlayer(viewerName)
            if (viewer == null) {
                future.complete("""{"success":false,"error":"Staff no está en línea"}""")
                return@Runnable
            }
            val recording = plugin.recordingManager.getRecordingById(recordingId)
            if (recording == null) {
                future.complete("""{"success":false,"error":"Grabación no encontrada"}""")
                return@Runnable
            }
            plugin.replayManager.startReplay(viewer, recording)
            future.complete("""{"success":true,"message":"Replay iniciado para ${viewer.name}"}""")
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
            plugin.replayManager.stopReplay(viewer)
            future.complete("""{"success":true}""")
        })
        return jsonResponse(future.get())
    }

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

    fun getRecordingFrames(id: Long, offset: Int = 0, limit: Int = 500): NanoHTTPD.Response {
        if (id <= 0) return errorResponse("ID inválido")

        val future = CompletableFuture<String>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val allFrames = plugin.recordingManager.getFrames(id)
            val total = allFrames.size
            val slice = allFrames.drop(offset).take(limit)
            if (slice.isEmpty()) {
                future.complete("""{"id":"$id","offset":$offset,"limit":$limit,"total":$total,"frames":[]}""")
                return@Runnable
            }

            val json = slice.joinToString(",") { frame ->
                // flags as string list
                val flags = mutableListOf<String>()
                if (frame.onGround) flags.add("ON_GROUND")
                if (frame.isSneaking) flags.add("SNEAKING")
                if (frame.isSprinting) flags.add("SPRINTING")
                if (frame.isFlying) flags.add("FLYING")
                if (frame.handSwing) flags.add("HAND_SWING")

                // equipment as filtered list
                val eq = mutableListOf<String>()
                if (frame.equipment.mainHand?.material != null) eq.add("MH:${frame.equipment.mainHand.material}")
                if (frame.equipment.offHand?.material != null) eq.add("OH:${frame.equipment.offHand.material}")
                if (frame.equipment.helmet?.material != null) eq.add(frame.equipment.helmet.material)
                if (frame.equipment.chestplate?.material != null) eq.add(frame.equipment.chestplate.material)
                if (frame.equipment.leggings?.material != null) eq.add(frame.equipment.leggings.material)
                if (frame.equipment.boots?.material != null) eq.add(frame.equipment.boots.material)

                val flagsJson = if (flags.isEmpty()) "" else flags.joinToString("\",\"")
                val eqJson = if (eq.isEmpty()) "" else eq.joinToString("\",\"")

                val flagsArray = if (flagsJson.isEmpty()) "[]" else """["$flagsJson"]"""
                val eqArray = if (eqJson.isEmpty()) "[]" else """["$eqJson"]"""
                """{"timestamp":${frame.timestamp},"x":${frame.position.x},"y":${frame.position.y},"z":${frame.position.z},"yaw":${frame.yaw.degrees},"pitch":${frame.pitch.degrees},"flags":$flagsArray,"equipment":$eqArray}"""
            }
            future.complete("""{"id":"$id","offset":$offset,"limit":$limit,"total":$total,"frames":[$json]}""")
        })
        return jsonResponse(future.get())
    }

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
