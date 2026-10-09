package com.rolla.musicplayer.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test

private fun testSong(id: String = "song-1") = Song(
    id = id,
    title = "Title",
    artist = "Artist",
    album = "Album",
    albumId = 0L,
    durationMs = 0L,
    trackNumber = null,
    year = null,
    contentUri = "content://media/$id",
    artworkUri = "",
)

/**
 * Exercises [PlaybackController]'s command queuing directly against a mocked
 * `ListenableFuture<MediaController>` installed through [PlaybackController.controllerFuture] (the
 * [androidx.annotation.VisibleForTesting] seam) -- [PlaybackController.connect] itself builds a real
 * controller via [androidx.media3.session.SessionToken], which needs a live Android session/binder
 * and can't run in a plain JVM unit test.
 */
class PlaybackControllerTest {

    private val context: Context = mockk(relaxed = true)
    private val playbackStateHolder = PlaybackStateHolder()
    private val controller = PlaybackController(context, playbackStateHolder)

    private val future: ListenableFuture<MediaController> = mockk(relaxed = true)
    private val mediaController: MediaController = mockk(relaxed = true)
    private val queuedRunnable = slot<Runnable>()

    @Before
    fun setUp() {
        every { future.isDone } returns false
        every { future.isCancelled } returns false
        every { future.addListener(capture(queuedRunnable), any()) } just Runs
        controller.controllerFuture = future
    }

    /** Simulates the future resolving to [mediaController], as connect()'s real future would. */
    private fun resolveFuture() {
        every { future.isDone } returns true
        every { future.get() } returns mediaController
    }

    @Test
    fun `togglePlayPause before the future resolves is queued and runs exactly once after resolution`() {
        controller.togglePlayPause()
        verify(exactly = 0) { mediaController.play() }

        resolveFuture()
        queuedRunnable.captured.run()

        verify(exactly = 1) { mediaController.play() }
    }

    @Test
    fun `next before the future resolves is queued and runs exactly once after resolution`() {
        controller.next()

        resolveFuture()
        queuedRunnable.captured.run()

        verify(exactly = 1) { mediaController.seekToNext() }
    }

    @Test
    fun `previous before the future resolves is queued and runs exactly once after resolution`() {
        controller.previous()

        resolveFuture()
        queuedRunnable.captured.run()

        verify(exactly = 1) { mediaController.seekToPrevious() }
    }

    @Test
    fun `seekTo before the future resolves is queued and runs exactly once after resolution`() {
        controller.seekTo(POSITION_MS)

        resolveFuture()
        queuedRunnable.captured.run()

        verify(exactly = 1) { mediaController.seekTo(POSITION_MS) }
    }

    @Test
    fun `play before the future resolves is still queued and runs exactly once after resolution`() {
        // play() builds a MediaItem, which calls the JVM-unmocked Uri.parse -- static-mock it
        // for exactly this test (the other queued commands never construct MediaItems).
        mockkStatic(Uri::class)
        try {
            every { Uri.parse(any()) } returns mockk(relaxed = true)

            controller.play(testSong())

            resolveFuture()
            queuedRunnable.captured.run()

            verify(exactly = 1) { mediaController.setMediaItem(any()) }
            verify(exactly = 1) { mediaController.prepare() }
            verify(exactly = 1) { mediaController.play() }
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    @Test
    fun `setShuffle then playAll before the future resolves apply shuffle first, then start playback`() {
        // playAll builds MediaItems, which call the JVM-unmocked Uri.parse (see the play test above).
        mockkStatic(Uri::class)
        try {
            every { Uri.parse(any()) } returns mockk(relaxed = true)
            // Keep every queued listener in order; the class-level slot only holds the last one.
            val queued = mutableListOf<Runnable>()
            every { future.addListener(capture(queued), any()) } just Runs

            controller.setShuffle(ShuffleMode.ON)
            controller.playAll(listOf(testSong("a"), testSong("b"), testSong("c")), startIndex = 1)
            verify(exactly = 0) { mediaController.play() }

            resolveFuture()
            queued.forEach { it.run() }

            verifyOrder {
                mediaController.shuffleModeEnabled = true
                mediaController.setMediaItems(any(), 1, any())
                mediaController.prepare()
                mediaController.play()
            }
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    @Test
    fun `togglePlayPause when already connected runs immediately without queuing`() {
        resolveFuture()

        controller.togglePlayPause()

        verify(exactly = 1) { mediaController.play() }
        verify(exactly = 0) { future.addListener(any(), any()) }
    }

    @Test
    fun `setShuffle when already connected applies immediately without queuing`() {
        resolveFuture()

        controller.setShuffle(ShuffleMode.ON)

        verify(exactly = 1) { mediaController.shuffleModeEnabled = true }
        verify(exactly = 0) { future.addListener(any(), any()) }
    }

    @Test
    fun `togglePlayPause pauses when the connected controller is already playing`() {
        resolveFuture()
        every { mediaController.isPlaying } returns true

        controller.togglePlayPause()

        verify(exactly = 1) { mediaController.pause() }
        verify(exactly = 0) { mediaController.play() }
    }

    private companion object {
        private const val POSITION_MS = 30_000L
    }
}
