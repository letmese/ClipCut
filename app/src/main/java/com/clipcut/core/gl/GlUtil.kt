package com.clipcut.core.gl

import android.opengl.GLES20
import android.opengl.Matrix
import com.clipcut.core.log.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GlUtil {
    private const val TAG = "GlUtil"

    val IDENTITY_MATRIX = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        checkGlError("glCreateProgram")
        if (program == 0) {
            AppLog.e(TAG, "Could not create GL program")
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        checkGlError("glAttachShader vertex")
        GLES20.glAttachShader(program, pixelShader)
        checkGlError("glAttachShader fragment")
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(program)
            AppLog.e(TAG, "Could not link program: $log")
            GLES20.glDeleteProgram(program)
            return 0
        }

        // Clean up individual shaders once linked
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(pixelShader)
        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGlError("glCreateShader type $shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            AppLog.e(TAG, "Could not compile shader $shaderType: $log")
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = "$op: glError 0x${Integer.toHexString(error)}"
            AppLog.e(TAG, msg)
            throw RuntimeException(msg)
        }
    }

    fun checkLocation(location: Int, label: String) {
        if (location < 0) {
            AppLog.w(TAG, "Unable to locate uniform/attribute '$label' in program")
        }
    }

    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(coords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(coords)
        fb.position(0)
        return fb
    }

    fun generateTexture(target: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkGlError("glGenTextures")
        val texId = textures[0]
        GLES20.glBindTexture(target, texId)
        checkGlError("glBindTexture $texId")

        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("glTexParameter")

        return texId
    }
}
