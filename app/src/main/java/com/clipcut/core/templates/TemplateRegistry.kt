package com.clipcut.core.templates

import android.content.Context
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.AspectRatio
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.OverlayItem
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Template — a recipe for a project: clip timings, transitions, effects,
 * filters, music URL, text overlays. Stored as JSON in assets/templates/.
 *
 * The home screen's templates carousel reads the bundled list; tapping a
 * card calls TemplateRegistry.applyToProject() which fills in the user's
 * media slots in order.
 */
data class Template(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("aspectRatio") val aspectRatio: String,
    @SerializedName("durationUs") val durationUs: Long,
    @SerializedName("clipSlots") val clipSlots: List<ClipSlot>,
    @SerializedName("transitions") val transitions: List<String>,
    @SerializedName("effects") val effects: List<String>,
    @SerializedName("filters") val filters: List<String>,
    @SerializedName("musicUrl") val musicUrl: String?,
    @SerializedName("textOverlays") val textOverlays: List<TextOverlay>
) {
    data class ClipSlot(
        @SerializedName("order") val order: Int,
        @SerializedName("durationUs") val durationUs: Long,
        @SerializedName("filter") val filter: String,
        @SerializedName("effect") val effect: String?
    )
    data class TextOverlay(
        @SerializedName("text") val text: String,
        @SerializedName("startUs") val startUs: Long,
        @SerializedName("endUs") val endUs: Long,
        @SerializedName("position") val position: String // "top" / "center" / "bottom"
    )
}

class TemplateRegistry(private val context: Context) {
    private val gson = Gson()

    fun listTemplates(): List<Template> {
        val out = mutableListOf<Template>()
        try {
            val files = context.assets.list("templates") ?: return emptyList()
            for (f in files.sorted()) {
                if (!f.endsWith(".json")) continue
                val json = context.assets.open("templates/$f").bufferedReader().use { it.readText() }
                out.add(gson.fromJson(json, Template::class.java))
            }
        } catch (t: Throwable) {
            AppLog.w(TAG, "TemplateRegistry.listTemplates failed: ${t.message}")
        }
        return out
    }

    /** Apply a template to a fresh project. mediaUris fills clip slots in order. */
    fun applyToProject(template: Template, project: Project, mediaUris: List<String>): Project {
        val clips = mutableListOf<TimelineItem>()
        for (i in template.clipSlots.indices) {
            val uri = mediaUris.getOrNull(i) ?: break
            val slot = template.clipSlots[i]
            clips.add(
                TimelineItem(
                    uri = uri,
                    sourceDurationUs = slot.durationUs,
                    trimStartUs = 0L,
                    trimEndUs = slot.durationUs
                )
            )
        }
        val overlays = template.textOverlays.map {
            OverlayItem(
                text = it.text,
                normalizedY = when (it.position) {
                    "top" -> 0.15f
                    "bottom" -> 0.85f
                    else -> 0.5f
                },
                timelineStartUs = it.startUs,
                timelineEndUs = it.endUs
            )
        }
        val audio = template.musicUrl?.let { url ->
            listOf(
                AudioTrackItem(
                    uri = url,
                    name = "Music",
                    sourceDurationUs = template.durationUs,
                    trimStartUs = 0L,
                    trimEndUs = template.durationUs,
                    volume = 0.7f
                )
            )
        } ?: emptyList()
        return project.copy(
            name = project.name + " (${template.name})",
            aspectRatio = AspectRatio.fromLabel(template.aspectRatio),
            videoClips = clips,
            audioTracks = audio,
            overlays = overlays
        )
    }

    companion object { private const val TAG = "TemplateRegistry" }
}
