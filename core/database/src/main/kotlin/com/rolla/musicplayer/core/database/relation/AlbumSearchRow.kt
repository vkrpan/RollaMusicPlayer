package com.rolla.musicplayer.core.database.relation

import com.rolla.musicplayer.core.model.Album

/**
 * Projection returned by `SearchDao.searchAlbums` — one row per `album_id` group among songs
 * whose album name matched the search query.
 *
 * The `songs` table has no `album_artist` column (see
 * [com.rolla.musicplayer.core.database.entity.SongEntity]), so [artist] is derived as
 * `MIN(artist)` across the album's songs: a deterministic pick of the lexicographically-first
 * per-track artist tag. For a normal album — every song credits the same artist — this *is* that
 * artist. For a compilation/various-artists album with mixed per-track artist tags it is a
 * documented approximation, not a true album-artist tag.
 */
data class AlbumSearchRow(
    val albumId: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: String,
)

/** Maps this row to the domain [Album]. */
fun AlbumSearchRow.toDomain(): Album = Album(
    id = albumId,
    title = title,
    artist = artist,
    songCount = songCount,
    artworkUri = artworkUri,
)
