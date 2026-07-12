package com.rolla.musicplayer.core.media

import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.equalizer.EqualizerSessionManager
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playbackStateHolder: PlaybackStateHolder

    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var equalizerSessionManager: EqualizerSessionManager

    @Inject
    lateinit var playbackSettingsBinder: PlaybackSettingsBinder

    @Inject
    lateinit var playbackUpdateDispatcher: PlaybackUpdateDispatcher

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private val supervisorJob = SupervisorJob()

    // Defense-in-depth: an uncaught exception in a root serviceScope coroutine would otherwise hit
    // the thread's default handler and kill the whole process. Background playback bookkeeping
    // (position ticker, play-count recording, equalizer session binding) should never be able to
    // take playback down — log and drop instead. SupervisorJob already keeps sibling coroutines
    // alive; this handler covers the escaped exception itself.
    private val serviceExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "PlaybackService background work failed", throwable)
    }
    private val serviceScope = CoroutineScope(supervisorJob + Dispatchers.Default + serviceExceptionHandler)

    private var equalizerBindJob: Job? = null

    private val playbackTracker = PlaybackTracker()

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            playbackStateHolder.setAudioSessionId(audioSessionId)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playbackStateHolder.setIsPlaying(isPlaying)
            val songId = playbackTracker.onIsPlayingChanged(isPlaying, player.currentMediaItem?.mediaId)
            if (songId != null) {
                serviceScope.launch { songRepository.recordPlaybackStarted(songId) }
            }
            // Push-update seam for :feature:widget et al (see PlaybackUpdateHook) -- dispatched
            // after the holder write above, off this (player-thread) callback.
            serviceScope.launch { playbackUpdateDispatcher.dispatch() }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            playbackTracker.onMediaItemTransition()
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
            // Push-update seam for :feature:widget et al (see PlaybackUpdateHook) -- dispatched
            // after both holder writes above, off this (player-thread) callback.
            serviceScope.launch { playbackUpdateDispatcher.dispatch() }
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
        bindEqualizerToAudioSession()
        playbackSettingsBinder.bind(player, serviceScope)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        player.removeListener(playerListener)
        // Stop the session-binding collectors BEFORE releasing the effect/player, and wait for
        // them: cancelAndJoin lets an in-flight attach/apply run to its next suspension point, so
        // nothing new can land on the effect or the player after the release calls below (an
        // attach/apply that slipped in afterward would leak an AudioEffect nobody ever releases,
        // or call into an already-released player). The wait is bounded — each collector body is
        // one native effect creation / one DataStore read plus a couple of player calls.
        runBlocking {
            equalizerBindJob?.cancelAndJoin()
            playbackSettingsBinder.unbind()
        }
        // Release the equalizer effect while its audio session still exists (i.e. before the
        // player that owns that session is torn down).
        equalizerSessionManager.release()
        mediaSession.release()
        player.release()
        supervisorJob.cancel()
        super.onDestroy()
    }

    // serviceScope runs on Dispatchers.Default, so the DataStore read (EqualizerRepository) and
    // the effect attach/apply (EqualizerController) never execute on the player's callback
    // thread (main) — onAudioSessionIdChanged above only writes to the StateFlow, it never calls
    // into the equalizer directly. StateFlow semantics do the rest: collectLatest immediately
    // receives the *current* id (covering the value pushed above before this collector starts),
    // an ordinary track change that keeps the same session id is deduplicated by the StateFlow
    // and never re-triggers attach (the previously attached effect just survives it), and
    // collectLatest cancels any still-running attach/apply if the id changes again before it
    // finishes.
    private fun bindEqualizerToAudioSession() {
        equalizerBindJob = serviceScope.launch {
            playbackStateHolder.audioSessionId.collectLatest { audioSessionId ->
                equalizerSessionManager.attachAndApplyPersisted(audioSessionId)
            }
        }
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

    // The loop itself keeps running on its fixed cadence regardless of play state -- it never stops
    // while paused, it just skips the holder write below. The hook dispatch therefore has to be
    // gated on that same "did we just update the position" condition (didUpdatePosition), not on the
    // ticker running at all, or paused playback would produce a hook call every tick for no change.
    private fun startPositionTicker() {
        serviceScope.launch {
            while (true) {
                delay(POSITION_POLL_INTERVAL_MS)
                val didUpdatePosition = withContext(Dispatchers.Main.immediate) {
                    if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                        playbackStateHolder.setPositionMs(player.currentPosition.coerceAtLeast(0L))
                        true
                    } else {
                        false
                    }
                }
                if (didUpdatePosition) {
                    // Push-update seam for :feature:widget et al (see PlaybackUpdateHook) --
                    // dispatched after the holder write above, off the main thread used for that write.
                    serviceScope.launch { playbackUpdateDispatcher.dispatch() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val POSITION_POLL_INTERVAL_MS = 1_000L
    }
}
