package com.rolla.musicplayer.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongArtworkRef
import com.rolla.musicplayer.core.model.songArtworkUri
import com.rolla.musicplayer.core.testing.FakePlaybackStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * The notification, lock screen and media card read artwork through the session's bitmap loader, and the resume card
 * through the recent root's start item. These tests build a REAL [MediaLibrarySession] with [buildPlaybackSession], the
 * assembly `PlaybackService` itself calls, and load through `session.bitmapLoader` (the chain Media3 really runs: its
 * `CacheBitmapLoader` and `SizeLimitedBitmapLoader` around [SongArtworkBitmapLoader]) or a real [MediaBrowser].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackSessionArtworkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakePlaybackStateRepository()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var player: ExoPlayer
    private lateinit var restoredOrder: RestoredShuffleOrder
    private var session: MediaLibrarySession? = null
    private var browser: MediaBrowser? = null

    @Before
    fun setUp() {
        player = playbackPlayerBuilder(context).build()
        // Production order (PlaybackService): the enforcer first, then RestoredShuffleOrder.
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        restoredOrder = RestoredShuffleOrder(player) { 99L }
        player.addListener(restoredOrder)
    }

    @After
    fun tearDown() {
        browser?.release()
        session?.release()
        player.release()
        serviceScope.cancel()
    }

    // PlaybackService's own assembly (buildPlaybackSession): one loader serves the session's bitmaps and the
    // callback's start-item artworkData.
    private fun buildSession(cache: FakeSongArtworkCache): MediaLibrarySession {
        val coordinator = RestoreCoordinator(repository, serviceScope, Dispatchers.Main.immediate) {}
        return buildPlaybackSession(context, player, coordinator, restoredOrder, {}, cache, serviceScope)
            .also { session = it }
    }

    // Runs the main looper (Media3's application thread) until [condition] holds, giving the Default threads time.
    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting on the main looper" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MS)
        }
    }

    private fun song(i: Int) = Song(
        id = "$i",
        title = "T$i",
        artist = "A",
        album = "B",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/$i",
        artworkUri = songArtworkUri("$i", 5L),
    )

    /** The recent root's one child (the saved current track, song 2), read by a real browser through the session. */
    private fun recentRootChild(cache: FakeSongArtworkCache): MediaItem {
        repository.saved = SavedPlayback(
            songs = (0 until 3).map(::song),
            shuffleOrder = listOf(0, 1, 2),
            state = SavedPlaybackState(currentIndex = 2, positionMs = 0L, shuffleEnabled = false, RepeatMode.OFF),
        )
        val realSession = buildSession(cache)
        val browserFuture = MediaBrowser.Builder(context, realSession.token).buildAsync()
        idleUntil { browserFuture.isDone }
        val connected = browserFuture.get().also { browser = it }
        val children = connected.getChildren(RECENT_ROOT_ID, 0, 1, null)
        idleUntil { children.isDone }
        val items = children.get().value.orEmpty()
        assertEquals("one child, the saved current track", listOf("2"), items.map { it.mediaId })
        return items.single()
    }

    // Ruling P14 (user decision) through the real session and the service's own wiring: the saved current track HAS a
    // picture, yet the resume card's item carries the placeholder PNG and the cache is never asked, so a user's picture
    // never leaves the app through the recent root. Trivial stub (no attach) → RED at "artworkData present".
    // Mutations: restore the cache-bytes branch in buildPlaybackSession's provider → RED at the decode's width (the
    // 4 px red file); pass `{ null }` as that provider, or give the metadata branch `startArtwork = null` → RED at
    // "artworkData present".
    @Test
    fun recentRootChild_trackWithPicture_stillCarriesThePlaceholderPng() {
        val art = temp.newFile("art.png").also { writePng(it, Color.RED) }
        val cache = FakeSongArtworkCache { ref -> art.takeIf { ref == SongArtworkRef(2L, 5L) } }

        val item = recentRootChild(cache)

        assertPlaceholderPng(item.mediaMetadata.artworkData)
        assertTrue("the cache is never asked on a metadata request", cache.getCalls.isEmpty())
    }

    // Ruling P12: no picture → PNG bytes that decode to the placeholder (512 px, background and note pixels). Trivial
    // stub → RED at "artworkData present". Mutation: answer the resume card with ByteArray(0) → RED at the decode.
    @Test
    fun recentRootChild_noPicture_carriesThePlaceholderPng() {
        val item = recentRootChild(FakeSongArtworkCache { null })

        assertPlaceholderPng(item.mediaMetadata.artworkData)
    }

    private fun assertPlaceholderPng(bytes: ByteArray?) {
        assertNotNull("artworkData present", bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size)
        assertNotNull("the bytes decode", bitmap)
        assertEquals("placeholder width", 512, bitmap!!.width)
        assertEquals("pixel (2,2) is artworkPlaceholderLarge", 0xFF3B3B3B.toInt(), bitmap.getPixel(2, 2))
        assertEquals("the centre pixel is the note", 0xFFFCFCFE.toInt(), bitmap.getPixel(256, 256))
    }

    // Companion §3 framework check: PlaybackService's builder installs the loader. A track without a picture comes
    // back as the dark placeholder with the white note (Media3's SizeLimitedBitmapLoader scales anything above
    // MediaSession.getBitmapDimensionLimit, so the side is the smaller of the two). Trivial stub (a 1×1 blank bitmap)
    // → RED at the width. Mutation: drop `.setBitmapLoader(...)` from playbackSessionBuilder → Media3's default
    // DataSourceBitmapLoader cannot open the rolla-artwork scheme → the future fails → RED at assertNotNull.
    @Test
    fun playbackSession_noPicture_loadsThePlaceholderThroughTheSongArtworkLoader() {
        val cache = FakeSongArtworkCache { null }
        val realSession = buildSession(cache)

        val bitmap = awaitBitmap(realSession.bitmapLoader.loadBitmap(songArtworkUri("1", 1L).toUri()))

        assertNotNull("the session's loader must answer a per-track value with a bitmap", bitmap)
        val side = minOf(512, MediaSession.getBitmapDimensionLimit(context))
        assertEquals("the placeholder's width, within Media3's size limit", side, bitmap!!.width)
        assertEquals("the placeholder's height, within Media3's size limit", side, bitmap.height)
        assertEquals("pixel (2,2) is artworkPlaceholderLarge", 0xFF3B3B3B.toInt(), bitmap.getPixel(2, 2))
        assertEquals(
            "the centre pixel is the note in artworkPlaceholderGlyph",
            0xFFFCFCFE.toInt(),
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2),
        )
        assertEquals("the song cache is asked for the ref", listOf(SongArtworkRef(1L, 1L)), cache.getCalls)
    }

    // Review Focus 5: the playing track's picture changes (a re-synced Song carries v=2). Media3's CacheBitmapLoader
    // keys on the uri, so the new version must reach the loader and decode the file again; the cache hands back the
    // same path both times, rewritten in between. Trivial stub (a 1×1 blank bitmap) → RED at v=1's width. Mutation:
    // memoize the decoded bitmap per song id in SongArtworkBitmapLoader → v=2 answers v=1's red → RED at the second.
    @Test
    fun updateSongMetadata_newVersion_loadsTheNewBitmap() {
        val art = temp.newFile("art.png")
        val cache = FakeSongArtworkCache { ref -> art.takeIf { ref.songId == 1L } }
        val realSession = buildSession(cache)

        writePng(art, Color.RED)
        val first = awaitBitmap(realSession.bitmapLoader.loadBitmap(songArtworkUri("1", 1L).toUri()))
        writePng(art, Color.BLUE)
        val second = awaitBitmap(realSession.bitmapLoader.loadBitmap(songArtworkUri("1", 2L).toUri()))

        assertNotNull("v=1 loads", first)
        assertNotNull("v=2 loads", second)
        assertEquals("v=1 is the file's 4 px picture", 4, first!!.width)
        assertEquals("v=2 is the file's 4 px picture", 4, second!!.width)
        assertEquals("v=1 shows the file's first picture", Color.RED, first.getPixel(2, 2))
        assertEquals("v=2 shows the rewritten picture", Color.BLUE, second.getPixel(2, 2))
    }

    private fun writePng(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
        const val RECENT_ROOT_ID = "androidx.media3.session.recent.root"
    }
}
