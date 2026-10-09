package com.clipcut.core.feature

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.BlurKeyframe
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.sqrt

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result, null) }
        addOnFailureListener { ex -> cont.resumeWithException(ex) }
        addOnCanceledListener { cont.cancel() }
    }

enum class TrackingMode(val label: String) {
    FACE("Auto Track Face (AI)"),
    OBJECT("Track Moving Object")
}

/**
 * MotionTracker — automates tracking of faces, heads, license plates, or moving objects
 * across timeline duration without frame-by-frame manual labor.
 * Emits smooth interpolated BlurKeyframes.
 */
class MotionTracker(private val context: Context) {

    suspend fun track(
        videoUri: String,
        initialX: Float,
        initialY: Float,
        initialW: Float,
        initialH: Float,
        initialRotation: Float = 0f,
        startTimeUs: Long,
        endTimeUs: Long,
        mode: TrackingMode = TrackingMode.FACE,
        onProgress: (Float) -> Unit = {}
    ): List<BlurKeyframe> = withContext(Dispatchers.Default) {
        val keyframes = mutableListOf<BlurKeyframe>()
        val totalSpanUs = (endTimeUs - startTimeUs).coerceAtLeast(100_000L)
        val stepUs = 200_000L // 5 samples per second provides responsive, smooth tracking
        val steps = (totalSpanUs / stepUs).toInt().coerceIn(1, 250)

        val retriever = MediaMetadataRetriever()
        try {
            if (videoUri.startsWith("content://") || videoUri.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoUri))
            } else {
                retriever.setDataSource(videoUri)
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "Failed to initialize MediaMetadataRetriever for tracking", t)
            return@withContext emptyList()
        }

        var curX = initialX
        var curY = initialY
        var curW = initialW
        var curH = initialH
        val curRot = initialRotation

        val faceDetector = if (mode == TrackingMode.FACE) {
            val opts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build()
            FaceDetection.getClient(opts)
        } else null

        val objectDetector = if (mode == TrackingMode.OBJECT) {
            val opts = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build()
            ObjectDetection.getClient(opts)
        } else null

        try {
            for (stepIndex in 0..steps) {
                val currentPtsUs = (startTimeUs + stepIndex * stepUs).coerceAtMost(endTimeUs)
                val progress = (stepIndex.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                onProgress(progress)

                val rawBmp = try {
                    retriever.getFrameAtTime(currentPtsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(currentPtsUs)
                } catch (_: Throwable) { null }

                if (rawBmp != null) {
                    val bmpW = rawBmp.width
                    val bmpH = rawBmp.height

                    if (mode == TrackingMode.FACE && faceDetector != null) {
                        try {
                            val img = InputImage.fromBitmap(rawBmp, 0)
                            val faces: List<com.google.mlkit.vision.face.Face> = faceDetector.process(img).awaitResult()
                            if (faces.isNotEmpty()) {
                                // Match the face closest to the previous track center
                                val bestFace = faces.minByOrNull { f ->
                                    val fx = f.boundingBox.centerX().toFloat() / bmpW.toFloat()
                                    val fy = f.boundingBox.centerY().toFloat() / bmpH.toFloat()
                                    val dx = fx - curX
                                    val dy = fy - curY
                                    dx * dx + dy * dy
                                }

                                if (bestFace != null) {
                                    val targetX = (bestFace.boundingBox.centerX().toFloat() / bmpW.toFloat()).coerceIn(0.04f, 0.96f)
                                    val targetY = (bestFace.boundingBox.centerY().toFloat() / bmpH.toFloat()).coerceIn(0.04f, 0.96f)
                                    val targetW = ((bestFace.boundingBox.width().toFloat() / bmpW.toFloat()) * 1.35f).coerceIn(0.06f, 0.9f)
                                    val targetH = ((bestFace.boundingBox.height().toFloat() / bmpH.toFloat()) * 1.35f).coerceIn(0.06f, 0.9f)

                                    // Exponential smoothing filter to prevent jitter
                                    curX = curX * 0.35f + targetX * 0.65f
                                    curY = curY * 0.35f + targetY * 0.65f
                                    curW = curW * 0.35f + targetW * 0.65f
                                    curH = curH * 0.35f + targetH * 0.65f
                                }
                            }
                        } catch (t: Throwable) {
                            AppLog.w(TAG, "Face detection error on frame: ${t.message}")
                        }
                    } else if (mode == TrackingMode.OBJECT && objectDetector != null) {
                        try {
                            val img = InputImage.fromBitmap(rawBmp, 0)
                            val objects: List<com.google.mlkit.vision.objects.DetectedObject> = objectDetector.process(img).awaitResult()
                            if (objects.isNotEmpty()) {
                                val bestObj = objects.minByOrNull { obj ->
                                    val ox = obj.boundingBox.centerX().toFloat() / bmpW.toFloat()
                                    val oy = obj.boundingBox.centerY().toFloat() / bmpH.toFloat()
                                    val dx = ox - curX
                                    val dy = oy - curY
                                    dx * dx + dy * dy
                                }
                                if (bestObj != null) {
                                    val targetX = (bestObj.boundingBox.centerX().toFloat() / bmpW.toFloat()).coerceIn(0.04f, 0.96f)
                                    val targetY = (bestObj.boundingBox.centerY().toFloat() / bmpH.toFloat()).coerceIn(0.04f, 0.96f)
                                    curX = curX * 0.4f + targetX * 0.6f
                                    curY = curY * 0.4f + targetY * 0.6f
                                }
                            }
                        } catch (t: Throwable) {
                            AppLog.w(TAG, "Object detection error on frame: ${t.message}")
                        }
                    }
                    rawBmp.recycle()
                }

                keyframes.add(
                    BlurKeyframe(
                        timeOffsetUs = currentPtsUs - startTimeUs,
                        normalizedX = curX,
                        normalizedY = curY,
                        width = curW,
                        height = curH,
                        rotation = curRot
                    )
                )
            }
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
            try { faceDetector?.close() } catch (_: Throwable) {}
            try { objectDetector?.close() } catch (_: Throwable) {}
        }

        keyframes
    }

    companion object {
        private const val TAG = "MotionTracker"
    }
}
