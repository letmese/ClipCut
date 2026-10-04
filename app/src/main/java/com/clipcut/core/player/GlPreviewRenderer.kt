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
import com.clipcut.core.gl.FramebufferTexture
import com.clipcut.core.gl.GlUtil
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.gl.OverlayRenderer
import com.clipcut.core.gl.TransitionFilter
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.MediaType
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import com.clipcut.core.model.TransitionType
import java.util.concurrent.atomic.AtomicBoolean

class GlPreviewRenderer(private val context: Context) {

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    private var eglCore: EglCore? = null
    private var eglSurface: EGLSurface? = null
    private var outputSurface: OutputSurface? = null

    private var colorFilter: ColorAdjustFilter? = null
    private var overlayRenderer: OverlayRenderer? = null
    private var transitionFilter: TransitionFilter? = null
    private var outgoingFbo: FramebufferTexture? = null
    private var incomingTransitionTexId = 0

    private var currentProject: Project? = null
    private var currentSurface: Surface? = null
    private var currentWidth = 0
    private var currentHeight = 0

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(true)
    private var pendingSeekUs = -1L
    private var currentClipIndex = -1
    private var currentDecoder: VideoDecoder? = null
    private var lastDecodedSourceUs = -1L

    // Multi-image texture cache, keyed by clip ID
    private val imageTextureCache = mutableMapOf<String, Triple<Int, Int, Int>>() // clipId -> (texId, w, h)

    var positionProvider: (() -> Long)? = null

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

                colorFilter = ColorAdjustFilter().also { it.init() }
                overlayRenderer = OverlayRenderer(context).also { it.init() }
                transitionFilter = TransitionFilter().also { it.init() }
                outgoingFbo = FramebufferTexture(currentWidth, currentHeight).also { it.init() }

