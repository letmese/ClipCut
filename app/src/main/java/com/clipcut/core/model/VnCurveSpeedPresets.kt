package com.clipcut.core.model

/**
 * VN Curve-Speed presets — ported from VN (com.frontrow.*) decompiled source:
 *   code_dump/vn/java/sources/com/frontrow/videoeditor/widget/SpeedMenuLayout.java
 *
 * Each preset is a list of normalized points (x in 0..1 = position along clip,
 * y = speed offset relative to 1.0x, matching VN's PointF speedPoints where
 * speed = 1 + y).
 */
object VnCurveSpeedPresets {

    data class CurvePoint(val x: Float, val y: Float)

    /** name -> normalized curve (copied verbatim from VN SpeedMenuLayout). */
    val presets: List<Pair<String, List<CurvePoint>>> = listOf(
        "Montage" to listOf(
            CurvePoint(0.00f, 0.00f), CurvePoint(0.50f, 0.40f),
            CurvePoint(0.65f, -0.25f), CurvePoint(0.80f, 0.00f), CurvePoint(1.00f, 0.00f)
        ),
        "Hero Time" to listOf(
            CurvePoint(0.00f, 0.00f), CurvePoint(0.36f, 0.32f),
            CurvePoint(0.50f, -0.25f), CurvePoint(0.64f, 0.32f), CurvePoint(1.00f, 0.00f)
        ),
        "Bullet Time" to listOf(
            CurvePoint(0.00f, 0.30f), CurvePoint(0.40f, 0.30f),
            CurvePoint(0.50f, -0.25f), CurvePoint(0.60f, 0.30f), CurvePoint(1.00f, 0.30f)
        ),
        "Jump Time" to listOf(
            CurvePoint(0.00f, -0.20f), CurvePoint(0.40f, -0.20f),
            CurvePoint(0.50f, 0.30f), CurvePoint(0.60f, -0.20f), CurvePoint(1.00f, -0.20f)
        ),
        "Fast In" to listOf(
            CurvePoint(0.00f, 0.30f), CurvePoint(0.40f, 0.30f),
            CurvePoint(0.60f, 0.00f), CurvePoint(1.00f, 0.00f)
        ),
        "Fast Out" to listOf(
            CurvePoint(0.00f, 0.00f), CurvePoint(0.40f, 0.00f),
            CurvePoint(0.60f, 0.30f), CurvePoint(1.00f, 0.30f)
        )
    )

    /** Convert a preset to a SpeedRamp scaled to the clip duration. */
    fun buildRamp(name: String, clipDurationUs: Long): SpeedRamp? {
        val points = presets.firstOrNull { it.first == name }?.second ?: return null
        val duration = clipDurationUs.coerceAtLeast(1_000_000L)
        val keyframes = points.map { p ->
            SpeedKeyframe(
                timeOffsetUs = (p.x * duration).toLong().coerceIn(0L, duration),
                speed = (1f + p.y).coerceAtLeast(0.1f)
            )
        }
        val sorted = keyframes.sortedBy { it.timeOffsetUs }
        return SpeedRamp(keyframes = sorted)
    }
}
