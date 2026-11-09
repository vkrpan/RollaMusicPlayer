package com.rolla.musicplayer.feature.tageditor.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [supportsArtwork] decides by the editable copy's extension alone (spec goal 9): artwork is written only to MP3, FLAC
 * and M4A, case-insensitively. Pure name checks; no file is created.
 */
class SupportsArtworkTest {

    @Test
    fun supportsArtwork_mp3FlacM4a_anyCase_isTrue() {
        listOf("song.MP3", "a.flac", "b.m4a").forEach { name ->
            assertTrue("$name supports artwork", File(name).supportsArtwork())
        }
    }

    @Test
    fun supportsArtwork_otherFormatsAndNoExtension_isFalse() {
        // Control: the check can answer true, so the falses below are its own answers.
        assertTrue("control: a.flac supports artwork", File("a.flac").supportsArtwork())

        listOf("c.wav", "d.ogg", "e.aac", "noext").forEach { name ->
            assertFalse("$name does not support artwork", File(name).supportsArtwork())
        }
    }
}
