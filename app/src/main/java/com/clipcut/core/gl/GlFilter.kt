package com.clipcut.core.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLUtils
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import com.clipcut.core.model.BlendMode
import com.clipcut.core.model.CropRect
import com.clipcut.core.model.FilterConfig
import com.clipcut.core.model.FilterType
import com.clipcut.core.model.LutConfig
import com.clipcut.core.model.MaskConfig
import com.clipcut.core.model.MaskShape
import com.clipcut.core.model.TransitionType
import java.nio.FloatBuffer

/**
 * GlFilter — base class for all GLSL passes in ClipCut.
 *
 * Subclasses override onInitCustomUniforms / onSetCustomUniforms to inject
 * their own uniforms. The base class handles program creation, vertex array
 * setup, and the default draw() path.
 */
abstract class GlFilter(
    vertexShaderSource: String = DEFAULT_VERTEX_SHADER,
    fragmentShaderSource: String = DEFAULT_FRAGMENT_SHADER
) {
    protected var programHandle: Int = -1
    protected var maPositionHandle: Int = -1
    protected var maTextureHandle: Int = -1
    protected var muMVPMatrixHandle: Int = -1
    protected var muTexMatrixHandle: Int = -1

    private val vertexCoords: FloatBuffer = GlUtil.createFloatBuffer(VERTEX_TRIANGLE)
    private val texCoords: FloatBuffer = GlUtil.createFloatBuffer(VERTEX_TRIANGLE)

    private val vertexSrc: String = vertexShaderSource
    private val fragmentSrc: String = fragmentShaderSource

    open fun init() {
        programHandle = GlUtil.createProgram(vertexSrc, fragmentSrc)
        maPositionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition")
        maTextureHandle = GLES20.glGetAttribLocation(programHandle, "aTextureCoord")
        muMVPMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
        muTexMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
        onInitCustomUniforms()
    }

    protected open fun onInitCustomUniforms() {}
    protected open fun onSetCustomUniforms(width: Int, height: Int) {}

    open fun draw(
        texId: Int,
        texMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        mvpMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        width: Int,
        height: Int,
        textureTarget: Int = GLES20.GL_TEXTURE_2D
    ) {
        GLES20.glUseProgram(programHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, texId)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muTexMatrixHandle, 1, false, texMatrix, 0)

        GLES20.glEnableVertexAttribArray(maPositionHandle)
        vertexCoords.position(0)
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, 20, vertexCoords)
        GLES20.glEnableVertexAttribArray(maTextureHandle)
        texCoords.position(3)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 20, texCoords)

        onSetCustomUniforms(width, height)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(maPositionHandle)
        GLES20.glDisableVertexAttribArray(maTextureHandle)
    }

    open fun release() {
        if (programHandle != -1) GLES20.glDeleteProgram(programHandle)
        programHandle = -1
    }
}

/** No-op pass-through for measuring baseline pipeline cost. */
class PassthroughFilter : GlFilter() {
    override fun init() {
        super.init()
    }
}

/** Samples from an external OES texture into a 2D surface. */
class OesFilter : GlFilter(fragmentShaderSource = OE_FRAGMENT_SHADER)

/**
 * ColorAdjustFilter — combines brightness/contrast/saturation/temperature +
 * crop + flip + B&W/Sepia/Vignette into a single fragment pass to minimise
 * GPU bandwidth on hot preview path.
 */
