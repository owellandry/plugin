package com.evidex.recording

import com.evidex.EvidexPlugin
import com.evidex.math.Angle
import com.evidex.math.Vec3d
import com.evidex.util.BukkitExtensions
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object FrameCapture {

    fun capture(
        plugin: EvidexPlugin,
        player: Player,
        timestampMs: Long,
        handSwing: Boolean = false,
        eventType: String? = null,
        eventDetail: String? = null
    ): PlayerFrame {
        val loc = player.location
        val equip = player.equipment
        val equipment = EquipmentFrame(
            mainHand = toItemFrame(equip.itemInMainHand),
            offHand = toItemFrame(equip.itemInOffHand),
            helmet = toItemFrame(equip.helmet),
            chestplate = toItemFrame(equip.chestplate),
            leggings = toItemFrame(equip.leggings),
            boots = toItemFrame(equip.boots)
        )

        return PlayerFrame(
            timestamp = timestampMs,
            position = Vec3d(loc.x, loc.y, loc.z),
            yaw = Angle(loc.yaw),
            pitch = Angle(loc.pitch),
            onGround = BukkitExtensions.isOnGround(player),
            isSneaking = player.isSneaking,
            isSprinting = player.isSprinting,
            isFlying = player.isFlying,
            handSwing = handSwing,
            equipment = equipment,
            health = player.health.toFloat(),
            food = player.foodLevel,
            hotbarSlot = player.inventory.heldItemSlot,
            nearbyEntities = EntityCapture.capture(player, plugin.configManager),
            eventType = eventType,
            eventDetail = eventDetail
        )
    }

    private fun toItemFrame(stack: ItemStack?): ItemFrame? {
        if (stack == null || stack.type.isAir) return null
        return ItemFrame(stack.type.name, stack.amount)
    }
}