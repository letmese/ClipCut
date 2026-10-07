package com.clipcut.core.feature

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.clipcut.core.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CaptionSegment — one timed captioned phrase. The CaptionSheet renders
 * these in an editable list and writes back to OverlayItem entries on Apply.
 */
data class CaptionSegment(
    val text: String,
    val startTimeUs: Long,
    val endTimeUs: Long,
    val confidence: Float = 1f,
    val isFinal: Boolean = false
)

/**
 * CaptionGenerator — feeds PCM from AudioRecord into android.speech.
 * SpeechRecognizer, parses partial + final results into timed segments.
 *
 *  start(context) → begins capturing mic input
 *  stop()         → returns the list of final CaptionSegment entries
 *
 * On Android, SpeechRecognizer requires audio input via the microphone
 * (it cannot directly consume an arbitrary PCM buffer); this implementation
 * uses the platform SpeechRecognizer which internally reads the mic.
 */
class CaptionGenerator {
    private var sr: SpeechRecognizer? = null
    private var running = false

    private val _segments = MutableStateFlow<List<CaptionSegment>>(emptyList())
    val segments: StateFlow<List<CaptionSegment>> = _segments.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private var sessionStartMs: Long = 0L
    private var segmentStartMs: Long = 0L

    fun start(context: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            AppLog.w(TAG, "SpeechRecognizer not available on this device")
            return
        }

        sr = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {
                    segmentStartMs = System.currentTimeMillis()
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    AppLog.w(TAG, "SpeechRecognizer error=$error")
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val txt = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    _partial.value = txt
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    val conf = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: 1f
                    val endMs = System.currentTimeMillis()
                    val text = list.joinToString(" ")
                    if (text.isNotBlank()) {
                        val sUs = (segmentStartMs - sessionStartMs).coerceAtLeast(0L) * 1000L
                        val eUs = (endMs - sessionStartMs).coerceAtLeast((segmentStartMs - sessionStartMs) + 500L) * 1000L
                        val newSeg = CaptionSegment(
                            text = text,
                            startTimeUs = sUs,
                            endTimeUs = eUs,
                            confidence = conf,
                            isFinal = true
                        )
                        _segments.value = _segments.value + newSeg
                        AppLog.i(TAG, "final: ${text.take(60)} ($sUs-$eUs us)")
                    }
                    segmentStartMs = endMs
                    // Continue listening until stop()
                    if (running) {
                        startRecognition()
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        running = true
        sessionStartMs = System.currentTimeMillis()
        startRecognition()
    }

    private fun startRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "com.clipcut")
        }
        sr?.startListening(intent)
    }

    fun stop(): List<CaptionSegment> {
        running = false
        try { sr?.stopListening() } catch (_: Throwable) {}
        try { sr?.destroy() } catch (_: Throwable) {}
        sr = null
        return _segments.value
    }

    /** Export segments to SubRip (.srt) format. */
    fun toSrt(segments: List<CaptionSegment>): String {
        val sb = StringBuilder()
        segments.forEachIndexed { i, s ->
            sb.append("${i + 1}\n")
            sb.append(formatSrtTime(s.startTimeUs)).append(" --> ").append(formatSrtTime(s.endTimeUs)).append("\n")
            sb.append(s.text).append("\n\n")
        }
        return sb.toString()
    }

    private fun formatSrtTime(us: Long): String {
        val ms = us / 1000
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val msRem = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", h, m, s, msRem)
    }

    companion object { private const val TAG = "CaptionGenerator" }
}
