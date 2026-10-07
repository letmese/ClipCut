package com.clipcut.core.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private data class AudioTrackPlayerItem(
    val id: String,
    val player: ExoPlayer,
    var timelineStartUs: Long,
    var timelineEndUs: Long,
    var uri: String,
    var trimStartUs: Long,
    var trimEndUs: Long
)

/**
 * PreviewPlayerManager — wraps a single ExoPlayer instance for preview.
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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    @Volatile private var cachedClips: List<TimelineItem> = emptyList()
    @Volatile private var cachedAudioTracks: List<AudioTrackItem> = emptyList()
    @Volatile private var projectVideoVolume: Float = 1.0f
    @Volatile private var backgroundMusicVolume: Float = 1.0f
    @Volatile private var audioDuckingEnabled: Boolean = false
    @Volatile private var audioDuckLevel: Float = 0.3f

    private val audioTrackPlayers = java.util.concurrent.CopyOnWriteArrayList<AudioTrackPlayerItem>()

    init {
        initialize()
        scope.launch {
            while (isActive) {
                try {
                    val p = player
                    val isPlayingNow = _isPlaying.value
                    if (isPlayingNow) {
                        if (p != null && cachedClips.isNotEmpty()) {
                            try {
                                val idx = p.currentMediaItemIndex
                                val clips = cachedClips
                                if (clips.isNotEmpty() && idx in clips.indices) {
                                    var accumulatedUs = 0L
                                    for (i in 0 until idx) {
                                        accumulatedUs += clips[i].effectiveDurationUs
                                    }
                                    val currentClip = clips[idx]
                                    if (currentClip.speedRamp.isActive) {
                                        val currentOffsetUs = (p.currentPosition * 1000L).coerceAtLeast(0L)
                                        val rampSpeed = currentClip.speedRamp.speedAt(currentOffsetUs).coerceIn(0.1f, 8.0f)
                                        if (Math.abs(p.playbackParameters.speed - rampSpeed) > 0.05f) {
                                            runOnMainThread {
                                                p.playbackParameters = androidx.media3.common.PlaybackParameters(rampSpeed)
                                            }
                                        }
                                    }
                                    val speed = if (currentClip.speedRamp.isActive) {
                                        currentClip.speedRamp.speedAt((p.currentPosition * 1000L).coerceAtLeast(0L)).coerceAtLeast(0.1f)
                                    } else {
                                        currentClip.speed.coerceAtLeast(0.1f)
                                    }
                                    val clipOffsetUs = (p.currentPosition * 1000L / speed).toLong()
                                    _positionUs.value = accumulatedUs + clipOffsetUs
                                } else {
                                    _positionUs.value = p.currentPosition * 1000L
                                }
                            } catch (_: Throwable) {}
                        } else if (cachedClips.isEmpty() && audioTrackPlayers.isNotEmpty()) {
                            _positionUs.value += 16_000L
                            if (_positionUs.value >= totalUs) {
                                _positionUs.value = 0L
                                runOnMainThread { pause() }
                            }
                        }
                    }

                    val currentPosUs = _positionUs.value
                    val isEnded = (totalUs > 0L && currentPosUs >= totalUs)
                    if (isEnded && isPlayingNow) {
                        runOnMainThread {
                            pause()
                            seekToUs(0L)
                        }
                    } else {
                        // Synchronize audio track players with current timeline position
                        for (item in audioTrackPlayers) {
                            try {
                                if (currentPosUs in item.timelineStartUs until item.timelineEndUs) {
                                    val relTimeMs = ((currentPosUs - item.timelineStartUs) / 1000L).coerceAtLeast(0L)
                                    if (isPlayingNow) {
                                        if (!item.player.isPlaying) {
                                            item.player.seekTo(relTimeMs)
                                            item.player.play()
                                        } else {
                                            val actualPos = item.player.currentPosition
                                            if (Math.abs(actualPos - relTimeMs) > 250) {
                                                item.player.seekTo(relTimeMs)
                                            }
                                        }
                                    } else {
                                        if (item.player.isPlaying) item.player.pause()
                                    }
                                } else {
                                    if (item.player.isPlaying) item.player.pause()
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (t: Throwable) {
                    AppLog.w(TAG, "Ticker loop non-fatal error: ${t.message}")
                }
                delay(16)
            }
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post { block() }
        }
    }

    fun setVideoSurface(surface: android.view.Surface?) = runOnMainThread {
        player?.setVideoSurface(surface)
    }

    fun initialize(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context)
            .setLooper(Looper.getMainLooper())
            .build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { _isPlaying.value = playing }
            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) _totalDurationUs.value = p.duration.coerceAtLeast(0L) * 1000L
                if (state == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    for (item in audioTrackPlayers) {
                        try {
                            item.player.pause()
                            item.player.seekTo(0L)
                        } catch (_: Throwable) {}
                    }
                    _positionUs.value = 0L
                    p.seekTo(0, 0L)
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = p.currentMediaItemIndex
                val clip = cachedClips.getOrNull(idx) ?: return
                p.playbackParameters = androidx.media3.common.PlaybackParameters(clip.speed.coerceAtLeast(0.1f))
                p.volume = (projectVideoVolume * clip.volume).coerceIn(0f, 1f)
            }
        })
        player = p
        return p
    }

    private var lastClipsFingerprint: String? = null

    fun syncWithProject(project: Project) {
        totalUs = project.totalDurationUs
        _totalDurationUs.value = totalUs
        cachedClips = project.videoClips
        cachedAudioTracks = project.audioTracks
        projectVideoVolume = project.originalVideoVolume
        backgroundMusicVolume = project.backgroundMusicVolume
        audioDuckingEnabled = project.audioDuckingEnabled
        audioDuckLevel = project.audioDuckLevel
        AppLog.d(TAG, "syncWithProject: clips=${project.videoClips.size} audio=${project.audioTracks.size} total=${totalUs}us")

        runOnMainThread {
            val p = player ?: return@runOnMainThread
            val currentFingerprint = project.videoClips.joinToString(";") { 
                "${it.id}:${it.uri}:${it.trimStartUs}:${it.trimEndUs}:${it.speed}" 
            }

            val currentIdx = try { p.currentMediaItemIndex } catch (_: Throwable) { 0 }
            val current = project.videoClips.getOrNull(currentIdx) ?: project.videoClips.firstOrNull()
            if (current != null) {
                p.playbackParameters = androidx.media3.common.PlaybackParameters(current.speed.coerceAtLeast(0.1f))
                p.volume = (project.originalVideoVolume * current.volume).coerceIn(0f, 1f)
            }

            if (currentFingerprint != lastClipsFingerprint) {
                lastClipsFingerprint = currentFingerprint
                val prevPositionMs = try { p.currentPosition } catch (_: Throwable) { 0L }
                val wasPlaying = p.isPlaying

                if (project.videoClips.isEmpty()) {
                    p.clearMediaItems()
                } else {
                    p.clearMediaItems()
                    for (clip in project.videoClips) {
                        try {
                            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs((clip.trimStartUs / 1000L).coerceAtLeast(0L))
                            if (clip.trimEndUs > clip.trimStartUs) {
                                clippingBuilder.setEndPositionMs((clip.trimEndUs / 1000L).coerceAtLeast(clip.trimStartUs / 1000L + 1L))
                            }
                            val mi = MediaItem.Builder()
                                .setUri(clip.uri)
                                .setClippingConfiguration(clippingBuilder.build())
                                .build()
                            p.addMediaItem(mi)
                        } catch (t: Throwable) {
                            AppLog.e(TAG, "Failed adding clip: ${clip.uri}", t)
                        }
                    }
                    p.prepare()
                    if (prevPositionMs > 0) {
                        p.seekTo(prevPositionMs)
                    }
                    if (wasPlaying) {
                        p.play()
                    }
                }
            }

            // Reconcile companion audio players with project.audioTracks
            val currentTrackIds = project.audioTracks.map { it.id }.toSet()
            val toRemove = audioTrackPlayers.filter { it.id !in currentTrackIds }
            for (existing in toRemove) {
                try { existing.player.release() } catch (_: Throwable) {}
                audioTrackPlayers.remove(existing)
            }

            for (track in project.audioTracks) {
                val existing = audioTrackPlayers.find { it.id == track.id }
                val trackEndUs = track.timelineStartUs + track.effectiveDurationUs
                val trackVol = (project.backgroundMusicVolume * track.volume).coerceIn(0f, 1f)
                if (existing == null) {
                    try {
                        val audioPlayer = ExoPlayer.Builder(context)
                            .setLooper(Looper.getMainLooper())
                            .build()
                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs((track.trimStartUs / 1000L).coerceAtLeast(0L))
                        if (track.trimEndUs > track.trimStartUs) {
                            clippingBuilder.setEndPositionMs((track.trimEndUs / 1000L).coerceAtLeast(track.trimStartUs / 1000L + 1L))
                        }
                        val mi = MediaItem.Builder()
                            .setUri(track.uri)
                            .setClippingConfiguration(clippingBuilder.build())
                            .build()
                        audioPlayer.setMediaItem(mi)
                        audioPlayer.volume = trackVol
                        audioPlayer.prepare()
                        audioTrackPlayers.add(
                            AudioTrackPlayerItem(
                                id = track.id,
                                player = audioPlayer,
                                timelineStartUs = track.timelineStartUs,
                                timelineEndUs = trackEndUs,
                                uri = track.uri,
                                trimStartUs = track.trimStartUs,
                                trimEndUs = track.trimEndUs
                            )
                        )
                    } catch (t: Throwable) {
                        AppLog.e(TAG, "Failed adding audio track player: ${track.name}", t)
                    }
                } else {
                    existing.timelineStartUs = track.timelineStartUs
                    existing.timelineEndUs = trackEndUs
                    existing.player.volume = trackVol
                    if (existing.uri != track.uri || existing.trimStartUs != track.trimStartUs || existing.trimEndUs != track.trimEndUs) {
                        existing.uri = track.uri
                        existing.trimStartUs = track.trimStartUs
                        existing.trimEndUs = track.trimEndUs
                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs((track.trimStartUs / 1000L).coerceAtLeast(0L))
                        if (track.trimEndUs > track.trimStartUs) {
                            clippingBuilder.setEndPositionMs((track.trimEndUs / 1000L).coerceAtLeast(track.trimStartUs / 1000L + 1L))
                        }
                        val mi = MediaItem.Builder()
                            .setUri(track.uri)
                            .setClippingConfiguration(clippingBuilder.build())
                            .build()
                        existing.player.setMediaItem(mi)
                        existing.player.prepare()
                    }
                }
            }
        }
    }

    fun play() = runOnMainThread {
        _isPlaying.value = true
        player?.play()
        val currentUs = _positionUs.value
        for (item in audioTrackPlayers) {
            try {
                if (currentUs in item.timelineStartUs until item.timelineEndUs) {
                    val relTimeMs = ((currentUs - item.timelineStartUs) / 1000L).coerceAtLeast(0L)
                    item.player.seekTo(relTimeMs)
                    item.player.play()
                } else {
                    item.player.pause()
                }
            } catch (_: Throwable) {}
        }
    }

    fun pause() = runOnMainThread {
        _isPlaying.value = false
        player?.pause()
        for (item in audioTrackPlayers) {
            try { item.player.pause() } catch (_: Throwable) {}
        }
    }

    fun togglePlayPause() { if (_isPlaying.value) pause() else play() }

    fun seekToUs(targetTimelineUs: Long) = runOnMainThread {
        val p = player ?: return@runOnMainThread
        val clips = cachedClips
        if (clips.isNotEmpty() && p.mediaItemCount > 0) {
            var accumulatedUs = 0L
            var targetIdx = clips.lastIndex
            var targetOffsetInClipMs = 0L
            for ((idx, clip) in clips.withIndex()) {
                val effDur = clip.effectiveDurationUs
                if (targetTimelineUs < accumulatedUs + effDur || idx == clips.lastIndex) {
                    targetIdx = idx
                    val offsetInClipUs = (targetTimelineUs - accumulatedUs).coerceAtLeast(0L)
                    val speed = clip.speed.coerceAtLeast(0.1f)
                    targetOffsetInClipMs = (offsetInClipUs * speed / 1000L).toLong()
                    break
                }
                accumulatedUs += effDur
            }
            if (targetIdx < p.mediaItemCount) {
                p.seekTo(targetIdx, targetOffsetInClipMs)
            }
        } else if (p.mediaItemCount > 0) {
            val ms = targetTimelineUs / 1000L
            p.seekTo(ms.coerceAtLeast(0L))
        }

        for (item in audioTrackPlayers) {
            try {
                if (targetTimelineUs in item.timelineStartUs until item.timelineEndUs) {
                    val relTimeMs = ((targetTimelineUs - item.timelineStartUs) / 1000L).coerceAtLeast(0L)
                    item.player.seekTo(relTimeMs)
                    if (_isPlaying.value) item.player.play() else item.player.pause()
                } else {
                    item.player.pause()
                    item.player.seekTo(0L)
                }
            } catch (_: Throwable) {}
        }

        _positionUs.value = targetTimelineUs
        onSeek?.invoke(targetTimelineUs)
    }

    fun seekByUs(deltaUs: Long) {
        seekToUs((_positionUs.value + deltaUs).coerceAtLeast(0L))
    }

    fun release() = runOnMainThread {
        scope.cancel()
        try { player?.release() } catch (t: Throwable) { AppLog.w(TAG, "player.release", t) }
        player = null
        for (item in audioTrackPlayers) {
            try { item.player.release() } catch (t: Throwable) { AppLog.w(TAG, "audioTrackPlayer.release", t) }
        }
        audioTrackPlayers.clear()
        lastClipsFingerprint = null
        _isPlaying.value = false
    }

    companion object { private const val TAG = "PreviewPlayerManager" }
}
