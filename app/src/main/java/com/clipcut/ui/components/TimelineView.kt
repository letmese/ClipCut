package com.clipcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    beatMarkers: List<BeatMarker> = emptyList(),
    selectedClipId: String?,
    selectedOverlayId: String? = null,
    selectedPipId: String? = null,
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var selectedAudioTrackId by remember { mutableStateOf<String?>(null) }

    // Dynamic scale: 1dp = usPerDp (50,000 us at 1x zoom)
    val safeZoom = if (zoomLevel.isFinite() && zoomLevel > 0f) zoomLevel else 1.0f
    val usPerDp = (50_000f / safeZoom.coerceAtLeast(0.5f)).let { if (it.isFinite()) it else 50_000f }
    val totalUs = totalDurationUs.coerceAtLeast(10_000_000L)
    val timelineWidthDp = ((totalUs / usPerDp).let { if (it.isFinite()) it else 400f }.coerceIn(400f, 100_000f) + 240f).dp
    val playheadDp = (currentPositionUs.coerceAtLeast(0L) / usPerDp).let { if (it.isFinite()) it else 0f }.dp

    // Auto-scroll timeline to follow playhead during playback
    LaunchedEffect(currentPositionUs) {
        val playheadPx = with(density) { playheadDp.toPx() }
        val viewportWidth = scrollState.viewportSize
        if (viewportWidth > 0) {
            val currentScroll = scrollState.value
            if (playheadPx > currentScroll + viewportWidth * 0.75f || playheadPx < currentScroll) {
                scrollState.scrollTo((playheadPx - viewportWidth * 0.25f).toInt().coerceAtLeast(0))
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    Row(modifier = modifier.fillMaxWidth().background(DarkSurface)) {
        // Track headers column (pinned on left)
        Column(
            modifier = Modifier
                .width(68.dp)
                .background(Surface1)
        ) {
            if (beatMarkers.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Beats", color = AccentGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = HairlineBorder)
            }

            // Timebar header with zoom controls & pause/stop buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val sec = (currentPositionUs / 1_000_000L).coerceAtLeast(0L)
                    val m = sec / 60
                    val s = sec % 60
                    Text("%02d:%02d".format(m, s), color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Play/Pause button
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = AccentPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        // Stop button
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
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
        }

        // Shared scrollable track area with pinch-to-zoom
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
                .pointerInput(scrollState) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom.isFinite() && zoom != 1f) {
                            zoomLevel = (zoomLevel * zoom).coerceIn(0.5f, 3.0f)
                        }
                        if (pan.x.isFinite() && pan.x != 0f) {
                            coroutineScope.launch {
                                scrollState.scrollBy(-pan.x)
                            }
                        }
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
                            .height(34.dp)
                            .background(Surface2)
                            .pointerInput(totalDurationUs, usPerDp) {
                                detectTapGestures { offset ->
                                    val us = ((offset.x / density.density) * usPerDp).toLong().coerceIn(0L, totalDurationUs)
                                    onSeek(us)
                                }
                            }
                    ) {
                        val tickStepUs = (1_000_000L / zoomLevel).toLong().coerceAtLeast(500_000L)
                        val numTicks = (totalUs / tickStepUs).toInt().coerceAtMost(500)
                        for (i in 0..numTicks) {
                            val tickUs = i * tickStepUs
                            val tickDp = (tickUs / usPerDp).dp
                            val isMajor = (tickUs % 5_000_000L == 0L)
                            val tickLineHeight = if (isMajor) 14.dp else 7.dp

                            // Vertical tick mark line
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(with(density) { tickDp.roundToPx() }, with(density) { (34.dp - tickLineHeight).roundToPx() }) }
                                    .width(1.dp)
                                    .height(tickLineHeight)
                                    .background(Color.White.copy(alpha = if (isMajor) 0.6f else 0.25f))
                            )

                            // Timestamp label on major ticks
                            if (isMajor || zoomLevel >= 1.5f) {
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(with(density) { tickDp.roundToPx() }, 0) }
                                        .padding(start = 3.dp, top = 2.dp)
                                ) {
                                    val s = (tickUs / 1_000_000L)
                                    val m = s / 60
                                    val secRem = s % 60
                                    Text(
                                        "%02d:%02d".format(java.util.Locale.US, m, secRem),
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Video Track with Trim Handles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        videoClips.forEach { clip ->
                            val widthDp = (clip.effectiveDurationUs / usPerDp).coerceIn(60f, 10_000f)
                            val durSec = String.format(java.util.Locale.US, "%.1f", clip.effectiveDurationUs / 1_000_000f)
                            val isSelected = clip.id == selectedClipId
                            ClipBlock(
                                label = clip.uri.substringAfterLast('/').safeTake(14),
                                durationText = "${durSec}s",
                                widthDp = widthDp,
                                selected = isSelected,
                                color = TimelineClipBg,
                                onClick = { onSelectClip(clip.id) },
                                onTrimStart = { dragAmount ->
                                    val deltaUs = (dragAmount / density.density) * usPerDp * clip.speed.coerceAtLeast(0.1f)
                                    val maxStart = (clip.trimEndUs - 200_000L).coerceAtLeast(0L)
                                    val newStart = (clip.trimStartUs + deltaUs.toLong()).coerceIn(0L, maxStart)
                                    onTrimClip(clip.id, newStart, clip.trimEndUs)
                                    onSeek(newStart)
                                },
                                onTrimEnd = { dragAmount ->
                                    val deltaUs = (dragAmount / density.density) * usPerDp * clip.speed.coerceAtLeast(0.1f)
                                    val maxEnd = if (clip.sourceDurationUs > 0) clip.sourceDurationUs else Long.MAX_VALUE
                                    val minEnd = (clip.trimStartUs + 200_000L).coerceAtMost(maxEnd)
                                    val newEnd = (clip.trimEndUs + deltaUs.toLong()).coerceIn(minEnd, maxEnd)
                                    onTrimClip(clip.id, clip.trimStartUs, newEnd)
                                    onSeek(newEnd)
                                }
                            )
                        }
                        AddClipButton(onClick = onAddMediaClick)
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Audio Track with Trim Handles (positioned accurately along the timeline)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        audioTracks.forEach { a ->
                            val startDp = (a.timelineStartUs / usPerDp).coerceAtLeast(0f).dp
                            val widthDp = (a.effectiveDurationUs / usPerDp).coerceIn(60f, 10_000f)
                            val durSec = String.format(java.util.Locale.US, "%.1f", a.effectiveDurationUs / 1_000_000f)
                            val isSelected = a.id == selectedAudioTrackId
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(with(density) { startDp.roundToPx() }, 0) }
                                    .padding(vertical = 3.dp)
                            ) {
                                ClipBlock(
                                    label = a.name.ifEmpty { "Audio" }.safeTake(14),
                                    durationText = "${durSec}s",
                                    widthDp = widthDp,
                                    selected = isSelected,
                                    color = AudioTrackColor,
                                    onClick = {
                                        selectedAudioTrackId = if (isSelected) null else a.id
                                        try { onSelectAudioTrack?.invoke(a) } catch (_: Throwable) {}
                                    },
                                    onTrimStart = { dragAmount ->
                                        val deltaUs = (dragAmount / density.density) * usPerDp
                                        val maxStart = (a.trimEndUs - 200_000L).coerceAtLeast(0L)
                                        val newStart = (a.trimStartUs + deltaUs.toLong()).coerceIn(0L, maxStart)
                                        onTrimAudioTrack?.invoke(a.id, newStart, a.trimEndUs)
                                        onSeek(a.timelineStartUs + (newStart - a.trimStartUs).coerceAtLeast(0L))
                                    },
                                    onTrimEnd = { dragAmount ->
                                        val deltaUs = (dragAmount / density.density) * usPerDp
                                        val maxEnd = if (a.sourceDurationUs > 0) a.sourceDurationUs else Long.MAX_VALUE
                                        val minEnd = (a.trimStartUs + 200_000L).coerceAtMost(maxEnd)
                                        val newEnd = (a.trimEndUs + deltaUs.toLong()).coerceIn(minEnd, maxEnd)
                                        onTrimAudioTrack?.invoke(a.id, a.trimStartUs, newEnd)
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Text/Overlay Track (positioned accurately along the timeline at timelineStartUs)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        overlays.forEach { o ->
                            val startDp = (o.timelineStartUs / usPerDp).coerceAtLeast(0f).dp
                            val durUs = (o.timelineEndUs - o.timelineStartUs).coerceAtLeast(500_000L)
                            val widthDp = (durUs / usPerDp).coerceIn(48f, 2500f)
                            val durSec = String.format(java.util.Locale.US, "%.1f", durUs / 1_000_000f)
                            val isSelected = o.id == selectedOverlayId
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(with(density) { startDp.roundToPx() }, 0) }
                                    .padding(vertical = 2.dp)
                            ) {
                                ClipBlock(
                                    label = o.text.ifEmpty { "Text" }.safeTake(12),
                                    durationText = "${durSec}s",
                                    widthDp = widthDp,
                                    selected = isSelected,
                                    color = OverlayTrackColor.copy(alpha = 0.35f),
                                    onClick = {
                                        try { onSelectOverlay?.invoke(o) } catch (_: Throwable) {}
                                    },
                                    onTrimStart = { dragAmount ->
                                        val deltaUs = (dragAmount / density.density) * usPerDp
                                        val maxStart = (o.timelineEndUs - 200_000L).coerceAtLeast(0L)
                                        val newStart = (o.timelineStartUs + deltaUs.toLong()).coerceIn(0L, maxStart)
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

                    // PiP Track (positioned accurately along the timeline at timelineStartUs)
                    if (pipTracks.isNotEmpty()) {
                        HorizontalDivider(color = HairlineBorder)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            pipTracks.forEach { p ->
                                val startDp = (p.timelineStartUs / usPerDp).coerceAtLeast(0f).dp
                                val durUs = (p.timelineEndUs - p.timelineStartUs).coerceAtLeast(500_000L)
                                val widthDp = (durUs / usPerDp).coerceIn(48f, 2500f)
                                val durSec = String.format(java.util.Locale.US, "%.1f", durUs / 1_000_000f)
                                val isSelected = p.id == selectedPipId
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(with(density) { startDp.roundToPx() }, 0) }
                                        .padding(vertical = 2.dp)
                                ) {
                                    ClipBlock(
                                        label = p.uri.substringAfterLast('/').safeTake(12),
                                        durationText = "${durSec}s",
                                        widthDp = widthDp,
                                        selected = isSelected,
                                        color = AccentGold.copy(alpha = 0.4f),
                                        onClick = {
                                            try { onSelectPip?.invoke(p) } catch (_: Throwable) {}
                                        },
                                        onTrimStart = { dragAmount ->
                                            val deltaUs = (dragAmount / density.density) * usPerDp
                                            val maxStart = (p.timelineEndUs - 200_000L).coerceAtLeast(0L)
                                            val newStart = (p.timelineStartUs + deltaUs.toLong()).coerceIn(0L, maxStart)
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
                }

                // Vertical Playhead across all tracks with explicit height
                val trackHeights = buildList {
                    if (beatMarkers.isNotEmpty()) add(18.dp)
                    add(34.dp) // timebar
                    add(48.dp) // video
                    add(46.dp) // audio
                    add(44.dp) // text
                    if (pipTracks.isNotEmpty()) add(44.dp) // pip
                }
                val totalTrackHeight = trackHeights.fold(0.dp) { acc, h -> acc + h }
                val playheadPx = with(density) { playheadDp.toPx() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(playheadPx.toInt(), 0) }
                        .width(2.dp)
                        .height(totalTrackHeight)
                        .background(TimelinePlayhead)
                )
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

private fun String.safeTake(n: Int): String {
    if (this.length <= n) return this
    val cut = if (n > 0 && Character.isHighSurrogate(this[n - 1])) n - 1 else n
    return this.take(cut.coerceAtLeast(0))
}
