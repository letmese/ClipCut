package com.clipcut.core.audio

/**
 * InShotEq — 10-band parametric equalizer definitions.
 * Ported from InShot decompiled sources (EqBand / AudioEqualizerFragment).
 */
object InShotEq {

    const val BAND_COUNT = 10

    /** Center frequencies in Hz — copied from InShot EqBand. */
    val FREQUENCIES_HZ: IntArray = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    const val MAX_GAIN_DB = 12f
    const val MIN_GAIN_DB = -12f

    /** InShot band width = freq * 0.707 -> Q = 1 / 0.707 ≈ 1.414. */
    const val BAND_Q = 1.4142f

    /** Flat 10-band curve default. */
    val DEFAULT_GAINS: FloatArray = FloatArray(BAND_COUNT) { 0f }

    fun bandWidthHz(freq: Float): Float = freq * 0.707f

    fun isValid(gains: FloatArray?): Boolean =
        gains != null && gains.size == BAND_COUNT && gains.all { it in MIN_GAIN_DB..MAX_GAIN_DB }

    fun clamp(gains: FloatArray): FloatArray =
        FloatArray(BAND_COUNT) { i -> gains.getOrElse(i) { 0f }.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) }

    fun copy(gains: FloatArray?): FloatArray =
        if (gains == null) DEFAULT_GAINS.copyOf() else clamp(gains)

    data class Preset(val name: String, val gains: FloatArray)

    val PRESETS: List<Preset> = listOf(
        Preset("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        Preset("Bass Boost", floatArrayOf(7f, 6f, 4f, 2f, 0f, -1f, -1f, 0f, 0f, 0f)),
        Preset("Vocal Boost", floatArrayOf(-2f, -2f, -1f, 1f, 4f, 5f, 3f, 0f, 0f, 0f)),
        Preset("Treble Boost", floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f)),
        Preset("Electronic", floatArrayOf(5f, 4f, 2f, 0f, -1f, 0f, 2f, 4f, 5f, 5f)),
        Preset("Rock", floatArrayOf(5f, 4f, 2f, -1f, -2f, -1f, 2f, 4f, 5f, 5f)),
        Preset("Pop", floatArrayOf(2f, 3f, 4f, 2f, -1f, -1f, 1f, 3f, 4f, 3f)),
        Preset("Classical", floatArrayOf(5f, 4f, 2f, 0f, -2f, -2f, 0f, 2f, 4f, 5f))
    )
}
