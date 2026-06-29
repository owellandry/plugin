package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.EquipmentFrame
import com.evidex.recording.ItemFrame
import com.evidex.recording.PlayerFrame
import com.evidex.recording.RecordingData
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask

class ReplaySession(
    private val plugin: EvidexPlugin,
    val viewer: Player,
    val recording: RecordingData
) {
    private var task: BukkitTask? = null
    private var currentFrameIndex = 0
    private var isPlaying = false
    private var originalGameMode: GameMode = viewer.gameMode
    private var originalAllowFlight: Boolean = viewer.allowFlight
    private var originalFlying: Boolean = viewer.isFlying
    private var originalLocation: Location? = null
    private var originalHealth: Double = viewer.health
    private var originalFood: Int = viewer.foodLevel
    private var originalHeldSlot: Int = viewer.inventory.heldItemSlot
    private val originalInventory: Array<ItemStack?> = viewer.inventory.contents.clone()
    private val originalArmor: Array<ItemStack?> = viewer.inventory.armorContents.clone()
    private var entityRenderer: ReplayEntityRenderer? = null
    private var replayWorld = viewer.world

    fun start() {
        if (recording.frames.isEmpty()) {
            viewer.sendMessage("§6[Evidex] §cLa grabación no tiene fotogramas")
            return
        }

        if (!PacketEventsService.isReady()) {
            viewer.sendMessage("§6[Evidex] §cPacketEvents no está listo — reinicia el servidor (no uses /reload)")
            return
        }

        if (plugin.configManager.isReplayTeleportToWorld() && !ensureReplayWorld()) return
        if (!plugin.configManager.isReplayTeleportToWorld()) {
            replayWorld = viewer.world
        }

        originalGameMode = viewer.gameMode
        originalAllowFlight = viewer.allowFlight
        originalFlying = viewer.isFlying
        originalLocation = viewer.location.clone()
        originalHealth = viewer.health
        originalFood = viewer.foodLevel
        originalHeldSlot = viewer.inventory.heldItemSlot
        viewer.inventory.contents.forEachIndexed { i, item ->
            originalInventory[i] = item?.clone()
        }
        viewer.inventory.armorContents.forEachIndexed { i, item ->
            originalArmor[i] = item?.clone()
        }

        viewer.gameMode = GameMode.ADVENTURE
        viewer.allowFlight = true

        val platform = NpcPlatformService.get()
        if (platform != null) {
            entityRenderer = ReplayEntityRenderer(
                plugin, platform, viewer, replayWorld, plugin.configManager
            )
        } else {
            viewer.sendMessage("§6[Evidex] §eAviso: NPCs desactivados — reinicia el servidor")
        }

        applyFrame(recording.frames.first())

        isPlaying = true
        currentFrameIndex = 1
        startTimestamp = System.currentTimeMillis()

        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            tick()
        }, 1L, 1L)

        val entitySample = recording.frames.sumOf { it.nearbyEntities.size }
        viewer.sendMessage("§6[Evidex] §aReplay iniciado — ${recording.frames.size} fotogramas (primera persona)")
        viewer.sendMessage("§6[Evidex] §7Mundo: §f${replayWorld.name} §7| Jugador: §f${recording.playerName}")
        if (entitySample == 0) {
            viewer.sendMessage("§6[Evidex] §eEsta grabación no tiene entidades capturadas — graba de nuevo con jugadores/mobs cerca")
        } else {
            viewer.sendMessage("§6[Evidex] §7Entidades en evidencia: §f$entitySample §7registros totales en frames")
        }
        viewer.sendMessage("§6[Evidex] §eUsa /evidex stop replay para detener")
    }

    private fun ensureReplayWorld(): Boolean {
        val targetName = recording.worldName
        if (targetName.isNullOrBlank()) {
            replayWorld = viewer.world
            return true
        }

        val targetWorld = Bukkit.getWorld(targetName)
        if (targetWorld == null) {
            viewer.sendMessage("§6[Evidex] §cMundo '$targetName' no cargado. Carga ese mundo antes del replay.")
            return false
        }

        replayWorld = targetWorld
        if (viewer.world.uid != targetWorld.uid) {
            val spawn = recording.frames.firstOrNull()?.let { frame ->
                ReplayCamera.firstPersonLocation(frame, targetWorld)
            } ?: Location(targetWorld, 0.5, 100.0, 0.5)
            viewer.teleport(spawn)
            viewer.sendMessage("§6[Evidex] §7Teletransportado al mundo de la evidencia: §f$targetName")
        }
        return true
    }

    private var startTimestamp = 0L

    private fun tick() {
        if (!isPlaying) return
        if (currentFrameIndex >= recording.frames.size) {
            stop()
            viewer.sendMessage("§6[Evidex] §aReplay finalizado")
            return
        }

        val frame = recording.frames[currentFrameIndex]
        val elapsed = System.currentTimeMillis() - startTimestamp

        if (elapsed >= frame.timestamp) {
            applyFrame(frame)
            currentFrameIndex++
        }
    }

    private fun applyFrame(frame: PlayerFrame) {
        viewer.teleport(ReplayCamera.firstPersonLocation(frame, replayWorld))
        applyEquipment(frame.equipment)
        viewer.health = frame.health.coerceIn(0.5f, viewer.maxHealth.toFloat()).toDouble()
        viewer.foodLevel = frame.food.coerceIn(0, 20)
        viewer.inventory.heldItemSlot = frame.hotbarSlot.coerceIn(0, 8)
        viewer.isFlying = frame.isFlying && !frame.onGround
        viewer.isSneaking = frame.isSneaking
        viewer.isSprinting = frame.isSprinting

        entityRenderer?.update(frame.nearbyEntities)

        if (plugin.configManager.isReplayShowActionBar()) {
            val nearby = frame.nearbyEntities.size
            val players = frame.nearbyEntities.count { it.entityType.equals("PLAYER", ignoreCase = true) }
            val mobs = nearby - players
            viewer.sendActionBar(
                Component.text(
                    "§6Evidex §7| §f${recording.playerName} §7| frame ${currentFrameIndex + 1}/${recording.frames.size} §7| §e$nearby entidades §7(§a$players jug §7/ §c$mobs mobs§7)"
                )
            )
        }
    }

    private fun applyEquipment(equipment: EquipmentFrame) {
        val inv = viewer.inventory
        inv.setItemInMainHand(itemFrom(equipment.mainHand))
        inv.setItemInOffHand(itemFrom(equipment.offHand))
        inv.helmet = itemFrom(equipment.helmet)
        inv.chestplate = itemFrom(equipment.chestplate)
        inv.leggings = itemFrom(equipment.leggings)
        inv.boots = itemFrom(equipment.boots)
    }

    private fun itemFrom(item: ItemFrame?): ItemStack {
        if (item == null) return ItemStack(Material.AIR)
        val material = runCatching { Material.valueOf(item.material) }.getOrNull() ?: Material.STONE
        return ItemStack(material, item.count.coerceAtLeast(1))
    }

    fun stop() {
        isPlaying = false
        task?.cancel()
        task = null

        entityRenderer?.clear()
        entityRenderer = null

        viewer.gameMode = originalGameMode
        viewer.allowFlight = originalAllowFlight
        viewer.isFlying = originalFlying
        viewer.health = originalHealth
        viewer.foodLevel = originalFood
        viewer.inventory.heldItemSlot = originalHeldSlot
        viewer.inventory.contents = originalInventory
        viewer.inventory.armorContents = originalArmor

        originalLocation?.let { viewer.teleport(it) }
    }
}