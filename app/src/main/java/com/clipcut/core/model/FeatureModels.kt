package com.clipcut.core.model

import java.util.UUID

/**
 * FeatureModels — feature models introduced from the 7 decompiled benchmark video editors:
 *   - StabilizationConfig  (CapCut video stabilization)
 *   - BrandKitConfig       (VN Brand Kit)
 *   - ProjectTemplate      (VN Storyline templates)
 */

/** CapCut video stabilization parameters. */
data class StabilizationConfig(
    val strength: Float = 0f,    // 0..1
    val angleLimit: Float = 0f,  // degrees
    val cropRatio: Float = 0f    // 0..0.25
) {
    val enabled: Boolean get() = strength > 0f
    companion object {
        val NONE = StabilizationConfig()
    }
}

/** VN Brand Kit — reusable brand identity exported over every project. */
data class BrandKitConfig(
    val brandColor: Long = 0xFF00E5FFL,
    val logoUri: String? = null,
    val watermarkText: String = "",
    val watermarkPosIndex: Int = 0,
    val watermarkScale: Float = 0.06f
) {
    val watermarkPosition: Pair<Float, Float>
        get() = when (watermarkPosIndex) {
            0 -> 0.50f to 0.92f
            1 -> 0.08f to 0.08f
            2 -> 0.92f to 0.08f
            3 -> 0.08f to 0.92f
            4 -> 0.92f to 0.92f
            else -> 0.50f to 0.92f
        }
}
