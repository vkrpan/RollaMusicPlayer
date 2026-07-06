package com.rolla.musicplayer.feature.tageditor

private const val MAX_YEAR_DIGITS = 4

/**
 * Light, shared field validation for the year/track-number [TagField]s -- a blank value is always
 * valid (blank means "clear this tag" for the single editor, or "not applied" for the batch
 * editor; see [SongTags] and [BatchTagFields]). Used by both [TagEditorViewModel] and
 * [BatchTagEditorViewModel] so the two editors never drift on what counts as a valid year.
 */
internal fun validateYear(year: String): String? = when {
    year.isBlank() -> null
    year.length > MAX_YEAR_DIGITS || !year.all { it.isDigit() } -> "Year must be blank or up to 4 digits."
    else -> null
}

/** See [validateYear] -- same sharing rationale, for the track-number field. */
internal fun validateTrackNumber(trackNumber: String): String? = when {
    trackNumber.isBlank() -> null
    !trackNumber.all { it.isDigit() } -> "Track number must be blank or digits only."
    else -> null
}
