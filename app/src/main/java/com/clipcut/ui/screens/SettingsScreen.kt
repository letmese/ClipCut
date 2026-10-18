package com.clipcut.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipcut.BuildConfig
import com.clipcut.ui.theme.*

/**
 * SettingsScreen — Stitch Obsidian Lumina Studio Preferences & Hardware Configuration.
 * Modeled from leading professional editing suites (InShot VideoSetting, VN PreferencesSettings,
 * and Premiere Pro Hardware preferences):
 *   - Video Export & Hardware Master (Resolution, 120fps, ProRes, AV1, 2x Bitrate, Canvas Auto-Adapt, GPU Decoders)
 *   - Timeline & Editing Preferences (Photo duration, Transitions, Fit/Fill, Ripple Delete, Magnetic Snapping)
 *   - Playback & Engine Performance (Preview HDR mode, Auto-Save interval, Haptics)
 *   - Theme & Studio Accent Palette
 *   - Storage & Cache (Live calculation gauge and 1-tap cache purger)
 *   - Diagnostics & Live System Log Viewer
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenLogs: () -> Unit = {},
    viewModel: com.clipcut.ui.viewmodel.SettingsViewModel = viewModel()
) {
    val vm = viewModel
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
            Surface(
                color = Surface1,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Surface2)
                                .border(1.dp, HairlineBorder, CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "Studio Preferences",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "HARDWARE & TIMELINE CONFIG",
                                color = TextTertiary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ElectricCyan.copy(alpha = 0.18f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "PRO",
                                color = ElectricCyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VibrantViolet.copy(alpha = 0.18f))
                                .border(1.dp, VibrantViolet.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "STUDIO",
                                color = Color(0xFFC6BFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. VIDEO EXPORT & HARDWARE MASTER ──
            StudioSettingsCard(
                title = "Video Export & Hardware Master",
                icon = Icons.Filled.VideoSettings,
                accentColor = ElectricCyan
            ) {
                Text("Default Resolution", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                SegmentedRow(listOf("720p", "1080p", "2K", "4K"), res) { vm.setExportRes(it) }
                Spacer(Modifier.height(14.dp))

                Text("Default Frame Rate", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                SegmentedRow(listOf("24 fps", "30 fps", "50 fps", "60 fps", "120 fps"), "${fps} fps") { 
                    vm.setExportFps(it.replace(" fps", "").toInt()) 
                }
                Spacer(Modifier.height(14.dp))

                Text("Mastering Codec", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                SegmentedRow(listOf("H264", "H265 (HEVC)", "ProRes", "AV1"), codec) { 
                    vm.setExportCodec(it.split(" ")[0]) 
                }
                Spacer(Modifier.height(12.dp))

                HorizontalDivider(color = Color(0x14FFFFFF))
                Spacer(Modifier.height(4.dp))

                StudioToggle(
                    label = "High Bitrate Mastering Mode",
                    desc = "Preserves fine texture and color fidelity with 2x target bitrate",
                    value = highBitrateExport
                ) { vm.setHighBitrateExport(it) }

                StudioToggle(
                    label = "Follow Video Frame (Canvas Auto-Adapt)",
                    desc = "Automatically match project aspect ratio to first imported video clip",
                    value = followVideoFrame
                ) { vm.setFollowVideoFrame(it) }

                StudioToggle(
                    label = "Hardware Decoder & GPU Acceleration",
                    desc = "Utilizes MediaCodec surface pipelines and zero-copy OpenGL textures",
                    value = hwAccel
                ) { vm.setHwAccel(it) }
            }

            // ── 2. TIMELINE & EDITING PREFERENCES ──
            StudioSettingsCard(
                title = "Timeline & Editing Preferences",
                icon = Icons.Filled.Timeline,
                accentColor = VibrantViolet
            ) {
                Text("Default Photo Duration", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                SegmentedRow(listOf("1.0s", "3.0s", "5.0s", "10.0s"), "${defaultPhotoDuration}s") {
                    vm.setDefaultPhotoDuration(it.replace("s", "").toFloat())
                }
                Spacer(Modifier.height(14.dp))

                Text("Default Transition Duration", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                SegmentedRow(listOf("0.5s", "1.0s", "1.5s", "2.0s"), "${defaultTransitionDuration}s") {
                    vm.setDefaultTransitionDuration(it.replace("s", "").toFloat())
                }
                Spacer(Modifier.height(14.dp))

                Text("Default Clip Fill Style", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                SegmentedRow(
                    options = listOf("Fit (Letterbox)", "Fill (Crop)"),
                    current = if (mediaFillStyle == "fill") "Fill (Crop)" else "Fit (Letterbox)"
                ) {
                    vm.setMediaFillStyle(if (it.startsWith("Fill")) "fill" else "fit")
                }
                Spacer(Modifier.height(12.dp))

                HorizontalDivider(color = Color(0x14FFFFFF))
                Spacer(Modifier.height(4.dp))

                StudioToggle(
                    label = "Audio & Censor Ripple Delete",
                    desc = "Automatically delete linked voiceovers, music markers, and blur boxes when video clip is deleted",
                    value = audioRippleDelete
                ) { vm.setAudioRippleDelete(it) }

                StudioToggle(
                    label = "Magnetic Timeline Snapping",
                    desc = "Snap playhead and clip edges to cuts, beat markers, and keyframe points",
                    value = gestures
                ) { vm.setGestures(it) }
            }

            // ── 3. PLAYBACK & ENGINE PERFORMANCE ──
            StudioSettingsCard(
                title = "Playback & Engine Performance",
                icon = Icons.Filled.Speed,
                accentColor = LuminescentAmber
            ) {
                Text("Preview Playback Quality", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                SegmentedRow(
                    options = listOf("Smooth (720p 30fps)", "Full Quality (Native)"),
                    current = if (previewPerfMode == "native_1080p") "Full Quality (Native)" else "Smooth (720p 30fps)"
                ) {
                    vm.setPreviewPerformanceMode(if (it.startsWith("Full")) "native_1080p" else "smooth_720p")
                }
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Save Interval", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("${autoSave}s", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Slider(
                    value = autoSave.toFloat(),
                    onValueChange = { vm.setAutoSave(it.toInt()) },
                    valueRange = 5f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPrimary,
                        activeTrackColor = AccentPrimary,
                        inactiveTrackColor = Surface3
                    )
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Feedback Intensity", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(LuminescentAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            when (haptic) {
                                0 -> "Off"
                                1 -> "Subtle"
                                2 -> "Medium"
                                else -> "Strong"
                            },
                            color = LuminescentAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Slider(
                    value = haptic.toFloat(),
                    onValueChange = { vm.setHaptic(it.toInt()) },
                    valueRange = 0f..3f,
                    steps = 2,
                    colors = SliderDefaults.colors(
                        thumbColor = LuminescentAmber,
                        activeTrackColor = LuminescentAmber,
                        inactiveTrackColor = Color(0xFF2A2934)
                    )
                )
            }

            // ── 4. THEME & CREATIVE ACCENT ──
            StudioSettingsCard(
                title = "Theme & Creative Accent",
                icon = Icons.Filled.Palette,
                accentColor = AccentPrimary
            ) {
                Text("App Mode", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                SegmentedRow(listOf("Dark", "Light", "System"), theme) { vm.setTheme(it.lowercase()) }
                Spacer(Modifier.height(14.dp))

                Text("Studio Accent Palette", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("#FACC15", "#00F2FE", "#7C3AED", "#10B981", "#E94560", "#4EA8DE", "#FF7043", "#F472B6").forEach { c ->
                        val selected = accent.equals(c, ignoreCase = true)
                        val parsedColor = runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrDefault(LuminescentAmber)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(parsedColor)
                                .border(
                                    if (selected) 2.5.dp else 1.dp,
                                    if (selected) TextPrimary else HairlineBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { vm.setAccent(c) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (c == "#FACC15" || c == "#00F2FE") Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── 5. STORAGE & RENDER CACHE ──
            StudioSettingsCard(
                title = "Storage & Render Cache",
                icon = Icons.Filled.Storage,
                accentColor = EmeraldWave
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Temporary Video & Proxy Cache", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(EmeraldWave.copy(alpha = 0.18f))
                            .border(1.dp, EmeraldWave.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("${realCacheMb} MB Used", color = EmeraldWave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Cached video frames, preview proxies, and temporary optical flow renders can be purged safely without losing saved projects.",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        val freed = realCacheMb
                        vm.clearCache()
                        realCacheMb = vm.getCacheSizeMb()
                        Toast.makeText(ctx, "Purged ${freed} MB temporary cache", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202536)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldWave.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            tint = EmeraldWave,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Purge Render Cache & Free Space",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ── 6. ABOUT & SYSTEM DIAGNOSTICS ──
            StudioSettingsCard(
                title = "About & System Diagnostics",
                icon = Icons.Filled.Info,
                accentColor = Color(0xFF4407FD)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ClipCut PRO Studio",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            color = AccentPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF4407FD).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFF4407FD).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("OFFLINE CORE", color = Color(0xFFD0C4FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pure on-device video suite • 6-tool Creative Studio • Multi-track Timeline • MediaPipe & OpenCV Motion Tracking • 10-Band Graphic EQ • VN Velocity Curve Engine • Obsidian Lumina Design System",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onOpenLogs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AccentPrimary, AccentSecondary, VibrantViolet)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Open Live Diagnostic Log Viewer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StudioSettingsCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                title.uppercase(),
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
        content()
    }
}

@Composable
private fun SegmentedRow(options: List<String>, current: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
            .padding(4.dp)
    ) {
        options.forEach { opt ->
            val selected = current.equals(opt, ignoreCase = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) AccentPrimary else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(opt) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    color = if (selected) {
                        if (ThemeState.accentColor == LuminescentAmber || ThemeState.accentColor == ElectricCyan) Color.Black else Color.White
                    } else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StudioToggle(
    label: String,
    desc: String? = null,
    value: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (desc != null) {
                Spacer(Modifier.height(2.dp))
                Text(desc, color = TextTertiary, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (ThemeState.accentColor == LuminescentAmber || ThemeState.accentColor == ElectricCyan) Color.Black else Color.White,
                checkedTrackColor = AccentPrimary,
                uncheckedTrackColor = Surface3,
                uncheckedThumbColor = TextTertiary
            )
        )
    }
}
