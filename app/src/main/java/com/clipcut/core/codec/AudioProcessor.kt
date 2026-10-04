package com.clipcut.core.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
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

        val pcmData = decodeAndMixPcm(totalDurationUs, onProgress)
        feedPcmToEncoder(encoder, pcmData, muxerWrapper)

        try { encoder.stop(); encoder.release() } catch (_: Exception) {}
        audioEncoder = null
    }

    private fun decodeAndMixPcm(
        totalDurationUs: Long,
        onProgress: (Float) -> Unit
    ): ByteArray {
        val sr = exportConfig.audioSampleRate
        val ch = exportConfig.audioChannels
        val totalSamples = ((totalDurationUs / 1_000_000.0) * sr * ch).toInt()
        val mixedPcm = ShortArray(totalSamples)

        // Build speech mask for ducking
        val speechMask = if (project.audioDuckingEnabled) {
            BooleanArray(totalSamples / ch)
        } else null

        val origVol = project.originalVideoVolume
        var clipStart = 0L
        val clipCount = project.videoClips.size.coerceAtLeast(1)
        var maskOffset = 0

        for ((i, clip) in project.videoClips.withIndex()) {
            val clipVol = clip.volume * origVol
            if (clipVol > 0.001f) {
                try {
                    mixClipAudioWithSpeed(clip, clipStart, clipVol, mixedPcm, sr, ch)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed clip audio ${clip.id}", e)
                }
                if (speechMask != null) {
                    val clipSamples = ((clip.effectiveDurationUs / 1_000_000.0) * sr).toInt()
                    for (s in 0 until clipSamples) {
                        val idx = maskOffset + s
                        if (idx < speechMask.size) speechMask[idx] = true
                    }
                }
            }
            clipStart += clip.effectiveDurationUs
            if (speechMask != null) {
                val clipSamples = ((clip.effectiveDurationUs / 1_000_000.0) * sr).toInt()
                maskOffset += clipSamples
            }
            onProgress(((i + 1).toFloat() / clipCount) * 0.6f)
        }

        val bgVol = project.backgroundMusicVolume
        val duckLevel = if (project.audioDuckingEnabled) project.audioDuckLevel else 1.0f
        val trackCount = project.audioTracks.size.coerceAtLeast(1)

        for ((i, track) in project.audioTracks.withIndex()) {
            val trackVol = track.volume * bgVol
            if (trackVol > 0.001f) {
                try {
                    mixTrackAudioWithDucking(track, trackVol, mixedPcm, sr, ch, speechMask, duckLevel)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed bg track ${track.name}", e)
                }
            }
            onProgress(0.6f + ((i + 1).toFloat() / trackCount) * 0.4f)
        }

        val bb = ByteBuffer.allocate(mixedPcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in mixedPcm) bb.putShort(s)
        return bb.array()
    }

    private fun mixClipAudioWithSpeed(
        clip: TimelineItem,
        timelineStartUs: Long,
        volume: Float,
        targetPcm: ShortArray,
        targetSampleRate: Int,
        targetChannels: Int
    ) {
        val decoded = decodeClipAudioToPcm(clip.uri, clip.trimStartUs, clip.trimEndUs) ?: return
        val stretched = if (kotlin.math.abs(clip.speed - 1.0f) > 0.001f) {
            stretchPcmWithSonic(decoded, clip.speed, targetSampleRate, targetChannels)
        } else decoded

        val shortBuffer = ByteBuffer.wrap(stretched).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val startIdx = ((timelineStartUs / 1_000_000.0) * targetSampleRate * targetChannels).toInt()
        val totalSamples = shortBuffer.remaining()
        val fadeInSamples = ((clip.fadeInMs / 1000.0) * targetSampleRate).toInt() * targetChannels
        val fadeOutSamples = ((clip.fadeOutMs / 1000.0) * targetSampleRate).toInt() * targetChannels

        var i = 0
        while (shortBuffer.hasRemaining() && (startIdx + i) < targetPcm.size) {
            val raw = shortBuffer.get()
            var gain = volume
            if (fadeInSamples > 0 && i < fadeInSamples) gain *= (i.toFloat() / fadeInSamples)
            val remaining = totalSamples - i
            if (fadeOutSamples > 0 && remaining < fadeOutSamples) gain *= (remaining.toFloat() / fadeOutSamples)
            targetPcm[startIdx + i] = (targetPcm[startIdx + i] + (raw * gain))
                .toInt().coerceIn(-32768, 32767).toShort()
            i++
        }
    }

    private fun mixTrackAudioWithDucking(
        track: com.clipcut.core.model.AudioTrackItem,
        volume: Float,
        targetPcm: ShortArray,
        targetSampleRate: Int,
        targetChannels: Int,
        speechMask: BooleanArray?,
        duckLevel: Float
    ) {
        val raw = decodeClipAudioToPcm(track.uri, track.trimStartUs, track.trimEndUs) ?: return
        val sb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val startIdx = ((track.timelineStartUs / 1_000_000.0) * targetSampleRate * targetChannels).toInt()
        val total = sb.remaining()
        val fadeIn = ((track.fadeInMs / 1000.0) * targetSampleRate).toInt() * targetChannels
        val fadeOut = ((track.fadeOutMs / 1000.0) * targetSampleRate).toInt() * targetChannels

        var i = 0
        while (sb.hasRemaining() && (startIdx + i) < targetPcm.size) {
            val s = sb.get()
            var g = volume
            if (fadeIn > 0 && i < fadeIn) g *= (i.toFloat() / fadeIn)
            val rem = total - i
            if (fadeOut > 0 && rem < fadeOut) g *= (rem.toFloat() / fadeOut)
            if (speechMask != null) {
                val sampleIdx = (startIdx + i) / targetChannels
                if (sampleIdx < speechMask.size && speechMask[sampleIdx]) g *= duckLevel
            }
            targetPcm[startIdx + i] = (targetPcm[startIdx + i] + (s * g))
                .toInt().coerceIn(-32768, 32767).toShort()
            i++
        }
    }

    private fun stretchPcmWithSonic(pcm: ByteArray, speed: Float, sr: Int, ch: Int): ByteArray {
        if (pcm.isEmpty()) return pcm
        val sonic = SonicAudioProcessor()
        sonic.setSpeed(speed); sonic.setPitch(1.0f)
        val fmt = AudioProcessor.AudioFormat(sr, channelsToChannelMask(ch), C.ENCODING_PCM_16BIT)
        try { sonic.configure(fmt); sonic.flush() } catch (e: Exception) { return pcm }

        val out = ByteArrayOutputStream(pcm.size)
        val inBuf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val chunk = 4096
        val tempBytes = ByteArray(chunk)
        try {
            while (inBuf.hasRemaining()) {
                val n = minOf(chunk, inBuf.remaining())
                inBuf.get(tempBytes, 0, n)
                val c = ByteBuffer.allocateDirect(n).order(ByteOrder.LITTLE_ENDIAN)
                c.put(tempBytes, 0, n)
                c.flip()
                sonic.queueInput(c)
                while (true) {
                    val o = sonic.getOutput()
                    if (o.remaining() == 0) break
                    val b = ByteArray(o.remaining()); o.get(b); out.write(b)
                }
            }
            sonic.queueEndOfStream()
            while (true) {
                val o = sonic.getOutput()
                if (o.remaining() == 0) break
                val b = ByteArray(o.remaining()); o.get(b); out.write(b)
            }
        } catch (_: Exception) { return pcm }
        finally { try { sonic.reset() } catch (_: Exception) {} }
        return out.toByteArray()
    }

    private fun channelsToChannelMask(ch: Int) = when (ch) {
        1 -> android.media.AudioFormat.CHANNEL_OUT_MONO
        else -> android.media.AudioFormat.CHANNEL_OUT_STEREO
    }

    private fun decodeClipAudioToPcm(uriStr: String, trimStartUs: Long, trimEndUs: Long): ByteArray? {
        val ext = MediaExtractor()
        try { ext.setDataSource(context, Uri.parse(uriStr), null) }
        catch (e: Exception) { ext.release(); return null }

        var audioTrack = -1; var fmt: MediaFormat? = null
        for (i in 0 until ext.trackCount) {
            val f = ext.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrack = i; fmt = f; ext.selectTrack(i); break
            }
        }
        if (audioTrack == -1 || fmt == null) { ext.release(); return null }

        val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(fmt, null, null, 0); dec.start()
        if (trimStartUs > 0) ext.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val pcm = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var eos = false
        try {
            while (!eos) {
                val inIdx = dec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = dec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        val size = ext.readSampleData(inBuf, 0)
                        val time = ext.sampleTime
                        if (size < 0 || (trimEndUs > 0 && time > trimEndUs)) {
                            dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            dec.queueInputBuffer(inIdx, 0, size, time, ext.sampleFlags)
                            ext.advance()
                        }
                    }
                }
                val outIdx = dec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos = true
                    if (info.size > 0) {
                        val outBuf = dec.getOutputBuffer(outIdx)
                        if (outBuf != null) {
                            val c = ByteArray(info.size)
                            outBuf.position(info.offset); outBuf.get(c); pcm.write(c)
                        }
                    }
                    dec.releaseOutputBuffer(outIdx, false)
                }
            }
        } catch (_: Exception) {}
        finally {
            try { dec.stop() } catch (_: Exception) {}
            try { dec.release() } catch (_: Exception) {}
            try { ext.release() } catch (_: Exception) {}
        }
        return pcm.toByteArray()
    }

    private fun feedPcmToEncoder(enc: MediaCodec, pcm: ByteArray, muxer: MediaMuxerWrapper) {
        var offset = 0
        val chunk = 4096
        var ptsUs = 0L
        val bps = exportConfig.audioSampleRate * exportConfig.audioChannels * 2
        var eos = false
        while (!eos) {
            val inIdx = enc.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val inBuf = enc.getInputBuffer(inIdx)
                if (inBuf != null) {
                    val rem = pcm.size - offset
                    val n = minOf(rem, chunk)
                    if (n > 0) {
                        inBuf.clear(); inBuf.put(pcm, offset, n)
                        ptsUs = ((offset.toDouble() / bps) * 1_000_000.0).toLong()
                        enc.queueInputBuffer(inIdx, 0, n, ptsUs, 0)
                        offset += n
                    } else {
                        enc.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }
            }
            val outIdx = enc.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    audioTrackIndex = muxer.addAudioTrack(enc.outputFormat)
                outIdx >= 0 -> {
                    val outBuf = enc.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeAudioSample(outBuf, bufferInfo)
                    }
                    enc.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos = true
                }
            }
        }
    }

    companion object { private const val TAG = "AudioProcessor" }
}
