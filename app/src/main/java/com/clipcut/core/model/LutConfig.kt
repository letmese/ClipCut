package com.clipcut.core.model

import java.util.UUID

data class LutConfig(
    val id: String = UUID.randomUUID().toString(),
    val uri: String = "",
    val name: String = "Custom",
    val intensity: Float = 1.0f
)
