package com.clipcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.BeatMarker
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.PiPTrackItem
import com.clipcut.core.model.TimelineItem
import com.clipcut.ui.theme.*

/**
 * TimelineView — multi-track synchronized timeline with:
 *   - Expand / shrink video and audio clips using interactive handles on both sides
 *   - Pinch-to-zoom & tap zoom buttons to change timeline time scale
 *   - Unified horizontal scroll across all tracks and timebar
 *   - Playhead synced to currentPositionUs with auto-scroll during playback
 *   - Video / Audio / Overlay / PiP / BeatMarker rows
 *   - Pause and Stop controls on left header
 *   - Tap-to-seek on timebar
 */
@Composable
fun TimelineView(
    videoClips: List<TimelineItem>,
    audioTracks: List<AudioTrackItem>,
    overlays: List<OverlayItem>,
    pipTracks: List<PiPTrackItem> = emptyList(),
    blurTracks: List<com.clipcut.core.model.BlurTrackItem> = emptyList(),
    beatMarkers: List<BeatMarker> = emptyList(),
    selectedClipId: String?,
    selectedOverlayId: String? = null,
    selectedPipId: String? = null,
    selectedBlurId: String? = null,
    currentPositionUs: Long,
    totalDurationUs: Long,
    isPlaying: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onSelectClip: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onAddMediaClick: () -> Unit,
    onTrimClip: (String, Long, Long) -> Unit = { _, _, _ -> },
    onSelectAudioTrack: ((AudioTrackItem) -> Unit)? = null,
    onTrimAudioTrack: ((String, Long, Long) -> Unit)? = null,
    onSelectOverlay: ((OverlayItem) -> Unit)? = null,
    onTrimOverlay: ((String, Long, Long) -> Unit)? = null,
    onSelectPip: ((PiPTrackItem) -> Unit)? = null,
    onTrimPip: ((String, Long, Long) -> Unit)? = null,
    onSelectBlur: ((com.clipcut.core.model.BlurTrackItem) -> Unit)? = null,
    onTrimBlur: ((String, Long, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var internalSelectedAudioTrackId by remember { mutableStateOf<String?>(null) }
    var internalSelectedOverlayId by remember { mutableStateOf<String?>(null) }
    var internalSelectedPipId by remember { mutableStateOf<String?>(null) }
    var internalSelectedBlurId by remember { mutableStateOf<String?>(null) }

    val activeSelectedOverlayId = selectedOverlayId ?: internalSelectedOverlayId
    val activeSelectedPipId = selectedPipId ?: internalSelectedPipId
    val activeSelectedBlurId = selectedBlurId ?: internalSelectedBlurId

    // Dynamic scale: 1dp = usPerDp (50,000 us at 1x zoom)
    val usPerDp = 50_000f / zoomLevel
    val totalUs = totalDurationUs.coerceAtLeast(10_000_000L)
    val timelineWidthDp = ((totalUs / usPerDp).coerceIn(400f, 100_000f) + 240f).dp
    val playheadDp = (currentPositionUs.coerceAtLeast(0L) / usPerDp).dp

    // Auto-scroll timeline to follow playhead only during active playback
    LaunchedEffect(currentPositionUs, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        val playheadPx = with(density) { playheadDp.toPx() }
        val viewportWidth = scrollState.viewportSize
        if (viewportWidth > 0) {
            val currentScroll = scrollState.value
            if (playheadPx > currentScroll + viewportWidth * 0.75f || playheadPx < currentScroll) {
                scrollState.scrollTo((playheadPx - viewportWidth * 0.25f).toInt().coerceAtLeast(0))
            }
        }
    }

    Row(modifier = modifier.fillMaxWidth().background(DarkSurface)) {
        // Track headers column (pinned on left)
        Column(
            modifier = Modifier
                .width(82.dp)
                .background(Surface1)
        ) {
            if (beatMarkers.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Beats", color = AccentGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = HairlineBorder)
            }

            // Timebar header with current timecode & play/stop buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Surface1)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val sec = (currentPositionUs / 1_000_000L).coerceAtLeast(0L)
                    val m = sec / 60
                    val s = sec % 60
                    Text(
                        "%02d:%02d".format(m, s),
                        color = AccentPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Play/Pause button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) AccentPrimary else Surface3)
                                .clickable { onTogglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (isPlaying) DarkBackground else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        // Stop button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Surface3)
                                .clickable { onStop() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Stop",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = HairlineBorder)

            // Video header
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text("Video", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = HairlineBorder)

            // Audio header
            Box(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text("Audio", color = AccentSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = HairlineBorder)

            // Text header
            Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text("Text", color = OverlayTrackColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            if (pipTracks.isNotEmpty()) {
                HorizontalDivider(color = HairlineBorder)
                // PiP header
                Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    Text("PiP", color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (blurTracks.isNotEmpty()) {
                HorizontalDivider(color = HairlineBorder)
                // Blur header
                Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Blur", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Shared scrollable track area with 2-finger pinch-to-zoom (1 finger scrolls smoothly)
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            if (pressedChanges.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    zoomLevel = (zoomLevel * zoomChange).coerceIn(0.5f, 3.0f)
                                    pressedChanges.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Box(modifier = Modifier.width(timelineWidthDp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Beat markers
                    if (beatMarkers.isNotEmpty()) {
                        BeatRow(beatMarkers = beatMarkers, density = density, usPerDp = usPerDp)
                        HorizontalDivider(color = HairlineBorder)
                    }

                    // Timebar - clickable to seek
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Surface2)
                            .pointerInput(totalDurationUs, usPerDp) {
                                detectTapGestures { offset ->
                                    val us = ((offset.x / density.density) * usPerDp).toLong().coerceIn(0L, totalDurationUs)
                                    onSeek(us)
                                }
                            }
                    ) {
                        // Dynamic label step calculation ensuring at least 65dp between timestamp labels
                        val dpPerSec = 1_000_000f / usPerDp
                        val minSec = (65f / dpPerSec).toDouble()
                        val labelStepSec = when {
                            minSec <= 1.0 -> 1L
                            minSec <= 2.0 -> 2L
                            minSec <= 5.0 -> 5L
                            minSec <= 10.0 -> 10L
                            minSec <= 15.0 -> 15L
                            minSec <= 30.0 -> 30L
                            minSec <= 60.0 -> 60L
                            minSec <= 120.0 -> 120L
                            minSec <= 300.0 -> 300L
                            else -> 600L
                        }
                        val minorTickStepSec = when {
                            labelStepSec <= 2L -> 1L
                            labelStepSec == 5L -> 1L
                            labelStepSec <= 15L -> 2L
                            labelStepSec <= 60L -> 5L
                            else -> 30L
                        }

                        val totalSec = (totalUs / 1_000_000L).coerceAtLeast(1L)
                        var s = 0L
                        while (s <= totalSec) {
                            val tickUs = s * 1_000_000L
                            val tickDp = (tickUs / usPerDp).dp
                            val isLabel = (s % labelStepSec) == 0L
                            val tickPx = with(density) { tickDp.roundToPx() }

                            // Vertical notch tick line at bottom of timebar
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(tickPx, with(density) { (if (isLabel) 20.dp else 26.dp).roundToPx() }) }
                                    .width(1.dp)
                                    .height(if (isLabel) 16.dp else 10.dp)
                                    .background(if (isLabel) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.20f))
                            )

                            // Timestamp label with generous breathing room
                            if (isLabel) {
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(tickPx + with(density) { 4.dp.roundToPx() }, with(density) { 4.dp.roundToPx() }) }
                                ) {
                                    val min = s / 60
                                    val secRem = s % 60
                                    Text(
                                        "%02d:%02d".format(min, secRem),
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            s += minorTickStepSec
                        }
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Video Track with Trim Handles (strictly contiguous, no gap drift)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        videoClips.forEach { clip ->
                            val widthDp = (clip.effectiveDurationUs / usPerDp).coerceIn(44f, 10_000f)
                            val durSec = String.format("%.1f", clip.effectiveDurationUs / 1_000_000f)
                            val isSelected = clip.id == selectedClipId
                            ClipBlock(
                                label = clip.uri.substringAfterLast('/').take(14),
                                durationText = "${durSec}s",
                                widthDp = widthDp,
                                selected = isSelected,
                                color = TimelineClipBg,
                                onClick = { onSelectClip(clip.id) },
                                onTrimStart = { dragAmount ->
                                    val deltaUs = (dragAmount / density.density) * usPerDp * clip.speed.coerceAtLeast(0.1f)
                                    val newStart = (clip.trimStartUs + deltaUs.toLong()).coerceIn(0L, clip.trimEndUs - 200_000L)
                                    onTrimClip(clip.id, newStart, clip.trimEndUs)
                                    onSeek(newStart)
                                },
                                onTrimEnd = { dragAmount ->
                                    val deltaUs = (dragAmount / density.density) * usPerDp * clip.speed.coerceAtLeast(0.1f)
                                    val maxEnd = if (clip.sourceDurationUs > 0) clip.sourceDurationUs else Long.MAX_VALUE
                                    val newEnd = (clip.trimEndUs + deltaUs.toLong()).coerceIn(clip.trimStartUs + 200_000L, maxEnd)
                                    onTrimClip(clip.id, clip.trimStartUs, newEnd)
                                    onSeek(newEnd)
                                }
                            )
                        }
                        AddClipButton(onClick = onAddMediaClick)
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Audio Track with Trim Handles (aligned to timelineStartUs)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        audioTracks.forEach { a ->
                            val startDp = (a.timelineStartUs / usPerDp).dp
                            val widthDp = (a.effectiveDurationUs / usPerDp).coerceIn(44f, 10_000f)
                            val durSec = String.format("%.1f", a.effectiveDurationUs / 1_000_000f)
                            val isSelected = a.id == internalSelectedAudioTrackId
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(with(density) { startDp.roundToPx() }, with(density) { 3.dp.roundToPx() }) }
                            ) {
                                ClipBlock(
                                    label = a.name.ifEmpty { "Audio" }.take(14),
                                    durationText = "${durSec}s",
                                    widthDp = widthDp,
                                    selected = isSelected,
                                    color = AudioTrackColor,
                                    onClick = {
                                        internalSelectedAudioTrackId = if (isSelected) null else a.id
                                        onSelectAudioTrack?.invoke(a)
                                    },
                                    onTrimStart = { dragAmount ->
                                        val deltaUs = (dragAmount / density.density) * usPerDp
                                        val newStart = (a.trimStartUs + deltaUs.toLong()).coerceIn(0L, a.trimEndUs - 200_000L)
                                        onTrimAudioTrack?.invoke(a.id, newStart, a.trimEndUs)
                                        onSeek(a.timelineStartUs + (newStart - a.trimStartUs).coerceAtLeast(0L))
                                    },
                                    onTrimEnd = { dragAmount ->
                                        val deltaUs = (dragAmount / density.density) * usPerDp
                                        val maxEnd = if (a.sourceDurationUs > 0) a.sourceDurationUs else Long.MAX_VALUE
                                        val newEnd = (a.trimEndUs + deltaUs.toLong()).coerceIn(a.trimStartUs + 200_000L, maxEnd)
                                        onTrimAudioTrack?.invoke(a.id, a.trimStartUs, newEnd)
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Text/Overlay Track (aligned to timelineStartUs)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        overlays.forEach { o ->
                            val startDp = (o.timelineStartUs / usPerDp).dp
                            val durUs = (o.timelineEndUs - o.timelineStartUs).coerceAtLeast(200_000L)
                            val widthDp = (durUs / usPerDp).coerceIn(44f, 5000f)
                            val durSec = String.format("%.1f", durUs / 1_000_000f)
                            val isSelected = o.id == activeSelectedOverlayId
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(with(density) { startDp.roundToPx() }, with(density) { 2.dp.roundToPx() }) }
                            ) {
                                ClipBlock(
                                    label = o.text.ifEmpty { "Text" }.take(12),
                                    durationText = "${durSec}s",
                                    widthDp = widthDp,
                                    selected = isSelected,
                                    color = OverlayTrackColor.copy(alpha = 0.45f),
                                    onClick = {
                                        internalSelectedOverlayId = if (isSelected) null else o.id
                                        onSelectOverlay?.invoke(o)
                                    },
                                    onTrimStart = { dragAmount ->
                                        val deltaUs = (dragAmount / density.density) * usPerDp
                                        val newStart = (o.timelineStartUs + deltaUs.toLong()).coerceIn(0L, o.timelineEndUs - 200_000L)
                                        onTrimOverlay?.invoke(o.id, newStart, o.timelineEndUs)
                                        onSeek(newStart)
                                    },
                                    onTrimEnd = { dragAmount ->
                                        val deltaUs = (dragAmount / density.density) * usPerDp
                                        val newEnd = (o.timelineEndUs + deltaUs.toLong()).coerceAtLeast(o.timelineStartUs + 200_000L)
                                        onTrimOverlay?.invoke(o.id, o.timelineStartUs, newEnd)
                                        onSeek(newEnd)
                                    }
                                )
                            }
                        }
                    }

                    // PiP Track (aligned to timelineStartUs)
                    if (pipTracks.isNotEmpty()) {
                        HorizontalDivider(color = HairlineBorder)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            pipTracks.forEach { p ->
                                val startDp = (p.timelineStartUs / usPerDp).dp
                                val durUs = (p.timelineEndUs - p.timelineStartUs).coerceAtLeast(200_000L)
                                val widthDp = (durUs / usPerDp).coerceIn(44f, 5000f)
                                val durSec = String.format("%.1f", durUs / 1_000_000f)
                                val isSelected = p.id == activeSelectedPipId
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(with(density) { startDp.roundToPx() }, with(density) { 2.dp.roundToPx() }) }
                                ) {
                                    ClipBlock(
                                        label = p.uri.substringAfterLast('/').take(12).ifEmpty { "PiP" },
                                        durationText = "${durSec}s",
                                        widthDp = widthDp,
                                        selected = isSelected,
                                        color = AccentGold.copy(alpha = 0.40f),
                                        onClick = {
                                            internalSelectedPipId = if (isSelected) null else p.id
                                            onSelectPip?.invoke(p)
                                        },
                                        onTrimStart = { dragAmount ->
                                            val deltaUs = (dragAmount / density.density) * usPerDp
                                            val newStart = (p.timelineStartUs + deltaUs.toLong()).coerceIn(0L, p.timelineEndUs - 200_000L)
                                            onTrimPip?.invoke(p.id, newStart, p.timelineEndUs)
                                            onSeek(newStart)
                                        },
                                        onTrimEnd = { dragAmount ->
                                            val deltaUs = (dragAmount / density.density) * usPerDp
                                            val newEnd = (p.timelineEndUs + deltaUs.toLong()).coerceAtLeast(p.timelineStartUs + 200_000L)
                                            onTrimPip?.invoke(p.id, p.timelineStartUs, newEnd)
                                            onSeek(newEnd)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    // Blur Track (aligned to timelineStartUs)
                    if (blurTracks.isNotEmpty()) {
                        HorizontalDivider(color = HairlineBorder)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            blurTracks.forEach { b ->
                                val startDp = (b.timelineStartUs / usPerDp).dp
                                val durUs = (b.timelineEndUs - b.timelineStartUs).coerceAtLeast(200_000L)
                                val widthDp = (durUs / usPerDp).coerceIn(44f, 5000f)
                                val durSec = String.format("%.1f", durUs / 1_000_000f)
                                val isSelected = b.id == activeSelectedBlurId
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(with(density) { startDp.roundToPx() }, with(density) { 2.dp.roundToPx() }) }
                                ) {
                                    val blockLabel = when (b.blurType) {
                                        com.clipcut.core.model.BlurType.CHROMA_PAINT -> "Studio Paint"
                                        com.clipcut.core.model.BlurType.PAINT -> "Finger Paint"
                                        com.clipcut.core.model.BlurType.IMAGE -> "Sticker"
                                        else -> if (b.keyframes.isNotEmpty()) "⚡ Blur (${b.shape.displayName})" else "Blur (${b.shape.displayName})"
                                    }
                                    val blockColor = when (b.blurType) {
                                        com.clipcut.core.model.BlurType.CHROMA_PAINT -> Color(0xFF10B981).copy(alpha = 0.50f)
                                        com.clipcut.core.model.BlurType.PAINT -> Color(0xFFE53935).copy(alpha = 0.40f)
                                        com.clipcut.core.model.BlurType.IMAGE -> Color(0xFFFFB300).copy(alpha = 0.40f)
                                        else -> Color(0xFF00E5FF).copy(alpha = 0.40f)
                                    }
                                    ClipBlock(
                                        label = blockLabel,
                                        durationText = "${durSec}s",
                                        widthDp = widthDp,
                                        selected = isSelected,
                                        color = blockColor,
                                        onClick = {
                                            internalSelectedBlurId = if (isSelected) null else b.id
                                            onSelectBlur?.invoke(b)
                                        },
                                        onTrimStart = { dragAmount ->
                                            val deltaUs = (dragAmount / density.density) * usPerDp
                                            val newStart = (b.timelineStartUs + deltaUs.toLong()).coerceIn(0L, b.timelineEndUs - 200_000L)
                                            onTrimBlur?.invoke(b.id, newStart, b.timelineEndUs)
                                            onSeek(newStart)
                                        },
                                        onTrimEnd = { dragAmount ->
                                            val deltaUs = (dragAmount / density.density) * usPerDp
                                            val newEnd = (b.timelineEndUs + deltaUs.toLong()).coerceAtLeast(b.timelineStartUs + 200_000L)
                                            onTrimBlur?.invoke(b.id, b.timelineStartUs, newEnd)
                                            onSeek(newEnd)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Vertical Playhead across all tracks with precise height and top indicator pin
                val trackHeights = buildList {
                    if (beatMarkers.isNotEmpty()) {
                        add(18.dp)
                        add(1.dp)
                    }
                    add(36.dp) // timebar
                    add(1.dp)
                    add(48.dp) // video
                    add(1.dp)
                    add(46.dp) // audio
                    add(1.dp)
                    add(44.dp) // text
                    if (pipTracks.isNotEmpty()) {
                        add(1.dp)
                        add(44.dp) // pip
                    }
                    if (blurTracks.isNotEmpty()) {
                        add(1.dp)
                        add(44.dp) // blur
                    }
                }
                val totalTrackHeight = trackHeights.fold(0.dp) { acc, h -> acc + h }
                val playheadPx = with(density) { playheadDp.toPx() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(playheadPx.toInt(), 0) }
                        .width(2.dp)
                        .height(totalTrackHeight)
                        .background(TimelinePlayhead)
                ) {
                    // Precision Playhead Top Pin in the ruler
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(with(density) { (-4).dp.roundToPx() }, 0) }
                            .size(10.dp, 12.dp)
                            .background(
                                color = TimelinePlayhead,
                                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun BeatRow(beatMarkers: List<BeatMarker>, density: Density, usPerDp: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(18.dp).background(Surface2)) {
        beatMarkers.forEach { m ->
            val xDp = (m.timeUs / usPerDp).dp
            val h = (4 + m.strength * 8).dp
            val xPx = with(density) { xDp.roundToPx() }
            val yPx = with(density) { (18.dp - h).roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset(xPx, yPx) }
                    .size(width = 2.dp, height = h)
                    .background(AccentGold)
            )
        }
    }
}

/**
 * ClipBlock — timeline block with interactive left and right trim handles.
 * When selected, dragging the left handle expands/shrinks the start.
 * Dragging the right handle expands/shrinks the end.
 */
@Composable
private fun ClipBlock(
    label: String,
    durationText: String = "",
    widthDp: Float,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    onTrimStart: ((Float) -> Unit)? = null,
    onTrimEnd: ((Float) -> Unit)? = null
) {
    val handleWidth = 24.dp
    // Local live deltas during drag (in dp)
    var dragStartOffsetDp by remember(widthDp) { mutableFloatStateOf(0f) }
    var dragEndOffsetDp by remember(widthDp) { mutableFloatStateOf(0f) }

    val activeWidthDp = (widthDp - dragStartOffsetDp + dragEndOffsetDp).coerceAtLeast(44f)

    Box(
        modifier = Modifier
            .width(activeWidthDp.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AccentPrimary else HairlineBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Center label & duration
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (selected) 26.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (durationText.isNotEmpty()) {
                Text(durationText, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1)
            }
        }

        // Left trim handle (draggable)
        if (selected && onTrimStart != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(handleWidth)
                    .fillMaxHeight()
                    .background(AccentPrimary, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .pointerInput(onTrimStart) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var accumulatedPx = 0f
                            val density = this.density
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    val delta = change.position.x - change.previousPosition.x
                                    if (delta != 0f) {
                                        change.consume()
                                        accumulatedPx += delta
                                        dragStartOffsetDp = accumulatedPx / density
                                    }
                                } else {
                                    change.consume()
                                    break
                                }
                            }
                            if (accumulatedPx != 0f) {
                                onTrimStart(accumulatedPx)
                            }
                            dragStartOffsetDp = 0f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(2.dp).height(16.dp).background(Color.White, RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(2.dp).height(16.dp).background(Color.White, RoundedCornerShape(1.dp)))
                }
            }
        }

        // Right trim handle (draggable)
        if (selected && onTrimEnd != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(handleWidth)
                    .fillMaxHeight()
                    .background(AccentPrimary, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .pointerInput(onTrimEnd) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var accumulatedPx = 0f
                            val density = this.density
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    val delta = change.position.x - change.previousPosition.x
                                    if (delta != 0f) {
                                        change.consume()
                                        accumulatedPx += delta
                                        dragEndOffsetDp = accumulatedPx / density
                                    }
                                } else {
                                    change.consume()
                                    break
                                }
                            }
                            if (accumulatedPx != 0f) {
                                onTrimEnd(accumulatedPx)
                            }
                            dragEndOffsetDp = 0f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(2.dp).height(16.dp).background(Color.White, RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(2.dp).height(16.dp).background(Color.White, RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

@Composable
private fun AddClipButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
    }
}
