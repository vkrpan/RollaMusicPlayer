package com.rolla.musicplayer.feature.tageditor.io

import java.io.File

/** Runs the shared [TagReaderWriterContractTest] suite against a FLAC (Vorbis comment) fixture. */
class FlacTagReaderWriterTest : TagReaderWriterContractTest() {

    override fun newFixtureFile(): File =
        File.createTempFile("fixture", ".flac").apply {
            deleteOnExit()
            writeBytes(AudioFixtures.flacBytes())
        }
}
