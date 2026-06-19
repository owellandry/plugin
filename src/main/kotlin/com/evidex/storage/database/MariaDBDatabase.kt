package com.evidex.storage.database

import com.evidex.config.ConfigManager

class MariaDBDatabase(config: ConfigManager) : PooledDatabase(config) {

    override fun connect() {
        createDataSource(
            jdbcUrl = "jdbc:mariadb://${config.getDatabaseHost()}:${config.getDatabasePort()}/${config.getDatabaseName()}${config.getDatabaseParams()}",
            driverClass = "org.mariadb.jdbc.Driver"
        )
    }

    override fun createTables() {
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS recording_metadata (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_name VARCHAR(64) NOT NULL,
                start_timestamp BIGINT NOT NULL,
                end_timestamp BIGINT,
                world VARCHAR(64),
                x DOUBLE,
                y DOUBLE,
                z DOUBLE,
                frame_count INT DEFAULT 0,
                file_path VARCHAR(512) NOT NULL,
                status VARCHAR(32) DEFAULT 'recording',
                created_at BIGINT NOT NULL
            )
        """)
        executeUpdate("CREATE INDEX idx_recording_player ON recording_metadata(player_name)")
        executeUpdate("CREATE INDEX idx_recording_status ON recording_metadata(status)")
        executeUpdate("CREATE INDEX idx_recording_created ON recording_metadata(created_at)")

        // Users table for dashboard auth
        executeUpdate("""
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(32) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                must_change_password TINYINT(1) DEFAULT 1,
                created_at BIGINT NOT NULL,
                last_login BIGINT
            )
        """)
        executeUpdate("CREATE INDEX idx_users_username ON users(username)")
    }
}
