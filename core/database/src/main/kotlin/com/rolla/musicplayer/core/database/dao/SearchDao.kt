package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.AlbumSearchRow
import com.rolla.musicplayer.core.database.relation.ArtistSearchRow
import kotlinx.coroutines.flow.Flow

/**
 * Local, offline substring search over the `songs` table.
 *
 * Uses `LIKE '%'||:query||'%'` rather than a Room FTS4 virtual table — a personal on-device
 * library (hundreds to low thousands of songs) doesn't need FTS's scalability, LIKE gives the
 * substring semantics users expect (typing "air" matches "Stairway"), and it requires no entity
 * schema change, no `MusicDatabase` version bump, and no migration. This is a deliberate,
 * binding architecture decision for this feature — do not introduce FTS here.
 *
 * A separate interface from [SongDao] (not more query methods added there) purely to stay under
 * detekt's `TooManyFunctions` interface threshold; it is registered on `MusicDatabase` the same
 * way, which is not itself a schema change (no new entity, no version bump).
 *
 * **LIKE-escaping contract:** every [query] parameter below must already have literal `%`, `_`,
 * and `\` in the raw user input replaced with `\%`, `\_`, `\` respectively, so that e.g.
 * searching for `"50%"` matches the literal text `50%` instead of `%` being interpreted as a
 * wildcard. `SearchRepository` (`:core:data`) owns this escaping before calling in here; every
 * query below declares `ESCAPE '\'` to honor it.
 */
@Dao
interface SearchDao {

    /**
     * Songs whose title, artist, or album contains [query] (substring, case-insensitive for
     * ASCII — SQLite's default `LIKE` behavior). Ordered with cheap relevance: title-prefix
     * matches first, then alphabetical by title.
     */
    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
           OR artist LIKE '%' || :query || '%' ESCAPE '\'
           OR album LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY
            CASE WHEN title LIKE :query || '%' ESCAPE '\' THEN 0 ELSE 1 END,
            title ASC
        """,
    )
    fun searchSongs(query: String): Flow<List<SongEntity>>

    /**
     * Albums — songs grouped by `album_id` — whose album name contains [query]. See
     * [AlbumSearchRow] for how `artist` is derived (there is no `album_artist` column).
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
        WHERE album LIKE '%' || :query || '%' ESCAPE '\'
        GROUP BY album_id
        ORDER BY title ASC
        """,
    )
    fun searchAlbums(query: String): Flow<List<AlbumSearchRow>>

    /**
     * Artists — songs grouped by `artist` name — whose name contains [query]. See
     * [ArtistSearchRow.toDomain] for how the synthetic `Artist.id` is derived (there is no
     * `artist_id` column).
     */
    @Query(
        """
        SELECT
            artist AS name,
            COUNT(DISTINCT album_id) AS albumCount,
            COUNT(*) AS songCount
        FROM songs
        WHERE artist LIKE '%' || :query || '%' ESCAPE '\'
        GROUP BY artist
        ORDER BY name ASC
        """,
    )
    fun searchArtists(query: String): Flow<List<ArtistSearchRow>>
}
