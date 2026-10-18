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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    init {
        previewRenderer.playerManager = playerManager
        previewRenderer.positionProvider = { playerManager.currentPositionUs.value }
    }

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _trackingState = MutableStateFlow<String?>(null)
    val trackingState: StateFlow<String?> = _trackingState.asStateFlow()

    var equalizerGains by mutableStateOf<List<Float>>(emptyList())
    var voicePreset by mutableStateOf(com.clipcut.core.audio.AudioDsp.VoicePreset.NATURAL)
    var brandKitConfig by mutableStateOf<com.clipcut.core.model.BrandKitConfig?>(null)

    fun updateEqualizerGains(gains: List<Float>) {
        equalizerGains = gains
        playerManager.setEqualizerGains(gains)
    }

    fun updateVoicePreset(preset: com.clipcut.core.audio.AudioDsp.VoicePreset) {
        voicePreset = preset
        playerManager.setVoicePreset(preset)
    }

    fun applyBrandKit(config: com.clipcut.core.model.BrandKitConfig?) {
        brandKitConfig = config
        previewRenderer.brandKitConfig = config
        previewRenderer.isDirty = true
        mutate { p ->
            val filtered = p.overlays.filterNot { it.id == "brand_kit_watermark" }
            p.copy(overlays = filtered, brandKitConfig = config)
        }
    }

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var currentExport: ExportEngine? = null

    fun loadProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val e = dao.getProjectById(projectId) ?: return@launch
                val p = e.toDomain()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _project.value = p
                    playerManager.syncWithProject(p)
                    previewRenderer.setProject(p)
                    brandKitConfig = p.brandKitConfig
                    previewRenderer.brandKitConfig = p.brandKitConfig
                }
                AppLog.i(TAG, "loadProject $projectId clips=${p.videoClips.size}")
            } catch (t: Throwable) {
                AppLog.e(TAG, "loadProject failed for $projectId", t)
            }
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

    fun importMedia(uri: Uri, mediaType: MediaType) {
        viewModelScope.launch(Dispatchers.IO) {
            val (duration, rotation) = if (mediaType == MediaType.VIDEO) extractMediaInfo(uri) else Pair(5_000_000L, 0)
            val clip = TimelineItem(
                uri = uri.toString(),
                mediaType = mediaType,
                sourceDurationUs = duration,
                trimStartUs = 0L,
                trimEndUs = duration,
                rotationDegrees = rotation
            )
            AppLog.i(TAG, "importMedia ${uri} ${mediaType} dur=${duration} rot=${rotation}")
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                mutate { p -> p.copy(videoClips = p.videoClips + clip) }
            }
        }
    }

    fun splitClipAtPlayhead() {
        val playheadUs = playerManager.currentPositionUs.value
        var newSelectedId: String? = null
        mutate { p ->
            val clips = p.videoClips.toMutableList()
            var acc = 0L
            val minBoundaryUs = 150_000L // 150ms buffer from clip start/end
            for (i in clips.indices) {
                val c = clips[i]
                val local = playheadUs - acc
                if (local in minBoundaryUs..(c.effectiveDurationUs - minBoundaryUs)) {
                    val splitAt = c.trimStartUs + (local * c.speed).toLong()
                    val left = c.copy(trimEndUs = splitAt)
                    val newRightId = UUID.randomUUID().toString()
                    val right = c.copy(
                        id = newRightId,
                        trimStartUs = splitAt,
                        trimEndUs = c.trimEndUs,
                        transitionType = TransitionType.NONE
                    )
                    clips[i] = left
                    clips.add(i + 1, right)
                    newSelectedId = newRightId
                    break
                }
                acc += c.effectiveDurationUs
            }
            p.copy(videoClips = clips)
        }
        if (newSelectedId != null) {
            _selectedClipId.value = newSelectedId
        }
    }

    fun trimClip(clipId: String, s: Long, e: Long) = mutateClip(clipId) {
        it.copy(trimStartUs = s.coerceAtLeast(0L), trimEndUs = e.coerceAtLeast(s + 1L))
    }

    fun setClipSpeed(clipId: String, speed: Float) = mutateClip(clipId) { it.copy(speed = speed.coerceIn(0.1f, 10f)) }
    fun setClipFilter(clipId: String, filter: FilterConfig) = mutateClip(clipId) { it.copy(filterConfig = filter) }
    fun setClipTransition(clipId: String, type: TransitionType, durationUs: Long = 500_000L) = mutateClip(clipId) { it.copy(transitionType = type, transitionDurationUs = durationUs) }
    fun setAllClipsTransition(type: TransitionType, durationUs: Long = 500_000L) {
        mutate { p ->
            val updated = p.videoClips.mapIndexed { idx, clip ->
                if (idx == 0) clip.copy(transitionType = TransitionType.NONE)
                else clip.copy(transitionType = type, transitionDurationUs = durationUs)
            }
            p.copy(videoClips = updated)
        }
    }
    fun setClipVolume(clipId: String, vol: Float) = mutateClip(clipId) { it.copy(volume = vol.coerceIn(0f, 2f)) }
    fun setClipRotation(clipId: String, deg: Int) = mutateClip(clipId) { it.copy(rotationDegrees = ((deg % 360) + 360) % 360) }
    fun setClipFlipH(clipId: String, flip: Boolean) = mutateClip(clipId) { it.copy(flipHorizontal = flip) }
    fun setClipFlipV(clipId: String, flip: Boolean) = mutateClip(clipId) { it.copy(flipVertical = flip) }
    fun setClipCrop(clipId: String, crop: CropRect) = mutateClip(clipId) { it.copy(cropRect = crop) }
    fun setClipFade(clipId: String, fadeInMs: Int, fadeOutMs: Int) = mutateClip(clipId) { it.copy(fadeInMs = fadeInMs, fadeOutMs = fadeOutMs) }
    fun setClipSpeedRamp(clipId: String, ramp: SpeedRamp) {
        val avgSpeed = if (ramp.isActive) ramp.keyframes.map { it.speed }.average().toFloat().coerceIn(0.1f, 10f) else 1.0f
        mutateClip(clipId) { it.copy(speedRamp = ramp, speed = avgSpeed) }
    }
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
    fun trimOverlay(id: String, startUs: Long, endUs: Long) = mutate { p ->
        p.copy(overlays = p.overlays.map { if (it.id == id) it.copy(timelineStartUs = startUs, timelineEndUs = endUs) else it })
    }
    fun updateOverlay(id: String, block: (OverlayItem) -> OverlayItem) = mutate { p ->
        p.copy(overlays = p.overlays.map { if (it.id == id) block(it) else it })
    }
    fun commitOverlayTransform(id: String, normX: Float, normY: Float, scale: Float, rotation: Float) = mutate { p ->
        p.copy(overlays = p.overlays.map {
            if (it.id == id) it.copy(
                normalizedX = normX.coerceIn(0.01f, 0.99f),
                normalizedY = normY.coerceIn(0.01f, 0.99f),
                scale = scale.coerceIn(0.2f, 5.0f),
                rotation = ((rotation % 360f) + 360f) % 360f
            ) else it
        })
    }
    fun setOverlayCrop(id: String, crop: CropRect?) = mutate { p ->
        p.copy(overlays = p.overlays.map {
            if (it.id == id) it.copy(cropRect = crop) else it
        })
    }
    fun duplicateOverlay(id: String) = mutate { p ->
        val orig = p.overlays.find { it.id == id } ?: return@mutate p
        val dup = orig.copy(
            id = UUID.randomUUID().toString(),
            normalizedX = (orig.normalizedX + 0.05f).coerceIn(0.05f, 0.95f),
            normalizedY = (orig.normalizedY + 0.05f).coerceIn(0.05f, 0.95f)
        )
        p.copy(overlays = p.overlays + dup)
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
    fun trimPipTrack(id: String, startUs: Long, endUs: Long) = mutate { p ->
        p.copy(pipTracks = p.pipTracks.map {
            if (it.id == id) {
                val s = startUs.coerceAtLeast(0L)
                val e = endUs.coerceAtLeast(s + 200_000L)
                it.copy(timelineStartUs = s, timelineEndUs = e)
            } else it
        })
    }
    fun updatePipTrack(id: String, block: (PiPTrackItem) -> PiPTrackItem) = mutate { p ->
        p.copy(pipTracks = p.pipTracks.map { if (it.id == id) block(it) else it })
    }
    fun commitPipTransform(id: String, normX: Float, normY: Float, scale: Float, rotation: Float = 0.0f) = mutate { p ->
        p.copy(pipTracks = p.pipTracks.map {
            if (it.id == id) it.copy(
                normalizedX = normX.coerceIn(0.01f, 0.99f),
                normalizedY = normY.coerceIn(0.01f, 0.99f),
                scale = scale.coerceIn(0.1f, 3.0f),
                rotation = (rotation % 360f + 360f) % 360f
            ) else it
        })
    }
    fun duplicatePipTrack(id: String) = mutate { p ->
        val orig = p.pipTracks.find { it.id == id } ?: return@mutate p
        val dup = orig.copy(
            id = UUID.randomUUID().toString(),
            normalizedX = (orig.normalizedX + 0.05f).coerceIn(0.05f, 0.95f),
            normalizedY = (orig.normalizedY + 0.05f).coerceIn(0.05f, 0.95f)
        )
        p.copy(pipTracks = p.pipTracks + dup)
    }

    fun setPipVolume(id: String, volume: Float) = mutate { p ->
        p.copy(pipTracks = p.pipTracks.map { if (it.id == id) it.copy(volume = volume.coerceIn(0f, 1f)) else it })
    }

    fun addBlurTrack(item: com.clipcut.core.model.BlurTrackItem) = mutate { it.copy(blurTracks = it.blurTracks + item) }
    fun removeBlurTrack(id: String) = mutate { it.copy(blurTracks = it.blurTracks.filterNot { b -> b.id == id }) }
    fun trimBlurTrack(id: String, startUs: Long, endUs: Long) = mutate { p ->
        p.copy(blurTracks = p.blurTracks.map {
            if (it.id == id) {
                val s = startUs.coerceAtLeast(0L)
                val e = endUs.coerceAtLeast(s + 200_000L)
                it.copy(timelineStartUs = s, timelineEndUs = e)
            } else it
        })
    }
    fun updateBlurTrack(id: String, block: (com.clipcut.core.model.BlurTrackItem) -> com.clipcut.core.model.BlurTrackItem) = mutate { p ->
        p.copy(blurTracks = p.blurTracks.map { old ->
            if (old.id == id) {
                val next = block(old)
                if (old.keyframes.isNotEmpty() && (next.width != old.width || next.height != old.height)) {
                    val sw = next.width / old.width.coerceAtLeast(0.01f)
                    val sh = next.height / old.height.coerceAtLeast(0.01f)
                    next.copy(
                        keyframes = old.keyframes.map { kf ->
                            kf.copy(
                                width = (kf.width * sw).coerceIn(0.03f, 1.2f),
                                height = (kf.height * sh).coerceIn(0.03f, 1.2f)
                            )
                        }
                    )
                } else next
            } else old
        })
    }
    fun updateBlurTrack(item: com.clipcut.core.model.BlurTrackItem) = updateBlurTrack(item.id) { item }

    fun commitBlurTransform(
        id: String,
        normX: Float,
        normY: Float,
        width: Float,
        height: Float,
        rotation: Float = 0.0f,
        autoKeyframeAtPlayhead: Boolean = false
    ) = mutate { p ->
        p.copy(blurTracks = p.blurTracks.map { old ->
            if (old.id == id) {
                if (autoKeyframeAtPlayhead || old.keyframes.isNotEmpty()) {
                    // Manual keyframe mode: update or insert keyframe at current playhead position
                    val playheadUs = playerManager.currentPositionUs.value
                    val offsetUs = (playheadUs - old.timelineStartUs).coerceIn(0L, old.durationUs)
                    val filtered = old.keyframes.filterNot { kotlin.math.abs(it.timeOffsetUs - offsetUs) < 25_000L }
                    val newKf = com.clipcut.core.model.BlurKeyframe(
                        timeOffsetUs = offsetUs,
                        normalizedX = normX.coerceIn(0.01f, 0.99f),
                        normalizedY = normY.coerceIn(0.01f, 0.99f),
                        width = width.coerceIn(0.03f, 1.2f),
                        height = height.coerceIn(0.03f, 1.2f),
                        rotation = (rotation % 360f + 360f) % 360f
                    )
                    val updatedKfs = (filtered + newKf).sortedBy { it.timeOffsetUs }
                    old.copy(
                        normalizedX = normX.coerceIn(0.01f, 0.99f),
                        normalizedY = normY.coerceIn(0.01f, 0.99f),
                        width = width.coerceIn(0.03f, 1.2f),
                        height = height.coerceIn(0.03f, 1.2f),
                        rotation = (rotation % 360f + 360f) % 360f,
                        keyframes = updatedKfs
                    )
                } else {
                    old.copy(
                        normalizedX = normX.coerceIn(0.01f, 0.99f),
                        normalizedY = normY.coerceIn(0.01f, 0.99f),
                        width = width.coerceIn(0.03f, 1.2f),
                        height = height.coerceIn(0.03f, 1.2f),
                        rotation = (rotation % 360f + 360f) % 360f
                    )
                }
            } else old
        })
    }

    fun setBlurKeyframeAtPlayhead(
        blurId: String,
        playheadUs: Long = playerManager.currentPositionUs.value,
        normX: Float? = null,
        normY: Float? = null,
        width: Float? = null,
        height: Float? = null,
        rotation: Float? = null
    ) {
        val proj = _project.value ?: return
        val blur = proj.blurTracks.find { it.id == blurId } ?: return
        val offsetUs = (playheadUs - blur.timelineStartUs).coerceIn(0L, blur.durationUs)

        val cur = blur.getTransformAt(playheadUs)
        val finalX = normX ?: cur.normalizedX
        val finalY = normY ?: cur.normalizedY
        val finalW = width ?: cur.width
        val finalH = height ?: cur.height
        val finalRot = rotation ?: cur.rotation

        val filtered = blur.keyframes.filterNot { kotlin.math.abs(it.timeOffsetUs - offsetUs) < 25_000L }
        val newKf = com.clipcut.core.model.BlurKeyframe(
            timeOffsetUs = offsetUs,
            normalizedX = finalX.coerceIn(0.01f, 0.99f),
            normalizedY = finalY.coerceIn(0.01f, 0.99f),
            width = finalW.coerceIn(0.03f, 1.2f),
            height = finalH.coerceIn(0.03f, 1.2f),
            rotation = (finalRot % 360f + 360f) % 360f
        )
        val updated = (filtered + newKf).sortedBy { it.timeOffsetUs }
        updateBlurTrack(blurId) { it.copy(keyframes = updated) }
    }

    fun removeBlurKeyframeAtPlayhead(
        blurId: String,
        playheadUs: Long = playerManager.currentPositionUs.value
    ) {
        val proj = _project.value ?: return
        val blur = proj.blurTracks.find { it.id == blurId } ?: return
        val offsetUs = (playheadUs - blur.timelineStartUs).coerceIn(0L, blur.durationUs)
        val updated = blur.keyframes.filterNot { kotlin.math.abs(it.timeOffsetUs - offsetUs) < 35_000L }
        updateBlurTrack(blurId) { it.copy(keyframes = updated) }
    }

    fun stepPlayheadByFrames(framesDelta: Int, fps: Int = 30) {
        val frameDurationUs = 1_000_000L / fps
        val currentUs = playerManager.currentPositionUs.value
        val totalDur = _project.value?.totalDurationUs ?: 5_000_000L
        val targetUs = (currentUs + framesDelta * frameDurationUs).coerceIn(0L, totalDur)
        playerManager.seekToUs(targetUs)
    }

    fun seekToPrevBlurKeyframe(blurId: String) {
        val proj = _project.value ?: return
        val blur = proj.blurTracks.find { it.id == blurId } ?: return
        val currentOffsetUs = playerManager.currentPositionUs.value - blur.timelineStartUs
        val prevKf = blur.keyframes.filter { it.timeOffsetUs < currentOffsetUs - 20_000L }.maxByOrNull { it.timeOffsetUs }
        if (prevKf != null) {
            playerManager.seekToUs((blur.timelineStartUs + prevKf.timeOffsetUs).coerceAtLeast(0L))
        }
    }

    fun seekToNextBlurKeyframe(blurId: String) {
        val proj = _project.value ?: return
        val blur = proj.blurTracks.find { it.id == blurId } ?: return
        val currentOffsetUs = playerManager.currentPositionUs.value - blur.timelineStartUs
        val nextKf = blur.keyframes.filter { it.timeOffsetUs > currentOffsetUs + 20_000L }.minByOrNull { it.timeOffsetUs }
        if (nextKf != null) {
            playerManager.seekToUs((blur.timelineStartUs + nextKf.timeOffsetUs).coerceAtLeast(0L))
        }
    }

    fun nudgeBlurPosition(blurId: String, dx: Float, dy: Float) {
        val proj = _project.value ?: return
        val blur = proj.blurTracks.find { it.id == blurId } ?: return
        val playheadUs = playerManager.currentPositionUs.value
        val cur = blur.getTransformAt(playheadUs)
        val newX = (cur.normalizedX + dx).coerceIn(0.01f, 0.99f)
        val newY = (cur.normalizedY + dy).coerceIn(0.01f, 0.99f)
        commitBlurTransform(blurId, newX, newY, cur.width, cur.height, cur.rotation, autoKeyframeAtPlayhead = true)
    }

    fun nudgeBlurSize(blurId: String, dw: Float, dh: Float) {
        val proj = _project.value ?: return
        val blur = proj.blurTracks.find { it.id == blurId } ?: return
        val playheadUs = playerManager.currentPositionUs.value
        val cur = blur.getTransformAt(playheadUs)
        val newW = (cur.width + dw).coerceIn(0.04f, 1.0f)
        val newH = (cur.height + dh).coerceIn(0.04f, 1.0f)
        commitBlurTransform(blurId, cur.normalizedX, cur.normalizedY, newW, newH, cur.rotation, autoKeyframeAtPlayhead = true)
    }

    fun clearBlurTracking(id: String) = updateBlurTrack(id) { it.copy(keyframes = emptyList()) }

    fun startMotionTracking(
        blurId: String,
        mode: com.clipcut.core.feature.TrackingMode,
        anchorPtsUs: Long = playerManager.currentPositionUs.value,
        onFinished: (Boolean) -> Unit = {}
    ) {
        val proj = _project.value ?: return
        val blur = proj.blurTracks.find { it.id == blurId } ?: return
        val mainClip = proj.videoClips.firstOrNull() ?: return
        val tracker = com.clipcut.core.feature.MotionTracker(getApplication())

        _trackingState.value = "Locking target at playhead..."
        viewModelScope.launch {
            try {
                val currentTransform = blur.getTransformAt(anchorPtsUs)
                val kfs = tracker.track(
                    videoUri = mainClip.uri,
                    initialX = currentTransform.normalizedX,
                    initialY = currentTransform.normalizedY,
                    initialW = currentTransform.width,
                    initialH = currentTransform.height,
                    initialRotation = currentTransform.rotation,
                    startTimeUs = blur.timelineStartUs,
                    endTimeUs = blur.timelineEndUs,
                    anchorPtsUs = anchorPtsUs.coerceIn(blur.timelineStartUs, blur.timelineEndUs),
                    mode = mode,
                    onProgress = { prog ->
                        val pct = (prog * 100).toInt()
                        _trackingState.value = "Tracking target ($pct%)..."
                    }
                )

                if (kfs.isNotEmpty()) {
                    updateBlurTrack(blurId) { it.copy(keyframes = kfs) }
                    onFinished(true)
                } else {
                    onFinished(false)
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Motion tracking failed: ${t.message}", t)
                onFinished(false)
            } finally {
                _trackingState.value = null
            }
        }
    }

    fun autoBlurAllFaces(
        currentPlayheadUs: Long,
        blurType: com.clipcut.core.model.BlurType = com.clipcut.core.model.BlurType.BLACKOUT,
        shape: com.clipcut.core.model.BlurShape = com.clipcut.core.model.BlurShape.OVAL,
        intensity: Float = 1.0f,
        feather: Float = 0.15f,
        customImageUri: String? = null
    ) {
        val proj = _project.value ?: return
        val mainClip = proj.videoClips.firstOrNull() ?: return
        val tracker = com.clipcut.core.feature.MotionTracker(getApplication())

        val styleDesc = when (blurType) {
            com.clipcut.core.model.BlurType.BLACKOUT -> "Solid Blackout"
            com.clipcut.core.model.BlurType.GAUSSIAN -> "Full Solid Blur"
            com.clipcut.core.model.BlurType.MOSAIC -> "Chunky Mosaic"
            com.clipcut.core.model.BlurType.STACK_BLUR -> "InShot StackBlur"
            com.clipcut.core.model.BlurType.MOTION_TRAIL -> "PowerDirector Trail"
            com.clipcut.core.model.BlurType.IMAGE -> "Stickers"
            com.clipcut.core.model.BlurType.PAINT -> "Finger Paint"
            com.clipcut.core.model.BlurType.CHROMA_PAINT -> "Green Screen Studio Brush"
        }

        _trackingState.value = "Scanning frame for all faces ($styleDesc)..."
        viewModelScope.launch {
            try {
                val faces = tracker.detectAllFacesAt(mainClip.uri, currentPlayheadUs)
                if (faces.isEmpty()) {
                    _trackingState.value = null
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "No faces detected at current playhead", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                _trackingState.value = "Found ${faces.size} faces! Tracking with $styleDesc..."
                val totalDur = proj.totalDurationUs.coerceAtLeast(3_000_000L)
                val newBlurTracks = mutableListOf<com.clipcut.core.model.BlurTrackItem>()

                for ((idx, face) in faces.withIndex()) {
                    val kfs = tracker.track(
                        videoUri = mainClip.uri,
                        initialX = face.normCenterX,
                        initialY = face.normCenterY,
                        initialW = face.normWidth,
                        initialH = face.normHeight,
                        initialRotation = 0f,
                        startTimeUs = 0L,
                        endTimeUs = totalDur,
                        anchorPtsUs = currentPlayheadUs,
                        mode = com.clipcut.core.feature.TrackingMode.FACE,
                        onProgress = { p ->
                            val pct = (p * 100).toInt()
                            _trackingState.value = "Tracking Face ${idx + 1}/${faces.size} ($pct%)..."
                        }
                    )

                    val blurTrack = com.clipcut.core.model.BlurTrackItem(
                        timelineStartUs = 0L,
                        timelineEndUs = totalDur,
                        normalizedX = face.normCenterX,
                        normalizedY = face.normCenterY,
                        width = face.normWidth,
                        height = face.normHeight,
                        shape = shape,
                        blurType = blurType,
                        intensity = intensity,
                        feather = feather,
                        customImageUri = customImageUri,
                        keyframes = kfs
                    )
                    newBlurTracks.add(blurTrack)
                }

                mutate { p -> p.copy(blurTracks = p.blurTracks + newBlurTracks) }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Auto-blurred and tracked ${faces.size} faces with $styleDesc!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Bulk face tracking failed: ${t.message}", t)
            } finally {
                _trackingState.value = null
            }
        }
    }

    fun autoBlackoutAllPeople(
        currentPlayheadUs: Long,
        blurType: com.clipcut.core.model.BlurType = com.clipcut.core.model.BlurType.BLACKOUT,
        shape: com.clipcut.core.model.BlurShape = com.clipcut.core.model.BlurShape.RECTANGLE,
        intensity: Float = 1.0f,
        feather: Float = 0.15f,
        customImageUri: String? = null
    ) {
        val proj = _project.value ?: return
        val mainClip = proj.videoClips.firstOrNull() ?: return
        val tracker = com.clipcut.core.feature.MotionTracker(getApplication())

        val styleDesc = when (blurType) {
            com.clipcut.core.model.BlurType.BLACKOUT -> "Solid Blackout"
            com.clipcut.core.model.BlurType.GAUSSIAN -> "Full Solid Blur"
            com.clipcut.core.model.BlurType.MOSAIC -> "Chunky Mosaic"
            com.clipcut.core.model.BlurType.STACK_BLUR -> "InShot StackBlur"
            com.clipcut.core.model.BlurType.MOTION_TRAIL -> "PowerDirector Trail"
            com.clipcut.core.model.BlurType.IMAGE -> "Stickers"
            com.clipcut.core.model.BlurType.PAINT -> "Finger Paint"
            com.clipcut.core.model.BlurType.CHROMA_PAINT -> "Green Screen Studio Brush"
        }

        _trackingState.value = "Scanning frame for all people ($styleDesc)..."
        viewModelScope.launch {
            try {
                val faces = tracker.detectAllFacesAt(mainClip.uri, currentPlayheadUs)
                if (faces.isEmpty()) {
                    _trackingState.value = null
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), "No people detected at current playhead", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                _trackingState.value = "Found ${faces.size} people! Tracking full bodies with $styleDesc..."
                val totalDur = proj.totalDurationUs.coerceAtLeast(3_000_000L)
                val newBlurTracks = mutableListOf<com.clipcut.core.model.BlurTrackItem>()

                for ((idx, face) in faces.withIndex()) {
                    val bodyW = (face.normWidth * 1.8f).coerceIn(0.24f, 0.45f)
                    val bodyH = (face.normHeight * 5.0f).coerceIn(0.60f, 0.90f)
                    val bodyX = face.normCenterX
                    val bodyY = (face.normCenterY + bodyH * 0.35f).coerceIn(0.25f, 0.92f)

                    val kfs = tracker.track(
                        videoUri = mainClip.uri,
                        initialX = bodyX,
                        initialY = bodyY,
                        initialW = bodyW,
                        initialH = bodyH,
                        initialRotation = 0f,
                        startTimeUs = 0L,
                        endTimeUs = totalDur,
                        anchorPtsUs = currentPlayheadUs,
                        mode = com.clipcut.core.feature.TrackingMode.BODY,
                        onProgress = { p ->
                            val pct = (p * 100).toInt()
                            _trackingState.value = "Tracking Person ${idx + 1}/${faces.size} ($pct%)..."
                        }
                    )

                    val blurTrack = com.clipcut.core.model.BlurTrackItem(
                        timelineStartUs = 0L,
                        timelineEndUs = totalDur,
                        normalizedX = bodyX,
                        normalizedY = bodyY,
                        width = bodyW,
                        height = bodyH,
                        shape = shape,
                        blurType = blurType,
                        intensity = intensity,
                        feather = feather,
                        customImageUri = customImageUri,
                        keyframes = kfs
                    )
                    newBlurTracks.add(blurTrack)
                }

                mutate { p -> p.copy(blurTracks = p.blurTracks + newBlurTracks) }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Auto-blacked out and tracked ${faces.size} full people with $styleDesc!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Bulk full body tracking failed: ${t.message}", t)
            } finally {
                _trackingState.value = null
            }
        }
    }

    fun setVolumes(orig: Float, music: Float) = mutate {
        it.copy(originalVideoVolume = orig.coerceIn(0f, 2f), backgroundMusicVolume = music.coerceIn(0f, 2f))
    }

    fun addAudioTrack(item: AudioTrackItem) = mutate { it.copy(audioTracks = it.audioTracks + item) }
    fun addAudioTrack(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            var displayName = "Music Track"
            try {
                val cursor = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            val name = it.getString(idx)
                            if (!name.isNullOrBlank()) displayName = name
                        }
                    }
                }
            } catch (t: Throwable) {
                AppLog.w(TAG, "Failed to resolve audio display name: ${t.message}")
            }

            // Copy audio track to internal app storage to guarantee persistent access across sessions and background export
            val audioDir = java.io.File(app.filesDir, "audio_tracks").apply { mkdirs() }
            val cleanExt = displayName.substringAfterLast('.', "mp3")
            val destFile = java.io.File(audioDir, "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$cleanExt")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to copy audio track to local filesDir", t)
            }

            val targetFileOrUri = if (destFile.exists() && destFile.length() > 0) {
                android.net.Uri.fromFile(destFile).toString()
            } else {
                uri.toString()
            }

            val durationUs = try {
                val retriever = android.media.MediaMetadataRetriever()
                if (destFile.exists() && destFile.length() > 0) {
                    retriever.setDataSource(destFile.absolutePath)
                } else {
                    retriever.setDataSource(app, uri)
                }
                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                (durStr?.toLongOrNull() ?: 5_000L) * 1000L
            } catch (t: Throwable) {
                AppLog.w(TAG, "extract audio duration failed: ${t.message}")
                5_000_000L
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val projDur = _project.value?.totalDurationUs ?: durationUs
                val targetEnd = if (projDur > 0L) minOf(durationUs, projDur) else durationUs
                val item = AudioTrackItem(
                    uri = targetFileOrUri,
                    name = displayName,
                    sourceDurationUs = durationUs,
                    timelineStartUs = 0L,
                    trimStartUs = 0L,
                    trimEndUs = if (targetEnd > 0L) targetEnd else durationUs.coerceAtLeast(1_000_000L)
                )
                addAudioTrack(item)
            }
        }
    }

    fun addAudioTrackWithTrim(uri: Uri, displayName: String, startUs: Long, endUs: Long, sourceDurUs: Long = 0L) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val audioDir = java.io.File(app.filesDir, "audio_tracks").apply { mkdirs() }
            val cleanExt = displayName.substringAfterLast('.', "mp3")
            val destFile = java.io.File(audioDir, "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$cleanExt")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to copy audio track to local filesDir", t)
            }

            val targetFileOrUri = if (destFile.exists() && destFile.length() > 0) {
                android.net.Uri.fromFile(destFile).toString()
            } else {
                uri.toString()
            }

            val durationUs = if (sourceDurUs > 0) sourceDurUs else {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    if (destFile.exists() && destFile.length() > 0) {
                        retriever.setDataSource(destFile.absolutePath)
                    } else {
                        retriever.setDataSource(app, uri)
                    }
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    (durStr?.toLongOrNull() ?: 5_000L) * 1000L
                } catch (t: Throwable) {
                    AppLog.w(TAG, "extract audio duration failed: ${t.message}")
                    5_000_000L
                }
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val finalStartUs = startUs.coerceIn(0L, durationUs)
                val finalEndUs = if (endUs > finalStartUs) endUs.coerceAtMost(durationUs) else durationUs
                val item = AudioTrackItem(
                    uri = targetFileOrUri,
                    name = displayName,
                    sourceDurationUs = durationUs,
                    timelineStartUs = 0L,
                    trimStartUs = finalStartUs,
                    trimEndUs = finalEndUs
                )
                addAudioTrack(item)
            }
        }
    }

    fun addAssetAudioTrack(assetPath: String, displayName: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val audioDir = java.io.File(app.filesDir, "audio_tracks").apply { mkdirs() }
            val cleanName = assetPath.substringAfterLast('/')
            val destFile = java.io.File(audioDir, "asset_${System.currentTimeMillis()}_$cleanName")
            try {
                app.assets.open(assetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to copy asset audio to filesDir: ${t.message}")
                return@launch
            }

            val durationUs = try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(destFile.absolutePath)
                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                (durStr?.toLongOrNull() ?: 3_000L) * 1000L
            } catch (t: Throwable) {
                3_000_000L
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val item = AudioTrackItem(
                    uri = android.net.Uri.fromFile(destFile).toString(),
                    name = displayName,
                    sourceDurationUs = durationUs,
                    timelineStartUs = playerManager.currentPositionUs.value,
                    trimStartUs = 0L,
                    trimEndUs = durationUs.coerceAtLeast(500_000L)
                )
                addAudioTrack(item)
            }
        }
    }

    fun removeAudioTrack(id: String) = mutate { it.copy(audioTracks = it.audioTracks.filterNot { a -> a.id == id }) }
    fun trimAudioTrack(id: String, startUs: Long, endUs: Long) = mutate { p ->
        p.copy(audioTracks = p.audioTracks.map {
            if (it.id == id) {
                val validStart = startUs.coerceAtLeast(0L)
                val maxEnd = if (it.sourceDurationUs > 0) it.sourceDurationUs else Long.MAX_VALUE
                val validEnd = endUs.coerceIn(validStart + 100_000L, maxEnd)
                it.copy(trimStartUs = validStart, trimEndUs = validEnd)
            } else it
        })
    }

    fun deleteCurrentProject(onDeleted: () -> Unit) {
        val pid = _project.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.deleteProjectById(pid)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDeleted()
                }
            } catch (t: Throwable) {
                AppLog.e(TAG, "Failed to delete project $pid", t)
            }
        }
    }

    fun startExport(config: ExportConfig) {
        if (currentExport != null) {
            AppLog.w(TAG, "startExport ignored — export already running")
            return
        }
        val p = _project.value ?: return
        val ctx = getApplication<Application>()
        currentExport = ExportEngine(ctx, p, config).also { eng ->
            _exportState.value = ExportState.Progress(0f, "Starting")
            viewModelScope.launch {
                try {
                    val res = eng.export { prog, status -> _exportState.value = ExportState.Progress(prog, status) }
                    res.onSuccess { _exportState.value = ExportState.Success(it.file, it.publicUri, it.galleryPath) }
                        .onFailure { _exportState.value = ExportState.Error(it.message ?: "Unknown") }
                } catch (t: Throwable) {
                    _exportState.value = ExportState.Error(t.message ?: "Unknown export error")
                } finally {
                    currentExport = null
                }
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
            brandKitConfig = it.brandKitConfig
            playerManager.syncWithProject(it)
            previewRenderer.setProject(it)
        }
    }

    private fun pushAutoSave(project: Project) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateProject(ProjectEntity.fromDomain(project))
        }
    }

    private fun extractMediaInfo(uri: Uri): Pair<Long, Int> {
        val ctx = getApplication<Application>()
        var duration = 5_000_000L
        var rotation = 0
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(ctx, uri)
            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) duration = durStr.toLong() * 1000L
            val rotStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (rotStr != null) rotation = rotStr.toIntOrNull() ?: 0
            retriever.release()
        } catch (t: Throwable) {
            AppLog.w(TAG, "extractMediaInfo fallback: ${t.message}")
        }
        return Pair(duration, rotation)
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
        previewRenderer.release()
        currentExport?.cancel()
    }

    companion object { private const val TAG = "EditorViewModel" }
}
