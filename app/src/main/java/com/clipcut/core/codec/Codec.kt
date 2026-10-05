package com.clipcut.core.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.clipcut.core.gl.InputSurface
import com.clipcut.core.gl.OutputSurface
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.Project
import com.clipcut.core.model.SpeedRamp
import com.clipcut.core.model.TimelineItem
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * VideoDecoder — wraps MediaCodec/MediaExtractor in decode-to-surface mode.
 * Used by both the preview path and the export path. Exposes:
 *   init(), seekTo(targetUs), decodeNextFrame(timeoutUs): Long (pts), release()
 */
class VideoDecoder(context: Context, clip: TimelineItem, outputSurface: OutputSurface) {
    private val extractor = MediaExtractor()
    private var decoder: MediaCodec? = null
    private var outputSurface: OutputSurface? = outputSurface
    private var inputSurface: Surface? = null
    val videoWidth: Int
    val videoHeight: Int
    val videoRotation: Int
    val durationUs: Long
    private val _cachedFormat: MediaFormat

    init {
        extractor.setDataSource(context, android.net.Uri.parse(clip.uri), null)
        var trackFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) { extractor.selectTrack(i); trackFormat = fmt; break }
        }
        if (trackFormat == null) { trackFormat = MediaFormat.createVideoFormat("video/avc", 1920, 1080) }
        videoWidth = trackFormat.getInteger(MediaFormat.KEY_WIDTH)
        videoHeight = trackFormat.getInteger(MediaFormat.KEY_HEIGHT)
        videoRotation = if (Build.VERSION.SDK_INT >= 23) trackFormat.getInteger("rotation-degrees", 0) else 0
        durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) trackFormat.getLong(MediaFormat.KEY_DURATION) else 0L
        _cachedFormat = trackFormat
    }

    fun init() {
        val mime = _cachedFormat.getString(MediaFormat.KEY_MIME) ?: "video/avc"
        // Use the format MediaExtractor provided — preserves CSD (SPS/PPS) & key parameters
        val format = _cachedFormat
        decoder = MediaCodec.createDecoderByType(mime)
        outputSurface?.surface?.let { decoder?.configure(format, it, null, 0) }
        decoder?.start()
        AppLog.d("VideoDecoder", "init ${videoWidth}x${videoHeight} rot=$videoRotation dur=$durationUs")
    }

    fun seekTo(targetUs: Long) {
        extractor.seekTo(targetUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    }

    fun decodeNextFrame(timeoutUs: Long = 10_000L): Long {
        val dec = decoder ?: return -1L
        var info = MediaCodec.BufferInfo()
        while (true) {
            val inIdx = dec.dequeueInputBuffer(timeoutUs)
            if (inIdx >= 0) {
                val buf = dec.getInputBuffer(inIdx) ?: continue
                val sampleSize = extractor.readSampleData(buf, 0)
                if (sampleSize < 0) {
                    dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    dec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
            val outIdx = dec.dequeueOutputBuffer(info, timeoutUs)
            if (outIdx >= 0) {
                val render = info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0
                dec.releaseOutputBuffer(outIdx, render)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return -1L
                if (render) return info.presentationTimeUs
            }
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inIdx < 0) {
                Thread.sleep(2)
                continue
            }
        }
    }

    fun release() {
        try { decoder?.stop() } catch (_: Throwable) {}
        decoder?.release()
        extractor.release()
        AppLog.d("VideoDecoder", "released")
    }
}

/**
 * VideoEncoder — wraps a MediaCodec encoder in surface-input mode.
 *  inputSurface is non-null after init() and is what frames are drawn into.
 */
class VideoEncoder(private val config: ExportConfig) {
    private var encoder: MediaCodec? = null
    val inputSurface: InputSurface? get() = _inputSurface
    private var _inputSurface: InputSurface? = null

    fun init() {
        val mime = config.codec.mimeType
        val format = MediaFormat.createVideoFormat(mime, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val enc = MediaCodec.createEncoderByType(mime)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = enc.createInputSurface()
        enc.start()
        _inputSurface = InputSurface(surface)
        encoder = enc
        AppLog.d("VideoEncoder", "init ${config.width}x${config.height} ${mime} bitrate=${config.bitRate}")
    }

    fun drainEncoder(muxerWrapper: MediaMuxerWrapper, endOfStream: Boolean) {
        val enc = encoder ?: return
        if (endOfStream) enc.signalEndOfInputStream()
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break else continue
            }
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                muxerWrapper.addVideoTrack(enc.outputFormat)
                continue
            }
            if (outIdx >= 0) {
                val buf = enc.getOutputBuffer(outIdx) ?: continue
                if (info.size > 0) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    muxerWrapper.writeVideoSample(buf, info)
                }
                enc.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }

    fun release() {
        try { encoder?.stop() } catch (_: Throwable) {}
        encoder?.release()
        _inputSurface?.release()
    }
}

