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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    audioTracks: List<AudioTrackItem> = emptyList(),
    hasMusicTrack: Boolean = audioTracks.isNotEmpty(),
    onVolumesChanged: (Float, Float) -> Unit,
    onPickMusicClick: () -> Unit,
    onDeleteTrack: (String) -> Unit = {},
    onTrimTrack: (String, Long, Long) -> Unit = { _, _, _ -> },
    onVoiceoverClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Audio Controls", onDismiss)
        Spacer(Modifier.height(12.dp))
        SheetSlider("Original Video Volume", originalVideoVolume, 0f..2f) { onVolumesChanged(it, backgroundMusicVolume) }
        SheetSlider("Background Music Volume", backgroundMusicVolume, 0f..2f) { onVolumesChanged(originalVideoVolume, it) }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton(
                text = "Add Music",
                onClick = onPickMusicClick,
                icon = Icons.Filled.MusicNote,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Record Voiceover",
                onClick = onVoiceoverClick,
                icon = Icons.Filled.Mic,
                modifier = Modifier.weight(1f)
            )
        }

        if (audioTracks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Loaded Audio Tracks (${audioTracks.size})", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            audioTracks.forEach { track ->
                val durSec = String.format("%.1f", track.effectiveDurationUs / 1_000_000f)
                val icon = if (track.isVoiceover || track.name.contains("voiceover", ignoreCase = true)) Icons.Filled.Mic else Icons.Filled.MusicNote
                val maxDurUs = if (track.sourceDurationUs > 0) track.sourceDurationUs else (track.trimEndUs.coerceAtLeast(5_000_000L))
                val maxDurSec = (maxDurUs / 1_000_000f).coerceAtLeast(1f)
                val startSec = (track.trimStartUs / 1_000_000f).coerceIn(0f, maxDurSec)
                val endSec = (track.trimEndUs / 1_000_000f).coerceIn(startSec + 0.1f, maxDurSec)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.name.ifEmpty { "Audio Track" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${durSec}s • ${(track.volume * 100).toInt()}% vol", color = TextSecondary, fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { onDeleteTrack(track.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Track", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Trim Audio Track (Sliders & Digital Input)", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Start ${String.format("%.1f", startSec)}s", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(68.dp))
                        Slider(
                            value = startSec,
                            onValueChange = { newStartSec ->
                                val newStartUs = (newStartSec * 1_000_000L).toLong()
                                val minEndUs = newStartUs + 500_000L
                                val endUs = if (track.trimEndUs < minEndUs) minEndUs else track.trimEndUs
                                onTrimTrack(track.id, newStartUs, endUs)
                            },
                            valueRange = 0f..maxDurSec,
                            colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary),
                            modifier = Modifier.weight(1f).height(32.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("End ${String.format("%.1f", endSec)}s", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(68.dp))
                        Slider(
                            value = endSec,
                            onValueChange = { newEndSec ->
                                val newEndUs = (newEndSec * 1_000_000L).toLong()
                                val maxStartUs = (newEndUs - 500_000L).coerceAtLeast(0L)
                                val startUs = if (track.trimStartUs > maxStartUs) maxStartUs else track.trimStartUs
                                onTrimTrack(track.id, startUs, newEndUs)
                            },
                            valueRange = 0f..maxDurSec,
                            colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary),
                            modifier = Modifier.weight(1f).height(32.dp)
                        )
                    }

                    // Digital input boxes: user can type exact numbers e.g. 1s, 2.5s, 10s directly!
                    Spacer(Modifier.height(6.dp))
                    var startInputText by remember(track.id, track.trimStartUs) {
                        mutableStateOf(String.format(java.util.Locale.US, "%.1f", startSec))
                    }
                    var endInputText by remember(track.id, track.trimEndUs) {
                        mutableStateOf(String.format(java.util.Locale.US, "%.1f", endSec))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = startInputText,
                            onValueChange = { str ->
                                startInputText = str
                                val num = str.toDoubleOrNull()
                                if (num != null && num >= 0.0) {
                                    val newStartUs = (num * 1_000_000L).toLong()
                                    val minEndUs = newStartUs + 500_000L
                                    val endUs = if (track.trimEndUs < minEndUs) minEndUs else track.trimEndUs
                                    onTrimTrack(track.id, newStartUs, endUs)
                                }
                            },
                            label = { Text("Start (s)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPrimary,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = endInputText,
                            onValueChange = { str ->
                                endInputText = str
                                val num = str.toDoubleOrNull()
                                if (num != null && num > 0.0) {
                                    val newEndUs = (num * 1_000_000L).toLong()
                                    val maxStartUs = (newEndUs - 500_000L).coerceAtLeast(0L)
                                    val startUs = if (track.trimStartUs > maxStartUs) maxStartUs else track.trimStartUs
                                    onTrimTrack(track.id, startUs, newEndUs)
                                }
                            },
                            label = { Text("End (s)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPrimary,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    // Quick duration presets: 1s, 5s, 10s, 15s, 30s
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 5, 10, 15, 30).forEach { sec ->
                            OutlinedButton(
                                onClick = {
                                    val startUs = 0L
                                    val endUs = (sec * 1_000_000L).coerceAtMost(maxDurUs)
                                    onTrimTrack(track.id, startUs, endUs)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Text("${sec}s", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
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
        Text("Select how this video or layer blends with background footage", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(BlendMode.values().toList()) { mode ->
                val isSelected = current == mode
                val borderColor = if (isSelected) AccentPrimary else HairlineBorder
                val cardBg = if (isSelected) AccentPrimary.copy(alpha = 0.18f) else Surface3

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg)
                        .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { current = mode }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Visual icon/mini diagram showing two overlapping circles illustrating the blend
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .offset(x = (-6).dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE94560).copy(alpha = 0.8f))
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .offset(x = 6.dp)
                                .clip(CircleShape)
                                .background(
                                    when (mode) {
                                        BlendMode.SCREEN, BlendMode.LIGHTEN -> Color(0xFF00F5D4).copy(alpha = 0.85f)
                                        BlendMode.MULTIPLY, BlendMode.DARKEN -> Color(0xFF7928CA).copy(alpha = 0.9f)
                                        BlendMode.OVERLAY, BlendMode.HARD_LIGHT -> Color(0xFFFFD166).copy(alpha = 0.85f)
                                        BlendMode.DIFFERENCE, BlendMode.EXCLUSION -> Color(0xFF4EA8DE).copy(alpha = 0.85f)
                                        else -> Color(0xFF4361EE).copy(alpha = 0.75f)
                                    }
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        mode.displayName,
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
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
        Text("Key Color Backdrop (Remove Background)", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                Triple("Green Screen", floatArrayOf(0f, 1f, 0f), Color(0xFF00E676)),
                Triple("Blue Screen", floatArrayOf(0f, 0f, 1f), Color(0xFF2979FF)),
                Triple("Red Backdrop", floatArrayOf(1f, 0f, 0f), Color(0xFFFF1744)),
                Triple("Custom Key", floatArrayOf(0f, 0.8f, 0.8f), AccentPrimary)
            ).forEach { (label, c, previewColor) ->
                val isSelected = colorPick.contentEquals(c)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentPrimary.copy(alpha = 0.15f) else Surface3)
                        .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { colorPick = c }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
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
        Text("Mask Shape", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MaskShape.values().forEach { s ->
                val isSelected = shape == s
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentPrimary.copy(alpha = 0.15f) else Surface3)
                        .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { shape = s }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (s) {
                            MaskShape.NONE -> Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (isSelected) AccentPrimary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            MaskShape.RECTANGLE -> Box(
                                Modifier.size(24.dp, 16.dp).border(1.5.dp, if (isSelected) AccentPrimary else Color.White, RoundedCornerShape(2.dp))
                            )
                            MaskShape.OVAL -> Box(
                                Modifier.size(24.dp, 16.dp).border(1.5.dp, if (isSelected) AccentPrimary else Color.White, CircleShape)
                            )
                            MaskShape.RADIAL -> Box(
                                Modifier.size(22.dp).border(1.5.dp, if (isSelected) AccentPrimary else Color.White, CircleShape)
                            )
                            MaskShape.LINEAR -> Box(
                                Modifier.width(26.dp).height(2.dp).background(if (isSelected) AccentPrimary else Color.White)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.displayName,
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        
        Spacer(Modifier.height(10.dp))
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
        SheetHeader("Cinematic 3D LUT", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Apply Hollywood cinematic color grading lookup tables", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        val realLuts = listOf(
            Triple("Cinema 01", "table_CINEMA01.png", Pair(Color(0xFF0077B6), Color(0xFFF77F00))),
            Triple("Cinema 02", "table_CINEMA02.png", Pair(Color(0xFF03045E), Color(0xFF00B4D8))),
            Triple("Cinema 03", "table_CINEMA03.png", Pair(Color(0xFF1F2421), Color(0xFF99D98C))),
            Triple("Kodak Film", "FILTER_FILM_01.png", Pair(Color(0xFFE76F51), Color(0xFFF4A261))),
            Triple("Fuji Film", "FILTER_FILM_02.png", Pair(Color(0xFF2A9D8F), Color(0xFFE9C46A))),
            Triple("Retro Film", "FILTER_FILM_03.png", Pair(Color(0xFF6B705C), Color(0xFFDDB892))),
            Triple("Golden Hour", "FILTER_GOLD.png", Pair(Color(0xFFFFB703), Color(0xFFFB8500))),
            Triple("Vintage 90s", "FILTER_VINTAGE.png", Pair(Color(0xFFCB997E), Color(0xFFEDDCD2))),
            Triple("Lomo Pop", "FILTER_LOMO.png", Pair(Color(0xFFD62828), Color(0xFF003049))),
            Triple("Pastel Glow", "FILTER_PINK.png", Pair(Color(0xFFFFB4A2), Color(0xFFE5989B))),
            Triple("Warm Sunset", "FILTER_WARM.png", Pair(Color(0xFFF77F00), Color(0xFFFCBF49))),
            Triple("Antique", "FILTER_ANTIQUE.png", Pair(Color(0xFF8D6E63), Color(0xFFD7CCC8))),
            Triple("Food Fresh", "FD1.png", Pair(Color(0xFFE63946), Color(0xFFA8DADC))),
            Triple("Food Crispy", "FD2.png", Pair(Color(0xFFFFB703), Color(0xFF2A9D8F)))
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            items(realLuts.size) { idx ->
                val (title, fileName, colors) = realLuts[idx]
                val lutAssetPath = "asset://luts/$fileName"
                val isSelected = currentLut?.uri == lutAssetPath || currentLut?.name == title
                Column(
                    modifier = Modifier
                        .width(88.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentPrimary.copy(alpha = 0.18f) else Surface3)
                        .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onApply(LutConfig(name = title, uri = lutAssetPath, intensity = 0.85f)) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(colors.first, colors.second)
                                )
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        title,
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Clear LUT", onClick = { onApply(null) }, modifier = Modifier.fillMaxWidth())
    }
}

// ── FilterSheet with Picture/Visual Previews ─────────────────────────────
@Composable
fun FilterSheet(
    initialConfig: FilterConfig,
    onApplyFilter: (FilterConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var cfg by remember { mutableStateOf(initialConfig) }

    // Color swatches for each filter type to show visual look of the filter
    val filterVisuals = mapOf(
        FilterType.NONE to Triple("Original", Color(0xFF3A86FF), Color(0xFF8338EC)),
        FilterType.BRIGHTNESS to Triple("Bright", Color(0xFFFFE49F), Color(0xFFFF9E00)),
        FilterType.CONTRAST to Triple("Contrast", Color(0xFF212529), Color(0xFFE9ECEF)),
        FilterType.SATURATION to Triple("Vibrant", Color(0xFFFF007F), Color(0xFF00F5D4)),
        FilterType.TEMPERATURE to Triple("Warm Gold", Color(0xFFFB5607), Color(0xFFFFBE0B)),
        FilterType.VIGNETTE to Triple("Vignette", Color(0xFF14213D), Color(0xFF000000)),
        FilterType.GRAYSCALE to Triple("B&W Film", Color(0xFFF8F9FA), Color(0xFF212529)),
        FilterType.SEPIA to Triple("Sepia Retro", Color(0xFFDDA15E), Color(0xFF606C38))
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Filter", onDismiss)
        Spacer(Modifier.height(10.dp))
        Text("Color Profiles & Moods", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        // Visual picture preview tiles for every filter
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FilterType.values().toList()) { ft ->
                val isSelected = cfg.filterType == ft
                val visual = filterVisuals[ft] ?: Triple(ft.displayName, Color(0xFF4361EE), Color(0xFF3F37C9))

                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccentPrimary.copy(alpha = 0.18f) else Surface2)
                        .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            cfg = cfg.copy(filterType = ft)
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Visual picture thumbnail simulating footage under this filter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(visual.second, visual.third)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Film overlay vignette / symbol inside preview
                        Icon(
                            imageVector = when (ft) {
                                FilterType.NONE -> Icons.Filled.Image
                                FilterType.BRIGHTNESS -> Icons.Filled.WbSunny
                                FilterType.CONTRAST -> Icons.Filled.Contrast
                                FilterType.SATURATION -> Icons.Filled.Palette
                                FilterType.TEMPERATURE -> Icons.Filled.Thermostat
                                FilterType.VIGNETTE -> Icons.Filled.Lens
                                FilterType.GRAYSCALE -> Icons.Filled.FilterBAndW
                                FilterType.SEPIA -> Icons.Filled.CameraRoll
                            },
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = ft.displayName,
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("3D LUT Cinematic Presets", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf(
                "Cinematic" to FilterConfig(brightness = 0f, contrast = 1.25f, saturation = 1.2f, temperature = -0.15f, vignette = 0.35f, tint = 0.05f),
                "Vintage Film" to FilterConfig(brightness = 0.05f, contrast = 0.95f, saturation = 0.85f, temperature = 0.25f, vignette = 0.45f, tint = 0.1f),
                "Teal & Orange" to FilterConfig(brightness = 0f, contrast = 1.35f, saturation = 1.3f, temperature = -0.2f, vignette = 0.25f, tint = 0.08f),
                "Warm Sunset" to FilterConfig(brightness = 0.05f, contrast = 1.1f, saturation = 1.25f, temperature = 0.4f, vignette = 0.2f, tint = -0.05f),
                "Noir Drama" to FilterConfig(filterType = FilterType.GRAYSCALE, brightness = 0f, contrast = 1.45f, saturation = 0f, temperature = 0f, vignette = 0.55f),
                "Clean Pop" to FilterConfig(brightness = 0.08f, contrast = 1.15f, saturation = 1.2f, temperature = 0f, vignette = 0f, tint = 0f)
            )
            items(presets) { (pName, pCfg) ->
                EditorFilterChip(pName, cfg.contrast == pCfg.contrast && cfg.vignette == pCfg.vignette && cfg.temperature == pCfg.temperature) {
                    cfg = pCfg
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Fine Adjustments", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        SheetSlider("Brightness", cfg.brightness, -0.5f..0.5f) { cfg = cfg.copy(brightness = it) }
        SheetSlider("Contrast", cfg.contrast, 0.5f..2f) { cfg = cfg.copy(contrast = it) }
        SheetSlider("Saturation", cfg.saturation, 0f..2f) { cfg = cfg.copy(saturation = it) }
        SheetSlider("Temperature", cfg.temperature, -0.5f..0.5f) { cfg = cfg.copy(temperature = it) }
        SheetSlider("Vignette", cfg.vignette, 0f..1f) { cfg = cfg.copy(vignette = it) }
        SheetSlider("Tint", cfg.tint, -0.5f..0.5f) { cfg = cfg.copy(tint = it) }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApplyFilter(cfg) }, modifier = Modifier.fillMaxWidth())
    }
}

// ── SpeedRampDialog (VN 2D Velocity Speed Ramping) ──────────────────────
@Composable
fun SpeedRampDialog(
    initialRamp: SpeedRamp,
    clipDurationUs: Long,
    onApply: (SpeedRamp) -> Unit,
    onDismiss: () -> Unit
) {
    var ramp by remember { mutableStateOf(initialRamp) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("2D Velocity Speed Ramp (VN)", onDismiss)
        Spacer(Modifier.height(8.dp))
        Text(
            "Smooth velocity curve speed ramping with VN curve presets (Bullet Time, Hero, Montage).",
            color = TextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        // VN Curve Speed Preset Chips
        Text("VN Curve Presets", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(com.clipcut.core.model.VnCurveSpeedPresets.presets.map { it.first }) { name ->
                val isSel = selectedPreset == name
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) AccentPrimary else Surface2)
                        .border(1.dp, if (isSel) AccentPrimary else HairlineBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            selectedPreset = name
                            com.clipcut.core.model.VnCurveSpeedPresets.buildRamp(name, clipDurationUs)?.let {
                                ramp = it
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        name,
                        color = if (isSel) DarkBackground else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 2D Velocity Curve Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
                .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f

                // Reference line (1.0x baseline)
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1.dp.toPx()
                )

                val dur = clipDurationUs.coerceAtLeast(1_000_000L).toFloat()
                val kfs = ramp.keyframes.sortedBy { it.timeOffsetUs }
                if (kfs.size >= 2) {
                    val path = Path()
                    kfs.forEachIndexed { idx, kf ->
                        val px = (kf.timeOffsetUs.toFloat() / dur).coerceIn(0f, 1f) * w
                        // map speed 0.1x to 4.0x: 1.0x is at midY
                        val py = (midY - (kf.speed - 1f) * (h / 3f)).coerceIn(8f, h - 8f)
                        if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(
                        path = path,
                        color = ElectricCyan,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    kfs.forEach { kf ->
                        val px = (kf.timeOffsetUs.toFloat() / dur).coerceIn(0f, 1f) * w
                        val py = (midY - (kf.speed - 1f) * (h / 3f)).coerceIn(8f, h - 8f)
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(px, py))
                        drawCircle(color = ElectricCyan, radius = 2.dp.toPx(), center = Offset(px, py))
                    }
                }
            }
            Text("1.0x Normal", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterStart))
            Text("Fast", color = ElectricCyan, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd))
            Text("Slow", color = ProGold, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }

        Spacer(Modifier.height(12.dp))
        Text("Keyframe Points (${ramp.keyframes.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        ramp.keyframes.forEachIndexed { i, kf ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${i+1}  ${kf.timeOffsetUs/1000}ms  ×${String.format("%.2f", kf.speed)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { ramp = ramp.copy(keyframes = ramp.keyframes.filterNot { it.id == kf.id }) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val newKf = SpeedKeyframe(timeOffsetUs = clipDurationUs / 2, speed = 1.5f)
                    ramp = ramp.copy(keyframes = (ramp.keyframes + newKf).sortedBy { it.timeOffsetUs })
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("+ Keyframe", color = Color.White, fontSize = 12.sp)
            }
            PrimaryButton("Apply Speed Ramp", onClick = {
                onApply(ramp)
                onDismiss()
            }, modifier = Modifier.weight(1f))
        }
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
    existingPips: List<PiPTrackItem> = emptyList(),
    onAddOverlay: (OverlayItem) -> Unit,
    onEditOverlay: (String) -> Unit = {},
    onDeleteOverlay: (String) -> Unit = {},
    onDeletePip: (String) -> Unit = {},
    onTrimPip: (String, Long, Long) -> Unit = { _, _, _ -> },
    onSetPipVolume: (String, Float) -> Unit = { _, _ -> },
    onSeekPreview: (Long) -> Unit = {},
    onOpenPiP: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Overlays & Layers", onDismiss)
        Spacer(Modifier.height(12.dp))
        val totalCount = existingOverlays.size + existingPips.size
        Text("Existing Layers ($totalCount)", color = Color.White, fontSize = 12.sp)
        existingOverlays.forEach { o ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Text: ${o.text.ifEmpty { "Caption" }}", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onEditOverlay(o.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Text", tint = Color(0xFF29B6F6), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onDeleteOverlay(o.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AccentPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        existingPips.forEach { p ->
            val curDurSec = ((p.timelineEndUs - p.timelineStartUs).coerceAtLeast(0L) / 1_000_000f)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Surface3, RoundedCornerShape(8.dp)).padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("PiP: ${p.uri.substringAfterLast('/').take(18)} (${String.format(java.util.Locale.US, "%.1f", curDurSec)}s)", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDeletePip(p.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AccentPrimary)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Duration:", color = TextSecondary, fontSize = 11.sp)
                    var pipDurText by remember(p.id, p.timelineEndUs) {
                        mutableStateOf(String.format(java.util.Locale.US, "%.1f", curDurSec))
                    }
                    OutlinedTextField(
                        value = pipDurText,
                        onValueChange = { str ->
                            pipDurText = str
                            val num = str.toDoubleOrNull()
                            if (num != null && num > 0.0) {
                                val newEndUs = p.timelineStartUs + (num * 1_000_000L).toLong()
                                onTrimPip(p.id, p.timelineStartUs, newEndUs)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Text("sec", color = TextSecondary, fontSize = 11.sp)
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newVol = if (p.volume > 0f) 0f else 1f
                            onSetPipVolume(p.id, newVol)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (p.volume > 0f) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = "PiP Volume",
                            tint = if (p.volume > 0f) AccentPrimary else TextTertiary
                        )
                    }
                    Text(
                        text = if (p.volume <= 0f) "Muted" else "${(p.volume * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.width(42.dp)
                    )
                    Slider(
                        value = p.volume,
                        onValueChange = { vol -> onSetPipVolume(p.id, vol) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary),
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                }
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

// ── BlurSubTab ────────────────────────────────────────────────────────
enum class BlurSubTab(val title: String, val icon: String) {
    PAINT("Finger Paint", "🖌️"),
    MANUAL("Manual Tracking", "🎬"),
    TRACKING("Auto Tracking", "🎯"),
    CENSOR("Censor Style", "🌫️"),
    AUTO("Auto AI (Batch)", "⚡"),
    SHAPE("Shape & Size", "📐"),
    TIMELINE("Timeline & Boxes", "⏱️")
}

// ── BlurSheet ──────────────────────────────────────────────────────────
@Composable
fun BlurSheet(
    blurItem: BlurTrackItem,
    totalDurationUs: Long,
    trackingStatus: String?,
    currentPlayheadUs: Long = 0L,
    isPlaying: Boolean = false,
    allBlurItems: List<BlurTrackItem> = emptyList(),
    selectedBlurId: String? = null,
    onSelectBlur: ((String) -> Unit)? = null,
    onAddNewBlurTrack: (() -> Unit)? = null,
    onUpdate: (BlurTrackItem) -> Unit,
    onStartTracking: (com.clipcut.core.feature.TrackingMode) -> Unit,
    onAutoBlurAllFaces: ((BlurType, BlurShape, Float, Float, String?) -> Unit)? = null,
    onAutoBlackoutAllPeople: ((BlurType, BlurShape, Float, Float, String?) -> Unit)? = null,
    onPickCustomImage: (() -> Unit)? = null,
    onClearTracking: () -> Unit,
    onAddKeyframeAtPlayhead: (() -> Unit)? = null,
    onRemoveKeyframeAtPlayhead: (() -> Unit)? = null,
    onStepFrames: ((Int) -> Unit)? = null,
    onSeekToPrevKeyframe: (() -> Unit)? = null,
    onSeekToNextKeyframe: (() -> Unit)? = null,
    onSeekToUs: ((Long) -> Unit)? = null,
    onTogglePlayPause: (() -> Unit)? = null,
    onNudge: ((dx: Float, dy: Float) -> Unit)? = null,
    onNudgeSize: ((dw: Float, dh: Float) -> Unit)? = null,
    isPainting: Boolean = false,
    onTogglePainting: ((Boolean) -> Unit)? = null,
    paintBrushWidth: Float = 0.06f,
    onBrushWidthChange: ((Float) -> Unit)? = null,
    paintIsBlackout: Boolean = true,
    onPaintIsBlackoutChange: ((Boolean) -> Unit)? = null,
    onUndoPaintStroke: (() -> Unit)? = null,
    onClearPaintStrokes: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSubTab by remember { mutableStateOf(BlurSubTab.PAINT) }
    var selectedTrackingMode by remember { mutableStateOf(com.clipcut.core.feature.TrackingMode.INSHOT_SOT) }

    LaunchedEffect(selectedSubTab) {
        onTogglePainting?.invoke(selectedSubTab == BlurSubTab.PAINT)
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Censor, Blur & Tracking Studio", onDismiss)
        Spacer(Modifier.height(8.dp))

        // Tracking status notification banner if active
        if (trackingStatus != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, AccentPrimary, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    color = AccentPrimary,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    trackingStatus,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // Sub-tabs row inside Blur tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BlurSubTab.values().forEach { tab ->
                val isSelected = selectedSubTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) AccentPrimary else Surface3)
                        .border(1.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(20.dp))
                        .clickable { selectedSubTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${tab.icon} ${tab.title}",
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        when (selectedSubTab) {
            BlurSubTab.PAINT -> {
                // TAB: FINGER-PAINT CENSOR & BLACKOUT
                Text("Finger-Paint Censor & Blackout", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Touch and drag directly over the video player above to paint censor strokes with your finger.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                // Paint mode toggle: Solid Blackout vs Frosted Blur
                Text("Censor Mode", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (paintIsBlackout) AccentPrimary else Surface3)
                            .clickable { onPaintIsBlackoutChange?.invoke(true) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⬛ Solid Blackout",
                            color = if (paintIsBlackout) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!paintIsBlackout) AccentPrimary else Surface3)
                            .clickable { onPaintIsBlackoutChange?.invoke(false) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🌫️ Heavy Blur",
                            color = if (!paintIsBlackout) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Brush Thickness
                Text(
                    "Brush Thickness: ${(paintBrushWidth * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = paintBrushWidth,
                    onValueChange = { onBrushWidthChange?.invoke(it) },
                    valueRange = 0.02f..0.18f,
                    colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Fine (8px)" to 0.03f, "Medium (16px)" to 0.06f, "Thick (28px)" to 0.10f, "Huge (48px)" to 0.16f).forEach { (lbl, sz) ->
                        EditorFilterChip(lbl, Math.abs(paintBrushWidth - sz) < 0.015f) {
                            onBrushWidthChange?.invoke(sz)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Stroke count and actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${blurItem.paintStrokes.size} strokes painted",
                        color = if (blurItem.paintStrokes.isNotEmpty()) AccentPrimary else TextTertiary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = "↩ Undo Stroke",
                            onClick = { onUndoPaintStroke?.invoke() },
                            enabled = blurItem.paintStrokes.isNotEmpty()
                        )
                        SecondaryButton(
                            text = "Clear All",
                            onClick = { onClearPaintStrokes?.invoke() },
                            enabled = blurItem.paintStrokes.isNotEmpty()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentPrimary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .border(1.dp, AccentPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "💡 Tip: Drag your finger across the video to censor faces, license plates, documents, or sensitive objects effortlessly.",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
            BlurSubTab.MANUAL -> {
                // TAB 0: MANUAL FRAME-BY-FRAME TRACKING & KEYFRAMING
                Text("Frame-by-Frame Manual Tracking", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Step through frames and reposition the blur/blackout box. Keyframes record automatically on movement and smoothly interpolate.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                val playheadSec = currentPlayheadUs / 1_000_000f
                val frameIndex = (currentPlayheadUs / 33_333L)
                val currentOffsetUs = (currentPlayheadUs - blurItem.timelineStartUs).coerceIn(0L, blurItem.durationUs)
                val hasKeyframeNear = blurItem.keyframes.any { kotlin.math.abs(it.timeOffsetUs - currentOffsetUs) < 35_000L }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface2)
                        .border(1.dp, if (hasKeyframeNear) AccentPrimary else HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    String.format(java.util.Locale.US, "Playhead: %.2fs  (Frame #%d)", playheadSec, frameIndex),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (hasKeyframeNear) "◆ Keyframe Active on this Frame" else "No keyframe at this frame (interpolating)",
                                    color = if (hasKeyframeNear) AccentPrimary else TextTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = if (hasKeyframeNear) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Text(
                                "${blurItem.keyframes.size} Keyframes",
                                color = if (blurItem.keyframes.isNotEmpty()) AccentPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Stepper Bar: -5f, -1f, Play/Pause, +1f, +5f
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onStepFrames?.invoke(-5) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("-5f", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onStepFrames?.invoke(-1) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("◀ -1f", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onTogglePlayPause?.invoke() },
                                modifier = Modifier.weight(1.2f).height(38.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary, contentColor = Color.Black)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(if (isPlaying) "Pause" else "Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onStepFrames?.invoke(1) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("+1f ▶", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onStepFrames?.invoke(5) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("+5f", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Keyframe Actions: Add KF, Delete KF, Prev KF, Next KF
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (hasKeyframeNear) onRemoveKeyframeAtPlayhead?.invoke()
                                    else onAddKeyframeAtPlayhead?.invoke()
                                },
                                modifier = Modifier.weight(1.3f).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasKeyframeNear) Color(0xFFE53935) else AccentPrimary,
                                    contentColor = if (hasKeyframeNear) Color.White else Color.Black
                                )
                            ) {
                                Text(
                                    if (hasKeyframeNear) "◆ Delete Keyframe" else "◆+ Add Keyframe",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { onSeekToPrevKeyframe?.invoke() },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp),
                                enabled = blurItem.keyframes.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("⏮ Prev", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { onSeekToNextKeyframe?.invoke() },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp),
                                enabled = blurItem.keyframes.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Next ⏭", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Precision Nudge Controls (D-Pad + Size)
                Text("Precision Nudge Controls", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Tap arrows to fine-tune blur box placement by 1.5% without blocking your view with your fingers.", color = TextTertiary, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // D-Pad for position nudging
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        IconButton(
                            onClick = { onNudge?.invoke(0f, -0.015f) },
                            modifier = Modifier.size(36.dp).background(Surface3, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Nudge Up", tint = Color.White)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { onNudge?.invoke(-0.015f, 0f) },
                                modifier = Modifier.size(36.dp).background(Surface3, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Nudge Left", tint = Color.White)
                            }
                            IconButton(
                                onClick = { onNudge?.invoke(0f, 0.015f) },
                                modifier = Modifier.size(36.dp).background(Surface3, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nudge Down", tint = Color.White)
                            }
                            IconButton(
                                onClick = { onNudge?.invoke(0.015f, 0f) },
                                modifier = Modifier.size(36.dp).background(Surface3, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Nudge Right", tint = Color.White)
                            }
                        }
                    }

                    // Size nudging
                    Column(
                        modifier = Modifier.padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNudgeSize?.invoke(0.03f, 0.03f) },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface3, contentColor = Color.White),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("➕ Grow Box", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { onNudgeSize?.invoke(-0.03f, -0.03f) },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface3, contentColor = Color.White),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("➖ Shrink Box", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Keyframe List Strip (if keyframes exist)
                if (blurItem.keyframes.isNotEmpty()) {
                    Text("Recorded Keyframe Strip (Tap to Jump)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        blurItem.keyframes.forEachIndexed { idx, kf ->
                            val kfSec = (blurItem.timelineStartUs + kf.timeOffsetUs) / 1_000_000f
                            val isAtKf = kotlin.math.abs(currentOffsetUs - kf.timeOffsetUs) < 35_000L
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAtKf) AccentPrimary else Surface3)
                                    .border(1.dp, if (isAtKf) AccentPrimary else HairlineBorder, RoundedCornerShape(8.dp))
                                    .clickable { onSeekToUs?.invoke(blurItem.timelineStartUs + kf.timeOffsetUs) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    String.format(java.util.Locale.US, "#%d: %.2fs", idx + 1, kfSec),
                                    color = if (isAtKf) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isAtKf) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onClearTracking,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                    ) {
                        Text("Clear All Keyframes (${blurItem.keyframes.size})", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Step-by-step instructions card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface3.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("💡 Frame-by-Frame Pro Workflow:", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "1. Position the box on the target at the start.\n" +
                            "2. Tap '+1f' or '+5f' to advance the video.\n" +
                            "3. Drag the box or tap the D-pad arrows to follow target.\n" +
                            "4. ClipCut automatically creates keyframes and smoothly interpolates frames between them!",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            BlurSubTab.TRACKING -> {
                // TAB 1: SMART MOTION TRACKING ENGINES
                Text("Select On-Device Tracking Engine", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose an algorithm extracted from top benchmark editors. All run 100% locally on-device.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))

                if (blurItem.keyframes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface3, RoundedCornerShape(8.dp))
                            .border(1.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚡ Active Tracking (${blurItem.keyframes.size} keyframes)",
                            color = AccentPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearTracking) {
                            Text("Clear Tracking", color = Color(0xFFE53935), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Tracking Engine Selector Cards
                com.clipcut.core.feature.TrackingMode.values().forEach { mode ->
                    val isCurrent = selectedTrackingMode == mode
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) AccentPrimary.copy(alpha = 0.15f) else Surface2)
                            .border(1.5.dp, if (isCurrent) AccentPrimary else HairlineBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedTrackingMode = mode }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    mode.label,
                                    color = if (isCurrent) AccentPrimary else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Text("SELECTED ✓", color = AccentPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                mode.description,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                PrimaryButton(
                    "🚀 Track Target with ${selectedTrackingMode.engineName}",
                    onClick = { onStartTracking(selectedTrackingMode) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "💡 Tip: Adjust your box on the video preview at the current frame, pick an engine above, and tap Track.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }

            BlurSubTab.CENSOR -> {
                // TAB 2: CENSOR STYLE & INTENSITY
                Text("Censor Visual Style", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BlurType.values().forEach { type ->
                        EditorFilterChip(
                            text = type.displayName,
                            selected = blurItem.blurType == type
                        ) {
                            onUpdate(blurItem.copy(blurType = type))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Picture / Sticker Picker (when IMAGE mode selected)
                if (blurItem.blurType == BlurType.IMAGE) {
                    Text("Choose Sticker, Emoji or Picture", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))

                    val emojis = listOf(
                        "sunglasses" to "😎",
                        "clown" to "🤡",
                        "mask" to "😷",
                        "cat" to "🐱",
                        "monkey" to "🙈",
                        "censor" to "🚫",
                        "star" to "⭐️",
                        "zipper" to "🤐",
                        "robot" to "🤖",
                        "dog" to "🐶",
                        "heart" to "❤️",
                        "fire" to "🔥"
                    )

                    val currentUri = blurItem.customImageUri ?: "emoji://sunglasses"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojis.forEach { (key, emoji) ->
                            val isSelected = currentUri == "emoji://$key"
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) AccentPrimary.copy(alpha = 0.25f) else Surface3)
                                    .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        onUpdate(blurItem.copy(customImageUri = "emoji://$key"))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { onPickCustomImage?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (currentUri.startsWith("content://") || currentUri.startsWith("file://")) AccentPrimary else HairlineBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (currentUri.startsWith("content://") || currentUri.startsWith("file://")) AccentPrimary.copy(alpha = 0.15f) else Surface2,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (currentUri.startsWith("content://") || currentUri.startsWith("file://")) "Custom Picture Selected ✓ (Tap to Change)" else "➕ Pick Custom Picture / Photo from Gallery",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                }

                // Blur Intensity & Feather Sliders
                if (blurItem.blurType != BlurType.IMAGE) {
                    if (blurItem.blurType != BlurType.BLACKOUT) {
                        SheetSlider(
                            label = if (blurItem.blurType == BlurType.MOSAIC) "Mosaic Block Size (Chunky Retro)" else "Blur Intensity (Heavy Frosted)",
                            value = blurItem.intensity,
                            range = 0.1f..1.0f
                        ) {
                            onUpdate(blurItem.copy(intensity = it))
                        }
                    }

                    SheetSlider(
                        label = "Edge Softness / Feather",
                        value = blurItem.feather,
                        range = 0.0f..0.45f
                    ) {
                        onUpdate(blurItem.copy(feather = it))
                    }
                }
            }

            BlurSubTab.AUTO -> {
                // TAB 3: AUTO AI BATCH DETECTION
                Text("One-Tap Automatic AI Censor", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Automatically detects and applies privacy tracking to everyone in the frame.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                if (onAutoBlackoutAllPeople != null) {
                    PrimaryButton(
                        "🧍 Auto-Blackout All People (Full Body Head-to-Toe)",
                        onClick = {
                            onAutoBlackoutAllPeople(BlurType.BLACKOUT, BlurShape.RECTANGLE, 1.0f, 0.15f, null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (onAutoBlurAllFaces != null) {
                    PrimaryButton(
                        "⚡ Auto-Blackout All Faces (Solid Censor)",
                        onClick = {
                            onAutoBlurAllFaces(BlurType.BLACKOUT, blurItem.shape, 1.0f, 0.15f, null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    SecondaryButton(
                        "🌫️ Auto-Blur All Faces (Full Solid Frosted)",
                        onClick = {
                            onAutoBlurAllFaces(BlurType.GAUSSIAN, blurItem.shape, 1.0f, 0.20f, null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    SecondaryButton(
                        "✨ Auto-Apply ${blurItem.blurType.displayName} to All Faces",
                        onClick = {
                            onAutoBlurAllFaces(blurItem.blurType, blurItem.shape, blurItem.intensity, blurItem.feather, blurItem.customImageUri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            BlurSubTab.SHAPE -> {
                // TAB 4: SHAPE MASK & DIMENSIONS
                Text("Shape Mask Geometry", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BlurShape.values().forEach { shape ->
                        EditorFilterChip(
                            text = shape.displayName,
                            selected = blurItem.shape == shape
                        ) {
                            onUpdate(blurItem.copy(shape = shape))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text("Free-form Dimensions", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Width: ${(blurItem.width * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = blurItem.width,
                            onValueChange = { onUpdate(blurItem.copy(width = it)) },
                            valueRange = 0.05f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Height: ${(blurItem.height * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                        Slider(
                            value = blurItem.height,
                            onValueChange = { onUpdate(blurItem.copy(height = it)) },
                            valueRange = 0.05f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text("Quick Aspect Ratio Presets", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    EditorFilterChip("🧍 Full Person", blurItem.height >= 0.70f) {
                        onUpdate(blurItem.copy(shape = BlurShape.RECTANGLE, width = 0.35f, height = 0.82f))
                    }
                    EditorFilterChip("Face Oval", blurItem.shape == BlurShape.OVAL && blurItem.height < 0.50f) {
                        onUpdate(blurItem.copy(shape = BlurShape.OVAL, width = 0.28f, height = 0.35f))
                    }
                    EditorFilterChip("License Plate", blurItem.width > blurItem.height * 1.5f) {
                        onUpdate(blurItem.copy(shape = BlurShape.RECTANGLE, width = 0.45f, height = 0.16f))
                    }
                    EditorFilterChip("Square 1:1", blurItem.width == blurItem.height && blurItem.height < 0.50f) {
                        onUpdate(blurItem.copy(width = 0.25f, height = 0.25f))
                    }
                }
            }

            BlurSubTab.TIMELINE -> {
                // TAB 5: TIMELINE DURATION & MULTI-BOX MANAGEMENT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Active Boxes (${allBlurItems.size.coerceAtLeast(1)})",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { onAddNewBlurTrack?.invoke() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Add Box", color = AccentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (allBlurItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allBlurItems.forEachIndexed { index, item ->
                            val isSelected = item.id == blurItem.id
                            EditorFilterChip(
                                text = "Box ${index + 1} (${item.blurType.displayName})",
                                selected = isSelected
                            ) {
                                onSelectBlur?.invoke(item.id)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Text("Timing & Duration", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val curStartSec = blurItem.timelineStartUs / 1_000_000f
                val curEndSec = blurItem.timelineEndUs / 1_000_000f
                val durSec = curEndSec - curStartSec
                Text(
                    String.format(java.util.Locale.US, "%.1fs – %.1fs (Total: %.1fs)", curStartSec, curEndSec, durSec),
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        "Entire Video",
                        onClick = {
                            onUpdate(blurItem.copy(timelineStartUs = 0L, timelineEndUs = totalDurationUs.coerceAtLeast(1_000_000L)))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        "+ 3 Seconds",
                        onClick = {
                            val newEnd = (blurItem.timelineStartUs + 3_000_000L).coerceAtMost(totalDurationUs)
                            onUpdate(blurItem.copy(timelineEndUs = newEnd))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                PrimaryButton(
                    "➕ Add Another Blur / Blackout Box",
                    onClick = { onAddNewBlurTrack?.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Add
                )

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Current Blur Box", fontSize = 13.sp)
                }
            }
        }
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
    var currentAspect by remember { mutableStateOf(projectAspectRatio) }
    var bitrateMbps by remember { mutableStateOf(12) }
    var activeSocialPreset by remember { mutableStateOf<String?>("TikTok") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Surface2,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SheetHeader("Export Video", onDismiss)
                Spacer(Modifier.height(14.dp))

                Text("One-Tap Social Presets", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        Triple("TikTok", "📱 TikTok / Reels / Shorts (9:16)", Pair(AspectRatio.R_9_16, 60)),
                        Triple("YouTube", "📺 YouTube (16:9 60fps)", Pair(AspectRatio.R_16_9, 60)),
                        Triple("IG Square", "📸 Instagram Post (1:1)", Pair(AspectRatio.R_1_1, 30)),
                        Triple("IG Story", "🖼️ Instagram Portrait (4:5)", Pair(AspectRatio.R_4_5, 30))
                    )
                    items(presets) { (pKey, pLabel, pConfig) ->
                        EditorFilterChip(pLabel, activeSocialPreset == pKey) {
                            activeSocialPreset = pKey
                            currentAspect = pConfig.first
                            fps = pConfig.second
                            resolution = ExportResolution.RES_1080P
                            bitrateMbps = 15
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Resolution", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    ExportResolution.values().forEach { r ->
                        EditorFilterChip(r.label, resolution == r) {
                            resolution = r
                            activeSocialPreset = null
                        }
                    }
                }

                Text("Bitrate Quality", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    listOf(
                        6 to "Low (6M)",
                        12 to "Standard (12M)",
                        25 to "High (25M)",
                        50 to "Ultra (50M)"
                    ).forEach { (mbps, label) ->
                        EditorFilterChip(label, bitrateMbps == mbps) { bitrateMbps = mbps }
                    }
                }

                Text("Frame Rate", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    listOf(24, 30, 60).forEach { f ->
                        EditorFilterChip("${f}fps", fps == f) { fps = f }
                    }
                }

                Text("Codec", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    ExportCodec.values().forEach { c ->
                        EditorFilterChip(c.label, codec == c) { codec = c }
                    }
                }

                Spacer(Modifier.height(14.dp))
                when (exportState) {
                    is com.clipcut.ui.viewmodel.ExportState.Progress -> {
                        LinearProgressIndicator(progress = { exportState.progress }, modifier = Modifier.fillMaxWidth(), color = AccentPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(exportState.status, color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("Cancel", onClick = onCancelExport, modifier = Modifier.fillMaxWidth())
                    }
                    is com.clipcut.ui.viewmodel.ExportState.Success -> {
                        Text("Saved to: ${exportState.galleryPath}", color = AccentSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                    }
                    is com.clipcut.ui.viewmodel.ExportState.Error -> {
                        Text("Export failed: ${exportState.message}", color = AccentPrimary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("View Logs", onClick = onViewLogsClick, modifier = Modifier.weight(1f))
                            PrimaryButton("Retry", onClick = {
                                val (w, h) = currentAspect.calculateExportDimensions(resolution)
                                onStartExport(
                                    ExportConfig(
                                        resolution = resolution,
                                        customWidth = w,
                                        customHeight = h,
                                        codec = codec,
                                        frameRate = fps,
                                        bitRate = bitrateMbps * 1_000_000
                                    )
                                )
                            }, modifier = Modifier.weight(1f))
                        }
                    }
                    com.clipcut.ui.viewmodel.ExportState.Idle -> {
                        PrimaryButton("Export Video", onClick = {
                            val (w, h) = currentAspect.calculateExportDimensions(resolution)
                            onStartExport(
                                ExportConfig(
                                    resolution = resolution,
                                    customWidth = w,
                                    customHeight = h,
                                    codec = codec,
                                    frameRate = fps,
                                    bitRate = bitrateMbps * 1_000_000
                                )
                            )
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ── Tool sheets (one per tool not covered by API ref) ─────────────────

@Composable
fun SpeedSheet(current: Float, onApply: (Float) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(current) }
    Column {
        SheetHeader("Playback Speed", onDismiss)
        Spacer(Modifier.height(10.dp))
        Text("Standard Presets & Motion Rates", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                0.25f to "Slow Mo",
                0.5f to "Smooth",
                1.0f to "Normal",
                2.0f to "Fast",
                5.0f to "Hyper"
            ).forEach { (speedVal, label) ->
                val isSelected = (s == speedVal)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentPrimary.copy(alpha = 0.18f) else Surface3)
                        .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { s = speedVal }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${speedVal}x",
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetSlider("Custom Speed: ${String.format(java.util.Locale.US, "%.2f", s)}x", s, 0.1f..10f) { s = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply Speed", onClick = { onApply(s) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun VolumeSheet(current: Float, onApply: (Float) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(current) }
    Column {
        SheetHeader("Audio Volume", onDismiss)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface3, RoundedCornerShape(10.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    v == 0f -> Icons.Filled.VolumeMute
                    v < 0.5f -> Icons.Filled.VolumeDown
                    else -> Icons.Filled.VolumeUp
                },
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${(v * 100).toInt()}% Gain", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (v > 1.0f) "Amplified (+${((v - 1f) * 100).toInt()}%)" else "Standard Level", color = TextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        SheetSlider("Gain", v, 0f..2f) { v = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply Volume", onClick = { onApply(v) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RotateSheet(current: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    Column {
        SheetHeader("Rotate Clip", onDismiss)
        Spacer(Modifier.height(10.dp))
        Text("Orient video clockwise or counter-clockwise", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0, 90, 180, 270).forEach { d ->
                val isSelected = (current == d)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentPrimary.copy(alpha = 0.18f) else Surface3)
                        .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onApply(d) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Rotate90DegreesCw,
                            contentDescription = null,
                            tint = if (isSelected) AccentPrimary else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${d}°",
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
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

    val cropPresets = listOf(
        "16:9 Landscape" to CropRect(0f, 0.05f, 1f, 0.95f),
        "9:16 Story/Reels" to CropRect(0.2f, 0f, 0.8f, 1f),
        "1:1 Square" to CropRect(0.125f, 0f, 0.875f, 1f),
        "4:5 Portrait" to CropRect(0.1f, 0f, 0.9f, 1f),
        "Full Frame" to CropRect(0f, 0f, 1f, 1f)
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Crop & Framing", onDismiss)
        Spacer(Modifier.height(10.dp))
        Text("Social Media Framing Presets", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cropPresets) { (label, preset) ->
                EditorFilterChip(label, (l == preset.left && r == preset.right && t == preset.top && b == preset.bottom)) {
                    l = preset.left; r = preset.right; t = preset.top; b = preset.bottom
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SheetSlider("Left Margin", l, 0f..0.45f) { l = it }
        SheetSlider("Top Margin", t, 0f..0.45f) { t = it }
        SheetSlider("Right Boundary", r, 0.55f..1f) { r = it }
        SheetSlider("Bottom Boundary", b, 0.55f..1f) { b = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply Crop", onClick = { onApply(CropRect(l, t, r, b)) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun FlipSheet(flipH: Boolean, flipV: Boolean, onApply: (Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    var h by remember { mutableStateOf(flipH) }
    var v by remember { mutableStateOf(flipV) }
    Column {
        SheetHeader("Mirror / Flip", onDismiss)
        Spacer(Modifier.height(10.dp))
        Text("Invert video horizontal mirror or vertical flip", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (h) AccentPrimary.copy(alpha = 0.18f) else Surface3)
                    .border(1.5.dp, if (h) AccentPrimary else HairlineBorder, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { h = !h }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.FlipCameraAndroid, contentDescription = null, tint = if (h) AccentPrimary else Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("Horizontal Mirror", color = if (h) AccentPrimary else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (h) "Enabled" else "Off", color = TextSecondary, fontSize = 10.sp)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (v) AccentPrimary.copy(alpha = 0.18f) else Surface3)
                    .border(1.5.dp, if (v) AccentPrimary else HairlineBorder, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { v = !v }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.SwapVert, contentDescription = null, tint = if (v) AccentPrimary else Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("Vertical Invert", color = if (v) AccentPrimary else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (v) "Enabled" else "Off", color = TextSecondary, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
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
    initialOverlay: OverlayItem? = null,
    playheadUs: Long = 0L,
    totalDurationUs: Long = 5_000_000L,
    onApply: (OverlayItem) -> Unit,
    onOpenCaptions: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var text by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.text ?: "") }
    var size by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.textSizeSp ?: 28f) }
    var bold by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.textBold ?: true) }
    var selectedColor by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.textColor ?: 0xFFFFFFFFL) }
    var selectedBgColor by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.backgroundColor ?: 0x00000000L) }

    var italic by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.textItalic ?: false) }
    var selectedFont by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.fontFamily ?: FontFamilyOption.DEFAULT) }
    var selectedAnimation by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.animation ?: TextAnimation.NONE) }
    var hasShadow by remember(initialOverlay?.id) { mutableStateOf(initialOverlay?.dropShadow ?: true) }
    var hasStroke by remember(initialOverlay?.id) { mutableStateOf((initialOverlay?.strokeWidthPx ?: 0f) > 0f) }

    val textColors = listOf(
        0xFFFFFFFFL to Color.White,
        0xFFFF2A6DL to Color(0xFFFF2A6D),
        0xFF00F5D4L to Color(0xFF00F5D4),
        0xFFFFD166L to Color(0xFFFFD166),
        0xFF7928CAL to Color(0xFF7928CA),
        0xFF4EA8DEL to Color(0xFF4EA8DE),
        0xFF000000L to Color.Black
    )

    val stylePresets = listOf(
        "Headline" to Pair(36f, 0x00000000L),
        "Caption" to Pair(22f, 0xB3000000L),
        "Subtitle" to Pair(26f, 0x801A1A24L),
        "Neon Tag" to Pair(30f, 0xCCFF2A6DL),
        "Boxed" to Pair(24f, 0xE6000000L)
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader(if (initialOverlay != null) "Edit Text Overlay" else "Text Overlay", onDismiss)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Type text here...", color = TextTertiary) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface3, unfocusedContainerColor = Surface3,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = AccentPrimary
            )
        )

        Spacer(Modifier.height(12.dp))
        Text("Font Family", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FontFamilyOption.values().forEach { font ->
                EditorFilterChip(font.label, selectedFont == font) { selectedFont = font }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Entrance Animation", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TextAnimation.values().toList()) { anim ->
                EditorFilterChip(anim.displayName, selectedAnimation == anim) { selectedAnimation = anim }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Text Style Presets", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stylePresets) { (name, preset) ->
                EditorFilterChip(name, size == preset.first && selectedBgColor == preset.second) {
                    size = preset.first
                    selectedBgColor = preset.second
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Font Color", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            textColors.forEach { (colorVal, composeColor) ->
                val isSelected = (selectedColor == colorVal)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(composeColor)
                        .border(
                            if (isSelected) 2.5.dp else 1.dp,
                            if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.4f),
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedColor = colorVal }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SheetSlider("Font Size: ${size.toInt()}sp", size, 14f..80f) { size = it }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = bold, onCheckedChange = { bold = it }, colors = CheckboxDefaults.colors(checkedColor = AccentPrimary))
                Text("Bold", color = Color.White, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = italic, onCheckedChange = { italic = it }, colors = CheckboxDefaults.colors(checkedColor = AccentPrimary))
                Text("Italic", color = Color.White, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasShadow, onCheckedChange = { hasShadow = it }, colors = CheckboxDefaults.colors(checkedColor = AccentPrimary))
                Text("Shadow", color = Color.White, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasStroke, onCheckedChange = { hasStroke = it }, colors = CheckboxDefaults.colors(checkedColor = AccentPrimary))
                Text("Outline", color = Color.White, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (initialOverlay != null) "Update Text Overlay" else "Add Text to Timeline", onClick = {
            if (text.isNotBlank()) {
                val startUs = initialOverlay?.timelineStartUs ?: playheadUs.coerceAtLeast(0L)
                val endUs = initialOverlay?.timelineEndUs ?: ((startUs + 3_000_000L).let { if (totalDurationUs > startUs) it.coerceAtMost(totalDurationUs) else it }.coerceAtLeast(startUs + 500_000L))
                val targetItem = (initialOverlay ?: OverlayItem()).copy(
                    text = text,
                    textSizeSp = size,
                    textBold = bold,
                    textItalic = italic,
                    fontFamily = selectedFont,
                    textColor = selectedColor,
                    backgroundColor = selectedBgColor,
                    strokeColor = if (hasStroke) 0xFF000000L else 0L,
                    strokeWidthPx = if (hasStroke) 4f else 0f,
                    dropShadow = hasShadow,
                    animation = selectedAnimation,
                    timelineStartUs = startUs,
                    timelineEndUs = endUs
                )
                onApply(targetItem)
                onDismiss()
            }
        }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        SecondaryButton("Auto Captions (Speech to Text)", onClick = onOpenCaptions, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun StickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Column {
        SheetHeader("Stickers & Overlays", onDismiss)
        Spacer(Modifier.height(10.dp))
        val categories = listOf("Trending", "InShot Emojis", "Censor Stamps", "Reels & Social", "Arrows & Pointers", "Vlog & Life", "Retro Y2K", "Aesthetic", "Emoji", "Badges")
        var category by remember { mutableStateOf("Trending") }

        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(category).coerceAtLeast(0),
            containerColor = Surface2,
            contentColor = AccentPrimary,
            edgePadding = 12.dp
        ) {
            categories.forEach { cat ->
                Tab(
                    selected = cat == category,
                    onClick = { category = cat },
                    text = { Text(cat, fontSize = 12.sp, fontWeight = if (cat == category) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // High-engagement, trending 2026 CapCut/TikTok/Reels creator sticker pack
        val glyphs = when (category) {
            "Trending" -> listOf(
                "🔥", "✨", "⚡", "💥", "🎬", "🍿", "💯", "🚀", "👑", "🚨", "💎", "🎯",
                "🎉", "🥂", "🏆", "🌟", "💫", "🦄", "💖", "🌸", "⭐", "🌈", "🦋", "🍄"
            )
            "InShot Emojis" -> listOf(
                "asset://stickers/aniemoji/aniemoji01_01_cover.png",
                "asset://stickers/aniemoji/aniemoji01_02_cover.png",
                "asset://stickers/aniemoji/aniemoji01_03_cover.png",
                "asset://stickers/aniemoji/aniemoji01_04_cover.png",
                "asset://stickers/aniemoji/aniemoji01_05_cover.png",
                "asset://stickers/aniemoji/aniemoji01_06_cover.png",
                "asset://stickers/aniemoji/aniemoji01_07_cover.png",
                "asset://stickers/aniemoji/aniemoji01_08_cover.png",
                "asset://stickers/aniemoji/aniemoji01_09_cover.png",
                "asset://stickers/aniemoji/aniemoji01_10_cover.png",
                "asset://stickers/aniemoji/aniemoji01_11_cover.png",
                "asset://stickers/aniemoji/aniemoji01_12_cover.png"
            )
            "Censor Stamps" -> listOf(
                "⛔ CENSORED", "🔒 TOP SECRET", "⚠️ WARNING", "🔞 18+ ONLY",
                "🛑 CONFIDENTIAL", "🚫 RESTRICTED", "🕵️ CLASSIFIED", "⬛ REDACTED",
                "👀 EYES HERE", "❌ PRIVATE", "‼️ DO NOT ENTER", "⚡ DANGER",
                "🔥 HOT CONTENT", "🚨 POLICE LINE", "🛡️ SECURED", "🤐 NO SPOILERS"
            )
            "Vlog & Life" -> listOf(
                "☕", "🥐", "🥑", "🍕", "🍔", "🍣", "🍦", "🍩", "🍷", "🧃", "🎧", "📷",
                "✈️", "🏖️", "🏙️", "🏕️", "🚗", "🚲", "🛒", "🛍️", "🧴", "💄", "🕶️", "👟"
            )
            "Reels & Social" -> listOf(
                "🔔", "👍", "💬", "❤️", "↗️", "📌", "🔴 REC", "▶️ PLAY", "⏳", "🔗", "⭐ SAVE", "👉 CLICK",
                "👀 LOOK", "🔥 VIRAL", "NEW POST", "SWIPE UP", "LINK IN BIO", "TAP HERE", "POV", "PART 2", "WAIT 4 IT", "GRWM", "OOTD", "VLOG"
            )
            "Retro Y2K" -> listOf(
                "👾", "🕹️", "📼", "📻", "📺", "💿", "💾", "📟", "🔋", "🛸", "🤖", "🔮",
                "ミ☆", "⋆𐙚♡⋆", "✦", "✧", "☾", "☼", "🕷️", "🕸️", "⚡️", "☠️", "🛸", "🎱"
            )
            "Aesthetic" -> listOf(
                "🌷", "🌻", "🌿", "🍀", "🍃", "☁️", "🌙", "🪐", "🎀", "🧸", "🕯️", "💌",
                "🕊️", "🫧", "🪴", "🪞", "🪻", "🌾", "🌊", "🫧", "🎨", "🩰", "🦢", "🐚"
            )
            "Arrows & Pointers" -> listOf(
                "👉", "👈", "👆", "👇", "➔", "➜", "➝", "➞", "➟", "➠", "➡", "➢",
                "➤", "➥", "➦", "➧", "➨", "➩", "➪", "➫", "➬", "➭", "➮", "➯"
            )
            "Emoji" -> listOf(
                "😀", "😍", "😎", "😂", "🤣", "🥹", "😉", "😜", "🥳", "😇", "🤩", "😏",
                "😡", "😱", "🤔", "😴", "🤯", "🫠", "🫣", "🫡", "👏", "🙏", "🤝", "🫶"
            )
            "Badges" -> listOf(
                "✅ VERIFIED", "TOP PICK", "BEST SELLER", "LIMITED", "HOT", "SALE", "MUST WATCH", "TUTORIAL",
                "BEFORE", "AFTER", "FAIL", "WIN", "PRO TIP", "HACK", "100%", "NO CAP", "FACTS", "MOOD", "REAL", "DAILY"
            )
            else -> listOf("✨", "🔥", "❤️")
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(290.dp).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(glyphs) { g ->
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface3)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPick(g) },
                    contentAlignment = Alignment.Center
                ) {
                    if (g.startsWith("asset://")) {
                        val clean = g.removePrefix("asset://")
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val bmp = remember(g) {
                            try {
                                ctx.assets.open(clean).use { stream ->
                                    android.graphics.BitmapFactory.decodeStream(stream)
                                }?.asImageBitmap()
                            } catch (_: Throwable) { null }
                        }
                        if (bmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp,
                                contentDescription = "Sticker",
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            Text("🎭", fontSize = 24.sp)
                        }
                    } else if (g.length > 3) {
                        Text(
                            text = g,
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            maxLines = 1
                        )
                    } else {
                        Text(text = g, fontSize = 26.sp)
                    }
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

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            generator.start(context)
            isTranscribing = true
        }
    }

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
                        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            generator.start(context)
                            isTranscribing = true
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
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

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            recorder.start()
        }
    }

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
                    val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        recorder.start()
                    } else {
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
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
    projectDurationUs: Long = 0L,
    onAdd: (PiPTrackItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val initialSec = if (projectDurationUs > playheadUs) {
        val rem = (projectDurationUs - playheadUs) / 1_000_000f
        minOf(5.0f, rem.coerceAtLeast(0.5f))
    } else 5.0f
    var durationSec by remember { mutableFloatStateOf(initialSec) }
    var durationInputText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", initialSec)) }
    var pipVolume by remember { mutableFloatStateOf(0.0f) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}

            // Persist file into internal storage so both preview thread and export engine have reliable access
            var finalUriString = uri.toString()
            try {
                val pipDir = java.io.File(context.filesDir, "pip_media").apply { mkdirs() }
                val isImage = context.contentResolver.getType(uri)?.startsWith("image/") == true
                val ext = if (isImage) "jpg" else "mp4"
                val destFile = java.io.File(pipDir, "pip_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    finalUriString = android.net.Uri.fromFile(destFile).toString()
                }
            } catch (t: Throwable) {
                com.clipcut.core.log.AppLog.w("PiPPanel", "Failed to cache PiP media locally: ${t.message}")
            }

            val maxAllowedDurUs = if (projectDurationUs > playheadUs) (projectDurationUs - playheadUs) else 300_000_000L
            val durUs = (durationSec * 1_000_000L).toLong().coerceIn(500_000L, maxAllowedDurUs.coerceAtLeast(500_000L))
            val item = PiPTrackItem(
                uri = finalUriString,
                timelineStartUs = playheadUs,
                timelineEndUs = playheadUs + durUs,
                scale = 0.35f,
                normalizedX = 0.75f,
                normalizedY = 0.25f,
                volume = pipVolume
            )
            onAdd(item)
            onDismiss()
        }
    }

    Column {
        SheetHeader("PiP Layer", onDismiss)
        Spacer(Modifier.height(12.dp))
        Text("Add a video or image as a Picture-in-Picture overlay layer. Drag to position, pinch to scale, two-finger rotate on the preview canvas.", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))

        Text("PiP Duration: ${String.format(java.util.Locale.US, "%.1f", durationSec)}s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Slider(
            value = durationSec,
            onValueChange = {
                durationSec = it
                durationInputText = String.format(java.util.Locale.US, "%.1f", it)
            },
            valueRange = 0.5f..60.0f,
            colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary),
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = durationInputText,
                onValueChange = { str ->
                    durationInputText = str
                    val num = str.toDoubleOrNull()
                    if (num != null && num > 0.0) {
                        durationSec = num.toFloat().coerceIn(0.5f, 300.0f)
                    }
                },
                label = { Text("Duration (seconds)", fontSize = 10.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = HairlineBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { pipVolume = if (pipVolume > 0f) 0f else 1f },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (pipVolume > 0f) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = "PiP Audio",
                    tint = if (pipVolume > 0f) AccentPrimary else TextTertiary
                )
            }
            Text(
                text = if (pipVolume <= 0f) "Audio: Muted" else "Audio: ${(pipVolume * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(96.dp)
            )
            Slider(
                value = pipVolume,
                onValueChange = { pipVolume = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary),
                modifier = Modifier.weight(1f).height(32.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        PrimaryButton("Pick Media (Video or Image)", onClick = { launcher.launch(arrayOf("video/*", "image/*")) }, modifier = Modifier.fillMaxWidth())
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

    val effectVisuals = mapOf(
        "glitch" to Pair(Color(0xFFFF0055), Color(0xFF00F5D4)),
        "vhs" to Pair(Color(0xFF7209B7), Color(0xFFF72585)),
        "rgb_split" to Pair(Color(0xFF4361EE), Color(0xFFFF007F)),
        "film_grain" to Pair(Color(0xFF6C757D), Color(0xFF343A40)),
        "light_leak" to Pair(Color(0xFFFF9E00), Color(0xFFFF0055)),
        "bokeh" to Pair(Color(0xFFFFBE0B), Color(0xFFFB5607)),
        "kaleidoscope" to Pair(Color(0xFF8338EC), Color(0xFF3A86FF)),
        "zoom_pulse" to Pair(Color(0xFF06D6A0), Color(0xFF118AB2)),
        "shake" to Pair(Color(0xFFE63946), Color(0xFFF1FAEE)),
        "bloom" to Pair(Color(0xFFFFD166), Color(0xFFEF476F)),
        "neon_glow" to Pair(Color(0xFF00F5D4), Color(0xFF7928CA)),
        "comic" to Pair(Color(0xFFFFD60A), Color(0xFF000814)),
        "oil_paint" to Pair(Color(0xFFE76F51), Color(0xFF2A9D8F)),
        "blur_gaussian" to Pair(Color(0xFF4EA8DE), Color(0xFF5E60CE)),
        "thermal" to Pair(Color(0xFFFF0000), Color(0xFF0000FF)),
        "night_vision" to Pair(Color(0xFF00FF66), Color(0xFF003300))
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Special Effects", onDismiss)
        Spacer(Modifier.height(10.dp))
        Text("Cinematic FX & Distortion Filters (${com.clipcut.core.effects.EffectRegistry.all.size}+)", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(com.clipcut.core.effects.EffectRegistry.all) { e ->
                val isSelected = selected == e.id
                val (c1, c2) = effectVisuals[e.id] ?: Pair(Color(0xFF4361EE), Color(0xFF7209B7))

                Column(
                    modifier = Modifier
                        .width(78.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccentPrimary.copy(alpha = 0.18f) else Surface2)
                        .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selected = e.id }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface3),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            when (e.id) {
                                "glitch" -> {
                                    drawRect(Color(0xFF1A1A24))
                                    drawRect(Color(0xFF00F5D4), Offset(w * 0.15f, h * 0.25f), Size(w * 0.5f, h * 0.16f))
                                    drawRect(Color(0xFFFF0055), Offset(w * 0.35f, h * 0.35f), Size(w * 0.55f, h * 0.16f))
                                    drawRect(Color.White, Offset(w * 0.25f, h * 0.30f), Size(w * 0.45f, h * 0.12f))
                                    drawLine(Color.Cyan, Offset(0f, h * 0.65f), Offset(w * 0.8f, h * 0.65f), strokeWidth = 3f)
                                    drawLine(Color.Magenta, Offset(w * 0.2f, h * 0.72f), Offset(w, h * 0.72f), strokeWidth = 3f)
                                }
                                "vhs" -> {
                                    drawRect(Color(0xFF100A26))
                                    for (i in 0..10) {
                                        val y = (h / 10f) * i
                                        drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f)
                                    }
                                    drawLine(Color(0xFFF72585), Offset(0f, h * 0.45f), Offset(w, h * 0.45f), strokeWidth = 4f)
                                    drawLine(Color(0xFF4CC9F0), Offset(0f, h * 0.48f), Offset(w, h * 0.48f), strokeWidth = 3f)
                                }
                                "rgb_split", "chroma_aberration" -> {
                                    drawRect(Color(0xFF161622))
                                    drawCircle(Color(0xFFFF0055).copy(alpha = 0.75f), radius = w * 0.22f, center = Offset(w * 0.42f, h * 0.5f))
                                    drawCircle(Color(0xFF00F5D4).copy(alpha = 0.75f), radius = w * 0.22f, center = Offset(w * 0.58f, h * 0.5f))
                                    drawCircle(Color.White, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.5f))
                                }
                                "film_grain", "noise_static" -> {
                                    drawRect(Color(0xFF2B2B36))
                                    val r = java.util.Random(42)
                                    for (i in 0..140) {
                                        val rx = r.nextFloat() * w
                                        val ry = r.nextFloat() * h
                                        val c = if (r.nextBoolean()) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                                        drawCircle(c, radius = 1.5f, center = Offset(rx, ry))
                                    }
                                }
                                "light_leak" -> {
                                    drawRect(Color(0xFF181528))
                                    drawCircle(Color(0xFFFF9E00).copy(alpha = 0.85f), radius = w * 0.45f, center = Offset(0f, 0f))
                                    drawCircle(Color(0xFFFF0055).copy(alpha = 0.6f), radius = w * 0.35f, center = Offset(w * 0.2f, h * 0.2f))
                                    drawCircle(Color(0xFFFFD166).copy(alpha = 0.5f), radius = w * 0.25f, center = Offset(0f, 0f))
                                }
                                "bokeh" -> {
                                    drawRect(Color(0xFF141923))
                                    drawCircle(Color(0xFFFFBE0B).copy(alpha = 0.6f), radius = w * 0.2f, center = Offset(w * 0.35f, h * 0.45f))
                                    drawCircle(Color(0xFFFB5607).copy(alpha = 0.5f), radius = w * 0.26f, center = Offset(w * 0.68f, h * 0.35f))
                                    drawCircle(Color(0xFFFF006E).copy(alpha = 0.45f), radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.75f))
                                    drawCircle(Color.White.copy(alpha = 0.7f), radius = w * 0.08f, center = Offset(w * 0.35f, h * 0.45f))
                                }
                                "kaleidoscope" -> {
                                    drawRect(Color(0xFF1E1035))
                                    val cx = w / 2f
                                    val cy = h / 2f
                                    for (i in 0 until 6) {
                                        val angle = (i * 60f) * (Math.PI / 180f).toFloat()
                                        val x = cx + kotlin.math.cos(angle) * w * 0.35f
                                        val y = cy + kotlin.math.sin(angle) * h * 0.35f
                                        drawLine(Color(0xFF8338EC), Offset(cx, cy), Offset(x, y), strokeWidth = 3f)
                                        drawCircle(Color(0xFF00F5D4), radius = 4f, center = Offset(x, y))
                                    }
                                    drawCircle(Color(0xFFFF007F), radius = 7f, center = Offset(cx, cy))
                                }
                                "zoom_pulse" -> {
                                    drawRect(Color(0xFF0D253A))
                                    val cx = w / 2f
                                    val cy = h / 2f
                                    drawCircle(Color(0xFF06D6A0).copy(alpha = 0.25f), radius = w * 0.42f, center = Offset(cx, cy), style = Stroke(2f))
                                    drawCircle(Color(0xFF06D6A0).copy(alpha = 0.55f), radius = w * 0.28f, center = Offset(cx, cy), style = Stroke(2.5f))
                                    drawCircle(Color(0xFF06D6A0), radius = w * 0.14f, center = Offset(cx, cy), style = Stroke(3f))
                                    drawCircle(Color.White, radius = 4f, center = Offset(cx, cy))
                                }
                                "shake" -> {
                                    drawRect(Color(0xFF2B1117))
                                    drawRect(Color(0xFFE63946).copy(alpha = 0.4f), Offset(w * 0.25f, h * 0.25f), Size(w * 0.5f, h * 0.5f))
                                    drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.32f, h * 0.28f), Size(w * 0.5f, h * 0.5f), style = Stroke(2.5f))
                                    drawLine(Color.Yellow, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), strokeWidth = 2.5f)
                                }
                                "bloom", "halation" -> {
                                    drawRect(Color(0xFF261D11))
                                    drawCircle(Color(0xFFFFD166).copy(alpha = 0.25f), radius = w * 0.4f, center = Offset(w * 0.5f, h * 0.5f))
                                    drawCircle(Color(0xFFFFD166).copy(alpha = 0.55f), radius = w * 0.24f, center = Offset(w * 0.5f, h * 0.5f))
                                    drawCircle(Color.White, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.5f))
                                }
                                "pixelate", "mosaic" -> {
                                    val cols = 6
                                    val rows = 4
                                    val cw = w / cols
                                    val ch = h / rows
                                    val colors = listOf(Color(0xFF4361EE), Color(0xFF3A0CA3), Color(0xFF7209B7), Color(0xFFF72585), Color(0xFF4CC9F0))
                                    for (r in 0 until rows) {
                                        for (c in 0 until cols) {
                                            drawRect(colors[(r * 3 + c) % colors.size], Offset(c * cw, r * ch), Size(cw - 1f, ch - 1f))
                                        }
                                    }
                                }
                                "scan_lines" -> {
                                    drawRect(Color(0xFF0F172A))
                                    drawCircle(Color(0xFF38BDF8), radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.5f))
                                    for (i in 0..12) {
                                        val y = (h / 12f) * i
                                        drawLine(Color.Black.copy(alpha = 0.75f), Offset(0f, y), Offset(w, y), strokeWidth = 2.5f)
                                    }
                                }
                                "wave", "ripple", "shockwave" -> {
                                    drawRect(Color(0xFF0B2545))
                                    val path = Path()
                                    path.moveTo(0f, h * 0.5f)
                                    for (x in 0..w.toInt() step 5) {
                                        val y = (h * 0.5f) + kotlin.math.sin(x * 0.15f) * (h * 0.25f)
                                        path.lineTo(x.toFloat(), y)
                                    }
                                    drawPath(path, Color(0xFF1790FF), style = Stroke(width = 3.5f))
                                    val path2 = Path()
                                    path2.moveTo(0f, h * 0.35f)
                                    for (x in 0..w.toInt() step 5) {
                                        val y = (h * 0.35f) + kotlin.math.sin(x * 0.15f + 1f) * (h * 0.2f)
                                        path2.lineTo(x.toFloat(), y)
                                    }
                                    drawPath(path2, Color(0xFF80E5FF), style = Stroke(width = 2.5f))
                                }
                                "mirror" -> {
                                    drawRect(Color(0xFF1E1E2F))
                                    drawRect(Color(0xFF9B5DE5), Offset(w * 0.15f, h * 0.2f), Size(w * 0.3f, h * 0.6f))
                                    drawRect(Color(0xFF9B5DE5), Offset(w * 0.55f, h * 0.2f), Size(w * 0.3f, h * 0.6f))
                                    drawLine(Color.White, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), strokeWidth = 2.5f)
                                }
                                "neon_glow" -> {
                                    drawRect(Color(0xFF080811))
                                    drawCircle(Color(0xFF00F5D4).copy(alpha = 0.3f), radius = w * 0.32f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(8f))
                                    drawCircle(Color(0xFF00F5D4), radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(3.5f))
                                    drawCircle(Color.White, radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(1.2f))
                                }
                                "comic", "sketch" -> {
                                    drawRect(Color(0xFFF4F4F6))
                                    drawLine(Color.Black, Offset(w * 0.2f, h * 0.25f), Offset(w * 0.8f, h * 0.25f), strokeWidth = 3f)
                                    drawLine(Color.Black, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.7f, h * 0.5f), strokeWidth = 3.5f)
                                    drawLine(Color.Black, Offset(w * 0.25f, h * 0.75f), Offset(w * 0.75f, h * 0.75f), strokeWidth = 3f)
                                    drawLine(Color(0xFFFF0055), Offset(w * 0.15f, h * 0.35f), Offset(w * 0.85f, h * 0.65f), strokeWidth = 2f)
                                }
                                "thermal" -> {
                                    val colors = listOf(Color(0xFF0000FF), Color(0xFF00FFFF), Color(0xFF00FF00), Color(0xFFFFFF00), Color(0xFFFF0000))
                                    val segW = w / colors.size
                                    for (i in colors.indices) {
                                        drawRect(colors[i], Offset(i * segW, 0f), Size(segW, h))
                                    }
                                    drawCircle(Color.White, radius = w * 0.18f, center = Offset(w * 0.7f, h * 0.5f))
                                }
                                "night_vision" -> {
                                    drawRect(Color(0xFF002409))
                                    drawCircle(Color(0xFF00FF66).copy(alpha = 0.35f), radius = w * 0.42f, center = Offset(w * 0.5f, h * 0.5f))
                                    drawCircle(Color(0xFF00FF66), radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(2.5f))
                                    drawLine(Color(0xFF00FF66), Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), strokeWidth = 1.5f)
                                    drawLine(Color(0xFF00FF66), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1.5f)
                                }
                                "blur_gaussian", "blur_motion" -> {
                                    drawRect(Color(0xFF1E293B))
                                    drawCircle(Color(0xFF60A5FA).copy(alpha = 0.25f), radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f))
                                    drawCircle(Color(0xFF60A5FA).copy(alpha = 0.5f), radius = w * 0.26f, center = Offset(w * 0.5f, h * 0.5f))
                                    drawCircle(Color(0xFF93C5FD).copy(alpha = 0.75f), radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.5f))
                                }
                                "invert" -> {
                                    drawRect(Color.White, Offset(0f, 0f), Size(w * 0.5f, h))
                                    drawRect(Color.Black, Offset(w * 0.5f, 0f), Size(w * 0.5f, h))
                                    drawCircle(Color.Black, radius = w * 0.18f, center = Offset(w * 0.3f, h * 0.5f))
                                    drawCircle(Color.White, radius = w * 0.18f, center = Offset(w * 0.7f, h * 0.5f))
                                }
                                "duotone", "posterize" -> {
                                    drawRect(Color(0xFF7209B7), Offset(0f, 0f), Size(w, h))
                                    drawCircle(Color(0xFF4CC9F0), radius = w * 0.26f, center = Offset(w * 0.5f, h * 0.5f))
                                    drawRect(Color(0xFFF72585).copy(alpha = 0.45f), Offset(w * 0.2f, h * 0.4f), Size(w * 0.6f, h * 0.4f))
                                }
                                "edge_detect", "emboss" -> {
                                    drawRect(Color(0xFF121212))
                                    drawCircle(Color.White, radius = w * 0.24f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(3f))
                                    drawRect(Color.Cyan, Offset(w * 0.28f, h * 0.3f), Size(w * 0.44f, h * 0.4f), style = Stroke(2f))
                                }
                                "old_film" -> {
                                    drawRect(Color(0xFF3D2C1D))
                                    drawLine(Color(0xFFE8D5B5), Offset(w * 0.35f, 0f), Offset(w * 0.35f, h), strokeWidth = 2f)
                                    drawCircle(Color.Black.copy(alpha = 0.6f), radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(8f))
                                    drawCircle(Color(0xFFE8D5B5), radius = 2f, center = Offset(w * 0.6f, h * 0.3f))
                                }
                                else -> {
                                    drawRect(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(c1, c2)
                                        )
                                    )
                                    drawCircle(Color.White.copy(alpha = 0.8f), radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(2.5f))
                                    drawCircle(Color.White, radius = 4f, center = Offset(w * 0.5f, h * 0.5f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        e.displayName,
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SheetSlider("Effect Intensity", intensity, 0f..1f) { intensity = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply Effect", onClick = { onApply(selected, intensity) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TransitionsSheet(
    current: TransitionType,
    durationUs: Long,
    onApply: (TransitionType, Long) -> Unit,
    onApplyToAll: ((TransitionType, Long) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var sel by remember { mutableStateOf(current) }
    var dur by remember { mutableStateOf(durationUs.toFloat().coerceIn(200_000f, 2_000_000f)) }

    val transitionVisuals = mapOf(
        TransitionType.NONE to Pair("Cut", Icons.Filled.Block),
        TransitionType.FADE to Pair("Fade", Icons.Filled.Gradient),
        TransitionType.SLIDE_LEFT to Pair("Slide L", Icons.Filled.ArrowBack),
        TransitionType.SLIDE_RIGHT to Pair("Slide R", Icons.Filled.ArrowForward),
        TransitionType.SLIDE_UP to Pair("Slide Up", Icons.Filled.ArrowUpward),
        TransitionType.SLIDE_DOWN to Pair("Slide Dn", Icons.Filled.ArrowDownward),
        TransitionType.DISSOLVE to Pair("Dissolve", Icons.Filled.BlurOn),
        TransitionType.WIPE_LEFT to Pair("Wipe L", Icons.Filled.West),
        TransitionType.WIPE_RIGHT to Pair("Wipe R", Icons.Filled.East),
        TransitionType.WIPE_UP to Pair("Wipe Up", Icons.Filled.ArrowUpward),
        TransitionType.WIPE_DOWN to Pair("Wipe Dn", Icons.Filled.ArrowDownward),
        TransitionType.CIRCLE_CROP to Pair("Circle In", Icons.Filled.RadioButtonUnchecked),
        TransitionType.CIRCLE_CLOSE to Pair("Circle Out", Icons.Filled.Lens),
        TransitionType.CROSS_ZOOM to Pair("Cross Zoom", Icons.Filled.ZoomIn),
        TransitionType.ZOOM_IN to Pair("Zoom In", Icons.Filled.ZoomIn),
        TransitionType.ZOOM_OUT to Pair("Zoom Out", Icons.Filled.ZoomOut),
        TransitionType.FLASH_WHITE to Pair("Flash W", Icons.Filled.FlashOn),
        TransitionType.FLASH_BLACK to Pair("Flash B", Icons.Filled.FlashOff),
        TransitionType.GLITCH to Pair("Glitch", Icons.Filled.Shuffle),
        TransitionType.PIXELATE to Pair("Pixelate", Icons.Filled.GridView),
        TransitionType.BLUR to Pair("Blur", Icons.Filled.BlurCircular),
        TransitionType.ROTATE to Pair("Rotate", Icons.Filled.RotateRight),
        TransitionType.RIPPLE to Pair("Ripple", Icons.Filled.Waves),
        TransitionType.HEART to Pair("Heart", Icons.Filled.Favorite),
        TransitionType.STAR to Pair("Star", Icons.Filled.Star),
        TransitionType.CURTAIN to Pair("Curtain", Icons.Filled.CompareArrows),
        TransitionType.ZOOM_BLUR to Pair("Zoom Blur", Icons.Filled.BlurCircular),
        TransitionType.ROTATE_ZOOM to Pair("Rot Zoom", Icons.Filled.RotateRight),
        TransitionType.SHAKE to Pair("Shake", Icons.Filled.Shuffle),
        TransitionType.FLOODLIGHT to Pair("Floodlight", Icons.Filled.FlashOn),
        TransitionType.SPIN to Pair("Spin", Icons.Filled.Sync),
        TransitionType.BLINK to Pair("Blink", Icons.Filled.FlashOff),
        TransitionType.SPLIT_V to Pair("Split Vert", Icons.Filled.GridView),
        TransitionType.SPLIT_H to Pair("Split Horiz", Icons.Filled.GridView)
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Scene Transitions", onDismiss)
        Spacer(Modifier.height(10.dp))
        Text("33 cinematic transitions ported from VN Editor and CapCut", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(TransitionType.values().toList()) { t ->
                val isSelected = sel == t
                val visual = transitionVisuals[t] ?: Pair(t.displayName, Icons.Filled.SwapHoriz)

                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccentPrimary.copy(alpha = 0.18f) else Surface2)
                        .border(1.5.dp, if (isSelected) AccentPrimary else HairlineBorder, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { sel = t }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected)
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(Color(0xFFE94560), Color(0xFFFF5E62))
                                    )
                                else
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(Color(0xFF1E1E2C), Color(0xFF2A2A3E))
                                    )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            visual.second,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else Color(0xFF00F5D4),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        visual.first,
                        color = if (isSelected) AccentPrimary else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SheetSlider("Transition Duration: ${String.format(java.util.Locale.US, "%.1f", dur / 1_000_000f)}s", dur / 1_000_000f, 0.2f..2.0f) { dur = it * 1_000_000f }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onApplyToAll != null) {
                SecondaryButton(
                    "Apply to All",
                    onClick = {
                        onApplyToAll(sel, dur.toLong())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            PrimaryButton(
                "Apply Transition",
                onClick = {
                    onApply(sel, dur.toLong())
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
        }
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
        SheetSlider("Music level under voice", lvl, 0f..1f) { lvl = it }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Apply", onClick = { onApply(en, lvl) }, modifier = Modifier.fillMaxWidth())
    }
}

// ── InShot 10-Band Graphic Equalizer Sheet ─────────────────────────────
@Composable
fun EqualizerSheet(
    gains: List<Float> = emptyList(),
    onApply: (List<Float>) -> Unit,
    onDismiss: () -> Unit
) {
    val current = remember(gains) {
        mutableStateOf(
            com.clipcut.core.audio.InShotEq.copy(
                if (gains.size == com.clipcut.core.audio.InShotEq.BAND_COUNT) gains.toFloatArray() else null
            )
        )
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Equalizer (10-Band InShot)", onDismiss)
        Spacer(Modifier.height(8.dp))
        Text("Presets", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(com.clipcut.core.audio.InShotEq.PRESETS) { p ->
                val selected = current.value.contentEquals(p.gains)
                Surface(
                    onClick = {
                        current.value = p.gains.copyOf()
                        onApply(current.value.toList())
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) AccentPrimary else Surface2,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AccentPrimary else HairlineBorder)
                ) {
                    Text(
                        p.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) DarkBackground else Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 10 Band Sliders
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            com.clipcut.core.audio.InShotEq.FREQUENCIES_HZ.forEachIndexed { i, freq ->
                val gain = current.value[i]
                val freqLabel = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(freqLabel, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                    Slider(
                        value = gain,
                        onValueChange = { newV ->
                            current.value = current.value.copyOf().also { it[i] = newV }
                        },
                        valueRange = -12f..12f,
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary),
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                    Text(
                        "${if (gain > 0) "+" else ""}${gain.toInt()} dB",
                        color = if (gain == 0f) TextSecondary else AccentPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(46.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    current.value = com.clipcut.core.audio.InShotEq.DEFAULT_GAINS.copyOf()
                    onApply(current.value.toList())
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset Flat", color = Color.White)
            }
            PrimaryButton(
                text = "Apply EQ",
                onClick = {
                    onApply(current.value.toList())
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── PowerDirector Voice Changer & Audio FX Sheet ───────────────────────
@Composable
fun VoiceRemixSheet(
    currentPreset: com.clipcut.core.audio.AudioDsp.VoicePreset = com.clipcut.core.audio.AudioDsp.VoicePreset.NATURAL,
    onApplyVoice: (com.clipcut.core.audio.AudioDsp.VoicePreset) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentPreset) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Voice Studio & Remix (PowerDirector)", onDismiss)
        Spacer(Modifier.height(8.dp))
        Text(
            "Real-time pitch shift, cyber-modulation, and character vocal filters rendered on-device.",
            color = TextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(14.dp))

        com.clipcut.core.audio.AudioDsp.VoicePreset.values().forEach { preset ->
            val isSel = selected == preset
            Surface(
                onClick = {
                    selected = preset
                    onApplyVoice(preset)
                },
                shape = RoundedCornerShape(10.dp),
                color = if (isSel) AccentPrimary.copy(alpha = 0.2f) else Surface2,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) AccentPrimary else HairlineBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = if (isSel) AccentPrimary else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        val desc = when (preset) {
                            com.clipcut.core.audio.AudioDsp.VoicePreset.NATURAL -> "Original unaltered audio"
                            com.clipcut.core.audio.AudioDsp.VoicePreset.CHIPMUNK -> "+8 semitones high-pitched voice"
                            com.clipcut.core.audio.AudioDsp.VoicePreset.DEEP_BASS -> "-7 semitones deep movie narrator"
                            com.clipcut.core.audio.AudioDsp.VoicePreset.ROBOT -> "40 Hz ring-modulated cyber-vocal"
                            com.clipcut.core.audio.AudioDsp.VoicePreset.RADIO -> "+2 semitones bandwidth limited AM radio"
                            com.clipcut.core.audio.AudioDsp.VoicePreset.ALIEN -> "+5 semitones dual modulation"
                        }
                        Text(desc, color = TextSecondary, fontSize = 11.sp)
                    }
                    if (isSel) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

// ── VN Brand Kit Sheet ─────────────────────────────────────────────────
@Composable
fun BrandKitSheet(
    current: com.clipcut.core.model.BrandKitConfig?,
    onApply: (com.clipcut.core.model.BrandKitConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    val base = current ?: com.clipcut.core.model.BrandKitConfig()
    var watermarkText by remember { mutableStateOf(base.watermarkText) }
    var selectedColor by remember { mutableStateOf(base.brandColor) }
    var posIndex by remember { mutableIntStateOf(base.watermarkPosIndex) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Brand Kit Studio (VN)", onDismiss)
        Spacer(Modifier.height(8.dp))
        Text(
            "VN Brand Kit: automatically overlay your creator watermark, custom brand palette, and channel signature.",
            color = TextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = watermarkText,
            onValueChange = { watermarkText = it },
            label = { Text("Watermark / Channel Signature", color = TextSecondary, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AccentPrimary
            )
        )

        Spacer(Modifier.height(12.dp))
        Text("Watermark Position", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Bottom Center" to 0, "Top Left" to 1, "Top Right" to 2, "Bottom Right" to 4).forEach { (lbl, idx) ->
                val isSel = posIndex == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) AccentPrimary else Surface2)
                        .border(1.dp, if (isSel) AccentPrimary else HairlineBorder, RoundedCornerShape(8.dp))
                        .clickable { posIndex = idx }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(lbl, color = if (isSel) DarkBackground else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Save Brand Kit",
            onClick = {
                onApply(
                    com.clipcut.core.model.BrandKitConfig(
                        brandColor = selectedColor,
                        watermarkText = watermarkText,
                        watermarkPosIndex = posIndex
                    )
                )
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
