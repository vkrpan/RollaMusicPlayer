package com.rolla.musicplayer.feature.tageditor.io

import java.io.File

/** Runs the shared [TagReaderWriterContractTest] suite against an MP3 (ID3v2) fixture. */
class Mp3TagReaderWriterTest : TagReaderWriterContractTest() {

    override fun newFixtureFile(): File =
        File.createTempFile("fixture", ".mp3").apply {
            deleteOnExit()
            writeBytes(AudioFixtures.mp3Bytes())
        }
}
