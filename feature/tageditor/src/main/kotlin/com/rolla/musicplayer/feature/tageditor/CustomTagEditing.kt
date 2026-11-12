package com.rolla.musicplayer.feature.tageditor

import com.rolla.musicplayer.core.data.repository.CustomTagNameResult
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.model.CUSTOM_TAG_NAME_MAX_LENGTH
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import com.rolla.musicplayer.core.ui.customtags.CustomTagPickerState
import com.rolla.musicplayer.core.ui.customtags.toggled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val NAME_BLANK_MESSAGE = "Enter a name"
private const val NAME_TOO_LONG_MESSAGE = "Use $CUSTOM_TAG_NAME_MAX_LENGTH characters or fewer"
private const val NAME_DUPLICATE_MESSAGE = "That name is already in the list"
private const val LIST_FULL_MESSAGE = "This list is full"

/**
 * The custom tag part of a tag editor's UI state: the picker, whether its sheet is open, and the name dialog.
 *
 * [prunedIds] are the ticks the lists' prune removed (an entry Manage tags deleted, or an import dropped) that the user
 * has not ticked or unticked since: not the user's removals, so the single editor's Save leaves them as the file holds
 * them. An editor that does not need them (the batch editor only adds) may leave them out of its state.
 */
internal data class CustomTagEditing(
    val picker: CustomTagPickerState,
    val isPickerOpen: Boolean,
    val nameDialog: CustomTagNameDialogState?,
    val prunedIds: CustomTagIds = CustomTagIds.EMPTY,
)

/**
 * The user's own custom tag edits in the single editor, measured from the IDs ticked at load: [ticked] the IDs ticked
 * since, [unticked] the loaded IDs the user unticked (a pruned tick is not one of them).
 */
internal data class CustomTagChange(val ticked: CustomTagIds, val unticked: CustomTagIds) {

    /** True when the user changed no tick, so Save has nothing of the user's to write. */
    val isEmpty: Boolean get() = ticked.isEmpty && unticked.isEmpty

    /** [current] (the field as the file holds it now) with this change applied: `(current − unticked) ∪ ticked`. */
    fun appliedTo(current: CustomTagIds): CustomTagIds = CustomTagIds(
        tagIds = (current.tagIds - unticked.tagIds) + ticked.tagIds,
        subTagIds = (current.subTagIds - unticked.subTagIds) + ticked.subTagIds,
    )

    companion object {
        /**
         * The change from [loadedKnown] (the known IDs ticked at load) to [selected] (the ticks now): `selected −
         * loadedKnown` ticked, and `(loadedKnown − selected) − pruned` unticked, so an ID the lists' prune removed
         * ([pruned]) counts as untouched.
         */
        fun of(selected: CustomTagIds, loadedKnown: CustomTagIds, pruned: CustomTagIds) = CustomTagChange(
            ticked = selected.without(loadedKnown),
            unticked = loadedKnown.without(selected).without(pruned),
        )
    }
}

/** How [CustomTagEditingHandlers] reaches into one editor's UI state [S]. */
internal interface CustomTagEditingAccess<S> {

    /** The custom tag part of [state]. */
    fun read(state: S): CustomTagEditing

    /**
     * [state] with [editing]'s fields copied back (its [CustomTagEditing.prunedIds] only by an editor that keeps them),
     * and the editor's own save-state recompute run when [editing]'s ticks or pruned IDs differ from [state]'s. The
     * recompute reads no other picker field, so a page, search, sheet or dialog change, or a list arriving, leaves the
     * save state exactly as it was (as the editors did before this helper).
     */
    fun write(state: S, editing: CustomTagEditing): S

    /** Whether the custom tags may change now: rows, ticks and Create are ignored while this is false. */
    fun canEdit(state: S): Boolean
}

/** Both custom tag lists, in library order. */
internal data class CustomTagLists(val tags: List<CustomTag>, val subTags: List<CustomTag>)

