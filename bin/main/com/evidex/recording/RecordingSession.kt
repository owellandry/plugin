package com.evidex.recording

import com.evidex.EvidexPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.*
import org.bukkit.scheduler.BukkitTask

class RecordingSession(
    private val plugin: EvidexPlugin,
    val player: Player,
    private val onMaxDuration: () -> Unit,
    prebufferFrames: List<PlayerFrame> = emptyList()
) : Listener {

    val data = RecordingData(
        playerName = player.name,
        startTimestamp = System.currentTimeMillis(),
        worldName = player.world.name
    )

    private var isRecording = true
    private var tickTask: BukkitTask? = null
    private var maxDurationTask: BukkitTask? = null

    init {
        if (prebufferFrames.isNotEmpty()) {
            data.frames.addAll(prebufferFrames)
            data.startTimestamp = System.currentTimeMillis() - prebufferFrames.last().timestamp
            plugin.log.debug(
                "Pre-buffer: ${prebufferFrames.size} frames (~${prebufferFrames.last().timestamp}ms) para ${player.name}"
            )
        }

        plugin.server.pluginManager.registerEvents(this, plugin)
        RecordingEventCompat.registerPickupListener(plugin, this, player) { captureFrame() }
        captureFrame()

        val tickInterval = plugin.configManager.getRecordingTickInterval().coerceAtLeast(1).toLong()
        tickTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable { captureFrame() }, tickInterval, tickInterval)

        val maxDurationMs = plugin.configManager.getRecordingMaxDuration()
        if (maxDurationMs > 0) {
            val maxTicks = (maxDurationMs / 50L).coerceAtLeast(1L)
            maxDurationTask = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (isRecording) onMaxDuration()
            }, maxTicks)
        }
    }

    fun stop() {
        isRecording = false
        tickTask?.cancel()
        maxDurationTask?.cancel()
        HandlerList.unregisterAll(this)
    }

    fun captureFrame(
        handSwing: Boolean = false,
        eventType: String? = null,
        eventDetail: String? = null
    ) {
        if (!isRecording || !player.isOnline) return

        val relativeMs = System.currentTimeMillis() - data.startTimestamp
        val frame = FrameCapture.capture(
            plugin, player, relativeMs, handSwing, eventType, eventDetail
        )
        data.frames.add(frame)
    }

    fun captureTaggedEvent(eventType: String, eventDetail: String) {
        captureFrame(eventType = eventType, eventDetail = eventDetail)
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        if (event.player != player || !isRecording) return
        val to = event.to
        if (event.from.toVector().equals(to.toVector()) &&
            event.from.yaw == to.yaw && event.from.pitch == to.pitch) return
        captureFrame()
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.player != player || !isRecording) return
        captureFrame()
    }

    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        if (event.player != player || !isRecording) return
        captureFrame()
    }

    @EventHandler
    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        if (event.player != player || !isRecording) return
        captureFrame()
    }

    @EventHandler
    fun onToggleSprint(event: PlayerToggleSprintEvent) {
        if (event.player != player || !isRecording) return
        captureFrame()
    }

    @EventHandler
    fun onToggleFlight(event: PlayerToggleFlightEvent) {
        if (event.player != player || !isRecording) return
        captureFrame()
    }

    @EventHandler
    fun onSwingHand(event: PlayerAnimationEvent) {
        if (event.player != player || !isRecording) return
        captureFrame(handSwing = true)
    }

    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        if (event.player != player || !isRecording) return
        captureFrame()
    }

    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity != player || !isRecording) return
        captureFrame()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.player != player || !isRecording) return
        val block = event.blockPlaced
        recordBlockChange(block.x, block.y, block.z, block.type)
        captureTaggedEvent(
            "place",
            """{"block":"${block.type.name}","x":${block.x},"y":${block.y},"z":${block.z}}"""
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.player != player || !isRecording) return
        val block = event.block
        recordBlockChange(block.x, block.y, block.z, Material.AIR)
        captureTaggedEvent(
            "mining",
            """{"block":"${block.type.name}","x":${block.x},"y":${block.y},"z":${block.z}}"""
        )
    }

    private fun recordBlockChange(x: Int, y: Int, z: Int, material: Material) {
        val relativeMs = System.currentTimeMillis() - data.startTimestamp
        data.blockChanges.add(BlockChange(relativeMs, x, y, z, material.name))
    }
}