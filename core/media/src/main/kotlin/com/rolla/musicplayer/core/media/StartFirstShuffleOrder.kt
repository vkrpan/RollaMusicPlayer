package com.rolla.musicplayer.core.media

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random

/**
 * Returns a permutation of `0 until count` with [startIndex] first and every other index shuffled
 * after it.
 *
 * Media3's [DefaultShuffleOrder] is a uniform random permutation that does NOT put the start item
 * first. With repeat OFF playback ends at the permutation's last item, so every item that happened
 * to sit before the start item was unreachable. A start-first order makes the whole list follow it.
 *
 * Returns an empty array for `count <= 0`. An out-of-range [startIndex] (including
 * `C.INDEX_UNSET`) is treated as "no start item": the result is then a plain shuffle of all indices.
 * Deterministic for a given [random] seed.
 */
internal fun startFirstShuffleOrder(count: Int, startIndex: Int, random: Random): IntArray {
    // Empty for count <= 0: an empty range.
    val indices = (0 until count).toList()
    return if (startIndex in indices) {
        val rest = indices.filter { it != startIndex }.shuffled(random)
        (listOf(startIndex) + rest).toIntArray()
    } else {
        indices.shuffled(random).toIntArray()
    }
}

/**
 * The wiring decision: the start-first order to apply, or `null` when nothing should change.
 *
 * Null when shuffle is off, when [currentIndex] isn't a valid item, or when the current shuffle
 * order already starts with the current item ([firstShuffledIndex] == [currentIndex]). The last
 * check is what keeps re-application idempotent: applying an order makes it true.
 */
internal fun startFirstShuffleOrderToApply(
    shuffleEnabled: Boolean,
    itemCount: Int,
    currentIndex: Int,
    firstShuffledIndex: Int,
    random: Random,
): IntArray? = when {
    !shuffleEnabled -> null
    currentIndex !in 0 until itemCount -> null
    firstShuffledIndex == currentIndex -> null
    else -> startFirstShuffleOrder(itemCount, currentIndex, random)
}

/**
 * Makes [player]'s shuffle order start at the current item, so Shuffle (random start index, then
 * the rest of the list) reaches every track even with repeat OFF. [PlaybackService] registers it as
 * a listener on the player it owns.
 *
 * Triggers:
 * - shuffle turns on ([onShuffleModeEnabledChanged] with `true`);
 * - the current item changes because the playlist was replaced or the current item removed
 *   ([onMediaItemTransition] with `MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED`). `setMediaItems`
 *   always creates new media-source holders with new window uids, so a replacement always reports
 *   this transition, even when it starts on the same song.
 *
 * Why not `onTimelineChanged(PLAYLIST_CHANGED)`: `setShuffleOrder` itself fires that (a different
 * shuffle order makes `Timeline.equals` false), so it would re-enter, and it also fires for
 * add-to-queue and tag-edit `replaceMediaItem`, where reshuffling "up next" would be a surprise.
 * `setShuffleOrder` keeps the current window, so it never fires a media-item transition: this
 * trigger can't loop. The idempotence check in [startFirstShuffleOrderToApply] is the second guard.
 *
 * Assumes one timeline window per media item, which holds for the local progressive files this app
 * plays (`ExoPlayer.setShuffleOrder` requires length == media-source count).
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class StartFirstShuffleEnforcer(
    private val player: ExoPlayer,
    private val random: Random = Random.Default,
) : Player.Listener {

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled) enforce()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) enforce()
    }

    private fun enforce() {
        val timeline = player.currentTimeline
        val order = startFirstShuffleOrderToApply(
            shuffleEnabled = player.shuffleModeEnabled,
            itemCount = timeline.windowCount,
            currentIndex = player.currentMediaItemIndex,
            firstShuffledIndex = timeline.getFirstWindowIndex(true),
            random = random,
        ) ?: return
        try {
            player.setShuffleOrder(DefaultShuffleOrder(order, random.nextLong()))
        } catch (e: IllegalArgumentException) {
            // A multi-window source would break the one-window-per-item assumption; keep Media3's own order rather
            // than crash the service from inside a player callback.
            Log.w(TAG, "Start-first shuffle order skipped", e)
        }
    }

    private companion object {
        const val TAG = "StartFirstShuffle"
    }
}
