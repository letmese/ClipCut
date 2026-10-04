package com.clipcut.core.codec

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.clipcut.core.log.AppLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * For reversed clips, decode forward and cache frames, then serve in reverse order.
 * Memory-heavy but works reliably for short reversed clips (< 30s).
 * For longer clips, use frame-by-frame re-decode on demand.
 */
class ReverseDecoder(
    private val context: Context,
    private val uri: String,
    private val trimStartUs: Long,
    private val trimEndUs: Long,
    private val frameCount: Int
) {
    private val frames = ArrayList<Bitmap>(frameCount)
    private val isReady = AtomicBoolean(false)

    fun prepare() {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(uri))
            val durationUs = trimEndUs - trimStartUs
            val intervalUs = durationUs / frameCount
            for (i in frameCount - 1 downTo 0) {
                val timeUs = trimStartUs + i * intervalUs
                val bmp = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (bmp != null) frames.add(bmp)
            }
            retriever.release()
            isReady.set(true)
            AppLog.i("ReverseDecoder", "Prepared ${frames.size} reversed frames")
        } catch (e: Exception) {
            AppLog.e("ReverseDecoder", "Failed to prepare", e)
        }
    }

    fun getFrame(index: Int): Bitmap? {
        if (index in frames.indices) return frames[index]
        return null
    }

    val size: Int get() = frames.size

    fun release() {
        frames.forEach { if (!it.isRecycled) it.recycle() }
        frames.clear()
    }
}
