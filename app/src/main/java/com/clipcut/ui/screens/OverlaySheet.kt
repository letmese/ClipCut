package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.TextAnimation
import com.clipcut.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySheet(
    totalDurationUs: Long,
    initialPlayheadUs: Long = 0L,
    existingOverlays: List<OverlayItem> = emptyList(),
    onAddOverlay: (OverlayItem) -> Unit,
    onDeleteOverlay: (String) -> Unit = {},
    onSeekPreview: (Long) -> Unit = {},
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0xFFFFFFFFL) }
    var textSizeSp by remember { mutableStateOf(28f) }
    var selectedAnimation by remember { mutableStateOf(TextAnimation.NONE) }

    val totalSec = maxOf(0.5f, totalDurationUs / 1_000_000f)
    var startSec by remember {
        mutableStateOf((initialPlayheadUs / 1_000_000f).coerceIn(0f, maxOf(0f, totalSec - 0.5f)))
    }
    var durationSec by remember {
        mutableStateOf(minOf(3f, totalSec - startSec).coerceAtLeast(0.5f))
    }

    val colorOptions = listOf(
        0xFFFFFFFFL, // White
        0xFFFFD166L, // Gold/Yellow
        0xFFE94560L, // Red
        0xFF00ADB5L, // Cyan
        0xFF06D6A0L, // Green
        0xFF000000L  // Black
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkSurfaceBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Text(
                text = "Text & Sticker Overlays",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Enter text") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = DarkSurfaceBorder
                )
            )

            Spacer(Modifier.height(16.dp))

            // Color Picker
            Text("Text Color", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colorOptions.forEach { cLong ->
                    val isSelected = selectedColor == cLong
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(cLong))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) AccentPrimary else DarkSurfaceBorder,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = cLong }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Size Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Font Size", color = TextSecondary, fontSize = 12.sp)
                Text("${textSizeSp.toInt()} sp", color = TextPrimary, fontSize = 12.sp)
            }
            Slider(
                value = textSizeSp,
                onValueChange = { textSizeSp = it },
                valueRange = 16f..64f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentPrimary,
                    activeTrackColor = AccentPrimary,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            Spacer(Modifier.height(12.dp))

            // Start Time Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Start Time", color = TextSecondary, fontSize = 12.sp)
                Text(String.format(Locale.US, "%.1fs", startSec), color = AccentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = startSec,
                onValueChange = {
                    startSec = it
                    durationSec = durationSec.coerceIn(0.5f, maxOf(0.5f, totalSec - startSec))
                },
                onValueChangeFinished = {
                    onSeekPreview((startSec * 1_000_000L).toLong())
                },
                valueRange = 0f..maxOf(0.1f, totalSec - 0.5f),
                colors = SliderDefaults.colors(
                    thumbColor = AccentPrimary,
                    activeTrackColor = AccentPrimary,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            Spacer(Modifier.height(12.dp))

            // Duration Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duration", color = TextSecondary, fontSize = 12.sp)
                Text(String.format(Locale.US, "%.1fs", durationSec), color = AccentPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = durationSec,
                onValueChange = { durationSec = it },
                valueRange = 0.5f..maxOf(0.5f, totalSec - startSec),
                colors = SliderDefaults.colors(
                    thumbColor = AccentPrimary,
                    activeTrackColor = AccentPrimary,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            Spacer(Modifier.height(16.dp))

            TextAnimationPicker(
                selected = selectedAnimation,
                onSelect = { selectedAnimation = it }
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        val startUs = (startSec * 1_000_000L).toLong()
                        val endUs = startUs + (durationSec * 1_000_000L).toLong()
                        onAddOverlay(
                            OverlayItem(
                                text = text,
                                textColor = selectedColor,
                                textSizeSp = textSizeSp,
                                timelineStartUs = startUs,
                                timelineEndUs = endUs,
                                animation = selectedAnimation,
                                animationDurationMs = 500
                            )
                        )
                        text = ""
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(10.dp),
                enabled = text.isNotBlank()
            ) {
                Text("Add Overlay", fontWeight = FontWeight.Bold)
            }

            // List of existing overlays with delete button
            if (existingOverlays.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Current Overlays (${existingOverlays.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                existingOverlays.forEach { overlay ->
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = overlay.text,
                                    color = Color(overlay.textColor),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1fs - %.1fs", overlay.timelineStartUs / 1_000_000.0, overlay.timelineEndUs / 1_000_000.0),
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { onDeleteOverlay(overlay.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
