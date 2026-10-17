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

    // Media3's LibraryResult.ofItemList rejects an item without both flags (panel-resume final review). Control: the
    // song's own item has neither flag, so the leaf's flags come from asPlayableLeaf. Mutation: drop
    // setIsPlayable(true) → isPlayable null → RED.
    @Test
    fun asPlayableLeaf_keepsTheItemAndFlagsItAPlayableLeaf() {
        val item = song.toMediaItem()
        val leaf = item.asPlayableLeaf()

        assertNull(item.mediaMetadata.isBrowsable)
        assertNull(item.mediaMetadata.isPlayable)
        assertEquals("42", leaf.mediaId)
        assertEquals("content://media/external/audio/media/42", leaf.localConfiguration?.uri.toString())
        assertEquals("Title", leaf.mediaMetadata.title)
        assertEquals("Artist", leaf.mediaMetadata.artist)
        assertEquals("Album", leaf.mediaMetadata.albumTitle)
        assertEquals("content://media/external/audio/albumart/7", leaf.mediaMetadata.artworkUri.toString())
        assertEquals(false, leaf.mediaMetadata.isBrowsable)
        assertEquals(true, leaf.mediaMetadata.isPlayable)
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
