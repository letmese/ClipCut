package com.clipcut.core.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.clipcut.core.model.BlendMode
import com.clipcut.core.model.CropRect
import com.clipcut.core.model.FilterConfig
import com.clipcut.core.model.LutConfig
import com.clipcut.core.model.MaskConfig
import com.clipcut.core.model.TimelineItem

/**
 * PipelineRenderer — the orchestration glue that runs a clip through:
 *   1. ColorAdjustFilter (with crop/flip/rotation)
 *   2. LutFilter (optional)
 *   3. MaskFilter (optional)
 *   4. ChromaKeyFilter (optional)
 *   5. EffectFilter (optional, from EffectRegistry)
 *   6. BlendFilter against a base layer (for PiP compositing)
 *
 * For each clip in EditorViewModel the GlPreviewRenderer calls renderFrame()
 * with the OES texture produced by ExoPlayer / VideoDecoder, and then for
 * per-clip post-processing applyPostProcessing().
 *
 * renderFrame is hot-path (60fps on preview). We allocate a single FBO once
 * and reuse it across clips via resizeIfNeeded().
 */
class PipelineRenderer(private val context: Context) {
    private var width: Int = -1
    private var height: Int = -1
    private var fbo: FramebufferTexture? = null
    private var fbo2: FramebufferTexture? = null

    private val colorAdjust = ColorAdjustFilter()
    private val passthrough = PassthroughFilter()
    private var oesFilter: OesFilter? = null
    private var mask: MaskFilter? = null
    private var chroma: ChromaKeyFilter? = null
    private var blend: BlendFilter? = null
    private var transition: TransitionFilter? = null
    private var lut: LutFilter? = null
    private var effectFilter: EffectFilter? = null
    private var currentEffectId: String? = null

    private var initialised = false

    fun init(width: Int, height: Int) {
        if (initialised) return
        this.width = width; this.height = height
        colorAdjust.init()
        passthrough.init()
        oesFilter = OesFilter().also { it.init() }
        mask = MaskFilter().also { it.init() }
        chroma = ChromaKeyFilter().also { it.init() }
        blend = BlendFilter().also { it.init() }
        transition = TransitionFilter().also { it.init() }
        fbo = FramebufferTexture(width, height).also { it.init() }
        fbo2 = FramebufferTexture(width, height).also { it.init() }
        initialised = true
    }

    fun resizeIfNeeded(width: Int, height: Int) {
        if (this.width == width && this.height == height) return
        fbo?.release()
        fbo2?.release()
        this.width = width; this.height = height
        fbo = FramebufferTexture(width, height).also { it.init() }
        fbo2 = FramebufferTexture(width, height).also { it.init() }
    }

