package com.evidex.util

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Registro unificado del plugin. El logger de Bukkit ya antepone `[Evidex]`.
 * El banner de arranque usa §-codes via console sender (compatible Spigot/Paper).
 */
class EvidexLog(private val logger: Logger) {

    fun info(message: String) = log(Level.INFO, message)

    fun warn(message: String, cause: Throwable? = null) = log(Level.WARNING, message, cause)

    fun error(message: String, cause: Throwable? = null) = log(Level.SEVERE, message, cause)

    fun debug(message: String) = log(Level.FINE, message)

    private val consoleLock = Any()

    fun console(message: String) {
        synchronized(consoleLock) {
            Bukkit.getServer().consoleSender.sendMessage(message)
        }
    }

    /** Imprime un bloque completo en un solo mensaje para que no se intercale con otros hilos. */
    fun consoleBlock(message: String) = console(message)

    private fun log(level: Level, message: String, cause: Throwable? = null) {
        if (cause != null) logger.log(level, message, cause) else logger.log(level, message)
    }

    companion object {
        fun of(plugin: JavaPlugin): EvidexLog = EvidexLog(plugin.logger)
    }
}