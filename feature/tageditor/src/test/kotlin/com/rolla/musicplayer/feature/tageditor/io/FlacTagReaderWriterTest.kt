package com.rolla.musicplayer.feature.tageditor.io

import com.rolla.musicplayer.core.testing.audio.AudioFixtures
import kotlinx.coroutines.test.runTest
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentFieldKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the shared [TagReaderWriterContractTest] suite against a FLAC (Vorbis comment) fixture, plus the FLAC-only case
 * of a picture stored as a Vorbis-comment field (`METADATA_BLOCK_PICTURE` or the legacy `COVERART`) rather than as a
 * PICTURE metadata block. jaudiotagger's `FlacTag` neither reports nor deletes such a picture; Android shows it.
 */
class FlacTagReaderWriterTest : TagReaderWriterContractTest() {

    override fun newFixtureFile(): File =
        File.createTempFile("fixture", ".flac").apply {
            deleteOnExit()
            writeBytes(AudioFixtures.flacBytes())
        }

    /** Every text field, plus a picture stored only as a Vorbis-comment field of the given form. */
    private suspend fun newVorbisPictureFile(form: VorbisPicture): File {
        val file = newFixtureFile()
        writer.write(file, ALL_FIELDS)
        val audioFile = AudioFileIO.read(file)
        val comments = (audioFile.tag as FlacTag).vorbisCommentTag
        when (form) {
            VorbisPicture.METADATA_BLOCK_PICTURE -> comments.setField(
                ArtworkFactory.getNew().apply {
                    binaryData = PNG_A
                    mimeType = "image/png"
                    pictureType = PictureTypes.DEFAULT_ID
                    width = 1
                    height = 1
                    description = ""
                },
            )
            VorbisPicture.COVERART -> comments.setArtworkField(PNG_A, "image/png")
            VorbisPicture.MALFORMED_METADATA_BLOCK_PICTURE -> comments.setField(
                comments.createField(VorbisCommentFieldKey.METADATA_BLOCK_PICTURE, "not base64!"),
            )
        }
        audioFile.commit()
        return file
    }

    private fun vorbisPictureCount(file: File): Int =
        (AudioFileIO.read(file).tag as FlacTag).vorbisCommentTag.artworkList.size

    private fun assertOnlyAVorbisPicture(file: File) {
        val tag = AudioFileIO.read(file).tag as FlacTag
        assertEquals("control: the picture is a Vorbis-comment field", 1, vorbisPictureCount(file))
        assertTrue("control: there is no PICTURE block", tag.images.isEmpty())
        assertNull("control: FlacTag alone does not see the picture", tag.firstArtwork)
    }

    @Test
    fun hasArtwork_vorbisCommentPictureOnly_isTrue() = runTest {
        val file = newVorbisPictureFile(VorbisPicture.METADATA_BLOCK_PICTURE)
        assertOnlyAVorbisPicture(file)

        assertTrue("a METADATA_BLOCK_PICTURE field counts as artwork", reader.hasArtwork(file))
    }

    @Test
    fun removeArtwork_vorbisCommentPicture_removesItAndKeepsEveryTextField() = runTest {
        val file = newVorbisPictureFile(VorbisPicture.METADATA_BLOCK_PICTURE)
        assertOnlyAVorbisPicture(file)

        writer.removeArtwork(file)

        assertEquals("the METADATA_BLOCK_PICTURE field is gone", 0, vorbisPictureCount(file))
        assertEquals("every text field survives the removal", ALL_FIELDS, reader.read(file))
    }

    @Test
    fun removeArtwork_legacyCoverArtField_removesIt() = runTest {
        val file = newVorbisPictureFile(VorbisPicture.COVERART)
        assertOnlyAVorbisPicture(file)

        writer.removeArtwork(file)

        assertEquals("the COVERART field is gone", 0, vorbisPictureCount(file))
        assertFalse("nothing reports artwork afterwards", reader.hasArtwork(file))
    }

    @Test
    fun writeArtwork_overAVorbisCommentPicture_leavesOnlyTheNewPicture() = runTest {
        val file = newVorbisPictureFile(VorbisPicture.METADATA_BLOCK_PICTURE)
        assertOnlyAVorbisPicture(file)

        writer.writeArtwork(file, pickedArtwork(PNG_B))

        assertEquals("the old Vorbis-comment picture is gone", 0, vorbisPictureCount(file))
        assertArrayEquals(
            "the new picture is the file's one PICTURE block",
            PNG_B,
            (AudioFileIO.read(file).tag as FlacTag).images.single().imageData,
        )
    }

    // A picture field whose value is not valid base64: decoding it (VorbisCommentTag.getArtworkList) throws
    // IllegalArgumentException, so the editor's artwork check must go by field presence (ruling P19).

    private fun vorbisComments(file: File) = (AudioFileIO.read(file).tag as FlacTag).vorbisCommentTag

    private fun assertMalformedVorbisPicture(file: File) {
        assertTrue(
            "control: the METADATA_BLOCK_PICTURE field is present",
            vorbisComments(file).hasField(VorbisCommentFieldKey.METADATA_BLOCK_PICTURE),
        )
        assertTrue(
            "control: decoding the field throws",
            runCatching { vorbisComments(file).artworkList }.isFailure,
        )
    }

    @Test
    fun hasArtwork_malformedVorbisPicture_isTrueWithoutThrowing() = runTest {
        val file = newVorbisPictureFile(VorbisPicture.MALFORMED_METADATA_BLOCK_PICTURE)
        assertMalformedVorbisPicture(file)

        val detected = runCatching { reader.hasArtwork(file) }

        assertEquals("hasArtwork answers true without throwing", Result.success(true), detected)
    }

    @Test
    fun removeArtwork_malformedVorbisPicture_clearsItWithoutThrowing() = runTest {
        val file = newVorbisPictureFile(VorbisPicture.MALFORMED_METADATA_BLOCK_PICTURE)
        assertMalformedVorbisPicture(file)

        val removed = runCatching { writer.removeArtwork(file) }

        assertTrue("removeArtwork must not throw, got $removed", removed.isSuccess)
        val comments = vorbisComments(file)
        assertFalse(
            "no Vorbis picture field is left",
            comments.hasField(VorbisCommentFieldKey.METADATA_BLOCK_PICTURE) ||
                comments.hasField(VorbisCommentFieldKey.COVERART),
        )
        assertEquals("every text field survives the removal", ALL_FIELDS, reader.read(file))
    }

    private enum class VorbisPicture { METADATA_BLOCK_PICTURE, COVERART, MALFORMED_METADATA_BLOCK_PICTURE }
}
