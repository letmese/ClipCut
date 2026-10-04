package com.clipcut.core.model

import java.util.UUID

enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE
}

enum class FilterType(val displayName: String) {
    NONE("Original"),
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    SATURATION("Saturation"),
    TEMPERATURE("Warmth"),
    VIGNETTE("Vignette"),
    GRAYSCALE("B&W"),
    SEPIA("Sepia")
}

data class FilterConfig(
    val filterType: FilterType = FilterType.NONE,
    val brightness: Float = 0.0f,     // -1.0 to 1.0 (default 0.0)
    val contrast: Float = 1.0f,       // 0.0 to 2.0 (default 1.0)
    val saturation: Float = 1.0f,     // 0.0 to 2.0 (default 1.0)
    val temperature: Float = 0.0f     // -1.0 to 1.0 (default 0.0, warm/cool)
)

enum class TransitionType(val displayName: String) {
    NONE("None"),
    FADE("Fade to Black"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    DISSOLVE("Dissolve")
}

data class TimelineItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val mediaType: MediaType = MediaType.VIDEO,
    val sourceDurationUs: Long = 0L,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = sourceDurationUs,
    val speed: Float = 1.0f,               // 0.25f .. 4.0f
    val volume: Float = 1.0f,              // 0.0f .. 2.0f
    val rotationDegrees: Int = 0,          // 0, 90, 180, 270
    val filterConfig: FilterConfig = FilterConfig(),
    val transitionType: TransitionType = TransitionType.NONE,
    val transitionDurationUs: Long = 500_000L // 0.5s
) {
    val trimmedDurationUs: Long
        get() = maxOf(0L, trimEndUs - trimStartUs)

    val effectiveDurationUs: Long
        get() = (trimmedDurationUs / speed.coerceIn(0.25f, 4.0f)).toLong()
}

data class AudioTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String = "Audio Track",
    val sourceDurationUs: Long = 0L,
    val timelineStartUs: Long = 0L,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = sourceDurationUs,
    val volume: Float = 1.0f
) {
    val effectiveDurationUs: Long
        get() = maxOf(0L, trimEndUs - trimStartUs)
}

data class OverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val textColor: Long = 0xFFFFFFFFL,
    val textSizeSp: Float = 28f,
    val imageUri: String? = null,
    val normalizedX: Float = 0.5f,        // 0.0 to 1.0 (center)
    val normalizedY: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotation: Float = 0.0f,
    val timelineStartUs: Long = 0L,
    val timelineEndUs: Long = 5_000_000L   // 5s default
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

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Video",
    val createdTime: Long = System.currentTimeMillis(),
    val lastModifiedTime: Long = System.currentTimeMillis(),
    val videoClips: List<TimelineItem> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val overlays: List<OverlayItem> = emptyList(),
    val originalVideoVolume: Float = 1.0f,
    val backgroundMusicVolume: Float = 1.0f
) {
    val totalDurationUs: Long
        get() = videoClips.sumOf { it.effectiveDurationUs }
}
