package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.data.repository.PlaybackStateRepository
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlaybackState
import com.rolla.musicplayer.core.testing.FakePlaybackStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class)
class QueuePersisterTest {

    private lateinit var player: ExoPlayer
    private val repository = FakePlaybackStateRepository()
    private var now = 0L

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() = player.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun items(n: Int) = (0 until n).map {
        MediaItem.Builder().setMediaId("id$it").setUri("content://media/external/audio/media/$it").build()
    }

    private fun TestScope.persister(
        enabled: Boolean = true,
        repo: PlaybackStateRepository = repository,
    ): QueuePersister =
        QueuePersister(player, repo, backgroundScope) { now }.also {
            player.addListener(it)
            if (enabled) it.enable()
        }

    // runCurrent, not advanceUntilIdle: the consumer lives in backgroundScope, and advanceUntilIdle stops as soon as no
    // foreground task is left, so it never runs a background-only drain.
    private fun TestScope.settle() {
        idle()
        runCurrent()
    }

    // Spec §6.3. The seek exercises the state path as well as the queue path. Mutations: drop the `enabled` guard in
    // onTimelineChanged → the new queue is written → RED; drop it in enqueueState → the seek's state is written → RED.
    @Test
    fun aDisabledPersisterWritesNothing() = runTest {
        persister(enabled = false)
        player.setMediaItems(items(3), 1, 5_000L)
        player.seekTo(2, 1_000L)
        settle()

        assertEquals(0, repository.queueWrites.size)
        assertEquals(0, repository.stateWrites.size)
    }

    // The shuffle order [2,0,3,1] is not slot order, and shuffle stays OFF: the ranks come from the shuffle walk
    // whatever the mode (spec §6.1). Mutations: walk with `shuffleModeEnabled` (false) → [0,1,2,3] → RED; write ids in
    // shuffle order → RED.
    @Test
    fun aNewQueueWritesItsIdsShuffleOrderAndState() = runTest {
        // Seeded so Media3's default order (unseeded, it is random) can never coincide with [2,0,3,1].
        player.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
        persister()
        player.setMediaItems(items(4), 1, 5_000L)
        player.setShuffleOrder(DefaultShuffleOrder(intArrayOf(2, 0, 3, 1), 1L))
        settle()

        val write = repository.queueWrites.last()
        assertEquals(listOf("id0", "id1", "id2", "id3"), write.songIds)
        assertEquals(listOf(2, 0, 3, 1), write.shuffleOrder)
        assertEquals(SavedPlaybackState(1, 5_000L, shuffleEnabled = false, RepeatMode.OFF), write.state)
    }

    // Spec §6.2 and Review Focus 2: a burst is one queue write carrying the latest state. Mutations: write every event
    // (no conflation) → 2+ queue writes → RED; `mergedWith` lets a State replace a pending Queue → no queue write
    // → RED.
    @Test
    fun aFiveThousandTrackQueueIsOneWrite() = runTest {
        persister()
        player.setMediaItems(items(3), 0, C.TIME_UNSET)
        player.setMediaItems(items(5_000), 10, 7_000L)
        player.seekTo(11, 9_000L)
        settle()

        assertEquals(1, repository.queueWrites.size)
        val write = repository.queueWrites.single()
        assertEquals(5_000, write.songIds.size)
        assertEquals((0 until 5_000).toSet(), write.shuffleOrder.toSet())
        assertEquals(11, write.state.currentIndex)
        assertEquals(9_000L, write.state.positionMs)
    }

    // A seek inside the current item fires only a position discontinuity (no transition, no timeline change), and a
    // seek to another item fires one too (ruling R9). Mutations (each deletes one override): onPositionDiscontinuity →
    // neither seek writes → RED; onShuffleModeEnabledChanged → RED; onRepeatModeChanged → RED.
    @Test
    fun seeksAndModeChangesWriteTheState() = runTest {
        persister()
        player.setMediaItems(items(4), 0, C.TIME_UNSET)
        settle()

        player.seekTo(0, 7_000L)
        settle()
        assertEquals(SavedPlaybackState(0, 7_000L, false, RepeatMode.OFF), repository.stateWrites.last())

        player.seekTo(2, 3_000L)
        settle()
        assertEquals(SavedPlaybackState(2, 3_000L, false, RepeatMode.OFF), repository.stateWrites.last())

        player.shuffleModeEnabled = true
        settle()
        assertEquals(true, repository.stateWrites.last().shuffleEnabled)

        player.repeatMode = Player.REPEAT_MODE_ALL
        settle()
        assertEquals(RepeatMode.ALL, repository.stateWrites.last().repeatMode)
    }

