package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.MaskConfig
import com.clipcut.core.model.MaskShape
import com.clipcut.ui.theme.*
import java.util.Locale

@Composable
fun MaskDialog(
    initial: MaskConfig,
    onApply: (MaskConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var config by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mask", color = TextPrimary) },
        text = {
            Column {
                Text("Shape", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MaskShape.values().toList()) { shape ->
                        val sel = config.shape == shape
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) AccentPrimary else DarkSurface)
                                .clickable {
                                    config = config.copy(
                                        shape = shape,
                                        centerX = 0.5f, centerY = 0.5f
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(shape.displayName, color = if (sel) TextPrimary else TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                MaskSlider("Width", config.width, 0.05f..1f) {
                    config = config.copy(width = it)
                }
                MaskSlider("Height", config.height, 0.05f..1f) {
                    config = config.copy(height = it)
                }
                MaskSlider("Feather", config.feather, 0f..0.5f) {
                    config = config.copy(feather = it)
                }
                MaskSlider("Rotation", config.rotation, -180f..180f) {
                    config = config.copy(rotation = it)
                }
                MaskSlider("Center X", config.centerX, 0f..1f) {
                    config = config.copy(centerX = it)
                }
                MaskSlider("Center Y", config.centerY, 0f..1f) {
                    config = config.copy(centerY = it)
                }

                Spacer(Modifier.height(8.dp))
                Row {
                    Checkbox(checked = config.inverted, onCheckedChange = { config = config.copy(inverted = it) })
                    Text("Invert Mask", color = TextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(config); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(MaskConfig()); onDismiss() }) {
                Text("Remove", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
private fun MaskSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(String.format(Locale.US, "%.2f", value), color = TextPrimary, fontSize = 12.sp)
        }
        Slider(
            value = value, onValueChange = onValue, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
        )
    }
}
