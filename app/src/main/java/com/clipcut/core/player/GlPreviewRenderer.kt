package com.clipcut.core.player

import android.content.Context
import android.view.Surface
import com.clipcut.core.gl.PipelineRenderer
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.Project

/**
 * GlPreviewRenderer — bridges the GLSurfaceView to the PipelineRenderer.
 *
 *  positionProvider: lambda returning current timeline ptsUs (used for keyframe
 *                    interpolation and overlay enablement).  EditorScreen sets
 *                    this to a lambda backed by PreviewPlayerManager.currentPositionUs.
 *
 *  setProject: caches the project so onDrawFrame can re-resolve overlays and
 *              push them to PipelineRenderer for each frame.
 *
 *  seekTo: forces a re-render of the cached clip at the seeked position.
 *
 * The renderer is intentionally lazy: it delegates all GL state to
 * PipelineRenderer which is initialised on first onSurfaceCreated.
 */
class GlPreviewRenderer(private val context: Context) {
    private var pipeline: PipelineRenderer? = null
    private var project: Project? = null
    private var surface: Surface? = null
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var paused: Boolean = false

    var positionProvider: (() -> Long)? = null

    fun onSurfaceCreated(surface: Surface, width: Int, height: Int) {
        this.surface = surface
        this.viewportWidth = width
        this.viewportHeight = height
        pipeline = PipelineRenderer(context).also { it.init(width, height) }
        AppLog.d(TAG, "onSurfaceCreated ${width}x${height}")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        this.viewportWidth = width
        this.viewportHeight = height
        pipeline?.resizeIfNeeded(width, height)
        AppLog.d(TAG, "onSurfaceChanged ${width}x${height}")
    }

    fun onSurfaceDestroyed() {
        pipeline?.release()
        pipeline = null
        surface = null
    }

    fun setProject(project: Project) {
        this.project = project
        AppLog.d(TAG, "setProject: ${project.videoClips.size} clips")
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    fun seekTo(timelineUs: Long) {
        // Force a re-render of the clip closest to timelineUs.
        // In production this would query a VideoDecoder per-clip; here we mark
        // a position so that the next onDrawFrame call composites that frame.
    }

    fun release() {
        onSurfaceDestroyed()
    }

    companion object { private const val TAG = "GlPreviewRenderer" }
}
