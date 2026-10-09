package com.clipcut.core.player

import android.content.Context
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.clipcut.core.gl.EglCore
import com.clipcut.core.gl.GlFilter
import com.clipcut.core.gl.GlUtil
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.gl.OverlayRenderer
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.Matrix
import com.clipcut.core.gl.PassthroughFilter
import com.clipcut.core.gl.FramebufferTexture
import com.clipcut.core.gl.OesFilter
import com.clipcut.core.gl.PipelineRenderer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import com.clipcut.core.model.TransitionType

class GlPreviewRenderer(private val context: Context) {

    private var pipeline: PipelineRenderer? = null
    @Volatile private var project: Project? = null
    private val pipRetrievers = mutableMapOf<String, MediaMetadataRetriever>()
    @Volatile private var viewportWidth = 0
    @Volatile private var viewportHeight = 0
    @Volatile private var paused = false
    @Volatile private var requestResize = false
    @Volatile private var resizeW = 0
    @Volatile private var resizeH = 0

    var positionProvider: (() -> Long)? = null
    var playerManager: PreviewPlayerManager? = null

    private var eglCore: EglCore? = null
    private var eglSurface: EGLSurface? = null
    private var outputSurface: OutputSurface? = null
    private var overlayRenderer: OverlayRenderer? = null
    private var overlayFilter: GlFilter? = null
    private var oesTextureId = -1

    private var pipOesTextureId = -1
    private var pipOutputSurface: OutputSurface? = null
    private var pipOesFilter: OesFilter? = null
    private val pipTexMatrix = FloatArray(16)
    @Volatile private var hasReceivedPipFrame = false
    @Volatile private var currentPipVideoUri: String? = null

    private val texMatrix = FloatArray(16)
    @Volatile private var isRunning = false
    @Volatile var isDirty = true
    @Volatile private var hasReceivedFirstFrame = false

    private var prevClipFbo: FramebufferTexture? = null
    private var prevClipFboWidth = 0
    private var prevClipFboHeight = 0
    @Volatile private var hasPrevClip = false
    @Volatile var activeEditingOverlayId: String? = null
    @Volatile var activeEditingPipId: String? = null
    @Volatile var activeEditingPipX: Float? = null
    @Volatile var activeEditingPipY: Float? = null
    @Volatile var activeEditingPipScale: Float? = null
    @Volatile var activeEditingPipRotation: Float? = null

    @Volatile var activeEditingBlurId: String? = null
    @Volatile var activeEditingBlurX: Float? = null
    @Volatile var activeEditingBlurY: Float? = null
    @Volatile var activeEditingBlurWidth: Float? = null
    @Volatile var activeEditingBlurHeight: Float? = null
    @Volatile var activeEditingBlurRotation: Float? = null

    fun setEditingBlurTransform(id: String?, x: Float?, y: Float?, w: Float?, h: Float?, rotation: Float?) {
        activeEditingBlurId = id
        activeEditingBlurX = x
        activeEditingBlurY = y
        activeEditingBlurWidth = w
        activeEditingBlurHeight = h
        activeEditingBlurRotation = rotation
        isDirty = true
    }

    fun setEditingPipTransform(id: String?, x: Float?, y: Float?, scale: Float?, rotation: Float?) {
        activeEditingPipId = id
        activeEditingPipX = x
        activeEditingPipY = y
        activeEditingPipScale = scale
        activeEditingPipRotation = rotation
        isDirty = true
    }

    fun requestRender() { isDirty = true }

    private val threadLock = Any()
    private var renderThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
        synchronized(threadLock) {
            stopRenderThreadLocked()
            this.viewportWidth = width
            this.viewportHeight = height
            hasReceivedFirstFrame = false
            isDirty = true
            isRunning = true
            renderThread = Thread({ runRenderLoop(surface, width, height) }, "GlPreviewRenderThread")
                .also { it.start() }
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        viewportWidth = width
        viewportHeight = height
        resizeW = width; resizeH = height; requestResize = true
        isDirty = true
    }