class ColorAdjustFilter : GlFilter(
    fragmentShaderSource = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sTexture;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uTemperature;
        uniform float uVignette;
        uniform float uTint;
        uniform float uEffectMode; // 0 none, 1 grayscale, 2 sepia
        uniform vec2 uCropOrigin;  // (left, top) in UV
        uniform vec2 uCropSize;    // (width, height)
        uniform bool uFlipH; uniform bool uFlipV;
        void main() {
            vec2 uv = vTexCoord;
            uv = uCropOrigin + uv * uCropSize;
            if (uFlipH) uv.x = 1.0 - uv.x;
            if (uFlipV) uv.y = 1.0 - uv.y;
            vec4 c = texture2D(sTexture, uv);
            c.rgb += uBrightness;
            c.rgb = (c.rgb - 0.5) * uContrast + 0.5;
            float gray = dot(c.rgb, vec3(0.299, 0.587, 0.114));
            c.rgb = mix(vec3(gray), c.rgb, uSaturation);
            c.r += uTemperature;
            c.b -= uTemperature;
            c.g += uTint;
            if (uEffectMode > 0.5 && uEffectMode < 1.5) {
                c.rgb = vec3(gray);
            } else if (uEffectMode > 1.5) {
                c.rgb = vec3(gray) * vec3(1.07, 0.74, 0.43);
                c.rgb = clamp(c.rgb, 0.0, 1.0);
            }
            float d = distance(vTexCoord, vec2(0.5));
            c.rgb *= 1.0 - smoothstep(0.4, 0.75, d) * uVignette;
            gl_FragColor = clamp(c, 0.0, 1.0);
        }
    """
) {
    var brightness: Float = 0.0f
    var contrast: Float = 1.0f
    var saturation: Float = 1.0f
    var temperature: Float = 0.0f
    var vignette: Float = 0.0f
    var tint: Float = 0.0f
    var filterType: FilterType = FilterType.NONE
    var cropLeft: Float = 0f; var cropTop: Float = 0f
    var cropRight: Float = 1f; var cropBottom: Float = 1f
    var flipHorizontal: Boolean = false
    var flipVertical: Boolean = false

    private var uBrightness = -1
    private var uContrast = -1
    private var uSaturation = -1
    private var uTemperature = -1
    private var uVignette = -1
    private var uTint = -1
    private var uEffectMode = -1
    private var uCropOrigin = -1
    private var uCropSize = -1
    private var uFlipH = -1
    private var uFlipV = -1

    fun updateConfig(c: FilterConfig) {
        brightness = c.brightness
        contrast = c.contrast
        saturation = c.saturation
        temperature = c.temperature
        vignette = if (c.vignette > 0f) c.vignette else (if (c.filterType == FilterType.VIGNETTE) 0.7f else 0.0f)
        tint = c.tint
        filterType = c.filterType
        when (c.filterType) {
            FilterType.GRAYSCALE -> { filterType = FilterType.GRAYSCALE }
            FilterType.SEPIA -> { filterType = FilterType.SEPIA }
            FilterType.VIGNETTE -> { vignette = 0.7f }
            else -> {}
        }
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
        uVignette = GLES20.glGetUniformLocation(programHandle, "uVignette")
        uTint = GLES20.glGetUniformLocation(programHandle, "uTint")
        uEffectMode = GLES20.glGetUniformLocation(programHandle, "uEffectMode")
        uCropOrigin = GLES20.glGetUniformLocation(programHandle, "uCropOrigin")
        uCropSize = GLES20.glGetUniformLocation(programHandle, "uCropSize")
        uFlipH = GLES20.glGetUniformLocation(programHandle, "uFlipH")
        uFlipV = GLES20.glGetUniformLocation(programHandle, "uFlipV")
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform1f(uBrightness, brightness)
        GLES20.glUniform1f(uContrast, contrast)
        GLES20.glUniform1f(uSaturation, saturation)
        GLES20.glUniform1f(uTemperature, temperature)
        GLES20.glUniform1f(uVignette, vignette)
        GLES20.glUniform1f(uTint, tint)
        val mode = when (filterType) {
            FilterType.GRAYSCALE -> 1f
            FilterType.SEPIA -> 2f
            else -> 0f
        }
        GLES20.glUniform1f(uEffectMode, mode)
        GLES20.glUniform2f(uCropOrigin, cropLeft, cropTop)
        GLES20.glUniform2f(uCropSize, cropRight - cropLeft, cropBottom - cropTop)
        GLES20.glUniform1i(uFlipH, if (flipHorizontal) 1 else 0)
        GLES20.glUniform1i(uFlipV, if (flipVertical) 1 else 0)
    }

    fun drawOes(
        texId: Int,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        rotationDegrees: Int = 0,
        width: Int,
        height: Int
    ) {
        // For OES-external-source path callers (live preview from
        // SurfaceTexture): fall through to standard draw; ColorAdjustFilter
        // uses GL_TEXTURE_2D after the OES→2D copy in PipelineRenderer.
        draw(texId, texMatrix, mvpMatrix, width, height, GLES20.GL_TEXTURE_2D)
    }
}

class ChromaKeyFilter(
    var keyColor: FloatArray = floatArrayOf(0f, 1f, 0f),
    var threshold: Float = 0.15f,
    var smoothness: Float = 0.05f
) : GlFilter(
    fragmentShaderSource = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sTexture;
        uniform vec3 uKeyColor;
        uniform float uThreshold;
        uniform float uSmoothness;
        void main() {
            vec4 c = texture2D(sTexture, vTexCoord);
            float d = distance(c.rgb, uKeyColor);
            float a = smoothstep(uThreshold, uThreshold + uSmoothness, d);
            gl_FragColor = vec4(c.rgb, a);
        }
    """
) {
    private var uKeyColor = -1
    private var uThreshold = -1
    private var uSmoothness = -1
    override fun onInitCustomUniforms() {
        uKeyColor = GLES20.glGetUniformLocation(programHandle, "uKeyColor")
        uThreshold = GLES20.glGetUniformLocation(programHandle, "uThreshold")
        uSmoothness = GLES20.glGetUniformLocation(programHandle, "uSmoothness")
    }
    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform3fv(uKeyColor, 1, keyColor, 0)
        GLES20.glUniform1f(uThreshold, threshold)
        GLES20.glUniform1f(uSmoothness, smoothness)
    }
}

