package com.evidex.storage.repository

import com.evidex.recording.BlockChange
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class BlockChangeRepository(private val framesDir: File) {

    init {
        framesDir.mkdirs()
    }

    fun writeChanges(recordingId: Long, changes: List<BlockChange>): String {
        val file = fileFor(recordingId)
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.write(MAGIC)
            out.writeInt(BINARY_VERSION)
            out.writeInt(changes.size)
            for (change in changes) {
                out.writeLong(change.timestamp)
                out.writeInt(change.x)
                out.writeInt(change.y)
                out.writeInt(change.z)
                writeString(out, change.material)
            }
        }
        return file.absolutePath
    }

    fun readChanges(recordingId: Long): List<BlockChange> {
        val file = fileFor(recordingId)
        if (!file.exists()) return emptyList()

        FileInputStream(file).use { fis ->
            val input = DataInputStream(BufferedInputStream(fis))
            val magic = ByteArray(4)
            if (input.read(magic) != 4 || !magic.contentEquals(MAGIC)) return emptyList()
            val version = input.readInt()
            if (version != BINARY_VERSION) return emptyList()
            val count = input.readInt().coerceAtMost(1_048_576)
            val changes = ArrayList<BlockChange>(count)
            repeat(count) {
                changes.add(
                    BlockChange(
                        timestamp = input.readLong(),
                        x = input.readInt(),
                        y = input.readInt(),
                        z = input.readInt(),
                        material = readString(input)
                    )
                )
            }
            return changes.sortedBy { it.timestamp }
        }
    }

    fun deleteFile(recordingId: Long) {
        fileFor(recordingId).delete()
    }

    private fun fileFor(recordingId: Long): File = File(framesDir, "rec_${recordingId}.evb")

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeShort(bytes.size.coerceAtMost(65535))
        out.write(bytes.copyOf(bytes.size.coerceAtMost(65535)))
    }

    private fun readString(input: DataInputStream): String {
        val len = input.readUnsignedShort()
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        private val MAGIC = byteArrayOf(0x45, 0x56, 0x42, 0x43) // EVBC
        private const val BINARY_VERSION = 1
    }
}