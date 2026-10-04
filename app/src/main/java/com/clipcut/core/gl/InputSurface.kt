package com.clipcut.core.gl

import android.opengl.EGLSurface
import android.view.Surface
import com.clipcut.core.log.AppLog

class InputSurface(val surface: Surface) {

    private val eglCore: EglCore = EglCore()
    private var eglSurface: EGLSurface = eglCore.createWindowSurface(surface)

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface)
    }

    fun setPresentationTime(nsecs: Long) {
        eglCore.setPresentationTime(eglSurface, nsecs)
    }

    fun release() {
        AppLog.d("InputSurface", "Releasing InputSurface")
        eglCore.destroySurface(eglSurface)
        eglCore.release()
        surface.release()
    }
}
