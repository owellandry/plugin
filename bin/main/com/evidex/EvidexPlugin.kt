package com.evidex

import com.evidex.config.ConfigManager
import com.evidex.detection.DetectionManager
import com.evidex.dashboard.DashboardServer
import com.evidex.playback.NpcPlatformService
import com.evidex.playback.PacketEventsService
import com.evidex.playback.ReplayManager
import com.evidex.recording.RecordingManager
import com.evidex.recording.WorldSnapshotService
import com.evidex.service.CleanupService
import com.evidex.storage.database.Database
import com.evidex.storage.database.DatabaseFactory
import com.evidex.storage.repository.BlockChangeRepository
import com.evidex.storage.repository.FrameRepository
import com.evidex.storage.repository.RecordingRepository
import com.evidex.storage.repository.ViolationRepository
import com.evidex.storage.repository.WorldRepository
import com.evidex.util.EvidexLog
import com.evidex.util.PluginCompat
import com.evidex.util.ServerCompatibility
import com.evidex.util.StartupBanner
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class EvidexPlugin : JavaPlugin() {

    lateinit var log: EvidexLog
        private set
    lateinit var configManager: ConfigManager
        private set
    lateinit var database: Database
        private set
    lateinit var recordingRepository: RecordingRepository
        private set
    lateinit var frameRepository: FrameRepository
        private set
    lateinit var recordingManager: RecordingManager
        private set
    lateinit var replayManager: ReplayManager
        private set
    lateinit var detectionManager: DetectionManager
        private set
    lateinit var violationRepository: ViolationRepository
        private set

    private var dashboard: DashboardServer? = null
    private var cleanupService: CleanupService? = null

    override fun onLoad() {
        log = EvidexLog.of(this)
        PacketEventsService.load(this, log)
    }

    override fun onEnable() {
        val startupStart = System.nanoTime()
        try {
            val compatibility = ServerCompatibility.validate(log)
            if (!compatibility.ok) {
                server.pluginManager.disablePlugin(this)
                return
            }

            configManager = ConfigManager(this)
            configManager.loadConfig()

            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }

            val dbType = configManager.getDatabaseType()
            database = DatabaseFactory.createDatabase(configManager)
            database.connect()
            database.createTables()

            recordingRepository = RecordingRepository(database)
            violationRepository = ViolationRepository(database)

            val framesDir = File(configManager.getFramesDirectory())
            frameRepository = FrameRepository(framesDir)
            val worldRepository = WorldRepository(framesDir)
            val blockChangeRepository = BlockChangeRepository(framesDir)
            val worldSnapshotService = WorldSnapshotService(configManager)

            recordingManager = RecordingManager(
                this,
                recordingRepository,
                frameRepository,
                worldRepository,
                blockChangeRepository,
                worldSnapshotService
            )
            PacketEventsService.init(this, log)
            replayManager = ReplayManager(this, worldRepository)
            NpcPlatformService.init(this, log)

            detectionManager = DetectionManager(this, violationRepository)
            detectionManager.start()

            server.scheduler.runTask(this, Runnable { registerCommands() })

            val dashboardEnabled = configManager.isDashboardEnabled()
            if (dashboardEnabled) {
                dashboard = DashboardServer(
                    this,
                    configManager.getDashboardPort(),
                    configManager.getDashboardBindAddress()
                )
            }

            cleanupService = CleanupService(
                this, recordingRepository, frameRepository, worldRepository, blockChangeRepository, configManager
            )
            cleanupService!!.start()

            val startupMs = (System.nanoTime() - startupStart) / 1_000_000
            val minecraftLabel = compatibility.release?.label()
                ?: server.minecraftVersion

            val bannerInfo = StartupBanner.Info(
                version = PluginCompat.version(this),
                minecraft = minecraftLabel,
                platform = compatibility.platform,
                database = dbType,
                detectionEnabled = detectionManager.isEnabled(),
                detectionChecks = detectionManager.enabledCheckCount(),
                dashboardEnabled = dashboardEnabled,
                dashboardPort = if (dashboardEnabled) configManager.getDashboardPort() else null,
                dashboardAddress = if (dashboardEnabled) configManager.getDashboardBindAddress() else null,
                serverHost = server.ip.takeIf { it.isNotBlank() },
                preBufferEnabled = configManager.isPreBufferEnabled(),
                preBufferSeconds = if (configManager.isPreBufferEnabled()) configManager.getPreBufferSeconds() else null,
                cleanupEnabled = configManager.isCleanupEnabled(),
                cleanupRetentionDays = if (configManager.isCleanupEnabled()) configManager.getCleanupRetentionDays() else null,
                startupMs = startupMs,
                warnings = compatibility.warnings
            )

            // Esperar unos ticks para que logs async (p. ej. PacketEvents) no corten el banner.
            server.scheduler.runTaskLater(this, Runnable {
                StartupBanner.print(log::consoleBlock, bannerInfo)
            }, 10L)
        } catch (e: Exception) {
            log.error("Error al iniciar Evidex", e)
            server.pluginManager.disablePlugin(this)
        }
    }

    private fun registerCommands() {
        try {
            val evidexCmd = getCommand("evidex") ?: return
            val executor = EvidexCommand(this)
            evidexCmd.setExecutor(executor)
            evidexCmd.tabCompleter = executor
            log.debug("Comando /evidex registrado")
        } catch (e: Exception) {
            log.warn("No se pudo registrar /evidex: ${e.message}")
        }
    }

    override fun onDisable() {
        unregisterCommands()
        dashboard?.shutdown()

        if (::recordingManager.isInitialized) {
            recordingManager.cancelInitialSnapshots()
            recordingManager.cancelPathSnapshots()
            recordingManager.stopAll()
            recordingManager.shutdown()
        }
        if (::replayManager.isInitialized) {
            replayManager.stopAll()
        }
        cleanupService?.stop()
        if (::detectionManager.isInitialized) {
            detectionManager.shutdown()
        }
        NpcPlatformService.shutdown()

        if (::database.isInitialized) {
            database.disconnect()
        }
        if (::log.isInitialized) {
            log.info("Evidex detenido")
        }
    }

    fun hasDetection(): Boolean = ::detectionManager.isInitialized

    fun hasViolationRepository(): Boolean = ::violationRepository.isInitialized

    private fun unregisterCommands() {
        try {
            val cmd = getCommand("evidex") ?: return
            cmd.setExecutor(null)
            cmd.tabCompleter = null
        } catch (e: Exception) {
            if (::log.isInitialized) log.debug("Limpieza de comandos: ${e.message}")
        }
    }
}