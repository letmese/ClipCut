package com.clipcut.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipcut.core.codec.ExportEngine
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.AspectRatio
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.BeatMarker
import com.clipcut.core.model.BlendMode
import com.clipcut.core.model.CropRect
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.FilterConfig
import com.clipcut.core.model.LutConfig
import com.clipcut.core.model.MaskConfig
import com.clipcut.core.model.MediaType
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.OverlayKeyframe
import com.clipcut.core.model.PiPTrackItem
import com.clipcut.core.model.Project
import com.clipcut.core.model.SpeedRamp
import com.clipcut.core.model.TimelineItem
import com.clipcut.core.model.TransitionType
import com.clipcut.core.player.GlPreviewRenderer
import com.clipcut.core.player.PreviewPlayerManager
import com.clipcut.data.db.ClipCutDatabase
import com.clipcut.data.db.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed class ExportState {
    object Idle : ExportState()
    data class Progress(val progress: Float, val status: String) : ExportState()
    data class Success(val file: File, val publicUri: android.net.Uri? = null, val galleryPath: String = "Movies/ClipCut") : ExportState()
    data class Error(val message: String) : ExportState()
}

/**
 * EditorViewModel — the central hub for editor mutations. Each mutation
 *   1. applies the change to project.value
 *   2. pushes a ProjectEntity to ClipCutDatabase (auto-save)
 *   3. keeps undo/redo stacks so canUndo/canRedo flow freshly
 *   4. re-syncs playerManager + previewRenderer with the new project
 *
 * All clip mutations funnel through mutateClip(clipId, block) so that
 * undo/redo, autosave, and player resync happen in one place.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ClipCutDatabase.getInstance(application)
    private val dao = db.projectDao()

    val playerManager: PreviewPlayerManager = PreviewPlayerManager(application)
    val previewRenderer: GlPreviewRenderer = GlPreviewRenderer(application)

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var currentExport: ExportEngine? = null

    fun loadProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val e = dao.getProjectById(projectId) ?: return@launch
            val p = e.toDomain()
            _project.value = p
            playerManager.syncWithProject(p)
            previewRenderer.setProject(p)
            AppLog.i(TAG, "loadProject $projectId clips=${p.videoClips.size}")
        }
    }

    fun selectClip(id: String?) { _selectedClipId.value = id }

    fun setProjectAspectRatio(aspectRatio: AspectRatio) = mutate { it.copy(aspectRatio = aspectRatio) }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _project.value ?: return
        redoStack.addLast(current)
        val prev = undoStack.removeLast()
        _project.value = prev
        pushAutoSave(prev)
        refreshDerived()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _project.value ?: return
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        _project.value = next
        pushAutoSave(next)
        refreshDerived()
    }

    fun importMedia(uri: Uri, mediaType: MediaType) = mutate { p ->
        val duration = mediaDurationUs(uri, mediaType)
        val clip = TimelineItem(uri = uri.toString(), mediaType = mediaType, sourceDurationUs = duration,
            trimStartUs = 0L, trimEndUs = duration, rotationDegrees = 0)
        AppLog.i(TAG, "importMedia ${uri} ${mediaType} dur=${duration}")
        p.copy(videoClips = p.videoClips + clip)
    }

    fun splitClipAtPlayhead() {
        val playheadUs = playerManager.currentPositionUs.value
        mutate { p ->
            val clips = p.videoClips.toMutableList()
            var acc = 0L
            for (i in clips.indices) {
                val c = clips[i]
                val local = playheadUs - acc
                if (local in 0L..c.effectiveDurationUs) {
                    val splitAt = c.trimStartUs + (local * c.speed).toLong()
                    val left = c.copy(trimEndUs = splitAt)
                    val right = c.copy(
                        id = UUID.randomUUID().toString(),
                        trimStartUs = splitAt,
                        trimEndUs = c.trimEndUs
                    )
                    clips[i] = left
                    clips.add(i + 1, right)
                    break
                }
                acc += c.effectiveDurationUs
            }
            p.copy(videoClips = clips)
        }
    }

    fun trimClip(clipId: String, s: Long, e: Long) = mutateClip(clipId) {
        it.copy(trimStartUs = s.coerceAtLeast(0L), trimEndUs = e.coerceAtLeast(s + 1L))
    }

    fun setClipSpeed(clipId: String, speed: Float) = mutateClip(clipId) { it.copy(speed = speed.coerceIn(0.1f, 10f)) }
    fun setClipFilter(clipId: String, filter: FilterConfig) = mutateClip(clipId) { it.copy(filterConfig = filter) }
    fun setClipTransition(clipId: String, type: TransitionType) = mutateClip(clipId) { it.copy(transitionType = type) }
    fun setClipVolume(clipId: String, vol: Float) = mutateClip(clipId) { it.copy(volume = vol.coerceIn(0f, 2f)) }
    fun setClipRotation(clipId: String, deg: Int) = mutateClip(clipId) { it.copy(rotationDegrees = ((deg % 360) + 360) % 360) }
    fun setClipFlipH(clipId: String, flip: Boolean) = mutateClip(clipId) { it.copy(flipHorizontal = flip) }
    fun setClipFlipV(clipId: String, flip: Boolean) = mutateClip(clipId) { it.copy(flipVertical = flip) }
    fun setClipCrop(clipId: String, crop: CropRect) = mutateClip(clipId) { it.copy(cropRect = crop) }
    fun setClipFade(clipId: String, fadeInMs: Int, fadeOutMs: Int) = mutateClip(clipId) { it.copy(fadeInMs = fadeInMs, fadeOutMs = fadeOutMs) }
    fun setClipSpeedRamp(clipId: String, ramp: SpeedRamp) = mutateClip(clipId) { it.copy(speedRamp = ramp) }
    fun setClipLut(clipId: String, lut: LutConfig?) = mutateClip(clipId) { it.copy(lutConfig = lut) }
    fun setClipChromaKey(clipId: String, color: FloatArray?, threshold: Float, smoothness: Float) = mutateClip(clipId) {
        it.copy(chromaKeyColor = color, chromaKeyThreshold = threshold, chromaKeySmoothness = smoothness)
    }
    fun setClipMask(clipId: String, mask: MaskConfig) = mutateClip(clipId) { it.copy(maskConfig = mask) }
    fun setClipBlendMode(clipId: String, mode: BlendMode) = mutateClip(clipId) { it.copy(blendMode = mode) }
    fun setClipReversed(clipId: String, reversed: Boolean) = mutateClip(clipId) { it.copy(isReversed = reversed) }
    fun setClipFreeze(clipId: String, freezeAtUs: Long?) = mutateClip(clipId) { it.copy(freezeAtUs = freezeAtUs) }

    /** Set a GLSL effect on the clip — see EffectRegistry for available IDs. */
    fun setClipEffect(clipId: String, effectId: String?, intensity: Float) =
        mutateClip(clipId) { it.copy(effectId = effectId, effectIntensity = intensity.coerceIn(0f, 1f)) }

    fun addBeatMarker(timeUs: Long) = mutate { p ->
        AppLog.d(TAG, "addBeatMarker ${timeUs}")
        p.copy(beatMarkers = p.beatMarkers + BeatMarker(timeUs = timeUs))
    }
    fun removeBeatMarker(id: String) = mutate { p -> p.copy(beatMarkers = p.beatMarkers.filterNot { it.id == id }) }
    fun clearBeatMarkers() = mutate { it.copy(beatMarkers = emptyList()) }
    fun setAudioDucking(enabled: Boolean, level: Float) = mutate {
        it.copy(audioDuckingEnabled = enabled, audioDuckLevel = level.coerceIn(0f, 1f))
    }

    fun deleteClip(clipId: String) = mutate { p -> p.copy(videoClips = p.videoClips.filterNot { it.id == clipId }) }
    fun reorderClips(from: Int, to: Int) = mutate { p ->
        if (from == to) return@mutate p
        val clips = p.videoClips.toMutableList()
        val moved = clips.removeAt(from)
        clips.add(to.coerceIn(0, clips.size), moved)
        p.copy(videoClips = clips)
    }

    fun addOverlay(o: OverlayItem) = mutate { it.copy(overlays = it.overlays + o) }
    fun removeOverlay(id: String) = mutate { it.copy(overlays = it.overlays.filterNot { o -> o.id == id }) }
    fun updateOverlay(id: String, block: (OverlayItem) -> OverlayItem) = mutate { p ->
        p.copy(overlays = p.overlays.map { if (it.id == id) block(it) else it })
    }
    fun addKeyframe(overlayId: String, kf: OverlayKeyframe) = mutate { p ->
        p.copy(overlays = p.overlays.map {
            if (it.id == overlayId) it.copy(keyframes = (it.keyframes + kf).sortedBy { k -> k.timeOffsetUs })
            else it
        })
    }
    fun removeKeyframe(overlayId: String, kfId: String) = mutate { p ->
        p.copy(overlays = p.overlays.map {
            if (it.id == overlayId) it.copy(keyframes = it.keyframes.filterNot { k -> k.id == kfId })
            else it
        })
    }

    fun addPipTrack(pip: PiPTrackItem) = mutate { it.copy(pipTracks = it.pipTracks + pip) }
    fun removePipTrack(id: String) = mutate { it.copy(pipTracks = it.pipTracks.filterNot { p -> p.id == id }) }
    fun updatePipTrack(id: String, block: (PiPTrackItem) -> PiPTrackItem) = mutate { p ->
        p.copy(pipTracks = p.pipTracks.map { if (it.id == id) block(it) else it })
    }

    fun setVolumes(orig: Float, music: Float) = mutate {
        it.copy(originalVideoVolume = orig.coerceIn(0f, 2f), backgroundMusicVolume = music.coerceIn(0f, 2f))
    }

    fun addAudioTrack(item: AudioTrackItem) = mutate { it.copy(audioTracks = it.audioTracks + item) }
    fun removeAudioTrack(id: String) = mutate { it.copy(audioTracks = it.audioTracks.filterNot { a -> a.id == id }) }

    fun startExport(config: ExportConfig) {
        val p = _project.value ?: return
        val ctx = getApplication<Application>()
        currentExport = ExportEngine(ctx, p, config).also { eng ->
            _exportState.value = ExportState.Progress(0f, "Starting")
            viewModelScope.launch {
                val res = eng.export { prog, status -> _exportState.value = ExportState.Progress(prog, status) }
                res.onSuccess { _exportState.value = ExportState.Success(it.file, it.publicUri, it.galleryPath) }
                    .onFailure { _exportState.value = ExportState.Error(it.message ?: "Unknown") }
                currentExport = null
            }
        }
    }

    fun cancelExport() { currentExport?.cancel(); _exportState.value = ExportState.Idle }
    fun dismissExportDialog() { _exportState.value = ExportState.Idle }

    // ── Internals ──
    private fun mutate(block: (Project) -> Project) {
        val current = _project.value ?: return
        undoStack.addLast(current)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        val next = block(current).copy(lastModifiedTime = System.currentTimeMillis())
        _project.value = next
        pushAutoSave(next)
        refreshDerived()
    }

    private fun mutateClip(clipId: String, block: (TimelineItem) -> TimelineItem) {
        mutate { p ->
            p.copy(videoClips = p.videoClips.map { if (it.id == clipId) block(it) else it })
        }
    }

    private fun refreshDerived() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _project.value?.let {
            playerManager.syncWithProject(it)
            previewRenderer.setProject(it)
        }
    }

    private fun pushAutoSave(project: Project) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateProject(ProjectEntity.fromDomain(project))
        }
    }

    private fun mediaDurationUs(uri: Uri, mediaType: MediaType): Long {
        return try {
            val ctx = getApplication<Application>()
            val ext = android.media.MediaExtractor().apply { setDataSource(ctx, uri, null) }
            for (i in 0 until ext.trackCount) {
                val fmt = ext.getTrackFormat(i)
                if (fmt.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                    val d = fmt.getLong(android.media.MediaFormat.KEY_DURATION)
                    ext.release(); return d
                }
            }
            ext.release(); 5_000_000L
        } catch (t: Throwable) {
            AppLog.w(TAG, "mediaDurationUs fallback: ${t.message}"); 5_000_000L
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
        previewRenderer.release()
        currentExport?.cancel()
    }

    companion object { private const val TAG = "EditorViewModel" }
}
