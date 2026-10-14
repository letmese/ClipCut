package com.clipcut.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AudioDsp — pure-Kotlin 16-bit PCM DSP core.
 * Ported from PowerDirector audio features (AudioRemix / VoiceChanger / AudioDucking)
 * and InShot 10-band equalizer.
 */
object AudioDsp {

    /** Biquad RBJ biquad filter. */
    class Biquad {
        private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
        private var a0 = 1.0; private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        fun configure(kind: Kind, freq: Double, sampleRate: Double, q: Double = 0.7071, gainDb: Double = 0.0) {
            val w0 = 2.0 * PI * freq / sampleRate
            val cosw = cos(w0)
            val sinw = sin(w0)
            val alpha = sinw / (2.0 * q)
            val a = 10.0.pow(gainDb / 40.0)
            when (kind) {
                Kind.LOW_SHELF -> {
                    val sq = 2.0 * sqrt(a) * alpha
                    b0 = a * ((a + 1.0) - (a - 1.0) * cosw + sq)
                    b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosw)
                    b2 = a * ((a + 1.0) - (a - 1.0) * cosw - sq)
                    a0 = (a + 1.0) + (a - 1.0) * cosw + sq
                    a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosw)
                    a2 = (a + 1.0) - (a - 1.0) * cosw - sq
                }
                Kind.HIGH_SHELF -> {
                    val sq = 2.0 * sqrt(a) * alpha
                    b0 = a * ((a + 1.0) + (a - 1.0) * cosw + sq)
                    b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosw)
                    b2 = a * ((a + 1.0) - (a - 1.0) * cosw - sq)
                    a0 = (a + 1.0) - (a - 1.0) * cosw + sq
                    a1 = 2.0 * ((a - 1.0) + (a + 1.0) * cosw)
                    a2 = (a + 1.0) - (a - 1.0) * cosw - sq
                }
                Kind.PEAKING -> {
                    b0 = 1.0 + alpha * a
                    b1 = -2.0 * cosw
                    b2 = 1.0 - alpha * a
                    a0 = 1.0 + alpha / a
                    a1 = -2.0 * cosw
                    a2 = 1.0 - alpha / a
                }
                Kind.LOW_PASS -> {
                    b0 = (1.0 - cosw) / 2.0
                    b1 = 1.0 - cosw
                    b2 = b0
                    a0 = 1.0 + alpha
                    a1 = -2.0 * cosw
                    a2 = 1.0 - alpha
                }
                Kind.HIGH_PASS -> {
                    b0 = (1.0 + cosw) / 2.0
                    b1 = -(1.0 + cosw)
                    b2 = b0
                    a0 = 1.0 + alpha
                    a1 = -2.0 * cosw
                    a2 = 1.0 - alpha
                }
            }
            b0 /= a0; b1 /= a0; b2 /= a0
            a1 /= a0; a2 /= a0
            x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
        }

        fun process(input: Double): Double {
            val out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = input
            y2 = y1; y1 = out
            return out
        }

        enum class Kind { LOW_SHELF, HIGH_SHELF, PEAKING, LOW_PASS, HIGH_PASS }
    }

    /** 10-band equalizer chain (InShot). */
    fun equalize10(samples: ShortArray, gainsDb: FloatArray, sampleRate: Int = 44100) {
        if (gainsDb.size != InShotEq.BAND_COUNT) return
        val biquads = Array(InShotEq.BAND_COUNT) { i ->
            val freq = InShotEq.FREQUENCIES_HZ[i].toDouble()
            val kind = when (i) {
                0 -> Biquad.Kind.LOW_SHELF
                InShotEq.BAND_COUNT - 1 -> Biquad.Kind.HIGH_SHELF
                else -> Biquad.Kind.PEAKING
            }
            Biquad().apply {
                configure(kind, freq, sampleRate.toDouble(), InShotEq.BAND_Q.toDouble(), gainsDb[i].toDouble())
            }
        }
        for (n in samples.indices) {
            var v = samples[n].toDouble()
            for (bq in biquads) {
                v = bq.process(v)
            }
            samples[n] = v.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).roundToInt().toShort()
        }
    }

    /** PowerDirector Voice Changer Presets. */
    enum class VoicePreset(val displayName: String, val pitchShiftSemitones: Float, val robotRateHz: Float) {
        NATURAL("Natural", 0f, 0f),
        CHIPMUNK("Chipmunk", 8f, 0f),
        DEEP_BASS("Deep Bass", -7f, 0f),
        ROBOT("Cyber Robot", 0f, 40f),
        RADIO("Vintage Radio", 2f, 0f),
        ALIEN("Alien Echo", 5f, 25f)
    }

    /** Apply voice modulation in-place on 16-bit PCM. */
    fun applyVoicePreset(samples: ShortArray, preset: VoicePreset, sampleRate: Int = 44100) {
        if (preset == VoicePreset.NATURAL) return

        if (preset.robotRateHz > 0f) {
            val modFreq = preset.robotRateHz.toDouble()
            for (i in samples.indices) {
                val t = i.toDouble() / sampleRate
                val mod = sin(2.0 * PI * modFreq * t)
                val s = (samples[i] * (0.5 + 0.5 * mod)).roundToInt()
                samples[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
}
