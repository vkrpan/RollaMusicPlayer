package com.rolla.musicplayer.core.model

/**
 * Find tracks' filter (custom tags phase 4). Each text field matches any part of the song's value, ignoring case and
 * the field's surrounding spaces; a blank field is inactive. [year] matches exactly; null is inactive. [tagIds] and
 * [subTagIds] must all be carried by the song, each within its own kind. Every active field must match (AND).
 */
data class SongFilter(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: Int? = null,
    val tagIds: Set<Long> = emptySet(),
    val subTagIds: Set<Long> = emptySet(),
) {
    /** True when no field is active: every text field blank, no year and no tag or subtag IDs. */
    val isEmpty: Boolean
        get() = title.isBlank() && artist.isBlank() && album.isBlank() && genre.isBlank() && year == null &&
            tagIds.isEmpty() && subTagIds.isEmpty()
}
