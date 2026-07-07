package com.rolla.musicplayer.feature.tageditor.io

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
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
 * is generous enough for any real cover scan).
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
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext null
        if (bytes.size > MAX_ARTWORK_BYTES) return@withContext null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
        val mimeType = options.outMimeType ?: context.contentResolver.getType(uri) ?: FALLBACK_MIME_TYPE
        PickedArtwork(bytes = bytes, mimeType = mimeType, width = options.outWidth, height = options.outHeight)
    }

    private companion object {
        const val MAX_ARTWORK_BYTES = 10 * 1024 * 1024
        const val FALLBACK_MIME_TYPE = "image/jpeg"
    }
}
