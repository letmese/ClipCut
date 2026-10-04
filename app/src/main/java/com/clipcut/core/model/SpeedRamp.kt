package com.clipcut.core.model

import java.util.UUID

data class SpeedKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetUs: Long,
    val speed: Float
)

data class SpeedRamp(
    val keyframes: List<SpeedKeyframe> = emptyList()
) {
    val isActive: Boolean get() = keyframes.size >= 2

    fun speedAt(timeOffsetUs: Long): Float {
        if (!isActive) return 1.0f
        val sorted = keyframes.sortedBy { it.timeOffsetUs }
        if (timeOffsetUs <= sorted.first().timeOffsetUs) return sorted.first().speed
        if (timeOffsetUs >= sorted.last().timeOffsetUs) return sorted.last().speed
        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeOffsetUs in k1.timeOffsetUs..k2.timeOffsetUs) {
                val span = (k2.timeOffsetUs - k1.timeOffsetUs).toFloat().coerceAtLeast(1f)
                val t = (timeOffsetUs - k1.timeOffsetUs) / span
                return k1.speed + t * (k2.speed - k1.speed)
            }
        }
        return sorted.last().speed
    }
}
