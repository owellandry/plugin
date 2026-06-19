package com.evidex.storage.database

import java.sql.Connection
import java.sql.PreparedStatement

abstract class AbstractDatabase : Database {

    protected abstract fun getConnection(): Connection

    override fun executeUpdate(sql: String, params: List<Any?>): Int {
        getConnection().prepareStatement(sql).use { stmt ->
            setParams(stmt, params)
            return stmt.executeUpdate()
        }
    }

    override fun executeQuery(sql: String, params: List<Any?>): List<Map<String, Any?>> {
        getConnection().prepareStatement(sql).use { stmt ->
            setParams(stmt, params)
            stmt.executeQuery().use { rs ->
                val meta = rs.metaData
                val cols = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                val results = mutableListOf<Map<String, Any?>>()
                while (rs.next()) {
                    results.add(cols.associate { it to rs.getObject(it) })
                }
                return results
            }
        }
    }

    override fun executeBatch(sql: String, paramsList: List<List<Any?>>): IntArray {
        getConnection().prepareStatement(sql).use { stmt ->
            for (params in paramsList) {
                setParams(stmt, params)
                stmt.addBatch()
            }
            return stmt.executeBatch()
        }
    }

    protected fun setParams(stmt: PreparedStatement, params: List<Any?>) {
        for ((index, param) in params.withIndex()) {
            when (param) {
                null -> stmt.setNull(index + 1, java.sql.Types.NULL)
                is Int -> stmt.setInt(index + 1, param)
                is Long -> stmt.setLong(index + 1, param)
                is Double -> stmt.setDouble(index + 1, param)
                is Float -> stmt.setFloat(index + 1, param)
                is Boolean -> stmt.setBoolean(index + 1, param)
                is String -> stmt.setString(index + 1, param)
                else -> stmt.setObject(index + 1, param)
            }
        }
    }
}
