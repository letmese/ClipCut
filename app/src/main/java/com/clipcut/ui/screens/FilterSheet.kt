package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.FilterConfig
import com.clipcut.core.model.FilterType
import com.clipcut.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    initialConfig: FilterConfig,
    onApplyFilter: (FilterConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Color & Filter FX",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                TextButton(onClick = {
                    config = FilterConfig()
                    onApplyFilter(config)
                }) {
                    Text("Reset", color = AccentPrimary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Presets Carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(FilterType.values()) { fType ->
                    val isSelected = config.filterType == fType
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) AccentPrimary else DarkSurface)
                            .border(1.dp, if (isSelected) AccentPrimary else DarkSurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                config = config.copy(filterType = fType)
                                onApplyFilter(config)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fType.displayName,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sliders
            FilterSlider(
                label = "Brightness",
                value = config.brightness,
                range = -0.5f..0.5f,
                onValueChange = {
                    config = config.copy(brightness = it)
                    onApplyFilter(config)
                }
            )

            FilterSlider(
                label = "Contrast",
                value = config.contrast,
                range = 0.5f..1.8f,
                onValueChange = {
                    config = config.copy(contrast = it)
                    onApplyFilter(config)
                }
            )

            FilterSlider(
                label = "Saturation",
                value = config.saturation,
                range = 0.0f..2.0f,
                onValueChange = {
                    config = config.copy(saturation = it)
                    onApplyFilter(config)
                }
            )

            FilterSlider(
                label = "Warmth",
                value = config.temperature,
                range = -0.5f..0.5f,
                onValueChange = {
                    config = config.copy(temperature = it)
                    onApplyFilter(config)
                }
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FilterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(String.format(Locale.US, "%.2f", value), color = TextPrimary, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AccentPrimary,
                activeTrackColor = AccentPrimary,
                inactiveTrackColor = DarkSurfaceBorder
            )
        )
    }
}