/**
 * AudioProcessor — PCM mixer with correct little-endian decoding and chunked buffer feed:
 *  1. For each AudioTrackItem in Project, decode via MediaExtractor+MediaCodec to PCM (16-bit).
 *  2. Mix with originalVideoVolume * clip.volume, applying volume scaling in Little-Endian.
 *  3. Re-encode via MediaCodec AAC into the muxer.
 */
class AudioProcessor(
    private val context: Context,
    private val project: Project,
    private val exportConfig: ExportConfig
) {
    fun processAudio(
        muxerWrapper: MediaMuxerWrapper,
        totalDurationUs: Long,
        onProgress: (Float) -> Unit
    ) {
        val tracksToMix = if (project.audioTracks.isNotEmpty()) {
            project.audioTracks
        } else {
            project.videoClips.filter { it.mediaType == com.clipcut.core.model.MediaType.VIDEO && it.volume > 0f }.map { clip ->
                com.clipcut.core.model.AudioTrackItem(
                    uri = clip.uri,
                    name = "Clip Audio",
                    sourceDurationUs = clip.sourceDurationUs,
                    timelineStartUs = 0L,
                    trimStartUs = clip.trimStartUs,
                    trimEndUs = if (clip.trimEndUs > clip.trimStartUs) clip.trimEndUs else clip.sourceDurationUs,
                    volume = clip.volume * project.originalVideoVolume
                )
            }
        }
        if (tracksToMix.isEmpty()) {
            onProgress(1f); return
        }
        val encMime = "audio/mp4a-latm"
        val encFmt = MediaFormat.createAudioFormat(encMime, exportConfig.audioSampleRate, exportConfig.audioChannels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, exportConfig.audioBitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val enc = MediaCodec.createEncoderByType(encMime)
        enc.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()

        var processedUs = 0L
        for (track in tracksToMix) {
            if (track.uri.isEmpty()) continue
            try {
                val ext = MediaExtractor().apply { setDataSource(context, android.net.Uri.parse(track.uri), null) }
                var audioFmt: MediaFormat? = null
                var audioTrackIdx = -1
                for (i in 0 until ext.trackCount) {
                    val fmt = ext.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) { ext.selectTrack(i); audioFmt = fmt; audioTrackIdx = i; break }
                }
                if (audioFmt != null) {
                    val dec = MediaCodec.createDecoderByType(audioFmt.getString(MediaFormat.KEY_MIME)!!)
                    dec.configure(audioFmt, null, null, 0)
                    dec.start()
                    drainAudioLoop(ext, dec, enc, muxerWrapper, track, totalDurationUs)
                    dec.stop(); dec.release()
                }
                ext.release()
            } catch (t: Throwable) {
                AppLog.w("AudioProcessor", "skip audio track ${track.name}: ${t.message}")
            }
            processedUs += track.effectiveDurationUs
            onProgress(processedUs / totalDurationUs.coerceAtLeast(1L).toFloat())
        }

        enc.signalEndOfInputStream()
        drainAudioEnc(enc, muxerWrapper, endOfStream = true)
        enc.stop(); enc.release()
        onProgress(1f)
    }

    private fun drainAudioLoop(
        ext: MediaExtractor, dec: MediaCodec, enc: MediaCodec,
        muxer: MediaMuxerWrapper, track: com.clipcut.core.model.AudioTrackItem,
        totalUs: Long
    ) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val inIdx = dec.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val buf = dec.getInputBuffer(inIdx) ?: continue
                val sampleSize = ext.readSampleData(buf, 0)
                if (sampleSize < 0) {
                    dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    dec.queueInputBuffer(inIdx, 0, sampleSize, ext.sampleTime, 0)
                    ext.advance()
                }
            }
            val outIdx = dec.dequeueOutputBuffer(info, 10_000)
            if (outIdx >= 0) {
                val buf = dec.getOutputBuffer(outIdx) ?: continue
                if (info.size > 0) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    applyVolume(buf, track.volume)
                    feedToEncoder(enc, muxer, buf, info.presentationTimeUs)
                }
                dec.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }

    private fun applyVolume(buf: ByteBuffer, vol: Float) {
        if (vol == 1.0f) return
        val savedPos = buf.position()
        val savedLimit = buf.limit()
        val savedOrder = buf.order()
        buf.order(ByteOrder.LITTLE_ENDIAN)
        val shorts = buf.asShortBuffer()
        val n = shorts.remaining()
        for (i in 0 until n) {
            val s = shorts.get(i).toInt()
            val scaled = (s * vol).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            shorts.put(i, scaled.toShort())
        }
        buf.order(savedOrder)
        buf.position(savedPos)
        buf.limit(savedLimit)
    }

    private var encFormatChanged = false
    private fun feedToEncoder(enc: MediaCodec, muxer: MediaMuxerWrapper, input: ByteBuffer, ptsUs: Long) {
        var consumed = 0
        val src = input.duplicate()
        while (src.hasRemaining()) {
            val inIdx = enc.dequeueInputBuffer(10_000)
            if (inIdx < 0) {
                drainAudioEnc(enc, muxer, endOfStream = false)
                continue
            }
            val out = enc.getInputBuffer(inIdx) ?: continue
            out.clear()
            val chunk = minOf(out.remaining(), src.remaining())
            val slice = src.duplicate()
            slice.limit(slice.position() + chunk)
            out.put(slice)
            val pts = ptsUs + (consumed * 1_000_000L / (exportConfig.audioSampleRate.toLong() * 2L * 2L))
            enc.queueInputBuffer(inIdx, 0, chunk, pts, 0)
            src.position(src.position() + chunk)
            consumed += chunk
            drainAudioEnc(enc, muxer, endOfStream = false)
        }
    }

    private fun drainAudioEnc(enc: MediaCodec, muxer: MediaMuxerWrapper?, endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0L)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                encFormatChanged = true
                if (muxer != null) muxer.addAudioTrack(enc.outputFormat)
                continue
            }
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break else continue
            }
            if (outIdx >= 0) {
                val buf = enc.getOutputBuffer(outIdx) ?: continue
                if (info.size > 0 && muxer != null) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    muxer.writeAudioSample(buf, info)
                }
                enc.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }
}

