package com.clipcut.core.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.Project
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioProcessor(
    private val context: Context,
    private val project: Project,
    private val exportConfig: ExportConfig
) {
    private var audioEncoder: MediaCodec? = null
    private var audioTrackIndex = -1
    private val bufferInfo = MediaCodec.BufferInfo()

    fun processAudio(
        muxerWrapper: MediaMuxerWrapper,
        totalDurationUs: Long,
        onProgress: (Float) -> Unit
    ) {
        AppLog.i(TAG, "Starting audio processing pipeline (totalDuration: ${totalDurationUs / 1000}ms)")

        // 1. Configure AAC Audio Encoder
        val audioFormat = MediaFormat.createAudioFormat(
            "audio/mp4a-latm",
            exportConfig.audioSampleRate,
            exportConfig.audioChannels
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, exportConfig.audioBitRate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
        encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        audioEncoder = encoder
        AppLog.i(TAG, "Audio encoder started (AAC ${exportConfig.audioSampleRate}Hz, ${exportConfig.audioChannels}ch, ${exportConfig.audioBitRate}bps)")

        // 2. Decode & Mix All Audio Streams to PCM Buffer
        val pcmData = decodeAndMixPcm(totalDurationUs, onProgress)

        // 3. Feed PCM to AAC Encoder & write to Muxer
        feedPcmToEncoder(encoder, pcmData, muxerWrapper)

        // 4. Clean up
        try {
            encoder.stop()
            encoder.release()
        } catch (e: Exception) {
            AppLog.e(TAG, "Error stopping audio encoder", e)
        }
        audioEncoder = null
        AppLog.i(TAG, "Audio processing complete")
    }

    private fun decodeAndMixPcm(
        totalDurationUs: Long,
        onProgress: (Float) -> Unit
    ): ByteArray {
        val sampleRate = exportConfig.audioSampleRate
        val channels = exportConfig.audioChannels
        val bytesPerSample = 2 // 16-bit PCM
        val totalSamples = ((totalDurationUs / 1_000_000.0) * sampleRate * channels).toInt()
        val mixedPcm = ShortArray(totalSamples)

        // Track 1: Original video audio
        val origVol = project.originalVideoVolume
        var clipTimelineStartUs = 0L

        for (clip in project.videoClips) {
            val clipVol = clip.volume * origVol
            if (clipVol > 0.001f) {
                try {
                    mixClipAudio(clip.uri, clip.trimStartUs, clip.trimEndUs, clipTimelineStartUs, clipVol, mixedPcm, sampleRate, channels)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to decode audio for clip ${clip.id}, inserting silence", e)
                }
            }
            clipTimelineStartUs += clip.effectiveDurationUs
            onProgress(0.2f)
        }

        // Track 2: Background music tracks
        val bgVol = project.backgroundMusicVolume
        for (track in project.audioTracks) {
            val trackVol = track.volume * bgVol
            if (trackVol > 0.001f) {
                try {
                    mixClipAudio(track.uri, track.trimStartUs, track.trimEndUs, track.timelineStartUs, trackVol, mixedPcm, sampleRate, channels)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to decode background track ${track.name}", e)
                }
            }
            onProgress(0.4f)
        }

        // Convert ShortArray to little-endian ByteArray
        val byteBuffer = ByteBuffer.allocate(mixedPcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in mixedPcm) {
            byteBuffer.putShort(s)
        }
        return byteBuffer.array()
    }

    private fun mixClipAudio(
        uriStr: String,
        trimStartUs: Long,
        trimEndUs: Long,
        timelineStartUs: Long,
        volume: Float,
        targetPcm: ShortArray,
        targetSampleRate: Int,
        targetChannels: Int
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, Uri.parse(uriStr), null)

        var audioTrack = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrack = i
                format = f
                extractor.selectTrack(i)
                break
            }
        }

        if (audioTrack == -1 || format == null) {
            extractor.release()
            return
        }

        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, null, null, 0)
        decoder.start()

        if (trimStartUs > 0) {
            extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }

        val decodedPcm = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var eos = false

        while (!eos) {
            val inIdx = decoder.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val inBuf = decoder.getInputBuffer(inIdx)
                if (inBuf != null) {
                    val size = extractor.readSampleData(inBuf, 0)
                    val time = extractor.sampleTime
                    if (size < 0 || (trimEndUs > 0 && time > trimEndUs)) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, size, time, extractor.sampleFlags)
                        extractor.advance()
                    }
                }
            }

            val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
            if (outIdx >= 0) {
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    eos = true
                }
                if (info.size > 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.get(chunk)
                        decodedPcm.write(chunk)
                    }
                }
                decoder.releaseOutputBuffer(outIdx, false)
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        // Mix decoded PCM samples into target array with timeline offset and volume scaling
        val rawBytes = decodedPcm.toByteArray()
        val shortBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val startSampleIdx = ((timelineStartUs / 1_000_000.0) * targetSampleRate * targetChannels).toInt()

        var i = 0
        while (shortBuffer.hasRemaining() && (startSampleIdx + i) < targetPcm.size) {
            val sample = shortBuffer.get()
            val existing = targetPcm[startSampleIdx + i]
            val mixed = (existing + (sample * volume)).toInt().coerceIn(-32768, 32767).toShort()
            targetPcm[startSampleIdx + i] = mixed
            i++
        }
    }

    private fun feedPcmToEncoder(
        encoder: MediaCodec,
        pcmData: ByteArray,
        muxerWrapper: MediaMuxerWrapper
    ) {
        var pcmOffset = 0
        val chunkSize = 4096
        var ptsUs = 0L
        val bytesPerSec = exportConfig.audioSampleRate * exportConfig.audioChannels * 2
        var isEos = false

        while (!isEos) {
            // Feed input
            val inIdx = encoder.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val inBuf = encoder.getInputBuffer(inIdx)
                if (inBuf != null) {
                    val remaining = pcmData.size - pcmOffset
                    val toCopy = minOf(remaining, chunkSize)

                    if (toCopy > 0) {
                        inBuf.clear()
                        inBuf.put(pcmData, pcmOffset, toCopy)
                        ptsUs = ((pcmOffset.toDouble() / bytesPerSec) * 1_000_000.0).toLong()
                        encoder.queueInputBuffer(inIdx, 0, toCopy, ptsUs, 0)
                        pcmOffset += toCopy
                    } else {
                        AppLog.i(TAG, "Audio input complete, queuing END_OF_STREAM")
                        encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }
            }

            // Pull output
            val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    AppLog.i(TAG, "Audio encoder output format ready: $newFormat")
                    audioTrackIndex = muxerWrapper.addAudioTrack(newFormat)
                }
                outIdx >= 0 -> {
                    val outBuf = encoder.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxerWrapper.writeAudioSample(outBuf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        AppLog.i(TAG, "Audio encoder reached END_OF_STREAM")
                        isEos = true
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AudioProcessor"
    }
}
