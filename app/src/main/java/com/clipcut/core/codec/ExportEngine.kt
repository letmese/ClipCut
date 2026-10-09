package com.clipcut.core.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.view.Surface
import com.clipcut.core.gl.FramebufferTexture
import com.clipcut.core.gl.GlUtil
import com.clipcut.core.gl.InputSurface
import com.clipcut.core.gl.OesFilter
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.gl.OverlayRenderer
import com.clipcut.core.gl.PassthroughFilter
import com.clipcut.core.gl.PipelineRenderer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.MediaType
import com.clipcut.core.model.PiPTrackItem
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * ExportResult — returned by ExportEngine.export() on success.
 * publicUri is null if MediaStore insert fails (older devices or sandboxed
 * storage).  galleryPath is the relative path inside Movies/.
 */
data class ExportResult(
    val file: File,
    val publicUri: Uri? = null,
    val galleryPath: String = "Movies/ClipCut"
)

/**
 * ExportEngine — orchestrates the encode pipeline:
 *   1. Build a MediaMuxerWrapper for the output .mp4
 *   2. Set up VideoEncoder (MediaCodec) with InputSurface
 *   3. For each clip: open a VideoDecoder against the InputSurface,
 *      loop frames through PipelineRenderer, swapBuffers
 *   4. Run AudioProcessor on a background thread to mix audio tracks
 *   5. stopAndRelease muxer
 *
 *  cancel() flips a flag that the inner loop polls and aborts.
 *
 *  export() is suspend and uses Dispatchers.IO.
 */
