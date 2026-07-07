package com.rolla.musicplayer.core.database.relation

import com.rolla.musicplayer.core.model.Artist

/**
 * Projection returned by `SearchDao.searchArtists` — one row per distinct `artist` name among
 * songs matching the search query.
 */
data class ArtistSearchRow(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
)

/**
 * Maps this row to the domain [Artist], deriving a stable synthetic [Artist.id].
 *
 * The `songs` table has no `artist_id` column — artists only exist as a text column, grouped at
 * query time (see `SearchDao.searchArtists`) — but [Artist.id] is `Long`. [name]'s 64-bit FNV-1a
 * hash is used as that id: the same artist name always produces the same id, so it's stable
 * across app runs/devices and safe as a list `key`, without requiring a schema change to add a
 * real `artist_id` column. There is no artist-detail route yet that resolves an id back to a
 * name, so a theoretical hash collision has no functional impact today — only revisit this if/when
 * such a route needs a guaranteed-unique id.
 */
fun ArtistSearchRow.toDomain(): Artist = Artist(
    id = name.fnv1aHash(),
    name = name,
    albumCount = albumCount,
    songCount = songCount,
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
