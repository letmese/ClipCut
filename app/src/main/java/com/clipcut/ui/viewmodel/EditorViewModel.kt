package com.clipcut.ui.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipcut.core.codec.ExportEngine
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.*
import com.clipcut.core.player.GlPreviewRenderer
import com.clipcut.core.player.PreviewPlayerManager
import com.clipcut.data.db.ClipCutDatabase
import com.clipcut.data.db.ProjectEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ExportState {
    object Idle : ExportState()
    data class Progress(val progress: Float, val status: String) : ExportState()
    data class Success(val file: File, val publicUri: Uri? = null, val galleryPath: String = "Movies/ClipCut") : ExportState()
    data class Error(val message: String) : ExportState()
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ClipCutDatabase.getInstance(application)
    private val dao = db.projectDao()

    val playerManager = PreviewPlayerManager(application)
    val previewRenderer = GlPreviewRenderer(application)

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private var activeExportEngine: ExportEngine? = null
    private var exportJob: Job? = null

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val entity = dao.getProjectById(projectId)
            if (entity != null) {
                val proj = entity.toDomain()
                _project.value = proj
                _selectedClipId.value = proj.videoClips.firstOrNull()?.id
                playerManager.syncWithProject(proj)
                previewRenderer.setProject(proj)
                AppLog.i(TAG, "Loaded project: ${proj.name} (${proj.videoClips.size} clips)")
            } else {
                AppLog.e(TAG, "Project not found with id $projectId")
            }
        }
    }

    fun selectClip(id: String?) {
        _selectedClipId.value = id
    }

    fun importMedia(uri: Uri, mediaType: MediaType) {
        val current = _project.value ?: return
        viewModelScope.launch {
            if (mediaType == MediaType.IMAGE) {
                val defaultImageDurationUs = 5_000_000L
                val newClip = TimelineItem(
                    uri = uri.toString(),
                    mediaType = MediaType.IMAGE,
                    sourceDurationUs = defaultImageDurationUs,
                    trimStartUs = 0L,
                    trimEndUs = defaultImageDurationUs
                )
                val updatedClips = current.videoClips + newClip
                updateProjectInternal(current.copy(videoClips = updatedClips))
                _selectedClipId.value = newClip.id
                AppLog.i(TAG, "Imported image clip: ${uri.lastPathSegment} (5000ms)")
                return@launch
            }

            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(getApplication(), uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 5000L
                val durationUs = durationMs * 1000L
                retriever.release()

                if (mediaType == MediaType.VIDEO) {
                    val newClip = TimelineItem(
                        uri = uri.toString(),
                        mediaType = mediaType,
                        sourceDurationUs = durationUs,
                        trimStartUs = 0L,
                        trimEndUs = durationUs
                    )
                    val updatedClips = current.videoClips + newClip
                    updateProjectInternal(current.copy(videoClips = updatedClips))
                    _selectedClipId.value = newClip.id
                    AppLog.i(TAG, "Imported video clip: ${uri.lastPathSegment} (${durationMs}ms)")
                } else if (mediaType == MediaType.AUDIO) {
                    val newTrack = AudioTrackItem(
                        uri = uri.toString(),
                        name = uri.lastPathSegment ?: "Audio Track",
                        sourceDurationUs = durationUs,
                        timelineStartUs = 0L,
                        trimStartUs = 0L,
                        trimEndUs = durationUs
                    )
                    val updatedTracks = current.audioTracks + newTrack
                    updateProjectInternal(current.copy(audioTracks = updatedTracks))
                    AppLog.i(TAG, "Imported audio track: ${newTrack.name} (${durationMs}ms)")
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to import media from $uri", e)
            }
        }
    }

    fun splitClipAtPlayhead() {
        val current = _project.value ?: return
        val playheadUs = playerManager.currentPositionUs.value
        val totalDurUs = current.totalDurationUs

        if (current.videoClips.isEmpty()) {
            AppLog.w(TAG, "Cannot split: No clips in project")
            return
        }

        if (playheadUs <= 50_000L || playheadUs >= totalDurUs - 50_000L) {
            AppLog.w(TAG, "Cannot split at edge")
            return
        }

        var accumulatedUs = 0L
        val updatedClips = mutableListOf<TimelineItem>()
        var didSplit = false

        for (clip in current.videoClips) {
            val clipDurationUs = clip.effectiveDurationUs
            val clipEndUs = accumulatedUs + clipDurationUs
            if (!didSplit && playheadUs > accumulatedUs + 30_000L && playheadUs < clipEndUs - 30_000L) {
                val relativeTimelineOffsetUs = playheadUs - accumulatedUs
                val relativeSourceUs = (relativeTimelineOffsetUs * clip.speed).toLong()
                val splitSourceUs = clip.trimStartUs + relativeSourceUs

                val clip1 = clip.copy(id = java.util.UUID.randomUUID().toString(), trimEndUs = splitSourceUs)
                val clip2 = clip.copy(id = java.util.UUID.randomUUID().toString(), trimStartUs = splitSourceUs)
                updatedClips.add(clip1)
                updatedClips.add(clip2)
                _selectedClipId.value = clip2.id
                didSplit = true
            } else {
                updatedClips.add(clip)
            }
            accumulatedUs += clipDurationUs
        }

        if (didSplit) {
            updateProjectInternal(current.copy(videoClips = updatedClips))
        }
    }

    fun trimClip(clipId: String, newTrimStartUs: Long, newTrimEndUs: Long) {
        val current = _project.value ?: return
        val updated = current.videoClips.map {
            if (it.id == clipId) {
                it.copy(
                    trimStartUs = newTrimStartUs.coerceIn(0L, it.sourceDurationUs),
                    trimEndUs = newTrimEndUs.coerceIn(newTrimStartUs, it.sourceDurationUs)
                )
            } else it
        }
        updateProjectInternal(current.copy(videoClips = updated))
    }

    fun setClipSpeed(clipId: String, speed: Float) {
        val current = _project.value ?: return
        val updated = current.videoClips.map {
            if (it.id == clipId) it.copy(speed = speed.coerceIn(0.25f, 4.0f)) else it
        }
        updateProjectInternal(current.copy(videoClips = updated))
    }

    fun setClipFilter(clipId: String, filterConfig: FilterConfig) {
        val current = _project.value ?: return
        val updated = current.videoClips.map {
            if (it.id == clipId) it.copy(filterConfig = filterConfig) else it
        }
        updateProjectInternal(current.copy(videoClips = updated))
    }

    fun setClipTransition(clipId: String, type: TransitionType) {
        val current = _project.value ?: return
        val updated = current.videoClips.map {
            if (it.id == clipId) it.copy(transitionType = type) else it
        }
        updateProjectInternal(current.copy(videoClips = updated))
        AppLog.i(TAG, "Updated transition for clip $clipId: $type")
    }

    fun setClipVolume(clipId: String, volume: Float) {
        val current = _project.value ?: return
        val updated = current.videoClips.map {
            if (it.id == clipId) it.copy(volume = volume.coerceIn(0f, 2f)) else it
        }
        updateProjectInternal(current.copy(videoClips = updated))
        AppLog.i(TAG, "Updated volume for clip $clipId: $volume")
    }

    fun deleteClip(clipId: String) {
        val current = _project.value ?: return
        val filtered = current.videoClips.filter { it.id != clipId }
        val updated = filtered.mapIndexed { index, clip ->
            if (index == 0 && clip.transitionType != TransitionType.NONE) {
                clip.copy(transitionType = TransitionType.NONE)
            } else clip
        }
        updateProjectInternal(current.copy(videoClips = updated))
        _selectedClipId.value = updated.firstOrNull()?.id
    }

    fun reorderClips(fromIndex: Int, toIndex: Int) {
        val current = _project.value ?: return
        val list = current.videoClips.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            val updated = list.mapIndexed { index, clip ->
                if (index == 0 && clip.transitionType != TransitionType.NONE) {
                    clip.copy(transitionType = TransitionType.NONE)
                } else clip
            }
            updateProjectInternal(current.copy(videoClips = updated))
        }
    }

    fun addOverlay(overlay: OverlayItem) {
        val current = _project.value ?: return
        val updated = current.overlays + overlay
        updateProjectInternal(current.copy(overlays = updated))
    }

    fun removeOverlay(overlayId: String) {
        val current = _project.value ?: return
        val updated = current.overlays.filter { it.id != overlayId }
        updateProjectInternal(current.copy(overlays = updated))
    }

    fun setVolumes(originalVideo: Float, backgroundMusic: Float) {
        val current = _project.value ?: return
        updateProjectInternal(
            current.copy(
                originalVideoVolume = originalVideo.coerceIn(0f, 2f),
                backgroundMusicVolume = backgroundMusic.coerceIn(0f, 2f)
            )
        )
    }

    private fun updateProjectInternal(updatedProject: Project) {
        val p = updatedProject.copy(lastModifiedTime = System.currentTimeMillis())
        _project.value = p
        playerManager.syncWithProject(p)
        previewRenderer.setProject(p)
        viewModelScope.launch {
            dao.updateProject(ProjectEntity.fromDomain(p))
        }
    }

    fun startExport(config: ExportConfig) {
        val proj = _project.value ?: return
        if (proj.videoClips.isEmpty()) {
            _exportState.value = ExportState.Error("Please add at least one video clip before exporting.")
            return
        }

        playerManager.pause()
        previewRenderer.pause()
        _exportState.value = ExportState.Progress(0f, "Starting export...")

        val engine = ExportEngine(getApplication(), proj, config)
        activeExportEngine = engine

        exportJob = viewModelScope.launch {
            val result = engine.export { progress, status ->
                _exportState.value = ExportState.Progress(progress, status)
            }
            result.fold(
                onSuccess = { exportResult ->
                    _exportState.value = ExportState.Success(
                        file = exportResult.file,
                        publicUri = exportResult.publicUri,
                        galleryPath = exportResult.galleryPath
                    )
                },
                onFailure = { error ->
                    _exportState.value = ExportState.Error(error.message ?: "Unknown export error")
                }
            )
            activeExportEngine = null
            previewRenderer.resume()
        }
    }

    fun cancelExport() {
        activeExportEngine?.cancel()
        exportJob?.cancel()
        _exportState.value = ExportState.Idle
        previewRenderer.resume()
    }

    fun dismissExportDialog() {
        _exportState.value = ExportState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
        previewRenderer.release()
    }

    companion object {
        private const val TAG = "EditorViewModel"
    }
}
