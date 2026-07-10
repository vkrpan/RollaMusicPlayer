package com.rolla.musicplayer.feature.widget

import com.rolla.musicplayer.core.media.PlaybackStateHolder
import com.rolla.musicplayer.core.model.Song
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

    private companion object {
        const val POSITION_MS = 45_000L
        const val DURATION_MS = 180_000L

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
