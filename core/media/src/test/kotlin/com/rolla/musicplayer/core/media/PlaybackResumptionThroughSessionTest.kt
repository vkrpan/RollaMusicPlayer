package com.rolla.musicplayer.core.media

import android.content.Context
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaybackStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.random.Random

/**
 * This test, from the Media3 upgrade's Task 4, drives playback resumption end to end through a REAL Media3
 * [MediaLibrarySession], the session type `PlaybackService` has served since panel resume. An in-process
 * [MediaController] presses Play on an empty player; Media3 1.11.1 routes it through `onPlayRequested` and
 * `handleMediaControllerPlayRequestInternal` to [PlaybackSessionCallback.onPlaybackResumption], then sets the returned
 * items inside `callWithControllerForCurrentRequestSet` and plays (`MediaSessionImpl.java:1295-1361`).
 *
 * The callback runs with the production wiring: its scope on [Dispatchers.Default] (the service scope) and its body on
 * [Dispatchers.Main.immediate]. Media3 1.11's `MediaSession.getPlayer()` verifies the application thread
 * (`MediaSessionImpl.java:373-375`), so reading it anywhere but the main thread fails the resumption, which a mocked
 * session cannot show.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackResumptionThroughSessionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakePlaybackStateRepository()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var player: ExoPlayer
    private lateinit var restoredOrder: RestoredShuffleOrder
    private var session: MediaLibrarySession? = null
    private var controller: MediaController? = null

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(context).build()
        // Production order (PlaybackService): the enforcer first, then RestoredShuffleOrder.
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        restoredOrder = RestoredShuffleOrder(player) { 99L }
        player.addListener(restoredOrder)
    }

    @After
    fun tearDown() {
        controller?.release()
        session?.release()
        player.release()
        serviceScope.cancel()
    }

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

    // Runs the main looper (Media3's application thread) until [condition] holds, giving the Default threads time.
    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting on the main looper" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
    }

    // The saved queue comes back at its index and position, in its shuffle order, with its modes, and plays. The
    // enforcer's start-first order begins at 3, so the saved order (starting at 2) cannot pass by accident.
    // Mutation: launch the callback's body on its scope (Dispatchers.Default) instead of mainDispatcher → reading
    // mediaSession.player throws off the application thread → Media3 plays an empty player → RED. Mutation: drop the
    // arm in prepareRestore → Media3 sets the items with the enforcer's order [3, 1, 0, 2] → RED. The player's items
    // are not library leaves (isPlayable stays unset). Mutation: map the true path's items through asPlayableLeaf →
    // RED.
    @Test
    fun playOnAnEmptyPlayer_throughARealSession_resumesTheSavedQueueAndPlays() {
        repository.saved = SavedPlayback(
            songs = (0 until 4).map(::song),
            shuffleOrder = listOf(2, 0, 3, 1),
            state = SavedPlaybackState(currentIndex = 3, positionMs = 61_000L, shuffleEnabled = true, RepeatMode.ONE),
        )
        val main = Dispatchers.Main.immediate
        val coordinator = RestoreCoordinator(repository, serviceScope, main) {}
        val callback = PlaybackSessionCallback(serviceScope, main, coordinator, restoredOrder) {}
        val realSession = MediaLibrarySession.Builder(context, player, callback).build()
        session = realSession
        val controllerFuture = MediaController.Builder(context, realSession.token).buildAsync()
        idleUntil { controllerFuture.isDone }
        val connected = controllerFuture.get()
        controller = connected

        connected.play()
        // Media3 calls play() after setting the items on success, and on failure too (on an empty player).
        idleUntil { player.playWhenReady }

        val mediaIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        assertEquals((0 until 4).map { "s$it" }, mediaIds)
        assertEquals(3, player.currentMediaItemIndex)
        assertEquals(61_000L, player.currentPosition)
        assertEquals(listOf(2, 0, 3, 1), player.queueState().items.map { it.index })
        assertTrue(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
        assertEquals(RestoreCoordinator.Outcome.HANDED_TO_RESUMPTION, coordinator.outcome)
        assertNull(player.currentMediaItem?.mediaMetadata?.isPlayable)
    }

    // Nothing saved: the callback reports nothing-to-resume once and fails the future, so the player stays empty.
    // Media3 still plays as requested after a failed resumption (MediaSessionImpl.java:1381-1382): Util's
    // handlePlayButtonAction prepares the idle player and calls play() (Util.java:4255-4264), so playWhenReady is true.
    // Mutation: drop onNothingToResume() from the saved == null branch → the count stays 0 → RED.
    @Test
    fun playOnAnEmptyPlayer_throughARealSession_withNothingSaved_reportsNothingToResumeOnceAndStaysEmpty() {
        repository.saved = null
        var nothingToResumeCount = 0
        val main = Dispatchers.Main.immediate
        val coordinator = RestoreCoordinator(repository, serviceScope, main) {}
        val callback = PlaybackSessionCallback(serviceScope, main, coordinator, restoredOrder) {
            nothingToResumeCount++
        }
        val realSession = MediaLibrarySession.Builder(context, player, callback).build()
        session = realSession
        val controllerFuture = MediaController.Builder(context, realSession.token).buildAsync()
        idleUntil { controllerFuture.isDone }
        val connected = controllerFuture.get()
        controller = connected

        connected.play()
        // Media3 calls play() only after the resumption future has failed, so this waits past the callback.
        idleUntil { player.playWhenReady }

        assertEquals(1, nothingToResumeCount)
        assertEquals(0, player.mediaItemCount)
        assertTrue(player.playWhenReady)
        assertEquals(RestoreCoordinator.Outcome.HANDED_TO_RESUMPTION, coordinator.outcome)
    }

    // Panel-resume spec §3 and §5.3, Review Focus 5. A browser that is not the System UI gets no root: neither the
    // plain root nor the recent root. Media3 serves the recent ROOT only to the System UI, but it answers that root's
    // CHILDREN (the saved current track's title, artist, album and artwork URI) for any connected browser (spec §10;
    // see aRecentRootChildrenRequest_describesTheSavedTrackReadOnly). Control: the browser connected (the future
    // resolved). Mutations (an item without isBrowsable is rejected by LibraryResult.ofItem, so the root is valid):
    // override onGetLibraryRoot to return a browsable, non-playable root for every request → RED at the plain-root
    // assertion; return it only when params?.isRecent == true, else super → RED at the recent-root assertion.
    @Test
    fun aBrowserThatIsNotSystemUi_getsNoLibraryRoot() {
        val main = Dispatchers.Main.immediate
        val coordinator = RestoreCoordinator(repository, serviceScope, main) {}
        val callback = PlaybackSessionCallback(serviceScope, main, coordinator, restoredOrder) {}
        val realSession = MediaLibrarySession.Builder(context, player, callback).build()
        session = realSession
        val browserFuture = MediaBrowser.Builder(context, realSession.token).buildAsync()
        idleUntil { browserFuture.isDone }
        val browser = browserFuture.get()
        controller = browser

        val plain = browser.getLibraryRoot(null)
        idleUntil { plain.isDone }
        val recent = browser.getLibraryRoot(LibraryParams.Builder().setRecent(true).build())
        idleUntil { recent.isDone }

        assertEquals(LibraryResult.RESULT_ERROR_NOT_SUPPORTED, plain.get().resultCode)
        assertEquals(LibraryResult.RESULT_ERROR_NOT_SUPPORTED, recent.get().resultCode)
    }

    // Panel-resume spec §5.3 and §10. Media3 answers getChildren on the recent root for any connected browser; its only
    // gate is canResumePlaybackOnStart(), true because core:media declares MediaButtonReceiver
    // (MediaLibrarySessionImpl.java:169-195). On an IDLE player it calls onPlaybackResumption(isForPlayback = false)
    // and returns mediaItems[startIndex] (:442-468). This also pins the accepted privacy fact (spec §10): any connected
    // browser can read the saved current track. No restoreInto, so the player stays empty and IDLE. Control: the one
    // child is the saved current track (s3, T3). Mutations: give false the identity mapping instead of asPlayableLeaf
    // → LibraryResult.ofItemList rejects the item → RESULT_ERROR_UNKNOWN → RED; give false the true branch's loader,
    // onNothing and prepareRestore → RED; call onNothingToResume from the false path's beforeReturn → RED. (Passing it
    // as the false path's onNothing is invisible here, since a saved queue never reaches onNothing; the
    // PlaybackSessionCallbackTest notForPlayback_nothingSaved/aFailingLoad tests catch that one.)
    @Test
    fun aRecentRootChildrenRequest_describesTheSavedTrackReadOnly() {
        repository.saved = SavedPlayback(
            songs = (0 until 4).map(::song),
            shuffleOrder = listOf(2, 0, 3, 1),
            state = SavedPlaybackState(currentIndex = 3, positionMs = 61_000L, shuffleEnabled = true, RepeatMode.ALL),
        )
        var nothingToResumeCount = 0
        val main = Dispatchers.Main.immediate
        val coordinator = RestoreCoordinator(repository, serviceScope, main) {}
        val callback = PlaybackSessionCallback(serviceScope, main, coordinator, restoredOrder) {
            nothingToResumeCount++
        }
        val browser = connectBrowser(callback)

        val children = browser.getChildren(RECENT_ROOT_ID, 0, 1, null)
        idleUntil { children.isDone }

        val result = children.get()
        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(listOf("s3" to "T3"), result.value.orEmpty().map { it.mediaId to "${it.mediaMetadata.title}" })
        assertEquals(RestoreCoordinator.Outcome.LOADING, coordinator.outcome)
        assertEquals(0, nothingToResumeCount)
        assertEquals(0, player.mediaItemCount)
        assertFalse(restoredOrder.isArmed)
        assertFalse(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
    }

    private fun connectBrowser(callback: PlaybackSessionCallback): MediaBrowser {
        val realSession = MediaLibrarySession.Builder(context, player, callback).build()
        session = realSession
        val browserFuture = MediaBrowser.Builder(context, realSession.token).buildAsync()
        idleUntil { browserFuture.isDone }
        return browserFuture.get().also { controller = it }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
        const val RECENT_ROOT_ID = "androidx.media3.session.recent.root"
    }
}
