package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.ui.theme.*
import kotlinx.coroutines.delay

/**
 * TeleprompterScreen — on-device scrolling teleprompter studio:
 * type a script, set scroll speed and font size, mirror the text for glass rigs,
 * and play / pause / reset the auto-scroll smoothly.
 */
@Composable
fun TeleprompterScreen(onNavigateBack: () -> Unit) {
    var script by remember {
        mutableStateOf(
            "Welcome to ClipCut Pro Teleprompter!\n\n" +
            "Type or paste your script here.\n\n" +
            "Press Play below to begin auto-scrolling at your preferred reading speed.\n\n" +
            "Use the Mirror toggle if using a glass teleprompter reflection rig."
        )
    }
    var speed by remember { mutableStateOf(4f) }
    var fontSize by remember { mutableStateOf(28f) }
    var mirror by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(running, speed) {
        while (running) {
            scrollState.scrollBy(speed * 1.5f)
            delay(60)
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Surface(color = Surface1, tonalElevation = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Teleprompter Studio", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = if (mirror) -1f else 1f }
                        .verticalScroll(scrollState)
                ) {
                    Spacer(Modifier.height(180.dp))
                    Text(
                        script,
                        color = Color.White,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.35f).sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(350.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { running = true },
                        enabled = !running,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = DarkBackground, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { running = false },
                        enabled = running,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Surface3)
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = {
                            running = false
                            kotlinx.coroutines.runBlocking { scrollState.scrollTo(0) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Speed", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 1f..10f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                    Text("${speed.toInt()}", color = AccentPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Font", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 16f..48f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                    )
                    Text("${fontSize.toInt()}sp", color = AccentPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mirror (Rig Display)", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = mirror,
                        onCheckedChange = { mirror = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentPrimary, checkedThumbColor = Color.White)
                    )
                }
            }
        }
    }
}
