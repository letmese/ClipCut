package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.clipcut.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySheet(
    totalDurationUs: Long,
    onAddOverlay: (OverlayItem) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0xFFFFFFFFL) }
    var textSizeSp by remember { mutableStateOf(28f) }
    var startSec by remember { mutableStateOf(0f) }
    var durationSec by remember { mutableStateOf(minOf(5f, totalDurationUs / 1_000_000f).coerceAtLeast(1f)) }

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
                .navigationBarsPadding()
        ) {
            Text(
                text = "Add Text Overlay",
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
                valueRange = 16f..56f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentPrimary,
                    activeTrackColor = AccentPrimary,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            Spacer(Modifier.height(12.dp))

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
                                timelineEndUs = endUs
                            )
                        )
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
        }
    }
}