                incomingTransitionTexId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, incomingTransitionTexId)
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    currentWidth, currentHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
                )
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

                isRunning.set(true)
                renderHandler?.post(renderLoop)
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to initialize render context", t)
            }
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        if (width != currentWidth || height != currentHeight) {
            currentWidth = width
            currentHeight = height
            renderHandler?.post {
                // Recreate the outgoing FBO and incoming transition texture at the new size
                outgoingFbo?.release()
                outgoingFbo = FramebufferTexture(width, height).also { it.init() }

                if (incomingTransitionTexId != 0) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, incomingTransitionTexId)
                    GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        width, height, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
                    )
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                }
            }
        }
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
                lastDecodedSourceUs = -1L

                colorFilter?.release()
                overlayRenderer?.release()
                transitionFilter?.release()
                outgoingFbo?.release()
                outputSurface?.release()

                if (incomingTransitionTexId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(incomingTransitionTexId), 0)
                    incomingTransitionTexId = 0
                }

                eglSurface?.let { eglCore?.destroySurface(it) }
                eglCore?.release()

                colorFilter = null
                overlayRenderer = null
                transitionFilter = null
                outgoingFbo = null
                outputSurface = null
                eglSurface = null
                eglCore = null

                // Image textures are also gone with the EGL context
                imageTextureCache.clear()
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
        // Force a decoder reset on the next frame so we pick up edits
        renderHandler?.post {
            currentDecoder?.release()
            currentDecoder = null
            currentClipIndex = -1
            lastDecodedSourceUs = -1L

            // Evict cached image textures for clips that no longer exist
            val liveIds = project.videoClips.map { it.id }.toSet()
            val staleIds = imageTextureCache.keys.filter { it !in liveIds }
            for (id in staleIds) {
                val cached = imageTextureCache.remove(id) ?: continue
                GLES20.glDeleteTextures(1, intArrayOf(cached.first), 0)
            }
        }
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

            // When paused, throttle to ~5fps to keep the surface alive with
            // minimal CPU. Seeking still works because pendingSeekUs is honored.
            if (isPaused.get() && pendingSeekUs < 0L) {
                renderHandler?.postDelayed(this, 200)
                return
            }

            try {
                val project = currentProject
                if (project == null || project.videoClips.isEmpty()) {
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

            renderHandler?.postDelayed(this, 33)
        }
    }

    private fun renderFrameAt(project: Project, timelineUs: Long) {
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

        // Detect clip change and reset the decoder
        if (clipIndex != currentClipIndex) {
            currentDecoder?.release()
            currentDecoder = null
            lastDecodedSourceUs = -1L
            currentClipIndex = clipIndex
        }

        GLES20.glViewport(0, 0, currentWidth, currentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val texMatrix = FloatArray(16)
        val mvpMatrix = FloatArray(16)

        // Check if we're inside a transition window
        val transitionActive = clip.transitionType != TransitionType.NONE &&
                clipIndex > 0 &&
                clipLocalUs < clip.transitionDurationUs

        if (transitionActive) {
            // Render incoming frame into the default framebuffer via transition.
            // The outgoing frame is captured from the previous render pass; if we
            // don't have one yet, just render the incoming clip directly.
            renderTransitionPreview(
                project = project,
                incomingClip = clip,
                clipLocalUs = clipLocalUs,
                texMatrix = texMatrix,
                mvpMatrix = mvpMatrix
            )
        } else {
            if (clip.mediaType == MediaType.IMAGE) {
                renderImagePreview(clip, mvpMatrix)
            } else {
                renderVideoPreview(clip, clipLocalUs, texMatrix, mvpMatrix)
            }
        }

        overlayRenderer?.renderOverlays(
            overlays = project.overlays,
            currentPtsUs = timelineUs,
            viewportWidth = currentWidth,
            viewportHeight = currentHeight
        )

        // Capture the composited frame into the outgoing FBO ONLY if the next clip
        // has an active transition and we are NOT currently rendering inside an incoming transition window,
        // preserving all overlays and preventing compound fade.
        val nextClip = project.videoClips.getOrNull(clipIndex + 1)
        val nextNeedsTransition = nextClip != null && nextClip.transitionType != TransitionType.NONE
        if (nextNeedsTransition && !transitionActive) {
            val fbo = outgoingFbo
            if (fbo != null) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbo.textureId)
                GLES20.glCopyTexSubImage2D(
                    GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                    currentWidth, currentHeight
                )
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            }
        }

        // Restore the viewport
        GLES20.glViewport(0, 0, currentWidth, currentHeight)
        eglCore?.swapBuffers(eglSurface!!)
    }

    private fun renderTransitionPreview(
        project: Project,
        incomingClip: TimelineItem,
        clipLocalUs: Long,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray
    ) {
        val progress = (clipLocalUs.toFloat() / incomingClip.transitionDurationUs.toFloat())
            .coerceIn(0f, 1f)

        // 1. Render incoming clip into the default framebuffer first (support both images and video)
        if (incomingClip.mediaType == MediaType.IMAGE) {
            renderImagePreview(incomingClip, mvpMatrix)
        } else {
            renderVideoPreview(incomingClip, clipLocalUs, texMatrix, mvpMatrix)
        }

        val texId = incomingTransitionTexId
        if (texId != 0) {
            // 2. Copy the incoming frame into the pre-allocated texture without allocating
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glCopyTexSubImage2D(
                GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                currentWidth, currentHeight
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

            // 3. Clear and blend outgoing FBO + incoming texture through transition shader
            GLES20.glViewport(0, 0, currentWidth, currentHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            transitionFilter?.setType(incomingClip.transitionType)
            transitionFilter?.setProgress(progress)
            transitionFilter?.drawTransition(
                textureOutId = outgoingFbo?.textureId ?: 0,
                textureInId = texId,
                texMatrix = GlUtil.IDENTITY_MATRIX,
                mvpMatrix = GlUtil.IDENTITY_MATRIX,
                width = currentWidth,
                height = currentHeight
            )
        }
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
                lastDecodedSourceUs = -1L
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to init decoder for preview", t)
                currentDecoder = null
                return
            }
        }

        val decoder = currentDecoder ?: return

        val sourceUs = clip.trimStartUs + (clipLocalUs * clip.speed).toLong()

        // Detect backward seek: if the target is behind the last decoded
        // position by more than a frame interval, reset the decoder.
        if (sourceUs < lastDecodedSourceUs - 100_000L) {
            AppLog.d(TAG, "Backward seek detected (${lastDecodedSourceUs}us -> ${sourceUs}us); resetting decoder")
            decoder.release()
            currentDecoder = VideoDecoder(context, clip, os)
            try {
                currentDecoder!!.init()
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to re-init decoder after seek", t)
                currentDecoder = null
                return
            }
            lastDecodedSourceUs = -1L
        }

        val activeDecoder = currentDecoder ?: return

        // Decode forward until we reach the target
        var lastPtsUs = lastDecodedSourceUs
        var attempts = 0
        while (attempts < 240) {
            val ptsUs = activeDecoder.decodeNextFrame()
            if (ptsUs < 0) break
            lastPtsUs = ptsUs
            if (ptsUs >= sourceUs) break
            attempts++
        }

        if (lastPtsUs < 0) return
        lastDecodedSourceUs = lastPtsUs

        val rotation = activeDecoder.videoRotation
        val rotated = rotation == 90 || rotation == 270
        val srcW = if (rotated) activeDecoder.videoHeight else activeDecoder.videoWidth
        val srcH = if (rotated) activeDecoder.videoWidth else activeDecoder.videoHeight

        buildFitMvpMatrix(srcW, srcH, currentWidth, currentHeight, mvpMatrix)
        os.getTransformMatrix(texMatrix)

        val cf = colorFilter ?: return
        cf.updateConfig(clip.filterConfig)
        cf.updateCropFlip(clip.cropRect, clip.flipHorizontal, clip.flipVertical)
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
        val cached = imageTextureCache[clip.id]
        val texId: Int
        val w: Int
        val h: Int

        if (cached != null) {
            texId = cached.first
            w = cached.second
            h = cached.third
        } else {
            val bmp = try {
                context.contentResolver.openInputStream(android.net.Uri.parse(clip.uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to load image for preview: ${clip.uri}", t)
                null
            } ?: return

            w = bmp.width
            h = bmp.height
            texId = uploadBitmapToTexture(bmp)
            bmp.recycle()
            if (texId == 0) return
            imageTextureCache[clip.id] = Triple(texId, w, h)
        }

        buildFitMvpMatrix(w, h, currentWidth, currentHeight, mvpMatrix)

        val cf = colorFilter ?: return
        cf.updateConfig(clip.filterConfig)
        cf.updateCropFlip(clip.cropRect, clip.flipHorizontal, clip.flipVertical)
        cf.draw(
            texId = texId,
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
        Matrix.scaleM(out, 0, (srcW * fit) / outW.toFloat(), (srcH * fit) / outH.toFloat(), 1f)
    }

    companion object {
        private const val TAG = "GlPreviewRenderer"
    }
}
