package com.clipcut.core.gl

import android.opengl.GLES20
import com.clipcut.core.model.BlendMode

/**
 * Blends two textures: a base layer and a top layer.
 * The base layer is what's already on screen (previous frame, background, etc.);
 * the top layer is the current clip's contribution.
 *
 * When a clip uses a non-NORMAL blend mode, ExportEngine and GlPreviewRenderer
 * must render the clip to an FBO first, then call drawBlend() with the FBO
 * texture as the top layer.
 */
class BlendFilter : GlFilter(
    vertexShaderSource = DEFAULT_VERTEX_SHADER,
    fragmentShaderSource = BLEND_FRAGMENT_SHADER
) {
    var mode: Int = 0
        private set

    private var uModeHandle = 0
    private var uBaseTextureHandle = 0
    private var uTopTextureHandle = 0

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
        uBaseTextureHandle = GLES20.glGetUniformLocation(programHandle, "sBaseTexture")
        uTopTextureHandle = GLES20.glGetUniformLocation(programHandle, "sTopTexture")
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform1i(uModeHandle, mode)
    }

    /**
     * Composites base and top textures using the current blend mode.
     * The result is written to the currently-bound framebuffer.
     */
    fun drawBlend(
        baseTexId: Int,
        topTexId: Int,
        texMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        mvpMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        width: Int,
        height: Int
    ) {
        if (programHandle == 0) init()

        GLES20.glUseProgram(programHandle)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muTexMatrixHandle, 1, false, texMatrix, 0)
        GLES20.glUniform1i(uModeHandle, mode)

        // Base layer to unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, baseTexId)
        GLES20.glUniform1i(uBaseTextureHandle, 0)

        // Top layer to unit 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, topTexId)
        GLES20.glUniform1i(uTopTextureHandle, 1)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
        GLES20.glEnableVertexAttribArray(maPositionHandle)

        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)
        GLES20.glEnableVertexAttribArray(maTextureHandle)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(maPositionHandle)
        GLES20.glDisableVertexAttribArray(maTextureHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    private val vertexBuffer = GlUtil.createFloatBuffer(floatArrayOf(
        -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
    ))

    private val textureBuffer = GlUtil.createFloatBuffer(floatArrayOf(
        0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f
    ))

    companion object {
        private const val BLEND_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sBaseTexture;
            uniform sampler2D sTopTexture;
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
                if (m == 5) {
                    return mix(
                        2.0 * base * top,
                        1.0 - 2.0 * (1.0 - base) * (1.0 - top),
                        step(0.5, top)
                    );
                }
                if (m == 6) return 1.0 - (1.0 - base) / max(top, vec3(0.001));
                if (m == 7) return base / max(1.0 - top, vec3(0.001));
                if (m == 8) return min(base, top);
                if (m == 9) return max(base, top);
                if (m == 10) return abs(base - top);
                if (m == 11) return base + top - 2.0 * base * top;
                return top;
            }

            void main() {
                vec4 base = texture2D(sBaseTexture, vTextureCoord);
                vec4 top  = texture2D(sTopTexture,  vTextureCoord);

                // Normal mode: just output top with its alpha
                if (uMode == 0) {
                    gl_FragColor = top;
                    return;
                }

                // Blended mode: blend RGB, preserve top's alpha so
                // transparent regions of the top layer stay transparent.
                vec3 blended = blend(base.rgb, top.rgb, uMode);
                gl_FragColor = vec4(blended, top.a);
            }
        """
    }
}
