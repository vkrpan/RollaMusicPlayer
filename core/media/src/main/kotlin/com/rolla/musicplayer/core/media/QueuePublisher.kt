package com.rolla.musicplayer.core.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * Publishes [player]'s queue into [holder] on the player's thread (ruling 1: the service publishes, the controller
 * never polls). [onTimelineChanged] also fires for StartFirstShuffleEnforcer's setShuffleOrder, so a reshuffle
 * republishes itself. Register it before the enforcer.
 */
internal class QueuePublisher(
    private val player: Player,
    private val holder: PlaybackStateHolder,
) : Player.Listener {

    /** A list change, a tag-edit replaceMediaItem or a new shuffle order changes the queue's contents or order. */
    override fun onTimelineChanged(timeline: Timeline, reason: Int) = publish()

    /** The current entry moved (skip, seek to another item, auto-advance), so currentIndex changes. */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()

    /** Shuffle switches the playback order between timeline order and the shuffle order. */
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = publish()

    private fun publish() = holder.setQueue(player.queueState())
}
