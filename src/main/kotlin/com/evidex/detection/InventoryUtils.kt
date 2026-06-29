package com.evidex.detection

import org.bukkit.event.inventory.InventoryType

object InventoryUtils {

    private val STORAGE_TYPES = setOf(
        InventoryType.CHEST,
        InventoryType.ENDER_CHEST,
        InventoryType.BARREL,
        InventoryType.SHULKER_BOX,
        InventoryType.HOPPER,
        InventoryType.DISPENSER,
        InventoryType.DROPPER,
        InventoryType.FURNACE,
        InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER,
        InventoryType.BREWING
    )

    fun isStorageContainer(type: InventoryType): Boolean = type in STORAGE_TYPES
}