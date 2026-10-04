package com.clipcut.core.gl

import android.opengl.GLES20
import com.clipcut.core.log.AppLog
import java.nio.FloatBuffer

abstract class GlFilter(
    protected val vertexShaderSource: String = DEFAULT_VERTEX_SHADER,
    protected val fragmentShaderSource: String = DEFAULT_FRAGMENT_SHADER
) {

    protected var programHandle: Int = 0
    protected var maPositionHandle: Int = 0
    protected var maTextureHandle: Int = 0
    protected var muMVPMatrixHandle: Int = 0
    protected var muTexMatrixHandle: Int = 0

    private val vertexBuffer: FloatBuffer = GlUtil.createFloatBuffer(VERTEX_COORDINATES)
    private val textureBuffer: FloatBuffer = GlUtil.createFloatBuffer(TEXTURE_COORDINATES)

    open fun init() {
        if (programHandle != 0) return
        programHandle = GlUtil.createProgram(vertexShaderSource, fragmentShaderSource)
        if (programHandle == 0) {
            throw RuntimeException("Failed to initialize GL program for filter ${javaClass.simpleName}")
        }

        maPositionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
        GlUtil.checkLocation(maPositionHandle, "aPosition")

        maTextureHandle = GLES20.glGetAttribLocation(programHandle, "aTextureCoord")
        GlUtil.checkLocation(maTextureHandle, "aTextureCoord")

        muMVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
        GlUtil.checkLocation(muMVPMatrixHandle, "uMVPMatrix")

        muTexMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
        GlUtil.checkLocation(muTexMatrixHandle, "uTexMatrix")

        onInitCustomUniforms()
        AppLog.d(TAG, "Initialized filter: ${javaClass.simpleName}")
    }

    /**
     * Subclasses override this to resolve custom uniform/attribute locations.
     */
    protected open fun onInitCustomUniforms() {}

    /**
     * Subclasses override this to bind values to their custom uniforms before drawing.
     */
    protected open fun onSetCustomUniforms(width: Int, height: Int) {}

    /**
     * Draw the texture onto the current framebuffer/EGL surface.
     */
    open fun draw(
        texId: Int,
        texMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        mvpMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        width: Int,
        height: Int,
        textureTarget: Int = GLES20.GL_TEXTURE_2D
    ) {
        if (programHandle == 0) {
            init()
        }

        GLES20.glUseProgram(programHandle)
        GlUtil.checkGlError("glUseProgram")

        // Bind MVP & Tex matrices
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muTexMatrixHandle, 1, false, texMatrix, 0)

        // Set custom uniforms
        onSetCustomUniforms(width, height)

        // Setup vertex attributes
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
        GLES20.glEnableVertexAttribArray(maPositionHandle)

        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)
        GLES20.glEnableVertexAttribArray(maTextureHandle)

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, texId)

        // Draw arrays
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError("glDrawArrays")

        GLES20.glDisableVertexAttribArray(maPositionHandle)
        GLES20.glDisableVertexAttribArray(maTextureHandle)
        GLES20.glBindTexture(textureTarget, 0)
        GLES20.glUseProgram(0)
    }

    open fun release() {
        if (programHandle != 0) {
            GLES20.glDeleteProgram(programHandle)
            programHandle = 0
            AppLog.d(TAG, "Released filter: ${javaClass.simpleName}")
        }
    }

    companion object {
        private const val TAG = "GlFilter"

        // Full screen quad (-1 to 1)
        private val VERTEX_COORDINATES = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )

        // Texture coordinates (0 to 1)
        private val TEXTURE_COORDINATES = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        const val DEFAULT_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        const val DEFAULT_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }
}
