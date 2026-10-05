package com.clipcut.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.MediaType
import com.clipcut.ui.components.EditorToolbar
import com.clipcut.ui.components.EditorTool
import com.clipcut.ui.components.PrimaryButton
import com.clipcut.ui.components.SecondaryButton
import com.clipcut.ui.components.UnifiedBottomSheetHost
import com.clipcut.ui.components.TimelineView
import com.clipcut.ui.components.EmptyState
import com.clipcut.ui.screens.AudioSheet
import com.clipcut.ui.screens.EffectsSheet
import com.clipcut.ui.screens.ExportDialog
import com.clipcut.ui.screens.FilterSheet
import com.clipcut.ui.screens.SpeedSheet
import com.clipcut.ui.screens.TransitionsSheet
import com.clipcut.ui.screens.VolumeSheet
import com.clipcut.ui.screens.VoiceoverSheet
import com.clipcut.ui.screens.StickerSheet
import com.clipcut.ui.screens.CaptionSheet
import com.clipcut.ui.screens.KeyframeSheet
import com.clipcut.ui.screens.PiPPanel
import com.clipcut.ui.screens.OverlaySheet
import com.clipcut.ui.screens.RotateSheet
import com.clipcut.ui.screens.CropSheet
import com.clipcut.ui.screens.FlipSheet
import com.clipcut.ui.screens.ChromaKeyDialog
import com.clipcut.ui.screens.MaskDialog
import com.clipcut.ui.screens.BlendModeDialog
import com.clipcut.ui.screens.TrimSheet
import com.clipcut.ui.screens.TextSheet
import com.clipcut.ui.screens.AudioDuckingSheet
import com.clipcut.ui.theme.AccentPrimary
import com.clipcut.ui.theme.DarkBackground
import com.clipcut.ui.theme.Surface1
import com.clipcut.ui.theme.Surface2

/**
 * EditorScreen — composes preview, timeline, toolbar and the unified bottom
 * sheet. Tool selection routes the sheet content.
 *
 * GlPreviewRenderer is invoked indirectly: EditorViewModel owns the player
 * and renderer; here we surface the player position to UI flows and call
 * viewModel mutations on tool-sheet callbacks.
 */
