package com.rolla.musicplayer.core.media

/**
 * Pure state machine that decides when a "playback started" event should be recorded for the
 * currently loaded media item.
 *
 * A play must be recorded exactly once per media item load — i.e. once per [onMediaItemTransition],
 * the first time playback transitions to "playing" for that item. Subsequent pause/resume cycles
 * on the same item must not re-record. A later transition to a new item (even a repeat-one replay
 * of the same song, which re-triggers [onMediaItemTransition]) resets the tracker so the next
 * "playing" transition records again.
 *
 * This class has no Android or coroutine dependencies so it can be unit tested in isolation;
 * [PlaybackService] owns the side effect (launching the repository call) based on the songId this
 * class returns.
 */
class PlaybackTracker {
    private var hasRecordedForCurrentItem = false

    /** Call on every `Player.Listener.onMediaItemTransition`, regardless of whether the new item is null. */
    fun onMediaItemTransition() {
        hasRecordedForCurrentItem = false
    }

    /**
     * Call on every `Player.Listener.onIsPlayingChanged`.
     *
     * @return the songId that should be recorded as "playback started", or null if nothing should
     * be recorded (either playback stopped/paused, there is no current media item, or a play was
     * already recorded for the current item).
     */
    fun onIsPlayingChanged(isPlaying: Boolean, mediaId: String?): String? {
        if (!isPlaying || mediaId == null || hasRecordedForCurrentItem) return null
        hasRecordedForCurrentItem = true
        return mediaId
    }
}
