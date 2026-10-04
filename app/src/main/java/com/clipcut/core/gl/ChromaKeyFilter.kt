package com.clipcut.core.gl

import android.opengl.GLES20

class ChromaKeyFilter(
    var keyColor: FloatArray = floatArrayOf(0f, 1f, 0f),
    var threshold: Float = 0.15f,
    var smoothness: Float = 0.05f
) : GlFilter(
    vertexShaderSource = DEFAULT_VERTEX_SHADER,
    fragmentShaderSource = CHROMA_FRAGMENT_SHADER
) {
    private var uKeyColorHandle = 0
    private var uThresholdHandle = 0
    private var uSmoothnessHandle = 0

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

    companion object {
        private const val CHROMA_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            uniform vec3 uKeyColor;
            uniform float uThreshold;
            uniform float uSmoothness;

            // Convert RGB to YCbCr (BT.601). We only use Cb/Cr for distance.
            vec2 rgb2CbCr(vec3 c) {
                float cb = -0.168736 * c.r - 0.331264 * c.g + 0.5 * c.b;
                float cr =  0.5 * c.r - 0.418688 * c.g - 0.081312 * c.b;
                return vec2(cb, cr);
            }

            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);

                // Compare chroma only, ignoring luminance. This keeps shadows
                // and highlights of the key color while preserving skin tones.
                vec2 keyChroma = rgb2CbCr(uKeyColor);
                vec2 pixelChroma = rgb2CbCr(color.rgb);

                float chromaDist = distance(pixelChroma, keyChroma);

                float alpha = smoothstep(uThreshold, uThreshold + uSmoothness, chromaDist);

                gl_FragColor = vec4(color.rgb, color.a * alpha);
            }
        """
    }
}
