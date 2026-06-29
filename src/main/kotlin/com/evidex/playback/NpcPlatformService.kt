package com.evidex.playback

import com.evidex.EvidexPlugin
import com.evidex.util.EvidexLog
import com.github.juliarn.npclib.api.Platform
import com.github.juliarn.npclib.bukkit.BukkitPlatform
import com.github.juliarn.npclib.bukkit.BukkitWorldAccessor
import com.github.juliarn.npclib.bukkit.protocol.BukkitProtocolAdapter
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

object NpcPlatformService {

    private var platform: Platform<World, Player, ItemStack, Plugin>? = null

    fun init(plugin: EvidexPlugin, log: EvidexLog) {
        if (platform != null) return

        platform = BukkitPlatform.bukkitNpcPlatformBuilder()
            .extension(plugin)
            .packetFactory(BukkitProtocolAdapter.packetEvents())
            .worldAccessor(BukkitWorldAccessor.nameBasedAccessor())
            .build()

        log.debug("Plataforma NPC lista")
    }

    fun get(): Platform<World, Player, ItemStack, Plugin>? = platform

    fun shutdown() {
        platform = null
        PacketEventsService.shutdown()
    }
}