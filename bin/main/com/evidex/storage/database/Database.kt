package com.evidex.storage.database

interface Database {
    fun connect()
    fun disconnect()
    val isConnected: Boolean
    fun createTables()
    fun executeUpdate(sql: String, params: List<Any?> = emptyList()): Int
    fun executeQuery(sql: String, params: List<Any?> = emptyList()): List<Map<String, Any?>>
    fun executeBatch(sql: String, paramsList: List<List<Any?>>): IntArray
}
