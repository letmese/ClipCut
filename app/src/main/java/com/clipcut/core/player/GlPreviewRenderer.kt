package com.clipcut.core.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.clipcut.core.codec.VideoDecoder
import com.clipcut.core.gl.ColorAdjustFilter
import com.clipcut.core.gl.EglCore
import com.clipcut.core.gl.GlUtil
import com.clipcut.core.gl.InputSurface
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.gl.OverlayRenderer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.MediaType
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A GL-based video preview renderer. Runs a decode-render loop on a dedicated
 * thread, rendering to a Surface (typically from AndroidExternalSurface).
 *
 * Audio playback is handled separately by PreviewPlayerManager. This renderer
 * polls the audio clock to stay in sync.
 */
class GlPreviewRenderer(private val context: Context) {

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    private var eglCore: EglCore? = null
    private var eglSurface: EGLSurface? = null
    private var outputSurface: OutputSurface? = null

    private var colorFilter: ColorAdjustFilter? = null
    private var overlayRenderer: OverlayRenderer? = null

    private var currentProject: Project? = null
    private var currentSurface: Surface? = null
    private var currentWidth = 0
    private var currentHeight = 0

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(true)
    private var pendingSeekUs = -1L
    private var currentClipIndex = 0
    private var currentDecoder: VideoDecoder? = null

    // Image texture cache for IMAGE clips
    private var imageTexId = 0
    private var imageW = 0
    private var imageH = 0

    /**
     * Clock callback: returns the current timeline position in microseconds.
     * Set by EditorViewModel to point at PreviewPlayerManager.currentPositionUs.
     */
    var positionProvider: (() -> Long)? = null

    /**
     * Called on the render thread when the preview surface is ready.
     */
    fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
        AppLog.i(TAG, "onSurfaceCreated: ${width}x${height}")
        currentSurface = surface
        currentWidth = width
        currentHeight = height

        ensureRenderThread()
        renderHandler?.post {
            try {
                eglCore = EglCore()
                eglSurface = eglCore!!.createWindowSurface(surface)
                eglCore!!.makeCurrent(eglSurface!!)

                val oesTexId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
                outputSurface = OutputSurface(oesTexId)

                colorFilter = ColorAdjustFilter()
                colorFilter!!.init()

                overlayRenderer = OverlayRenderer(context)
                overlayRenderer!!.init()

                isRunning.set(true)
                renderHandler?.post(renderLoop)
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to initialize render context", t)
            }
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        currentWidth = width
        currentHeight = height
    }

    fun onSurfaceDestroyed() {
        AppLog.i(TAG, "onSurfaceDestroyed")
        isRunning.set(false)
        val handler = renderHandler ?: return
        val latch = java.util.concurrent.CountDownLatch(1)
        handler.post {
            try {
                currentDecoder?.release()
                currentDecoder = null

                colorFilter?.release()
                overlayRenderer?.release()
                outputSurface?.release()

                eglSurface?.let { eglCore?.destroySurface(it) }
                eglCore?.release()

                colorFilter = null
                overlayRenderer = null
                outputSurface = null
                eglSurface = null
                eglCore = null
            } catch (t: Throwable) {
                AppLog.e(TAG, "Error releasing GL resources", t)
            } finally {
                latch.countDown()
            }
        }
        try { latch.await() } catch (_: InterruptedException) {}
        currentSurface = null
    }

    fun setProject(project: Project) {
        currentProject = project
        // Reset the clip index so the next frame starts at the beginning
        currentClipIndex = 0
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun seekTo(timelineUs: Long) {
        pendingSeekUs = timelineUs
    }

    fun release() {
        isRunning.set(false)
        onSurfaceDestroyed()
        renderThread?.quitSafely()
        renderThread = null
        renderHandler = null
        AppLog.d(TAG, "GlPreviewRenderer released")
    }

    private fun ensureRenderThread() {
        if (renderThread == null) {
            renderThread = HandlerThread("GlPreviewRenderer").also { it.start() }
            renderHandler = Handler(renderThread!!.looper)
        }
    }

    private val renderLoop = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return

            try {
                val project = currentProject
                if (project == null || project.videoClips.isEmpty()) {
                    // Nothing to render; clear to black
                    GLES20.glViewport(0, 0, currentWidth, currentHeight)
                    GLES20.glClearColor(0f, 0f, 0f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    eglCore?.swapBuffers(eglSurface!!)
                    renderHandler?.postDelayed(this, 33)
                    return
                }

                val clockUs = positionProvider?.invoke() ?: 0L
                val targetUs = if (pendingSeekUs >= 0L) {
                    val s = pendingSeekUs
                    pendingSeekUs = -1L
                    s
                } else {
                    clockUs
                }

                renderFrameAt(project, targetUs)

            } catch (t: Throwable) {
                AppLog.e(TAG, "Render loop error", t)
            }

            // ~30fps preview
            renderHandler?.postDelayed(this, 33)
        }
    }

