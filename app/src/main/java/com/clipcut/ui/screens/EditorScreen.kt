package com.clipcut.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.OptIn
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.clipcut.core.model.MediaType
import com.clipcut.ui.theme.*
import com.clipcut.ui.viewmodel.EditorViewModel

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
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

    // Media pickers
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMedia(it, MediaType.VIDEO) }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMedia(it, MediaType.AUDIO) }
    }

    val selectedClip = remember(project, selectedClipId) {
        project?.videoClips?.find { it.id == selectedClipId }
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
                    IconButton(onClick = onOpenLogs) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = "Logs",
                            tint = AccentPrimary
                        )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Video Preview Player Area
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
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = viewModel.playerManager.initialize()
                                useController = false // Custom UI overlay
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay live preview text
                    project?.overlays?.forEach { overlay ->
                        if (currentPositionUs in overlay.timelineStartUs..overlay.timelineEndUs) {
                            Text(
                                text = overlay.text,
                                color = androidx.compose.ui.graphics.Color(overlay.textColor),
                                fontSize = overlay.textSizeSp.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(8.dp)
                            )
                        }
                    }

                    // Play/Pause Center Indicator (semi-transparent when playing)
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
                    }
                }
            }

            // Dedicated Playback Transport Controls Bar
            if (project?.videoClips?.isNotEmpty() == true) {
                Surface(
                    color = DarkSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isAtEnd = totalDurationUs > 0 && currentPositionUs >= (totalDurationUs - 50_000L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind to start
                        IconButton(
                            onClick = { viewModel.playerManager.seekToUs(0L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Start",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Step backward 1 sec
                        IconButton(
                            onClick = { viewModel.playerManager.seekByUs(-1_000_000L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.FastRewind,
                                contentDescription = "-1s",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Big Play / Pause / Replay Button
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
                                contentDescription = if (isPlaying) "Pause" else if (isAtEnd) "Replay" else "Play",
                                tint = TextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Step forward 1 sec
                        IconButton(
                            onClick = { viewModel.playerManager.seekByUs(1_000_000L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = "+1s",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Jump to end
                        IconButton(
                            onClick = { viewModel.playerManager.seekToUs(totalDurationUs) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "End",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Timeline View
            TimelineView(
                videoClips = project?.videoClips ?: emptyList(),
                audioTracks = project?.audioTracks ?: emptyList(),
                overlays = project?.overlays ?: emptyList(),
                selectedClipId = selectedClipId,
                currentPositionUs = currentPositionUs,
                totalDurationUs = totalDurationUs,
                onSelectClip = { id -> viewModel.selectClip(id) },
                onSeek = { targetUs -> viewModel.playerManager.seekToUs(targetUs) },
                onAddMediaClick = { videoPickerLauncher.launch("video/*") }
            )

            // Bottom Action Toolbar
            Surface(
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EditorActionButton(
                        icon = Icons.Default.Add,
                        label = "Add",
                        onClick = { videoPickerLauncher.launch("video/*") }
                    )

                    EditorActionButton(
                        icon = Icons.Default.ContentCut,
                        label = "Split",
                        enabled = selectedClip != null && currentPositionUs > 30_000L && currentPositionUs < (totalDurationUs - 30_000L),
                        onClick = { viewModel.splitClipAtPlayhead() }
                    )

                    EditorActionButton(
                        icon = Icons.Default.Speed,
                        label = "Speed",
                        enabled = selectedClip != null,
                        onClick = { showSpeedDialog = true }
                    )

                    EditorActionButton(
                        icon = Icons.Default.Tune,
                        label = "Filters",
                        enabled = selectedClip != null,
                        onClick = { showFilterSheet = true }
                    )

                    EditorActionButton(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        onClick = { showOverlaySheet = true }
                    )

                    EditorActionButton(
                        icon = Icons.Default.MusicNote,
                        label = "Audio",
                        onClick = { showAudioSheet = true }
                    )

                    EditorActionButton(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = AccentPrimary,
                        enabled = selectedClip != null,
                        onClick = { selectedClipId?.let { viewModel.deleteClip(it) } }
                    )
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet && selectedClip != null) {
        FilterSheet(
            initialConfig = selectedClip.filterConfig,
            onApplyFilter = { newConfig ->
                viewModel.setClipFilter(selectedClip.id, newConfig)
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Overlay Bottom Sheet
    if (showOverlaySheet) {
        OverlaySheet(
            totalDurationUs = totalDurationUs,
            onAddOverlay = { overlay -> viewModel.addOverlay(overlay) },
            onDismiss = { showOverlaySheet = false }
        )
    }

    // Audio Bottom Sheet
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

    // Speed Selection Dialog
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

    // Export Dialog
    if (showExportDialog || exportState !is com.clipcut.ui.viewmodel.ExportState.Idle) {
        ExportDialog(
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
