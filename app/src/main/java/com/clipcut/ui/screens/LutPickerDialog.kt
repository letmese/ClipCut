package com.clipcut.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.LutConfig
import com.clipcut.ui.theme.*

@Composable
fun LutPickerDialog(
    currentLut: LutConfig?,
    onApply: (LutConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    val lutPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            onApply(LutConfig(uri = it.toString(), name = it.lastPathSegment ?: "Custom", intensity = 1.0f))
            onDismiss()
        }
    }

    var intensity by remember { mutableStateOf(currentLut?.intensity ?: 1.0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cinematic LUT", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Import a .cube or HALD .png LUT for professional color grading.",
                    color = TextTertiary, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                if (currentLut != null) {
                    Text("Current: ${currentLut.name}", color = AccentSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("Intensity: ${(intensity * 100).toInt()}%", color = TextSecondary)
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentPrimary,
                            activeTrackColor = AccentPrimary
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onApply(null); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Remove LUT", color = AccentPrimary) }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { lutPicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) { Text("Import LUT File") }
            }
        },
        confirmButton = {
            if (currentLut != null) {
                Button(
                    onClick = {
                        onApply(currentLut.copy(intensity = intensity))
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) { Text("Apply") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
        },
        containerColor = DarkSurfaceElevated
    )
}
