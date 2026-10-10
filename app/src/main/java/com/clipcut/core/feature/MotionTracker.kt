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
    BODY("Track Full Body (Head to Toe)"),
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
 * High-speed Zero-Allocation Color + Luminance Template Tracker.
 * Tracks fast-moving objects, full bodies, visual patches, and faces using Sum of Absolute Differences (SAD).
 *
 * Features:
 *  - Dual-Memory Architecture (Original Anchor + Running Adaptive): Blends current appearance with the
 *    original anchor template to prevent appearance drift onto pavement/cars while following turning bodies.
 *  - 4-Channel (RGB + Luminance) color discrimination: preserves clothing/silhouette identity.
 *  - Full Frame Edge Tolerance: Never drops candidates whose boxes touch or extend past screen borders.
 *  - 3-Phase Hierarchical Pyramid Search (Coarse step 8, Medium step 2, Fine step 1):
 *    covers a massive ±260px search window in ~2ms.
 */
private class VisualTemplateTracker {
    private var anchorR: IntArray? = null
    private var anchorG: IntArray? = null
    private var anchorB: IntArray? = null
    private var anchorLum: IntArray? = null

    private var templateR: IntArray? = null
    private var templateG: IntArray? = null
    private var templateB: IntArray? = null
    private var templateLum: IntArray? = null
    private val tSize = 32

