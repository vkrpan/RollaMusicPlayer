package com.rolla.musicplayer.core.model

/** Everything before the song id in a per-track artwork value. */
private const val SONG_ARTWORK_PREFIX = "rolla-artwork://song/"

/** Separates the song id from the version in a per-track artwork value. */
private const val SONG_ARTWORK_VERSION_SEPARATOR = "?v="

/**
 * A parsed per-track artwork value: the picture embedded in the audio file of the song with [songId] (the MediaStore
 * row id), as it was at [version] (the row's `date_modified`, so a rewritten file gets a new value).
 */
data class SongArtworkRef(val songId: Long, val version: Long)

/**
 * The per-track artwork value stored in [Song.artworkUri]: `rolla-artwork://song/{songId}?v={version}` (spec §5.2).
 * It names the picture embedded in that track's own file, never MediaStore's per-album cover, and [version] changes
 * whenever the file changes, so loaders keyed on the value never serve a stale picture.
 */
fun songArtworkUri(songId: String, version: Long): String =
    "$SONG_ARTWORK_PREFIX$songId$SONG_ARTWORK_VERSION_SEPARATOR$version"

/**
 * Parses a value built by [songArtworkUri]. Returns null for anything else: null, blank, a MediaStore uri
 * (`content://media/external/audio/albumart/…`, `…/audio/media/{id}/albumart`), a non-numeric song id or a missing
 * version. Plain string parsing: `:core:model` is pure Kotlin, without `android.net.Uri`.
 */
fun parseSongArtworkUri(uri: String?): SongArtworkRef? {
    if (uri == null || !uri.startsWith(SONG_ARTWORK_PREFIX)) return null
    val rest = uri.substring(SONG_ARTWORK_PREFIX.length)
    val songId = rest.substringBefore(SONG_ARTWORK_VERSION_SEPARATOR, missingDelimiterValue = "").toLongOrNull()
    val version = rest.substringAfter(SONG_ARTWORK_VERSION_SEPARATOR, missingDelimiterValue = "").toLongOrNull()
    return if (songId != null && version != null) SongArtworkRef(songId, version) else null
}
