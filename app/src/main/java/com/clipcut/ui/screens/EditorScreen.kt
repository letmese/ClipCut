package com.clipcut.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.AndroidExternalSurface
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Delete
import com.clipcut.core.model.AspectRatio
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.MediaType
import com.clipcut.ui.theme.HairlineBorder
import com.clipcut.ui.theme.TextSecondary
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
import com.clipcut.ui.theme.Surface3

/**
 * EditorScreen — composes preview, timeline, toolbar and the unified bottom
 * sheet. Tool selection routes the sheet content.
 *
 * GlPreviewRenderer is invoked indirectly: EditorViewModel owns the player
 * and renderer; here we surface the player position to UI flows and call
 * viewModel mutations on tool-sheet callbacks.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    var activeSpecialSheet by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var selectedPipId by remember { mutableStateOf<String?>(null) }

    val ctx = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val type = ctx.contentResolver.getType(uri) ?: ""
            val mediaType = if (type.startsWith("image/")) MediaType.IMAGE else MediaType.VIDEO
            viewModel.importMedia(uri, mediaType)
        }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.addAudioTrack(uri)
        }
    }

    LaunchedEffect(projectId) { viewModel.loadProject(projectId) }

    LaunchedEffect(selectedOverlayId) {
        viewModel.previewRenderer.activeEditingOverlayId = selectedOverlayId
        viewModel.previewRenderer.requestRender()
    }

    LaunchedEffect(selectedPipId) {
        viewModel.previewRenderer.activeEditingPipId = selectedPipId
        viewModel.previewRenderer.requestRender()
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            selectedOverlayId = null
            selectedPipId = null
        }
    }

    DisposableEffect(projectId) {
        onDispose {
            viewModel.playerManager.pause()
            viewModel.previewRenderer.pause()
        }
    }

    var showDeleteProjectDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            EditorTopBar(
                onBack = onNavigateBack,
                onLogs = onOpenLogs,
                onExport = {
                    viewModel.playerManager.pause()
                    showExportDialog = true
                },
                onDelete = { showDeleteProjectDialog = true },
                title = project?.name ?: "Loading…",
                currentAspectRatio = project?.aspectRatio ?: AspectRatio.R_16_9,
                onSelectAspectRatio = { ar -> viewModel.setProjectAspectRatio(ar) }
            )
        },
        bottomBar = {
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
                canRedo = canRedo,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Preview surface (placeholder gradient — GL surface wired by GlPreviewRenderer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                    AndroidExternalSurface(
                        modifier = Modifier.fillMaxSize(),
                        isOpaque = true
                    ) {
                        onSurface { surface, width, height ->
                            if (width <= 0 || height <= 0) return@onSurface
                            viewModel.previewRenderer.onSurfaceCreated(surface, width, height)
                            surface.onDestroyed {
                                viewModel.previewRenderer.onSurfaceDestroyed()
                            }
                            surface.onChanged { newWidth, newHeight ->
                                if (newWidth > 0 && newHeight > 0) {
                                    viewModel.previewRenderer.onSurfaceChanged(newWidth, newHeight)
                                }
                            }
                        }
                    }
                }
                // Interactive Sticker / Overlay / PiP Canvas Manipulation
                var previewBoxSize by remember { mutableStateOf(IntSize.Zero) }
                var previewCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { previewBoxSize = it }
                        .onGloballyPositioned { previewCoords = it }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                selectedOverlayId = null
                                selectedPipId = null
                            }
                        }
                ) {
                    val activeOverlays = project?.overlays?.filter {
                        positionUs in it.timelineStartUs..it.timelineEndUs
                    } ?: emptyList()

                    val activePips = project?.pipTracks?.filter {
                        positionUs in it.timelineStartUs..it.timelineEndUs && it.uri.isNotEmpty()
                    } ?: emptyList()

                    val bw = previewBoxSize.width.toFloat()
                    val bh = previewBoxSize.height.toFloat()

                    if (bw > 0f && bh > 0f) {
                        // ── Render Interactive Overlays & Stickers ──
                        activeOverlays.forEach { overlay ->
                            val isSelected = overlay.id == selectedOverlayId

                            if (isSelected) {
                                var localNormX by remember(overlay.id) { mutableFloatStateOf(overlay.normalizedX) }
                                var localNormY by remember(overlay.id) { mutableFloatStateOf(overlay.normalizedY) }
                                var localScale by remember(overlay.id) { mutableFloatStateOf(overlay.scale) }
                                var localRotation by remember(overlay.id) { mutableFloatStateOf(overlay.rotation) }
                                var contentSize by remember(overlay.id) { mutableStateOf(IntSize.Zero) }
                                var rotateHandleCoords by remember(overlay.id) { mutableStateOf<LayoutCoordinates?>(null) }
                                var rotateStartAngleDeg by remember(overlay.id) { mutableFloatStateOf(0f) }
                                var rotateStartRotation by remember(overlay.id) { mutableFloatStateOf(0f) }

                                LaunchedEffect(overlay.normalizedX, overlay.normalizedY, overlay.scale, overlay.rotation) {
                                    localNormX = overlay.normalizedX
                                    localNormY = overlay.normalizedY
                                    localScale = overlay.scale
                                    localRotation = overlay.rotation
                                }

                                val currentPixelX = localNormX * bw
                                val currentPixelY = localNormY * bh

                                // Parent bounding box centered at (currentPixelX, currentPixelY)
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                (currentPixelX - 100.dp.toPx()).toInt(),
                                                (currentPixelY - 70.dp.toPx()).toInt()
                                            )
                                        }
                                        .size(200.dp, 140.dp)
                                        .graphicsLayer {
                                            scaleX = localScale
                                            scaleY = localScale
                                            rotationZ = localRotation
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Main sticker body with rotation-compensated 1-finger drag physics
                                    Box(
                                        modifier = Modifier
                                            .border(
                                                width = 1.5.dp,
                                                color = AccentPrimary,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .background(
                                                color = AccentPrimary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .pointerInput(overlay.id, bw, bh) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        selectedOverlayId = overlay.id
                                                        selectedPipId = null
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        localNormX = (localNormX + dragAmount.x / bw).coerceIn(0.02f, 0.98f)
                                                        localNormY = (localNormY + dragAmount.y / bh).coerceIn(0.02f, 0.98f)
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, localRotation)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, localRotation)
                                                    }
                                                )
                                            }
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = overlay.text.ifEmpty { "Sticker" },
                                            color = Color(overlay.textColor),
                                            fontSize = overlay.textSizeSp.sp,
                                            fontWeight = if (overlay.textBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (overlay.textItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2
                                        )
                                    }

                                    // Top-Right: Delete button (40dp easy tap target)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(40.dp)
                                            .background(Color(0xFFE53935), CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                val toDel = overlay.id
                                                selectedOverlayId = null
                                                viewModel.removeOverlay(toDel)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Delete Overlay",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Bottom-Right: Dedicated Scale / Resize Handle
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(40.dp)
                                            .background(AccentPrimary, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .pointerInput(overlay.id) {
                                                detectDragGestures(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val delta = (dragAmount.x + dragAmount.y) / (bw * 0.25f)
                                                        localScale = (localScale + delta).coerceIn(0.2f, 4.0f)
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, localRotation)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, localRotation)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.OpenInFull,
                                            contentDescription = "Scale Overlay",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Bottom-Left: Dedicated Rotate Handle (360° circular follow-finger rotation)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .size(40.dp)
                                            .background(Surface3, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .onGloballyPositioned { rotateHandleCoords = it }
                                            .pointerInput(overlay.id, bw, bh) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        selectedOverlayId = overlay.id
                                                        rotateStartRotation = localRotation
                                                        val touchPos = try {
                                                            if (previewCoords?.isAttached == true && rotateHandleCoords?.isAttached == true) {
                                                                previewCoords?.localPositionOf(rotateHandleCoords!!, offset)
                                                            } else null
                                                        } catch (_: Throwable) { null }
                                                        if (touchPos != null) {
                                                            val dx = touchPos.x - (localNormX * bw)
                                                            val dy = touchPos.y - (localNormY * bh)
                                                            rotateStartAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                                        }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val touchPos = try {
                                                            if (previewCoords?.isAttached == true && rotateHandleCoords?.isAttached == true) {
                                                                previewCoords?.localPositionOf(rotateHandleCoords!!, change.position)
                                                            } else null
                                                        } catch (_: Throwable) { null }
                                                        if (touchPos != null) {
                                                            val dx = touchPos.x - (localNormX * bw)
                                                            val dy = touchPos.y - (localNormY * bh)
                                                            val currentAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                                            var deltaDeg = currentAngleDeg - rotateStartAngleDeg
                                                            while (deltaDeg > 180f) deltaDeg -= 360f
                                                            while (deltaDeg < -180f) deltaDeg += 360f
                                                            localRotation = ((rotateStartRotation + deltaDeg) % 360f + 360f) % 360f
                                                        } else {
                                                            val rotDelta = dragAmount.x * 1.0f
                                                            localRotation = ((localRotation + rotDelta) % 360f + 360f) % 360f
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, localRotation)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, localRotation)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.RotateRight,
                                            contentDescription = "Rotate Overlay",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Top-Left: Reset Rotation to 0°
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .size(40.dp)
                                            .background(Surface2, CircleShape)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                localRotation = 0f
                                                viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, 0f)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FilterCenterFocus,
                                            contentDescription = "Reset Angle",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                // Tap target for unselected overlay
                                val unselectedPixelX = overlay.normalizedX * bw
                                val unselectedPixelY = overlay.normalizedY * bh
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                (unselectedPixelX - 60.dp.toPx()).toInt(),
                                                (unselectedPixelY - 45.dp.toPx()).toInt()
                                            )
                                        }
                                        .size(120.dp, 90.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selectedOverlayId = overlay.id
                                            selectedPipId = null
                                        }
                                )
                            }
                        }

                        // ── Render Interactive PiP Layers (Video & Image Overlays) ──
                        activePips.forEach { pip ->
                            val isPipSelected = pip.id == selectedPipId

                            if (isPipSelected) {
                                var localPipNormX by remember(pip.id) { mutableFloatStateOf(pip.normalizedX) }
                                var localPipNormY by remember(pip.id) { mutableFloatStateOf(pip.normalizedY) }
                                var localPipScale by remember(pip.id) { mutableFloatStateOf(pip.scale) }
                                var localPipRotation by remember(pip.id) { mutableFloatStateOf(pip.rotation) }
                                var pipContentSize by remember(pip.id) { mutableStateOf(IntSize.Zero) }
                                var pipRotateHandleCoords by remember(pip.id) { mutableStateOf<LayoutCoordinates?>(null) }
                                var pipRotateStartAngleDeg by remember(pip.id) { mutableFloatStateOf(0f) }
                                var pipRotateStartRotation by remember(pip.id) { mutableFloatStateOf(0f) }

                                LaunchedEffect(pip.normalizedX, pip.normalizedY, pip.scale, pip.rotation) {
                                    localPipNormX = pip.normalizedX
                                    localPipNormY = pip.normalizedY
                                    localPipScale = pip.scale
                                    localPipRotation = pip.rotation
                                }

                                val pipPixelX = localPipNormX * bw
                                val pipPixelY = localPipNormY * bh

                                // Parent bounding box centered at (pipPixelX, pipPixelY)
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                (pipPixelX - 110.dp.toPx()).toInt(),
                                                (pipPixelY - 80.dp.toPx()).toInt()
                                            )
                                        }
                                        .size(220.dp, 160.dp)
                                        .graphicsLayer {
                                            scaleX = localPipScale
                                            scaleY = localPipScale
                                            rotationZ = localPipRotation
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // PiP visual card bounding box with transparent body and live drag feedback
                                    Box(
                                        modifier = Modifier
                                            .size(150.dp, 90.dp)
                                            .border(
                                                width = 2.dp,
                                                color = AccentPrimary,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .background(
                                                color = Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .pointerInput(pip.id, bw, bh) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        selectedPipId = pip.id
                                                        selectedOverlayId = null
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        localPipNormX = (localPipNormX + dragAmount.x / bw).coerceIn(0.02f, 0.98f)
                                                        localPipNormY = (localPipNormY + dragAmount.y / bh).coerceIn(0.02f, 0.98f)
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, null, null, null, null)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, null, null, null, null)
                                                    }
                                                )
                                            }
                                    )

                                    // Top-Right: Delete PiP button (40dp easy tap target)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(40.dp)
                                            .background(Color(0xFFE53935), CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                selectedPipId = null
                                                viewModel.previewRenderer.setEditingPipTransform(null, null, null, null, null)
                                                viewModel.removePipTrack(pip.id)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Delete PiP",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Bottom-Right: Dedicated Scale / Resize handle
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(40.dp)
                                            .background(AccentPrimary, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .pointerInput(pip.id) {
                                                detectDragGestures(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val scaleDelta = (dragAmount.x + dragAmount.y) / (bw * 0.30f)
                                                        localPipScale = (localPipScale + scaleDelta).coerceIn(0.15f, 3.0f)
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, null, null, null, null)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, null, null, null, null)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.OpenInFull,
                                            contentDescription = "Resize PiP",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Bottom-Left: Dedicated Rotate handle (360° circular follow-finger rotation)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .size(40.dp)
                                            .background(Surface3, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .onGloballyPositioned { pipRotateHandleCoords = it }
                                            .pointerInput(pip.id, bw, bh) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        selectedPipId = pip.id
                                                        pipRotateStartRotation = localPipRotation
                                                        val touchPos = try {
                                                            if (previewCoords?.isAttached == true && pipRotateHandleCoords?.isAttached == true) {
                                                                previewCoords?.localPositionOf(pipRotateHandleCoords!!, offset)
                                                            } else null
                                                        } catch (_: Throwable) { null }
                                                        if (touchPos != null) {
                                                            val dx = touchPos.x - (localPipNormX * bw)
                                                            val dy = touchPos.y - (localPipNormY * bh)
                                                            pipRotateStartAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                                        }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val touchPos = try {
                                                            if (previewCoords?.isAttached == true && pipRotateHandleCoords?.isAttached == true) {
                                                                previewCoords?.localPositionOf(pipRotateHandleCoords!!, change.position)
                                                            } else null
                                                        } catch (_: Throwable) { null }
                                                        if (touchPos != null) {
                                                            val dx = touchPos.x - (localPipNormX * bw)
                                                            val dy = touchPos.y - (localPipNormY * bh)
                                                            val currentAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                                            var deltaDeg = currentAngleDeg - pipRotateStartAngleDeg
                                                            while (deltaDeg > 180f) deltaDeg -= 360f
                                                            while (deltaDeg < -180f) deltaDeg += 360f
                                                            localPipRotation = ((pipRotateStartRotation + deltaDeg) % 360f + 360f) % 360f
                                                        } else {
                                                            val rotDelta = dragAmount.x * 1.0f
                                                            localPipRotation = ((localPipRotation + rotDelta) % 360f + 360f) % 360f
                                                        }
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, null, null, null, null)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, localPipRotation)
                                                        viewModel.previewRenderer.setEditingPipTransform(pip.id, null, null, null, null)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.RotateRight,
                                            contentDescription = "Rotate PiP",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Top-Left: Reset Rotation to 0°
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .size(40.dp)
                                            .background(Surface2, CircleShape)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                localPipRotation = 0f
                                                viewModel.previewRenderer.setEditingPipTransform(pip.id, null, null, null, null)
                                                viewModel.commitPipTransform(pip.id, localPipNormX, localPipNormY, localPipScale, 0f)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FilterCenterFocus,
                                            contentDescription = "Reset PiP Angle",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                // Tap target for unselected PiP
                                val unselectedPipX = pip.normalizedX * bw
                                val unselectedPipY = pip.normalizedY * bh
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                (unselectedPipX - 60.dp.toPx()).toInt(),
                                                (unselectedPipY - 40.dp.toPx()).toInt()
                                            )
                                        }
                                        .size(120.dp, 80.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selectedPipId = pip.id
                                            selectedOverlayId = null
                                        }
                                )
                            }
                        }
                    }

                    // ── Floating Quick Controls Bar for Selected Overlay ──
                    val activeOverlay = project?.overlays?.find { it.id == selectedOverlayId }
                    if (activeOverlay != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xDD1E1E2E),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val newScale = (activeOverlay.scale - 0.15f).coerceAtLeast(0.2f)
                                        viewModel.commitOverlayTransform(activeOverlay.id, activeOverlay.normalizedX, activeOverlay.normalizedY, newScale, activeOverlay.rotation)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, "Smaller", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                Text(
                                    text = "${(activeOverlay.scale * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        val newScale = (activeOverlay.scale + 0.15f).coerceAtMost(5.0f)
                                        viewModel.commitOverlayTransform(activeOverlay.id, activeOverlay.normalizedX, activeOverlay.normalizedY, newScale, activeOverlay.rotation)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Add, "Bigger", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.2f)))

                                IconButton(
                                    onClick = {
                                        val newRot = (activeOverlay.rotation + 90f) % 360f
                                        viewModel.commitOverlayTransform(activeOverlay.id, activeOverlay.normalizedX, activeOverlay.normalizedY, activeOverlay.scale, newRot)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.RotateRight, "Rotate 90°", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.commitOverlayTransform(activeOverlay.id, 0.5f, 0.5f, activeOverlay.scale, activeOverlay.rotation)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.FilterCenterFocus, "Center", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.duplicateOverlay(activeOverlay.id)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, "Duplicate", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val toDel = activeOverlay.id
                                        selectedOverlayId = null
                                        viewModel.removeOverlay(toDel)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { selectedOverlayId = null },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(AccentPrimary, CircleShape)
                                ) {
                                    Icon(Icons.Filled.Check, "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // ── Floating Quick Controls Bar for Selected PiP ──
                    val activePip = project?.pipTracks?.find { it.id == selectedPipId }
                    if (activePip != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xDD1E1E2E),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val newScale = (activePip.scale - 0.1f).coerceAtLeast(0.15f)
                                        viewModel.commitPipTransform(activePip.id, activePip.normalizedX, activePip.normalizedY, newScale, activePip.rotation)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, "Smaller", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                Text(
                                    text = "${(activePip.scale * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        val newScale = (activePip.scale + 0.1f).coerceAtMost(3.0f)
                                        viewModel.commitPipTransform(activePip.id, activePip.normalizedX, activePip.normalizedY, newScale, activePip.rotation)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Add, "Bigger", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.2f)))

                                IconButton(
                                    onClick = {
                                        val newRot = (activePip.rotation + 90f) % 360f
                                        viewModel.commitPipTransform(activePip.id, activePip.normalizedX, activePip.normalizedY, activePip.scale, newRot)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.RotateRight, "Rotate 90°", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.commitPipTransform(activePip.id, 0.5f, 0.5f, activePip.scale, activePip.rotation)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.FilterCenterFocus, "Center", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.duplicatePipTrack(activePip.id)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, "Duplicate", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val toDel = activePip.id
                                        selectedPipId = null
                                        viewModel.removePipTrack(toDel)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { selectedPipId = null },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(AccentPrimary, CircleShape)
                                ) {
                                    Icon(Icons.Filled.Check, "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // Play/pause overlay
                if (selectedOverlayId == null && selectedPipId == null) {
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
            }
            // Timeline
            TimelineView(
                videoClips = project?.videoClips ?: emptyList(),
                audioTracks = project?.audioTracks ?: emptyList(),
                overlays = project?.overlays ?: emptyList(),
                pipTracks = project?.pipTracks ?: emptyList(),
                beatMarkers = project?.beatMarkers ?: emptyList(),
                selectedClipId = selectedClipId,
                selectedOverlayId = selectedOverlayId,
                selectedPipId = selectedPipId,
                currentPositionUs = positionUs,
                totalDurationUs = project?.totalDurationUs ?: 0L,
                isPlaying = isPlaying,
                onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                onStop = {
                    viewModel.playerManager.pause()
                    viewModel.playerManager.seekToUs(0L)
                },
                onSelectClip = { viewModel.selectClip(it) },
                onSeek = { viewModel.playerManager.seekToUs(it) },
                onAddMediaClick = { pickMedia.launch(arrayOf("video/*", "image/*")) },
                onTrimClip = { id, s, e -> viewModel.trimClip(id, s, e) },
                onSelectAudioTrack = { selectedTool = EditorTool.AUDIO },
                onTrimAudioTrack = { id, s, e -> viewModel.trimAudioTrack(id, s, e) },
                onSelectOverlay = { overlay ->
                    selectedOverlayId = if (selectedOverlayId == overlay.id) null else overlay.id
                    selectedPipId = null
                },
                onTrimOverlay = { id, s, e -> viewModel.trimOverlay(id, s, e) },
                onSelectPip = { pip ->
                    selectedPipId = if (selectedPipId == pip.id) null else pip.id
                    selectedOverlayId = null
                },
                onTrimPip = { id, s, e -> viewModel.trimPipTrack(id, s, e) },
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            )
        }
    }

    // Bottom sheet host routes content based on selectedTool.
    val tool = selectedTool
    val sid = selectedClipId
    val clip = project?.videoClips?.firstOrNull { it.id == sid }
    UnifiedBottomSheetHost(
        title = when (activeSpecialSheet) {
            "voiceover" -> "Voiceover"
            "captions" -> "Auto Captions"
            "pip" -> "PiP Layer"
            else -> tool?.label ?: ""
        },
        onDone = { selectedTool = null; activeSpecialSheet = null },
        onDismiss = { selectedTool = null; activeSpecialSheet = null },
        visible = tool != null || activeSpecialSheet != null
    ) {
        if (activeSpecialSheet != null) {
            when (activeSpecialSheet) {
                "voiceover" -> VoiceoverSheet(
                    playheadUs = positionUs,
                    onVoiceoverRecorded = { viewModel.addAudioTrack(it) },
                    onDismiss = { activeSpecialSheet = null }
                )
                "captions" -> CaptionSheet(
                    onApplyCaptions = { it.forEach { ov -> viewModel.addOverlay(ov) } },
                    onDismiss = { activeSpecialSheet = null }
                )
                "pip" -> PiPPanel(
                    playheadUs = positionUs,
                    onAdd = {
                        viewModel.addPipTrack(it)
                        selectedPipId = it.id
                        selectedOverlayId = null
                        activeSpecialSheet = null
                    },
                    onDismiss = { activeSpecialSheet = null }
                )
            }
        } else {
            when (tool) {
                EditorTool.AUDIO -> AudioSheet(
                    originalVideoVolume = project?.originalVideoVolume ?: 1f,
                    backgroundMusicVolume = project?.backgroundMusicVolume ?: 1f,
                    audioTracks = project?.audioTracks ?: emptyList(),
                    hasMusicTrack = (project?.audioTracks?.isNotEmpty() == true),
                    onVolumesChanged = { o, m -> viewModel.setVolumes(o, m) },
                    onPickMusicClick = { pickAudio.launch("audio/*") },
                    onDeleteTrack = { id -> viewModel.removeAudioTrack(id) },
                    onTrimTrack = { id, s, e -> viewModel.trimAudioTrack(id, s, e) },
                    onVoiceoverClick = { activeSpecialSheet = "voiceover" },
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
                        viewModel.setClipTransition(it, type, dur)
                    }
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.TRIM -> TrimSheet(
                startUs = clip?.trimStartUs ?: 0L,
                endUs = clip?.trimEndUs ?: (clip?.sourceDurationUs ?: 5_000_000L),
                onApply = { s, e -> sid?.let { viewModel.trimClip(it, s, e) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.FILTER, EditorTool.ADJUST -> FilterSheet(
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
                playheadUs = positionUs,
                totalDurationUs = project?.totalDurationUs ?: 5_000_000L,
                onApply = { overlay ->
                    viewModel.addOverlay(overlay)
                    selectedOverlayId = overlay.id
                },
                onOpenCaptions = { activeSpecialSheet = "captions" },
                onDismiss = { selectedTool = null }
            )
            EditorTool.STICKER -> StickerSheet(
                onPick = { stickerRes ->
                    val newSticker = com.clipcut.core.model.OverlayItem(
                        text = stickerRes,
                        timelineStartUs = positionUs,
                        timelineEndUs = positionUs + 3_000_000L,
                        normalizedX = 0.5f,
                        normalizedY = 0.5f,
                        scale = 1.0f,
                        textSizeSp = 36f
                    )
                    viewModel.addOverlay(newSticker)
                    selectedOverlayId = newSticker.id
                    selectedTool = null
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.OVERLAY -> OverlaySheet(
                totalDurationUs = project?.totalDurationUs ?: 0L,
                initialPlayheadUs = positionUs,
                existingOverlays = project?.overlays ?: emptyList(),
                existingPips = project?.pipTracks ?: emptyList(),
                onAddOverlay = { o ->
                    viewModel.addOverlay(o)
                    selectedOverlayId = o.id
                    selectedPipId = null
                },
                onDeleteOverlay = { id ->
                    if (selectedOverlayId == id) selectedOverlayId = null
                    viewModel.removeOverlay(id)
                },
                onDeletePip = { id ->
                    if (selectedPipId == id) selectedPipId = null
                    viewModel.removePipTrack(id)
                },
                onTrimPip = { id, s, e -> viewModel.trimPipTrack(id, s, e) },
                onSeekPreview = { us -> viewModel.playerManager.seekToUs(us) },
                onOpenPiP = {
                    selectedTool = null
                    activeSpecialSheet = "pip"
                },
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
}

    if (showExportDialog) {
        ExportDialog(
            projectAspectRatio = project?.aspectRatio ?: com.clipcut.core.model.AspectRatio.R_16_9,
            exportState = exportState,
            onStartExport = { cfg ->
                viewModel.playerManager.pause()
                viewModel.startExport(cfg)
            },
            onCancelExport = { viewModel.cancelExport() },
            onDismiss = { showExportDialog = false; viewModel.dismissExportDialog() },
            onViewLogsClick = onOpenLogs
        )
    }

    if (showDeleteProjectDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProjectDialog = false },
            containerColor = Surface2,
            title = { Text("Delete Project?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${project?.name ?: "this project"}\"? All edits and clips will be permanently removed.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteProjectDialog = false
                        viewModel.deleteCurrentProject {
                            onNavigateBack()
                        }
                    }
                ) { Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProjectDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    onBack: () -> Unit,
    onLogs: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    title: String,
    currentAspectRatio: AspectRatio,
    onSelectAspectRatio: (AspectRatio) -> Unit
) {
    Surface(
        color = Surface1,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)

            var showAspectRatioMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.padding(end = 4.dp)) {
                OutlinedButton(
                    onClick = { showAspectRatioMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(currentAspectRatio.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                DropdownMenu(
                    expanded = showAspectRatioMenu,
                    onDismissRequest = { showAspectRatioMenu = false }
                ) {
                    AspectRatio.values().forEach { ar ->
                        DropdownMenuItem(
                            text = { Text("${ar.label} (${ar.description})") },
                            onClick = {
                                onSelectAspectRatio(ar)
                                showAspectRatioMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete Project", tint = Color(0xFFFF6B6B))
            }
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
