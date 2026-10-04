package com.clipcut.data.db

import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem

data class ProjectEntity(
    val id: String,
    val name: String,
    val createdTime: Long,
    val lastModifiedTime: Long,
    val videoClips: List<TimelineItem>,
    val audioTracks: List<AudioTrackItem>,
    val overlays: List<OverlayItem>,
    val originalVideoVolume: Float,
    val backgroundMusicVolume: Float
) {
    fun toDomain(): Project = Project(
        id = id,
        name = name,
        createdTime = createdTime,
        lastModifiedTime = lastModifiedTime,
        videoClips = videoClips,
        audioTracks = audioTracks,
        overlays = overlays,
        originalVideoVolume = originalVideoVolume,
        backgroundMusicVolume = backgroundMusicVolume
    )

    companion object {
        fun fromDomain(project: Project): ProjectEntity = ProjectEntity(
            id = project.id,
            name = project.name,
            createdTime = project.createdTime,
            lastModifiedTime = project.lastModifiedTime,
            videoClips = project.videoClips,
            audioTracks = project.audioTracks,
            overlays = project.overlays,
            originalVideoVolume = project.originalVideoVolume,
            backgroundMusicVolume = project.backgroundMusicVolume
        )
    }
}
