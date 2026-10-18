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

enum class BrandLogoStyle(val label: String, val description: String) {
    NEON_PILL("Neon Pill", "Glowing accent outline with dark capsule backdrop"),
    GLASS_CARD("Glass Card", "Frosted glass card with dual-line branding"),
    MINIMAL_WATERMARK("Minimal", "Clean transparent watermark with subtle drop shadow"),
    CINEMA_STAMP("Cinema Stamp", "Letterbox cinema badge with tracked header"),
    BADGE_CREST("Badge Crest", "Bold creator shield & crest with icon avatar"),
    OUTRO_SIGNATURE("Signature", "Handwritten signature aesthetic with flourish underline")
}

enum class BrandIconType(val label: String, val glyph: String) {
    VERIFIED("Verified", "✓"),
    CROWN("Crown", "👑"),
    DIAMOND("Diamond", "💎"),
    FLAME("Flame", "🔥"),
    STAR("Star", "★"),
    CAMERA("Cinema", "🎬"),
    PODCAST("Podcast", "🎙️"),
    MUSIC("Music", "♪"),
    YOUTUBE("YouTube", "▶"),
    TIKTOK("TikTok", "♫"),
    INSTAGRAM("Instagram", "◎"),
    TWITTER("X / Twitter", "𝕏"),
    NONE("No Icon", "")
}

/** VN Brand Kit — reusable brand identity exported over every project. */
data class BrandKitConfig(
    val brandColor: Long = 0xFF00E5FFL,
    val logoUri: String? = null,
    val watermarkText: String = "",
    val tagline: String = "",
    val watermarkPosIndex: Int = 0,
    val watermarkScale: Float = 1.0f,
    val opacity: Float = 0.90f,
    val fontFamily: FontFamilyOption = FontFamilyOption.MONTSERRAT,
    val logoStyle: BrandLogoStyle = BrandLogoStyle.NEON_PILL,
    val iconType: BrandIconType = BrandIconType.VERIFIED
) {
    val watermarkPosition: Pair<Float, Float>
        get() = when (watermarkPosIndex) {
            0 -> 0.50f to 0.92f // Bottom Center
            1 -> 0.08f to 0.08f // Top Left
            2 -> 0.92f to 0.08f // Top Right
            3 -> 0.08f to 0.92f // Bottom Left
            4 -> 0.92f to 0.92f // Bottom Right
            5 -> 0.50f to 0.08f // Top Center
            else -> 0.50f to 0.92f
        }
}
