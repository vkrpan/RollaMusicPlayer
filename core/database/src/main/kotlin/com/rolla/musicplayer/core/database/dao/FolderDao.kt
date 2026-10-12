package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.FolderSummary
import kotlinx.coroutines.flow.Flow

/**
 * Folder queries over `songs.folder_path` (spec §9.4): the Folders tab and FolderDetail.
 *
 * A separate interface from [SongDao], which is at detekt's TooManyFunctions interface threshold, registered on
 * MusicDatabase like [AlbumDao] and [ArtistDao]. Rows whose folder_path is still NULL (not scanned since the v4
 * migration) belong to no folder until LibraryIndexer backfills them.
 */
@Dao
interface FolderDao {

    /**
     * Every folder that directly holds songs, grouped by exact path, ordered by path case-insensitively and then
     * exactly. artworkUri is the cover of the folder's alphabetically first song: the inner query has exactly one
     * MIN() aggregate, so SQLite takes the bare artwork_uri column from that row (the same documented behaviour
     * ArtistDao.observeArtists relies on).
     */
    @Query(
        """
        SELECT path, songCount, artworkUri FROM (
            SELECT
                folder_path AS path,
                COUNT(*) AS songCount,
                artwork_uri AS artworkUri,
                MIN(title COLLATE NOCASE) AS firstTitle
            FROM songs
            WHERE folder_path IS NOT NULL
            GROUP BY folder_path
        )
        ORDER BY path COLLATE NOCASE ASC, path ASC
        """,
    )
    fun observeFolders(): Flow<List<FolderSummary>>

    /** The songs directly in [path] (not in its subfolders), by title case-insensitively, the same order as Tracks. */
    @Query("SELECT * FROM songs WHERE folder_path = :path ORDER BY title COLLATE NOCASE ASC")
    fun observeSongsInFolder(path: String): Flow<List<SongEntity>>
}
