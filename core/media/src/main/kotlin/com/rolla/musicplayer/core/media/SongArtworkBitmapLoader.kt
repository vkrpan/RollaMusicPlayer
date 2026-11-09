package com.rolla.musicplayer.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.rolla.musicplayer.core.data.artwork.SongArtworkCache
import com.rolla.musicplayer.core.model.SongArtworkRef
import com.rolla.musicplayer.core.model.parseSongArtworkUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * The Media3 [BitmapLoader] `PlaybackService`'s session uses for the notification, the lock screen and the System UI
 * media card (spec §5.1): each track shows the picture embedded in its own file, or the white-note placeholder.
 *
 * A per-track value (`rolla-artwork://song/{id}?v={version}`, [parseSongArtworkUri]) is answered from [cache]: the
 * cached, downscaled JPEG is decoded on [Dispatchers.IO] in [scope] (the service's scope). A track without a picture, a
 * cache file `BitmapFactory` cannot decode, and a cache that throws (a disk write failure, ruling P9) all answer with
 * the placeholder; cancellation is rethrown. Any other uri also answers the placeholder without reading anything
 * (ruling P13: a track never shows a picture that is not in its own file). [decodeBitmap] and [supportsMimeType]
 * delegate to [fallback].
 *
 * [resumeCardArtworkData] is the resume media card's picture as bytes (ruling P12: Media3's legacy browse path decodes
 * only `MediaMetadata.artworkData`, never a uri). By user decision (ruling P14) it is ALWAYS the placeholder, never a
 * track's own picture, so a user's picture is never handed to other apps through the recent root; it never asks
 * [cache].
 *
 * Media3 wraps this loader in its `CacheBitmapLoader`, which remembers the last uri's future. A new version of a track
 * is a new value (`v=` follows the file's `date_modified`), so a changed picture always reaches this loader.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SongArtworkBitmapLoader(
    private val context: Context,
    private val cache: SongArtworkCache,
    private val scope: CoroutineScope,
    private val fallback: BitmapLoader = DataSourceBitmapLoader.Builder(context).build(),
) : BitmapLoader {

    /** Drawn once, on first use (an IO thread), immutable, and shared by every track without a picture. */
    private val placeholder: Bitmap by lazy { drawPlaceholder() }

    /** [placeholder] encoded as PNG, once, for [resumeCardArtworkData]. */
    private val placeholderPng: ByteArray by lazy {
        ByteArrayOutputStream().use { out ->
            check(placeholder.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "Placeholder PNG failed" }
            out.toByteArray()
        }
    }

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val ref = parseSongArtworkUri(uri.toString())
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch(Dispatchers.IO) { future.set(bitmapFor(ref)) }
        // A scope cancelled before or during the load (the service is going away) cancels the future, and anything
        // the body did not catch fails it, so a caller never waits forever. A caller that cancels stops the load.
        job.invokeOnCompletion { cause ->
            when (cause) {
                null -> Unit
                is CancellationException -> future.cancel(false)
                else -> future.setException(cause)
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return future
    }

    /**
     * The resume card's `artworkData`: [placeholder] as PNG, for every track (ruling P14, user decision: the card
     * always shows the white note, never the track's own picture). Never asks [cache]. On [Dispatchers.IO], because
     * the first call draws and encodes the placeholder.
     */
    suspend fun resumeCardArtworkData(): ByteArray = withContext(Dispatchers.IO) { placeholderPng }

    /** [ref]'s picture, or [placeholder] when there is no ref, no picture, its file does not decode, or a failure. */
    private suspend fun bitmapFor(ref: SongArtworkRef?): Bitmap {
        val decoded = ref?.let { r -> orNullOnFailure(r) { cache.get(r)?.let { BitmapFactory.decodeFile(it.path) } } }
        return decoded ?: placeholder
    }

    /** [block]'s result, or null when it throws anything but cancellation (ruling P9). */
    private suspend fun <T> orNullOnFailure(ref: SongArtworkRef, block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Artwork for song ${ref.songId} failed; showing the placeholder", e)
            null
        }

    /**
     * Now Playing's dark large placeholder as a [PLACEHOLDER_SIZE_PX] square (ruling R4): `artworkPlaceholderLarge`
     * with the note in `artworkPlaceholderGlyph` (the drawable's own colour, 10.93:1 on the background), centred at
     * Now Playing's glyph-to-artwork ratio, `npArtGlyph / npArtSize`.
     */
    private fun drawPlaceholder(): Bitmap {
        val bitmap = Bitmap.createBitmap(PLACEHOLDER_SIZE_PX, PLACEHOLDER_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PLACEHOLDER_BACKGROUND)
        val glyph = checkNotNull(ContextCompat.getDrawable(context, R.drawable.artwork_placeholder_note))
        val glyphPx = (PLACEHOLDER_SIZE_PX * NP_ART_GLYPH_DP / NP_ART_SIZE_DP).roundToInt()
        val inset = (PLACEHOLDER_SIZE_PX - glyphPx) / 2
        glyph.setBounds(inset, inset, inset + glyphPx, inset + glyphPx)
        glyph.draw(canvas)
        // Shared with every consumer (Media3, the notification), so nobody may draw on it.
        return bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
    }

    private companion object {
        const val TAG = "SongArtworkBitmapLoader"

        /** Ignored for PNG (lossless); the API requires a value. */
        const val PNG_QUALITY = 100

        /** The placeholder's side: the size of the cache's downscaled pictures. */
        const val PLACEHOLDER_SIZE_PX = 512

        /** `artworkPlaceholderLarge`, dark theme (`Color.kt`). */
        const val PLACEHOLDER_BACKGROUND = 0xFF3B3B3B.toInt()

        /** `RollaDimens.npArtGlyph` and `RollaDimens.npArtSize` (`Dimens.kt`): 72 / 168 of the side, 219 px of 512. */
        const val NP_ART_GLYPH_DP = 72f
        const val NP_ART_SIZE_DP = 168f
    }
}