    // A pause writes the state; starting to play does not. Mutation: write on `true` as well → 2 writes → RED.
    @Test
    fun onlyAPauseWritesTheState() = runTest {
        val persister = persister()
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()
        val before = repository.stateWrites.size

        persister.onIsPlayingChanged(true)
        settle()
        persister.onIsPlayingChanged(false)
        settle()

        assertEquals(before + 1, repository.stateWrites.size)
    }

    // Spec §6.3. Mutation: treat an empty timeline like any other (skip it) → clearCount 0 → RED.
    @Test
    fun anEmptiedQueueClearsTheSave() = runTest {
        persister()
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()

        player.clearMediaItems()
        settle()

        assertEquals(1, repository.clearCount)
    }

    // Spec §6.1, every 10 s while playing. The clock starts at 50 s, not at the stamp's initial 0, so the queue write
    // must stamp it; the 60_001 tick proves the tick's own write re-stamps it. Mutations: an interval of 1_000 → the
    // 59_999 tick writes → RED; delete `lastStateSaveAt = elapsedRealtime()` → the 59_999 tick writes → RED.
    @Test
    fun aPlayingTickWritesAtMostEveryTenSeconds() = runTest {
        val persister = persister()
        now = 50_000L
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()
        val before = repository.stateWrites.size

        now = 59_999L
        persister.onPlayingTick()
        settle()
        assertEquals(before, repository.stateWrites.size)

        now = 60_000L
        persister.onPlayingTick()
        settle()
        assertEquals(before + 1, repository.stateWrites.size)

        now = 60_001L
        persister.onPlayingTick()
        settle()
        assertEquals(before + 1, repository.stateWrites.size)
    }

    // Spec §8: a failed write is logged and the next one still happens (a queue write, since the failed one left the
    // queue dirty). Mutation: remove the catch in the drain loop → the consumer dies (the test fails on the uncaught
    // IOException, or the second write never lands) → RED.
    @Test
    fun aFailedWriteDoesNotStopLaterWrites() = runTest {
        persister()
        repository.failNextWrite = IOException("disk full")
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()

        player.seekTo(1, 1_000L)
        settle()

        assertEquals(1, repository.queueWrites.last().state.currentIndex)
    }

    // Final-review I1: a queue that is already in the player when the persister is enabled (the restored one, with its
    // missing songs dropped) is written at once. Mutation: drop the enqueue in enable() → no queue write → RED.
    @Test
    fun enablingWithAQueueWritesIt() = runTest {
        val persister = persister(enabled = false)
        player.setMediaItems(items(3), 1, 4_000L)
        settle()

        persister.enable()
        settle()

        val write = repository.queueWrites.single()
        assertEquals(listOf("id0", "id1", "id2"), write.songIds)
        assertEquals(SavedPlaybackState(1, 4_000L, false, RepeatMode.OFF), write.state)
    }

    // Final-review M3: after a failed queue write the saved rows may not match the player, so the next state trigger
    // writes the whole queue; once that lands, states are plain state writes again. Mutations: drop the dirty upgrade
    // (stateWrite always returns a State) → no queue write after the seek → RED; never clear the flag after a queue
    // write lands → the second seek is a queue write too → RED.
    @Test
    fun aFailedQueueWriteUpgradesTheNextStateWrite() = runTest {
        persister()
        repository.failNextWrite = IOException("disk full")
        player.setMediaItems(items(3), 0, C.TIME_UNSET)
        settle()
        assertEquals(0, repository.queueWrites.size)

        player.seekTo(1, 2_000L)
        settle()
        assertEquals(SavedPlaybackState(1, 2_000L, false, RepeatMode.OFF), repository.queueWrites.single().state)
        assertEquals(0, repository.stateWrites.size)

        player.seekTo(2, 3_000L)
        settle()
        assertEquals(1, repository.queueWrites.size)
        assertEquals(SavedPlaybackState(2, 3_000L, false, RepeatMode.OFF), repository.stateWrites.single())
    }

    // Final-review M3 for the final save: a dirty queue is written whole by saveFinal. Mutation: saveFinal folds in a
    // plain State (`PendingWrite.State(currentState())`) → a state write and no queue write → RED.
    @Test
    fun saveFinalWritesADirtyQueueWhole() = runTest {
        val persister = persister()
        repository.failNextWrite = IOException("disk full")
        player.setMediaItems(items(3), 0, C.TIME_UNSET)
        settle()
        player.removeListener(persister)
        player.seekTo(2, 6_000L)
        idle()

        persister.saveFinal()

        assertEquals(SavedPlaybackState(2, 6_000L, false, RepeatMode.OFF), repository.queueWrites.single().state)
        assertEquals(0, repository.stateWrites.size)
    }

