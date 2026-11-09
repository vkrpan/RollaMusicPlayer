package com.rolla.musicplayer.core.data.artwork

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.io.OutputStream

/**
 * [toJpegBytes], the re-encode step of [RetrieverArtworkDecoder] (final review, deferred Task 2 minor T2-b). The
 * [Bitmap] is a mock: its `compress` is an unimplemented stub under plain JUnit, and the result under test is exactly
 * what `compress` reports.
 */
class ToJpegBytesTest {

    private fun bitmapWhoseCompress(returns: Boolean, writes: ByteArray = byteArrayOf()): Bitmap = mockk {
        every { compress(Bitmap.CompressFormat.JPEG, QUALITY, any()) } answers {
            thirdArg<OutputStream>().write(writes)
            returns
        }
    }

    // Trivial stub (always throw) → RED here. Mutation: return an empty array → bytes differ → RED.
    @Test
    fun toJpegBytes_compressSucceeds_returnsTheEncodedBytes() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

        val bytes = bitmapWhoseCompress(returns = true, writes = jpeg).toJpegBytes(QUALITY)

        assertArrayEquals("the encoder's bytes are returned as written", jpeg, bytes)
    }

    // A false return must fail the decode, so the cache publishes nothing (it treats a throw as "cache nothing") rather
    // than an empty .jpg that every later load would hit. Trivial stub (return the stream's bytes) → nothing thrown →
    // RED. Mutation: ignore compress()'s result → nothing thrown → RED.
    @Test
    fun toJpegBytes_compressFails_throws() {
        val bitmap = bitmapWhoseCompress(returns = false)

        assertThrows("a failed JPEG encode must throw, not publish empty bytes", IOException::class.java) {
            bitmap.toJpegBytes(QUALITY)
        }
    }

    private companion object {
        const val QUALITY = 85
    }
}
