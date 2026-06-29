package com.evidex.util

import org.bukkit.plugin.java.JavaPlugin

/** Lectura de metadatos del plugin compatible Spigot/Paper. */
object PluginCompat {

    @Suppress("DEPRECATION")
    fun version(plugin: JavaPlugin): String = plugin.description.version
}