package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.AlbumSearchRow
import kotlinx.coroutines.flow.Flow

/**
 * Album queries over the `songs` table: the whole-library list (Albums tab) and album detail.
 *
 * A separate interface from [SongDao] (not more query methods added there) purely to stay under
 * detekt's `TooManyFunctions` interface threshold (`SongDao` is already at 10 of 12) — the same
 * reasoning documented on [SearchDao]. Registered on `MusicDatabase` the same way, which is not
 * itself a schema change (no new entity, no version bump).
 */
@Dao
interface AlbumDao {

    /**
     * The album summary for [albumId] — same grouped projection shape as
     * `SearchDao.searchAlbums` (see [AlbumSearchRow] for how `artist` is derived via `MIN(artist)`,
     * there is no `album_artist` column). Emits `null` when no songs with that `album_id` exist
     * (e.g. the album was removed by a re-scan).
     */
    @Query(
        """
        SELECT
            album_id AS albumId,
            album AS title,
            MIN(artist) AS artist,
            COUNT(*) AS songCount,
            MAX(artwork_uri) AS artworkUri
        FROM songs
        WHERE album_id = :albumId
        GROUP BY album_id
        """,
    )
    fun observeAlbum(albumId: Long): Flow<AlbumSearchRow?>

    /**
     * Every album, one row per album_id, ordered by title case-insensitively (album id breaks ties, so two
     * "Greatest Hits" keep a stable order). Same projection as observeAlbum (see AlbumSearchRow for MIN(artist)).
     */
    @Query(
        """
        SELECT
            album_id AS albumId,
            album AS title,
            MIN(artist) AS artist,
            COUNT(*) AS songCount,
            MAX(artwork_uri) AS artworkUri
        FROM songs
        GROUP BY album_id
        ORDER BY title COLLATE NOCASE ASC, albumId ASC
        """,
    )
    fun observeAlbums(): Flow<List<AlbumSearchRow>>

    /**
     * The album's songs in disc/track order: `track_number` ascending with untagged
     * (`NULL`) track numbers sorted last, then alphabetically by title as a tiebreak.
     *
     * `track_number IS NULL` evaluates to `0` for a non-null value and `1` for `NULL` in SQLite,
     * so ordering by it ascending first places every tagged track before every untagged one —
     * the standard SQLite NULLS-LAST idiom.
     */
    @Query(
        """
        SELECT * FROM songs
        WHERE album_id = :albumId
        ORDER BY track_number IS NULL, track_number ASC, title ASC
        """,
    )
    fun observeAlbumSongs(albumId: Long): Flow<List<SongEntity>>
}
