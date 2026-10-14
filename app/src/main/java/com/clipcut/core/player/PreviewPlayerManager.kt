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

private data class PipVideoPlayerItem(
    val id: String,
    val player: ExoPlayer,
    var timelineStartUs: Long,
    var timelineEndUs: Long,
    var uri: String,
    var trimStartUs: Long,
    var trimEndUs: Long
)

private data class PlayerSegment(
    val uri: String,
    val timelineStartUs: Long,
    val timelineEndUs: Long,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val speed: Float,
    val volume: Float,
    val speedRamp: com.clipcut.core.model.SpeedRamp = com.clipcut.core.model.SpeedRamp()
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
    @Volatile private var cachedSegments: List<PlayerSegment> = emptyList()
    @Volatile private var cachedAudioTracks: List<AudioTrackItem> = emptyList()
    @Volatile private var projectVideoVolume: Float = 1.0f
    @Volatile private var backgroundMusicVolume: Float = 1.0f
    @Volatile private var audioDuckingEnabled: Boolean = false
    @Volatile private var audioDuckLevel: Float = 0.3f
    @Volatile private var voicePitchMultiplier: Float = 1.0f
    @Volatile private var currentVoicePreset: com.clipcut.core.audio.AudioDsp.VoicePreset = com.clipcut.core.audio.AudioDsp.VoicePreset.NATURAL
    private var audioEqualizer: android.media.audiofx.Equalizer? = null
    @Volatile private var cachedEqualizerGains: List<Float> = emptyList()

    private val audioTrackPlayers = mutableListOf<AudioTrackPlayerItem>()
    private var pipVideoPlayer: PipVideoPlayerItem? = null
    @Volatile private var pipVideoSurface: android.view.Surface? = null

    fun setPipVideoSurface(surface: android.view.Surface?) = runOnMainThread {
        pipVideoSurface = surface
        pipVideoPlayer?.player?.setVideoSurface(surface)
    }

    init {
        initialize()
        scope.launch {
            while (isActive) {
                val p = player
                val isPlayingNow = _isPlaying.value
                if (isPlayingNow) {
                    if (p != null && (cachedSegments.isNotEmpty() || cachedClips.isNotEmpty())) {
                        try {
                            val idx = p.currentMediaItemIndex
                            val segments = cachedSegments
                            if (segments.isNotEmpty() && idx in segments.indices) {
                                val seg = segments[idx]
                                val offsetInSegTimelineUs = (_positionUs.value - seg.timelineStartUs).coerceAtLeast(0L)
                                val dynamicSpeed = if (seg.speedRamp.isActive) {
                                    seg.speedRamp.speedAt(offsetInSegTimelineUs).coerceIn(0.1f, 10.0f)
                                } else {
                                    seg.speed.coerceAtLeast(0.1f)
                                }
                                if (Math.abs(p.playbackParameters.speed - dynamicSpeed) > 0.05f || Math.abs(p.playbackParameters.pitch - voicePitchMultiplier) > 0.02f) {
                                    p.playbackParameters = androidx.media3.common.PlaybackParameters(dynamicSpeed, voicePitchMultiplier)
                                }
                                val segOffsetUs = (p.currentPosition * 1000L / dynamicSpeed).toLong()
                                _positionUs.value = (seg.timelineStartUs + segOffsetUs).coerceIn(0L, totalUs.coerceAtLeast(0L))
                            } else {
                                _positionUs.value = p.currentPosition * 1000L
                            }
                        } catch (_: IllegalStateException) {}
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

                    // Synchronize PiP video player with current timeline position
                    pipVideoPlayer?.let { item ->
                        try {
                            if (currentPosUs in item.timelineStartUs until item.timelineEndUs) {
                                val relTimeMs = ((currentPosUs - item.timelineStartUs + item.trimStartUs) / 1000L).coerceAtLeast(0L)
                                if (isPlayingNow) {
                                    if (!item.player.isPlaying) {
                                        item.player.seekTo(relTimeMs)
                                        item.player.play()
                                    } else {
                                        val actualPos = item.player.currentPosition
                                        if (Math.abs(actualPos - relTimeMs) > 200) {
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

    private fun createLoadControl(): androidx.media3.exoplayer.DefaultLoadControl {
        return androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1000,
                /* maxBufferMs = */ 3000,
                /* bufferForPlaybackMs = */ 50,
                /* bufferForPlaybackAfterRebufferMs = */ 100
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    private fun buildSegments(clips: List<TimelineItem>): List<PlayerSegment> {
        if (clips.isEmpty()) return emptyList()
        val segments = mutableListOf<PlayerSegment>()
        var curUri = clips[0].uri
        var curTlStart = 0L
        var curSourceStart = clips[0].trimStartUs
        var curSourceEnd = if (clips[0].trimEndUs > clips[0].trimStartUs) clips[0].trimEndUs else (clips[0].trimStartUs + clips[0].effectiveDurationUs)
        var curSpeed = clips[0].speed
        var curSpeedRamp = clips[0].speedRamp
        var curVolume = clips[0].volume
        var curTlDuration = clips[0].effectiveDurationUs

        for (i in 1 until clips.size) {
            val next = clips[i]
            val nextSourceStart = next.trimStartUs
            val nextSourceEnd = if (next.trimEndUs > next.trimStartUs) next.trimEndUs else (next.trimStartUs + next.effectiveDurationUs)
            val isContiguous = (next.uri == curUri) &&
                    (Math.abs(next.speed - curSpeed) < 0.01f) &&
                    !next.speedRamp.isActive &&
                    !curSpeedRamp.isActive &&
                    (Math.abs(nextSourceStart - curSourceEnd) <= 2000L)

            if (isContiguous) {
                curSourceEnd = nextSourceEnd
                curTlDuration += next.effectiveDurationUs
            } else {
                segments.add(
                    PlayerSegment(
                        uri = curUri,
                        timelineStartUs = curTlStart,
                        timelineEndUs = curTlStart + curTlDuration,
                        sourceStartUs = curSourceStart,
                        sourceEndUs = curSourceEnd,
                        speed = curSpeed,
                        volume = curVolume,
                        speedRamp = curSpeedRamp
                    )
                )
                curTlStart += curTlDuration
                curUri = next.uri
                curSourceStart = nextSourceStart
                curSourceEnd = nextSourceEnd
                curSpeed = next.speed
                curSpeedRamp = next.speedRamp
                curVolume = next.volume
                curTlDuration = next.effectiveDurationUs
            }
        }
        segments.add(
            PlayerSegment(
                uri = curUri,
                timelineStartUs = curTlStart,
                timelineEndUs = curTlStart + curTlDuration,
                sourceStartUs = curSourceStart,
                sourceEndUs = curSourceEnd,
                speed = curSpeed,
                volume = curVolume,
                speedRamp = curSpeedRamp
            )
        )
        return segments
    }

    fun initialize(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(context)
            .setLoadControl(createLoadControl())
            .setLooper(Looper.getMainLooper())
            .build()
        p.pauseAtEndOfMediaItems = false
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { _isPlaying.value = playing }
            override fun onPlaybackStateChanged(state: Int) {
                _isBuffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    _totalDurationUs.value = if (totalUs > 0L) totalUs else (p.duration.coerceAtLeast(0L) * 1000L)
                    applyEqualizerGains()
                }
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
                val seg = cachedSegments.getOrNull(idx) ?: return
                p.playbackParameters = androidx.media3.common.PlaybackParameters(seg.speed.coerceAtLeast(0.1f), voicePitchMultiplier)
                p.volume = (projectVideoVolume * seg.volume).coerceIn(0f, 1f)
                applyEqualizerGains()
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
        val newSegments = buildSegments(project.videoClips)
        cachedSegments = newSegments
        cachedAudioTracks = project.audioTracks
        projectVideoVolume = project.originalVideoVolume
        backgroundMusicVolume = project.backgroundMusicVolume
        audioDuckingEnabled = project.audioDuckingEnabled
        audioDuckLevel = project.audioDuckLevel
        AppLog.d(TAG, "syncWithProject: clips=${project.videoClips.size} segments=${newSegments.size} audio=${project.audioTracks.size} total=${totalUs}us")

        runOnMainThread {
            val p = player ?: return@runOnMainThread
            val currentFingerprint = newSegments.joinToString(";") { seg ->
                val rampKey = if (seg.speedRamp.isActive) seg.speedRamp.keyframes.joinToString(",") { "${it.timeOffsetUs}_${it.speed}" } else "none"
                "${seg.uri}:${seg.sourceStartUs}:${seg.sourceEndUs}:${seg.speed}:$rampKey"
            }

            val currentIdx = try { p.currentMediaItemIndex } catch (_: Throwable) { 0 }
            val current = newSegments.getOrNull(currentIdx) ?: newSegments.firstOrNull()
            if (current != null) {
                p.playbackParameters = androidx.media3.common.PlaybackParameters(current.speed.coerceAtLeast(0.1f), voicePitchMultiplier)
                p.volume = (project.originalVideoVolume * current.volume).coerceIn(0f, 1f)
            }

            if (currentFingerprint != lastClipsFingerprint) {
                lastClipsFingerprint = currentFingerprint
                val prevTimelineUs = _positionUs.value
                val wasPlaying = p.isPlaying

                if (newSegments.isEmpty()) {
                    p.clearMediaItems()
                } else {
                    p.clearMediaItems()
                    for (seg in newSegments) {
                        try {
                            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs((seg.sourceStartUs / 1000L).coerceAtLeast(0L))
                            if (seg.sourceEndUs > seg.sourceStartUs) {
                                clippingBuilder.setEndPositionMs((seg.sourceEndUs / 1000L).coerceAtLeast(seg.sourceStartUs / 1000L + 1L))
                            }
                            val mi = MediaItem.Builder()
                                .setUri(seg.uri)
                                .setClippingConfiguration(clippingBuilder.build())
                                .build()
                            p.addMediaItem(mi)
                        } catch (t: Throwable) {
                            AppLog.e(TAG, "Failed adding segment: ${seg.uri}", t)
                        }
                    }
                    p.prepare()
                    seekToUs(prevTimelineUs)
                    if (wasPlaying) {
                        p.play()
                    }
                }
            }

            // Reconcile companion audio players with project.audioTracks
            val currentTrackIds = project.audioTracks.map { it.id }.toSet()
            val iter = audioTrackPlayers.iterator()
            while (iter.hasNext()) {
                val existing = iter.next()
                if (existing.id !in currentTrackIds) {
                    try { existing.player.release() } catch (_: Throwable) {}
                    iter.remove()
                }
            }

            for (track in project.audioTracks) {
                val existing = audioTrackPlayers.find { it.id == track.id }
                val trackEndUs = track.timelineStartUs + track.effectiveDurationUs
                val trackVol = (project.backgroundMusicVolume * track.volume).coerceIn(0f, 1f)
                if (existing == null) {
                    try {
                        val audioPlayer = ExoPlayer.Builder(context)
                            .setLoadControl(createLoadControl())
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

            // Reconcile PiP video player with project.pipTracks
            val firstVideoPip = project.pipTracks.firstOrNull { pip ->
                pip.uri.isNotEmpty() && (
                    pip.uri.contains("video", ignoreCase = true) ||
                    pip.uri.endsWith(".mp4", ignoreCase = true) ||
                    pip.uri.endsWith(".mkv", ignoreCase = true) ||
                    pip.uri.endsWith(".mov", ignoreCase = true) ||
                    pip.uri.endsWith(".webm", ignoreCase = true) ||
                    (pip.uri.startsWith("content://") && !pip.uri.contains("images", ignoreCase = true) && !pip.uri.endsWith(".png", true) && !pip.uri.endsWith(".jpg", true) && !pip.uri.endsWith(".jpeg", true) && !pip.uri.endsWith(".webp", true))
                )
            }

            if (firstVideoPip != null) {
                val currentPip = pipVideoPlayer
                if (currentPip == null) {
                    try {
                        val p = ExoPlayer.Builder(context)
                            .setLoadControl(createLoadControl())
                            .setLooper(Looper.getMainLooper())
                            .build()
                        p.setVideoSurface(pipVideoSurface)
                        p.volume = firstVideoPip.volume
                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs((firstVideoPip.trimStartUs / 1000L).coerceAtLeast(0L))
                        if (firstVideoPip.trimEndUs > firstVideoPip.trimStartUs) {
                            clippingBuilder.setEndPositionMs((firstVideoPip.trimEndUs / 1000L).coerceAtLeast(firstVideoPip.trimStartUs / 1000L + 1L))
                        }
                        val mi = MediaItem.Builder()
                            .setUri(firstVideoPip.uri)
                            .setClippingConfiguration(clippingBuilder.build())
                            .build()
                        p.setMediaItem(mi)
                        p.prepare()
                        pipVideoPlayer = PipVideoPlayerItem(
                            id = firstVideoPip.id,
                            player = p,
                            timelineStartUs = firstVideoPip.timelineStartUs,
                            timelineEndUs = firstVideoPip.timelineEndUs,
                            uri = firstVideoPip.uri,
                            trimStartUs = firstVideoPip.trimStartUs,
                            trimEndUs = firstVideoPip.trimEndUs
                        )
                    } catch (t: Throwable) {
                        AppLog.w(TAG, "Failed creating pip player for ${firstVideoPip.uri}", t)
                    }
                } else {
                    currentPip.timelineStartUs = firstVideoPip.timelineStartUs
                    currentPip.timelineEndUs = firstVideoPip.timelineEndUs
                    currentPip.player.volume = firstVideoPip.volume
                    if (currentPip.uri != firstVideoPip.uri || currentPip.trimStartUs != firstVideoPip.trimStartUs || currentPip.trimEndUs != firstVideoPip.trimEndUs) {
                        currentPip.uri = firstVideoPip.uri
                        currentPip.trimStartUs = firstVideoPip.trimStartUs
                        currentPip.trimEndUs = firstVideoPip.trimEndUs
                        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs((firstVideoPip.trimStartUs / 1000L).coerceAtLeast(0L))
                        if (firstVideoPip.trimEndUs > firstVideoPip.trimStartUs) {
                            clippingBuilder.setEndPositionMs((firstVideoPip.trimEndUs / 1000L).coerceAtLeast(firstVideoPip.trimStartUs / 1000L + 1L))
                        }
                        val mi = MediaItem.Builder()
                            .setUri(firstVideoPip.uri)
                            .setClippingConfiguration(clippingBuilder.build())
                            .build()
                        currentPip.player.setMediaItem(mi)
                        currentPip.player.prepare()
                    }
                }
            } else {
                try { pipVideoPlayer?.player?.release() } catch (_: Throwable) {}
                pipVideoPlayer = null
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
        pipVideoPlayer?.let { item ->
            try {
                if (currentUs in item.timelineStartUs until item.timelineEndUs) {
                    val relTimeMs = ((currentUs - item.timelineStartUs + item.trimStartUs) / 1000L).coerceAtLeast(0L)
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
        pipVideoPlayer?.let {
            try { it.player.pause() } catch (_: Throwable) {}
        }
    }

    fun togglePlayPause() { if (_isPlaying.value) pause() else play() }

    fun seekToUs(targetTimelineUs: Long) = runOnMainThread {
        val p = player ?: return@runOnMainThread
        val segments = cachedSegments
        if (segments.isNotEmpty()) {
            var targetIdx = segments.lastIndex
            var targetOffsetInSegMs = 0L
            for ((idx, seg) in segments.withIndex()) {
                if (targetTimelineUs in seg.timelineStartUs until seg.timelineEndUs || idx == segments.lastIndex) {
                    targetIdx = idx
                    val offsetInSegTimelineUs = (targetTimelineUs - seg.timelineStartUs).coerceAtLeast(0L)
                    val speed = seg.speed.coerceAtLeast(0.1f)
                    val offsetInSegSourceUs = (offsetInSegTimelineUs * speed).toLong()
                    targetOffsetInSegMs = (offsetInSegSourceUs / 1000L).coerceAtLeast(0L)
                    break
                }
            }
            p.seekTo(targetIdx, targetOffsetInSegMs)
        } else {
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

        pipVideoPlayer?.let { item ->
            try {
                if (targetTimelineUs in item.timelineStartUs until item.timelineEndUs) {
                    val relTimeMs = ((targetTimelineUs - item.timelineStartUs + item.trimStartUs) / 1000L).coerceAtLeast(0L)
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

    fun setVoicePreset(preset: com.clipcut.core.audio.AudioDsp.VoicePreset) = runOnMainThread {
        currentVoicePreset = preset
        voicePitchMultiplier = when (preset) {
            com.clipcut.core.audio.AudioDsp.VoicePreset.CHIPMUNK -> 1.6f
            com.clipcut.core.audio.AudioDsp.VoicePreset.DEEP_BASS -> 0.65f
            com.clipcut.core.audio.AudioDsp.VoicePreset.RADIO -> 1.15f
            com.clipcut.core.audio.AudioDsp.VoicePreset.ROBOT -> 0.85f
            com.clipcut.core.audio.AudioDsp.VoicePreset.ALIEN -> 1.35f
            com.clipcut.core.audio.AudioDsp.VoicePreset.NATURAL -> 1.0f
        }
        val p = player ?: return@runOnMainThread
        p.playbackParameters = androidx.media3.common.PlaybackParameters(p.playbackParameters.speed, voicePitchMultiplier)
    }

    fun setEqualizerGains(gains: List<Float>) = runOnMainThread {
        cachedEqualizerGains = gains
        applyEqualizerGains()
    }

    private fun applyEqualizerGains() {
        val p = player ?: return
        val sessionId = try { p.audioSessionId } catch (_: Throwable) { 0 }
        if (sessionId == 0 || sessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return
        try {
            if (audioEqualizer == null) {
                audioEqualizer = android.media.audiofx.Equalizer(0, sessionId).also {
                    it.enabled = true
                }
            }
            val eq = audioEqualizer ?: return
            eq.enabled = true
            val numBands = eq.numberOfBands.toInt()
            val bandRange = eq.bandLevelRange
            val minLevel = bandRange?.getOrNull(0) ?: -1200
            val maxLevel = bandRange?.getOrNull(1) ?: 1200
            for (b in 0 until numBands) {
                val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000
                var closestIdx = 0
                var minDiff = Int.MAX_VALUE
                for (i in com.clipcut.core.audio.InShotEq.FREQUENCIES_HZ.indices) {
                    val diff = Math.abs(com.clipcut.core.audio.InShotEq.FREQUENCIES_HZ[i] - centerFreqHz)
                    if (diff < minDiff) {
                        minDiff = diff
                        closestIdx = i
                    }
                }
                val gainDb = cachedEqualizerGains.getOrNull(closestIdx) ?: 0f
                val mB = (gainDb * 100f).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                eq.setBandLevel(b.toShort(), mB)
            }
        } catch (t: Throwable) {
            AppLog.w(TAG, "applyEqualizerGains error: ${t.message}")
        }
    }

    fun release() = runOnMainThread {
        scope.cancel()
        try { audioEqualizer?.release() } catch (_: Throwable) {}
        audioEqualizer = null
        try { player?.release() } catch (t: Throwable) { AppLog.w(TAG, "player.release", t) }
        player = null
        for (item in audioTrackPlayers) {
            try { item.player.release() } catch (t: Throwable) { AppLog.w(TAG, "audioTrackPlayer.release", t) }
        }
        audioTrackPlayers.clear()
        try { pipVideoPlayer?.player?.release() } catch (t: Throwable) { AppLog.w(TAG, "pipVideoPlayer.release", t) }
        pipVideoPlayer = null
        lastClipsFingerprint = null
        _isPlaying.value = false
    }

    companion object { private const val TAG = "PreviewPlayerManager" }
}
