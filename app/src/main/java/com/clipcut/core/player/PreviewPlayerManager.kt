package com.clipcut.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreviewPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val musicPlayers = mutableMapOf<String, ExoPlayer>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionUs = MutableStateFlow(0L)
    val currentPositionUs: StateFlow<Long> = _currentPositionUs.asStateFlow()

    private val _totalDurationUs = MutableStateFlow(0L)
    val totalDurationUs: StateFlow<Long> = _totalDurationUs.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null
    private var currentProject: Project? = null
    private var lastClipsFingerprint: String? = null
    private var lastMusicFingerprint: String? = null

    var onSeek: ((Long) -> Unit)? = null

    fun initialize(): ExoPlayer {
        if (exoPlayer == null) {
            val player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startPositionUpdates()
                            musicPlayers.values.forEach { it.play() }
                        } else {
                            stopPositionUpdates()
                            musicPlayers.values.forEach { it.pause() }
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        _isBuffering.value = (state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_ENDED) {
                            _isPlaying.value = false
                            _currentPositionUs.value = _totalDurationUs.value
                            stopPositionUpdates()
                            musicPlayers.values.forEach { it.pause() }
                            AppLog.d(TAG, "Playback reached END - ready to replay")
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        AppLog.e(TAG, "ExoPlayer error: ${error.errorCodeName} - ${error.message}", error)
                    }
                })
            }
            exoPlayer = player
            AppLog.i(TAG, "ExoPlayer preview initialized")
        }
        return exoPlayer!!
    }

    fun syncWithProject(project: Project) {
        currentProject = project
        val player = exoPlayer ?: initialize()

        _totalDurationUs.value = project.totalDurationUs
        player.volume = project.originalVideoVolume

        if (project.videoClips.isEmpty()) {
            player.clearMediaItems()
            releaseAllMusicPlayers()
            _totalDurationUs.value = 0L
            lastClipsFingerprint = null
            lastMusicFingerprint = null
            return
        }

        val newClipsFingerprint = project.videoClips.joinToString(";") { clip ->
            "${clip.id}:${clip.uri}:${clip.trimStartUs}:${clip.trimEndUs}:${clip.speed}"
        }

        if (lastClipsFingerprint != newClipsFingerprint) {
            lastClipsFingerprint = newClipsFingerprint
            val wasPlaying = player.isPlaying
            val savedPosUs = _currentPositionUs.value

            val mediaItems = project.videoClips.map { clip ->
                val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartUs / 1000)
                    .setEndPositionMs(clip.trimEndUs / 1000)
                    .build()

                MediaItem.Builder()
                    .setUri(Uri.parse(clip.uri))
                    .setClippingConfiguration(clippingConfig)
                    .build()
            }

            player.setMediaItems(mediaItems)
            player.prepare()

            if (savedPosUs > 0L) {
                seekToUs(savedPosUs)
            }

            if (wasPlaying) {
                player.play()
            }
            AppLog.d(TAG, "Re-prepared ExoPlayer with ${mediaItems.size} clips (dur=${project.totalDurationUs / 1000}ms)")
        }

        syncMusicPlayers(project)
    }

    private fun syncMusicPlayers(project: Project) {
        val newFingerprint = project.audioTracks.joinToString(";") { track ->
            "${track.id}:${track.uri}"
        }

        if (lastMusicFingerprint == newFingerprint) {
            updateMusicVolumes(project)
            return
        }

        lastMusicFingerprint = newFingerprint

        val currentTrackIds = project.audioTracks.map { it.id }.toSet()
        val toRemove = musicPlayers.keys.filter { it !in currentTrackIds }
        for (id in toRemove) {
            musicPlayers.remove(id)?.release()
        }

        for (track in project.audioTracks) {
            val existing = musicPlayers[track.id]
            if (existing == null) {
                val newPlayer = ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    setMediaItem(MediaItem.fromUri(Uri.parse(track.uri)))
                    prepare()
                }
                musicPlayers[track.id] = newPlayer
            }
        }

        updateMusicVolumes(project)

        val videoPlayer = exoPlayer
        if (videoPlayer != null && videoPlayer.isPlaying) {
            musicPlayers.values.forEach { it.play() }
        }
    }

    private fun updateMusicVolumes(project: Project) {
        val bgVol = project.backgroundMusicVolume
        for (track in project.audioTracks) {
            val player = musicPlayers[track.id] ?: continue
            player.volume = (bgVol * track.volume).coerceIn(0f, 2f)
        }
    }

    private fun releaseAllMusicPlayers() {
        musicPlayers.values.forEach { it.release() }
        musicPlayers.clear()
        lastMusicFingerprint = null
    }

    fun play() {
        val player = exoPlayer ?: return
        if (player.playbackState == Player.STATE_ENDED ||
            _currentPositionUs.value >= (_totalDurationUs.value - 50_000L).coerceAtLeast(0L)
        ) {
            AppLog.d(TAG, "Playback at end; rewinding to 0 before play")
            seekToUs(0L)
        }
        player.play()
        musicPlayers.values.forEach { it.play() }
    }

    fun pause() {
        exoPlayer?.pause()
        musicPlayers.values.forEach { it.pause() }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekToUs(targetTimelineUs: Long) {
        val proj = currentProject ?: return
        val player = exoPlayer ?: return

        val clampedTargetUs = targetTimelineUs.coerceIn(0L, _totalDurationUs.value)
        var accumulatedUs = 0L
        var targetWindowIndex = 0
        var windowOffsetMs = 0L

        for ((index, clip) in proj.videoClips.withIndex()) {
            val clipDurationUs = clip.effectiveDurationUs
            if (clampedTargetUs <= accumulatedUs + clipDurationUs || index == proj.videoClips.lastIndex) {
                targetWindowIndex = index
                val localUs = clampedTargetUs - accumulatedUs
                windowOffsetMs = (localUs * clip.speed / 1000).toLong()
                break
            }
            accumulatedUs += clipDurationUs
        }

        val currentClip = proj.videoClips.getOrNull(targetWindowIndex)
        if (currentClip != null && player.playbackParameters.speed != currentClip.speed) {
            player.playbackParameters = PlaybackParameters(currentClip.speed)
        }

        player.seekTo(targetWindowIndex, windowOffsetMs.coerceAtLeast(0L))

        for (track in proj.audioTracks) {
            val musicPlayer = musicPlayers[track.id] ?: continue
            val localPlaybackUs = clampedTargetUs - track.timelineStartUs
            if (localPlaybackUs >= 0L) {
                musicPlayer.seekTo(localPlaybackUs / 1000)
            } else {
                musicPlayer.seekTo(0L)
            }
        }

        _currentPositionUs.value = clampedTargetUs
        onSeek?.invoke(clampedTargetUs)
    }

    fun seekByUs(deltaUs: Long) {
        seekToUs(_currentPositionUs.value + deltaUs)
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                updateCurrentTimelinePosition()
                delay(33)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun updateCurrentTimelinePosition() {
        val player = exoPlayer ?: return
        val proj = currentProject ?: return

        val currentWindowIndex = player.currentMediaItemIndex
        val currentClip = proj.videoClips.getOrNull(currentWindowIndex)
        if (currentClip != null && player.playbackParameters.speed != currentClip.speed) {
            player.playbackParameters = PlaybackParameters(currentClip.speed)
        }

        val currentPositionInWindowMs = player.currentPosition

        var accumulatedUs = 0L
        for (i in 0 until currentWindowIndex) {
            if (i < proj.videoClips.size) {
                accumulatedUs += proj.videoClips[i].effectiveDurationUs
            }
        }

        if (currentWindowIndex < proj.videoClips.size) {
            val clip = proj.videoClips[currentWindowIndex]
            val localUs = (currentPositionInWindowMs * 1000.0 / clip.speed).toLong()
            accumulatedUs += localUs
        }

        _currentPositionUs.value = accumulatedUs.coerceIn(0L, _totalDurationUs.value)
    }

    fun release() {
        stopPositionUpdates()
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
        releaseAllMusicPlayers()
        AppLog.d(TAG, "PreviewPlayerManager released")
    }

    companion object {
        private const val TAG = "PreviewPlayerManager"
    }
}
