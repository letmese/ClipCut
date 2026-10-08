package com.clipcut.core.model

enum class ExportResolution(val label: String, val width: Int, val height: Int, val defaultBitrate: Int) {
    RES_720P("720p", 1280, 720, 4_000_000),
    RES_1080P("1080p", 1920, 1080, 8_000_000),
    RES_4K("4K", 3840, 2160, 25_000_000)
}

enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264", "video/avc"),
    H265("H.265", "video/hevc")
}

data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val customWidth: Int = resolution.width,
    val customHeight: Int = resolution.height,
    val frameRate: Int = 30,
    val bitRate: Int = resolution.defaultBitrate,
    val codec: ExportCodec = ExportCodec.H264,
    val audioBitRate: Int = 192_000,
    val audioSampleRate: Int = 48000,
    val audioChannels: Int = 2
) {
    val width: Int get() = customWidth
    val height: Int get() = customHeight
}
