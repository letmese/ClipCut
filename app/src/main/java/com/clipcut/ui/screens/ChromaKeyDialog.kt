package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.ui.theme.*
import java.util.Locale

@Composable
fun ChromaKeyDialog(
    initialColor: FloatArray,
    initialThreshold: Float,
    initialSmoothness: Float,
    onApply: (FloatArray, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var color by remember { mutableStateOf(initialColor) }
    var threshold by remember { mutableStateOf(initialThreshold) }
    var smoothness by remember { mutableStateOf(initialSmoothness) }

    val presets = listOf(
        Triple("Green", floatArrayOf(0f, 1f, 0f), Color.Green),
        Triple("Blue", floatArrayOf(0f, 0f, 1f), Color.Blue),
        Triple("Cyan", floatArrayOf(0f, 1f, 1f), Color.Cyan)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chroma Key", color = TextPrimary) },
        text = {
            Column {
                Text("Key Color", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { (name, rgb, uiColor) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(uiColor)
                                .clickable { color = rgb }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                CKSlider("Threshold", threshold, 0f..0.4f) { threshold = it }
                CKSlider("Smoothness", smoothness, 0.005f..0.15f) { smoothness = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(color, threshold, smoothness); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
private fun CKSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(String.format(Locale.US, "%.2f", value), color = TextPrimary, fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary))
    }
}
