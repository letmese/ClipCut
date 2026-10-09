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

data class DetectedFaceTarget(
    val normCenterX: Float,
    val normCenterY: Float,
    val normWidth: Float,
    val normHeight: Float
)

/**
 * MotionTracker — automates tracking of faces, heads, license plates, or moving objects
 * across timeline duration without frame-by-frame manual labor.
 *
 * Features:
 *  - Target-Lock: Locks onto the specific face or object chosen by the user at the playhead.
 *  - Multi-Face Disambiguation: Uses trackingId and spatial proximity gating so it never jumps
 *    between multiple people in the same shot.
 *  - Proportional Free-Form Scaling: Keeps the user's custom shape, width, and height aspect ratio.
 *  - Multi-Face Detection: Scans the frame and extracts all faces at once for 1-tap bulk tracking.
 *  - Frame-Accurate Decoding: Uses OPTION_CLOSEST instead of keyframe-only jumping.
 */
class MotionTracker(private val context: Context) {

    /**
     * Scans the video frame at [ptsUs] and returns bounding boxes of all detected faces.
     */
    suspend fun detectAllFacesAt(videoUri: String, ptsUs: Long): List<DetectedFaceTarget> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            if (videoUri.startsWith("content://") || videoUri.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoUri))
            } else {
                retriever.setDataSource(videoUri)
            }
            val bmp = retriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return@withContext emptyList()

            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()

            val opts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build()
            val detector = FaceDetection.getClient(opts)
            try {
                val img = InputImage.fromBitmap(bmp, 0)
                val faces = detector.process(img).awaitResult()
                faces.map { f ->
                    val cx = (f.boundingBox.centerX().toFloat() / bmpW).coerceIn(0.04f, 0.96f)
                    val cy = (f.boundingBox.centerY().toFloat() / bmpH).coerceIn(0.04f, 0.96f)
                    val w = ((f.boundingBox.width().toFloat() / bmpW) * 1.35f).coerceIn(0.08f, 0.8f)
                    val h = ((f.boundingBox.height().toFloat() / bmpH) * 1.40f).coerceIn(0.08f, 0.8f)
                    DetectedFaceTarget(cx, cy, w, h)
                }
            } finally {
                bmp.recycle()
                detector.close()
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "detectAllFacesAt failed: ${t.message}", t)
            emptyList()
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    /**
     * Tracks the specific target selected and sized by the user from [anchorPtsUs] across [startTimeUs..endTimeUs].
     */
    suspend fun track(
        videoUri: String,
        initialX: Float,
        initialY: Float,
        initialW: Float,
        initialH: Float,
        initialRotation: Float = 0f,
        startTimeUs: Long,
        endTimeUs: Long,
        anchorPtsUs: Long = startTimeUs,
        mode: TrackingMode = TrackingMode.FACE,
        onProgress: (Float) -> Unit = {}
    ): List<BlurKeyframe> = withContext(Dispatchers.Default) {
        val totalSpanUs = (endTimeUs - startTimeUs).coerceAtLeast(100_000L)
        val stepUs = 160_000L // ~6 samples/sec delivers ultra-fluid tracking
        val steps = (totalSpanUs / stepUs).toInt().coerceIn(1, 350)

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

        // 1. ANCHOR LOCK: Sample the frame at anchorPtsUs to identify the locked face/object & user-defined relative proportions
        var lockedTrackingId: Int? = null
        var userScaleX = 1.0f
        var userScaleY = 1.0f
        var userOffsetX = 0.0f
        var userOffsetY = 0.0f

        val anchorBmp = try {
            retriever.getFrameAtTime(anchorPtsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(anchorPtsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) { null }

        if (anchorBmp != null) {
            val abW = anchorBmp.width.toFloat()
            val abH = anchorBmp.height.toFloat()

            if (mode == TrackingMode.FACE && faceDetector != null) {
                try {
                    val img = InputImage.fromBitmap(anchorBmp, 0)
                    val faces: List<com.google.mlkit.vision.face.Face> = faceDetector.process(img).awaitResult()
                    // Match the face closest to the user's selected center & within user's box
                    val bestAnchorFace = faces.minByOrNull { f ->
                        val fx = f.boundingBox.centerX() / abW
                        val fy = f.boundingBox.centerY() / abH
                        val dx = fx - initialX
                        val dy = fy - initialY
                        dx * dx + dy * dy
                    }

                    if (bestAnchorFace != null) {
                        lockedTrackingId = bestAnchorFace.trackingId
                        val rawFaceW = (bestAnchorFace.boundingBox.width() / abW).coerceAtLeast(0.02f)
                        val rawFaceH = (bestAnchorFace.boundingBox.height() / abH).coerceAtLeast(0.02f)
                        val rawFaceX = bestAnchorFace.boundingBox.centerX() / abW
                        val rawFaceY = bestAnchorFace.boundingBox.centerY() / abH

                        // Preserve user's custom free-form aspect ratio & padding
                        userScaleX = (initialW / rawFaceW).coerceIn(0.5f, 3.5f)
                        userScaleY = (initialH / rawFaceH).coerceIn(0.5f, 3.5f)
                        userOffsetX = initialX - rawFaceX
                        userOffsetY = initialY - rawFaceY
                        AppLog.d(TAG, "Locked to face id=$lockedTrackingId, scaleX=$userScaleX, scaleY=$userScaleY")
                    }
                } catch (t: Throwable) {
                    AppLog.w(TAG, "Anchor frame face analysis failed: ${t.message}")
                }
            }
            anchorBmp.recycle()
        }

        val keyframes = mutableListOf<BlurKeyframe>()
        var curX = initialX
        var curY = initialY
        var curW = initialW
        var curH = initialH

        try {
            for (stepIndex in 0..steps) {
                val currentPtsUs = (startTimeUs + stepIndex * stepUs).coerceAtMost(endTimeUs)
                val progress = (stepIndex.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                onProgress(progress)

                val rawBmp = try {
                    retriever.getFrameAtTime(currentPtsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: retriever.getFrameAtTime(currentPtsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Throwable) { null }

                if (rawBmp != null) {
                    val bmpW = rawBmp.width.toFloat()
                    val bmpH = rawBmp.height.toFloat()

                    if (mode == TrackingMode.FACE && faceDetector != null) {
                        try {
                            val img = InputImage.fromBitmap(rawBmp, 0)
                            val faces: List<com.google.mlkit.vision.face.Face> = faceDetector.process(img).awaitResult()
                            if (faces.isNotEmpty()) {
                                // Match: Priority 1: same trackingId as locked face; Priority 2: closest face within proximity gate
                                val matchedFace = if (lockedTrackingId != null) {
                                    faces.firstOrNull { it.trackingId == lockedTrackingId }
                                } else null

                                val targetFace = matchedFace ?: faces.filter { f ->
                                    val fx = f.boundingBox.centerX() / bmpW
                                    val fy = f.boundingBox.centerY() / bmpH
                                    val dist = sqrt((fx - curX) * (fx - curX) + (fy - curY) * (fy - curY))
                                    dist < 0.28f // Proximity gate: never jump to another person across the frame!
                                }.minByOrNull { f ->
                                    val fx = f.boundingBox.centerX() / bmpW
                                    val fy = f.boundingBox.centerY() / bmpH
                                    val dx = fx - curX
                                    val dy = fy - curY
                                    dx * dx + dy * dy
                                }

                                if (targetFace != null) {
                                    if (lockedTrackingId == null && targetFace.trackingId != null) {
                                        lockedTrackingId = targetFace.trackingId
                                    }
                                    val fcX = targetFace.boundingBox.centerX() / bmpW
                                    val fcY = targetFace.boundingBox.centerY() / bmpH
                                    val fcW = targetFace.boundingBox.width() / bmpW
                                    val fcH = targetFace.boundingBox.height() / bmpH

                                    val computedTargetX = (fcX + userOffsetX).coerceIn(0.02f, 0.98f)
                                    val computedTargetY = (fcY + userOffsetY).coerceIn(0.02f, 0.98f)
                                    val computedTargetW = (fcW * userScaleX).coerceIn(0.04f, 1.0f)
                                    val computedTargetH = (fcH * userScaleY).coerceIn(0.04f, 1.0f)

                                    // High-responsiveness smoothing filter (0.75 target / 0.25 momentum)
                                    curX = curX * 0.25f + computedTargetX * 0.75f
                                    curY = curY * 0.25f + computedTargetY * 0.75f
                                    curW = curW * 0.25f + computedTargetW * 0.75f
                                    curH = curH * 0.25f + computedTargetH * 0.75f
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
                                val bestObj = objects.filter { obj ->
                                    val ox = obj.boundingBox.centerX() / bmpW
                                    val oy = obj.boundingBox.centerY() / bmpH
                                    val dist = sqrt((ox - curX) * (ox - curX) + (oy - curY) * (oy - curY))
                                    dist < 0.30f
                                }.minByOrNull { obj ->
                                    val ox = obj.boundingBox.centerX() / bmpW
                                    val oy = obj.boundingBox.centerY() / bmpH
                                    val dx = ox - curX
                                    val dy = oy - curY
                                    dx * dx + dy * dy
                                }
                                if (bestObj != null) {
                                    val targetX = (bestObj.boundingBox.centerX() / bmpW).coerceIn(0.04f, 0.96f)
                                    val targetY = (bestObj.boundingBox.centerY() / bmpH).coerceIn(0.04f, 0.96f)
                                    curX = curX * 0.30f + targetX * 0.70f
                                    curY = curY * 0.30f + targetY * 0.70f
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
                        rotation = initialRotation
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
