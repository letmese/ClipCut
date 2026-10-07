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

            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.color = o.textColor.toInt()
            textPaint.textSize = o.textSizeSp * scale * (width / 1080f)
            textPaint.isFakeBoldText = o.textBold
            textPaint.textSkewX = if (o.textItalic) -0.25f else 0f
            textPaint.typeface = when (o.fontFamily) {
                FontFamilyOption.SERIF -> Typeface.SERIF
                FontFamilyOption.MONOSPACE -> Typeface.MONOSPACE
                FontFamilyOption.SANS_SERIF_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                else -> Typeface.DEFAULT
            }
            val text = applyAnimation(o, currentPtsUs)
            if (text.isNotEmpty()) {
                val fm = textPaint.fontMetrics
                val textWidth = textPaint.measureText(text)
                val textHeight = fm.descent - fm.ascent
                if (o.backgroundColor != 0L) {
                    bgPaint.color = o.backgroundColor.toInt()
                    val pad = o.backgroundPaddingPx.toFloat()
                    canvas.drawRect(
                        cx - textWidth / 2 - pad, cy - textHeight / 2 - pad,
                        cx + textWidth / 2 + pad, cy + textHeight / 2 + pad, bgPaint
                    )
                }
                canvas.save()
                canvas.rotate(rot, cx.toFloat(), cy.toFloat())
                canvas.drawText(text, cx - textWidth / 2, cy - textHeight / 2 - fm.ascent, textPaint)
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
