package com.clipcut.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * AssetImageHelper provides thread-safe, downsampled caching of overlay,
 * frame, sky, and backdrop assets for high-speed UI thumbnail rendering.
 */
object AssetImageHelper {
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val imageBitmapCache = ConcurrentHashMap<String, ImageBitmap>()

    fun loadThumbnail(
        context: Context,
        rawUri: String,
        targetWidth: Int = 260,
        targetHeight: Int = 180
    ): ImageBitmap? {
        val clean = rawUri.removePrefix("asset://").removePrefix("/")
        val cacheKey = "${clean}_${targetWidth}x${targetHeight}"

        imageBitmapCache[cacheKey]?.let { return it }

        val bmp = getOrLoadBitmap(context, clean, targetWidth, targetHeight) ?: return null
        val imgBmp = bmp.asImageBitmap()
        imageBitmapCache[cacheKey] = imgBmp
        return imgBmp
    }

    private fun getOrLoadBitmap(
        context: Context,
        assetPath: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val cacheKey = "${assetPath}_${targetWidth}x${targetHeight}"
        bitmapCache[cacheKey]?.let { return it }

        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(assetPath).use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }

            var inSampleSize = 1
            if (boundsOpts.outHeight > targetHeight || boundsOpts.outWidth > targetWidth) {
                val halfHeight = boundsOpts.outHeight / 2
                val halfWidth = boundsOpts.outWidth / 2
                while ((halfHeight / inSampleSize) >= targetHeight && (halfWidth / inSampleSize) >= targetWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = context.assets.open(assetPath).use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            bitmapCache[cacheKey] = decoded
            decoded
        } catch (_: Throwable) {
            null
        }
    }

    fun clearCache() {
        imageBitmapCache.clear()
        bitmapCache.values.forEach { try { it.recycle() } catch (_: Throwable) {} }
        bitmapCache.clear()
    }
}
