package com.rolla.musicplayer.core.media

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random

/**
 * Puts a saved shuffle order back after a restore (queue-restore spec §7.5, ruling R3). [arm] it just before the
 * restore sets its items; on the next playlist replacement it applies the order once and disarms.
 *
 * Register it AFTER [StartFirstShuffleEnforcer]. Both react to `MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED`, and the
 * later listener's `setShuffleOrder` is the one that sticks. `setShuffleOrder` fires no media-item transition, so
 * nothing re-enters.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class RestoredShuffleOrder(
    private val player: ExoPlayer,
    private val seed: () -> Long = { Random.nextLong() },
) : Player.Listener {

    private var pending: IntArray? = null

    val isArmed: Boolean get() = pending != null

    fun arm(order: List<Int>) {
        pending = order.toIntArray()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        val order = pending ?: return
        pending = null
        try {
            player.setShuffleOrder(DefaultShuffleOrder(order, seed()))
        } catch (e: IllegalArgumentException) {
            // The restored list no longer matches the order's length; keep the enforcer's order rather than crash.
            Log.w(TAG, "Restored shuffle order skipped", e)
        }
    }

    private companion object {
        const val TAG = "RestoredShuffleOrder"
    }
}
