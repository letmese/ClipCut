package com.clipcut.data.db

import com.clipcut.core.model.*

data class ProjectEntity(
    val id: String,
    val name: String,
    val createdTime: Long,
    val lastModifiedTime: Long,
    val aspectRatio: String = "16:9",
    val videoClips: List<TimelineItem>,
    val audioTracks: List<AudioTrackItem>,
    val overlays: List<OverlayItem>,
    val pipTracks: List<PiPTrackItem> = emptyList(),
    val beatMarkers: List<BeatMarker> = emptyList(),
    val originalVideoVolume: Float = 1.0f,
    val backgroundMusicVolume: Float = 1.0f,
    val audioDuckingEnabled: Boolean = false,
    val audioDuckLevel: Float = 0.3f
) {
    fun toDomain(): Project = Project(
        id = id, name = name,
        createdTime = createdTime, lastModifiedTime = lastModifiedTime,
        aspectRatio = AspectRatio.fromLabel(aspectRatio),
        videoClips = videoClips, audioTracks = audioTracks, overlays = overlays,
        pipTracks = pipTracks,
        beatMarkers = beatMarkers,
        originalVideoVolume = originalVideoVolume,
        backgroundMusicVolume = backgroundMusicVolume,
        audioDuckingEnabled = audioDuckingEnabled,
        audioDuckLevel = audioDuckLevel
    )

    companion object {
        fun fromDomain(project: Project): ProjectEntity = ProjectEntity(
            id = project.id, name = project.name,
            createdTime = project.createdTime, lastModifiedTime = project.lastModifiedTime,
            aspectRatio = project.aspectRatio.label,
            videoClips = project.videoClips, audioTracks = project.audioTracks,
            overlays = project.overlays, pipTracks = project.pipTracks,
            beatMarkers = project.beatMarkers,
            originalVideoVolume = project.originalVideoVolume,
            backgroundMusicVolume = project.backgroundMusicVolume,
            audioDuckingEnabled = project.audioDuckingEnabled,
            audioDuckLevel = project.audioDuckLevel
        )
    }
}
