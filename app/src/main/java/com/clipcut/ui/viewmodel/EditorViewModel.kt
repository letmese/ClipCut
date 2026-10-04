package com.clipcut.ui.viewmodel

import android.app.Application
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipcut.core.codec.ExportEngine
import com.clipcut.core.codec.SpeedRampProcessor
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

    init {
        playerManager.onSeek = { targetUs ->
            previewRenderer.seekTo(targetUs)
        }
    }

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    // Undo / redo stacks
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private val MAX_HISTORY = 30

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var activeExportEngine: ExportEngine? = null
    private var exportJob: Job? = null

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val entity = dao.getProjectById(projectId)
            if (entity != null) {
                val proj = entity.toDomain()
                _project.value = proj
                undoStack.clear(); redoStack.clear()
                updateUndoRedoFlags()
                _selectedClipId.value = proj.videoClips.firstOrNull()?.id
                playerManager.syncWithProject(proj)
                previewRenderer.setProject(proj)
            } else {
                AppLog.e(TAG, "Project not found with id $projectId")
            }
        }
    }

    fun selectClip(id: String?) { _selectedClipId.value = id }

    fun setProjectAspectRatio(aspectRatio: AspectRatio) {
        val current = _project.value ?: return
        if (current.aspectRatio == aspectRatio) return
        applyEdit(current.copy(aspectRatio = aspectRatio))
        AppLog.i(TAG, "Set project aspect ratio to ${aspectRatio.label}")
    }

    // -------------------- UNDO / REDO --------------------

    private fun pushHistory(project: Project) {
        undoStack.addLast(project)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        updateUndoRedoFlags()
    }

    private fun updateUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        val current = _project.value ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        commitInternal(prev)
        updateUndoRedoFlags()
        AppLog.i(TAG, "Undo (stack: ${undoStack.size}, redo: ${redoStack.size})")
    }

    fun redo() {
        val current = _project.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        commitInternal(next)
        updateUndoRedoFlags()
        AppLog.i(TAG, "Redo (stack: ${undoStack.size}, redo: ${redoStack.size})")
    }

    // -------------------- MEDIA --------------------

    fun importMedia(uri: Uri, mediaType: MediaType) {
        try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {}

        val current = _project.value ?: return
        viewModelScope.launch {
            if (mediaType == MediaType.IMAGE) {
                val dur = 5_000_000L
                val newClip = TimelineItem(
                    uri = uri.toString(), mediaType = MediaType.IMAGE,
                    sourceDurationUs = dur, trimStartUs = 0L, trimEndUs = dur
                )
                val newProject = if (current.videoClips.isEmpty()) {
                    val detectedRatio = try {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, opts)
                        }
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            val r = opts.outWidth.toFloat() / opts.outHeight.toFloat()
                            when {
                                r < 0.65f -> AspectRatio.R_9_16
                                r in 0.7f..0.88f -> AspectRatio.R_4_5
                                r in 0.9f..1.1f -> AspectRatio.R_1_1
                                r in 1.25f..1.4f -> AspectRatio.R_4_3
                                r > 2.0f -> AspectRatio.R_21_9
                                else -> AspectRatio.R_16_9
                            }
                        } else null
                    } catch (_: Throwable) { null }
                    if (detectedRatio != null) current.copy(aspectRatio = detectedRatio, videoClips = current.videoClips + newClip)
                    else current.copy(videoClips = current.videoClips + newClip)
                } else {
                    current.copy(videoClips = current.videoClips + newClip)
                }
                applyEdit(newProject)
                _selectedClipId.value = newClip.id
                return@launch
            }
            try {
                val retriever = MediaMetadataRetriever()
                var detectedRatio: AspectRatio? = null
                val durationMs = try {
                    retriever.setDataSource(getApplication(), uri)
                    val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    if (w > 0 && h > 0) {
                        val effW = if (rot == 90 || rot == 270) h else w
                        val effH = if (rot == 90 || rot == 270) w else h
                        val r = effW.toFloat() / effH.toFloat()
                        detectedRatio = when {
                            r < 0.65f -> AspectRatio.R_9_16
                            r in 0.7f..0.88f -> AspectRatio.R_4_5
                            r in 0.9f..1.1f -> AspectRatio.R_1_1
                            r in 1.25f..1.4f -> AspectRatio.R_4_3
                            r > 2.0f -> AspectRatio.R_21_9
                            else -> AspectRatio.R_16_9
                        }
                    }
                    retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 5000L
                } finally {
                    try { retriever.release() } catch (_: Throwable) {}
                }
                val durationUs = durationMs * 1000L

                if (mediaType == MediaType.VIDEO) {
                    val newClip = TimelineItem(
                        uri = uri.toString(), mediaType = mediaType,
                        sourceDurationUs = durationUs, trimStartUs = 0L, trimEndUs = durationUs
                    )
                    val newProject = if (current.videoClips.isEmpty() && detectedRatio != null) {
                        current.copy(aspectRatio = detectedRatio, videoClips = current.videoClips + newClip)
                    } else {
                        current.copy(videoClips = current.videoClips + newClip)
                    }
                    applyEdit(newProject)
                    _selectedClipId.value = newClip.id
                } else if (mediaType == MediaType.AUDIO) {
                    val newTrack = AudioTrackItem(
                        uri = uri.toString(),
                        name = uri.lastPathSegment ?: "Audio Track",
                        sourceDurationUs = durationUs,
                        timelineStartUs = 0L, trimStartUs = 0L, trimEndUs = durationUs
                    )
                    applyEdit(current.copy(audioTracks = current.audioTracks + newTrack))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to import media from $uri", e)
            }
        }
    }

    // -------------------- EDIT OPS --------------------

    fun splitClipAtPlayhead() {
        val current = _project.value ?: return
        val playheadUs = playerManager.currentPositionUs.value
        val totalDurUs = current.totalDurationUs
        if (current.videoClips.isEmpty()) return
        if (playheadUs <= 50_000L || playheadUs >= totalDurUs - 50_000L) return

        var acc = 0L
        val updated = mutableListOf<TimelineItem>()
        var didSplit = false
        for (clip in current.videoClips) {
            val dur = clip.effectiveDurationUs
            val end = acc + dur
            if (!didSplit && playheadUs > acc + 30_000L && playheadUs < end - 30_000L) {
                val relTimeline = playheadUs - acc
                val relSource = if (clip.speedRamp.isActive) {
                    SpeedRampProcessor.timelineToSource(relTimeline, clip.speedRamp, clip.speed)
                } else {
                    (relTimeline * clip.speed).toLong()
                }
                val splitSource = clip.trimStartUs + relSource
                updated.add(clip.copy(id = java.util.UUID.randomUUID().toString(), trimEndUs = splitSource))
                val c2 = clip.copy(id = java.util.UUID.randomUUID().toString(), trimStartUs = splitSource)
                updated.add(c2)
                _selectedClipId.value = c2.id
                didSplit = true
            } else updated.add(clip)
            acc += dur
        }
        if (didSplit) applyEdit(current.copy(videoClips = updated))
    }

    fun trimClip(clipId: String, s: Long, e: Long) {
        val c = _project.value ?: return
        val updated = c.videoClips.map {
            if (it.id == clipId) it.copy(
                trimStartUs = s.coerceIn(0L, it.sourceDurationUs),
                trimEndUs = e.coerceIn(s, it.sourceDurationUs)
            ) else it
        }
        applyEdit(c.copy(videoClips = updated))
    }

    fun setClipSpeed(clipId: String, speed: Float) = updateClip(clipId) {
        it.copy(speed = speed.coerceIn(0.25f, 4.0f))
    }

    fun setClipFilter(clipId: String, filter: FilterConfig) = updateClip(clipId) {
        it.copy(filterConfig = filter)
    }

    fun setClipTransition(clipId: String, type: TransitionType) = updateClip(clipId) {
        it.copy(transitionType = type)
    }

    fun setClipVolume(clipId: String, vol: Float) = updateClip(clipId) {
        it.copy(volume = vol.coerceIn(0f, 2f))
    }

    fun setClipRotation(clipId: String, deg: Int) = updateClip(clipId) {
        it.copy(rotationDegrees = ((deg % 360) + 360) % 360)
    }

    fun setClipFlipH(clipId: String, flip: Boolean) = updateClip(clipId) {
        it.copy(flipHorizontal = flip)
    }

    fun setClipFlipV(clipId: String, flip: Boolean) = updateClip(clipId) {
        it.copy(flipVertical = flip)
    }

    fun setClipCrop(clipId: String, crop: CropRect) = updateClip(clipId) {
        it.copy(cropRect = crop)
    }

    fun setClipFade(clipId: String, fadeInMs: Int, fadeOutMs: Int) = updateClip(clipId) {
        it.copy(
            fadeInMs = fadeInMs.coerceIn(0, 10_000),
            fadeOutMs = fadeOutMs.coerceIn(0, 10_000)
        )
    }

    fun setClipSpeedRamp(clipId: String, ramp: SpeedRamp) = updateClip(clipId) {
        it.copy(speedRamp = ramp)
    }

    fun setClipLut(clipId: String, lut: LutConfig?) = updateClip(clipId) {
        it.copy(lutConfig = lut)
    }

    fun setClipChromaKey(clipId: String, color: FloatArray?, threshold: Float, smoothness: Float) = updateClip(clipId) {
        it.copy(chromaKeyColor = color, chromaKeyThreshold = threshold, chromaKeySmoothness = smoothness)
    }

    fun setClipMask(clipId: String, mask: MaskConfig) = updateClip(clipId) {
        it.copy(maskConfig = mask)
    }

    fun setClipBlendMode(clipId: String, mode: BlendMode) = updateClip(clipId) {
        it.copy(blendMode = mode)
    }

    fun setClipReversed(clipId: String, reversed: Boolean) = updateClip(clipId) {
        it.copy(isReversed = reversed)
    }

    fun setClipFreeze(clipId: String, freezeAtUs: Long?) = updateClip(clipId) {
        it.copy(freezeAtUs = freezeAtUs)
    }

    fun addBeatMarker(timeUs: Long) {
        val c = _project.value ?: return
        applyEdit(c.copy(beatMarkers = (c.beatMarkers + BeatMarker(timeUs = timeUs)).sortedBy { it.timeUs }))
    }

    fun removeBeatMarker(id: String) {
        val c = _project.value ?: return
        applyEdit(c.copy(beatMarkers = c.beatMarkers.filter { it.id != id }))
    }

    fun clearBeatMarkers() {
        val c = _project.value ?: return
        applyEdit(c.copy(beatMarkers = emptyList()))
    }

    fun setAudioDucking(enabled: Boolean, level: Float) {
        val c = _project.value ?: return
        applyEdit(c.copy(audioDuckingEnabled = enabled, audioDuckLevel = level.coerceIn(0.1f, 1.0f)))
    }

    private fun updateClip(clipId: String, block: (TimelineItem) -> TimelineItem) {
        val c = _project.value ?: return
        val updated = c.videoClips.map { if (it.id == clipId) block(it) else it }
        applyEdit(c.copy(videoClips = updated))
    }

    fun deleteClip(clipId: String) {
        val c = _project.value ?: return
        val filtered = c.videoClips.filter { it.id != clipId }
        val normalized = filtered.mapIndexed { i, clip ->
            if (i == 0 && clip.transitionType != TransitionType.NONE)
                clip.copy(transitionType = TransitionType.NONE) else clip
        }
        applyEdit(c.copy(videoClips = normalized))
        _selectedClipId.value = normalized.firstOrNull()?.id
    }

    fun reorderClips(from: Int, to: Int) {
        val c = _project.value ?: return
        val list = c.videoClips.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            val normalized = list.mapIndexed { i, clip ->
                if (i == 0 && clip.transitionType != TransitionType.NONE)
                    clip.copy(transitionType = TransitionType.NONE) else clip
            }
            applyEdit(c.copy(videoClips = normalized))
        }
    }

    // -------------------- OVERLAYS --------------------

    fun addOverlay(o: OverlayItem) {
        val c = _project.value ?: return
        applyEdit(c.copy(overlays = c.overlays + o))
    }

    fun removeOverlay(id: String) {
        val c = _project.value ?: return
        applyEdit(c.copy(overlays = c.overlays.filter { it.id != id }))
    }

    fun updateOverlay(id: String, block: (OverlayItem) -> OverlayItem) {
        val c = _project.value ?: return
        applyEdit(c.copy(overlays = c.overlays.map { if (it.id == id) block(it) else it }))
    }

    fun addKeyframe(overlayId: String, kf: OverlayKeyframe) {
        updateOverlay(overlayId) { it.copy(keyframes = (it.keyframes + kf).sortedBy { k -> k.timeOffsetUs }) }
    }

    fun removeKeyframe(overlayId: String, kfId: String) {
        updateOverlay(overlayId) { it.copy(keyframes = it.keyframes.filter { k -> k.id != kfId }) }
    }

    // -------------------- PIP --------------------

    fun addPipTrack(pip: PiPTrackItem) {
        val c = _project.value ?: return
        applyEdit(c.copy(pipTracks = c.pipTracks + pip))
    }

    fun removePipTrack(id: String) {
        val c = _project.value ?: return
        applyEdit(c.copy(pipTracks = c.pipTracks.filter { it.id != id }))
    }

    fun updatePipTrack(id: String, block: (PiPTrackItem) -> PiPTrackItem) {
        val c = _project.value ?: return
        applyEdit(c.copy(pipTracks = c.pipTracks.map { if (it.id == id) block(it) else it }))
    }

    fun setVolumes(orig: Float, music: Float) {
        val c = _project.value ?: return
        applyEdit(c.copy(
            originalVideoVolume = orig.coerceIn(0f, 2f),
            backgroundMusicVolume = music.coerceIn(0f, 2f)
        ))
    }

    // -------------------- APPLY --------------------

    private fun applyEdit(newProject: Project) {
        val current = _project.value
        if (current != null && current != newProject) pushHistory(current)
        commitInternal(newProject)
    }

    private fun commitInternal(newProject: Project) {
        val p = newProject.copy(lastModifiedTime = System.currentTimeMillis())
        _project.value = p
        val currentSelected = _selectedClipId.value
        if (currentSelected != null && p.videoClips.none { it.id == currentSelected }) {
            _selectedClipId.value = p.videoClips.firstOrNull()?.id
        }
        playerManager.syncWithProject(p)
        previewRenderer.setProject(p)
        viewModelScope.launch { dao.updateProject(ProjectEntity.fromDomain(p)) }
    }

    // -------------------- EXPORT --------------------

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
                onSuccess = { r ->
                    _exportState.value = ExportState.Success(
                        file = r.file, publicUri = r.publicUri, galleryPath = r.galleryPath
                    )
                },
                onFailure = { e ->
                    _exportState.value = ExportState.Error(e.message ?: "Unknown export error")
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

    fun dismissExportDialog() { _exportState.value = ExportState.Idle }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
        previewRenderer.release()
    }

    companion object { private const val TAG = "EditorViewModel" }
}