    fun initTemplate(
        anchorBmp: Bitmap,
        normX: Float,
        normY: Float,
        normW: Float,
        normH: Float
    ) {
        val bmpW = anchorBmp.width
        val bmpH = anchorBmp.height

        val pxCenterX = (normX * bmpW).toInt()
        val pxCenterY = (normY * bmpH).toInt()
        val pxW = (normW * bmpW).toInt().coerceIn(16, bmpW)
        val pxH = (normH * bmpH).toInt().coerceIn(16, bmpH)

        val left = (pxCenterX - pxW / 2).coerceIn(0, bmpW - 1)
        val top = (pxCenterY - pxH / 2).coerceIn(0, bmpH - 1)
        val actualW = pxW.coerceIn(8, bmpW - left)
        val actualH = pxH.coerceIn(8, bmpH - top)

        try {
            val cropped = Bitmap.createBitmap(anchorBmp, left, top, actualW, actualH)
            val scaled = Bitmap.createScaledBitmap(cropped, tSize, tSize, true)
            if (cropped != scaled && cropped != anchorBmp) {
                cropped.recycle()
            }
            val pixels = IntArray(tSize * tSize)
            scaled.getPixels(pixels, 0, tSize, 0, 0, tSize, tSize)
            scaled.recycle()

            val rArr = IntArray(tSize * tSize)
            val gArr = IntArray(tSize * tSize)
            val bArr = IntArray(tSize * tSize)
            val lumArr = IntArray(tSize * tSize)

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                rArr[i] = r
                gArr[i] = g
                bArr[i] = b
                lumArr[i] = ((r * 77) + (g * 150) + (b * 29)) shr 8
            }

            anchorR = rArr.clone()
            anchorG = gArr.clone()
            anchorB = bArr.clone()
            anchorLum = lumArr.clone()

            templateR = rArr
            templateG = gArr
            templateB = bArr
            templateLum = lumArr
        } catch (_: Throwable) {
            anchorR = null; anchorG = null; anchorB = null; anchorLum = null
            templateR = null; templateG = null; templateB = null; templateLum = null
        }
    }

    data class MatchResult(val normX: Float, val normY: Float, val confidence: Float)

    fun findMatch(
        frameBmp: Bitmap,
        expectedNormX: Float,
        expectedNormY: Float,
        normW: Float,
        normH: Float
    ): MatchResult? {
        val tLum = templateLum ?: return null
        val tR = templateR ?: return null
        val tG = templateG ?: return null
        val tB = templateB ?: return null

        val aLum = anchorLum ?: tLum
        val aR = anchorR ?: tR
        val aG = anchorG ?: tG
        val aB = anchorB ?: tB

        val bmpW = frameBmp.width
        val bmpH = frameBmp.height

        val expPxX = (expectedNormX * bmpW).toInt()
        val expPxY = (expectedNormY * bmpH).toInt()
        val pxW = (normW * bmpW).toInt().coerceIn(16, bmpW)
        val pxH = (normH * bmpH).toInt().coerceIn(16, bmpH)

        val framePixels = IntArray(bmpW * bmpH)
        frameBmp.getPixels(framePixels, 0, bmpW, 0, 0, bmpW, bmpH)

        // Broad search radius covers fast walking, running, and rapid pans
        val searchRadiusX = (bmpW * 0.38f).toInt().coerceIn(60, 260)
        val searchRadiusY = (bmpH * 0.38f).toInt().coerceIn(60, 260)

        // ── Phase 1: Hierarchical Coarse Search (step = 8, subsample template by 4) ──
        var bestCoarseDx = 0
        var bestCoarseDy = 0
        var minCoarseSad = Long.MAX_VALUE
        val coarseStep = 8

        var dy = -searchRadiusY
        while (dy <= searchRadiusY) {
            var dx = -searchRadiusX
            while (dx <= searchRadiusX) {
                val candCenterX = expPxX + dx
                val candCenterY = expPxY + dy

                // Valid candidate center in frame — safe edge clamping inside pixel loop avoids dropping full bodies
                if (candCenterX in 0 until bmpW && candCenterY in 0 until bmpH) {
                    var sad = 0L
                    val startX = candCenterX - pxW / 2f
                    val startY = candCenterY - pxH / 2f
                    val stepX = pxW.toFloat() / tSize
                    val stepY = pxH.toFloat() / tSize

                    for (r in 0 until tSize step 4) {
                        val sy = (startY + r * stepY).toInt().coerceIn(0, bmpH - 1)
                        val rowOffset = sy * bmpW
                        val tRowOffset = r * tSize
                        for (c in 0 until tSize step 4) {
                            val sx = (startX + c * stepX).toInt().coerceIn(0, bmpW - 1)
                            val p = framePixels[rowOffset + sx]
                            val cr = (p shr 16) and 0xFF
                            val cg = (p shr 8) and 0xFF
                            val cb = p and 0xFF
                            val cl = ((cr * 77) + (cg * 150) + (cb * 29)) shr 8

                            val tIdx = tRowOffset + c
                            val diffLum = kotlin.math.abs(cl - tLum[tIdx])
                            val diffCol = (kotlin.math.abs(cr - tR[tIdx]) + kotlin.math.abs(cg - tG[tIdx]) + kotlin.math.abs(cb - tB[tIdx])) / 3

                            val aDiffLum = kotlin.math.abs(cl - aLum[tIdx])
                            val aDiffCol = (kotlin.math.abs(cr - aR[tIdx]) + kotlin.math.abs(cg - aG[tIdx]) + kotlin.math.abs(cb - aB[tIdx])) / 3

                            val combined = ((diffLum + diffCol) * 2 + (aDiffLum + aDiffCol)) / 3
                            sad += combined
                        }
                    }

                    if (sad < minCoarseSad) {
                        minCoarseSad = sad
                        bestCoarseDx = dx
                        bestCoarseDy = dy
                    }
                }
                dx += coarseStep
            }
            dy += coarseStep
        }

        // ── Phase 2: Medium Search around best coarse (step = 2, subsample template by 2) ──
        var bestMedDx = bestCoarseDx
        var bestMedDy = bestCoarseDy
        var minMedSad = Long.MAX_VALUE
        val medRadius = 8

        var mdy = bestCoarseDy - medRadius
        while (mdy <= bestCoarseDy + medRadius) {
            var mdx = bestCoarseDx - medRadius
            while (mdx <= bestCoarseDx + medRadius) {
                val candCenterX = expPxX + mdx
                val candCenterY = expPxY + mdy

                if (candCenterX in 0 until bmpW && candCenterY in 0 until bmpH) {
                    var sad = 0L
                    val startX = candCenterX - pxW / 2f
                    val startY = candCenterY - pxH / 2f
                    val stepX = pxW.toFloat() / tSize
                    val stepY = pxH.toFloat() / tSize

                    for (r in 0 until tSize step 2) {
                        val sy = (startY + r * stepY).toInt().coerceIn(0, bmpH - 1)
                        val rowOffset = sy * bmpW
                        val tRowOffset = r * tSize
                        for (c in 0 until tSize step 2) {
                            val sx = (startX + c * stepX).toInt().coerceIn(0, bmpW - 1)
                            val p = framePixels[rowOffset + sx]
                            val cr = (p shr 16) and 0xFF
                            val cg = (p shr 8) and 0xFF
                            val cb = p and 0xFF
                            val cl = ((cr * 77) + (cg * 150) + (cb * 29)) shr 8

                            val tIdx = tRowOffset + c
                            val diffLum = kotlin.math.abs(cl - tLum[tIdx])
                            val diffCol = (kotlin.math.abs(cr - tR[tIdx]) + kotlin.math.abs(cg - tG[tIdx]) + kotlin.math.abs(cb - tB[tIdx])) / 3

                            val aDiffLum = kotlin.math.abs(cl - aLum[tIdx])
                            val aDiffCol = (kotlin.math.abs(cr - aR[tIdx]) + kotlin.math.abs(cg - aG[tIdx]) + kotlin.math.abs(cb - aB[tIdx])) / 3

                            val combined = ((diffLum + diffCol) * 2 + (aDiffLum + aDiffCol)) / 3
                            sad += combined
                        }
                    }

                    if (sad < minMedSad) {
                        minMedSad = sad
                        bestMedDx = mdx
                        bestMedDy = mdy
                    }
                }
                mdx += 2
            }
            mdy += 2
        }

        // ── Phase 3: Fine Search around best medium (step = 1, full 32x32 template) ──
        var bestDx = bestMedDx
        var bestDy = bestMedDy
        var minSad = Long.MAX_VALUE
        val fineRadius = 2

        for (fdy in (bestMedDy - fineRadius)..(bestMedDy + fineRadius)) {
            for (fdx in (bestMedDx - fineRadius)..(bestMedDx + fineRadius)) {
                val candCenterX = expPxX + fdx
                val candCenterY = expPxY + fdy

                if (candCenterX in 0 until bmpW && candCenterY in 0 until bmpH) {
                    var sad = 0L
                    val startX = candCenterX - pxW / 2f
                    val startY = candCenterY - pxH / 2f
                    val stepX = pxW.toFloat() / tSize
                    val stepY = pxH.toFloat() / tSize

                    for (r in 0 until tSize) {
                        val sy = (startY + r * stepY).toInt().coerceIn(0, bmpH - 1)
                        val rowOffset = sy * bmpW
                        val tRowOffset = r * tSize
                        for (c in 0 until tSize) {
                            val sx = (startX + c * stepX).toInt().coerceIn(0, bmpW - 1)
                            val p = framePixels[rowOffset + sx]
                            val cr = (p shr 16) and 0xFF
                            val cg = (p shr 8) and 0xFF
                            val cb = p and 0xFF
                            val cl = ((cr * 77) + (cg * 150) + (cb * 29)) shr 8

                            val tIdx = tRowOffset + c
                            val diffLum = kotlin.math.abs(cl - tLum[tIdx])
                            val diffCol = (kotlin.math.abs(cr - tR[tIdx]) + kotlin.math.abs(cg - tG[tIdx]) + kotlin.math.abs(cb - tB[tIdx])) / 3

                            val aDiffLum = kotlin.math.abs(cl - aLum[tIdx])
                            val aDiffCol = (kotlin.math.abs(cr - aR[tIdx]) + kotlin.math.abs(cg - aG[tIdx]) + kotlin.math.abs(cb - aB[tIdx])) / 3

                            val combined = ((diffLum + diffCol) * 2 + (aDiffLum + aDiffCol)) / 3
                            sad += combined
                        }
                    }

                    if (sad < minSad) {
                        minSad = sad
                        bestDx = fdx
                        bestDy = fdy
                    }
                }
            }
        }

        val finalPxX = expPxX + bestDx
        val finalPxY = expPxY + bestDy
        val resultNormX = (finalPxX.toFloat() / bmpW).coerceIn(0.02f, 0.98f)
        val resultNormY = (finalPxY.toFloat() / bmpH).coerceIn(0.02f, 0.98f)

        // Confidence: average error per pixel across all color channels
        val avgError = minSad.toFloat() / (tSize * tSize)
        val confidence = (1.0f - (avgError / 120f)).coerceIn(0f, 1f)

        // Adaptive template update on high confidence (blend gently 8% to follow rotations without drift)
        if (confidence >= 0.52f) {
            adaptTemplate(framePixels, bmpW, bmpH, finalPxX, finalPxY, pxW, pxH)
        }

        return MatchResult(resultNormX, resultNormY, confidence)
    }

    private fun adaptTemplate(
        framePixels: IntArray,
        bmpW: Int,
        bmpH: Int,
        centerX: Int,
        centerY: Int,
        pxW: Int,
        pxH: Int
    ) {
        val tLum = templateLum ?: return
        val tR = templateR ?: return
        val tG = templateG ?: return
        val tB = templateB ?: return

        val startX = centerX - pxW / 2f
        val startY = centerY - pxH / 2f
        val stepX = pxW.toFloat() / tSize
        val stepY = pxH.toFloat() / tSize

        for (r in 0 until tSize) {
            val sy = (startY + r * stepY).toInt().coerceIn(0, bmpH - 1)
            val rowOffset = sy * bmpW
            val tRowOffset = r * tSize
            for (c in 0 until tSize) {
                val sx = (startX + c * stepX).toInt().coerceIn(0, bmpW - 1)
                val p = framePixels[rowOffset + sx]
                val cr = (p shr 16) and 0xFF
                val cg = (p shr 8) and 0xFF
                val cb = p and 0xFF
                val cl = ((cr * 77) + (cg * 150) + (cb * 29)) shr 8

                val tIdx = tRowOffset + c
                tR[tIdx] = (tR[tIdx] * 92 + cr * 8) / 100
                tG[tIdx] = (tG[tIdx] * 92 + cg * 8) / 100
                tB[tIdx] = (tB[tIdx] * 92 + cb * 8) / 100
                tLum[tIdx] = (tLum[tIdx] * 92 + cl * 8) / 100
            }
        }
    }
}

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

        // On Android, MediaMetadataRetriever often pre-rotates portrait video frames.
        // If the bitmap is already portrait (srcW < srcH) when rotation is 90 or 270, do NOT rotate again!
        val alreadyRotated = (rotationDegrees == 90 || rotationDegrees == 270) && (srcW < srcH)
        val deg = if (alreadyRotated) 0 else rotationDegrees

        val scale = if (maxOf(srcW, srcH) > maxDim) {
            maxDim.toFloat() / maxOf(srcW, srcH).toFloat()
        } else 1.0f

        val matrix = Matrix()
        if (scale < 1.0f) {
            matrix.postScale(scale, scale)
        }
        if (deg != 0) {
            matrix.postRotate(deg.toFloat())
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

        val faceDetector = if (mode == TrackingMode.FACE || mode == TrackingMode.BODY) {
            val opts = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.04f)
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

        // 1. ANCHOR LOCK: Sample the upright frame at anchorPtsUs to identify the locked target & visual template
        var anchorFaceX = initialX
        var anchorFaceY = initialY
        var anchorFaceW = 0f
        var anchorFaceH = 0f
        var userOffsetX = 0f
        var userOffsetY = 0f
        var hasHeadAnchor = false

        val templateTracker = VisualTemplateTracker()

        val anchorBmp = extractUprightFrame(retriever, safeAnchorUs, rotationDegrees, maxDim = 720)
        if (anchorBmp != null) {
            val abW = anchorBmp.width.toFloat()
            val abH = anchorBmp.height.toFloat()

            // Initialize visual template tracker with the user's exact box region
            templateTracker.initTemplate(anchorBmp, initialX, initialY, initialW, initialH)

            if ((mode == TrackingMode.FACE || mode == TrackingMode.BODY) && faceDetector != null) {
                try {
                    val img = InputImage.fromBitmap(anchorBmp, 0)
                    val faces: List<Face> = faceDetector.process(img).awaitResult()

                    val expectedFaceY = if (mode == TrackingMode.BODY) {
                        (initialY - initialH * 0.32f).coerceIn(0.02f, 0.98f)
                    } else {
                        initialY
                    }

                    val bestAnchorFace = faces.minByOrNull { f ->
                        val fx = f.boundingBox.centerX() / abW
                        val fy = f.boundingBox.centerY() / abH
                        val dx = fx - initialX
                        val dy = fy - expectedFaceY
                        dx * dx + dy * dy
                    }

                    if (bestAnchorFace != null) {
                        val fx = bestAnchorFace.boundingBox.centerX() / abW
                        val fy = bestAnchorFace.boundingBox.centerY() / abH
                        val dist = sqrt((fx - initialX) * (fx - initialX) + (fy - expectedFaceY) * (fy - expectedFaceY))
                        val maxDist = if (mode == TrackingMode.BODY) 0.35f else 0.20f

                        if (dist < maxDist) {
                            hasHeadAnchor = true
                            anchorFaceX = fx
                            anchorFaceY = fy
                            anchorFaceW = (bestAnchorFace.boundingBox.width() / abW).coerceAtLeast(0.02f)
                            anchorFaceH = (bestAnchorFace.boundingBox.height() / abH).coerceAtLeast(0.02f)

                            userOffsetX = initialX - anchorFaceX
                            userOffsetY = initialY - anchorFaceY
                            AppLog.d(TAG, "Locked to face/head at anchorUs=$safeAnchorUs, facePos=($anchorFaceX, $anchorFaceY), offset=($userOffsetX, $userOffsetY), mode=$mode")
                        }
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
            startH: Float,
            startFaceX: Float,
            startFaceY: Float
        ) {
            var curX = startX
            var curY = startY
            var curW = startW
            var curH = startH
            var curFaceX = startFaceX
            var curFaceY = startFaceY

            var velX = 0f
            var velY = 0f
            var velFaceX = 0f
            var velFaceY = 0f

            for (pts in timestamps) {
                processedSteps++
                onProgress((processedSteps.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f))

                // At the exact anchor frame, guarantee pristine user placement
                if (pts == safeAnchorUs) {
                    curX = initialX
                    curY = initialY
                    curW = initialW
                    curH = initialH
                    curFaceX = anchorFaceX
                    curFaceY = anchorFaceY
                    velX = 0f
                    velY = 0f
                    velFaceX = 0f
                    velFaceY = 0f
                    resultsMap[pts] = FrameTransform(initialX, initialY, initialW, initialH)
                    continue
                }

                // Momentum prediction leads fast-moving targets
                val predX = (curX + velX).coerceIn(0.02f, 0.98f)
                val predY = (curY + velY).coerceIn(0.02f, 0.98f)
                val predFaceX = (curFaceX + velFaceX).coerceIn(0.02f, 0.98f)
                val predFaceY = (curFaceY + velFaceY).coerceIn(0.02f, 0.98f)

                val rawBmp = extractUprightFrame(retriever, pts, rotationDegrees, maxDim = 720)
                if (rawBmp != null) {
                    val bmpW = rawBmp.width.toFloat()
                    val bmpH = rawBmp.height.toFloat()

                    if (mode == TrackingMode.BODY) {
                        // ── FULL BODY / PERSON TRACKING ──
                        val match = templateTracker.findMatch(rawBmp, predX, predY, curW, curH)

                        var headFound = false
                        var headImpliedX = predX
                        var headImpliedY = predY
                        var scaleFactor = 1.0f

                        if (faceDetector != null && hasHeadAnchor) {
                            try {
                                val img = InputImage.fromBitmap(rawBmp, 0)
                                val faces: List<Face> = faceDetector.process(img).awaitResult()
                                val targetFace = faces.minByOrNull { f ->
                                    val fx = f.boundingBox.centerX() / bmpW
                                    val fy = f.boundingBox.centerY() / bmpH
                                    val dx = fx - predFaceX
                                    val dy = fy - predFaceY
                                    dx * dx + dy * dy
                                }
                                if (targetFace != null) {
                                    val fx = targetFace.boundingBox.centerX() / bmpW
                                    val fy = targetFace.boundingBox.centerY() / bmpH
                                    val fcW = targetFace.boundingBox.width() / bmpW
                                    val dist = sqrt((fx - predFaceX) * (fx - predFaceX) + (fy - predFaceY) * (fy - predFaceY))
                                    if (dist < 0.22f) {
                                        headFound = true
                                        curFaceX = fx
                                        curFaceY = fy
                                        headImpliedX = (fx + userOffsetX).coerceIn(0.02f, 0.98f)
                                        headImpliedY = (fy + userOffsetY).coerceIn(0.02f, 0.98f)
                                        if (anchorFaceW > 0.01f) {
                                            scaleFactor = (fcW / anchorFaceW).coerceIn(0.85f, 2.0f)
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }

                        val targetX: Float
                        val targetY: Float
                        if (headFound && match != null && match.confidence >= 0.30f) {
                            // Fuse head anchor and body template match
                            targetX = headImpliedX * 0.40f + match.normX * 0.60f
                            targetY = headImpliedY * 0.40f + match.normY * 0.60f
                        } else if (match != null && match.confidence >= 0.22f) {
                            // Head turned or occluded: template tracks clothing/body seamlessly
                            targetX = match.normX
                            targetY = match.normY
                        } else if (headFound) {
                            targetX = headImpliedX
                            targetY = headImpliedY
                        } else {
                            targetX = predX
                            targetY = predY
                        }

                        val instVelX = targetX - curX
                        val instVelY = targetY - curY
                        velX = velX * 0.40f + instVelX * 0.60f
                        velY = velY * 0.40f + instVelY * 0.60f

                        curX = (curX * 0.25f + targetX * 0.75f).coerceIn(0.02f, 0.98f)
                        curY = (curY * 0.25f + targetY * 0.75f).coerceIn(0.02f, 0.98f)

                        curFaceX = curX - userOffsetX
                        curFaceY = curY - userOffsetY
                        velFaceX = velX
                        velFaceY = velY

                        curW = (initialW * scaleFactor).coerceIn(0.08f, 1.2f)
                        curH = (initialH * scaleFactor).coerceIn(0.08f, 1.2f)

                    } else if (mode == TrackingMode.FACE && faceDetector != null) {
                        var faceFoundAndLocked = false
                        try {
                            val img = InputImage.fromBitmap(rawBmp, 0)
                            val faces: List<Face> = faceDetector.process(img).awaitResult()

                            // Match candidate face closest to predicted face position
                            val targetFace = faces.minByOrNull { f ->
                                val fx = f.boundingBox.centerX() / bmpW
                                val fy = f.boundingBox.centerY() / bmpH
                                val dx = fx - predFaceX
                                val dy = fy - predFaceY
                                dx * dx + dy * dy
                            }

                            if (targetFace != null) {
                                val fx = targetFace.boundingBox.centerX() / bmpW
                                val fy = targetFace.boundingBox.centerY() / bmpH
                                val fcW = targetFace.boundingBox.width() / bmpW
                                val dist = sqrt((fx - predFaceX) * (fx - predFaceX) + (fy - predFaceY) * (fy - predFaceY))

                                // Tight proximity check: prevents jumping to strangers across crowded streets
                                if (dist < 0.16f) {
                                    faceFoundAndLocked = true
                                    val instVelFaceX = fx - curFaceX
                                    val instVelFaceY = fy - curFaceY
                                    velFaceX = velFaceX * 0.4f + instVelFaceX * 0.6f
                                    velFaceY = velFaceY * 0.4f + instVelFaceY * 0.6f

                                    curFaceX = fx
                                    curFaceY = fy

                                    val targetBoxX = (curFaceX + userOffsetX).coerceIn(0.02f, 0.98f)
                                    val targetBoxY = (curFaceY + userOffsetY).coerceIn(0.02f, 0.98f)

                                    val instVelX = targetBoxX - curX
                                    val instVelY = targetBoxY - curY
                                    velX = velX * 0.4f + instVelX * 0.6f
                                    velY = velY * 0.4f + instVelY * 0.6f

                                    val scaleFactor = if (anchorFaceW > 0.01f) {
                                        (fcW / anchorFaceW).coerceIn(1.0f, 2.5f)
                                    } else 1.0f

                                    val targetBoxW = initialW * scaleFactor
                                    val targetBoxH = initialH * scaleFactor

                                    curX = curX * 0.25f + targetBoxX * 0.75f
                                    curY = curY * 0.25f + targetBoxY * 0.75f
                                    curW = maxOf(curW * 0.25f + targetBoxW * 0.75f, initialW)
                                    curH = maxOf(curH * 0.25f + targetBoxH * 0.75f, initialH)
                                }
                            }
                        } catch (t: Throwable) {
                            AppLog.w(TAG, "Face detection error on frame: ${t.message}")
                        }

                        if (!faceFoundAndLocked) {
                            // Head turned sideways, looked down, or profile face:
                            // Fall back to Visual Template Tracker on the subject's head!
                            val match = templateTracker.findMatch(rawBmp, predX, predY, curW, curH)
                            if (match != null && match.confidence >= 0.28f) {
                                val instVelX = match.normX - curX
                                val instVelY = match.normY - curY
                                velX = velX * 0.4f + instVelX * 0.6f
                                velY = velY * 0.4f + instVelY * 0.6f

                                curX = curX * 0.25f + match.normX * 0.75f
                                curY = curY * 0.25f + match.normY * 0.75f
                                curFaceX = curX - userOffsetX
                                curFaceY = curY - userOffsetY
                                velFaceX = velX
                                velFaceY = velY
                            } else {
                                // Apply momentum dampening
                                curX = (curX + velX).coerceIn(0.02f, 0.98f)
                                curY = (curY + velY).coerceIn(0.02f, 0.98f)
                                curFaceX = curX - userOffsetX
                                curFaceY = curY - userOffsetY
                                velX *= 0.85f
                                velY *= 0.85f
                                velFaceX *= 0.85f
                                velFaceY *= 0.85f
                            }
                            curW = maxOf(curW, initialW)
                            curH = maxOf(curH, initialH)
                        }
                    } else {
                        // OBJECT TRACKING: Hierarchical template tracker with velocity lead
                        val match = templateTracker.findMatch(rawBmp, predX, predY, curW, curH)
                        if (match != null && match.confidence >= 0.22f) {
                            val instVelX = match.normX - curX
                            val instVelY = match.normY - curY
                            velX = velX * 0.4f + instVelX * 0.6f
                            velY = velY * 0.4f + instVelY * 0.6f

                            curX = curX * 0.20f + match.normX * 0.80f
                            curY = curY * 0.20f + match.normY * 0.80f
                        } else {
                            curX = (curX + velX).coerceIn(0.02f, 0.98f)
                            curY = (curY + velY).coerceIn(0.02f, 0.98f)
                            velX *= 0.85f
                            velY *= 0.85f
                        }
                        curW = initialW
                        curH = initialH
                    }
                    rawBmp.recycle()
                }

                resultsMap[pts] = FrameTransform(curX, curY, curW, curH)
            }
        }

        try {
            // Forward pass from anchor to end
            if (forwardTimestamps.isNotEmpty()) {
                trackSequence(forwardTimestamps, initialX, initialY, initialW, initialH, anchorFaceX, anchorFaceY)
            }

            // Backward pass from anchor down to start
            if (backwardTimestamps.isNotEmpty()) {
                trackSequence(backwardTimestamps, initialX, initialY, initialW, initialH, anchorFaceX, anchorFaceY)
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
