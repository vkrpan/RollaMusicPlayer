package com.rolla.musicplayer.core.data.scanner

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.rolla.musicplayer.core.database.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Gives [FileTagPass] a song's file to read. A seam so the pass is testable without a ContentResolver; the app binds
 * [AndroidSongFileSource].
 */
interface SongFileSource {

    /**
     * The song file's extension (`"mp3"`, as the file name spells it), or null when the row has neither a display name
     * nor a path with one. Lets a caller skip a format without opening the file.
     */
    suspend fun extensionOf(song: SongEntity): String?

    /**
     * Runs [block] on a readable local [File] holding [song]'s bytes, under the file's own extension (jaudiotagger
     * picks its reader from it), and returns its result. The file is valid only inside [block].
     *
     * @throws Exception when the file cannot be opened (missing, unreadable, or the row is gone).
     */
    suspend fun <T> withReadableFile(song: SongEntity, block: (File) -> T): T
}

/** How [AndroidSongFileSource] reaches a song's bytes. */
internal enum class ReadMode {
    /** Read MediaStore's `DATA` file where it is. */
    IN_PLACE,

    /** Copy the content uri's bytes into the app cache and read the copy. */
    COPY,
}

/**
 * Reads the `DATA` path in place on API 28 and below, and on 30+ where the read permission (or all-files access)
 * makes the path readable; copies otherwise. API 29 always copies: without `requestLegacyExternalStorage`, which this
 * app does not set, scoped storage blocks plain file IO on shared-storage paths there.
 */
internal fun chooseReadMode(sdk: Int, dataPath: String?, canRead: Boolean): ReadMode = when {
    sdk == Build.VERSION_CODES.Q -> ReadMode.COPY
    dataPath != null && canRead -> ReadMode.IN_PLACE
    else -> ReadMode.COPY
}

/**
 * [SongFileSource] over MediaStore. The extension comes from the row's `DISPLAY_NAME`, else from its `DATA` path. A
 * copy ([ReadMode.COPY]) goes to `cacheDir/file_tag_reads/<id>.<ext>` and is deleted once [withReadableFile]'s block
 * returns or throws. Read-only: nothing here ever writes the song's file.
 */
class AndroidSongFileSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SongFileSource {

    override suspend fun extensionOf(song: SongEntity): String? = withContext(Dispatchers.IO) {
        queryRow(song).extension
    }

    override suspend fun <T> withReadableFile(song: SongEntity, block: (File) -> T): T = withContext(Dispatchers.IO) {
        val row = queryRow(song)
        val dataFile = row.dataPath?.let(::File)
        val mode = chooseReadMode(Build.VERSION.SDK_INT, row.dataPath, dataFile?.canRead() == true)
        if (mode == ReadMode.IN_PLACE && dataFile != null) {
            block(dataFile)
        } else {
            val extension = row.extension ?: error("No extension for song ${song.id}")
            val copy = File(File(context.cacheDir, COPY_DIR_NAME).apply { mkdirs() }, "${song.id}.$extension")
            try {
                val input = context.contentResolver.openInputStream(Uri.parse(song.contentUri))
                    ?: error("Unable to open ${song.contentUri}")
                input.use { source -> FileOutputStream(copy).use { output -> source.copyTo(output) } }
                block(copy)
            } finally {
                copy.delete()
            }
        }
    }

    @Suppress("DEPRECATION") // DATA is deprecated, not removed; it is the only way to a file path to read in place.
    private fun queryRow(song: SongEntity): SongFileRow {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA)
        val cursor = context.contentResolver.query(Uri.parse(song.contentUri), projection, null, null, null)
            ?: error("No MediaStore row for song ${song.id}")
        return cursor.use {
            if (!it.moveToFirst()) error("No MediaStore row for song ${song.id}")
            SongFileRow(
                displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)),
                dataPath = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                    ?.takeIf { path -> path.isNotBlank() },
            )
        }
    }

    private data class SongFileRow(val displayName: String?, val dataPath: String?) {
        val extension: String?
            get() = displayName?.extensionOrNull() ?: dataPath?.extensionOrNull()
    }

    private companion object {
        // Cache subdirectory for the temporary copies; each is deleted after its read.
        const val COPY_DIR_NAME = "file_tag_reads"
    }
}

private fun String.extensionOrNull(): String? =
    substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }
