package com.rolla.musicplayer.feature.widget

import android.graphics.Bitmap
import com.rolla.musicplayer.core.data.artwork.SongArtworkCache
import com.rolla.musicplayer.core.model.SongArtworkRef
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * [WidgetArtworkLoader] resolves a per-track artwork value (`rolla-artwork://song/{id}?v={version}`) through
 * [SongArtworkCache] and decodes the cached file; anything else is "no artwork" without a read. The real
 * [android.graphics.BitmapFactory] decode is an unimplemented stub under plain JUnit, so the loader's file decode is
 * replaced by a recording lambda returning [mockk] `Bitmap`s (a mock never invokes the stub), and the memo is tested
 * directly through [WidgetArtworkLoader.decodeMemoized].
 */
class WidgetArtworkLoaderTest {

    private val bitmap = mockk<Bitmap>()
    private val decodedFiles = mutableListOf<File>()
    private val decode: (File) -> Bitmap? = { file ->
        decodedFiles += file
        bitmap
    }

    private fun loaderFor(cache: SongArtworkCache) = WidgetArtworkLoader(cache, Dispatchers.Unconfined, decode)

    // ── null / blank short-circuit ───────────────────────────────────────────

    @Test
    fun `load returns null for a null path without touching the cache`() = runTest {
        val cache = FakeSongArtworkCache()

        assertNull("a null path has no artwork", loaderFor(cache).load(null))
        assertEquals("a null path never reads the cache", 0, cache.calls)
    }

    @Test
    fun `load returns null for a blank path without touching the cache`() = runTest {
        val cache = FakeSongArtworkCache()

        assertNull("a blank path has no artwork", loaderFor(cache).load("   "))
        assertEquals("a blank path never reads the cache", 0, cache.calls)
    }

    // ── the per-track ref path ───────────────────────────────────────────────

    // Mutation: decode File(artworkPath) instead of the cache's file → the decode sees the wrong file → RED.
    @Test
    fun songRef_decodesTheCacheFile() = runTest {
        val file = File("/cache/42-100.jpg")
        val cache = FakeSongArtworkCache(files = mapOf(42L to file))

        val result = loaderFor(cache).load("rolla-artwork://song/42?v=100")

        assertSame("the decoded cache file is the widget's bitmap", bitmap, result)
        assertEquals("get is asked for the parsed ref", listOf(SongArtworkRef(42L, 100L)), cache.getCalls)
        assertEquals("the cache file is decoded exactly once", listOf(file), decodedFiles)
    }

    @Test
    fun `load queries the cache on every call, not just once behind the memo`() = runTest {
        val file = File("/cache/3-100.jpg")
        val cache = FakeSongArtworkCache(files = mapOf(3L to file))
        val loader = loaderFor(cache)

        loader.load("rolla-artwork://song/3?v=100")
        loader.load("rolla-artwork://song/3?v=100")

        // A new version of the file publishes a new cache file with a new lastModified, which busts the memo, so every
        // load must re-resolve the cache; only the decode itself is skippable behind the memo.
        assertEquals("every load re-resolves the cache", 2, cache.getCalls.size)
        assertEquals("the unchanged file decodes once", 1, decodedFiles.size)
    }

    @Test
    fun `load returns null without decoding when the track has no picture`() = runTest {
        val cache = FakeSongArtworkCache()

        val result = loaderFor(cache).load("rolla-artwork://song/7?v=100")

        assertNull("no picture has no artwork", result)
        assertEquals("the cache was asked", 1, cache.getCalls.size)
        assertEquals("nothing is decoded", emptyList<File>(), decodedFiles)
    }

    // Mutation: a non-ref path is decoded as a file (File(artworkPath)) instead of returning null → decode runs → RED.
    @Test
    fun nonRefPath_returnsNullWithoutReadingAnything() = runTest {
        val cache = FakeSongArtworkCache(files = mapOf(1L to File("/cache/1-100.jpg")))
        val loader = loaderFor(cache)
        assertSame("control: a ref decodes the cache file", bitmap, loader.load("rolla-artwork://song/1?v=100"))
        assertEquals("control: the cache path is reachable", 1, cache.calls)
        assertEquals("control: the decode is observable", 1, decodedFiles.size)

        val result = loader.load("content://media/external/audio/albumart/1")

        assertNull("a MediaStore album uri has no artwork", result)
        assertEquals("a MediaStore album uri never reads the cache", 1, cache.calls)
        assertEquals("a MediaStore album uri never decodes anything", 1, decodedFiles.size)
    }

