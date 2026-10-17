package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.media.RestoreCoordinator.Outcome
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaybackStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RestoreCoordinatorTest {

    private lateinit var player: ExoPlayer
    private lateinit var restoredOrder: RestoredShuffleOrder
    private val repository = FakePlaybackStateRepository()
    private val holder = PlaybackStateHolder()
    private var settledCount = 0

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
        // Production order (PlaybackService): the enforcer first, then RestoredShuffleOrder.
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        restoredOrder = RestoredShuffleOrder(player) { 99L }
        player.addListener(restoredOrder)
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

    private val saved = SavedPlayback(
        songs = (0 until 5).map(::song),
        shuffleOrder = listOf(3, 0, 4, 1, 2),
        state = SavedPlaybackState(currentIndex = 2, positionMs = 83_000L, shuffleEnabled = true, RepeatMode.ALL),
    )

    private fun TestScope.coordinator(): RestoreCoordinator {
        val main = StandardTestDispatcher(testScheduler)
        return RestoreCoordinator(repository, backgroundScope, main) { settledCount++ }
    }

    // runCurrent, not advanceUntilIdle: the load and the restore live in backgroundScope, and advanceUntilIdle stops as
    // soon as no foreground task is left, so it would never run them.
    private fun TestScope.settle() {
        runCurrent()
        idle()
        runCurrent()
    }

    /** Media3's own seeded order for the saved songs from [startIndex], read from a bare player with no listeners. */
    private fun media3Order(startIndex: Int): List<Int> {
        val bare = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            bare.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
            bare.shuffleModeEnabled = true
            bare.setMediaItems(saved.songs.map { it.toMediaItem() }, startIndex, C.TIME_UNSET)
            idle()
            return bare.queueState().items.map { it.index }
        } finally {
            bare.release()
        }
    }

    // Spec §2 and §7.2. Media3's seeded order does not start at the current window (2; with seed 1 it does start at 1),
    // so the enforcer reshuffles first and the saved order must replace that; the first assertion pins this fixture
    // property. Mutations: skip
    // `arm` → the enforcer's start-first order → RED; call play() → playWhenReady → RED; drop the start position → RED;
    // skip holder.setPositionMs → RED.
    @Test
    fun appliesTheSavedQueuePausedWithItsOrderPositionAndModes() = runTest {
        assertNotEquals(2, media3Order(startIndex = 2).first())
        repository.saved = saved
        val coordinator = coordinator()

        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        assertEquals(Outcome.APPLIED, coordinator.outcome)
        assertEquals(
            (0 until 5).map { "s$it" },
            (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
        )
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(83_000L, player.currentPosition)
        assertEquals(listOf(3, 0, 4, 1, 2), player.queueState().items.map { it.index })
        assertEquals(true, player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        assertFalse(player.playWhenReady)
        assertEquals(83_000L, holder.positionMs.value)
        assertEquals(1, settledCount)
    }

    // Review Focus 4: the user's queue wins, and its order and modes are left alone. Mutations: drop the
    // `mediaItemCount > 0` check → the saved queue replaces theirs → RED; prepareRestore above that check → armed, and
    // the saved modes overwrite theirs → RED.
    @Test
    fun discardsWhenAQueueArrivedFirst() = runTest {
        repository.saved = saved
        repository.loadGate = CompletableDeferred()
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        player.setMediaItems(listOf(MediaItem.fromUri("content://media/external/audio/media/77")), 0, C.TIME_UNSET)
        repository.loadGate!!.complete(Unit)
        settle()

        assertEquals(Outcome.DISCARDED, coordinator.outcome)
        assertEquals(1, player.mediaItemCount)
        assertEquals(1, settledCount)
        assertFalse(restoredOrder.isArmed)
        assertFalse(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
    }

    // Mutation: don't call onSettled on DISCARDED → settledCount 0 (the persister would never start) → RED.
    @Test
    fun nothingSaved_discardsAndStillSettles() = runTest {
        val coordinator = coordinator()

        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        assertEquals(Outcome.DISCARDED, coordinator.outcome)
        assertEquals(0, player.mediaItemCount)
        assertEquals(1, settledCount)
    }

    // Spec §8 and ruling P1 (a failed load is logged, not cleared). Mutations: no catch around the load → the restore
    // job fails and never settles → RED; clear the save in the catch → clearCount 1 → RED.
    @Test
    fun aFailingLoad_discardsAndStillSettles() = runTest {
        repository.loadFailure = IOException("corrupt")
        val coordinator = coordinator()

        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        assertEquals(Outcome.DISCARDED, coordinator.outcome)
        assertEquals(1, settledCount)
        assertEquals(0, repository.clearCount)
    }

    // Ruling R1/R6: a resumption that arrives while loading takes the first load, and the in-app restore stands down.
    // Mutations: restoreInto ignores HANDED_TO_RESUMPTION → it also applies → 5 items → RED; restoreInto runs
    // prepareRestore before the HANDED_TO_RESUMPTION check → a stale arm → RED.
    @Test
    fun aResumptionWhileLoadingTakesTheRestore() = runTest {
        repository.saved = saved
        repository.loadGate = CompletableDeferred()
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        val resumed = async { coordinator.savedForResumption() }
        settle()
        repository.loadGate!!.complete(Unit)
        settle()

        assertEquals(saved, resumed.await())
        assertEquals(Outcome.HANDED_TO_RESUMPTION, coordinator.outcome)
        assertEquals(0, player.mediaItemCount)
        assertEquals(1, repository.loadCount)
        assertEquals(1, settledCount)
        assertFalse(restoredOrder.isArmed)
    }

    // Ruling R6. Mutation: always return the first load → the stale queue → RED.
    @Test
    fun aResumptionAfterSettlingReloads() = runTest {
        repository.saved = saved
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()
        assertEquals(Outcome.APPLIED, coordinator.outcome)

        val newer = saved.copy(songs = saved.songs.take(2), shuffleOrder = listOf(1, 0))
        repository.saved = newer

        assertEquals(newer, coordinator.savedForResumption())
        assertEquals(2, repository.loadCount)
    }

    // Panel-resume spec §5.4. A peek while loading shares the first load and leaves the restore to the app. Control:
    // the in-app restore applies (APPLIED, 5 items). Mutation: implement peekSaved as savedForResumption() →
    // HANDED_TO_RESUMPTION and an empty player → RED.
    @Test
    fun aPeekWhileLoading_sharesTheFirstLoadAndTheAppStillRestores() = runTest {
        repository.saved = saved
        repository.loadGate = CompletableDeferred()
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        val peeked = async { coordinator.peekSaved() }
        settle()
        repository.loadGate!!.complete(Unit)
        settle()

        assertEquals(saved, peeked.await())
        assertEquals(Outcome.APPLIED, coordinator.outcome)
        assertEquals(5, player.mediaItemCount)
        assertEquals(listOf(3, 0, 4, 1, 2), player.queueState().items.map { it.index })
        assertEquals(1, repository.loadCount)
        assertEquals(1, settledCount)
    }

    // Spec §5.4 (as ruling R6). Once settled a peek reads again and never settles a second time. Mutations: always
    // return the first load → the stale queue → RED; call onSettled in peekSaved → settledCount 2 → RED.
    @Test
    fun aPeekAfterSettling_readsAgainWithoutSettling() = runTest {
        repository.saved = saved
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()
        assertEquals(Outcome.APPLIED, coordinator.outcome)

        val newer = saved.copy(songs = saved.songs.take(2), shuffleOrder = listOf(1, 0))
        repository.saved = newer

        assertEquals(newer, coordinator.peekSaved())
        assertEquals(2, repository.loadCount)
        assertEquals(Outcome.APPLIED, coordinator.outcome)
        assertEquals(1, settledCount)
    }

    // Spec §5.4. With no in-app restore started, a peek leaves the coordinator LOADING. Control: it returns the save.
    // Mutation: set outcome = HANDED_TO_RESUMPTION in peekSaved → RED.
    @Test
    fun aPeekAlone_leavesTheCoordinatorLoading() = runTest {
        repository.saved = saved
        val coordinator = coordinator()

        val peeked = async { coordinator.peekSaved() }
        settle()

        assertEquals(saved, peeked.await())
        assertEquals(Outcome.LOADING, coordinator.outcome)
        assertEquals(0, settledCount)
    }

    private companion object {
        const val MEDIA3_ORDER_SEED = 1L
    }
}
