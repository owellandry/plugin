package com.evidex.storage.database

import com.evidex.config.ConfigManager
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class SQLiteDatabase(private val config: ConfigManager) : AbstractDatabase() {

    private var connection: Connection? = null

    override fun getConnection(): Connection =
        connection ?: throw IllegalStateException("SQLite database not connected")

    override val isConnected: Boolean
        get() = connection?.isClosed?.not() ?: false

    override fun connect() {
        val dbPath = config.getDatabasePath()
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()
        
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            connection?.createStatement()?.use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA foreign_keys=ON")
            }
        } catch (e: Exception) {
            // If connection fails, try to delete the database file and recreate
            if (dbFile.exists()) {
                dbFile.delete()
                connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
                connection?.createStatement()?.use { stmt ->
                    stmt.execute("PRAGMA journal_mode=WAL")
                    stmt.execute("PRAGMA foreign_keys=ON")
                }
            } else {
                throw e
            }
        }
    }

    override fun disconnect() {
        try {
            connection?.close()
        } catch (_: Exception) {}
        connection = null
    }

    override fun createTables() {
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS recording_metadata (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_name TEXT NOT NULL,
                start_timestamp INTEGER NOT NULL,
                end_timestamp INTEGER,
                world TEXT,
                x REAL,
                y REAL,
                z REAL,
                frame_count INTEGER DEFAULT 0,
                file_path TEXT NOT NULL,
                status TEXT DEFAULT 'recording',
                created_at INTEGER NOT NULL
            )
        """)
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_recording_player ON recording_metadata(player_name)")
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_recording_status ON recording_metadata(status)")
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_recording_created ON recording_metadata(created_at)")

        // Users table for dashboard auth
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                must_change_password INTEGER DEFAULT 1,
                created_at INTEGER NOT NULL,
                last_login INTEGER
            )
        """)
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)")
    }
}