@Composable
fun EditorScreen(
    projectId: String,
    viewModel: com.clipcut.ui.viewmodel.EditorViewModel,
    onNavigateBack: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val project by viewModel.project.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val positionUs by viewModel.playerManager.currentPositionUs.collectAsState()

    var selectedTool by remember { mutableStateOf<EditorTool?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.importMedia(uri, MediaType.VIDEO)
    }

    LaunchedEffect(projectId) { viewModel.loadProject(projectId) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            EditorTopBar(
                onBack = onNavigateBack,
                onLogs = onOpenLogs,
                onExport = { showExportDialog = true },
                title = project?.name ?: "Loading…"
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Preview surface (placeholder gradient — GL surface wired by GlPreviewRenderer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Surface2),
                contentAlignment = Alignment.Center
            ) {
                if (project == null || project!!.videoClips.isEmpty()) {
                    EmptyState(
                        title = "No clips yet",
                        description = "Tap the + below to add media",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("${project!!.videoClips.size} clips loaded", color = Color.White.copy(alpha = 0.7f))
                }
                // Play/pause overlay
                IconButton(
                    onClick = { viewModel.playerManager.togglePlayPause() },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayCircle,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            // Timeline
            TimelineView(
                videoClips = project?.videoClips ?: emptyList(),
                audioTracks = project?.audioTracks ?: emptyList(),
                overlays = project?.overlays ?: emptyList(),
                beatMarkers = project?.beatMarkers ?: emptyList(),
                selectedClipId = selectedClipId,
                currentPositionUs = positionUs,
                totalDurationUs = project?.totalDurationUs ?: 0L,
                onSelectClip = { viewModel.selectClip(it) },
                onSeek = { viewModel.playerManager.seekToUs(it) },
                onAddMediaClick = { pickMedia.launch("video/*") }
            )
            // Toolbar
            EditorToolbar(
                selected = selectedTool,
                onSelect = { tool ->
                    when (tool) {
                        EditorTool.UNDO -> viewModel.undo()
                        EditorTool.REDO -> viewModel.redo()
                        EditorTool.SPLIT -> viewModel.splitClipAtPlayhead()
                        else -> selectedTool = tool
                    }
                },
                canUndo = canUndo,
                canRedo = canRedo
            )
        }
    }

    // Bottom sheet host routes content based on selectedTool.
    val tool = selectedTool
    val sid = selectedClipId
    val clip = project?.videoClips?.firstOrNull { it.id == sid }
    UnifiedBottomSheetHost(
        title = tool?.label ?: "",
        onDone = { selectedTool = null },
        onDismiss = { selectedTool = null },
        visible = tool != null
    ) {
        when (tool) {
            EditorTool.AUDIO -> AudioSheet(
                originalVideoVolume = project?.originalVideoVolume ?: 1f,
                backgroundMusicVolume = project?.backgroundMusicVolume ?: 1f,
                hasMusicTrack = (project?.audioTracks?.isNotEmpty() == true),
                onVolumesChanged = { o, m -> viewModel.setVolumes(o, m) },
                onPickMusicClick = { /* wired to picker */ },
                onDismiss = { selectedTool = null }
            )
            EditorTool.EFFECTS -> EffectsSheet(
                currentEffectId = clip?.effectId,
                currentIntensity = clip?.effectIntensity ?: 0f,
                onApply = { id, intensity ->
                    sid?.let { viewModel.setClipEffect(it, id, intensity) }
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.TRANSITIONS -> TransitionsSheet(
                current = clip?.transitionType ?: com.clipcut.core.model.TransitionType.NONE,
                durationUs = clip?.transitionDurationUs ?: 500_000L,
                onApply = { type, dur ->
                    sid?.let {
                        viewModel.setClipTransition(it, type)
                    }
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.FILTER -> FilterSheet(
                initialConfig = clip?.filterConfig ?: com.clipcut.core.model.FilterConfig(),
                onApplyFilter = { fc -> sid?.let { viewModel.setClipFilter(it, fc) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.SPEED -> SpeedSheet(
                current = clip?.speed ?: 1f,
                onApply = { sp -> sid?.let { viewModel.setClipSpeed(it, sp) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.VOLUME -> VolumeSheet(
                current = clip?.volume ?: 1f,
                onApply = { v -> sid?.let { viewModel.setClipVolume(it, v) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.ROTATE -> RotateSheet(
                current = clip?.rotationDegrees ?: 0,
                onApply = { d -> sid?.let { viewModel.setClipRotation(it, d) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.CROP -> CropSheet(
                current = clip?.cropRect ?: com.clipcut.core.model.CropRect(),
                onApply = { c -> sid?.let { viewModel.setClipCrop(it, c) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.FLIP -> FlipSheet(
                flipH = clip?.flipHorizontal ?: false,
                flipV = clip?.flipVertical ?: false,
                onApply = { h, v -> sid?.let { viewModel.setClipFlipH(it, h); viewModel.setClipFlipV(it, v) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.CHROMA -> ChromaKeyDialog(
                initialColor = clip?.chromaKeyColor ?: floatArrayOf(0f, 1f, 0f),
                initialThreshold = clip?.chromaKeyThreshold ?: 0.15f,
                initialSmoothness = clip?.chromaKeySmoothness ?: 0.05f,
                onApply = { c, t, s -> sid?.let { viewModel.setClipChromaKey(it, c, t, s) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.MASK -> MaskDialog(
                initial = clip?.maskConfig ?: com.clipcut.core.model.MaskConfig(),
                onApply = { m -> sid?.let { viewModel.setClipMask(it, m) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.BLEND -> BlendModeDialog(
                initial = clip?.blendMode ?: com.clipcut.core.model.BlendMode.NORMAL,
                onApply = { b -> sid?.let { viewModel.setClipBlendMode(it, b) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.TEXT -> TextSheet(
                onApply = { overlay -> viewModel.addOverlay(overlay) },
                onDismiss = { selectedTool = null }
            )
            EditorTool.STICKER -> StickerSheet(
                onPick = { stickerRes ->
                    viewModel.addOverlay(
                        com.clipcut.core.model.OverlayItem(
                            text = stickerRes,
                            timelineStartUs = positionUs,
                            timelineEndUs = positionUs + 2_000_000L
                        )
                    )
                    selectedTool = null
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.OVERLAY -> OverlaySheet(
                totalDurationUs = project?.totalDurationUs ?: 0L,
                initialPlayheadUs = positionUs,
                existingOverlays = project?.overlays ?: emptyList(),
                onAddOverlay = { o -> viewModel.addOverlay(o) },
                onDeleteOverlay = { id -> viewModel.removeOverlay(id) },
                onSeekPreview = { us -> viewModel.playerManager.seekToUs(us) },
                onDismiss = { selectedTool = null }
            )
            EditorTool.KEYFRAMES -> KeyframeSheet(
                overlay = project?.overlays?.firstOrNull(),
                playheadUs = positionUs,
                onAddKeyframe = { ovId, kf -> viewModel.addKeyframe(ovId, kf) },
                onRemoveKeyframe = { ovId, kfId -> viewModel.removeKeyframe(ovId, kfId) },
                onDismiss = { selectedTool = null }
            )
            EditorTool.MORE -> AudioDuckingSheet(
                enabled = project?.audioDuckingEnabled ?: false,
                level = project?.audioDuckLevel ?: 0.3f,
                onApply = { e, l -> viewModel.setAudioDucking(e, l) },
                onDismiss = { selectedTool = null }
            )
            else -> { /* default */ }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            projectAspectRatio = project?.aspectRatio ?: com.clipcut.core.model.AspectRatio.R_16_9,
            exportState = exportState,
            onStartExport = { cfg -> viewModel.startExport(cfg) },
            onCancelExport = { viewModel.cancelExport() },
            onDismiss = { showExportDialog = false; viewModel.dismissExportDialog() },
            onViewLogsClick = onOpenLogs
        )
    }
}

@Composable
private fun EditorTopBar(
    onBack: () -> Unit,
    onLogs: () -> Unit,
    onExport: () -> Unit,
    title: String
) {
    Surface(color = Surface1, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onLogs) {
                Icon(Icons.Filled.BugReport, contentDescription = "Logs", tint = Color.White.copy(alpha = 0.7f))
            }
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Export", fontSize = 12.sp) }
        }
    }
}
