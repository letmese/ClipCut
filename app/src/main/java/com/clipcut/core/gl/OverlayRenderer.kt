package com.clipcut.core.gl

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.FontFamilyOption
import com.clipcut.core.model.OverlayItem

class OverlayRenderer(private val context: Context) {

    private val quadFilter = object : GlFilter() {}
    private val textureCache = mutableMapOf<String, CachedTexture>()

    // Reused scratch matrix — one per renderer instance, not per frame
    private val scratchMvpMatrix = FloatArray(16)

    private data class CachedTexture(
        val texId: Int,
        val width: Int,
        val height: Int,
        val signature: String
    )

    fun init() {
        quadFilter.init()
    }

    fun renderOverlays(
        overlays: List<OverlayItem>,
        currentPtsUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        val activeOverlays = overlays.filter {
            currentPtsUs in it.timelineStartUs..it.timelineEndUs
        }
        if (activeOverlays.isEmpty()) return

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        for (item in activeOverlays) {
            val texture = getOrCreateTexture(item) ?: continue

            // Reuse the class-level scratch matrix instead of allocating per overlay
            Matrix.setIdentityM(scratchMvpMatrix, 0)

            var normX = item.normalizedX
            var normY = item.normalizedY
            var sc = item.scale
            var rot = item.rotation

            if (item.keyframes.isNotEmpty()) {
                val relUs = currentPtsUs - item.timelineStartUs
                val sorted = item.keyframes.sortedBy { it.timeOffsetUs }
                if (relUs <= sorted.first().timeOffsetUs) {
                    normX = sorted.first().normalizedX
                    normY = sorted.first().normalizedY
                    sc = sorted.first().scale
                    rot = sorted.first().rotation
                } else if (relUs >= sorted.last().timeOffsetUs) {
                    normX = sorted.last().normalizedX
                    normY = sorted.last().normalizedY
                    sc = sorted.last().scale
                    rot = sorted.last().rotation
                } else {
                    for (k in 0 until sorted.size - 1) {
                        val k1 = sorted[k]
                        val k2 = sorted[k + 1]
                        if (relUs in k1.timeOffsetUs..k2.timeOffsetUs) {
                            val span = (k2.timeOffsetUs - k1.timeOffsetUs).toFloat().coerceAtLeast(1f)
                            val t = (relUs - k1.timeOffsetUs) / span
                            normX = k1.normalizedX + t * (k2.normalizedX - k1.normalizedX)
                            normY = k1.normalizedY + t * (k2.normalizedY - k1.normalizedY)
                            sc = k1.scale + t * (k2.scale - k1.scale)
                            rot = k1.rotation + t * (k2.rotation - k1.rotation)
                            break
                        }
                    }
                }
            }

            val posX = (normX * 2.0f) - 1.0f
            val posY = 1.0f - (normY * 2.0f)

            Matrix.translateM(scratchMvpMatrix, 0, posX, posY, 0f)
            Matrix.rotateM(scratchMvpMatrix, 0, rot, 0f, 0f, 1f)

            val scaleX = (texture.width.toFloat() / viewportWidth.toFloat()) * sc
            val scaleY = (texture.height.toFloat() / viewportHeight.toFloat()) * sc
            Matrix.scaleM(scratchMvpMatrix, 0, scaleX, scaleY, 1f)

            quadFilter.draw(
                texId = texture.texId,
                mvpMatrix = scratchMvpMatrix,
                width = viewportWidth,
                height = viewportHeight,
                textureTarget = GLES20.GL_TEXTURE_2D
            )
        }

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun getOrCreateTexture(item: OverlayItem): CachedTexture? {
        val sig = "${item.text}_${item.textColor}_${item.textSizeSp}_${item.imageUri}_" +
                  "${item.textBold}_${item.textItalic}_${item.fontFamily}_" +
                  "${item.backgroundColor}_${item.backgroundPaddingPx}"
        val existing = textureCache[item.id]
        if (existing != null && existing.signature == sig) {
            return existing
        }

        existing?.let {
            val arr = intArrayOf(it.texId)
            GLES20.glDeleteTextures(1, arr, 0)
        }

        val bitmap: Bitmap? = if (!item.imageUri.isNullOrEmpty()) {
            loadBitmapFromUri(item.imageUri)
        } else if (item.text.isNotEmpty()) {
            createTextBitmap(item)
        } else {
            null
        }

        if (bitmap == null) return null

        val texId = uploadBitmapToTexture(bitmap)
        val cached = CachedTexture(texId, bitmap.width, bitmap.height, sig)
        textureCache[item.id] = cached
        bitmap.recycle()
        return cached
    }

    private fun createTextBitmap(item: OverlayItem): Bitmap {
        val style = when {
            item.textBold && item.textItalic -> Typeface.BOLD_ITALIC
            item.textBold -> Typeface.BOLD
            item.textItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val family = when (item.fontFamily) {
            FontFamilyOption.DEFAULT -> Typeface.DEFAULT
            FontFamilyOption.SERIF -> Typeface.SERIF
            FontFamilyOption.MONOSPACE -> Typeface.MONOSPACE
            FontFamilyOption.SANS_SERIF_CONDENSED -> Typeface.SANS_SERIF
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.textColor.toInt()
            textSize = item.textSizeSp * 2.5f
            typeface = Typeface.create(family, style)
            setShadowLayer(6f, 2f, 2f, Color.argb(180, 0, 0, 0))
        }

        val bounds = Rect()
        paint.getTextBounds(item.text, 0, item.text.length, bounds)

        val pad = 32
        val bgPad = item.backgroundPaddingPx
        val width = maxOf(64, bounds.width() + pad * 2 + bgPad * 2)
        val height = maxOf(64, bounds.height() + pad * 2 + bgPad * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background box if non-transparent
        if ((item.backgroundColor ushr 24) != 0L) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.backgroundColor.toInt()
            }
            val rect = RectF(
                (pad - bgPad).toFloat(),
                (pad - bgPad).toFloat(),
                (width - pad + bgPad).toFloat(),
                (height - pad + bgPad).toFloat()
            )
            canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
        }

        val x = (width - bounds.width()) / 2f - bounds.left
        val y = (height - bounds.height()) / 2f - bounds.top
        canvas.drawText(item.text, x, y, paint)

        return bitmap
    }

    private fun loadBitmapFromUri(uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load overlay image from $uriString", e)
            null
        }
    }

    private fun uploadBitmapToTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texId
    }

    fun release() {
        quadFilter.release()
        for (cached in textureCache.values) {
            val arr = intArrayOf(cached.texId)
            GLES20.glDeleteTextures(1, arr, 0)
        }
        textureCache.clear()
        AppLog.d(TAG, "OverlayRenderer released")
    }

    companion object {
        private const val TAG = "OverlayRenderer"
    }
}
