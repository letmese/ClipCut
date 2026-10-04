package com.clipcut.core.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.clipcut.core.gl.InputSurface
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.ExportCodec
import com.clipcut.core.model.ExportConfig

class VideoEncoder(private val config: ExportConfig) {

    private var encoder: MediaCodec? = null
    var inputSurface: InputSurface? = null
        private set

    private var trackIndex = -1
    private val bufferInfo = MediaCodec.BufferInfo()

    fun init() {
        val mimeType = when (config.codec) {
            ExportCodec.H264 -> "video/avc"
            ExportCodec.H265 -> "video/hevc"
        }

        val format = MediaFormat.createVideoFormat(mimeType, config.customWidth, config.customHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval
        }

        AppLog.i(TAG, "Configuring VideoEncoder: $mimeType ${config.customWidth}x${config.customHeight}, ${config.bitRate / 1000}kbps, ${config.frameRate}fps")

        val enc = MediaCodec.createEncoderByType(mimeType)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val rawSurface = enc.createInputSurface()
        inputSurface = InputSurface(rawSurface)
        enc.start()
        encoder = enc
        AppLog.i(TAG, "VideoEncoder started successfully with hardware InputSurface")
    }

    /**
     * Pulls encoded output buffers from MediaCodec and writes them to MediaMuxerWrapper.
     */
    fun drainEncoder(muxerWrapper: MediaMuxerWrapper, endOfStream: Boolean) {
        val enc = encoder ?: return

        if (endOfStream) {
            AppLog.i(TAG, "Signaling end of input stream to video encoder")
            enc.signalEndOfInputStream()
        }

        val timeoutUs = if (endOfStream) 10_000L else 0L

        while (true) {
            val status = enc.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return // Nothing ready right now
                    // In EOS mode, loop a few times to flush
                    Thread.sleep(5)
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (trackIndex != -1) {
                        AppLog.w(TAG, "Video encoder output format changed twice!")
                    }
                    val newFormat = enc.outputFormat
                    AppLog.i(TAG, "Video encoder output format ready: $newFormat")
                    trackIndex = muxerWrapper.addVideoTrack(newFormat)
                }
                status >= 0 -> {
                    val encodedData = enc.getOutputBuffer(status)
                        ?: throw RuntimeException("Encoder output buffer $status was null")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        AppLog.d(TAG, "Ignoring BUFFER_FLAG_CODEC_CONFIG (${bufferInfo.size} bytes)")
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxerWrapper.writeVideoSample(encodedData, bufferInfo)
                    }

                    enc.releaseOutputBuffer(status, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        AppLog.i(TAG, "Video encoder reached END_OF_STREAM")
                        break
                    }
                }
            }
        }
    }

    fun release() {
        try {
            inputSurface?.release()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error releasing InputSurface", e)
        }
        inputSurface = null

        try {
            encoder?.stop()
            encoder?.release()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error releasing encoder", e)
        }
        encoder = null
        AppLog.d(TAG, "VideoEncoder released")
    }

    companion object {
        private const val TAG = "VideoEncoder"
    }
}
