package com.clipcut.core.feature

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.opengl.GLES20
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.BeatMarker
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BeatDetector — runs an android.media.audiofx.Visualizer on the currently
 * playing audio track to capture FFT bins, then performs simple peak
 * detection to emit BeatMarker timestamps.
 *
 * detect(uri, totalDurationUs) → List<BeatMarker> at the strongest onset frames
 */
class BeatDetector(private val context: Context) {
    fun detect(uri: Uri, totalDurationUs: Long): List<BeatMarker> {
        if (uri.toString().isEmpty()) return emptyList()
        val player = MediaPlayer()
        return try {
            player.setDataSource(context, uri)
            player.isLooping = false
            player.prepare()
            val out = mutableListOf<BeatMarker>()
            val visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
            }
            visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null) return
                    var totalEnergy = 0L
                    for (i in 0 until fft.size step 2) {
                        val re = fft[i].toInt(); val im = fft[i + 1].toInt()
                        val mag = Math.sqrt((re * re + im * im).toDouble())
                        totalEnergy += mag.toLong()
                    }
                    val energy = totalEnergy / (fft.size / 2)
                    if (energy > ENERGY_THRESHOLD) {
                        val us = (player.currentPosition.toLong()) * 1000L
                        out.add(BeatMarker(timeUs = us, strength = (energy / 4000f).coerceIn(0.1f, 1f)))
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true)
            visualizer.enabled = true
            player.start()
            while (player.isPlaying && player.currentPosition < player.duration) {
                Thread.sleep(50)
            }
            visualizer.enabled = false
            visualizer.release()
            out
        } catch (t: Throwable) {
            AppLog.w(TAG, "BeatDetector failed: ${t.message}")
            emptyList()
        } finally {
            try { player.release() } catch (_: Throwable) {}
        }
    }

    companion object {
        private const val TAG = "BeatDetector"
        private const val ENERGY_THRESHOLD = 800L
    }
}

/**
 * ColorPicker — eyedropper via glReadPixels. Given the current frame's
 * rendered GL surface, pickXY(x, y, viewportWidth, viewportHeight) reads
 * the RGBA at that pixel and returns an ARGB int suitable for use as
 * OverlayItem.textColor.
 */
object ColorPicker {
    fun pickXY(x: Int, y: Int, viewportWidth: Int, viewportHeight: Int): Int {
        val rgba = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(
            x.coerceIn(0, viewportWidth - 1),
            (viewportHeight - 1 - y.coerceIn(0, viewportHeight - 1)),
            1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba
        )
        val r = rgba.get(0).toInt() and 0xFF
        val g = rgba.get(1).toInt() and 0xFF
        val b = rgba.get(2).toInt() and 0xFF
        val a = rgba.get(3).toInt() and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
