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
    val originalVideoVolume: Float,
    val backgroundMusicVolume: Float
) {
    fun toDomain(): Project = Project(
        id = id, name = name,
        createdTime = createdTime, lastModifiedTime = lastModifiedTime,
        aspectRatio = AspectRatio.fromLabel(aspectRatio),
        videoClips = videoClips, audioTracks = audioTracks, overlays = overlays,
        pipTracks = pipTracks,
        originalVideoVolume = originalVideoVolume,
        backgroundMusicVolume = backgroundMusicVolume
    )

    companion object {
        fun fromDomain(project: Project): ProjectEntity = ProjectEntity(
            id = project.id, name = project.name,
            createdTime = project.createdTime, lastModifiedTime = project.lastModifiedTime,
            aspectRatio = project.aspectRatio.label,
            videoClips = project.videoClips, audioTracks = project.audioTracks,
            overlays = project.overlays, pipTracks = project.pipTracks,
            originalVideoVolume = project.originalVideoVolume,
            backgroundMusicVolume = project.backgroundMusicVolume
        )
    }
}
