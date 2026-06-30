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
    fun isReplayIsolateLiveEntities(): Boolean = plugin.config.getBoolean("replay.isolate-live-entities", true)
    fun isReplayApplyWorldSnapshot(): Boolean = plugin.config.getBoolean("replay.apply-world-snapshot", true)
    fun isReplayLockInput(): Boolean = plugin.config.getBoolean("replay.lock-input", true)
    fun getReplayWorldOverlayBlocksPerTick(): Int =
        plugin.config.getInt("replay.world-overlay-blocks-per-tick", 3000).coerceIn(500, 20000)

    // Cleanup
    fun isCleanupEnabled(): Boolean = plugin.config.getBoolean("cleanup.enabled", true)
    fun getCleanupRetentionDays(): Int = plugin.config.getInt("cleanup.retention-days", 30)
    fun getCleanupInterval(): Long = plugin.config.getLong("cleanup.interval", 3600)

    // Dashboard
    fun isDashboardEnabled(): Boolean = plugin.config.getBoolean("dashboard.enabled", true)
    fun getDashboardPort(): Int = plugin.config.getInt("dashboard.port", 9090)
    fun getDashboardBindAddress(): String = plugin.config.getString("dashboard.bind-address", "127.0.0.1")!!

    // Detection
    fun isDetectionEnabled(): Boolean = plugin.config.getBoolean("detection.enabled", true)
    fun getExemptPermission(): String = plugin.config.getString("detection.exempt-permission", "evidex.bypass")!!
    fun isExemptCreative(): Boolean = plugin.config.getBoolean("detection.exempt-creative", true)
    fun isExemptSpectator(): Boolean = plugin.config.getBoolean("detection.exempt-spectator", true)
    fun getDetectionDisabledWorlds(): List<String> =
        plugin.config.getStringList("detection.disabled-worlds")
    fun getDetectionEnabledWorlds(): List<String> =
        plugin.config.getStringList("detection.enabled-worlds-only")
    fun getJoinGraceSeconds(): Int = plugin.config.getInt("detection.join-grace-seconds", 5).coerceAtLeast(0)
    fun getSensitivityMultiplier(): Double =
        plugin.config.getDouble("detection.sensitivity-multiplier", 1.0).coerceIn(0.25, 3.0)
    fun getAlertCooldownMs(): Long = plugin.config.getLong("detection.alert-cooldown-ms", 1500).coerceAtLeast(0)
    fun getVlDecayFactor(): Double =
        plugin.config.getDouble("detection.vl-decay-factor", 0.85).coerceIn(0.5, 0.99)
    fun isLagCompensationEnabled(): Boolean = plugin.config.getBoolean("detection.lag-compensation.enabled", true)
    fun getLagReachPerMs(): Double = plugin.config.getDouble("detection.lag-compensation.reach-per-ms", 0.003)
    fun isLagUseTransactions(): Boolean = plugin.config.getBoolean("detection.lag-compensation.use-transactions", true)
    fun getLagTransactionIntervalTicks(): Long =
        plugin.config.getLong("detection.lag-compensation.transaction-interval-ticks", 20).coerceAtLeast(10)
    fun getLagMaxPingMs(): Int = plugin.config.getInt("detection.lag-compensation.max-ping-ms", 400).coerceAtLeast(50)
    fun getLagMinTps(): Double = plugin.config.getDouble("detection.lag-compensation.min-tps", 18.0).coerceIn(5.0, 20.0)
    fun isAutoRecordOnFlag(): Boolean = plugin.config.getBoolean("detection.auto-record-on-flag", true)
    fun isAutoRecordOnFirstFlag(): Boolean = plugin.config.getBoolean("detection.auto-record-on-first-flag", true)
    fun getAutoRecordMinVl(): Int = plugin.config.getInt("detection.auto-record-min-vl", 5)
    fun isAlertStaffEnabled(): Boolean = plugin.config.getBoolean("detection.alert-staff", true)
    fun getDecayIntervalTicks(): Long = plugin.config.getLong("detection.decay-interval-ticks", 100)

    fun isCheckEnabled(check: String): Boolean =
        plugin.config.getBoolean("detection.checks.$check.enabled", true)

    fun getCheckMaxVl(check: String): Int =
        plugin.config.getInt("detection.checks.$check.max-vl", 20).coerceAtLeast(1)

    fun getCheckFlagVl(check: String): Int =
        plugin.config.getInt("detection.checks.$check.flag-vl", 5).coerceAtLeast(1)

    fun getCheckMaxReach(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.max-distance", 3.5).coerceAtLeast(3.0)

    fun getCheckMaxSpeed(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.max-speed", 0.85).coerceAtLeast(0.3)

    fun getCheckMaxAirTicks(check: String): Int =
        plugin.config.getInt("detection.checks.$check.max-air-ticks", 40).coerceAtLeast(10)

    fun getCheckMaxAngle(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.max-angle", 75.0).coerceIn(30.0, 180.0)

    fun getCheckMaxTargets(check: String): Int =
        plugin.config.getInt("detection.checks.$check.max-targets", 2).coerceAtLeast(2)

    fun getCheckMaxCps(check: String): Int =
        plugin.config.getInt("detection.checks.$check.max-cps", 18).coerceAtLeast(10)

    fun getCheckSnapDegrees(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.snap-degrees", 35.0).coerceIn(10.0, 90.0)

    fun getCheckMinBreakMs(check: String): Long =
        plugin.config.getLong("detection.checks.$check.min-break-ms", 80).coerceAtLeast(20)

    fun getCheckMinFallDistance(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.min-fall-distance", 3.5).coerceAtLeast(2.0)

    fun getCheckVlAdd(check: String, default: Int): Int =
        plugin.config.getInt("detection.checks.$check.vl-add", default).coerceAtLeast(1)

    fun getCheckMaxStepHeight(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.max-step-height", 0.6).coerceIn(0.3, 1.0)

    fun getCheckMaxMovesPerSecond(check: String): Int =
        plugin.config.getInt("detection.checks.$check.max-moves-per-second", 22).coerceAtLeast(10)

    fun getCheckMaxBlinkDistance(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.max-blink-distance", 8.0).coerceAtLeast(3.0)

    fun getCheckMaxPlacesPerSecond(check: String): Int =
        plugin.config.getInt("detection.checks.$check.max-places-per-second", 12).coerceAtLeast(4)

    fun getCheckMinEatMs(check: String): Long =
        plugin.config.getLong("detection.checks.$check.min-eat-ms", 900).coerceAtLeast(200)

    fun getCheckMaxWallBlocks(check: String): Int =
        plugin.config.getInt("detection.checks.$check.min-wall-blocks", 1).coerceAtLeast(1)

    fun getCheckMaxPitch(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.max-pitch", 91.0).coerceIn(80.0, 180.0)

    fun getCheckVelocityMinRatio(check: String): Double =
        plugin.config.getDouble("detection.checks.$check.min-kb-ratio", 0.25).coerceIn(0.05, 1.0)

    fun getCheckMinHiddenOres(check: String): Int =
        plugin.config.getInt("detection.checks.$check.min-hidden-ores", 1).coerceAtLeast(1)

    fun getCheckWindowMs(check: String): Long =
        plugin.config.getLong("detection.checks.$check.window-ms", 400).coerceAtLeast(100)

    fun getCheckMaxClicksPerWindow(check: String): Int =
        plugin.config.getInt("detection.checks.$check.max-clicks-per-window", 8).coerceAtLeast(3)

    fun getCheckMaxInventoryCps(check: String): Int =
        plugin.config.getInt("detection.checks.$check.max-clicks-per-second", 25).coerceAtLeast(8)

    fun isPreBufferEnabled(): Boolean = plugin.config.getBoolean("detection.pre-buffer.enabled", true)
    fun getPreBufferSeconds(): Int = plugin.config.getInt("detection.pre-buffer.seconds", 10).coerceIn(3, 60)
    fun getPreBufferTickInterval(): Int =
        plugin.config.getInt("detection.pre-buffer.tick-interval", getRecordingTickInterval()).coerceAtLeast(1)
    fun isPreBufferAutoOnly(): Boolean = plugin.config.getBoolean("detection.pre-buffer.auto-only", true)

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
