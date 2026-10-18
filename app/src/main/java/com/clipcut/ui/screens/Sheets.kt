package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.sqrt
import java.util.UUID
import com.clipcut.core.model.*
import com.clipcut.ui.components.EditorFilterChip
import com.clipcut.ui.components.PrimaryButton
import com.clipcut.ui.components.SecondaryButton
import com.clipcut.ui.util.AssetImageHelper
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
    onOpenLiveTrimmer: (AudioTrackItem) -> Unit = {},
    onVoiceoverClick: () -> Unit = {},
    onAddAssetAudio: (String, String) -> Unit = { _, _ -> },
    onExtractAudioClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var activeCategory by remember { mutableStateOf("Sound FX") }
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    val mediaPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    var playingTrackId by remember { mutableStateOf<String?>(null) }
    val trackPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
            } catch (_: Throwable) {}
            mediaPlayerRef.value = null

            try {
                trackPlayerRef.value?.stop()
                trackPlayerRef.value?.release()
            } catch (_: Throwable) {}
            trackPlayerRef.value = null
        }
    }

    fun playTrackPreview(track: AudioTrackItem) {
        if (playingTrackId == track.id && trackPlayerRef.value?.isPlaying == true) {
            try {
                trackPlayerRef.value?.stop()
                trackPlayerRef.value?.release()
            } catch (_: Throwable) {}
            trackPlayerRef.value = null
            playingTrackId = null
            return
        }

        try {
            trackPlayerRef.value?.stop()
            trackPlayerRef.value?.release()
            trackPlayerRef.value = null

            val mp = android.media.MediaPlayer()
            val uri = android.net.Uri.parse(track.uri)
            if (track.uri.startsWith("asset://") || (!track.uri.contains("://") && !track.uri.startsWith("/"))) {
                val clean = track.uri.removePrefix("asset://").removePrefix("/")
                val tempFile = java.io.File(ctx.cacheDir, "track_preview.tmp")
                ctx.assets.open(clean).use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                mp.setDataSource(tempFile.absolutePath)
            } else if (uri.scheme == "content") {
                mp.setDataSource(ctx, uri)
            } else {
                val path = uri.path ?: track.uri
                mp.setDataSource(path)
            }
            mp.setOnCompletionListener {
                playingTrackId = null
            }
            mp.prepare()
            val startMs = (track.trimStartUs / 1000).toInt()
            if (startMs > 0) mp.seekTo(startMs)
            mp.start()
            playingTrackId = track.id
            trackPlayerRef.value = mp
        } catch (_: Throwable) {
            playingTrackId = null
        }
    }

    fun playPreview(assetPath: String) {
        try {
            if (currentlyPlayingPath == assetPath && mediaPlayerRef.value?.isPlaying == true) {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
                mediaPlayerRef.value = null
                currentlyPlayingPath = null
                return
            }
            mediaPlayerRef.value?.stop()
            mediaPlayerRef.value?.release()
            mediaPlayerRef.value = null

            val tempFile = java.io.File(ctx.cacheDir, "preview_audio.tmp")
            ctx.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val mp = android.media.MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    currentlyPlayingPath = null
                }
                prepare()
                start()
            }
            mediaPlayerRef.value = mp
            currentlyPlayingPath = assetPath
        } catch (_: Throwable) {
            currentlyPlayingPath = null
        }
    }

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
                text = "Pick Custom File",
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

        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onExtractAudioClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.8f)),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = AccentPrimary.copy(alpha = 0.12f)
            )
        ) {
            Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Extract Audio from Video (MP3)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("Creator Audio & SFX Library", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Instant sound effects & soundtrack from benchmark editors", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        // Categories
        val categories = listOf("Sound FX", "Gaming & Memes", "Transitions", "Chill BGM")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                EditorFilterChip(cat, activeCategory == cat) { activeCategory = cat }
            }
        }
        Spacer(Modifier.height(10.dp))

        val currentItems = when (activeCategory) {
            "Sound FX" -> listOf(
                Triple("Vine Boom", "💥", "audio/sfx/Memes-Vine_Boom.mp3"),
                Triple("Whoosh Swoosh", "💨", "audio/sfx/Whoosh_Swoosh_08.mp3"),
                Triple("Air Tail Woosh", "🚀", "audio/sfx/Air_Tail_Woosh.mp3"),
                Triple("Camera Shutter", "📸", "audio/sfx/Camera_Shutter_02.mp3"),
                Triple("Magic Pop Ding", "🔔", "audio/sfx/Magic_Pop_Ding.mp3"),
                Triple("Explosion", "💣", "audio/sfx/Explosion.mp3"),
                Triple("Suspense Impact", "⚡", "audio/sfx/Suspense_Impact.wav"),
                Triple("Camera Scan Beep", "🔍", "audio/sfx/camera_scan_beep.mp3")
            )
            "Gaming & Memes" -> listOf(
                Triple("Mario Jump", "🍄", "audio/sfx/Memes-Mario_Jump.mp3"),
                Triple("Meme Alert", "🚨", "audio/sfx/Memes-Alert.mp3"),
                Triple("Baby Laughing", "👶", "audio/sfx/Memes-Baby_Laughing.mp3"),
                Triple("Cartoon Running", "🏃", "audio/sfx/Memes-Cartoon_Running.mp3"),
                Triple("Children Yay!", "🎉", "audio/sfx/Memes-Children_Yay.mp3"),
                Triple("Funny Laugh", "😂", "audio/sfx/Memes-Funny_Laugh_01.mp3"),
                Triple("Laser Pew Pew", "🔫", "audio/sfx/Memes-Pew.mp3"),
                Triple("Kid Oof", "💀", "audio/sfx/Memes-Kid_Oof.mp3"),
                Triple("Asian Huh?", "❓", "audio/sfx/Memes-Asian_Huh.mp3")
            )
            "Transitions" -> listOf(
                Triple("Film Transition 1", "🎬", "audio/transitions/audio_trans_film01.mp3"),
                Triple("Film Transition 2", "🎬", "audio/transitions/audio_trans_film02.mp3"),
                Triple("Film Transition 3", "🎬", "audio/transitions/audio_trans_film03.mp3"),
                Triple("Film Transition 4", "🎬", "audio/transitions/audio_trans_film04.mp3"),
                Triple("Film Transition 5", "🎬", "audio/transitions/audio_trans_film05.mp3"),
                Triple("Film Transition 6", "🎬", "audio/transitions/audio_trans_film06.mp3"),
                Triple("Film Transition 7", "🎬", "audio/transitions/audio_trans_film07.mp3"),
                Triple("Classic Film Whoosh", "🎞️", "audio/transitions/flim_transition.mp3")
            )
            else -> listOf(
                Triple("Autumn Afternoon (Lofi)", "☕", "audio/bgm/autumn_afternoon_lofi.mp3"),
                Triple("Holiday Acoustic (Santa)", "🎄", "audio/bgm/holiday_acoustic_santa.mp3")
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            currentItems.forEach { (name, icon, path) ->
                val isPlaying = currentlyPlayingPath == path
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { playPreview(path) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(if (isPlaying) "⏹" else "▶", fontSize = 14.sp, color = AccentPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentPrimary.copy(alpha = 0.2f))
                            .clickable {
                                onAddAssetAudio(path, name)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("+ Add", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
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
                    val isTrackPlaying = (playingTrackId == track.id)
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
                        // Live audition play/pause cut button
                        IconButton(
                            onClick = { playTrackPreview(track) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (isTrackPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isTrackPlaying) "Pause" else "Play",
                                tint = AccentPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteTrack(track.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Track", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Live Trimmer Studio Quick Open Button
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onOpenLiveTrimmer(track) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.7f)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = AccentPrimary.copy(alpha = 0.12f)
                        )
                    ) {
                        Text("✂️ Open Live Cut & Waveform Studio", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    onOpenFingerPaint: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var threshold by remember { mutableStateOf(initialThreshold) }
    var smoothness by remember { mutableStateOf(initialSmoothness) }
    var colorPick by remember { mutableStateOf(initialColor) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Chroma Key", onDismiss)
        Spacer(Modifier.height(12.dp))

        // Finger Paint Green Screen Quick Launcher
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF10B981).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color(0xFF10B981)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenFingerPaint() }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Brush, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("🖌️ Paint Green Screen with Finger", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("No green cloth? Swipe your finger across your room to paint green screen & apply studios", color = TextSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
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
            Triple("HDR Warm", "cube/Landscape HDR Warm 01.cube", Pair(Color(0xFFF77F00), Color(0xFFFCBF49))),
            Triple("HDR Cold", "cube/Landscape HDR Cold 01.cube", Pair(Color(0xFF0077B6), Color(0xFF90E0EF))),
            Triple("Spring Cool", "cube/Spring_Cool & Bright_heavy.cube", Pair(Color(0xFF00B4D8), Color(0xFF7209B7))),
            Triple("Amber Glow", "cube/Spring_Amber Highlight_heavy.cube", Pair(Color(0xFFFFB703), Color(0xFFFB8500))),
            Triple("Vintage Kick", "cube/Spring_Vintage Kick_heavy.cube", Pair(Color(0xFFCB997E), Color(0xFFDDB892))),
            Triple("Fresh Burst", "cube/Spring_Fresh Burst_heavy.cube", Pair(Color(0xFF2A9D8F), Color(0xFFE9C46A))),
            Triple("Luminescent", "cube/Spring_Luminescent_heavy.cube", Pair(Color(0xFFF72585), Color(0xFF7209B7))),
            Triple("Epic Cinema", "Epic_Cinematic.cube", Pair(Color(0xFFE63946), Color(0xFF1D3557))),
            Triple("Hot Sun", "Hot_Sun.cube", Pair(Color(0xFFFF9E00), Color(0xFFFF0054))),
            Triple("Nature Skin", "Nature_Skin.cube", Pair(Color(0xFF588157), Color(0xFFE9C46A))),
            Triple("Summer View", "Summer_View.cube", Pair(Color(0xFF0077B6), Color(0xFF00F5D4))),
            Triple("B&W Film 3D", "B&W_Film.CUBE", Pair(Color(0xFF2B2D42), Color(0xFF8D99AE)))
        )

        val ctx = androidx.compose.ui.platform.LocalContext.current
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            items(realLuts.size) { idx ->
                val (title, fileName, colors) = realLuts[idx]
                val lutAssetPath = "asset://luts/$fileName"
                val isSelected = currentLut?.uri == lutAssetPath || currentLut?.name == title

                val thumbBmp = remember(fileName) {
                    try {
                        val base = fileName.substringAfterLast('/').removeSuffix(".png").removeSuffix(".cube").replace(" ", "_")
                        val thumbPath = "luts/thumbs/${base}_thumb.png"
                        ctx.assets.open(thumbPath).use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        }?.asImageBitmap()
                    } catch (_: Throwable) { null }
                }

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
                    if (thumbBmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = thumbBmp,
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
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
                    }
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

// ── FilterSheet with 3-Way Wheels & Film LUTs Studio (Stitch Screen 4) ───
@Composable
private fun StudioColorWheel(
    title: String,
    subtitle: String,
    accentColor: Color,
    puckX: Float,
    puckY: Float,
    sliderValue: Float,
    sliderRange: ClosedFloatingPointRange<Float>,
    sliderLabel: String,
    onPuckChange: (Float, Float) -> Unit,
    onSliderChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextSecondary, fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(ObsidianSurfaceLowest)
                .border(1.5.dp, ObsidianBorderVariant, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val radius = 43.dp.toPx()
                        val nx = ((puckX * radius) + dragAmount.x).coerceIn(-radius, radius) / radius
                        val ny = ((puckY * radius) + dragAmount.y).coerceIn(-radius, radius) / radius
                        onPuckChange(nx, ny)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            accentColor.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    ),
                    radius = r,
                    center = center
                )
                drawLine(Color.White.copy(alpha = 0.15f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1f)
                drawLine(Color.White.copy(alpha = 0.15f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1f)
                val px = center.x + puckX * (r - 7.dp.toPx())
                val py = center.y + puckY * (r - 7.dp.toPx())
                drawCircle(accentColor.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = Offset(px, py))
                drawCircle(accentColor, radius = 5.dp.toPx(), center = Offset(px, py))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(px, py))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "$sliderLabel: ${String.format(java.util.Locale.US, "%.2f", sliderValue)}",
            color = TextSecondary,
            fontSize = 10.sp
        )
        Slider(
            value = sliderValue,
            onValueChange = onSliderChange,
            valueRange = sliderRange,
            modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = ObsidianBorder
            )
        )
    }
}

@Composable
private fun RgbParadeWaveform(cfg: FilterConfig) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceLowest)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val channelW = w / 3f

            val ire100 = h * 0.15f
            val ire50 = h * 0.50f
            val ire0 = h * 0.85f
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, ire100), Offset(w, ire100), 1f)
            drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, ire50), Offset(w, ire50), 1f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, ire0), Offset(w, ire0), 1f)

            // Red channel
            val redLift = (cfg.brightness * 18f + cfg.temperature * 16f)
            val redPath = Path().apply {
                moveTo(4f, ire50 - redLift)
                var x = 4f
                while (x < channelW - 4f) {
                    val progress = (x - 4f) / (channelW - 8f)
                    val amp = (sin(progress * 12.0) * 14.0 * cfg.contrast).toFloat()
                    lineTo(x, (ire50 - redLift + amp).coerceIn(4f, h - 4f))
                    x += 4f
                }
            }
            drawPath(redPath, Color(0xFFFF4D4D).copy(alpha = 0.85f), style = Stroke(1.5f))

            // Green channel
            val greenLift = (cfg.brightness * 18f + cfg.tint * 16f)
            val greenPath = Path().apply {
                moveTo(channelW + 4f, ire50 - greenLift)
                var x = channelW + 4f
                while (x < channelW * 2f - 4f) {
                    val progress = (x - (channelW + 4f)) / (channelW - 8f)
                    val amp = (cos(progress * 14.0) * 12.0 * cfg.contrast).toFloat()
                    lineTo(x, (ire50 - greenLift + amp).coerceIn(4f, h - 4f))
                    x += 4f
                }
            }
            drawPath(greenPath, Color(0xFF10B981).copy(alpha = 0.85f), style = Stroke(1.5f))

            // Blue channel
            val blueLift = (cfg.brightness * 18f - cfg.temperature * 16f)
            val bluePath = Path().apply {
                moveTo(channelW * 2f + 4f, ire50 - blueLift)
                var x = channelW * 2f + 4f
                while (x < w - 4f) {
                    val progress = (x - (channelW * 2f + 4f)) / (channelW - 8f)
                    val amp = (sin(progress * 10.0 + 1.0) * 15.0 * cfg.contrast).toFloat()
                    lineTo(x, (ire50 - blueLift + amp).coerceIn(4f, h - 4f))
                    x += 4f
                }
            }
            drawPath(bluePath, Color(0xFF00F2FE).copy(alpha = 0.85f), style = Stroke(1.5f))

            drawLine(Color.White.copy(alpha = 0.1f), Offset(channelW, 0f), Offset(channelW, h), 1f)
            drawLine(Color.White.copy(alpha = 0.1f), Offset(channelW * 2f, 0f), Offset(channelW * 2f, h), 1f)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RED", color = Color(0xFFFF6B6B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("GREEN", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("BLUE", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text("RGB PARADE", color = Color.White.copy(alpha = 0.35f), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

