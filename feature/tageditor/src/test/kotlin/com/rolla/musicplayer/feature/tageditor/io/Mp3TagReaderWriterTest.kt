package com.rolla.musicplayer.feature.tageditor.io

import com.rolla.musicplayer.core.testing.audio.AudioFixtures
import com.rolla.musicplayer.feature.tageditor.SongTags
import com.rolla.musicplayer.feature.tageditor.TagField
import kotlinx.coroutines.test.runTest
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the shared [TagReaderWriterContractTest] suite against an MP3 (ID3v2) fixture, plus the ID3v1-only cases that
 * only MP3 has.
 */
class Mp3TagReaderWriterTest : TagReaderWriterContractTest() {

    override fun newFixtureFile(): File =
        File.createTempFile("fixture", ".mp3").apply {
            deleteOnExit()
            writeBytes(AudioFixtures.mp3Bytes())
        }

    private fun newId3v1OnlyFile(): File =
        File.createTempFile("fixture-v1", ".mp3").apply {
            deleteOnExit()
            writeBytes(AudioFixtures.mp3WithId3v1OnlyBytes(AudioFixtures.Id3v1Fields(V1_TITLE, V1_ARTIST, V1_ALBUM)))
        }

    /** An ID3v1.1-only MP3 carrying every field ID3v1 can hold: title, artist, album, year, track and genre. */
    private fun newFullId3v1OnlyFile(): File =
        File.createTempFile("fixture-v1full", ".mp3").apply {
            deleteOnExit()
            writeBytes(
                AudioFixtures.mp3WithId3v1OnlyBytes(
                    AudioFixtures.Id3v1Fields(
                        V1_TITLE,
                        V1_ARTIST,
                        V1_ALBUM,
                        year = V1_YEAR,
                        track = V1_TRACK,
                        genre = V1_GENRE_ROCK_INDEX,
                    ),
                ),
            )
        }

    @Test
    fun read_id3v1Only_showsTheV1Fields() = runTest {
        val file = newFullId3v1OnlyFile()
        val before = file.readBytes()

        val tags = reader.read(file)

        assertEquals(
            "the editor shows the file's ID3v1 text",
            SongTags(
                title = V1_TITLE,
                artist = V1_ARTIST,
                album = V1_ALBUM,
                genre = V1_GENRE_ROCK,
                year = V1_YEAR,
                trackNumber = V1_TRACK.toString(),
            ),
            tags,
        )
        assertArrayEquals("reading never writes the file", before, file.readBytes())
    }

    @Test
    fun writeFields_id3v1Only_keepsUnchangedFieldsAndAppliesTheEdit() = runTest {
        val file = newFullId3v1OnlyFile()

        writer.writeFields(file, mapOf(TagField.TITLE to "Edited Title"))

        val v2 = (AudioFileIO.read(file) as MP3File).iD3v2Tag
        assertEquals("the edit is applied", "Edited Title", v2.getFirst(FieldKey.TITLE))
        assertEquals("the unchanged artist survives in the v2 tag", V1_ARTIST, v2.getFirst(FieldKey.ARTIST))
        assertEquals("the unchanged album survives in the v2 tag", V1_ALBUM, v2.getFirst(FieldKey.ALBUM))
        assertEquals("the unchanged year survives in the v2 tag", V1_YEAR, v2.getFirst(FieldKey.YEAR))
        assertEquals("the unchanged track survives in the v2 tag", V1_TRACK.toString(), v2.getFirst(FieldKey.TRACK))
        assertEquals("the unchanged genre survives in the v2 tag", V1_GENRE_ROCK, v2.getFirst(FieldKey.GENRE))
    }

    @Test
    fun removeArtwork_id3v1OnlyFile_doesNotThrowAndLeavesTheFileUnchanged() = runTest {
        val file = newId3v1OnlyFile()
        // Control: the fixture really is ID3v1-only, with a readable title.
        val mp3 = AudioFileIO.read(file) as MP3File
        assertTrue("control: the fixture has an ID3v1 tag", mp3.hasID3v1Tag())
        assertFalse("control: the fixture has no ID3v2 tag", mp3.hasID3v2Tag())
        assertEquals("control: the v1 title reads back", V1_TITLE, mp3.tag.getFirst(FieldKey.TITLE))
        val before = file.readBytes()

        val result = runCatching { writer.removeArtwork(file) }

        assertTrue("removeArtwork on an ID3v1-only MP3 must not throw, got $result", result.isSuccess)
        assertArrayEquals("an ID3v1-only MP3 has no picture to remove and is not rewritten", before, file.readBytes())
    }

    @Test
    fun writeArtwork_id3v1OnlyFile_keepsTitleArtistAlbumInTheNewId3v2Tag() = runTest {
        val file = newId3v1OnlyFile()

        writer.writeArtwork(file, pickedArtwork(PNG_A))

        // Android reads ID3v1 only when there is no ID3v2 tag, so the new v2 tag must carry the v1 text.
        val v2 = (AudioFileIO.read(file) as MP3File).iD3v2Tag
        assertArrayEquals("the picture is in the ID3v2 tag", PNG_A, v2.firstArtwork?.binaryData)
        assertEquals("the v2 title is the v1 title", V1_TITLE, v2.getFirst(FieldKey.TITLE))
        assertEquals("the v2 artist is the v1 artist", V1_ARTIST, v2.getFirst(FieldKey.ARTIST))
        assertEquals("the v2 album is the v1 album", V1_ALBUM, v2.getFirst(FieldKey.ALBUM))
    }

    private companion object {
        const val V1_TITLE = "V1 Title"
        const val V1_ARTIST = "V1 Artist"
        const val V1_ALBUM = "V1 Album"
        const val V1_YEAR = "1999"
        const val V1_TRACK = 7
        const val V1_GENRE_ROCK_INDEX = 17 // ID3v1 genre table: 17 = Rock
        const val V1_GENRE_ROCK = "Rock"
    }
}
