package com.rolla.musicplayer.core.media

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackStateHolder: PlaybackStateHolder

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private val supervisorJob = SupervisorJob()
    private val serviceScope = CoroutineScope(supervisorJob + Dispatchers.Default)

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            playbackStateHolder.setAudioSessionId(audioSessionId)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playbackStateHolder.setIsPlaying(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = mediaItem?.let { item ->
                Song(
                    id = item.mediaId,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                    album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
                    albumId = 0L,
                    durationMs = 0L,
                    trackNumber = null,
                    year = null,
                    contentUri = item.localConfiguration?.uri?.toString().orEmpty(),
                    artworkUri = item.mediaMetadata.artworkUri?.toString().orEmpty(),
                )
            }
            playbackStateHolder.setCurrentSong(song)
            playbackStateHolder.setDurationMs(player.duration.coerceAtLeast(0L))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                playbackStateHolder.setDurationMs(player.duration.coerceAtLeast(0L))
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            val mode = if (shuffleModeEnabled) ShuffleMode.ON else ShuffleMode.OFF
            playbackStateHolder.setShuffleMode(mode)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
            playbackStateHolder.setRepeatMode(mode)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = buildPlayer()
        playbackStateHolder.setAudioSessionId(player.audioSessionId)
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player).build()
        startPositionTicker()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        supervisorJob.cancel()
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

    private fun startPositionTicker() {
        serviceScope.launch {
            while (true) {
                delay(POSITION_POLL_INTERVAL_MS)
                withContext(Dispatchers.Main.immediate) {
                    if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                        playbackStateHolder.setPositionMs(player.currentPosition.coerceAtLeast(0L))
                    }
                }
            }
        }
    }

    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 1_000L
    }
}
