package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import androidx.compose.runtime.Immutable
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import com.rolla.musicplayer.core.ui.customtags.CustomTagPickerState

/** One field's editing state in the batch editor: the typed [value] and whether it is [applied]. */
@Immutable
data class BatchFieldState(
    val value: String = "",
    val applied: Boolean = false,
)

/**
 * The 8 editable [TagField]s for the batch editor, each with its own [BatchFieldState].
 *
 * Deliberately 8 named properties (mirroring [SongTags]), not a `Map<TagField, BatchFieldState>`:
 * a `Map` is not a Compose-stable collection type, so every keystroke would force every reader of
 * [BatchTagEditorUiState] to recompose, whereas a plain data class of stable properties lets
 * Compose skip anything that doesn't read the one field that changed. [get]/[with] give
 * [BatchTagEditorViewModel] the same `TagField ->` ergonomics a `Map` would have, without paying
 * for one.
 */
@Immutable
data class BatchTagFields(
    val title: BatchFieldState = BatchFieldState(),
    val artist: BatchFieldState = BatchFieldState(),
    val album: BatchFieldState = BatchFieldState(),
    val albumArtist: BatchFieldState = BatchFieldState(),
    val genre: BatchFieldState = BatchFieldState(),
    val year: BatchFieldState = BatchFieldState(),
    val trackNumber: BatchFieldState = BatchFieldState(),
    val composer: BatchFieldState = BatchFieldState(),
) {

    operator fun get(field: TagField): BatchFieldState = when (field) {
        TagField.TITLE -> title
        TagField.ARTIST -> artist
        TagField.ALBUM -> album
        TagField.ALBUM_ARTIST -> albumArtist
        TagField.GENRE -> genre
        TagField.YEAR -> year
        TagField.TRACK_NUMBER -> trackNumber
        TagField.COMPOSER -> composer
    }

    /** Returns a copy with [field]'s [BatchFieldState] replaced by the result of [transform]. */
    fun with(field: TagField, transform: (BatchFieldState) -> BatchFieldState): BatchTagFields = when (field) {
        TagField.TITLE -> copy(title = transform(title))
        TagField.ARTIST -> copy(artist = transform(artist))
        TagField.ALBUM -> copy(album = transform(album))
        TagField.ALBUM_ARTIST -> copy(albumArtist = transform(albumArtist))
        TagField.GENRE -> copy(genre = transform(genre))
        TagField.YEAR -> copy(year = transform(year))
        TagField.TRACK_NUMBER -> copy(trackNumber = transform(trackNumber))
        TagField.COMPOSER -> copy(composer = transform(composer))
    }

    /** True once at least one [TagField] has [BatchFieldState.applied] set. */
    fun anyApplied(): Boolean = TagField.entries.any { this[it].applied }

    /**
     * Only the applied fields, ready to hand to [com.rolla.musicplayer.feature.tageditor.io.TagWriter.writeFields]
     * -- an unpicked field is simply absent from this map, which is exactly what tells
     * [TagWriter][com.rolla.musicplayer.feature.tageditor.io.TagWriter] to leave it untouched on disk.
     */
    fun appliedValues(): Map<TagField, String> =
        TagField.entries.filter { this[it].applied }.associateWith { this[it].value }
}

/** Progress of an in-flight batch save: [done] files fully resolved (succeeded or failed) of [total]. */
@Immutable
data class BatchProgress(val done: Int, val total: Int)

/**
 * Batch tag editor screen UI state (`BatchTagEditor(songIds: List<Long>)`).
 *
 * Unlike the single-song editor, [fields] never displays a per-song "current value" -- editing N
 * songs' tags at once has no single baseline to diff against, so every field starts blank
 * ([BatchFieldState.value] `== ""`) and [BatchFieldState.applied] starts `false` regardless of what
 * any selected song already has. [songCount] is the number of ids the screen was navigated to with
 * (known synchronously, before any [com.rolla.musicplayer.core.model.Song] is loaded); it does not
 * shrink if some of those ids turn out to no longer exist by the time [BatchTagEditorViewModel]
 * actually resolves them at save time (see that class's KDoc on why resolution is deferred to
 * [BatchTagEditorViewModel.onSaveClick]).
 *
 * The consent/recovery/message/isSaving/isClosed quartet mirrors [TagEditorUiState] exactly (see
 * its KDoc for the shape of the dance); [progress] is the one addition, non-null only while
 * [BatchTagEditorViewModel.onConsentGranted]'s per-song loop is actually running.
 *
 * Custom tags work the same way as the fields: nothing is read from the songs up front, so [picker]'s
 * [CustomTagPickerState.selected] starts empty and holds what Save ADDS to every selected track's `ROLLA_TAGS` field
 * (never removes). The Tags and Subtags rows are always enabled: whether a file's format can take custom tags is judged
 * per file at save time.
 */
@Immutable
data class BatchTagEditorUiState(
    val songCount: Int = 0,
    val fields: BatchTagFields = BatchTagFields(),
    /**
     * The custom tag picker: both lists in library order, the chosen IDs to add to every selected track (empty at
     * first), the open page and each page's search text. The Tags and Subtags rows show the chosen names.
     */
    val picker: CustomTagPickerState = CustomTagPickerState(
        tags = emptyList(),
        subTags = emptyList(),
        selected = CustomTagIds.EMPTY,
    ),
    /** Whether the picker sheet is open. */
    val isPickerOpen: Boolean = false,
    /** The open "New tag"/"New subtag" dialog, with the reason the last Create was refused; null when closed. */
    val nameDialog: CustomTagNameDialogState? = null,
    val yearError: String? = null,
    val trackNumberError: String? = null,
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val consentRequest: List<String>? = null,
    val recoveryRequest: RecoverableSecurityException? = null,
    val progress: BatchProgress? = null,
    val message: String? = null,
    val isClosed: Boolean = false,
)

/**
 * Recomputes [BatchTagEditorUiState.yearError]/[BatchTagEditorUiState.trackNumberError]/
 * [BatchTagEditorUiState.canSave] from [BatchTagEditorUiState.fields]: called after every field
 * value/apply-toggle change. Per the task contract, validation is only ENFORCED for a field while
 * its apply-toggle is on -- an invalid year/track-number typed into a field the user hasn't (yet)
 * chosen to apply neither blocks [BatchTagEditorUiState.canSave] nor surfaces an error message,
 * since that value isn't going to be written anywhere until the toggle is switched on. A chosen
 * custom tag or subtag is something to write as well, so it makes Save available with no field
 * applied; called after every pick change too.
 */
internal fun BatchTagEditorUiState.recomputeSaveState(): BatchTagEditorUiState {
    val yearField = fields.year
    val trackField = fields.trackNumber
    val yearError = if (yearField.applied) validateYear(yearField.value) else null
    val trackNumberError = if (trackField.applied) validateTrackNumber(trackField.value) else null
    val hasErrors = yearError != null || trackNumberError != null
    val hasEdit = fields.anyApplied() || !picker.selected.isEmpty
    return copy(
        yearError = yearError,
        trackNumberError = trackNumberError,
        canSave = hasEdit && !hasErrors && !isSaving,
    )
}