    fun renderFrame(
        sourceTexId: Int,
        sourceTexMatrix: FloatArray,
        sourceIsOes: Boolean,
        mvpMatrix: FloatArray,
        rotationDegrees: Int,
        filterConfig: FilterConfig,
        cropRect: CropRect,
        flipH: Boolean,
        flipV: Boolean,
        chromaKeyColor: FloatArray?,
        chromaKeyThreshold: Float,
        chromaKeySmoothness: Float,
        lutConfig: LutConfig?,
        maskConfig: MaskConfig,
        blendMode: BlendMode,
        baseLayerTexId: Int?,
        effectId: String? = null,
        effectIntensity: Float = 0f
    ) {
        if (!initialised) return

        val mvp = FloatArray(16)
        Matrix.setRotateM(mvp, 0, rotationDegrees.toFloat(), 0f, 0f, 1f)
        colorAdjust.updateConfig(filterConfig)
        colorAdjust.updateCropFlip(cropRect, flipH, flipV)

        var currentTex: Int
        if (sourceIsOes) {
            fbo?.bind()
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            oesFilter?.draw(sourceTexId, sourceTexMatrix, GlUtil.IDENTITY_MATRIX, width, height, android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            fbo?.unbind()

            val f2 = fbo2
            if (f2 != null) {
                f2.bind()
                GLES20.glViewport(0, 0, width, height)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                colorAdjust.draw(fbo?.textureId ?: sourceTexId, GlUtil.IDENTITY_MATRIX, mvp, width, height)
                f2.unbind()
                currentTex = f2.textureId
            } else {
                currentTex = fbo?.textureId ?: sourceTexId
            }
        } else {
            fbo?.bind()
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            colorAdjust.draw(sourceTexId, sourceTexMatrix, mvp, width, height)
            fbo?.unbind()
            currentTex = fbo?.textureId ?: sourceTexId
        }

        fun nextTarget(): FramebufferTexture? = if (currentTex == fbo?.textureId) fbo2 else fbo

        // Step 2: LUT
        if (lutConfig != null && lutConfig.uri.isNotEmpty()) {
            val target = nextTarget()
            val lt = LutFilter(context, lutConfig.uri, lutConfig.intensity).also { it.init() }
            target?.bind()
            lt.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
            target?.unbind()
            currentTex = target?.textureId ?: currentTex
            lt.release()
        }

        // Step 3: Mask
        if (maskConfig.shape != com.clipcut.core.model.MaskShape.NONE) {
            val target = nextTarget()
            mask?.updateConfig(maskConfig)
            target?.bind()
            mask?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
            target?.unbind()
            currentTex = target?.textureId ?: currentTex
        }

        // Step 4: Chroma key
        // Step 4: Chroma key
        if (chromaKeyColor != null) {
            val target = nextTarget()
            chroma?.keyColor = chromaKeyColor
            chroma?.threshold = chromaKeyThreshold
            chroma?.smoothness = chromaKeySmoothness
            target?.bind()
            chroma?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
            target?.unbind()
            currentTex = target?.textureId ?: currentTex
        }

        // Step 4b: EffectFilter
        if (effectId != null && effectIntensity > 0f) {
            val desc = com.clipcut.core.effects.EffectRegistry.byId(effectId)
            if (desc != null) {
                if (effectFilter == null || currentEffectId != effectId) {
                    effectFilter?.release()
                    effectFilter = EffectFilter(desc.fragmentShader).also { it.init() }
                    currentEffectId = effectId
                }
                val ef = effectFilter
                if (ef != null) {
                    ef.intensity = effectIntensity
                    ef.timeSeconds = (System.currentTimeMillis() % 100_000L) / 1000f
                    val target = nextTarget()
                    target?.bind()
                    ef.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
                    target?.unbind()
                    currentTex = target?.textureId ?: currentTex
                }
            }
        }

        // Step 5: Blend with base layer (PiP compositing) or final blit
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        if (baseLayerTexId != null && blendMode != BlendMode.NORMAL) {
            blend?.updateMode(blendMode)
            blend?.drawBlend(baseLayerTexId, currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
        } else {
            passthrough.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
        }
    }

    fun applyPostProcessing(
        sourceTexId: Int,
        sourceTexMatrix: FloatArray,
        sourceIsOes: Boolean,
        mvpMatrix: FloatArray,
        rotationDegrees: Int,
        clip: TimelineItem
    ) {
        renderFrame(
            sourceTexId, sourceTexMatrix, sourceIsOes, mvpMatrix, rotationDegrees,
            clip.filterConfig, clip.cropRect, clip.flipHorizontal, clip.flipVertical,
            clip.chromaKeyColor, clip.chromaKeyThreshold, clip.chromaKeySmoothness,
            clip.lutConfig, clip.maskConfig, clip.blendMode, null,
            clip.effectId, clip.effectIntensity
        )
    }

    fun release() {
        oesFilter?.release()
        colorAdjust.release()
        passthrough.release()
        mask?.release()
        chroma?.release()
        blend?.release()
        transition?.release()
        lut?.release()
        effectFilter?.release()
        effectFilter = null
        currentEffectId = null
        fbo?.release()
        fbo2?.release()
        initialised = false
    }
}

class EffectFilter(fragmentShaderSource: String) : GlFilter(DEFAULT_VERTEX_SHADER, fragmentShaderSource) {
    private var uIntensity = -1
    private var uTime = -1
    private var uResolution = -1

    var intensity: Float = 0.5f
    var timeSeconds: Float = 0f

    override fun onInitCustomUniforms() {
        uIntensity = GLES20.glGetUniformLocation(programHandle, "uIntensity")
        uTime = GLES20.glGetUniformLocation(programHandle, "uTime")
        uResolution = GLES20.glGetUniformLocation(programHandle, "uResolution")
    }

    override fun onSetCustomUniforms(width: Int, height: Int) {
        if (uIntensity >= 0) GLES20.glUniform1f(uIntensity, intensity)
        if (uTime >= 0) GLES20.glUniform1f(uTime, timeSeconds)
        if (uResolution >= 0) GLES20.glUniform2f(uResolution, width.toFloat(), height.toFloat())
    }
}
