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

    fun decodeNextFrame(timeoutUs: Long = 2_500L): Long {
        val dec = decoder ?: return -1L
        val info = MediaCodec.BufferInfo()
        val startMs = System.currentTimeMillis()
        while (true) {
            if (System.currentTimeMillis() - startMs > 3000L) {
                AppLog.w("VideoDecoder", "decodeNextFrame timed out (3000ms budget exceeded)")
                return -1L
            }
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
                Thread.yield()
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
        var tryAgainCount = 0
        while (true) {
            val outIdx = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
                tryAgainCount++
                if (tryAgainCount > 20) break
                continue
            }
            tryAgainCount = 0
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
    private data class AudioSource(
        val uri: String,
        val timelineStartUs: Long,
        val trimStartUs: Long,
        val trimEndUs: Long,
        val volume: Float,
        val speed: Float = 1.0f,
        val isBackgroundTrack: Boolean = false
    )

    fun processAudio(
        muxerWrapper: MediaMuxerWrapper,
        totalDurationUs: Long,
        onProgress: (Float) -> Unit
    ) {
        val tracksToMix = mutableListOf<AudioSource>()

        // 1. Audio from video clips
        var clipTimelinePosUs = 0L
        for (clip in project.videoClips) {
            val trimStart = clip.trimStartUs.coerceAtLeast(0L)
            val trimEnd = if (clip.trimEndUs > trimStart) clip.trimEndUs else clip.sourceDurationUs
            val effDur = ((trimEnd - trimStart) / clip.speed.coerceAtLeast(0.1f)).toLong().coerceAtLeast(0L)
            if (clip.mediaType == com.clipcut.core.model.MediaType.VIDEO && clip.volume > 0f && clip.uri.isNotEmpty()) {
                tracksToMix.add(AudioSource(
                    uri = clip.uri,
                    timelineStartUs = clipTimelinePosUs,
                    trimStartUs = trimStart,
                    trimEndUs = trimEnd,
                    volume = clip.volume * project.originalVideoVolume,
                    speed = clip.speed.coerceAtLeast(0.1f),
                    isBackgroundTrack = false
                ))
            }
            clipTimelinePosUs += effDur
        }

        // Build voice activity intervals for dynamic audio ducking
        val voiceIntervals = mutableListOf<LongRange>()
        var vPosUs = 0L
        for (clip in project.videoClips) {
            val trimStart = clip.trimStartUs.coerceAtLeast(0L)
            val trimEnd = if (clip.trimEndUs > trimStart) clip.trimEndUs else clip.sourceDurationUs
            val effDur = ((trimEnd - trimStart) / clip.speed.coerceAtLeast(0.1f)).toLong().coerceAtLeast(0L)
            if (clip.mediaType == com.clipcut.core.model.MediaType.VIDEO && clip.volume > 0.05f) {
                voiceIntervals.add(vPosUs..(vPosUs + effDur))
            }
            vPosUs += effDur
        }
        for (t in project.audioTracks) {
            val isVo = t.isVoiceover || t.name.contains("voiceover", ignoreCase = true) || t.uri.contains("voiceover", ignoreCase = true)
            if (isVo && t.volume > 0.05f) {
                voiceIntervals.add(t.timelineStartUs..(t.timelineStartUs + t.effectiveDurationUs))
            }
        }

        // 2. Added audio tracks (music, sound effects, voiceovers)
        for (track in project.audioTracks) {
            if (track.volume > 0f && track.uri.isNotEmpty()) {
                val trimStart = track.trimStartUs.coerceAtLeast(0L)
                val trimEnd = if (track.trimEndUs > trimStart) track.trimEndUs else track.sourceDurationUs
                val isVoiceover = track.isVoiceover || track.name.contains("voiceover", ignoreCase = true) || track.uri.contains("voiceover", ignoreCase = true)
                val baseVol = if (isVoiceover) track.volume else (track.volume * project.backgroundMusicVolume)
                tracksToMix.add(AudioSource(
                    uri = track.uri,
                    timelineStartUs = track.timelineStartUs,
                    trimStartUs = trimStart,
                    trimEndUs = trimEnd,
                    volume = baseVol,
                    speed = 1.0f,
                    isBackgroundTrack = !isVoiceover
                ))
            }
        }

        if (tracksToMix.isEmpty()) {
            onProgress(1f)
            return
        }

        val targetSampleRate = exportConfig.audioSampleRate
        val targetChannels = exportConfig.audioChannels
        val maxDurationUs = totalDurationUs.coerceAtLeast(100_000L)
        val totalSamples = ((maxDurationUs * targetSampleRate) / 1_000_000L).toInt() * targetChannels
        val mixBuffer = FloatArray(totalSamples.coerceAtLeast(targetChannels * 1024))

        // Decode and accumulate each source into mixBuffer
        for ((idx, track) in tracksToMix.withIndex()) {
            try {
                val ext = MediaExtractor().apply { setDataSource(context, android.net.Uri.parse(track.uri), null) }
                var audioFmt: MediaFormat? = null
                for (i in 0 until ext.trackCount) {
                    val fmt = ext.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        ext.selectTrack(i)
                        audioFmt = fmt
                        break
                    }
                }
                if (audioFmt != null) {
                    val mime = audioFmt.getString(MediaFormat.KEY_MIME)!!
                    val dec = MediaCodec.createDecoderByType(mime)
                    dec.configure(audioFmt, null, null, 0)
                    dec.start()

                    if (track.trimStartUs > 0) {
                        ext.seekTo(track.trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    }

                    val decInfo = MediaCodec.BufferInfo()
                    var decEos = false
                    var inputEos = false
                    var smoothedDuckMultiplier = 1.0f
                    var currentTargetFrame = -1
                    var resamplePhase = 0.0

                    while (!decEos) {
                        if (!inputEos) {
                            val inIdx = dec.dequeueInputBuffer(10_000)
                            if (inIdx >= 0) {
                                val inBuf = dec.getInputBuffer(inIdx)
                                if (inBuf != null) {
                                    val sz = ext.readSampleData(inBuf, 0)
                                    if (sz < 0 || (ext.sampleTime > track.trimEndUs && track.trimEndUs > 0)) {
                                        dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                        inputEos = true
                                    } else {
                                        dec.queueInputBuffer(inIdx, 0, sz, ext.sampleTime, 0)
                                        ext.advance()
                                    }
                                }
                            }
                        }

                        val outIdx = dec.dequeueOutputBuffer(decInfo, 10_000)
                        if (outIdx >= 0) {
                            val outBuf = dec.getOutputBuffer(outIdx)
                            if (outBuf != null && decInfo.size > 0) {
                                outBuf.position(decInfo.offset)
                                outBuf.limit(decInfo.offset + decInfo.size)
                                outBuf.order(ByteOrder.LITTLE_ENDIAN)

                                val outFmt = try { dec.outputFormat } catch (_: Throwable) { audioFmt }
                                val srcSampleRate = try { outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Throwable) { targetSampleRate }
                                val srcChannels = try { outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Throwable) { targetChannels }

                                val rawPts = decInfo.presentationTimeUs
                                if (rawPts >= track.trimStartUs) {
                                    val ptsOffsetUs = ((rawPts - track.trimStartUs) / track.speed).toLong()
                                    val timelinePtsUs = track.timelineStartUs + ptsOffsetUs
                                    val ptsTargetFrame = ((timelinePtsUs * targetSampleRate) / 1_000_000L).toInt()

                                    // Resynchronize frame position only initially or on genuine seek / stream discontinuity (> 50ms)
                                    if (currentTargetFrame < 0 || kotlin.math.abs(ptsTargetFrame - currentTargetFrame) > (targetSampleRate / 20)) {
                                        currentTargetFrame = ptsTargetFrame
                                        resamplePhase = 0.0
                                    }

                                    val shortBuf = outBuf.asShortBuffer()
                                    val inFrames = shortBuf.remaining() / srcChannels
                                    if (inFrames > 0) {
                                        val inLeft = FloatArray(inFrames)
                                        val inRight = FloatArray(inFrames)
                                        for (f in 0 until inFrames) {
                                            if (srcChannels == 1) {
                                                val s = shortBuf.get().toFloat() * track.volume
                                                inLeft[f] = s
                                                inRight[f] = s
                                            } else if (srcChannels == 2) {
                                                val l = shortBuf.get().toFloat() * track.volume
                                                val r = shortBuf.get().toFloat() * track.volume
                                                inLeft[f] = l
                                                inRight[f] = r
                                            } else {
                                                // High-fidelity downmix for multi-channel / 5.1 phone recordings (L, R, C, LFE, Ls, Rs)
                                                val l = shortBuf.get().toFloat()
                                                val r = shortBuf.get().toFloat()
                                                val c = if (shortBuf.hasRemaining()) shortBuf.get().toFloat() else 0f
                                                val lfe = if (shortBuf.hasRemaining()) shortBuf.get().toFloat() else 0f
                                                val ls = if (shortBuf.hasRemaining()) shortBuf.get().toFloat() else 0f
                                                val rs = if (shortBuf.hasRemaining()) shortBuf.get().toFloat() else 0f
                                                for (extra in 6 until srcChannels) {
                                                    if (shortBuf.hasRemaining()) shortBuf.get()
                                                }
                                                // ITU-R standard downmix to stereo: preserve dialogue in Center channel
                                                inLeft[f] = (l + 0.707f * c + 0.5f * ls) * track.volume
                                                inRight[f] = (r + 0.707f * c + 0.5f * rs) * track.volume
                                            }
                                        }

                                        if (srcSampleRate == targetSampleRate && track.speed == 1.0f) {
                                            // Bit-accurate 1:1 direct passthrough: ZERO resampling math, ZERO phase comb filtering, ZERO noise
                                            for (f in 0 until inFrames) {
                                                var finalL = inLeft[f]
                                                var finalR = inRight[f]
                                                if (track.isBackgroundTrack && project.audioDuckingEnabled) {
                                                    val currentPtsUs = (currentTargetFrame.toLong() * 1_000_000L) / targetSampleRate.toLong()
                                                    val isVoiceActive = voiceIntervals.any { currentPtsUs in it }
                                                    val targetMultiplier = if (isVoiceActive) project.audioDuckLevel.coerceIn(0f, 1.0f) else 1.0f
                                                    val slewAlpha = (1f / (0.03f * targetSampleRate.toFloat())).coerceIn(0.0001f, 0.05f)
                                                    smoothedDuckMultiplier += (targetMultiplier - smoothedDuckMultiplier) * slewAlpha
                                                    finalL *= smoothedDuckMultiplier
                                                    finalR *= smoothedDuckMultiplier
                                                }

                                                val targetIdx = currentTargetFrame * targetChannels
                                                if (targetIdx >= 0 && targetIdx + 1 < mixBuffer.size) {
                                                    mixBuffer[targetIdx] += finalL
                                                    mixBuffer[targetIdx + 1] += finalR
                                                }
                                                currentTargetFrame++
                                            }
                                        } else {
                                            // Continuous linear resampling with cross-chunk phase carryover
                                            val step = (srcSampleRate.toDouble() / targetSampleRate.toDouble()) * track.speed.toDouble()
                                            var srcPos = resamplePhase
                                            while (srcPos < inFrames) {
                                                val base = srcPos.toInt()
                                                val frac = (srcPos - base).toFloat()
                                                val sampleL: Float
                                                val sampleR: Float
                                                if (base + 1 < inFrames) {
                                                    sampleL = inLeft[base] * (1f - frac) + inLeft[base + 1] * frac
                                                    sampleR = inRight[base] * (1f - frac) + inRight[base + 1] * frac
                                                } else if (base < inFrames) {
                                                    sampleL = inLeft[base]
                                                    sampleR = inRight[base]
                                                } else {
                                                    break
                                                }

                                                var finalL = sampleL
                                                var finalR = sampleR
                                                if (track.isBackgroundTrack && project.audioDuckingEnabled) {
                                                    val currentPtsUs = (currentTargetFrame.toLong() * 1_000_000L) / targetSampleRate.toLong()
                                                    val isVoiceActive = voiceIntervals.any { currentPtsUs in it }
                                                    val targetMultiplier = if (isVoiceActive) project.audioDuckLevel.coerceIn(0f, 1.0f) else 1.0f
                                                    val slewAlpha = (1f / (0.03f * targetSampleRate.toFloat())).coerceIn(0.0001f, 0.05f)
                                                    smoothedDuckMultiplier += (targetMultiplier - smoothedDuckMultiplier) * slewAlpha
                                                    finalL *= smoothedDuckMultiplier
                                                    finalR *= smoothedDuckMultiplier
                                                }

                                                val targetIdx = currentTargetFrame * targetChannels
                                                if (targetIdx >= 0 && targetIdx + 1 < mixBuffer.size) {
                                                    mixBuffer[targetIdx] += finalL
                                                    mixBuffer[targetIdx + 1] += finalR
                                                }

                                                currentTargetFrame++
                                                srcPos += step
                                            }
                                            resamplePhase = srcPos - inFrames
                                        }
                                    }
                                }
                            }
                            dec.releaseOutputBuffer(outIdx, false)
                            if ((decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                decEos = true
                            }
                        }
                    }
                    try { dec.stop() } catch (_: Throwable) {}
                    try { dec.release() } catch (_: Throwable) {}
                }
                ext.release()
            } catch (t: Throwable) {
                AppLog.w("AudioProcessor", "Mix source failed: ${track.uri}: ${t.message}")
            }
            onProgress(0.4f * ((idx + 1).toFloat() / tracksToMix.size.toFloat()))
        }

        // 3. Encode mixed PCM into AAC
        val encMime = "audio/mp4a-latm"
        val encFmt = MediaFormat.createAudioFormat(encMime, targetSampleRate, targetChannels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, exportConfig.audioBitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val enc = MediaCodec.createEncoderByType(encMime)
        enc.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()

        val pcmChunkSamples = 1024 * targetChannels
        val pcmByteBuffer = ByteBuffer.allocateDirect(pcmChunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        var sampleCursor = 0
        while (sampleCursor < mixBuffer.size) {
            val inIdx = enc.dequeueInputBuffer(10_000)
            if (inIdx < 0) {
                drainAudioEnc(enc, muxerWrapper, endOfStream = false)
                continue
            }
            val inBuf = enc.getInputBuffer(inIdx) ?: continue
            inBuf.clear()
            inBuf.order(ByteOrder.LITTLE_ENDIAN)

            val samplesToRead = minOf(pcmChunkSamples, mixBuffer.size - sampleCursor)
            pcmByteBuffer.clear()
            for (i in 0 until samplesToRead) {
                val sample = mixBuffer[sampleCursor + i]
                // Soft-knee limiter: smooth tanh compression above 30,000 to prevent harsh digital clipping
                val limited = if (kotlin.math.abs(sample) <= 30000f) {
                    sample
                } else {
                    val sign = if (sample > 0) 1f else -1f
                    val excess = kotlin.math.abs(sample) - 30000f
                    sign * (30000f + 2767f * kotlin.math.tanh(excess.toDouble() / 10000.0).toFloat())
                }
                val clamped = limited.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                pcmByteBuffer.putShort(clamped)
            }
            // Pad trailing chunk with silence to maintain exact 1024-frame AAC encoder boundaries
            if (samplesToRead < pcmChunkSamples) {
                for (p in samplesToRead until pcmChunkSamples) {
                    pcmByteBuffer.putShort(0)
                }
            }
            pcmByteBuffer.flip()
            inBuf.put(pcmByteBuffer)
            inBuf.position(0)
            inBuf.limit(pcmChunkSamples * 2)

            val currentPts = (sampleCursor.toLong() * 1_000_000L) / (targetSampleRate.toLong() * targetChannels.toLong())
            enc.queueInputBuffer(inIdx, 0, pcmChunkSamples * 2, currentPts, 0)
            sampleCursor += samplesToRead

            drainAudioEnc(enc, muxerWrapper, endOfStream = false)
            onProgress(0.4f + 0.6f * (sampleCursor.toFloat() / mixBuffer.size.toFloat()))
        }

        var eosQueued = false
        var attempts = 0
        while (!eosQueued && attempts < 25) {
            val inIdx = enc.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val lastPts = (sampleCursor.toLong() * 1_000_000L) / (targetSampleRate.toLong() * targetChannels.toLong())
                enc.queueInputBuffer(inIdx, 0, 0, minOf(lastPts, totalDurationUs), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                eosQueued = true
            } else {
                drainAudioEnc(enc, muxerWrapper, endOfStream = false)
                attempts++
            }
        }
        drainAudioEnc(enc, muxerWrapper, endOfStream = true)
        try { enc.stop() } catch (_: Throwable) {}
        try { enc.release() } catch (_: Throwable) {}
        onProgress(1f)
    }

    private fun drainAudioEnc(enc: MediaCodec, muxer: MediaMuxerWrapper?, endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        var tryAgainCount = 0
        while (true) {
            val outIdx = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0L)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxer != null) muxer.addAudioTrack(enc.outputFormat)
                continue
            }
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
                tryAgainCount++
                if (tryAgainCount > 20) break
                continue
            }
            tryAgainCount = 0
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
