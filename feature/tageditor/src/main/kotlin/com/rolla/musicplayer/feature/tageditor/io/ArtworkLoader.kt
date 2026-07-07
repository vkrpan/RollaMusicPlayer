package com.rolla.musicplayer.feature.tageditor.io

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject

/**
 * Loads a user-picked LOCAL image (PhotoPicker / document uri) into a [PickedArtwork] ready for
 * [TagWriter.writeArtwork]. Local sources only -- this class never fetches anything remote, per
 * the app's offline contract.
 *
 * Returns null -- "not usable as artwork", surfaced by the caller as a friendly message, never a
 * crash -- when the uri can't be opened, the bytes don't decode as an image
 * ([BitmapFactory.decodeByteArray] bounds-only pass), or the file exceeds [MAX_ARTWORK_BYTES]
 * (embedding a multi-megabyte camera original would bloat every copy of the audio file; the cap
 * is generous enough for any real cover scan). The cap is enforced WHILE streaming: reading stops
 * as soon as the limit is crossed, so an arbitrarily large picked file (pre-13 devices fall back
 * to the unrestricted document picker) can never be fully buffered into memory, let alone OOM.
 *
 * The MIME type is taken from what the bytes actually decode as ([android.graphics.BitmapFactory.Options.outMimeType])
 * in preference to the provider's claim -- providers routinely report `application/octet-stream`
 * for perfectly good images, and the tag frame's declared type should describe the embedded
 * bytes, not the provider's guess.
 */
class ArtworkLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @TagEditorIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun load(imageUri: String): PickedArtwork? = withContext(ioDispatcher) {
        val uri = Uri.parse(imageUri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_ARTWORK_BYTES) }
            ?: return@withContext null
        if (bytes.size > MAX_ARTWORK_BYTES) return@withContext null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
        val mimeType = options.outMimeType ?: context.contentResolver.getType(uri) ?: FALLBACK_MIME_TYPE
        PickedArtwork(bytes = bytes, mimeType = mimeType, width = options.outWidth, height = options.outHeight)
    }

    /**
     * Reads until EOF or until strictly more than [limit] bytes have accumulated, whichever comes
     * first -- an over-limit source yields `limit < size <= limit + buffer` bytes, enough for the
     * caller's `size > limit` rejection without ever buffering the rest of the file.
     */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val accumulated = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (accumulated.size() <= limit) {
            val read = read(buffer)
            if (read == -1) break
            accumulated.write(buffer, 0, read)
        }
        return accumulated.toByteArray()
    }

    private companion object {
        const val MAX_ARTWORK_BYTES = 10 * 1024 * 1024
        const val COPY_BUFFER_BYTES = 8 * 1024
        const val FALLBACK_MIME_TYPE = "image/jpeg"
    }
}
