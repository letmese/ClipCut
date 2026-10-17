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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import com.clipcut.core.model.AspectRatio
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.MediaType
import com.clipcut.ui.theme.*
import com.clipcut.ui.theme.TextSecondary
import com.clipcut.ui.components.EditorToolbar
import com.clipcut.ui.components.EditorTool
import com.clipcut.ui.components.PrimaryButton
import com.clipcut.ui.components.SecondaryButton
import com.clipcut.ui.components.UnifiedBottomSheetHost
import com.clipcut.ui.components.TimelineView
import com.clipcut.ui.components.EmptyState
import com.clipcut.ui.components.FreeformCropPreviewOverlay
import com.clipcut.ui.components.CropControlBottomBar
import com.clipcut.ui.components.computePresetCrop
import com.clipcut.core.model.CropRect
import com.clipcut.ui.screens.AudioSheet
import com.clipcut.ui.screens.AudioExtractorSheet
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
import com.clipcut.ui.screens.SpeedRampDialog
import com.clipcut.ui.screens.EqualizerSheet
import com.clipcut.ui.screens.VoiceRemixSheet
import com.clipcut.ui.screens.BrandKitSheet
import com.clipcut.ui.theme.AccentPrimary
import com.clipcut.ui.theme.DarkBackground
import com.clipcut.ui.theme.Surface1
import com.clipcut.ui.theme.Surface2
import com.clipcut.ui.theme.Surface3

