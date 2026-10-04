package com.clipcut.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.BeatMarker
import com.clipcut.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatMarkerSheet(
    totalDurationUs: Long,
    existingMarkers: List<BeatMarker>,
    onAddMarker: (Long) -> Unit,
    onRemoveMarker: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var tapTimeSec by remember { mutableStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkSurfaceBorder) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()
        ) {
            Text("Beat Markers", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap the button at each beat to add a marker. Use markers to snap cuts.",
                color = TextTertiary, fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))

            val totalSec = totalDurationUs / 1_000_000f
            Text(
                "Time: ${String.format(Locale.US, "%.2f", tapTimeSec)}s / ${String.format(Locale.US, "%.2f", totalSec)}s",
                color = TextSecondary, fontSize = 12.sp
            )
            Slider(
                value = tapTimeSec,
                onValueChange = { tapTimeSec = it },
                valueRange = 0f..totalSec.coerceAtLeast(0.1f),
                colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAddMarker((tapTimeSec * 1_000_000L).toLong()) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) { Text("Add Beat") }
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f)
                ) { Text("Clear All", color = AccentPrimary) }
            }

            Spacer(Modifier.height(16.dp))
            Text("Existing (${existingMarkers.size})", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            existingMarkers.forEach { marker ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        String.format(Locale.US, "%.2fs", marker.timeUs / 1_000_000.0),
                        color = TextPrimary, fontSize = 13.sp
                    )
                    TextButton(onClick = { onRemoveMarker(marker.id) }) {
                        Text("Remove", color = AccentPrimary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) { Text("Done") }
        }
    }
}
