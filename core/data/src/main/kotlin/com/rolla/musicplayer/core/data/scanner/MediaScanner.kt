package com.rolla.musicplayer.core.data.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    internal suspend fun scan(): List<ScannedSong> = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            MediaStoreColumns.COLLECTION,
            MediaStoreColumns.PROJECTION,
            MediaStoreColumns.SELECTION,
            null,
            MediaStoreColumns.SORT_ORDER,
        )?.use { cursor -> readSongs(cursor) } ?: emptyList()
    }

    /**
     * Scoped scan used for a targeted re-sync of specific MediaStore rows (e.g. right after the tag
     * editor writes tags and re-indexes just the files it touched). Same projection/mapping as
     * [scan], filtered to [mediaStoreIds] via a parameterized `_ID IN (...)` selection — ids are
     * always passed as `selectionArgs`, never concatenated into the selection string.
     */
    internal suspend fun scan(mediaStoreIds: List<Long>): List<ScannedSong> = withContext(Dispatchers.IO) {
        if (mediaStoreIds.isEmpty()) return@withContext emptyList()
        context.contentResolver.query(
            MediaStoreColumns.COLLECTION,
            MediaStoreColumns.PROJECTION,
            MediaStoreColumns.selectionForIds(mediaStoreIds.size),
            mediaStoreIds.map { it.toString() }.toTypedArray(),
            MediaStoreColumns.SORT_ORDER,
        )?.use { cursor -> readSongs(cursor) } ?: emptyList()
    }

    private fun readSongs(cursor: Cursor): List<ScannedSong> {
        val cols = ColumnIndices.from(cursor)
        val results = mutableListOf<ScannedSong>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cols.id)
            val albumId = cursor.getLong(cols.albumId)
            val rawTrack = cursor.getInt(cols.track)
            // MediaStore encodes disc*1000 + track; modulo extracts the actual track number.
            val trackNumber = if (rawTrack > 0) rawTrack % MediaStoreColumns.DISC_TRACK_MULTIPLIER else null
            results += ScannedSong(
                mediaStoreId = id,
                title = cursor.getString(cols.title).orEmpty(),
                artist = cursor.getString(cols.artist).orEmpty(),
                album = cursor.getString(cols.album).orEmpty(),
                albumId = albumId,
                durationMs = cursor.getLong(cols.duration),
                trackNumber = trackNumber,
                year = cursor.getInt(cols.year).takeIf { it > 0 },
                contentUri = ContentUris.withAppendedId(MediaStoreColumns.COLLECTION, id).toString(),
                artworkUri = ContentUris.withAppendedId(MediaStoreColumns.ALBUM_ART_URI, albumId).toString(),
                dateModified = cursor.getLong(cols.dateModified),
            )
        }
        return results
    }

    private data class ColumnIndices(
        val id: Int,
        val title: Int,
        val artist: Int,
        val album: Int,
        val albumId: Int,
        val duration: Int,
        val track: Int,
        val year: Int,
        val dateModified: Int,
    ) {
        companion object {
            fun from(cursor: Cursor) = ColumnIndices(
                id = cursor.getColumnIndexOrThrow(MediaStoreColumns.ID),
                title = cursor.getColumnIndexOrThrow(MediaStoreColumns.TITLE),
                artist = cursor.getColumnIndexOrThrow(MediaStoreColumns.ARTIST),
                album = cursor.getColumnIndexOrThrow(MediaStoreColumns.ALBUM),
                albumId = cursor.getColumnIndexOrThrow(MediaStoreColumns.ALBUM_ID),
                duration = cursor.getColumnIndexOrThrow(MediaStoreColumns.DURATION),
                track = cursor.getColumnIndexOrThrow(MediaStoreColumns.TRACK),
                year = cursor.getColumnIndexOrThrow(MediaStoreColumns.YEAR),
                dateModified = cursor.getColumnIndexOrThrow(MediaStoreColumns.DATE_MODIFIED),
            )
        }
    }
}
