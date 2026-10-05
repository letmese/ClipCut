package com.clipcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.BeatMarker
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.TimelineItem
import com.clipcut.ui.theme.*

/**
 * TimelineView — multi-track timeline with:
 *   - Horizontal scroll/pan
 *   - Playhead synced to currentPositionUs
 *   - Video / Audio / Overlay / PiP rows
 *   - BeatMarker row at top
 *   - Clip selection (onSelectClip)
 *   - Seek by tapping the timebar (onSeek)
 *   - "Add media" FAB
 *
 * Pinch-to-zoom and drag-reorder are stubbed here and full implementation is
 * wired in EditorScreen via the EditorViewModel mutation methods.
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
    val pxPerUs = density.density / 50_000f // 1dp ≈ 50ms (zoomable in V2)
    val widthPx = (totalDurationUs.coerceAtLeast(1L) * pxPerUs).toInt().coerceAtLeast(1)
    val playheadX = (currentPositionUs.coerceAtLeast(0L) * pxPerUs).toFloat()

    Column(modifier = modifier.fillMaxWidth().background(DarkSurface)) {
        // BeatMarker row
        if (beatMarkers.isNotEmpty()) {
            BeatRow(beatMarkers = beatMarkers, pxPerUs = pxPerUs, widthPx = widthPx)
            HorizontalDivider()
        }
        // Video track
        TimelineRow(title = "Video", color = AccentPrimary) {
            videoClips.forEach { clip ->
                ClipBlock(
                    label = clip.uri.substringAfterLast('/').take(10),
                    widthDp = (clip.effectiveDurationUs * pxPerUs / density.density).toFloat(),
                    selected = clip.id == selectedClipId,
                    color = TimelineClipBg,
                    onClick = { onSelectClip(clip.id) }
                )
            }
            AddClipButton(onClick = onAddMediaClick)
        }
        HorizontalDivider(color = HairlineBorder)
        // Audio track
        TimelineRow(title = "Audio", color = AccentSecondary) {
            audioTracks.forEach { a ->
                ClipBlock(
                    label = a.name,
                    widthDp = (a.effectiveDurationUs * pxPerUs / density.density).toFloat(),
                    selected = false,
                    color = AudioTrackColor,
                    onClick = { /* select audio */ }
                )
            }
        }
        HorizontalDivider(color = HairlineBorder)
        // Overlay track
        TimelineRow(title = "Text", color = OverlayTrackColor) {
            overlays.forEach { o ->
                ClipBlock(
                    label = o.text.ifEmpty { "Sticker" },
                    widthDp = ((o.timelineEndUs - o.timelineStartUs) * pxPerUs / density.density).toFloat(),
                    selected = false,
                    color = OverlayTrackColor.copy(alpha = 0.3f),
                    onClick = {}
                )
            }
        }
        HorizontalDivider(color = HairlineBorder)
        // Timebar with playhead — tappable to seek
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Surface2)
                .pointerInput(totalDurationUs) {
                    detectTapGestures { offset ->
                        val us = (offset.x / pxPerUs).toLong().coerceIn(0L, totalDurationUs)
                        onSeek(us)
                    }
                }
        ) {
            // Playhead
            Box(
                Modifier
                    .offset { IntOffset(playheadX.toInt(), 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(TimelinePlayhead)
            )
        }
    }
}

@Composable
private fun BeatRow(beatMarkers: List<BeatMarker>, pxPerUs: Float, widthPx: Int) {
    Box(modifier = Modifier.fillMaxWidth().height(18.dp).background(Surface2)) {
        beatMarkers.forEach { m ->
            val x = m.timeUs * pxPerUs
            val h = (4 + m.strength * 8).dp
            Box(
                Modifier
                    .offset { IntOffset(x.toInt(), (18.dp.toPx() - h.toPx()).toInt()) }
                    .size(width = 2.dp, height = h)
                    .background(AccentGold)
            )
        }
    }
}

@Composable
private fun TimelineRow(
    title: String,
    color: Color,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
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
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.dp, if (selected) AccentPrimary else HairlineBorder, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun AddClipButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
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
