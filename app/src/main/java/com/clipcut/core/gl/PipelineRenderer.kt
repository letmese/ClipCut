package com.clipcut.core.gl

import android.content.Context
import android.opengl.GLES11Ext
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
 * Orchestrates multi-pass rendering for a single video frame:
 *
 *   Pass 1: Decode texture -> ColorAdjustFilter -> ChromaKey (if any) -> FBO
 *   Pass 2: FBO texture -> Mask (if any) -> LUT (if any) -> default framebuffer
 *   Pass 3: If blend mode != NORMAL, blend the FBO result with a base layer
 *
 * This lets LUT, mask, and blend coexist without cramming everything into
 * one fragment shader.
 */
class PipelineRenderer(private val context: Context) {

    private var intermediateFbo: FramebufferTexture? = null
    private var fboWidth = 0
    private var fboHeight = 0

    private var colorFilter: ColorAdjustFilter? = null
    private var chromaFilter: ChromaKeyFilter? = null
    private var lutFilter: LutFilter? = null
    private var maskFilter: MaskFilter? = null
    private var blendFilter: BlendFilter? = null
    private var passthroughFilter: PassthroughFilter? = null

    // Track which LUT URI is currently loaded so we only rebuild when it changes
    private var currentLutUri: String? = null

    fun init(width: Int, height: Int) {
        fboWidth = width
        fboHeight = height
        intermediateFbo = FramebufferTexture(width, height).also { it.init() }
        colorFilter = ColorAdjustFilter().also { it.init() }
        chromaFilter = ChromaKeyFilter().also { it.init() }
        maskFilter = MaskFilter().also { it.init() }
        blendFilter = BlendFilter().also { it.init() }
        passthroughFilter = PassthroughFilter().also { it.init() }
        AppLog.i(TAG, "PipelineRenderer initialized (${width}x${height})")
    }

    fun resizeIfNeeded(width: Int, height: Int) {
        if (width != fboWidth || height != fboHeight) {
            intermediateFbo?.release()
            fboWidth = width
            fboHeight = height
            intermediateFbo = FramebufferTexture(width, height).also { it.init() }
        }
    }

    /**
     * Render a single frame through the full pipeline into the currently
     * bound default framebuffer.
     */
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
        baseLayerTexId: Int?   // If provided and blendMode != NORMAL, this is the "under" layer
    ) {
        val fbo = intermediateFbo ?: return

        // ---- PASS 1: source -> color/chroma -> FBO ----
        fbo.bind()
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val hasChroma = chromaKeyColor != null
        if (hasChroma) {
            chromaFilter?.keyColor = chromaKeyColor!!
            chromaFilter?.threshold = chromaKeyThreshold
            chromaFilter?.smoothness = chromaKeySmoothness
            if (sourceIsOes) {
                chromaFilter?.drawOes(sourceTexId, sourceTexMatrix, mvpMatrix, rotationDegrees, fboWidth, fboHeight)
            } else {
                chromaFilter?.draw(sourceTexId, sourceTexMatrix, mvpMatrix, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
            }
        } else {
            colorFilter?.updateConfig(filterConfig)
            colorFilter?.updateCropFlip(cropRect, flipH, flipV)
            if (sourceIsOes) {
                colorFilter?.drawOes(sourceTexId, sourceTexMatrix, mvpMatrix, rotationDegrees, fboWidth, fboHeight)
            } else {
                colorFilter?.draw(sourceTexId, sourceTexMatrix, mvpMatrix, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
            }
        }
        fbo.unbind()

        // ---- PASS 2: FBO -> (mask) -> (LUT) -> output ----
        val hasMask = maskConfig.shape != MaskShape.NONE
        val hasLut = lutConfig != null
        val needsBlend = blendMode != BlendMode.NORMAL && baseLayerTexId != null

        if (hasMask) {
            maskFilter?.updateConfig(maskConfig)
            maskFilter?.draw(fbo.textureId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        } else if (lutConfig != null) {
            if (lutConfig.uri != currentLutUri) {
                lutFilter?.release()
                lutFilter = LutFilter(context, lutConfig.uri, lutConfig.intensity).also { it.init() }
                currentLutUri = lutConfig.uri
            } else {
                lutFilter?.intensity = lutConfig.intensity
            }
            lutFilter?.draw(fbo.textureId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        } else {
            passthroughFilter?.draw(fbo.textureId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        }

        if (needsBlend) {
            blendFilter?.updateMode(blendMode)
            blendFilter?.drawBlend(
                baseTexId = baseLayerTexId!!,
                topTexId = fbo.textureId,
                texMatrix = GlUtil.IDENTITY_MATRIX,
                mvpMatrix = GlUtil.IDENTITY_MATRIX,
                width = fboWidth,
                height = fboHeight
            )
        }
    }

    /**
     * Simplified single-shot variant used by ExportEngine and GlPreviewRenderer
     * when the caller already knows the current clip's parameters.
     */
    fun applyPostProcessing(
        sourceTexId: Int,
        sourceTexMatrix: FloatArray,
        sourceIsOes: Boolean,
        mvpMatrix: FloatArray,
        rotationDegrees: Int,
        clip: TimelineItem
    ) {
        val fbo = intermediateFbo ?: return

        fbo.bind()
        GLES20.glViewport(0, 0, fboWidth, fboHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val hasChroma = clip.chromaKeyColor != null
        if (hasChroma) {
            chromaFilter?.keyColor = clip.chromaKeyColor!!
            chromaFilter?.threshold = clip.chromaKeyThreshold
            chromaFilter?.smoothness = clip.chromaKeySmoothness
            if (sourceIsOes) {
                chromaFilter?.drawOes(sourceTexId, sourceTexMatrix, mvpMatrix, rotationDegrees, fboWidth, fboHeight)
            } else {
                chromaFilter?.draw(sourceTexId, sourceTexMatrix, mvpMatrix, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
            }
        } else {
            colorFilter?.updateConfig(clip.filterConfig)
            colorFilter?.updateCropFlip(clip.cropRect, clip.flipHorizontal, clip.flipVertical)
            if (sourceIsOes) {
                colorFilter?.drawOes(sourceTexId, sourceTexMatrix, mvpMatrix, rotationDegrees, fboWidth, fboHeight)
            } else {
                colorFilter?.draw(sourceTexId, sourceTexMatrix, mvpMatrix, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
            }
        }
        fbo.unbind()

        val outputTexId = fbo.textureId

        if (clip.maskConfig.shape != MaskShape.NONE) {
            maskFilter?.updateConfig(clip.maskConfig)
            maskFilter?.draw(outputTexId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        } else if (clip.lutConfig != null) {
            val lut = clip.lutConfig
            if (lut.uri != currentLutUri) {
                lutFilter?.release()
                lutFilter = LutFilter(context, lut.uri, lut.intensity).also { it.init() }
                currentLutUri = lut.uri
            } else {
                lutFilter?.intensity = lut.intensity
            }
            lutFilter?.draw(outputTexId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        } else {
            passthroughFilter?.draw(outputTexId, GlUtil.IDENTITY_MATRIX, GlUtil.IDENTITY_MATRIX, fboWidth, fboHeight, GLES20.GL_TEXTURE_2D)
        }
    }

    fun release() {
        intermediateFbo?.release()
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
