package com.rolla.musicplayer.feature.tageditor.io

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Resolves an editable local [File] for a [Song] known only by its content:// uri, and streams
 * edits back to that uri.
 *
 * This is the ONLY class in the tag-IO layer that touches Android framework I/O types
 * ([ContentResolver], [Uri]) -- [TagReader]/[TagWriter] work on a plain [File] so they stay
 * JVM-testable without Robolectric. Per the project's write strategy (copy-through-cache):
 * jaudiotagger's [org.jaudiotagger.audio.AudioFile.commit] writes a sibling temp file and renames
 * it, which fails on a content://-only, no-path media file in scoped storage even after write
 * consent is granted. So this class never asks jaudiotagger to touch the real file directly --
 * it copies the song's bytes into an app-cache file first ([createEditableCopy]), the caller edits
 * that file with [TagWriter], and [persistEditedCopy] streams the edited bytes back over the uri.
 * This same pipeline is used at every SDK level; only the consent mechanism that must precede
 * [persistEditedCopy] differs, and that consent flow is intentionally NOT implemented here -- it
 * belongs to a later step (MediaWriteRequester, per the tag-editor skill).
 */
class SongFileResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    @TagEditorIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * Copies [song]'s bytes from its content:// uri into a fresh temp file under
     * [EDITABLE_COPY_CACHE_DIR_NAME] in the app cache dir, preserving the original file extension
     * -- jaudiotagger's [org.jaudiotagger.audio.AudioFileIO.read]/commit sniff the format from the
     * file extension, so a wrong or missing one picks the wrong reader/writer entirely.
     *
     * The extension is derived from [ContentResolver.getType]'s MIME type via [MimeTypeMap]
     * first; if that can't be mapped (some OEM providers return a generic
     * application/octet-stream), it falls back to the extension on the DISPLAY_NAME column. This
     * deliberately never queries the deprecated DATA column for this purpose -- that query is
     * reserved for [resolveFilePath], which exists only for the post-write re-index/scanner step,
     * not for format sniffing.
     *
     * Caller owns the returned file: delete it with [deleteEditableCopy] once the edit is
     * committed (via [persistEditedCopy]) or abandoned.
     */
    suspend fun createEditableCopy(song: Song): File = withContext(ioDispatcher) {
        val uri = Uri.parse(song.contentUri)
        val target = File(editableCopyCacheDir(), "${song.id}_${System.currentTimeMillis()}.${resolveExtension(uri)}")
        val input = resolver.openInputStream(uri) ?: error("Unable to open input stream for $uri")
        input.use { source -> FileOutputStream(target).use { output -> source.copyTo(output) } }
        target
    }

    /**
     * Streams [editedCopy]'s bytes back over [song]'s content:// uri using
     * openOutputStream(uri, "wt") -- the "wt" mode truncates the existing content first, since the
     * edited copy is very often a different size than the original (tag frames grow or shrink).
     *
     * This method performs NO consent handling and assumes it is only called after write consent
     * has already been granted for [song]'s uri; calling it before that throws:
     *  - API 30+ (R): a plain SecurityException -- the write request
     *    (MediaStore.createWriteRequest) must be granted first.
     *  - API 29 (Q): android.app.RecoverableSecurityException -- the caller must catch it, launch
     *    userAction.actionIntent.intentSender, and retry this call once the user grants it.
     *  - API <=28: requires the (separately-granted, runtime) WRITE_EXTERNAL_STORAGE permission;
     *    no exception dance needed.
     * All of the above is a later step's responsibility (MediaWriteRequester); this method just
     * performs the write and lets any security exception propagate untouched.
     */
    suspend fun persistEditedCopy(song: Song, editedCopy: File): Unit = withContext(ioDispatcher) {
        val uri = Uri.parse(song.contentUri)
        val output = resolver.openOutputStream(uri, "wt") ?: error("Unable to open output stream for $uri")
        output.use { sink -> editedCopy.inputStream().use { source -> source.copyTo(sink) } }
    }

    /**
     * Best-effort lookup of [song]'s on-disk path, needed only so a caller can hand a real path to
     * MediaScannerConnection.scanFile after a successful write (see the tag-editor skill's
     * re-index step). Returns null when the row has no path -- some OEM providers omit it, or the
     * file may since have been removed -- callers must treat that as "skip the scanner call", not
     * a failure.
     *
     * Queries the deprecated MediaStore.MediaColumns.DATA column directly. It still works (only
     * deprecated, not removed) and is far simpler than reconstructing a path from volume/uri APIs
     * across every supported SDK level for what is a best-effort convenience here. The
     * deprecation suppression is scoped to this one function rather than the whole class/module.
     */
    @Suppress("DEPRECATION")
    suspend fun resolveFilePath(song: Song): String? = withContext(ioDispatcher) {
        val uri = Uri.parse(song.contentUri)
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (columnIndex < 0 || !cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(columnIndex)?.takeIf { it.isNotBlank() }
            }
        }
    }

    /** Deletes a temp file created by [createEditableCopy]. Safe to call even if already gone. */
    suspend fun deleteEditableCopy(file: File): Unit = withContext(ioDispatcher) {
        file.delete()
        Unit
    }

    private fun editableCopyCacheDir(): File = File(context.cacheDir, EDITABLE_COPY_CACHE_DIR_NAME).apply { mkdirs() }

    private fun resolveExtension(uri: Uri): String {
        val mimeType = resolver.getType(uri)
        val fromMimeType = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return fromMimeType ?: resolveExtensionFromDisplayName(uri) ?: DEFAULT_EXTENSION
    }

    private fun resolveExtensionFromDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex < 0 || !cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(columnIndex)
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.takeIf { it.isNotBlank() }
            }
        }
    }

    private companion object {
        // Cache subdirectory holding in-progress tag edits; contract: the caller deletes when done.
        const val EDITABLE_COPY_CACHE_DIR_NAME = "tag_editor_edits"
        const val DEFAULT_EXTENSION = "mp3"
    }
}
