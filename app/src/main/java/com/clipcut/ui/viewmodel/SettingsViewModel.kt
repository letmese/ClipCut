package com.clipcut.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SettingsViewModel — persistence of global user preferences via
 * SharedPreferences. Exposes typed StateFlows that the SettingsScreen reads.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("clipcut_settings", 0)

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, "system") ?: "system")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _accent = MutableStateFlow(prefs.getString(KEY_ACCENT, "#FACC15") ?: "#FACC15")
    val accent: StateFlow<String> = _accent.asStateFlow()

    private val _exportRes = MutableStateFlow(prefs.getString(KEY_RES, "1080p") ?: "1080p")
    val exportRes: StateFlow<String> = _exportRes.asStateFlow()

    private val _exportCodec = MutableStateFlow(prefs.getString(KEY_CODEC, "H264") ?: "H264")
    val exportCodec: StateFlow<String> = _exportCodec.asStateFlow()

    private val _exportFps = MutableStateFlow(prefs.getInt(KEY_FPS, 30))
    val exportFps: StateFlow<Int> = _exportFps.asStateFlow()

    private val _autoSaveSeconds = MutableStateFlow(prefs.getInt(KEY_AUTOSAVE, 30))
    val autoSaveSeconds: StateFlow<Int> = _autoSaveSeconds.asStateFlow()

    private val _hwAccel = MutableStateFlow(prefs.getBoolean(KEY_HWACCEL, true))
    val hwAccel: StateFlow<Boolean> = _hwAccel.asStateFlow()

    private val _gestures = MutableStateFlow(prefs.getBoolean(KEY_GESTURES, true))
    val gestures: StateFlow<Boolean> = _gestures.asStateFlow()

    private val _hapticLevel = MutableStateFlow(prefs.getInt(KEY_HAPTIC, 1))
    val hapticLevel: StateFlow<Int> = _hapticLevel.asStateFlow()

    private val _cacheLimitMb = MutableStateFlow(prefs.getInt(KEY_CACHE_MB, 1024))
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    private val _followVideoFrame = MutableStateFlow(prefs.getBoolean(KEY_FOLLOW_FRAME, true))
    val followVideoFrame: StateFlow<Boolean> = _followVideoFrame.asStateFlow()

    private val _defaultPhotoDuration = MutableStateFlow(prefs.getFloat(KEY_PHOTO_DURATION, 3.0f))
    val defaultPhotoDuration: StateFlow<Float> = _defaultPhotoDuration.asStateFlow()

    private val _defaultTransitionDuration = MutableStateFlow(prefs.getFloat(KEY_TRANS_DURATION, 0.5f))
    val defaultTransitionDuration: StateFlow<Float> = _defaultTransitionDuration.asStateFlow()

    private val _mediaFillStyle = MutableStateFlow(prefs.getString(KEY_FILL_STYLE, "fit") ?: "fit")
    val mediaFillStyle: StateFlow<String> = _mediaFillStyle.asStateFlow()

    private val _audioRippleDelete = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_RIPPLE, true))
    val audioRippleDelete: StateFlow<Boolean> = _audioRippleDelete.asStateFlow()

    private val _previewPerformanceMode = MutableStateFlow(prefs.getString(KEY_PREVIEW_MODE, "smooth_720p") ?: "smooth_720p")
    val previewPerformanceMode: StateFlow<String> = _previewPerformanceMode.asStateFlow()

    private val _highBitrateExport = MutableStateFlow(prefs.getBoolean(KEY_HIGH_BITRATE, true))
    val highBitrateExport: StateFlow<Boolean> = _highBitrateExport.asStateFlow()

    fun setTheme(v: String) {
        _theme.value = v
        prefs.edit().putString(KEY_THEME, v).apply()
        com.clipcut.ui.theme.ThemeState.update(v, _accent.value, com.clipcut.ui.theme.ThemeState.isDark)
    }
    fun setAccent(v: String) {
        _accent.value = v
        prefs.edit().putString(KEY_ACCENT, v).apply()
        com.clipcut.ui.theme.ThemeState.update(_theme.value, v, com.clipcut.ui.theme.ThemeState.isDark)
    }
    fun setExportRes(v: String) { _exportRes.value = v; prefs.edit().putString(KEY_RES, v).apply() }
    fun setExportCodec(v: String) { _exportCodec.value = v; prefs.edit().putString(KEY_CODEC, v).apply() }
    fun setExportFps(v: Int) { _exportFps.value = v; prefs.edit().putInt(KEY_FPS, v).apply() }
    fun setAutoSave(v: Int) { _autoSaveSeconds.value = v; prefs.edit().putInt(KEY_AUTOSAVE, v).apply() }
    fun setHwAccel(v: Boolean) { _hwAccel.value = v; prefs.edit().putBoolean(KEY_HWACCEL, v).apply() }
    fun setGestures(v: Boolean) { _gestures.value = v; prefs.edit().putBoolean(KEY_GESTURES, v).apply() }
    fun setHaptic(v: Int) { _hapticLevel.value = v; prefs.edit().putInt(KEY_HAPTIC, v).apply() }
    fun setCacheLimit(v: Int) { _cacheLimitMb.value = v; prefs.edit().putInt(KEY_CACHE_MB, v).apply() }
    fun setFollowVideoFrame(v: Boolean) { _followVideoFrame.value = v; prefs.edit().putBoolean(KEY_FOLLOW_FRAME, v).apply() }
    fun setDefaultPhotoDuration(v: Float) { _defaultPhotoDuration.value = v; prefs.edit().putFloat(KEY_PHOTO_DURATION, v).apply() }
    fun setDefaultTransitionDuration(v: Float) { _defaultTransitionDuration.value = v; prefs.edit().putFloat(KEY_TRANS_DURATION, v).apply() }
    fun setMediaFillStyle(v: String) { _mediaFillStyle.value = v; prefs.edit().putString(KEY_FILL_STYLE, v).apply() }
    fun setAudioRippleDelete(v: Boolean) { _audioRippleDelete.value = v; prefs.edit().putBoolean(KEY_AUDIO_RIPPLE, v).apply() }
    fun setPreviewPerformanceMode(v: String) { _previewPerformanceMode.value = v; prefs.edit().putString(KEY_PREVIEW_MODE, v).apply() }
    fun setHighBitrateExport(v: Boolean) { _highBitrateExport.value = v; prefs.edit().putBoolean(KEY_HIGH_BITRATE, v).apply() }

    fun getCacheSizeMb(): Long {
        val cacheDir = getApplication<Application>().cacheDir
        fun dirSize(f: java.io.File): Long {
            if (!f.exists()) return 0L
            if (!f.isDirectory) return f.length()
            return f.listFiles()?.sumOf { dirSize(it) } ?: 0L
        }
        val bytes = dirSize(cacheDir)
        return (bytes / (1024 * 1024)).coerceAtLeast(1L)
    }

    fun clearCache() {
        val cacheDir = getApplication<Application>().cacheDir
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_RES = "export_res"
        private const val KEY_CODEC = "export_codec"
        private const val KEY_FPS = "export_fps"
        private const val KEY_AUTOSAVE = "autosave_seconds"
        private const val KEY_HWACCEL = "hw_accel"
        private const val KEY_GESTURES = "gestures"
        private const val KEY_HAPTIC = "haptic_level"
        private const val KEY_CACHE_MB = "cache_mb"
        private const val KEY_FOLLOW_FRAME = "follow_video_frame"
        private const val KEY_PHOTO_DURATION = "default_photo_duration"
        private const val KEY_TRANS_DURATION = "default_trans_duration"
        private const val KEY_FILL_STYLE = "media_fill_style"
        private const val KEY_AUDIO_RIPPLE = "audio_ripple_delete"
        private const val KEY_PREVIEW_MODE = "preview_perf_mode"
        private const val KEY_HIGH_BITRATE = "high_bitrate_export"
    }
}
