package com.rolla.musicplayer.feature.tageditor.io

import com.rolla.musicplayer.core.testing.audio.M4aFixture
import java.io.File

/** Runs the shared [TagReaderWriterContractTest] suite against an M4A/MP4 (mp4a + esds) fixture. */
class M4aTagReaderWriterTest : TagReaderWriterContractTest() {

    override fun newFixtureFile(): File =
        File.createTempFile("fixture", ".m4a").apply {
            deleteOnExit()
            writeBytes(M4aFixture.bytes())
        }
}
