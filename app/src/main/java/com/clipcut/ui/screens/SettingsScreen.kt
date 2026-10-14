package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipcut.ui.components.PrimaryButton
import com.clipcut.ui.components.SecondaryButton
import com.clipcut.ui.theme.*

/**
 * SettingsScreen — wraps SettingsViewModel. Sections per spec:
 *   - Theme (Dark/Light/System)
 *   - Accent color (8 presets)
 *   - Default export (resolution, codec, frame rate)
 *   - Auto-save interval
 *   - Cache size limit + Clear cache
 *   - Hardware acceleration toggle
 *   - Gesture controls toggle
 *   - Haptic feedback level
 *   - About + version + open source licenses
 *
 * MainActivity reads the SharedPreferences written here on next launch.
 * EditorScreen reads the same prefs to inform GlPreviewRenderer flags.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenLogs: () -> Unit = {}
) {
    val vm: com.clipcut.ui.viewmodel.SettingsViewModel = viewModel()
    val theme by vm.theme.collectAsState()
    val accent by vm.accent.collectAsState()
    val res by vm.exportRes.collectAsState()
    val codec by vm.exportCodec.collectAsState()
    val fps by vm.exportFps.collectAsState()
    val autoSave by vm.autoSaveSeconds.collectAsState()
    val hwAccel by vm.hwAccel.collectAsState()
    val gestures by vm.gestures.collectAsState()
    val haptic by vm.hapticLevel.collectAsState()
    val cacheMb by vm.cacheLimitMb.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Surface(color = Surface1, tonalElevation = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Settings & Preferences", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection("Appearance") {
                SegmentedRow(listOf("Dark", "Light", "System"), theme) { vm.setTheme(it.lowercase()) }
                Spacer(Modifier.height(12.dp))
                Text("Accent color", color = TextSecondary, fontSize = 12.sp)
                Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#00E5FF", "#FFB703", "#E94560", "#06D6A0", "#4EA8DE", "#9B5DE5", "#FF7043", "#F472B6").forEach { c ->
                        val selected = accent.equals(c, ignoreCase = true)
                        val parsedColor = runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrDefault(AccentPrimary)
                        Box(modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(parsedColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { vm.setAccent(c) }
                            .then(if (selected) Modifier.padding(2.dp) else Modifier)
                        )
                    }
                }
            }
            SettingsSection("Export Defaults") {
                Text("Resolution", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(listOf("720p", "1080p", "4K"), res) { vm.setExportRes(it) }
                Spacer(Modifier.height(8.dp))
                Text("Codec", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(listOf("H264", "H265"), codec) { vm.setExportCodec(it) }
                Spacer(Modifier.height(8.dp))
                Text("Frame Rate", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(listOf("24", "30", "60"), fps.toString()) { vm.setExportFps(it.toInt()) }
            }
            SettingsSection("Editor & Processing") {
                Text("Auto-save interval: ${autoSave}s", color = Color.White, fontSize = 12.sp)
                Slider(value = autoSave.toFloat(), onValueChange = { vm.setAutoSave(it.toInt()) }, valueRange = 5f..120f, colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary))
                Toggle("GPU Hardware acceleration", hwAccel) { vm.setHwAccel(it) }
                Toggle("Gesture controls", gestures) { vm.setGestures(it) }
                Text("Haptic feedback level: $haptic", color = Color.White, fontSize = 12.sp)
                Slider(value = haptic.toFloat(), onValueChange = { vm.setHaptic(it.toInt()) }, valueRange = 0f..3f, colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary))
            }
            SettingsSection("Storage & Cache") {
                Text("Cache limit: $cacheMb MB", color = Color.White, fontSize = 12.sp)
                Slider(value = cacheMb.toFloat(), onValueChange = { vm.setCacheLimit(it.toInt()) }, valueRange = 256f..8192f, colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary))
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Clear Cache & Temp Frames", onClick = { vm.clearCache() }, modifier = Modifier.fillMaxWidth())
            }
            SettingsSection("About & Diagnostics") {
                Text("ClipCut PRO v1.2.54 (Build 65)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("Kotlin 2.1.20 • Compose BOM 2024.12.01 • Media3 1.5.0 • OpenCV & MediaPipe", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "View Diagnostics & App Logs",
                    onClick = onOpenLogs,
                    icon = Icons.Filled.BugReport,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Open source licenses", color = AccentPrimary, fontSize = 12.sp, modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { })
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    Surface(color = Surface1, shape = RoundedCornerShape(0.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
    HorizontalDivider(color = HairlineBorder)
}

@Composable
private fun SegmentedRow(options: List<String>, current: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        options.forEach { opt ->
            val selected = current.equals(opt, ignoreCase = true)
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) AccentPrimary else Surface3)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(opt) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(opt, color = if (selected) Color.White else TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = AccentPrimary, checkedTrackColor = AccentPrimary.copy(alpha = 0.4f)))
    }
}
