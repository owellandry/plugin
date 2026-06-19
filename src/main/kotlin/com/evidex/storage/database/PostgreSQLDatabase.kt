package com.evidex.storage.database

import com.evidex.config.ConfigManager

class PostgreSQLDatabase(config: ConfigManager) : PooledDatabase(config) {

    override fun connect() {
        createDataSource(
            jdbcUrl = "jdbc:postgresql://${config.getDatabaseHost()}:${config.getDatabasePort()}/${config.getDatabaseName()}${config.getDatabaseParams()}",
            driverClass = "org.postgresql.Driver"
        )
    }

    override fun createTables() {
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS recording_metadata (
                id SERIAL PRIMARY KEY,
                player_name VARCHAR(64) NOT NULL,
                start_timestamp BIGINT NOT NULL,
                end_timestamp BIGINT,
                world VARCHAR(64),
                x DOUBLE PRECISION,
                y DOUBLE PRECISION,
                z DOUBLE PRECISION,
                frame_count INT DEFAULT 0,
                file_path VARCHAR(512) NOT NULL,
                status VARCHAR(32) DEFAULT 'recording',
                created_at BIGINT NOT NULL
            )
        """)
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_recording_player ON recording_metadata(player_name)")
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_recording_status ON recording_metadata(status)")
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_recording_created ON recording_metadata(created_at)")

        // Users table for dashboard auth
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                username VARCHAR(32) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                must_change_password SMALLINT DEFAULT 1,
                created_at BIGINT NOT NULL,
                last_login BIGINT
            )
        """)
        executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)")
    }
}
