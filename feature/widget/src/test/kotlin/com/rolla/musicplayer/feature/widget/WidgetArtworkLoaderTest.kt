package com.rolla.musicplayer.feature.widget

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [BitmapFactory]-backed decode paths (the happy path and malformed-bytes path) require a real
 * Android runtime -- `BitmapFactory` is an unimplemented JVM stub under plain JUnit -- so those
 * are verified on-device (see the widget-agent report). This suite covers everything reachable
 * without a real decode:
 * - the null/blank short-circuit (touches neither the resolver nor the cache)
 * - the albumId-parse + [AlbumArtworkCache] wiring: a well-formed albumart uri resolves its
 *   albumId and calls through to the cache on every [WidgetArtworkLoader.load] (the cache's own
 *   `lastModified` touch-for-recency is how staleness is detected, so it must never be
 *   short-circuited by the local memo); a `null` from the cache returns `null` immediately without
 *   falling back to a direct decode; an unparseable path never touches the cache and instead
 *   exercises the pre-wave-2 direct-`content://`-decode fallback
 * - the exception-to-null failure contract, from both the resolver and the cache
 * - [WidgetArtworkLoader.decodeMemoized] directly, using [mockk] `Bitmap` instances (an
 *   `android.graphics.Bitmap` mock never invokes the real, unmocked JVM stub) to prove the memo
 *   hits for an unchanged [WidgetArtworkLoader.DecodeKey] and busts for a changed one -- this is
 *   the exact mechanism [WidgetArtworkLoader.load] wires a cache [java.io.File]'s
 *   `(absolutePath, lastModified)` into, so it stands in for a real decode-and-memoize /
 *   decode-once / re-decode-on-rewrite pass without needing [android.graphics.BitmapFactory] to
 *   work under plain JUnit.
 */
class WidgetArtworkLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val resolver: ContentResolver = mockk(relaxed = true)
    private val albumArtworkCache: AlbumArtworkCache = mockk(relaxed = true)
    private val loader = WidgetArtworkLoader(context, albumArtworkCache, Dispatchers.Unconfined)

    // ── null / blank short-circuit ───────────────────────────────────────────

    @Test
    fun `load returns null for a null path without touching the resolver or cache`() = runTest {
        val bitmap = loader.load(null)

        assertNull(bitmap)
        verify(exactly = 0) { context.contentResolver }
        coVerify(exactly = 0) { albumArtworkCache.get(any(), any()) }
    }

    @Test
    fun `load returns null for a blank path without touching the resolver or cache`() = runTest {
        val bitmap = loader.load("   ")

        assertNull(bitmap)
        verify(exactly = 0) { context.contentResolver }
        coVerify(exactly = 0) { albumArtworkCache.get(any(), any()) }
    }

    // ── albumId parse + cache wiring ─────────────────────────────────────────

    @Test
    fun `load resolves albumId from a well-formed albumart uri and queries the cache`() = runTest {
        val path = "content://media/external/audio/albumart/42"
        coEvery { albumArtworkCache.get(42L, path) } returns null

        loader.load(path)

        coVerify(exactly = 1) { albumArtworkCache.get(42L, path) }
    }

    @Test
    fun `load queries the cache on every call, not just once behind the memo`() = runTest {
        val path = "content://media/external/audio/albumart/3"
        val file = tempFolder.newFile("3.jpg") // empty -- BitmapFactory decode fails, exception caught to null
        coEvery { albumArtworkCache.get(3L, path) } returns file

        loader.load(path)
        loader.load(path)

        // The cache's own lastModified touch-for-recency (see AlbumArtworkCacheImpl) is how a
        // rewritten file busts the memo, so every load must re-resolve the cache -- only the
        // BitmapFactory decode itself is skippable behind the memo.
        coVerify(exactly = 2) { albumArtworkCache.get(3L, path) }
    }

    @Test
    fun `load returns null immediately when the cache has no decodable art`() = runTest {
        val path = "content://media/external/audio/albumart/7"
        coEvery { albumArtworkCache.get(7L, path) } returns null

        val bitmap = loader.load(path)

        assertNull(bitmap)
        verify(exactly = 0) { context.contentResolver }
    }

    @Test
    fun `load falls back to direct decode for an unparseable path without touching the cache`() = runTest {
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } throws SecurityException("no grant")

        val bitmap = loader.load("content://media/local/art/not-a-number")

        assertNull(bitmap)
        coVerify(exactly = 0) { albumArtworkCache.get(any(), any()) }
    }

    // ── failure contract ─────────────────────────────────────────────────────

    @Test
    fun `load returns null when the resolver throws SecurityException`() = runTest {
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } throws SecurityException("no grant")

        val bitmap = loader.load("content://media/local/art/not-a-number")

        assertNull(bitmap)
    }

    @Test
    fun `load returns null when the cache throws SecurityException`() = runTest {
        val path = "content://media/external/audio/albumart/1"
        coEvery { albumArtworkCache.get(1L, path) } throws SecurityException("no grant")

        val bitmap = loader.load(path)

        assertNull(bitmap)
    }

    // ── decodeMemoized: the memo mechanism itself ────────────────────────────

    @Test
    fun `decodeMemoized decodes once and memoizes the result for an unchanged key`() {
        val bitmap = mockk<Bitmap>()
        val key = WidgetArtworkLoader.DecodeKey.CacheFile(absolutePath = "/cache/1.jpg", lastModified = 1_000L)
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
        val staleBitmap = mockk<Bitmap>()
        val freshBitmap = mockk<Bitmap>()
        var decodeCallCount = 0
        val decode = {
            decodeCallCount++
            if (decodeCallCount == 1) staleBitmap else freshBitmap
        }

        val first = loader.decodeMemoized(
            WidgetArtworkLoader.DecodeKey.CacheFile(absolutePath = "/cache/1.jpg", lastModified = 1_000L),
            decode,
        )
        // Simulates AlbumArtworkCache#invalidate + a subsequent AlbumArtworkCache#get rewriting
        // the file: same path, new lastModified -- an edited cover must never keep serving stale art.
        val second = loader.decodeMemoized(
            WidgetArtworkLoader.DecodeKey.CacheFile(absolutePath = "/cache/1.jpg", lastModified = 2_000L),
            decode,
        )

        assertSame(staleBitmap, first)
        assertSame(freshBitmap, second)
        assertEquals(2, decodeCallCount)
    }

    @Test
    fun `decodeMemoized does not memoize a null decode result`() {
        var decodeCallCount = 0
        val key = WidgetArtworkLoader.DecodeKey.DirectUri("content://media/local/art/1")

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
}
