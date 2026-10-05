package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.*
import com.clipcut.ui.components.EditorFilterChip
import com.clipcut.ui.components.PrimaryButton
import com.clipcut.ui.components.SecondaryButton
import com.clipcut.ui.theme.*

/**
 * Consolidated sheet components. Each composable listed in the API reference
 * (AudioSheet, BeatMarkerSheet, BlendModeDialog, ChromaKeyDialog, ExportDialog,
 * FilterSheet, LutPickerDialog, MaskDialog, OverlaySheet, SpeedRampDialog,
 * TextAnimationPicker, TrimSheet) is implemented here, plus the additional
 * sheets required by the 22 tools (SpeedSheet, VolumeSheet, RotateSheet,
 * CropSheet, FlipSheet, TextSheet, StickerSheet, CaptionSheet, VoiceoverSheet,
 * KeyframeSheet, PiPPanel, EffectsSheet, TransitionsSheet, AudioDuckingSheet).
 */

@Composable
fun SheetHeader(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
    }
}

@Composable
fun SheetSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(String.format("%.2f", value), color = Color.White, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = AccentPrimary,
                activeTrackColor = AccentPrimary,
                inactiveTrackColor = HairlineBorder
            )
        )
    }
}

// ── AudioSheet ─────────────────────────────────────────────────────────
@Composable
fun AudioSheet(
    originalVideoVolume: Float,
    backgroundMusicVolume: Float,
    hasMusicTrack: Boolean,
    onVolumesChanged: (Float, Float) -> Unit,
    onPickMusicClick: () -> Unit,
    onVoiceoverClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Audio", onDismiss)
        Spacer(Modifier.height(12.dp))
        SheetSlider("Original Video", originalVideoVolume, 0f..2f) { onVolumesChanged(it, backgroundMusicVolume) }
        SheetSlider("Background Music", backgroundMusicVolume, 0f..2f) { onVolumesChanged(originalVideoVolume, it) }
        Spacer(Modifier.height(16.dp))
        if (!hasMusicTrack) {
            PrimaryButton("Add Music Track", onPickMusicClick, modifier = Modifier.fillMaxWidth())
        } else {
            Text("1 music track loaded", color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Record Voiceover", onClick = onVoiceoverClick, modifier = Modifier.fillMaxWidth())
    }
}

// ── BeatMarkerSheet ─────────────────────────────────────────────────────
@Composable
fun BeatMarkerSheet(
    totalDurationUs: Long,
    existingMarkers: List<BeatMarker>,
    onAddMarker: (Long) -> Unit,
    onRemoveMarker: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        SheetHeader("Beat Markers", onDismiss)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Add at playhead", onClick = { onAddMarker(0L) }, modifier = Modifier.weight(1f))
            SecondaryButton("Clear All", onClick = onClearAll, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        existingMarkers.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${m.timeUs / 1000} ms • strength ${String.format("%.2f", m.strength)}",
                    color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemoveMarker(m.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = AccentPrimary)
                }
            }
        }
    }
}

// ── BlendModeDialog ────────────────────────────────────────────────────
@Composable
fun BlendModeDialog(
    initial: BlendMode,
    onApply: (BlendMode) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Blend Mode", onDismiss)
        Spacer(Modifier.height(12.dp))
        BlendMode.values().forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { current = mode }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = current == mode, onClick = { current = mode }, colors = RadioButtonDefaults.colors(selectedColor = AccentPrimary))
                Spacer(Modifier.width(8.dp))
                Text(mode.displayName, color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(current) }, modifier = Modifier.fillMaxWidth())
    }
}