    // Mutation: drop load's catch of non-cancellation exceptions → the IOException escapes load → RED.
    @Test
    fun getThrows_returnsNull() = runTest {
        val cache = FakeSongArtworkCache(
            files = mapOf(1L to File("/cache/1-100.jpg")),
            failures = mapOf(2L to IOException("disk full")),
        )
        val loader = loaderFor(cache)
        assertSame("control: a ref decodes the cache file", bitmap, loader.load("rolla-artwork://song/1?v=100"))

        val outcome = runCatching { loader.load("rolla-artwork://song/2?v=200") }

        assertTrue("a throwing cache must not escape load, got $outcome", outcome.isSuccess)
        assertNull("a throwing cache has no artwork", outcome.getOrNull())
        assertEquals("the throwing cache was asked", listOf(1L, 2L), cache.getCalls.map { it.songId })
    }

    // ── decodeMemoized: the memo mechanism itself ────────────────────────────

    @Test
    fun `decodeMemoized decodes once and memoizes the result for an unchanged key`() {
        val loader = loaderFor(FakeSongArtworkCache())
        val key = WidgetArtworkLoader.DecodeKey(absolutePath = "/cache/1.jpg", lastModified = 1_000L)
        var decodeCallCount = 0
        val decode = {
            decodeCallCount++
            bitmap
        }

        val first = loader.decodeMemoized(key, decode)
        val second = loader.decodeMemoized(key, decode)

        assertSame(bitmap, first)
        assertSame(bitmap, second)
        assertEquals("decode must run exactly once behind the memo", 1, decodeCallCount)
    }

    @Test
    fun `decodeMemoized busts and re-decodes when the file's lastModified changes`() {
        val loader = loaderFor(FakeSongArtworkCache())
        val staleBitmap = mockk<Bitmap>()
        val freshBitmap = mockk<Bitmap>()
        var decodeCallCount = 0
        val decode = {
            decodeCallCount++
            if (decodeCallCount == 1) staleBitmap else freshBitmap
        }

        val first = loader.decodeMemoized(
            WidgetArtworkLoader.DecodeKey(absolutePath = "/cache/1.jpg", lastModified = 1_000L),
            decode,
        )
        // Simulates the cache rewriting the file: same path, new lastModified -- an edited picture must never keep
        // serving stale art.
        val second = loader.decodeMemoized(
            WidgetArtworkLoader.DecodeKey(absolutePath = "/cache/1.jpg", lastModified = 2_000L),
            decode,
        )

        assertSame(staleBitmap, first)
        assertSame(freshBitmap, second)
        assertEquals(2, decodeCallCount)
    }

    @Test
    fun `decodeMemoized does not memoize a null decode result`() {
        val loader = loaderFor(FakeSongArtworkCache())
        var decodeCallCount = 0
        val key = WidgetArtworkLoader.DecodeKey(absolutePath = "/cache/1.jpg", lastModified = 1_000L)

        val first = loader.decodeMemoized(key) {
            decodeCallCount++
            null
        }
        val second = loader.decodeMemoized(key) {
            decodeCallCount++
            null
        }

        assertNull(first)
        assertNull(second)
        assertEquals(2, decodeCallCount)
    }

    /** [files] and [failures] answer `get` by song id; anything else is "no picture". [calls] counts every call. */
    private class FakeSongArtworkCache(
        private val files: Map<Long, File> = emptyMap(),
        private val failures: Map<Long, Exception> = emptyMap(),
    ) : SongArtworkCache {
        val getCalls = mutableListOf<SongArtworkRef>()
        var calls = 0
            private set

        override suspend fun get(ref: SongArtworkRef): File? {
            calls++
            getCalls += ref
            failures[ref.songId]?.let { throw it }
            return files[ref.songId]
        }

        override fun peek(ref: SongArtworkRef): File? {
            calls++
            return files[ref.songId]
        }

        override suspend fun invalidate(songId: Long) {
            calls++
        }

        override suspend fun clear() {
            calls++
        }
    }
}
