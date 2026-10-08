package com.clipcut.core.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.clipcut.core.log.AppLog

class EglCore(
    sharedContext: EGLContext? = null,
    flags: Int = FLAG_RECORDABLE
) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    @Volatile private var released = false

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL_RECORDABLE_ANDROID, if (flags and FLAG_RECORDABLE != 0) 1 else 0,
            EGL14.EGL_NONE
        )
        val ok = EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        check(ok && numConfigs[0] > 0) { "eglChooseConfig returned 0 configs" }

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], sharedContext ?: EGL14.EGL_NO_CONTEXT, ctxAttribs, 0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        eglConfig = configs[0]
    }

    fun release() {
        if (released) return
        released = true
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
    }

    fun createWindowSurface(surface: Any): EGLSurface {
        check(!released) { "EglCore already released" }
        val attrs = intArrayOf(EGL14.EGL_NONE)
        val s = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attrs, 0)
        check(s != null && s != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        return s
    }

    fun createOffscreenSurface(w: Int, h: Int): EGLSurface {
        val attrs = intArrayOf(EGL14.EGL_WIDTH, w, EGL14.EGL_HEIGHT, h, EGL14.EGL_NONE)
        val s = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, attrs, 0)
        check(s != null && s != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        return s
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        if (released) return
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    fun makeNothingCurrent() {
        if (released) return
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext)
    }

    fun swapBuffers(eglSurface: EGLSurface): Boolean {
        if (released) return false
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) {
        if (released) return
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
    }

    fun destroySurface(eglSurface: EGLSurface?) {
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) return
        if (released) return
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    companion object {
        const val FLAG_RECORDABLE = 0x01
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}

class InputSurface(val surface: Surface) {
    private val eglCore = EglCore(flags = EglCore.FLAG_RECORDABLE)
    private val eglSurface: EGLSurface = eglCore.createWindowSurface(surface)

    fun makeCurrent() = eglCore.makeCurrent(eglSurface)
    fun swapBuffers(): Boolean = eglCore.swapBuffers(eglSurface)
    fun setPresentationTime(nsecs: Long) = eglCore.setPresentationTime(eglSurface, nsecs)

    fun release() {
        try { eglCore.makeNothingCurrent() } catch (_: Throwable) {}
        try { eglCore.destroySurface(eglSurface) } catch (_: Throwable) {}
        try { eglCore.release() } catch (_: Throwable) {}
        try { surface.release() } catch (_: Throwable) {}
    }
}

class OutputSurface(val textureId: Int) : SurfaceTexture.OnFrameAvailableListener {
    private val handlerThread = HandlerThread("OutputSurfaceThread").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val surfaceTexture = SurfaceTexture(textureId).also { it.setOnFrameAvailableListener(this, handler) }
    val surface: Surface = Surface(surfaceTexture)

    @Volatile private var frameAvailable = false
    @Volatile private var released = false
    private val lock = Object()
    var onFrameAvailableCallback: (() -> Unit)? = null

    override fun onFrameAvailable(st: SurfaceTexture) {
        synchronized(lock) { frameAvailable = true; lock.notifyAll() }
        onFrameAvailableCallback?.invoke()
    }

    fun awaitNewImage(timeoutMs: Long = 1000): Boolean {
        synchronized(lock) {
            if (released) return false
            if (!frameAvailable) {
                try { lock.wait(timeoutMs) } catch (_: InterruptedException) { return false }
            }
            if (!frameAvailable || released) return false
            frameAvailable = false
        }
        return try {
            surfaceTexture.updateTexImage()
            true
        } catch (t: Throwable) {
            AppLog.w("OutputSurface", "updateTexImage failed: ${t.message}")
            false
        }
    }

    fun getTransformMatrix(mtx: FloatArray) {
        try { surfaceTexture.getTransformMatrix(mtx) } catch (_: Throwable) {}
    }

    fun release() {
        synchronized(lock) { released = true; lock.notifyAll() }
        try { surfaceTexture.setOnFrameAvailableListener(null) } catch (_: Throwable) {}
        try { surface.release() } catch (_: Throwable) {}
        try { surfaceTexture.release() } catch (_: Throwable) {}
        try { handlerThread.quitSafely() } catch (_: Throwable) {}
    }
}

class FramebufferTexture(val width: Int, val height: Int) {
    val textureId: Int = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
    private var fboName = -1

    fun init() {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        fboName = fbo[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboName)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureId, 0)
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            AppLog.e("FramebufferTexture", "FBO incomplete: 0x${Integer.toHexString(status)}")
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboName)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureId, 0)
    }

    fun unbind() { GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) }

    fun release() {
        unbind()
        if (fboName != -1) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fboName), 0)
            fboName = -1
        }
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}
