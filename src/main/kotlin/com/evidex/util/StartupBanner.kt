package com.evidex.util

object StartupBanner {

    data class Info(
        val version: String,
        val minecraft: String,
        val platform: String,
        val database: String,
        val detectionEnabled: Boolean,
        val detectionChecks: Int,
        val dashboardEnabled: Boolean,
        val dashboardPort: Int?,
        val dashboardAddress: String? = null,
        val serverHost: String? = null,
        val preBufferEnabled: Boolean,
        val preBufferSeconds: Int?,
        val cleanupEnabled: Boolean,
        val cleanupRetentionDays: Int?,
        val startupMs: Long,
        val warnings: List<String> = emptyList()
    )

    private val art = listOf(
        " ███████╗██╗   ██╗██╗██████╗ ███████╗██╗  ██╗",
        " ██╔════╝██║   ██║██║██╔══██╗██╔════╝╚██╗██╔╝",
        " █████╗  ██║   ██║██║██║  ██║█████╗   ╚███╔╝ ",
        " ██╔══╝  ╚██╗ ██╔╝██║██║  ██║██╔══╝   ██╔██╗ ",
        " ███████╗ ╚████╔╝ ██║██████╔╝███████╗██╔╝ ██╗",
        " ╚══════╝  ╚═══╝  ╚═╝╚═════╝ ╚══════╝╚═╝  ╚═╝"
    )

    fun print(consoleBlock: (String) -> Unit, info: Info) {
        val lines = buildList {
            add("")
            art.forEach { add("§b§l$it") }
            add("§7  Anti-Cheat§8 · §7Grabación§8 · §7Replay  §ev${info.version}")
            add("§8  ─────────────────────────────────────────────")
            addAll(stats(info))
            add("§a§l  ✔ §7Listo en §f${info.startupMs} ms§8  ·  §b/evidex")
            info.warnings.forEach { warning ->
                add("§e  ⚠ §7$warning")
            }
            add("")
        }
        consoleBlock(lines.joinToString("\n"))
    }

    private fun stats(info: Info): List<String> = buildList {
        add(stat("Servidor", "${info.minecraft} (${info.platform})"))
        add(stat("Base de datos", info.database))
        if (info.detectionEnabled) {
            add(stat("Detección", "${info.detectionChecks} checks activos", "§a"))
        } else {
            add(stat("Detección", "desactivada", "§c"))
        }
        if (info.dashboardEnabled && info.dashboardPort != null) {
            add(stat("Dashboard", buildDashboardUrl(info.dashboardAddress, info.dashboardPort, info.serverHost), "§a"))
        } else {
            add(stat("Dashboard", "desactivado", "§7"))
        }
        if (info.preBufferEnabled && info.preBufferSeconds != null) {
            add(stat("Pre-buffer", "${info.preBufferSeconds}s"))
        }
        if (info.cleanupEnabled && info.cleanupRetentionDays != null) {
            add(stat("Retención", "${info.cleanupRetentionDays} días"))
        }
    }

    internal fun buildDashboardUrl(bindAddress: String?, port: Int, serverHost: String? = null): String {
        val normalized = bindAddress?.trim()?.takeIf { it.isNotBlank() } ?: "0.0.0.0"
        val host = serverHost?.trim()?.takeIf { it.isNotBlank() }
        return if (normalized == "0.0.0.0" || normalized == "::" || normalized == "[::]" || normalized == "0") {
            if (host != null) "http://$host:$port" else "http://localhost:$port"
        } else {
            "http://$normalized:$port"
        }
    }

    private fun stat(label: String, value: String, valueColor: String = "§f"): String =
        "§8  ${String.format("%-14s", label)}$valueColor$value"
}