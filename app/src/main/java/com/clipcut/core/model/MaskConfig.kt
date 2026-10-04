package com.clipcut.core.model

enum class MaskShape(val displayName: String) {
    NONE("None"),
    LINEAR("Linear"),
    RADIAL("Radial"),
    RECTANGLE("Rectangle"),
    OVAL("Oval")
}

data class MaskConfig(
    val shape: MaskShape = MaskShape.NONE,
    val centerX: Float = 0.5f,      // normalized 0..1
    val centerY: Float = 0.5f,
    val width: Float = 0.6f,        // normalized 0..1
    val height: Float = 0.6f,
    val rotation: Float = 0f,       // degrees
    val feather: Float = 0.05f,     // 0 = hard edge, 1 = very soft
    val inverted: Boolean = false
)