// ── ChromaKeyDialog ────────────────────────────────────────────────────
@Composable
fun ChromaKeyDialog(
    initialColor: FloatArray,
    initialThreshold: Float,
    initialSmoothness: Float,
    onApply: (FloatArray, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var threshold by remember { mutableStateOf(initialThreshold) }
    var smoothness by remember { mutableStateOf(initialSmoothness) }
    var colorPick by remember { mutableStateOf(initialColor) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Chroma Key", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Key Color", color = TextSecondary, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf(
                "Green" to floatArrayOf(0f, 1f, 0f),
                "Blue" to floatArrayOf(0f, 0f, 1f),
                "Red" to floatArrayOf(1f, 0f, 0f)
            ).forEach { (label, c) ->
                EditorFilterChip(label, colorPick.contentEquals(c)) { colorPick = c }
            }
        }
        SheetSlider("Threshold", threshold, 0.01f..0.6f) { threshold = it }
        SheetSlider("Smoothness", smoothness, 0.01f..0.3f) { smoothness = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(colorPick, threshold, smoothness) }, modifier = Modifier.fillMaxWidth())
    }
}

// ── MaskDialog ─────────────────────────────────────────────────────────
@Composable
fun MaskDialog(
    initial: MaskConfig,
    onApply: (MaskConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var shape by remember { mutableStateOf(initial.shape) }
    var width by remember { mutableStateOf(initial.width) }
    var height by remember { mutableStateOf(initial.height) }
    var feather by remember { mutableStateOf(initial.feather) }
    var inverted by remember { mutableStateOf(initial.inverted) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Mask", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Shape", color = TextSecondary, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            MaskShape.values().forEach { s ->
                EditorFilterChip(s.displayName, shape == s) { shape = s }
            }
        }
        SheetSlider("Width", width, 0.1f..1f) { width = it }
        SheetSlider("Height", height, 0.1f..1f) { height = it }
        SheetSlider("Feather", feather, 0f..0.3f) { feather = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = inverted, onCheckedChange = { inverted = it }, colors = CheckboxDefaults.colors(checkedColor = AccentPrimary))
            Text("Invert mask", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = {
            onApply(MaskConfig(shape, initial.centerX, initial.centerY, width, height, initial.rotation, feather, inverted))
        }, modifier = Modifier.fillMaxWidth())
    }
}

// ── LutPickerDialog ────────────────────────────────────────────────────
@Composable
fun LutPickerDialog(
    currentLut: LutConfig?,
    onApply: (LutConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        SheetHeader("LUT", onDismiss)
        Spacer(Modifier.height(12.dp))
        // Bundled LUT list (would ship in assets/luts/)
        val luts = listOf("Neutral", "TealOrange", "Vintage", "Noir", "Bloom")
        luts.forEach { name ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onApply(LutConfig(name = name, uri = "asset://luts/${name.lowercase()}.png")) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Clear LUT", onClick = { onApply(null) }, modifier = Modifier.fillMaxWidth())
    }
}

// ── FilterSheet ────────────────────────────────────────────────────────
@Composable
fun FilterSheet(
    initialConfig: FilterConfig,
    onApplyFilter: (FilterConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var cfg by remember { mutableStateOf(initialConfig) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Filter", onDismiss)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FilterType.values().toList()) { ft ->
                EditorFilterChip(ft.displayName, cfg.filterType == ft) {
                    cfg = cfg.copy(filterType = ft)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SheetSlider("Brightness", cfg.brightness, -0.5f..0.5f) { cfg = cfg.copy(brightness = it) }
        SheetSlider("Contrast", cfg.contrast, 0.5f..2f) { cfg = cfg.copy(contrast = it) }
        SheetSlider("Saturation", cfg.saturation, 0f..2f) { cfg = cfg.copy(saturation = it) }
        SheetSlider("Temperature", cfg.temperature, -0.5f..0.5f) { cfg = cfg.copy(temperature = it) }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApplyFilter(cfg) }, modifier = Modifier.fillMaxWidth())
    }
}

// ── SpeedRampDialog ─────────────────────────────────────────────────────
@Composable
fun SpeedRampDialog(
    initialRamp: SpeedRamp,
    clipDurationUs: Long,
    onApply: (SpeedRamp) -> Unit,
    onDismiss: () -> Unit
) {
    var ramp by remember { mutableStateOf(initialRamp) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Speed Ramp", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Speed keyframes (${ramp.keyframes.size})", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        ramp.keyframes.forEachIndexed { i, kf ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${i+1}  ${kf.timeOffsetUs/1000}ms  ×${String.format("%.2f", kf.speed)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { ramp = ramp.copy(keyframes = ramp.keyframes.filterNot { it.id == kf.id }) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = AccentPrimary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Add keyframe at playhead", onClick = {
            val newKf = SpeedKeyframe(timeOffsetUs = clipDurationUs / 2, speed = 1f)
            ramp = ramp.copy(keyframes = (ramp.keyframes + newKf).sortedBy { it.timeOffsetUs })
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(ramp) }, modifier = Modifier.fillMaxWidth())
    }
}

// ── TextAnimationPicker ────────────────────────────────────────────────
@Composable
fun TextAnimationPicker(selected: TextAnimation, onSelect: (TextAnimation) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TextAnimation.values().toList()) { a ->
            EditorFilterChip(a.displayName, selected == a) { onSelect(a) }
        }
    }
}

