package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.ListenableFuture
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaybackStateRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ExecutionException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionCallbackTest {

    private lateinit var player: ExoPlayer
    private lateinit var restoredOrder: RestoredShuffleOrder
    private val repository = FakePlaybackStateRepository()
    private val session = mockk<MediaSession>()
    private val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
        // Production order (PlaybackService): the enforcer first, then RestoredShuffleOrder.
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        restoredOrder = RestoredShuffleOrder(player) { 99L }
        player.addListener(restoredOrder)
        every { session.player } returns player
    }

    @After
    fun tearDown() = player.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun song(i: Int) = Song(
        id = "s$i",
        title = "T$i",
        artist = "A",
        album = "B",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/$i",
        artworkUri = "",
    )

    private fun TestScope.callback(onNothingToResume: () -> Unit = {}): PlaybackSessionCallback {
        val main = StandardTestDispatcher(testScheduler)
        val coordinator = RestoreCoordinator(repository, backgroundScope, main) {}
        return PlaybackSessionCallback(backgroundScope, main, coordinator, restoredOrder, onNothingToResume)
    }

    /** Media3's own seeded order for [n] items starting at [startIndex], read from a bare player with no listeners. */
    private fun media3Order(n: Int, startIndex: Int): List<Int> {
        val bare = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            bare.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
            bare.shuffleModeEnabled = true
            bare.setMediaItems((0 until n).map { song(it).toMediaItem() }, startIndex, C.TIME_UNSET)
            idle()
            return bare.queueState().items.map { it.index }
        } finally {
            bare.release()
        }
    }

    // Spec §7.6. After Media3 sets the returned items (simulated as MediaSessionImpl does), the saved order and modes
    // hold. Media3's seeded order does not start at the current window (3), so the enforcer reshuffles first and the
    // saved order must replace that; the first assertion pins this fixture property. Mutations: startIndex 0 → RED;
    // skip prepareRestore (no arm) → the enforcer's order → RED; drop the position → RED.
    @Test
    fun resumptionReturnsTheSavedQueueAndRestoresItsOrderAndModes() = runTest {
        assertNotEquals(3, media3Order(4, startIndex = 3).first())
        repository.saved = SavedPlayback(
            songs = (0 until 4).map(::song),
            shuffleOrder = listOf(2, 0, 3, 1),
            state = SavedPlaybackState(currentIndex = 3, positionMs = 61_000L, shuffleEnabled = true, RepeatMode.ONE),
        )

        val future = callback().onPlaybackResumption(session, controller, isForPlayback = true)
        // runCurrent, not advanceUntilIdle: the callback's coroutine and the load live in backgroundScope.
        runCurrent()
        assertTrue(future.isDone)
        val result = future.get()
        player.setMediaItems(result.mediaItems, result.startIndex, result.startPositionMs)
        idle()

        assertEquals((0 until 4).map { "s$it" }, result.mediaItems.map { it.mediaId })
        assertEquals(3, result.startIndex)
        assertEquals(61_000L, result.startPositionMs)
        assertEquals(listOf(2, 0, 3, 1), player.queueState().items.map { it.index })
        assertEquals(true, player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
    }

    // Media3 1.11's isForPlayback has no meaning here yet: false resumes the same saved queue exactly as true does
    // (the test above), with the same fixture guard so the order assertion cannot pass vacuously. Mutation: fail the
    // future when isForPlayback is false → future.get() throws → RED.
    @Test
    fun resumptionNotForPlayback_resumesTheSameSavedQueue() = runTest {
        assertNotEquals(3, media3Order(4, startIndex = 3).first())
        repository.saved = SavedPlayback(
            songs = (0 until 4).map(::song),
            shuffleOrder = listOf(2, 0, 3, 1),
            state = SavedPlaybackState(currentIndex = 3, positionMs = 61_000L, shuffleEnabled = true, RepeatMode.ONE),
        )

        val future = callback().onPlaybackResumption(session, controller, isForPlayback = false)
        runCurrent()
        assertTrue(future.isDone)
        val result = future.get()
        player.setMediaItems(result.mediaItems, result.startIndex, result.startPositionMs)
        idle()

        assertEquals((0 until 4).map { "s$it" }, result.mediaItems.map { it.mediaId })
        assertEquals(3, result.startIndex)
        assertEquals(61_000L, result.startPositionMs)
        assertEquals(listOf(2, 0, 3, 1), player.queueState().items.map { it.index })
        assertEquals(true, player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
    }

    // Mutation: return an empty MediaItemsWithStartPosition instead of failing → future.get() succeeds → RED.
    @Test
    fun nothingSaved_failsTheFuture() = runTest {
        val future = callback().onPlaybackResumption(session, controller, isForPlayback = true)
        runCurrent()
        assertTrue(future.isDone)

        val failure = runCatching { future.get() }.exceptionOrNull()
        assertTrue(failure is ExecutionException)
    }

    // A service scope cancelled before the coroutine runs must not leave Media3 waiting on a future that never
    // completes. Mutation: drop the invokeOnCompletion handler → the body never runs, the future stays pending → RED.
    @Test
    fun aCancelledScope_cancelsTheFuture() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        val coordinator = RestoreCoordinator(repository, backgroundScope, main) {}
        val cancelledScope = CoroutineScope(Job().apply { cancel() })

        val future = PlaybackSessionCallback(cancelledScope, main, coordinator, restoredOrder) {}
            .onPlaybackResumption(session, controller, isForPlayback = true)
        runCurrent()

        assertTrue(future.isCancelled)
    }

    // Ruling P6: nothing saved reports once, before the future fails. Mutations: drop the call in the nothing-saved
    // branch → RED (count 0); move it after setException → RED (the future is already done when it runs).
    @Test
    fun nothingSaved_reportsNothingToResumeOnceBeforeFailing() = runTest {
        var reports = 0
        var doneWhenReported: Boolean? = null
        lateinit var future: ListenableFuture<MediaSession.MediaItemsWithStartPosition>
        future = callback {
            reports++
            doneWhenReported = future.isDone
        }.onPlaybackResumption(session, controller, isForPlayback = true)
        runCurrent()

        assertEquals(1, reports)
        assertEquals(false, doneWhenReported)
        assertTrue(runCatching { future.get() }.exceptionOrNull() is ExecutionException)
    }

    // A failure while preparing the resumption also leaves nothing to resume. Mutation: drop the call in the catch
    // branch → RED (count 0).
    @Test
    fun aFailedResumption_reportsNothingToResume() = runTest {
        repository.saved = SavedPlayback(
            songs = (0 until 2).map(::song),
            shuffleOrder = listOf(1, 0),
            state = SavedPlaybackState(currentIndex = 1, positionMs = 5_000L, shuffleEnabled = false, RepeatMode.OFF),
        )
        every { session.player } throws IllegalStateException("player gone")
        var reports = 0

        val future = callback { reports++ }.onPlaybackResumption(session, controller, isForPlayback = true)
        runCurrent()

        assertEquals(1, reports)
        assertTrue(runCatching { future.get() }.exceptionOrNull() is ExecutionException)
    }

    // Mutation: report unconditionally once savedForResumption returns (move the call above the null check) → RED.
    @Test
    fun aSuccessfulResumption_doesNotReport() = runTest {
        repository.saved = SavedPlayback(
            songs = (0 until 2).map(::song),
            shuffleOrder = listOf(1, 0),
            state = SavedPlaybackState(currentIndex = 1, positionMs = 5_000L, shuffleEnabled = false, RepeatMode.OFF),
        )
        var reports = 0

        val future = callback { reports++ }.onPlaybackResumption(session, controller, isForPlayback = true)
        runCurrent()

        assertEquals(1, future.get().startIndex)
        assertEquals(0, reports)
    }

    private companion object {
        const val MEDIA3_ORDER_SEED = 1L
    }
}
