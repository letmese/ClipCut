package com.clipcut.core.codec

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.clipcut.core.gl.ColorAdjustFilter
import com.clipcut.core.gl.GlUtil
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.gl.OverlayRenderer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class ExportResult(
    val file: File,
    val publicUri: Uri? = null,
    val galleryPath: String = "Movies/ClipCut"
)

class ExportEngine(
    private val context: Context,
    private val project: Project,
    private val config: ExportConfig
) {
    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        AppLog.w(TAG, "Export cancellation requested by user")
        isCancelled.set(true)
    }

    suspend fun export(
        onProgress: (progress: Float, status: String) -> Unit
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val totalDurationUs = project.totalDurationUs
        if (totalDurationUs <= 0 || project.videoClips.isEmpty()) {
            val err = "Project has no video clips or zero duration"
            AppLog.e(TAG, err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val exportDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(exportDir, "ClipCut_${timeStamp}.mp4")

        AppLog.i(TAG, "=== Starting Video Export ===")
        AppLog.i(TAG, "Output file: ${outputFile.absolutePath}")
        AppLog.i(TAG, "Config: ${config.customWidth}x${config.customHeight}, ${config.bitRate / 1000}kbps, ${config.frameRate}fps, codec=${config.codec}")
        AppLog.i(TAG, "Total duration: ${totalDurationUs / 1_000_000.0}s, Clips: ${project.videoClips.size}")

        var muxerWrapper: MediaMuxerWrapper? = null
        var videoEncoder: VideoEncoder? = null
        var outputSurface: OutputSurface? = null
        var colorFilter: ColorAdjustFilter? = null
        var overlayRenderer: OverlayRenderer? = null

        val startTimeMs = System.currentTimeMillis()

        try {
            onProgress(0.01f, "Initializing encoder & GL pipeline...")

            // 1. Initialize Muxer (Video + Audio tracks)
            val hasAudio = project.videoClips.any { it.volume > 0.001f } || project.audioTracks.isNotEmpty()
            val expectedTracks = if (hasAudio) 2 else 1
            muxerWrapper = MediaMuxerWrapper(outputFile.absolutePath, expectedTracks)

            // 2. Initialize Video Encoder
            videoEncoder = VideoEncoder(config)
            videoEncoder.init()
            val inputSurface = videoEncoder.inputSurface ?: throw RuntimeException("InputSurface is null")

            // 3. Make EGL Context current on encoder input surface
            inputSurface.makeCurrent()

            // 4. Initialize OpenGL filters & overlay renderer
            val oesTexId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            outputSurface = OutputSurface(oesTexId)

            colorFilter = ColorAdjustFilter()
            colorFilter.init()

            overlayRenderer = OverlayRenderer(context)
            overlayRenderer.init()

            val texMatrix = FloatArray(16)
            var currentTimelineOffsetUs = 0L
            var totalFramesRendered = 0L

            // 5. Decode & Render Each Video Clip
            for ((clipIndex, clip) in project.videoClips.withIndex()) {
                if (isCancelled.get()) {
                    throw InterruptedException("Export cancelled")
                }

                AppLog.i(TAG, "Processing clip ${clipIndex + 1}/${project.videoClips.size}: ${clip.id} (speed=${clip.speed}x, trim=${clip.trimStartUs / 1000}..${clip.trimEndUs / 1000}ms)")

                val decoder = VideoDecoder(context, clip, outputSurface)
                decoder.init()

                var lastPtsUs = -1L
                try {
                    while (!isCancelled.get()) {
                        val ptsUs = decoder.decodeNextFrame()
                        if (ptsUs < 0) {
                            AppLog.d(TAG, "Clip $clipIndex decoding completed")
                            break
                        }

                        // Calculate presentation time scaled by speed
                        val clipLocalTimeUs = (ptsUs - clip.trimStartUs).coerceAtLeast(0L)
                        val scaledLocalTimeUs = (clipLocalTimeUs / clip.speed).toLong()
                        val outputPtsUs = currentTimelineOffsetUs + scaledLocalTimeUs

                        // Avoid backwards or duplicate PTS
                        if (outputPtsUs <= lastPtsUs) {
                            continue
                        }
                        lastPtsUs = outputPtsUs

                        // OpenGL Rendering
                        GLES20.glViewport(0, 0, config.customWidth, config.customHeight)
                        GLES20.glClearColor(0f, 0f, 0f, 1f)
                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                        outputSurface.getTransformMatrix(texMatrix)

                        // Apply filter
                        colorFilter.updateConfig(clip.filterConfig)
                        colorFilter.drawOes(
                            texId = oesTexId,
                            texMatrix = texMatrix,
                            width = config.customWidth,
                            height = config.customHeight
                        )

                        // Apply overlays (text & stickers)
                        overlayRenderer.renderOverlays(
                            overlays = project.overlays,
                            currentPtsUs = outputPtsUs,
                            viewportWidth = config.customWidth,
                            viewportHeight = config.customHeight
                        )

                        // Set presentation timestamp in nanoseconds and submit frame to encoder
                        inputSurface.setPresentationTime(outputPtsUs * 1000L)
                        inputSurface.swapBuffers()
                        totalFramesRendered++

                        // Drain output from video encoder into muxer
                        videoEncoder.drainEncoder(muxerWrapper, false)

                        // Progress callback (Video is 0% to 75% of export)
                        val videoProgress = (outputPtsUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f) * 0.75f
                        onProgress(videoProgress, "Encoding video frames (${(videoProgress * 100).toInt()}%)...")
                    }
                } finally {
                    decoder.release()
                }

                currentTimelineOffsetUs += clip.effectiveDurationUs
            }

            if (isCancelled.get()) throw InterruptedException("Export cancelled")

            // 6. Signal end of input stream to video encoder & drain remaining buffers
            onProgress(0.75f, "Finalizing video track...")
            videoEncoder.drainEncoder(muxerWrapper, true)

            // 7. Audio Processing & Muxing
            if (hasAudio && !isCancelled.get()) {
                onProgress(0.78f, "Mixing and encoding audio tracks...")
                val audioProcessor = AudioProcessor(context, project, config)
                audioProcessor.processAudio(muxerWrapper, totalDurationUs) { audioSubProgress ->
                    val combinedProgress = 0.78f + (audioSubProgress * 0.18f)
                    onProgress(combinedProgress, "Encoding audio (${(combinedProgress * 100).toInt()}%)...")
                }
            }

            // 8. Finalize Muxer
            onProgress(0.98f, "Closing output MP4 container...")
            muxerWrapper.stopAndRelease()

            val elapsedTimeSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
            val fileSizeMb = outputFile.length() / (1024.0 * 1024.0)
            AppLog.i(TAG, "=== Export Succeeded ===")
            AppLog.i(TAG, "Rendered $totalFramesRendered frames in ${elapsedTimeSec}s (${String.format(Locale.US, "%.1f", totalFramesRendered / elapsedTimeSec)} fps)")
            AppLog.i(TAG, "Output file size: ${String.format(Locale.US, "%.2f", fileSizeMb)} MB (${outputFile.absolutePath})")

            onProgress(0.99f, "Saving video to phone gallery...")
            val publicUri = copyToPublicGallery(context, outputFile)

            onProgress(1.0f, "Export Complete!")
            Result.success(ExportResult(outputFile, publicUri))

        } catch (t: Throwable) {
            AppLog.e(TAG, "Export failed: ${t.message}", t)
            muxerWrapper?.stopAndRelease()
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(t)
        } finally {
            colorFilter?.release()
            overlayRenderer?.release()
            outputSurface?.release()
            videoEncoder?.release()
        }
    }

    private fun copyToPublicGallery(context: Context, sourceFile: File): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipCut")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, contentValues)
            if (itemUri != null) {
                resolver.openOutputStream(itemUri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                }

                AppLog.i(TAG, "Copied video to Gallery: $itemUri (Movies/ClipCut/${sourceFile.name})")

                try {
                    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    val publicFilePath = File(publicDir, "ClipCut/${sourceFile.name}").absolutePath
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(publicFilePath, sourceFile.absolutePath),
                        arrayOf("video/mp4")
                    ) { path, uri ->
                        AppLog.d(TAG, "MediaScanner indexed: $path -> $uri")
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "MediaScannerConnection warning: ${e.message}")
                }

                itemUri
            } else {
                AppLog.w(TAG, "MediaStore insert returned null, video remains in app storage")
                null
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to copy video to public MediaStore gallery", e)
            null
        }
    }

    companion object {
        private const val TAG = "ExportEngine"
    }
}
