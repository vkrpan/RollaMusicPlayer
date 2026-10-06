package com.rolla.musicplayer.feature.widget

import app.cash.turbine.test
import com.rolla.musicplayer.core.media.PlaybackStateHolder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WidgetStateProviderTest {

    private lateinit var playbackStateHolder: PlaybackStateHolder
    private lateinit var provider: WidgetStateProvider

    @Before
    fun setUp() {
        playbackStateHolder = PlaybackStateHolder()
        provider = WidgetStateProviderImpl(playbackStateHolder)
    }

    @Test
    fun `current maps a populated holder to widget state`() {
        playbackStateHolder.setCurrentSong(testSong())
        playbackStateHolder.setIsPlaying(true)
        playbackStateHolder.setPositionMs(POSITION_MS)
        playbackStateHolder.setDurationMs(DURATION_MS)

        val state = provider.current()

        assertEquals("Test Title", state.title)
        assertEquals("Test Artist", state.artist)
        assertEquals(true, state.isPlaying)
        assertEquals(POSITION_MS, state.positionMs)
        assertEquals(DURATION_MS, state.durationMs)
        assertEquals("content://media/local/art/1", state.artworkPath)
    }

    @Test
    fun `current returns defaults when no song is playing`() {
        val state = provider.current()

        assertEquals("", state.title)
        assertEquals("", state.artist)
        assertEquals(false, state.isPlaying)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertNull(state.artworkPath)
    }

    @Test
    fun `current maps blank artwork uri to a null artwork path`() {
        playbackStateHolder.setCurrentSong(testSong(artworkUri = ""))

        val state = provider.current()

        assertNull(state.artworkPath)
    }

    @Test
    fun `current reads durationMs from the holder's live flow, not the song's stale field`() {
        // Song.durationMs is deliberately left at a value the holder's own StateFlow disagrees
        // with (e.g. the service hasn't yet pushed the just-prepared player's real duration into
        // the holder, or the MediaItem transition callback -- which always builds Song with
        // durationMs = 0L, see PlaybackService -- fired before the ready-state duration write).
        // WidgetStateProviderImpl must snapshot playbackStateHolder.durationMs, never song.durationMs.
        playbackStateHolder.setCurrentSong(testSong().copy(durationMs = STALE_SONG_DURATION_MS))
        playbackStateHolder.setDurationMs(DURATION_MS)

        val state = provider.current()

        assertEquals(DURATION_MS, state.durationMs)
    }

    @Test
    fun `states emits a fresh snapshot whenever the holder's position or play state changes`() = runTest {
        playbackStateHolder.setCurrentSong(testSong())
        playbackStateHolder.setDurationMs(DURATION_MS)

        provider.states.test {
            assertEquals(0L, awaitItem().positionMs)

            playbackStateHolder.setPositionMs(POSITION_MS)
            assertEquals(POSITION_MS, awaitItem().positionMs)

            playbackStateHolder.setIsPlaying(true)
            assertEquals(true, awaitItem().isPlaying)
        }
    }

    @Test
    fun `states maps exactly like current`() = runTest {
        playbackStateHolder.setCurrentSong(testSong())
        playbackStateHolder.setIsPlaying(true)
        playbackStateHolder.setPositionMs(POSITION_MS)
        playbackStateHolder.setDurationMs(DURATION_MS)

        assertEquals(provider.current(), provider.states.first())
    }

    private companion object {
        const val POSITION_MS = 45_000L
        const val DURATION_MS = 180_000L
        const val STALE_SONG_DURATION_MS = 0L

        fun testSong(artworkUri: String = "content://media/local/art/1"): Song = Song(
            id = "song-1",
            title = "Test Title",
            artist = "Test Artist",
            album = "Test Album",
            albumId = 1L,
            durationMs = DURATION_MS,
            trackNumber = 1,
            year = 2024,
            contentUri = "content://media/external/audio/media/1",
            artworkUri = artworkUri,
        )
    }
}
