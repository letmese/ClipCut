package com.clipcut.core.gl

import android.opengl.GLES20
import com.clipcut.core.log.AppLog

/**
 * An off-screen render target: framebuffer + color texture.
 * Used to capture the outgoing clip's last frame for transitions.
 */
class FramebufferTexture(val width: Int, val height: Int) {

    var textureId: Int = 0
        private set
    private var framebufferId: Int = 0

    fun init() {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        textureId = texIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        val fbIds = IntArray(1)
        GLES20.glGenFramebuffers(1, fbIds, 0)
        framebufferId = fbIds[0]

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureId, 0
        )

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            AppLog.e("FramebufferTexture", "Framebuffer incomplete: 0x${Integer.toHexString(status)}")
            throw RuntimeException("Framebuffer incomplete: 0x${Integer.toHexString(status)}")
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        AppLog.d("FramebufferTexture", "FBO created (${width}x${height}, tex=$textureId, fb=$framebufferId)")
    }

    /**
     * Binds this framebuffer as the current render target. All subsequent GL draws
     * go into the attached texture.
     */
    fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * Unbinds the framebuffer. Subsequent draws go to the default framebuffer
     * (the EGL surface).
     */
    fun unbind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun release() {
        if (framebufferId != 0) {
            val arr = intArrayOf(framebufferId)
            GLES20.glDeleteFramebuffers(1, arr, 0)
            framebufferId = 0
        }
        if (textureId != 0) {
            val arr = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, arr, 0)
            textureId = 0
        }
        AppLog.d("FramebufferTexture", "FBO released")
    }
}
