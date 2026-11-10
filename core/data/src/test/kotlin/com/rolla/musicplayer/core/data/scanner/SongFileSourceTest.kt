package com.rolla.musicplayer.core.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

/** [chooseReadMode]: read the MediaStore `DATA` file in place when it can be read, copy the uri's bytes otherwise. */
class SongFileSourceTest {

    @Test
    fun chooseReadMode_api29AlwaysCopies() {
        assertEquals(ReadMode.COPY, chooseReadMode(sdk = 29, dataPath = PATH, canRead = true))
    }

    @Test
    fun chooseReadMode_readablePathReadsInPlace() {
        assertEquals(ReadMode.IN_PLACE, chooseReadMode(sdk = 33, dataPath = PATH, canRead = true))
        assertEquals(ReadMode.IN_PLACE, chooseReadMode(sdk = 28, dataPath = PATH, canRead = true))
    }

    @Test
    fun chooseReadMode_noOrUnreadablePathCopies() {
        assertEquals(ReadMode.COPY, chooseReadMode(sdk = 33, dataPath = null, canRead = false))
        assertEquals(ReadMode.COPY, chooseReadMode(sdk = 33, dataPath = PATH, canRead = false))
    }
}

private const val PATH = "/storage/x.mp3"
