package com.evidex

import com.evidex.util.EvidexMessages
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class EvidexCommand(private val plugin: EvidexPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("evidex.admin")) {
            EvidexMessages.error(sender, "Sin permiso (evidex.admin)")
            return true
        }

        if (args.isEmpty()) {
            EvidexMessages.warn(sender, "Uso: /evidex <record|replay|stop|list|alerts|violations|check> [jugador]")
            return true
        }

        when (args[0].lowercase()) {
            "record" -> handleRecord(sender, args)
            "replay" -> handleReplay(sender, args)
            "stop" -> handleStop(sender, args)
            "list" -> handleList(sender)
            "alerts" -> handleAlerts(sender, args)
            "violations" -> handleViolations(sender, args)
            "check" -> handleCheck(sender, args)
            else -> EvidexMessages.error(sender, "Comando desconocido. Usa: record, replay, stop, list, alerts, violations, check")
        }
        return true
    }

    private fun handleRecord(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            EvidexMessages.warn(sender, "Uso: /evidex record <jugador>")
            return
        }
        val target = plugin.server.getPlayer(args[1])
        if (target == null) {
            EvidexMessages.error(sender, "Jugador '${args[1]}' no encontrado")
            return
        }
        if (plugin.recordingManager.isRecording(target)) {
            EvidexMessages.error(sender, "Ya se está grabando a ${target.name}")
            return
        }
        plugin.recordingManager.startRecording(target)
        EvidexMessages.success(sender, "Grabación iniciada: ${target.name}")
    }

    private fun handleReplay(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            EvidexMessages.warn(sender, "Uso: /evidex replay <jugador|pause|resume|speed|skip>")
            return
        }
        val viewer = sender as? Player
        if (viewer == null) {
            EvidexMessages.error(sender, "Debes ser un jugador en el juego para usar replays")
            return
        }

        when (args[1].lowercase()) {
            "pause" -> {
                if (plugin.replayManager.getSession(viewer) == null) {
                    EvidexMessages.warn(viewer, "No tienes un replay activo")
                    return
                }
                plugin.replayManager.pause(viewer)
            }
            "resume" -> {
                if (plugin.replayManager.getSession(viewer) == null) {
                    EvidexMessages.warn(viewer, "No tienes un replay activo")
                    return
                }
                plugin.replayManager.resume(viewer)
            }
            "speed" -> {
                if (args.size < 3) {
                    EvidexMessages.warn(viewer, "Uso: /evidex replay speed <0.5|1|2>")
                    return
                }
                val speed = args[2].toDoubleOrNull()
                if (speed == null) {
                    EvidexMessages.error(viewer, "Velocidad inválida")
                    return
                }
                if (plugin.replayManager.getSession(viewer) == null) {
                    EvidexMessages.warn(viewer, "No tienes un replay activo")
                    return
                }
                plugin.replayManager.setSpeed(viewer, speed)
            }
            "skip" -> {
                if (plugin.replayManager.getSession(viewer) == null) {
                    EvidexMessages.warn(viewer, "No tienes un replay activo")
                    return
                }
                plugin.replayManager.skipToNextFlag(viewer)
            }
            else -> {
                val recording = plugin.recordingManager.getRecording(args[1])
                    ?: plugin.recordingManager.getRecordingById(args[1].toLongOrNull() ?: -1)
                if (recording == null) {
                    EvidexMessages.error(sender, "No hay grabación para '${args[1]}'")
                    return
                }
                plugin.replayManager.startReplay(viewer, recording)
            }
        }
    }

    private fun handleStop(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            EvidexMessages.warn(sender, "Uso: /evidex stop <record|replay> [jugador]")
            return
        }
        when (args[1].lowercase()) {
            "record" -> {
                if (args.size < 3) {
                    plugin.recordingManager.stopAll()
                    EvidexMessages.success(sender, "Todas las grabaciones detenidas")
                } else {
                    val target = plugin.server.getPlayer(args[2])
                    if (target != null && plugin.recordingManager.isRecording(target)) {
                        plugin.recordingManager.stopRecording(target)
                        EvidexMessages.success(sender, "Grabación detenida: ${target.name}")
                    }
                }
            }
            "replay" -> {
                val viewer = sender as? Player ?: return
                plugin.replayManager.stopReplay(viewer)
                EvidexMessages.success(sender, "Replay detenido")
            }
        }
    }

    private fun handleAlerts(sender: CommandSender, args: Array<out String>) {
        if (!plugin.hasViolationRepository()) {
            EvidexMessages.error(sender, "Detección no disponible")
            return
        }
        val limit = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 50) ?: 10
        val alerts = plugin.violationRepository.findRecent(limit)
        if (alerts.isEmpty()) {
            EvidexMessages.info(sender, "Sin alertas recientes")
            return
        }
        EvidexMessages.info(sender, "Últimas $limit alertas:")
        for (v in alerts) {
            sender.sendMessage("§7#${v.id} §e${v.playerName} §c${v.checkName} §7VL ${v.vlTotal} §8(${v.severity})")
        }
    }

    private fun handleViolations(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            EvidexMessages.warn(sender, "Uso: /evidex violations <jugador>")
            return
        }
        if (!plugin.hasViolationRepository()) {
            EvidexMessages.error(sender, "Detección no disponible")
            return
        }
        val list = plugin.violationRepository.findByPlayer(args[1])
        if (list.isEmpty()) {
            EvidexMessages.info(sender, "Sin violaciones para ${args[1]}")
            return
        }
        for (v in list.take(15)) {
            sender.sendMessage("§7${v.checkName} §cVL ${v.vlTotal} §8— ${v.infoJson}")
        }
    }

    private fun handleCheck(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            EvidexMessages.warn(sender, "Uso: /evidex check <jugador>")
            return
        }
        if (!plugin.hasDetection()) {
            EvidexMessages.error(sender, "Detección no disponible")
            return
        }
        val target = plugin.server.getPlayer(args[1])
        if (target == null) {
            EvidexMessages.error(sender, "Jugador no encontrado")
            return
        }
        val profile = plugin.detectionManager.getProfile(target.uniqueId)
        if (profile == null || profile.vlByCheck.isEmpty()) {
            EvidexMessages.info(sender, "${args[1]}: sin VL activo")
            return
        }
        val summary = profile.vlByCheck.entries.joinToString(", ") { "${it.key}=${it.value}" }
        EvidexMessages.info(sender, "${target.name}: $summary (total ${profile.totalVl()})")
    }

    private fun handleList(sender: CommandSender) {
        val recordings = plugin.recordingManager.getRecordingNames()
        if (recordings.isEmpty()) {
            EvidexMessages.info(sender, "No hay grabaciones disponibles")
        } else {
            EvidexMessages.info(sender, "Grabaciones: ${recordings.joinToString(", ")}")
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> = try {
        when (args.size) {
            1 -> listOf("record", "replay", "stop", "list", "alerts", "violations", "check")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "replay" -> {
                    val controls = listOf("pause", "resume", "speed", "skip")
                    val players = plugin.server.onlinePlayers.map { it.name }
                    (controls + players).filter { it.startsWith(args[1], ignoreCase = true) }
                }
                "record", "violations", "check" -> plugin.server.onlinePlayers.map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                "stop" -> listOf("record", "replay").filter { it.startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            3 -> when {
                args[0].equals("replay", true) && args[1].equals("speed", true) ->
                    listOf("0.5", "1", "2").filter { it.startsWith(args[2]) }
                args[0].equals("stop", true) && args[1].equals("record", true) ->
                    plugin.server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}