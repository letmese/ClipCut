package com.clipcut.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.log.AppLog
import com.clipcut.core.log.LogEntry
import com.clipcut.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val allLogs by AppLog.logsFlow.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogEntry.Level?>(null) }

    val filteredLogs = remember(allLogs, searchQuery, selectedLevel) {
        allLogs.filter { entry ->
            val matchesLevel = selectedLevel == null || entry.level == selectedLevel ||
                    (selectedLevel == LogEntry.Level.ERROR && entry.level == LogEntry.Level.FATAL)
            val matchesSearch = searchQuery.isBlank() ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    entry.message.contains(searchQuery, ignoreCase = true) ||
                    (entry.throwable?.message?.contains(searchQuery, ignoreCase = true) == true)
            matchesLevel && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Copy All
                    IconButton(onClick = {
                        val text = AppLog.getFullLogText(context)
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "Full logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy All Logs")
                    }

                    // Share File
                    IconButton(onClick = {
                        AppLog.shareLogs(context)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Log File", tint = AccentPrimary)
                    }

                    // Clear
                    IconButton(onClick = {
                        AppLog.clear(context)
                        Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search and Filters Bar
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search logs by tag or keyword...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextTertiary)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = DarkSurfaceBorder,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Level filter chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LevelFilterChip("ALL", isSelected = selectedLevel == null) { selectedLevel = null }
                        LevelFilterChip("ERROR", isSelected = selectedLevel == LogEntry.Level.ERROR, color = AccentPrimary) {
                            selectedLevel = if (selectedLevel == LogEntry.Level.ERROR) null else LogEntry.Level.ERROR
                        }
                        LevelFilterChip("WARN", isSelected = selectedLevel == LogEntry.Level.WARN, color = AccentGold) {
                            selectedLevel = if (selectedLevel == LogEntry.Level.WARN) null else LogEntry.Level.WARN
                        }
                        LevelFilterChip("INFO", isSelected = selectedLevel == LogEntry.Level.INFO, color = AccentSecondary) {
                            selectedLevel = if (selectedLevel == LogEntry.Level.INFO) null else LogEntry.Level.INFO
                        }
                        LevelFilterChip("DEBUG", isSelected = selectedLevel == LogEntry.Level.DEBUG, color = TextSecondary) {
                            selectedLevel = if (selectedLevel == LogEntry.Level.DEBUG) null else LogEntry.Level.DEBUG
                        }
                    }
                }
            }

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Showing ${filteredLogs.size} / ${allLogs.size} entries",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
                Text(
                    text = "Tap any entry to copy",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }

            // Log entries list
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (allLogs.isEmpty()) "No logs recorded yet" else "No matching logs found",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs) { entry ->
                        LogEntryCard(entry = entry) {
                            val line = entry.toLogLine()
                            clipboardManager.setText(AnnotatedString(line))
                            Toast.makeText(context, "Log entry copied", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelFilterChip(
    text: String,
    isSelected: Boolean,
    color: Color = AccentPrimary,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) color else DarkSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LogEntryCard(
    entry: LogEntry,
    onClick: () -> Unit
) {
    val levelColor = when (entry.level) {
        LogEntry.Level.VERBOSE -> TextTertiary
        LogEntry.Level.DEBUG -> TextSecondary
        LogEntry.Level.INFO -> AccentSecondary
        LogEntry.Level.WARN -> AccentGold
        LogEntry.Level.ERROR, LogEntry.Level.FATAL -> AccentPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurfaceElevated)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[${entry.level.name.first()}]",
                    color = levelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.formattedTime,
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.tag,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(3.dp))

            Text(
                text = entry.message,
                color = if (entry.level == LogEntry.Level.ERROR || entry.level == LogEntry.Level.FATAL) AccentPrimaryVariant else TextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            if (entry.throwable != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.throwable.stackTraceToString().take(500),
                    color = AccentPrimary.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
