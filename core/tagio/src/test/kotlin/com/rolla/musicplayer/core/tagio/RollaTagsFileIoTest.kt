package com.rolla.musicplayer.core.tagio

import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.testing.audio.AudioFixtures
import com.rolla.musicplayer.core.testing.audio.M4aFixture
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v22Frames
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import org.jcodec.containers.mp4.MP4Util
import org.jcodec.containers.mp4.boxes.NodeBox
import org.jcodec.containers.mp4.boxes.UdtaBox
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The `ROLLA_TAGS` field's file contract: every case goes through real jaudiotagger reads and commits on fixture files,
 * because the field is user-visible and other programs must read it as written here.
 */
class RollaTagsFileIoTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val io = RollaTagsFileIo()
    private val ids = CustomTagIds(setOf(2, 3), setOf(1, 3, 4))
    private val expected = """{"tags":"2,3","subTags":"1,3,4"}"""

    @Before
    fun silenceJaudiotaggerLogging() {
        // Quiets jaudiotagger's java.util.logging output for this test JVM only (see TagReaderWriterContractTest),
        // through the handlers alone: the "org.jaudiotagger" logger's own level is what jaudiotaggerLogging_isWarning
        // checks, so it is left as RollaTagsFileIo set it.
        var logger: Logger? = Logger.getLogger(JAUDIOTAGGER_LOGGER)
        while (logger != null) {
            logger.handlers.forEach { it.level = Level.OFF }
            logger = logger.parent
        }
    }

    // ── logging ───────────────────────────────────────────────────────────

    // `io` above constructed a RollaTagsFileIo, which quiets jaudiotagger's per-file INFO logging in production.
    @Test
    fun jaudiotaggerLogging_isWarning() =
        assertEquals(Level.WARNING, Logger.getLogger(JAUDIOTAGGER_LOGGER).level)

    // ── MP3 ───────────────────────────────────────────────────────────────

    @Test
    fun mp3_untagged_roundTrips() {
        val file = mp3File()

        io.writeCustomTagIds(file, ids)

        assertEquals(ids, io.read(file).customTagIds)
    }

    @Test
    fun mp3_id3v1Only_roundTripsAndKeepsV1Text() {
        val file = newFile(
            "v1.mp3",
            AudioFixtures.mp3WithId3v1OnlyBytes(AudioFixtures.Id3v1Fields(V1_TITLE, V1_ARTIST, V1_ALBUM)),
        )

        io.writeCustomTagIds(file, ids)

        assertEquals(ids, io.read(file).customTagIds)
        val tag = AudioFileIO.read(file).tag
        assertEquals("the v1 title survives", V1_TITLE, tag.getFirst(FieldKey.TITLE))
        assertEquals("the v1 artist survives", V1_ARTIST, tag.getFirst(FieldKey.ARTIST))
        assertEquals("the v1 album survives", V1_ALBUM, tag.getFirst(FieldKey.ALBUM))
    }

    @Test
    fun mp3_id3v24_roundTrips() {
        val file = mp3File()
        commitId3v2Tag(file, ID3v24Tag().apply { setField(FieldKey.TITLE, "v2.4 title") })

        io.writeCustomTagIds(file, ids)

        assertEquals(ids, io.read(file).customTagIds)
        val v2 = (AudioFileIO.read(file) as MP3File).iD3v2Tag
        assertTrue("the file keeps its ID3v2.4 tag, got ${v2.javaClass.simpleName}", v2 is ID3v24Tag)
        assertEquals("the field is a v2.4 TXXX frame", listOf(expected), rollaTxxxTexts(file))
    }

    @Test
    fun mp3_id3v22_roundTrips() {
        val file = mp3File()
        commitId3v2Tag(file, ID3v22Tag().apply { setField(FieldKey.TITLE, "v2.2 title") })

        io.writeCustomTagIds(file, ids)

        assertEquals(ids, io.read(file).customTagIds)
        val v2 = (AudioFileIO.read(file) as MP3File).iD3v2Tag
        assertTrue("the file keeps its ID3v2.2 tag, got ${v2.javaClass.simpleName}", v2 is ID3v22Tag)
        assertEquals("the field is a v2.2 TXX frame", listOf(expected), rollaTxxxTexts(file))
    }

    @Test
    fun mp3_writesTxxxWithExactValue() {
        val file = mp3File()

        io.writeCustomTagIds(file, ids)

        assertEquals(listOf(expected), rollaTxxxTexts(file))
    }

    @Test
    fun mp3_keepsCommentOtherTxxxAndArtwork() {
        val file = mp3File()
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault as AbstractID3v2Tag
        tag.setField(FieldKey.COMMENT, "my notes")
        tag.setFrame(txxxFrame(tag, "MIK_ENERGY", "6"))
        tag.setField(pngArtwork())
        audioFile.commit()

        io.writeCustomTagIds(file, ids)

        val after = (AudioFileIO.read(file) as MP3File).iD3v2Tag
        assertEquals("the comment is unchanged", "my notes", after.getFirst(FieldKey.COMMENT))
        assertEquals("the other TXXX frame is unchanged", listOf("6"), txxxTexts(after, "MIK_ENERGY"))
        assertArrayEquals("the picture is unchanged", PNG, after.firstArtwork?.binaryData)
        assertEquals("the field is written", listOf(expected), txxxTexts(after, RollaTagsJson.FIELD_NAME))
    }

    @Test
    fun mp3_secondWriteReplacesNotDuplicates() {
        val file = mp3File()

        io.writeCustomTagIds(file, ids)
        io.writeCustomTagIds(file, CustomTagIds(setOf(9), emptySet()))

        assertEquals(listOf("""{"tags":"9","subTags":""}"""), rollaTxxxTexts(file))
    }

    @Test
    fun mp3_writeEmptyRemovesField() {
        val file = mp3File()

        io.writeCustomTagIds(file, ids)
        io.writeCustomTagIds(file, CustomTagIds.EMPTY)

        assertEquals("no ROLLA_TAGS TXXX frame remains", emptyList<String>(), rollaTxxxTexts(file))
        assertEquals(CustomTagIds.EMPTY, io.read(file).customTagIds)
    }

    // ── FLAC ──────────────────────────────────────────────────────────────

    @Test
    fun flac_roundTripsAndKeepsComment() {
        val file = newFile("song.flac", AudioFixtures.flacBytes())
        seed(file) { setField(FieldKey.COMMENT, "my notes") }

        io.writeCustomTagIds(file, ids)

        assertEquals(ids, io.read(file).customTagIds)
        val comments = (AudioFileIO.read(file).tag as FlacTag).vorbisCommentTag
        assertEquals("the Vorbis comment holds the value", expected, comments.getFirst(RollaTagsJson.FIELD_NAME))
        assertEquals("the comment is unchanged", "my notes", comments.getFirst(FieldKey.COMMENT))
    }

    @Test
    fun flac_writeEmptyRemovesField() {
        val file = newFile("song.flac", AudioFixtures.flacBytes())

        io.writeCustomTagIds(file, ids)
        io.writeCustomTagIds(file, CustomTagIds.EMPTY)

        val comments = (AudioFileIO.read(file).tag as FlacTag).vorbisCommentTag
        assertFalse("no ROLLA_TAGS comment remains", comments.hasField(RollaTagsJson.FIELD_NAME))
        assertEquals(CustomTagIds.EMPTY, io.read(file).customTagIds)
    }

    // ── M4A (read only in phase 1) ────────────────────────────────────────

    @Test
    fun m4a_readsRollaTagsAtomWrittenByAnotherProgram() {
        val file = m4aFile(M4aFixture.FreeformAtom(ITUNES_MEAN, RollaTagsJson.FIELD_NAME, expected))

        assertEquals(ids, io.read(file).customTagIds)
    }

    @Test
    fun m4a_writeIsRefusedAndFileUnchanged() {
        val file = m4aFile()
        val before = file.readBytes()

        assertThrows(UnsupportedOperationException::class.java) { io.writeCustomTagIds(file, ids) }

        assertArrayEquals("the refused write leaves the file as it was", before, file.readBytes())
    }

    // Only the M4A refusal names the Mp4TagCreator reason; any other format gets the generic message.
    @Test
    fun write_refusalMessage_namesMp4ReasonOnlyForM4a() {
        val m4a = assertThrows(UnsupportedOperationException::class.java) { io.writeCustomTagIds(m4aFile(), ids) }
        val wav = assertThrows(UnsupportedOperationException::class.java) {
            io.writeCustomTagIds(newFile("song.wav", ByteArray(0)), ids)
        }

        assertTrue("the M4A message names the reason: ${m4a.message}", m4a.message.orEmpty().contains("Mp4TagCreator"))
        assertFalse("the WAV message does not: ${wav.message}", wav.message.orEmpty().contains("Mp4TagCreator"))
        assertTrue("the WAV message says unsupported: ${wav.message}", wav.message.orEmpty().contains("unsupported"))
    }

    // Characterises the jaudiotagger fork, not RollaTagsFileIo (the tag editor's saves take this path). Its
    // Mp4TagReader reads only the freeform atoms Mp4FieldKey lists, and a commit rebuilds the ilst from what was read,
    // so a plain title edit succeeds and silently drops every unlisted freeform atom, ROLLA_TAGS included.
    @Test
    fun m4a_unknownFreeformAtom_isDroppedByCommit() {
        val file = newFile(
            "song.m4a",
            M4aFixture.bytes(
                listOf(
                    M4aFixture.FreeformAtom(ITUNES_MEAN, "MIK_KEY", "8A"),
                    M4aFixture.FreeformAtom(ITUNES_MEAN, RollaTagsJson.FIELD_NAME, expected),
                ),
                title = "Original",
            ),
        )
        assertEquals("control: the atoms are in the file", setOf(MIK_KEY, ROLLA_KEY), freeformKeys(file))
        assertEquals("control: the title is read", "Original", AudioFileIO.read(file).tag.getFirst(FieldKey.TITLE))
        TagOptionSingleton.getInstance().isPreserveFileIdentity = true
        val audioFile = AudioFileIO.read(file)
        audioFile.editableTag().setField(FieldKey.TITLE, "Edited")

        audioFile.commit()

        assertEquals("the edit is written", "Edited", AudioFileIO.read(file).tag.getFirst(FieldKey.TITLE))
        assertEquals("every unlisted freeform atom is gone", emptySet<String>(), freeformKeys(file))
        assertEquals("so the custom tags are lost", CustomTagIds.EMPTY, io.read(file).customTagIds)
    }

    // ── read ──────────────────────────────────────────────────────────────

    @Test
    fun read_undecodableValue_returnsEmpty() {
        val file = mp3File()
        seed(file) { (this as AbstractID3v2Tag).setFrame(txxxFrame(this, RollaTagsJson.FIELD_NAME, "Techno")) }
        assertEquals("control: the seeded value is on disk", listOf("Techno"), rollaTxxxTexts(file))
        val before = file.readBytes()

        val tags = io.read(file)

        assertEquals(CustomTagIds.EMPTY, tags.customTagIds)
        assertArrayEquals("reading never writes the file", before, file.readBytes())
    }

    @Test
    fun read_genre_fromV2AndFromV1Only() {
        val v2File = mp3File()
        seed(v2File) { setField(FieldKey.GENRE, "Techno") }
        val v1File = newFile(
            "v1.mp3",
            AudioFixtures.mp3WithId3v1OnlyBytes(
                AudioFixtures.Id3v1Fields(V1_TITLE, V1_ARTIST, V1_ALBUM, genre = V1_GENRE_ROCK_INDEX),
            ),
        )

        assertEquals("Techno", io.read(v2File).genre)
        assertEquals("Rock", io.read(v1File).genre)
    }

    @Test
    fun read_blankGenre_isNull() = assertNull(io.read(mp3File()).genre)

    // ── format support ────────────────────────────────────────────────────

    @Test
    fun supportsCustomTags_byExtension() {
        assertTrue(File("a.MP3").supportsCustomTags())
        assertTrue(File("b.flac").supportsCustomTags())
        assertTrue(File("c.M4a").supportsCustomTags())
        assertFalse(File("d.wav").supportsCustomTags())
        assertFalse(File("e.ogg").supportsCustomTags())
        assertFalse(File("f.aac").supportsCustomTags())
        assertFalse(File("noExtension").supportsCustomTags())
    }

    @Test
    fun canWriteCustomTags_mp3AndFlacOnly() {
        assertTrue(canWriteCustomTags("MP3"))
        assertTrue(canWriteCustomTags("FLAC"))
        assertFalse(canWriteCustomTags("m4a"))
        assertFalse(canWriteCustomTags("wav"))
        assertFalse(canWriteCustomTags(""))
    }

    @Test
    fun isCustomTagExtension_caseInsensitive() {
        assertTrue(isCustomTagExtension("MP3"))
        assertTrue(isCustomTagExtension("flac"))
        assertTrue(isCustomTagExtension("M4a"))
        assertFalse(isCustomTagExtension("wav"))
        assertFalse(isCustomTagExtension("ogg"))
        assertFalse(isCustomTagExtension("aac"))
        assertFalse(isCustomTagExtension(""))
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun newFile(name: String, bytes: ByteArray): File = folder.newFile(name).apply { writeBytes(bytes) }

    private fun mp3File(): File = newFile("song.mp3", AudioFixtures.mp3Bytes())

    private fun m4aFile(vararg atoms: M4aFixture.FreeformAtom): File =
        newFile("song.m4a", M4aFixture.bytes(atoms.toList()))

    /** The `----:mean:name` keys of [file]'s freeform atoms, read with the jcodec parser jaudiotagger bundles. */
    private fun freeformKeys(file: File): Set<String> = RandomAccessFile(file, "r").use { raf ->
        val moov = MP4Util.parseFullMovieChannel(raf.channel).moov
        NodeBox.findFirst(moov, UdtaBox::class.java, UdtaBox.fourcc())?.meta()?.rdnsMeta?.keys.orEmpty()
    }

    /** Edits [file]'s tag (created when absent) with [edit] and commits in place. */
    private fun seed(file: File, edit: Tag.() -> Unit) {
        TagOptionSingleton.getInstance().isPreserveFileIdentity = true
        val audioFile = AudioFileIO.read(file)
        audioFile.tagOrCreateAndSetDefault.edit()
        audioFile.commit()
    }

    private fun commitId3v2Tag(file: File, tag: AbstractID3v2Tag) {
        TagOptionSingleton.getInstance().isPreserveFileIdentity = true
        val mp3 = AudioFileIO.read(file) as MP3File
        mp3.setID3v2Tag(tag)
        mp3.commit()
    }

    private fun txxxFrame(tag: Tag, description: String, text: String): AbstractID3v2Frame =
        (tag as AbstractID3v2Tag).createFrame(tag.txxxId()).apply {
            body = FrameBodyTXXX(TextEncoding.ISO_8859_1, description, text)
        }

    /** The text of every `ROLLA_TAGS` user-defined text frame in [file]'s ID3v2 tag, as on disk. */
    private fun rollaTxxxTexts(file: File): List<String> =
        txxxTexts((AudioFileIO.read(file) as MP3File).iD3v2Tag, RollaTagsJson.FIELD_NAME)

    private fun txxxTexts(tag: AbstractID3v2Tag, description: String): List<String> =
        tag.getFields(tag.txxxId())
            .map { (it as AbstractID3v2Frame).body as FrameBodyTXXX }
            .filter { it.description == description }
            .map { it.text }

    private fun Tag.txxxId(): String =
        if (this is ID3v22Tag) ID3v22Frames.FRAME_ID_V2_USER_DEFINED_INFO else ID3v24Frames.FRAME_ID_USER_DEFINED_INFO

    private fun pngArtwork() = ArtworkFactory.getNew().apply {
        binaryData = PNG
        mimeType = "image/png"
        pictureType = PictureTypes.DEFAULT_ID
        width = 1
        height = 1
        description = ""
    }

    private companion object {
        const val JAUDIOTAGGER_LOGGER = "org.jaudiotagger"
        const val ITUNES_MEAN = "com.apple.iTunes"
        const val MIK_KEY = "----:$ITUNES_MEAN:MIK_KEY"
        const val ROLLA_KEY = "----:$ITUNES_MEAN:ROLLA_TAGS"
        const val V1_TITLE = "V1 Title"
        const val V1_ARTIST = "V1 Artist"
        const val V1_ALBUM = "V1 Album"
        const val V1_GENRE_ROCK_INDEX = 17 // ID3v1 genre table: 17 = Rock

        // A real 1x1 PNG, so the MP3 picture frame keeps its exact bytes (same image as the tag editor's tests).
        val PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
    }
}
