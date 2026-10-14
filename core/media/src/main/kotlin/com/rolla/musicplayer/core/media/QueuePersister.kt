package com.rolla.musicplayer.core.media

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.rolla.musicplayer.core.data.repository.PlaybackStateRepository
import com.rolla.musicplayer.core.model.SavedPlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A write waiting for the persister's single consumer (queue-restore spec §6.2). */
internal sealed interface PendingWrite {
    data class Queue(
        val songIds: List<String>,
        val shuffleOrder: List<Int>,
        val state: SavedPlaybackState,
    ) : PendingWrite

    data class State(val state: SavedPlaybackState) : PendingWrite

    data object Clear : PendingWrite
}

/**
 * The latest write wins, except that a newer state folds into a pending queue write instead of replacing it, and never
 * replaces a pending clear (once the queue is cleared, a state has no queue to describe).
 */
internal fun PendingWrite?.mergedWith(next: PendingWrite): PendingWrite = when {
    this is PendingWrite.Queue && next is PendingWrite.State -> copy(state = next.state)
    this is PendingWrite.Clear && next is PendingWrite.State -> this
    else -> next
}

/**
 * Saves the player's queue and state (queue-restore spec §6). It reads the player on its application thread (every
 * callback, [enable], [onPlayingTick] and [saveFinal] run there) and writes through [repository] from [scope], one
 * write at a time, conflating bursts. It writes nothing until [enable] (spec §6.3: not while the restore is loading).
 */
internal class QueuePersister(
    private val player: Player,
    private val repository: PlaybackStateRepository,
    scope: CoroutineScope,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : Player.Listener {

    private val lock = Any()
    private var pending: PendingWrite? = null

    // Serializes the consumer with saveFinal: a write is taken from `pending` and written under it, so writes land in
    // the order they were taken and an older write can never finish after a newer one.
    private val writeMutex = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var enabled = false
    private var lastStateSaveAt = 0L

    // A queue write failed, so the saved rows may not match the player's queue: the next state write is upgraded to a
    // queue write (a state indexes the queue it was taken from). Set and cleared by the drain, read on the main thread.
    @Volatile
    private var queueDirty = false

    init {
        scope.launch {
            for (ignored in signal) drain()
        }
    }

    /**
     * Starts saving. Call it on the player's application thread, like every callback. The queue already in the player
     * (a restore that dropped missing songs, or a queue that arrived while the restore was loading) is written at once,
     * so later states index the rows they describe.
     */
    fun enable() {
        enabled = true
        if (!player.currentTimeline.isEmpty) enqueue(queueWrite())
    }

    // The queue is rewritten only for a playlist change (set, add, remove, move, replace, setShuffleOrder), never for a
    // SOURCE_UPDATE such as a prepared item's real duration. An empty timeline clears the save whatever the reason.
    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (!enabled) return
        if (timeline.isEmpty) {
            enqueue(PendingWrite.Clear)
        } else if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            enqueue(queueWrite())
        }
    }

    // No onMediaItemTransition (ruling R9): every transition also brings a discontinuity or a timeline change.

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) enqueueState()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) = enqueueState()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = enqueueState()

    override fun onRepeatModeChanged(repeatMode: Int) = enqueueState()

    /** Called every second while playing; writes the position at most every [STATE_SAVE_INTERVAL_MS]. */
    fun onPlayingTick() {
        if (!enabled) return
        if (elapsedRealtime() - lastStateSaveAt >= STATE_SAVE_INTERVAL_MS) enqueueState()
    }

    /**
     * The final save as the service stops (ruling R7: PlaybackService.onDestroy, inside its bounded runBlocking on the
     * application thread). Folds the current state (a whole queue write while the queue is dirty) into whatever is
     * still pending, so a queue write not yet drained carries it, and writes that inline, after any write already in
     * flight. Does nothing while disabled or empty.
     */
    suspend fun saveFinal() {
        if (!enabled || player.currentTimeline.isEmpty) return
        synchronized(lock) { pending = pending.mergedWith(stateWrite()) }
        drain()
    }

    private fun queueWrite(): PendingWrite.Queue {
        val timeline = player.currentTimeline
        val count = timeline.windowCount
        val window = Timeline.Window()
        val songIds = (0 until count).map { timeline.getWindow(it, window).mediaItem.mediaId }
        val walk = playbackOrder(count, timeline.getFirstWindowIndex(true)) {
            timeline.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, true)
        }
        val shuffleOrder = if (walk.size == count) walk else (0 until count).toList()
        return PendingWrite.Queue(songIds, shuffleOrder, currentState())
    }

    // Upgraded to a queue write while the saved rows may be stale (queueDirty).
    private fun stateWrite(): PendingWrite = if (queueDirty) queueWrite() else PendingWrite.State(currentState())

    private fun enqueueState() {
        if (!enabled || player.currentTimeline.isEmpty) return
        enqueue(stateWrite())
    }

    private fun enqueue(write: PendingWrite) {
        if (write !is PendingWrite.Clear) lastStateSaveAt = elapsedRealtime()
        synchronized(lock) { pending = pending.mergedWith(write) }
        signal.trySend(Unit)
    }

    private fun currentState() = SavedPlaybackState(
        currentIndex = player.currentMediaItemIndex,
        positionMs = player.currentPosition.coerceAtLeast(0L),
        shuffleEnabled = player.shuffleModeEnabled,
        repeatMode = player.repeatMode.toRepeatMode(),
    )

    private suspend fun drain() = writeMutex.withLock {
        val write = synchronized(lock) { pending.also { pending = null } } ?: return@withLock
        try {
            when (write) {
                is PendingWrite.Queue -> {
                    repository.saveQueue(write.songIds, write.shuffleOrder, write.state)
                    queueDirty = false
                }
                is PendingWrite.State -> repository.saveState(write.state)
                PendingWrite.Clear -> repository.clear()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Logged and dropped (constraints: persistence never takes playback down); the next write still runs.
            Log.e(TAG, "Saving the playback queue failed", e)
            if (write is PendingWrite.Queue) queueDirty = true
        }
    }

    private companion object {
        const val TAG = "QueuePersister"
        const val STATE_SAVE_INTERVAL_MS = 10_000L
    }
}
