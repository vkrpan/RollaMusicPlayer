package com.rolla.musicplayer.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaybackTrackerTest {

    private lateinit var tracker: PlaybackTracker

    @Before
    fun setUp() {
        tracker = PlaybackTracker()
    }

    @Test
    fun `first isPlaying true after transition records once`() {
        tracker.onMediaItemTransition()

        val songId = tracker.onIsPlayingChanged(isPlaying = true, mediaId = "song-1")

        assertEquals("song-1", songId)
    }

    @Test
    fun `subsequent isPlaying true after pause on same item does not re-record`() {
        tracker.onMediaItemTransition()
        tracker.onIsPlayingChanged(isPlaying = true, mediaId = "song-1")

        tracker.onIsPlayingChanged(isPlaying = false, mediaId = "song-1")
        val songId = tracker.onIsPlayingChanged(isPlaying = true, mediaId = "song-1")

        assertNull(songId)
    }

    @Test
    fun `new transition followed by isPlaying true records again`() {
        tracker.onMediaItemTransition()
        tracker.onIsPlayingChanged(isPlaying = true, mediaId = "song-1")

        tracker.onMediaItemTransition()
        val songId = tracker.onIsPlayingChanged(isPlaying = true, mediaId = "song-1")

        assertEquals("song-1", songId)
    }

    @Test
    fun `isPlaying true with null mediaId records nothing`() {
        tracker.onMediaItemTransition()

        val songId = tracker.onIsPlayingChanged(isPlaying = true, mediaId = null)

        assertNull(songId)
    }
}
