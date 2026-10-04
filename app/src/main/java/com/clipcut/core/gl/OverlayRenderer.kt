package com.clipcut.core.gl

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.OverlayItem

class OverlayRenderer(private val context: Context) {

    private val quadFilter = object : GlFilter() {}
    private val textureCache = mutableMapOf<String, CachedTexture>()

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

        // Enable alpha blending for overlays
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        for (item in activeOverlays) {
            val texture = getOrCreateTexture(item) ?: continue

            // Build MVP matrix for overlay positioning
            val mvpMatrix = FloatArray(16)
            Matrix.setIdentityM(mvpMatrix, 0)

            // Convert normalized coordinates (0..1) to NDC (-1..1)
            val posX = (item.normalizedX * 2.0f) - 1.0f
            val posY = 1.0f - (item.normalizedY * 2.0f) // Invert Y for GL coords

            Matrix.translateM(mvpMatrix, 0, posX, posY, 0f)
            Matrix.rotateM(mvpMatrix, 0, item.rotation, 0f, 0f, 1f)

            // Aspect ratio scaling
            val scaleX = (texture.width.toFloat() / viewportWidth.toFloat()) * item.scale
            val scaleY = (texture.height.toFloat() / viewportHeight.toFloat()) * item.scale
            Matrix.scaleM(mvpMatrix, 0, scaleX, scaleY, 1f)

            quadFilter.draw(
                texId = texture.texId,
                mvpMatrix = mvpMatrix,
                width = viewportWidth,
                height = viewportHeight,
                textureTarget = GLES20.GL_TEXTURE_2D
            )
        }

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun getOrCreateTexture(item: OverlayItem): CachedTexture? {
        val sig = "${item.text}_${item.textColor}_${item.textSizeSp}_${item.imageUri}"
        val existing = textureCache[item.id]
        if (existing != null && existing.signature == sig) {
            return existing
        }

        // Recreate texture
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
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.textColor.toInt()
            textSize = item.textSizeSp * 2.5f // High-DPI rasterization
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 2f, 2f, Color.argb(180, 0, 0, 0))
        }

        val bounds = Rect()
        paint.getTextBounds(item.text, 0, item.text.length, bounds)

        val padding = 32
        val width = maxOf(64, bounds.width() + padding * 2)
        val height = maxOf(64, bounds.height() + padding * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

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
