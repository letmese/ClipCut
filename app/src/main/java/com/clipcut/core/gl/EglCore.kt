package com.clipcut.core.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface

/**
 * EglCore — minimal EGL 1.4 context+surface manager.
 *  - createWindowSurface(Surface) for MediaCodec encoder input or TextureView
 *  - createOffscreenSurface(w,h) for headless filtering passes
 *
 * FLAG_RECORDABLE is required when the EGLSurface will be consumed by
 * MediaCodec.configure(...) with a Surface; without it encoder input
 * glReadPixels / swapBuffers can silently produce black frames.
 */
class EglCore(
    sharedContext: EGLContext? = null,
    flags: Int = FLAG_RECORDABLE
) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var glVersion = -1

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay !== EGL14.EGL_NO_DISPLAY) { "no EGL display" }
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
        glVersion = version[0]

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, if (flags and FLAG_RECORDABLE != 0) EGL14.EGL_OPENGL_ES2_BIT else EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_NONE
        )
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)

        val ctxAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0],
            sharedContext ?: EGL14.EGL_NO_CONTEXT, ctxAttribs, 0
        )
        check(eglContext !== EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        eglConfig = configs[0]
    }

    fun release() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
    }

    fun createWindowSurface(surface: Any): EGLSurface {
        val attrs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attrs, 0)
    }

    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val attrs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        return EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, attrs, 0)
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    fun makeNothingCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext)
    }

    fun swapBuffers(eglSurface: EGLSurface): Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
    }

    fun destroySurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    companion object {
        const val FLAG_RECORDABLE = 0x01
    }
}

/**
 * InputSurface — the Surface handed to MediaCodec.createInputSurface().
 * Used by VideoEncoder.  MediaCodec frames then flow through the EglCore
 * context owned by this surface.
 */
class InputSurface(val surface: Surface) {
    private val eglCore = EglCore(flags = EglCore.FLAG_RECORDABLE)
    private val eglSurface: EGLSurface = eglCore.createWindowSurface(surface)

    fun makeCurrent() = eglCore.makeCurrent(eglSurface)
    fun swapBuffers(): Boolean = eglCore.swapBuffers(eglSurface)
    fun setPresentationTime(nsecs: Long) = eglCore.setPresentationTime(eglSurface, nsecs)
    fun release() {
        eglCore.destroySurface(eglSurface)
        eglCore.release()
        surface.release()
    }
}

/**
 * OutputSurface — receives decoded frames from MediaCodec via the
 * SurfaceTexture / SurfaceTexture.OnFrameAvailableListener callback.
 * The texture is created by an external caller and passed in.
 */
class OutputSurface(val textureId: Int) : SurfaceTexture.OnFrameAvailableListener {
    private val surfaceTexture = SurfaceTexture(textureId).also { it.setOnFrameAvailableListener(this) }
    val surface: Surface = Surface(surfaceTexture)

    @Volatile private var frameAvailable = false
    private val lock = Object()

    override fun onFrameAvailable(st: SurfaceTexture) {
        synchronized(lock) { frameAvailable = true; lock.notify() }
    }

    /** Block until a new frame is available, then update its texture. */
    fun awaitNewImage(timeoutMs: Long = 1000): Boolean {
        synchronized(lock) {
            if (!frameAvailable) {
                try { lock.wait(timeoutMs) } catch (_: InterruptedException) {}
            }
            if (!frameAvailable) return false
            frameAvailable = false
        }
        return try {
            surfaceTexture.updateTexImage()
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getTransformMatrix(mtx: FloatArray) = surfaceTexture.getTransformMatrix(mtx)

    fun release() {
        try {
            surface.release()
        } catch (_: Throwable) {}
        try {
            surfaceTexture.release()
        } catch (_: Throwable) {}
    }
}

/**
 * FramebufferTexture — an FBO with a single RGBA color attachment.
 * Used by PipelineRenderer.applyPostProcessing for multi-pass filtering.
 */
class FramebufferTexture(val width: Int, val height: Int) {
    val textureId: Int = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
    private var fboName = -1

    fun init() {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        fboName = fbo[0]
    }

    fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboName)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureId, 0)
    }

    fun unbind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun release() {
        GLES20.glDeleteFramebuffers(1, intArrayOf(fboName), 0)
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}