class MaskFilter : GlFilter(
    fragmentShaderSource = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sTexture;
        uniform int uShape;
        uniform vec2 uCenter; uniform vec2 uSize;
        uniform float uRotation; uniform float uFeather; uniform float uInverted;
        float rectMask(vec2 uv) {
            vec2 d = abs(uv - uCenter) / (uSize * 0.5);
            float m = 1.0 - smoothstep(1.0 - uFeather, 1.0, max(d.x, d.y));
            return m;
        }
        float ovalMask(vec2 uv) {
            vec2 d = (uv - uCenter) / (uSize * 0.5);
            return 1.0 - smoothstep(1.0 - uFeather, 1.0, dot(d, d));
        }
        float linearMask(vec2 uv) {
            return 1.0 - smoothstep(0.5 - uFeather, 0.5, uv.y);
        }
        float radialMask(vec2 uv) {
            float d = distance(uv, uCenter);
            return 1.0 - smoothstep(0.4 - uFeather, 0.4, d);
        }
        void main() {
            vec4 c = texture2D(sTexture, vTexCoord);
            float a = 1.0;
            if (uShape == 1) a = linearMask(vTexCoord);
            else if (uShape == 2) a = radialMask(vTexCoord);
            else if (uShape == 3) a = rectMask(vTexCoord);
            else if (uShape == 4) a = ovalMask(vTexCoord);
            if (uInverted > 0.5) a = 1.0 - a;
            gl_FragColor = vec4(c.rgb, c.a * a);
        }
    """
) {
    var shape: Int = 0
    var centerX: Float = 0.5f; var centerY: Float = 0.5f
    var width: Float = 0.6f; var height: Float = 0.6f
    var rotation: Float = 0f; var feather: Float = 0.05f; var inverted: Float = 0f

    private var uShape = -1; private var uCenter = -1; private var uSize = -1
    private var uRotation = -1; private var uFeather = -1; private var uInverted = -1

    fun updateConfig(config: MaskConfig) {
        shape = when (config.shape) {
            MaskShape.NONE -> 0
            MaskShape.LINEAR -> 1
            MaskShape.RADIAL -> 2
            MaskShape.RECTANGLE -> 3
            MaskShape.OVAL -> 4
        }
        centerX = config.centerX; centerY = config.centerY
        width = config.width; height = config.height
        rotation = config.rotation; feather = config.feather
        inverted = if (config.inverted) 1f else 0f
    }

    override fun onInitCustomUniforms() {
        uShape = GLES20.glGetUniformLocation(programHandle, "uShape")
        uCenter = GLES20.glGetUniformLocation(programHandle, "uCenter")
        uSize = GLES20.glGetUniformLocation(programHandle, "uSize")
        uRotation = GLES20.glGetUniformLocation(programHandle, "uRotation")
        uFeather = GLES20.glGetUniformLocation(programHandle, "uFeather")
        uInverted = GLES20.glGetUniformLocation(programHandle, "uInverted")
    }
    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glUniform1i(uShape, shape)
        GLES20.glUniform2f(uCenter, centerX, centerY)
        GLES20.glUniform2f(uSize, width.toFloat(), height.toFloat())
        GLES20.glUniform1f(uRotation, rotation)
        GLES20.glUniform1f(uFeather, feather)
        GLES20.glUniform1f(uInverted, inverted)
    }
}

class BlendFilter : GlFilter(
    fragmentShaderSource = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sBase;
        uniform sampler2D sTop;
        uniform int uMode;
        vec3 blend(int mode, vec3 a, vec3 b) {
            if (mode == 1) return a * b;             // MULTIPLY
            if (mode == 2) return 1.0 - (1.0 - a) * (1.0 - b); // SCREEN
            if (mode == 3) return mix(a, b, 0.5);    // OVERLAY (simplified)
            if (mode == 4) return a * b + b * (1.0 - a); // SOFT_LIGHT
            if (mode == 5) return step(0.5, a) * (1.0 - (1.0 - 2.0*(a-0.5))*(1.0-b)) + (1.0-step(0.5,a))*(2.0*a*b); // HARD_LIGHT
            if (mode == 6) return 1.0 - (1.0 - b) / a; // COLOR_BURN
            if (mode == 7) return a / (1.0 - b);    // COLOR_DODGE
            if (mode == 8) return min(a, b);         // DARKEN
            if (mode == 9) return max(a, b);         // LIGHTEN
            if (mode == 10) return abs(a - b);       // DIFFERENCE
            if (mode == 11) return a + b - 2.0 * a * b; // EXCLUSION
            return b;
        }
        void main() {
            vec4 a = texture2D(sBase, vTexCoord);
            vec4 b = texture2D(sTop, vTexCoord);
            gl_FragColor = vec4(blend(uMode, a.rgb, b.rgb), b.a);
        }
    """
) {
    val mode: Int = 0
    private var uMode = -1
    private var sBase = -1
    private var sTop = -1

    fun updateMode(blendMode: BlendMode) {
        GLES20.glUseProgram(programHandle)
        val m = when (blendMode) {
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
        GLES20.glUniform1i(uMode, m)
    }

    fun drawBlend(
        baseTexId: Int, topTexId: Int,
        texMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        mvpMatrix: FloatArray = GlUtil.IDENTITY_MATRIX,
        width: Int, height: Int
    ) {
        GLES20.glUseProgram(programHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, baseTexId)
        GLES20.glUniform1i(sBase, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, topTexId)
        GLES20.glUniform1i(sTop, 1)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muTexMatrixHandle, 1, false, texMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        super.draw(baseTexId, texMatrix, mvpMatrix, width, height, GLES20.GL_TEXTURE_2D)
    }

    override fun onInitCustomUniforms() {
        uMode = GLES20.glGetUniformLocation(programHandle, "uMode")
        sBase = GLES20.glGetUniformLocation(programHandle, "sBase")
        sTop = GLES20.glGetUniformLocation(programHandle, "sTop")
    }
    override fun onSetCustomUniforms(width: Int, height: Int) {}
}

class TransitionFilter : GlFilter(
    fragmentShaderSource = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sOut;
        uniform sampler2D sIn;
        uniform float uProgress;
        uniform int uType;
        void main() {
            vec2 uv = vTexCoord;
            float p = uProgress;
            vec4 a = texture2D(sOut, uv);
            vec4 b = texture2D(sIn, uv);
            vec4 r = a;

            if (uType == 0) { // NONE
                r = a;
            } else if (uType == 1) { // FADE
                r = mix(a, b, p);
            } else if (uType == 2) { // SLIDE_LEFT
                r = (uv.x < 1.0 - p) ? texture2D(sOut, uv + vec2(p, 0.0)) : texture2D(sIn, uv - vec2(1.0 - p, 0.0));
            } else if (uType == 3) { // SLIDE_RIGHT
                r = (uv.x > p) ? texture2D(sOut, uv - vec2(p, 0.0)) : texture2D(sIn, uv + vec2(1.0 - p, 0.0));
            } else if (uType == 4) { // SLIDE_UP
                r = (uv.y < 1.0 - p) ? texture2D(sOut, uv + vec2(0.0, p)) : texture2D(sIn, uv - vec2(0.0, 1.0 - p));
            } else if (uType == 5) { // SLIDE_DOWN
                r = (uv.y > p) ? texture2D(sOut, uv - vec2(0.0, p)) : texture2D(sIn, uv + vec2(0.0, 1.0 - p));
            } else if (uType == 6) { // DISSOLVE
                r = mix(a, b, smoothstep(0.0, 1.0, p));
            } else if (uType == 7) { // WIPE_LEFT
                r = mix(a, b, step(1.0 - p, uv.x));
            } else if (uType == 8) { // WIPE_RIGHT
                r = mix(a, b, step(uv.x, p));
            } else if (uType == 9) { // WIPE_UP
                r = mix(a, b, step(1.0 - p, uv.y));
            } else if (uType == 10) { // WIPE_DOWN
                r = mix(a, b, step(uv.y, p));
            } else if (uType == 11) { // CIRCLE_CROP
                float dist = distance(uv, vec2(0.5));
                r = mix(a, b, step(dist, p * 0.7071));
            } else if (uType == 12) { // CIRCLE_CLOSE
                float dist = distance(uv, vec2(0.5));
                r = mix(b, a, step(dist, (1.0 - p) * 0.7071));
            } else if (uType == 13) { // CROSS_ZOOM
                vec2 center = vec2(0.5);
                vec2 uvA = (uv - center) * (1.0 + p * 0.5) + center;
                vec2 uvB = (uv - center) * (1.5 - p * 0.5) + center;
                r = mix(texture2D(sOut, uvA), texture2D(sIn, uvB), p);
            } else if (uType == 14) { // ZOOM_IN
                vec2 center = vec2(0.5);
                vec2 uvA = (uv - center) * (1.0 - p * 0.3) + center;
                r = mix(texture2D(sOut, uvA), b, p);
            } else if (uType == 15) { // ZOOM_OUT
                vec2 center = vec2(0.5);
                vec2 uvB = (uv - center) * (0.7 + p * 0.3) + center;
                r = mix(a, texture2D(sIn, uvB), p);
            } else if (uType == 16) { // FLASH_WHITE
                vec4 flashColor = vec4(1.0);
                r = (p < 0.5) ? mix(a, flashColor, p * 2.0) : mix(flashColor, b, (p - 0.5) * 2.0);
            } else if (uType == 17) { // FLASH_BLACK
                vec4 blackColor = vec4(0.0, 0.0, 0.0, 1.0);
                r = (p < 0.5) ? mix(a, blackColor, p * 2.0) : mix(blackColor, b, (p - 0.5) * 2.0);
            } else if (uType == 18) { // GLITCH
                float offset = sin(uv.y * 30.0 + p * 20.0) * 0.03 * (1.0 - abs(p - 0.5) * 2.0);
                vec4 ga = texture2D(sOut, vec2(uv.x + offset, uv.y));
                vec4 gb = texture2D(sIn, vec2(uv.x - offset, uv.y));
                r = mix(ga, gb, p);
            } else if (uType == 19) { // PIXELATE
                float d = 1.0 + 30.0 * sin(p * 3.14159);
                vec2 coord = floor(uv * d) / d;
                r = mix(texture2D(sOut, coord), texture2D(sIn, coord), p);
            } else if (uType == 20) { // BLUR
                float blurAmt = sin(p * 3.14159) * 0.02;
                vec4 blurA = (texture2D(sOut, uv) + texture2D(sOut, uv + vec2(blurAmt, 0.0)) + texture2D(sOut, uv - vec2(blurAmt, 0.0))) / 3.0;
                vec4 blurB = (texture2D(sIn, uv) + texture2D(sIn, uv + vec2(blurAmt, 0.0)) + texture2D(sIn, uv - vec2(blurAmt, 0.0))) / 3.0;
                r = mix(blurA, blurB, p);
            } else if (uType == 21) { // ROTATE
                float angle = p * 3.14159 * 0.5;
                float cosA = cos(angle);
                float sinA = sin(angle);
                vec2 center = vec2(0.5);
                vec2 rotUV = vec2(cosA * (uv.x - center.x) - sinA * (uv.y - center.y) + center.x,
                                  sinA * (uv.x - center.x) + cosA * (uv.y - center.y) + center.y);
                r = mix(texture2D(sOut, rotUV), b, p);
            } else if (uType == 22) { // RIPPLE
                vec2 dir = uv - vec2(0.5);
                float dist = length(dir);
                vec2 offset = dir * (sin(dist * 20.0 - p * 15.0) * 0.03 * (1.0 - p));
                r = mix(texture2D(sOut, uv + offset), texture2D(sIn, uv), p);
            } else if (uType == 23) { // HEART
                vec2 q = (uv - vec2(0.5, 0.4)) * 2.0;
                float heart = q.x*q.x + pow(q.y - sqrt(abs(q.x)), 2.0);
                r = mix(a, b, step(heart, p * 2.5));
            } else if (uType == 24) { // STAR
                vec2 st = (uv - vec2(0.5)) * 2.0;
                float l = length(st);
                float angle = atan(st.y, st.x);
                float starShape = 0.5 + 0.3 * cos(angle * 5.0);
                r = mix(a, b, step(l / starShape, p * 2.0));
            } else if (uType == 25) { // CURTAIN
                float distCenter = abs(uv.x - 0.5);
                r = (distCenter > (1.0 - p) * 0.5) ? b : a;
            } else {
                r = mix(a, b, p);
            }
            gl_FragColor = r;
        }
    """
) {
    private var uProgress = -1
    private var uType = -1
    private var sOut = -1
    private var sIn = -1

    fun setType(type: TransitionType) {
        GLES20.glUseProgram(programHandle)
        val idx = TransitionType.values().indexOf(type).coerceAtLeast(0)
        GLES20.glUniform1i(uType, idx)
    }

    fun setProgress(p: Float) {
        GLES20.glUseProgram(programHandle)
        GLES20.glUniform1f(uProgress, p.coerceIn(0f, 1f))
    }

    fun drawTransition(
        textureOutId: Int, textureInId: Int,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray,
        width: Int, height: Int
    ) {
        GLES20.glUseProgram(programHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureOutId)
        GLES20.glUniform1i(sOut, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureInId)
        GLES20.glUniform1i(sIn, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        draw(textureOutId, texMatrix, mvpMatrix, width, height, GLES20.GL_TEXTURE_2D)
    }

    override fun onInitCustomUniforms() {
        uProgress = GLES20.glGetUniformLocation(programHandle, "uProgress")
        uType = GLES20.glGetUniformLocation(programHandle, "uType")
        sOut = GLES20.glGetUniformLocation(programHandle, "sOut")
        sIn = GLES20.glGetUniformLocation(programHandle, "sIn")
    }
    override fun onSetCustomUniforms(width: Int, height: Int) {}
}

class LutFilter(
    context: Context,
    lutUri: String,
    var intensity: Float = 1.0f
) : GlFilter(
    fragmentShaderSource = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D sTexture;
        uniform sampler2D sLut;
        uniform float uIntensity;
        void main() {
            vec4 c = texture2D(sTexture, vTexCoord);
            float b = c.b * 63.0;
            float bFloor = floor(b);
            float bFrac = b - bFloor;
            vec2 cell = vec2((bFloor * 64.0 + c.r * 63.0 + 0.5) / 4096.0, (c.g * 63.0 + 0.5) / 64.0);
            vec2 cell2 = vec2(((bFloor + 1.0) * 64.0 + c.r * 63.0 + 0.5) / 4096.0, (c.g * 63.0 + 0.5) / 64.0);
            vec3 mapped = mix(texture2D(sLut, cell).rgb, texture2D(sLut, cell2).rgb, bFrac);
            vec3 outRgb = mix(c.rgb, mapped, uIntensity);
            gl_FragColor = vec4(outRgb, c.a);
        }
    """
) {
    private var sLut = -1
    private var uIntensity = -1
    private var lutTexId = -1

    init {
        // Load LUT bitmap asynchronously — guard with try/catch since missing
        // LUT must not crash the whole pipeline.
        try {
            val input = context.contentResolver.openInputStream(android.net.Uri.parse(lutUri))
            if (input != null) {
                val bmp = BitmapFactory.decodeStream(input)
                input.close()
                if (bmp != null) {
                    lutTexId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                    bmp.recycle()
                }
            }
        } catch (_: Throwable) { /* missing LUT — fall back to passthrough */ }
    }

    override fun onInitCustomUniforms() {
        sLut = GLES20.glGetUniformLocation(programHandle, "sLut")
        uIntensity = GLES20.glGetUniformLocation(programHandle, "uIntensity")
    }
    override fun onSetCustomUniforms(width: Int, height: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
        GLES20.glUniform1i(sLut, 1)
        GLES20.glUniform1f(uIntensity, intensity)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }
}
