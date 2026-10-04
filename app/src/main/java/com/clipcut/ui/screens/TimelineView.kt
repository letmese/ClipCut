package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.TimelineItem
import com.clipcut.ui.theme.*
import java.util.Locale

private const val MIN_CLIP_DURATION_US = 100_000L  // 100ms

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
    onTrimClip: (String, Long, Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurfaceElevated)
            .padding(vertical = 4.dp)
    ) {
        // -------- Scrubber / Ruler Bar --------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(DarkSurface)
                .pointerInput(totalDurationUs) {
                    detectTapGestures { offset ->
                        if (totalDurationUs > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((fraction * totalDurationUs).toLong())
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
                        onDragEnd = { },
                        onDragCancel = { },
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

        // -------- Timeline Track Area --------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(DarkBackground)
                .pointerInput(totalDurationUs) {
                    detectTapGestures { offset ->
                        if (totalDurationUs > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((fraction * totalDurationUs).toLong())
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
                        onDragEnd = { },
                        onDragCancel = { },
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
                // ---------- Video Clip Track ----------
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
                            val weight = (clip.effectiveDurationUs.toFloat() /
                                    maxOf(1L, totalDurationUs).toFloat())
                                .coerceAtLeast(0.08f)
                            val clipStartUs = runningOffsetUs
                            runningOffsetUs += clip.effectiveDurationUs

                            TimelineClip(
                                clip = clip,
                                index = index,
                                isSelected = isSelected,
                                clipWeight = weight,
                                clipStartUs = clipStartUs,
                                onSelectClip = onSelectClip,
                                onSeek = onSeek,
                                onTrimClip = onTrimClip
                            )
                        }
                    }
                }

                // ---------- Audio Track ----------
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

                // ---------- Overlay Track ----------
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

            // ---------- Playhead Indicator (Non-Interactive Overlay) ----------
            if (totalDurationUs > 0) {
                val progressFraction =
                    (currentPositionUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f)
                BoxWithConstraints(
                    modifier = Modifier.matchParentSize()
                ) {
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

/**
 * One clip row with local trim state. Commits to the ViewModel on drag end.
 */
@Composable
private fun RowScope.TimelineClip(
    clip: TimelineItem,
    index: Int,
    isSelected: Boolean,
    clipWeight: Float,
    clipStartUs: Long,
    onSelectClip: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .weight(clipWeight)
            .fillMaxHeight()
    ) {
        val density = LocalDensity.current
        val clipWidthPx = with(density) { maxWidth.toPx() }

        var pendingTrimStartUs by remember(clip.id, clip.trimStartUs) {
            mutableStateOf(clip.trimStartUs)
        }
        var pendingTrimEndUs by remember(clip.id, clip.trimEndUs) {
            mutableStateOf(clip.trimEndUs)
        }

        val pixelsPerSourceUs = remember(clipWidthPx, clip.trimmedDurationUs) {
            if (clip.trimmedDurationUs > 0L) {
                clipWidthPx / clip.trimmedDurationUs.toFloat()
            } else 0f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) AccentPrimary.copy(alpha = 0.25f) else TimelineClipBg
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) AccentPrimary else TimelineClipBorder,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable {
                    onSelectClip(clip.id)
                    onSeek(clipStartUs)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isSelected) 24.dp else 6.dp,
                        vertical = 6.dp
                    ),
                verticalArrangement = Arrangement.Center
            ) {
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

            if (isSelected) {
                // -------- Left trim handle --------
                TrimHandle(
                    edge = TrimEdge.LEFT,
                    pixelsPerUs = pixelsPerSourceUs,
                    modifier = Modifier.align(Alignment.CenterStart),
                    onDragDeltaUs = { deltaUs ->
                        val newStart = (pendingTrimStartUs + deltaUs)
                            .coerceIn(0L, pendingTrimEndUs - MIN_CLIP_DURATION_US)
                        pendingTrimStartUs = newStart
                    },
                    onDragEnd = {
                        onTrimClip(clip.id, pendingTrimStartUs, pendingTrimEndUs)
                    }
                )

                // -------- Right trim handle --------
                TrimHandle(
                    edge = TrimEdge.RIGHT,
                    pixelsPerUs = pixelsPerSourceUs,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onDragDeltaUs = { deltaUs ->
                        val newEnd = (pendingTrimEndUs + deltaUs)
                            .coerceIn(
                                pendingTrimStartUs + MIN_CLIP_DURATION_US,
                                clip.sourceDurationUs
                            )
                        pendingTrimEndUs = newEnd
                    },
                    onDragEnd = {
                        onTrimClip(clip.id, pendingTrimStartUs, pendingTrimEndUs)
                    }
                )
            }
        }
    }
}

enum class TrimEdge { LEFT, RIGHT }

/**
 * Draggable trim handle. Claims the gesture on the first down so the parent
 * scrub detector never sees the event.
 */
@Composable
private fun TrimHandle(
    edge: TrimEdge,
    pixelsPerUs: Float,
    onDragDeltaUs: (Long) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val touchWidth = 32.dp
    val visualWidth = 6.dp
    val pixelsPerUsState = rememberUpdatedState(pixelsPerUs)

    Box(
        modifier = modifier
            .width(touchWidth)
            .fillMaxHeight()
            .pointerInput(edge) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    down.consume()

                    drag(down.id) { change ->
                        val deltaPx = change.positionChange().x
                        if (deltaPx != 0f) {
                            val ppus = pixelsPerUsState.value
                            if (ppus > 0f) {
                                val deltaUs = (deltaPx / ppus).toLong()
                                if (deltaUs != 0L) {
                                    onDragDeltaUs(deltaUs)
                                }
                            }
                            change.consume()
                        }
                    }

                    onDragEnd()
                }
            },
        contentAlignment = if (edge == TrimEdge.LEFT) Alignment.CenterStart
                           else Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(visualWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(AccentPrimary)
        )
    }
}

private fun formatDuration(us: Long): String {
    val totalSec = us / 1_000_000L
    val mins = totalSec / 60
    val secs = totalSec % 60
    val millis = (us % 1_000_000L) / 100_000L
    return String.format(Locale.US, "%02d:%02d.%01d", mins, secs, millis)
}
