package com.rolla.musicplayer.core.data.scanner

import com.rolla.musicplayer.core.database.entity.SongEntity

internal data class ScannedSong(
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val contentUri: String,
    val artworkUri: String,
    val dateModified: Long,
    val folderPath: String,
    /** MediaStore's genre (API 30+), null below 30 or when blank. */
    val genre: String?,
)

/**
 * Maps a freshly-scanned MediaStore row to the Room entity, merging in the user-state columns
 * (favourite / play count / last played / date added) from [existing].
 *
 * Room's `@Upsert` replaces the *entire* row, so any upsert that builds a [SongEntity] straight
 * from scan data alone silently wipes those columns back to their defaults for every row it
 * touches. This is the single merge point both [LibraryIndexer.sync] and
 * [LibraryIndexer.syncSongs] go through so that guarantee only needs to be correct once.
 *
 * [existing] is null only when MediaStore has never reported this row before — i.e. it is being
 * indexed for the first time — in which case it gets `dateAdded = System.currentTimeMillis()`,
 * which backs the "Recently added" smart playlist's ordering. When [existing] is non-null every
 * user-state column is carried forward unchanged.
 *
 * The folder path always comes from the scan, never from [existing]: a stored NULL or a moved file must take the
 * scanned value.
 *
 * The file pass's two columns, `fileTagsRead` and `genre`, are carried forward only while the file is unchanged: the
 * read flag survives when [existing] had it set, its `dateModified` equals the scanned one and [rereadFileTags] is
 * false, and the genre then stays the one the pass read from the file. Otherwise the flag resets, so [FileTagPass]
 * reads the file again, and the genre falls back to the scanned MediaStore value until it does. [LibraryIndexer.sync]
 * passes false; [LibraryIndexer.syncSongs] passes true, because its caller knows the file changed even when
 * `DATE_MODIFIED`, which has second granularity, did not (the same reason it upserts unconditionally).
 */
internal fun ScannedSong.toEntity(existing: SongEntity?, rereadFileTags: Boolean = false): SongEntity {
    val keepsFileTags = !rereadFileTags && existing?.fileTagsRead == true && existing.dateModified == dateModified
    return SongEntity(
        id = mediaStoreId.toString(),
        mediaStoreId = mediaStoreId,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        contentUri = contentUri,
        artworkUri = artworkUri,
        dateModified = dateModified,
        dateAdded = existing?.dateAdded ?: System.currentTimeMillis(),
        isFavorite = existing?.isFavorite ?: false,
        playCount = existing?.playCount ?: 0,
        lastPlayed = existing?.lastPlayed,
        folderPath = folderPath,
        genre = if (keepsFileTags) existing?.genre else genre,
        fileTagsRead = keepsFileTags,
    )
}
