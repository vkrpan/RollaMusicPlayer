package com.rolla.musicplayer.core.model

/** The label shown for an artist MediaStore does not know (the reference mini-player's own subtitle). */
internal const val UNKNOWN_ARTIST_LABEL = "Unknown"

/** What MediaStore stores for a missing artist: `MediaStore.UNKNOWN_STRING`, a literal, not a localised label. */
internal const val MEDIASTORE_UNKNOWN = "<unknown>"

/**
 * An artist's display name (Phase 7 ruling 3): [UNKNOWN_ARTIST_LABEL] when [raw] is blank or exactly MediaStore's
 * [MEDIASTORE_UNKNOWN], and [raw] otherwise. Display only: data, routes and list keys keep the raw name, because
 * artists are keyed by name (Phase 4a ruling 12), so `ArtistDetail("<unknown>")` still opens that artist.
 */
fun artistDisplayName(raw: String): String =
    if (raw.isBlank() || raw == MEDIASTORE_UNKNOWN) UNKNOWN_ARTIST_LABEL else raw
