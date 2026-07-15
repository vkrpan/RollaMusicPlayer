package com.rolla.musicplayer.core.data.artwork

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Real [ArtworkDecoder]: a two-pass [BitmapFactory] decode (bounds-only pass to read the source's
 * width/height without allocating pixels, then a sampled decode pass) mirroring
 * `:feature:widget`'s `WidgetArtworkLoader` -- `:core:data` cannot depend on `:feature:widget`, so
 * the approach is replicated here rather than shared. [sourceUri] is always a local `content://`
 * uri (e.g. `content://media/external/audio/albumart/{albumId}`), never a network URL, per the
 * app's offline contract; `content://` streams are single-use, so the uri is opened twice via
 * [ContentResolver.openInputStream], each in its own `.use { }`.
 *
 * Any failure -- an unopenable uri, bytes that don't decode as an image, a revoked grant -- returns
 * `null`. This class does not itself guard against [kotlinx.coroutines.CancellationException]; it
 * launches no coroutines of its own, so cancellation-safety is the caller's ([AlbumArtworkCacheImpl])
 * responsibility, matching `WidgetArtworkLoader`'s precedent of keeping the decode and the
 * try/catch in separate layers.
 */
class BitmapArtworkDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) : ArtworkDecoder {

    override suspend fun decode(sourceUri: String): ByteArray? {
        val uri = Uri.parse(sourceUri)
        val bitmap = decodeSampledBitmap(uri) ?: return null
        return try {
            compress(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = readBounds(resolver, uri) ?: return null
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, TARGET_MAX_EDGE_PX)
        }
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun readBounds(resolver: ContentResolver, uri: Uri): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val read = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return options.takeIf { read != null && it.outWidth > 0 && it.outHeight > 0 }
    }

    private fun compress(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return output.toByteArray()
    }

    private companion object {
        /** Big enough for now-playing/cards, small enough that list decodes stay cheap. */
        const val TARGET_MAX_EDGE_PX = 512
        const val JPEG_QUALITY = 85
    }
}

/**
 * Computes the smallest power-of-two [BitmapFactory.Options.inSampleSize] such that decoding a
 * [width]x[height] source downscales its longer edge to at or just above [targetMaxEdge] --
 * [BitmapFactory] only honors power-of-two sample sizes, so this doubles from `1` until halving
 * again would drop the longer edge below the target. Non-positive dimensions defensively return
 * `1` rather than looping or dividing by a non-positive number.
 */
internal fun calculateInSampleSize(width: Int, height: Int, targetMaxEdge: Int): Int {
    if (width <= 0 || height <= 0 || targetMaxEdge <= 0) return 1
    val longerEdge = maxOf(width, height)
    var sampleSize = 1
    while (longerEdge / (sampleSize * 2) >= targetMaxEdge) {
        sampleSize *= 2
    }
    return sampleSize
}
