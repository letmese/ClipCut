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

/**
 * GlPreviewRenderer — bridges the GLSurfaceView to the PipelineRenderer.
 *
 *  positionProvider: lambda returning current timeline ptsUs (used for keyframe
 *                    interpolation and overlay enablement).  EditorScreen sets
 *                    this to a lambda backed by PreviewPlayerManager.currentPositionUs.
 *
 *  setProject: caches the project so onDrawFrame can re-resolve overlays and
 *              push them to PipelineRenderer for each frame.
 *
 *  seekTo: forces a re-render of the cached clip at the seeked position.
 */
class GlPreviewRenderer(private val context: Context) {
    private var pipeline: PipelineRenderer? = null
    private var project: Project? = null
    private var surface: Surface? = null
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var paused: Boolean = false

    var positionProvider: (() -> Long)? = null
    var playerManager: PreviewPlayerManager? = null

    private var eglCore: EglCore? = null
    private var eglSurface: EGLSurface? = null
    private var outputSurface: OutputSurface? = null
    private var overlayRenderer: OverlayRenderer? = null
    private var overlayFilter: GlFilter? = null
    private var oesTextureId: Int = -1

    private val texMatrix = FloatArray(16)
    @Volatile private var isRunning = false
    @Volatile private var isDirty = true
    private var renderThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
        this.surface = surface
        this.viewportWidth = width
        this.viewportHeight = height
        AppLog.d(TAG, "onSurfaceCreated ${width}x${height}")

        stopRenderThread()
        isRunning = true
        renderThread = Thread({
            runRenderLoop(surface, width, height)
        }, "GlPreviewRenderThread").apply { start() }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        this.viewportWidth = width
        this.viewportHeight = height
        pipeline?.resizeIfNeeded(width, height)
        isDirty = true
        AppLog.d(TAG, "onSurfaceChanged ${width}x${height}")
    }

    fun onSurfaceDestroyed() {
        stopRenderThread()
        surface = null
    }

    private fun stopRenderThread() {
        isRunning = false
        try {
            renderThread?.join(500)
        } catch (_: InterruptedException) {}
        renderThread = null
    }

    private fun runRenderLoop(surface: Surface, initialWidth: Int, initialHeight: Int) {
        try {
            val core = EglCore(null, EglCore.FLAG_RECORDABLE)
            eglCore = core
            val eSurface = core.createWindowSurface(surface)
            eglSurface = eSurface
            core.makeCurrent(eSurface)

            val oesId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            oesTextureId = oesId
            val outSurface = OutputSurface(oesId)
            outputSurface = outSurface

            mainHandler.post {
                playerManager?.setVideoSurface(outSurface.surface)
            }

            val pipe = PipelineRenderer(context).also { it.init(initialWidth, initialHeight) }
            pipeline = pipe

            val over = OverlayRenderer(context).also { it.init() }
            overlayRenderer = over

            val filter = PassthroughFilter().also { it.init() }
            overlayFilter = filter

            while (isRunning) {
                if (!paused) {
                    onDrawFrame()
                }
                Thread.sleep(16)
            }
        } catch (t: Throwable) {
            AppLog.e(TAG, "Render thread exception: ${t.message}", t)
        } finally {
            cleanUpGl()
        }
    }

    fun onDrawFrame() {
        val core = eglCore ?: return
        val eSurface = eglSurface ?: return
        val outSurface = outputSurface ?: return
        val pipe = pipeline ?: return

        val newFrame = outSurface.awaitNewImage(10)
        if (!newFrame && !isDirty) {
            return
        }
        isDirty = false

        outSurface.getTransformMatrix(texMatrix)
        val posUs = positionProvider?.invoke() ?: 0L
        val clip = findClipAt(posUs)

        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (clip != null) {
            pipe.applyPostProcessing(
                sourceTexId = oesTextureId,
                sourceTexMatrix = texMatrix,
                sourceIsOes = true,
                mvpMatrix = GlUtil.IDENTITY_MATRIX,
                rotationDegrees = clip.rotationDegrees,
                clip = clip
            )
        }

        val proj = project
        if (proj != null && proj.overlays.isNotEmpty()) {
            overlayRenderer?.renderOverlays(proj.overlays, posUs, viewportWidth, viewportHeight)
            val overId = overlayRenderer?.textureId ?: -1
            if (overId != -1) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                overlayFilter?.draw(overId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, viewportWidth, viewportHeight)
                GLES20.glDisable(GLES20.GL_BLEND)
            }
        }

        core.swapBuffers(eSurface)
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

    private fun cleanUpGl() {
        mainHandler.post {
            playerManager?.setVideoSurface(null)
        }
        overlayFilter?.release()
        overlayFilter = null
        overlayRenderer?.release()
        overlayRenderer = null
        pipeline?.release()
        pipeline = null
        outputSurface?.release()
        outputSurface = null
        if (oesTextureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            oesTextureId = -1
        }
        eglSurface?.let { eglCore?.destroySurface(it) }
        eglSurface = null
        eglCore?.release()
        eglCore = null
    }

    fun setProject(project: Project) {
        this.project = project
        isDirty = true
        AppLog.d(TAG, "setProject: ${project.videoClips.size} clips")
    }

    fun pause() { paused = true }
    fun resume() { paused = false; isDirty = true }

    fun seekTo(timelineUs: Long) {
        isDirty = true
    }

    fun release() {
        onSurfaceDestroyed()
    }

    companion object { private const val TAG = "GlPreviewRenderer" }
}
