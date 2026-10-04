package com.clipcut.core.gl

import android.content.Context
import android.opengl.GLES20
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.BlendMode
import com.clipcut.core.model.CropRect
import com.clipcut.core.model.FilterConfig
import com.clipcut.core.model.LutConfig
import com.clipcut.core.model.MaskConfig
import com.clipcut.core.model.MaskShape
import com.clipcut.core.model.TimelineItem

/**
 * Orchestrates multi-pass rendering for a single video frame with dual-FBO ping-pong:
 *
 *   Pass 1: Source (video OES or image 2D) -> ColorAdjustFilter (crop, flip, adjust) -> primaryFbo
 *   Pass 2: If Chroma Key active -> primaryFbo -> ChromaKeyFilter -> secondaryFbo (swapped)
 *   Pass 3: If Mask active -> currentFbo -> MaskFilter -> altFbo (swapped)
 *   Pass 4: If LUT active -> currentFbo -> LutFilter -> output framebuffer
 *           Else -> currentFbo -> PassthroughFilter -> output framebuffer
 *   Pass 5: If Blend mode != NORMAL -> BlendFilter.drawBlend(baseLayer, currentFbo) -> output
 *
 * This completely resolves filter exclusivity, chaining Color Adjust + Chroma + Mask + LUT sequentially.
 */
class PipelineRenderer(private val context: Context) {

    private var primaryFbo: FramebufferTexture? = null
    private var secondaryFbo: FramebufferTexture? = null
    private var fboWidth = 0
    private var fboHeight = 0

    private var colorFilter: ColorAdjustFilter? = null
    private var chromaFilter: ChromaKeyFilter? = null
    private var lutFilter: LutFilter? = null
    private var maskFilter: MaskFilter? = null
    private var blendFilter: BlendFilter? = null
    private var passthroughFilter: PassthroughFilter? = null

    private var currentLutUri: String? = null

    fun init(width: Int, height: Int) {
        fboWidth = width
        fboHeight = height
        primaryFbo = FramebufferTexture(width, height).also { it.init() }
        secondaryFbo = FramebufferTexture(width, height).also { it.init() }
        colorFilter = ColorAdjustFilter().also { it.init() }
        chromaFilter = ChromaKeyFilter().also { it.init() }
        maskFilter = MaskFilter().also { it.init() }
        blendFilter = BlendFilter().also { it.init() }
        passthroughFilter = PassthroughFilter().also { it.init() }
        AppLog.i(TAG, "PipelineRenderer initialized (${width}x${height}) with dual FBOs")
    }

