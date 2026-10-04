package com.clipcut.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.clipcut.core.model.ExportCodec
import com.clipcut.core.model.ExportConfig
import com.clipcut.core.model.ExportResolution
import com.clipcut.ui.theme.*
import com.clipcut.ui.viewmodel.ExportState
import java.io.File
import java.util.Locale

@Composable
fun ExportDialog(
    exportState: ExportState,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit,
    onViewLogsClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedRes by remember { mutableStateOf(ExportResolution.RES_1080P) }
    var selectedCodec by remember { mutableStateOf(ExportCodec.H264) }
    var frameRate by remember { mutableStateOf(30) }

    Dialog(onDismissRequest = {
        if (exportState !is ExportState.Progress) onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (exportState) {
                    is ExportState.Idle -> {
                        Text(
                            text = "Export Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(16.dp))

                        // Resolution Selector
                        Text("Resolution", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExportResolution.values().forEach { res ->
                                val isSelected = selectedRes == res
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentPrimary else DarkSurface)
                                        .border(1.dp, if (isSelected) AccentPrimary else DarkSurfaceBorder, RoundedCornerShape(8.dp))
                                        .clickable { selectedRes = res }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = res.label.substringBefore(" "),
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Codec Selector
                        Text("Video Codec", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExportCodec.values().forEach { c ->
                                val isSelected = selectedCodec == c
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentPrimary else DarkSurface)
                                        .border(1.dp, if (isSelected) AccentPrimary else DarkSurfaceBorder, RoundedCornerShape(8.dp))
                                        .clickable { selectedCodec = c }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = c.label,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    val cfg = ExportConfig(
                                        resolution = selectedRes,
                                        customWidth = selectedRes.width,
                                        customHeight = selectedRes.height,
                                        frameRate = frameRate,
                                        bitRate = selectedRes.defaultBitrate,
                                        codec = selectedCodec
                                    )
                                    onStartExport(cfg)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Export MP4", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is ExportState.Progress -> {
                        Text(
                            text = "Exporting Video...",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = { exportState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentPrimary,
                            trackColor = DarkSurface
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "${(exportState.progress * 100).toInt()}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPrimary
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = exportState.status,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = onCancelExport,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel Export")
                        }
                    }

                    is ExportState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AudioTrackColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Export Complete!",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = exportState.file.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "Size: %.2f MB", exportState.file.length() / (1024.0 * 1024.0)),
                            fontSize = 11.sp,
                            color = TextTertiary
                        )
                        Spacer(Modifier.height(12.dp))

                        // Public Gallery confirmation badge
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AudioTrackColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AudioTrackColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Saved to Device Gallery",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AudioTrackColor
                                    )
                                    Text(
                                        text = "${exportState.galleryPath}/${exportState.file.name}",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Open in Video Player Button
                        Button(
                            onClick = {
                                openVideoFile(context, exportState.file, exportState.publicUri)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Open in Video Player", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Share Button
                        OutlinedButton(
                            onClick = {
                                shareExportedFile(context, exportState.file, exportState.publicUri)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share Video")
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(onClick = onDismiss) {
                            Text("Done", color = TextSecondary)
                        }
                    }

                    is ExportState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Export Failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = exportState.message,
                            fontSize = 12.sp,
                            color = AccentPrimary,
                            maxLines = 4
                        )
                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = onViewLogsClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("View & Share Diagnostic Logs", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(onClick = onDismiss) {
                            Text("Dismiss", color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun openVideoFile(context: android.content.Context, file: File, publicUri: Uri?) {
    try {
        val uri = publicUri ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        com.clipcut.core.log.AppLog.e("ExportDialog", "Failed to open video file", e)
    }
}

private fun shareExportedFile(context: android.content.Context, file: File, publicUri: Uri?) {
    try {
        val uri = publicUri ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Video"))
    } catch (e: Exception) {
        com.clipcut.core.log.AppLog.e("ExportDialog", "Failed to share video file", e)
    }
}
