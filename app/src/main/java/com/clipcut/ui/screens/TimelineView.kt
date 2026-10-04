package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.TimelineItem
import com.clipcut.ui.theme.*
import java.util.Locale

@Composable
fun TimelineView(
    videoClips: List<TimelineItem>,
    audioTracks: List<AudioTrackItem>,
    overlays: List<OverlayItem>,
    selectedClipId: String?,
    currentPositionUs: Long,
    totalDurationUs: Long,
    onSelectClip: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onAddMediaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceElevated)
            .padding(vertical = 4.dp)
    ) {
        // Scrubber / Ruler Bar with Time indicators & Drag Scrubbing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(DarkSurface)
                .pointerInput(totalDurationUs) {
                    detectTapGestures { offset ->
                        if (totalDurationUs > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val targetUs = (fraction * totalDurationUs).toLong()
                            onSeek(targetUs)
                        }
                    }
                }
                .pointerInput(totalDurationUs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (totalDurationUs > 0) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeek((fraction * totalDurationUs).toLong())
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (totalDurationUs > 0) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                onSeek((fraction * totalDurationUs).toLong())
                            }
                        }
                    )
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "▶ ${formatDuration(currentPositionUs)}",
                    color = AccentPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Scrub ◄►",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
                Text(
                    text = formatDuration(totalDurationUs),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Timeline Track Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(DarkBackground)
                .pointerInput(totalDurationUs) {
                    detectTapGestures { offset ->
                        if (totalDurationUs > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val targetUs = (fraction * totalDurationUs).toLong()
                            onSeek(targetUs)
                        }
                    }
                }
                .pointerInput(totalDurationUs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (totalDurationUs > 0) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeek((fraction * totalDurationUs).toLong())
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (totalDurationUs > 0) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                onSeek((fraction * totalDurationUs).toLong())
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Video Clip Track
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (videoClips.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurface)
                                .clickable { onAddMediaClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = AccentPrimary)
                                Spacer(Modifier.width(6.dp))
                                Text("Tap to Import Video", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else {
                        var runningOffsetUs = 0L
                        videoClips.forEachIndexed { index, clip ->
                            val isSelected = clip.id == selectedClipId
                            val weight = (clip.effectiveDurationUs.toFloat() / maxOf(1L, totalDurationUs).toFloat()).coerceAtLeast(0.08f)
                            val clipStartUs = runningOffsetUs
                            runningOffsetUs += clip.effectiveDurationUs

                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AccentPrimary.copy(alpha = 0.25f) else TimelineClipBg)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) AccentPrimary else TimelineClipBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onSelectClip(clip.id)
                                        onSeek(clipStartUs)
                                    }
                                    .padding(6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = "Clip ${index + 1}",
                                        color = if (isSelected) AccentPrimary else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatDuration(clip.effectiveDurationUs)} (${clip.speed}x)",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Audio Track (Music/Sound)
                if (audioTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        audioTracks.forEach { track ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AudioTrackColor.copy(alpha = 0.3f))
                                    .border(1.dp, AudioTrackColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "♫ ${track.name}",
                                    color = AudioTrackColor,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Overlay Track (Text/Stickers)
                if (overlays.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        overlays.forEach { overlay ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(OverlayTrackColor.copy(alpha = 0.3f))
                                    .border(1.dp, OverlayTrackColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "T: ${overlay.text.ifBlank { "Image" }}",
                                    color = OverlayTrackColor,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Playhead Indicator
            if (totalDurationUs > 0) {
                val progressFraction = (currentPositionUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f)
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val playheadX = maxWidth * progressFraction
                    Box(
                        modifier = Modifier
                            .offset(x = (playheadX - 1.5.dp).coerceAtLeast(0.dp))
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(TimelinePlayhead)
                    )
                }
            }
        }
    }
}

private fun formatDuration(us: Long): String {
    val totalSec = us / 1_000_000L
    val mins = totalSec / 60
    val secs = totalSec % 60
    val millis = (us % 1_000_000L) / 100_000L
    return String.format(Locale.US, "%02d:%02d.%01d", mins, secs, millis)
}