// ── OverlaySheet ───────────────────────────────────────────────────────
@Composable
fun OverlaySheet(
    totalDurationUs: Long,
    initialPlayheadUs: Long = 0L,
    existingOverlays: List<OverlayItem> = emptyList(),
    onAddOverlay: (OverlayItem) -> Unit,
    onDeleteOverlay: (String) -> Unit = {},
    onSeekPreview: (Long) -> Unit = {},
    onOpenPiP: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Overlays", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Existing (${existingOverlays.size})", color = Color.White, fontSize = 12.sp)
        existingOverlays.forEach { o ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(o.text.ifEmpty { "Sticker" }, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onDeleteOverlay(o.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AccentPrimary) }
            }
        }
        Spacer(Modifier.height(12.dp))
        var text by remember { mutableStateOf("") }
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Caption text", color = TextTertiary) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface3, unfocusedContainerColor = Surface3,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = AccentPrimary
            )
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Add Text Overlay", onClick = {
            if (text.isNotBlank()) {
                onAddOverlay(OverlayItem(text = text, timelineStartUs = initialPlayheadUs, timelineEndUs = initialPlayheadUs + 3_000_000L))
                text = ""
            }
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Add PiP Video/Image", onClick = onOpenPiP, modifier = Modifier.fillMaxWidth())
    }
}

