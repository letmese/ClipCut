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

    private val colorAdjust = ColorAdjustFilter()
    private var mask: MaskFilter? = null
    private var chroma: ChromaKeyFilter? = null
    private var blend: BlendFilter? = null
    private var transition: TransitionFilter? = null
    private var lut: LutFilter? = null

    private var initialised = false

    fun init(width: Int, height: Int) {
        if (initialised) return
        this.width = width; this.height = height
        colorAdjust.init()
        mask = MaskFilter().also { it.init() }
        chroma = ChromaKeyFilter().also { it.init() }
        blend = BlendFilter().also { it.init() }
        transition = TransitionFilter().also { it.init() }
        fbo = FramebufferTexture(width, height).also { it.init() }
        initialised = true
    }

    fun resizeIfNeeded(width: Int, height: Int) {
        if (this.width == width && this.height == height) return
        fbo?.release()
        this.width = width; this.height = height
        fbo = FramebufferTexture(width, height).also { it.init() }
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
        baseLayerTexId: Int?
    ) {
        if (!initialised) return
        // Step 1: OES → 2D copy + color/adjust pass into FBO
        val mvp = FloatArray(16)
        Matrix.setRotateM(mvp, 0, rotationDegrees.toFloat(), 0f, 0f, 1f)
        colorAdjust.updateConfig(filterConfig)
        colorAdjust.updateCropFlip(cropRect, flipH, flipV)
        fbo?.bind()
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        colorAdjust.draw(sourceTexId, sourceTexMatrix, mvp, width, height)
        fbo?.unbind()
        var currentTex = fbo?.textureId ?: sourceTexId

        // Step 2: LUT
        if (lutConfig != null && lutConfig.uri.isNotEmpty()) {
            val lt = LutFilter(context, lutConfig.uri, lutConfig.intensity).also { it.init() }
            fbo?.bind()
            lt.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
            fbo?.unbind()
            currentTex = fbo?.textureId ?: currentTex
            lt.release()
        }

        // Step 3: Mask
        if (maskConfig.shape != com.clipcut.core.model.MaskShape.NONE) {
            mask?.updateConfig(maskConfig)
            fbo?.bind()
            mask?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
            fbo?.unbind()
            currentTex = fbo?.textureId ?: currentTex
        }

        // Step 4: Chroma key
        if (chromaKeyColor != null) {
            chroma?.keyColor = chromaKeyColor
            chroma?.threshold = chromaKeyThreshold
            chroma?.smoothness = chromaKeySmoothness
            fbo?.bind()
            chroma?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
            fbo?.unbind()
            currentTex = fbo?.textureId ?: currentTex
        }

        // Step 5: Blend with base layer (PiP compositing)
        if (baseLayerTexId != null && blendMode != BlendMode.NORMAL) {
            blend?.updateMode(blendMode)
            blend?.drawBlend(baseLayerTexId, currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
        } else {
            // Final blit to default framebuffer
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            colorAdjust.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, width, height)
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
            clip.lutConfig, clip.maskConfig, clip.blendMode, null
        )
    }

    fun release() {
        colorAdjust.release()
        mask?.release()
        chroma?.release()
        blend?.release()
        transition?.release()
        lut?.release()
        fbo?.release()
        initialised = false
    }
}
