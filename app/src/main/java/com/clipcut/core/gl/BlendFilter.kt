package com.clipcut.core.gl

import android.opengl.GLES20
import com.clipcut.core.model.BlendMode

class BlendFilter : GlFilter(
    vertexShaderSource = OES_VERTEX_SHADER,
    fragmentShaderSource = BLEND_FRAGMENT_SHADER
) {
    var mode: Int = 0
    private var uModeHandle = 0

    fun updateMode(blendMode: BlendMode) {
        mode = when (blendMode) {
            BlendMode.NORMAL -> 0
            BlendMode.MULTIPLY -> 1
            BlendMode.SCREEN -> 2
            BlendMode.OVERLAY -> 3
            BlendMode.SOFT_LIGHT -> 4
            BlendMode.HARD_LIGHT -> 5
            BlendMode.COLOR_BURN -> 6
            BlendMode.COLOR_DODGE -> 7
            BlendMode.DARKEN -> 8
            BlendMode.LIGHTEN -> 9
            BlendMode.DIFFERENCE -> 10
            BlendMode.EXCLUSION -> 11
        }
    }

    override fun onInitCustomUniforms() {
        uModeHandle = GLES20.glGetUniformLocation(programHandle, "uMode")
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform1i(uModeHandle, mode)
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

        private const val BLEND_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform int uMode;

            vec3 blend(vec3 base, vec3 top, int m) {
                if (m == 1) return base * top;
                if (m == 2) return 1.0 - (1.0 - base) * (1.0 - top);
                if (m == 3) {
                    return mix(
                        2.0 * base * top,
                        1.0 - 2.0 * (1.0 - base) * (1.0 - top),
                        step(0.5, base)
                    );
                }
                if (m == 4) {
                    vec3 d = mix(
                        ((16.0 * base - 12.0) * base + 4.0) * base,
                        sqrt(base),
                        step(0.25, base)
                    );
                    return mix(
                        base - (1.0 - 2.0 * top) * base * (1.0 - base),
                        base + (2.0 * top - 1.0) * (d - base),
                        step(0.5, top)
                    );
                }
                if (m == 5) return mix(2.0 * base * top, 1.0 - 2.0 * (1.0 - base) * (1.0 - top), step(0.5, top));
                if (m == 6) return 1.0 - (1.0 - base) / max(top, vec3(0.001));
                if (m == 7) return base / max(1.0 - top, vec3(0.001));
                if (m == 8) return min(base, top);
                if (m == 9) return max(base, top);
                if (m == 10) return abs(base - top);
                if (m == 11) return base + top - 2.0 * base * top;
                return top;
            }

            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                gl_FragColor = color;
            }
        """
    }
}
