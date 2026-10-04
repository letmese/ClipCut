package com.clipcut.core.gl

import android.graphics.SurfaceTexture
import android.opengl.*
import android.view.Surface
import com.clipcut.core.log.AppLog

class EglCore(sharedContext: EGLContext? = null, flags: Int = FLAG_RECORDABLE) {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("unable to initialize EGL14")
        }

        val config = getConfig(flags) ?: throw RuntimeException("Unable to find a suitable EGLConfig")
        eglConfig = config

        val attrib2List = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )

        val context = EGL14.eglCreateContext(
            eglDisplay, config,
            sharedContext ?: EGL14.EGL_NO_CONTEXT,
            attrib2List, 0
        )
        checkEglError("eglCreateContext")
        eglContext = context
        AppLog.d(TAG, "EglCore initialized (OpenGL ES 2.0 context created)")
    }

    private fun getConfig(flags: Int): EGLConfig? {
        val renderableType = EGL14.EGL_OPENGL_ES2_BIT
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            if ((flags and FLAG_RECORDABLE) != 0) EGL_RECORDABLE_ANDROID else EGL14.EGL_NONE,
            if ((flags and FLAG_RECORDABLE) != 0) 1 else EGL14.EGL_NONE,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
            AppLog.w(TAG, "unable to find RGBA8888 / $renderableType EGLConfig")
            return null
        }
        return configs[0]
    }

    fun release() {
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
        AppLog.d(TAG, "EglCore released")
    }

    fun createWindowSurface(surface: Any): EGLSurface {
        if (surface !is Surface && surface !is SurfaceTexture) {
            throw RuntimeException("invalid surface: $surface")
        }
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, surface, surfaceAttribs, 0
        )
        checkEglError("eglCreateWindowSurface")
        if (eglSurface == null || eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("surface was null")
        }
        return eglSurface
    }

    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        val eglSurface = EGL14.eglCreatePbufferSurface(
            eglDisplay, eglConfig, surfaceAttribs, 0
        )
        checkEglError("eglCreatePbufferSurface")
        if (eglSurface == null || eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("pbuffer surface was null")
        }
        return eglSurface
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            AppLog.w(TAG, "makeCurrent called with no display")
            return
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun makeNothingCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            throw RuntimeException("eglMakeNothingCurrent failed")
        }
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

    private fun checkEglError(msg: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            val errStr = "$msg: EGL error: 0x${Integer.toHexString(error)}"
            AppLog.e(TAG, errStr)
            throw RuntimeException(errStr)
        }
    }

    companion object {
        private const val TAG = "EglCore"
        const val FLAG_RECORDABLE = 0x01
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
