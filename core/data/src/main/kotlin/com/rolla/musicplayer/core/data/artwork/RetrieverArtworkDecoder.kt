package com.rolla.musicplayer.core.data.artwork

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/**
 * Real [EmbeddedArtworkDecoder]: reads the picture embedded in the track's own file with
 * [MediaMetadataRetriever.getEmbeddedPicture] on the track's local `content://` uri (spec §5.1), never MediaStore's
 * per-album cover. No picture → [DecodedArtwork.None]. Otherwise a two-pass [BitmapFactory] decode (bounds first, then
 * a sampled decode) to about [TARGET_MAX_EDGE_PX] on the longer edge, re-compressed as JPEG at [JPEG_QUALITY].
 *
 * Throws when the file cannot be read ([MediaMetadataRetriever.setDataSource] fails), when its picture bytes do not
 * decode as an image, or when the JPEG re-encode fails ([toJpegBytes]), so the cache stores nothing and a later load
 * tries again (Review Focus 3). The cache guards cancellation; this class launches no coroutines.
 */
class RetrieverArtworkDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) : EmbeddedArtworkDecoder {

    override suspend fun decode(songId: Long): DecodedArtwork {
        val embedded = readEmbeddedPicture(songId) ?: return DecodedArtwork.None
        val bitmap = decodeSampledBitmap(embedded)
        return try {
            DecodedArtwork.Picture(bitmap.toJpegBytes(JPEG_QUALITY))
        } finally {
            bitmap.recycle()
        }
    }

    private fun readEmbeddedPicture(songId: Long): ByteArray? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(
                context,
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId),
            )
            return retriever.embeddedPicture
        } finally {
            retriever.release()
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Embedded picture has no readable image bounds")
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, TARGET_MAX_EDGE_PX)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("Embedded picture did not decode as an image")
    }

    private companion object {
        /** Big enough for Now Playing and cards, small enough that list decodes stay cheap. */
        const val TARGET_MAX_EDGE_PX = 512
        const val JPEG_QUALITY = 85
    }
}

/**
 * Re-compresses this bitmap as a JPEG at [quality]. Throws [IOException] when [Bitmap.compress] reports failure, so the
 * cache stores nothing and a later load tries again; ignoring the result would publish an empty `.jpg` that every
 * later load hits until the track's version changes or a Rescan.
 */
internal fun Bitmap.toJpegBytes(quality: Int): ByteArray {
    val output = ByteArrayOutputStream()
    if (!compress(Bitmap.CompressFormat.JPEG, quality, output)) {
        throw IOException("Embedded picture could not be re-encoded as JPEG")
    }
    return output.toByteArray()
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
