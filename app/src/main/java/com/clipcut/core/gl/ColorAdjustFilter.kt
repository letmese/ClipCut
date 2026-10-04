package com.clipcut.core.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.clipcut.core.model.FilterConfig
import com.clipcut.core.model.FilterType

import android.opengl.Matrix

class ColorAdjustFilter : GlFilter(
    vertexShaderSource = OES_VERTEX_SHADER,
    fragmentShaderSource = OES_FRAGMENT_SHADER
) {

    var brightness: Float = 0.0f
    var contrast: Float = 1.0f
    var saturation: Float = 1.0f
    var temperature: Float = 0.0f
    var filterType: FilterType = FilterType.NONE

    private var uBrightnessHandle = 0
    private var uContrastHandle = 0
    private var uSaturationHandle = 0
    private var uTemperatureHandle = 0
    private var uFilterModeHandle = 0

    fun updateConfig(config: FilterConfig) {
        this.filterType = config.filterType
        this.brightness = config.brightness
        this.contrast = config.contrast
        this.saturation = config.saturation
        this.temperature = config.temperature
    }

    override fun onInitCustomUniforms() {
        uBrightnessHandle = GLES20.glGetUniformLocation(programHandle, "uBrightness")
        uContrastHandle = GLES20.glGetUniformLocation(programHandle, "uContrast")
        uSaturationHandle = GLES20.glGetUniformLocation(programHandle, "uSaturation")
        uTemperatureHandle = GLES20.glGetUniformLocation(programHandle, "uTemperature")
        uFilterModeHandle = GLES20.glGetUniformLocation(programHandle, "uFilterMode")
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform1f(uBrightnessHandle, brightness)
        GLES20.glUniform1f(uContrastHandle, contrast)
        GLES20.glUniform1f(uSaturationHandle, saturation)
        GLES20.glUniform1f(uTemperatureHandle, temperature)

        val mode = when (filterType) {
            FilterType.NONE -> 0
            FilterType.BRIGHTNESS, FilterType.CONTRAST, FilterType.SATURATION, FilterType.TEMPERATURE -> 0
            FilterType.GRAYSCALE -> 1
            FilterType.SEPIA -> 2
            FilterType.VIGNETTE -> 3
        }
        GLES20.glUniform1i(uFilterModeHandle, mode)
    }

    // Scratch matrices — allocated once, reused every frame
    private val rotationMatrix = FloatArray(16)
    private val finalTexMatrix = FloatArray(16)

    fun drawOes(
        texId: Int,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        rotationDegrees: Int = 0,
        width: Int,
        height: Int
    ) {
        val matrixToUse: FloatArray = if (rotationDegrees != 0) {
            // Build a rotation about the texture center (UV space 0..1).
            // Positive rotationDegrees = clockwise on screen.
            Matrix.setIdentityM(rotationMatrix, 0)
            Matrix.translateM(rotationMatrix, 0, 0.5f, 0.5f, 0f)
            Matrix.rotateM(rotationMatrix, 0, -rotationDegrees.toFloat(), 0f, 0f, 1f)
            Matrix.translateM(rotationMatrix, 0, -0.5f, -0.5f, 0f)

            // finalTexMatrix = rotation * texMatrix
            // multiplyMM(result, resultOffset, lhs, lhsOffset, rhs, rhsOffset) = lhs * rhs
            Matrix.multiplyMM(finalTexMatrix, 0, rotationMatrix, 0, texMatrix, 0)
            finalTexMatrix
        } else {
            texMatrix
        }

        draw(
            texId = texId,
            texMatrix = matrixToUse,
            mvpMatrix = mvpMatrix,
            width = width,
            height = height,
            textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        )
    }

    companion object {
        const val OES_VERTEX_SHADER = """
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

        const val OES_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;

            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uTemperature;
            uniform int uFilterMode;

            const vec3 W = vec3(0.2125, 0.7154, 0.0721);

            void main() {
                vec4 textureColor = texture2D(sTexture, vTextureCoord);
                vec3 color = textureColor.rgb;

                // 1. Brightness
                color += vec3(uBrightness);

                // 2. Contrast
                color = clamp(((color - vec3(0.5)) * uContrast + vec3(0.5)), 0.0, 1.0);

                // 3. Saturation
                float luminance = dot(color, W);
                color = clamp(mix(vec3(luminance), color, uSaturation), 0.0, 1.0);

                // 4. Color Temperature (warmth/cool shift)
                if (uTemperature != 0.0) {
                    color.r = clamp(color.r + uTemperature * 0.1, 0.0, 1.0);
                    color.b = clamp(color.b - uTemperature * 0.1, 0.0, 1.0);
                }

                // 5. Stylized filter modes
                if (uFilterMode == 1) {
                    // Grayscale
                    float gray = dot(color, W);
                    color = vec3(gray);
                } else if (uFilterMode == 2) {
                    // Sepia
                    float r = dot(color, vec3(0.393, 0.769, 0.189));
                    float g = dot(color, vec3(0.349, 0.686, 0.168));
                    float b = dot(color, vec3(0.272, 0.534, 0.131));
                    color = clamp(vec3(r, g, b), 0.0, 1.0);
                } else if (uFilterMode == 3) {
                    // Vignette
                    vec2 coord = vTextureCoord - vec2(0.5);
                    float dist = length(coord);
                    float vignette = smoothstep(0.7, 0.35, dist);
                    color *= vignette;
                }

                gl_FragColor = vec4(color, textureColor.a);
            }
        """
    }
}
