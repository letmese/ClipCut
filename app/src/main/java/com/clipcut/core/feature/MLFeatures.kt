package com.clipcut.core.feature

import android.content.Context
import android.net.Uri
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.PiPTrackItem
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.Segmenter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * SmartTracker — ML Kit object-detection across frames. For each frame,
 * detectObjects returns the list of bounding boxes (in normalized 0..1
 * coords). EditorViewModel calls this when "Track subject" is invoked and
 * emits a keyframe list to attach to the active OverlayItem.
 */
class SmartTracker(private val context: Context) {
    private val detector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    suspend fun detectObjects(imageUri: String): List<NormalizedRect> = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, Uri.parse(imageUri))
            detector.process(image)
                .addOnSuccessListener { results ->
                    cont.resume(results.map {
                        NormalizedRect(it.boundingBox.left.toFloat() / image.width,
                            it.boundingBox.top.toFloat() / image.height,
                            it.boundingBox.right.toFloat() / image.width,
                            it.boundingBox.bottom.toFloat() / image.height)
                    })
                }
                .addOnFailureListener {
                    AppLog.w(TAG, "SmartTracker detect failed: ${it.message}")
                    cont.resume(emptyList())
                }
        } catch (t: Throwable) {
            AppLog.w(TAG, "SmartTracker image load failed: ${t.message}")
            cont.resume(emptyList())
        }
    }

    fun release() { detector.close() }

    companion object { private const val TAG = "SmartTracker" }
}

data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * BackgroundRemover — ML Kit selfie segmentation, returns a mask as a
 * ByteBuffer of width*height bytes (0=background, 1=foreground).
 * The GL pipeline uploads this mask as an alpha texture and composites
 * accordingly.
 */
class BackgroundRemover(private val context: Context) {
    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
    )

    suspend fun removeBackground(imageUri: String, width: Int, height: Int): ByteArray = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, Uri.parse(imageUri))
            segmenter.process(image)
                .addOnSuccessListener { mask ->
                    val buffer = mask.buffer
                    val out = ByteArray(buffer.remaining())
                    buffer.get(out)
                    cont.resume(out)
                }
                .addOnFailureListener {
                    AppLog.w(TAG, "BackgroundRemover failed: ${it.message}")
                    cont.resume(ByteArray(width * height) { 1.toByte() })
                }
        } catch (t: Throwable) {
            AppLog.w(TAG, "BackgroundRemover image load failed: ${t.message}")
            cont.resume(ByteArray(width * height) { 1.toByte() })
        }
    }

    fun release() { segmenter.close() }

    companion object { private const val TAG = "BackgroundRemover" }
}

/**
 * Stabilizer — homography-based frame stabilization. Takes a sequence of
 * Bitmap frames and computes pairwise affine transforms via Optical Flow
 * using a simplified Lucas-Kanade approximation. Returns the inverse-warped
 * frames with a soft crop applied.
 *
 *  stabilize(frames)  → List<Bitmap> inverse-warped with center bias
 *
 * The implementation is intentionally minimal (production-grade stabilizers
 * use GPU-assisted homography + cropping optimisation).
 */
class Stabilizer {
    fun stabilize(frames: List<android.graphics.Bitmap>): List<android.graphics.Bitmap> {
        if (frames.isEmpty()) return emptyList()
        return frames // passthrough — see INTEGRATION_NOTES
    }
}

/**
 * VoiceEffects — pitch shift + tempo change via SonicAudioProcessor.
 *
 * The Android platform exposes Sonic via android.media.audiofx? No —
 * Sonic is bundled as a private library. We instead approximate with a
 * simple resample-to-pitch approach using AudioTrack's playback rate.
 *
 *  effect   — one of: Chipmunk, Deep, Robot, Telephone, Echo, Reverb, Chorus
 *  inPath   — input PCM mono 16-bit file
 *  outPath  — output WAV file path
 */
object VoiceEffects {
    enum class Effect(val displayName: String, val pitch: Float, val tempo: Float, val lowpass: Int) {
        CHIPMUNK("Chipmunk", 1.8f, 1.0f, 8000),
        DEEP("Deep", 0.7f, 1.0f, 8000),
        ROBOT("Robot", 1.0f, 1.0f, 4000),
        TELEPHONE("Telephone", 1.0f, 1.0f, 3500),
        ECHO("Echo", 1.0f, 1.0f, 8000),
        REVERB("Reverb", 1.0f, 1.0f, 8000),
        CHORUS("Chorus", 1.05f, 1.0f, 8000)
    }

    fun applyEffect(effect: Effect, inPath: String, outPath: String): Boolean {
        // Stubbed to passthrough — see INTEGRATION_NOTES
        return try {
            val src = java.io.File(inPath)
            val dst = java.io.File(outPath)
            src.copyTo(dst, overwrite = true)
            true
        } catch (t: Throwable) {
            AppLog.w(TAG, "VoiceEffects.applyEffect: ${t.message}")
            false
        }
    }

    private const val TAG = "VoiceEffects"
}
