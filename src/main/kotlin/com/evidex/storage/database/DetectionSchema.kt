package com.evidex.storage.database

internal object DetectionSchema {

    fun applyMigrations(db: Database) {
        try { db.executeUpdate("ALTER TABLE recording_metadata ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MANUAL'") } catch (_: Exception) {}
        try { db.executeUpdate("ALTER TABLE recording_metadata ADD COLUMN trigger_check VARCHAR(32)") } catch (_: Exception) {}
        try { db.executeUpdate("ALTER TABLE recording_metadata ADD COLUMN peak_vl INT NOT NULL DEFAULT 0") } catch (_: Exception) {}
        try { db.executeUpdate("ALTER TABLE recording_metadata ADD COLUMN violation_count INT NOT NULL DEFAULT 0") } catch (_: Exception) {}
    }

    fun createViolationsTableMySql(db: Database) {
        db.executeUpdate("""
            CREATE TABLE IF NOT EXISTS violations (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(64) NOT NULL,
                player_name VARCHAR(64) NOT NULL,
                check_name VARCHAR(32) NOT NULL,
                category VARCHAR(16) NOT NULL,
                vl_added INT NOT NULL,
                vl_total INT NOT NULL,
                severity VARCHAR(16) NOT NULL,
                info_json TEXT NOT NULL,
                recording_id INT,
                world VARCHAR(64),
                x DOUBLE,
                y DOUBLE,
                z DOUBLE,
                timestamp BIGINT NOT NULL
            )
        """)
        try { db.executeUpdate("CREATE INDEX idx_violations_player ON violations(player_name)") } catch (_: Exception) {}
        try { db.executeUpdate("CREATE INDEX idx_violations_time ON violations(timestamp)") } catch (_: Exception) {}
        try { db.executeUpdate("CREATE INDEX idx_violations_recording ON violations(recording_id)") } catch (_: Exception) {}
    }

    fun createViolationsTablePostgres(db: Database) {
        db.executeUpdate("""
            CREATE TABLE IF NOT EXISTS violations (
                id SERIAL PRIMARY KEY,
                player_uuid VARCHAR(64) NOT NULL,
                player_name VARCHAR(64) NOT NULL,
                check_name VARCHAR(32) NOT NULL,
                category VARCHAR(16) NOT NULL,
                vl_added INT NOT NULL,
                vl_total INT NOT NULL,
                severity VARCHAR(16) NOT NULL,
                info_json TEXT NOT NULL DEFAULT '{}',
                recording_id INT,
                world VARCHAR(64),
                x DOUBLE PRECISION,
                y DOUBLE PRECISION,
                z DOUBLE PRECISION,
                timestamp BIGINT NOT NULL
            )
        """)
        db.executeUpdate("CREATE INDEX IF NOT EXISTS idx_violations_player ON violations(player_name)")
        db.executeUpdate("CREATE INDEX IF NOT EXISTS idx_violations_time ON violations(timestamp)")
        db.executeUpdate("CREATE INDEX IF NOT EXISTS idx_violations_recording ON violations(recording_id)")
    }
}