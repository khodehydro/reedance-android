package com.hydro.reedance.media

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var lyricsOverlay: PlaybackLyricsOverlay? = null
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }).build()
        session?.let { lyricsOverlay = PlaybackLyricsOverlay(this, it.player) }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        lyricsOverlay?.close()
        lyricsOverlay = null
        session?.let { it.player.release(); it.release() }
        session = null
        super.onDestroy()
    }
}
