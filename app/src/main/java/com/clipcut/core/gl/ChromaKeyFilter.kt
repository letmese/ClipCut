package com.clipcut.core.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix

class ChromaKeyFilter(
    var keyColor: FloatArray = floatArrayOf(0f, 1f, 0f),
    var threshold: Float = 0.4f,
    var smoothness: Float = 0.1f
) : GlFilter(
    vertexShaderSource = OES_VERTEX_SHADER,
    fragmentShaderSource = CHROMA_FRAGMENT_SHADER
) {
    private var uKeyColorHandle = 0
    private var uThresholdHandle = 0
    private var uSmoothnessHandle = 0

    private val rotationMatrix = FloatArray(16)
    private val finalTexMatrix = FloatArray(16)

    override fun onInitCustomUniforms() {
        uKeyColorHandle = GLES20.glGetUniformLocation(programHandle, "uKeyColor")
        uThresholdHandle = GLES20.glGetUniformLocation(programHandle, "uThreshold")
        uSmoothnessHandle = GLES20.glGetUniformLocation(programHandle, "uSmoothness")
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform3f(uKeyColorHandle, keyColor[0], keyColor[1], keyColor[2])
        GLES20.glUniform1f(uThresholdHandle, threshold)
        GLES20.glUniform1f(uSmoothnessHandle, smoothness)
    }

    fun drawOes(
        texId: Int,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        rotationDegrees: Int = 0,
        width: Int,
        height: Int
    ) {
        val matrixToUse = if (rotationDegrees != 0) {
            Matrix.setIdentityM(rotationMatrix, 0)
            Matrix.translateM(rotationMatrix, 0, 0.5f, 0.5f, 0f)
            Matrix.rotateM(rotationMatrix, 0, -rotationDegrees.toFloat(), 0f, 0f, 1f)
            Matrix.translateM(rotationMatrix, 0, -0.5f, -0.5f, 0f)
            Matrix.multiplyMM(finalTexMatrix, 0, rotationMatrix, 0, texMatrix, 0)
            finalTexMatrix
        } else texMatrix

        draw(texId, matrixToUse, mvpMatrix, width, height, GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
    }

    companion object {
        private const val OES_VERTEX_SHADER = """
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

        private const val CHROMA_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform vec3 uKeyColor;
            uniform float uThreshold;
            uniform float uSmoothness;

            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                float diff = distance(color.rgb, uKeyColor);
                float alpha = smoothstep(uThreshold, uThreshold + uSmoothness, diff);
                gl_FragColor = vec4(color.rgb, color.a * alpha);
            }
        """
    }
}