class ExportEngine(
    private val context: Context,
    private val project: Project,
    private val config: ExportConfig
) {
    private val tag = "ExportEngine"
    @Volatile private var cancelled = false
    private var inputSurface: InputSurface? = null
    private var videoEncoder: VideoEncoder? = null
    private var muxer: MediaMuxerWrapper? = null
    private val pipRetrievers = mutableMapOf<String, android.media.MediaMetadataRetriever>()
    private var prevClipFbo: FramebufferTexture? = null
    private var hasPrevClip = false

    private fun getPipRetriever(uri: String): android.media.MediaMetadataRetriever? {
        return pipRetrievers.getOrPut(uri) {
            android.media.MediaMetadataRetriever().apply {
                try {
                    if (uri.startsWith("file://")) {
                        setDataSource(Uri.parse(uri).path)
                    } else if (uri.startsWith("/")) {
                        setDataSource(uri)
                    } else {
                        setDataSource(context, Uri.parse(uri))
                    }
                } catch (t: Throwable) {
                    AppLog.w(tag, "Failed to setDataSource for PiP in ExportEngine: $uri", t)
                }
            }
        }
    }

    private class ExportPipSession(
        val pip: PiPTrackItem,
        val isVideo: Boolean,
        var imageTexId: Int = -1,
        var videoDecoder: VideoDecoder? = null,
        var outputSurface: OutputSurface? = null,
        var oesTexId: Int = -1,
        var lastDecodedPtsUs: Long = -1L,
        val texMatrix: FloatArray = FloatArray(16)
    )

    private fun isVideoUri(uriStr: String): Boolean {
        if (uriStr.endsWith(".png", true) || uriStr.endsWith(".jpg", true) ||
            uriStr.endsWith(".jpeg", true) || uriStr.endsWith(".webp", true)) {
            return false
        }
        if (uriStr.endsWith(".mp4", true) || uriStr.endsWith(".mkv", true) ||
            uriStr.endsWith(".mov", true) || uriStr.endsWith(".webm", true) ||
            uriStr.contains("video", true)) {
            return true
        }
        return try {
            val retr = getPipRetriever(uriStr)
            val hasVideo = retr?.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            val mime = retr?.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            hasVideo || mime.startsWith("video/")
        } catch (_: Throwable) {
            false
        }
    }

    fun cancel() {
        AppLog.w(tag, "cancel requested")
        cancelled = true
    }

    private fun hasAudioTrack(uriStr: String): Boolean {
        return try {
            val ext = android.media.MediaExtractor().apply {
                setDataSource(context, Uri.parse(uriStr), null)
            }
            var found = false
            for (i in 0 until ext.trackCount) {
                val mime = ext.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    found = true
                    break
                }
            }
            ext.release()
            found
        } catch (_: Throwable) {
            false
        }
    }

    private fun detectAudioSampleRate(project: Project): Int? {
        val firstAudioUri = project.videoClips.firstOrNull { it.mediaType == MediaType.VIDEO && it.volume > 0f && hasAudioTrack(it.uri) }?.uri
            ?: project.audioTracks.firstOrNull()?.uri ?: return null
        return try {
            val ext = android.media.MediaExtractor().apply { setDataSource(context, Uri.parse(firstAudioUri), null) }
            var rate: Int? = null
            for (i in 0 until ext.trackCount) {
                val fmt = ext.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        val r = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (r in listOf(44100, 48000, 32000, 24000, 16000)) {
                            rate = r
                        }
                    }
                    break
                }
            }
            ext.release()
            rate
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun export(onProgress: (progress: Float, status: String) -> Unit): Result<ExportResult> = withContext(Dispatchers.IO) {
        var pipeline: PipelineRenderer? = null
        var overlayRenderer: OverlayRenderer? = null
        var passthrough: PassthroughFilter? = null
        var enc: VideoEncoder? = null
        var surface: InputSurface? = null
        var mux: MediaMuxerWrapper? = null
        val pipSessions = mutableMapOf<String, ExportPipSession>()
        var pipOesFilter: OesFilter? = null

        try {
            val videoDurationUs = project.videoClips.sumOf { it.effectiveDurationUs }
            val totalUs = if (videoDurationUs > 0L) videoDurationUs else project.totalDurationUs.coerceAtLeast(1L)
            val outFile = File(context.cacheDir, "clipcut_export_${System.currentTimeMillis()}.mp4")
            if (outFile.exists()) outFile.delete()

            val hasAudio = project.audioTracks.isNotEmpty() || project.videoClips.any { clip ->
                clip.mediaType == MediaType.VIDEO && clip.volume > 0f && hasAudioTrack(clip.uri)
            }

            // Auto-detect native source sample rate (44.1kHz vs 48kHz) to eliminate resampling distortion
            val activeAudioSampleRate = if (hasAudio) {
                detectAudioSampleRate(project) ?: config.audioSampleRate
            } else config.audioSampleRate
            val activeConfig = if (activeAudioSampleRate != config.audioSampleRate) {
                config.copy(audioSampleRate = activeAudioSampleRate)
            } else config

            val expectedTracks = if (hasAudio) 2 else 1
            mux = MediaMuxerWrapper(outFile.absolutePath, expectedTrackCount = expectedTracks)
            muxer = mux

            if (hasAudio) {
                try {
                    val aacFmt = MediaFormat.createAudioFormat("audio/mp4a-latm", activeConfig.audioSampleRate, activeConfig.audioChannels).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, 2)
                        setInteger(MediaFormat.KEY_BIT_RATE, activeConfig.audioBitRate)
                        val csd = getAacCsd(activeConfig.audioSampleRate, activeConfig.audioChannels)
                        setByteBuffer("csd-0", ByteBuffer.wrap(csd))
                    }
                    mux.addAudioTrack(aacFmt)
                } catch (t: Throwable) {
                    AppLog.w(tag, "Pre-configuring audio track in muxer failed", t)
                }
            }

            enc = VideoEncoder(config).also { it.init() }
            videoEncoder = enc
            surface = enc.inputSurface ?: throw IllegalStateException("InputSurface is null")
            inputSurface = surface
            surface.makeCurrent()

            pipeline = PipelineRenderer(context).also { it.init(config.width, config.height) }
            overlayRenderer = OverlayRenderer(context).also { it.init() }
            passthrough = PassthroughFilter().also { it.init() }

            // Pre-initialize PiP sessions (hardware video decoders and cached image textures)
            if (project.pipTracks.isNotEmpty()) {
                val hasVideoPip = project.pipTracks.any { isVideoUri(it.uri) }
                if (hasVideoPip) {
                    try {
                        pipOesFilter = OesFilter().also { it.init() }
                    } catch (t: Throwable) {
                        AppLog.w(tag, "Failed to init pipOesFilter: ${t.message}")
                    }
                }

                for (pip in project.pipTracks) {
                    if (pip.uri.isEmpty()) continue
                    val isVid = isVideoUri(pip.uri)
                    if (isVid) {
                        try {
                            val oesTex = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
                            val outSurface = OutputSurface(oesTex)
                            val dummyClip = TimelineItem(
                                uri = pip.uri,
                                trimStartUs = pip.trimStartUs,
                                trimEndUs = pip.trimEndUs
                            )
                            val dec = VideoDecoder(context, dummyClip, outSurface).also {
                                it.init()
                                if (pip.trimStartUs > 0) it.seekTo(pip.trimStartUs)
                            }
                            pipSessions[pip.id] = ExportPipSession(
                                pip = pip,
                                isVideo = true,
                                oesTexId = oesTex,
                                outputSurface = outSurface,
                                videoDecoder = dec
                            )
                        } catch (t: Throwable) {
                            AppLog.w(tag, "Failed hardware VideoDecoder for PiP ${pip.uri}: ${t.message}")
                        }
                    } else {
                        // Pre-cache image into GL texture once
                        try {
                            val bmp = if (pip.uri.startsWith("content://") || pip.uri.startsWith("file://")) {
                                context.contentResolver.openInputStream(Uri.parse(pip.uri))?.use {
                                    BitmapFactory.decodeStream(it)
                                }
                            } else {
                                BitmapFactory.decodeFile(pip.uri)
                            }
                            if (bmp != null) {
                                val imgTex = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imgTex)
                                android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                                bmp.recycle()
                                pipSessions[pip.id] = ExportPipSession(
                                    pip = pip,
                                    isVideo = false,
                                    imageTexId = imgTex
                                )
                            }
                        } catch (t: Throwable) {
                            AppLog.w(tag, "Failed to pre-cache image PiP: ${pip.uri}", t)
                        }
                    }
                }
            }

            onProgress(0.05f, "Encoder initialised")

            var progressUs = 0L
            var lastPtsNs = -1L
            for (clip in project.videoClips) {
                if (cancelled) return@withContext Result.failure(RuntimeException("Cancelled"))
                lastPtsNs = processClip(
                    clip = clip,
                    pipeline = pipeline,
                    overlayRenderer = overlayRenderer,
                    passthrough = passthrough,
                    pipSessions = pipSessions,
                    pipOesFilter = pipOesFilter,
                    onProgress = onProgress,
                    totalUs = totalUs,
                    progressUs = progressUs,
                    lastPtsNs = lastPtsNs
                )
                progressUs += clip.effectiveDurationUs
            }

            // Drain encoder EoS
            enc.drainEncoder(mux, endOfStream = true)

            // Audio
            if (hasAudio && !cancelled) {
                onProgress(0.92f, "Processing audio")
                val ap = AudioProcessor(context, project, activeConfig)
                ap.processAudio(mux, totalUs) { /* per-clip audio progress */ }
            }

            mux.stopAndRelease()
            enc.release()
            surface.release()
            pipeline.release()
            passthrough.release()

            onProgress(1.00f, "Done")

            // Insert into MediaStore so it appears in Movies/ClipCut
            val saved = saveToGallery(outFile)
            Result.success(saved)
        } catch (t: Throwable) {
            AppLog.e(tag, "export failed", t)
            try { pipeline?.release() } catch (_: Throwable) {}
            try { passthrough?.release() } catch (_: Throwable) {}
            try { enc?.release() } catch (_: Throwable) {}
            try { surface?.release() } catch (_: Throwable) {}
            try { mux?.stopAndRelease() } catch (_: Throwable) {}
            Result.failure(t)
        } finally {
            for (sess in pipSessions.values) {
                try { sess.videoDecoder?.release() } catch (_: Throwable) {}
                try { sess.outputSurface?.release() } catch (_: Throwable) {}
                if (sess.oesTexId != -1) {
                    try { GLES20.glDeleteTextures(1, intArrayOf(sess.oesTexId), 0) } catch (_: Throwable) {}
                }
                if (sess.imageTexId != -1) {
                    try { GLES20.glDeleteTextures(1, intArrayOf(sess.imageTexId), 0) } catch (_: Throwable) {}
                }
            }
            pipSessions.clear()
            try { pipOesFilter?.release() } catch (_: Throwable) {}
            for (r in pipRetrievers.values) {
                try { r.release() } catch (_: Throwable) {}
            }
            pipRetrievers.clear()
            prevClipFbo?.release()
            prevClipFbo = null
            hasPrevClip = false
        }
    }

    private fun processClip(
        clip: TimelineItem,
        pipeline: PipelineRenderer,
        overlayRenderer: OverlayRenderer,
        passthrough: PassthroughFilter,
        pipSessions: Map<String, ExportPipSession>,
        pipOesFilter: OesFilter?,
        onProgress: (Float, String) -> Unit,
        totalUs: Long,
        progressUs: Long,
        lastPtsNs: Long
    ): Long {
        if (clip.mediaType != MediaType.VIDEO && clip.mediaType != MediaType.IMAGE) return lastPtsNs
        val surface = inputSurface ?: return lastPtsNs
        val enc = videoEncoder ?: return lastPtsNs
        val mux = muxer ?: return lastPtsNs

        var currentLastPtsNs = lastPtsNs

        if (clip.mediaType == MediaType.VIDEO && clip.isReversed) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                if (clip.uri.startsWith("content://") || clip.uri.startsWith("file://")) {
                    retriever.setDataSource(context, Uri.parse(clip.uri))
                } else {
                    retriever.setDataSource(clip.uri)
                }
                val trimStart = clip.trimStartUs.coerceAtLeast(0L)
                val trimEnd = if (clip.trimEndUs > trimStart) clip.trimEndUs else (clip.sourceDurationUs.takeIf { it > 0 } ?: 5_000_000L)
                val effectiveDuration = ((trimEnd - trimStart) / clip.speed.coerceAtLeast(0.1f)).toLong()
                val totalFrames = ((effectiveDuration * config.frameRate) / 1_000_000L).toInt().coerceAtLeast(1)
                val frameStepUs = if (totalFrames > 1) (trimEnd - trimStart) / totalFrames else (trimEnd - trimStart)

                val revTexId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
                for (i in 0 until totalFrames) {
                    if (cancelled) break
                    val currentSourcePtsUs = trimEnd - (i * frameStepUs)
                    val frameBmp = retriever.getFrameAtTime(currentSourcePtsUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST) ?: continue

                    try {
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, revTexId)
                        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frameBmp, 0)
                    } finally {
                        frameBmp.recycle()
                    }

                    val timelineOffsetUs = (i * 1_000_000L / config.frameRate)
                    val timelinePtsUs = (progressUs + timelineOffsetUs).coerceAtMost(totalUs)

                    if (i % 8 == 0) {
                        val p = 0.05f + 0.85f * (timelinePtsUs.toFloat() / totalUs.toFloat()).coerceIn(0f, 1f)
                        val pct = (p * 100).toInt()
                        onProgress(p, "Exporting ($pct%)")
                    }

                    val transProgress = if (clip.transitionType != com.clipcut.core.model.TransitionType.NONE && clip.transitionDurationUs > 0) {
                        (timelineOffsetUs.toFloat() / clip.transitionDurationUs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    pipeline.applyPostProcessing(
                        sourceTexId = revTexId,
                        sourceTexMatrix = GlUtil.IDENTITY_MATRIX,
                        sourceIsOes = false,
                        mvpMatrix = GlUtil.IDENTITY_MATRIX,
                        rotationDegrees = clip.rotationDegrees,
                        clip = clip,
                        transitionProgress = transProgress,
                        prevClipTexId = if (hasPrevClip) prevClipFbo?.textureId else null,
                        activeBlurs = project.blurTracks,
                        timelinePtsUs = timelinePtsUs
                    )

                    renderPiPAndOverlays(timelinePtsUs, passthrough, overlayRenderer, pipSessions, pipOesFilter)

                    val ptsNs = timelinePtsUs * 1000L
                    currentLastPtsNs = if (ptsNs > currentLastPtsNs) ptsNs else currentLastPtsNs + 1_000L
                    surface.setPresentationTime(currentLastPtsNs)
                    surface.swapBuffers()

                    enc.drainEncoder(mux, endOfStream = false)
                }
                GLES20.glDeleteTextures(1, intArrayOf(revTexId), 0)
            } catch (t: Throwable) {
                AppLog.w(tag, "Reverse processing failed: ${t.message}", t)
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }
        } else if (clip.mediaType == MediaType.VIDEO) {
            val oesTexId = GlUtil.generateTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            val outputSurface = OutputSurface(oesTexId)
            val dec = VideoDecoder(context, clip, outputSurface = outputSurface).also { it.init() }

            val trimStart = clip.trimStartUs.coerceAtLeast(0L)
            val trimEnd = if (clip.trimEndUs > trimStart) clip.trimEndUs else (if (dec.durationUs > 0) dec.durationUs else Long.MAX_VALUE)
            if (trimStart > 0) {
                dec.seekTo(trimStart)
            }

            val texMatrix = FloatArray(16)
            var frameCount = 0

            while (true) {
                if (cancelled) break
                val pts = dec.decodeNextFrame(timeoutUs = 2_500L)
                if (pts < 0) break
                if (!outputSurface.awaitNewImage(timeoutMs = 200)) continue

                if (pts < trimStart) continue
                if (pts > trimEnd) break

                val sourceOffsetUs = (pts - trimStart).coerceAtLeast(0L)
                val timelineOffsetUs = SpeedRampProcessor.sourceToTimeline(sourceOffsetUs, clip.speedRamp, clip.speed)
                if (clip.effectiveDurationUs > 0L && timelineOffsetUs > clip.effectiveDurationUs + 50_000L) break
                val timelinePtsUs = (progressUs + timelineOffsetUs).coerceAtMost(totalUs)

                frameCount++
                if (frameCount % 8 == 0) {
                    val p = 0.05f + 0.85f * (timelinePtsUs.toFloat() / totalUs.toFloat()).coerceIn(0f, 1f)
                    val pct = (p * 100).toInt()
                    onProgress(p, "Exporting ($pct%)")
                }

                outputSurface.getTransformMatrix(texMatrix)

                val transProgress = if (clip.transitionType != com.clipcut.core.model.TransitionType.NONE && clip.transitionDurationUs > 0) {
                    (timelineOffsetUs.toFloat() / clip.transitionDurationUs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                pipeline.applyPostProcessing(
                    sourceTexId = oesTexId,
                    sourceTexMatrix = texMatrix,
                    sourceIsOes = true,
                    mvpMatrix = GlUtil.IDENTITY_MATRIX,
                    rotationDegrees = clip.rotationDegrees,
                    clip = clip,
                    transitionProgress = transProgress,
                    prevClipTexId = if (hasPrevClip) prevClipFbo?.textureId else null,
                    activeBlurs = project.blurTracks,
                    timelinePtsUs = timelinePtsUs
                )

                renderPiPAndOverlays(timelinePtsUs, passthrough, overlayRenderer, pipSessions, pipOesFilter)

                val ptsNs = timelinePtsUs * 1000L
                currentLastPtsNs = if (ptsNs > currentLastPtsNs) ptsNs else currentLastPtsNs + 1_000L
                surface.setPresentationTime(currentLastPtsNs)
                surface.swapBuffers()

                enc.drainEncoder(mux, endOfStream = false)
            }

            dec.release()
            outputSurface.release()
            GLES20.glDeleteTextures(1, intArrayOf(oesTexId), 0)
        } else if (clip.mediaType == MediaType.IMAGE) {
            val bmp = try {
                if (clip.uri.startsWith("content://") || clip.uri.startsWith("file://")) {
                    context.contentResolver.openInputStream(Uri.parse(clip.uri))?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                } else {
                    android.graphics.BitmapFactory.decodeFile(clip.uri)
                }
            } catch (t: Throwable) {
                AppLog.w(tag, "failed to decode image for export: ${clip.uri}", t)
                null
            }

            if (bmp != null) {
                val imgTexId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imgTexId)
                android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                bmp.recycle()

                val totalFrames = (clip.effectiveDurationUs * config.frameRate / 1_000_000L).toInt().coerceAtLeast(1)
                for (i in 0 until totalFrames) {
                    if (cancelled) break
                    val timelineOffsetUs = (i * 1_000_000L / config.frameRate)
                    val timelinePtsUs = (progressUs + timelineOffsetUs).coerceAtMost(totalUs)

                    if (i % 8 == 0) {
                        val p = 0.05f + 0.85f * (timelinePtsUs.toFloat() / totalUs.toFloat()).coerceIn(0f, 1f)
                        val pct = (p * 100).toInt()
                        onProgress(p, "Exporting ($pct%)")
                    }

                    val transProgress = if (clip.transitionType != com.clipcut.core.model.TransitionType.NONE && clip.transitionDurationUs > 0) {
                        (timelineOffsetUs.toFloat() / clip.transitionDurationUs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    pipeline.applyPostProcessing(
                        sourceTexId = imgTexId,
                        sourceTexMatrix = GlUtil.IDENTITY_MATRIX,
                        sourceIsOes = false,
                        mvpMatrix = GlUtil.IDENTITY_MATRIX,
                        rotationDegrees = clip.rotationDegrees,
                        clip = clip,
                        transitionProgress = transProgress,
                        prevClipTexId = if (hasPrevClip) prevClipFbo?.textureId else null,
                        activeBlurs = project.blurTracks,
                        timelinePtsUs = timelinePtsUs
                    )

                    renderPiPAndOverlays(timelinePtsUs, passthrough, overlayRenderer, pipSessions, pipOesFilter)

                    val ptsNs = timelinePtsUs * 1000L
                    currentLastPtsNs = if (ptsNs > currentLastPtsNs) ptsNs else currentLastPtsNs + 1_000L
                    surface.setPresentationTime(currentLastPtsNs)
                    surface.swapBuffers()

                    enc.drainEncoder(mux, endOfStream = false)
                }
                GLES20.glDeleteTextures(1, intArrayOf(imgTexId), 0)
            }
        }

        if (pipeline.lastDecoratedTexId > 0) {
            try {
                if (prevClipFbo == null) {
                    prevClipFbo = FramebufferTexture(config.width, config.height).also { it.init() }
                }
                prevClipFbo?.bind()
                GLES20.glViewport(0, 0, config.width, config.height)
                passthrough.draw(pipeline.lastDecoratedTexId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, config.width, config.height)
                prevClipFbo?.unbind()
                hasPrevClip = true
            } catch (t: Throwable) {
                AppLog.w(tag, "Saving decorated transition frame failed", t)
                hasPrevClip = false
            }
        } else {
            hasPrevClip = false
        }

        return currentLastPtsNs
    }

    private fun renderPiPAndOverlays(
        timelinePtsUs: Long,
        passthrough: PassthroughFilter,
        overlayRenderer: OverlayRenderer,
        pipSessions: Map<String, ExportPipSession>,
        pipOesFilter: OesFilter?
    ) {
        // 1. PiP layers
        for (pip in project.pipTracks) {
            if (timelinePtsUs in pip.timelineStartUs..pip.timelineEndUs && pip.uri.isNotEmpty()) {
                try {
                    val pipMvp = FloatArray(16)
                    android.opengl.Matrix.setIdentityM(pipMvp, 0)
                    val tx = (pip.normalizedX * 2f) - 1f
                    val ty = 1f - (pip.normalizedY * 2f)
                    android.opengl.Matrix.translateM(pipMvp, 0, tx, ty, 0f)
                    if (pip.rotation != 0f) {
                        android.opengl.Matrix.rotateM(pipMvp, 0, -pip.rotation, 0f, 0f, 1f)
                    }
                    android.opengl.Matrix.scaleM(pipMvp, 0, pip.scale, pip.scale, 1f)

                    val session = pipSessions[pip.id]
                    if (session != null) {
                        if (session.isVideo && session.videoDecoder != null && session.outputSurface != null && pipOesFilter != null) {
                            val relTimeUs = (timelinePtsUs - pip.timelineStartUs) + pip.trimStartUs
                            val dec = session.videoDecoder!!
                            val outSurf = session.outputSurface!!
                            while (session.lastDecodedPtsUs < relTimeUs) {
                                val pts = dec.decodeNextFrame(timeoutUs = 2_500L)
                                if (pts < 0) break
                                if (outSurf.awaitNewImage(timeoutMs = 50)) {
                                    session.lastDecodedPtsUs = pts
                                    outSurf.getTransformMatrix(session.texMatrix)
                                }
                            }
                            GLES20.glEnable(GLES20.GL_BLEND)
                            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                            pipOesFilter.draw(session.oesTexId, session.texMatrix, pipMvp, config.width, config.height, GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
                            GLES20.glDisable(GLES20.GL_BLEND)
                        } else if (!session.isVideo && session.imageTexId != -1) {
                            GLES20.glEnable(GLES20.GL_BLEND)
                            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                            passthrough.draw(session.imageTexId, GlUtil.FLIP_Y_MATRIX, pipMvp, config.width, config.height)
                            GLES20.glDisable(GLES20.GL_BLEND)
                        }
                    } else {
                        // Safe fallback for rare video formats using cached retriever
                        val retr = getPipRetriever(pip.uri)
                        val relTime = (timelinePtsUs - pip.timelineStartUs) + pip.trimStartUs
                        val frameBmp = try {
                            retr?.getFrameAtTime(relTime, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } catch (_: Throwable) { null }
                        if (frameBmp != null) {
                            val fallbackTexId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fallbackTexId)
                            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frameBmp, 0)
                            frameBmp.recycle()
                            GLES20.glEnable(GLES20.GL_BLEND)
                            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                            passthrough.draw(fallbackTexId, GlUtil.FLIP_Y_MATRIX, pipMvp, config.width, config.height)
                            GLES20.glDisable(GLES20.GL_BLEND)
                            GLES20.glDeleteTextures(1, intArrayOf(fallbackTexId), 0)
                        }
                    }
                } catch (t: Throwable) {
                    AppLog.w(tag, "PiP frame draw error: ${t.message}")
                }
            }
        }

        // 2. Overlays
        val activeOverlays = project.overlays.filter { timelinePtsUs in it.timelineStartUs..it.timelineEndUs }
        if (activeOverlays.isNotEmpty()) {
            overlayRenderer.renderOverlays(project.overlays, timelinePtsUs, config.width, config.height)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            passthrough.draw(overlayRenderer.textureId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, config.width, config.height)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun saveToGallery(src: File): ExportResult {
        val resolver = context.contentResolver
        val moviesPath = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val name = "ClipCut_${System.currentTimeMillis()}.mp4"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/ClipCut")
                put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "ClipCut")
                if (!dir.exists()) dir.mkdirs()
                val legacyFile = File(dir, name)
                put(android.provider.MediaStore.Video.Media.DATA, legacyFile.absolutePath)
            }
        }
        var pendingUri: Uri? = null
        return try {
            val uri = resolver.insert(moviesPath, values)
            pendingUri = uri
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                if (Build.VERSION.SDK_INT >= 29) {
                    values.clear(); values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            ExportResult(file = src, publicUri = uri, galleryPath = "Movies/ClipCut/$name")
        } catch (t: Throwable) {
            AppLog.w(tag, "MediaStore insert failed, file remains in cache", t)
            if (pendingUri != null) {
                try { resolver.delete(pendingUri, null, null) } catch (_: Throwable) {}
            }
            ExportResult(file = src, publicUri = null)
        }
    }

    companion object {
        fun getAacCsd(sampleRate: Int, channelCount: Int): ByteArray {
            val sampleRateIndex = when (sampleRate) {
                96000 -> 0
                88200 -> 1
                64000 -> 2
                48000 -> 3
                44100 -> 4
                32000 -> 5
                24000 -> 6
                22050 -> 7
                16000 -> 8
                12000 -> 9
                11025 -> 10
                8000 -> 11
                7350 -> 12
                else -> 4
            }
            val audioObjectType = 2 // AAC-LC
            val byte1 = (audioObjectType shl 3) or (sampleRateIndex shr 1)
            val byte2 = ((sampleRateIndex and 1) shl 7) or (channelCount shl 3)
            return byteArrayOf(byte1.toByte(), byte2.toByte())
        }
    }
}

/**
 * MediaMuxerWrapper — wraps android.media.MediaMuxer with a thread-safe
 * start-when-ready policy. Waits for BOTH video and audio tracks (when expected)
 * before calling muxer.start(). Samples arriving prior to muxer.start() are
 * buffered in pendingSamples and flushed immediately upon start.
 */
class MediaMuxerWrapper(outputPath: String, private val expectedTrackCount: Int = 2) {
    private val muxer: MediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var started = false
    private var trackCount = 0
    private val tracks = mutableMapOf<Int, Int>() // 0: Video, 1: Audio
    private val lock = Any()

    private class PendingSample(val trackKey: Int, val buffer: ByteBuffer, val info: MediaCodec.BufferInfo)
    private val pendingSamples = mutableListOf<PendingSample>()

    fun addVideoTrack(format: MediaFormat): Int = synchronized(lock) {
        val idx = muxer.addTrack(format)
        tracks[0] = idx
        trackCount++
        checkStartMuxerLocked()
        return idx
    }

    fun addAudioTrack(format: MediaFormat): Int = synchronized(lock) {
        val existing = tracks[1]
        if (existing != null) return existing
        val idx = muxer.addTrack(format)
        tracks[1] = idx
        trackCount++
        checkStartMuxerLocked()
        return idx
    }

    fun checkStartMuxerLocked() {
        if (!started && trackCount >= expectedTrackCount) {
            muxer.start()
            started = true
            flushPendingSamplesLocked()
        }
    }

    private fun flushPendingSamplesLocked() {
        for (sample in pendingSamples) {
            val trackIdx = tracks[sample.trackKey] ?: continue
            muxer.writeSampleData(trackIdx, sample.buffer, sample.info)
        }
        pendingSamples.clear()
    }

    fun writeVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (started) {
                val trackIdx = tracks[0] ?: return@synchronized
                muxer.writeSampleData(trackIdx, buffer, bufferInfo)
            } else {
                if (pendingSamples.size > 300) {
                    pendingSamples.removeAt(0)
                }
                val copy = ByteBuffer.allocateDirect(bufferInfo.size)
                buffer.position(bufferInfo.offset)
                buffer.limit(bufferInfo.offset + bufferInfo.size)
                copy.put(buffer)
                copy.flip()
                val infoCopy = MediaCodec.BufferInfo().apply {
                    set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                }
                pendingSamples.add(PendingSample(0, copy, infoCopy))
            }
        }
    }

    fun writeAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (started) {
                val trackIdx = tracks[1] ?: return@synchronized
                muxer.writeSampleData(trackIdx, buffer, bufferInfo)
            } else {
                if (pendingSamples.size > 300) {
                    pendingSamples.removeAt(0)
                }
                val copy = ByteBuffer.allocateDirect(bufferInfo.size)
                buffer.position(bufferInfo.offset)
                buffer.limit(bufferInfo.offset + bufferInfo.size)
                copy.put(buffer)
                copy.flip()
                val infoCopy = MediaCodec.BufferInfo().apply {
                    set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                }
                pendingSamples.add(PendingSample(1, copy, infoCopy))
            }
        }
    }

    fun stopAndRelease() {
        synchronized(lock) {
            if (!started && trackCount > 0) {
                try {
                    muxer.start()
                    started = true
                    flushPendingSamplesLocked()
                } catch (t: Throwable) {
                    AppLog.w("MediaMuxerWrapper", "forced muxer.start() failed", t)
                }
            }
            if (started) {
                try { muxer.stop() } catch (t: Throwable) {
                    AppLog.w("MediaMuxerWrapper", "muxer.stop() failed", t)
                }
            }
            try { muxer.release() } catch (t: Throwable) {
                AppLog.w("MediaMuxerWrapper", "muxer.release() failed", t)
            }
        }
    }
}
