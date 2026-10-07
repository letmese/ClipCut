package com.clipcut.core.feature

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.AudioTrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * VoiceoverRecorder — wraps MediaRecorder to capture an AAC .m4a file with
 * a live waveform feed. After stop() returns the AudioTrackItem that
 * EditorViewModel can append to project.audioTracks.
 *
 * AudioFormat: AAC, 44.1kHz, mono. Output is written to cacheDir so it can
 * be added via content:// and trimmed if needed.
 */
class VoiceoverRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun start(): Boolean {
        return try {
            val out = File(context.cacheDir, "voiceover_${System.currentTimeMillis()}.m4a")
            outputFile = out
            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128_000)
                setAudioChannels(1)
                setOutputFile(out.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            startAmplitudePolling()
            AppLog.i(TAG, "VoiceoverRecorder.start at ${out.absolutePath}")
            true
        } catch (t: Throwable) {
            AppLog.e(TAG, "VoiceoverRecorder.start failed", t)
            false
        }
    }

    fun stop(): AudioTrackItem? {
        val rec = recorder ?: return null
        val out = outputFile ?: return null
        try {
            rec.stop()
            rec.release()
        } catch (t: Throwable) {
            AppLog.w(TAG, "VoiceoverRecorder.stop: ${t.message}")
            rec.reset(); rec.release()
        }
        recorder = null
        _isRecording.value = false
        val durationMs = System.currentTimeMillis() - startTimeMs
        val item = AudioTrackItem(
            id = UUID.randomUUID().toString(),
            uri = "file://${out.absolutePath}",
            name = "Voiceover ${startTimeMs}",
            sourceDurationUs = durationMs * 1000L,
            timelineStartUs = 0L,
            trimStartUs = 0L,
            trimEndUs = durationMs * 1000L,
            volume = 1.0f,
            isVoiceover = true
        )
        return item
    }

    private fun startAmplitudePolling() {
        Thread {
            while (_isRecording.value) {
                try {
                    val max = recorder?.maxAmplitude ?: 0
                    _amplitude.value = (max / 32767f).coerceIn(0f, 1f)
                } catch (_: Throwable) {}
                Thread.sleep(50)
            }
        }.start()
    }

    companion object { private const val TAG = "VoiceoverRecorder" }
}
