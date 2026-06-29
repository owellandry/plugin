package com.evidex.math

/** Vector 3D para posiciones guardadas en fotogramas. */
data class Vec3d(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
    companion object {
        val ZERO = Vec3d()
    }
}