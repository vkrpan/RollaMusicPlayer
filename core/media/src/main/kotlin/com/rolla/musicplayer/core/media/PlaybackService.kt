package com.rolla.musicplayer.core.media

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.rolla.musicplayer.core.data.repository.PlaybackStateRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.equalizer.EqualizerSessionManager
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
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

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

    @Inject
    lateinit var playbackStateRepository: PlaybackStateRepository

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    /** Publishes the queue into [playbackStateHolder] (ruling 1); added after playerListener, before the enforcer. */
    private lateinit var queuePublisher: QueuePublisher

    // Keeps shuffle orders start-first so Shuffle reaches every track (see StartFirstShuffleEnforcer).
    private lateinit var shuffleEnforcer: StartFirstShuffleEnforcer

    // Queue restore (spec 2026-10-12): restoredShuffleOrder is registered after the enforcer (ruling R3).
    private lateinit var restoredShuffleOrder: RestoredShuffleOrder
    private lateinit var queuePersister: QueuePersister
    private lateinit var restoreCoordinator: RestoreCoordinator

    // Ruling P6 and final-review I2: a media-button start that nothing else puts in the foreground (nothing to resume,
    // or a key UP, which Media3 ignores) must still enter (and leave) it. Ruling P8: a first start by a non-play key
    // (the notification's deleteIntent STOP) stops the service once the restore settles idle, so the next open
    // creates a fresh service with a prepared restore.
    private val mediaButtonStartFallback =
        MediaButtonStartFallback(this, ::hasNothingToPlay, ::isIdleWithoutResumption)

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

        // Seeks (including while paused) and auto-transitions move the position without the 1s
        // ticker seeing it -- the ticker only writes while playing. Without this the holder kept the
        // pre-seek position: the widget's progress bar didn't move after a paused seek, and repeated
        // widget ±15s taps all computed their target from the same stale position.
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            playbackStateHolder.setPositionMs(newPosition.positionMs.coerceAtLeast(0L))
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
            playbackStateHolder.setRepeatMode(repeatMode.toRepeatMode())
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = buildPlayer()
        playbackStateHolder.setAudioSessionId(player.audioSessionId)
        player.addListener(playerListener)
        // Before the enforcer: its setShuffleOrder fires onTimelineChanged, which republishes the reshuffled order.
        queuePublisher = QueuePublisher(player, playbackStateHolder)
        player.addListener(queuePublisher)
        shuffleEnforcer = StartFirstShuffleEnforcer(player)
        player.addListener(shuffleEnforcer)
        mediaSession = installQueueRestore()
        startPositionTicker()
        bindEqualizerToAudioSession()
        playbackSettingsBinder.bind(player, serviceScope)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    // Recorded before super: Media3 posts the media-button dispatch, so the record is in place before any resumption.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mediaButtonStartFallback.onStartCommand(intent, startId, isPlaybackOngoing())
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        // Stop the session-binding collectors BEFORE releasing the effect/player, and wait for
        // them: cancelAndJoin lets an in-flight attach/apply run to its next suspension point, so
        // nothing new can land on the effect or the player after the release calls below (an
        // attach/apply that slipped in afterward would leak an AudioEffect nobody ever releases,
        // or call into an already-released player). The wait is bounded — each collector body is
        // one native effect creation / one DataStore read plus a couple of player calls.
        // The listeners are removed only after this block. That is safe only because nothing inside it calls a player
        // setter (a setter on this thread delivers callbacks synchronously); runBlocking holds the main thread, so no
        // other player callback can be delivered before the removals below.
        runBlocking {
            // Ruling R7: the last position, written while the persister is attached and the player still holds its
            // items. The timeout bounds the wait for the persister's write lock (a write already in flight); once the
            // final write itself has started, Room runs it in withContext, which the timeout cannot interrupt.
            // saveFinal logs and drops its failures.
            withTimeoutOrNull(FINAL_SAVE_TIMEOUT_MS) { queuePersister.saveFinal() }
            equalizerBindJob?.cancelAndJoin()
            playbackSettingsBinder.unbind()
        }
        player.removeListener(playerListener)
        player.removeListener(queuePublisher)
        player.removeListener(shuffleEnforcer)
        player.removeListener(restoredShuffleOrder)
        player.removeListener(queuePersister)
        // Release the equalizer effect while its audio session still exists (i.e. before the
        // player that owns that session is torn down).
        equalizerSessionManager.release()
        mediaSession.release()
        player.release()
        supervisorJob.cancel()
        super.onDestroy()
    }

    // Listener order matters: the enforcer is already registered, so restoredShuffleOrder runs after it (ruling R3).
    // The persister starts writing only once the restore settles (spec §6.3). Its consumer runs on serviceScope
    // (Dispatchers.Default), never main: onDestroy's runBlocking on main awaits its write mutex in saveFinal.
    private fun installQueueRestore(): MediaLibrarySession {
        restoredShuffleOrder = RestoredShuffleOrder(player)
        player.addListener(restoredShuffleOrder)
        queuePersister = QueuePersister(player, playbackStateRepository, serviceScope)
        player.addListener(queuePersister)
        val main = Dispatchers.Main.immediate
        restoreCoordinator = RestoreCoordinator(playbackStateRepository, serviceScope, main) {
            queuePersister.enable()
            mediaButtonStartFallback.onRestoreSettled()
        }
        val callback = PlaybackSessionCallback(
            serviceScope,
            main,
            restoreCoordinator,
            restoredShuffleOrder,
            mediaButtonStartFallback::onNothingToResume,
        )
        val session = MediaLibrarySession.Builder(this, player, callback).build()
        restoreCoordinator.restoreInto(player, restoredShuffleOrder, playbackStateHolder)
        return session
    }

    // The restore settled without a queue (nothing saved) and none has arrived since; a resumption in flight
    // (HANDED_TO_RESUMPTION) decides for itself. Main thread only.
    private fun hasNothingToPlay(): Boolean =
        restoreCoordinator.outcome == RestoreCoordinator.Outcome.DISCARDED && player.mediaItemCount == 0

    // Ruling P8: nothing is playing or about to (no playWhenReady) and no resumption took the first load. An APPLIED
    // restore counts as idle: a deleteIntent STOP is exactly APPLIED plus STOP. Main thread only.
    private fun isIdleWithoutResumption(): Boolean =
        restoreCoordinator.outcome != RestoreCoordinator.Outcome.HANDED_TO_RESUMPTION && !player.playWhenReady

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

    private fun buildPlayer(): ExoPlayer = playbackPlayerBuilder(this).build()

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
                        queuePersister.onPlayingTick()
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
        private const val FINAL_SAVE_TIMEOUT_MS = 500L
    }
}

/**
 * The service player's configuration. Platform diagnostics are off: on API 31+ ExoPlayer would
 * otherwise report playback events to the platform's MediaMetricsManager, which Google may collect
 * when the user shares usage and diagnostics data, and this app promises that no data leaves the device. This turns off
 * ExoPlayer's own event reporting only: on API 31+ the platform still assigns a log-session id to the codecs and
 * AudioTrack, and OS-level codec metrics are outside the app's control.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun playbackPlayerBuilder(context: Context): ExoPlayer.Builder =
    ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .setUsePlatformDiagnostics(false)
