package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.log.AppLog
import com.clipcut.core.log.LogEntry
import com.clipcut.ui.components.SecondaryButton
import com.clipcut.ui.theme.*

/**
 * LogViewerScreen — displays AppLog.logsFlow as a scrolling monospace list.
 * Each entry is colored by level. Action row:
 *   - Clear (writes empty list + recreates log file)
 *   - Share (AppLog.shareLogs via FileProvider chooser)
 *   - Flush (writes accumulated entries to disk)
 *
 * The screen is reachable from the editor top-bar bug icon and the
 * ProjectList settings button.
 */
@Composable
fun LogViewerScreen(onNavigateBack: () -> Unit) {
    val ctx = LocalContext.current
    val logs by AppLog.logsFlow.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Surface(
                color = Surface1,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                    Text("Logs (${logs.size})", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { AppLog.clear(ctx) }) { Icon(Icons.Filled.Delete, contentDescription = "Clear", tint = TextSecondary) }
                    IconButton(onClick = { AppLog.shareLogs(ctx) }) { Icon(Icons.Filled.Share, contentDescription = "Share", tint = TextSecondary) }
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            items(logs) { e ->
                LogRow(e)
            }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry) {
    val color = when (e.level) {
        LogEntry.Level.VERBOSE -> TextTertiary
        LogEntry.Level.DEBUG -> TextSecondary
        LogEntry.Level.INFO -> AccentSecondary
        LogEntry.Level.WARN -> AccentGold
        LogEntry.Level.ERROR -> AccentPrimary
        LogEntry.Level.FATAL -> AccentPrimary
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("${e.formattedTime} ${e.tag}: ${e.message}", color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        e.throwable?.let {
            Text(it.stackTraceToString().take(800), color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
