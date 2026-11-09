package com.rolla.musicplayer.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rolla.musicplayer.core.data.artwork.SongArtworkCache
import com.rolla.musicplayer.core.model.SongArtworkRef
import com.rolla.musicplayer.core.model.songArtworkUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [SongArtworkBitmapLoader], the Media3 [BitmapLoader] behind the notification, lock screen and media card artwork.
 * A per-track value (`rolla-artwork://song/{id}?v={version}`) is served from [SongArtworkCache], with the dark
 * white-note placeholder when the track has no usable picture or the uri is anything else (ruling P13). The resume
 * card's bytes are always the placeholder PNG (rulings P12 and P14). Native graphics, so `BitmapFactory` and the vector
 * drawable really draw pixels.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(UnstableApi::class)
class SongArtworkBitmapLoaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fallback = RecordingBitmapLoader()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun loader(cache: FakeSongArtworkCache) = SongArtworkBitmapLoader(context, cache, scope, fallback)

    // Control for the placeholder tests: a non-placeholder result is possible. Trivial stub (a 1×1 blank bitmap for
    // every uri) → RED at the width. Mutation: drop the decode (`cache.get(ref)?.let { null }`) → the 512 px
    // placeholder → RED at the width.
    @Test
    fun songRef_withPicture_returnsTheDecodedFile() {
        val red = pngFile("red.png", Color.RED)
        val cache = FakeSongArtworkCache { ref -> red.takeIf { ref == SongArtworkRef(1L, 1L) } }

        val bitmap = awaitBitmap(loader(cache).loadBitmap(songArtworkUri("1", 1L).toUri()))

        assertNotNull("the loader must answer with a bitmap", bitmap)
        assertEquals("the decoded file's width", 4, bitmap!!.width)
        assertEquals("pixel (2,2) is the file's red", Color.RED, bitmap.getPixel(2, 2))
        assertEquals("the cache is asked for the parsed ref", listOf(SongArtworkRef(1L, 1L)), cache.getCalls)
    }

    // Trivial stub → a 1×1 transparent bitmap → RED at the size. Mutations: background 0xFF000000 → RED at (2,2); skip
    // drawing the note → RED at the centre; side 256 → RED at the size.
    @Test
    fun songRef_noPicture_returnsThePlaceholder() {
        val cache = FakeSongArtworkCache { null }

        val bitmap = awaitBitmap(loader(cache).loadBitmap(songArtworkUri("1", 1L).toUri()))

        assertPlaceholder(bitmap)
    }

    // Ruling P9: SongArtworkCache.get can throw (a disk write failure). Trivial stub → RED at the size. Mutation: drop
    // the `catch (e: Exception)` → the job fails, the future carries the exception → no bitmap → RED at assertNotNull.
    @Test
    fun songRef_getThrows_returnsThePlaceholder() {
        val cache = FakeSongArtworkCache { throw IOException("disk full") }

        val bitmap = awaitBitmap(loader(cache).loadBitmap(songArtworkUri("1", 1L).toUri()))

        assertPlaceholder(bitmap)
    }

    // A cache file BitmapFactory cannot decode (decodeFile returns null) shows the placeholder too. Trivial stub → RED
    // at the size. Mutation: turn a null decode into a blank bitmap (`decodeFile(path) ?: createBitmap(1, 1)`) → RED.
    @Test
    fun songRef_undecodableFile_returnsThePlaceholder() {
        val corrupt = temp.newFile("corrupt.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val cache = FakeSongArtworkCache { corrupt }

        val bitmap = awaitBitmap(loader(cache).loadBitmap(songArtworkUri("1", 1L).toUri()))

        assertPlaceholder(bitmap)
    }

    // Ruling P13: anything that is not a per-track value (here MediaStore's album-art uri, which the Global Constraint
    // forbids reading) answers the placeholder without reading anything: neither the fallback nor the cache is asked.
    // Trivial stub → RED at the size. Mutation: restore `?: return fallback.loadBitmap(uri)` → the fallback is asked
    // (and answers 1×1) → RED.
    @Test
    fun otherUri_returnsThePlaceholderWithoutReading() {
        val cache = FakeSongArtworkCache { error("a non-ref uri must not reach the cache") }
        val uri = "content://media/external/audio/albumart/5".toUri()

        val bitmap = awaitBitmap(loader(cache).loadBitmap(uri))

        assertTrue("the fallback is never asked", fallback.loadedUris.isEmpty())
        assertTrue("the cache is never asked", cache.getCalls.isEmpty())
        assertPlaceholder(bitmap)
    }

    // decodeBitmap still delegates (Media3 decodes artworkData through it). Trivial stub: n/a (delegation is the
    // stub's own behaviour); mutation: answer decodeBitmap with the placeholder → RED.
    @Test
    fun decodeBitmap_delegatesToFallback() {
        val data = byteArrayOf(9, 8, 7)

        val future = loader(FakeSongArtworkCache { null }).decodeBitmap(data)

        assertSame("the fallback's future is returned as is", fallback.lastDecodeFuture, future)
        assertEquals("the fallback gets the bytes", listOf(data.toList()), fallback.decoded.map { it.toList() })
    }

    // The placeholder is one shared instance, so it must be immutable. Trivial stub's blank bitmap is mutable → RED.
    // Mutation: return the drawn bitmap without the immutable copy → RED.
    @Test
    fun placeholder_isImmutable() {
        val bitmap = awaitBitmap(loader(FakeSongArtworkCache { null }).loadBitmap(songArtworkUri("1", 1L).toUri()))

        assertPlaceholder(bitmap)
        assertFalse("the shared placeholder cannot be drawn on", bitmap!!.isMutable)
    }

    // Rulings P12 and P14: the resume card's bytes are the placeholder as PNG, even though every track here has a
    // picture, and the cache is never asked. Mutations: encode the placeholder as JPEG → RED at the PNG signature;
    // answer an empty array → RED at the signature size.
    @Test
    fun resumeCardArtworkData_isThePlaceholderPngAndNeverAsksTheCache() = runBlocking {
        val red = pngFile("red.png", Color.RED)
        val cache = FakeSongArtworkCache { red }

        val bytes = loader(cache).resumeCardArtworkData()

        assertPlaceholderPng(bytes)
        assertTrue("the cache is never asked", cache.getCalls.isEmpty())
    }

    // The future/job bridge: a scope already cancelled (the service is gone) gives a cancelled future, never one
    // left pending. Trivial stub (an immediate bitmap) → not cancelled → RED. Mutation: drop the
    // `is CancellationException -> future.cancel(false)` arm → the future stays pending → RED.
    @Test
    fun loadBitmap_cancelledScope_returnsACancelledFuture() {
        val cancelledScope = CoroutineScope(Job().apply { cancel() })
        val loader = SongArtworkBitmapLoader(context, FakeSongArtworkCache { null }, cancelledScope, fallback)

        val future = loader.loadBitmap(songArtworkUri("1", 1L).toUri())
        runCatching { future.get(BRIDGE_TIMEOUT_S, TimeUnit.SECONDS) }

        assertTrue("the future is cancelled", future.isCancelled)
    }

    // The bridge the other way: a caller cancelling the returned future cancels the load (a suspending cache sees
    // it). Trivial stub → the cache is never entered → RED at "started". Mutation: drop the future listener that
    // cancels the job → the load keeps waiting → RED at "cancelled".
    @Test
    fun cancellingTheFuture_cancelsTheLoad() {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val cache = object : SongArtworkCache {
            override suspend fun get(ref: SongArtworkRef): File? {
                started.countDown()
                try {
                    awaitCancellation()
                } finally {
                    cancelled.countDown()
                }
            }

            override fun peek(ref: SongArtworkRef): File? = error("unused")

            override suspend fun invalidate(songId: Long) = error("unused")

            override suspend fun clear() = error("unused")
        }

        val loader = SongArtworkBitmapLoader(context, cache, scope, fallback)
        val future = loader.loadBitmap(songArtworkUri("1", 1L).toUri())
        assertTrue("the load started", started.await(BRIDGE_TIMEOUT_S, TimeUnit.SECONDS))
        future.cancel(false)

        assertTrue("the load was cancelled", cancelled.await(BRIDGE_TIMEOUT_S, TimeUnit.SECONDS))
    }

    private fun assertPlaceholderPng(bytes: ByteArray) {
        assertTrue("PNG signature", bytes.size > PNG_SIGNATURE.size)
        assertArrayEquals("PNG signature", PNG_SIGNATURE, bytes.copyOf(PNG_SIGNATURE.size))
        assertPlaceholder(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    private fun assertPlaceholder(bitmap: Bitmap?) {
        assertNotNull("the loader must answer with a bitmap", bitmap)
        assertEquals("placeholder width", PLACEHOLDER_SIDE, bitmap!!.width)
        assertEquals("placeholder height", PLACEHOLDER_SIDE, bitmap.height)
        assertEquals("pixel (2,2) is artworkPlaceholderLarge", PLACEHOLDER_BACKGROUND, bitmap.getPixel(2, 2))
        assertEquals(
            "the centre pixel is the note in artworkPlaceholderGlyph",
            PLACEHOLDER_GLYPH,
            bitmap.getPixel(PLACEHOLDER_SIDE / 2, PLACEHOLDER_SIDE / 2),
        )
    }

    private fun pngFile(name: String, color: Int): File {
        val file = temp.newFile(name)
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /** A [BitmapLoader] that records every uri and byte array it is asked for and answers with a 1×1 bitmap. */
    private class RecordingBitmapLoader : BitmapLoader {
        val loadedUris: MutableList<Uri> = CopyOnWriteArrayList()
        val decoded: MutableList<ByteArray> = CopyOnWriteArrayList()
        var lastDecodeFuture: ListenableFuture<Bitmap>? = null

        override fun supportsMimeType(mimeType: String): Boolean = true

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            decoded += data
            return Futures.immediateFuture(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
                .also { lastDecodeFuture = it }
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            loadedUris += uri
            return Futures.immediateFuture(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        }
    }

    private companion object {
        const val BRIDGE_TIMEOUT_S = 10L
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        const val PLACEHOLDER_SIDE = 512
        const val PLACEHOLDER_BACKGROUND = 0xFF3B3B3B.toInt()
        const val PLACEHOLDER_GLYPH = 0xFFFCFCFE.toInt()
    }
}

/** The future's bitmap, or null when it failed or did not complete within ten seconds. */
internal fun awaitBitmap(future: ListenableFuture<Bitmap>): Bitmap? =
    runCatching { future.get(10L, TimeUnit.SECONDS) }.getOrNull()
