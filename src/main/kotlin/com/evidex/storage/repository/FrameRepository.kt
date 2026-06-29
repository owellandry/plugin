package com.evidex.storage.repository

import com.evidex.math.Angle
import com.evidex.math.Vec3d
import com.evidex.recording.EquipmentFrame
import com.evidex.recording.ItemFrame
import com.evidex.recording.NearbyEntityFrame
import com.evidex.recording.PlayerFrame
import java.io.File
import java.io.RandomAccessFile

class FrameRepository(private val framesDir: File) {

    init {
        framesDir.mkdirs()
    }

    fun writeFrames(recordingId: Long, frames: List<PlayerFrame>, maxEntitiesPerFrame: Int = MAX_NEARBY_ENTITIES): String {
        val file = File(framesDir, "rec_${recordingId}.evf")
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(MAGIC)      // 4 bytes magic
            raf.writeInt(VERSION) // 4 bytes version
            raf.writeLong(recordingId) // 8 bytes recording id
            raf.writeInt(frames.size) // 4 bytes frame count

            for (frame in frames) {
                writeFrame(raf, frame, maxEntitiesPerFrame)
            }
        }
        return file.absolutePath
    }

    fun appendFrame(filePath: String, frame: PlayerFrame) {
        val file = File(filePath)
        if (!file.exists()) return

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(8)
            val recordingId = raf.readLong()
            val frameCount = raf.readInt()
            raf.seek(16)
            raf.writeInt(frameCount + 1)
            raf.seek(raf.length())
            writeFrame(raf, frame)
        }
    }

    fun readFrames(filePath: String): List<PlayerFrame> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()

        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (!magic.contentEquals(MAGIC)) return emptyList()

            val version = raf.readInt()
            val recordingId = raf.readLong()
            val frameCount = raf.readInt()

            val frames = mutableListOf<PlayerFrame>()
            for (i in 0 until frameCount) {
                frames.add(readFrame(raf, version))
            }
            return frames
        }
    }

    fun deleteFile(filePath: String) {
        File(filePath).delete()
    }

    private fun writeFrame(raf: RandomAccessFile, frame: PlayerFrame, maxEntitiesPerFrame: Int = MAX_NEARBY_ENTITIES) {
        raf.writeLong(frame.timestamp)
        raf.writeDouble(frame.position.x)
        raf.writeDouble(frame.position.y)
        raf.writeDouble(frame.position.z)
        raf.writeFloat(frame.yaw.degrees)
        raf.writeFloat(frame.pitch.degrees)

        var flags = 0
        if (frame.onGround) flags = flags or 0x01
        if (frame.isSneaking) flags = flags or 0x02
        if (frame.isSprinting) flags = flags or 0x04
        if (frame.isFlying) flags = flags or 0x08
        if (frame.handSwing) flags = flags or 0x10
        raf.writeByte(flags)

        writeEquipment(raf, frame.equipment)
        raf.writeFloat(frame.health)
        raf.writeByte(frame.food.coerceIn(0, 20))
        raf.writeByte(frame.hotbarSlot.coerceIn(0, 8))
        writeNearbyEntities(raf, frame.nearbyEntities, maxEntitiesPerFrame)
    }

    private fun readFrame(raf: RandomAccessFile, version: Int): PlayerFrame {
        val timestamp = raf.readLong()
        val x = raf.readDouble()
        val y = raf.readDouble()
        val z = raf.readDouble()
        val yaw = raf.readFloat()
        val pitch = raf.readFloat()
        val flags = raf.readByte().toInt()

        val equipment = readEquipment(raf)

        val health: Float
        val food: Int
        val hotbarSlot: Int
        val nearbyEntities: List<NearbyEntityFrame>
        if (version >= 2) {
            health = raf.readFloat()
            food = raf.readUnsignedByte()
            hotbarSlot = raf.readUnsignedByte()
            nearbyEntities = readNearbyEntities(raf, version)
        } else {
            health = 20f
            food = 20
            hotbarSlot = 0
            nearbyEntities = emptyList()
        }

        return PlayerFrame(
            timestamp = timestamp,
            position = Vec3d(x, y, z),
            yaw = Angle(yaw),
            pitch = Angle(pitch),
            onGround = (flags and 0x01) != 0,
            isSneaking = (flags and 0x02) != 0,
            isSprinting = (flags and 0x04) != 0,
            isFlying = (flags and 0x08) != 0,
            handSwing = (flags and 0x10) != 0,
            equipment = equipment,
            health = health,
            food = food,
            hotbarSlot = hotbarSlot,
            nearbyEntities = nearbyEntities
        )
    }

    private fun writeNearbyEntities(
        raf: RandomAccessFile,
        entities: List<NearbyEntityFrame>,
        maxEntitiesPerFrame: Int = MAX_NEARBY_ENTITIES
    ) {
        val capped = entities.take(maxEntitiesPerFrame.coerceIn(4, 64))
        raf.writeShort(capped.size)
        for (entity in capped) {
            val typeBytes = entity.entityType.toByteArray(Charsets.UTF_8)
            raf.writeByte(typeBytes.size.coerceAtMost(255))
            raf.write(typeBytes.copyOf(typeBytes.size.coerceAtMost(255)))

            val name = entity.name
            if (name == null) {
                raf.writeShort(0)
            } else {
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                raf.writeShort(nameBytes.size.coerceAtMost(65535))
                raf.write(nameBytes.copyOf(nameBytes.size.coerceAtMost(65535)))
            }

            val uuid = entity.playerUuid
            if (uuid.isNullOrBlank()) {
                raf.writeShort(0)
            } else {
                val uuidBytes = uuid.toByteArray(Charsets.UTF_8)
                raf.writeShort(uuidBytes.size.coerceAtMost(65535))
                raf.write(uuidBytes.copyOf(uuidBytes.size.coerceAtMost(65535)))
            }

            val entityUuid = entity.entityUuid
            if (entityUuid.isNullOrBlank()) {
                raf.writeShort(0)
            } else {
                val entityUuidBytes = entityUuid.toByteArray(Charsets.UTF_8)
                raf.writeShort(entityUuidBytes.size.coerceAtMost(65535))
                raf.write(entityUuidBytes.copyOf(entityUuidBytes.size.coerceAtMost(65535)))
            }

            raf.writeDouble(entity.position.x)
            raf.writeDouble(entity.position.y)
            raf.writeDouble(entity.position.z)
            raf.writeFloat(entity.yaw.degrees)
            raf.writeFloat(entity.pitch.degrees)
            var entityFlags = 0
            if (entity.isSneaking) entityFlags = entityFlags or 0x01
            if (entity.isBaby) entityFlags = entityFlags or 0x02
            raf.writeByte(entityFlags)
        }
    }

    private fun readNearbyEntities(raf: RandomAccessFile, version: Int): List<NearbyEntityFrame> {
        val count = raf.readUnsignedShort()
        val entities = mutableListOf<NearbyEntityFrame>()
        repeat(count) {
            val typeLen = raf.readUnsignedByte()
            val typeBytes = ByteArray(typeLen)
            raf.readFully(typeBytes)
            val entityType = String(typeBytes, Charsets.UTF_8)

            val nameLen = raf.readUnsignedShort()
            val name = if (nameLen == 0) {
                null
            } else {
                val nameBytes = ByteArray(nameLen)
                raf.readFully(nameBytes)
                String(nameBytes, Charsets.UTF_8)
            }

            val playerUuid = if (version >= 3) {
                val uuidLen = raf.readUnsignedShort()
                if (uuidLen == 0) {
                    null
                } else {
                    val uuidBytes = ByteArray(uuidLen)
                    raf.readFully(uuidBytes)
                    String(uuidBytes, Charsets.UTF_8)
                }
            } else {
                null
            }

            val entityUuid = if (version >= 4) {
                val entityUuidLen = raf.readUnsignedShort()
                if (entityUuidLen == 0) {
                    null
                } else {
                    val entityUuidBytes = ByteArray(entityUuidLen)
                    raf.readFully(entityUuidBytes)
                    String(entityUuidBytes, Charsets.UTF_8)
                }
            } else {
                playerUuid
            }

            val x = raf.readDouble()
            val y = raf.readDouble()
            val z = raf.readDouble()
            val yaw = raf.readFloat()
            val pitch = raf.readFloat()
            val entityFlags = raf.readByte().toInt()

            entities.add(
                NearbyEntityFrame(
                    entityType = entityType,
                    name = name,
                    playerUuid = playerUuid,
                    entityUuid = entityUuid,
                    position = Vec3d(x, y, z),
                    yaw = Angle(yaw),
                    pitch = Angle(pitch),
                    isSneaking = (entityFlags and 0x01) != 0,
                    isBaby = (entityFlags and 0x02) != 0
                )
            )
        }
        return entities
    }

    private fun writeEquipment(raf: RandomAccessFile, eq: EquipmentFrame) {
        writeItem(raf, eq.mainHand)
        writeItem(raf, eq.offHand)
        writeItem(raf, eq.helmet)
        writeItem(raf, eq.chestplate)
        writeItem(raf, eq.leggings)
        writeItem(raf, eq.boots)
    }

    private fun readEquipment(raf: RandomAccessFile): EquipmentFrame {
        return EquipmentFrame(
            mainHand = readItem(raf),
            offHand = readItem(raf),
            helmet = readItem(raf),
            chestplate = readItem(raf),
            leggings = readItem(raf),
            boots = readItem(raf)
        )
    }

    private fun writeItem(raf: RandomAccessFile, item: ItemFrame?) {
        if (item == null) {
            raf.writeByte(0)
            return
        }
        raf.writeByte(1)
        val bytes = item.material.toByteArray(Charsets.UTF_8)
        raf.writeShort(bytes.size)
        raf.write(bytes)
        raf.writeShort(item.count)
    }

    private fun readItem(raf: RandomAccessFile): ItemFrame? {
        val present = raf.readByte().toInt()
        if (present == 0) return null
        val len = raf.readUnsignedShort()
        val bytes = ByteArray(len)
        raf.readFully(bytes)
        val material = String(bytes, Charsets.UTF_8)
        val count = raf.readUnsignedShort()
        return ItemFrame(material, count)
    }

    companion object {
        private val MAGIC = byteArrayOf(0x45, 0x56, 0x46, 0x58) // "EVFX"
        private const val VERSION = 4
        private const val MAX_NEARBY_ENTITIES = 64
    }
}
