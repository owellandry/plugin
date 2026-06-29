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
    fun getRecordingTickInterval(): Int = plugin.config.getInt("recording.tick-interval", 2).coerceAtLeast(1)
    fun getRecordingMaxDuration(): Long = plugin.config.getLong("recording.max-duration", 3_600_000)
    fun getRecordingWorldRadius(): Int = plugin.config.getInt("recording.world-radius", 64).coerceIn(16, 128)
    fun getRecordingWorldHeight(): Int = plugin.config.getInt("recording.world-height", 64).coerceIn(16, 128)
    fun isPathSnapshotEnabled(): Boolean = plugin.config.getBoolean("recording.path-snapshot-enabled", true)
    fun getPathSnapshotMaxSamples(): Int = plugin.config.getInt("recording.path-snapshot-max-samples", 12).coerceIn(2, 64)
    fun getPathSnapshotTickInterval(): Int = plugin.config.getInt("recording.path-snapshot-tick-interval", 5).coerceAtLeast(1)
    fun getWorldSnapshotSlices(): Int = plugin.config.getInt("recording.world-snapshot-slices", 16).coerceIn(4, 64)
    fun getWorldSnapshotSliceInterval(): Int = plugin.config.getInt("recording.world-snapshot-slice-interval", 3).coerceAtLeast(1)

    fun getEntityCaptureRadius(): Int =
        plugin.config.getInt("recording.entity-radius", getRecordingWorldRadius()).coerceIn(8, 128)
    fun getMaxEntitiesPerFrame(): Int =
        plugin.config.getInt("recording.max-entities-per-frame", 48).coerceIn(4, 64)
    fun isCapturePlayersEnabled(): Boolean = plugin.config.getBoolean("recording.capture-players", true)
    fun isCaptureMobsEnabled(): Boolean = plugin.config.getBoolean("recording.capture-mobs", true)
    fun isCaptureItemsEnabled(): Boolean = plugin.config.getBoolean("recording.capture-items", false)
    fun isCaptureVisibleOnly(): Boolean = plugin.config.getBoolean("recording.capture-visible-only", false)
    fun getCaptureFovDegrees(): Double = plugin.config.getDouble("recording.capture-fov-degrees", 110.0).coerceIn(30.0, 180.0)

    // Replay
    fun isReplayTeleportToWorld(): Boolean = plugin.config.getBoolean("replay.teleport-to-world", true)
    fun isReplayShowPlayerNpcs(): Boolean = plugin.config.getBoolean("replay.show-player-npcs", true)
    fun isReplayShowMobMarkers(): Boolean = plugin.config.getBoolean("replay.show-mob-markers", true)
    fun isReplayShowActionBar(): Boolean = plugin.config.getBoolean("replay.show-action-bar", true)

    fun getMaxEntitiesPerFrameWrite(): Int = getMaxEntitiesPerFrame()

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
