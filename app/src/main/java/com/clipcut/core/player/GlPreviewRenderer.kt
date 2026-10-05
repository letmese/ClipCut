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
import com.clipcut.core.gl.PassthroughFilter
import com.clipcut.core.gl.PipelineRenderer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem

class GlPreviewRenderer(private val context: Context) {

    private var pipeline: PipelineRenderer? = null
    @Volatile private var project: Project? = null
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
    @Volatile private var isDirty = true
    @Volatile private var hasReceivedFirstFrame = false

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
                if (!paused) {
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
        if (proj != null && proj.overlays.isNotEmpty()) {
            over.renderOverlays(proj.overlays, posUs, w, h)
            val overId = over.textureId
            if (overId != -1) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                filter.draw(overId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, w, h)
                GLES20.glDisable(GLES20.GL_BLEND)
            }
        }

        core.swapBuffers(eSurface)
        Thread.sleep(16)
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
    fun release() { onSurfaceDestroyed() }

    companion object { private const val TAG = "GlPreviewRenderer" }
}
