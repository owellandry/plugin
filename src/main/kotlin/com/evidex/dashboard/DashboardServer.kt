package com.evidex.dashboard

import com.evidex.EvidexPlugin
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.InputStream

class DashboardServer(
    private val plugin: EvidexPlugin,
    port: Int = 9090
) : NanoHTTPD(port) {

    private val authManager = AuthManager(plugin, plugin.database)
    private val apiHandler = ApiHandler(plugin, authManager)
    private val staticDir: File = File(plugin.dataFolder, "static")

    init {
        staticDir.mkdirs()
        extractStaticFiles()
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        plugin.logger.info("Dashboard started on http://0.0.0.0:$port (with auth)")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (method == Method.OPTIONS) {
            val resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            addCors(resp)
            return resp
        }

        return when {
            uri.startsWith("/api/") -> handleApi(session, uri, method)
            else -> handleStatic(uri)
        }
    }

    private fun handleApi(session: IHTTPSession, uri: String, method: Method): Response {
        val body = mutableMapOf<String, String>()
        if (method == Method.POST) {
            try {
                session.parseBody(body)
            } catch (e: Exception) {
                return jsonError("Invalid request", Response.Status.BAD_REQUEST)
            }
        }

        val params = parsePostParams(session, body)
        val cookies = parseCookies(session.headers["cookie"])
        val token = cookies["evidex_session"]
        val authSession = authManager.validateSession(token)

        return try {
            when {
                uri == "/api/auth/login" && method == Method.POST -> {
                    val uname = params["username"] ?: ""
                    val pw = params["password"] ?: ""
                    plugin.logger.info("[Dashboard] Login attempt for user='${uname}' (pw len=${pw.length})")
                    val result = authManager.login(uname, pw)
                    if (result.success && result.token != null) {
                        plugin.logger.info("[Dashboard] Login SUCCESS for '${result.username}' mustChange=${result.mustChange}")
                        val json = """{"success":true,"token":"${result.token}","mustChange":${result.mustChange},"username":"${result.username}"}"""
                        val resp = newFixedLengthResponse(Response.Status.OK, "application/json", json)
                        resp.addHeader("Set-Cookie", "evidex_session=${result.token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=28800")
                        addCors(resp)
                        resp
                    } else {
                        plugin.logger.info("[Dashboard] Login FAILED for '${uname}': ${result.error}")
                        val msg = result.error ?: "Usuario o contraseña incorrectos"
                        val r = newFixedLengthResponse(Response.Status.OK, "application/json", """{"success":false,"error":"$msg"}""")
                        addCors(r)
                        r
                    }
                }
                uri == "/api/auth/logout" && method == Method.POST -> apiHandler.logout(token)
                uri == "/api/auth/me" && method == Method.GET -> apiHandler.me(authSession)
                uri == "/api/auth/change-password" && method == Method.POST ->
                    apiHandler.changePassword(authSession, params["current"] ?: "", params["new"] ?: "")
                uri == "/api/auth/force-change-password" && method == Method.POST ->
                    apiHandler.forceChangePassword(authSession, params["new"] ?: "")

                else -> {
                    if (authSession == null && !uri.startsWith("/api/auth/")) {
                        return jsonError("No autenticado", Response.Status.UNAUTHORIZED)
                    }
                    when {
                        uri == "/api/players" && method == Method.GET -> apiHandler.getPlayers()
                        uri == "/api/admins" && method == Method.GET -> apiHandler.getAdmins()
                        uri == "/api/recordings" && method == Method.GET -> apiHandler.getRecordings()
                        uri == "/api/recordings/active" && method == Method.GET -> apiHandler.getActiveRecordings()
                        uri == "/api/record/start" && method == Method.POST -> apiHandler.startRecording(params["player"] ?: "")
                        uri == "/api/record/stop" && method == Method.POST -> apiHandler.stopRecording(params["player"] ?: "")
                        uri == "/api/replay/start-id" && method == Method.POST ->
                            apiHandler.startReplayById((params["id"] ?: "0").toLongOrNull() ?: 0L, params["viewer"] ?: "")
                        uri == "/api/replay/stop" && method == Method.POST -> apiHandler.stopReplay(params["viewer"] ?: params["admin"] ?: "")
                        uri == "/api/recordings/delete" && method == Method.POST ->
                            apiHandler.deleteRecording((params["id"] ?: "0").toLongOrNull() ?: 0L)
                        uri.matches(Regex("^/api/recordings/\\d+$")) && method == Method.DELETE -> {
                            val id = uri.removePrefix("/api/recordings/").toLongOrNull() ?: 0L
                            apiHandler.deleteRecording(id)
                        }
                        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"Not found"}""")
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("API error: ${e.message}")
            jsonError(e.message ?: "Server error", Response.Status.INTERNAL_ERROR)
        }
    }

    private fun handleStatic(uri: String): Response {
        val path = if (uri == "/") "/index.html" else uri
        val fileName = path.trimStart('/')

        val resourcePath = "/static/$fileName"
        val stream = javaClass.getResourceAsStream(resourcePath)
        if (stream != null) {
            try {
                val content = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val mimeType = when {
                    fileName.endsWith(".html") -> "text/html"
                    fileName.endsWith(".css") -> "text/css"
                    fileName.endsWith(".js") -> "application/javascript"
                    fileName.endsWith(".png") -> "image/png"
                    fileName.endsWith(".svg") -> "image/svg+xml"
                    else -> "application/octet-stream"
                }
                val response = newFixedLengthResponse(Response.Status.OK, mimeType, content)
                addCors(response)
                return response
            } catch (e: Exception) {
                plugin.logger.warning("[Evidex] Error serving static from resources: ${e.message}")
            } finally {
                stream.close()
            }
        }

        val file = File(staticDir, fileName)
        if (!file.exists() || file.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }

        val mimeType = when {
            fileName.endsWith(".html") -> "text/html"
            fileName.endsWith(".css") -> "text/css"
            fileName.endsWith(".js") -> "application/javascript"
            fileName.endsWith(".png") -> "image/png"
            fileName.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }

        val response = newFixedLengthResponse(Response.Status.OK, mimeType, file.readText())
        addCors(response)
        return response
    }

    private fun parseCookies(cookieHeader: String?): Map<String, String> {
        if (cookieHeader.isNullOrBlank()) return emptyMap()
        return cookieHeader.split(";").mapNotNull {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    private fun parsePostParams(session: IHTTPSession, body: Map<String, String>): Map<String, String> {
        val base = session.parameters.mapValues { it.value.firstOrNull() ?: "" } + body

        val postData = body["postData"]?.trim() ?: return base
        if (!postData.startsWith("{")) return base

        val extracted = mutableMapOf<String, String>()
        try {
            val regex = """"(\w+)"\s*:\s*(?:"([^"]*)"|\b(true|false|null|-?\d+\.?\d*)\b)""".toRegex()
            regex.findAll(postData).forEach { m ->
                val key = m.groupValues[1]
                val quoted = m.groupValues[2]
                val unquoted = m.groupValues[3]
                var value = if (quoted.isNotEmpty()) quoted else unquoted
                if (value == "null" || value == "true" || value == "false") value = ""
                extracted[key] = value
            }
        } catch (_: Exception) {}
        return base + extracted
    }

    private fun addCors(resp: Response) {
        resp.addHeader("Access-Control-Allow-Origin", "*")
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Cookie")
    }

    private fun jsonError(msg: String, status: Response.Status): Response {
        val r = newFixedLengthResponse(status, "application/json", """{"success":false,"error":"$msg"}""")
        addCors(r)
        return r
    }

    private fun extractStaticFiles() {
        plugin.logger.info("[Evidex] Extracting fresh static dashboard files...")

        try {
            if (staticDir.exists()) {
                staticDir.deleteRecursively()
            }
        } catch (e: Exception) {
            plugin.logger.warning("[Evidex] Could not fully delete old static dir: ${e.message}")
        }
        staticDir.mkdirs()

        val resources = mapOf(
            "index.html" to "/static/index.html",
            "style.css" to "/static/style.css",
            "icons-module.js" to "/static/icons-module.js",
            "app.js" to "/static/app.js"
        )

        var extractedCount = 0
        for ((name, resourcePath) in resources) {
            val stream: InputStream? = javaClass.getResourceAsStream(resourcePath)
            if (stream != null) {
                try {
                    val target = File(staticDir, name)
                    target.writeBytes(stream.readBytes())
                    extractedCount++
                    plugin.logger.info("[Evidex] Extracted $name to ${target.absolutePath}")
                } catch (e: Exception) {
                    plugin.logger.warning("[Evidex] Failed to extract $name: ${e.message}")
                } finally {
                    stream.close()
                }
            } else {
                plugin.logger.warning("[Evidex] Resource not found in jar: $resourcePath")
            }
        }
        plugin.logger.info("[Evidex] Static files extraction complete. Extracted $extractedCount files.")
    }

    fun shutdown() {
        stop()
        plugin.logger.info("Dashboard stopped")
    }
}