/**
 * ReverseDecoder — pre-decodes all frames of a clip and serves them in reverse
 * order, used when TimelineItem.isReversed == true.
 */
class ReverseDecoder(
    private val context: Context,
    private val uri: String,
    private val trimStartUs: Long,
    private val trimEndUs: Long,
    private val frameCount: Int
) {
    private val frames: MutableList<android.graphics.Bitmap> = mutableListOf()
    var size: Int = 0
        private set

    fun prepare() {
        val ext = MediaExtractor().apply { setDataSource(context, android.net.Uri.parse(uri), null) }
        var format: MediaFormat? = null
        for (i in 0 until ext.trackCount) {
            val fmt = ext.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) { ext.selectTrack(i); format = fmt; break }
        }
        if (format == null) { ext.release(); return }
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val dec = MediaCodec.createDecoderByType(mime)
        dec.configure(format, null, null, 0)
        dec.start()
        ext.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val info = MediaCodec.BufferInfo()
        var count = 0
        while (count < frameCount) {
            val inIdx = dec.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val buf = dec.getInputBuffer(inIdx) ?: continue
                val sz = ext.readSampleData(buf, 0)
                if (sz < 0) {
                    dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else if (ext.sampleTime in trimStartUs..trimEndUs) {
                    dec.queueInputBuffer(inIdx, 0, sz, ext.sampleTime, 0)
                    ext.advance()
                } else {
                    ext.advance()
                }
            }
            val outIdx = dec.dequeueOutputBuffer(info, 10_000)
            if (outIdx >= 0) {
                if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                    val bmp = android.graphics.Bitmap.createBitmap(
                        format.getInteger(MediaFormat.KEY_WIDTH),
                        format.getInteger(MediaFormat.KEY_HEIGHT),
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    dec.releaseOutputBuffer(outIdx, false)
                    frames.add(bmp)
                    count++
                } else {
                    dec.releaseOutputBuffer(outIdx, false)
                }
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
        dec.stop(); dec.release(); ext.release()
        size = frames.size
    }

    fun getFrame(index: Int): android.graphics.Bitmap? =
        frames.getOrNull(frames.size - 1 - index.coerceIn(0, frames.size - 1))

    fun release() {
        frames.forEach { it.recycle() }
        frames.clear()
    }
}

object SpeedRampProcessor {
    fun sourceToTimeline(sourceOffsetUs: Long, ramp: SpeedRamp, constantSpeed: Float): Long {
        if (ramp.keyframes.isEmpty()) return (sourceOffsetUs / constantSpeed.coerceAtLeast(0.01f)).toLong()
        val totalSpeedFactor = ramp.speedAt(sourceOffsetUs)
        return (sourceOffsetUs / totalSpeedFactor.coerceAtLeast(0.01f)).toLong()
    }
    fun timelineToSource(timelineOffsetUs: Long, ramp: SpeedRamp, constantSpeed: Float): Long {
        if (ramp.keyframes.isEmpty()) return (timelineOffsetUs * constantSpeed).toLong()
        return (timelineOffsetUs * ramp.speedAt(timelineOffsetUs)).toLong()
    }
}