    private fun renderFrameAt(project: Project, timelineUs: Long) {
        // Find which clip the timeline position falls in
        var accumulatedUs = 0L
        var clipIndex = 0
        var clipLocalUs = 0L
        for ((i, clip) in project.videoClips.withIndex()) {
            val dur = clip.effectiveDurationUs
            if (timelineUs <= accumulatedUs + dur || i == project.videoClips.lastIndex) {
                clipIndex = i
                clipLocalUs = (timelineUs - accumulatedUs).coerceAtLeast(0L)
                break
            }
            accumulatedUs += dur
        }

        val clip = project.videoClips[clipIndex]

        // If we switched clips, release the old decoder
        if (clipIndex != currentClipIndex || currentDecoder == null) {
            currentDecoder?.release()
            currentDecoder = null
            currentClipIndex = clipIndex
        }

        // Ensure the viewport is correct
        GLES20.glViewport(0, 0, currentWidth, currentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val texMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)

        if (clip.mediaType == MediaType.IMAGE) {
            renderImagePreview(clip, mvpMatrix)
        } else {
            renderVideoPreview(clip, clipLocalUs, texMatrix, mvpMatrix)
        }

        overlayRenderer?.renderOverlays(
            overlays = project.overlays,
            currentPtsUs = timelineUs,
            viewportWidth = currentWidth,
            viewportHeight = currentHeight
        )

        eglCore?.swapBuffers(eglSurface!!)
    }

    private fun renderVideoPreview(
        clip: TimelineItem,
        clipLocalUs: Long,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray
    ) {
        val os = outputSurface ?: return

        if (currentDecoder == null) {
            currentDecoder = VideoDecoder(context, clip, os)
            try {
                currentDecoder!!.init()
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to init decoder for preview", t)
                currentDecoder = null
                return
            }
        }

        val decoder = currentDecoder ?: return

        // Compute the source-time position we want to display
        val sourceUs = clip.trimStartUs + (clipLocalUs * clip.speed).toLong()

        // Decode forward until we reach the target time
        var lastPtsUs = -1L
        var attempts = 0
        while (attempts < 120) {
            val ptsUs = decoder.decodeNextFrame()
            if (ptsUs < 0) break
            if (ptsUs >= sourceUs) {
                lastPtsUs = ptsUs
                break
            }
            lastPtsUs = ptsUs
            attempts++
        }

        if (lastPtsUs < 0) return

        val rotation = decoder.videoRotation
        val rotated = rotation == 90 || rotation == 270
        val srcW = if (rotated) decoder.videoHeight else decoder.videoWidth
        val srcH = if (rotated) decoder.videoWidth else decoder.videoHeight

        buildFitMvpMatrix(srcW, srcH, currentWidth, currentHeight, mvpMatrix)
        os.getTransformMatrix(texMatrix)

        val cf = colorFilter ?: return
        cf.updateConfig(clip.filterConfig)
        cf.drawOes(
            texId = os.textureId,
            texMatrix = texMatrix,
            mvpMatrix = mvpMatrix,
            rotationDegrees = rotation,
            width = currentWidth,
            height = currentHeight
        )
    }

    private fun renderImagePreview(clip: TimelineItem, mvpMatrix: FloatArray) {
        if (imageTexId == 0) {
            try {
                context.contentResolver.openInputStream(android.net.Uri.parse(clip.uri))?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        imageW = bmp.width
                        imageH = bmp.height
                        imageTexId = uploadBitmapToTexture(bmp)
                        bmp.recycle()
                    }
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to load image for preview", t)
            }
        }
        if (imageTexId == 0) return

        buildFitMvpMatrix(imageW, imageH, currentWidth, currentHeight, mvpMatrix)

        val cf = colorFilter ?: return
        cf.updateConfig(clip.filterConfig)
        cf.draw(
            texId = imageTexId,
            texMatrix = GlUtil.IDENTITY_MATRIX,
            mvpMatrix = mvpMatrix,
            width = currentWidth,
            height = currentHeight,
            textureTarget = GLES20.GL_TEXTURE_2D
        )
    }

    private fun uploadBitmapToTexture(bitmap: Bitmap): Int {
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

        val ndcScaleX = (srcW * fit) / outW.toFloat()
        val ndcScaleY = (srcH * fit) / outH.toFloat()

        Matrix.scaleM(out, 0, ndcScaleX, ndcScaleY, 1f)
    }

    companion object {
        private const val TAG = "GlPreviewRenderer"
    }
}
