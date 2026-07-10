package com.rolla.musicplayer.core.media

import androidx.annotation.VisibleForTesting
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.rolla.musicplayer.core.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Binds `:core:data`'s [SettingsRepository] playback settings onto the service's live [ExoPlayer].
 *
 * Mirrors [com.rolla.musicplayer.core.media.equalizer.EqualizerSessionManager]'s role in
 * `PlaybackService`: a small, Hilt-injected collaborator the service starts once it has built its
 * player ([bind], called from `onCreate`) and stops once, on teardown ([unbind], called from
 * `onDestroy`) -- rather than wiring `collectLatest` loops inline in the service.
 *
 * ### Why `collectLatest`, and why the first emission is the "re-apply on start"
 * [SettingsRepository.observePlaybackSpeed] and [SettingsRepository.observeSkipSilence] are both
 * DataStore-backed flows that replay their *current* value to a new collector immediately -- so the
 * very first emission delivered right after [bind] re-applies whatever the user persisted in a
 * previous session; there is no separate "read the initial value on startup" step. `collectLatest`
 * means a rapid back-to-back settings change (e.g. dragging the speed slider) only ever applies the
 * newest value -- an in-flight apply for a now-stale value is simply abandoned.
 *
 * ### Threading
 * `ExoPlayer.setPlaybackSpeed` and `ExoPlayer.skipSilenceEnabled` are Player-thread-confined APIs --
 * they must be called on the player's application thread. [applyPlaybackSpeed] and
 * [applySkipSilence] switch onto [Dispatchers.Main.immediate] before touching the player, the same
 * pattern `PlaybackService`'s position ticker uses for `player.currentPosition`. Collection itself
 * runs on whatever dispatcher the caller's [CoroutineScope] uses (the service passes its
 * `Dispatchers.Default` service scope) -- only the player call itself needs the main thread.
 */
// skipSilenceEnabled is an UnstableApi -- same module-wide opt-in stance as PlaybackService /
// PlaybackStateHolder / EqualizerController.
@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class PlaybackSettingsBinder @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    private var bindJob: Job? = null

    /**
     * Starts collecting both settings and applying each new emission onto [player]. `PlaybackService`
     * calls this exactly once, from `onCreate`, right after building its player.
     */
    fun bind(player: ExoPlayer, scope: CoroutineScope) {
        bindJob = scope.launch {
            launch {
                settingsRepository.observePlaybackSpeed().collectLatest { speed ->
                    applyPlaybackSpeed(player, speed)
                }
            }
            launch {
                settingsRepository.observeSkipSilence().collectLatest { skipSilence ->
                    applySkipSilence(player, skipSilence)
                }
            }
        }
    }

    /**
     * Stops collecting. `cancelAndJoin` (not a bare `cancel`) mirrors `EqualizerSessionManager`'s
     * teardown ordering requirement in `PlaybackService.onDestroy`: it waits for any in-flight apply
     * to reach its next suspension point before the caller releases the player, so no apply call can
     * land on an already-released player. Safe to call when never bound.
     */
    suspend fun unbind() {
        bindJob?.cancelAndJoin()
        bindJob = null
    }

    /**
     * Applies [speed] to [player] on the player's application thread. Split out from [bind] and
     * marked [VisibleForTesting] purely so tests can exercise the player call directly, mirroring
     * `EqualizerSessionManager.applySettings`.
     */
    @VisibleForTesting
    internal suspend fun applyPlaybackSpeed(player: ExoPlayer, speed: Float) =
        withContext(Dispatchers.Main.immediate) {
            player.setPlaybackSpeed(speed)
        }

    /** Applies [skipSilence] to [player] on the player's application thread. See [applyPlaybackSpeed]. */
    @VisibleForTesting
    internal suspend fun applySkipSilence(player: ExoPlayer, skipSilence: Boolean) =
        withContext(Dispatchers.Main.immediate) {
            player.skipSilenceEnabled = skipSilence
        }
}
