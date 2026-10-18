package com.clipcut.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.CropRect
import com.clipcut.ui.theme.AccentPrimary
import com.clipcut.ui.theme.HairlineBorder
import com.clipcut.ui.theme.ObsidianBackground
import com.clipcut.ui.theme.ObsidianSurfaceHigh

data class AspectRatioPreset(
    val id: String,
    val label: String,
    val ratio: Float?, // width / height or null for freeform
    val iconEmoji: String
)

val CROP_PRESETS = listOf(
    AspectRatioPreset("free", "Free", null, "✨"),
    AspectRatioPreset("16:9", "16:9", 16f / 9f, "📺"),
    AspectRatioPreset("9:16", "9:16", 9f / 16f, "📱"),
    AspectRatioPreset("1:1", "1:1", 1f, "⬜"),
    AspectRatioPreset("4:5", "4:5", 4f / 5f, "📸"),
    AspectRatioPreset("4:3", "4:3", 4f / 3f, "📼"),
    AspectRatioPreset("21:9", "21:9", 21f / 9f, "🎬"),
    AspectRatioPreset("full", "Full", 0f, "🔲")
)

/**
 * Calculates a crop rectangle centered in the viewport for a given aspect ratio (w/h).
 */
fun computePresetCrop(presetRatio: Float?, bw: Float, bh: Float): CropRect {
    if (presetRatio == null || presetRatio <= 0f || bw <= 0f || bh <= 0f) {
        return CropRect(0f, 0f, 1f, 1f)
    }
    val canvasRatio = bw / bh
    val normRatio = presetRatio / canvasRatio
    val targetW: Float
    val targetH: Float
    if (normRatio <= 1f) {
        targetH = 1.0f
        targetW = (targetH * normRatio).coerceIn(0.1f, 1f)
    } else {
        targetW = 1.0f
        targetH = (targetW / normRatio).coerceIn(0.1f, 1f)
    }
    val l = ((1f - targetW) / 2f).coerceIn(0f, 1f - targetW)
    val t = ((1f - targetH) / 2f).coerceIn(0f, 1f - targetH)
    return CropRect(l, t, l + targetW, t + targetH)
}

enum class CropHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_EDGE,
    BOTTOM_EDGE,
    LEFT_EDGE,
    RIGHT_EDGE,
    CENTER_PAN
}

private fun determineHandle(
    touchPos: Offset,
    pxLeft: Float,
    pxTop: Float,
    pxRight: Float,
    pxBottom: Float,
    cornerRadius: Float,
    edgeMargin: Float
): CropHandle {
    val x = touchPos.x
    val y = touchPos.y

    // 1. Check 4 corners first (corners have highest priority)
    val distTL = kotlin.math.hypot((x - pxLeft).toDouble(), (y - pxTop).toDouble()).toFloat()
    val distTR = kotlin.math.hypot((x - pxRight).toDouble(), (y - pxTop).toDouble()).toFloat()
    val distBL = kotlin.math.hypot((x - pxLeft).toDouble(), (y - pxBottom).toDouble()).toFloat()
    val distBR = kotlin.math.hypot((x - pxRight).toDouble(), (y - pxBottom).toDouble()).toFloat()

    val minDist = minOf(distTL, distTR, distBL, distBR)
    if (minDist <= cornerRadius) {
        return when (minDist) {
            distTL -> CropHandle.TOP_LEFT
            distTR -> CropHandle.TOP_RIGHT
            distBL -> CropHandle.BOTTOM_LEFT
            else -> CropHandle.BOTTOM_RIGHT
        }
    }

    // 2. Check 4 edges (within edgeMargin of each boundary)
    val nearTop = kotlin.math.abs(y - pxTop) <= edgeMargin && x >= (pxLeft - edgeMargin) && x <= (pxRight + edgeMargin)
    val nearBottom = kotlin.math.abs(y - pxBottom) <= edgeMargin && x >= (pxLeft - edgeMargin) && x <= (pxRight + edgeMargin)
    val nearLeft = kotlin.math.abs(x - pxLeft) <= edgeMargin && y >= (pxTop - edgeMargin) && y <= (pxBottom + edgeMargin)
    val nearRight = kotlin.math.abs(x - pxRight) <= edgeMargin && y >= (pxTop - edgeMargin) && y <= (pxBottom + edgeMargin)

    if (nearTop) return CropHandle.TOP_EDGE
    if (nearBottom) return CropHandle.BOTTOM_EDGE
    if (nearLeft) return CropHandle.LEFT_EDGE
    if (nearRight) return CropHandle.RIGHT_EDGE

    // 3. Inside the crop box -> Pan
    if (x in pxLeft..pxRight && y in pxTop..pxBottom) {
        return CropHandle.CENTER_PAN
    }

    // 4. If touched outside, pick closest corner or pan
    return when (minDist) {
        distTL -> CropHandle.TOP_LEFT
        distTR -> CropHandle.TOP_RIGHT
        distBL -> CropHandle.BOTTOM_LEFT
        distBR -> CropHandle.BOTTOM_RIGHT
        else -> CropHandle.CENTER_PAN
    }
}

