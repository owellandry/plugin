package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.recording.EquipmentFrame
import com.evidex.recording.ItemFrame
import com.evidex.recording.PlayerFrame
import com.evidex.recording.RecordingData
import com.evidex.storage.repository.WorldRepository
import com.evidex.util.BukkitExtensions
import com.evidex.util.EvidexMessages
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask

class ReplaySession(
    private val plugin: EvidexPlugin,
    private val worldRepository: WorldRepository,
    val viewer: Player,
    val recording: RecordingData
) {
    private var task: BukkitTask? = null
    private var currentFrameIndex = 0
    private var isPlaying = false
    private var paused = false
    private var speedMultiplier = 1.0
    private var playheadMs = 0L
    private var lastTickWallMs = 0L

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
    private var entityIsolation: ReplayEntityIsolation? = null
    private var inputGuard: ReplayInputGuard? = null
    private var worldOverlay: ReplayWorldOverlay? = null
    private var blockTimeline: ReplayBlockTimeline? = null
    private var replayWorld = viewer.world
    private var preparingWorld = false

    val isPaused: Boolean get() = paused
    val playbackSpeed: Double get() = speedMultiplier
    val frameIndex: Int get() = currentFrameIndex.coerceAtMost(recording.frames.lastIndex.coerceAtLeast(0))
    val frameCount: Int get() = recording.frames.size
    val isActive: Boolean get() = isPlaying || preparingWorld

    fun start() {
        if (recording.frames.isEmpty()) {
            EvidexMessages.error(viewer, "La grabación no tiene fotogramas")
            return
        }

        if (!PacketEventsService.isReady()) {
            EvidexMessages.error(viewer, "PacketEvents no está listo — reinicia el servidor (no uses /reload)")
            return
        }

        if (plugin.configManager.isReplayTeleportToWorld() && !ensureReplayWorld()) return
        if (!plugin.configManager.isReplayTeleportToWorld()) {
            replayWorld = viewer.world
        }

        saveViewerState()
        viewer.gameMode = GameMode.ADVENTURE
        viewer.allowFlight = true

        if (plugin.configManager.isReplayIsolateLiveEntities()) {
            entityIsolation = ReplayEntityIsolation(plugin, viewer).also { it.activate() }
        }
        if (plugin.configManager.isReplayLockInput()) {
            inputGuard = ReplayInputGuard(plugin, viewer).also { it.activate() }
        }

        val isolation = entityIsolation
        entityRenderer = ReplayEntityRenderer(
            plugin,
            viewer,
            replayWorld,
            plugin.configManager,
            isolation
        )

        preparingWorld = true
        EvidexMessages.info(viewer, "§6Modo evidencia §8— cargando escena…")

        beginWorldOverlay {
            prepareBlockTimeline()
            preparingWorld = false
            beginPlayback()
        }
    }

    private fun beginWorldOverlay(onReady: () -> Unit) {
        val snapshot = if (plugin.configManager.isReplayApplyWorldSnapshot() && recording.worldFilePath.isNotBlank()) {
            worldRepository.readSnapshot(recording.worldFilePath)
        } else null

        if (plugin.configManager.isReplayApplyWorldSnapshot() && recording.worldFilePath.isBlank()) {
            EvidexMessages.warn(viewer, "Sin snapshot de mundo — solo entidades grabadas")
        }

        if (snapshot == null || snapshot.blocks.isEmpty()) {
            if (plugin.configManager.isReplayApplyWorldSnapshot() && recording.worldFilePath.isNotBlank()) {
                EvidexMessages.warn(viewer, "Snapshot no disponible — mapa en vivo")
            }
            prepareBlockTimeline()
            onReady()
            return
        }

        blockTimeline = buildBlockTimeline(snapshot.blocks)

        worldOverlay = ReplayWorldOverlay(
            plugin,
            replayWorld,
            snapshot.blocks,
            plugin.configManager.getReplayWorldOverlayBlocksPerTick()
        )
        worldOverlay!!.begin(onReady)
    }

    private fun prepareBlockTimeline() {
        if (blockTimeline != null) {
            blockTimeline!!.initializeBaseState()
            return
        }
        blockTimeline = buildBlockTimeline(emptyList())
        blockTimeline?.initializeBaseState()
    }

    private fun buildBlockTimeline(snapshotBlocks: List<com.evidex.recording.WorldBlock>): ReplayBlockTimeline? {
        if (recording.blockChanges.isEmpty()) return null

        val snapshotMaterials = HashMap<Long, Material>(snapshotBlocks.size)
        for (block in snapshotBlocks) {
            val material = runCatching { Material.valueOf(block.material) }.getOrNull() ?: continue
            snapshotMaterials[ReplayBlockKeys.pack(block.x, block.y, block.z)] = material
        }
        return ReplayBlockTimeline(replayWorld, snapshotMaterials, recording.blockChanges)
    }

    private fun beginPlayback() {
        currentFrameIndex = 0
        playheadMs = 0L
        lastTickWallMs = System.currentTimeMillis()
        paused = false
        speedMultiplier = 1.0

        applyFrame(recording.frames.first())
        currentFrameIndex = 1
        isPlaying = true

        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)

        val entitySample = recording.frames.sumOf { it.nearbyEntities.size }
        EvidexMessages.success(viewer, "Replay iniciado — ${recording.frames.size} fotogramas (modo evidencia)")
        EvidexMessages.info(viewer, "Mundo: ${replayWorld.name} | Jugador: ${recording.playerName}")
        EvidexMessages.info(viewer, "Controles: §e/evidex replay pause§7, §eresume§7, §espeed <0.5|1|2>§7, §eskip§7, §estop replay")
        if (entitySample == 0) {
            EvidexMessages.warn(viewer, "Sin entidades capturadas — graba de nuevo con jugadores/mobs cerca")
        }

        plugin.log.info("Replay iniciado: ${viewer.name} revisa a ${recording.playerName}")
    }

    fun pause() {
        if (!isPlaying || paused) return
        paused = true
        EvidexMessages.info(viewer, "Replay en pausa")
    }

    fun resume() {
        if (!isPlaying || !paused) return
        paused = false
        lastTickWallMs = System.currentTimeMillis()
        EvidexMessages.info(viewer, "Replay reanudado (${speedMultiplier}x)")
    }

    fun setSpeed(multiplier: Double) {
        speedMultiplier = multiplier.coerceIn(0.25, 4.0)
        if (isPlaying && !paused) {
            lastTickWallMs = System.currentTimeMillis()
        }
        EvidexMessages.info(viewer, "Velocidad: ${speedMultiplier}x")
    }

    fun skipToNextFlag() {
        if (!isPlaying || recording.frames.isEmpty()) return
        val next = recording.frames.withIndex()
            .drop(currentFrameIndex)
            .firstOrNull { (_, frame) ->
                frame.eventType?.startsWith("flag:") == true ||
                    frame.eventType in FLAG_EVENT_TYPES
            }
        if (next == null) {
            EvidexMessages.warn(viewer, "No hay más flags en esta grabación")
            return
        }
        seekToFrame(next.index)
        EvidexMessages.info(viewer, "Salto al evento: ${next.value.eventType}")
    }

    fun seekToFrame(index: Int) {
        if (recording.frames.isEmpty()) return
        val idx = index.coerceIn(0, recording.frames.lastIndex)
        currentFrameIndex = idx
        playheadMs = recording.frames[idx].timestamp
        blockTimeline?.reapplyUpTo(playheadMs)
        applyFrame(recording.frames[idx])
        lastTickWallMs = System.currentTimeMillis()
        if (idx < recording.frames.lastIndex) {
            currentFrameIndex = idx + 1
        }
    }

    private fun saveViewerState() {
        originalGameMode = viewer.gameMode
        originalAllowFlight = viewer.allowFlight
        originalFlying = viewer.isFlying
        originalLocation = viewer.location.clone()
        originalHealth = viewer.health
        originalFood = viewer.foodLevel
        originalHeldSlot = viewer.inventory.heldItemSlot
        viewer.inventory.contents.forEachIndexed { i, item -> originalInventory[i] = item?.clone() }
        viewer.inventory.armorContents.forEachIndexed { i, item -> originalArmor[i] = item?.clone() }
    }

    private fun ensureReplayWorld(): Boolean {
        val targetName = recording.worldName
        if (targetName.isNullOrBlank()) {
            replayWorld = viewer.world
            return true
        }

        val targetWorld = Bukkit.getWorld(targetName)
        if (targetWorld == null) {
            EvidexMessages.error(viewer, "Mundo '$targetName' no cargado")
            return false
        }

        replayWorld = targetWorld
        if (viewer.world.uid != targetWorld.uid) {
            val spawn = recording.frames.firstOrNull()?.let { ReplayCamera.firstPersonLocation(it, targetWorld) }
                ?: Location(targetWorld, 0.5, 100.0, 0.5)
            viewer.teleport(spawn)
            EvidexMessages.info(viewer, "Teletransportado al mundo: $targetName")
        }
        return true
    }

    private fun tick() {
        if (!isPlaying || paused) return
        if (currentFrameIndex >= recording.frames.size) {
            stop()
            EvidexMessages.success(viewer, "Replay finalizado")
            return
        }

        val now = System.currentTimeMillis()
        val delta = now - lastTickWallMs
        lastTickWallMs = now
        playheadMs += (delta * speedMultiplier).toLong()

        blockTimeline?.applyUpTo(playheadMs)

        while (currentFrameIndex < recording.frames.size &&
            recording.frames[currentFrameIndex].timestamp <= playheadMs
        ) {
            applyFrame(recording.frames[currentFrameIndex])
            currentFrameIndex++
        }
    }

    private fun applyFrame(frame: PlayerFrame) {
        viewer.teleport(ReplayCamera.firstPersonLocation(frame, replayWorld))
        applyEquipment(frame.equipment)
        val maxHp = BukkitExtensions.maxHealth(viewer)
        viewer.health = frame.health.coerceIn(0.5f, maxHp.toFloat()).toDouble()
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
            val state = if (paused) " §e⏸" else " §a▶ ${speedMultiplier}x"
            val eventHint = when {
                frame.eventType?.startsWith("flag:") == true ->
                    " §c⚑ ${frame.eventType.removePrefix("flag:")}"
                !frame.eventType.isNullOrBlank() ->
                    " §e● ${frame.eventType}"
                else -> ""
            }
            val shownIndex = currentFrameIndex.coerceAtLeast(1).coerceAtMost(recording.frames.size)
            EvidexMessages.actionBar(
                viewer,
                "§6Evidex §7| §f${recording.playerName}$state §7| " +
                    "frame $shownIndex/${recording.frames.size}$eventHint §7| " +
                    "§e$nearby ent §7(§a$players jug §7/ §c$mobs mobs§7)"
            )
        }
    }

    private fun applyEquipment(equipment: EquipmentFrame) {
        val inv = viewer.inventory
        inv.setItemInMainHand(itemFrom(equipment.mainHand))
        inv.setItemInOffHand(itemFrom(equipment.offHand))
        inv.setHelmet(itemFrom(equipment.helmet))
        inv.setChestplate(itemFrom(equipment.chestplate))
        inv.setLeggings(itemFrom(equipment.leggings))
        inv.setBoots(itemFrom(equipment.boots))
    }

    private fun itemFrom(item: ItemFrame?): ItemStack {
        if (item == null) return ItemStack(Material.AIR)
        val material = runCatching { Material.valueOf(item.material) }.getOrNull() ?: Material.STONE
        return ItemStack(material, item.count.coerceAtLeast(1))
    }

    fun stop() {
        isPlaying = false
        preparingWorld = false
        paused = false
        task?.cancel()
        task = null

        entityRenderer?.clear()
        entityRenderer = null
        inputGuard?.deactivate()
        inputGuard = null
        entityIsolation?.deactivate()
        entityIsolation = null
        blockTimeline?.restore()
        blockTimeline = null
        worldOverlay?.restore()
        worldOverlay = null

        viewer.gameMode = originalGameMode
        viewer.allowFlight = originalAllowFlight
        viewer.isFlying = originalFlying
        viewer.health = originalHealth
        viewer.foodLevel = originalFood
        viewer.inventory.heldItemSlot = originalHeldSlot
        viewer.inventory.contents = originalInventory
        viewer.inventory.armorContents = originalArmor
        originalLocation?.let { viewer.teleport(it) }

        EvidexMessages.success(viewer, "Modo evidencia finalizado — volviste al servidor normal")
    }

    companion object {
        private val FLAG_EVENT_TYPES = setOf("attack", "mining", "place", "inventory")
    }
}