/**
 * The picker handling both tag editors share: it observes both lists into the picker for [scope]'s lifetime and
 * handles the picker's and the name dialog's events on [uiState], through [access].
 *
 * Rows, ticks, "New tag" and Create are ignored unless [CustomTagEditingAccess.canEdit]; the page, the search, Done and
 * the dialog's dismissal always apply. A second Create while one runs is ignored.
 *
 * Whenever the lists arrive, the ticks keep only the IDs they know (custom tags phase 3): Manage tags may have deleted
 * a ticked entry, or an import dropped it, while the editor was open, and the picker cannot show such a tick. A pruned
 * tick is recorded in [CustomTagEditing.prunedIds] until the user ticks or unticks that ID, because it is not the
 * user's removal: the single editor's Save leaves it as the file holds it then (final review I1). The empty fallback
 * of a failed lists flow is the exception: it prunes nothing.
 */
internal class CustomTagEditingHandlers<S>(
    private val uiState: MutableStateFlow<S>,
    private val scope: CoroutineScope,
    private val repository: CustomTagRepository,
    private val access: CustomTagEditingAccess<S>,
) {

    /**
     * True once the lists flow has failed and [lists] holds the empty fallback. Declared before [lists], so it is
     * initialised before the flow can run.
     */
    private var listsFailed: Boolean = false

    /** Both custom tag lists in library order; null until the repository's first answer. */
    val lists: StateFlow<CustomTagLists?> = combine(
        repository.observeCustomTags(CustomTagKind.TAG),
        repository.observeCustomTags(CustomTagKind.SUBTAG),
    ) { tags, subTags -> CustomTagLists(tags, subTags) }
        // A broken custom tag table degrades to empty lists rather than crashing the editor: the fields still edit.
        // The fallback is not a deletion, so it never prunes the ticks (fix round 1, review I1): a failure after load
        // leaves the ticks as loaded, and a save that changes no tick leaves the field untouched. A failure before load
        // makes no ID known, so nothing is ticked and a save keeps every ID in the file.
        .catch {
            listsFailed = true
            emit(CustomTagLists(tags = emptyList(), subTags = emptyList()))
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** True while a "New tag" Create runs, so a second tap on Create does not create twice. */
    private var isCreating: Boolean = false

    init {
        scope.launch {
            lists.filterNotNull().collect { lists ->
                // Manage tags can delete a ticked entry, or an import drop it, while the editor is open: the ticks
                // keep only IDs the lists still know, and the dropped ones are recorded as pruned, not unticked. The
                // empty fallback of a failed flow says nothing about deletions, so it prunes nothing.
                edit {
                    val selected = if (listsFailed) it.picker.selected else it.picker.selected.knownIn(lists)
                    it.copy(
                        picker = it.picker.copy(tags = lists.tags, subTags = lists.subTags, selected = selected),
                        prunedIds = it.prunedIds.union(it.picker.selected.without(selected)),
                    )
                }
            }
        }
    }

    /** A tap on the Tags or Subtags row: opens the picker on [kind]'s page with empty searches. */
    fun onRowsClick(kind: CustomTagKind) {
        if (!canEdit()) return
        edit { it.copy(isPickerOpen = true, picker = it.picker.copy(page = kind, tagQuery = "", subTagQuery = "")) }
    }

    /** The picker's tab or pager moved to [kind]'s page; the sheet follows the state. */
    fun onPageChange(kind: CustomTagKind) {
        edit { it.copy(picker = it.picker.copy(page = kind)) }
    }

    /** The search text of [kind]'s page changed. */
    fun onQueryChange(kind: CustomTagKind, query: String) {
        edit {
            val picker = when (kind) {
                CustomTagKind.TAG -> it.picker.copy(tagQuery = query)
                CustomTagKind.SUBTAG -> it.picker.copy(subTagQuery = query)
            }
            it.copy(picker = picker)
        }
    }

    /** Ticks or unticks entry [id] of [kind]'s list; from now on the user's own choice, never a pruned tick. */
    fun onToggle(kind: CustomTagKind, id: Long) {
        if (!canEdit()) return
        edit {
            it.copy(
                picker = it.picker.copy(selected = it.picker.selected.toggled(kind, id)),
                prunedIds = it.prunedIds.minusId(kind, id),
            )
        }
    }

    /** "Done", or the sheet dismissed: closes the picker. Every tick has already been applied. */
    fun onDone() {
        edit { it.copy(isPickerOpen = false) }
    }

    /** "New tag"/"New subtag" in the picker: opens the name dialog for [kind], prefilled with [name] (the search). */
    fun onCreateClick(kind: CustomTagKind, name: String) {
        if (!canEdit()) return
        edit { it.copy(nameDialog = CustomTagNameDialogState(kind = kind, initialName = name)) }
    }

    /**
     * "Create" in the name dialog: adds [name] to the dialog's list. A saved entry closes the dialog and is ticked; a
     * refused name keeps the dialog open with the reason. Ignored while an earlier Create runs.
     */
    fun onCreateConfirm(name: String) {
        val dialog = access.read(uiState.value).nameDialog ?: return
        if (isCreating || !canEdit()) return
        isCreating = true
        scope.launch {
            try {
                applyCreateResult(dialog.kind, repository.create(dialog.kind, name))
            } finally {
                isCreating = false
            }
        }
    }

    /** Closes the name dialog without creating anything. */
    fun onNameDialogDismiss() {
        edit { it.copy(nameDialog = null) }
    }

    private fun canEdit(): Boolean = access.canEdit(uiState.value)

    private inline fun edit(transform: (CustomTagEditing) -> CustomTagEditing) {
        uiState.update { state -> access.write(state, transform(access.read(state))) }
    }

    /** Closes the name dialog and ticks the new entry, or keeps the dialog open with the reason [result] gives. */
    private fun applyCreateResult(kind: CustomTagKind, result: CustomTagNameResult) {
        if (result is CustomTagNameResult.Saved) {
            edit {
                val selected = it.picker.selected.plusId(kind, result.tag.id)
                val pruned = it.prunedIds.minusId(kind, result.tag.id)
                it.copy(nameDialog = null, picker = it.picker.copy(selected = selected), prunedIds = pruned)
            }
            return
        }
        // Only rename answers NotFound; a create never does. Kept open and unchanged rather than guessed at.
        val error = result.refusalMessage() ?: return
        edit { it.copy(nameDialog = it.nameDialog?.copy(error = error)) }
    }
}

/** The reason the name dialog shows for a refused name; null for [CustomTagNameResult.Saved] and NotFound. */
internal fun CustomTagNameResult.refusalMessage(): String? = when (this) {
    is CustomTagNameResult.Saved -> null
    CustomTagNameResult.Blank -> NAME_BLANK_MESSAGE
    CustomTagNameResult.TooLong -> NAME_TOO_LONG_MESSAGE
    CustomTagNameResult.Duplicate -> NAME_DUPLICATE_MESSAGE
    CustomTagNameResult.LimitReached -> LIST_FULL_MESSAGE
    CustomTagNameResult.NotFound -> null
}

/** These IDs without any the [lists] do not know: what the picker may show and tick. */
internal fun CustomTagIds.knownIn(lists: CustomTagLists): CustomTagIds {
    val knownTagIds = lists.tags.mapTo(HashSet()) { it.id }
    val knownSubTagIds = lists.subTags.mapTo(HashSet()) { it.id }
    return CustomTagIds(
        tagIds = tagIds.filterTo(HashSet()) { it in knownTagIds },
        subTagIds = subTagIds.filterTo(HashSet()) { it in knownSubTagIds },
    )
}

/** These IDs with [id] in [kind]'s list (unchanged when it is there already). */
internal fun CustomTagIds.plusId(kind: CustomTagKind, id: Long): CustomTagIds = when (kind) {
    CustomTagKind.TAG -> copy(tagIds = tagIds + id)
    CustomTagKind.SUBTAG -> copy(subTagIds = subTagIds + id)
}

/** These IDs without [id] in [kind]'s list (unchanged when it is not there). */
internal fun CustomTagIds.minusId(kind: CustomTagKind, id: Long): CustomTagIds = when (kind) {
    CustomTagKind.TAG -> copy(tagIds = tagIds - id)
    CustomTagKind.SUBTAG -> copy(subTagIds = subTagIds - id)
}

/** These IDs without any of [other]'s, kind by kind. */
internal fun CustomTagIds.without(other: CustomTagIds): CustomTagIds =
    CustomTagIds(tagIds = tagIds - other.tagIds, subTagIds = subTagIds - other.subTagIds)

/** These IDs with all of [other]'s, kind by kind. */
internal fun CustomTagIds.union(other: CustomTagIds): CustomTagIds =
    CustomTagIds(tagIds = tagIds + other.tagIds, subTagIds = subTagIds + other.subTagIds)
