package com.evidex.math

@JvmInline
value class Angle(val degrees: Float) {

    val radians: Float get() = degrees * DEG_TO_RAD
    val wrapped: Angle get() = Angle(wrapDegrees(degrees))
    val normalized: Angle get() = Angle(normalizeDegrees(degrees))

    operator fun plus(other: Angle) = Angle(degrees + other.degrees)
    operator fun minus(other: Angle) = Angle(degrees - other.degrees)
    operator fun unaryMinus() = Angle(-degrees)
    operator fun times(factor: Float) = Angle(degrees * factor)
    operator fun div(factor: Float) = Angle(degrees / factor)

    fun lerp(target: Angle, alpha: Float): Angle {
        val diff = wrapDegrees(target.degrees - degrees)
        return Angle(degrees + diff * alpha.coerceIn(0f, 1f))
    }

    fun differenceTo(other: Angle): Float = wrapDegrees(other.degrees - degrees)

    companion object {
        const val DEG_TO_RAD: Float = (Math.PI / 180.0).toFloat()
        const val RAD_TO_DEG: Float = (180.0 / Math.PI).toFloat()

        fun fromRadians(rad: Float) = Angle(rad * RAD_TO_DEG)

        fun wrapDegrees(value: Float): Float {
            var v = value % 360f
            if (v >= 180f) v -= 360f
            if (v < -180f) v += 360f
            return v
        }

        fun normalizeDegrees(value: Float): Float {
            var v = value % 360f
            if (v < 0f) v += 360f
            return v
        }

        fun wrapDegreesTo(value: Float, target: Float): Float {
            var v = value
            while (target - v < -180f) v -= 360f
            while (target - v >= 180f) v += 360f
            return v
        }
    }
}
