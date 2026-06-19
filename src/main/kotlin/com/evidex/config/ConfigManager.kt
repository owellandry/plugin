package com.evidex.config

import com.evidex.EvidexPlugin
import java.io.File

class ConfigManager(private val plugin: EvidexPlugin) {

    fun loadConfig() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
    }

    // Database
    fun getDatabaseType(): String = plugin.config.getString("database.type", "sqlite")!!
    fun getDatabasePath(): String {
        val path = plugin.config.getString("database.path", "evidex.db")!!
        return resolvePath(path)
    }
    fun getDatabaseHost(): String = plugin.config.getString("database.host", "localhost")!!
    fun getDatabasePort(): Int = plugin.config.getInt("database.port", 3306)
    fun getDatabaseName(): String = plugin.config.getString("database.name", "evidex")!!
    fun getDatabaseUser(): String = plugin.config.getString("database.user", "evidex")!!
    fun getDatabasePassword(): String = plugin.config.getString("database.password", "")!!
    fun getDatabasePoolSize(): Int = plugin.config.getInt("database.pool-size", 10)
    fun getDatabaseParams(): String = plugin.config.getString("database.params", "")!!

    // Storage
    fun getFramesDirectory(): String {
        val path = plugin.config.getString("storage.frames-dir", "frames")!!
        return resolvePath(path)
    }

    // Recording
    fun getRecordingTickInterval(): Int = plugin.config.getInt("recording.tick-interval", 2)
    fun getRecordingMaxDuration(): Long = plugin.config.getLong("recording.max-duration", 3_600_000)

    // Cleanup
    fun isCleanupEnabled(): Boolean = plugin.config.getBoolean("cleanup.enabled", true)
    fun getCleanupRetentionDays(): Int = plugin.config.getInt("cleanup.retention-days", 30)
    fun getCleanupInterval(): Long = plugin.config.getLong("cleanup.interval", 3600)

    // Dashboard
    fun isDashboardEnabled(): Boolean = plugin.config.getBoolean("dashboard.enabled", true)
    fun getDashboardPort(): Int = plugin.config.getInt("dashboard.port", 9090)
    fun getDashboardBindAddress(): String = plugin.config.getString("dashboard.bind-address", "0.0.0.0")!!

    private fun resolvePath(path: String): String {
        val file = File(path)
        if (file.isAbsolute) {
            return file.absolutePath
        }
        if (path.startsWith("plugins/")) {
            return File(path).absolutePath
        }
        return File(plugin.dataFolder, path).absolutePath
    }
}