    // Ruling P2: only a playlist change rewrites the queue. prepare() makes Media3 replace its placeholder timeline
    // with the source's own (reason SOURCE_UPDATE); `reasons` proves that event really arrived. Mutation: drop the
    // reason filter → the SOURCE_UPDATE rewrites the queue → RED.
    @Test
    fun aSourceUpdateDoesNotRewriteTheQueue() = runTest {
        persister()
        val reasons = mutableListOf<Int>()
        player.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    reasons += reason
                }
            },
        )
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()
        val before = repository.queueWrites.size

        player.prepare()
        awaitMainLooper { Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE in reasons }
        settle()

        assertEquals(1, before)
        assertEquals(before, repository.queueWrites.size)
    }

    // Ruling R5: a state change on an empty player describes no queue, so nothing is written. Mutation: delete the
    // empty-timeline return in enqueueState → the repeat change and the pause write a state → RED.
    @Test
    fun aStateChangeOnAnEmptyPlayerWritesNothing() = runTest {
        val persister = persister()
        player.repeatMode = Player.REPEAT_MODE_ALL
        persister.onIsPlayingChanged(false)
        settle()

        assertEquals(0, repository.stateWrites.size)
    }

    // Ruling R7. Mutation: saveFinal ignores `enabled` → the state is written → RED.
    @Test
    fun saveFinalWritesNothingWhileDisabled() = runTest {
        val persister = persister(enabled = false)
        player.setMediaItems(items(2), 1, 4_000L)
        idle()

        persister.saveFinal()

        assertEquals(0, repository.queueWrites.size)
        assertEquals(0, repository.stateWrites.size)
    }

    // Rulings R5 and R7. Mutation: saveFinal ignores an empty timeline → a state with no queue is written → RED.
    @Test
    fun saveFinalWritesNothingForAnEmptyPlayer() = runTest {
        val persister = persister()

        persister.saveFinal()

        assertEquals(0, repository.stateWrites.size)
        assertEquals(0, repository.queueWrites.size)
    }

    // Ruling R7: a queue change the consumer has not drained yet (no runCurrent) is written by saveFinal, carrying the
    // final state. The listener is removed before the last seek so the position moves without an event, as it does
    // during playback between ticks. Mutations: saveFinal writes only the state → no queue write → RED; saveFinal
    // drains without folding in the current state → the queue write carries (1, 4_000) → RED.
    @Test
    fun saveFinalWritesAnUndrainedQueueWithTheFinalState() = runTest {
        val persister = persister()
        player.setMediaItems(items(3), 1, 4_000L)
        idle()
        player.removeListener(persister)
        player.seekTo(2, 6_000L)
        idle()

        persister.saveFinal()

        assertEquals(1, repository.queueWrites.size)
        assertEquals(SavedPlaybackState(2, 6_000L, false, RepeatMode.OFF), repository.queueWrites.single().state)
        assertEquals(0, repository.stateWrites.size)
    }

    // Ruling R7 with a write in flight: the consumer is mid-write of (1, 1_000) on a slow disk when the service stops.
    // saveFinal waits for it, then writes the final state, so the final state is the last one saved. Mutation: drain
    // without writeMutex → saveFinal writes first and the stale (1, 1_000) lands last → RED.
    @Test
    fun saveFinalLandsAfterAWriteInFlight() = runTest {
        val gated = GatedRepository(repository)
        val persister = persister(repo = gated)
        player.setMediaItems(items(3), 0, C.TIME_UNSET)
        settle()
        val gate = CompletableDeferred<Unit>()
        gated.gate = gate
        player.seekTo(1, 1_000L)
        settle()
        player.seekTo(2, 2_000L)
        idle()

        val finalSave = launch { persister.saveFinal() }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        finalSave.join()

        val inFlight = SavedPlaybackState(1, 1_000L, false, RepeatMode.OFF)
        val final = SavedPlaybackState(2, 2_000L, false, RepeatMode.OFF)
        assertEquals(listOf(inFlight, final), repository.stateWrites)
    }

    /** Idles the main looper until [condition] holds, giving ExoPlayer's playback thread real time to post to it. */
    private fun awaitMainLooper(condition: () -> Boolean) {
        repeat(AWAIT_ATTEMPTS) {
            idle()
            if (condition()) return
            Thread.sleep(AWAIT_STEP_MS)
        }
        throw AssertionError("condition not met after ${AWAIT_ATTEMPTS * AWAIT_STEP_MS} ms")
    }

    /** Delegates to [delegate]; the first saveState after [gate] is set waits for it, as a slow disk write would. */
    private class GatedRepository(
        private val delegate: FakePlaybackStateRepository,
    ) : PlaybackStateRepository by delegate {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun saveState(state: SavedPlaybackState) {
            gate?.let {
                gate = null
                it.await()
            }
            delegate.saveState(state)
        }
    }

    private companion object {
        const val MEDIA3_ORDER_SEED = 42L
        const val AWAIT_ATTEMPTS = 200
        const val AWAIT_STEP_MS = 10L
    }
}
