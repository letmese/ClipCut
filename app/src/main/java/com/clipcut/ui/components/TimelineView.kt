package com.clipcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.clipcut.core.model.TimelineItem
import com.clipcut.ui.theme.*

/**
 * TimelineView — multi-track synchronized timeline with:
 *   - Unified horizontal scroll across all tracks and timebar
 *   - Playhead synced to currentPositionUs with auto-scroll during playback
 *   - Pinned track headers on the left
 *   - Video / Audio / Overlay / BeatMarker rows
 *   - Safe bounds: all clip blocks clamped to sensible min/max widths
 *   - Tap-to-seek on timebar
 */
@Composable
fun TimelineView(
    videoClips: List<TimelineItem>,
    audioTracks: List<AudioTrackItem>,
    overlays: List<OverlayItem>,
    beatMarkers: List<BeatMarker> = emptyList(),
    selectedClipId: String?,
    currentPositionUs: Long,
    totalDurationUs: Long,
    onSelectClip: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onAddMediaClick: () -> Unit,
    onTrimClip: (String, Long, Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    // 1dp = 50ms (50_000 us)
    val totalUs = totalDurationUs.coerceAtLeast(10_000_000L)
    val timelineWidthDp = ((totalUs / 50_000L).coerceIn(400L, 100_000L).toFloat() + 200f).dp
    val playheadDp = (currentPositionUs.coerceAtLeast(0L) / 50_000f).dp

    // Auto-scroll timeline to follow playhead during playback
    LaunchedEffect(currentPositionUs) {
        val playheadPx = with(density) { playheadDp.toPx() }
        val viewportWidth = scrollState.viewportSize
        if (viewportWidth > 0) {
            val currentScroll = scrollState.value
            if (playheadPx > currentScroll + viewportWidth * 0.75f || playheadPx < currentScroll) {
                scrollState.animateScrollTo((playheadPx - viewportWidth * 0.25f).toInt().coerceAtLeast(0))
            }
        }
    }

    Row(modifier = modifier.fillMaxWidth().background(DarkSurface)) {
        // Track headers column (pinned on left)
        Column(
            modifier = Modifier
                .width(52.dp)
                .background(Surface1)
        ) {
            if (beatMarkers.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Beats", color = AccentGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = HairlineBorder)
            }
            // Timebar header
            Box(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                val sec = (currentPositionUs / 1_000_000L).coerceAtLeast(0L)
                val m = sec / 60
                val s = sec % 60
                Text("%02d:%02d".format(m, s), color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = HairlineBorder)
            // Video header
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text("Video", color = AccentPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = HairlineBorder)
            // Audio header
            Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text("Audio", color = AccentSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = HairlineBorder)
            // Text header
            Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text("Text", color = OverlayTrackColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Shared scrollable track area
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
        ) {
            Box(modifier = Modifier.width(timelineWidthDp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Beat markers
                    if (beatMarkers.isNotEmpty()) {
                        BeatRow(beatMarkers = beatMarkers, density = density)
                        HorizontalDivider(color = HairlineBorder)
                    }

                    // Timebar - clickable to seek
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .background(Surface2)
                            .pointerInput(totalDurationUs) {
                                detectTapGestures { offset ->
                                    val us = ((offset.x / density.density) * 50_000f).toLong().coerceIn(0L, totalDurationUs)
                                    onSeek(us)
                                }
                            }
                    ) {
                        // Ticks every 5 seconds
                        val fiveSecsUs = 5_000_000L
                        val numTicks = (totalUs / fiveSecsUs).toInt().coerceAtMost(200)
                        for (i in 0..numTicks) {
                            val tickUs = i * fiveSecsUs
                            val tickDp = (tickUs / 50_000L).toFloat().dp
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(with(density) { tickDp.roundToPx() }, 0) }
                                    .padding(start = 2.dp, top = 4.dp)
                            ) {
                                val s = (tickUs / 1_000_000L)
                                val m = s / 60
                                val secRem = s % 60
                                Text(
                                    "%02d:%02d".format(m, secRem),
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Video Track
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        videoClips.forEach { clip ->
                            val widthDp = (clip.effectiveDurationUs / 50_000L).coerceIn(48L, 2500L).toFloat()
                            ClipBlock(
                                label = clip.uri.substringAfterLast('/').take(12),
                                widthDp = widthDp,
                                selected = clip.id == selectedClipId,
                                color = TimelineClipBg,
                                onClick = { onSelectClip(clip.id) }
                            )
                        }
                        AddClipButton(onClick = onAddMediaClick)
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Audio Track
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        audioTracks.forEach { a ->
                            val widthDp = (a.effectiveDurationUs / 50_000L).coerceIn(48L, 2500L).toFloat()
                            ClipBlock(
                                label = a.name.ifEmpty { "Audio" }.take(12),
                                widthDp = widthDp,
                                selected = false,
                                color = AudioTrackColor,
                                onClick = {}
                            )
                        }
                    }
                    HorizontalDivider(color = HairlineBorder)

                    // Text/Overlay Track
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        overlays.forEach { o ->
                            val durUs = (o.timelineEndUs - o.timelineStartUs).coerceAtLeast(500_000L)
                            val widthDp = (durUs / 50_000L).coerceIn(48L, 2500L).toFloat()
                            ClipBlock(
                                label = o.text.ifEmpty { "Text" }.take(12),
                                widthDp = widthDp,
                                selected = false,
                                color = OverlayTrackColor.copy(alpha = 0.35f),
                                onClick = {}
                            )
                        }
                    }
                }

                // Vertical Playhead across all tracks with explicit height
                val trackHeights = buildList {
                    if (beatMarkers.isNotEmpty()) add(18.dp)
                    add(26.dp) // timebar
                    add(48.dp) // video
                    add(44.dp) // audio
                    add(44.dp) // text
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
private fun BeatRow(beatMarkers: List<BeatMarker>, density: Density) {
    Box(modifier = Modifier.fillMaxWidth().height(18.dp).background(Surface2)) {
        beatMarkers.forEach { m ->
            val xDp = (m.timeUs / 50_000L).toFloat().dp
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

@Composable
private fun ClipBlock(
    label: String,
    widthDp: Float,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.dp, if (selected) AccentPrimary else HairlineBorder, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun AddClipButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, HairlineBorder, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
    }
}