@Composable
fun FilterSheet(
    initialConfig: FilterConfig,
    onApplyFilter: (FilterConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var cfg by remember { mutableStateOf(initialConfig) }
    var activeStudioTab by remember { mutableStateOf(0) } // 0: 3-Way Wheels, 1: Film LUTs, 2: Adjustments

    // Lift / Gamma / Gain puck states
    var liftX by remember { mutableStateOf((cfg.tint / 0.5f).coerceIn(-1f, 1f)) }
    var liftY by remember { mutableStateOf((-cfg.temperature / 0.5f).coerceIn(-1f, 1f)) }
    var gammaX by remember { mutableStateOf(0f) }
    var gammaY by remember { mutableStateOf(0f) }
    var gainX by remember { mutableStateOf((cfg.temperature / 0.5f).coerceIn(-1f, 1f)) }
    var gainY by remember { mutableStateOf((cfg.brightness / 0.5f).coerceIn(-1f, 1f)) }

    val filmLuts = listOf(
        Triple("Rec.709 Standard", "Clean Studio Baseline", FilterConfig(brightness = 0f, contrast = 1.0f, saturation = 1.0f, temperature = 0f, vignette = 0f, tint = 0f)),
        Triple("Teal & Orange", "Hollywood Blockbuster", FilterConfig(brightness = 0f, contrast = 1.35f, saturation = 1.30f, temperature = -0.18f, vignette = 0.28f, tint = 0.08f)),
        Triple("Kodak Portra 400", "Warm Skin & Gold Tones", FilterConfig(brightness = 0.05f, contrast = 1.05f, saturation = 0.95f, temperature = 0.22f, vignette = 0.30f, tint = 0.06f)),
        Triple("Tokyo Cyberpunk", "Neon Violet & Cyan", FilterConfig(brightness = 0.02f, contrast = 1.40f, saturation = 1.50f, temperature = -0.30f, vignette = 0.40f, tint = 0.25f)),
        Triple("Vintage 35mm", "Analog Cinema Faded", FilterConfig(brightness = 0.08f, contrast = 0.90f, saturation = 0.80f, temperature = 0.15f, vignette = 0.45f, tint = -0.05f)),
        Triple("Warm Sunset", "Golden Hour Radiance", FilterConfig(brightness = 0.05f, contrast = 1.15f, saturation = 1.25f, temperature = 0.40f, vignette = 0.20f, tint = -0.08f)),
        Triple("Noir Drama", "Tri-X High Contrast B&W", FilterConfig(filterType = FilterType.GRAYSCALE, brightness = 0f, contrast = 1.55f, saturation = 0f, temperature = 0f, vignette = 0.60f)),
        Triple("Emerald Matrix", "Sci-Fi Cool Shadows", FilterConfig(brightness = -0.02f, contrast = 1.25f, saturation = 1.10f, temperature = -0.12f, vignette = 0.35f, tint = -0.22f))
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Color Grading Studio", onDismiss)
        Spacer(Modifier.height(8.dp))

        // Live RGB Parade Waveform scope
        RgbParadeWaveform(cfg)
        Spacer(Modifier.height(10.dp))

        // Studio Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianSurfaceLowest)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("3-Way Wheels", "Film LUTs", "Adjustments").forEachIndexed { index, label ->
                val isSelected = activeStudioTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ObsidianSurfaceHighest else Color.Transparent)
                        .clickable { activeStudioTab = index }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) ElectricCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (activeStudioTab) {
            0 -> {
                // 3-Way Wheels: Lift, Gamma, Gain
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudioColorWheel(
                        title = "LIFT",
                        subtitle = "Shadows & Pedestal",
                        accentColor = ElectricCyan,
                        puckX = liftX,
                        puckY = liftY,
                        sliderValue = cfg.brightness,
                        sliderRange = -0.5f..0.5f,
                        sliderLabel = "Pedestal",
                        onPuckChange = { nx, ny ->
                            liftX = nx
                            liftY = ny
                            cfg = cfg.copy(tint = nx * 0.4f, temperature = -ny * 0.4f)
                        },
                        onSliderChange = { cfg = cfg.copy(brightness = it) }
                    )

                    StudioColorWheel(
                        title = "GAMMA",
                        subtitle = "Midtones & Balance",
                        accentColor = VibrantViolet,
                        puckX = gammaX,
                        puckY = gammaY,
                        sliderValue = cfg.contrast,
                        sliderRange = 0.5f..2.0f,
                        sliderLabel = "Contrast",
                        onPuckChange = { nx, ny ->
                            gammaX = nx
                            gammaY = ny
                            cfg = cfg.copy(saturation = (1f + nx * 0.6f).coerceIn(0f, 2f))
                        },
                        onSliderChange = { cfg = cfg.copy(contrast = it) }
                    )

                    StudioColorWheel(
                        title = "GAIN",
                        subtitle = "Highlights & Whites",
                        accentColor = LuminescentAmber,
                        puckX = gainX,
                        puckY = gainY,
                        sliderValue = cfg.temperature,
                        sliderRange = -0.5f..0.5f,
                        sliderLabel = "Warmth",
                        onPuckChange = { nx, ny ->
                            gainX = nx
                            gainY = ny
                            cfg = cfg.copy(temperature = nx * 0.4f, brightness = (ny * 0.3f).coerceIn(-0.5f, 0.5f))
                        },
                        onSliderChange = { cfg = cfg.copy(temperature = it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        liftX = 0f; liftY = 0f
                        gammaX = 0f; gammaY = 0f
                        gainX = 0f; gainY = 0f
                        cfg = FilterConfig()
                    }) {
                        Text("Reset Wheels", color = TextTertiary, fontSize = 11.sp)
                    }
                }
            }

            1 -> {
                // Film LUTs Presets
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filmLuts) { (lutTitle, lutDesc, lutCfg) ->
                        val isSel = cfg.contrast == lutCfg.contrast && cfg.vignette == lutCfg.vignette && cfg.temperature == lutCfg.temperature
                        Column(
                            modifier = Modifier
                                .width(108.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) ObsidianSurfaceHighest else ObsidianSurface)
                                .border(1.5.dp, if (isSel) ElectricCyan else ObsidianBorder, RoundedCornerShape(12.dp))
                                .clickable { cfg = lutCfg }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            when (lutTitle) {
                                                "Teal & Orange" -> listOf(Color(0xFF005C8A), Color(0xFFFF7E47))
                                                "Kodak Portra 400" -> listOf(Color(0xFFD4A373), Color(0xFFFAEDCD))
                                                "Tokyo Cyberpunk" -> listOf(Color(0xFF7C3AED), Color(0xFF00F2FE))
                                                "Vintage 35mm" -> listOf(Color(0xFF6B705C), Color(0xFFDDA15E))
                                                "Warm Sunset" -> listOf(Color(0xFFFF5400), Color(0xFFFFBD00))
                                                "Noir Drama" -> listOf(Color(0xFF212529), Color(0xFFE9ECEF))
                                                "Emerald Matrix" -> listOf(Color(0xFF064E3B), Color(0xFF10B981))
                                                else -> listOf(Color(0xFF3A86FF), Color(0xFF8338EC))
                                            }
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MovieCreation,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                lutTitle,
                                color = if (isSel) ElectricCyan else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                lutDesc,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            2 -> {
                // Fine Adjustments
                SheetSlider("Brightness", cfg.brightness, -0.5f..0.5f) { cfg = cfg.copy(brightness = it) }
                SheetSlider("Contrast", cfg.contrast, 0.5f..2f) { cfg = cfg.copy(contrast = it) }
                SheetSlider("Saturation", cfg.saturation, 0f..2f) { cfg = cfg.copy(saturation = it) }
                SheetSlider("Temperature", cfg.temperature, -0.5f..0.5f) { cfg = cfg.copy(temperature = it) }
                SheetSlider("Vignette", cfg.vignette, 0f..1f) { cfg = cfg.copy(vignette = it) }
                SheetSlider("Tint", cfg.tint, -0.5f..0.5f) { cfg = cfg.copy(tint = it) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onApplyFilter(cfg) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(ElectricCyan, VibrantViolet))),
                contentAlignment = Alignment.Center
            ) {
                Text("Apply Color Grade", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// ── SpeedRampDialog (Stitch Screen 3: Cubic Bezier Speed Ramp Graph) ─────
@Composable
fun SpeedRampDialog(
    initialRamp: SpeedRamp,
    clipDurationUs: Long,
    onApply: (SpeedRamp) -> Unit,
    onDismiss: () -> Unit
) {
    var ramp by remember {
        mutableStateOf(
            if (initialRamp.keyframes.size >= 2) initialRamp
            else SpeedRamp(
                keyframes = listOf(
                    SpeedKeyframe(timeOffsetUs = 0L, speed = 1.0f),
                    SpeedKeyframe(timeOffsetUs = (clipDurationUs * 0.35f).toLong(), speed = 2.0f),
                    SpeedKeyframe(timeOffsetUs = (clipDurationUs * 0.65f).toLong(), speed = 0.5f),
                    SpeedKeyframe(timeOffsetUs = clipDurationUs, speed = 1.0f)
                )
            )
        )
    }
    var selectedPreset by remember { mutableStateOf<String?>("Montage") }
    var selectedNodeIndex by remember { mutableStateOf<Int?>(null) }
    var opticalFlowEnabled by remember { mutableStateOf(true) }
    var pitchPreserveEnabled by remember { mutableStateOf(true) }

    val presets = listOf(
        "Custom" to emptyList(),
        "Montage" to listOf(0.0f to 1.0f, 0.4f to 2.5f, 0.6f to 0.4f, 0.8f to 1.8f, 1.0f to 1.0f),
        "Hero Ramp" to listOf(0.0f to 1.0f, 0.3f to 3.0f, 0.5f to 0.3f, 0.7f to 2.5f, 1.0f to 1.0f),
        "Bullet Time" to listOf(0.0f to 2.5f, 0.35f to 2.5f, 0.5f to 0.2f, 0.65f to 2.5f, 1.0f to 2.5f),
        "Flash In" to listOf(0.0f to 4.0f, 0.25f to 3.5f, 0.55f to 1.0f, 1.0f to 1.0f),
        "Jump Cut" to listOf(0.0f to 0.5f, 0.4f to 0.5f, 0.55f to 3.5f, 0.7f to 0.5f, 1.0f to 0.5f)
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Speed Ramping & Velocity Curve", onDismiss)
        Spacer(Modifier.height(6.dp))

        // Active node coordinate readout badge
        val activeKf = selectedNodeIndex?.let { idx -> ramp.keyframes.getOrNull(idx) } ?: ramp.keyframes.firstOrNull()
        val timeSec = (activeKf?.timeOffsetUs ?: 0L).toFloat() / 1_000_000f
        val activeSpeed = activeKf?.speed ?: 1.0f

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianSurfaceLowest)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ElectricCyan))
                Spacer(Modifier.width(6.dp))
                Text(
                    "At ${String.format(java.util.Locale.US, "%.2f", timeSec)}s · ${String.format(java.util.Locale.US, "%.2f", activeSpeed)}x Velocity",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "${ramp.keyframes.size} Control Nodes",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // High-Resolution Cubic Bezier Curve Canvas
        val durationFloat = clipDurationUs.coerceAtLeast(1_000_000L).toFloat()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurfaceLowest)
                .border(1.dp, ObsidianBorderVariant, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(ramp.keyframes) {
                        detectTapGestures { tapOffset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val hitRadius = 24.dp.toPx()
                            val hitIndex = ramp.keyframes.indexOfFirst { kf ->
                                val kfX = (kf.timeOffsetUs.toFloat() / durationFloat).coerceIn(0f, 1f) * w
                                val kfY = (h * 0.88f - ((kf.speed - 0.1f) / 4.9f) * (h * 0.76f)).coerceIn(12f, h - 12f)
                                sqrt((tapOffset.x - kfX) * (tapOffset.x - kfX) + (tapOffset.y - kfY) * (tapOffset.y - kfY)) <= hitRadius
                            }
                            if (hitIndex >= 0) {
                                selectedNodeIndex = hitIndex
                            } else {
                                val tapTime = ((tapOffset.x / w).coerceIn(0f, 1f) * durationFloat).toLong()
                                val tapSpeed = (0.1f + ((h * 0.88f - tapOffset.y) / (h * 0.76f)) * 4.9f).coerceIn(0.1f, 5.0f)
                                val newKf = SpeedKeyframe(timeOffsetUs = tapTime, speed = tapSpeed)
                                val updated = (ramp.keyframes + newKf).sortedBy { it.timeOffsetUs }
                                ramp = ramp.copy(keyframes = updated)
                                selectedNodeIndex = updated.indexOf(newKf)
                                selectedPreset = "Custom"
                            }
                        }
                    }
                    .pointerInput(ramp.keyframes, selectedNodeIndex) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val hitRadius = 28.dp.toPx()
                                val hitIndex = ramp.keyframes.indexOfFirst { kf ->
                                    val kfX = (kf.timeOffsetUs.toFloat() / durationFloat).coerceIn(0f, 1f) * w
                                    val kfY = (h * 0.88f - ((kf.speed - 0.1f) / 4.9f) * (h * 0.76f)).coerceIn(12f, h - 12f)
                                    sqrt((startOffset.x - kfX) * (startOffset.x - kfX) + (startOffset.y - kfY) * (startOffset.y - kfY)) <= hitRadius
                                }
                                if (hitIndex >= 0) {
                                    selectedNodeIndex = hitIndex
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val curIdx = selectedNodeIndex ?: return@detectDragGestures
                                val kfs = ramp.keyframes.toMutableList()
                                if (curIdx in kfs.indices) {
                                    val kf = kfs[curIdx]
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    val curX = (kf.timeOffsetUs.toFloat() / durationFloat).coerceIn(0f, 1f) * w
                                    val curY = (h * 0.88f - ((kf.speed - 0.1f) / 4.9f) * (h * 0.76f)).coerceIn(12f, h - 12f)

                                    val newX = (curX + dragAmount.x).coerceIn(0f, w)
                                    val newY = (curY + dragAmount.y).coerceIn(12f, h - 12f)

                                    val newTime = if (curIdx == 0) 0L else if (curIdx == kfs.lastIndex) clipDurationUs else ((newX / w) * durationFloat).toLong()
                                    val newSpeed = (0.1f + ((h * 0.88f - newY) / (h * 0.76f)) * 4.9f).coerceIn(0.1f, 5.0f)

                                    kfs[curIdx] = kf.copy(timeOffsetUs = newTime, speed = newSpeed)
                                    ramp = ramp.copy(keyframes = kfs.sortedBy { it.timeOffsetUs })
                                    selectedPreset = "Custom"
                                }
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height

                val baselineY = (h * 0.88f - ((1.0f - 0.1f) / 4.9f) * (h * 0.76f))
                drawLine(
                    color = LuminescentAmber.copy(alpha = 0.35f),
                    start = Offset(0f, baselineY),
                    end = Offset(w, baselineY),
                    strokeWidth = 1.dp.toPx()
                )

                listOf(0.5f, 2.0f, 4.0f).forEach { spd ->
                    val gy = (h * 0.88f - ((spd - 0.1f) / 4.9f) * (h * 0.76f))
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                val sorted = ramp.keyframes.sortedBy { it.timeOffsetUs }
                if (sorted.size >= 2) {
                    val curvePath = Path()
                    val fillPath = Path()

                    val points = sorted.map { kf ->
                        val px = (kf.timeOffsetUs.toFloat() / durationFloat).coerceIn(0f, 1f) * w
                        val py = (h * 0.88f - ((kf.speed - 0.1f) / 4.9f) * (h * 0.76f)).coerceIn(12f, h - 12f)
                        Offset(px, py)
                    }

                    curvePath.moveTo(points[0].x, points[0].y)
                    fillPath.moveTo(points[0].x, h)
                    fillPath.lineTo(points[0].x, points[0].y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx1 = p0.x + (p1.x - p0.x) * 0.5f
                        val cy1 = p0.y
                        val cx2 = p0.x + (p1.x - p0.x) * 0.5f
                        val cy2 = p1.y
                        curvePath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                        fillPath.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                    }

                    fillPath.lineTo(points.last().x, h)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(
                                ElectricCyan.copy(alpha = 0.28f),
                                VibrantViolet.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )

                    drawPath(
                        path = curvePath,
                        brush = Brush.horizontalGradient(listOf(ElectricCyan, VibrantViolet)),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    points.forEachIndexed { idx, pt ->
                        val isSel = (idx == selectedNodeIndex)
                        if (isSel) {
                            drawCircle(ElectricCyan.copy(alpha = 0.45f), radius = 10.dp.toPx(), center = pt)
                        }
                        drawCircle(if (isSel) ElectricCyan else Color.White, radius = 5.dp.toPx(), center = pt)
                        drawCircle(ObsidianBackground, radius = 2.dp.toPx(), center = pt)
                    }
                }
            }

            Text("1.0x Normal", color = LuminescentAmber.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp))
            Text("4.0x Fast", color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
            Text("0.2x Slow", color = VibrantViolet, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
        }

        Spacer(Modifier.height(12.dp))

        // Preset Curves Row with illustrated mini curves
        Text("Velocity Curve Presets", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { (pName, pPts) ->
                val isSel = (selectedPreset == pName)
                Column(
                    modifier = Modifier
                        .width(78.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) ObsidianSurfaceHighest else ObsidianSurface)
                        .border(1.5.dp, if (isSel) ElectricCyan else ObsidianBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            selectedPreset = pName
                            if (pPts.isNotEmpty()) {
                                val dur = clipDurationUs.coerceAtLeast(1_000_000L)
                                val newKfs = pPts.map { (rx, sp) ->
                                    SpeedKeyframe(
                                        timeOffsetUs = (rx * dur).toLong().coerceIn(0L, dur),
                                        speed = sp
                                    )
                                }
                                ramp = SpeedRamp(keyframes = newKfs.sortedBy { it.timeOffsetUs })
                                selectedNodeIndex = 0
                            }
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianSurfaceLowest),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            val pw = size.width
                            val ph = size.height
                            val miniPath = Path()
                            when (pName) {
                                "Hero Ramp" -> {
                                    miniPath.moveTo(0f, ph * 0.7f)
                                    miniPath.cubicTo(pw * 0.3f, ph * 0.1f, pw * 0.5f, ph * 0.9f, pw, ph * 0.5f)
                                }
                                "Bullet Time" -> {
                                    miniPath.moveTo(0f, ph * 0.2f)
                                    miniPath.lineTo(pw * 0.3f, ph * 0.2f)
                                    miniPath.cubicTo(pw * 0.4f, ph * 0.9f, pw * 0.6f, ph * 0.9f, pw * 0.7f, ph * 0.2f)
                                    miniPath.lineTo(pw, ph * 0.2f)
                                }
                                "Flash In" -> {
                                    miniPath.moveTo(0f, ph * 0.1f)
                                    miniPath.cubicTo(pw * 0.3f, ph * 0.2f, pw * 0.6f, ph * 0.8f, pw, ph * 0.8f)
                                }
                                "Jump Cut" -> {
                                    miniPath.moveTo(0f, ph * 0.8f)
                                    miniPath.lineTo(pw * 0.4f, ph * 0.8f)
                                    miniPath.lineTo(pw * 0.5f, ph * 0.1f)
                                    miniPath.lineTo(pw * 0.6f, ph * 0.8f)
                                    miniPath.lineTo(pw, ph * 0.8f)
                                }
                                else -> {
                                    miniPath.moveTo(0f, ph * 0.5f)
                                    miniPath.cubicTo(pw * 0.4f, ph * 0.1f, pw * 0.6f, ph * 0.9f, pw, ph * 0.5f)
                                }
                            }
                            drawPath(miniPath, if (isSel) ElectricCyan else Color.White.copy(alpha = 0.6f), style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pName,
                        color = if (isSel) ElectricCyan else Color.White,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // AI & Audio Switches
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Optical Flow Slow-Mo (AI Smooth)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Synthesizes high frame rate interpolation", color = TextSecondary, fontSize = 10.sp)
            }
            Switch(
                checked = opticalFlowEnabled,
                onCheckedChange = { opticalFlowEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ElectricCyan)
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Preserve Audio Pitch", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Keeps pitch natural across velocity changes", color = TextSecondary, fontSize = 10.sp)
            }
            Switch(
                checked = pitchPreserveEnabled,
                onCheckedChange = { pitchPreserveEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibrantViolet)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Keyframe Points Management
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val dur = clipDurationUs.coerceAtLeast(1_000_000L)
                    val midTime = dur / 2L
                    val newKf = SpeedKeyframe(timeOffsetUs = midTime, speed = 1.8f)
                    val updated = (ramp.keyframes + newKf).sortedBy { it.timeOffsetUs }
                    ramp = ramp.copy(keyframes = updated)
                    selectedNodeIndex = updated.indexOf(newKf)
                    selectedPreset = "Custom"
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Node", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = {
                    val idx = selectedNodeIndex
                    if (idx != null && ramp.keyframes.size > 2 && idx in ramp.keyframes.indices) {
                        val updated = ramp.keyframes.toMutableList().also { it.removeAt(idx) }
                        ramp = ramp.copy(keyframes = updated)
                        selectedNodeIndex = null
                    }
                },
                enabled = (selectedNodeIndex != null && ramp.keyframes.size > 2),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete Node", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = {
                    val dur = clipDurationUs.coerceAtLeast(1_000_000L)
                    ramp = SpeedRamp(
                        keyframes = listOf(
                            SpeedKeyframe(timeOffsetUs = 0L, speed = 1.0f),
                            SpeedKeyframe(timeOffsetUs = dur, speed = 1.0f)
                        )
                    )
                    selectedPreset = "Custom"
                    selectedNodeIndex = null
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Text("Reset 1.0x", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                onApply(ramp)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(ElectricCyan, VibrantViolet))),
                contentAlignment = Alignment.Center
            ) {
                Text("Apply Velocity Curve", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
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

// ── Overlay Types & Cards for Real Asset Previews ────────────────────
enum class OverlayPresetType {
    FRAME, SKY, STUDIO_BG, TITLE
}

data class OverlayPresetItem(
    val title: String,
    val subtitle: String,
    val assetPath: String,
    val type: OverlayPresetType
)

@Composable
fun OverlayPresetCard(
    item: OverlayPresetItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onApply: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val thumbBmp = remember(item.assetPath) {
        com.clipcut.ui.util.AssetImageHelper.loadThumbnail(ctx, item.assetPath, 260, 160)
    }

    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AccentPrimary.copy(alpha = 0.22f) else Surface2)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AccentPrimary else HairlineBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect)
            .padding(6.dp)
    ) {
        Box(
            modifier = if (item.type == OverlayPresetType.FRAME) {
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2C3E50), Color(0xFF0F172A))
                        )
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F131A))
            },
            contentAlignment = Alignment.Center
        ) {
            if (item.type == OverlayPresetType.FRAME) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1E3A8A), Color(0xFF065F46))
                            ),
                            RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "VIDEO",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (thumbBmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbBmp,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (item.type == OverlayPresetType.FRAME || item.type == OverlayPresetType.TITLE) {
                        androidx.compose.ui.layout.ContentScale.Fit
                    } else {
                        androidx.compose.ui.layout.ContentScale.Crop
                    }
                )
            } else {
                Text(
                    text = when (item.type) {
                        OverlayPresetType.FRAME -> "🎞️"
                        OverlayPresetType.SKY -> "🌤️"
                        OverlayPresetType.STUDIO_BG -> "🎙️"
                        OverlayPresetType.TITLE -> "🎬"
                    },
                    fontSize = 22.sp
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (item.type) {
                        OverlayPresetType.FRAME -> "FRAME"
                        OverlayPresetType.SKY -> "SKY"
                        OverlayPresetType.STUDIO_BG -> "STUDIO"
                        OverlayPresetType.TITLE -> "TITLE"
                    },
                    color = AccentPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            text = item.subtitle,
            color = TextSecondary,
            fontSize = 9.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) AccentPrimary else Surface3)
                .clickable(onClick = onApply)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSelected) "Selected ✓" else "+ Apply",
                color = if (isSelected) DarkBackground else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
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
    onOpenFingerPaintStudio: (String) -> Unit = {},
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
                onAddOverlay(OverlayItem(text = text, timelineStartUs = initialPlayheadUs, timelineEndUs = initialPlayheadUs + 5_000_000L))
                text = ""
            }
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Add PiP Video/Image", onClick = onOpenPiP, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))

        // ── Real Overlay Presets Data ──
        val framePresets = remember {
            listOf(
                OverlayPresetItem("Camera REC 4K", "HUD Viewfinder", "overlays/frames/Camera.png", OverlayPresetType.FRAME),
                OverlayPresetItem("Smartphone Bezel", "Phone Frame", "overlays/frames/Phone.png", OverlayPresetType.FRAME),
                OverlayPresetItem("Polaroid Instant", "Vintage Film Border", "overlays/frames/Photo.png", OverlayPresetType.FRAME),
                OverlayPresetItem("Romantic Heart", "Heart Mask Vignette", "overlays/frames/Heart.png", OverlayPresetType.FRAME),
                OverlayPresetItem("Circular Lens", "Iris Vignette Mask", "overlays/frames/Circular.png", OverlayPresetType.FRAME),
                OverlayPresetItem("Water Ripple", "Organic Caustics", "overlays/frames/Water.png", OverlayPresetType.FRAME),
                OverlayPresetItem("Film Box 1:1", "Square Film Sprocket", "overlays/frames/Square.png", OverlayPresetType.FRAME),
                OverlayPresetItem("Cinemascope 2.39", "Letterbox Movie Bars", "overlays/frames/Rectangle.png", OverlayPresetType.FRAME)
            )
        }

        val skyPresets = remember {
            listOf(
                OverlayPresetItem("Clear Azure Sky", "Sunny Day Blue", "overlays/sky/PRESET_SYK_1.png", OverlayPresetType.SKY),
                OverlayPresetItem("Sunset Warm Glow", "Golden Dusk Clouds", "overlays/sky/PRESET_SYK_2.png", OverlayPresetType.SKY),
                OverlayPresetItem("Golden Dusk", "Warm Evening Sun", "overlays/sky/PRESET_SYK_3.png", OverlayPresetType.SKY),
                OverlayPresetItem("Purple Twilight", "Violet Sunset", "overlays/sky/PRESET_SYK_4.png", OverlayPresetType.SKY),
                OverlayPresetItem("Night Sky Stars", "Starlit Midnight", "overlays/sky/PRESET_SYK_5.png", OverlayPresetType.SKY),
                OverlayPresetItem("Storm Drama", "Moody Storm Clouds", "overlays/sky/PRESET_SYK_6.png", OverlayPresetType.SKY),
                OverlayPresetItem("Milky Way Galaxy", "Deep Cosmos", "overlays/sky/PRESET_SYK_7.png", OverlayPresetType.SKY),
                OverlayPresetItem("Deep Crimson", "Fiery Red Sunset", "overlays/sky/PRESET_SYK_8.png", OverlayPresetType.SKY),
                OverlayPresetItem("Aurora Borealis", "Polar Green Glow", "overlays/sky/PRESET_SYK_9.png", OverlayPresetType.SKY),
                OverlayPresetItem("Cosmic Horizon", "Alien Atmosphere", "overlays/sky/PRESET_SYK_10.png", OverlayPresetType.SKY)
            )
        }

        val bgPresets = remember {
            listOf(
                OverlayPresetItem("Neon Cyberpunk", "Futuristic Street", "overlays/backgrounds/human_seg_1.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Podcast Studio", "Acoustic Creator Room", "overlays/backgrounds/human_seg_2.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Minimal Loft", "Modern Apartment", "overlays/backgrounds/human_seg_3.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Modern Office", "High-Rise Executive", "overlays/backgrounds/human_seg_4.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Tropical Beach", "Sunny Ocean Coast", "overlays/backgrounds/human_seg_5.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Sci-Fi Chamber", "Starship Corridor", "overlays/backgrounds/human_seg_6.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Art Gallery", "Exhibition Hall", "overlays/backgrounds/human_seg_7.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Cozy Cafe", "Warm Coffee House", "overlays/backgrounds/human_seg_8.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Sunset Rooftop", "City Skyline Lounge", "overlays/backgrounds/human_seg_9.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Nightclub VIP", "Neon Dance Club", "overlays/backgrounds/human_seg_10.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Tokyo Street", "Lantern Alleyway", "overlays/backgrounds/human_seg_11.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Abstract Studio", "Pastel Minimal Stage", "overlays/backgrounds/human_seg_12.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Industrial Space", "Concrete Studio", "overlays/backgrounds/human_seg_13.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Alpine Mountain", "Snowy Peak Vista", "overlays/backgrounds/human_seg_14.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Cinema Stage", "Theater Spotlight", "overlays/backgrounds/human_seg_15.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Golden Balcony", "Sunset Terrace", "overlays/backgrounds/human_seg_16.webp", OverlayPresetType.STUDIO_BG),
                OverlayPresetItem("Newsroom Desk", "Broadcast TV Studio", "overlays/backgrounds/human_seg_17.webp", OverlayPresetType.STUDIO_BG)
            )
        }

        val titlePresets = remember {
            listOf(
                OverlayPresetItem("Minimal Accent", "Clean Headline", "overlays/titles/Big_Title_Pack_Title_04.png", OverlayPresetType.TITLE),
                OverlayPresetItem("Lower Third", "Broadcast Tag", "overlays/titles/Big_Title_Pack_Title_14.png", OverlayPresetType.TITLE),
                OverlayPresetItem("Impact Title", "Bold Heavy Stamp", "overlays/titles/Big_Title_Pack_Title_18.png", OverlayPresetType.TITLE),
                OverlayPresetItem("Channel Stamp", "Creator Watermark", "overlays/titles/Social_Media_Watermark_Pack_Title_01.png", OverlayPresetType.TITLE),
                OverlayPresetItem("Vlog Lifestyle", "Aesthetic Title", "overlays/titles/Vlog_Title_Pack_Title_08.png", OverlayPresetType.TITLE),
                OverlayPresetItem("Cinematic Intro", "Film Opener", "overlays/titles/Vlog_Title_Pack_Title_15.png", OverlayPresetType.TITLE)
            )
        }

        var inspectedPreset by remember { mutableStateOf<OverlayPresetItem?>(framePresets.first()) }

        val applyPresetOverlay = { preset: OverlayPresetItem ->
            val assetUri = "asset://${preset.assetPath}"
            val isSky = preset.type == OverlayPresetType.SKY
            onAddOverlay(
                OverlayItem(
                    text = assetUri,
                    imageUri = assetUri,
                    timelineStartUs = 0L,
                    timelineEndUs = if (totalDurationUs > 0L) totalDurationUs else 60_000_000L,
                    normalizedX = 0.5f,
                    normalizedY = if (isSky) 0.26f else 0.5f,
                    scale = 1.0f
                )
            )
        }

        // ── 1. Interactive Live Overlay Preview Monitor ──
        if (inspectedPreset != null) {
            val preset = inspectedPreset!!
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val previewBmp = remember(preset.assetPath) {
                com.clipcut.ui.util.AssetImageHelper.loadThumbnail(ctx, preset.assetPath, 520, 320)
            }

            Text("Live Overlay Preview Monitor", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C1017))
                    .border(1.dp, AccentPrimary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            ) {
                if (preset.type == OverlayPresetType.FRAME) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "▶ SAMPLE FOOTAGE CANVAS",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (preset.type == OverlayPresetType.SKY) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color(0xFF1E293B),
                                    0.55f to Color(0xFF0F172A),
                                    1.0f to Color(0xFF020617)
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .background(Color(0xFF090D16))
                        )
                    }
                }

                if (previewBmp != null) {
                    if (preset.type == OverlayPresetType.SKY) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBmp,
                            contentDescription = preset.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.68f)
                                .align(Alignment.TopCenter),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (preset.type == OverlayPresetType.STUDIO_BG) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBmp,
                            contentDescription = preset.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color(0xCC101319), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF00E676), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "👤 Green Screen Studio Backdrop",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        androidx.compose.foundation.Image(
                            bitmap = previewBmp,
                            contentDescription = preset.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xDD000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = when (preset.type) {
                                OverlayPresetType.FRAME -> "CINEMATIC CAMERA FRAME"
                                OverlayPresetType.SKY -> "ATMOSPHERIC SKY REPLACEMENT"
                                OverlayPresetType.STUDIO_BG -> "VIRTUAL STUDIO (CHROMA KEY)"
                                OverlayPresetType.TITLE -> "MOTION TITLE OVERLAY"
                            },
                            color = AccentPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xF2090D14))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = preset.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = preset.subtitle,
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentPrimary)
                            .clickable { applyPresetOverlay(preset) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ Apply to Project",
                            color = DarkBackground,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        // ── 2. Cinematic Frames & Camera Masks ──
        Text("Cinematic Frames & Camera Masks (${framePresets.size} Presets)", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Real camera viewfinders, phones, polaroids & cinema letterbox masks", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(framePresets) { item ->
                OverlayPresetCard(
                    item = item,
                    isSelected = inspectedPreset == item,
                    onSelect = { inspectedPreset = item },
                    onApply = { applyPresetOverlay(item) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 3. Sky Replacement Overlays ──
        Text("Atmospheric Sky Replacement (${skyPresets.size} Presets)", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Photorealistic sunsets, night stars, auroras, galaxy & clear skies", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(skyPresets) { item ->
                OverlayPresetCard(
                    item = item,
                    isSelected = inspectedPreset == item,
                    onSelect = { inspectedPreset = item },
                    onApply = { applyPresetOverlay(item) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 4. AI Studio Cutout Backgrounds ──
        Text("AI Virtual Studios & Backdrops (${bgPresets.size} Scenes)", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Virtual green-screen studios, neon Cyberpunk, lofts, cafes & stages", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF10B981).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color(0xFF10B981)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val uri = inspectedPreset?.takeIf { it.type == OverlayPresetType.STUDIO_BG }?.assetPath
                        ?: "overlays/backgrounds/human_seg_2.webp"
                    onOpenFingerPaintStudio(uri)
                }
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Brush, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Paint Virtual Studio with Finger", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("No green screen? Swipe your finger across your room to apply the studio directly", color = TextSecondary, fontSize = 10.sp)
                }
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(bgPresets) { item ->
                OverlayPresetCard(
                    item = item,
                    isSelected = inspectedPreset == item,
                    onSelect = { inspectedPreset = item },
                    onApply = { applyPresetOverlay(item) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 5. Cinematic Motion Titles & Lower Thirds ──
        Text("Cinematic Motion Titles & Lower Thirds (${titlePresets.size} Presets)", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Professional video lower thirds, lifestyle headers & channel stamps", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(titlePresets) { item ->
                OverlayPresetCard(
                    item = item,
                    isSelected = inspectedPreset == item,
                    onSelect = { inspectedPreset = item },
                    onApply = { applyPresetOverlay(item) }
                )
            }
        }
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
    paintCensorStyle: String = "BLACKOUT",
    onPaintCensorStyleChange: ((String) -> Unit)? = null,
    onUndoPaintStroke: (() -> Unit)? = null,
    onClearPaintStrokes: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSubTab by remember { mutableStateOf(BlurSubTab.PAINT) }
    var selectedTrackingMode by remember { mutableStateOf(com.clipcut.core.feature.TrackingMode.INSHOT_SOT) }

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
                PrimaryButton(
                    text = "🖌️ Start Painting on Video",
                    onClick = { onTogglePainting?.invoke(true) },
                    icon = Icons.Filled.Edit,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text("Finger-Paint Settings", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Select mode and brush size below, then tap 'Start Painting' to draw directly on the video player with zero obstruction.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                // Paint mode toggle: All 7 Censor Styles
                Text("Censor Style", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val censorStyles = listOf(
                        "BLACKOUT" to "⬛ Solid Blackout",
                        "MOSAIC" to "▦ Pixel Mosaic",
                        "FROSTED" to "🌫️ Frosted Blur",
                        "BLUR" to "💧 Soft Blur",
                        "REDACTED" to "🪧 Top Secret Redacted",
                        "CAUTION" to "🚧 Hazard Caution",
                        "WHITEOUT" to "⚪ Solid Whiteout"
                    )
                    censorStyles.forEach { (styleKey, styleLabel) ->
                        val isSel = paintCensorStyle.equals(styleKey, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) AccentPrimary else Surface3)
                                .clickable {
                                    onPaintCensorStyleChange?.invoke(styleKey)
                                    onPaintIsBlackoutChange?.invoke(styleKey == "BLACKOUT" || styleKey == "REDACTED" || styleKey == "CAUTION")
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
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

// ── ExportDialog (Stitch Screen 5: 4K Master Render Hub) ─────────────────
@Composable
fun ExportDialog(
    projectAspectRatio: AspectRatio,
    exportState: com.clipcut.ui.viewmodel.ExportState,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit,
    onViewLogsClick: () -> Unit,
    projectTitle: String = "Master Video",
    projectDurationUs: Long = 10_000_000L
) {
    var resolution by remember { mutableStateOf(ExportResolution.RES_1080P) }
    var codec by remember { mutableStateOf(ExportCodec.H264) }
    var fps by remember { mutableStateOf(30) }
    var currentAspect by remember { mutableStateOf(projectAspectRatio) }
    var bitrateMbps by remember { mutableStateOf(16) }
    var activeSocialPreset by remember { mutableStateOf<String?>("TikTok") }
    var gpuAcceleration by remember { mutableStateOf(true) }
    var hdr10Enabled by remember { mutableStateOf(false) }
    var watermarkEnabled by remember { mutableStateOf(false) }

    val durSec = (projectDurationUs.toFloat() / 1_000_000f).coerceAtLeast(1f)
    val estimatedMb = (durSec * (bitrateMbps * 1_000_000f + 192_000f) / 8f / (1024f * 1024f)).coerceAtLeast(0.5f)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.2.dp, ObsidianBorderVariant),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SheetHeader("Master Export Studio", onDismiss)
                Spacer(Modifier.height(10.dp))

                // Video Master Summary Card (Stitch Screen 5)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurfaceLowest)
                        .border(1.dp, Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.5f), VibrantViolet.copy(alpha = 0.5f))), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 16:9 Thumbnail Box
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(VibrantViolet.copy(alpha = 0.4f), ElectricCyan.copy(alpha = 0.3f))))
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                projectTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${resolution.label} · ${fps}fps",
                                    color = ElectricCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(TextSecondary))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "~${String.format(java.util.Locale.US, "%.1f", estimatedMb)} MB",
                                    color = LuminescentAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text("One-Tap Social Targets", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        Triple("TikTok", "📱 TikTok / Reels (9:16 60fps)", Pair(AspectRatio.R_9_16, 60)),
                        Triple("YouTube", "📺 YouTube 4K (16:9 60fps)", Pair(AspectRatio.R_16_9, 60)),
                        Triple("IG Square", "📸 Instagram Post (1:1 30fps)", Pair(AspectRatio.R_1_1, 30)),
                        Triple("WhatsApp", "💬 WhatsApp Quick (720p)", Pair(AspectRatio.R_9_16, 30))
                    )
                    items(presets) { (pKey, pLabel, pConfig) ->
                        val isSel = (activeSocialPreset == pKey)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) ObsidianSurfaceHighest else ObsidianSurfaceLow)
                                .border(1.5.dp, if (isSel) ElectricCyan else ObsidianBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    activeSocialPreset = pKey
                                    currentAspect = pConfig.first
                                    fps = pConfig.second
                                    when (pKey) {
                                        "YouTube" -> {
                                            resolution = ExportResolution.RES_4K
                                            bitrateMbps = 35
                                        }
                                        "WhatsApp" -> {
                                            resolution = ExportResolution.RES_720P
                                            bitrateMbps = 6
                                        }
                                        else -> {
                                            resolution = ExportResolution.RES_1080P
                                            bitrateMbps = 18
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                pLabel,
                                color = if (isSel) ElectricCyan else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Resolution & Canvas", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ExportResolution.values().forEach { r ->
                        val isSel = (resolution == r)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) ObsidianSurfaceHighest else ObsidianSurfaceLow)
                                .border(1.5.dp, if (isSel) ElectricCyan else ObsidianBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    resolution = r
                                    activeSocialPreset = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    r.label,
                                    color = if (isSel) ElectricCyan else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${r.width}×${r.height}",
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Frame Rate", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        24 to "24 Cinema",
                        30 to "30 Normal",
                        60 to "60 Smooth",
                        120 to "120 Ultra"
                    ).forEach { (fVal, fLabel) ->
                        val isSel = (fps == fVal)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) ObsidianSurfaceHighest else ObsidianSurfaceLow)
                                .border(1.5.dp, if (isSel) ElectricCyan else ObsidianBorder, RoundedCornerShape(10.dp))
                                .clickable { fps = fVal }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                fLabel,
                                color = if (isSel) ElectricCyan else Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("Format & Video Codec", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ExportCodec.H264 to "H.264 (Universal MP4)",
                        ExportCodec.H265 to "HEVC / H.265 (Pro High-Efficiency)"
                    ).forEach { (cVal, cLabel) ->
                        val isSel = (codec == cVal)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) ObsidianSurfaceHighest else ObsidianSurfaceLow)
                                .border(1.5.dp, if (isSel) ElectricCyan else ObsidianBorder, RoundedCornerShape(10.dp))
                                .clickable { codec = cVal }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                cLabel,
                                color = if (isSel) ElectricCyan else Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Dynamic Bitrate Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Master Bitrate Quality", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("$bitrateMbps Mbps (VBR)", color = LuminescentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = bitrateMbps.toFloat(),
                    onValueChange = { bitrateMbps = it.toInt() },
                    valueRange = 4f..60f,
                    modifier = Modifier.fillMaxWidth().height(26.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = ObsidianBorder
                    )
                )

                Spacer(Modifier.height(10.dp))

                // Mastering Options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurfaceLow)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Hardware GPU Acceleration", color = Color.White, fontSize = 11.sp)
                    Switch(
                        checked = gpuAcceleration,
                        onCheckedChange = { gpuAcceleration = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ElectricCyan)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurfaceLow)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("10-Bit Color Profile (HDR10)", color = Color.White, fontSize = 11.sp)
                    Switch(
                        checked = hdr10Enabled,
                        onCheckedChange = { hdr10Enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibrantViolet)
                    )
                }

                Spacer(Modifier.height(14.dp))

                when (exportState) {
                    is com.clipcut.ui.viewmodel.ExportState.Progress -> {
                        LinearProgressIndicator(
                            progress = { exportState.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = ElectricCyan,
                            trackColor = ObsidianBorder
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(exportState.status, color = TextSecondary, fontSize = 11.sp)
                            Text("${(exportState.progress * 100).toInt()}%", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("Cancel Export", onClick = onCancelExport, modifier = Modifier.fillMaxWidth())
                    }
                    is com.clipcut.ui.viewmodel.ExportState.Success -> {
                        Text("Export Complete! Saved to Gallery.", color = EmeraldWave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(exportState.galleryPath, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldWave)
                        ) {
                            Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    is com.clipcut.ui.viewmodel.ExportState.Error -> {
                        Text("Export Error: ${exportState.message}", color = Color(0xFFFF5252), fontSize = 11.sp)
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
                        Button(
                            onClick = {
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
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.horizontalGradient(listOf(ElectricCyan, VibrantViolet))),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Export Master Video", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
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
    var opticalFlow by remember { mutableStateOf(true) }
    var pitchLock by remember { mutableStateOf(true) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Playback Speed", onDismiss)
        Spacer(Modifier.height(8.dp))

        // Large Cyberpunk Speed Multiplier Readout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurfaceLowest)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${String.format(java.util.Locale.US, "%.2f", s)}x",
                    color = ElectricCyan,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = when {
                        s < 0.5f -> "Ultra Slow Motion · Optical Flow"
                        s < 1.0f -> "Smooth Slow Motion"
                        s == 1.0f -> "Normal Speed (1.00x)"
                        s <= 2.0f -> "Fast Motion (2x)"
                        s <= 5.0f -> "Timelapse Motion"
                        else -> "Hyperlapse Velocity"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Quick Speed Presets", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                0.2f to "0.2x",
                0.5f to "0.5x",
                1.0f to "1.0x",
                2.0f to "2.0x",
                4.0f to "4.0x",
                8.0f to "8.0x"
            ).forEach { (speedVal, label) ->
                val isSelected = (s == speedVal)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) ObsidianSurfaceHighest else ObsidianSurface)
                        .border(1.5.dp, if (isSelected) ElectricCyan else ObsidianBorder, RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { s = speedVal }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        color = if (isSelected) ElectricCyan else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SheetSlider("Fine Speed Rate: ${String.format(java.util.Locale.US, "%.2f", s)}x", s, 0.1f..10f) { s = it }

        Spacer(Modifier.height(10.dp))

        // AI & Pitch Options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Optical Flow Smooth AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Synthesize in-between frames for buttery motion", color = TextSecondary, fontSize = 10.sp)
            }
            Switch(
                checked = opticalFlow,
                onCheckedChange = { opticalFlow = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ElectricCyan)
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Preserve Pitch", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Keep voices and music in natural tone", color = TextSecondary, fontSize = 10.sp)
            }
            Switch(
                checked = pitchLock,
                onCheckedChange = { pitchLock = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibrantViolet)
            )
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onApply(s) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(ElectricCyan, VibrantViolet))),
                contentAlignment = Alignment.Center
            ) {
                Text("Apply Speed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
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

    var hueSliderVal by remember { mutableFloatStateOf(0f) }

    val textColors = listOf(
        // Monochrome & Neutrals
        0xFFFFFFFFL to Color(0xFFFFFFFF), // Pure White
        0xFFF1F5F9L to Color(0xFFF1F5F9), // Snow Silver
        0xFFCBD5E1L to Color(0xFFCBD5E1), // Cool Platinum
        0xFF94A3B8L to Color(0xFF94A3B8), // Slate Gray
        0xFF475569L to Color(0xFF475569), // Dark Slate
        0xFF0F172AL to Color(0xFF0F172A), // Midnight Obsidian
        0xFF000000L to Color(0xFF000000), // Pitch Black
        // Neons & Cyberpunk
        0xFF00F2FEL to Color(0xFF00F2FE), // Electric Cyan
        0xFF00F5D4L to Color(0xFF00F5D4), // Neon Mint
        0xFFA6FF00L to Color(0xFFA6FF00), // Acid Lime
        0xFF00FF66L to Color(0xFF00FF66), // Toxic Green
        0xFFFFEA00L to Color(0xFFFFEA00), // Neon Yellow
        0xFFFFD166L to Color(0xFFFFD166), // Gold Amber
        0xFFFF8500L to Color(0xFFFF8500), // Bright Orange
        0xFFFF5722L to Color(0xFFFF5722), // Tangerine
        0xFFEF4444L to Color(0xFFEF4444), // Crimson Red
        0xFFFF2A6DL to Color(0xFFFF2A6D), // Hot Pink
        0xFFFF007FL to Color(0xFFFF007F), // Cyberpunk Rose
        0xFFFF1493L to Color(0xFFFF1493), // Deep Magenta
        0xFF9D4EDDL to Color(0xFF9D4EDD), // Electric Purple
        0xFF7C3AEDL to Color(0xFF7C3AED), // Hyper Violet
        // Oceanic & Blues
        0xFF2563EBL to Color(0xFF2563EB), // Royal Blue
        0xFF0070F3L to Color(0xFF0070F3), // Azure Blue
        0xFF4EA8DEL to Color(0xFF4EA8DE), // Sky Blue
        0xFF38BDF8L to Color(0xFF38BDF8), // Ice Blue
        0xFF2DD4BFL to Color(0xFF2DD4BF), // Aquamarine
        0xFF10B981L to Color(0xFF10B981), // Emerald Green
        0xFF059669L to Color(0xFF059669), // Forest Pine
        // Pastels & Soft Tones
        0xFFFEF08AL to Color(0xFFFEF08A), // Pastel Butter
        0xFFA7F3D0L to Color(0xFFA7F3D0), // Pastel Mint
        0xFFBAE6FDL to Color(0xFFBAE6FD), // Pastel Sky
        0xFFDDD6FEL to Color(0xFFDDD6FE), // Pastel Lavender
        0xFFE879F9L to Color(0xFFE879F9), // Pastel Lilac
        0xFFFECDD3L to Color(0xFFFECDD3), // Pastel Rose
        0xFFF5EBE0L to Color(0xFFF5EBE0), // Warm Cream
        0xFFD4A373L to Color(0xFFD4A373)  // Vintage Bronze
    )

    val stylePresets = listOf(
        "None" to Pair(28f, 0x00000000L),
        "Headline" to Pair(36f, 0x00000000L),
        "Caption" to Pair(22f, 0xB3000000L),
        "Subtitle" to Pair(26f, 0x801A1A24L),
        "Neon Tag" to Pair(30f, 0xCCFF2A6DL),
        "Dark Box" to Pair(24f, 0xE6000000L),
        "Crimson Tag" to Pair(26f, 0xCCEF4444L),
        "Emerald Pill" to Pair(26f, 0xCC10B981L)
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader(if (initialOverlay != null) "Edit Text Overlay" else "Text Overlay Studio", onDismiss)
        Spacer(Modifier.height(10.dp))

        // ── LIVE WYSIWYG PREVIEW CARD ──
        val previewFontFamily = remember(selectedFont, ctx, text) {
            com.clipcut.ui.util.FontHelper.getFontFamily(ctx, selectedFont, text)
        }
        val previewBg = if (selectedBgColor != 0L) Color(selectedBgColor) else Color.Transparent

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1B1E2A), Color(0xFF0F111A))
                    )
                )
                .border(1.5.dp, AccentPrimary.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Live rendered text with active font, color, background, outline, shadow
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(previewBg)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text.ifBlank { "Type text to preview font..." },
                    color = Color(selectedColor),
                    fontSize = (size * 0.72f).coerceIn(16f, 32f).sp,
                    fontFamily = previewFontFamily,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = if (hasShadow) androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = androidx.compose.ui.geometry.Offset(3f, 3f),
                            blurRadius = 6f
                        ) else null
                    ),
                    maxLines = 2
                )
            }

            // Top-left badges: Active Font & Animation
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentPrimary.copy(alpha = 0.22f))
                        .border(0.5.dp, AccentPrimary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "✦ ${selectedFont.label}",
                        color = AccentPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (selectedAnimation != TextAnimation.NONE) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VibrantViolet.copy(alpha = 0.25f))
                            .border(0.5.dp, VibrantViolet.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            selectedAnimation.displayName,
                            color = Color(0xFFC6BFFF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom-right tag: WYSIWYG indicator
            Text(
                "LIVE PREVIEW",
                color = TextTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Text input field
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("Type text here to preview in all fonts...", color = TextTertiary) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface3, unfocusedContainerColor = Surface3,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = AccentPrimary
            )
        )

        Spacer(Modifier.height(14.dp))

        // ── FONT FAMILY PICKER WITH LIVE CARD PREVIEWS ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Font Family (Tap to test & select)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("${FontFamilyOption.values().size} Fonts", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FontFamilyOption.values().toList()) { font ->
                val isSel = (selectedFont == font)
                val fontFam = remember(font, ctx, text) {
                    com.clipcut.ui.util.FontHelper.getFontFamily(ctx, font, text)
                }
                val sampleText = remember(font, text) {
                    com.clipcut.ui.util.FontHelper.getPreviewSample(font, text)
                }
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) AccentPrimary.copy(alpha = 0.18f) else Surface2)
                        .border(
                            width = if (isSel) 2.dp else 1.dp,
                            color = if (isSel) AccentPrimary else HairlineBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedFont = font }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            font.label,
                            color = if (isSel) AccentPrimary else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = sampleText,
                            color = if (isSel) Color(selectedColor) else Color.White,
                            fontFamily = fontFam,
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                            fontSize = 15.sp,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSel) AccentPrimary else Color.Transparent)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── EXPANDED COLOR PALETTE & RAINBOW HUE SLIDER ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Font Color (36+ Swatches & Custom Rainbow)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(selectedColor))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                )
                Text(
                    "#%06X".format(selectedColor and 0xFFFFFFL),
                    color = AccentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 2-row horizontal scrolling color swatches
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chunked = textColors.chunked(2)
            items(chunked) { pair ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (colorVal, composeColor) ->
                        val isSelected = (selectedColor == colorVal)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(composeColor)
                                .border(
                                    if (isSelected) 2.5.dp else 1.dp,
                                    if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.35f),
                                    CircleShape
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedColor = colorVal
                                }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Custom Rainbow Hue Slider for infinite colors
        Text("Continuous Hue Spectrum", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        val rainbowBrush = remember {
            Brush.horizontalGradient(
                listOf(
                    Color.Red, Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(rainbowBrush)
                .border(1.dp, HairlineBorder, RoundedCornerShape(9.dp))
        )
        Slider(
            value = hueSliderVal,
            onValueChange = { h ->
                hueSliderVal = h
                val hsvColor = Color.hsv(h, 1.0f, 1.0f)
                val argb = (0xFF000000L or ((hsvColor.red * 255).toLong() shl 16) or ((hsvColor.green * 255).toLong() shl 8) or (hsvColor.blue * 255).toLong())
                selectedColor = argb
            },
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = Color(selectedColor),
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-14).dp)
        )

        // ── ENTRANCE ANIMATION ──
        Text("Entrance Animation", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TextAnimation.values().toList()) { anim ->
                EditorFilterChip(anim.displayName, selectedAnimation == anim) { selectedAnimation = anim }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── TEXT STYLE PRESETS ──
        Text("Text Style & Background Presets", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

        // Font Size slider
        SheetSlider("Font Size: ${size.toInt()}sp", size, 14f..80f) { size = it }

        Spacer(Modifier.height(8.dp))

        // Styling checkboxes: Bold, Italic, Shadow, Outline
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

        Spacer(Modifier.height(14.dp))

        PrimaryButton(if (initialOverlay != null) "Update Text Overlay" else "Add Text to Timeline", onClick = {
            if (text.isNotBlank()) {
                val startUs = initialOverlay?.timelineStartUs ?: playheadUs.coerceAtLeast(0L)
                val endUs = initialOverlay?.timelineEndUs ?: ((startUs + 5_000_000L).let { if (totalDurationUs > startUs) it.coerceAtMost(totalDurationUs) else it }.coerceAtLeast(startUs + 1_000_000L))
                val targetItem = (initialOverlay ?: OverlayItem(
                    normalizedX = 0.5f,
                    normalizedY = 0.5f,
                    scale = 1.0f
                )).copy(
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
        val categories = listOf("Trending", "InShot Emojis", "Frames & Masks", "Sky Overlays", "Studio Backgrounds", "Motion Titles", "Censor Stamps", "Reels & Social", "Arrows & Pointers", "Vlog & Life", "Retro Y2K", "Aesthetic", "Emoji", "Badges")
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
            "Frames & Masks" -> listOf(
                "asset://overlays/frames/Camera.png",
                "asset://overlays/frames/Phone.png",
                "asset://overlays/frames/Photo.png",
                "asset://overlays/frames/Heart.png",
                "asset://overlays/frames/Circular.png",
                "asset://overlays/frames/Water.png",
                "asset://overlays/frames/Square.png",
                "asset://overlays/frames/Rectangle.png"
            )
            "Sky Overlays" -> listOf(
                "asset://overlays/sky/PRESET_SYK_1.png",
                "asset://overlays/sky/PRESET_SYK_2.png",
                "asset://overlays/sky/PRESET_SYK_3.png",
                "asset://overlays/sky/PRESET_SYK_4.png",
                "asset://overlays/sky/PRESET_SYK_5.png",
                "asset://overlays/sky/PRESET_SYK_6.png",
                "asset://overlays/sky/PRESET_SYK_7.png",
                "asset://overlays/sky/PRESET_SYK_8.png",
                "asset://overlays/sky/PRESET_SYK_9.png",
                "asset://overlays/sky/PRESET_SYK_10.png"
            )
            "Studio Backgrounds" -> listOf(
                "asset://overlays/backgrounds/human_seg_1.webp",
                "asset://overlays/backgrounds/human_seg_2.webp",
                "asset://overlays/backgrounds/human_seg_3.webp",
                "asset://overlays/backgrounds/human_seg_4.webp",
                "asset://overlays/backgrounds/human_seg_5.webp",
                "asset://overlays/backgrounds/human_seg_6.webp",
                "asset://overlays/backgrounds/human_seg_7.webp",
                "asset://overlays/backgrounds/human_seg_8.webp",
                "asset://overlays/backgrounds/human_seg_9.webp",
                "asset://overlays/backgrounds/human_seg_10.webp",
                "asset://overlays/backgrounds/human_seg_11.webp",
                "asset://overlays/backgrounds/human_seg_12.webp",
                "asset://overlays/backgrounds/human_seg_13.webp",
                "asset://overlays/backgrounds/human_seg_14.webp",
                "asset://overlays/backgrounds/human_seg_15.webp",
                "asset://overlays/backgrounds/human_seg_16.webp",
                "asset://overlays/backgrounds/human_seg_17.webp"
            )
            "Motion Titles" -> listOf(
                "asset://overlays/titles/Big_Title_Pack_Title_04.png",
                "asset://overlays/titles/Big_Title_Pack_Title_14.png",
                "asset://overlays/titles/Big_Title_Pack_Title_18.png",
                "asset://overlays/titles/Social_Media_Watermark_Pack_Title_01.png",
                "asset://overlays/titles/Vlog_Title_Pack_Title_08.png",
                "asset://overlays/titles/Vlog_Title_Pack_Title_15.png"
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

        val isOverlayCategory = category in listOf("Frames & Masks", "Sky Overlays", "Studio Backgrounds", "Motion Titles")
        val ctx = androidx.compose.ui.platform.LocalContext.current

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isOverlayCategory) 2 else 4),
            modifier = Modifier.height(310.dp).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(glyphs) { g ->
                if (isOverlayCategory && g.startsWith("asset://")) {
                    val assetPath = g.removePrefix("asset://")
                    val isFrame = category == "Frames & Masks"
                    val isSky = category == "Sky Overlays"
                    val isStudio = category == "Studio Backgrounds"
                    val isTitle = category == "Motion Titles"

                    val title = when {
                        isFrame -> assetPath.substringAfterLast("/").removeSuffix(".png").replace("_", " ") + " Viewfinder"
                        isSky -> "Sky Preset #" + assetPath.substringAfterLast("/").removeSuffix(".png").replace("PRESET_SYK_", "")
                        isStudio -> "Studio Backdrop #" + assetPath.substringAfterLast("/").removeSuffix(".webp").replace("human_seg_", "")
                        isTitle -> assetPath.substringAfterLast("/").removeSuffix(".png").replace("_", " ")
                        else -> assetPath.substringAfterLast("/")
                    }
                    val badgeLabel = when {
                        isFrame -> "FRAME"
                        isSky -> "SKY"
                        isStudio -> "STUDIO"
                        isTitle -> "TITLE"
                        else -> "OVERLAY"
                    }

                    val bmp: androidx.compose.ui.graphics.ImageBitmap? = remember(assetPath) {
                        com.clipcut.ui.util.AssetImageHelper.loadThumbnail(ctx, assetPath, 240, 160)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface2)
                            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onPick(g) }
                            .padding(6.dp)
                    ) {
                        val boxModifier = if (isStudio) {
                            Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0D1117))
                        } else if (isSky) {
                            Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF0284C7))))
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF161622))
                        }

                        Box(
                            modifier = boxModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFrame) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                        .background(
                                            Brush.verticalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF065F46))),
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "VIDEO CANVAS",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (bmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp,
                                    contentDescription = title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = if (isFrame || isTitle) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Text(
                                    when {
                                        isFrame -> "🎞️"
                                        isSky -> "🌤️"
                                        isStudio -> "🎙️"
                                        else -> "🎬"
                                    },
                                    fontSize = 20.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(badgeLabel, color = AccentPrimary, fontSize = 7.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Surface3)
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Add to Video", color = AccentPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
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
                            val bmp: androidx.compose.ui.graphics.ImageBitmap? = remember(g) {
                                com.clipcut.ui.util.AssetImageHelper.loadThumbnail(ctx, clean, 100, 100)
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

// ── VN Brand Kit Studio & Logo Maker Sheet ──────────────────────────────
@Composable
fun BrandKitSheet(
    current: com.clipcut.core.model.BrandKitConfig?,
    onApply: (com.clipcut.core.model.BrandKitConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val base = current ?: com.clipcut.core.model.BrandKitConfig()
    var watermarkText by remember { mutableStateOf(base.watermarkText) }
    var tagline by remember { mutableStateOf(base.tagline) }
    var customLogoUri by remember { mutableStateOf(base.logoUri) }
    var selectedColor by remember { mutableLongStateOf(base.brandColor) }
    var posIndex by remember { mutableIntStateOf(base.watermarkPosIndex) }
    var selectedStyle by remember { mutableStateOf(base.logoStyle) }
    var selectedIcon by remember { mutableStateOf(base.iconType) }
    var selectedFont by remember { mutableStateOf(base.fontFamily) }
    var opacity by remember { mutableFloatStateOf(base.opacity) }
    var scale by remember { mutableFloatStateOf(base.watermarkScale) }

    val logoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                // Persist file into app private files to guarantee export read access
                val logoFile = java.io.File(ctx.filesDir, "brand_kit_logo_${System.currentTimeMillis()}.png")
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    logoFile.outputStream().use { output -> input.copyTo(output) }
                }
                customLogoUri = logoFile.absolutePath
            } catch (t: Throwable) {
                customLogoUri = uri.toString()
            }
        }
    }

    val brandColorCompose = Color(selectedColor or 0xFF000000L)

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Brand Kit Studio & Logo Maker", onDismiss)
        Spacer(Modifier.height(6.dp))
        Text(
            "VN Creator Identity: automatically brand every export with custom typography, creator badges, social tags, and logo styling.",
            color = TextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(14.dp))

        // ── 1. Live WYSIWYG Preview Monitor ──
        Text("Live Preview Monitor", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F141C), Color(0xFF1E2530))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            contentAlignment = when (posIndex) {
                1 -> Alignment.TopStart
                2 -> Alignment.TopEnd
                5 -> Alignment.TopCenter
                3 -> Alignment.BottomStart
                4 -> Alignment.BottomEnd
                else -> Alignment.BottomCenter
            }
        ) {
            // Background grid guide lines
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            )

            // Rendered preview badge
            val displayTitle = watermarkText.ifBlank { "ClipCut Creator" }
            val fontFam = remember(selectedFont, ctx, watermarkText) {
                com.clipcut.ui.util.FontHelper.getFontFamily(ctx, selectedFont, watermarkText)
            }

            Box(
                modifier = Modifier
                    .clip(
                        when (selectedStyle) {
                            BrandLogoStyle.NEON_PILL -> RoundedCornerShape(24.dp)
                            BrandLogoStyle.GLASS_CARD -> RoundedCornerShape(10.dp)
                            BrandLogoStyle.CINEMA_STAMP -> RoundedCornerShape(3.dp)
                            BrandLogoStyle.BADGE_CREST -> RoundedCornerShape(8.dp)
                            else -> RoundedCornerShape(6.dp)
                        }
                    )
                    .background(
                        when (selectedStyle) {
                            BrandLogoStyle.NEON_PILL -> Color(0xDD0D1117).copy(alpha = opacity * 0.90f)
                            BrandLogoStyle.GLASS_CARD -> Color(0x991E2430).copy(alpha = opacity * 0.75f)
                            BrandLogoStyle.CINEMA_STAMP -> Color(0xF008080C).copy(alpha = opacity * 0.95f)
                            BrandLogoStyle.BADGE_CREST -> Color(0xDD12141C).copy(alpha = opacity * 0.90f)
                            BrandLogoStyle.MINIMAL_WATERMARK, BrandLogoStyle.OUTRO_SIGNATURE -> Color.Transparent
                        }
                    )
                    .border(
                        width = when (selectedStyle) {
                            BrandLogoStyle.NEON_PILL -> 1.5.dp
                            BrandLogoStyle.GLASS_CARD -> 1.dp
                            BrandLogoStyle.CINEMA_STAMP -> 1.2.dp
                            else -> 0.dp
                        },
                        color = when (selectedStyle) {
                            BrandLogoStyle.NEON_PILL, BrandLogoStyle.CINEMA_STAMP -> brandColorCompose.copy(alpha = opacity)
                            BrandLogoStyle.GLASS_CARD -> Color.White.copy(alpha = opacity * 0.35f)
                            else -> Color.Transparent
                        },
                        shape = when (selectedStyle) {
                            BrandLogoStyle.NEON_PILL -> RoundedCornerShape(24.dp)
                            BrandLogoStyle.GLASS_CARD -> RoundedCornerShape(10.dp)
                            BrandLogoStyle.CINEMA_STAMP -> RoundedCornerShape(3.dp)
                            else -> RoundedCornerShape(6.dp)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedStyle == BrandLogoStyle.BADGE_CREST) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(brandColorCompose.copy(alpha = opacity), RoundedCornerShape(2.dp))
                        )
                    }

                    if (!customLogoUri.isNullOrBlank()) {
                        val customBmp = remember(customLogoUri) {
                            try {
                                val u = customLogoUri!!
                                if (u.startsWith("content://")) {
                                    ctx.contentResolver.openInputStream(android.net.Uri.parse(u))?.use {
                                        android.graphics.BitmapFactory.decodeStream(it)
                                    }
                                } else {
                                    val path = if (u.startsWith("file://")) android.net.Uri.parse(u).path ?: u.removePrefix("file://") else u
                                    android.graphics.BitmapFactory.decodeFile(path)
                                }
                            } catch (_: Throwable) { null }
                        }
                        if (customBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = customBmp.asImageBitmap(),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    } else if (selectedIcon != BrandIconType.NONE && selectedIcon.glyph.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(brandColorCompose.copy(alpha = opacity), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedIcon.glyph,
                                color = Color(0xFF0F1218),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Column {
                        Text(
                            text = displayTitle,
                            color = Color.White.copy(alpha = opacity),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFam
                        )
                        if (tagline.isNotBlank()) {
                            Text(
                                text = tagline,
                                color = Color(0xFFB0B8C4).copy(alpha = opacity * 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 2. Brand Identity Inputs (Dual-Line) ──
        Text("Creator & Brand Typography", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = watermarkText,
            onValueChange = { watermarkText = it },
            label = { Text("Primary Brand / Creator Name", color = TextSecondary, fontSize = 11.sp) },
            placeholder = { Text("e.g. Alex Rivera, ClipStudio, TechDaily", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
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

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = tagline,
            onValueChange = { tagline = it },
            label = { Text("Secondary Handle / Tagline (Optional)", color = TextSecondary, fontSize = 11.sp) },
            placeholder = { Text("e.g. @alexcreates, Directed By, Official Channel", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
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

        Spacer(Modifier.height(6.dp))
        // Quick handle suggestions
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf("@channel", "Official", "Directed by", "Vlogs", "Studio", "Subscribe").forEach { suggestion ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface3)
                        .clickable { tagline = suggestion }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ $suggestion", color = TextSecondary, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 3. Logo Style Maker ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Logo Style Maker", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(selectedStyle.label, color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BrandLogoStyle.values().toList()) { style ->
                val isSel = selectedStyle == style
                Box(
                    modifier = Modifier
                        .width(115.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) AccentPrimary.copy(alpha = 0.2f) else Surface2)
                        .border(
                            width = if (isSel) 1.5.dp else 1.dp,
                            color = if (isSel) AccentPrimary else HairlineBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedStyle = style }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = style.label,
                            color = if (isSel) AccentPrimary else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = style.description,
                            color = TextSecondary,
                            fontSize = 9.sp,
                            maxLines = 2,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 4. Creator & Social Icons or Custom Brand Logo ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Creator & Social Badge Icons", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (!customLogoUri.isNullOrBlank()) {
                TextButton(onClick = { customLogoUri = null }) {
                    Text("Clear Custom Logo", color = AccentPrimary, fontSize = 10.sp)
                }
            } else {
                Text(selectedIcon.label, color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))

        // Upload custom logo button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (!customLogoUri.isNullOrBlank()) AccentPrimary.copy(alpha = 0.2f) else Surface2)
                .border(
                    width = if (!customLogoUri.isNullOrBlank()) 1.5.dp else 1.dp,
                    color = if (!customLogoUri.isNullOrBlank()) AccentPrimary else HairlineBorder,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { logoPickerLauncher.launch("image/*") }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!customLogoUri.isNullOrBlank()) "Custom Logo Attached" else "Upload Custom Brand Logo / Avatar",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (!customLogoUri.isNullOrBlank()) "Tap to replace logo image (PNG/WEBP/JPG)" else "Import transparent PNG or icon from device gallery",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
            if (!customLogoUri.isNullOrBlank()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BrandIconType.values().toList()) { icon ->
                val isSel = selectedIcon == icon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) brandColorCompose.copy(alpha = 0.22f) else Surface2)
                        .border(
                            width = if (isSel) 1.5.dp else 1.dp,
                            color = if (isSel) brandColorCompose else HairlineBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedIcon = icon }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (icon.glyph.isNotEmpty()) {
                            Text(icon.glyph, fontSize = 13.sp)
                        }
                        Text(
                            text = icon.label,
                            color = if (isSel) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 5. Font Selector (with live preview) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Typography Font", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(selectedFont.label, color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FontFamilyOption.values().toList()) { font ->
                val isSel = selectedFont == font
                val fontFam = remember(font, ctx, watermarkText) {
                    com.clipcut.ui.util.FontHelper.getFontFamily(ctx, font, watermarkText)
                }
                val sampleGlyph = remember(font, watermarkText) {
                    if (watermarkText.isNotBlank()) {
                        watermarkText.take(4)
                    } else when (font) {
                        FontFamilyOption.URDU_NASTALIQ -> "اردو"
                        FontFamilyOption.ARABIC_AMIRI, FontFamilyOption.ARABIC_MODERN, FontFamilyOption.ARABIC_KUFI -> "عربي"
                        else -> "Aa"
                    }
                }
                Box(
                    modifier = Modifier
                        .width(105.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) AccentPrimary.copy(alpha = 0.2f) else Surface2)
                        .border(
                            width = if (isSel) 1.5.dp else 1.dp,
                            color = if (isSel) AccentPrimary else HairlineBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedFont = font }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = sampleGlyph,
                            fontFamily = fontFam,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) AccentPrimary else Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = font.label,
                            fontSize = 10.sp,
                            color = if (isSel) Color.White else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 6. Watermark Position (6 Positions) ──
        Text("Screen Position", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        val positions = listOf(
            Triple("Top Left", 1, Alignment.TopStart),
            Triple("Top Center", 5, Alignment.TopCenter),
            Triple("Top Right", 2, Alignment.TopEnd),
            Triple("Bottom Left", 3, Alignment.BottomStart),
            Triple("Bottom Center", 0, Alignment.BottomCenter),
            Triple("Bottom Right", 4, Alignment.BottomEnd)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            positions.take(3).forEach { (lbl, idx, _) ->
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
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            positions.drop(3).forEach { (lbl, idx, _) ->
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

        Spacer(Modifier.height(14.dp))

        // ── 7. Brand Color Palette (36+ Curated Colors) ──
        Text("Brand Accent Color", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        val brandPalette = listOf(
            0xFF00E5FFL, 0xFF00B0FFL, 0xFF2979FFL, 0xFF651FFFL, 0xFF7C4DFFL, 0xFFB388FFL,
            0xFFFF4081L, 0xFFF50057L, 0xFFFF1744L, 0xFFFF5252L, 0xFFFF6E40L, 0xFFFF9100L,
            0xFFFFD700L, 0xFFFFEA00L, 0xFFAEEA00L, 0xFF76FF03L, 0xFF00E676L, 0xFF1DE9B6L,
            0xFF00BFA5L, 0xFF26A69AL, 0xFF80CBC4L, 0xFF80D8FFL, 0xFF82B1FFL, 0xFFB39DDBL,
            0xFFF48FB1L, 0xFFFFAB91L, 0xFFFFCC80L, 0xFFFFF59DL, 0xFFE6EE9CL, 0xFFA5D6A7L,
            0xFFFFFFFFL, 0xFFECEFF1L, 0xFFB0BEC5L, 0xFF78909CL, 0xFF455A64L, 0xFF263238L
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(brandPalette) { colorVal ->
                val isSel = selectedColor == colorVal
                val c = Color(colorVal or 0xFF000000L)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (isSel) 2.5.dp else 1.dp,
                            color = if (isSel) Color.White else Color.Black.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { selectedColor = colorVal },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSel) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (colorVal == 0xFFFFFFFFL) Color.Black else Color.White, CircleShape)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 8. Opacity & Scale Sliders ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Watermark Opacity", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("${(opacity * 100).toInt()}%", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            valueRange = 0.20f..1.0f,
            colors = SliderDefaults.colors(
                thumbColor = AccentPrimary,
                activeTrackColor = AccentPrimary,
                inactiveTrackColor = Surface3
            )
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Watermark Scale", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    scale < 0.9f -> "Compact"
                    scale > 1.2f -> "Large"
                    else -> "Standard"
                },
                color = AccentPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = scale,
            onValueChange = { scale = it },
            valueRange = 0.6f..1.6f,
            colors = SliderDefaults.colors(
                thumbColor = AccentPrimary,
                activeTrackColor = AccentPrimary,
                inactiveTrackColor = Surface3
            )
        )

        Spacer(Modifier.height(18.dp))

        // ── 9. Action Buttons ──
        PrimaryButton(
            text = "Save & Apply Brand Kit",
            onClick = {
                onApply(
                    com.clipcut.core.model.BrandKitConfig(
                        brandColor = selectedColor,
                        logoUri = customLogoUri,
                        watermarkText = watermarkText,
                        tagline = tagline,
                        watermarkPosIndex = posIndex,
                        watermarkScale = scale,
                        opacity = opacity,
                        fontFamily = selectedFont,
                        logoStyle = selectedStyle,
                        iconType = if (!customLogoUri.isNullOrBlank()) BrandIconType.NONE else selectedIcon
                    )
                )
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (current != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Remove Watermark",
                onClick = {
                    onApply(null)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── AudioExtractorSheet ──────────────────────────────────────────────────
@Composable
fun AudioExtractorSheet(
    initialVideoUri: android.net.Uri? = null,
    onAddToTimeline: ((android.net.Uri) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedVideoUri by remember { mutableStateOf<android.net.Uri?>(initialVideoUri) }
    var videoName by remember { mutableStateOf("Selected Video") }
    var videoDurationMs by remember { mutableStateOf(0L) }

    var mp3FileName by remember { mutableStateOf("Audio_${System.currentTimeMillis() % 100000}") }
    var selectedBitrate by remember { mutableIntStateOf(192) }

    var isExtracting by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableFloatStateOf(0f) }
    var extractResult by remember { mutableStateOf<com.clipcut.core.audio.AudioExtractResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isPlayingPreview by remember { mutableStateOf(false) }
    val mediaPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
            } catch (_: Throwable) {}
            mediaPlayerRef.value = null
        }
    }

    // Function to inspect selected video URI
    fun inspectVideo(uri: android.net.Uri) {
        try {
            var name = "video_${System.currentTimeMillis() % 10000}"
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1 && cursor.moveToFirst()) {
                    val n = cursor.getString(nameIdx)
                    if (!n.isNullOrBlank()) name = n
                }
            }
            videoName = name
            mp3FileName = name.substringBeforeLast('.').take(40).ifBlank { "Extracted_Audio" }

            val retr = android.media.MediaMetadataRetriever()
            retr.setDataSource(ctx, uri)
            val dur = retr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            videoDurationMs = dur
            retr.release()
        } catch (t: Throwable) {
            com.clipcut.core.log.AppLog.w("AudioExtractorSheet", "Failed to inspect video: ${t.message}")
        }
    }

    LaunchedEffect(selectedVideoUri) {
        selectedVideoUri?.let { inspectVideo(it) }
    }

    val videoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            extractResult = null
            errorMessage = null
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SheetHeader("Audio Extractor (Video to MP3)", onDismiss)
        Spacer(Modifier.height(8.dp))
        Text(
            "Extract studio-quality MP3 audio from any video and save it directly to your phone's Music folder.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        // Video Selection Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Surface2,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedVideoUri != null) AccentPrimary.copy(alpha = 0.5f) else HairlineBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { videoPickerLauncher.launch("video/*") }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.VideoFile,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedVideoUri != null) videoName else "Choose Video File...",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (selectedVideoUri != null && videoDurationMs > 0L) {
                            val sec = (videoDurationMs / 1000L) % 60
                            val min = (videoDurationMs / 1000L) / 60
                            "Duration: %02d:%02d • Tap to change".format(min, sec)
                        } else if (selectedVideoUri != null) {
                            "Ready to extract • Tap to change"
                        } else {
                            "Select video from phone gallery / storage"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (extractResult == null) {
            // Configuration options
            Text("MP3 Output Settings", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))

            // File Name Input
            Text("File Name (.mp3)", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.OutlinedTextField(
                value = mp3FileName,
                onValueChange = { mp3FileName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = HairlineBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AccentPrimary
                ),
                trailingIcon = {
                    Text(".mp3", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                }
            )

            Spacer(Modifier.height(12.dp))

            // Bitrate selector
            Text("Encoding Bitrate (LAME MP3 Engine)", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    128 to "128 kbps",
                    192 to "192 kbps (HQ)",
                    320 to "320 kbps (Master)"
                ).forEach { (br, label) ->
                    val isSel = selectedBitrate == br
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) AccentPrimary.copy(alpha = 0.2f) else Surface2,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) AccentPrimary else HairlineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedBitrate = br }
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) AccentPrimary else TextPrimary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33FF5252),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            if (isExtracting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface2)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { extractProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentPrimary,
                        trackColor = Surface3
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Extracting & encoding MP3... ${(extractProgress * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Running local high-speed MediaCodec & LAME processor",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            } else {
                PrimaryButton(
                    text = "Extract & Save MP3",
                    onClick = {
                        val uri = selectedVideoUri
                        if (uri == null) {
                            errorMessage = "Please select a video file first."
                            return@PrimaryButton
                        }
                        isExtracting = true
                        extractProgress = 0f
                        errorMessage = null
                        coroutineScope.launch {
                            val res = com.clipcut.core.audio.AudioExtractorEngine.extractAudioToMp3(
                                context = ctx,
                                sourceUri = uri,
                                outputBaseName = mp3FileName.ifBlank { "Extracted_Audio" },
                                bitRateKbps = selectedBitrate,
                                onProgress = { p -> extractProgress = p }
                            )
                            isExtracting = false
                            if (res.isSuccess) {
                                extractResult = res.getOrNull()
                                android.widget.Toast.makeText(ctx, "MP3 saved to Music/ClipCut!", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                errorMessage = res.exceptionOrNull()?.message ?: "Extraction failed."
                            }
                        }
                    },
                    icon = Icons.Filled.Download,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Success Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Surface2,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Audio Extracted & Saved!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Saved as ${extractResult!!.fileName} in your phone's Music folder (Music/ClipCut/)",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    val sec = (extractResult!!.durationMs / 1000L) % 60
                    val min = (extractResult!!.durationMs / 1000L) / 60
                    Text(
                        text = "Format: MP3 • ${selectedBitrate} kbps • %02d:%02d • ${extractResult!!.channels} Channels".format(min, sec),
                        color = AccentPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Play / Pause Preview Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrimaryButton(
                    text = if (isPlayingPreview) "Pause Preview" else "Play MP3 Preview",
                    onClick = {
                        val file = extractResult?.file ?: return@PrimaryButton
                        try {
                            if (isPlayingPreview) {
                                mediaPlayerRef.value?.pause()
                                isPlayingPreview = false
                            } else {
                                if (mediaPlayerRef.value == null) {
                                    mediaPlayerRef.value = android.media.MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        setOnCompletionListener { isPlayingPreview = false }
                                        prepare()
                                    }
                                }
                                mediaPlayerRef.value?.start()
                                isPlayingPreview = true
                            }
                        } catch (t: Throwable) {
                            com.clipcut.core.log.AppLog.w("AudioExtractorSheet", "Preview failed: ${t.message}")
                            isPlayingPreview = false
                        }
                    },
                    icon = if (isPlayingPreview) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    modifier = Modifier.weight(1f)
                )

                // Share Button
                SecondaryButton(
                    text = "Share MP3",
                    onClick = {
                        val res = extractResult ?: return@SecondaryButton
                        try {
                            val shareUri = androidx.core.content.FileProvider.getUriForFile(
                                ctx,
                                "${ctx.packageName}.fileprovider",
                                res.file
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "audio/mpeg"
                                putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(android.content.Intent.createChooser(intent, "Share Extracted Audio"))
                        } catch (t: Throwable) {
                            android.widget.Toast.makeText(ctx, "Share error: ${t.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    icon = Icons.Filled.Share,
                    modifier = Modifier.weight(1f)
                )
            }

            if (onAddToTimeline != null && extractResult != null) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        onAddToTimeline(android.net.Uri.fromFile(extractResult!!.file))
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LuminescentAmber),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = LuminescentAmber.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = LuminescentAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Project Timeline Track", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Extract Another Video",
                onClick = {
                    extractResult = null
                    selectedVideoUri = null
                    try {
                        mediaPlayerRef.value?.stop()
                        mediaPlayerRef.value?.release()
                    } catch (_: Throwable) {}
                    mediaPlayerRef.value = null
                    isPlayingPreview = false
                    videoPickerLauncher.launch("video/*")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── LiveAudioTrimmerSheet (Live Audition & Waveform Trimmer) ──────────────
@Composable
fun LiveAudioTrimmerSheet(
    audioUri: android.net.Uri? = null,
    existingTrack: com.clipcut.core.model.AudioTrackItem? = null,
    onAddTrack: (uri: android.net.Uri, name: String, startUs: Long, endUs: Long, durationUs: Long) -> Unit,
    onUpdateTrack: ((id: String, startUs: Long, endUs: Long) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val effectiveUri = remember(audioUri, existingTrack) {
        audioUri ?: existingTrack?.uri?.let { android.net.Uri.parse(it) }
    }

    var trackTitle by remember(effectiveUri, existingTrack) {
        val initialName = existingTrack?.name
        if (!initialName.isNullOrBlank()) {
            mutableStateOf(initialName)
        } else if (audioUri != null) {
            var resolved = "Custom Audio Track"
            try {
                ctx.contentResolver.query(audioUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            val n = it.getString(idx)
                            if (!n.isNullOrBlank()) resolved = n
                        }
                    }
                }
            } catch (_: Throwable) {}
            mutableStateOf(resolved)
        } else {
            mutableStateOf("Audio Track")
        }
    }

    var totalDurationMs by remember(effectiveUri, existingTrack) {
        val durFromTrack = existingTrack?.sourceDurationUs?.div(1000L) ?: 0L
        if (durFromTrack > 0L) {
            mutableLongStateOf(durFromTrack)
        } else {
            var dur = 10_000L
            if (effectiveUri != null) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    if (effectiveUri.scheme == "content") {
                        retriever.setDataSource(ctx, effectiveUri)
                    } else {
                        val path = effectiveUri.path ?: effectiveUri.toString()
                        if (path.startsWith("asset://")) {
                            val clean = path.removePrefix("asset://").removePrefix("/")
                            val tempFile = java.io.File(ctx.cacheDir, "meta_temp.tmp")
                            ctx.assets.open(clean).use { input -> tempFile.outputStream().use { input.copyTo(it) } }
                            retriever.setDataSource(tempFile.absolutePath)
                        } else {
                            retriever.setDataSource(path)
                        }
                    }
                    val dStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()
                    dur = dStr?.toLongOrNull() ?: 10_000L
                } catch (_: Throwable) {}
            }
            mutableLongStateOf(dur.coerceAtLeast(1000L))
        }
    }

    var trimStartMs by remember(existingTrack, totalDurationMs) {
        val st = (existingTrack?.trimStartUs ?: 0L) / 1000L
        mutableLongStateOf(st.coerceIn(0L, totalDurationMs))
    }

    var trimEndMs by remember(existingTrack, totalDurationMs) {
        val ed = if (existingTrack != null && existingTrack.trimEndUs > 0L) {
            existingTrack.trimEndUs / 1000L
        } else {
            totalDurationMs
        }
        mutableLongStateOf(ed.coerceIn(trimStartMs + 100L, totalDurationMs))
    }

    var currentPlayPosMs by remember(trimStartMs) { mutableLongStateOf(trimStartMs) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoopingCut by remember { mutableStateOf(true) }
    var isAuditionOnlyCut by remember { mutableStateOf(true) }
    val mediaPlayerRef = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    fun formatMs(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        val m = s / 60L
        val sec = s % 60L
        val tenths = ((ms.coerceAtLeast(0L) % 1000L) / 100L)
        return String.format(java.util.Locale.US, "%02d:%02d.%d", m, sec, tenths)
    }

    DisposableEffect(effectiveUri) {
        if (effectiveUri != null) {
            try {
                val mp = android.media.MediaPlayer()
                if (effectiveUri.scheme == "content") {
                    mp.setDataSource(ctx, effectiveUri)
                } else {
                    val path = effectiveUri.path ?: effectiveUri.toString()
                    if (path.startsWith("asset://") || (!path.contains("://") && !path.startsWith("/"))) {
                        val clean = path.removePrefix("asset://").removePrefix("/")
                        val tempFile = java.io.File(ctx.cacheDir, "trimmer_preview.tmp")
                        ctx.assets.open(clean).use { input -> tempFile.outputStream().use { input.copyTo(it) } }
                        mp.setDataSource(tempFile.absolutePath)
                    } else {
                        mp.setDataSource(path)
                    }
                }
                mp.prepare()
                val dur = mp.duration.toLong()
                if (dur > 0L && totalDurationMs <= 10_000L) {
                    totalDurationMs = dur
                    if (trimEndMs <= 10_000L || trimEndMs > dur) trimEndMs = dur
                }
                mp.seekTo(trimStartMs.toInt())
                mediaPlayerRef.value = mp
            } catch (_: Throwable) {}
        }

        onDispose {
            try {
                mediaPlayerRef.value?.stop()
                mediaPlayerRef.value?.release()
            } catch (_: Throwable) {}
            mediaPlayerRef.value = null
            isPlaying = false
        }
    }

    fun seekAudio(toMs: Long) {
        val clamped = toMs.coerceIn(0L, totalDurationMs)
        currentPlayPosMs = clamped
        try {
            val mp = mediaPlayerRef.value ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                mp.seekTo(clamped, android.media.MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(clamped.toInt())
            }
        } catch (_: Throwable) {}
    }

    fun togglePlayback() {
        val mp = mediaPlayerRef.value ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                isPlaying = false
            } else {
                if (isAuditionOnlyCut) {
                    if (currentPlayPosMs < trimStartMs || currentPlayPosMs >= trimEndMs) {
                        seekAudio(trimStartMs)
                    }
                }
                mp.start()
                isPlaying = true
            }
        } catch (_: Throwable) {}
    }

    LaunchedEffect(isPlaying, isAuditionOnlyCut, isLoopingCut, trimStartMs, trimEndMs) {
        while (isActive && isPlaying) {
            val mp = mediaPlayerRef.value
            if (mp != null && mp.isPlaying) {
                val pos = mp.currentPosition.toLong()
                currentPlayPosMs = pos

                if (isAuditionOnlyCut && pos >= trimEndMs) {
                    if (isLoopingCut) {
                        mp.seekTo(trimStartMs.toInt())
                        currentPlayPosMs = trimStartMs
                    } else {
                        mp.pause()
                        mp.seekTo(trimStartMs.toInt())
                        currentPlayPosMs = trimStartMs
                        isPlaying = false
                    }
                }
            } else if (mp != null && !mp.isPlaying && isPlaying) {
                isPlaying = false
            }
            kotlinx.coroutines.delay(35)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SheetHeader(
            if (existingTrack != null) "Live Audio Trimmer (Audition & Cut)" else "Live Custom Audio Preview & Cut",
            onDismiss
        )
        Spacer(Modifier.height(10.dp))

        // Track Info Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(trackTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Total Duration: ${formatMs(totalDurationMs)} • Drag waveform to audition",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Interactive Audio Waveform & Cut Segment Visualizer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF13151D))
                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val targetMs = (ratio * totalDurationMs).toLong()
                        seekAudio(targetMs)
                    }
                }
                .pointerInput(totalDurationMs) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val targetMs = (ratio * totalDurationMs).toLong()
                        seekAudio(targetMs)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val barCount = 54
                val barWidth = (w / barCount) * 0.65f
                val barSpacing = w / barCount

                val startRatio = (trimStartMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                val endRatio = (trimEndMs.toFloat() / totalDurationMs).coerceIn(startRatio, 1f)
                val playheadRatio = (currentPlayPosMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)

                // Draw simulated waveform bars
                for (i in 0 until barCount) {
                    val barCenterRatio = (i + 0.5f) / barCount
                    val isInsideCut = barCenterRatio in startRatio..endRatio
                    val barH = (0.28f + 0.65f * kotlin.math.abs(kotlin.math.sin(i * 0.44f) * kotlin.math.cos(i * 0.23f))) * (h * 0.70f)
                    val x = i * barSpacing + (barSpacing - barWidth) / 2f
                    val y = (h - barH) / 2f

                    drawRoundRect(
                        color = if (isInsideCut) Color(0xFF00E5FF) else Color(0xFF2C3246),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }

                // Shaded cut-off zones
                val cutStartX = startRatio * w
                val cutEndX = endRatio * w
                if (cutStartX > 0f) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.55f),
                        topLeft = Offset(0f, 0f),
                        size = Size(cutStartX, h)
                    )
                }
                if (cutEndX < w) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.55f),
                        topLeft = Offset(cutEndX, 0f),
                        size = Size(w - cutEndX, h)
                    )
                }

                // Active cut selection highlight
                drawRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                    topLeft = Offset(cutStartX, 0f),
                    size = Size(cutEndX - cutStartX, h)
                )

                // Start Marker Line
                drawLine(
                    color = Color(0xFF00E676),
                    start = Offset(cutStartX, 0f),
                    end = Offset(cutStartX, h),
                    strokeWidth = 3.dp.toPx()
                )

                // End Marker Line
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(cutEndX, 0f),
                    end = Offset(cutEndX, h),
                    strokeWidth = 3.dp.toPx()
                )

                // Playhead line
                val playheadX = playheadRatio * w
                drawLine(
                    color = Color(0xFFFFEB3B),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, h),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawCircle(
                    color = Color(0xFFFFEB3B),
                    radius = 4.dp.toPx(),
                    center = Offset(playheadX, 6.dp.toPx())
                )
            }

            // In / Out Marker labels
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("[ IN ${formatMs(trimStartMs)}", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("OUT ${formatMs(trimEndMs)} ]", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Time indicator row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Playhead: ${formatMs(currentPlayPosMs)} / ${formatMs(totalDurationMs)}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            val cutDurMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
            Text(
                "Cut Length: ${formatMs(cutDurMs)}",
                color = AccentPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        // Live Audition Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { seekAudio(trimStartMs) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "To Start", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            IconButton(
                onClick = { togglePlayback() },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(
                onClick = { seekAudio((trimEndMs - 1000L).coerceAtLeast(0L)) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "To End", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            FilterChip(
                selected = isAuditionOnlyCut,
                onClick = {
                    isAuditionOnlyCut = !isAuditionOnlyCut
                    if (isAuditionOnlyCut && (currentPlayPosMs < trimStartMs || currentPlayPosMs >= trimEndMs)) {
                        seekAudio(trimStartMs)
                    }
                },
                label = { Text(if (isAuditionOnlyCut) "Cut Only" else "Full Track", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentPrimary.copy(alpha = 0.25f),
                    selectedLabelColor = AccentPrimary
                )
            )

            FilterChip(
                selected = isLoopingCut,
                onClick = { isLoopingCut = !isLoopingCut },
                label = { Text("🔁 Loop", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentPrimary.copy(alpha = 0.25f),
                    selectedLabelColor = AccentPrimary
                )
            )
        }

        Spacer(Modifier.height(16.dp))

        // Start Cut (In-Point) Slider & Nudges
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00E676)))
                    Spacer(Modifier.width(6.dp))
                    Text("Start Cut (In Point)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(formatMs(trimStartMs), color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = trimStartMs.toFloat(),
                onValueChange = { newStart ->
                    trimStartMs = newStart.toLong().coerceIn(0L, (trimEndMs - 200L).coerceAtLeast(0L))
                    seekAudio(trimStartMs)
                },
                valueRange = 0f..totalDurationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E676),
                    activeTrackColor = Color(0xFF00E676)
                ),
                modifier = Modifier.fillMaxWidth().height(32.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(-1000L to "-1.0s", -100L to "-0.1s", 100L to "+0.1s", 1000L to "+1.0s").forEach { (delta, label) ->
                    OutlinedButton(
                        onClick = {
                            val target = (trimStartMs + delta).coerceIn(0L, (trimEndMs - 100L).coerceAtLeast(0L))
                            trimStartMs = target
                            seekAudio(target)
                        },
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Text(label, fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // End Cut (Out-Point) Slider & Nudges
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5252)))
                    Spacer(Modifier.width(6.dp))
                    Text("End Cut (Out Point)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(formatMs(trimEndMs), color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = trimEndMs.toFloat(),
                onValueChange = { newEnd ->
                    trimEndMs = newEnd.toLong().coerceIn((trimStartMs + 200L).coerceAtMost(totalDurationMs), totalDurationMs)
                },
                valueRange = 0f..totalDurationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF5252),
                    activeTrackColor = Color(0xFFFF5252)
                ),
                modifier = Modifier.fillMaxWidth().height(32.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(-1000L to "-1.0s", -100L to "-0.1s", 100L to "+0.1s", 1000L to "+1.0s").forEach { (delta, label) ->
                    OutlinedButton(
                        onClick = {
                            val target = (trimEndMs + delta).coerceIn((trimStartMs + 100L).coerceAtMost(totalDurationMs), totalDurationMs)
                            trimEndMs = target
                            seekAudio((target - 1500L).coerceAtLeast(trimStartMs))
                        },
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Text(label, fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Quick Presets
        Text("Quick Cut Presets", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(5L to "5s", 10L to "10s", 15L to "15s", 30L to "30s", 60L to "60s").forEach { (sec, label) ->
                OutlinedButton(
                    onClick = {
                        val durTargetMs = sec * 1000L
                        trimEndMs = (trimStartMs + durTargetMs).coerceAtMost(totalDurationMs)
                        seekAudio(trimStartMs)
                    },
                    modifier = Modifier.weight(1f).height(30.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Text(label, fontSize = 11.sp, color = Color.White)
                }
            }
            OutlinedButton(
                onClick = {
                    trimStartMs = 0L
                    trimEndMs = totalDurationMs
                    seekAudio(0L)
                },
                modifier = Modifier.weight(1.2f).height(30.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text("Full Track", fontSize = 11.sp, color = AccentPrimary)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = if (existingTrack != null) "✓ Apply Cut" else "+ Add Cut to Video",
                onClick = {
                    try {
                        mediaPlayerRef.value?.stop()
                        mediaPlayerRef.value?.release()
                    } catch (_: Throwable) {}
                    mediaPlayerRef.value = null
                    isPlaying = false

                    val startUs = trimStartMs * 1000L
                    val endUs = trimEndMs * 1000L
                    val durUs = totalDurationMs * 1000L

                    if (existingTrack != null && onUpdateTrack != null) {
                        onUpdateTrack(existingTrack.id, startUs, endUs)
                    } else if (audioUri != null) {
                        onAddTrack(audioUri, trackTitle, startUs, endUs, durUs)
                    }
                },
                icon = Icons.Filled.Check,
                modifier = Modifier.weight(1.5f)
            )
        }
    }
}

