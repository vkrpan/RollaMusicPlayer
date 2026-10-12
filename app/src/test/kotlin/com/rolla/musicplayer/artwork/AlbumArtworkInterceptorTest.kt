package com.rolla.musicplayer.artwork

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCache
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * [AlbumArtworkInterceptor]'s routing: a cover already on disk ([AlbumArtworkCache.peek] hit) resolves without the
 * suspending, IO-hopping [AlbumArtworkCache.get]; a peek miss falls back to `get`; anything that isn't an albumart
 * uri touches neither. The cache and the chain are MockK mocks; [ImageRequest] is real (it needs a Context, hence
 * Robolectric).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AlbumArtworkInterceptorTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    // Mutation: drop the peek() call (always get) → get is called on a peek hit → RED.
    @Test
    fun `a peek hit never calls get`() = runTest {
        val file = File("/cache/1.jpg")
        val cache = mockk<AlbumArtworkCache> { every { peek(1L) } returns file }
        val proceeded = slot<ImageRequest>()
        val chain = chainFor(albumArtRequest(albumId = 1L), proceeded)

        AlbumArtworkInterceptor(cache).intercept(chain)

        coVerify(exactly = 0) { cache.get(any(), any()) }
        assertEquals(file, proceeded.captured.data)
    }

    // Mutations: (a) on a peek miss, proceed with the original request (skip get) → data stays the content uri → RED;
    // (b) pass albumId.toString() as get's source → the strict mock has no answer for it → MockKException → RED.
    @Test
    fun `a peek miss falls back to get`() = runTest {
        val file = File("/cache/2.jpg")
        val cache = mockk<AlbumArtworkCache> {
            every { peek(2L) } returns null
            coEvery { get(2L, "content://media/external/audio/albumart/2") } returns file
        }
        val proceeded = slot<ImageRequest>()

        AlbumArtworkInterceptor(cache).intercept(chainFor(albumArtRequest(albumId = 2L), proceeded))

        assertEquals(file, proceeded.captured.data)
    }

    // Mutation: drop the prefix check (treat any data as album art) → peek is called for a file path → RED.
    @Test
    fun `non-albumart data touches neither peek nor get`() = runTest {
        val cache = mockk<AlbumArtworkCache>()
        val proceeded = slot<ImageRequest>()

        AlbumArtworkInterceptor(cache).intercept(chainFor(requestFor("content://media/external/images/9"), proceeded))

        verify(exactly = 0) { cache.peek(any()) }
        coVerify(exactly = 0) { cache.get(any(), any()) }
    }

    private fun albumArtRequest(albumId: Long): ImageRequest =
        requestFor("content://media/external/audio/albumart/$albumId")

    private fun requestFor(data: Any): ImageRequest = ImageRequest.Builder(context).data(data).build()

    private fun chainFor(request: ImageRequest, proceeded: CapturingSlot<ImageRequest>): Interceptor.Chain =
        mockk {
            every { this@mockk.request } returns request
            coEvery { proceed(capture(proceeded)) } returns mockk<ErrorResult>()
        }
}
