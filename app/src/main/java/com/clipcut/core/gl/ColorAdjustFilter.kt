package com.clipcut.core.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.clipcut.core.model.CropRect
import com.clipcut.core.model.FilterConfig
import com.clipcut.core.model.FilterType

class ColorAdjustFilter : GlFilter(
    vertexShaderSource = OES_VERTEX_SHADER,
    fragmentShaderSource = OES_FRAGMENT_SHADER
) {
    var brightness: Float = 0.0f
    var contrast: Float = 1.0f
    var saturation: Float = 1.0f
    var temperature: Float = 0.0f
    var filterType: FilterType = FilterType.NONE

    // Crop (in UV space)
    var cropLeft: Float = 0f
    var cropTop: Float = 0f
    var cropRight: Float = 1f
    var cropBottom: Float = 1f

    // Flip
    var flipHorizontal: Boolean = false
    var flipVertical: Boolean = false

    private var uBrightness = 0; private var uContrast = 0
    private var uSaturation = 0; private var uTemperature = 0
    private var uFilterMode = 0
    private var uCropL = 0; private var uCropT = 0
    private var uCropR = 0; private var uCropB = 0
    private var uFlipH = 0; private var uFlipV = 0

    fun updateConfig(c: FilterConfig) {
        filterType = c.filterType; brightness = c.brightness
        contrast = c.contrast; saturation = c.saturation; temperature = c.temperature
    }

    fun updateCropFlip(crop: CropRect, flipH: Boolean, flipV: Boolean) {
        cropLeft = crop.left; cropTop = crop.top
        cropRight = crop.right; cropBottom = crop.bottom
        flipHorizontal = flipH; flipVertical = flipV
    }

    override fun onInitCustomUniforms() {
        uBrightness = GLES20.glGetUniformLocation(programHandle, "uBrightness")
        uContrast = GLES20.glGetUniformLocation(programHandle, "uContrast")
        uSaturation = GLES20.glGetUniformLocation(programHandle, "uSaturation")
        uTemperature = GLES20.glGetUniformLocation(programHandle, "uTemperature")
        uFilterMode = GLES20.glGetUniformLocation(programHandle, "uFilterMode")
        uCropL = GLES20.glGetUniformLocation(programHandle, "uCropL")
        uCropT = GLES20.glGetUniformLocation(programHandle, "uCropT")
        uCropR = GLES20.glGetUniformLocation(programHandle, "uCropR")
        uCropB = GLES20.glGetUniformLocation(programHandle, "uCropB")
        uFlipH = GLES20.glGetUniformLocation(programHandle, "uFlipH")
        uFlipV = GLES20.glGetUniformLocation(programHandle, "uFlipV")
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform1f(uBrightness, brightness)
        GLES20.glUniform1f(uContrast, contrast)
        GLES20.glUniform1f(uSaturation, saturation)
        GLES20.glUniform1f(uTemperature, temperature)
        val mode = when (filterType) {
            FilterType.NONE -> 0
            FilterType.BRIGHTNESS, FilterType.CONTRAST,
            FilterType.SATURATION, FilterType.TEMPERATURE -> 0
            FilterType.GRAYSCALE -> 1; FilterType.SEPIA -> 2; FilterType.VIGNETTE -> 3
        }
        GLES20.glUniform1i(uFilterMode, mode)
        GLES20.glUniform1f(uCropL, cropLeft)
        GLES20.glUniform1f(uCropT, cropTop)
        GLES20.glUniform1f(uCropR, cropRight)
        GLES20.glUniform1f(uCropB, cropBottom)
        GLES20.glUniform1f(uFlipH, if (flipHorizontal) 1f else 0f)
        GLES20.glUniform1f(uFlipV, if (flipVertical) 1f else 0f)
    }

    private val rotationMatrix = FloatArray(16)
    private val finalTexMatrix = FloatArray(16)

    fun drawOes(
        texId: Int, texMatrix: FloatArray,
        mvpMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        rotationDegrees: Int = 0,
        width: Int, height: Int
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

            uniform float uCropL;
            uniform float uCropT;
            uniform float uCropR;
            uniform float uCropB;
            uniform float uFlipH;
            uniform float uFlipV;

            const vec3 W = vec3(0.2125, 0.7154, 0.0721);

            void main() {
                // Map [0,1] to crop rect
                vec2 cropUv = vTextureCoord;
                cropUv.x = uCropL + cropUv.x * (uCropR - uCropL);
                cropUv.y = uCropT + cropUv.y * (uCropB - uCropT);
                if (uFlipH > 0.5) cropUv.x = uCropL + uCropR - cropUv.x;
                if (uFlipV > 0.5) cropUv.y = uCropT + uCropB - cropUv.y;

                vec4 textureColor = texture2D(sTexture, cropUv);
                vec3 color = textureColor.rgb;

                color += vec3(uBrightness);
                color = clamp(((color - vec3(0.5)) * uContrast + vec3(0.5)), 0.0, 1.0);
                float lum = dot(color, W);
                color = clamp(mix(vec3(lum), color, uSaturation), 0.0, 1.0);

                if (uTemperature != 0.0) {
                    color.r = clamp(color.r + uTemperature * 0.1, 0.0, 1.0);
                    color.b = clamp(color.b - uTemperature * 0.1, 0.0, 1.0);
                }

                if (uFilterMode == 1) {
                    float g = dot(color, W); color = vec3(g);
                } else if (uFilterMode == 2) {
                    float r = dot(color, vec3(0.393, 0.769, 0.189));
                    float g = dot(color, vec3(0.349, 0.686, 0.168));
                    float b = dot(color, vec3(0.272, 0.534, 0.131));
                    color = clamp(vec3(r, g, b), 0.0, 1.0);
                } else if (uFilterMode == 3) {
                    vec2 c = vTextureCoord - vec2(0.5);
                    float d = length(c);
                    float v = smoothstep(0.7, 0.35, d);
                    color *= v;
                }

                gl_FragColor = vec4(color, textureColor.a);
            }
        """
    }
}
