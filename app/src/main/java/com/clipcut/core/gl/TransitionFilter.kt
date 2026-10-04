package com.clipcut.core.gl

import android.opengl.GLES20
import com.clipcut.core.model.TransitionType
import java.nio.FloatBuffer

/**
 * Renders a transition between two textures (outgoing + incoming).
 * The two textures are blended according to `progress` (0..1) using a
 * shader that depends on the TransitionType.
 */
class TransitionFilter : GlFilter(
    vertexShaderSource = DEFAULT_VERTEX_SHADER,
    fragmentShaderSource = DISSOLVE_SHADER
) {
    private var currentType: TransitionType = TransitionType.DISSOLVE
    private var progress: Float = 0f
    private var uProgressHandle = 0
    private var uTextureOutHandle = 0
    private var uTextureInHandle = 0

    private val programCache = mutableMapOf<TransitionType, Int>()

    private val vertexBuffer: FloatBuffer = GlUtil.createFloatBuffer(VERTEX_COORDS)
    private val textureBuffer: FloatBuffer = GlUtil.createFloatBuffer(TEX_COORDS)

    fun setType(type: TransitionType) {
        if (type != currentType) {
            currentType = type
            programHandle = 0
        }
    }

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
    }

    override fun init() {
        val cached = programCache[currentType]
        programHandle = if (cached != null) {
            cached
        } else {
            val frag = fragmentShaderFor(currentType)
                ?: throw RuntimeException("No shader for transition $currentType")
            val prog = GlUtil.createProgram(DEFAULT_VERTEX_SHADER, frag)
            programCache[currentType] = prog
            prog
        }

        maPositionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
        maTextureHandle = GLES20.glGetAttribLocation(programHandle, "aTextureCoord")
        muMVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
        muTexMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")

        uProgressHandle = GLES20.glGetUniformLocation(programHandle, "uProgress")
        uTextureOutHandle = GLES20.glGetUniformLocation(programHandle, "sTextureOut")
        uTextureInHandle = GLES20.glGetUniformLocation(programHandle, "sTextureIn")
    }

    fun drawTransition(
        textureOutId: Int,
        textureInId: Int,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray,
        width: Int,
        height: Int
    ) {
        if (programHandle == 0) init()

        GLES20.glUseProgram(programHandle)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muTexMatrixHandle, 1, false, texMatrix, 0)
        GLES20.glUniform1f(uProgressHandle, progress)

        // Texture unit 0 = outgoing
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureOutId)
        GLES20.glUniform1i(uTextureOutHandle, 0)

        // Texture unit 1 = incoming
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureInId)
        GLES20.glUniform1i(uTextureInHandle, 1)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
        GLES20.glEnableVertexAttribArray(maPositionHandle)

        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)
        GLES20.glEnableVertexAttribArray(maTextureHandle)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(maPositionHandle)
        GLES20.glDisableVertexAttribArray(maTextureHandle)

        // Unbind both texture units
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    override fun release() {
        for ((_, prog) in programCache) {
            if (prog != 0) GLES20.glDeleteProgram(prog)
        }
        programCache.clear()
        programHandle = 0
    }

    private fun fragmentShaderFor(type: TransitionType): String? = when (type) {
        TransitionType.DISSOLVE -> DISSOLVE_SHADER
        TransitionType.FADE -> FADE_SHADER
        TransitionType.SLIDE_LEFT -> SLIDE_LEFT_SHADER
        TransitionType.SLIDE_RIGHT -> SLIDE_RIGHT_SHADER
        TransitionType.NONE -> null
    }

    companion object {
        private val VERTEX_COORDS = floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f
        )
        private val TEX_COORDS = floatArrayOf(
            0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f
        )

        private const val DISSOLVE_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTextureOut;
            uniform sampler2D sTextureIn;
            uniform float uProgress;
            void main() {
                vec4 o = texture2D(sTextureOut, vTextureCoord);
                vec4 i = texture2D(sTextureIn, vTextureCoord);
                gl_FragColor = mix(o, i, uProgress);
            }
        """

        private const val FADE_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTextureOut;
            uniform sampler2D sTextureIn;
            uniform float uProgress;
            void main() {
                vec4 o = texture2D(sTextureOut, vTextureCoord);
                vec4 i = texture2D(sTextureIn, vTextureCoord);
                float outA = smoothstep(0.0, 0.5, uProgress);
                float inA  = smoothstep(0.5, 1.0, uProgress);
                vec3 c = o.rgb * (1.0 - outA) + i.rgb * inA;
                gl_FragColor = vec4(c, 1.0);
            }
        """

        private const val SLIDE_LEFT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTextureOut;
            uniform sampler2D sTextureIn;
            uniform float uProgress;
            void main() {
                vec2 outUv = vTextureCoord + vec2(uProgress, 0.0);
                vec2 inUv  = vTextureCoord - vec2(1.0 - uProgress, 0.0);
                vec4 o = outUv.x <= 1.0 ? texture2D(sTextureOut, outUv) : vec4(0.0);
                vec4 i = inUv.x  >= 0.0 ? texture2D(sTextureIn,  inUv)  : vec4(0.0);
                gl_FragColor = o + i;
            }
        """

        private const val SLIDE_RIGHT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTextureOut;
            uniform sampler2D sTextureIn;
            uniform float uProgress;
            void main() {
                vec2 outUv = vTextureCoord - vec2(uProgress, 0.0);
                vec2 inUv  = vTextureCoord + vec2(1.0 - uProgress, 0.0);
                vec4 o = outUv.x >= 0.0 ? texture2D(sTextureOut, outUv) : vec4(0.0);
                vec4 i = inUv.x  <= 1.0 ? texture2D(sTextureIn,  inUv)  : vec4(0.0);
                gl_FragColor = o + i;
            }
        """
    }
}
