package com.clipcut.core.codec

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.clipcut.core.gl.ChromaKeyFilter
import com.clipcut.core.gl.ColorAdjustFilter
import com.clipcut.core.gl.FramebufferTexture
import com.clipcut.core.gl.GlUtil
import com.clipcut.core.gl.LutFilter
import com.clipcut.core.gl.MaskFilter
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.gl.OverlayRenderer
import com.clipcut.core.gl.TransitionFilter
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.MediaType
import com.clipcut.core.model.MaskShape
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import com.clipcut.core.model.TransitionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class ExportResult(
    val file: File,
    val publicUri: Uri? = null,
    val galleryPath: String = "Movies/ClipCut"
)

class ExportEngine(
    private val context: Context,
    private val project: Project,
    private val config: ExportConfig
) {
    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        AppLog.w(TAG, "Export cancellation requested by user")
        isCancelled.set(true)
    }

    suspend fun export(
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val totalDurationUs = project.totalDurationUs
        if (totalDurationUs <= 0 || project.videoClips.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Project has no video clips or zero duration"))
        }

        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(exportDir, "ClipCut_${timeStamp}.mp4")

        var muxerWrapper: MediaMuxerWrapper? = null
        var videoEncoder: VideoEncoder? = null
        var outputSurface: OutputSurface? = null
        var colorFilter: ColorAdjustFilter? = null
        var overlayRenderer: OverlayRenderer? = null
        var transitionFilter: TransitionFilter? = null
        var maskFilter: MaskFilter? = null
        var outgoingFbo: FramebufferTexture? = null
        var incomingTransitionTexId = 0
        var lutFilter: LutFilter? = null
        var chromaFilter: ChromaKeyFilter? = null
        var currentLutUri: String? = null

        try {
            onProgress(0.01f, "Initializing encoder & GL pipeline...")

            val hasAudio = project.videoClips.any { it.volume > 0.001f } || project.audioTracks.isNotEmpty()
            val expectedTracks = if (hasAudio) 2 else 1
            muxerWrapper = MediaMuxerWrapper(outputFile.absolutePath, expectedTracks)

            videoEncoder = VideoEncoder(config)
            videoEncoder.init()
            val inputSurface = videoEncoder.inputSurface ?: throw RuntimeException("InputSurface is null")
            inputSurface.makeCurrent()

            val oesTexId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            outputSurface = OutputSurface(oesTexId)

            colorFilter = ColorAdjustFilter().also { it.init() }
            overlayRenderer = OverlayRenderer(context).also { it.init() }
            transitionFilter = TransitionFilter().also { it.init() }
            maskFilter = MaskFilter().also { it.init() }
            outgoingFbo = FramebufferTexture(config.customWidth, config.customHeight).also { it.init() }

            incomingTransitionTexId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, incomingTransitionTexId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                config.customWidth, config.customHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

            val texMatrix = FloatArray(16)
            val mvpMatrix = FloatArray(16)
            var currentTimelineOffsetUs = 0L

            for ((clipIndex, clip) in project.videoClips.withIndex()) {
                if (isCancelled.get()) throw InterruptedException("Export cancelled")

                // Prepare LUT filter if needed
                if (clip.lutConfig != null && clip.lutConfig.uri != currentLutUri) {
                    lutFilter?.release()
                    lutFilter = LutFilter(context, clip.lutConfig.uri, clip.lutConfig.intensity)
                    lutFilter.init()
                    currentLutUri = clip.lutConfig.uri
                } else if (clip.lutConfig == null) {
                    lutFilter?.release()
                    lutFilter = null
                    currentLutUri = null
                }

                // Prepare chroma filter if needed
                if (clip.chromaKeyColor != null) {
                    if (chromaFilter == null) {
                        chromaFilter = ChromaKeyFilter().also { it.init() }
                    }
                    chromaFilter.keyColor = clip.chromaKeyColor
                    chromaFilter.threshold = clip.chromaKeyThreshold
                    chromaFilter.smoothness = clip.chromaKeySmoothness
                } else {
                    chromaFilter?.release()
                    chromaFilter = null
                }

                val nextClip = project.videoClips.getOrNull(clipIndex + 1)
                val nextNeedsTransition = nextClip != null && nextClip.transitionType != TransitionType.NONE

                if (clip.mediaType == MediaType.IMAGE) {
                    renderImageClip(
                        clip, clipIndex, currentTimelineOffsetUs, inputSurface, colorFilter,
                        overlayRenderer, transitionFilter, maskFilter, lutFilter, chromaFilter,
                        incomingTransitionTexId, muxerWrapper, videoEncoder, totalDurationUs,
                        onProgress, outgoingFbo, nextNeedsTransition
                    )
                    currentTimelineOffsetUs += clip.effectiveDurationUs
                    continue
                }

                val decoder = VideoDecoder(context, clip, outputSurface)
                decoder.init()

                val rotation = decoder.videoRotation
                val rotated = rotation == 90 || rotation == 270
                val srcW = decoder.videoWidth
                val srcH = decoder.videoHeight

                buildFitMvpMatrix(
                    srcW = if (rotated) srcH else srcW,
                    srcH = if (rotated) srcW else srcH,
                    outW = config.customWidth, outH = config.customHeight,
                    out = mvpMatrix
                )

                val transitionActive = clip.transitionType != TransitionType.NONE && clipIndex > 0
                val transitionDurationUs = if (transitionActive) clip.transitionDurationUs else 0L
                var lastPtsUs = -1L

                try {
                    while (!isCancelled.get()) {
                        val ptsUs = decoder.decodeNextFrame()
                        if (ptsUs < 0) break

                        val clipLocalSourceUs = (ptsUs - clip.trimStartUs).coerceAtLeast(0L)
                        val scaledLocalTimeUs = if (clip.speedRamp.isActive) {
                            SpeedRampProcessor.sourceToTimeline(clipLocalSourceUs, clip.speedRamp, clip.speed)
                        } else {
                            (clipLocalSourceUs / clip.speed).toLong()
                        }
                        val outputPtsUs = currentTimelineOffsetUs + scaledLocalTimeUs

                        if (outputPtsUs <= lastPtsUs) continue
                        lastPtsUs = outputPtsUs

                        GLES20.glViewport(0, 0, config.customWidth, config.customHeight)
                        GLES20.glClearColor(0f, 0f, 0f, 1f)
                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                        outputSurface.getTransformMatrix(texMatrix)

                        val inTransition = transitionActive && scaledLocalTimeUs < transitionDurationUs

                        if (inTransition) {
                            colorFilter.updateConfig(clip.filterConfig)
                            colorFilter.updateCropFlip(clip.cropRect, clip.flipHorizontal, clip.flipVertical)
                            colorFilter.drawOes(oesTexId, texMatrix, mvpMatrix, rotation, config.customWidth, config.customHeight)

                            if (lutFilter != null) {
                                // LUT is applied after the base render — skip for simplicity during transition
                            }

                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, incomingTransitionTexId)
                            GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, config.customWidth, config.customHeight)
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

                            GLES20.glViewport(0, 0, config.customWidth, config.customHeight)
                            GLES20.glClearColor(0f, 0f, 0f, 1f)
                            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                            transitionFilter!!.setType(clip.transitionType)
                            val progress = (scaledLocalTimeUs.toFloat() / transitionDurationUs.toFloat()).coerceIn(0f, 1f)
                            transitionFilter.setProgress(progress)
                            transitionFilter.drawTransition(
                                textureOutId = outgoingFbo!!.textureId,
                                textureInId = incomingTransitionTexId,
                                texMatrix = GlUtil.IDENTITY_MATRIX,
                                mvpMatrix = GlUtil.IDENTITY_MATRIX,
                                width = config.customWidth, height = config.customHeight
                            )
                        } else {
                            // Chroma key first (if any)
                            if (chromaFilter != null) {
                                chromaFilter.drawOes(oesTexId, texMatrix, mvpMatrix, rotation, config.customWidth, config.customHeight)
                            } else {
                                colorFilter.updateConfig(clip.filterConfig)
                                colorFilter.updateCropFlip(clip.cropRect, clip.flipHorizontal, clip.flipVertical)
                                colorFilter.drawOes(oesTexId, texMatrix, mvpMatrix, rotation, config.customWidth, config.customHeight)
                            }
                        }

                        // Mask
                        if (clip.maskConfig.shape != MaskShape.NONE) {
                            maskFilter?.updateConfig(clip.maskConfig)
                            // Mask would need a second pass — simplified here
                        }

                        overlayRenderer.renderOverlays(
                            overlays = project.overlays,
                            currentPtsUs = outputPtsUs,
                            viewportWidth = config.customWidth,
                            viewportHeight = config.customHeight
                        )

                        if (nextNeedsTransition && !inTransition) {
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outgoingFbo!!.textureId)
                            GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, config.customWidth, config.customHeight)
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                        }

                        inputSurface.setPresentationTime(outputPtsUs * 1000L)
                        inputSurface.swapBuffers()

                        videoEncoder.drainEncoder(muxerWrapper, false)

                        val videoProgress = (outputPtsUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f) * 0.75f
                        onProgress(videoProgress, "Encoding video frames...")
                    }
                } finally {
                    decoder.release()
                }

                currentTimelineOffsetUs += clip.effectiveDurationUs
            }

            if (isCancelled.get()) throw InterruptedException("Export cancelled")

            onProgress(0.75f, "Finalizing video track...")
            videoEncoder.drainEncoder(muxerWrapper, true)

            if (hasAudio && !isCancelled.get()) {
                onProgress(0.78f, "Mixing and encoding audio tracks...")
                val audioProcessor = AudioProcessor(context, project, config)
                audioProcessor.processAudio(muxerWrapper, totalDurationUs) { sub ->
                    val combined = 0.78f + (sub * 0.18f)
                    onProgress(combined, "Encoding audio...")
                }
            }

            onProgress(0.97f, "Closing output MP4 container...")
            muxerWrapper.stopAndRelease()

            onProgress(0.98f, "Saving to gallery...")
            val publicUri = copyToPublicGallery(context, outputFile)
            onProgress(1.0f, "Export Complete!")
            Result.success(ExportResult(outputFile, publicUri))

        } catch (t: Throwable) {
            AppLog.e(TAG, "Export failed: ${t.message}", t)
            muxerWrapper?.stopAndRelease()
            if (outputFile.exists()) outputFile.delete()
            Result.failure(t)
        } finally {
            if (incomingTransitionTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(incomingTransitionTexId), 0)
            }
            lutFilter?.release()
            chromaFilter?.release()
            maskFilter?.release()
            outgoingFbo?.release()
            transitionFilter?.release()
            colorFilter?.release()
            overlayRenderer?.release()
            outputSurface?.release()
            videoEncoder?.release()
        }
    }

    private fun renderImageClip(
        clip: TimelineItem, clipIndex: Int, timelineStartUs: Long,
        inputSurface: com.clipcut.core.gl.InputSurface,
        colorFilter: ColorAdjustFilter,
        overlayRenderer: OverlayRenderer,
        transitionFilter: TransitionFilter?,
        maskFilter: MaskFilter?,
        lutFilter: LutFilter?,
        chromaFilter: ChromaKeyFilter?,
        incomingTransitionTexId: Int,
        muxerWrapper: MediaMuxerWrapper,
        videoEncoder: VideoEncoder,
        totalDurationUs: Long,
        onProgress: (Float, String) -> Unit,
        outgoingFbo: FramebufferTexture?,
        nextNeedsTransition: Boolean
    ) {
        val bitmap = try {
            context.contentResolver.openInputStream(Uri.parse(clip.uri))?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { null } ?: return

        val bitmapW = bitmap.width
        val bitmapH = bitmap.height
        val texId = uploadBitmapToTexture(bitmap)
        bitmap.recycle()
        if (texId == 0) return

        try {
            val durationUs = clip.effectiveDurationUs
            val frameIntervalUs = (1_000_000L / config.frameRate.toFloat()).toLong()
            var framePtsUs = 0L
            val mvpMatrix = FloatArray(16)

            buildFitMvpMatrix(bitmapW, bitmapH, config.customWidth, config.customHeight, mvpMatrix)

            val transitionActive = clip.transitionType != TransitionType.NONE &&
                    clipIndex > 0 && transitionFilter != null && outgoingFbo != null
            val transitionDurationUs = if (transitionActive) clip.transitionDurationUs else 0L

            while (framePtsUs < durationUs && !isCancelled.get()) {
                val outputPtsUs = timelineStartUs + framePtsUs
                GLES20.glViewport(0, 0, config.customWidth, config.customHeight)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                val inTransition = transitionActive && framePtsUs < transitionDurationUs

                if (inTransition) {
                    colorFilter.updateConfig(clip.filterConfig)
                    colorFilter.updateCropFlip(clip.cropRect, clip.flipHorizontal, clip.flipVertical)
                    colorFilter.draw(texId, GlUtil.IDENTITY_MATRIX, mvpMatrix, config.customWidth, config.customHeight, GLES20.GL_TEXTURE_2D)

                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, incomingTransitionTexId)
                    GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, config.customWidth, config.customHeight)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

                    GLES20.glViewport(0, 0, config.customWidth, config.customHeight)
                    GLES20.glClearColor(0f, 0f, 0f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                    transitionFilter!!.setType(clip.transitionType)
                    val progress = (framePtsUs.toFloat() / transitionDurationUs.toFloat()).coerceIn(0f, 1f)
                    transitionFilter.setProgress(progress)
                    transitionFilter.drawTransition(
                        textureOutId = outgoingFbo!!.textureId,
                        textureInId = incomingTransitionTexId,
                        texMatrix = GlUtil.IDENTITY_MATRIX,
                        mvpMatrix = GlUtil.IDENTITY_MATRIX,
                        width = config.customWidth, height = config.customHeight
                    )
                } else {
                    colorFilter.updateConfig(clip.filterConfig)
                    colorFilter.updateCropFlip(clip.cropRect, clip.flipHorizontal, clip.flipVertical)
                    colorFilter.draw(texId, GlUtil.IDENTITY_MATRIX, mvpMatrix, config.customWidth, config.customHeight, GLES20.GL_TEXTURE_2D)
                }

                overlayRenderer.renderOverlays(project.overlays, outputPtsUs, config.customWidth, config.customHeight)

                if (nextNeedsTransition && outgoingFbo != null && (framePtsUs + frameIntervalUs >= durationUs) && !inTransition) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outgoingFbo.textureId)
                    GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, config.customWidth, config.customHeight)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                }

                inputSurface.setPresentationTime(outputPtsUs * 1000L)
                inputSurface.swapBuffers()
                videoEncoder.drainEncoder(muxerWrapper, false)

                onProgress((outputPtsUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f) * 0.75f, "Encoding...")
                framePtsUs += frameIntervalUs
            }
        } finally {
            GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
        }
    }

    private fun uploadBitmapToTexture(bitmap: android.graphics.Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texId
    }

    private fun buildFitMvpMatrix(srcW: Int, srcH: Int, outW: Int, outH: Int, out: FloatArray) {
        Matrix.setIdentityM(out, 0)
        if (srcW <= 0 || srcH <= 0 || outW <= 0 || outH <= 0) return
        val scaleX = outW.toFloat() / srcW.toFloat()
        val scaleY = outH.toFloat() / srcH.toFloat()
        val fit = minOf(scaleX, scaleY)
        Matrix.scaleM(out, 0, (srcW * fit) / outW.toFloat(), (srcH * fit) / outH.toFloat(), 1f)
    }

    private fun copyToPublicGallery(context: Context, sourceFile: File): Uri? {
        return try {
            val cv = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipCut")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

            val itemUri = resolver.insert(collection, cv)
            if (itemUri != null) {
                resolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(sourceFile).use { it.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cv.clear()
                    cv.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(itemUri, cv, null, null)
                }
                itemUri
            } else null
        } catch (e: Exception) { null }
    }

    companion object { private const val TAG = "ExportEngine" }
}
