package com.rolla.musicplayer.core.data.artwork

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [AlbumArtworkCacheImpl]'s cache bookkeeping -- key derivation, hit/miss,
 * invalidation, and LRU eviction. [ArtworkDecoder] is faked throughout: the real
 * [BitmapArtworkDecoder] calls into `android.graphics.BitmapFactory`, an unimplemented stub under
 * plain JUnit (the same constraint `WidgetArtworkLoaderTest` documents for its own decode path),
 * so none of that is exercised here -- only the plain-Kotlin logic this class owns.
 *
 * A real temp directory ([TemporaryFolder]) backs every test rather than a mocked `File`, since
 * the behavior under test IS the directory bookkeeping (existence checks, writes, deletes,
 * `lastModified`-based ordering). [Dispatchers.Unconfined] makes every `withContext` hop run
 * synchronously, matching `WidgetArtworkLoaderTest`'s precedent.
 */
class AlbumArtworkCacheImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun cache(decoder: ArtworkDecoder): AlbumArtworkCacheImpl =
        AlbumArtworkCacheImpl(tempFolder.root, decoder, Dispatchers.Unconfined)

    // ── key derivation ───────────────────────────────────────────────────────

    @Test
    fun cacheFileName_isStablePerAlbumId() {
        assertEquals(cacheFileName(42L), cacheFileName(42L))
    }

    @Test
    fun cacheFileName_isDistinctAcrossAlbumIds() {
        assertNotEquals(cacheFileName(1L), cacheFileName(2L))
    }

    // ── get: miss / hit ──────────────────────────────────────────────────────

    @Test
    fun get_miss_decodesOnceWritesFileAndReturnsIt() = runTest {
        val decoder = FakeArtworkDecoder()
        val artworkCache = cache(decoder)

        val file = artworkCache.get(albumId = 1L, sourceUri = "content://a")

        assertNotNull(file)
        assertTrue(file!!.exists())
        assertArrayEquals(FakeArtworkDecoder.DEFAULT_BYTES, file.readBytes())
        assertEquals(1, decoder.callCount)
    }

    @Test
    fun get_secondCallForSameAlbum_returnsCachedFileWithoutReinvokingDecoder() = runTest {
        val decoder = FakeArtworkDecoder()
        val artworkCache = cache(decoder)

        val first = artworkCache.get(albumId = 1L, sourceUri = "content://a")
        val second = artworkCache.get(albumId = 1L, sourceUri = "content://a")

        assertEquals(first, second)
        assertEquals(1, decoder.callCount)
    }

    @Test
    fun get_decoderReturnsNull_returnsNullAndWritesNothing() = runTest {
        val decoder = FakeArtworkDecoder { null }
        val artworkCache = cache(decoder)

        val file = artworkCache.get(albumId = 1L, sourceUri = "content://a")

        assertNull(file)
        assertTrue(tempFolder.root.listFiles()?.toList().orEmpty().isEmpty())
    }

    @Test
    fun get_cacheHit_leavesLastModifiedUnchanged() = runTest {
        // A read must NOT bump lastModified: Coil's file cache key and the widget's decode memo both
        // key on it, so a moving timestamp on every bind would defeat both caches. It moves only on
        // (re)write -- proven by get_afterInvalidate_reDecodeAdvancesLastModified below.
        val decoder = FakeArtworkDecoder()
        val artworkCache = cache(decoder)
        val file = artworkCache.get(albumId = 1L, sourceUri = "content://a")!!
        file.setLastModified(BASE_TIME)

        artworkCache.get(albumId = 1L, sourceUri = "content://a")

        assertEquals(BASE_TIME, file.lastModified())
    }

    @Test
    fun get_afterInvalidate_reDecodeAdvancesLastModified() = runTest {
        // The counterpart: an edited cover MUST refresh downstream caches, so the rewritten file's
        // timestamp advances past the stamp the pre-invalidate file carried.
        val decoder = FakeArtworkDecoder()
        val artworkCache = cache(decoder)
        val file = artworkCache.get(albumId = 1L, sourceUri = "content://a")!!
        file.setLastModified(BASE_TIME)

        artworkCache.invalidate(1L)
        val reDecoded = artworkCache.get(albumId = 1L, sourceUri = "content://a")!!

        assertTrue(reDecoded.lastModified() > BASE_TIME)
    }

    // ── invalidate ────────────────────────────────────────────────────────────

    @Test
    fun invalidate_deletesFileAndEmitsAlbumId() = runTest {
        val decoder = FakeArtworkDecoder()
        val artworkCache = cache(decoder)
        val file = artworkCache.get(albumId = 7L, sourceUri = "content://a")!!

        artworkCache.invalidations.test {
            artworkCache.invalidate(7L)
            assertEquals(7L, awaitItem())
        }
        assertFalse(file.exists())
    }

    @Test
    fun invalidate_uncachedAlbumId_stillEmits() = runTest {
        val artworkCache = cache(FakeArtworkDecoder())

        artworkCache.invalidations.test {
            artworkCache.invalidate(999L)
            assertEquals(999L, awaitItem())
        }
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Test
    fun clear_deletesAllCachedFiles() = runTest {
        val decoder = FakeArtworkDecoder()
        val artworkCache = cache(decoder)
        artworkCache.get(albumId = 1L, sourceUri = "content://a")
        artworkCache.get(albumId = 2L, sourceUri = "content://b")

        artworkCache.clear()

        assertTrue(tempFolder.root.listFiles()?.toList().orEmpty().isEmpty())
    }

    // ── LRU eviction ──────────────────────────────────────────────────────────

    @Test
    fun get_writeOverCap_evictsOldestFirstUntilUnderCap() = runTest {
        val decoder = FakeArtworkDecoder { uri -> ByteArray(if (uri == "c") FIFTEEN_MB else TEN_MB) }
        val artworkCache = cache(decoder)

        val fileA = artworkCache.get(albumId = 1L, sourceUri = "a")!!
        fileA.setLastModified(BASE_TIME)
        val fileB = artworkCache.get(albumId = 2L, sourceUri = "b")!!
        fileB.setLastModified(BASE_TIME + 1_000)

        // 10 + 10 + 15 = 35 MiB, over the 32 MiB cap -- this write must trigger eviction.
        val fileC = artworkCache.get(albumId = 3L, sourceUri = "c")

        assertFalse("oldest entry should be evicted", fileA.exists())
        assertTrue("newer entry should survive", fileB.exists())
        assertNotNull(fileC)
        assertTrue("just-written entry should never be evicted", fileC!!.exists())
        val totalBytes = tempFolder.root.listFiles()?.sumOf { it.length() } ?: 0L
        assertTrue(totalBytes <= AlbumArtworkCacheImpl.MAX_CACHE_BYTES)
    }

    /**
     * Proves eviction protects the just-written file by identity, not merely because it usually
     * has the newest [java.io.File.lastModified]. The three pre-existing entries are stamped
     * further in the future than "now" -- without the `protectedFile` guard in
     * [AlbumArtworkCacheImpl], the just-written (oldest-by-timestamp) file would be evicted first,
     * and this test would fail.
     */
    @Test
    fun get_neverEvictsTheEntryItJustWrote_evenWhenItLooksOldestByTimestamp() = runTest {
        val decoder = FakeArtworkDecoder { ByteArray(TEN_MB) }
        val artworkCache = cache(decoder)

        val preexisting = listOf(
            artworkCache.get(albumId = 1L, sourceUri = "a")!!,
            artworkCache.get(albumId = 2L, sourceUri = "b")!!,
            artworkCache.get(albumId = 3L, sourceUri = "c")!!,
        )
        val future = System.currentTimeMillis() + FUTURE_OFFSET_MS
        preexisting.forEach { it.setLastModified(future) }

        val justWritten = artworkCache.get(albumId = 4L, sourceUri = "d")

        assertNotNull(justWritten)
        assertTrue(justWritten!!.exists())
        assertEquals(2, preexisting.count { it.exists() })
        val totalBytes = tempFolder.root.listFiles()?.sumOf { it.length() } ?: 0L
        assertTrue(totalBytes <= AlbumArtworkCacheImpl.MAX_CACHE_BYTES)
    }

    private companion object {
        const val BASE_TIME = 1_700_000_000_000L
        const val FUTURE_OFFSET_MS = 1_000_000L
        const val TEN_MB = 10 * 1024 * 1024
        const val FIFTEEN_MB = 15 * 1024 * 1024
    }
}

private class FakeArtworkDecoder(
    private val bytesForUri: (String) -> ByteArray? = { DEFAULT_BYTES },
) : ArtworkDecoder {

    var callCount = 0
        private set

    override suspend fun decode(sourceUri: String): ByteArray? {
        callCount++
        return bytesForUri(sourceUri)
    }

    companion object {
        val DEFAULT_BYTES = byteArrayOf(1, 2, 3)
    }
}
