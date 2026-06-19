package com.evidex

import com.evidex.config.ConfigManager
import com.evidex.dashboard.DashboardServer
import com.evidex.playback.ReplayManager
import com.evidex.recording.RecordingManager
import com.evidex.service.CleanupService
import com.evidex.storage.database.Database
import com.evidex.storage.database.DatabaseFactory
import com.evidex.storage.repository.FrameRepository
import com.evidex.storage.repository.RecordingRepository
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class EvidexPlugin : JavaPlugin() {

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

    private var dashboard: DashboardServer? = null
    private var cleanupService: CleanupService? = null

    override fun onEnable() {
        try {
            logger.info("=== Evidex Initialization ===")

            configManager = ConfigManager(this)
            configManager.loadConfig()
            logger.info("Config loaded from ${dataFolder}/config.yml")

            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
                logger.info("Created plugin directory: $dataFolder")
            }

            val dbType = configManager.getDatabaseType()
            database = DatabaseFactory.createDatabase(configManager)
            database.connect()
            logger.info("Database connected: $dbType")

            database.createTables()
            logger.info("Database tables created")

            recordingRepository = RecordingRepository(database)

            val framesDir = File(configManager.getFramesDirectory())
            frameRepository = FrameRepository(framesDir)
            logger.info("Frames directory: ${framesDir.absolutePath}")

            recordingManager = RecordingManager(this, recordingRepository, frameRepository)
            replayManager = ReplayManager(this)

            recordingManager.register()

            // Delay command registration slightly. This helps avoid race conditions with Paper's async command tree builder during reloads.
            server.scheduler.runTask(this) { _ ->
                try {
                    val evidexCmd = getCommand("evidex")
                    val executor = EvidexCommand(this)
                    evidexCmd?.setExecutor(executor)
                    evidexCmd?.tabCompleter = executor
                    logger.info("Commands registered: /evidex")
                } catch (e: Exception) {
                    logger.warning("Failed to register /evidex command: ${e.message}")
                }
            }

            if (configManager.isDashboardEnabled()) {
                dashboard = DashboardServer(this, configManager.getDashboardPort())
                logger.info("Dashboard server started on port ${configManager.getDashboardPort()}")
            }

            cleanupService = CleanupService(this, recordingRepository, frameRepository, configManager)
            cleanupService!!.start()
            // Note: CleanupService also logs its own start message

            if (server.isPrimaryThread.not()) {
                // Rare, but during reload this can happen
            }

            logger.info("=== Evidex enabled ($dbType) ===")
            logger.warning("[WARNING] Using /reload is NOT recommended and often causes ConcurrentModificationException + other bugs. Use /stop + restart the server instead.")
        } catch (e: Exception) {
            logger.severe("Failed to initialize Evidex: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        // Unregister command as thoroughly as possible to minimize ConcurrentModificationException during /reload
        try {
            val cmd = getCommand("evidex")
            if (cmd != null) {
                cmd.setExecutor(null)
                cmd.tabCompleter = null

                // Attempt to remove from Bukkit's internal command map (helps with reload issues)
                try {
                    val commandMapField = server.javaClass.getDeclaredField("commandMap")
                    commandMapField.isAccessible = true
                    val commandMap = commandMapField.get(server) as org.bukkit.command.CommandMap

                    val knownCommandsField = commandMap.javaClass.getDeclaredField("knownCommands")
                    knownCommandsField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val knownCommands = knownCommandsField.get(commandMap) as MutableMap<String, org.bukkit.command.Command>

                    knownCommands.remove("evidex")
                    knownCommands.remove("${description.name.lowercase()}:evidex")
                    // Remove any aliases if present
                    cmd.aliases.forEach { alias ->
                        knownCommands.remove(alias)
                        knownCommands.remove("${description.name.lowercase()}:$alias")
                    }
                } catch (inner: Exception) {
                    // Internal fields may vary by version; this is best-effort
                    logger.fine("Could not fully remove from knownCommands (normal on some versions): ${inner.message}")
                }
            }
        } catch (e: Exception) {
            logger.warning("Error while unregistering /evidex command: ${e.message}")
        }

        dashboard?.shutdown()

        if (::recordingManager.isInitialized) {
            recordingManager.stopAll()
        }
        if (::replayManager.isInitialized) {
            replayManager.stopAll()
        }
        cleanupService?.stop()
        if (::database.isInitialized) {
            database.disconnect()
        }
        logger.info("Evidex disabled")
    }
}
