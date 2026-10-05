package com.clipcut.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PreviewPlayerManager — wraps a single ExoPlayer instance for preview.
 *
 * The timeline (Project) is a sequence of clips joined by transitions; for
 * preview we currently concatenate all VIDEO clips into one MediaItem via
 * concatenation if Media3 supports it. For simplicity in this build, we
 * render the first clip; full multi-clip preview is handled by the
 * GlPreviewRenderer which iterates clips at the GL level using VideoDecoder.
 *
 * Exposes the API surface required by EditorViewModel:
 *   isPlaying, currentPositionUs, totalDurationUs, isBuffering
 *   play(), pause(), togglePlayPause(), seekToUs(Long), seekByUs(Long)
 */
class PreviewPlayerManager(private val context: Context) {
    private var player: ExoPlayer? = null
    private var totalUs: Long = 0L

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionUs = MutableStateFlow(0L)
    val currentPositionUs: StateFlow<Long> = _positionUs.asStateFlow()

    private val _totalDurationUs = MutableStateFlow(0L)
    val totalDurationUs: StateFlow<Long> = _totalDurationUs.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    var onSeek: ((Long) -> Unit)? = null

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    init {
        initialize()
        scope.launch {
            while (isActive) {
                val p = player
                if (p != null && _isPlaying.value) {
                    _positionUs.value = p.currentPosition * 1000L
                }
                kotlinx.coroutines.delay(16)
            }
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post { block() }
        }
    }

    fun setVideoSurface(surface: android.view.Surface?) = runOnMainThread {
        player?.setVideoSurface(surface)
    }

    fun initialize(): ExoPlayer {
        if (player != null) return player!!
        val p = ExoPlayer.Builder(context).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { _isPlaying.value = playing }
            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) _totalDurationUs.value = p.duration.coerceAtLeast(0L) * 1000L
            }
        })
        player = p
        return p
    }

    fun syncWithProject(project: Project) {
        totalUs = project.totalDurationUs
        _totalDurationUs.value = totalUs
        AppLog.d(TAG, "syncWithProject: clips=${project.videoClips.size} total=${totalUs}us")

        runOnMainThread {
            val p = player ?: return@runOnMainThread
            if (project.videoClips.isEmpty()) {
                p.clearMediaItems()
            } else {
                p.clearMediaItems()
                for (clip in project.videoClips) {
                    try {
                        val mi = MediaItem.Builder()
                            .setUri(clip.uri)
                            .build()
                        p.addMediaItem(mi)
                    } catch (t: Throwable) {
                        AppLog.e(TAG, "Failed adding clip: ${clip.uri}", t)
                    }
                }
                val firstSpeed = project.videoClips.firstOrNull()?.speed ?: 1f
                p.playbackParameters = androidx.media3.common.PlaybackParameters(firstSpeed)
                p.prepare()
            }
        }
    }

    fun play() = runOnMainThread {
        player?.play()
    }

    fun pause() = runOnMainThread {
        player?.pause()
    }

    fun togglePlayPause() { if (_isPlaying.value) pause() else play() }

    fun seekToUs(targetTimelineUs: Long) = runOnMainThread {
        val ms = targetTimelineUs / 1000L
        player?.seekTo(ms.coerceAtLeast(0L))
        _positionUs.value = targetTimelineUs
        onSeek?.invoke(targetTimelineUs)
    }

    fun seekByUs(deltaUs: Long) {
        seekToUs((_positionUs.value + deltaUs).coerceAtLeast(0L))
    }

    fun release() = runOnMainThread {
        scope.cancel()
        player?.release()
        player = null
    }

    companion object { private const val TAG = "PreviewPlayerManager" }
}
