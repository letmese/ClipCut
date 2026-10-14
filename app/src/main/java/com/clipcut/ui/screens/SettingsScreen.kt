package com.clipcut.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipcut.BuildConfig
import com.clipcut.ui.components.SecondaryButton
import com.clipcut.ui.theme.*

/**
 * SettingsScreen — Modern professional editing preferences.
 * Extracted and modeled from decompiled InShot (VideoSettingFragment) and
 * VN Editor (PreferencesSettingsActivity, PreviewPerformanceActivity):
 *   - Video Export Defaults (Resolution, FPS, Codec, High Bitrate, Follow Video Frame)
 *   - Editing Preferences (Default Photo Duration, Transition Duration, Fill Style, Audio Ripple Delete)
 *   - Playback & Performance (Preview Quality Mode, GPU Acceleration, Gestures, Haptics)
 *   - Storage & Cache (Real-time MB calculation & 1-tap cache cleaner)
 *   - Diagnostics & Live System Log Viewer
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenLogs: () -> Unit = {}
) {
    val vm: com.clipcut.ui.viewmodel.SettingsViewModel = viewModel()
    val ctx = LocalContext.current

    val theme by vm.theme.collectAsState()
    val accent by vm.accent.collectAsState()
    val res by vm.exportRes.collectAsState()
    val codec by vm.exportCodec.collectAsState()
    val fps by vm.exportFps.collectAsState()
    val autoSave by vm.autoSaveSeconds.collectAsState()
    val hwAccel by vm.hwAccel.collectAsState()
    val gestures by vm.gestures.collectAsState()
    val haptic by vm.hapticLevel.collectAsState()
    val cacheLimitMb by vm.cacheLimitMb.collectAsState()
    val followVideoFrame by vm.followVideoFrame.collectAsState()
    val defaultPhotoDuration by vm.defaultPhotoDuration.collectAsState()
    val defaultTransitionDuration by vm.defaultTransitionDuration.collectAsState()
    val mediaFillStyle by vm.mediaFillStyle.collectAsState()
    val audioRippleDelete by vm.audioRippleDelete.collectAsState()
    val previewPerfMode by vm.previewPerformanceMode.collectAsState()
    val highBitrateExport by vm.highBitrateExport.collectAsState()

    var realCacheMb by remember { mutableLongStateOf(vm.getCacheSizeMb()) }

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
                    Text(
                        "Preferences & Settings",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
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
            // ── VIDEO EXPORT DEFAULTS (InShot VideoSettingFragment) ──
            SettingsSection("Video Export Defaults") {
                Text("Default Resolution", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(listOf("720p", "1080p", "2K", "4K"), res) { vm.setExportRes(it) }
                Spacer(Modifier.height(10.dp))

                Text("Default Frame Rate", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(listOf("24 fps", "30 fps", "50 fps", "60 fps"), "${fps} fps") { 
                    vm.setExportFps(it.replace(" fps", "").toInt()) 
                }
                Spacer(Modifier.height(10.dp))

                Text("Encoding Codec", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(listOf("H264", "H265 (HEVC)"), codec) { vm.setExportCodec(it.split(" ")[0]) }
                Spacer(Modifier.height(6.dp))

                Toggle(
                    label = "High Bitrate Mastering Mode",
                    desc = "Preserves fine texture and color fidelity with 2x bitrate",
                    value = highBitrateExport
                ) { vm.setHighBitrateExport(it) }

                Toggle(
                    label = "Follow Video Frame (Canvas Auto-Adapt)",
                    desc = "Automatically match project aspect ratio to first imported video clip",
                    value = followVideoFrame
                ) { vm.setFollowVideoFrame(it) }
            }

            // ── EDITING PREFERENCES (VN PreferencesSettingsActivity) ──
            SettingsSection("Timeline & Editing Preferences") {
                Text("Default Photo Duration", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(listOf("3.0s", "5.0s", "10.0s"), "${defaultPhotoDuration}s") {
                    vm.setDefaultPhotoDuration(it.replace("s", "").toFloat())
                }
                Spacer(Modifier.height(10.dp))

                Text("Default Transition Duration", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(listOf("0.5s", "1.0s", "1.5s"), "${defaultTransitionDuration}s") {
                    vm.setDefaultTransitionDuration(it.replace("s", "").toFloat())
                }
                Spacer(Modifier.height(10.dp))

                Text("Default Clip Fill Style", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(
                    options = listOf("Fit (Letterbox)", "Fill (Crop)"),
                    current = if (mediaFillStyle == "fill") "Fill (Crop)" else "Fit (Letterbox)"
                ) {
                    vm.setMediaFillStyle(if (it.startsWith("Fill")) "fill" else "fit")
                }
                Spacer(Modifier.height(6.dp))

                Toggle(
                    label = "Audio & Censor Ripple Delete",
                    desc = "Automatically delete linked voiceovers, music markers, and blur boxes when video clip is deleted",
                    value = audioRippleDelete
                ) { vm.setAudioRippleDelete(it) }

                Toggle(
                    label = "Magnetic Timeline Snapping",
                    desc = "Snap playhead to clip cuts, markers, and keyframe points",
                    value = gestures
                ) { vm.setGestures(it) }
            }

            // ── PLAYBACK & PERFORMANCE (VN PreviewPerformanceActivity) ──
            SettingsSection("Playback & Performance") {
                Text("Preview Playback Quality", color = TextSecondary, fontSize = 12.sp)
                SegmentedRow(
                    options = listOf("Smooth (720p 30fps)", "Full Quality (Native)"),
                    current = if (previewPerfMode == "native_1080p") "Full Quality (Native)" else "Smooth (720p 30fps)"
                ) {
                    vm.setPreviewPerformanceMode(if (it.startsWith("Full")) "native_1080p" else "smooth_720p")
                }
                Spacer(Modifier.height(8.dp))

                Toggle(
                    label = "Hardware Decoder & GPU Acceleration",
                    desc = "Utilizes MediaCodec hardware surface pipelines for smooth playback",
                    value = hwAccel
                ) { vm.setHwAccel(it) }

                Text("Auto-save interval: ${autoSave}s", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = autoSave.toFloat(),
                    onValueChange = { vm.setAutoSave(it.toInt()) },
                    valueRange = 5f..120f,
                    colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                )

                Text("Haptic Feedback Intensity: $haptic", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = haptic.toFloat(),
                    onValueChange = { vm.setHaptic(it.toInt()) },
                    valueRange = 0f..3f,
                    colors = SliderDefaults.colors(thumbColor = AccentPrimary, activeTrackColor = AccentPrimary)
                )
            }

            // ── APPEARANCE & THEME ──
            SettingsSection("Theme & Accent") {
                SegmentedRow(listOf("Dark", "Light", "System"), theme) { vm.setTheme(it.lowercase()) }
                Spacer(Modifier.height(12.dp))
                Text("Accent Color", color = TextSecondary, fontSize = 12.sp)
                Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#00E5FF", "#FFB703", "#E94560", "#06D6A0", "#4EA8DE", "#9B5DE5", "#FF7043", "#F472B6").forEach { c ->
                        val selected = accent.equals(c, ignoreCase = true)
                        val parsedColor = runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrDefault(AccentPrimary)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(parsedColor)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { vm.setAccent(c) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // ── STORAGE & CACHE ──
            SettingsSection("Storage & Cache") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Temporary Video & Proxy Cache", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${realCacheMb} MB Used", color = AccentPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cached video frames, preview proxies, and temporary renders can be cleared safely without losing projects.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Clear Cache & Free Space (${realCacheMb} MB)",
                    onClick = {
                        val freed = realCacheMb
                        vm.clearCache()
                        realCacheMb = vm.getCacheSizeMb()
                        Toast.makeText(ctx, "Cleared ${freed} MB temporary cache", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── ABOUT & DIAGNOSTICS ──
            SettingsSection("About & Diagnostics") {
                Text(
                    "ClipCut PRO v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pure on-device video suite • 6-tool Creative Studio • Multi-track Timeline • MediaPipe & OpenCV Motion Tracking • 10-Band Graphic EQ • VN Velocity Curve Engine",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(14.dp))
                SecondaryButton(
                    text = "View Live Diagnostics & System Logs",
                    onClick = onOpenLogs,
                    icon = Icons.Filled.BugReport,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        color = AccentPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
    Surface(color = Surface1, shape = RoundedCornerShape(0.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
    HorizontalDivider(color = HairlineBorder)
}

@Composable
private fun SegmentedRow(options: List<String>, current: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        options.forEach { opt ->
            val selected = current.equals(opt, ignoreCase = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) AccentPrimary else Surface3)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(opt) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    color = if (selected) Color.Black else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun Toggle(label: String, desc: String? = null, value: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (desc != null) {
                Text(desc, color = TextTertiary, fontSize = 11.sp)
            }
        }
        Switch(
            checked = value,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AccentPrimary
            )
        )
    }
}
