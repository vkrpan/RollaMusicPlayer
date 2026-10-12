package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.AlbumSearchRow
import com.rolla.musicplayer.core.database.relation.ArtistSearchRow
import kotlinx.coroutines.flow.Flow

/**
 * Artist queries over the `songs` table: the whole-library list (Artists tab) and artist detail.
 *
 * A separate interface from [SongDao] (not more query methods added there) purely to stay under detekt's
 * `TooManyFunctions` interface threshold — the same reasoning documented on [AlbumDao] and [SearchDao].
 * Registered on `MusicDatabase` the same way, which is not itself a schema change (no new entity, no version
 * bump).
 *
 * The `songs` table has no `artist_id` column — artists only exist as a text column (see
 * [com.rolla.musicplayer.core.database.entity.SongEntity]), grouped at query time. Every per-artist query
 * below matches `artist = :artistName` by exact equality: the name passed in always comes from the same
 * `GROUP BY artist` key used to derive it (see [ArtistSearchRow.toDomain]'s synthetic id), so an exact match
 * is correct — there is deliberately no `LIKE` fuzziness here as there is in [SearchDao].
 */
@Dao
interface ArtistDao {

    /**
     * Every artist (exact-name groups), ordered case-insensitively, then binary, so "Queen" and
     * "queen" sit together in a stable order. artworkUri is the cover of the artist's alphabetically
     * first album: the inner query has exactly one MIN() aggregate, so SQLite takes the bare
     * artwork_uri column from that row (documented SQLite behaviour). One scan, with no per-artist
     * subquery.
     */
    @Query(
        """
        SELECT name, albumCount, songCount, artworkUri FROM (
            SELECT
                artist AS name,
                COUNT(DISTINCT album_id) AS albumCount,
                COUNT(*) AS songCount,
                artwork_uri AS artworkUri,
                MIN(album COLLATE NOCASE) AS firstAlbum
            FROM songs
            GROUP BY artist
        )
        ORDER BY name COLLATE NOCASE ASC, name ASC
        """,
    )
    fun observeArtists(): Flow<List<ArtistSearchRow>>

    /**
     * The artist summary for [artistName] — the same derived-table shape as [observeArtists]
     * (counts plus the cover of the artist's alphabetically first album, see [ArtistSearchRow]),
     * filtered to one exact name. Emits `null` when no songs by that artist exist (e.g. the
     * artist's songs were removed by a re-scan).
     */
    @Query(
        """
        SELECT name, albumCount, songCount, artworkUri FROM (
            SELECT
                artist AS name,
                COUNT(DISTINCT album_id) AS albumCount,
                COUNT(*) AS songCount,
                artwork_uri AS artworkUri,
                MIN(album COLLATE NOCASE) AS firstAlbum
            FROM songs
            WHERE artist = :artistName
            GROUP BY artist
        )
        """,
    )
    fun observeArtist(artistName: String): Flow<ArtistSearchRow?>

    /**
     * The albums this artist appears on — songs grouped by `album_id`, same shape as
     * `AlbumDao.observeAlbum`/`SearchDao.searchAlbums` (see [AlbumSearchRow] for how `artist` is
     * derived via `MIN(artist)`, there is no `album_artist` column). Ordered alphabetically by
     * title.
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
        WHERE artist = :artistName
        GROUP BY album_id
        ORDER BY title ASC
        """,
    )
    fun observeArtistAlbums(artistName: String): Flow<List<AlbumSearchRow>>

    /**
     * This artist's songs, grouped by album in display order, then the standard disc/track order
     * within each album: `track_number` ascending with untagged (`NULL`) track numbers sorted
     * last (SQLite's `track_number IS NULL` evaluates to `0`/`1`, the standard NULLS-LAST idiom —
     * see [AlbumDao.observeAlbumSongs]), then alphabetically by title as a final tiebreak.
     */
    @Query(
        """
        SELECT * FROM songs
        WHERE artist = :artistName
        ORDER BY album ASC, track_number IS NULL, track_number ASC, title ASC
        """,
    )
    fun observeArtistSongs(artistName: String): Flow<List<SongEntity>>
}
