package com.rolla.musicplayer.core.media

import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Invokes every [PlaybackUpdateHook] contributed to the Hilt multibinding, containing each hook's
 * failure so one misbehaving hook (e.g. `:feature:widget`'s updater) can never take down
 * [PlaybackService]'s background work.
 *
 * [PlaybackService] calls [dispatch] from exactly three places, always *after* it has finished
 * writing the relevant change to [PlaybackStateHolder] for that same trigger -- hook implementations
 * read the holder, so the holder must already reflect the new state by the time a hook runs:
 *  1. `Player.Listener.onMediaItemTransition` -- after [PlaybackStateHolder.setCurrentSong] and
 *     [PlaybackStateHolder.setDurationMs].
 *  2. `Player.Listener.onIsPlayingChanged` -- after [PlaybackStateHolder.setIsPlaying].
 *  3. The position ticker -- after [PlaybackStateHolder.setPositionMs], and only on ticks where the
 *     player is actually ready and playing. The ticker's own loop keeps running on its fixed cadence
 *     regardless of play state (it does not stop while paused), so gating the [dispatch] call on
 *     that same "did we just update the position" condition is what keeps paused playback from
 *     producing hook churn.
 *
 * Every call site wraps its [dispatch] call in `serviceScope.launch { ... }` (`Dispatchers.Default`)
 * so hooks never run synchronously on the player's callback thread -- see [PlaybackService].
 */
@Singleton
class PlaybackUpdateDispatcher @Inject constructor(
    private val hooks: Set<@JvmSuppressWildcards PlaybackUpdateHook>,
) {

    /**
     * Invokes every contributed hook exactly once, isolating each call via [runHookSafely] so one
     * hook's exception can neither skip the remaining hooks nor propagate out of [dispatch] itself.
     */
    fun dispatch() {
        for (hook in hooks) {
            runHookSafely(hook)
        }
    }

    private fun runHookSafely(hook: PlaybackUpdateHook) {
        try {
            hook.onPlaybackStateChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            // Dropped deliberately (house best-effort idiom, same as TagSaveFinalizer): a broken
            // hook must never crash the service or starve the remaining hooks.
        }
    }
}
