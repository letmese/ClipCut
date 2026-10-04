package com.clipcut.core.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.view.Surface
import com.clipcut.core.log.AppLog

class OutputSurface(val textureId: Int) : SurfaceTexture.OnFrameAvailableListener {

    val surfaceTexture: SurfaceTexture = SurfaceTexture(textureId).apply {
        setOnFrameAvailableListener(this@OutputSurface)
    }
    val surface: Surface = Surface(surfaceTexture)

    private val frameSyncObject = Object()
    private var frameAvailable = false

    override fun onFrameAvailable(st: SurfaceTexture) {
        synchronized(frameSyncObject) {
            if (frameAvailable) {
                AppLog.w(TAG, "Frame available already set, frame may be dropped")
            }
            frameAvailable = true
            frameSyncObject.notifyAll()
        }
    }

    fun awaitNewImage(timeoutMs: Long = 2500): Boolean {
        synchronized(frameSyncObject) {
            while (!frameAvailable) {
                try {
                    frameSyncObject.wait(timeoutMs)
                    if (!frameAvailable) {
                        AppLog.w(TAG, "awaitNewImage timed out ($timeoutMs ms)")
                        return false
                    }
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    AppLog.e(TAG, "awaitNewImage interrupted", ie)
                    return false
                }
            }
            frameAvailable = false
        }
        // Latch the new frame into the texture
        GlUtil.checkGlError("before updateTexImage")
        surfaceTexture.updateTexImage()
        return true
    }

    fun getTransformMatrix(mtx: FloatArray) {
        surfaceTexture.getTransformMatrix(mtx)
    }

    fun release() {
        AppLog.d(TAG, "Releasing OutputSurface")
        surface.release()
        surfaceTexture.release()
    }

    companion object {
        private const val TAG = "OutputSurface"
    }
}
