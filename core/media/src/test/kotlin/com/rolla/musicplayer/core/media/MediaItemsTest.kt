package com.rolla.musicplayer.core.media

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongArtworkRef
import com.rolla.musicplayer.core.model.parseSongArtworkUri
import com.rolla.musicplayer.core.model.songArtworkUri
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

    // Phase 7 ruling P8: the notification and the System UI card read MediaMetadata.artist, so it carries the display
    // name, while the Song the service rebuilds (Now Playing's "Go to artist" key) gets the raw name back from the
    // extra.
    // Trivial stubs: no mapping → "Unknown" display assertion RED; no extra read → raw assertion RED. Control: "Sade"
    // stays "Sade" both ways, so a constant "Unknown" or "<unknown>" also fails.
    // Mutations: (1) toPlaybackSong reads mediaMetadata.artist instead of rawArtist() → raw assertion RED;
    // (2) setArtist(artist) without artistDisplayName → display assertion RED.
    @Test
    fun unknownArtistShowsUnknownInTheMetadataAndRebuildsRaw() {
        val unknown = song.copy(artist = "<unknown>").toMediaItem()
        assertEquals("the metadata shows MediaStore's <unknown> as Unknown", "Unknown", unknown.mediaMetadata.artist)
        assertEquals("the rebuilt Song keeps the raw <unknown>", "<unknown>", unknown.toPlaybackSong().artist)

        val sade = song.copy(artist = "Sade").toMediaItem()
        assertEquals("control: the metadata shows a real artist's name", "Sade", sade.mediaMetadata.artist)
        assertEquals("control: the rebuilt Song keeps a real artist's name", "Sade", sade.toPlaybackSong().artist)
    }

    // The raw-name extra survives asPlayableLeaf (the System UI card's resumption items), and the rebuild keeps the
    // rest of the item. Mutation: toPlaybackSong drops the title → RED.
    @Test
    fun toPlaybackSong_rebuildsTheItemsFields() {
        val rebuilt = song.copy(artist = "<unknown>").toMediaItem().asPlayableLeaf().toPlaybackSong()
        assertEquals("the leaf's rebuilt Song keeps the raw <unknown>", "<unknown>", rebuilt.artist)
        assertEquals("the rebuilt Song keeps the id", "42", rebuilt.id)
        assertEquals("the rebuilt Song keeps the title", "Title", rebuilt.title)
        assertEquals("the rebuilt Song keeps the album", "Album", rebuilt.album)
        assertEquals(
            "the rebuilt Song keeps the content uri",
            "content://media/external/audio/media/42",
            rebuilt.contentUri,
        )
        assertEquals(
            "the rebuilt Song keeps the artwork uri",
            "content://media/external/audio/albumart/7",
            rebuilt.artworkUri,
        )
    }

    // Per-track artwork (Task 1 consumer audit): the rolla-artwork value survives android.net.Uri and the rebuild
    // unchanged, so it still parses to the same song and version. Trivial stub (toPlaybackSong drops the artwork) →
    // RED. Mutation: toPlaybackSong reads the uri's path only → "/9545" → RED.
    @Test
    fun perTrackArtworkValue_passesThroughUnchanged() {
        val value = songArtworkUri("9545", 1_794_133_952L)
        val item = song.copy(artworkUri = value).toMediaItem()
        assertEquals("the media item carries the value as is", value, item.mediaMetadata.artworkUri.toString())
        val rebuilt = item.asPlayableLeaf().toPlaybackSong()
        assertEquals("the rebuilt Song keeps the value as is", value, rebuilt.artworkUri)
        assertEquals(
            "the rebuilt value parses to the same song and version",
            SongArtworkRef(9545L, 1_794_133_952L),
            parseSongArtworkUri(rebuilt.artworkUri),
        )
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
