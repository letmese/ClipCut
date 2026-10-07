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
import com.clipcut.core.gl.PipelineRenderer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem

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

    private val texMatrix = FloatArray(16)
    @Volatile private var isRunning = false
    @Volatile var isDirty = true
    @Volatile private var hasReceivedFirstFrame = false
    @Volatile var activeEditingOverlayId: String? = null
    @Volatile var activeEditingPipId: String? = null

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
        } else {
            mainHandler.post { playerManager?.setVideoSurface(null) }
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

            val os = outSurface.surface
            mainHandler.post { playerManager?.setVideoSurface(os) }

            pipe = PipelineRenderer(context).also { it.init(initialW, initialH) }
            pipeline = pipe

            over = OverlayRenderer(context).also { it.init() }
            overlayRenderer = over

            filter = PassthroughFilter().also { it.init() }
            overlayFilter = filter

            while (isRunning) {
                if (requestResize) {
                    requestResize = false
                    pipe.resizeIfNeeded(resizeW, resizeH)
                }
                if (!paused || isDirty) {
                    renderOnce(core, eSurface, outSurface, pipe, over, filter, oesTex)
                } else {
                    Thread.sleep(16)
                }
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "Render thread fatal: ${t.message}", t)
        } finally {
            mainHandler.post { playerManager?.setVideoSurface(null) }
            try { filter?.release() } catch (_: Throwable) {}
            try { over?.release() } catch (_: Throwable) {}
            try { pipe?.release() } catch (_: Throwable) {}
            try { outSurface?.release() } catch (_: Throwable) {}
            if (oesTex != -1) {
                try { GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0) } catch (_: Throwable) {}
            }
            try { core?.makeNothingCurrent() } catch (_: Throwable) {}
            try { core?.destroySurface(eSurface) } catch (_: Throwable) {}
            try { core?.release() } catch (_: Throwable) {}
            eglCore = null; eglSurface = null; outputSurface = null
            pipeline = null; overlayRenderer = null; overlayFilter = null
            oesTextureId = -1
        }
    }

    private class PipFrameCache(
        val uri: String,
        @Volatile var timestampBucket: Long = -1L,
        @Volatile var textureId: Int = -1,
        @Volatile var hasValidTexture: Boolean = false,
        @Volatile var pendingBitmap: android.graphics.Bitmap? = null,
        @Volatile var isDecoding: Boolean = false
    )
    private val pipCaches = java.util.concurrent.ConcurrentHashMap<String, PipFrameCache>()
    private val pipDecodeExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)

    private fun renderOnce(
        core: EglCore, eSurface: EGLSurface, out: OutputSurface,
        pipe: PipelineRenderer, over: OverlayRenderer, filter: GlFilter, oesTex: Int
    ) {
        val newFrame = out.awaitNewImage(10)
        if (newFrame) hasReceivedFirstFrame = true
        if (!newFrame && !isDirty) { Thread.sleep(8); return }
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
        val clip = findClipAt(posUs)

        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (clip != null) {
            pipe.applyPostProcessing(
                sourceTexId = oesTex,
                sourceTexMatrix = texMatrix,
                sourceIsOes = true,
                mvpMatrix = GlUtil.IDENTITY_MATRIX,
                rotationDegrees = clip.rotationDegrees,
                clip = clip
            )
        }

        val proj = project
        if (proj != null) {
            // Render PiP Layers
            for (pip in proj.pipTracks) {
                if (posUs in pip.timelineStartUs..pip.timelineEndUs && pip.uri.isNotEmpty()) {
                    if (pip.id == activeEditingPipId) continue
                    try {
                        val relTime = (posUs - pip.timelineStartUs) + pip.trimStartUs
                        val isVideo = pip.uri.contains("video", ignoreCase = true) || pip.uri.endsWith(".mp4", ignoreCase = true) || pip.uri.endsWith(".mkv", ignoreCase = true)
                        val bucket = if (isVideo) (relTime / 100_000L).coerceAtLeast(0L) else 0L

                        val cache = pipCaches.getOrPut(pip.uri) { PipFrameCache(pip.uri) }

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

                        // If bucket changed and not currently decoding, submit async decode task
                        if (cache.timestampBucket != bucket && !cache.isDecoding) {
                            cache.isDecoding = true
                            val decodeUri = pip.uri
                            val targetBucket = bucket
                            val targetRelTime = relTime
                            pipDecodeExecutor.execute {
                                try {
                                    val bmp = if (decodeUri.startsWith("content://")) {
                                        val retr = getPipRetriever(decodeUri)
                                        val videoFrame = try {
                                            retr?.getFrameAtTime(targetRelTime, MediaMetadataRetriever.OPTION_CLOSEST)
                                        } catch (_: Throwable) { null }
                                        videoFrame ?: run {
                                            try {
                                                context.contentResolver.openInputStream(Uri.parse(decodeUri))?.use {
                                                    BitmapFactory.decodeStream(it)
                                                }
                                            } catch (_: Throwable) { null }
                                        }
                                    } else {
                                        val filePath = if (decodeUri.startsWith("file://")) Uri.parse(decodeUri).path ?: decodeUri.removePrefix("file://") else decodeUri
                                        val retr = getPipRetriever(filePath)
                                        val videoFrame = try {
                                            retr?.getFrameAtTime(targetRelTime, MediaMetadataRetriever.OPTION_CLOSEST)
                                        } catch (_: Throwable) { null }
                                        videoFrame ?: BitmapFactory.decodeFile(filePath)
                                    }

                                    val targetCache = pipCaches[decodeUri]
                                    if (targetCache != null) {
                                        targetCache.pendingBitmap?.recycle()
                                        targetCache.pendingBitmap = bmp
                                        targetCache.timestampBucket = targetBucket
                                        // Request a render pass so the new bitmap is immediately uploaded and drawn
                                        isDirty = true
                                    } else {
                                        bmp?.recycle()
                                    }
                                } catch (t: Throwable) {
                                    AppLog.w(TAG, "Async PiP frame decode failed: ${t.message}")
                                } finally {
                                    pipCaches[decodeUri]?.isDecoding = false
                                }
                            }
                        }

                        val pipTexId = cache.textureId
                        // Only draw if we have successfully uploaded a valid decoded frame texture
                        if (pipTexId != -1 && cache.hasValidTexture) {
                            val pipMvp = FloatArray(16)
                            Matrix.setIdentityM(pipMvp, 0)
                            val tx = (pip.normalizedX * 2f) - 1f
                            val ty = 1f - (pip.normalizedY * 2f)
                            Matrix.translateM(pipMvp, 0, tx, ty, 0f)
                            if (pip.rotation != 0f) {
                                Matrix.rotateM(pipMvp, 0, -pip.rotation, 0f, 0f, 1f)
                            }
                            Matrix.scaleM(pipMvp, 0, pip.scale, pip.scale, 1f)

                            GLES20.glEnable(GLES20.GL_BLEND)
                            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                            // Apply FLIP_Y_MATRIX to texture matrix so the bitmap renders right-side up!
                            filter.draw(pipTexId, GlUtil.FLIP_Y_MATRIX, pipMvp, w, h)
                            GLES20.glDisable(GLES20.GL_BLEND)
                        }
                    } catch (t: Throwable) {
                        AppLog.w(TAG, "PiP preview frame error: ${t.message}")
                    }
                }
            }

            // Render Overlays / Text
            if (proj.overlays.isNotEmpty()) {
                val editingId = activeEditingOverlayId
                val overlaysToRender = if (editingId != null) {
                    proj.overlays.filter { it.id != editingId }
                } else {
                    proj.overlays
                }
                if (overlaysToRender.isNotEmpty()) {
                    over.renderOverlays(overlaysToRender, posUs, w, h)
                    val overId = over.textureId
                    if (overId != -1) {
                        GLES20.glEnable(GLES20.GL_BLEND)
                        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                        filter.draw(overId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, w, h)
                        GLES20.glDisable(GLES20.GL_BLEND)
                    }
                } else if (over.textureId != -1) {
                    over.renderOverlays(emptyList(), posUs, w, h)
                }
            }
        }

        core.swapBuffers(eSurface)
    }

    private fun getPipRetriever(uriStr: String): MediaMetadataRetriever? {
        return pipRetrievers.getOrPut(uriStr) {
            MediaMetadataRetriever().apply {
                try {
                    if (uriStr.startsWith("file://")) {
                        setDataSource(Uri.parse(uriStr).path)
                    } else if (uriStr.startsWith("/")) {
                        setDataSource(uriStr)
                    } else {
                        setDataSource(context, Uri.parse(uriStr))
                    }
                } catch (t: Throwable) {
                    AppLog.w(TAG, "Failed to setDataSource for PiP: $uriStr", t)
                }
            }
        }
    }

    private fun findClipAt(timelineUs: Long): TimelineItem? {
        val clips = project?.videoClips ?: return null
        if (clips.isEmpty()) return null
        var accum = 0L
        for (c in clips) {
            val end = accum + c.effectiveDurationUs
            if (timelineUs in accum until end) return c
            accum = end
        }
        return clips.lastOrNull()
    }

    fun setProject(project: Project) { this.project = project; isDirty = true }
    fun pause() { paused = true }
    fun resume() { paused = false; isDirty = true }
    fun seekTo(@Suppress("UNUSED_PARAMETER") timelineUs: Long) { isDirty = true }
    fun release() {
        onSurfaceDestroyed()
        pipRetrievers.values.forEach {
            try { it.release() } catch (_: Throwable) {}
        }
        pipRetrievers.clear()
        for (it in pipCaches.values) {
            if (it.textureId != -1) {
                try { GLES20.glDeleteTextures(1, intArrayOf(it.textureId), 0) } catch (_: Throwable) {}
            }
            it.pendingBitmap?.recycle()
            it.pendingBitmap = null
        }
        pipCaches.clear()
        try { pipDecodeExecutor.shutdownNow() } catch (_: Throwable) {}
    }

    companion object { private const val TAG = "GlPreviewRenderer" }
}
