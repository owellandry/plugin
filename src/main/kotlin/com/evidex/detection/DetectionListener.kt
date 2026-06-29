package com.evidex.detection

import com.evidex.util.BukkitExtensions
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

class DetectionListener(private val manager: DetectionManager) : Listener {

    private val movementChecks get() = listOf(
        manager.flightCheck,
        manager.speedCheck,
        manager.jesusCheck,
        manager.stepCheck,
        manager.spiderCheck,
        manager.timerCheck,
        manager.blinkCheck,
        manager.invalidRotationCheck,
        manager.velocityCheck
    )

    private fun flag(player: Player, profile: PlayerProfile, result: ViolationResult) {
        manager.alert.handle(player, profile, result)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!manager.canCheck(player)) return
        val to = event.to
        if (event.from.x == to.x && event.from.y == to.y && event.from.z == to.z &&
            event.from.yaw == to.yaw && event.from.pitch == to.pitch
        ) return

        val profile = manager.getOrCreate(player)
        profile.lastMoveTick = player.world.fullTime
        profile.lastYaw = event.from.yaw
        profile.lastPitch = event.from.pitch
        profile.recordMove()

        if (BukkitExtensions.isOnGround(player) || player.isFlying || player.isInsideVehicle) {
            profile.airTicks = 0
        } else {
            profile.airTicks++
        }

        profile.updateFallState(BukkitExtensions.isOnGround(player), player.fallDistance)

        for (check in movementChecks) {
            check.check(player, profile)?.let { flag(player, profile, it) }
        }

        if (BukkitExtensions.isOnGround(player) && profile.pendingFallDistance > 0) {
            val sinceDamage = System.currentTimeMillis() - profile.lastFallDamageMs
            if (sinceDamage > 500) {
                manager.noFallCheck.checkLand(player, profile)?.let { flag(player, profile, it) }
            }
            profile.pendingFallDistance = 0.0
        }

        profile.lastLocation = to.clone()
        profile.lastOnGround = BukkitExtensions.isOnGround(player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        if (!manager.canCheck(attacker)) return

        val profile = manager.getOrCreate(attacker)
        val eye = attacker.eyeLocation
        val targetLoc = event.entity.location.add(0.0, event.entity.height / 2.0, 0.0)
        val distance = eye.distance(targetLoc)

        manager.reachCheck.checkAttack(attacker, profile, event.entity, distance)?.let {
            flag(attacker, profile, it)
        }
        manager.killAuraCheck.checkAttack(attacker, profile, event.entity)?.let {
            flag(attacker, profile, it)
        }
        manager.aimAssistCheck.checkAttack(attacker, profile, event.entity)?.let {
            flag(attacker, profile, it)
        }
        manager.wallHitCheck.checkAttack(attacker, profile, event.entity)?.let {
            flag(attacker, profile, it)
        }

        manager.plugin.recordingManager.tagSuspiciousEvent(
            attacker.name,
            "attack",
            """{"target":"${BukkitExtensions.entityLabel(event.entity)}","dist":"${String.format("%.2f", distance)}"}"""
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwing(event: PlayerAnimationEvent) {
        val player = event.player
        if (!manager.canCheck(player)) return
        val profile = manager.getOrCreate(player)
        manager.autoClickCheck.checkSwing(player, profile)?.let { flag(player, profile, it) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (!manager.canCheck(player)) return
        val profile = manager.getOrCreate(player)

        manager.fastBreakCheck.checkBreak(player, profile)?.let { flag(player, profile, it) }
        manager.xRayCheck.checkBreak(player, profile, event.block)?.let { flag(player, profile, it) }

        manager.plugin.recordingManager.tagSuspiciousEvent(
            player.name,
            "mining",
            """{"block":"${event.block.type.name}","x":${event.block.x},"y":${event.block.y},"z":${event.block.z}}"""
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        if (!manager.canCheck(player)) return
        val profile = manager.getOrCreate(player)
        manager.scaffoldCheck.checkPlace(player, profile, event.block)?.let {
            flag(player, profile, it)
        }
        manager.plugin.recordingManager.tagSuspiciousEvent(
            player.name,
            "place",
            """{"block":"${event.block.type.name}"}"""
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!manager.canCheck(player)) return
        val profile = manager.getOrCreate(player)

        manager.chestStealerCheck.checkClick(player, profile, event)?.let {
            flag(player, profile, it)
        }
        manager.fastInventoryCheck.checkClick(player, profile, event)?.let {
            flag(player, profile, it)
        }

        manager.plugin.recordingManager.tagSuspiciousEvent(
            player.name,
            "inventory",
            """{"slot":${event.rawSlot},"container":"${event.view.topInventory.type.name}"}"""
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        if (!manager.canCheck(player)) return
        val profile = manager.getOrCreate(player)
        manager.fastEatCheck.checkConsume(player, profile, event)?.let {
            flag(player, profile, it)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (!manager.canCheck(player)) return
        val profile = manager.getOrCreate(player)

        when (event.cause) {
            EntityDamageEvent.DamageCause.FALL -> profile.recordFallDamage()
            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            EntityDamageEvent.DamageCause.PROJECTILE -> {
                manager.velocityCheck.onKnockback(player, profile, event.damage)
            }
            else -> {}
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val profile = manager.getOrCreate(event.player)
        profile.lastYaw = event.player.location.yaw
        profile.lastPitch = event.player.location.pitch
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        manager.remove(event.player.uniqueId)
    }
}