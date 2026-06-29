package com.evidex.playback

internal object ReplayBlockKeys {
    fun pack(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or
            ((z.toLong() and 0x3FFFFFF) shl 12) or
            (y.toLong() and 0xFFF)

    fun unpackX(key: Long): Int = (key shr 38).toInt()
    fun unpackY(key: Long): Int = (key and 0xFFF).toInt()
    fun unpackZ(key: Long): Int = ((key shr 12) and 0x3FFFFFF).toInt()
}