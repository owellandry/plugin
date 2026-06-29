package com.evidex.storage.repository

import com.evidex.recording.WorldBlock
import com.evidex.recording.WorldSnapshot
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WorldRepository(private val framesDir: File) {

    init {
        framesDir.mkdirs()
    }

    fun writeSnapshot(recordingId: Long, snapshot: WorldSnapshot): String {
        val file = File(framesDir, "rec_${recordingId}.evw")
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.write(MAGIC)
            out.writeInt(BINARY_VERSION)
            writeString(out, snapshot.worldName)
            out.writeInt(snapshot.centerX)
            out.writeInt(snapshot.centerY)
            out.writeInt(snapshot.centerZ)
            out.writeInt(snapshot.radius)
            out.writeInt(snapshot.blocks.size)
            for (block in snapshot.blocks) {
                out.writeInt(block.x)
                out.writeInt(block.y)
                out.writeInt(block.z)
                writeString(out, block.material)
            }
        }
        return file.absolutePath
    }

    fun readSnapshot(filePath: String): WorldSnapshot? {
        val file = File(filePath)
        if (!file.exists()) return null

        FileInputStream(file).use { fis ->
            val buffered = BufferedInputStream(fis)
            buffered.mark(4)
            val magic = ByteArray(4)
            val read = buffered.read(magic)
            if (read == 4 && magic.contentEquals(MAGIC)) {
                return readBinary(DataInputStream(buffered))
            }
            buffered.reset()
        }

        return parseSnapshotJson(file.readText())
    }

    /**
     * Loads blocks into an existing map without building a full snapshot object first.
     * Safe to call from a background thread.
     */
    fun loadBlocksInto(
        filePath: String,
        blockMap: MutableMap<Long, WorldBlock>,
        keyOf: (Int, Int, Int) -> Long
    ): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        FileInputStream(file).use { fis ->
            val buffered = BufferedInputStream(fis)
            buffered.mark(4)
            val magic = ByteArray(4)
            val read = buffered.read(magic)
            if (read == 4 && magic.contentEquals(MAGIC)) {
                readBinaryBlocksInto(DataInputStream(buffered), blockMap, keyOf)
                return true
            }
            buffered.reset()
        }

        val snapshot = parseSnapshotJson(file.readText()) ?: return false
        for (block in snapshot.blocks) {
            blockMap[keyOf(block.x, block.y, block.z)] = block
        }
        return true
    }

    fun readSnapshotJson(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) return null
        if (isBinaryFile(file)) return null
        return file.readText()
    }

    fun deleteFile(filePath: String) {
        if (filePath.isBlank()) return
        File(filePath).delete()
    }

    /** JSON representation for dashboard API responses. */
    fun snapshotToJson(snapshot: WorldSnapshot): String {
        val blocksJson = snapshot.blocks.joinToString(",") { block ->
            """{"x":${block.x},"y":${block.y},"z":${block.z},"m":"${escapeJson(block.material)}"}"""
        }
        return """{"version":${snapshot.version},"world":"${escapeJson(snapshot.worldName)}","centerX":${snapshot.centerX},"centerY":${snapshot.centerY},"centerZ":${snapshot.centerZ},"radius":${snapshot.radius},"blocks":[$blocksJson]}"""
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun isBinaryFile(file: File): Boolean {
        FileInputStream(file).use { fis ->
            val magic = ByteArray(4)
            if (fis.read(magic) != 4) return false
            return magic.contentEquals(MAGIC)
        }
    }

    private fun readBinary(input: DataInputStream): WorldSnapshot {
        val version = input.readInt()
        val worldName = readString(input)
        val centerX = input.readInt()
        val centerY = input.readInt()
        val centerZ = input.readInt()
        val radius = input.readInt()
        val blockCount = input.readInt()
        val blocks = ArrayList<WorldBlock>(blockCount.coerceAtMost(1_048_576))
        repeat(blockCount) {
            blocks.add(
                WorldBlock(
                    x = input.readInt(),
                    y = input.readInt(),
                    z = input.readInt(),
                    material = readString(input)
                )
            )
        }
        return WorldSnapshot(version, worldName, centerX, centerY, centerZ, radius, blocks)
    }

    private fun readBinaryBlocksInto(
        input: DataInputStream,
        blockMap: MutableMap<Long, WorldBlock>,
        keyOf: (Int, Int, Int) -> Long
    ) {
        input.readInt() // version
        readString(input) // world
        input.readInt() // centerX
        input.readInt() // centerY
        input.readInt() // centerZ
        input.readInt() // radius
        val blockCount = input.readInt()
        repeat(blockCount) {
            val x = input.readInt()
            val y = input.readInt()
            val z = input.readInt()
            val material = readString(input)
            blockMap[keyOf(x, y, z)] = WorldBlock(x, y, z, material)
        }
    }

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

    // --- Legacy JSON support (old .evw files) ---

    private fun parseSnapshotJson(json: String): WorldSnapshot? {
        val version = extractIntFast(json, "version") ?: 1
        val world = extractStringFast(json, "world") ?: "world"
        val centerX = extractIntFast(json, "centerX") ?: 0
        val centerY = extractIntFast(json, "centerY") ?: 64
        val centerZ = extractIntFast(json, "centerZ") ?: 0
        val radius = extractIntFast(json, "radius") ?: 32
        val blocks = parseBlocksFast(json)
        return WorldSnapshot(version, world, centerX, centerY, centerZ, radius, blocks)
    }

    private fun parseBlocksFast(json: String): List<WorldBlock> {
        val start = json.indexOf("\"blocks\":[")
        if (start < 0) return emptyList()
        val arrayStart = json.indexOf('[', start)
        val arrayEnd = findMatchingBracket(json, arrayStart)
        if (arrayEnd < 0) return emptyList()

        val blocks = ArrayList<WorldBlock>(65536)
        var i = arrayStart + 1
        while (i < arrayEnd) {
            val objStart = json.indexOf('{', i)
            if (objStart < 0 || objStart >= arrayEnd) break
            val objEnd = json.indexOf('}', objStart)
            if (objEnd < 0) break

            val x = extractIntFast(json, "x", objStart, objEnd) ?: break
            val y = extractIntFast(json, "y", objStart, objEnd) ?: break
            val z = extractIntFast(json, "z", objStart, objEnd) ?: break
            val m = extractStringFast(json, "m", objStart, objEnd) ?: break
            blocks.add(WorldBlock(x, y, z, m))
            i = objEnd + 1
        }
        return blocks
    }

    private fun findMatchingBracket(json: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until json.length) {
            when (json[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun extractIntFast(json: String, key: String, from: Int = 0, to: Int = json.length): Int? {
        val needle = "\"$key\":"
        val idx = json.indexOf(needle, from)
        if (idx < 0 || idx >= to) return null
        var i = idx + needle.length
        while (i < to && json[i].isWhitespace()) i++
        val start = i
        while (i < to && (json[i] == '-' || json[i].isDigit())) i++
        return json.substring(start, i).toIntOrNull()
    }

    private fun extractStringFast(json: String, key: String, from: Int = 0, to: Int = json.length): String? {
        val needle = "\"$key\":\""
        val idx = json.indexOf(needle, from)
        if (idx < 0 || idx >= to) return null
        var i = idx + needle.length
        val sb = StringBuilder()
        while (i < to) {
            val ch = json[i]
            if (ch == '"') return unescapeJson(sb.toString())
            if (ch == '\\' && i + 1 < to) {
                when (json[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(json[i + 1])
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return null
    }

    private fun unescapeJson(value: String): String = value

    companion object {
        private val MAGIC = byteArrayOf(0x45, 0x56, 0x57, 0x44) // EVWD
        private const val BINARY_VERSION = 1
    }
}