/**
 * Interactive On-Preview Freeform Crop Overlay
 *
 * Employs a single unified touch-surface across the preview so drag gestures never
 * cancel or stutter during real-time state updates.
 */
@Composable
fun FreeformCropPreviewOverlay(
    cropRect: CropRect,
    boxWidth: Float,
    boxHeight: Float,
    activePresetId: String,
    onPresetChange: (String) -> Unit,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    if (boxWidth <= 0f || boxHeight <= 0f) return

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 56.dp.toPx() }
    val edgeMarginPx = with(density) { 40.dp.toPx() }

    val currentCropState by rememberUpdatedState(cropRect)
    val onCropChangeState by rememberUpdatedState(onCropChange)
    val onPresetChangeState by rememberUpdatedState(onPresetChange)

    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
    var liveCrop by remember { mutableStateOf(cropRect) }

    // Keep liveCrop in sync when external preset or reset buttons are clicked
    LaunchedEffect(cropRect) {
        if (activeHandle == CropHandle.NONE) {
            liveCrop = cropRect
        }
    }

    val displayCrop = if (activeHandle != CropHandle.NONE) liveCrop else cropRect
    val pxLeft = (displayCrop.left * boxWidth).coerceIn(0f, boxWidth)
    val pxTop = (displayCrop.top * boxHeight).coerceIn(0f, boxHeight)
    val pxRight = (displayCrop.right * boxWidth).coerceIn(pxLeft + 40f, boxWidth)
    val pxBottom = (displayCrop.bottom * boxHeight).coerceIn(pxTop + 40f, boxHeight)
    val cropW = pxRight - pxLeft
    val cropH = pxBottom - pxTop

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(boxWidth, boxHeight) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val current = liveCrop
                        val pxL = (current.left * boxWidth).coerceIn(0f, boxWidth)
                        val pxT = (current.top * boxHeight).coerceIn(0f, boxHeight)
                        val pxR = (current.right * boxWidth).coerceIn(pxL + 40f, boxWidth)
                        val pxB = (current.bottom * boxHeight).coerceIn(pxT + 40f, boxHeight)
                        activeHandle = determineHandle(startOffset, pxL, pxT, pxR, pxB, cornerRadiusPx, edgeMarginPx)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (activeHandle == CropHandle.NONE) return@detectDragGestures

                        val dxNorm = dragAmount.x / boxWidth
                        val dyNorm = dragAmount.y / boxHeight
                        val minNorm = 0.08f

                        var newL = liveCrop.left
                        var newT = liveCrop.top
                        var newR = liveCrop.right
                        var newB = liveCrop.bottom

                        when (activeHandle) {
                            CropHandle.CENTER_PAN -> {
                                val w = newR - newL
                                val h = newB - newT
                                newL = (newL + dxNorm).coerceIn(0f, 1f - w)
                                newT = (newT + dyNorm).coerceIn(0f, 1f - h)
                                newR = newL + w
                                newB = newT + h
                            }
                            CropHandle.TOP_LEFT -> {
                                onPresetChangeState("free")
                                newL = (newL + dxNorm).coerceIn(0f, newR - minNorm)
                                newT = (newT + dyNorm).coerceIn(0f, newB - minNorm)
                            }
                            CropHandle.TOP_RIGHT -> {
                                onPresetChangeState("free")
                                newR = (newR + dxNorm).coerceIn(newL + minNorm, 1f)
                                newT = (newT + dyNorm).coerceIn(0f, newB - minNorm)
                            }
                            CropHandle.BOTTOM_LEFT -> {
                                onPresetChangeState("free")
                                newL = (newL + dxNorm).coerceIn(0f, newR - minNorm)
                                newB = (newB + dyNorm).coerceIn(newT + minNorm, 1f)
                            }
                            CropHandle.BOTTOM_RIGHT -> {
                                onPresetChangeState("free")
                                newR = (newR + dxNorm).coerceIn(newL + minNorm, 1f)
                                newB = (newB + dyNorm).coerceIn(newT + minNorm, 1f)
                            }
                            CropHandle.TOP_EDGE -> {
                                onPresetChangeState("free")
                                newT = (newT + dyNorm).coerceIn(0f, newB - minNorm)
                            }
                            CropHandle.BOTTOM_EDGE -> {
                                onPresetChangeState("free")
                                newB = (newB + dyNorm).coerceIn(newT + minNorm, 1f)
                            }
                            CropHandle.LEFT_EDGE -> {
                                onPresetChangeState("free")
                                newL = (newL + dxNorm).coerceIn(0f, newR - minNorm)
                            }
                            CropHandle.RIGHT_EDGE -> {
                                onPresetChangeState("free")
                                newR = (newR + dxNorm).coerceIn(newL + minNorm, 1f)
                            }
                            CropHandle.NONE -> {}
                        }

                        val updated = CropRect(newL, newT, newR, newB)
                        liveCrop = updated
                        onCropChangeState(updated)
                    },
                    onDragEnd = {
                        activeHandle = CropHandle.NONE
                    },
                    onDragCancel = {
                        activeHandle = CropHandle.NONE
                    }
                )
            }
    ) {
        // High-precision canvas overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maskColor = Color(0x99000000)

            // Outer dark mask rectangles
            if (pxTop > 0f) {
                drawRect(maskColor, topLeft = Offset(0f, 0f), size = Size(boxWidth, pxTop))
            }
            if (pxBottom < boxHeight) {
                drawRect(maskColor, topLeft = Offset(0f, pxBottom), size = Size(boxWidth, boxHeight - pxBottom))
            }
            if (pxLeft > 0f && cropH > 0f) {
                drawRect(maskColor, topLeft = Offset(0f, pxTop), size = Size(pxLeft, cropH))
            }
            if (pxRight < boxWidth && cropH > 0f) {
                drawRect(maskColor, topLeft = Offset(pxRight, pxTop), size = Size(boxWidth - pxRight, cropH))
            }

            // 3x3 Rule-of-Thirds Grid
            val gridColor = Color.White.copy(alpha = 0.35f)
            val stroke1px = 1.dp.toPx()
            val oneThirdW = cropW / 3f
            val oneThirdH = cropH / 3f

            drawLine(gridColor, start = Offset(pxLeft + oneThirdW, pxTop), end = Offset(pxLeft + oneThirdW, pxBottom), strokeWidth = stroke1px)
            drawLine(gridColor, start = Offset(pxLeft + 2f * oneThirdW, pxTop), end = Offset(pxLeft + 2f * oneThirdW, pxBottom), strokeWidth = stroke1px)
            drawLine(gridColor, start = Offset(pxLeft, pxTop + oneThirdH), end = Offset(pxRight, pxTop + oneThirdH), strokeWidth = stroke1px)
            drawLine(gridColor, start = Offset(pxLeft, pxTop + 2f * oneThirdH), end = Offset(pxRight, pxTop + 2f * oneThirdH), strokeWidth = stroke1px)

            // Crop Box White Border with dark subtle outer shadow
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(pxLeft - 1f, pxTop - 1f),
                size = Size(cropW + 2f, cropH + 2f),
                style = Stroke(width = 3.dp.toPx())
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(pxLeft, pxTop),
                size = Size(cropW, cropH),
                style = Stroke(width = 2.dp.toPx())
            )

            // Corner L-Bracket Visual Indicators (26px length, 4.5px thick)
            val bracketLen = 26.dp.toPx().coerceAtMost(cropW / 3f).coerceAtMost(cropH / 3f)
            val bracketThick = 4.5.dp.toPx()

            // Top-Left
            drawRect(Color.White, topLeft = Offset(pxLeft - 2f, pxTop - 2f), size = Size(bracketLen, bracketThick))
            drawRect(Color.White, topLeft = Offset(pxLeft - 2f, pxTop - 2f), size = Size(bracketThick, bracketLen))

            // Top-Right
            drawRect(Color.White, topLeft = Offset(pxRight - bracketLen + 2f, pxTop - 2f), size = Size(bracketLen, bracketThick))
            drawRect(Color.White, topLeft = Offset(pxRight - bracketThick + 2f, pxTop - 2f), size = Size(bracketThick, bracketLen))

            // Bottom-Left
            drawRect(Color.White, topLeft = Offset(pxLeft - 2f, pxBottom - bracketThick + 2f), size = Size(bracketLen, bracketThick))
            drawRect(Color.White, topLeft = Offset(pxLeft - 2f, pxBottom - bracketLen + 2f), size = Size(bracketThick, bracketLen))

            // Bottom-Right
            drawRect(Color.White, topLeft = Offset(pxRight - bracketLen + 2f, pxBottom - bracketThick + 2f), size = Size(bracketLen, bracketThick))
            drawRect(Color.White, topLeft = Offset(pxRight - bracketThick + 2f, pxBottom - bracketLen + 2f), size = Size(bracketThick, bracketLen))

            // Edge Pill Visual Indicators
            val pillLen = 32.dp.toPx().coerceAtMost(cropW / 3f).coerceAtMost(cropH / 3f)
            // Top Edge
            drawRect(Color.White, topLeft = Offset(pxLeft + (cropW - pillLen) / 2f, pxTop - 2f), size = Size(pillLen, bracketThick))
            // Bottom Edge
            drawRect(Color.White, topLeft = Offset(pxLeft + (cropW - pillLen) / 2f, pxBottom - bracketThick + 2f), size = Size(pillLen, bracketThick))
            // Left Edge
            drawRect(Color.White, topLeft = Offset(pxLeft - 2f, pxTop + (cropH - pillLen) / 2f), size = Size(bracketThick, pillLen))
            // Right Edge
            drawRect(Color.White, topLeft = Offset(pxRight - bracketThick + 2f, pxTop + (cropH - pillLen) / 2f), size = Size(bracketThick, pillLen))
        }
    }
}

/**
 * Dedicated Crop Control Bottom Bar
 *
 * Replaces the default editor toolbar when EditorTool.CROP is active.
 * Leaves the video preview 100% visible and interactive.
 */
@Composable
fun CropControlBottomBar(
    activePresetId: String,
    onSelectPreset: (AspectRatioPreset) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Row: Cancel (X), Title + Preset Pill, Reset, Done (✓)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Cancel Button
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(38.dp)
                        .background(ObsidianSurfaceHigh, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel Crop",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Title & Active Mode Badge
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Crop & Framing",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (activePresetId == "free") "Freeform Drag" else "Preset: $activePresetId",
                        color = AccentPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Reset Button
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier
                            .size(38.dp)
                            .background(ObsidianSurfaceHigh, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset Crop",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Done / Apply Button
                    Button(
                        onClick = onApply,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            contentColor = Color(0xFF0E1117)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Done",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Aspect Ratio Preset Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CROP_PRESETS) { preset ->
                    val isSelected = activePresetId == preset.id
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) AccentPrimary else ObsidianSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AccentPrimary else HairlineBorder
                        ),
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelectPreset(preset)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = preset.iconEmoji,
                                fontSize = 12.sp
                            )
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
        }
    }
}
