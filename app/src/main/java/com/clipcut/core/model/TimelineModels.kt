package com.clipcut.core.model

import java.util.UUID

enum class MediaType { VIDEO, AUDIO, IMAGE }

enum class FilterType(val displayName: String) {
    NONE("Original"), BRIGHTNESS("Brightness"), CONTRAST("Contrast"),
    SATURATION("Saturation"), TEMPERATURE("Warmth"),
    VIGNETTE("Vignette"), GRAYSCALE("B&W"), SEPIA("Sepia")
}

data class FilterConfig(
    val filterType: FilterType = FilterType.NONE,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperature: Float = 0.0f
)

enum class TransitionType(val displayName: String) {
    NONE("None"), FADE("Fade to Black"), SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"), DISSOLVE("Dissolve")
}

enum class AspectRatio(
    val label: String,
    val wRatio: Float,
    val hRatio: Float,
    val description: String = ""
) {
    ORIGINAL("Original", 0f, 0f, "Fit Media"),
    R_16_9("16:9", 16f, 9f, "YouTube"),
    R_9_16("9:16", 9f, 16f, "TikTok / Shorts"),
    R_1_1("1:1", 1f, 1f, "Instagram"),
    R_4_5("4:5", 4f, 5f, "Feed"),
    R_4_3("4:3", 4f, 3f, "Standard"),
    R_21_9("21:9", 21f, 9f, "Cinematic"),
    FREE("Free", 0f, 0f, "Freeform");

    fun calculateExportDimensions(resolution: ExportResolution): Pair<Int, Int> {
        return when (this) {
            R_16_9 -> when (resolution) {
                ExportResolution.RES_720P -> Pair(1280, 720)
                ExportResolution.RES_1080P -> Pair(1920, 1080)
                ExportResolution.RES_4K -> Pair(3840, 2160)
            }
            R_9_16 -> when (resolution) {
                ExportResolution.RES_720P -> Pair(720, 1280)
                ExportResolution.RES_1080P -> Pair(1080, 1920)
                ExportResolution.RES_4K -> Pair(2160, 3840)
            }
            R_1_1 -> when (resolution) {
                ExportResolution.RES_720P -> Pair(720, 720)
                ExportResolution.RES_1080P -> Pair(1080, 1080)
                ExportResolution.RES_4K -> Pair(2160, 2160)
            }
            R_4_5 -> when (resolution) {
                ExportResolution.RES_720P -> Pair(720, 900)
                ExportResolution.RES_1080P -> Pair(1080, 1350)
                ExportResolution.RES_4K -> Pair(2160, 2700)
            }
            R_4_3 -> when (resolution) {
                ExportResolution.RES_720P -> Pair(960, 720)
                ExportResolution.RES_1080P -> Pair(1440, 1080)
                ExportResolution.RES_4K -> Pair(2880, 2160)
            }
            R_21_9 -> when (resolution) {
                ExportResolution.RES_720P -> Pair(1680, 720)
                ExportResolution.RES_1080P -> Pair(2560, 1080)
                ExportResolution.RES_4K -> Pair(3840, 1648)
            }
            ORIGINAL, FREE -> when (resolution) {
                ExportResolution.RES_720P -> Pair(1280, 720)
                ExportResolution.RES_1080P -> Pair(1920, 1080)
                ExportResolution.RES_4K -> Pair(3840, 2160)
            }
        }
    }

    companion object {
        fun fromLabel(label: String): AspectRatio =
            values().firstOrNull { it.label == label || it.name == label } ?: R_16_9
    }
}

enum class FontFamilyOption(val label: String) {
    DEFAULT("Default"), SERIF("Serif"),
    MONOSPACE("Monospace"), SANS_SERIF_CONDENSED("Condensed")
}

data class CropRect(
    val left: Float = 0f, val top: Float = 0f,
    val right: Float = 1f, val bottom: Float = 1f
) {
    val width: Float get() = (right - left).coerceIn(0f, 1f)
    val height: Float get() = (bottom - top).coerceIn(0f, 1f)
    val isValid: Boolean get() = width > 0.01f && height > 0.01f
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
    val transitionDurationUs: Long = 500_000L
) {
    val trimmedDurationUs: Long get() = maxOf(0L, trimEndUs - trimStartUs)
    val effectiveDurationUs: Long get() = (trimmedDurationUs / speed.coerceIn(0.25f, 4.0f)).toLong()
}

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
    val fadeOutMs: Int = 0
) {
    val effectiveDurationUs: Long get() = maxOf(0L, trimEndUs - trimStartUs)
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

enum class ExportResolution(val label: String, val width: Int, val height: Int, val defaultBitrate: Int) {
    RES_720P("720p HD", 1280, 720, 4_000_000),
    RES_1080P("1080p Full HD", 1920, 1080, 8_000_000),
    RES_4K("4K Ultra HD", 3840, 2160, 24_000_000)
}

enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264 / AVC", "video/avc"),
    H265("H.265 / HEVC", "video/hevc")
}

data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val customWidth: Int = resolution.width,
    val customHeight: Int = resolution.height,
    val frameRate: Int = 30,
    val bitRate: Int = resolution.defaultBitrate,
    val codec: ExportCodec = ExportCodec.H264,
    val audioBitRate: Int = 192_000,
    val audioSampleRate: Int = 44100,
    val audioChannels: Int = 2
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
    val volume: Float = 1.0f
)

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
    val beatMarkers: List<BeatMarker> = emptyList(),
    val originalVideoVolume: Float = 1.0f,
    val backgroundMusicVolume: Float = 1.0f,
    val audioDuckingEnabled: Boolean = false,
    val audioDuckLevel: Float = 0.3f
) {
    val totalDurationUs: Long get() = videoClips.sumOf { it.effectiveDurationUs }
}
