package com.evidex.recording

import com.evidex.EvidexPlugin
import com.evidex.math.Angle
import com.evidex.math.Vec3d
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.event.entity.EntityToggleSwimEvent
import org.bukkit.Bukkit

class RecordingSession(
    private val plugin: EvidexPlugin,
    val player: Player
) : Listener {

    val data = RecordingData(
        playerName = player.name,
        startTimestamp = System.currentTimeMillis()
    )

    private var isRecording = true

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        captureFrame()
    }

    fun stop() {
        isRecording = false
        HandlerList.unregisterAll(this)
    }

    fun captureFrame() {
        if (!isRecording || !player.isOnline) return

        val loc = player.location
        val equipment = EquipmentFrame(
            mainHand = player.equipment.itemInMainHand?.let {
                if (it.type.isAir) null else ItemFrame(it.type.name, it.amount)
            },
            offHand = player.equipment.itemInOffHand?.let {
                if (it.type.isAir) null else ItemFrame(it.type.name, it.amount)
            },
            helmet = player.equipment.helmet?.let {
                if (it.type.isAir) null else ItemFrame(it.type.name, it.amount)
            },
            chestplate = player.equipment.chestplate?.let {
                if (it.type.isAir) null else ItemFrame(it.type.name, it.amount)
            },
            leggings = player.equipment.leggings?.let {
                if (it.type.isAir) null else ItemFrame(it.type.name, it.amount)
            },
            boots = player.equipment.boots?.let {
                if (it.type.isAir) null else ItemFrame(it.type.name, it.amount)
            }
        )

        val frame = PlayerFrame(
            timestamp = System.currentTimeMillis() - data.startTimestamp,
            position = Vec3d(loc.x, loc.y, loc.z),
            yaw = Angle(loc.yaw),
            pitch = Angle(loc.pitch),
            onGround = player.isOnGround,
            isSneaking = player.isSneaking,
            isSprinting = player.isSprinting,
            isFlying = player.isFlying,
            handSwing = false,
            equipment = equipment
        )

        data.frames.add(frame)
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        if (event.player != player || !isRecording) return
        if (event.from.toVector().equals(event.to!!.toVector()) &&
            event.from.yaw == event.to.yaw && event.from.pitch == event.to.pitch) return
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
        captureFrame()
    }
}
