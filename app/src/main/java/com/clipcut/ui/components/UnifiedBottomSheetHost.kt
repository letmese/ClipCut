package com.clipcut.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clipcut.ui.theme.AccentPrimary
import com.clipcut.ui.theme.DarkSurface
import com.clipcut.ui.theme.HairlineBorder

/**
 * UnifiedBottomSheetHost — the single ModalBottomSheet used by the editor
 * for ALL tool panels. The `selected` EditorTool determines which `content`
 * slot is rendered. Provides the drag handle, title, and Done button so
 * every tool panel has consistent chrome.
 *
 * State preservation across tool switches is provided by the parent
 * EditorViewModel; each sheet panel reads from the same VM StateFlows, so
 * swapping tools does not discard in-progress values.
 *
 * `onDismiss` is invoked on swipe-down or by tapping the scrim.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedBottomSheetHost(
    title: String,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean,
    content: @Composable () -> Unit
) {
    if (!visible) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        containerColor = DarkSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { SheetDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDone) {
                    Text("Done", color = AccentPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .size(width = 36.dp, height = 4.dp)
            .let { it.then(Modifier) },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.size(width = 36.dp, height = 4.dp),
            color = HairlineBorder,
            shape = RoundedCornerShape(2.dp),
            content = {}
        )
    }
}
