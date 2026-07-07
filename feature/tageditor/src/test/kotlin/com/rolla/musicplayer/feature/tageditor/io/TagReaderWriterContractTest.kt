package com.rolla.musicplayer.feature.tageditor.io

import com.rolla.musicplayer.feature.tageditor.SongTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jaudiotagger.audio.AudioFileIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.logging.Level
import java.util.logging.Logger

private val ALL_FIELDS = SongTags(
    title = "Fixture Title",
    artist = "Fixture Artist",
    album = "Fixture Album",
    albumArtist = "Fixture Album Artist",
    genre = "Fixture Genre",
    year = "2024",
    trackNumber = "3",
    composer = "Fixture Composer",
)

// A real, structurally-valid 1x1 PNG (signature + IHDR/IDAT/IEND), so format-sniffing writers
// (mp4's covr type flag in particular) recognize it. PNG_B appends one trailing byte after IEND:
// still a valid PNG by signature (all any tag writer inspects) but byte-distinct from PNG_A, which
// is what makes "replace, don't append" observable.
private val PNG_A: ByteArray = Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
)
private val PNG_B: ByteArray = PNG_A + byteArrayOf(0)

private fun pickedArtwork(bytes: ByteArray): PickedArtwork =
    PickedArtwork(bytes = bytes, mimeType = "image/png", width = 1, height = 1)

/**
 * Shared contract exercised against every audio format [TagReader]/[TagWriter] must support.
 * Format-specific subclasses supply a fresh, structurally-valid fixture file for their format --
 * jaudiotagger itself does the actual reading/writing here, these are not fakes/mocks of it.
 */
abstract class TagReaderWriterContractTest {

    private val reader = TagReader(Dispatchers.Unconfined)
    private val writer = TagWriter(Dispatchers.Unconfined)

    /** A fresh temp file in this subclass's format, with no pre-existing tag. */
    abstract fun newFixtureFile(): File

    @Before
    fun silenceJaudiotaggerLogging() {
        // jaudiotagger logs at INFO/WARNING via java.util.logging; this only quiets test output
        // for this JVM test process, it does not change any project-wide logging configuration.
        // Some of its loggers (e.g. the mp4 reader) set their own explicit level at class-init
        // time, which overrides an ancestor logger's level -- so silence every handler up the
        // "org.jaudiotagger" logger chain too, since Handler-level filtering is independent of
        // each Logger's own level and still applies regardless.
        var logger: Logger? = Logger.getLogger("org.jaudiotagger").apply { level = Level.OFF }
        while (logger != null) {
            logger.handlers.forEach { it.level = Level.OFF }
            logger = logger.parent
        }
    }

    @Test
    fun read_untaggedFile_returnsAllBlankSongTags() = runTest {
        val file = newFixtureFile()

        val tags = reader.read(file)

        assertEquals(SongTags(), tags)
    }

    @Test
    fun write_allEightFields_thenRead_roundTripsExactly() = runTest {
        val file = newFixtureFile()

        writer.write(file, ALL_FIELDS)
        val readBack = reader.read(file)

        assertEquals(ALL_FIELDS, readBack)
    }

    @Test
    fun write_thenRewriteWithSomeFieldsBlank_clearsOnlyThoseFieldsAndKeepsTheRest() = runTest {
        val file = newFixtureFile()
        writer.write(file, ALL_FIELDS)

        val partiallyCleared = ALL_FIELDS.copy(genre = "", composer = "", year = "")
        writer.write(file, partiallyCleared)
        val readBack = reader.read(file)

        assertEquals(partiallyCleared, readBack)
    }

    @Test
    fun write_thenReadingRawFile_stillParsesAsValidAudio() = runTest {
        val file = newFixtureFile()
        val before = AudioFileIO.read(file).audioHeader

        writer.write(file, ALL_FIELDS)
        val after = AudioFileIO.read(file).audioHeader

        assertEquals(before.sampleRate, after.sampleRate)
        assertEquals(before.trackLength, after.trackLength)
        assertEquals(before.bitRate, after.bitRate)
    }

    // ── artwork ───────────────────────────────────────────────────────────

    @Test
    fun writeArtwork_onUntaggedFile_embedsExactBytes() = runTest {
        val file = newFixtureFile()

        writer.writeArtwork(file, pickedArtwork(PNG_A))

        val embedded = AudioFileIO.read(file).tag.firstArtwork
        assertArrayEquals(PNG_A, embedded.binaryData)
    }

    @Test
    fun writeArtwork_again_replacesTheCoverRatherThanAppendingASecondOne() = runTest {
        val file = newFixtureFile()
        writer.writeArtwork(file, pickedArtwork(PNG_A))

        writer.writeArtwork(file, pickedArtwork(PNG_B))

        val tag = AudioFileIO.read(file).tag
        assertEquals(1, tag.artworkList.size)
        assertArrayEquals(PNG_B, tag.firstArtwork.binaryData)
    }

    @Test
    fun writeArtwork_afterFieldWrite_leavesEveryTextFieldIntact() = runTest {
        val file = newFixtureFile()
        writer.write(file, ALL_FIELDS)

        writer.writeArtwork(file, pickedArtwork(PNG_A))

        assertEquals(ALL_FIELDS, reader.read(file))
    }
}
