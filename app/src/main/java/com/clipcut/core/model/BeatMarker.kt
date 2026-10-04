package com.clipcut.core.model

import java.util.UUID

data class BeatMarker(
    val id: String = UUID.randomUUID().toString(),
    val timeUs: Long,
    val strength: Float = 1.0f   // 0..1, for visual hierarchy
)
