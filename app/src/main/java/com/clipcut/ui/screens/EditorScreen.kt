package com.clipcut.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.MediaType
import com.clipcut.core.model.TransitionType
import com.clipcut.core.model.AspectRatio
import com.clipcut.core.model.CropRect
import com.clipcut.core.model.TimelineItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.Color
import com.clipcut.ui.theme.*
import com.clipcut.ui.viewmodel.EditorViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onOpenLogs: () -> Unit
) {
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val project by viewModel.project.collectAsState()
    val selectedClipId by viewModel.selectedClipId.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val currentPositionUs by viewModel.playerManager.currentPositionUs.collectAsState()
    val totalDurationUs by viewModel.playerManager.totalDurationUs.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showOverlaySheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTransitionDialog by remember { mutableStateOf(false) }
    var showClipDialog by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showFadeDialog by remember { mutableStateOf(false) }
    var showAspectRatioDialog by remember { mutableStateOf(false) }
    var showSpeedRampDialog by remember { mutableStateOf(false) }
    var showLutDialog by remember { mutableStateOf(false) }
    var showChromaDialog by remember { mutableStateOf(false) }
    var showMaskDialog by remember { mutableStateOf(false) }
    var showBlendDialog by remember { mutableStateOf(false) }
    var showBeatSheet by remember { mutableStateOf(false) }
    var showReverseDialog by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMedia(it, MediaType.VIDEO) }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMedia(it, MediaType.IMAGE) }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMedia(it, MediaType.AUDIO) }
    }

    val selectedClip = remember(project, selectedClipId) {
        project?.videoClips?.find { it.id == selectedClipId }
    }

    LaunchedEffect(Unit) {
        viewModel.previewRenderer.positionProvider = {
            viewModel.playerManager.currentPositionUs.value
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = project?.name ?: "Editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val currentRatio = project?.aspectRatio ?: AspectRatio.R_16_9
                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clickable { showAspectRatioDialog = true }
                            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(currentRatio.label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.Default.BugReport, contentDescription = "Logs", tint = AccentPrimary)
                    }
                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // GL Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(DarkBackground)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (project?.videoClips?.isNotEmpty() == true) {
                            viewModel.playerManager.togglePlayPause()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (project?.videoClips?.isNotEmpty() == true) {
                    val renderer = viewModel.previewRenderer

                    LaunchedEffect(project) {
                        project?.let { renderer.setProject(it) }
                    }

                    val currentRatio = project?.aspectRatio ?: AspectRatio.R_16_9
                    val targetRatioValue = if (currentRatio.wRatio > 0f && currentRatio.hRatio > 0f) {
                        currentRatio.wRatio / currentRatio.hRatio
                    } else 16f / 9f

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val maxW = maxWidth
                        val maxH = maxHeight
                        val boxAspect = (maxW / maxH).coerceAtLeast(0.01f)
                        val (canvasW, canvasH) = if (boxAspect > targetRatioValue) {
                            Pair(maxH * targetRatioValue, maxH)
                        } else {
                            Pair(maxW, maxW / targetRatioValue)
                        }

                        Box(
                            modifier = Modifier
                                .size(canvasW, canvasH)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidExternalSurface(modifier = Modifier.fillMaxSize()) {
                                onSurface { surface, width, height ->
                                    renderer.onSurfaceCreated(surface, width, height)
                                    surface.onChanged { w, h -> renderer.onSurfaceChanged(w, h) }
                                    surface.onDestroyed { renderer.onSurfaceDestroyed() }
                                }
                            }

                            if (!isPlaying) {
                                val isAtEnd = totalDurationUs > 0 && currentPositionUs >= (totalDurationUs - 50_000L)
                                IconButton(
                                    onClick = { viewModel.playerManager.togglePlayPause() },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(DarkSurface.copy(alpha = 0.75f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isAtEnd) Icons.Default.Replay else Icons.Default.PlayArrow,
                                        contentDescription = if (isAtEnd) "Replay" else "Play",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { videoPickerLauncher.launch("video/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to Import Video", color = TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("or use Add menu below", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }

            // Transport bar
            if (project?.videoClips?.isNotEmpty() == true) {
                Surface(color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
                    val isAtEnd = totalDurationUs > 0 && currentPositionUs >= (totalDurationUs - 50_000L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.playerManager.seekToUs(0L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, "Start", tint = TextPrimary, modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = { viewModel.playerManager.seekByUs(-1_000_000L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.FastRewind, "-1s", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        Button(
                            onClick = { viewModel.playerManager.togglePlayPause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAtEnd) AudioTrackColor else AccentPrimary
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isPlaying -> Icons.Default.Pause
                                    isAtEnd -> Icons.Default.Replay
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = "Play/Pause",
                                tint = TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.playerManager.seekByUs(1_000_000L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.FastForward, "+1s", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { viewModel.playerManager.seekToUs(totalDurationUs) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, "End", tint = TextPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // Timeline
            TimelineView(
                videoClips = project?.videoClips ?: emptyList(),
                audioTracks = project?.audioTracks ?: emptyList(),
                overlays = project?.overlays ?: emptyList(),
                beatMarkers = project?.beatMarkers ?: emptyList(),
                selectedClipId = selectedClipId,
                currentPositionUs = currentPositionUs,
                totalDurationUs = totalDurationUs,
                onSelectClip = { id -> viewModel.selectClip(id) },
                onSeek = { targetUs -> viewModel.playerManager.seekToUs(targetUs) },
                onAddMediaClick = { videoPickerLauncher.launch("video/*") },
                onTrimClip = { clipId, startUs, endUs -> viewModel.trimClip(clipId, startUs, endUs) }
            )

            // Toolbar (regrouped)
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) {
                val clips = project?.videoClips ?: emptyList()
                val selectedIndex = clips.indexOfFirst { it.id == selectedClipId }
                var showAddMenu by remember { mutableStateOf(false) }
                var showMoreMenu by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Group 1: Undo / Redo
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EditorActionButton(
                            Icons.AutoMirrored.Filled.Undo, "Undo",
                            enabled = viewModel.canUndo.collectAsState().value
                        ) { viewModel.undo() }
                        EditorActionButton(
                            Icons.AutoMirrored.Filled.Redo, "Redo",
                            enabled = viewModel.canRedo.collectAsState().value
                        ) { viewModel.redo() }
                    }

                    // Group 2: Add
                    Box {
                        EditorActionButton(Icons.Default.Add, "Add") { showAddMenu = true }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Video") },
                                onClick = { showAddMenu = false; videoPickerLauncher.launch("video/*") }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Image") },
                                onClick = { showAddMenu = false; imagePickerLauncher.launch("image/*") }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Audio") },
                                onClick = { showAddMenu = false; audioPickerLauncher.launch("audio/*") }
                            )
                        }
                    }

                    // Group 3: Edit
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EditorActionButton(
                            Icons.Default.ContentCut, "Split",
                            enabled = selectedClip != null && currentPositionUs > 30_000L &&
                                    currentPositionUs < totalDurationUs - 30_000L
                        ) { viewModel.splitClipAtPlayhead() }
                        EditorActionButton(
                            Icons.Default.Tune, "Edit",
                            enabled = selectedClip != null
                        ) { showClipDialog = true }
                        EditorActionButton(
                            Icons.Default.TextFields, "Text"
                        ) { showOverlaySheet = true }
                        EditorActionButton(
                            Icons.Default.MusicNote, "Audio"
                        ) { showAudioSheet = true }
                    }

                    // Group 4: More
                    Box {
                        EditorActionButton(Icons.Default.MoreVert, "More") { showMoreMenu = true }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Canvas Ratio (${project?.aspectRatio?.label ?: "16:9"})") },
                                onClick = { showMoreMenu = false; showAspectRatioDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Reverse Clip") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showReverseDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Speed") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showSpeedDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Speed Ramp") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showSpeedRampDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("LUT") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showLutDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Chroma Key") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showChromaDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Mask") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showMaskDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Blend Mode") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showBlendDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Beat Markers") },
                                onClick = { showMoreMenu = false; showBeatSheet = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Filters") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showFilterSheet = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Transition") },
                                enabled = selectedClip != null && selectedIndex > 0,
                                onClick = { showMoreMenu = false; showTransitionDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Crop & Rotate") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showCropDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Fade In / Out") },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; showFadeDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Move ◀") },
                                enabled = selectedIndex > 0,
                                onClick = { showMoreMenu = false; viewModel.reorderClips(selectedIndex, selectedIndex - 1) }
                            )
                            DropdownMenuItem(
                                text = { Text("Move ▶") },
                                enabled = selectedIndex in 0..<clips.lastIndex,
                                onClick = { showMoreMenu = false; viewModel.reorderClips(selectedIndex, selectedIndex + 1) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Clip", color = AccentPrimary) },
                                enabled = selectedClip != null,
                                onClick = { showMoreMenu = false; selectedClipId?.let { viewModel.deleteClip(it) } }
                            )
                        }
                    }
                }
            }
        }
    }

    // Filter sheet
    if (showFilterSheet && selectedClip != null) {
        FilterSheet(
            initialConfig = selectedClip.filterConfig,
            onApplyFilter = { newConfig -> viewModel.setClipFilter(selectedClip.id, newConfig) },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Overlay sheet
    if (showOverlaySheet) {
        OverlaySheet(
            totalDurationUs = totalDurationUs,
            initialPlayheadUs = currentPositionUs,
            existingOverlays = project?.overlays ?: emptyList(),
            onAddOverlay = { overlay -> viewModel.addOverlay(overlay) },
            onDeleteOverlay = { overlayId -> viewModel.removeOverlay(overlayId) },
            onSeekPreview = { targetUs -> viewModel.playerManager.seekToUs(targetUs) },
            onDismiss = { showOverlaySheet = false }
        )
    }

    // Audio sheet
    if (showAudioSheet) {
        AudioSheet(
            originalVideoVolume = project?.originalVideoVolume ?: 1f,
            backgroundMusicVolume = project?.backgroundMusicVolume ?: 1f,
            hasMusicTrack = project?.audioTracks?.isNotEmpty() == true,
            onVolumesChanged = { orig, music -> viewModel.setVolumes(orig, music) },
            onPickMusicClick = { audioPickerLauncher.launch("audio/*") },
            onDismiss = { showAudioSheet = false }
        )
    }

    // Speed dialog
    if (showSpeedDialog && selectedClip != null) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 4.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Clip Speed", color = TextPrimary) },
            text = {
                Column {
                    speeds.forEach { spd ->
                        val isSelected = selectedClip.speed == spd
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setClipSpeed(selectedClip.id, spd)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setClipSpeed(selectedClip.id, spd)
                                    showSpeedDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${spd}x",
                                color = if (isSelected) AccentPrimary else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Transition dialog
    if (showTransitionDialog && selectedClip != null) {
        AlertDialog(
            onDismissRequest = { showTransitionDialog = false },
            title = { Text("Transition (into this clip)", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "The transition plays at the start of this clip, blending from the previous clip.",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TransitionType.values().forEach { type ->
                        val isSelected = selectedClip.transitionType == type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setClipTransition(selectedClip.id, type)
                                    showTransitionDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setClipTransition(selectedClip.id, type)
                                    showTransitionDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = type.displayName,
                                color = if (isSelected) AccentPrimary else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTransitionDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Clip volume dialog
    if (showClipDialog && selectedClip != null) {
        var clipVolume by remember(selectedClip.id) { mutableStateOf(selectedClip.volume) }
        AlertDialog(
            onDismissRequest = {
                viewModel.setClipVolume(selectedClip.id, clipVolume)
                showClipDialog = false
            },
            title = { Text("Clip Volume", color = TextPrimary) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volume", color = TextSecondary, fontSize = 13.sp)
                        Text("${(clipVolume * 100).toInt()}%", color = AccentPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = clipVolume,
                        onValueChange = { clipVolume = it },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentPrimary,
                            activeTrackColor = AccentPrimary,
                            inactiveTrackColor = DarkSurfaceBorder
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "0% mutes this clip. 100% is original. 200% doubles it.",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setClipVolume(selectedClip.id, clipVolume)
                        showClipDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClipDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Crop dialog
    if (showCropDialog && selectedClip != null) {
        CropDialog(
            clip = selectedClip,
            onApply = { rot, fh, fv, crop, _ ->
                viewModel.setClipRotation(selectedClip.id, rot)
                viewModel.setClipFlipH(selectedClip.id, fh)
                viewModel.setClipFlipV(selectedClip.id, fv)
                viewModel.setClipCrop(selectedClip.id, crop)
            },
            onDismiss = { showCropDialog = false }
        )
    }

    // Fade dialog
    if (showFadeDialog && selectedClip != null) {
        FadeDialog(
            clip = selectedClip,
            onApply = { fi, fo -> viewModel.setClipFade(selectedClip.id, fi, fo) },
            onDismiss = { showFadeDialog = false }
        )
    }

    // Aspect Ratio dialog
    if (showAspectRatioDialog) {
        AspectRatioDialog(
            currentAspectRatio = project?.aspectRatio ?: AspectRatio.R_16_9,
            onSelect = { ar -> viewModel.setProjectAspectRatio(ar) },
            onDismiss = { showAspectRatioDialog = false }
        )
    }

    // Export dialog
    if (showExportDialog || exportState !is com.clipcut.ui.viewmodel.ExportState.Idle) {
        ExportDialog(
            projectAspectRatio = project?.aspectRatio ?: AspectRatio.R_16_9,
            exportState = exportState,
            onStartExport = { cfg -> viewModel.startExport(cfg) },
            onCancelExport = { viewModel.cancelExport() },
            onDismiss = {
                showExportDialog = false
                viewModel.dismissExportDialog()
            },
            onViewLogsClick = {
                showExportDialog = false
                viewModel.dismissExportDialog()
                onOpenLogs()
            }
        )
    }

    if (showSpeedRampDialog && selectedClip != null) {
        SpeedRampDialog(
            initialRamp = selectedClip.speedRamp,
            clipDurationUs = selectedClip.effectiveDurationUs,
            onApply = { viewModel.setClipSpeedRamp(selectedClip.id, it) },
            onDismiss = { showSpeedRampDialog = false }
        )
    }

    if (showLutDialog && selectedClip != null) {
        LutPickerDialog(
            currentLut = selectedClip.lutConfig,
            onApply = { viewModel.setClipLut(selectedClip.id, it) },
            onDismiss = { showLutDialog = false }
        )
    }

    if (showChromaDialog && selectedClip != null) {
        ChromaKeyDialog(
            initialColor = selectedClip.chromaKeyColor ?: floatArrayOf(0f, 1f, 0f),
            initialThreshold = selectedClip.chromaKeyThreshold,
            initialSmoothness = selectedClip.chromaKeySmoothness,
            onApply = { c, t, s -> viewModel.setClipChromaKey(selectedClip.id, c, t, s) },
            onDismiss = { showChromaDialog = false }
        )
    }

    if (showMaskDialog && selectedClip != null) {
        MaskDialog(
            initial = selectedClip.maskConfig,
            onApply = { viewModel.setClipMask(selectedClip.id, it) },
            onDismiss = { showMaskDialog = false }
        )
    }

    if (showBlendDialog && selectedClip != null) {
        BlendModeDialog(
            initial = selectedClip.blendMode,
            onApply = { viewModel.setClipBlendMode(selectedClip.id, it) },
            onDismiss = { showBlendDialog = false }
        )
    }

    if (showBeatSheet) {
        BeatMarkerSheet(
            totalDurationUs = totalDurationUs,
            existingMarkers = project?.beatMarkers ?: emptyList(),
            onAddMarker = { viewModel.addBeatMarker(it) },
            onRemoveMarker = { viewModel.removeBeatMarker(it) },
            onClearAll = { viewModel.clearBeatMarkers() },
            onDismiss = { showBeatSheet = false }
        )
    }

    if (showReverseDialog && selectedClip != null) {
        AlertDialog(
            onDismissRequest = { showReverseDialog = false },
            title = { Text("Reverse Clip", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Play this clip backwards. Note: short clips only (< 30s).",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = selectedClip.isReversed,
                            onCheckedChange = { viewModel.setClipReversed(selectedClip.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentPrimary,
                                checkedTrackColor = AccentPrimary.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Reverse playback", color = TextPrimary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showReverseDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) { Text("Done") }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

@Composable
private fun CropDialog(
    clip: TimelineItem,
    onApply: (Int, Boolean, Boolean, CropRect, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var rotation by remember(clip.id) { mutableStateOf(clip.rotationDegrees) }
    var flipH by remember(clip.id) { mutableStateOf(clip.flipHorizontal) }
    var flipV by remember(clip.id) { mutableStateOf(clip.flipVertical) }
    var crop by remember(clip.id) { mutableStateOf(clip.cropRect) }
    var aspect by remember(clip.id) { mutableStateOf(AspectRatio.FREE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop & Transform", color = TextPrimary) },
        text = {
            Column {
                Text("Aspect Ratio", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(AspectRatio.values().toList()) { ar ->
                        val sel = aspect == ar
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) AccentPrimary else DarkSurface)
                                .clickable {
                                    aspect = ar
                                    if (ar != AspectRatio.FREE) {
                                        val targetH = ar.hRatio / ar.wRatio
                                        crop = CropRect(left = 0f, top = (1f - targetH) / 2f, right = 1f, bottom = 1f - (1f - targetH) / 2f)
                                    } else crop = CropRect()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(ar.label, color = if (sel) TextPrimary else TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Rotation: $rotation°", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 90, 180, 270).forEach { deg ->
                        OutlinedButton(onClick = { rotation = deg }) { Text("$deg°", fontSize = 11.sp) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = flipH, onCheckedChange = { flipH = it })
                    Text("Flip Horizontal", color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = flipV, onCheckedChange = { flipV = it })
                    Text("Flip Vertical", color = TextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(rotation, flipH, flipV, crop, null); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
private fun FadeDialog(
    clip: TimelineItem,
    onApply: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var fadeIn by remember(clip.id) { mutableStateOf(clip.fadeInMs) }
    var fadeOut by remember(clip.id) { mutableStateOf(clip.fadeOutMs) }
    val maxMs = (clip.effectiveDurationUs / 1000L / 2).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Fade", color = TextPrimary) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Fade In", color = TextSecondary); Text("${fadeIn}ms", color = AccentPrimary)
                }
                Slider(
                    value = fadeIn.toFloat(),
                    onValueChange = { fadeIn = it.toInt() },
                    valueRange = 0f..maxMs.toFloat().coerceAtLeast(100f),
                    colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Fade Out", color = TextSecondary); Text("${fadeOut}ms", color = AccentPrimary)
                }
                Slider(
                    value = fadeOut.toFloat(),
                    onValueChange = { fadeOut = it.toInt() },
                    valueRange = 0f..maxMs.toFloat().coerceAtLeast(100f),
                    colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(fadeIn, fadeOut); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
private fun EditorActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = TextPrimary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else TextTertiary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (enabled) TextSecondary else TextTertiary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AspectRatioDialog(
    currentAspectRatio: AspectRatio,
    onSelect: (AspectRatio) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = AccentPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Canvas Aspect Ratio", color = TextPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Choose the framing aspect ratio for your video canvas and export:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))

                val options = listOf(
                    AspectRatio.R_16_9,
                    AspectRatio.R_9_16,
                    AspectRatio.R_1_1,
                    AspectRatio.R_4_5,
                    AspectRatio.R_4_3,
                    AspectRatio.R_21_9
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(options) { ar ->
                        val isSelected = currentAspectRatio == ar
                        Surface(
                            color = if (isSelected) AccentPrimary.copy(alpha = 0.2f) else DarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) AccentPrimary else DarkSurfaceBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(ar)
                                    onDismiss()
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                            ) {
                                // Miniature aspect frame preview
                                val frameW = when (ar) {
                                    AspectRatio.R_16_9 -> 32.dp
                                    AspectRatio.R_9_16 -> 18.dp
                                    AspectRatio.R_1_1 -> 24.dp
                                    AspectRatio.R_4_5 -> 20.dp
                                    AspectRatio.R_4_3 -> 28.dp
                                    AspectRatio.R_21_9 -> 36.dp
                                    else -> 28.dp
                                }
                                val frameH = when (ar) {
                                    AspectRatio.R_16_9 -> 18.dp
                                    AspectRatio.R_9_16 -> 32.dp
                                    AspectRatio.R_1_1 -> 24.dp
                                    AspectRatio.R_4_5 -> 25.dp
                                    AspectRatio.R_4_3 -> 21.dp
                                    AspectRatio.R_21_9 -> 15.dp
                                    else -> 20.dp
                                }
                                Box(
                                    modifier = Modifier
                                        .size(frameW, frameH)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isSelected) AccentPrimary else TextTertiary,
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                        .background(
                                            if (isSelected) AccentPrimary.copy(alpha = 0.4f) else DarkSurfaceBorder.copy(alpha = 0.2f)
                                        )
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = ar.label,
                                    color = if (isSelected) AccentPrimary else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = ar.description,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}
