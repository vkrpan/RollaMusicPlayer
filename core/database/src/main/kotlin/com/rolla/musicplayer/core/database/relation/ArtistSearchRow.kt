package com.rolla.musicplayer.core.database.relation

import com.rolla.musicplayer.core.model.Artist

/**
 * Projection returned by `SearchDao.searchArtists`, `ArtistDao.observeArtists` and
 * `ArtistDao.observeArtist` — one row per distinct (exact, case-sensitive) `artist` name.
 *
 * [artworkUri] is the cover of the artist's alphabetically first album (compared
 * case-insensitively): each query's inner `GROUP BY artist` has exactly one `MIN(album COLLATE
 * NOCASE)` aggregate, so SQLite takes the bare `artwork_uri` column from that minimum row.
 */
data class ArtistSearchRow(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val artworkUri: String,
)

/**
 * Maps this row to the domain [Artist], deriving a stable synthetic [Artist.id].
 *
 * The `songs` table has no `artist_id` column — artists only exist as a text column, grouped at
 * query time (see `SearchDao.searchArtists`) — but [Artist.id] is `Long`. [name]'s 64-bit FNV-1a
 * hash is used as that id: the same artist name always produces the same id, so it's stable
 * across app runs/devices, without requiring a schema change to add a real `artist_id` column.
 * The id is synthetic and display-only. It is NOT a safe list `key`: two names could hash to the
 * same id, and a duplicate key crashes a lazy list, so artist lists are keyed by [name] (the
 * `GROUP BY` key, hence unique). `ArtistDetail` likewise routes by name, never by this id.
 */
fun ArtistSearchRow.toDomain(): Artist = Artist(
    id = name.fnv1aHash(),
    name = name,
    albumCount = albumCount,
    songCount = songCount,
    artworkUri = artworkUri,
)

// 64-bit FNV-1a offset basis / prime — see http://www.isthe.com/chongo/tech/comp/fnv/.
// Written as the raw 64-bit bit pattern (negative as a signed Long); this is the standard constant,
// not an arbitrary magic number.
private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xcbf29ce484222325 as a signed Long
private const val FNV_PRIME = 0x100000001b3L
private const val BYTE_MASK = 0xFFL

private fun String.fnv1aHash(): Long {
    var hash = FNV_OFFSET_BASIS
    for (byte in toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toLong() and BYTE_MASK)
        hash *= FNV_PRIME
    }
    return hash
}
