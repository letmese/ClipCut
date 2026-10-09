package com.clipcut.core.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import com.clipcut.core.model.FontFamilyOption
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.TextAnimation

/**
 * OverlayRenderer — paints overlays (text + styling) into a reusable
 * Bitmap then uploads it as a GL texture.
 */
class OverlayRenderer(private val context: Context) {
    private var width: Int = 1920
    private var height: Int = 1080
    private var overlayTexId: Int = -1
    val textureId: Int get() = overlayTexId

    private var reusableBitmap: Bitmap? = null
    private var reusableCanvas: Canvas? = null
    private var flippedBitmap: Bitmap? = null
    private var flippedCanvas: Canvas? = null
    private val flipMatrix = android.graphics.Matrix()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun init() {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        overlayTexId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun renderOverlays(
        overlays: List<OverlayItem>,
        currentPtsUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        if (overlayTexId == -1) return

        if (width != viewportWidth || height != viewportHeight || reusableBitmap == null) {
            width = viewportWidth.coerceAtLeast(1)
            height = viewportHeight.coerceAtLeast(1)
            reusableBitmap?.recycle()
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            reusableBitmap = bmp
            reusableCanvas = Canvas(bmp)
            flippedBitmap?.recycle()
            flippedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        val bmp = reusableBitmap ?: return
        val canvas = reusableCanvas ?: return

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        if (overlays.isEmpty()) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            return
        }

        overlays.filter { currentPtsUs in it.timelineStartUs..it.timelineEndUs }.forEach { o ->
            val (x, y, scale, rot) = interpolatedTransform(o, currentPtsUs)
            val cx = (x * width).toInt()
            val cy = (y * height).toInt()

            var animAlpha = 1f
            var animOffsetX = 0f
            var animOffsetY = 0f
            var animScaleMul = 1f
            if (o.animation != TextAnimation.NONE && o.animation != TextAnimation.TYPEWRITER) {
                val animProgress = ((currentPtsUs - o.timelineStartUs).toFloat() / (o.animationDurationMs * 1000f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                when (o.animation) {
                    TextAnimation.FADE_IN -> animAlpha = animProgress
                    TextAnimation.SLIDE_LEFT -> {
                        animOffsetX = (1f - animProgress) * 250f * (width / 1080f)
                        animAlpha = animProgress
                    }
                    TextAnimation.SLIDE_UP -> {
                        animOffsetY = (1f - animProgress) * 150f * (height / 1920f)
                        animAlpha = animProgress
                    }
                    TextAnimation.SCALE_IN -> {
                        animScaleMul = 0.2f + 0.8f * animProgress
                        animAlpha = animProgress
                    }
                    TextAnimation.BOUNCE -> {
                        val b = if (animProgress < 0.6f) animProgress * 1.8f else (1.0f + 0.15f * kotlin.math.sin(animProgress * 3.14159f))
                        animScaleMul = b.coerceIn(0.1f, 1.25f)
                    }
                    else -> {}
                }
            }

            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.color = o.textColor.toInt()
            textPaint.alpha = (255 * animAlpha.coerceIn(0f, 1f)).toInt()
            textPaint.textSize = o.textSizeSp * scale * animScaleMul * (width / 1080f)
            textPaint.isFakeBoldText = o.textBold
            textPaint.textSkewX = if (o.textItalic) -0.25f else 0f
            textPaint.typeface = when (o.fontFamily) {
                FontFamilyOption.SERIF -> Typeface.SERIF
                FontFamilyOption.MONOSPACE -> Typeface.MONOSPACE
                FontFamilyOption.SANS_SERIF_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                else -> Typeface.DEFAULT
            }
            if (o.dropShadow) {
                textPaint.setShadowLayer(8f * (width / 1080f), 3f * (width / 1080f), 3f * (width / 1080f), 0xCC000000.toInt())
            }

            val text = applyAnimation(o, currentPtsUs)
            if (text.isNotEmpty()) {
                val fm = textPaint.fontMetrics
                val textWidth = textPaint.measureText(text)
                val textHeight = fm.descent - fm.ascent
                val drawX = (cx + animOffsetX) - textWidth / 2
                val drawY = (cy + animOffsetY) - textHeight / 2 - fm.ascent

                if (o.backgroundColor != 0L) {
                    bgPaint.color = o.backgroundColor.toInt()
                    bgPaint.alpha = (Color.alpha(o.backgroundColor.toInt()) * animAlpha).toInt()
                    val pad = o.backgroundPaddingPx.toFloat()
                    canvas.drawRect(
                        (cx + animOffsetX) - textWidth / 2 - pad, (cy + animOffsetY) - textHeight / 2 - pad,
                        (cx + animOffsetX) + textWidth / 2 + pad, (cy + animOffsetY) + textHeight / 2 + pad, bgPaint
                    )
                }
                canvas.save()
                canvas.rotate(rot, (cx + animOffsetX), (cy + animOffsetY))

                // Draw stroke outline if specified
                if (o.strokeWidthPx > 0f && o.strokeColor != 0L) {
                    val strokePaint = Paint(textPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = o.strokeWidthPx * (width / 1080f)
                        color = o.strokeColor.toInt()
                        alpha = (255 * animAlpha.coerceIn(0f, 1f)).toInt()
                        clearShadowLayer()
                    }
                    canvas.drawText(text, drawX, drawY, strokePaint)
                }

                canvas.drawText(text, drawX, drawY, textPaint)
                canvas.restore()
            }
        }

        // Upload: GLUtils.texImage2D stores row 0 at texture V=0 (bottom).
        // Canvas row 0 is visual top. Flip bitmap once vertically so texture V=0
        // corresponds to visual top. Now every caller uses IDENTITY tex-matrix!
        if (flippedBitmap == null || flippedBitmap?.width != width || flippedBitmap?.height != height) {
            flippedBitmap?.recycle()
            val newBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            flippedBitmap = newBmp
            flippedCanvas = Canvas(newBmp)
        }
        val dst = flippedBitmap ?: return
        val dstCanvas = flippedCanvas ?: Canvas(dst).also { flippedCanvas = it }
        dstCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        flipMatrix.reset()
        flipMatrix.setScale(1f, -1f)
        flipMatrix.postTranslate(0f, height.toFloat())
        dstCanvas.drawBitmap(bmp, flipMatrix, null)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, dst, 0)
    }

    private fun interpolatedTransform(o: OverlayItem, ptsUs: Long): FloatArray {
        if (o.keyframes.isEmpty()) return floatArrayOf(o.normalizedX, o.normalizedY, o.scale, o.rotation)
        val sorted = o.keyframes.sortedBy { it.timeOffsetUs }
        val relUs = ptsUs - o.timelineStartUs
        if (relUs <= sorted.first().timeOffsetUs) {
            val f = sorted.first()
            return floatArrayOf(f.normalizedX, f.normalizedY, f.scale, f.rotation)
        }
        if (relUs >= sorted.last().timeOffsetUs) {
            val f = sorted.last()
            return floatArrayOf(f.normalizedX, f.normalizedY, f.scale, f.rotation)
        }
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            if (relUs in a.timeOffsetUs..b.timeOffsetUs) {
                val t = (relUs - a.timeOffsetUs).toFloat() / (b.timeOffsetUs - a.timeOffsetUs).coerceAtLeast(1L)
                return floatArrayOf(
                    a.normalizedX + (b.normalizedX - a.normalizedX) * t,
                    a.normalizedY + (b.normalizedY - a.normalizedY) * t,
                    a.scale + (b.scale - a.scale) * t,
                    a.rotation + (b.rotation - a.rotation) * t
                )
            }
        }
        return floatArrayOf(o.normalizedX, o.normalizedY, o.scale, o.rotation)
    }

    private fun applyAnimation(o: OverlayItem, ptsUs: Long): String {
        if (o.animation == TextAnimation.NONE) return o.text
        val rel = (ptsUs - o.timelineStartUs).toFloat() / (o.animationDurationMs * 1000f).coerceAtLeast(1f)
        return when (o.animation) {
            TextAnimation.TYPEWRITER -> o.text.take((o.text.length * rel.coerceIn(0f, 1f)).toInt())
            else -> o.text
        }
    }

    fun release() {
        reusableBitmap?.recycle()
        reusableBitmap = null
        reusableCanvas = null
        flippedBitmap?.recycle()
        flippedBitmap = null
        flippedCanvas = null
        if (overlayTexId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(overlayTexId), 0)
            overlayTexId = -1
        }
    }
}
