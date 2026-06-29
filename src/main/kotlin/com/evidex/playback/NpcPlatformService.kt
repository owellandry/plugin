package com.evidex.playback

import com.evidex.EvidexPlugin
import com.github.juliarn.npclib.api.Npc
import com.github.juliarn.npclib.api.Platform
import com.github.juliarn.npclib.bukkit.BukkitPlatform
import com.github.juliarn.npclib.bukkit.BukkitWorldAccessor
import com.github.juliarn.npclib.bukkit.protocol.BukkitProtocolAdapter
import com.github.retrooper.packetevents.PacketEvents
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

object NpcPlatformService {

    private var platform: Platform<World, Player, ItemStack, Plugin>? = null

    fun init(plugin: EvidexPlugin) {
        if (platform != null) return

        platform = BukkitPlatform.bukkitNpcPlatformBuilder()
            .extension(plugin)
            .debug(true)
            .packetFactory(BukkitProtocolAdapter.packetEvents())
            .worldAccessor(BukkitWorldAccessor.nameBasedAccessor())
            .build()

        plugin.logger.info("[Evidex] NPC platform ready (fake players with skins via PacketEvents)")
    }

    fun get(): Platform<World, Player, ItemStack, Plugin>? = platform

    fun shutdown() {
        platform = null
        PacketEventsService.shutdown()
    }
}