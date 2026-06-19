package com.evidex.storage.database

import com.evidex.config.ConfigManager

object DatabaseFactory {
    fun createDatabase(config: ConfigManager): Database {
        return when (config.getDatabaseType().lowercase()) {
            "sqlite" -> SQLiteDatabase(config)
            "mysql" -> MySQLDatabase(config)
            "postgresql" -> PostgreSQLDatabase(config)
            "mariadb" -> MariaDBDatabase(config)
            else -> throw IllegalArgumentException("Unsupported database type: ${config.getDatabaseType()}")
        }
    }
}
