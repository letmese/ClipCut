package com.clipcut.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSheet(
    originalVideoVolume: Float,
    backgroundMusicVolume: Float,
    hasMusicTrack: Boolean,
    onVolumesChanged: (orig: Float, music: Float) -> Unit,
    onPickMusicClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var origVol by remember { mutableStateOf(originalVideoVolume) }
    var musicVol by remember { mutableStateOf(backgroundMusicVolume) }

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
                text = "Audio & Music Mixing",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Spacer(Modifier.height(16.dp))

            // Original Video Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Video Audio Volume", color = TextSecondary, fontSize = 13.sp)
                Text("${(origVol * 100).toInt()}%", color = TextPrimary, fontSize = 13.sp)
            }
            Slider(
                value = origVol,
                onValueChange = {
                    origVol = it
                    onVolumesChanged(origVol, musicVol)
                },
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentPrimary,
                    activeTrackColor = AccentPrimary,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            Spacer(Modifier.height(12.dp))

            // Background Music Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Background Music Volume", color = TextSecondary, fontSize = 13.sp)
                Text("${(musicVol * 100).toInt()}%", color = TextPrimary, fontSize = 13.sp)
            }
            Slider(
                value = musicVol,
                onValueChange = {
                    musicVol = it
                    onVolumesChanged(origVol, musicVol)
                },
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = AudioTrackColor,
                    activeTrackColor = AudioTrackColor,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    onPickMusicClick()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AudioTrackColor),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = AudioTrackColor)
                Spacer(Modifier.width(8.dp))
                Text(if (hasMusicTrack) "Replace Background Music" else "Import Background Music")
            }

            Spacer(Modifier.height(12.dp))

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
