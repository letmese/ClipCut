package com.clipcut.core.gl

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import com.clipcut.core.log.AppLog

class LutFilter(
    private val context: Context,
    private val lutUri: String,
    var intensity: Float = 1.0f
) : GlFilter(
    vertexShaderSource = DEFAULT_VERTEX_SHADER,
    fragmentShaderSource = LUT_FRAGMENT_SHADER
) {
    private var uLutTextureHandle = 0
    private var uIntensityHandle = 0
    private var lutTextureId = 0

    override fun init() {
        super.init()
        uLutTextureHandle = GLES20.glGetUniformLocation(programHandle, "uLutTexture")
        uIntensityHandle = GLES20.glGetUniformLocation(programHandle, "uIntensity")
        loadLut()
    }

    private fun loadLut() {
        try {
            val bitmap = context.contentResolver.openInputStream(Uri.parse(lutUri))?.use {
                BitmapFactory.decodeStream(it)
            } ?: return

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            lutTextureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            bitmap.recycle()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load LUT", e)
        }
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        if (lutTextureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTextureId)
            GLES20.glUniform1i(uLutTextureHandle, 1)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
        GLES20.glUniform1f(uIntensityHandle, intensity)
    }

    override fun release() {
        super.release()
        if (lutTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            lutTextureId = 0
        }
    }

    companion object {
        private const val TAG = "LutFilter"

        private const val LUT_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            uniform sampler2D uLutTexture;
            uniform float uIntensity;

            const float LUT_SIZE = 8.0;
            const float LUT_SCALE = 7.0 / 8.0;
            const float LUT_OFFSET = 0.5 / 8.0;

            vec3 sampleLut(vec3 color) {
                float blue = color.b * LUT_SCALE + LUT_OFFSET;
                vec2 uv = vec2(
                    (color.r * LUT_SCALE + LUT_OFFSET) / LUT_SIZE,
                    (color.g * LUT_SCALE + LUT_OFFSET) / LUT_SIZE
                );
                float slice = floor(blue * LUT_SIZE);
                float frac = blue * LUT_SIZE - slice;
                vec2 uv1 = uv + vec2(slice / LUT_SIZE, 0.0);
                vec2 uv2 = uv + vec2((slice + 1.0) / LUT_SIZE, 0.0);
                vec3 c1 = texture2D(uLutTexture, uv1).rgb;
                vec3 c2 = texture2D(uLutTexture, uv2).rgb;
                return mix(c1, c2, frac);
            }

            void main() {
                vec4 original = texture2D(sTexture, vTextureCoord);
                vec3 graded = sampleLut(original.rgb);
                vec3 final = mix(original.rgb, graded, uIntensity);
                gl_FragColor = vec4(final, original.a);
            }
        """
    }
}