// ── ExportDialog ───────────────────────────────────────────────────────
@Composable
fun ExportDialog(
    projectAspectRatio: AspectRatio,
    exportState: com.clipcut.ui.viewmodel.ExportState,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit,
    onViewLogsClick: () -> Unit
) {
    var resolution by remember { mutableStateOf(ExportResolution.RES_1080P) }
    var codec by remember { mutableStateOf(ExportCodec.H264) }
    var fps by remember { mutableStateOf(30) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Export", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Resolution", color = TextSecondary, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            ExportResolution.values().forEach { r ->
                EditorFilterChip(r.label, resolution == r) { resolution = r }
            }
        }
        Text("Codec", color = TextSecondary, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            ExportCodec.values().forEach { c ->
                EditorFilterChip(c.label, codec == c) { codec = c }
            }
        }
        Text("Frame Rate", color = TextSecondary, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf(24, 30, 60).forEach { f ->
                EditorFilterChip("${f}fps", fps == f) { fps = f }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (exportState) {
            is com.clipcut.ui.viewmodel.ExportState.Progress -> {
                LinearProgressIndicator(progress = { exportState.progress }, modifier = Modifier.fillMaxWidth(), color = AccentPrimary)
                Spacer(Modifier.height(4.dp))
                Text(exportState.status, color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Cancel", onClick = onCancelExport, modifier = Modifier.fillMaxWidth())
            }
            is com.clipcut.ui.viewmodel.ExportState.Success -> {
                Text("Saved to: ${exportState.galleryPath}", color = AccentSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
            is com.clipcut.ui.viewmodel.ExportState.Error -> {
                Text("Export failed: ${exportState.message}", color = AccentPrimary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("View Logs", onClick = onViewLogsClick, modifier = Modifier.weight(1f))
                    PrimaryButton("Retry", onClick = {
                        onStartExport(ExportConfig(resolution = resolution, codec = codec, frameRate = fps))
                    }, modifier = Modifier.weight(1f))
                }
            }
            com.clipcut.ui.viewmodel.ExportState.Idle -> {
                PrimaryButton("Export", onClick = {
                    onStartExport(ExportConfig(resolution = resolution, codec = codec, frameRate = fps))
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ── Tool sheets (one per tool not covered by API ref) ─────────────────

@Composable
fun SpeedSheet(current: Float, onApply: (Float) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(current) }
    Column {
        SheetHeader("Speed", onDismiss)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f)) { sp ->
                EditorFilterChip("${sp}x", s == sp) { s = sp }
            }
        }
        SheetSlider("Custom", s, 0.1f..10f) { s = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(s) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun VolumeSheet(current: Float, onApply: (Float) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(current) }
    Column {
        SheetHeader("Volume", onDismiss)
        Spacer(Modifier.height(12.dp))
        SheetSlider("Volume", v, 0f..2f) { v = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(v) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RotateSheet(current: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    Column {
        SheetHeader("Rotate", onDismiss)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 90, 180, 270).forEach { d ->
                EditorFilterChip("${d}°", current == d) { onApply(d) }
            }
        }
    }
}

@Composable
fun CropSheet(current: CropRect, onApply: (CropRect) -> Unit, onDismiss: () -> Unit) {
    var l by remember { mutableStateOf(current.left) }
    var t by remember { mutableStateOf(current.top) }
    var r by remember { mutableStateOf(current.right) }
    var b by remember { mutableStateOf(current.bottom) }
    Column {
        SheetHeader("Crop", onDismiss)
        Spacer(Modifier.height(12.dp))
        SheetSlider("Left", l, 0f..0.5f) { l = it }
        SheetSlider("Top", t, 0f..0.5f) { t = it }
        SheetSlider("Right", r, 0.5f..1f) { r = it }
        SheetSlider("Bottom", b, 0.5f..1f) { b = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(CropRect(l, t, r, b)) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun FlipSheet(flipH: Boolean, flipV: Boolean, onApply: (Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    var h by remember { mutableStateOf(flipH) }
    var v by remember { mutableStateOf(flipV) }
    Column {
        SheetHeader("Flip", onDismiss)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = h, onCheckedChange = { h = it }, colors = CheckboxDefaults.colors(checkedColor = AccentPrimary))
            Text("Horizontal", color = Color.White, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = v, onCheckedChange = { v = it }, colors = CheckboxDefaults.colors(checkedColor = AccentPrimary))
            Text("Vertical", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(h, v) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TrimSheet(startUs: Long, endUs: Long, onApply: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(startUs.toFloat()) }
    var e by remember { mutableStateOf(endUs.toFloat()) }
    Column {
        SheetHeader("Trim", onDismiss)
        Spacer(Modifier.height(12.dp))
        SheetSlider("Start", s, 0f..(endUs.toFloat()), onValueChange = { s = it })
        SheetSlider("End", e, (startUs.toFloat())..(endUs.toFloat() * 2), onValueChange = { e = it })
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(s.toLong(), e.toLong()) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TextSheet(
    onApply: (OverlayItem) -> Unit,
    onOpenCaptions: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(28f) }
    var bold by remember { mutableStateOf(true) }
    Column {
        SheetHeader("Text", onDismiss)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Type text", color = TextTertiary) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface3, unfocusedContainerColor = Surface3,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = AccentPrimary
            )
        )
        SheetSlider("Size", size, 12f..80f) { size = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = bold, onCheckedChange = { bold = it }, colors = CheckboxDefaults.colors(checkedColor = AccentPrimary))
            Text("Bold", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Add", onClick = {
            if (text.isNotBlank()) onApply(OverlayItem(text = text, textSizeSp = size, textBold = bold))
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Auto Captions", onClick = onOpenCaptions, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun StickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Column {
        SheetHeader("Stickers", onDismiss)
        Spacer(Modifier.height(12.dp))
        val categories = listOf("Emoji", "Shapes", "Arrows", "Reactions", "Banners")
        var category by remember { mutableStateOf("Emoji") }
        TabRow(selectedTabIndex = categories.indexOf(category), containerColor = Surface2, contentColor = AccentPrimary) {
            categories.forEach { cat ->
                Tab(selected = cat == category, onClick = { category = cat }, text = { Text(cat, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(12.dp))
        // 100+ stickers are bundled in assets/stickers/{category}/{n}.svg
        // For the build we render a grid of emoji glyphs as placeholders for
        // the vector packs. In production, parse assets/stickers/index.json
        // and load vector drawables from the asset manager.
        val glyphs = when (category) {
            "Emoji" -> listOf("😀","😍","😎","😂","😉","😜","🥳","😇","😡","😱","🤔","😴","🤯","👏","🙏","🤝")
            "Shapes" -> listOf("●","■","▲","◆","★","♦","♣","♠","♥","◐","◑","◒","◓","∞","≈","≡")
            "Arrows" -> listOf("→","←","↑","↓","↔","↕","⇒","⇐","⇑","⇓","⇔","⇕","➜","➤","➢","➣")
            "Reactions" -> listOf("❤","💛","💚","💙","💜","🖤","💔","❣","💕","💞","💖","💘","👍","👎","👌","✌")
            "Banners" -> listOf("🎉","🎊","🎈","✨","⭐","🌟","💫","🔥","💥","💦","💨","🎵","🎶","✅","❌","⚠")
            else -> listOf("?")
        }
        LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(280.dp)) {
            items(glyphs) { g ->
                Box(modifier = Modifier
                    .padding(4.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface3)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPick(g) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(g, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
fun CaptionSheet(
    onApplyCaptions: (List<OverlayItem>) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val generator = remember { com.clipcut.core.feature.CaptionGenerator() }
    val segments by generator.segments.collectAsState()
    val partial by generator.partial.collectAsState()
    var isTranscribing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (isTranscribing) {
                generator.stop()
            }
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Auto Captions", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text(
            "Captions are generated on-device using SpeechRecognizer. Tap Start Transcription to listen.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        if (partial.isNotBlank()) {
            Surface(
                color = Surface3,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = partial,
                    color = AccentPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (segments.isNotEmpty()) {
            Text("Generated Segments (${segments.size}):", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            segments.forEachIndexed { idx, seg ->
                Text(
                    "${idx + 1}. \"${seg.text}\"",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                text = if (isTranscribing) "Stop" else "Start Transcription",
                onClick = {
                    if (isTranscribing) {
                        generator.stop()
                        isTranscribing = false
                    } else {
                        generator.start(context)
                        isTranscribing = true
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (segments.isNotEmpty()) {
                SecondaryButton(
                    text = "Apply to Timeline",
                    onClick = {
                        val overlays = segments.map { s ->
                            OverlayItem(
                                text = s.text,
                                timelineStartUs = s.startTimeUs,
                                timelineEndUs = s.endTimeUs,
                                normalizedX = 0.5f,
                                normalizedY = 0.85f,
                                textSizeSp = 24f,
                                backgroundColor = 0x80000000L
                            )
                        }
                        onApplyCaptions(overlays)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun VoiceoverSheet(
    playheadUs: Long = 0L,
    onVoiceoverRecorded: (com.clipcut.core.model.AudioTrackItem) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recorder = remember { com.clipcut.core.feature.VoiceoverRecorder(context) }
    val isRecording by recorder.isRecording.collectAsState()
    val amplitude by recorder.amplitude.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            if (recorder.isRecording.value) {
                recorder.stop()
            }
        }
    }

    Column {
        SheetHeader("Voiceover", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Record AAC m4a into the audio track at the playhead.", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        if (isRecording) {
            LinearProgressIndicator(
                progress = { amplitude },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = AccentPrimary,
                trackColor = Surface3
            )
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                if (isRecording) {
                    val item = recorder.stop()
                    if (item != null) {
                        onVoiceoverRecorded(item.copy(timelineStartUs = playheadUs))
                        onDismiss()
                    }
                } else {
                    recorder.start()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) AccentPrimary else AccentSecondary,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isRecording) "Stop & Save" else "Record")
        }
    }
}

@Composable
fun KeyframeSheet(
    overlay: OverlayItem?,
    playheadUs: Long,
    onAddKeyframe: (String, OverlayKeyframe) -> Unit,
    onRemoveKeyframe: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        SheetHeader("Keyframes", onDismiss)
        Spacer(Modifier.height(12.dp))
        if (overlay == null) {
            Text("Select an overlay or PiP layer first.", color = TextSecondary, fontSize = 12.sp)
            return@Column
        }
        Text("Keyframes for: ${overlay.text.ifEmpty { "Layer" }}", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        overlay.keyframes.forEach { kf ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${kf.timeOffsetUs/1000}ms  pos(${String.format("%.2f", kf.normalizedX)},${String.format("%.2f", kf.normalizedY)})  scale ${String.format("%.2f", kf.scale)}",
                    color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemoveKeyframe(overlay.id, kf.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = AccentPrimary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Add keyframe at playhead", onClick = {
            val offset = (playheadUs - overlay.timelineStartUs).coerceAtLeast(0L)
            onAddKeyframe(overlay.id, OverlayKeyframe(timeOffsetUs = offset, normalizedX = overlay.normalizedX, normalizedY = overlay.normalizedY, scale = overlay.scale, rotation = overlay.rotation))
        }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun PiPPanel(
    playheadUs: Long = 0L,
    onAdd: (PiPTrackItem) -> Unit,
    onDismiss: () -> Unit
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val item = PiPTrackItem(
                uri = uri.toString(),
                timelineStartUs = playheadUs,
                timelineEndUs = playheadUs + 5_000_000L,
                scale = 0.35f,
                normalizedX = 0.75f,
                normalizedY = 0.25f
            )
            onAdd(item)
            onDismiss()
        }
    }

    Column {
        SheetHeader("PiP Layer", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Add a video or image as a Picture-in-Picture overlay layer. Drag to position, pinch to scale, two-finger rotate on the preview canvas.", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Pick Media", onClick = { launcher.launch("video/*") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun EffectsSheet(
    currentEffectId: String?,
    currentIntensity: Float,
    onApply: (String, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentEffectId ?: "none") }
    var intensity by remember { mutableStateOf(currentIntensity) }
    Column {
        SheetHeader("Effects", onDismiss)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(com.clipcut.core.effects.EffectRegistry.all) { e ->
                EditorFilterChip(e.displayName, selected == e.id) { selected = e.id }
            }
        }
        SheetSlider("Intensity", intensity, 0f..1f) { intensity = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(selected, intensity) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TransitionsSheet(
    current: TransitionType,
    durationUs: Long,
    onApply: (TransitionType, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var sel by remember { mutableStateOf(current) }
    var dur by remember { mutableStateOf(durationUs.toFloat()) }
    Column {
        SheetHeader("Transitions", onDismiss)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TransitionType.values().toList()) { t ->
                EditorFilterChip(t.displayName, sel == t) { sel = t }
            }
        }
        SheetSlider("Duration (s)", dur / 1_000_000f, 0.1f..3f) { dur = it * 1_000_000f }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(sel, dur.toLong()) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun AudioDuckingSheet(enabled: Boolean, level: Float, onApply: (Boolean, Float) -> Unit, onDismiss: () -> Unit) {
    var en by remember { mutableStateOf(enabled) }
    var lvl by remember { mutableStateOf(level) }
    Column {
        SheetHeader("Audio Ducking", onDismiss)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = en, onCheckedChange = { en = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentPrimary, checkedTrackColor = AccentPrimary.copy(alpha = 0.4f)))
            Spacer(Modifier.width(8.dp))
            Text("Auto-duck background music on voice", color = Color.White, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        SheetSlider("Duck Level", lvl, 0f..1f) { lvl = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(en, lvl) }, modifier = Modifier.fillMaxWidth())
    }
}
