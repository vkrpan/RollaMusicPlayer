package com.rolla.musicplayer.feature.tageditor

import androidx.compose.runtime.Immutable

/**
 * The editable tag fields of a single audio file, as surfaced by the tag editor
 * (see the implement-tag-editor skill).
 *
 * Every field is a [String], including [year] and [trackNumber], so the editor can hold
 * blank or half-typed input (e.g. a partially-typed year) without a parse error on every
 * keystroke. Validation/parsing happens at write time, not while the user is typing.
 *
 * A blank field on save means "clear this tag" — the writer distinguishes a blank string
 * (explicit clear) from a field the user never touched (left as-is), per the batch-editor's
 * per-field overwrite selection.
 *
 * Artwork is intentionally NOT part of this model: it is handled separately as raw bytes by
 * a later step (the writer embeds artwork sourced from local images only).
 *
 * This is a feature-local editing DTO for `:feature:tageditor` — it does not belong in
 * `:core:model` because nothing outside the tag editor consumes it.
 */
@Immutable
data class SongTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val composer: String = "",
)
