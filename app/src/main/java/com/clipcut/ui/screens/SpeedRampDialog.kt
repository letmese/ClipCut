package com.clipcut.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.core.model.SpeedKeyframe
import com.clipcut.core.model.SpeedRamp
import com.clipcut.ui.theme.*

@Composable
fun SpeedRampDialog(
    initialRamp: SpeedRamp,
    clipDurationUs: Long,
    onApply: (SpeedRamp) -> Unit,
    onDismiss: () -> Unit
) {
    var keyframes by remember {
        mutableStateOf(initialRamp.keyframes.ifEmpty {
            listOf(
                SpeedKeyframe(timeOffsetUs = 0L, speed = 1.0f),
                SpeedKeyframe(timeOffsetUs = clipDurationUs, speed = 1.0f)
            )
        })
    }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed Ramp", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Drag points vertically to change speed. Tap empty space to add.",
                    color = TextTertiary, fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(clipDurationUs) {
                            detectTapGestures { offset ->
                                val time = (offset.x / size.width * clipDurationUs).toLong()
                                val speed = (1f - offset.y / size.height) * 4f
                                keyframes = (keyframes + SpeedKeyframe(
                                    timeOffsetUs = time,
                                    speed = speed.coerceIn(0.25f, 4f)
                                )).sortedBy { it.timeOffsetUs }
                            }
                        }
                        .pointerInput(clipDurationUs) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    var closest = -1
                                    var minDist = Float.MAX_VALUE
                                    keyframes.forEachIndexed { i, kf ->
                                        val x = kf.timeOffsetUs.toFloat() / clipDurationUs * size.width
                                        val y = (1f - kf.speed / 4f) * size.height
                                        val d = kotlin.math.hypot(offset.x - x, offset.y - y)
                                        if (d < minDist) { minDist = d; closest = i }
                                    }
                                    draggingIndex = if (minDist < 60f) closest else null
                                },
                                onDragEnd = { draggingIndex = null },
                                onDrag = { change, _ ->
                                    val idx = draggingIndex ?: return@detectDragGestures
                                    val kf = keyframes[idx]
                                    val newTime = (change.position.x / size.width * clipDurationUs)
                                        .toLong().coerceIn(0L, clipDurationUs)
                                    val newSpeed = ((1f - change.position.y / size.height) * 4f)
                                        .coerceIn(0.25f, 4f)
                                    keyframes = keyframes.toMutableList().also {
                                        it[idx] = kf.copy(timeOffsetUs = newTime, speed = newSpeed)
                                    }.sortedBy { it.timeOffsetUs }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (i in 0..4) {
                            val y = size.height * i / 4f
                            drawLine(DarkSurfaceBorder, Offset(0f, y), Offset(size.width, y), 1f)
                        }
                        if (keyframes.size >= 2) {
                            val path = Path()
                            keyframes.sortedBy { it.timeOffsetUs }.forEachIndexed { i, kf ->
                                val x = kf.timeOffsetUs.toFloat() / clipDurationUs * size.width
                                val y = (1f - kf.speed / 4f) * size.height
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color = AccentPrimary, style = Stroke(width = 4f))
                        }
                        keyframes.forEach { kf ->
                            val x = kf.timeOffsetUs.toFloat() / clipDurationUs * size.width
                            val y = (1f - kf.speed / 4f) * size.height
                            drawCircle(AccentGold, radius = 10f, center = Offset(x, y))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(SpeedRamp(keyframes.sortedBy { it.timeOffsetUs }))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurfaceElevated
    )
}
