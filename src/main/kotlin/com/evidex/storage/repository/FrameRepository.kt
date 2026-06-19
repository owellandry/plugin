package com.evidex.storage.repository

import com.evidex.math.Angle
import com.evidex.math.Vec3d
import com.evidex.recording.EquipmentFrame
import com.evidex.recording.ItemFrame
import com.evidex.recording.PlayerFrame
import java.io.File
import java.io.RandomAccessFile

class FrameRepository(private val framesDir: File) {

    init {
        framesDir.mkdirs()
    }

    fun writeFrames(recordingId: Long, frames: List<PlayerFrame>): String {
        val file = File(framesDir, "rec_${recordingId}.evf")
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(MAGIC)      // 4 bytes magic
            raf.writeInt(VERSION) // 4 bytes version
            raf.writeLong(recordingId) // 8 bytes recording id
            raf.writeInt(frames.size) // 4 bytes frame count

            for (frame in frames) {
                writeFrame(raf, frame)
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
                frames.add(readFrame(raf))
            }
            return frames
        }
    }

    fun deleteFile(filePath: String) {
        File(filePath).delete()
    }

    private fun writeFrame(raf: RandomAccessFile, frame: PlayerFrame) {
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
    }

    private fun readFrame(raf: RandomAccessFile): PlayerFrame {
        val timestamp = raf.readLong()
        val x = raf.readDouble()
        val y = raf.readDouble()
        val z = raf.readDouble()
        val yaw = raf.readFloat()
        val pitch = raf.readFloat()
        val flags = raf.readByte().toInt()

        val equipment = readEquipment(raf)

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
            equipment = equipment
        )
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
        private const val VERSION = 1
    }
}
