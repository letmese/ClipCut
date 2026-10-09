package com.clipcut.core.feature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.BlurKeyframe
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resumeWithException
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

private data class FrameTransform(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

/**
 * MotionTracker — automates tracking of faces, heads, license plates, or moving objects
 * across timeline duration without frame-by-frame manual labor.
 *
 * Features:
 *  - Full Rotation Awareness: Reads METADATA_KEY_VIDEO_ROTATION and rotates extracted frames
 *    so ML Kit always sees upright faces (9:16 portrait camera videos, 16:9, etc.).
 *  - 1:1 Preview Coordinate Space: Normalizes bounding boxes strictly against upright display dimensions.
 *  - Target-Lock: Locks onto the specific face or object chosen by the user at the playhead.
 *  - Proportional Free-Form Scaling: Keeps the user's custom shape, width, and height aspect ratio.
 *  - Bidirectional Tracking: Tracks forward and backward from anchor playhead so keyframes cover full span.
 *  - High-Speed Downscaled Extraction: Decodes at optimal 640px resolution for ultra-fast ~15ms/frame inference.
 *  - Momentum / Occlusion Fallback: Retains velocity when face turns away or is occluded so blur never freezes.
 *  - Multi-Face Bulk Detection: Scans frame for all faces at once for 1-tap bulk tracking.
 */
class MotionTracker(private val context: Context) {

    private fun extractUprightFrame(
        retriever: MediaMetadataRetriever,
        ptsUs: Long,
        rotationDegrees: Int,
        maxDim: Int = 640
    ): Bitmap? {
        val rawBmp = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST, maxDim, maxDim)
                    ?: retriever.getScaledFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxDim, maxDim)
                    ?: retriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Throwable) {
            try {
                retriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Throwable) { null }
        } ?: return null

        val srcW = rawBmp.width
        val srcH = rawBmp.height

        val scale = if (maxOf(srcW, srcH) > maxDim) {
            maxDim.toFloat() / maxOf(srcW, srcH).toFloat()
        } else 1.0f

        val matrix = Matrix()
        if (scale < 1.0f) {
            matrix.postScale(scale, scale)
        }
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }

        return if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(rawBmp, 0, 0, srcW, srcH, matrix, true)
            if (transformed != rawBmp) {
                rawBmp.recycle()
            }
            transformed
        } else {
            rawBmp
        }
    }

    /**
     * Scans the video frame at [ptsUs] and returns bounding boxes of all detected faces
     * in normalized upright display coordinates (0..1).
     */
    suspend fun detectAllFacesAt(videoUri: String, ptsUs: Long): List<DetectedFaceTarget> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            if (videoUri.startsWith("content://") || videoUri.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoUri))
            } else {
                retriever.setDataSource(videoUri)
            }
            val rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val uprightBmp = extractUprightFrame(retriever, ptsUs, rotationDegrees, maxDim = 720)
                ?: return@withContext emptyList()

            val w = uprightBmp.width.toFloat()
            val h = uprightBmp.height.toFloat()

            val opts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.05f)
                .enableTracking()
                .build()
            val detector = FaceDetection.getClient(opts)
            try {
                val img = InputImage.fromBitmap(uprightBmp, 0)
                val faces = detector.process(img).awaitResult()
                AppLog.d(TAG, "detectAllFacesAt: found ${faces.size} faces at ptsUs=$ptsUs (rotation=$rotationDegrees, dims=${w.toInt()}x${h.toInt()})")
                faces.map { f ->
                    val cx = (f.boundingBox.centerX().toFloat() / w).coerceIn(0.02f, 0.98f)
                    val cy = (f.boundingBox.centerY().toFloat() / h).coerceIn(0.02f, 0.98f)
                    val fw = ((f.boundingBox.width().toFloat() / w) * 1.30f).coerceIn(0.06f, 0.85f)
                    val fh = ((f.boundingBox.height().toFloat() / h) * 1.35f).coerceIn(0.06f, 0.85f)
                    DetectedFaceTarget(cx, cy, fw, fh)
                }
            } finally {
                uprightBmp.recycle()
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
        val stepUs = 125_000L // ~8 samples/sec delivers super-fluid tracking
        val safeAnchorUs = anchorPtsUs.coerceIn(startTimeUs, endTimeUs)

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

        val rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

        val faceDetector = if (mode == TrackingMode.FACE) {
            val opts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.05f)
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

        // 1. ANCHOR LOCK: Sample the upright frame at anchorPtsUs to identify the locked target & user proportions
        var lockedTrackingId: Int? = null
        var userScaleX = 1.0f
        var userScaleY = 1.0f
        var userOffsetX = 0.0f
        var userOffsetY = 0.0f

        val anchorBmp = extractUprightFrame(retriever, safeAnchorUs, rotationDegrees, maxDim = 640)
        if (anchorBmp != null) {
            val abW = anchorBmp.width.toFloat()
            val abH = anchorBmp.height.toFloat()

            if (mode == TrackingMode.FACE && faceDetector != null) {
                try {
                    val img = InputImage.fromBitmap(anchorBmp, 0)
                    val faces: List<Face> = faceDetector.process(img).awaitResult()
                    val bestAnchorFace = faces.minByOrNull { f ->
                        val fx = f.boundingBox.centerX() / abW
                        val fy = f.boundingBox.centerY() / abH
                        val dx = fx - initialX
                        val dy = fy - initialY
                        dx * dx + dy * dy
                    }

                    if (bestAnchorFace != null) {
                        lockedTrackingId = bestAnchorFace.trackingId
                        val rawFaceW = (bestAnchorFace.boundingBox.width() / abW).coerceAtLeast(0.03f)
                        val rawFaceH = (bestAnchorFace.boundingBox.height() / abH).coerceAtLeast(0.03f)
                        val rawFaceX = bestAnchorFace.boundingBox.centerX() / abW
                        val rawFaceY = bestAnchorFace.boundingBox.centerY() / abH

                        userScaleX = (initialW / rawFaceW).coerceIn(0.4f, 4.0f)
                        userScaleY = (initialH / rawFaceH).coerceIn(0.4f, 4.0f)
                        userOffsetX = initialX - rawFaceX
                        userOffsetY = initialY - rawFaceY
                        AppLog.d(TAG, "Locked to face id=$lockedTrackingId at anchorUs=$safeAnchorUs, scale=($userScaleX, $userScaleY), offset=($userOffsetX, $userOffsetY)")
                    }
                } catch (t: Throwable) {
                    AppLog.w(TAG, "Anchor frame face analysis failed: ${t.message}")
                }
            } else if (mode == TrackingMode.OBJECT && objectDetector != null) {
                try {
                    val img = InputImage.fromBitmap(anchorBmp, 0)
                    val objects: List<DetectedObject> = objectDetector.process(img).awaitResult()
                    val bestObj = objects.minByOrNull { obj ->
                        val ox = obj.boundingBox.centerX() / abW
                        val oy = obj.boundingBox.centerY() / abH
                        val dx = ox - initialX
                        val dy = oy - initialY
                        dx * dx + dy * dy
                    }
                    if (bestObj != null) {
                        lockedTrackingId = bestObj.trackingId
                        val rawW = (bestObj.boundingBox.width() / abW).coerceAtLeast(0.03f)
                        val rawH = (bestObj.boundingBox.height() / abH).coerceAtLeast(0.03f)
                        userScaleX = (initialW / rawW).coerceIn(0.4f, 4.0f)
                        userScaleY = (initialH / rawH).coerceIn(0.4f, 4.0f)
                        userOffsetX = initialX - (bestObj.boundingBox.centerX() / abW)
                        userOffsetY = initialY - (bestObj.boundingBox.centerY() / abH)
                    }
                } catch (t: Throwable) {
                    AppLog.w(TAG, "Anchor frame object analysis failed: ${t.message}")
                }
            }
            anchorBmp.recycle()
        }

        // Build list of all frame timestamps
        val allTimestamps = mutableListOf<Long>()
        var t = startTimeUs
        while (t <= endTimeUs) {
            allTimestamps.add(t)
            t += stepUs
        }
        if (allTimestamps.isEmpty() || allTimestamps.last() != endTimeUs) {
            allTimestamps.add(endTimeUs)
        }

        val forwardTimestamps = allTimestamps.filter { it >= safeAnchorUs }.sorted()
        val backwardTimestamps = allTimestamps.filter { it < safeAnchorUs }.sortedDescending()
        val totalSteps = (forwardTimestamps.size + backwardTimestamps.size).coerceAtLeast(1)
        var processedSteps = 0

        val resultsMap = mutableMapOf<Long, FrameTransform>()

        // Helper to track a sequence of timestamps starting from an anchor state
        suspend fun trackSequence(
            timestamps: List<Long>,
            startX: Float,
            startY: Float,
            startW: Float,
            startH: Float
        ) {
            var curX = startX
            var curY = startY
            var curW = startW
            var curH = startH
            var velX = 0f
            var velY = 0f

            for (pts in timestamps) {
                processedSteps++
                onProgress((processedSteps.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f))

                val rawBmp = extractUprightFrame(retriever, pts, rotationDegrees, maxDim = 640)
                if (rawBmp != null) {
                    val bmpW = rawBmp.width.toFloat()
                    val bmpH = rawBmp.height.toFloat()

                    if (mode == TrackingMode.FACE && faceDetector != null) {
                        try {
                            val img = InputImage.fromBitmap(rawBmp, 0)
                            val faces: List<Face> = faceDetector.process(img).awaitResult()
                            if (faces.isNotEmpty()) {
                                val matchedFace = if (lockedTrackingId != null) {
                                    faces.firstOrNull { it.trackingId == lockedTrackingId }
                                } else null

                                val targetFace = matchedFace ?: faces.filter { f ->
                                    val fx = f.boundingBox.centerX() / bmpW
                                    val fy = f.boundingBox.centerY() / bmpH
                                    val dist = sqrt((fx - curX) * (fx - curX) + (fy - curY) * (fy - curY))
                                    dist < 0.38f
                                }.minByOrNull { f ->
                                    val fx = f.boundingBox.centerX() / bmpW
                                    val fy = f.boundingBox.centerY() / bmpH
                                    val dx = fx - (curX + velX)
                                    val dy = fy - (curY + velY)
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

                                    val targetTargetX = (fcX + userOffsetX).coerceIn(0.02f, 0.98f)
                                    val targetTargetY = (fcY + userOffsetY).coerceIn(0.02f, 0.98f)
                                    val targetTargetW = (fcW * userScaleX).coerceIn(0.04f, 1.0f)
                                    val targetTargetH = (fcH * userScaleY).coerceIn(0.04f, 1.0f)

                                    velX = (targetTargetX - curX) * 0.45f
                                    velY = (targetTargetY - curY) * 0.45f

                                    // Snappy responsiveness filter
                                    curX = curX * 0.18f + targetTargetX * 0.82f
                                    curY = curY * 0.18f + targetTargetY * 0.82f
                                    curW = curW * 0.22f + targetTargetW * 0.78f
                                    curH = curH * 0.22f + targetTargetH * 0.78f
                                } else {
                                    // Fallback: apply momentum velocity
                                    curX = (curX + velX).coerceIn(0.02f, 0.98f)
                                    curY = (curY + velY).coerceIn(0.02f, 0.98f)
                                    velX *= 0.75f
                                    velY *= 0.75f
                                }
                            } else {
                                curX = (curX + velX).coerceIn(0.02f, 0.98f)
                                curY = (curY + velY).coerceIn(0.02f, 0.98f)
                                velX *= 0.75f
                                velY *= 0.75f
                            }
                        } catch (t: Throwable) {
                            AppLog.w(TAG, "Face detection error on frame: ${t.message}")
                        }
                    } else if (mode == TrackingMode.OBJECT && objectDetector != null) {
                        try {
                            val img = InputImage.fromBitmap(rawBmp, 0)
                            val objects: List<DetectedObject> = objectDetector.process(img).awaitResult()
                            if (objects.isNotEmpty()) {
                                val bestObj = objects.filter { obj ->
                                    val ox = obj.boundingBox.centerX() / bmpW
                                    val oy = obj.boundingBox.centerY() / bmpH
                                    val dist = sqrt((ox - curX) * (ox - curX) + (oy - curY) * (oy - curY))
                                    dist < 0.38f
                                }.minByOrNull { obj ->
                                    val ox = obj.boundingBox.centerX() / bmpW
                                    val oy = obj.boundingBox.centerY() / bmpH
                                    val dx = ox - (curX + velX)
                                    val dy = oy - (curY + velY)
                                    dx * dx + dy * dy
                                }
                                if (bestObj != null) {
                                    val targetX = (bestObj.boundingBox.centerX() / bmpW + userOffsetX).coerceIn(0.02f, 0.98f)
                                    val targetY = (bestObj.boundingBox.centerY() / bmpH + userOffsetY).coerceIn(0.02f, 0.98f)
                                    val targetW = (bestObj.boundingBox.width() / bmpW * userScaleX).coerceIn(0.04f, 1.0f)
                                    val targetH = (bestObj.boundingBox.height() / bmpH * userScaleY).coerceIn(0.04f, 1.0f)

                                    velX = (targetX - curX) * 0.45f
                                    velY = (targetY - curY) * 0.45f

                                    curX = curX * 0.20f + targetX * 0.80f
                                    curY = curY * 0.20f + targetY * 0.80f
                                    curW = curW * 0.25f + targetW * 0.75f
                                    curH = curH * 0.25f + targetH * 0.75f
                                } else {
                                    curX = (curX + velX).coerceIn(0.02f, 0.98f)
                                    curY = (curY + velY).coerceIn(0.02f, 0.98f)
                                    velX *= 0.75f
                                    velY *= 0.75f
                                }
                            }
                        } catch (t: Throwable) {
                            AppLog.w(TAG, "Object detection error on frame: ${t.message}")
                        }
                    }
                    rawBmp.recycle()
                }

                resultsMap[pts] = FrameTransform(curX, curY, curW, curH)
            }
        }

        try {
            // Forward pass from anchor to end
            if (forwardTimestamps.isNotEmpty()) {
                trackSequence(forwardTimestamps, initialX, initialY, initialW, initialH)
            }

            // Backward pass from anchor down to start
            if (backwardTimestamps.isNotEmpty()) {
                trackSequence(backwardTimestamps, initialX, initialY, initialW, initialH)
            }
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
            try { faceDetector?.close() } catch (_: Throwable) {}
            try { objectDetector?.close() } catch (_: Throwable) {}
        }

        // Convert sorted results map to BlurKeyframes
        allTimestamps.sorted().mapNotNull { pts ->
            val tr = resultsMap[pts] ?: return@mapNotNull null
            BlurKeyframe(
                timeOffsetUs = pts - startTimeUs,
                normalizedX = tr.x,
                normalizedY = tr.y,
                width = tr.w,
                height = tr.h,
                rotation = initialRotation
            )
        }
    }

    companion object {
        private const val TAG = "MotionTracker"
    }
}
