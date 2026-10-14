package com.rolla.musicplayer.core.media

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaItemsTest {

    private val song = Song(
        id = "42",
        title = "Title",
        artist = "Artist",
        album = "Album",
        albumId = 7L,
        durationMs = 1_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/42",
        artworkUri = "content://media/external/audio/albumart/7",
    )

    // Mutations: drop setArtworkUri → null artwork → RED; setMediaId(title) → RED.
    @Test
    fun toMediaItem_carriesTheIdUriAndMetadata() {
        val item = song.toMediaItem()

        assertEquals("42", item.mediaId)
        assertEquals("content://media/external/audio/media/42", item.localConfiguration?.uri.toString())
        assertEquals("Title", item.mediaMetadata.title)
        assertEquals("Artist", item.mediaMetadata.artist)
        assertEquals("Album", item.mediaMetadata.albumTitle)
        assertEquals("content://media/external/audio/albumart/7", item.mediaMetadata.artworkUri.toString())
    }

    // Mutation: drop `.ifEmpty { null }` → an empty Uri instead of null → RED.
    @Test
    fun toMediaItem_withoutArtwork_hasNoArtworkUri() {
        assertNull(song.copy(artworkUri = "").toMediaItem().mediaMetadata.artworkUri)
    }

    // Mutation: swap ONE and ALL in either mapping → RED.
    @Test
    fun repeatModesRoundTrip() {
        for (mode in RepeatMode.entries) {
            assertEquals(mode, mode.toPlayerRepeatMode().toRepeatMode())
        }
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.ALL.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.ONE.toPlayerRepeatMode())
    }
}
