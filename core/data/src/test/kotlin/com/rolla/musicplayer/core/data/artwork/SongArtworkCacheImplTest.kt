package com.rolla.musicplayer.core.data.artwork

import com.rolla.musicplayer.core.model.SongArtworkRef
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Unit tests for [SongArtworkCacheImpl]'s bookkeeping: hit/miss, the negative marker, versions, invalidation, clear
 * and write-age eviction. [EmbeddedArtworkDecoder] is faked throughout: the real [RetrieverArtworkDecoder] needs
 * `MediaMetadataRetriever` and `BitmapFactory`, which are unimplemented stubs under plain JUnit.
 *
 * A real temp directory backs every test, since the behaviour under test is the directory itself (names, writes,
 * deletes, `lastModified`). [Dispatchers.Unconfined] makes every `withContext` hop run synchronously.
 */
class SongArtworkCacheImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun cache(decoder: EmbeddedArtworkDecoder): SongArtworkCacheImpl =
        SongArtworkCacheImpl(tempFolder.root, decoder, Dispatchers.Unconfined)

    private fun cachedNames(): List<String> = tempFolder.root.list().orEmpty().sorted()

    // Mutation: the miss path writes an empty payload instead of the decoded bytes → bytes differ → RED.
    @Test
    fun get_picture_writesAndReturnsTheFile() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { DecodedArtwork.Picture(BYTES_A) }

        val file = cache(decoder).get(SongArtworkRef(songId = 1L, version = 5L))

        assertNotNull("a picture must yield a cached file", file)
        assertArrayEquals("the cached file must hold the decoded bytes", BYTES_A, file!!.readBytes())
        assertEquals("one miss decodes once", 1, decoder.decodeCalls)
    }

    // Mutation: a hit stamps lastModified to now → the stamp moves → RED.
    @Test
    fun get_hit_doesNotDecodeAgainNorTouchLastModified() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { DecodedArtwork.Picture(BYTES_A) }
        val artworkCache = cache(decoder)
        val ref = SongArtworkRef(songId = 1L, version = 5L)
        val first = artworkCache.get(ref)
        assertNotNull("the first get must publish a file", first)
        assertTrue("the test needs to set the stamp", first!!.setLastModified(BASE_TIME))

        val second = artworkCache.get(ref)

        assertEquals("a hit returns the published file", first, second)
        assertEquals("a hit must not decode", 1, decoder.decodeCalls)
        assertEquals("a hit must not touch lastModified", BASE_TIME, first.lastModified())
    }

    // Mutation: DecodedArtwork.None writes no marker → the second get decodes again (calls == 2) → RED.
    @Test
    fun get_none_returnsNullAndRemembersIt() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { DecodedArtwork.None }
        val artworkCache = cache(decoder)
        val ref = SongArtworkRef(songId = 1L, version = 5L)

        assertNull("no picture → null", artworkCache.get(ref))
        assertEquals("the first get decodes", 1, decoder.decodeCalls)
        assertNull("no picture → null again", artworkCache.get(ref))
        assertEquals("the negative marker answers the second get without decoding", 1, decoder.decodeCalls)
    }

    // Mutation: a throwing decode writes the negative marker as if it were None → a file appears and the second
    // get does not decode → RED.
    @Test
    fun get_decoderThrows_returnsNullAndCachesNothing() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { throw IOException("corrupt picture") }
        val artworkCache = cache(decoder)
        val ref = SongArtworkRef(songId = 1L, version = 5L)

        assertNull("a failed decode → null", artworkCache.get(ref))
        assertEquals("a failed decode leaves the directory empty", emptyList<String>(), cachedNames())
        assertNull("a failed decode → null again", artworkCache.get(ref))
        assertEquals("a failed decode is retried by the next get", 2, decoder.decodeCalls)
    }

    // Mutation: the miss path no longer drops the song's other versions → "1-1.jpg" remains → RED.
    @Test
    fun get_newVersion_dropsTheOldFileAndDecodesAgain() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { DecodedArtwork.Picture(BYTES_A) }
        val artworkCache = cache(decoder)

        artworkCache.get(SongArtworkRef(songId = 1L, version = 1L))
        assertEquals("version 1 is published", listOf("1-1.jpg"), cachedNames())
        val newer = artworkCache.get(SongArtworkRef(songId = 1L, version = 2L))

        assertNotNull("version 2 is published", newer)
        assertEquals("a new version decodes again", 2, decoder.decodeCalls)
        assertEquals("only the newest version remains", listOf("1-2.jpg"), cachedNames())
    }

    // Mutation: the file name drops the song id → song 2 hits song 1's file → same file, song 1's bytes → RED.
    @Test
    fun twoSongs_neverShareAFile() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { songId ->
            DecodedArtwork.Picture(if (songId == 1L) BYTES_A else BYTES_B)
        }
        val artworkCache = cache(decoder)

        val first = artworkCache.get(SongArtworkRef(songId = 1L, version = 7L))
        val second = artworkCache.get(SongArtworkRef(songId = 2L, version = 7L))

        assertNotNull("song 1 has a file", first)
        assertNotNull("song 2 has a file", second)
        assertNotEquals("two songs must not share a file", first, second)
        assertArrayEquals("song 1's file holds song 1's picture", BYTES_A, first!!.readBytes())
        assertArrayEquals("song 2's file holds song 2's picture", BYTES_B, second!!.readBytes())
    }

    // Mutation: peek returns the picture file without the exists() check → non-null before any get → RED.
    @Test
    fun peek_onlyAfterAPublishedPicture() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { songId ->
            if (songId == 1L) DecodedArtwork.Picture(BYTES_A) else DecodedArtwork.None
        }
        val artworkCache = cache(decoder)
        val withPicture = SongArtworkRef(songId = 1L, version = 3L)
        val withoutPicture = SongArtworkRef(songId = 2L, version = 3L)

        assertNull("nothing is published before a get", artworkCache.peek(withPicture))
        val published = artworkCache.get(withPicture)
        assertNotNull("the get publishes a picture", published)
        assertEquals("peek finds the published picture", published, artworkCache.peek(withPicture))
        artworkCache.get(withoutPicture)
        assertNull("a negative marker is not a picture", artworkCache.peek(withoutPicture))
        assertEquals("peek never decodes", 2, decoder.decodeCalls)
    }

    // Mutation: invalidate deletes only the picture file → the negative marker survives → song 2 is not decoded
    // again → RED.
    @Test
    fun invalidate_dropsPictureAndNegativeMarker() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { songId ->
            if (songId == 1L) DecodedArtwork.Picture(BYTES_A) else DecodedArtwork.None
        }
        val artworkCache = cache(decoder)
        val withPicture = SongArtworkRef(songId = 1L, version = 3L)
        val withoutPicture = SongArtworkRef(songId = 2L, version = 3L)
        artworkCache.get(withPicture)
        artworkCache.get(withoutPicture)
        assertEquals("both are cached", listOf("1-3.jpg", "2-3.none"), cachedNames())

        artworkCache.invalidate(1L)
        artworkCache.invalidate(2L)
        artworkCache.get(withPicture)
        artworkCache.get(withoutPicture)

        assertEquals("song 1 decodes again after invalidate", 2, decoder.callsFor(1L))
        assertEquals("song 2 decodes again after invalidate", 2, decoder.callsFor(2L))
    }

    // Mutation: clear deletes nothing → the files remain → RED.
    @Test
    fun clear_wipesEverything() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { songId ->
            if (songId == 1L) DecodedArtwork.Picture(BYTES_A) else DecodedArtwork.None
        }
        val artworkCache = cache(decoder)
        artworkCache.get(SongArtworkRef(songId = 1L, version = 3L))
        artworkCache.get(SongArtworkRef(songId = 2L, version = 3L))
        assertEquals("both are cached before clear", listOf("1-3.jpg", "2-3.none"), cachedNames())

        artworkCache.clear()

        assertEquals("clear leaves the directory empty", emptyList<String>(), cachedNames())
    }

    /**
     * Ported from the album cache: eviction protects the just-written file by identity, not because it usually has
     * the newest stamp. The three earlier entries are stamped in the future, so without the guard the just-written
     * (oldest-by-stamp) file would be evicted first.
     *
     * Mutation: eviction no longer skips the just-written file → it is deleted → RED.
     */
    @Test
    fun eviction_keepsTheJustWrittenFileAndStaysUnderBudget() = runTest {
        val decoder = FakeEmbeddedArtworkDecoder { DecodedArtwork.Picture(ByteArray(TEN_MB)) }
        val artworkCache = cache(decoder)
        val earlier = (1L..3L).map { songId ->
            requireNotNull(artworkCache.get(SongArtworkRef(songId = songId, version = 1L))) { "song $songId cached" }
        }
        val future = System.currentTimeMillis() + FUTURE_OFFSET_MS
        earlier.forEach { assertTrue("the test needs to set the stamp", it.setLastModified(future)) }

        val justWritten = artworkCache.get(SongArtworkRef(songId = 4L, version = 1L))

        assertNotNull("the fourth picture is published", justWritten)
        assertTrue("the just-written file is never evicted", justWritten!!.exists())
        assertEquals("one earlier file is evicted to make room", 2, earlier.count { it.exists() })
        val totalBytes = tempFolder.root.listFiles()?.sumOf { it.length() } ?: 0L
        assertTrue("the directory stays under the budget", totalBytes <= SongArtworkCacheImpl.MAX_CACHE_BYTES)
    }

    // Two misses for one ref race: #1 suspends in decode, #2 decodes and publishes [2], then #1 finishes. UNDISPATCHED
    // plus the Unconfined dispatcher fix that interleaving. Mutation: drop the picture re-check under the lock → #1
    // deletes the published file with the song's other entries and publishes [1] → the bytes probe is RED.
    @Test
    fun get_concurrentMisses_publishOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val decoder = EmbeddedArtworkDecoder {
            calls++
            if (calls == 1) {
                gate.await()
                DecodedArtwork.Picture(byteArrayOf(1))
            } else {
                DecodedArtwork.Picture(byteArrayOf(2))
            }
        }
        val artworkCache = cache(decoder)
        val ref = SongArtworkRef(songId = 1L, version = 1L)
        var firstResult: File? = null

        val first = launch(start = CoroutineStart.UNDISPATCHED) { firstResult = artworkCache.get(ref) }
        val second = artworkCache.get(ref)
        gate.complete(Unit)
        first.join()

        val published = File(tempFolder.root, "1-1.jpg")
        assertEquals("the second miss returns the published file", published, second)
        assertEquals("the first miss returns the already published file", published, firstResult)
        assertArrayEquals("the first publisher's bytes stay", byteArrayOf(2), published.readBytes())
        assertEquals("one file, no temp left", listOf("1-1.jpg"), cachedNames())
    }

    private companion object {
        val BYTES_A = byteArrayOf(1, 2, 3)
        val BYTES_B = byteArrayOf(9, 8, 7, 6)
        const val BASE_TIME = 1_700_000_000_000L
        const val FUTURE_OFFSET_MS = 1_000_000L
        const val TEN_MB = 10 * 1024 * 1024
    }
}

/** Counts decodes, per song and in total; [resultFor] picks each song's result (or throws). */
private class FakeEmbeddedArtworkDecoder(
    private val resultFor: (Long) -> DecodedArtwork,
) : EmbeddedArtworkDecoder {

    private val calls = mutableMapOf<Long, Int>()

    val decodeCalls: Int get() = calls.values.sum()

    fun callsFor(songId: Long): Int = calls[songId] ?: 0

    override suspend fun decode(songId: Long): DecodedArtwork {
        calls[songId] = callsFor(songId) + 1
        return resultFor(songId)
    }
}
