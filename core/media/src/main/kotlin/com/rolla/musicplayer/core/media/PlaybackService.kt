package com.rolla.musicplayer.core.media

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackStateHolder: PlaybackStateHolder

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            playbackStateHolder.setAudioSessionId(audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = buildPlayer()
        playbackStateHolder.setAudioSessionId(player.audioSessionId)
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
}
