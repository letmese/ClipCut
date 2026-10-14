package com.clipcut.core.model

import java.util.UUID

enum class MediaType { VIDEO, AUDIO, IMAGE }

enum class FilterType(val displayName: String) {
    NONE("Original"), BRIGHTNESS("Brightness"), CONTRAST("Contrast"),
    SATURATION("Saturation"), TEMPERATURE("Temperature"),
    VIGNETTE("Vignette"), GRAYSCALE("B&W"), SEPIA("Sepia")
}

data class FilterConfig(
    val filterType: FilterType = FilterType.NONE,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperature: Float = 0.0f,
    val vignette: Float = 0.0f,
    val tint: Float = 0.0f
)

enum class TransitionType(val displayName: String) {
    NONE("None"), FADE("Fade"), SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"), SLIDE_UP("Slide Up"), SLIDE_DOWN("Slide Down"),
    DISSOLVE("Dissolve"), WIPE_LEFT("Wipe Left"), WIPE_RIGHT("Wipe Right"),
    WIPE_UP("Wipe Up"), WIPE_DOWN("Wipe Down"), CIRCLE_CROP("Circle Open"),
    CIRCLE_CLOSE("Circle Close"), CROSS_ZOOM("Cross Zoom"), ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"), FLASH_WHITE("Flash White"), FLASH_BLACK("Flash Black"),
    GLITCH("Glitch"), PIXELATE("Pixelate"), BLUR("Blur"),
    ROTATE("Rotate"), RIPPLE("Ripple"), HEART("Heart"),
    STAR("Star"), CURTAIN("Curtain"),
    ZOOM_BLUR("Zoom Blur"), ROTATE_ZOOM("Rotate Zoom"),
    SHAKE("Camera Shake"), FLOODLIGHT("Floodlight"),
    SPIN("Dynamic Spin"), BLINK("Strobe Blink"),
    SPLIT_V("Vertical Split"), SPLIT_H("Horizontal Split")
}

enum class AspectRatio(val label: String, val wRatio: Float, val hRatio: Float, val description: String = "") {
    ORIGINAL("Original", 0f, 0f, "Keep source"),
    R_16_9("16:9", 16f, 9f, "Widescreen"),
    R_9_16("9:16", 9f, 16f, "Vertical / TikTok"),
    R_1_1("1:1", 1f, 1f, "Square / Instagram"),
    R_4_5("4:5", 4f, 5f, "Portrait"),
    R_4_3("4:3", 4f, 3f, "Classic"),
    R_21_9("21:9", 21f, 9f, "Cinematic"),
    FREE("Free", 0f, 0f, "Custom");

    fun calculateExportDimensions(resolution: ExportResolution): Pair<Int, Int> {
        if (this == ORIGINAL || this == FREE) return resolution.width to resolution.height
        val baseDim = minOf(resolution.width, resolution.height) // e.g. 1080 for RES_1080P
        val (w, h) = if (wRatio >= hRatio) {
            // Landscape or square: short side is baseDim (e.g. 1080), long side is baseDim * (wRatio / hRatio) (e.g. 1920)
            val hVal = baseDim
            val wVal = (baseDim * (wRatio / hRatio)).toInt()
            wVal to hVal
        } else {
            // Portrait / Vertical (e.g. 9:16 TikTok): short side (width) is baseDim (e.g. 1080), height is baseDim * (hRatio / wRatio) (e.g. 1920)
            val wVal = baseDim
            val hVal = (baseDim * (hRatio / wRatio)).toInt()
            wVal to hVal
        }
        // Codecs require even dimensions
        return (w + (w % 2)) to (h + (h % 2))
    }

    companion object {
        fun fromLabel(label: String): AspectRatio =
            values().firstOrNull { it.label == label } ?: ORIGINAL
    }
}

enum class FontFamilyOption(val label: String, val assetPath: String? = null) {
    DEFAULT("Default", null),
    SERIF("Serif", null),
    MONOSPACE("Mono", null),
    SANS_SERIF_CONDENSED("Condensed", null),
    BEBAS("Bebas Pro", "fonts/BEBAS.ttf"),
    LOBSTER("Lobster", "fonts/Lobster_1.3.otf"),
    LUCKIEST_GUY("Luckiest Guy", "fonts/LuckiestGuy-Regular.ttf"),
    PERMANENT_MARKER("Marker", "fonts/PermanentMarker.ttf"),
    BLACKOUT("Blackout", "fonts/Blackout-2am.ttf"),
    AMATIC("Amatic SC", "fonts/AmaticSC-Regular.ttf"),
    CAVIAR_DREAMS("Caviar Dreams", "fonts/CaviarDreams.ttf")
}

