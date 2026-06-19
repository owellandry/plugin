package com.evidex

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class EvidexCommand(private val plugin: EvidexPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§6[Evidex] §eUsage: /evidex <record|replay|stop|list> [player]")
            return true
        }

        when (args[0].lowercase()) {
            "record" -> handleRecord(sender, args)
            "replay" -> handleReplay(sender, args)
            "stop" -> handleStop(sender, args)
            "list" -> handleList(sender)
            else -> sender.sendMessage("§6[Evidex] §cUnknown command. Use: record, replay, stop, list")
        }
        return true
    }

    private fun handleRecord(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§6[Evidex] §eUsage: /evidex record <player>")
            return
        }
        val target = plugin.server.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage("§6[Evidex] §cPlayer '${args[1]}' not found")
            return
        }
        if (plugin.recordingManager.isRecording(target)) {
            sender.sendMessage("§6[Evidex] §cAlready recording ${target.name}")
            return
        }
        plugin.recordingManager.startRecording(target)
        sender.sendMessage("§6[Evidex] §aStarted recording ${target.name}")
    }

    private fun handleReplay(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§6[Evidex] §eUsage: /evidex replay <player>")
            return
        }
        val targetName = args[1]
        val recording = plugin.recordingManager.getRecording(targetName)
        if (recording == null) {
            sender.sendMessage("§6[Evidex] §cNo recording found for '$targetName'")
            return
        }
        val viewer = sender as? Player
        if (viewer == null) {
            sender.sendMessage("§6[Evidex] §cMust be a player to view replays")
            return
        }
        plugin.replayManager.startReplay(viewer, recording)
        sender.sendMessage("§6[Evidex] §aPlaying replay of $targetName")
    }

    private fun handleStop(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§6[Evidex] §eUsage: /evidex stop <record|replay> [player]")
            return
        }
        when (args[1].lowercase()) {
            "record" -> {
                if (args.size < 3) {
                    plugin.recordingManager.stopAll()
                    sender.sendMessage("§6[Evidex] §aStopped all recordings")
                } else {
                    val target = plugin.server.getPlayer(args[2])
                    if (target != null && plugin.recordingManager.isRecording(target)) {
                        plugin.recordingManager.stopRecording(target)
                        sender.sendMessage("§6[Evidex] §aStopped recording ${target.name}")
                    }
                }
            }
            "replay" -> {
                val viewer = sender as? Player
                if (viewer != null) {
                    plugin.replayManager.stopReplay(viewer)
                    sender.sendMessage("§6[Evidex] §aStopped replay")
                }
            }
        }
    }

    private fun handleList(sender: CommandSender) {
        val recordings = plugin.recordingManager.getRecordingNames()
        if (recordings.isEmpty()) {
            sender.sendMessage("§6[Evidex] §7No recordings available")
        } else {
            sender.sendMessage("§6[Evidex] §eRecordings: §f${recordings.joinToString(", ")}")
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        return try {
            when (args.size) {
                1 -> listOf("record", "replay", "stop", "list").filter { it.startsWith(args[0].lowercase()) }
                2 -> when (args[0].lowercase()) {
                    "record", "replay" -> plugin.server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                    "stop" -> listOf("record", "replay").filter { it.startsWith(args[1].lowercase()) }
                    else -> emptyList()
                }
                3 -> if (args[0].equals("stop", ignoreCase = true) && args[1].equals("record", ignoreCase = true)) {
                    plugin.server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                } else emptyList()
                else -> emptyList()
            }
        } catch (e: Exception) {
            // Defensive: during /reload the server state can be inconsistent
            emptyList()
        }
    }
}
