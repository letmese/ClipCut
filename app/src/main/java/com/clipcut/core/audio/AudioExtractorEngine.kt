package com.clipcut.core.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.clipcut.core.log.AppLog
import de.sciss.jump3r.lowlevel.LameEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat

data class AudioExtractResult(
    val file: File,
    val publicUri: Uri?,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val fileName: String
)

object AudioExtractorEngine {
    private const val TAG = "AudioExtractorEngine"

    /**
     * Extracts the audio track from a video [sourceUri], decodes it via MediaCodec,
     * encodes it to standard MP3 (192 kbps) using Jump3r LAME, and saves it both
     * to app storage and to the phone's public Music folder (MediaStore).
     */
    suspend fun extractAudioToMp3(
        context: Context,
        sourceUri: Uri,
        outputBaseName: String = "Extracted_Audio_${System.currentTimeMillis()}",
        bitRateKbps: Int = 192,
        onProgress: (Float) -> Unit = {}
    ): Result<AudioExtractResult> = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var lameEncoder: LameEncoder? = null
        var tempMp3File: File? = null

        try {
            extractor = MediaExtractor().apply {
                setDataSource(context, sourceUri, null)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext Result.failure(IllegalStateException("No audio track found in the selected video."))
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val totalDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 1_000_000L

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }

            val cacheDir = File(context.cacheDir, "extracted_audio").apply { mkdirs() }
            val cleanName = outputBaseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            tempMp3File = File(cacheDir, "${cleanName}.mp3")
            val outputStream = FileOutputStream(tempMp3File)

            // Setup LAME encoder
            val pcmFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate.toFloat(),
                16,
                channelCount,
                channelCount * 2,
                sampleRate.toFloat(),
                false // little-endian
            )

            val channelMode = if (channelCount == 1) {
                LameEncoder.CHANNEL_MODE_MONO
            } else {
                LameEncoder.CHANNEL_MODE_JOINT_STEREO
            }

            lameEncoder = LameEncoder(
                pcmFormat,
                bitRateKbps,
                channelMode,
                LameEncoder.QUALITY_HIGH,
                false
            )

            val mp3BufferSize = lameEncoder.getMP3BufferSize().coerceAtLeast(8192)
            val mp3Buffer = ByteArray(mp3BufferSize)

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var lastProgressTime = 0L

            while (!outputEos) {
                if (!inputEos) {
                    val inIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputEos = true
                    }

                    val outBuffer = decoder.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val pcmChunk = ByteArray(bufferInfo.size)
                        outBuffer.get(pcmChunk)

                        // Encode chunk to MP3
                        val bytesEncoded = lameEncoder.encodeBuffer(pcmChunk, 0, pcmChunk.size, mp3Buffer)
                        if (bytesEncoded > 0) {
                            outputStream.write(mp3Buffer, 0, bytesEncoded)
                        }

                        // Progress notification
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime > 100L) {
                            lastProgressTime = now
                            val progress = if (totalDurationUs > 0) {
                                (bufferInfo.presentationTimeUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 0.95f)
                            } else 0.5f
                            onProgress(progress)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = decoder.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            // Flush remaining encoder bytes
            val flushBytes = lameEncoder.encodeFinish(mp3Buffer)
            if (flushBytes > 0) {
                outputStream.write(mp3Buffer, 0, flushBytes)
            }
            outputStream.flush()
            outputStream.close()
            lameEncoder.close()

            onProgress(0.98f)

            // Export to public MediaStore (Music directory on phone)
            val publicUri = saveToPublicMusic(context, tempMp3File, "${cleanName}.mp3", totalDurationUs / 1000L)

            onProgress(1.0f)

            AppLog.i(TAG, "Audio successfully extracted to MP3: ${tempMp3File.absolutePath}, publicUri: $publicUri")

            Result.success(
                AudioExtractResult(
                    file = tempMp3File,
                    publicUri = publicUri,
                    durationMs = totalDurationUs / 1000L,
                    sampleRate = sampleRate,
                    channels = channelCount,
                    fileName = "${cleanName}.mp3"
                )
            )
        } catch (t: Throwable) {
            AppLog.e(TAG, "Failed to extract audio to MP3", t)
            Result.failure(t)
        } finally {
            try { decoder?.stop() } catch (_: Throwable) {}
            try { decoder?.release() } catch (_: Throwable) {}
            try { extractor?.release() } catch (_: Throwable) {}
        }
    }

    private fun saveToPublicMusic(
        context: Context,
        mp3File: File,
        displayName: String,
        durationMs: Long
    ): Uri? {
        return try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, displayName.removeSuffix(".mp3"))
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.DURATION, durationMs)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.ARTIST, "ClipCut Audio Extractor")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/ClipCut")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    mp3File.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            }
            uri
        } catch (t: Throwable) {
            AppLog.w(TAG, "Could not save to public MediaStore: ${t.message}")
            null
        }
    }
}