data class CropRect(
    val left: Float = 0f, val top: Float = 0f,
    val right: Float = 1f, val bottom: Float = 1f
) {
    val width: Float get() = (right - left).coerceIn(0f, 1f)
    val height: Float get() = (bottom - top).coerceIn(0f, 1f)
    val isValid: Boolean get() = width > 0.01f && height > 0.01f
}

data class SpeedKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetUs: Long,
    val speed: Float
)

data class SpeedRamp(val keyframes: List<SpeedKeyframe> = emptyList()) {
    val isActive: Boolean get() = keyframes.size >= 2
    fun speedAt(timeOffsetUs: Long): Float {
        if (!isActive) return 1f
        val sorted = keyframes.sortedBy { it.timeOffsetUs }
        if (timeOffsetUs <= sorted.first().timeOffsetUs) return sorted.first().speed
        if (timeOffsetUs >= sorted.last().timeOffsetUs) return sorted.last().speed
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            if (timeOffsetUs in a.timeOffsetUs..b.timeOffsetUs) {
                val t = (timeOffsetUs - a.timeOffsetUs).toFloat() / (b.timeOffsetUs - a.timeOffsetUs).coerceAtLeast(1L)
                return a.speed + (b.speed - a.speed) * t
            }
        }
        return 1f
    }
}

enum class MaskShape(val displayName: String) {
    NONE("None"), LINEAR("Linear"), RADIAL("Radial"),
    RECTANGLE("Rectangle"), OVAL("Oval")
}

data class MaskConfig(
    val shape: MaskShape = MaskShape.NONE,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.6f,
    val height: Float = 0.6f,
    val rotation: Float = 0f,
    val feather: Float = 0.05f,
    val inverted: Boolean = false
)

enum class BlendMode(val displayName: String) {
    NORMAL("Normal"), MULTIPLY("Multiply"), SCREEN("Screen"),
    OVERLAY("Overlay"), SOFT_LIGHT("Soft Light"), HARD_LIGHT("Hard Light"),
    COLOR_BURN("Color Burn"), COLOR_DODGE("Color Dodge"),
    DARKEN("Darken"), LIGHTEN("Lighten"),
    DIFFERENCE("Difference"), EXCLUSION("Exclusion")
}

data class LutConfig(
    val id: String = UUID.randomUUID().toString(),
    val uri: String = "",
    val name: String = "Custom",
    val intensity: Float = 1.0f
)

enum class TextAnimation(val displayName: String) {
    NONE("None"), FADE_IN("Fade In"), TYPEWRITER("Typewriter"),
    BOUNCE("Bounce"), SLIDE_LEFT("Slide Left"), SLIDE_UP("Slide Up"),
    SCALE_IN("Scale In"), BLUR_IN("Blur In")
}

data class OverlayKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetUs: Long,
    val normalizedX: Float,
    val normalizedY: Float,
    val scale: Float,
    val rotation: Float
)

data class OverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val textColor: Long = 0xFFFFFFFFL,
    val textSizeSp: Float = 28f,
    val textBold: Boolean = true,
    val textItalic: Boolean = false,
    val fontFamily: FontFamilyOption = FontFamilyOption.DEFAULT,
    val backgroundColor: Long = 0x00000000L,
    val backgroundPaddingPx: Int = 16,
    val strokeColor: Long = 0x00000000L,
    val strokeWidthPx: Float = 0f,
    val dropShadow: Boolean = false,
    val imageUri: String? = null,
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotation: Float = 0.0f,
    val timelineStartUs: Long = 0L,
    val timelineEndUs: Long = 5_000_000L,
    val keyframes: List<OverlayKeyframe> = emptyList(),
    val animation: TextAnimation = TextAnimation.NONE,
    val animationDurationMs: Int = 500
)

data class PiPTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val timelineStartUs: Long = 0L,
    val timelineEndUs: Long = 5_000_000L,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = 0L,
    val normalizedX: Float = 0.75f,
    val normalizedY: Float = 0.25f,
    val scale: Float = 0.3f,
    val rotation: Float = 0.0f,
    val volume: Float = 0.0f
)

data class BeatMarker(
    val id: String = UUID.randomUUID().toString(),
    val timeUs: Long,
    val strength: Float = 1.0f
)

