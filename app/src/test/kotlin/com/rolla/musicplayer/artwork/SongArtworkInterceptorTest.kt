package com.rolla.musicplayer.artwork

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.annotation.ExperimentalCoilApi
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.size.Size
import com.rolla.musicplayer.core.data.artwork.SongArtworkCache
import com.rolla.musicplayer.core.model.SongArtworkRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * [SongArtworkInterceptor]'s routing: a per-track artwork value resolves through [SongArtworkCache] (`peek` first,
 * `get` on a peek miss) and proceeds with the cached file; a track without a picture, or a cache that throws, returns
 * an [ErrorResult] without proceeding (the placeholder under `AsyncImage` shows); any other data proceeds unchanged.
 * The cache and the chain are hand-written fakes; [ImageRequest] is real (it needs a Context, hence Robolectric).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SongArtworkInterceptorTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    // Mutation (T3-1b): drop peek, always call get (`Result.success(cache.get(ref))`) → get answers with the same file,
    // so the request is still rewritten, but get was called → RED on "a peek hit never calls get".
    @Test
    fun hit_peek_rewritesDataToTheFile() = runTest {
        val file = File("/cache/1-100.jpg")
        val cache = FakeSongArtworkCache(peeked = mapOf(1L to file), got = mapOf(1L to file))
        val chain = RecordingChain(requestFor("rolla-artwork://song/1?v=100"))

        SongArtworkInterceptor(cache).intercept(chain)

        assertEquals("the chain proceeds once", 1, chain.proceeded.size)
        assertEquals("the proceeded request loads the cached file", file, chain.proceeded.single().data)
        assertEquals("a peek hit never calls get", emptyList<SongArtworkRef>(), cache.getCalls)
        assertEquals("peek is asked for the parsed ref", listOf(SongArtworkRef(1L, 100L)), cache.peekCalls)
    }

    // Mutation (T3-2): skip get on a peek miss (`Result.success(cache.peek(ref))`) → get is never asked
    // → RED on "a peek miss falls back to get".
    @Test
    fun miss_get_rewritesDataToTheFile() = runTest {
        val file = File("/cache/2-200.jpg")
        val cache = FakeSongArtworkCache(got = mapOf(2L to file))
        val chain = RecordingChain(requestFor("rolla-artwork://song/2?v=200"))

        SongArtworkInterceptor(cache).intercept(chain)

        assertEquals("a peek miss falls back to get", listOf(SongArtworkRef(2L, 200L)), cache.getCalls)
        assertEquals("the proceeded request loads the decoded file", file, chain.proceeded.single().data)
    }

    // Mutation (T3-3): the no-picture branch returns `chain.proceed(request)` instead of the ErrorResult → the second
    // chain proceeds → RED on "no picture never proceeds".
    @Test
    fun noPicture_returnsErrorWithoutProceeding() = runTest {
        val cache = FakeSongArtworkCache(peeked = mapOf(1L to File("/cache/1-100.jpg")))
        val chain = RecordingChain(requestFor("rolla-artwork://song/1?v=100"))
        val interceptor = SongArtworkInterceptor(cache)
        interceptor.intercept(chain)
        assertEquals("control: a hit proceeds, so proceed is observable", 1, chain.proceeded.size)

        val noPictureChain = RecordingChain(requestFor("rolla-artwork://song/2?v=200"))
        val result = interceptor.intercept(noPictureChain)

        assertEquals("get is asked for the track without a picture", listOf(SongArtworkRef(2L, 200L)), cache.getCalls)
        assertEquals("no picture never proceeds", 0, noPictureChain.proceeded.size)
        assertTrue(
            "no picture returns the interceptor's own NoArtworkException error, got $result",
            (result as? ErrorResult)?.throwable is NoArtworkException,
        )
    }

    // Mutation (T3-4): a lenient parse that also takes any uri's last path segment as a song id (the old album
    // interceptor's rule) → the album uri peeks song 1 and is rewritten → RED on "a MediaStore album uri proceeds
    // unchanged".
    @Test
    fun mediaStoreAlbumUri_isNotIntercepted() = runTest {
        val cache = FakeSongArtworkCache(peeked = mapOf(1L to File("/cache/1-100.jpg")))
        val interceptor = SongArtworkInterceptor(cache)
        val refChain = RecordingChain(requestFor("rolla-artwork://song/1?v=100"))
        interceptor.intercept(refChain)
        assertEquals("control: a ref touches the cache", 1, cache.peekCalls.size)

        val albumArtRequest = requestFor("content://media/external/audio/albumart/1")
        val albumArtChain = RecordingChain(albumArtRequest)
        interceptor.intercept(albumArtChain)

        assertSame("a MediaStore album uri proceeds unchanged", albumArtRequest, albumArtChain.proceeded.single())
        assertEquals("a MediaStore album uri never peeks", 1, cache.peekCalls.size)
        assertEquals("a MediaStore album uri never gets", emptyList<SongArtworkRef>(), cache.getCalls)
    }

    // Mutation (T3-5): drop cachedFile's non-cancellation catch → the IOException escapes intercept
    // → RED on "a throwing cache must not escape intercept".
    @Test
    fun getThrows_returnsErrorWithoutProceeding() = runTest {
        val cache = FakeSongArtworkCache(
            peeked = mapOf(1L to File("/cache/1-100.jpg")),
            getFailure = IOException("disk full"),
        )
        val interceptor = SongArtworkInterceptor(cache)
        val hitChain = RecordingChain(requestFor("rolla-artwork://song/1?v=100"))
        interceptor.intercept(hitChain)
        assertEquals("control: a hit proceeds, so proceed is observable", 1, hitChain.proceeded.size)

        val throwingChain = RecordingChain(requestFor("rolla-artwork://song/2?v=200"))
        val outcome = runCatching { interceptor.intercept(throwingChain) }

        assertTrue("a throwing cache must not escape intercept, got $outcome", outcome.isSuccess)
        assertEquals("a throwing cache never proceeds", 0, throwingChain.proceeded.size)
        assertTrue(
            "a throwing cache returns the NoArtworkException error, got ${outcome.getOrNull()}",
            (outcome.getOrNull() as? ErrorResult)?.throwable is NoArtworkException,
        )
    }

    private fun requestFor(data: Any): ImageRequest = ImageRequest.Builder(context).data(data).build()

    /** Records every request the interceptor proceeds with; the terminal result is irrelevant to these tests. */
    private class RecordingChain(override val request: ImageRequest) : Interceptor.Chain {
        val proceeded = mutableListOf<ImageRequest>()
        override val size: Size = Size.ORIGINAL
        override fun withSize(size: Size): Interceptor.Chain = this

        @OptIn(ExperimentalCoilApi::class)
        override fun withRequest(request: ImageRequest): Interceptor.Chain = RecordingChain(request)
        override suspend fun proceed(request: ImageRequest): ImageResult {
            proceeded += request
            return ErrorResult(null, request, IllegalStateException("terminal"))
        }
    }

    /** [peeked] and [got] answer by song id; [getFailure], when set, is thrown by every `get` for a song not peeked. */
    private class FakeSongArtworkCache(
        private val peeked: Map<Long, File> = emptyMap(),
        private val got: Map<Long, File> = emptyMap(),
        private val getFailure: Exception? = null,
    ) : SongArtworkCache {
        val peekCalls = mutableListOf<SongArtworkRef>()
        val getCalls = mutableListOf<SongArtworkRef>()

        override fun peek(ref: SongArtworkRef): File? {
            peekCalls += ref
            return peeked[ref.songId]
        }

        override suspend fun get(ref: SongArtworkRef): File? {
            getCalls += ref
            getFailure?.let { throw it }
            return got[ref.songId]
        }

        override suspend fun invalidate(songId: Long) = error("unused")

        override suspend fun clear() = error("unused")
    }
}
