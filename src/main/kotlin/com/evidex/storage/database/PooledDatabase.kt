package com.evidex.storage.database

import com.evidex.config.ConfigManager
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource

abstract class PooledDatabase(protected val config: ConfigManager) : AbstractDatabase() {

    private var dataSource: DataSource? = null

    override fun getConnection(): Connection =
        dataSource?.connection ?: throw IllegalStateException("Database not connected")

    override val isConnected: Boolean
        get() = dataSource != null

    protected fun createDataSource(
        jdbcUrl: String,
        driverClass: String? = null
    ) {
        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = config.getDatabaseUser()
            password = config.getDatabasePassword()
            maximumPoolSize = config.getDatabasePoolSize()
            minimumIdle = 2
            idleTimeout = 30000
            connectionTimeout = 10000
            validationTimeout = 5000
            if (driverClass != null) this.driverClassName = driverClass
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("useServerPrepStmts", "true")
        }
        dataSource = HikariDataSource(hikariConfig)
    }

    override fun disconnect() {
        (dataSource as? HikariDataSource)?.close()
        dataSource = null
    }
}
