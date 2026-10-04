package com.clipcut.core.gl

import android.opengl.GLES20
import com.clipcut.core.model.MaskConfig
import com.clipcut.core.model.MaskShape

class MaskFilter : GlFilter(
    vertexShaderSource = OES_VERTEX_SHADER,
    fragmentShaderSource = MASK_FRAGMENT_SHADER
) {
    var shape: Int = 0        // 0=none 1=linear 2=radial 3=rect 4=oval
    var centerX: Float = 0.5f
    var centerY: Float = 0.5f
    var width: Float = 0.6f
    var height: Float = 0.6f
    var rotation: Float = 0f
    var feather: Float = 0.05f
    var inverted: Float = 0f

    private var uShapeHandle = 0
    private var uCenterHandle = 0
    private var uSizeHandle = 0
    private var uRotationHandle = 0
    private var uFeatherHandle = 0
    private var uInvertedHandle = 0

    fun updateConfig(config: MaskConfig) {
        shape = when (config.shape) {
            MaskShape.NONE -> 0
            MaskShape.LINEAR -> 1
            MaskShape.RADIAL -> 2
            MaskShape.RECTANGLE -> 3
            MaskShape.OVAL -> 4
        }
        centerX = config.centerX
        centerY = config.centerY
        width = config.width
        height = config.height
        rotation = config.rotation
        feather = config.feather
        inverted = if (config.inverted) 1f else 0f
    }

    override fun onInitCustomUniforms() {
        uShapeHandle = GLES20.glGetUniformLocation(programHandle, "uShape")
        uCenterHandle = GLES20.glGetUniformLocation(programHandle, "uCenter")
        uSizeHandle = GLES20.glGetUniformLocation(programHandle, "uSize")
        uRotationHandle = GLES20.glGetUniformLocation(programHandle, "uRotation")
        uFeatherHandle = GLES20.glGetUniformLocation(programHandle, "uFeather")
        uInvertedHandle = GLES20.glGetUniformLocation(programHandle, "uInverted")
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform1i(uShapeHandle, shape)
        GLES20.glUniform2f(uCenterHandle, centerX, centerY)
        GLES20.glUniform2f(uSizeHandle, this.width, this.height)
        GLES20.glUniform1f(uRotationHandle, rotation)
        GLES20.glUniform1f(uFeatherHandle, feather)
        GLES20.glUniform1f(uInvertedHandle, inverted)
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

        private const val MASK_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform int uShape;
            uniform vec2 uCenter;
            uniform vec2 uSize;
            uniform float uRotation;
            uniform float uFeather;
            uniform float uInverted;

            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                if (uShape == 0) {
                    gl_FragColor = color;
                    return;
                }

                // Rotate UV around center
                vec2 uv = vTextureCoord - uCenter;
                float rad = radians(uRotation);
                float c = cos(rad);
                float s = sin(rad);
                uv = vec2(uv.x * c - uv.y * s, uv.x * s + uv.y * c);

                float alpha = 1.0;

                if (uShape == 1) {
                    // Linear: left half transparent
                    alpha = smoothstep(0.0, uFeather, -uv.x + uSize.x * 0.5);
                } else if (uShape == 2) {
                    // Radial
                    float d = length(uv);
                    alpha = 1.0 - smoothstep(uSize.x * 0.5 - uFeather, uSize.x * 0.5, d);
                } else if (uShape == 3) {
                    // Rectangle
                    float hx = uSize.x * 0.5;
                    float hy = uSize.y * 0.5;
                    float dx = abs(uv.x) - hx + uFeather;
                    float dy = abs(uv.y) - hy + uFeather;
                    alpha = 1.0 - clamp(max(dx, dy) / uFeather, 0.0, 1.0);
                } else if (uShape == 4) {
                    // Oval
                    vec2 n = uv / (uSize * 0.5);
                    float d = length(n);
                    alpha = 1.0 - smoothstep(1.0 - uFeather * 2.0, 1.0, d);
                }

                if (uInverted > 0.5) alpha = 1.0 - alpha;
                gl_FragColor = vec4(color.rgb, color.a * alpha);
            }
        """
    }
}