data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String = "Audio Track",
    val sourceDurationUs: Long = 0L,
    val timelineStartUs: Long = 0L,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = sourceDurationUs,
    val volume: Float = 1.0f,
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
    val isVoiceover: Boolean = false
) {
    val effectiveDurationUs: Long get() = (trimEndUs - trimStartUs).coerceAtLeast(0L)
}

data class TimelineItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val mediaType: MediaType = MediaType.VIDEO,
    val sourceDurationUs: Long = 0L,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = sourceDurationUs,
    val speed: Float = 1.0f,
    val speedRamp: SpeedRamp = SpeedRamp(),
    val volume: Float = 1.0f,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropRect: CropRect = CropRect(),
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
    val isReversed: Boolean = false,
    val freezeAtUs: Long? = null,
    val filterConfig: FilterConfig = FilterConfig(),
    val lutConfig: LutConfig? = null,
    val chromaKeyColor: FloatArray? = null,
    val chromaKeyThreshold: Float = 0.15f,
    val chromaKeySmoothness: Float = 0.05f,
    val maskConfig: MaskConfig = MaskConfig(),
    val blendMode: BlendMode = BlendMode.NORMAL,
    val transitionType: TransitionType = TransitionType.NONE,
    val transitionDurationUs: Long = 500_000L,
    val effectId: String? = null,
    val effectIntensity: Float = 0f
) {
    val trimmedDurationUs: Long get() = (trimEndUs - trimStartUs).coerceAtLeast(0L)
    val effectiveDurationUs: Long get() = (trimmedDurationUs / speed.coerceAtLeast(0.1f)).toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TimelineItem
        if (id != other.id) return false
        if (uri != other.uri) return false
        if (mediaType != other.mediaType) return false
        if (sourceDurationUs != other.sourceDurationUs) return false
        if (trimStartUs != other.trimStartUs) return false
        if (trimEndUs != other.trimEndUs) return false
        if (speed != other.speed) return false
        if (speedRamp != other.speedRamp) return false
        if (volume != other.volume) return false
        if (rotationDegrees != other.rotationDegrees) return false
        if (flipHorizontal != other.flipHorizontal) return false
        if (flipVertical != other.flipVertical) return false
        if (cropRect != other.cropRect) return false
        if (fadeInMs != other.fadeInMs) return false
        if (fadeOutMs != other.fadeOutMs) return false
        if (isReversed != other.isReversed) return false
        if (freezeAtUs != other.freezeAtUs) return false
        if (filterConfig != other.filterConfig) return false
        if (lutConfig != other.lutConfig) return false
        if (chromaKeyColor != null) {
            if (other.chromaKeyColor == null) return false
            if (!chromaKeyColor.contentEquals(other.chromaKeyColor)) return false
        } else if (other.chromaKeyColor != null) return false
        if (chromaKeyThreshold != other.chromaKeyThreshold) return false
        if (chromaKeySmoothness != other.chromaKeySmoothness) return false
        if (maskConfig != other.maskConfig) return false
        if (blendMode != other.blendMode) return false
        if (transitionType != other.transitionType) return false
        if (transitionDurationUs != other.transitionDurationUs) return false
        if (effectId != other.effectId) return false
        if (effectIntensity != other.effectIntensity) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + mediaType.hashCode()
        result = 31 * result + sourceDurationUs.hashCode()
        result = 31 * result + trimStartUs.hashCode()
        result = 31 * result + trimEndUs.hashCode()
        result = 31 * result + speed.hashCode()
        result = 31 * result + volume.hashCode()
        result = 31 * result + rotationDegrees.hashCode()
        result = 31 * result + flipHorizontal.hashCode()
        result = 31 * result + flipVertical.hashCode()
        result = 31 * result + cropRect.hashCode()
        result = 31 * result + isReversed.hashCode()
        result = 31 * result + filterConfig.hashCode()
        result = 31 * result + (lutConfig?.hashCode() ?: 0)
        result = 31 * result + (chromaKeyColor?.contentHashCode() ?: 0)
        result = 31 * result + maskConfig.hashCode()
        result = 31 * result + blendMode.hashCode()
        result = 31 * result + transitionType.hashCode()
        result = 31 * result + transitionDurationUs.hashCode()
        result = 31 * result + (effectId?.hashCode() ?: 0)
        result = 31 * result + effectIntensity.hashCode()
        return result
    }
}

enum class BlurType(val displayName: String) {
    GAUSSIAN("Heavy Blur"),
    MOSAIC("Pixelate Mosaic"),
    BLACKOUT("Solid Blackout"),
    PAINT("Finger Paint"),
    STACK_BLUR("InShot StackBlur"),
    MOTION_TRAIL("PowerDirector Trail"),
    IMAGE("Picture / Sticker")
}

