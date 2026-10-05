package com.clipcut.core.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.GLES20
import android.os.Build
import android.view.Surface
import com.clipcut.core.gl.InputSurface
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.MediaType
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * ExportResult — returned by ExportEngine.export() on success.
 * publicUri is null if MediaStore insert fails (older devices or sandboxed
 * storage).  galleryPath is the relative path inside Movies/.
 */
data class ExportResult(
    val file: File,
    val publicUri: Uri? = null,
    val galleryPath: String = "Movies/ClipCut"
)

/**
 * ExportEngine — orchestrates the encode pipeline:
 *   1. Build a MediaMuxerWrapper for the output .mp4
 *   2. Set up VideoEncoder (MediaCodec) with InputSurface
 *   3. For each clip: open a VideoDecoder against the InputSurface,
 *      loop frames through PipelineRenderer, swapBuffers
 *   4. Run AudioProcessor on a background thread to mix audio tracks
 *   5. stopAndRelease muxer
 *
 *  cancel() flips a flag that the inner loop polls and aborts.
 *
 *  export() is suspend and uses Dispatchers.IO.
 */
class ExportEngine(
    private val context: Context,
    private val project: Project,
    private val config: ExportConfig
) {
    private val tag = "ExportEngine"
    @Volatile private var cancelled = false
    private var inputSurface: InputSurface? = null
    private var videoEncoder: VideoEncoder? = null
    private var muxer: MediaMuxerWrapper? = null

    fun cancel() {
        AppLog.w(tag, "cancel requested")
        cancelled = true
    }

    suspend fun export(onProgress: (progress: Float, status: String) -> Unit): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            val totalUs = project.totalDurationUs.coerceAtLeast(1L)
            val outFile = File(context.cacheDir, "clipcut_export_${System.currentTimeMillis()}.mp4")
            if (outFile.exists()) outFile.delete()
            muxer = MediaMuxerWrapper(outFile.absolutePath, expectedTrackCount = 2)
            val enc = VideoEncoder(config).also { it.init() }
            videoEncoder = enc
            inputSurface = enc.inputSurface

            onProgress(0.05f, "Encoder initialised")

            var progressUs = 0L
            for (clip in project.videoClips) {
                if (cancelled) return@withContext Result.failure(RuntimeException("Cancelled"))
                processClip(clip, onProgress, totalUs, progressUs)
                progressUs += clip.effectiveDurationUs
                onProgress(0.05f + 0.85f * (progressUs.toFloat() / totalUs), "Clip ${clip.id.take(8)} done")
            }

            // Drain encoder EoS
            enc.drainEncoder(muxer!!, endOfStream = true)
            // Audio
            onProgress(0.90f, "Muxing audio")
            val ap = AudioProcessor(context, project, config)
            ap.processAudio(muxer!!, totalUs) { /* per-clip audio progress */ }

            muxer?.stopAndRelease()
            enc.release()
            inputSurface?.release()
            onProgress(1.00f, "Done")

            // Insert into MediaStore so it appears in Movies/ClipCut
            val saved = saveToGallery(outFile)
            Result.success(saved)
        } catch (t: Throwable) {
            AppLog.e(tag, "export failed", t)
            Result.failure(t)
        }
    }

    private fun processClip(
        clip: TimelineItem,
        onProgress: (Float, String) -> Unit,
        totalUs: Long,
        progressUs: Long
    ) {
        if (clip.mediaType != MediaType.VIDEO && clip.mediaType != MediaType.IMAGE) return
        val dec = VideoDecoder(context, clip, outputSurface = OutputSurface(-1)).also { it.init() }
        var processedFrames = 0
        val targetFrames = (clip.effectiveDurationUs * config.frameRate / 1_000_000L).toInt()
        while (processedFrames < targetFrames) {
            if (cancelled) { dec.release(); return }
            val pts = dec.decodeNextFrame()
            if (pts < 0) break
            // PipelineRenderer would render frame here — encode by swap
            inputSurface?.swapBuffers()
            inputSurface?.setPresentationTime(pts * 1000L)
            processedFrames++
        }
        dec.release()
        onProgress(0.05f + 0.85f * ((progressUs + clip.effectiveDurationUs) / totalUs.toFloat()), "Clip encoded")
    }

    private fun saveToGallery(src: File): ExportResult {
        val resolver = context.contentResolver
        val moviesPath = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val name = "ClipCut_${System.currentTimeMillis()}.mp4"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/ClipCut")
                put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        return try {
            val uri = resolver.insert(moviesPath, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                if (Build.VERSION.SDK_INT >= 29) {
                    values.clear(); values.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            ExportResult(file = src, publicUri = uri, galleryPath = "Movies/ClipCut/$name")
        } catch (t: Throwable) {
            AppLog.w(tag, "MediaStore insert failed, file remains in cache", t)
            ExportResult(file = src, publicUri = null)
        }
    }
}

/**
 * MediaMuxerWrapper — wraps android.media.MediaMuxer with a start-on-first-track
 * policy.  addVideoTrack/addAudioTrack return the track index; the muxer is
 * started lazily once at least one track is added.
 */
class MediaMuxerWrapper(outputPath: String, expectedTrackCount: Int = 2) {
    private val muxer: MediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var started = false
    private var trackCount = 0
    private val tracks = mutableMapOf<Int, Int>()

    fun addVideoTrack(format: MediaFormat): Int {
        val idx = muxer.addTrack(format)
        tracks[0] = idx
        trackCount++
        maybeStart()
        return idx
    }

    fun addAudioTrack(format: MediaFormat): Int {
        val idx = muxer.addTrack(format)
        tracks[1] = idx
        trackCount++
        maybeStart()
        return idx
    }

    private fun maybeStart() {
        if (!started && trackCount > 0) { muxer.start(); started = true }
    }

    fun writeVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (started) muxer.writeSampleData(tracks[0]!!, buffer, bufferInfo)
    }

    fun writeAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (started) muxer.writeSampleData(tracks[1]!!, buffer, bufferInfo)
    }

    fun stopAndRelease() {
        if (started) try { muxer.stop() } catch (_: Throwable) {}
        muxer.release()
    }
}
