package com.clipcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.ui.theme.AccentPrimary
import com.clipcut.ui.theme.DarkSurface
import com.clipcut.ui.theme.HairlineBorder
import com.clipcut.ui.theme.Surface2

/**
 * EditorTool — the 22 tools that appear in the horizontal toolbar at the
 * bottom of the editor. Each enum value carries its icon and resourceId.
 * The selected tool drives which sheet the UnifiedBottomSheetHost renders.
 */
enum class EditorTool(val label: String, val icon: ImageVector) {
    UNDO("Undo", Icons.Filled.Undo),
    REDO("Redo", Icons.Filled.Redo),
    SPLIT("Split", Icons.Filled.ContentCut),
    DELETE("Delete", Icons.Filled.Delete),
    TRIM("Trim", Icons.Filled.ArrowBack),
    SPEED("Speed", Icons.Filled.Speed),
    VOLUME("Volume", Icons.Filled.VolumeUp),
    ROTATE("Rotate", Icons.Filled.Rotate90DegreesCw),
    CROP("Crop", Icons.Filled.Crop),
    FLIP("Flip", Icons.Filled.FlipCameraAndroid),
    FILTER("Filter", Icons.Filled.PhotoFilter),
    ADJUST("Adjust", Icons.Filled.Tune),
    MASK("Mask", Icons.Filled.FormatShapes),
    CHROMA("Chroma", Icons.Filled.Colorize),
    BLEND("Blend", Icons.Filled.Layers),
    BLUR("Blur", Icons.Filled.BlurOn),
    TEXT("Text", Icons.Filled.TextFields),
    STICKER("Sticker", Icons.Filled.EmojiEmotions),
    AUDIO("Audio", Icons.Filled.MusicNote),
    OVERLAY("Overlay", Icons.Filled.ViewInAr),
    EFFECTS("Effects", Icons.Filled.AutoAwesome),
    TRANSITIONS("Transitions", Icons.Filled.SwapHoriz),
    KEYFRAMES("Keyframes", Icons.Filled.Diamond),
    MORE("More", Icons.Filled.MoreHoriz)
}

/**
 * EditorToolbar — horizontal scrollable list of all 22 tools.
 *
 *  - Active tool highlighted with AccentPrimary; inactive 60% white.
 *  - Auto-scrolls to keep selected tool in view (LaunchedEffect animates
 *    the LazyRow list state to the selected index).
 *  - Haptic feedback on selection (LongPress).
 *  - UNDO / REDO disabled when the respective stacks are empty.
 */
@Composable
fun EditorToolbar(
    selected: EditorTool?,
    onSelect: (EditorTool) -> Unit,
    canUndo: Boolean = true,
    canRedo: Boolean = true,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(selected) {
        if (selected != null) {
            val idx = EditorTool.values().indexOf(selected)
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
            }
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(86.dp).padding(horizontal = 6.dp, vertical = 4.dp),
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(EditorTool.values().toList()) { tool ->
                val enabled = when (tool) {
                    EditorTool.UNDO -> canUndo
                    EditorTool.REDO -> canRedo
                    else -> true
                }
                ToolButton(tool = tool, selected = selected == tool, enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (enabled) onSelect(tool)
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    tool: EditorTool,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val fg = when {
        selected -> AccentPrimary
        !enabled -> Color.White.copy(alpha = 0.3f)
        else -> Color.White.copy(alpha = 0.9f)
    }
    val tileBg = when {
        selected -> AccentPrimary.copy(alpha = 0.22f)
        else -> Surface2
    }
    val tileBorder = when {
        selected -> AccentPrimary
        else -> HairlineBorder
    }

    Column(
        modifier = Modifier
            .width(68.dp)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileBg)
                .border(1.2.dp, tileBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(tool.icon, contentDescription = tool.label, tint = fg, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            tool.label,
            color = if (selected) AccentPrimary else Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