data class PaintPoint(val x: Float, val y: Float)

data class PaintStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<PaintPoint> = emptyList(),
    val strokeWidthNorm: Float = 0.05f,
    val isBlackout: Boolean = true,
    val color: Long = 0xFF000000L
)

enum class BlurShape(val displayName: String) {
    OVAL("Oval / Circle"),
    RECTANGLE("Rectangle / Bar"),
    ROUNDED_RECT("Rounded Box")
}

data class BlurKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetUs: Long = 0L,
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.5f,
    val width: Float = 0.25f,
    val height: Float = 0.25f,
    val rotation: Float = 0f
)

data class BlurTransform(
    val normalizedX: Float,
    val normalizedY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float
)

data class BlurTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val timelineStartUs: Long = 0L,
    val timelineEndUs: Long = 5_000_000L,
    val blurType: BlurType = BlurType.GAUSSIAN,
    val shape: BlurShape = BlurShape.OVAL,
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.5f,
    val width: Float = 0.25f,
    val height: Float = 0.25f,
    val rotation: Float = 0f,
    val intensity: Float = 0.85f,
    val feather: Float = 0.15f,
    val customImageUri: String? = null,
    val keyframes: List<BlurKeyframe> = emptyList(),
    val paintStrokes: List<PaintStroke> = emptyList()
) {
    val durationUs: Long get() = (timelineEndUs - timelineStartUs).coerceAtLeast(0L)

    fun getTransformAt(timelineUs: Long): BlurTransform {
        if (keyframes.isEmpty()) {
            return BlurTransform(normalizedX, normalizedY, width, height, rotation)
        }
        val offsetUs = timelineUs - timelineStartUs
        if (offsetUs <= keyframes.first().timeOffsetUs) {
            val k = keyframes.first()
            return BlurTransform(k.normalizedX, k.normalizedY, k.width, k.height, k.rotation)
        }
        if (offsetUs >= keyframes.last().timeOffsetUs) {
            val k = keyframes.last()
            return BlurTransform(k.normalizedX, k.normalizedY, k.width, k.height, k.rotation)
        }
        for (i in 0 until keyframes.size - 1) {
            val k1 = keyframes[i]
            val k2 = keyframes[i + 1]
            if (offsetUs in k1.timeOffsetUs..k2.timeOffsetUs) {
                val span = (k2.timeOffsetUs - k1.timeOffsetUs).coerceAtLeast(1L)
                val frac = (offsetUs - k1.timeOffsetUs).toFloat() / span.toFloat()
                val x = k1.normalizedX + (k2.normalizedX - k1.normalizedX) * frac
                val y = k1.normalizedY + (k2.normalizedY - k1.normalizedY) * frac
                val w = k1.width + (k2.width - k1.width) * frac
                val h = k1.height + (k2.height - k1.height) * frac
                val rot = k1.rotation + (k2.rotation - k1.rotation) * frac
                return BlurTransform(x, y, w, h, rot)
            }
        }
        return BlurTransform(normalizedX, normalizedY, width, height, rotation)
    }
}

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Video",
    val createdTime: Long = System.currentTimeMillis(),
    val lastModifiedTime: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatio = AspectRatio.R_16_9,
    val videoClips: List<TimelineItem> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val overlays: List<OverlayItem> = emptyList(),
    val pipTracks: List<PiPTrackItem> = emptyList(),
    val blurTracks: List<BlurTrackItem> = emptyList(),
    val beatMarkers: List<BeatMarker> = emptyList(),
    val originalVideoVolume: Float = 1.0f,
    val backgroundMusicVolume: Float = 1.0f,
    val audioDuckingEnabled: Boolean = false,
    val audioDuckLevel: Float = 0.3f
) {
    val totalDurationUs: Long
        get() {
            val video = videoClips.sumOf { it.effectiveDurationUs }
            if (video > 0L) return video
            val audio = audioTracks.maxOfOrNull { it.timelineStartUs + it.effectiveDurationUs } ?: 0L
            val overlay = overlays.maxOfOrNull { it.timelineEndUs } ?: 0L
            val pip = pipTracks.maxOfOrNull { it.timelineEndUs } ?: 0L
            val blur = blurTracks.maxOfOrNull { it.timelineEndUs } ?: 0L
            return maxOf(video, audio, overlay, pip, blur)
        }
}
