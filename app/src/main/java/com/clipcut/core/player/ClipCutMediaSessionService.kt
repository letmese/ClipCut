package com.clipcut.core.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class ClipCutMediaSessionService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val player = ExoPlayer.Builder(this).build()
            session = MediaSession.Builder(this, player).build()
        } catch (_: Throwable) {
            session = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        try {
            session?.player?.release()
            session?.release()
        } catch (_: Throwable) {}
        session = null
        super.onDestroy()
    }
}
