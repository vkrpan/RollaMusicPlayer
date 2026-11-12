package com.rolla.musicplayer.feature.tageditor.managetags

import android.app.RecoverableSecurityException
import androidx.compose.runtime.Immutable
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.data.repository.ImportClash
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.CustomTagListFile
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import com.rolla.musicplayer.core.ui.tracksCountLabel
import com.rolla.musicplayer.feature.tageditor.BatchProgress

/** One row of Manage tags: a list entry and the number of tracks that carry it (its "N tracks" subtitle). */
@Immutable
data class ManagedTag(val tag: CustomTag, val songCount: Int)

/** The open delete confirmation: the entry to delete and the number of tracks it will be removed from. */
@Immutable
data class DeleteConfirm(val tag: CustomTag, val songCount: Int)

/**
 * The open import confirmation: the decoded list [file] that Import will apply, and the [clashes] it would cause (tags
 * first, then subtags, each by ID; [CustomTagRepository.previewImport]), one [warningLine] each.
 */
@Immutable
data class ImportConfirm(val file: CustomTagListFile, val clashes: List<ImportClash>)

/**
 * The clash's line in the import confirmation (spec §5.2): "2: Techno → Jazz, on 14 tracks" for a renamed ID, "5: House
 * → removed, on 3 tracks" for one the file lacks; the count reads as [tracksCountLabel] words ("on 1 track").
 */
fun ImportClash.warningLine(): String =
    "$id: $currentName → ${importedName ?: "removed"}, on ${tracksCountLabel(songCount)}"

/**
 * Manage tags screen UI state (custom tags phase 3; [ManageCustomTagsViewModel]).
 *
 * - [page], [tagQuery], [subTagQuery]: the open page and each page's search text; the screen filters [tags] and
 *   [subTags] with the picker's `filterCustomTags`.
 * - [tags], [subTags]: both lists in library order, each entry with its song count.
 * - [nameDialog]: the open name dialog, with the reason the last name was refused; [renaming] is the entry it renames,
 *   or null when it creates one.
 * - [deleteConfirm]: the open delete confirmation.
 * - [importConfirm]: the open import confirmation ("Replace your tags?"), with the file and its clashes.
 * - [consentRequest] / [recoveryRequest]: the one write-permission prompt covering every file that carries the entry
 *   being deleted, and API 29's per-file recovery prompt, resolved by the screen with `MediaWriteRequester`.
 * - [progress]: files done of the delete's files, while its per-file loop runs.
 * - [message]: a one-shot line ("Updated X of N tracks.", "Tags exported", "Couldn't export the tags.", "Couldn't
 *   read that file.", "Tags imported"); [ManageCustomTagsViewModel.dismissMessage] once shown.
 * - [isBusy]: true from a confirmed delete until it ends, and while an export writes, an import's file is read and
 *   previewed, or a confirmed import is applied; creating, renaming, deleting, exporting and importing are ignored
 *   meanwhile.
 */
@Immutable
data class ManageCustomTagsUiState(
    val page: CustomTagKind = CustomTagKind.TAG,
    val tagQuery: String = "",
    val subTagQuery: String = "",
    val tags: List<ManagedTag> = emptyList(),
    val subTags: List<ManagedTag> = emptyList(),
    val nameDialog: CustomTagNameDialogState? = null,
    val renaming: CustomTag? = null,
    val deleteConfirm: DeleteConfirm? = null,
    val importConfirm: ImportConfirm? = null,
    val consentRequest: List<String>? = null,
    val recoveryRequest: RecoverableSecurityException? = null,
    val progress: BatchProgress? = null,
    val message: String? = null,
    val isBusy: Boolean = false,
)
