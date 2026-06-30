package com.evidex.dashboard

import com.evidex.EvidexPlugin
import com.evidex.storage.database.Database
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Simple authentication manager for the Evidex dashboard.
 * On first install a random temporary password for 'admin' is printed to the
 * server console (must be changed on first login). No hardcoded credentials.
 */
class AuthManager(
    private val plugin: EvidexPlugin,
    private val database: Database
) {

    data class Session(val username: String, val expiresAt: Long)

    private val sessions = ConcurrentHashMap<String, Session>()
    private val random = SecureRandom()

    /** Estado de lockout por usuario: intentos fallidos + instante de desbloqueo. */
    private data class Attempts(val count: Int, val lockedUntil: Long)
    private val failedLogins = ConcurrentHashMap<String, Attempts>()

    companion object {
        private const val SESSION_DURATION_MS = 1000L * 60 * 60 * 8 // 8 hours
        private const val PBKDF2_ITERATIONS = 600_000 // OWASP 2024 floor para PBKDF2-HMAC-SHA256
        private const val PBKDF2_KEYLEN = 256
        private const val USERNAME_MAX = 32
        private const val MIN_PASSWORD_LEN = 8
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_MS = 1000L * 60 * 5 // 5 min
    }

    init {
        ensureUsersTable()
        ensureDefaultAdmin()
    }

    private fun ensureUsersTable() {
        try {
            // Create the table if it didn't exist in this DB file (defensive for upgrades)
            database.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    must_change_password INTEGER DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    last_login INTEGER
                )
                """
            )
            // Also try a safe index (ignore errors on other DBs)
            try { database.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)") } catch (_: Exception) {}
        } catch (e: Exception) {
            plugin.log.warn("No se pudo crear tabla de usuarios: ${e.message}")
        }
    }

    private fun ensureDefaultAdmin() {
        try {
            val existing = getUser("admin")
            if (existing == null) {
                val tempPassword = generateTempPassword()
                createUser("admin", hashPassword(tempPassword), mustChange = true)
                plugin.log.warn("════════════════════════════════════════════════════════")
                plugin.log.warn("Dashboard: usuario 'admin' creado.")
                plugin.log.warn("  Contraseña TEMPORAL: $tempPassword")
                plugin.log.warn("  Cámbiala en el primer inicio de sesión. No se vuelve a mostrar.")
                plugin.log.warn("════════════════════════════════════════════════════════")
            }
        } catch (e: Exception) {
            plugin.log.warn("No se pudo crear usuario admin: ${e.message}")
        }
    }

    /** Contraseña temporal aleatoria (alfabeto sin caracteres ambiguos). */
    private fun generateTempPassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        return buildString { repeat(16) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    // --- User operations (lightweight, direct SQL via db) ---

    private fun createUser(username: String, passwordHash: String, mustChange: Boolean) {
        val now = System.currentTimeMillis()
        val sql = """
            INSERT INTO users (username, password_hash, must_change_password, created_at, last_login)
            VALUES (?, ?, ?, ?, NULL)
        """
        database.executeUpdate(sql, listOf(
            username.lowercase(),
            passwordHash,
            if (mustChange) 1 else 0,
            now
        ))
    }

    fun getUser(username: String): Map<String, Any?>? {
        val results = database.executeQuery(
            "SELECT * FROM users WHERE username = ? LIMIT 1",
            listOf(username.lowercase())
        )
        val row = results.firstOrNull() ?: return null
        // Normalize keys to lowercase for compatibility across DBs / JDBC versions
        return row.mapKeys { (key, _) -> key.lowercase() }
    }

    fun updatePassword(username: String, newHash: String, mustChange: Boolean = false) {
        val sql = """
            UPDATE users SET password_hash = ?, must_change_password = ? WHERE username = ?
        """
        database.executeUpdate(sql, listOf(newHash, if (mustChange) 1 else 0, username.lowercase()))
    }

    fun updateLastLogin(username: String) {
        val now = System.currentTimeMillis()
        database.executeUpdate(
            "UPDATE users SET last_login = ? WHERE username = ?",
            listOf(now, username.lowercase())
        )
    }

    fun listUsers(): List<Map<String, Any?>> {
        return database.executeQuery("SELECT id, username, must_change_password, created_at, last_login FROM users ORDER BY username", emptyList())
    }

    // --- Password hashing ---

    fun hashPassword(password: String): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEYLEN)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val hashB64 = Base64.getEncoder().encodeToString(hash)
        return "$saltB64:$PBKDF2_ITERATIONS:$hashB64"
    }

    fun verifyPassword(password: String, stored: String): Boolean {
        return try {
            val parts = stored.split(":")
            if (parts.size != 3) return false
            val salt = Base64.getDecoder().decode(parts[0])
            val iterations = parts[1].toInt()
            val hash = Base64.getDecoder().decode(parts[2])
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, hash.size * 8)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val testHash = factory.generateSecret(spec).encoded
            hash.contentEquals(testHash)
        } catch (e: Exception) {
            false
        }
    }

    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LEN

    // --- Auth / Sessions ---

    fun login(username: String, password: String): LoginResult {
        val lowerUser = username.lowercase()
        val now = System.currentTimeMillis()

        // Lockout: tras MAX_FAILED_ATTEMPTS fallos, bloquea LOCKOUT_MS.
        failedLogins[lowerUser]?.let { att ->
            if (att.lockedUntil > now) {
                val mins = ((att.lockedUntil - now) / 60000) + 1
                return LoginResult(false, error = "Demasiados intentos. Espera $mins min.")
            }
        }

        val user = getUser(username)
        val storedHash = user?.get("password_hash") as? String
        if (storedHash == null || !verifyPassword(password, storedHash)) {
            registerFailure(lowerUser)
            return LoginResult(false, error = "Usuario o contraseña incorrectos")
        }

        // Login correcto: limpia intentos fallidos.
        failedLogins.remove(lowerUser)

        val mustChange = (user["must_change_password"] as? Number)?.toInt() == 1

        val token = generateToken()
        sessions[token] = Session(lowerUser, now + SESSION_DURATION_MS)
        updateLastLogin(username)

        return LoginResult(true, token = token, mustChange = mustChange, username = lowerUser)
    }

    private fun registerFailure(lowerUser: String) {
        val now = System.currentTimeMillis()
        failedLogins.compute(lowerUser) { _, prev ->
            val count = (prev?.count ?: 0) + 1
            val lockedUntil = if (count >= MAX_FAILED_ATTEMPTS) now + LOCKOUT_MS else 0L
            if (lockedUntil > 0L) {
                plugin.log.warn("Dashboard: usuario '$lowerUser' bloqueado ${LOCKOUT_MS / 60000} min por $count intentos fallidos")
            }
            Attempts(count, lockedUntil)
        }
    }

    fun validateSession(token: String?): SessionInfo? {
        if (token.isNullOrBlank()) return null
        val session = sessions[token] ?: return null
        if (System.currentTimeMillis() > session.expiresAt) {
            sessions.remove(token)
            return null
        }
        return SessionInfo(session.username)
    }

    fun logout(token: String?) {
        if (!token.isNullOrBlank()) sessions.remove(token)
    }

    fun changePassword(username: String, currentPassword: String, newPassword: String): ChangeResult {
        if (!isValidPassword(newPassword)) {
            return ChangeResult(false, "La nueva contraseña debe tener al menos $MIN_PASSWORD_LEN caracteres")
        }

        val user = getUser(username) ?: return ChangeResult(false, "Usuario no encontrado")
        val stored = user["password_hash"] as? String ?: return ChangeResult(false, "Error")
        if (!verifyPassword(currentPassword, stored)) {
            return ChangeResult(false, "Contraseña actual incorrecta")
        }

        val newHash = hashPassword(newPassword)
        updatePassword(username, newHash, mustChange = false)
        return ChangeResult(true)
    }

    // Force set new password (used after first login)
    fun forceSetPassword(username: String, newPassword: String): ChangeResult {
        if (!isValidPassword(newPassword)) {
            return ChangeResult(false, "La contraseña debe tener al menos $MIN_PASSWORD_LEN caracteres")
        }
        val newHash = hashPassword(newPassword)
        updatePassword(username, newHash, mustChange = false)
        return ChangeResult(true)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    data class LoginResult(
        val success: Boolean,
        val token: String? = null,
        val mustChange: Boolean = false,
        val username: String? = null,
        val error: String? = null
    )

    data class ChangeResult(val success: Boolean, val error: String? = null)
    data class SessionInfo(val username: String)
}