    fun resizeIfNeeded(width: Int, height: Int) {
        if (width != fboWidth || height != fboHeight) {
            primaryFbo?.release()
            secondaryFbo?.release()
            fboWidth = width
            fboHeight = height
            primaryFbo = FramebufferTexture(width, height).also { it.init() }
            secondaryFbo = FramebufferTexture(width, height).also { it.init() }
        }
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
        val fboA = primaryFbo ?: return
        val fboB = secondaryFbo ?: return

        // ---- PASS 1: source -> ColorAdjustFilter -> primaryFbo ----
        fboA.bind()
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        colorFilter?.updateConfig(filterConfig)
        colorFilter?.updateCropFlip(cropRect, flipH, flipV)
        if (sourceIsOes) {
            colorFilter?.drawOes(sourceTexId, sourceTexMatrix, mvpMatrix, rotationDegrees, fboWidth, fboHeight)
        } else {
            colorFilter?.draw(sourceTexId, sourceTexMatrix, mvpMatrix, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        }
        fboA.unbind()

        var currentTex = fboA.textureId
        var currentFbo = fboA
        var altFbo = fboB

        // ---- PASS 2: (optional) Chroma Key ----
        if (chromaKeyColor != null) {
            altFbo.bind()
            GLES20.glViewport(0, 0, fboWidth, fboHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            chromaFilter?.keyColor = chromaKeyColor
            chromaFilter?.threshold = chromaKeyThreshold
            chromaFilter?.smoothness = chromaKeySmoothness
            chromaFilter?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
            altFbo.unbind()

            currentTex = altFbo.textureId
            val temp = currentFbo; currentFbo = altFbo; altFbo = temp
        }

        // ---- PASS 3: (optional) Mask ----
        if (maskConfig.shape != MaskShape.NONE) {
            altFbo.bind()
            GLES20.glViewport(0, 0, fboWidth, fboHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            maskFilter?.updateConfig(maskConfig)
            maskFilter?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
            altFbo.unbind()

            currentTex = altFbo.textureId
            val temp = currentFbo; currentFbo = altFbo; altFbo = temp
        }

        // ---- PASS 4: LUT or Passthrough -> Default Framebuffer ----
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        if (lutConfig != null) {
            if (lutConfig.uri != currentLutUri) {
                lutFilter?.release()
                lutFilter = LutFilter(context, lutConfig.uri, lutConfig.intensity).also { it.init() }
                currentLutUri = lutConfig.uri
            } else {
                lutFilter?.intensity = lutConfig.intensity
            }
            lutFilter?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        } else {
            passthroughFilter?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        }

        // ---- PASS 5: (optional) Dual-texture blend ----
        if (blendMode != BlendMode.NORMAL && baseLayerTexId != null) {
            blendFilter?.updateMode(blendMode)
            blendFilter?.drawBlend(
                baseTexId = baseLayerTexId,
                topTexId = currentTex,
                texMatrix = GlUtil.IDENTITY_MATRIX,
                mvpMatrix = GlUtil.IDENTITY_MATRIX,
                width = fboWidth,
                height = fboHeight
            )
        }
    }

    /**
     * Simplified single-shot variant used by ExportEngine and GlPreviewRenderer.
     */
    fun applyPostProcessing(
        sourceTexId: Int,
        sourceTexMatrix: FloatArray,
        sourceIsOes: Boolean,
        mvpMatrix: FloatArray,
        rotationDegrees: Int,
        clip: TimelineItem
    ) {
        val fboA = primaryFbo ?: return
        val fboB = secondaryFbo ?: return

        // 1. Color adjustments (crop, flip, warmth, brightness, contrast)
        fboA.bind()
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        colorFilter?.updateConfig(clip.filterConfig)
        colorFilter?.updateCropFlip(clip.cropRect, clip.flipHorizontal, clip.flipVertical)
        if (sourceIsOes) {
            colorFilter?.drawOes(sourceTexId, sourceTexMatrix, mvpMatrix, rotationDegrees, fboWidth, fboHeight)
        } else {
            colorFilter?.draw(sourceTexId, sourceTexMatrix, mvpMatrix, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        }
        fboA.unbind()

        var currentTex = fboA.textureId
        var currentFbo = fboA
        var altFbo = fboB

        // 2. Chroma Key
        if (clip.chromaKeyColor != null) {
            altFbo.bind()
            GLES20.glViewport(0, 0, fboWidth, fboHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            chromaFilter?.keyColor = clip.chromaKeyColor
            chromaFilter?.threshold = clip.chromaKeyThreshold
            chromaFilter?.smoothness = clip.chromaKeySmoothness
            chromaFilter?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
            altFbo.unbind()

            currentTex = altFbo.textureId
            val temp = currentFbo; currentFbo = altFbo; altFbo = temp
        }

        // 3. Mask
        if (clip.maskConfig.shape != MaskShape.NONE) {
            altFbo.bind()
            GLES20.glViewport(0, 0, fboWidth, fboHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            maskFilter?.updateConfig(clip.maskConfig)
            maskFilter?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
            altFbo.unbind()

            currentTex = altFbo.textureId
            val temp = currentFbo; currentFbo = altFbo; altFbo = temp
        }

        // 4. LUT or Passthrough to default framebuffer
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        if (clip.lutConfig != null) {
            val lut = clip.lutConfig
            if (lut.uri != currentLutUri) {
                lutFilter?.release()
                lutFilter = LutFilter(context, lut.uri, lut.intensity).also { it.init() }
                currentLutUri = lut.uri
            } else {
                lutFilter?.intensity = lut.intensity
            }
            lutFilter?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        } else {
            passthroughFilter?.draw(currentTex, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        }
    }

    fun release() {
        primaryFbo?.release()
        secondaryFbo?.release()
        colorFilter?.release()
        chromaFilter?.release()
        lutFilter?.release()
        maskFilter?.release()
        blendFilter?.release()
        passthroughFilter?.release()
        currentLutUri = null
    }

    companion object {
        private const val TAG = "PipelineRenderer"
    }
}

class PassthroughFilter : GlFilter()