@Composable
private fun RenderOverlayVisualContent(
    overlay: com.clipcut.core.model.OverlayItem,
    isFrame: Boolean,
    isSky: Boolean,
    isStudioBg: Boolean,
    isAssetImage: Boolean,
    assetUri: String?,
    ctx: android.content.Context,
    modifier: Modifier = Modifier
) {
    if (isAssetImage && assetUri != null) {
        val clean = assetUri.removePrefix("asset://")
        val bmp = remember(assetUri) {
            try {
                ctx.assets.open(clean).use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }?.asImageBitmap()
            } catch (_: Throwable) { null }
        }
        if (bmp != null) {
            when {
                isStudioBg -> {
                    // Studio backdrops are rendered natively in OpenGL beneath the keyed video canvas.
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .border(1.5.dp, AccentPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xCC101319),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentPrimary),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Text(
                                text = "🎙️ Studio Backdrop Active (Background)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                isSky -> {
                    // Sky Replacement: Responsive bounding box that can be freely repositioned, scaled, and cropped
                    val crop = overlay.cropRect
                    val hasCrop = crop != null && crop.isValid
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .then(
                                if (hasCrop) {
                                    Modifier.graphicsLayer {
                                        clip = true
                                        shape = androidx.compose.ui.graphics.RectangleShape
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = "Sky Overlay",
                            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                            0.0f to Color.White,
                                            0.50f to Color.White,
                                            1.0f to Color.Transparent
                                        ),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                    )
                                }
                        )
                    }
                }
                isFrame -> {
                    val crop = overlay.cropRect
                    val hasCrop = crop != null && crop.isValid
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .then(
                                if (hasCrop) {
                                    Modifier.graphicsLayer {
                                        clip = true
                                        shape = androidx.compose.ui.graphics.RectangleShape
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = "Overlay Frame",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                else -> {
                    val crop = overlay.cropRect
                    val hasCrop = crop != null && crop.isValid
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .then(
                                if (hasCrop) {
                                    Modifier.graphicsLayer {
                                        clip = true
                                        shape = androidx.compose.ui.graphics.RectangleShape
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = "Sticker",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            Text(
                text = when {
                    isSky -> "🌤️ Sky"
                    isStudioBg -> "🎙️ Studio"
                    isFrame -> "🖼️ Frame"
                    else -> "🎭 Sticker"
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        // Text is rendered natively with WYSIWYG typography, fonts, drop shadows, stroke outlines,
        // and motion entrance animations directly on the OpenGL video canvas. We don't render a duplicate
        // static Compose Text layer on top of it.
    }
}

/**
 * Transforms a local pointer drag delta inside a scaled and rotated composable
 * back into the parent world/screen coordinate space.
 */
private fun toWorldDelta(localDelta: androidx.compose.ui.geometry.Offset, rotationDeg: Float, scale: Float = 1f): androidx.compose.ui.geometry.Offset {
    val rad = Math.toRadians(rotationDeg.toDouble())
    val cosA = kotlin.math.cos(rad).toFloat()
    val sinA = kotlin.math.sin(rad).toFloat()
    val worldX = (localDelta.x * cosA - localDelta.y * sinA) * scale
    val worldY = (localDelta.x * sinA + localDelta.y * cosA) * scale
    return androidx.compose.ui.geometry.Offset(worldX, worldY)
}

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
    var selectedBlurId by remember { mutableStateOf<String?>(null) }
    val trackingStatus by viewModel.trackingState.collectAsState()
    var isFingerPaintActive by remember { mutableStateOf(false) }
    var paintBrushWidth by remember { mutableFloatStateOf(0.06f) }
    var paintIsBlackout by remember { mutableStateOf(true) }
    var paintCensorStyle by remember { mutableStateOf("BLACKOUT") }
    var activeDrawingPoints by remember { mutableStateOf<List<com.clipcut.core.model.PaintPoint>>(emptyList()) }
    var currentTouchOffset by remember { mutableStateOf<Offset?>(null) }

    var isChromaPaintActive by remember { mutableStateOf(false) }
    var chromaPaintStudioUri by remember { mutableStateOf("overlays/backgrounds/human_seg_2.webp") }
    var chromaPaintViewMode by remember { mutableStateOf("STUDIO") } // "STUDIO" vs "GREEN"
    var chromaPaintBrushWidth by remember { mutableFloatStateOf(0.08f) }
    var chromaPaintIsEraser by remember { mutableStateOf(false) }
    var chromaPaintInverted by remember { mutableStateOf(false) }
    var activeChromaDrawingPoints by remember { mutableStateOf<List<com.clipcut.core.model.PaintPoint>>(emptyList()) }
    var chromaTouchOffset by remember { mutableStateOf<Offset?>(null) }

    var localCropRect by remember { mutableStateOf(CropRect(0f, 0f, 1f, 1f)) }
    var activeCropPresetId by remember { mutableStateOf("free") }
    var overlayCropTarget by remember { mutableStateOf<com.clipcut.core.model.OverlayItem?>(null) }
    var overlayCropRect by remember { mutableStateOf(CropRect(0f, 0f, 1f, 1f)) }
    var overlayCropPresetId by remember { mutableStateOf("free") }
    var previewBoxSize by remember { mutableStateOf(IntSize.Zero) }
    var previewCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val currentPlayheadUs = positionUs
    val targetClip = if (selectedClipId != null) {
        project?.videoClips?.firstOrNull { it.id == selectedClipId }
    } else {
        var acc = 0L
        project?.videoClips?.find { c ->
            val end = acc + c.effectiveDurationUs
            val hit = currentPlayheadUs in acc until end
            acc = end
            hit
        } ?: project?.videoClips?.firstOrNull()
    }
    val sid = selectedClipId ?: targetClip?.id

    LaunchedEffect(selectedTool) {
        if (selectedTool == EditorTool.CROP) {
            val clipToCrop = targetClip
            localCropRect = clipToCrop?.cropRect ?: CropRect(0f, 0f, 1f, 1f)
            activeCropPresetId = "free"
            viewModel.previewRenderer.isCropEditingActive = true
            viewModel.previewRenderer.requestRender()
        } else {
            if (viewModel.previewRenderer.isCropEditingActive) {
                viewModel.previewRenderer.isCropEditingActive = false
                viewModel.previewRenderer.requestRender()
            }
        }
    }

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
    val pickBlurImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val blurId = selectedBlurId
            if (blurId != null) {
                viewModel.updateBlurTrack(blurId) {
                    it.copy(
                        blurType = com.clipcut.core.model.BlurType.IMAGE,
                        customImageUri = uri.toString()
                    )
                }
            }
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
            selectedBlurId = null
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
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                title = project?.name ?: "Loading…",
                currentAspectRatio = project?.aspectRatio ?: AspectRatio.R_16_9,
                onSelectAspectRatio = { ar -> viewModel.setProjectAspectRatio(ar) }
            )
        },
        bottomBar = {
            if (selectedTool == EditorTool.CROP) {
                val bw = previewBoxSize.width.toFloat()
                val bh = previewBoxSize.height.toFloat()
                CropControlBottomBar(
                    activePresetId = activeCropPresetId,
                    onSelectPreset = { preset ->
                        activeCropPresetId = preset.id
                        if (preset.id == "full") {
                            localCropRect = CropRect(0f, 0f, 1f, 1f)
                        } else if (preset.ratio != null) {
                            localCropRect = computePresetCrop(preset.ratio, bw, bh)
                        }
                    },
                    onReset = {
                        localCropRect = CropRect(0f, 0f, 1f, 1f)
                        activeCropPresetId = "free"
                    },
                    onCancel = {
                        viewModel.previewRenderer.isCropEditingActive = false
                        viewModel.previewRenderer.requestRender()
                        selectedTool = null
                    },
                    onApply = {
                        viewModel.previewRenderer.isCropEditingActive = false
                        sid?.let {
                            viewModel.setClipCrop(it, localCropRect)
                            android.widget.Toast.makeText(ctx, "Crop applied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        viewModel.previewRenderer.requestRender()
                        selectedTool = null
                    }
                )
            } else {
                EditorToolbar(
                    selected = selectedTool,
                    onSelect = { tool ->
                        when (tool) {
                            EditorTool.UNDO -> viewModel.undo()
                            EditorTool.REDO -> viewModel.redo()
                            EditorTool.SPLIT -> viewModel.splitClipAtPlayhead()
                            EditorTool.DELETE -> {
                                val blurId = selectedBlurId
                                val ovId = selectedOverlayId
                                val pipId = selectedPipId
                                val clipId = selectedClipId
                                if (blurId != null) {
                                    viewModel.removeBlurTrack(blurId)
                                    selectedBlurId = null
                                    android.widget.Toast.makeText(ctx, "Blur deleted", android.widget.Toast.LENGTH_SHORT).show()
                                } else if (ovId != null) {
                                    viewModel.removeOverlay(ovId)
                                    selectedOverlayId = null
                                    android.widget.Toast.makeText(ctx, "Overlay deleted", android.widget.Toast.LENGTH_SHORT).show()
                                } else if (pipId != null) {
                                    viewModel.removePipTrack(pipId)
                                    selectedPipId = null
                                    android.widget.Toast.makeText(ctx, "PiP layer deleted", android.widget.Toast.LENGTH_SHORT).show()
                                } else if (clipId != null) {
                                    viewModel.deleteClip(clipId)
                                    viewModel.selectClip(null)
                                    android.widget.Toast.makeText(ctx, "Clip deleted", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val currentPlayhead = viewModel.playerManager.currentPositionUs.value
                                    var acc = 0L
                                    val clipAtPlayhead = project?.videoClips?.find { c ->
                                        val end = acc + c.effectiveDurationUs
                                        val hit = currentPlayhead in acc until end
                                        acc = end
                                        hit
                                    } ?: project?.videoClips?.lastOrNull()
                                    if (clipAtPlayhead != null) {
                                        viewModel.deleteClip(clipAtPlayhead.id)
                                        viewModel.selectClip(null)
                                        android.widget.Toast.makeText(ctx, "Clip deleted", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(ctx, "No clip to delete", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            else -> selectedTool = tool
                        }
                    },
                    canUndo = canUndo,
                    canRedo = canRedo,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { previewBoxSize = it }
                        .onGloballyPositioned { previewCoords = it }
                        .then(
                            if (!isFingerPaintActive && !isChromaPaintActive && selectedTool != EditorTool.CROP) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures {
                                        selectedOverlayId = null
                                        selectedPipId = null
                                        selectedBlurId = null
                                    }
                                }
                            } else Modifier
                        )
                ) {
                    val activeOverlays = project?.overlays?.filter {
                        it.id != "brand_kit_watermark" && positionUs in it.timelineStartUs..it.timelineEndUs
                    } ?: emptyList()

                    val activePips = project?.pipTracks?.filter {
                        positionUs in it.timelineStartUs..it.timelineEndUs && it.uri.isNotEmpty()
                    } ?: emptyList()

                    val bw = previewBoxSize.width.toFloat()
                    val bh = previewBoxSize.height.toFloat()

                    if (bw > 0f && bh > 0f) {
                        if (selectedTool == EditorTool.CROP) {
                            FreeformCropPreviewOverlay(
                                cropRect = localCropRect,
                                boxWidth = bw,
                                boxHeight = bh,
                                activePresetId = activeCropPresetId,
                                onPresetChange = { activeCropPresetId = it },
                                onCropChange = { newCrop ->
                                    localCropRect = newCrop
                                }
                            )
                        } else {
                            // ── Render Interactive Overlays & Stickers ──
                            activeOverlays.forEach { overlay ->
                            val isSelected = overlay.id == selectedOverlayId

                            val assetUri = when {
                                overlay.text.startsWith("asset://") || (!overlay.text.contains(" ") && (overlay.text.endsWith(".png") || overlay.text.endsWith(".webp"))) -> overlay.text
                                overlay.imageUri != null && (overlay.imageUri.startsWith("asset://") || overlay.imageUri.endsWith(".png") || overlay.imageUri.endsWith(".webp")) -> overlay.imageUri
                                else -> null
                            }
                            val isAssetImage = assetUri != null
                            val isSky = isAssetImage && (assetUri!!.contains("overlays/sky/") || assetUri.contains("PRESET_SYK"))
                            val isStudioBg = isAssetImage && (assetUri!!.contains("overlays/backgrounds/") || assetUri.contains("human_seg"))
                            val isFrame = isAssetImage && !isSky && !isStudioBg && (
                                assetUri!!.contains("overlays/frames/") ||
                                assetUri.contains("Camera") || assetUri.contains("Phone") ||
                                assetUri.contains("Photo") || assetUri.contains("Heart") ||
                                assetUri.contains("Circular") || assetUri.contains("Water") ||
                                assetUri.contains("Square") || assetUri.contains("Rectangle")
                            )

                            val density = LocalDensity.current
                            val boxW = when {
                                isFrame -> with(density) { (bw * 0.88f).toDp() }
                                isSky -> with(density) { (bw * 0.90f).toDp() }
                                isStudioBg -> with(density) { (bw * 0.85f).toDp() }
                                else -> 200.dp
                            }
                            val boxH = when {
                                isFrame -> with(density) { (bh * 0.88f).toDp() }
                                isSky -> with(density) { (bh * 0.52f).toDp() }
                                isStudioBg -> with(density) { (bh * 0.85f).toDp() }
                                else -> 140.dp
                            }
                            val boxHalfWPx = with(density) { (boxW / 2).toPx() }
                            val boxHalfHPx = with(density) { (boxH / 2).toPx() }

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

                                LaunchedEffect(localNormX, localNormY, localScale, localRotation) {
                                    viewModel.previewRenderer.setEditingOverlayTransform(
                                        overlay.id, localNormX, localNormY, localScale, localRotation
                                    )
                                }

                                DisposableEffect(overlay.id) {
                                    onDispose {
                                        viewModel.previewRenderer.setEditingOverlayTransform(null, null, null, null, null)
                                    }
                                }

                                val currentPixelX = localNormX * bw
                                val currentPixelY = localNormY * bh

                                // Parent bounding box centered at (currentPixelX, currentPixelY)
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                (currentPixelX - boxHalfWPx).toInt(),
                                                (currentPixelY - boxHalfHPx).toInt()
                                            )
                                        }
                                        .size(boxW, boxH)
                                        .graphicsLayer {
                                            scaleX = localScale
                                            scaleY = localScale
                                            rotationZ = localRotation
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Main sticker/frame body with rotation-compensated 1-finger drag physics
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .border(
                                                width = if (isFrame) 1.dp else 1.5.dp,
                                                color = if (isFrame) AccentPrimary.copy(alpha = 0.5f) else AccentPrimary,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .background(
                                                color = if (isFrame || isSky) Color.Transparent else AccentPrimary.copy(alpha = 0.15f),
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
                                                        val world = toWorldDelta(dragAmount, localRotation, localScale)
                                                        localNormX = (localNormX + world.x / bw).coerceIn(0.02f, 0.98f)
                                                        localNormY = (localNormY + world.y / bh).coerceIn(0.02f, 0.98f)
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, localRotation)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitOverlayTransform(overlay.id, localNormX, localNormY, localScale, localRotation)
                                                    }
                                                )
                                            }
                                            .padding(if (isFrame || isSky || isStudioBg) 0.dp else 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RenderOverlayVisualContent(
                                            overlay = overlay,
                                            isFrame = isFrame,
                                            isSky = isSky,
                                            isStudioBg = isStudioBg,
                                            isAssetImage = isAssetImage,
                                            assetUri = assetUri,
                                            ctx = ctx
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
                                                        val world = toWorldDelta(dragAmount, localRotation, localScale)
                                                        val rad = Math.toRadians(localRotation.toDouble())
                                                        val cosA = kotlin.math.cos(rad).toFloat()
                                                        val sinA = kotlin.math.sin(rad).toFloat()
                                                        val handleLx = boxHalfWPx - 20f
                                                        val handleLy = boxHalfHPx - 20f
                                                        val rx = (handleLx * cosA - handleLy * sinA) * localScale
                                                        val ry = (handleLx * sinA + handleLy * cosA) * localScale
                                                        val rLen = kotlin.math.sqrt(rx * rx + ry * ry).coerceAtLeast(1f)
                                                        val radialDrag = (rx * world.x + ry * world.y) / rLen
                                                        val baseDist = kotlin.math.sqrt(handleLx * handleLx + handleLy * handleLy).coerceAtLeast(1f)
                                                        val delta = radialDrag / baseDist
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
                                            .pointerInput(overlay.id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        selectedOverlayId = overlay.id
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val world = toWorldDelta(dragAmount, localRotation, localScale)
                                                        val rad = Math.toRadians(localRotation.toDouble())
                                                        val cosA = kotlin.math.cos(rad).toFloat()
                                                        val sinA = kotlin.math.sin(rad).toFloat()
                                                        val handleLx = -boxHalfWPx + 20f
                                                        val handleLy = boxHalfHPx - 20f
                                                        val rx = (handleLx * cosA - handleLy * sinA) * localScale
                                                        val ry = (handleLx * sinA + handleLy * cosA) * localScale
                                                        val lenSq = (rx * rx + ry * ry).coerceAtLeast(100f)
                                                        val dThetaRad = (rx * world.y - ry * world.x) / lenSq
                                                        val deltaDeg = Math.toDegrees(dThetaRad.toDouble()).toFloat()
                                                        localRotation = ((localRotation + deltaDeg) % 360f + 360f) % 360f
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

                                    // Top-Left: Dedicated Edit Button for Text Overlays OR Crop Button for Image/Frame/Sticker
                                    if (!isAssetImage && overlay.imageUri == null && !isStudioBg && !isSky && !isFrame) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .size(40.dp)
                                                .background(Color(0xFF29B6F6), CircleShape)
                                                .border(2.dp, Color.White, CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    selectedOverlayId = overlay.id
                                                    selectedTool = EditorTool.TEXT
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Edit Text",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .size(40.dp)
                                                .background(AccentPrimary, CircleShape)
                                                .border(2.dp, Color.White, CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    overlayCropTarget = overlay
                                                    overlayCropRect = overlay.cropRect ?: CropRect(0f, 0f, 1f, 1f)
                                                    overlayCropPresetId = "free"
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Crop,
                                                contentDescription = "Crop Overlay",
                                                tint = Color(0xFF0E1117),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Top-Center: Reset Rotation to 0°
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .size(36.dp)
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
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                // Tap target & persistent preview renderer for unselected overlay
                                val unselectedPixelX = overlay.normalizedX * bw
                                val unselectedPixelY = overlay.normalizedY * bh
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                (unselectedPixelX - boxHalfWPx).toInt(),
                                                (unselectedPixelY - boxHalfHPx).toInt()
                                            )
                                        }
                                        .size(boxW, boxH)
                                        .graphicsLayer {
                                            scaleX = overlay.scale
                                            scaleY = overlay.scale
                                            rotationZ = overlay.rotation
                                        }
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selectedOverlayId = overlay.id
                                            selectedPipId = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Touch target is transparent; OpenGL renders all active overlays on the video canvas beneath
                                }
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
                                                        val world = toWorldDelta(dragAmount, localPipRotation, localPipScale)
                                                        localPipNormX = (localPipNormX + world.x / bw).coerceIn(0.02f, 0.98f)
                                                        localPipNormY = (localPipNormY + world.y / bh).coerceIn(0.02f, 0.98f)
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
                                                val pipHalfWPx = with(density) { 110.dp.toPx() }
                                                val pipHalfHPx = with(density) { 80.dp.toPx() }
                                                detectDragGestures(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val world = toWorldDelta(dragAmount, localPipRotation, localPipScale)
                                                        val rad = Math.toRadians(localPipRotation.toDouble())
                                                        val cosA = kotlin.math.cos(rad).toFloat()
                                                        val sinA = kotlin.math.sin(rad).toFloat()
                                                        val handleLx = pipHalfWPx - 20f
                                                        val handleLy = pipHalfHPx - 20f
                                                        val rx = (handleLx * cosA - handleLy * sinA) * localPipScale
                                                        val ry = (handleLx * sinA + handleLy * cosA) * localPipScale
                                                        val rLen = kotlin.math.sqrt(rx * rx + ry * ry).coerceAtLeast(1f)
                                                        val radialDrag = (rx * world.x + ry * world.y) / rLen
                                                        val baseDist = kotlin.math.sqrt(handleLx * handleLx + handleLy * handleLy).coerceAtLeast(1f)
                                                        val scaleDelta = radialDrag / baseDist
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
                                            .pointerInput(pip.id) {
                                                val pipHalfWPx = with(density) { 110.dp.toPx() }
                                                val pipHalfHPx = with(density) { 80.dp.toPx() }
                                                detectDragGestures(
                                                    onDragStart = {
                                                        selectedPipId = pip.id
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val world = toWorldDelta(dragAmount, localPipRotation, localPipScale)
                                                        val rad = Math.toRadians(localPipRotation.toDouble())
                                                        val cosA = kotlin.math.cos(rad).toFloat()
                                                        val sinA = kotlin.math.sin(rad).toFloat()
                                                        val handleLx = -pipHalfWPx + 20f
                                                        val handleLy = pipHalfHPx - 20f
                                                        val rx = (handleLx * cosA - handleLy * sinA) * localPipScale
                                                        val ry = (handleLx * sinA + handleLy * cosA) * localPipScale
                                                        val lenSq = (rx * rx + ry * ry).coerceAtLeast(100f)
                                                        val dThetaRad = (rx * world.y - ry * world.x) / lenSq
                                                        val deltaDeg = Math.toDegrees(dThetaRad.toDouble()).toFloat()
                                                        localPipRotation = ((localPipRotation + deltaDeg) % 360f + 360f) % 360f
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
                                            selectedBlurId = null
                                        }
                                )
                            }
                        }

                        // ── Render Interactive Blur / Censor Tracks ──
                        val activeBlurs = project?.blurTracks?.filter {
                            it.blurType != com.clipcut.core.model.BlurType.PAINT &&
                            positionUs in it.timelineStartUs..it.timelineEndUs
                        } ?: emptyList()

                        activeBlurs.forEach { blur ->
                            val isBlurSelected = blur.id == selectedBlurId

                            if (isBlurSelected && !isFingerPaintActive) {
                                val currentTransform = blur.getTransformAt(positionUs)
                                var localNormX by remember(blur.id) { mutableFloatStateOf(currentTransform.normalizedX) }
                                var localNormY by remember(blur.id) { mutableFloatStateOf(currentTransform.normalizedY) }
                                var localWidth by remember(blur.id) { mutableFloatStateOf(currentTransform.width) }
                                var localHeight by remember(blur.id) { mutableFloatStateOf(currentTransform.height) }
                                var localRotation by remember(blur.id) { mutableFloatStateOf(currentTransform.rotation) }
                                var blurRotateHandleCoords by remember(blur.id) { mutableStateOf<LayoutCoordinates?>(null) }
                                var blurRotateStartAngleDeg by remember(blur.id) { mutableFloatStateOf(0f) }
                                var blurRotateStartRotation by remember(blur.id) { mutableFloatStateOf(0f) }

                                LaunchedEffect(positionUs, blur.id, blur.keyframes.size, blur.width, blur.height) {
                                    val t = blur.getTransformAt(positionUs)
                                    localNormX = t.normalizedX
                                    localNormY = t.normalizedY
                                    localWidth = t.width
                                    localHeight = t.height
                                    localRotation = t.rotation
                                }

                                val blurPixelX = localNormX * bw
                                val blurPixelY = localNormY * bh
                                val boxW = (localWidth * bw).coerceIn(48f, bw)
                                val boxH = (localHeight * bh).coerceIn(48f, bh)

                                val borderShape = when (blur.shape) {
                                    com.clipcut.core.model.BlurShape.OVAL -> CircleShape
                                    com.clipcut.core.model.BlurShape.ROUNDED_RECT -> RoundedCornerShape(12.dp)
                                    com.clipcut.core.model.BlurShape.RECTANGLE -> RoundedCornerShape(0.dp)
                                }

                                Box(
                                    modifier = Modifier
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                (blurPixelX - boxW / 2f).toInt(),
                                                (blurPixelY - boxH / 2f).toInt()
                                            )
                                        }
                                        .size(
                                            with(androidx.compose.ui.platform.LocalDensity.current) { boxW.toDp() },
                                            with(androidx.compose.ui.platform.LocalDensity.current) { boxH.toDp() }
                                        )
                                        .graphicsLayer {
                                            rotationZ = localRotation
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Main blur bounding box with cyan highlight & 1-finger drag
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .border(2.dp, AccentPrimary, borderShape)
                                            .background(AccentPrimary.copy(alpha = 0.12f), borderShape)
                                            .pointerInput(blur.id, bw, bh) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        selectedBlurId = blur.id
                                                        selectedOverlayId = null
                                                        selectedPipId = null
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val world = toWorldDelta(dragAmount, localRotation, 1f)
                                                        localNormX = (localNormX + world.x / bw).coerceIn(0.02f, 0.98f)
                                                        localNormY = (localNormY + world.y / bh).coerceIn(0.02f, 0.98f)
                                                        viewModel.previewRenderer.setEditingBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation)
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation, autoKeyframeAtPlayhead = (blur.keyframes.isNotEmpty() || selectedTool == EditorTool.BLUR))
                                                        viewModel.previewRenderer.setEditingBlurTransform(null, null, null, null, null, null)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation, autoKeyframeAtPlayhead = (blur.keyframes.isNotEmpty() || selectedTool == EditorTool.BLUR))
                                                        viewModel.previewRenderer.setEditingBlurTransform(null, null, null, null, null, null)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (blur.blurType) {
                                                com.clipcut.core.model.BlurType.IMAGE -> "STICKER"
                                                com.clipcut.core.model.BlurType.BLACKOUT -> "BLACKOUT"
                                                com.clipcut.core.model.BlurType.MOSAIC -> "MOSAIC"
                                                com.clipcut.core.model.BlurType.GAUSSIAN -> "BLUR"
                                                com.clipcut.core.model.BlurType.STACK_BLUR -> "STACKBLUR"
                                                com.clipcut.core.model.BlurType.MOTION_TRAIL -> "TRAIL"
                                                com.clipcut.core.model.BlurType.PAINT -> "PAINT"
                                                com.clipcut.core.model.BlurType.CHROMA_PAINT -> "STUDIO PAINT"
                                            },
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Top-Right: Delete button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = (-6).dp)
                                            .size(34.dp)
                                            .background(Color(0xFFE53935), CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                val toDel = blur.id
                                                selectedBlurId = null
                                                viewModel.removeBlurTrack(toDel)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Delete Blur",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Bottom-Right: Freeform Resize handle (prominent 44dp touch target)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 8.dp, y = 8.dp)
                                            .size(44.dp)
                                            .background(AccentPrimary, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                            .pointerInput(blur.id, bw, bh) {
                                                detectDragGestures(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val deltaW = (dragAmount.x * 2f) / bw
                                                        val deltaH = (dragAmount.y * 2f) / bh
                                                        localWidth = (localWidth + deltaW).coerceIn(0.04f, 1.0f)
                                                        localHeight = (localHeight + deltaH).coerceIn(0.04f, 1.0f)
                                                        viewModel.previewRenderer.setEditingBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation)
                                                    },
                                                    onDragEnd = {
                                                        viewModel.commitBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation, autoKeyframeAtPlayhead = (blur.keyframes.isNotEmpty() || selectedTool == EditorTool.BLUR))
                                                        viewModel.previewRenderer.setEditingBlurTransform(null, null, null, null, null, null)
                                                    },
                                                    onDragCancel = {
                                                        viewModel.commitBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation, autoKeyframeAtPlayhead = (blur.keyframes.isNotEmpty() || selectedTool == EditorTool.BLUR))
                                                        viewModel.previewRenderer.setEditingBlurTransform(null, null, null, null, null, null)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.OpenInFull,
                                            contentDescription = "Resize Blur",
                                            tint = Color.Black,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Only show Rotate & Edit corners when box is spacious enough to avoid touch overlap
                                    if (boxW > 85f && boxH > 85f) {
                                        // Bottom-Left: Rotate handle
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .offset(x = (-6).dp, y = 6.dp)
                                                .size(34.dp)
                                                .background(Surface3, CircleShape)
                                                .border(2.dp, Color.White, CircleShape)
                                                .pointerInput(blur.id) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            selectedBlurId = blur.id
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            val world = toWorldDelta(dragAmount, localRotation, 1f)
                                                            val rad = Math.toRadians(localRotation.toDouble())
                                                            val cosA = kotlin.math.cos(rad).toFloat()
                                                            val sinA = kotlin.math.sin(rad).toFloat()
                                                            val handleLx = -boxW / 2f
                                                            val handleLy = boxH / 2f
                                                            val rx = handleLx * cosA - handleLy * sinA
                                                            val ry = handleLx * sinA + handleLy * cosA
                                                            val lenSq = (rx * rx + ry * ry).coerceAtLeast(100f)
                                                            val dThetaRad = (rx * world.y - ry * world.x) / lenSq
                                                            val deltaDeg = Math.toDegrees(dThetaRad.toDouble()).toFloat()
                                                            localRotation = ((localRotation + deltaDeg) % 360f + 360f) % 360f
                                                            viewModel.previewRenderer.setEditingBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation)
                                                        },
                                                        onDragEnd = {
                                                            viewModel.commitBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation, autoKeyframeAtPlayhead = (blur.keyframes.isNotEmpty() || selectedTool == EditorTool.BLUR))
                                                            viewModel.previewRenderer.setEditingBlurTransform(null, null, null, null, null, null)
                                                        },
                                                        onDragCancel = {
                                                            viewModel.commitBlurTransform(blur.id, localNormX, localNormY, localWidth, localHeight, localRotation, autoKeyframeAtPlayhead = (blur.keyframes.isNotEmpty() || selectedTool == EditorTool.BLUR))
                                                            viewModel.previewRenderer.setEditingBlurTransform(null, null, null, null, null, null)
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.RotateRight,
                                                contentDescription = "Rotate Blur",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Top-Left: Edit button
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(x = (-6).dp, y = (-6).dp)
                                                .size(34.dp)
                                                .background(AccentPrimary, CircleShape)
                                                .border(2.dp, Color.White, CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    selectedBlurId = blur.id
                                                    selectedTool = EditorTool.BLUR
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Edit Blur",
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (!isFingerPaintActive && !isChromaPaintActive) {
                                // Tap target for unselected blur
                                val unselectedPixelX = blur.normalizedX * bw
                                val unselectedPixelY = blur.normalizedY * bh
                                val targetW = (blur.width * bw).coerceIn(40f, bw)
                                val targetH = (blur.height * bh).coerceIn(40f, bh)
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            androidx.compose.ui.unit.IntOffset(
                                                (unselectedPixelX - targetW / 2f).toInt(),
                                                (unselectedPixelY - targetH / 2f).toInt()
                                            )
                                        }
                                        .size(
                                            with(androidx.compose.ui.platform.LocalDensity.current) { targetW.toDp() },
                                            with(androidx.compose.ui.platform.LocalDensity.current) { targetH.toDp() }
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selectedBlurId = blur.id
                                            selectedOverlayId = null
                                            selectedPipId = null
                                        }
                                )
                            }
                        }

                        // ── Top-Layer Interactive Finger-Painting Censor & Blackout Canvas ──
                        val hasCommittedPaint = project?.blurTracks?.any { it.paintStrokes.isNotEmpty() } == true
                        if (isFingerPaintActive || hasCommittedPaint) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isFingerPaintActive) {
                                            Modifier.pointerInput(isFingerPaintActive, paintBrushWidth, paintIsBlackout, paintCensorStyle) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    down.consume()
                                                    val startPt = com.clipcut.core.model.PaintPoint(
                                                        (down.position.x / bw).coerceIn(0f, 1f),
                                                        (down.position.y / bh).coerceIn(0f, 1f)
                                                    )
                                                    var strokePoints = listOf(startPt, startPt)
                                                    activeDrawingPoints = strokePoints
                                                    currentTouchOffset = down.position

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull() ?: break
                                                        if (!change.pressed) {
                                                            change.consume()
                                                            break
                                                        }
                                                        change.consume()
                                                        currentTouchOffset = change.position
                                                        val pt = com.clipcut.core.model.PaintPoint(
                                                            (change.position.x / bw).coerceIn(0f, 1f),
                                                            (change.position.y / bh).coerceIn(0f, 1f)
                                                        )
                                                        strokePoints = strokePoints + pt
                                                        activeDrawingPoints = strokePoints
                                                    }

                                                    // Commit stroke to project on touch up
                                                    if (strokePoints.isNotEmpty()) {
                                                        val newStroke = com.clipcut.core.model.PaintStroke(
                                                            points = strokePoints,
                                                            strokeWidthNorm = paintBrushWidth,
                                                            isBlackout = (paintCensorStyle == "BLACKOUT" || paintCensorStyle == "REDACTED" || paintCensorStyle == "CAUTION"),
                                                            style = paintCensorStyle
                                                        )
                                                        val totalDur = project?.totalDurationUs ?: 60_000_000L
                                                        val targetBlur = project?.blurTracks?.find { it.id == selectedBlurId && it.blurType == com.clipcut.core.model.BlurType.PAINT }
                                                            ?: project?.blurTracks?.firstOrNull { it.blurType == com.clipcut.core.model.BlurType.PAINT }
                                                        if (targetBlur != null) {
                                                            viewModel.updateBlurTrack(
                                                                targetBlur.copy(
                                                                    timelineStartUs = 0L,
                                                                    timelineEndUs = maxOf(targetBlur.timelineEndUs, totalDur, 3_600_000_000L),
                                                                    paintStrokes = targetBlur.paintStrokes + newStroke
                                                                )
                                                            )
                                                        } else {
                                                            val newTrack = com.clipcut.core.model.BlurTrackItem(
                                                                timelineStartUs = 0L,
                                                                timelineEndUs = maxOf(totalDur, 3_600_000_000L),
                                                                blurType = com.clipcut.core.model.BlurType.PAINT,
                                                                paintStrokes = listOf(newStroke)
                                                            )
                                                            viewModel.addBlurTrack(newTrack)
                                                            selectedBlurId = newTrack.id
                                                        }
                                                    }
                                                    activeDrawingPoints = emptyList()
                                                    currentTouchOffset = null
                                                }
                                            }
                                        } else Modifier
                                    )
                            ) {
                                val committedStrokes = project?.blurTracks?.filter {
                                    it.paintStrokes.isNotEmpty() && (isFingerPaintActive || (positionUs in it.timelineStartUs..it.timelineEndUs))
                                }?.flatMap { it.paintStrokes } ?: emptyList()

                                val allStrokes = committedStrokes + (
                                    if (activeDrawingPoints.size >= 2) listOf(
                                        com.clipcut.core.model.PaintStroke(
                                            points = activeDrawingPoints,
                                            strokeWidthNorm = paintBrushWidth,
                                            isBlackout = (paintCensorStyle == "BLACKOUT" || paintCensorStyle == "REDACTED" || paintCensorStyle == "CAUTION"),
                                            style = paintCensorStyle
                                        )
                                    ) else emptyList()
                                )

                                for (stroke in allStrokes) {
                                    if (stroke.points.isEmpty()) continue
                                    val strokePx = (stroke.strokeWidthNorm * bw).coerceAtLeast(6f)
                                    val path = androidx.compose.ui.graphics.Path()
                                    val first = stroke.points.first()
                                    path.moveTo(first.x * bw, first.y * bh)
                                    if (stroke.points.size == 1) {
                                        path.lineTo(first.x * bw + 0.1f, first.y * bh + 0.1f)
                                    } else {
                                        for (idx in 1 until stroke.points.size) {
                                            path.lineTo(stroke.points[idx].x * bw, stroke.points[idx].y * bh)
                                        }
                                    }

                                    when (stroke.style.uppercase()) {
                                        "BLACKOUT" -> {
                                            drawPath(
                                                path = path,
                                                color = Color.Black,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                        }
                                        "MOSAIC" -> {
                                            val blockSize = strokePx * 0.85f
                                            for (pt in stroke.points) {
                                                val qx = (kotlin.math.floor((pt.x * bw) / blockSize) * blockSize)
                                                val qy = (kotlin.math.floor((pt.y * bh) / blockSize) * blockSize)
                                                drawRect(
                                                    color = Color(0xEE202020),
                                                    topLeft = androidx.compose.ui.geometry.Offset(qx, qy),
                                                    size = androidx.compose.ui.geometry.Size(blockSize, blockSize)
                                                )
                                                drawRect(
                                                    color = Color(0x44666666),
                                                    topLeft = androidx.compose.ui.geometry.Offset(qx, qy),
                                                    size = androidx.compose.ui.geometry.Size(blockSize, blockSize),
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                                                )
                                            }
                                        }
                                        "FROSTED" -> {
                                            drawPath(
                                                path = path,
                                                color = Color(0xF0181818),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                            drawPath(
                                                path = path,
                                                color = Color(0x66FFFFFF),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx * 0.35f,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                        }
                                        "BLUR" -> {
                                            drawPath(
                                                path = path,
                                                color = Color(0xCC2C2C2C),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                            drawPath(
                                                path = path,
                                                color = Color(0x55555555),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx * 0.5f,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                        }
                                        "REDACTED" -> {
                                            drawPath(
                                                path = path,
                                                color = Color(0xFF050505),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Square,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Miter
                                                )
                                            )
                                            drawPath(
                                                path = path,
                                                color = Color(0xFFD32F2F),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = 2.dp.toPx(),
                                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                                                )
                                            )
                                        }
                                        "CAUTION" -> {
                                            drawPath(
                                                path = path,
                                                color = Color(0xFFFFD600),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Square,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Miter
                                                )
                                            )
                                            drawPath(
                                                path = path,
                                                color = Color.Black,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Square,
                                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(strokePx, strokePx), 0f)
                                                )
                                            )
                                        }
                                        "WHITEOUT" -> {
                                            drawPath(
                                                path = path,
                                                color = Color.White,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokePx,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                )
                                            )
                                        }
                                        else -> {
                                            if (stroke.isBlackout) {
                                                drawPath(
                                                    path = path,
                                                    color = Color.Black,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                        width = strokePx,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                    )
                                                )
                                            } else {
                                                drawPath(
                                                    path = path,
                                                    color = Color(0xF0181818),
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                        width = strokePx,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                // Glowing brush reticle circle under the finger while painting
                                currentTouchOffset?.let { touch ->
                                    val radiusPx = (paintBrushWidth * bw) / 2f
                                    drawCircle(
                                        color = if (paintIsBlackout) Color.Red.copy(alpha = 0.35f) else AccentPrimary.copy(alpha = 0.35f),
                                        radius = radiusPx,
                                        center = touch
                                    )
                                    drawCircle(
                                        color = if (paintIsBlackout) Color.White else AccentPrimary,
                                        radius = radiusPx,
                                        center = touch,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                                    )
                                }
                            }

                            // ── Floating HUD Banner & Quick Painter Bar ──
                            if (isFingerPaintActive) {
                                Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top Indicator Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black.copy(alpha = 0.85f))
                                        .border(1.5.dp, AccentPrimary, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("🖌️", fontSize = 13.sp)
                                        Text(
                                            "PAINT MODE ACTIVE: Drag finger over video",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Bottom Floating Quick Controls Pill
                                Surface(
                                    color = DarkSurface.copy(alpha = 0.95f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Surface3),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Row 1: All Censor Styles (scrollable)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val censorStyles = listOf(
                                                "BLACKOUT" to "⬛ Blackout",
                                                "MOSAIC" to "▦ Mosaic",
                                                "FROSTED" to "🌫️ Frosted",
                                                "BLUR" to "💧 Blur",
                                                "REDACTED" to "🪧 Redacted",
                                                "CAUTION" to "🚧 Caution",
                                                "WHITEOUT" to "⚪ Whiteout"
                                            )
                                            censorStyles.forEach { (styleKey, styleLabel) ->
                                                val isSel = paintCensorStyle.equals(styleKey, ignoreCase = true)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSel) AccentPrimary else Surface2)
                                                        .clickable {
                                                            paintCensorStyle = styleKey
                                                            paintIsBlackout = (styleKey == "BLACKOUT" || styleKey == "REDACTED" || styleKey == "CAUTION")
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        styleLabel,
                                                        color = if (isSel) Color.Black else Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Row 2: Brush sizes + Undo + Done
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf("8px" to 0.03f, "16px" to 0.06f, "28px" to 0.10f, "48px" to 0.16f).forEach { (lbl, sz) ->
                                                    val isSel = kotlin.math.abs(paintBrushWidth - sz) < 0.015f
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isSel) AccentSecondary else Surface2)
                                                            .clickable { paintBrushWidth = sz }
                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            lbl,
                                                            color = if (isSel) Color.Black else TextSecondary,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Undo
                                                IconButton(
                                                    onClick = {
                                                        val all = project?.blurTracks ?: emptyList()
                                                        val target = all.firstOrNull { it.paintStrokes.isNotEmpty() }
                                                        if (target != null && target.paintStrokes.isNotEmpty()) {
                                                            viewModel.updateBlurTrack(target.copy(paintStrokes = target.paintStrokes.dropLast(1)))
                                                            android.widget.Toast.makeText(ctx, "Undid stroke", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Undo", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }

                                                // Done button
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(AccentPrimary)
                                                        .clickable {
                                                            // Commit any uncommitted active in-progress stroke
                                                            if (activeDrawingPoints.size >= 2) {
                                                                val newStroke = com.clipcut.core.model.PaintStroke(
                                                                    points = activeDrawingPoints,
                                                                    strokeWidthNorm = paintBrushWidth,
                                                                    isBlackout = (paintCensorStyle == "BLACKOUT" || paintCensorStyle == "REDACTED" || paintCensorStyle == "CAUTION"),
                                                                    style = paintCensorStyle
                                                                )
                                                                val totalDur = project?.totalDurationUs ?: 60_000_000L
                                                                val targetBlur = project?.blurTracks?.find { it.id == selectedBlurId && it.blurType == com.clipcut.core.model.BlurType.PAINT }
                                                                    ?: project?.blurTracks?.firstOrNull { it.blurType == com.clipcut.core.model.BlurType.PAINT }
                                                                if (targetBlur != null) {
                                                                    viewModel.updateBlurTrack(
                                                                        targetBlur.copy(
                                                                            timelineStartUs = 0L,
                                                                            timelineEndUs = maxOf(targetBlur.timelineEndUs, totalDur, 3_600_000_000L),
                                                                            paintStrokes = targetBlur.paintStrokes + newStroke
                                                                        )
                                                                    )
                                                                } else {
                                                                    val newTrack = com.clipcut.core.model.BlurTrackItem(
                                                                        timelineStartUs = 0L,
                                                                        timelineEndUs = maxOf(totalDur, 3_600_000_000L),
                                                                        blurType = com.clipcut.core.model.BlurType.PAINT,
                                                                        paintStrokes = listOf(newStroke)
                                                                    )
                                                                    viewModel.addBlurTrack(newTrack)
                                                                    selectedBlurId = newTrack.id
                                                                }
                                                            }
                                                            activeDrawingPoints = emptyList()
                                                            isFingerPaintActive = false
                                                            selectedTool = null
                                                            viewModel.previewRenderer.requestRender()
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text("✓ Done", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }

                        // ── Top-Layer Interactive Chroma Green & Studio Painter Canvas & HUD ──
                        val hasCommittedChromaPaint = project?.blurTracks?.any {
                            it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT && it.paintStrokes.isNotEmpty()
                        } == true

                        if (isChromaPaintActive || hasCommittedChromaPaint) {
                            val studioBmp = remember(chromaPaintStudioUri) {
                                if (!chromaPaintStudioUri.isNullOrBlank()) {
                                    try {
                                        val clean = chromaPaintStudioUri.removePrefix("asset://")
                                        ctx.assets.open(clean).use {
                                            android.graphics.BitmapFactory.decodeStream(it)
                                        }
                                    } catch (_: Throwable) { null }
                                } else null
                            }

                            val studioShaderBrush = remember(studioBmp, bw, bh) {
                                if (studioBmp != null && bw > 0f && bh > 0f) {
                                    val shader = android.graphics.BitmapShader(
                                        studioBmp,
                                        android.graphics.Shader.TileMode.CLAMP,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                    val matrix = android.graphics.Matrix()
                                    val sx = bw / studioBmp.width.toFloat()
                                    val sy = bh / studioBmp.height.toFloat()
                                    val s = maxOf(sx, sy)
                                    matrix.setScale(s, s)
                                    val dx = (bw - studioBmp.width * s) / 2f
                                    val dy = (bh - studioBmp.height * s) / 2f
                                    matrix.postTranslate(dx, dy)
                                    shader.setLocalMatrix(matrix)
                                    androidx.compose.ui.graphics.ShaderBrush(shader)
                                } else null
                            }

                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isChromaPaintActive) {
                                            Modifier.pointerInput(
                                                isChromaPaintActive,
                                                chromaPaintBrushWidth,
                                                chromaPaintIsEraser,
                                                chromaPaintViewMode,
                                                chromaPaintStudioUri,
                                                chromaPaintInverted
                                            ) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    down.consume()
                                                    val startPt = com.clipcut.core.model.PaintPoint(
                                                        (down.position.x / bw).coerceIn(0f, 1f),
                                                        (down.position.y / bh).coerceIn(0f, 1f)
                                                    )
                                                    var strokePoints = listOf(startPt, startPt)
                                                    activeChromaDrawingPoints = strokePoints
                                                    chromaTouchOffset = down.position

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull() ?: break
                                                        if (!change.pressed) {
                                                            change.consume()
                                                            break
                                                        }
                                                        change.consume()
                                                        chromaTouchOffset = change.position
                                                        val pt = com.clipcut.core.model.PaintPoint(
                                                            (change.position.x / bw).coerceIn(0f, 1f),
                                                            (change.position.y / bh).coerceIn(0f, 1f)
                                                        )
                                                        strokePoints = strokePoints + pt
                                                        activeChromaDrawingPoints = strokePoints
                                                    }

                                                    if (strokePoints.isNotEmpty()) {
                                                        val strokeStyle = if (chromaPaintIsEraser) "ERASER" else if (chromaPaintViewMode == "STUDIO") "STUDIO_OVERLAY" else "CHROMA_GREEN"
                                                        val newStroke = com.clipcut.core.model.PaintStroke(
                                                            points = strokePoints,
                                                            strokeWidthNorm = chromaPaintBrushWidth,
                                                            isBlackout = false,
                                                            style = strokeStyle
                                                        )
                                                        val totalDur = project?.totalDurationUs ?: 60_000_000L
                                                        val targetTrack = project?.blurTracks?.find { it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT }
                                                        if (targetTrack != null) {
                                                            viewModel.updateBlurTrack(
                                                                targetTrack.copy(
                                                                    timelineStartUs = 0L,
                                                                    timelineEndUs = maxOf(targetTrack.timelineEndUs, totalDur, 3_600_000_000L),
                                                                    customImageUri = chromaPaintStudioUri,
                                                                    intensity = if (chromaPaintInverted) -1f else if (chromaPaintViewMode == "STUDIO") 1f else 0f,
                                                                    paintStrokes = targetTrack.paintStrokes + newStroke
                                                                )
                                                            )
                                                        } else {
                                                            val newTrack = com.clipcut.core.model.BlurTrackItem(
                                                                timelineStartUs = 0L,
                                                                timelineEndUs = maxOf(totalDur, 3_600_000_000L),
                                                                blurType = com.clipcut.core.model.BlurType.CHROMA_PAINT,
                                                                customImageUri = chromaPaintStudioUri,
                                                                intensity = if (chromaPaintInverted) -1f else if (chromaPaintViewMode == "STUDIO") 1f else 0f,
                                                                paintStrokes = listOf(newStroke)
                                                            )
                                                            viewModel.addBlurTrack(newTrack)
                                                        }
                                                        viewModel.previewRenderer.requestRender()
                                                    }
                                                    activeChromaDrawingPoints = emptyList()
                                                    chromaTouchOffset = null
                                                }
                                            }
                                        } else Modifier
                                    )
                            ) {
                                val committedChroma = project?.blurTracks?.filter {
                                    it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT && it.paintStrokes.isNotEmpty()
                                }?.flatMap { it.paintStrokes } ?: emptyList()

                                val allStrokes = committedChroma + (
                                    if (activeChromaDrawingPoints.size >= 2) listOf(
                                        com.clipcut.core.model.PaintStroke(
                                            points = activeChromaDrawingPoints,
                                            strokeWidthNorm = chromaPaintBrushWidth,
                                            isBlackout = false,
                                            style = if (chromaPaintIsEraser) "ERASER" else if (chromaPaintViewMode == "STUDIO") "STUDIO_OVERLAY" else "CHROMA_GREEN"
                                        )
                                    ) else emptyList()
                                )

                                for (stroke in allStrokes) {
                                    if (stroke.points.isEmpty()) continue
                                    val strokePx = (stroke.strokeWidthNorm * bw).coerceAtLeast(6f)
                                    val path = androidx.compose.ui.graphics.Path()
                                    val first = stroke.points.first()
                                    path.moveTo(first.x * bw, first.y * bh)
                                    if (stroke.points.size == 1) {
                                        path.lineTo(first.x * bw + 0.1f, first.y * bh + 0.1f)
                                    } else {
                                        for (i in 1 until stroke.points.size) {
                                            val pt = stroke.points[i]
                                            path.lineTo(pt.x * bw, pt.y * bh)
                                        }
                                    }

                                    if (stroke.style.equals("ERASER", ignoreCase = true)) {
                                        drawPath(
                                            path = path,
                                            color = Color.Transparent,
                                            blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = strokePx,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                                            )
                                        )
                                    } else if ((chromaPaintViewMode == "STUDIO" || stroke.style.equals("STUDIO_OVERLAY", ignoreCase = true)) && studioShaderBrush != null) {
                                        drawPath(
                                            path = path,
                                            brush = studioShaderBrush,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = strokePx,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                                            )
                                        )
                                    } else {
                                        drawPath(
                                            path = path,
                                            color = Color(0xFF00E676),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = strokePx,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                                            )
                                        )
                                    }
                                }

                                chromaTouchOffset?.let { touch ->
                                    val radiusPx = (chromaPaintBrushWidth * bw) / 2f
                                    val reticleColor = if (chromaPaintIsEraser) Color(0xFFFF5252) else Color(0xFF00E676)
                                    drawCircle(
                                        color = reticleColor.copy(alpha = 0.35f),
                                        radius = radiusPx,
                                        center = touch
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = radiusPx,
                                        center = touch,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }

                            if (isChromaPaintActive) {
                                val studioPresets = remember {
                                    listOf(
                                        "Podcast" to "overlays/backgrounds/human_seg_2.webp",
                                        "Cyberpunk" to "overlays/backgrounds/human_seg_1.webp",
                                        "Newsroom" to "overlays/backgrounds/human_seg_17.webp",
                                        "Office" to "overlays/backgrounds/human_seg_4.webp",
                                        "Loft" to "overlays/backgrounds/human_seg_3.webp",
                                        "Beach" to "overlays/backgrounds/human_seg_5.webp",
                                        "Sci-Fi" to "overlays/backgrounds/human_seg_6.webp",
                                        "Gallery" to "overlays/backgrounds/human_seg_7.webp",
                                        "Cafe" to "overlays/backgrounds/human_seg_8.webp",
                                        "Rooftop" to "overlays/backgrounds/human_seg_9.webp",
                                        "VIP Club" to "overlays/backgrounds/human_seg_10.webp",
                                        "Tokyo" to "overlays/backgrounds/human_seg_11.webp",
                                        "Abstract" to "overlays/backgrounds/human_seg_12.webp",
                                        "Industrial" to "overlays/backgrounds/human_seg_13.webp",
                                        "Alpine" to "overlays/backgrounds/human_seg_14.webp",
                                        "Cinema" to "overlays/backgrounds/human_seg_15.webp",
                                        "Balcony" to "overlays/backgrounds/human_seg_16.webp",
                                        "Blue Sky" to "overlays/sky/PRESET_SYK_1.png",
                                        "Sunset" to "overlays/sky/PRESET_SYK_2.png",
                                        "Galaxy" to "overlays/sky/PRESET_SYK_7.png"
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xEE121820),
                                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "🖌️ PAINT GREEN SCREEN",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (chromaPaintViewMode == "STUDIO") Color(0xFF10B981) else Surface3)
                                                    .clickable {
                                                        chromaPaintViewMode = if (chromaPaintViewMode == "STUDIO") "GREEN" else "STUDIO"
                                                        val target = project?.blurTracks?.find { it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT }
                                                        if (target != null) {
                                                            viewModel.updateBlurTrack(target.copy(intensity = if (chromaPaintViewMode == "STUDIO") 1f else 0f))
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    if (chromaPaintViewMode == "STUDIO") "🎙️ Studio View" else "🟢 Green View",
                                                    color = if (chromaPaintViewMode == "STUDIO") Color.Black else Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(AccentPrimary)
                                                    .clickable {
                                                        if (activeChromaDrawingPoints.size >= 2) {
                                                            val strokeStyle = if (chromaPaintIsEraser) "ERASER" else if (chromaPaintViewMode == "STUDIO") "STUDIO_OVERLAY" else "CHROMA_GREEN"
                                                            val newStroke = com.clipcut.core.model.PaintStroke(
                                                                points = activeChromaDrawingPoints,
                                                                strokeWidthNorm = chromaPaintBrushWidth,
                                                                isBlackout = false,
                                                                style = strokeStyle
                                                            )
                                                            val totalDur = project?.totalDurationUs ?: 60_000_000L
                                                            val targetTrack = project?.blurTracks?.find { it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT }
                                                            if (targetTrack != null) {
                                                                viewModel.updateBlurTrack(
                                                                    targetTrack.copy(
                                                                        timelineStartUs = 0L,
                                                                        timelineEndUs = maxOf(targetTrack.timelineEndUs, totalDur, 3_600_000_000L),
                                                                        customImageUri = chromaPaintStudioUri,
                                                                        intensity = if (chromaPaintInverted) -1f else if (chromaPaintViewMode == "STUDIO") 1f else 0f,
                                                                        paintStrokes = targetTrack.paintStrokes + newStroke
                                                                    )
                                                                )
                                                            } else {
                                                                val newTrack = com.clipcut.core.model.BlurTrackItem(
                                                                    timelineStartUs = 0L,
                                                                    timelineEndUs = maxOf(totalDur, 3_600_000_000L),
                                                                    blurType = com.clipcut.core.model.BlurType.CHROMA_PAINT,
                                                                    customImageUri = chromaPaintStudioUri,
                                                                    intensity = if (chromaPaintInverted) -1f else if (chromaPaintViewMode == "STUDIO") 1f else 0f,
                                                                    paintStrokes = listOf(newStroke)
                                                                )
                                                                viewModel.addBlurTrack(newTrack)
                                                            }
                                                        }
                                                        activeChromaDrawingPoints = emptyList()
                                                        isChromaPaintActive = false
                                                        viewModel.previewRenderer.requestRender()
                                                        android.widget.Toast.makeText(ctx, "Green screen studio mask applied!", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("✓ Done", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xF50D1117),
                                        border = BorderStroke(1.dp, Surface3),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                studioPresets.forEach { (name, path) ->
                                                    val isSelected = chromaPaintStudioUri == path
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (isSelected) Color(0xFF10B981) else Surface2)
                                                            .border(1.dp, if (isSelected) Color.White else HairlineBorder, RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                chromaPaintStudioUri = path
                                                                val target = project?.blurTracks?.find { it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT }
                                                                if (target != null) {
                                                                    viewModel.updateBlurTrack(target.copy(customImageUri = path))
                                                                    viewModel.previewRenderer.requestRender()
                                                                }
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                                    ) {
                                                        Text(
                                                            name,
                                                            color = if (isSelected) Color.Black else Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (!chromaPaintIsEraser) Color(0xFF00E676) else Surface2)
                                                            .clickable { chromaPaintIsEraser = false }
                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("🖌️ Paint", color = if (!chromaPaintIsEraser) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (chromaPaintIsEraser) Color(0xFFFF5252) else Surface2)
                                                            .clickable { chromaPaintIsEraser = true }
                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("🧹 Erase", color = if (chromaPaintIsEraser) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (chromaPaintInverted) AccentPrimary else Surface2)
                                                            .clickable {
                                                                chromaPaintInverted = !chromaPaintInverted
                                                                val target = project?.blurTracks?.find { it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT }
                                                                if (target != null) {
                                                                    viewModel.updateBlurTrack(target.copy(intensity = if (chromaPaintInverted) -1f else 1f))
                                                                    viewModel.previewRenderer.requestRender()
                                                                }
                                                                android.widget.Toast.makeText(
                                                                    ctx,
                                                                    if (chromaPaintInverted) "Inverted: Studio fills frame around cutout" else "Normal: Studio fills painted strokes",
                                                                    android.widget.Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                                    ) {
                                                        Text("🔄 Invert", color = if (chromaPaintInverted) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    listOf("12px" to 0.04f, "24px" to 0.08f, "48px" to 0.14f).forEach { (lbl, sz) ->
                                                        val isSel = kotlin.math.abs(chromaPaintBrushWidth - sz) < 0.02f
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSel) Color(0xFF00E676) else Surface2)
                                                                .clickable { chromaPaintBrushWidth = sz }
                                                                .padding(horizontal = 5.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(lbl, color = if (isSel) Color.Black else TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            val target = project?.blurTracks?.find { it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT }
                                                            if (target != null && target.paintStrokes.isNotEmpty()) {
                                                                viewModel.updateBlurTrack(target.copy(paintStrokes = target.paintStrokes.dropLast(1)))
                                                                viewModel.previewRenderer.requestRender()
                                                                android.widget.Toast.makeText(ctx, "Undid stroke", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Undo", tint = Color.White, modifier = Modifier.size(15.dp))
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            val target = project?.blurTracks?.find { it.blurType == com.clipcut.core.model.BlurType.CHROMA_PAINT }
                                                            if (target != null) {
                                                                viewModel.updateBlurTrack(target.copy(paintStrokes = emptyList()))
                                                                viewModel.previewRenderer.requestRender()
                                                                android.widget.Toast.makeText(ctx, "Cleared painted strokes", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "Clear", tint = Color(0xFFFF5252), modifier = Modifier.size(15.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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

                    // ── Floating Quick Controls Bar for Selected Blur ──
                    val activeBlur = project?.blurTracks?.find { it.id == selectedBlurId }
                    if (activeBlur != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xDD1E1E2E),
                            border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.5f)),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { selectedTool = EditorTool.BLUR },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, "Edit Blur", tint = AccentPrimary, modifier = Modifier.size(18.dp))
                                }

                                val blurOffsetUs = (positionUs - activeBlur.timelineStartUs).coerceIn(0L, activeBlur.durationUs)
                                val hasKfHere = activeBlur.keyframes.any { kotlin.math.abs(it.timeOffsetUs - blurOffsetUs) < 35_000L }

                                IconButton(
                                    onClick = { viewModel.stepPlayheadByFrames(-1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("◀", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        if (hasKfHere) {
                                            viewModel.removeBlurKeyframeAtPlayhead(activeBlur.id, positionUs)
                                        } else {
                                            viewModel.setBlurKeyframeAtPlayhead(activeBlur.id, positionUs)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(
                                        "◆",
                                        color = if (hasKfHere) AccentPrimary else Color.White.copy(alpha = 0.5f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.stepPlayheadByFrames(1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("▶", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        val cur = activeBlur.getTransformAt(positionUs)
                                        viewModel.commitBlurTransform(activeBlur.id, cur.normalizedX, cur.normalizedY, (cur.width * 0.82f).coerceAtLeast(0.04f), (cur.height * 0.82f).coerceAtLeast(0.04f), cur.rotation)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, "Smaller", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val cur = activeBlur.getTransformAt(positionUs)
                                        viewModel.commitBlurTransform(activeBlur.id, cur.normalizedX, cur.normalizedY, (cur.width * 1.20f).coerceAtMost(0.95f), (cur.height * 1.20f).coerceAtMost(0.95f), cur.rotation)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Add, "Bigger", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.startMotionTracking(activeBlur.id, com.clipcut.core.feature.TrackingMode.BODY, positionUs)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, "Track Target", tint = Color.White, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val bType = activeBlur.blurType
                                        viewModel.autoBlurAllFaces(
                                            currentPlayheadUs = positionUs,
                                            blurType = bType,
                                            shape = activeBlur.shape,
                                            intensity = if (bType == com.clipcut.core.model.BlurType.GAUSSIAN) 1.0f else activeBlur.intensity,
                                            feather = activeBlur.feather,
                                            customImageUri = activeBlur.customImageUri
                                        )
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Layers, "Blur All Faces", tint = AccentPrimary, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val totalDur = project?.totalDurationUs ?: 5_000_000L
                                        val newBox = com.clipcut.core.model.BlurTrackItem(
                                            timelineStartUs = positionUs,
                                            timelineEndUs = minOf(totalDur.coerceAtLeast(positionUs + 4_000_000L), positionUs + 4_000_000L),
                                            blurType = activeBlur.blurType,
                                            shape = activeBlur.shape,
                                            normalizedX = (activeBlur.normalizedX + 0.12f).let { if (it > 0.85f) 0.35f else it },
                                            normalizedY = (activeBlur.normalizedY + 0.12f).let { if (it > 0.85f) 0.35f else it },
                                            width = activeBlur.width,
                                            height = activeBlur.height,
                                            intensity = activeBlur.intensity,
                                            feather = activeBlur.feather,
                                            customImageUri = activeBlur.customImageUri
                                        )
                                        viewModel.addBlurTrack(newBox)
                                        selectedBlurId = newBox.id
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Add, "Add Another Box", tint = AccentPrimary, modifier = Modifier.size(18.dp))
                                }

                                Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.2f)))

                                IconButton(
                                    onClick = {
                                        val toDel = activeBlur.id
                                        selectedBlurId = null
                                        viewModel.removeBlurTrack(toDel)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { selectedBlurId = null },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(AccentPrimary, CircleShape)
                                ) {
                                    Icon(Icons.Filled.Check, "Done", tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // ── Active AI Tracking Floating Banner ──
                    if (trackingStatus != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xEE1E1E2E),
                            border = BorderStroke(1.5.dp, AccentPrimary),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = AccentPrimary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = trackingStatus ?: "",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    }
                }

                // Play/pause overlay
                if (selectedOverlayId == null && selectedPipId == null && selectedBlurId == null && selectedTool != EditorTool.CROP && !isFingerPaintActive && !isChromaPaintActive) {
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
            if (selectedTool != EditorTool.CROP) {
                TimelineView(
                    videoClips = project?.videoClips ?: emptyList(),
                    audioTracks = project?.audioTracks ?: emptyList(),
                    overlays = project?.overlays ?: emptyList(),
                    pipTracks = project?.pipTracks ?: emptyList(),
                    blurTracks = project?.blurTracks ?: emptyList(),
                    beatMarkers = project?.beatMarkers ?: emptyList(),
                    selectedClipId = selectedClipId,
                    selectedOverlayId = selectedOverlayId,
                    selectedPipId = selectedPipId,
                    selectedBlurId = selectedBlurId,
                    currentPositionUs = positionUs,
                    totalDurationUs = project?.totalDurationUs ?: 0L,
                    isPlaying = isPlaying,
                    onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                    onStop = {
                        viewModel.playerManager.pause()
                        viewModel.playerManager.seekToUs(0L)
                    },
                    onSelectClip = { id ->
                        if (selectedClipId == id) viewModel.selectClip(null) else viewModel.selectClip(id)
                    },
                    onSeek = { viewModel.playerManager.seekToUs(it) },
                    onAddMediaClick = { pickMedia.launch(arrayOf("video/*", "image/*")) },
                    onTrimClip = { id, s, e -> viewModel.trimClip(id, s, e) },
                    onSelectAudioTrack = { selectedTool = EditorTool.AUDIO },
                    onTrimAudioTrack = { id, s, e -> viewModel.trimAudioTrack(id, s, e) },
                    onSelectOverlay = { overlay ->
                        selectedOverlayId = if (selectedOverlayId == overlay.id) null else overlay.id
                        selectedPipId = null
                        selectedBlurId = null
                    },
                    onTrimOverlay = { id, s, e -> viewModel.trimOverlay(id, s, e) },
                    onSelectPip = { pip ->
                        selectedPipId = if (selectedPipId == pip.id) null else pip.id
                        selectedOverlayId = null
                        selectedBlurId = null
                    },
                    onTrimPip = { id, s, e -> viewModel.trimPipTrack(id, s, e) },
                    onSelectBlur = { blur ->
                        selectedBlurId = if (selectedBlurId == blur.id) null else blur.id
                        selectedOverlayId = null
                        selectedPipId = null
                    },
                    onTrimBlur = { id, s, e -> viewModel.trimBlurTrack(id, s, e) },
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                )
            }
        }
    }

    // Bottom sheet host routes content based on selectedTool.
    val tool = selectedTool
    val clip = targetClip
    UnifiedBottomSheetHost(
        title = when (activeSpecialSheet) {
            "voiceover" -> "Voiceover"
            "captions" -> "Auto Captions"
            "pip" -> "PiP Layer"
            else -> tool?.label ?: ""
        },
        onDone = { selectedTool = null; activeSpecialSheet = null },
        onDismiss = { selectedTool = null; activeSpecialSheet = null },
        visible = (tool != null || activeSpecialSheet != null) && !isFingerPaintActive && tool != EditorTool.CROP
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
                    projectDurationUs = project?.totalDurationUs ?: 0L,
                    onAdd = {
                        viewModel.addPipTrack(it)
                        selectedPipId = it.id
                        selectedOverlayId = null
                        activeSpecialSheet = null
                    },
                    onDismiss = { activeSpecialSheet = null }
                )
                "extract_audio" -> AudioExtractorSheet(
                    initialVideoUri = project?.videoClips?.firstOrNull()?.uri?.let { android.net.Uri.parse(it) },
                    onAddToTimeline = { mp3Uri ->
                        viewModel.addAudioTrack(mp3Uri)
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
                    onAddAssetAudio = { path, name -> viewModel.addAssetAudioTrack(path, name) },
                    onExtractAudioClick = { activeSpecialSheet = "extract_audio" },
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
                    val targetId = sid ?: (project?.videoClips?.getOrNull(1)?.id ?: project?.videoClips?.firstOrNull()?.id)
                    if (targetId != null) {
                        viewModel.setClipTransition(targetId, type, dur)
                        android.widget.Toast.makeText(ctx, "Applied ${type.displayName} transition", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    selectedTool = null
                },
                onApplyToAll = { type, dur ->
                    viewModel.setAllClipsTransition(type, dur)
                    android.widget.Toast.makeText(ctx, "Applied ${type.displayName} to all clips", android.widget.Toast.LENGTH_SHORT).show()
                    selectedTool = null
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
            EditorTool.SPEED_RAMP -> SpeedRampDialog(
                initialRamp = clip?.speedRamp ?: com.clipcut.core.model.SpeedRamp(),
                clipDurationUs = clip?.sourceDurationUs ?: (project?.totalDurationUs ?: 5_000_000L),
                onApply = { ramp ->
                    sid?.let { viewModel.setClipSpeedRamp(it, ramp) }
                    selectedTool = null
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.VOLUME -> VolumeSheet(
                current = clip?.volume ?: 1f,
                onApply = { v -> sid?.let { viewModel.setClipVolume(it, v) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.EQUALIZER -> EqualizerSheet(
                gains = viewModel.equalizerGains,
                onApply = { gains ->
                    viewModel.updateEqualizerGains(gains)
                    android.widget.Toast.makeText(ctx, "10-Band EQ applied", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.VOICE -> VoiceRemixSheet(
                currentPreset = viewModel.voicePreset,
                onApplyVoice = { preset ->
                    viewModel.updateVoicePreset(preset)
                    android.widget.Toast.makeText(ctx, "Voice effect: ${preset.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.BRAND_KIT -> BrandKitSheet(
                current = viewModel.brandKitConfig,
                onApply = { config ->
                    viewModel.applyBrandKit(config)
                    android.widget.Toast.makeText(ctx, "Brand kit applied to project", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.ROTATE -> RotateSheet(
                current = clip?.rotationDegrees ?: 0,
                onApply = { d -> sid?.let { viewModel.setClipRotation(it, d) } },
                onDismiss = { selectedTool = null }
            )
            EditorTool.CROP -> {} // Handled freeform directly on the video preview canvas
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
                onApply = { c, t, s ->
                    sid?.let {
                        viewModel.setClipChromaKey(it, c, t, s)
                        android.widget.Toast.makeText(ctx, "Chroma key applied", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    selectedTool = null
                },
                onOpenFingerPaint = {
                    selectedTool = null
                    isChromaPaintActive = true
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.MASK -> MaskDialog(
                initial = clip?.maskConfig ?: com.clipcut.core.model.MaskConfig(),
                onApply = { m ->
                    sid?.let {
                        viewModel.setClipMask(it, m)
                        android.widget.Toast.makeText(ctx, "Mask applied", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    selectedTool = null
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.BLEND -> BlendModeDialog(
                initial = clip?.blendMode ?: com.clipcut.core.model.BlendMode.NORMAL,
                onApply = { b ->
                    sid?.let {
                        viewModel.setClipBlendMode(it, b)
                        android.widget.Toast.makeText(ctx, "Blend mode applied: ${b.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    selectedTool = null
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.TEXT -> {
                val rawOverlay = project?.overlays?.firstOrNull { it.id == selectedOverlayId }
                val isTextItem = rawOverlay != null && rawOverlay.imageUri == null && !rawOverlay.text.startsWith("asset://") && !rawOverlay.text.endsWith(".png") && !rawOverlay.text.endsWith(".webp")
                val editingOverlay = if (isTextItem) rawOverlay else null
                TextSheet(
                    initialOverlay = editingOverlay,
                    playheadUs = positionUs,
                    totalDurationUs = project?.totalDurationUs ?: 5_000_000L,
                    onApply = { overlay ->
                        if (editingOverlay != null) {
                            viewModel.updateOverlay(editingOverlay.id) { overlay }
                            android.widget.Toast.makeText(ctx, "Text updated", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addOverlay(overlay)
                            selectedOverlayId = overlay.id
                            android.widget.Toast.makeText(ctx, "Text added", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        selectedTool = null
                    },
                    onOpenCaptions = { activeSpecialSheet = "captions" },
                    onDismiss = { selectedTool = null }
                )
            }
            EditorTool.STICKER -> StickerSheet(
                onPick = { stickerRes ->
                    val isSky = stickerRes.contains("overlays/sky/") || stickerRes.contains("PRESET_SYK")
                    val isStudioBg = stickerRes.contains("overlays/backgrounds/") || stickerRes.contains("human_seg")
                    val isFrame = !isSky && !isStudioBg && (
                        stickerRes.contains("overlays/frames/") ||
                        stickerRes.contains("Camera") || stickerRes.contains("Phone") ||
                        stickerRes.contains("Photo") || stickerRes.contains("Heart") ||
                        stickerRes.contains("Circular") || stickerRes.contains("Water") ||
                        stickerRes.contains("Square") || stickerRes.contains("Rectangle")
                    )
                    val isImg = stickerRes.startsWith("asset://") || (!stickerRes.contains(" ") && (stickerRes.endsWith(".png") || stickerRes.endsWith(".webp")))
                    val totalDur = project?.totalDurationUs ?: 0L
                    val newSticker = com.clipcut.core.model.OverlayItem(
                        text = stickerRes,
                        imageUri = if (isImg) stickerRes else null,
                        timelineStartUs = if (isFrame || isSky || isStudioBg) 0L else positionUs,
                        timelineEndUs = if ((isFrame || isSky || isStudioBg) && totalDur > 0L) totalDur else if (isFrame || isSky || isStudioBg) 60_000_000L else positionUs + 5_000_000L,
                        normalizedX = 0.5f,
                        normalizedY = if (isSky) 0.30f else 0.5f,
                        scale = when {
                            isFrame -> 1.0f
                            isSky -> 1.0f
                            isStudioBg -> 1.0f
                            isImg -> 0.85f
                            else -> 0.70f
                        },
                        textSizeSp = 36f
                    )
                    viewModel.addOverlay(newSticker)
                    selectedOverlayId = newSticker.id
                    if (isStudioBg) {
                        val activeClip = project?.videoClips?.firstOrNull { it.id == selectedClipId }
                            ?: project?.videoClips?.firstOrNull()
                        if (activeClip != null && activeClip.chromaKeyColor == null) {
                            viewModel.setClipChromaKey(activeClip.id, floatArrayOf(0f, 1f, 0f), 0.05f, 0.06f)
                        }
                    }
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
                    val uri = o.imageUri ?: o.text
                    val isStudioBg = uri.contains("overlays/backgrounds/") || uri.contains("human_seg")
                    val isSky = uri.contains("overlays/sky/") || uri.contains("PRESET_SYK")
                    if (isStudioBg) {
                        val activeClip = project?.videoClips?.firstOrNull { it.id == selectedClipId }
                            ?: project?.videoClips?.firstOrNull()
                        if (activeClip != null && activeClip.chromaKeyColor == null) {
                            viewModel.setClipChromaKey(activeClip.id, floatArrayOf(0f, 1f, 0f), 0.05f, 0.06f)
                        }
                    }
                },
                onEditOverlay = { id ->
                    val ov = project?.overlays?.firstOrNull { it.id == id }
                    selectedOverlayId = id
                    if (ov != null && ov.imageUri == null && !ov.text.startsWith("asset://") && !ov.text.endsWith(".png") && !ov.text.endsWith(".webp")) {
                        selectedTool = EditorTool.TEXT
                    }
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
                onSetPipVolume = { id, v -> viewModel.setPipVolume(id, v) },
                onSeekPreview = { us -> viewModel.playerManager.seekToUs(us) },
                onOpenPiP = {
                    selectedTool = null
                    activeSpecialSheet = "pip"
                },
                onOpenFingerPaintStudio = { studioUri ->
                    selectedTool = null
                    chromaPaintStudioUri = studioUri
                    isChromaPaintActive = true
                },
                onDismiss = { selectedTool = null }
            )
            EditorTool.KEYFRAMES -> KeyframeSheet(
                overlay = project?.overlays?.firstOrNull { it.id == selectedOverlayId } ?: project?.overlays?.firstOrNull(),
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
            EditorTool.BLUR -> {
                val totalDur = project?.totalDurationUs ?: 5_000_000L
                val blurTracks = project?.blurTracks ?: emptyList()
                val currentBlur = blurTracks.firstOrNull { it.id == selectedBlurId }
                    ?: blurTracks.firstOrNull { positionUs in it.timelineStartUs..it.timelineEndUs }
                    ?: blurTracks.firstOrNull()

                val itemToEdit = currentBlur ?: run {
                    val newBlur = com.clipcut.core.model.BlurTrackItem(
                        timelineStartUs = positionUs,
                        timelineEndUs = minOf(totalDur.coerceAtLeast(positionUs + 3_000_000L), positionUs + 3_000_000L)
                    )
                    viewModel.addBlurTrack(newBlur)
                    selectedBlurId = newBlur.id
                    newBlur
                }

                BlurSheet(
                    blurItem = itemToEdit,
                    allBlurItems = blurTracks,
                    selectedBlurId = itemToEdit.id,
                    totalDurationUs = totalDur,
                    trackingStatus = trackingStatus,
                    currentPlayheadUs = positionUs,
                    isPlaying = isPlaying,
                    onSelectBlur = { newId ->
                        selectedBlurId = newId
                    },
                    onAddNewBlurTrack = {
                        val base = itemToEdit
                        val newBox = com.clipcut.core.model.BlurTrackItem(
                            timelineStartUs = positionUs,
                            timelineEndUs = minOf(totalDur.coerceAtLeast(positionUs + 4_000_000L), positionUs + 4_000_000L),
                            blurType = base.blurType,
                            shape = base.shape,
                            normalizedX = (base.normalizedX + 0.12f).let { if (it > 0.85f) 0.35f else it },
                            normalizedY = (base.normalizedY + 0.12f).let { if (it > 0.85f) 0.35f else it },
                            width = base.width,
                            height = base.height,
                            intensity = base.intensity,
                            feather = base.feather,
                            customImageUri = base.customImageUri
                        )
                        viewModel.addBlurTrack(newBox)
                        selectedBlurId = newBox.id
                    },
                    onUpdate = { updated ->
                        viewModel.updateBlurTrack(updated)
                    },
                    onStartTracking = { mode ->
                        viewModel.startMotionTracking(itemToEdit.id, mode, positionUs)
                    },
                    onAutoBlurAllFaces = { bType, bShape, bInt, bFeat, bUri ->
                        viewModel.autoBlurAllFaces(
                            currentPlayheadUs = positionUs,
                            blurType = bType,
                            shape = bShape,
                            intensity = bInt,
                            feather = bFeat,
                            customImageUri = bUri
                        )
                        selectedTool = null
                    },
                    onAutoBlackoutAllPeople = { bType, bShape, bInt, bFeat, bUri ->
                        viewModel.autoBlackoutAllPeople(
                            currentPlayheadUs = positionUs,
                            blurType = bType,
                            shape = bShape,
                            intensity = bInt,
                            feather = bFeat,
                            customImageUri = bUri
                        )
                        selectedTool = null
                    },
                    onPickCustomImage = {
                        pickBlurImage.launch("image/*")
                    },
                    onClearTracking = {
                        viewModel.clearBlurTracking(itemToEdit.id)
                    },
                    onAddKeyframeAtPlayhead = {
                        viewModel.setBlurKeyframeAtPlayhead(itemToEdit.id, positionUs)
                    },
                    onRemoveKeyframeAtPlayhead = {
                        viewModel.removeBlurKeyframeAtPlayhead(itemToEdit.id, positionUs)
                    },
                    onStepFrames = { delta ->
                        viewModel.stepPlayheadByFrames(delta)
                    },
                    onSeekToPrevKeyframe = {
                        viewModel.seekToPrevBlurKeyframe(itemToEdit.id)
                    },
                    onSeekToNextKeyframe = {
                        viewModel.seekToNextBlurKeyframe(itemToEdit.id)
                    },
                    onSeekToUs = { targetUs ->
                        viewModel.playerManager.seekToUs(targetUs)
                    },
                    onTogglePlayPause = {
                        viewModel.playerManager.togglePlayPause()
                    },
                    onNudge = { dx, dy ->
                        viewModel.nudgeBlurPosition(itemToEdit.id, dx, dy)
                    },
                    onNudgeSize = { dw, dh ->
                        viewModel.nudgeBlurSize(itemToEdit.id, dw, dh)
                    },
                    isPainting = isFingerPaintActive,
                    onTogglePainting = { isFingerPaintActive = it },
                    paintBrushWidth = paintBrushWidth,
                    onBrushWidthChange = { paintBrushWidth = it },
                    paintIsBlackout = paintIsBlackout,
                    onPaintIsBlackoutChange = { paintIsBlackout = it },
                    paintCensorStyle = paintCensorStyle,
                    onPaintCensorStyleChange = { paintCensorStyle = it },
                    onUndoPaintStroke = {
                        val b = itemToEdit
                        if (b.paintStrokes.isNotEmpty()) {
                            viewModel.updateBlurTrack(b.copy(paintStrokes = b.paintStrokes.dropLast(1)))
                            android.widget.Toast.makeText(ctx, "Undid stroke", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClearPaintStrokes = {
                        viewModel.updateBlurTrack(itemToEdit.copy(paintStrokes = emptyList()))
                        android.widget.Toast.makeText(ctx, "Cleared painted strokes", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        viewModel.removeBlurTrack(itemToEdit.id)
                        selectedBlurId = null
                        selectedTool = null
                    },
                    onDismiss = {
                        selectedTool = null
                    }
                )
            }
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
            onViewLogsClick = onOpenLogs,
            projectTitle = project?.name ?: "Master Video",
            projectDurationUs = project?.totalDurationUs ?: 10_000_000L
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

    // ── Dedicated Interactive Crop Modal for Overlays & Stickers ──
    if (overlayCropTarget != null) {
        val targetOv = overlayCropTarget!!
        val assetPath = when {
            targetOv.text.startsWith("asset://") || (!targetOv.text.contains(" ") && (targetOv.text.endsWith(".png") || targetOv.text.endsWith(".webp"))) -> targetOv.text
            targetOv.imageUri != null && (targetOv.imageUri.startsWith("asset://") || targetOv.imageUri.endsWith(".png") || targetOv.imageUri.endsWith(".webp")) -> targetOv.imageUri
            else -> null
        }
        val cleanPath = assetPath?.removePrefix("asset://")
        val overlayBitmap = remember(cleanPath) {
            if (cleanPath != null) {
                try {
                    ctx.assets.open(cleanPath).use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }?.asImageBitmap()
                } catch (_: Throwable) { null }
            } else null
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { overlayCropTarget = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                color = Color(0xF00A0D14)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { overlayCropTarget = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Crop Overlay / Sticker", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Drag handles or pan to frame your graphic", color = AccentPrimary, fontSize = 11.sp)
                        }
                        IconButton(onClick = {
                            viewModel.setOverlayCrop(targetOv.id, overlayCropRect)
                            overlayCropTarget = null
                            android.widget.Toast.makeText(ctx, "Overlay crop applied", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Apply", tint = AccentPrimary)
                        }
                    }

                    // Interactive Canvas with Image Preview & Freeform Crop Overlay
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(Color(0xFF10141D), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF262C3A), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        var cropCanvasSize by remember { mutableStateOf(IntSize.Zero) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .onSizeChanged { cropCanvasSize = it },
                            contentAlignment = Alignment.Center
                        ) {
                            val cw = cropCanvasSize.width.toFloat()
                            val ch = cropCanvasSize.height.toFloat()

                            if (overlayBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = overlayBitmap,
                                    contentDescription = "Overlay Crop Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }

                            if (cw > 0f && ch > 0f) {
                                FreeformCropPreviewOverlay(
                                    cropRect = overlayCropRect,
                                    boxWidth = cw,
                                    boxHeight = ch,
                                    activePresetId = overlayCropPresetId,
                                    onPresetChange = { overlayCropPresetId = it },
                                    onCropChange = { newCrop -> overlayCropRect = newCrop }
                                )
                            }
                        }
                    }

                    // Preset bottom bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(com.clipcut.ui.components.CROP_PRESETS) { preset ->
                                val isSelected = overlayCropPresetId == preset.id
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) AccentPrimary else Surface2,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) AccentPrimary else HairlineBorder
                                    ),
                                    modifier = Modifier.clickable {
                                        overlayCropPresetId = preset.id
                                        if (preset.id == "full") {
                                            overlayCropRect = CropRect(0f, 0f, 1f, 1f)
                                        } else if (preset.ratio != null) {
                                            overlayCropRect = com.clipcut.ui.components.computePresetCrop(preset.ratio, 1000f, 1000f)
                                        }
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(text = preset.iconEmoji, fontSize = 12.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = preset.label,
                                            color = if (isSelected) Color(0xFF0E1117) else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SecondaryButton(
                                text = "Reset",
                                onClick = {
                                    overlayCropRect = CropRect(0f, 0f, 1f, 1f)
                                    overlayCropPresetId = "free"
                                },
                                icon = Icons.Filled.Refresh,
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryButton(
                                text = "Done",
                                onClick = {
                                    viewModel.setOverlayCrop(targetOv.id, overlayCropRect)
                                    overlayCropTarget = null
                                    android.widget.Toast.makeText(ctx, "Overlay crop applied", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                icon = Icons.Filled.Check,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    onBack: () -> Unit,
    onLogs: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    title: String,
    currentAspectRatio: AspectRatio,
    onSelectAspectRatio: (AspectRatio) -> Unit
) {
    Surface(
        color = Surface1.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Section: Back & Title Stack
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                    Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = TextSecondary, modifier = Modifier.size(12.dp))
                }
            }

            // Center Badges: Aspect & Format Engine
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var showAspectRatioMenu by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Surface3.copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .clickable { showAspectRatioMenu = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(currentAspectRatio.label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Aspect", tint = TextSecondary, modifier = Modifier.size(12.dp))
                    }
                    DropdownMenu(
                        expanded = showAspectRatioMenu,
                        onDismissRequest = { showAspectRatioMenu = false },
                        modifier = Modifier.background(Surface2)
                    ) {
                        AspectRatio.values().forEach { ar ->
                            DropdownMenuItem(
                                text = { Text("${ar.label} (${ar.description})", color = TextPrimary, fontSize = 12.sp) },
                                onClick = {
                                    onSelectAspectRatio(ar)
                                    showAspectRatioMenu = false
                                }
                            )
                        }
                    }
                }

                // 4K 60 HDR Badge with pulsing emerald dot
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Surface3.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(5.dp).background(Color(0xFF10B981), CircleShape))
                    Text(
                        "4K 60 HDR",
                        color = TextPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            // Right Section: Undo / Redo / Logs / Delete / Glowing Gradient Export
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onUndo, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Undo, contentDescription = "Undo", tint = TextSecondary, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onRedo, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Redo, contentDescription = "Redo", tint = TextSecondary, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onLogs, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.BugReport, contentDescription = "Logs", tint = TextSecondary.copy(alpha = 0.7f), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(17.dp))
                }

                Spacer(Modifier.width(4.dp))

                // Glowing Cyan-Violet Gradient Export Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00F2FE), Color(0xFF38BDF8), Color(0xFF7C3AED))
                            )
                        )
                        .clickable { onExport() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Export", color = Color(0xFF0C0E14), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Export",
                            tint = Color(0xFF0C0E14),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
