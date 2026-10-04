package com.clipcut.core.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.TimelineItem

class VideoDecoder(
    private val context: Context,
    private val clip: TimelineItem,
    private val outputSurface: OutputSurface
) {
    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var videoTrackIndex = -1
    private var isEosSignaled = false
    private var isDecoderEos = false

    var videoWidth: Int = 0
        private set
    var videoHeight: Int = 0
        private set
    var videoRotation: Int = 0
        private set
    var durationUs: Long = 0L
        private set

    fun init() {
        val ext = MediaExtractor()
        val uri = Uri.parse(clip.uri)
        ext.setDataSource(context, uri, null)
        extractor = ext

        for (i in 0 until ext.trackCount) {
            val format = ext.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoTrackIndex = i
                ext.selectTrack(i)

                videoWidth = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 1920
                videoHeight = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 1080
                videoRotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

                AppLog.i(TAG, "Selected video track $i: $mime (${videoWidth}x${videoHeight}, rot=$videoRotation, dur=${durationUs / 1000}ms)")

                val dec = MediaCodec.createDecoderByType(mime)
                dec.configure(format, outputSurface.surface, null, 0)
                dec.start()
                decoder = dec
                break
            }
        }

        if (decoder == null) {
            throw RuntimeException("No video decoder could be created for clip ${clip.uri}")
        }

        // Seek to trim start
        if (clip.trimStartUs > 0) {
            AppLog.d(TAG, "Seeking to trimStartUs: ${clip.trimStartUs / 1000}ms")
            ext.seekTo(clip.trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }
    }

    /**
     * Steps the decoder until one frame is rendered to OutputSurface, or EOS is reached.
     * Returns the frame PTS in microseconds, or -1 if EOS.
     */
    fun decodeNextFrame(timeoutUs: Long = 10_000): Long {
        val dec = decoder ?: return -1L
        val ext = extractor ?: return -1L

        val bufferInfo = MediaCodec.BufferInfo()

        while (!isDecoderEos) {
            // 1. Feed input buffers from MediaExtractor
            if (!isEosSignaled) {
                val inputIndex = dec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = dec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = ext.readSampleData(inputBuffer, 0)
                        val sampleTime = ext.sampleTime

                        if (sampleSize < 0 || (clip.trimEndUs > 0 && sampleTime > clip.trimEndUs)) {
                            AppLog.d(TAG, "Signaling EOS to decoder (sampleSize=$sampleSize, sampleTime=$sampleTime)")
                            dec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEosSignaled = true
                        } else {
                            dec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, ext.sampleFlags)
                            ext.advance()
                        }
                    }
                }
            }

            // 2. Pull output buffers from MediaCodec
            val outIndex = dec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = dec.outputFormat
                    AppLog.d(TAG, "Decoder output format changed: $newFormat")
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output ready yet, continue looping
                }
                outIndex >= 0 -> {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        AppLog.i(TAG, "Decoder reached END_OF_STREAM")
                        isDecoderEos = true
                        dec.releaseOutputBuffer(outIndex, false)
                        return -1L
                    }

                    val ptsUs = bufferInfo.presentationTimeUs

                    // Skip frames prior to trimStartUs if seek landed on earlier keyframe
                    if (ptsUs < clip.trimStartUs) {
                        dec.releaseOutputBuffer(outIndex, false)
                        continue
                    }

                    // Release buffer to SurfaceTexture (render = true)
                    dec.releaseOutputBuffer(outIndex, true)

                    // Await new image on OutputSurface
                    val success = outputSurface.awaitNewImage()
                    if (success) {
                        return ptsUs
                    } else {
                        AppLog.w(TAG, "Frame rendered but awaitNewImage timed out for pts $ptsUs")
                    }
                }
            }
        }

        return -1L
    }

    fun release() {
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error releasing decoder", e)
        }
        decoder = null

        try {
            extractor?.release()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error releasing extractor", e)
        }
        extractor = null
        AppLog.d(TAG, "VideoDecoder released")
    }

    companion object {
        private const val TAG = "VideoDecoder"
    }
}
