package com.evidex.math

data class Vec3d(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {

    operator fun plus(other: Vec3d) = Vec3d(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3d) = Vec3d(x - other.x, y - other.y, z - other.z)
    operator fun times(factor: Double) = Vec3d(x * factor, y * factor, z * factor)
    operator fun div(factor: Double) = Vec3d(x / factor, y / factor, z / factor)

    fun dot(other: Vec3d) = x * other.x + y * other.y + z * other.z
    fun length() = kotlin.math.sqrt(x * x + y * y + z * z)
    fun lengthSq() = x * x + y * y + z * z
    fun distanceTo(other: Vec3d) = (this - other).length()
    fun distanceSqTo(other: Vec3d) = (this - other).lengthSq()

    fun lerp(target: Vec3d, alpha: Double): Vec3d {
        val a = alpha.coerceIn(0.0, 1.0)
        return Vec3d(
            x + (target.x - x) * a,
            y + (target.y - y) * a,
            z + (target.z - z) * a
        )
    }

    companion object {
        val ZERO = Vec3d()
    }
}