    fun onSurfaceDestroyed() {
        synchronized(threadLock) { stopRenderThreadLocked() }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            playerManager?.setVideoSurface(null)
            playerManager?.setPipVideoSurface(null)
        } else {
            mainHandler.post {
                playerManager?.setVideoSurface(null)
                playerManager?.setPipVideoSurface(null)
            }
        }
    }

    private fun stopRenderThreadLocked() {
        val t = renderThread ?: return
        isRunning = false
        try { t.join(1500) } catch (_: InterruptedException) {}
        if (t.isAlive) {
            AppLog.w(TAG, "render thread did not exit in 1500ms")
        }
        renderThread = null
    }

    private fun runRenderLoop(windowSurface: Surface, initialW: Int, initialH: Int) {
        var core: EglCore? = null
        var eSurface: EGLSurface? = null
        var outSurface: OutputSurface? = null
        var pipe: PipelineRenderer? = null
        var over: OverlayRenderer? = null
        var filter: GlFilter? = null
        var oesTex = -1

        var pipOesTex = -1
        var pipOutSurface: OutputSurface? = null
        var pipFilter: OesFilter? = null

        try {
            core = EglCore(null, EglCore.FLAG_RECORDABLE)
            eglCore = core
            eSurface = core.createWindowSurface(windowSurface)
            eglSurface = eSurface
            core.makeCurrent(eSurface)

            oesTex = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            oesTextureId = oesTex
            outSurface = OutputSurface(oesTex)
            outputSurface = outSurface
            outSurface.onFrameAvailableCallback = { isDirty = true }

            val os = outSurface.surface
            mainHandler.post { playerManager?.setVideoSurface(os) }

            // Dedicated hardware OES texture & Surface for PiP video playback
            pipOesTex = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            pipOesTextureId = pipOesTex
            pipOutSurface = OutputSurface(pipOesTex)
            pipOutputSurface = pipOutSurface
            pipOutSurface.onFrameAvailableCallback = { isDirty = true }

            val pOs = pipOutSurface.surface
            mainHandler.post { playerManager?.setPipVideoSurface(pOs) }

            pipe = PipelineRenderer(context).also { it.init(initialW, initialH) }
            pipeline = pipe

            over = OverlayRenderer(context).also { it.init() }
            overlayRenderer = over

            filter = PassthroughFilter().also { it.init() }
            overlayFilter = filter

            pipFilter = OesFilter().also { it.init() }
            pipOesFilter = pipFilter

            while (isRunning) {
                if (requestResize) {
                    requestResize = false
                    pipe.resizeIfNeeded(resizeW, resizeH)
                }
                if (!paused || isDirty) {
                    renderOnce(core, eSurface, outSurface, pipe, over, filter, oesTex, pipOutSurface, pipFilter, pipOesTex)
                } else {
                    Thread.sleep(16)
                }
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "Render thread fatal: ${t.message}", t)
        } finally {
            mainHandler.post {
                playerManager?.setVideoSurface(null)
                playerManager?.setPipVideoSurface(null)
            }
            try { filter?.release() } catch (_: Throwable) {}
            try { pipFilter?.release() } catch (_: Throwable) {}
            try { over?.release() } catch (_: Throwable) {}
            try { pipe?.release() } catch (_: Throwable) {}
            try { outSurface?.release() } catch (_: Throwable) {}
            try { pipOutSurface?.release() } catch (_: Throwable) {}
            try { prevClipFbo?.release() } catch (_: Throwable) {}
            prevClipFbo = null
            hasPrevClip = false
            if (oesTex != -1) {
                try { GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0) } catch (_: Throwable) {}
            }
            if (pipOesTex != -1) {
                try { GLES20.glDeleteTextures(1, intArrayOf(pipOesTex), 0) } catch (_: Throwable) {}
            }
            try { core?.makeNothingCurrent() } catch (_: Throwable) {}
            try { core?.destroySurface(eSurface) } catch (_: Throwable) {}
            try { core?.release() } catch (_: Throwable) {}
            eglCore = null; eglSurface = null; outputSurface = null
            pipOutputSurface = null; pipOesFilter = null; pipOesTextureId = -1
            pipeline = null; overlayRenderer = null; overlayFilter = null
            oesTextureId = -1
            hasReceivedPipFrame = false
        }
    }

    private class PipFrameCache(
        val pipId: String,
        var uri: String,
        @Volatile var timestampBucket: Long = -1L,
        @Volatile var textureId: Int = -1,
        @Volatile var hasValidTexture: Boolean = false,
        @Volatile var pendingBitmap: android.graphics.Bitmap? = null,
        @Volatile var isDecoding: Boolean = false
    )
    private val pipCaches = java.util.concurrent.ConcurrentHashMap<String, PipFrameCache>()
    private val pipDecodeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private class PipMediaMeta(
        val isVideo: Boolean,
        val scaledWidth: Int,
        val scaledHeight: Int
    )
    private val pipMediaMetaMap = java.util.concurrent.ConcurrentHashMap<String, PipMediaMeta>()

    private fun getPipMediaMeta(uriStr: String): PipMediaMeta {
        return pipMediaMetaMap.getOrPut(uriStr) {
            try {
                val retr = getPipRetriever(uriStr)
                val hasVideo = retr?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
                val mime = retr?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
                val isVid = hasVideo || mime.startsWith("video/") || uriStr.contains("video", ignoreCase = true) ||
                        uriStr.endsWith(".mp4", ignoreCase = true) || uriStr.endsWith(".mkv", ignoreCase = true) ||
                        uriStr.endsWith(".mov", ignoreCase = true) || uriStr.endsWith(".webm", ignoreCase = true)

                if (isVid && retr != null) {
                    val origW = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
                    val origH = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
                    val rot = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val (rawW, rawH) = if (rot == 90 || rot == 270) Pair(origH, origW) else Pair(origW, origH)
                    val maxDim = maxOf(rawW, rawH).coerceAtLeast(1)
                    val targetDim = 480
                    val scaleFactor = if (maxDim > targetDim) targetDim.toFloat() / maxDim else 1f
                    val dstW = ((rawW * scaleFactor).toInt() / 2 * 2).coerceAtLeast(16)
                    val dstH = ((rawH * scaleFactor).toInt() / 2 * 2).coerceAtLeast(16)
                    PipMediaMeta(isVideo = true, scaledWidth = dstW, scaledHeight = dstH)
                } else {
                    PipMediaMeta(isVideo = false, scaledWidth = 480, scaledHeight = 480)
                }
            } catch (_: Throwable) {
                PipMediaMeta(isVideo = false, scaledWidth = 480, scaledHeight = 480)
            }
        }
    }

    private fun renderOnce(
        core: EglCore, eSurface: EGLSurface, out: OutputSurface,
        pipe: PipelineRenderer, over: OverlayRenderer, filter: GlFilter, oesTex: Int,
        pipOut: OutputSurface?, pipFilter: OesFilter?, pipOesTex: Int
    ) {
        val newFrame = out.awaitNewImage(10)
        if (newFrame) hasReceivedFirstFrame = true

        val newPipFrame = pipOut?.awaitNewImage(2) ?: false
        if (newPipFrame) {
            hasReceivedPipFrame = true
            pipOut?.getTransformMatrix(pipTexMatrix)
        }

        if (!newFrame && !newPipFrame && !isDirty) { Thread.sleep(8); return }
        isDirty = false

        val w = viewportWidth.coerceAtLeast(1)
        val h = viewportHeight.coerceAtLeast(1)

        if (!hasReceivedFirstFrame) {
            GLES20.glViewport(0, 0, w, h)
            GLES20.glClearColor(0.08f, 0.08f, 0.08f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            core.swapBuffers(eSurface)
            Thread.sleep(16)
            return
        }

        out.getTransformMatrix(texMatrix)
        val posUs = positionProvider?.invoke() ?: 0L
        val (clip, clipStartUs) = findClipAndOffsetAt(posUs)

        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (clip != null) {
            val offsetUs = (posUs - clipStartUs).coerceAtLeast(0L)
            val transProgress = if (clip.transitionType != TransitionType.NONE && clip.transitionDurationUs > 0) {
                (offsetUs.toFloat() / clip.transitionDurationUs.toFloat()).coerceIn(0f, 1f)
            } else 1f

            val useTransition = (hasPrevClip && prevClipFbo != null && clip.transitionType != TransitionType.NONE && transProgress in 0f..0.999f)

            pipe.applyPostProcessing(
                sourceTexId = oesTex,
                sourceTexMatrix = texMatrix,
                sourceIsOes = true,
                mvpMatrix = GlUtil.IDENTITY_MATRIX,
                rotationDegrees = clip.rotationDegrees,
                clip = clip,
                transitionProgress = if (useTransition) transProgress else 0f,
                prevClipTexId = if (useTransition) prevClipFbo?.textureId else null,
                activeBlurs = project?.blurTracks ?: emptyList(),
                timelinePtsUs = posUs,
                editingBlurId = activeEditingBlurId,
                editingBlurX = activeEditingBlurX,
                editingBlurY = activeEditingBlurY,
                editingBlurW = activeEditingBlurWidth,
                editingBlurH = activeEditingBlurHeight,
                editingBlurRot = activeEditingBlurRotation
            )

            // When not actively transitioning, capture this decorated frame into prevClipFbo
            // so when playback moves to the next clip, prevClipFbo has the last frame of this clip
            if (!useTransition && pipe.lastDecoratedTexId > 0) {
                try {
                    if (prevClipFbo == null || prevClipFboWidth != w || prevClipFboHeight != h) {
                        prevClipFbo?.release()
                        prevClipFbo = FramebufferTexture(w, h).also { it.init() }
                        prevClipFboWidth = w
                        prevClipFboHeight = h
                    }
                    prevClipFbo?.bind()
                    GLES20.glViewport(0, 0, w, h)
                    filter.draw(pipe.lastDecoratedTexId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, w, h)
                    prevClipFbo?.unbind()
                    hasPrevClip = true
                } catch (t: Throwable) {
                    AppLog.w(TAG, "Failed updating prevClipFbo: ${t.message}")
                }
            }
        }

        val proj = project
        if (proj != null) {
            val activeExoPip = proj.pipTracks.firstOrNull { pip ->
                pip.uri.isNotEmpty() && (
                    pip.uri.contains("video", ignoreCase = true) ||
                    pip.uri.endsWith(".mp4", ignoreCase = true) ||
                    pip.uri.endsWith(".mkv", ignoreCase = true) ||
                    pip.uri.endsWith(".mov", ignoreCase = true) ||
                    pip.uri.endsWith(".webm", ignoreCase = true) ||
                    (pip.uri.startsWith("content://") && !pip.uri.contains("images", ignoreCase = true) && !pip.uri.endsWith(".png", true) && !pip.uri.endsWith(".jpg", true))
                )
            }
            if (activeExoPip?.uri != currentPipVideoUri) {
                currentPipVideoUri = activeExoPip?.uri
                hasReceivedPipFrame = false
            }

            // Render PiP Layers
            for (pip in proj.pipTracks) {
                if (posUs in pip.timelineStartUs..pip.timelineEndUs && pip.uri.isNotEmpty()) {
                    try {
                        val meta = getPipMediaMeta(pip.uri)
                        val isVideo = meta.isVideo
                        val isEditingThisPip = (pip.id == activeEditingPipId && activeEditingPipX != null)
                        val normX = if (isEditingThisPip) activeEditingPipX!! else pip.normalizedX
                        val normY = if (isEditingThisPip) (activeEditingPipY ?: pip.normalizedY) else pip.normalizedY
                        val scale = if (isEditingThisPip) (activeEditingPipScale ?: pip.scale) else pip.scale
                        val rot = if (isEditingThisPip) (activeEditingPipRotation ?: pip.rotation) else pip.rotation

                        val pipMvp = FloatArray(16)
                        Matrix.setIdentityM(pipMvp, 0)
                        val tx = (normX * 2f) - 1f
                        val ty = 1f - (normY * 2f)
                        Matrix.translateM(pipMvp, 0, tx, ty, 0f)
                        if (rot != 0f) {
                            Matrix.rotateM(pipMvp, 0, -rot, 0f, 0f, 1f)
                        }
                        Matrix.scaleM(pipMvp, 0, scale, scale, 1f)

                        val isExoVideoPip = (pip.id == activeExoPip?.id && hasReceivedPipFrame && pipOesTex != -1 && pipFilter != null)

                        if (isExoVideoPip) {
                            // Hardware-accelerated 60 FPS playback via OES texture direct from ExoPlayer MediaCodec
                            GLES20.glEnable(GLES20.GL_BLEND)
                            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                            pipFilter!!.draw(pipOesTex, pipTexMatrix, pipMvp, w, h, GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
                            GLES20.glDisable(GLES20.GL_BLEND)
                        } else {
                            // Fallback: 2D texture cache for image overlays/stickers or initial poster frame
                            val relTime = (posUs - pip.timelineStartUs) + pip.trimStartUs
                            val bucket = if (isVideo) (relTime / 125_000L).coerceAtLeast(0L) else 0L

                            var cache = pipCaches.getOrPut(pip.id) { PipFrameCache(pip.id, pip.uri) }
                            if (cache.uri != pip.uri) {
                                if (cache.textureId != -1) {
                                    try { GLES20.glDeleteTextures(1, intArrayOf(cache.textureId), 0) } catch (_: Throwable) {}
                                }
                                cache.pendingBitmap?.recycle()
                                cache.pendingBitmap = null
                                cache = PipFrameCache(pip.id, pip.uri)
                                pipCaches[pip.id] = cache
                            }

                            // Check if a newly decoded bitmap is ready to upload to OpenGL texture
                            val readyBmp = cache.pendingBitmap
                            if (readyBmp != null && !readyBmp.isRecycled) {
                                cache.pendingBitmap = null
                                val texId = if (cache.textureId != -1) {
                                    cache.textureId
                                } else {
                                    GlUtil.generateTexture(GLES20.GL_TEXTURE_2D).also { cache.textureId = it }
                                }
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                                android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, readyBmp, 0)
                                cache.hasValidTexture = true
                                readyBmp.recycle()
                            }

                            // Only submit decode if not video or if video has no valid texture yet
                            val shouldDecode = !isVideo || (!hasReceivedPipFrame && !cache.hasValidTexture)
                            if (shouldDecode && cache.timestampBucket != bucket && !cache.isDecoding) {
                                cache.isDecoding = true
                                val decodeUri = pip.uri
                                val targetBucket = bucket
                                val targetRelTime = relTime
                                val pipId = pip.id
                                pipDecodeExecutor.execute {
                                    try {
                                        val itemMeta = getPipMediaMeta(decodeUri)
                                        val bmp = if (itemMeta.isVideo) {
                                            val retr = getPipRetriever(decodeUri)
                                            try {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 &&
                                                    itemMeta.scaledWidth > 0 && itemMeta.scaledHeight > 0
                                                ) {
                                                    retr?.getScaledFrameAtTime(
                                                        targetRelTime,
                                                        MediaMetadataRetriever.OPTION_CLOSEST,
                                                        itemMeta.scaledWidth,
                                                        itemMeta.scaledHeight
                                                    ) ?: retr?.getFrameAtTime(targetRelTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                                } else {
                                                    retr?.getFrameAtTime(targetRelTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                                }
                                            } catch (_: Throwable) { null }
                                        } else {
                                            try {
                                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                                if (decodeUri.startsWith("content://")) {
                                                    context.contentResolver.openInputStream(Uri.parse(decodeUri))?.use {
                                                        BitmapFactory.decodeStream(it, null, options)
                                                    }
                                                } else {
                                                    val path = if (decodeUri.startsWith("file://")) Uri.parse(decodeUri).path ?: decodeUri.removePrefix("file://") else decodeUri
                                                    BitmapFactory.decodeFile(path, options)
                                                }
                                                val maxDim = maxOf(options.outWidth, options.outHeight).coerceAtLeast(1)
                                                var sampleSize = 1
                                                while (maxDim / (sampleSize * 2) >= 480) {
                                                    sampleSize *= 2
                                                }
                                                val decodeOptions = BitmapFactory.Options().apply {
                                                    inSampleSize = sampleSize
                                                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                                                }
                                                if (decodeUri.startsWith("content://")) {
                                                    context.contentResolver.openInputStream(Uri.parse(decodeUri))?.use {
                                                        BitmapFactory.decodeStream(it, null, decodeOptions)
                                                    }
                                                } else {
                                                    val path = if (decodeUri.startsWith("file://")) Uri.parse(decodeUri).path ?: decodeUri.removePrefix("file://") else decodeUri
                                                    BitmapFactory.decodeFile(path, decodeOptions)
                                                }
                                            } catch (_: Throwable) { null }
                                        }

                                        val targetCache = pipCaches[pipId]
                                        if (targetCache != null && targetCache.uri == decodeUri) {
                                            targetCache.pendingBitmap?.recycle()
                                            targetCache.pendingBitmap = bmp
                                            targetCache.timestampBucket = if (itemMeta.isVideo) targetBucket else Long.MAX_VALUE
                                            isDirty = true
                                        } else {
                                            bmp?.recycle()
                                        }
                                    } catch (t: Throwable) {
                                        AppLog.w(TAG, "Async PiP frame decode failed: ${t.message}")
                                    } finally {
                                        pipCaches[pipId]?.isDecoding = false
                                    }
                                }
                            }

                            val pipTexId = cache.textureId
                            // Draw PiP layer with live or persisted transform
                            if (pipTexId != -1 && cache.hasValidTexture) {
                                GLES20.glEnable(GLES20.GL_BLEND)
                                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                                filter.draw(pipTexId, GlUtil.FLIP_Y_MATRIX, pipMvp, w, h)
                                GLES20.glDisable(GLES20.GL_BLEND)
                            }
                        }
                    } catch (t: Throwable) {
                        AppLog.w(TAG, "PiP preview frame error: ${t.message}")
                    }
                }
            }

            // Render Overlays / Text & Blur Image Stickers
            val imageBlurs = proj.blurTracks.filter { it.blurType == com.clipcut.core.model.BlurType.IMAGE }
            if (proj.overlays.isNotEmpty() || imageBlurs.isNotEmpty()) {
                val editingId = activeEditingOverlayId
                val overlaysToRender = if (editingId != null) {
                    proj.overlays.filter { it.id != editingId }
                } else {
                    proj.overlays
                }
                over.renderOverlays(
                    overlays = overlaysToRender,
                    blurTracks = imageBlurs,
                    currentPtsUs = posUs,
                    viewportWidth = w,
                    viewportHeight = h,
                    editingBlurId = activeEditingBlurId,
                    editingBlurX = activeEditingBlurX,
                    editingBlurY = activeEditingBlurY,
                    editingBlurW = activeEditingBlurWidth,
                    editingBlurH = activeEditingBlurHeight,
                    editingBlurRot = activeEditingBlurRotation
                )
                val overId = over.textureId
                if (overId != -1) {
                    GLES20.glEnable(GLES20.GL_BLEND)
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                    filter.draw(overId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, w, h)
                    GLES20.glDisable(GLES20.GL_BLEND)
                }
            } else if (over.textureId != -1) {
                over.renderOverlays(emptyList(), emptyList(), posUs, w, h)
            }
        }

        core.swapBuffers(eSurface)
    }

    private fun getPipRetriever(uriStr: String): MediaMetadataRetriever? {
        val existing = pipRetrievers[uriStr]
        if (existing != null) return existing
        return try {
            val retr = MediaMetadataRetriever()
            if (uriStr.startsWith("file://")) {
                retr.setDataSource(Uri.parse(uriStr).path)
            } else if (uriStr.startsWith("/")) {
                retr.setDataSource(uriStr)
            } else {
                retr.setDataSource(context, Uri.parse(uriStr))
            }
            pipRetrievers[uriStr] = retr
            retr
        } catch (t: Throwable) {
            AppLog.w(TAG, "Failed to setDataSource for PiP: $uriStr", t)
            null
        }
    }

    private fun findClipAndOffsetAt(timelineUs: Long): Pair<TimelineItem?, Long> {
        val clips = project?.videoClips ?: return Pair(null, 0L)
        if (clips.isEmpty()) return Pair(null, 0L)
        var accum = 0L
        for (c in clips) {
            val end = accum + c.effectiveDurationUs
            if (timelineUs in accum until end) return Pair(c, accum)
            accum = end
        }
        val last = clips.lastOrNull()
        val startOfLast = (accum - (last?.effectiveDurationUs ?: 0L)).coerceAtLeast(0L)
        return Pair(last, startOfLast)
    }

    private fun findClipAt(timelineUs: Long): TimelineItem? = findClipAndOffsetAt(timelineUs).first

    fun setProject(project: Project) { this.project = project; isDirty = true }
    fun pause() { paused = true }
    fun resume() { paused = false; isDirty = true }
    fun seekTo(@Suppress("UNUSED_PARAMETER") timelineUs: Long) { isDirty = true }
    fun release() {
        onSurfaceDestroyed()
        try { prevClipFbo?.release() } catch (_: Throwable) {}
        prevClipFbo = null
        hasPrevClip = false
        pipRetrievers.values.forEach {
            try { it.release() } catch (_: Throwable) {}
        }
        pipRetrievers.clear()
        pipCaches.values.forEach { c ->
            if (c.textureId != -1) {
                try { GLES20.glDeleteTextures(1, intArrayOf(c.textureId), 0) } catch (_: Throwable) {}
            }
            c.pendingBitmap?.recycle()
            c.pendingBitmap = null
        }
        pipCaches.clear()
        pipMediaMetaMap.clear()
        try { pipDecodeExecutor.shutdownNow() } catch (_: Throwable) {}
    }

    companion object { private const val TAG = "GlPreviewRenderer" }
}
