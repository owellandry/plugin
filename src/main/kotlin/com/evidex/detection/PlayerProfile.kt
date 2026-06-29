package com.evidex.detection

import org.bukkit.Location
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerProfile(val uuid: UUID, var name: String) {

    val joinedAt: Long = System.currentTimeMillis()
    val lastAlertMs: MutableMap<String, Long> = ConcurrentHashMap()
    val moveTimestamps: MutableList<Long> = mutableListOf()
    val placeTimestamps: MutableList<Long> = mutableListOf()

    var lastLocation: Location? = null
    var lastOnGround: Boolean = true
    var airTicks: Int = 0
    var lastMoveTick: Long = 0

    val vlByCheck: MutableMap<String, Int> = ConcurrentHashMap()
    val swingTimestamps: MutableList<Long> = mutableListOf()
    val recentAttackTargets: MutableList<Pair<UUID, Long>> = mutableListOf()

    var lastAttackDistance: Double? = null
    var lastAttackTargetUuid: UUID? = null

    var lastYaw: Float = 0f
    var lastPitch: Float = 0f
    var lastAngleToTarget: Double? = null

    var wasInAir: Boolean = false
    var maxFallDuringAir: Float = 0f
    var pendingFallDistance: Double = 0.0
    var lastFallDamageMs: Long = 0

    var lastBlockBreakMs: Long = 0
    var lastEatMs: Long = 0
    var lastVelocityMs: Long = 0
    var expectedKnockback: Double = 0.0
    private val hiddenOreBreakTimestamps = mutableListOf<Long>()
    private val containerClickTimestamps = mutableListOf<Long>()
    private val inventoryClickTimestamps = mutableListOf<Long>()

    fun recordMove(now: Long = System.currentTimeMillis()) {
        moveTimestamps.add(now)
        val cutoff = now - 1000
        moveTimestamps.removeIf { it < cutoff }
    }

    fun movesPerSecond(): Int = moveTimestamps.size

    fun recordPlace(now: Long = System.currentTimeMillis()) {
        placeTimestamps.add(now)
        val cutoff = now - 1000
        placeTimestamps.removeIf { it < cutoff }
    }

    fun placesPerSecond(): Int = placeTimestamps.size

    fun totalVl(): Int = vlByCheck.values.sum()

    fun addVl(check: String, amount: Int, maxVl: Int): Int {
        val current = vlByCheck.getOrDefault(check, 0)
        val next = (current + amount).coerceAtMost(maxVl)
        vlByCheck[check] = next
        return next
    }

    fun decayVl(factor: Double) {
        val iterator = vlByCheck.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val decayed = (entry.value * factor).toInt()
            if (decayed <= 0) iterator.remove() else entry.setValue(decayed)
        }
    }

    fun recordSwing(now: Long = System.currentTimeMillis()) {
        swingTimestamps.add(now)
        val cutoff = now - 2000
        swingTimestamps.removeIf { it < cutoff }
    }

    fun cps(windowMs: Long = 1000): Int {
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs
        return swingTimestamps.count { it >= cutoff }
    }

    fun recordAttack(target: UUID, now: Long = System.currentTimeMillis()) {
        recentAttackTargets.add(target to now)
        val cutoff = now - 500
        recentAttackTargets.removeIf { it.second < cutoff }
    }

    fun distinctRecentTargets(): Int =
        recentAttackTargets.map { it.first }.distinct().size

    fun horizontalSpeed(from: Location, to: Location): Double {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    fun verticalDelta(from: Location, to: Location): Double = to.y - from.y

    fun recordHiddenOreBreak(now: Long = System.currentTimeMillis()) {
        hiddenOreBreakTimestamps.add(now)
        val cutoff = now - 120_000
        hiddenOreBreakTimestamps.removeIf { it < cutoff }
    }

    fun hiddenOreBreaksRecent(): Int = hiddenOreBreakTimestamps.size

    fun recordFallDamage(now: Long = System.currentTimeMillis()) {
        lastFallDamageMs = now
        pendingFallDistance = 0.0
    }

    fun recordContainerClick(now: Long = System.currentTimeMillis()) {
        containerClickTimestamps.add(now)
        val cutoff = now - 2000
        containerClickTimestamps.removeIf { it < cutoff }
    }

    fun containerClicksInWindow(windowMs: Long): Int {
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs
        return containerClickTimestamps.count { it >= cutoff }
    }

    fun recordInventoryClick(now: Long = System.currentTimeMillis()) {
        inventoryClickTimestamps.add(now)
        val cutoff = now - 2000
        inventoryClickTimestamps.removeIf { it < cutoff }
    }

    fun inventoryClicksPerSecond(): Int {
        val now = System.currentTimeMillis()
        val cutoff = now - 1000
        return inventoryClickTimestamps.count { it >= cutoff }
    }

    fun updateFallState(onGround: Boolean, fallDistance: Float) {
        if (!onGround) {
            wasInAir = true
            if (fallDistance > maxFallDuringAir) maxFallDuringAir = fallDistance
        } else if (wasInAir) {
            if (maxFallDuringAir > 0f) pendingFallDistance = maxFallDuringAir.toDouble()
            maxFallDuringAir = 0f
            wasInAir = false
        }
    }
}