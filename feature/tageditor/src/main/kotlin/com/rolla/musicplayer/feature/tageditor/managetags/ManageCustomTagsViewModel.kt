package com.rolla.musicplayer.feature.tageditor.managetags

import android.app.RecoverableSecurityException
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.CustomTagNameResult
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.tagio.CustomTagListJson
import com.rolla.musicplayer.core.tagio.RollaTagsFileIo
import com.rolla.musicplayer.core.tagio.canWriteCustomTags
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import com.rolla.musicplayer.feature.tageditor.BatchProgress
import com.rolla.musicplayer.feature.tageditor.TagSaveFinalizer
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagEditorIoDispatcher
import com.rolla.musicplayer.feature.tageditor.minusId
import com.rolla.musicplayer.feature.tageditor.refusalMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** A confirmed delete in progress: the entry, the number of tracks carrying it, their songs, and the tally so far. */
private class DeleteRun(val tag: CustomTag, val total: Int, val songs: List<Song>) {
    /** The songs whose file lost the ID and was persisted: what [TagSaveFinalizer] re-indexes. */
    val written = mutableListOf<Song>()

    /** Files updated: [written] plus the files that no longer carried the ID. */
    var updated = 0

    /** Files finished, updated or not: the progress count. */
    var done = 0
}

/** Where the delete's per-file loop paused, waiting on an API 29 recovery prompt for [song]'s already-edited [copy]. */
private class DeleteRecovery(val song: Song, val copy: File, val resumeIndex: Int)

/** One file's attempt to lose the deleted ID. */
private sealed interface CleanupOutcome {
    /** The ID was removed from the copy and the copy persisted. */
    class Written(val copy: File) : CleanupOutcome

    /** The file no longer carried the ID: updated, with nothing written. */
    class Unchanged(val copy: File) : CleanupOutcome

    /** Not updated: a format that cannot be written (M4A, WAV, OGG), or any failure. */
    class NotUpdated(val copy: File?) : CleanupOutcome

    /** API 29 refused the persist; the copy is already edited. */
    class NeedsRecovery(val copy: File, val exception: RecoverableSecurityException) : CleanupOutcome
}

/**
 * Manage tags (custom tags phase 3, spec §5.2, §5.4 and §6 calls 8 and 13): both lists with their track counts,
 * create, rename, delete with file clean-up, and export and import of the list file.
 *
 * ## Lists
 * Each kind's entries in library order ([CustomTagRepository.observeCustomTags]) joined with the number of songs that
 * carry each ([CustomTagRepository.observeLinkCounts]); a failing table reads as empty lists, as in the editors.
 *
 * ## Create and rename
 * [onCreateClick] and [onRenameClick] open phase 2's name dialog ([ManageCustomTagsUiState.renaming] says which);
 * [onNameConfirm] runs [CustomTagRepository.create] or [CustomTagRepository.rename]. A saved name closes the dialog; a
 * refused one keeps it open with phase 2's reason ([refusalMessage]). A rename of an entry deleted meanwhile
 * (`NotFound`) closes it too: there is nothing left to rename.
 *
 * ## Delete
 * [onDeleteClick] opens the confirmation with the entry's track count. [onDeleteConfirm] finds the songs that carry the
 * entry ([CustomTagRepository.songIdsCarrying]) and asks, through [ManageCustomTagsUiState.consentRequest], for ONE
 * write-permission prompt covering all their files; with none there is no prompt. Nothing is written before
 * [onConsentGranted]. Then, one file at a time, modelled on `SelectionArtworkViewModel`'s loop: an editable copy
 * ([SongFileResolver.createEditableCopy]); a format [canWriteCustomTags] rejects (M4A, WAV, OGG) is not updated;
 * otherwise the copy's `ROLLA_TAGS` field is read and, only if it carries the ID, rewritten without it, keeping every
 * other ID, known or not, and every other field, then persisted. A file that no longer carries the ID counts as updated
 * with nothing written. The copy is always deleted; a failure on one file never stops the others. On API 29 a
 * `RecoverableSecurityException` from the persist pauses the loop on that file
 * ([ManageCustomTagsUiState.recoveryRequest]): [onRecoveryGranted] retries only that persist and resumes, declining it
 * counts only that file as not updated.
 *
 * Then, whatever the files did (a declined prompt writes nothing), the entry and its links are deleted
 * ([CustomTagRepository.delete]), [TagSaveFinalizer.onSongsSaved] re-indexes the written songs, and when fewer files
 * were updated than tracks carried the entry, [ManageCustomTagsUiState.message] reads "Updated X of N tracks." (the
 * literal plural, as the batch editor's "Edited X of N tracks."). The copy in flight is tracked so [onCleared]
 * deletes it even mid-loop.
 *
 * ## Export and import (spec §5.2)
 * The screen's Storage Access Framework prompts pick the document; a cancelled prompt never reaches here.
 * [onExportDocumentChosen] writes [CustomTagListJson.encode] of [CustomTagRepository.exportLists] to it through
 * [CustomTagListDocuments]: "Tags exported", or "Couldn't export the tags." on any failure. [onImportDocumentChosen]
 * reads it and decodes it strictly ([CustomTagListJson.decode]); an unreadable or refused file says "Couldn't read that
 * file." and changes nothing. Otherwise [CustomTagRepository.previewImport] lists the clashes and
 * [ManageCustomTagsUiState.importConfirm] asks "Replace your tags?". Nothing is written before [onImportConfirm], which
 * runs [CustomTagRepository.applyImport] ("Tags imported"; a refusal there, which the strict decode makes unreachable,
 * reads as "Couldn't read that file." and writes nothing); [onImportDismiss] changes nothing. Each step holds
 * [ManageCustomTagsUiState.isBusy] while it runs and is ignored while another holds it.
 */
// TooManyFunctions: one public handler per screen event plus the delete loop's steps, all one consent flow (as in
// SelectionArtworkViewModel and BatchTagEditorViewModel). LongParameterList: the delete's four file collaborators, the
// repository, the list file's documents and the IO dispatcher, each an injected seam the tests replace.
@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList")
class ManageCustomTagsViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val songFileResolver: SongFileResolver,
    private val tagSaveFinalizer: TagSaveFinalizer,
    private val customTagRepository: CustomTagRepository,
    private val rollaTagsFileIo: RollaTagsFileIo,
    private val customTagListDocuments: CustomTagListDocuments,
    @TagEditorIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** The same API 29 test seam the tag editors use. */
    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    private val _uiState = MutableStateFlow(ManageCustomTagsUiState())
    val uiState: StateFlow<ManageCustomTagsUiState> = _uiState.asStateFlow()

    /** True while a create or rename runs, so a second tap on the dialog's button does not run it twice. */
    private var isNaming = false
    private var deleteRun: DeleteRun? = null
    private var pendingRecovery: DeleteRecovery? = null
    private var activeEditableCopy: File? = null

    init {
        viewModelScope.launch {
            combine(
                customTagRepository.managedTags(CustomTagKind.TAG),
                customTagRepository.managedTags(CustomTagKind.SUBTAG),
            ) { tags, subTags -> tags to subTags }
                .collect { (tags, subTags) -> _uiState.update { it.copy(tags = tags, subTags = subTags) } }
        }
    }

    /** The tab or pager moved to [kind]'s page. */
    fun onPageChange(kind: CustomTagKind) {
        _uiState.update { it.copy(page = kind) }
    }

    /** The search text of [kind]'s page changed. */
    fun onQueryChange(kind: CustomTagKind, query: String) {
        _uiState.update {
            when (kind) {
                CustomTagKind.TAG -> it.copy(tagQuery = query)
                CustomTagKind.SUBTAG -> it.copy(subTagQuery = query)
            }
        }
    }

    /** "New tag"/"New subtag": opens the name dialog for [kind], prefilled with [name] (the search). */
    fun onCreateClick(kind: CustomTagKind, name: String) {
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(nameDialog = CustomTagNameDialogState(kind = kind, initialName = name), renaming = null)
        }
    }

    /** A row tap or its "Rename": opens the name dialog prefilled with [tag]'s name. */
    fun onRenameClick(tag: CustomTag) {
        if (_uiState.value.isBusy) return
        _uiState.update {
            it.copy(nameDialog = CustomTagNameDialogState(kind = tag.kind, initialName = tag.name), renaming = tag)
        }
    }

    /** The name dialog's button: creates or renames (see the class KDoc). Ignored while an earlier one runs. */
    fun onNameConfirm(name: String) {
        val state = _uiState.value
        val dialog = state.nameDialog ?: return
        if (isNaming || state.isBusy) return
        val renaming = state.renaming
        isNaming = true
        viewModelScope.launch {
            try {
                val result = if (renaming == null) {
                    customTagRepository.create(dialog.kind, name)
                } else {
                    customTagRepository.rename(renaming.kind, renaming.id, name)
                }
                applyNameResult(result)
            } finally {
                isNaming = false
            }
        }
    }

    /** Closes the name dialog without saving anything. */
    fun onNameDialogDismiss() {
        _uiState.update { it.copy(nameDialog = null, renaming = null) }
    }

    /** "Delete" in a row's menu: opens the confirmation with the number of tracks carrying [tag]. */
    fun onDeleteClick(tag: CustomTag) {
        val state = _uiState.value
        if (state.isBusy) return
        val entries = if (tag.kind == CustomTagKind.TAG) state.tags else state.subTags
        val songCount = entries.firstOrNull { it.tag.id == tag.id }?.songCount ?: 0
        _uiState.update { it.copy(deleteConfirm = DeleteConfirm(tag, songCount)) }
    }

    /** Closes the confirmation without deleting. */
    fun onDeleteDismiss() {
        _uiState.update { it.copy(deleteConfirm = null) }
    }

    /** "Delete" in the confirmation: asks for the one prompt covering every file that carries the entry. */
    fun onDeleteConfirm() {
        val state = _uiState.value
        val confirm = state.deleteConfirm ?: return
        if (state.isBusy) return
        _uiState.update { it.copy(deleteConfirm = null, isBusy = true) }
        viewModelScope.launch {
            val tag = confirm.tag
            val songIds = customTagRepository.songIdsCarrying(tag.kind, tag.id)
            val byId = songRepository.observeSongs().first().associateBy(Song::id)
            val songs = songIds.mapNotNull { byId[it] }
            // total counts every carrying song; links are cascade-deleted with their song, so it equals songs.size
            // unless a song leaves the library meanwhile, and that file then counts as not updated.
            val run = DeleteRun(tag, total = songIds.size, songs = songs).also { deleteRun = it }
            if (songs.isEmpty()) {
                finishDelete(run)
            } else {
                _uiState.update { it.copy(consentRequest = songs.map(Song::contentUri)) }
            }
        }
    }

    /** Called once `MediaWriteRequester.ensureWritable` reports the one prompt granted: runs the per-file loop. */
    fun onConsentGranted() {
        if (_uiState.value.consentRequest == null) return
        _uiState.update { it.copy(consentRequest = null) }
        viewModelScope.launch { runLoop(startIndex = 0) }
    }

    /**
     * A declined prompt. The initial one writes nothing and still deletes the entry ("Updated 0 of N tracks."); an API
     * 29 recovery decline counts only that file as not updated and resumes the loop.
     */
    fun onConsentDenied() {
        val run = deleteRun ?: return
        if (_uiState.value.consentRequest != null) {
            _uiState.update { it.copy(consentRequest = null) }
            viewModelScope.launch { finishDelete(run) }
        } else {
            val recovery = takeRecovery() ?: return
            viewModelScope.launch {
                finishFile(run, recovery.song, CleanupOutcome.NotUpdated(recovery.copy))
                runLoop(recovery.resumeIndex)
            }
        }
    }

    /** Called once `MediaWriteRequester.recoverAndRetry` reports consent: retries only the paused file's persist. */
    fun onRecoveryGranted() {
        val run = deleteRun ?: return
        val recovery = takeRecovery() ?: return
        viewModelScope.launch {
            val copy = recovery.copy
            val persisted = retryPersist(songFileResolver, recovery.song, copy)
            val outcome = if (persisted) CleanupOutcome.Written(copy) else CleanupOutcome.NotUpdated(copy)
            finishFile(run, recovery.song, outcome)
            runLoop(recovery.resumeIndex)
        }
    }

    /** The document the export's save prompt created: writes both lists to it (see the class KDoc). */
    fun onExportDocumentChosen(uri: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val exported = succeeds {
                customTagListDocuments.write(uri, CustomTagListJson.encode(customTagRepository.exportLists()))
            }
            val message = if (exported) TAGS_EXPORTED else COULD_NOT_EXPORT
            _uiState.update { it.copy(isBusy = false, message = message) }
        }
    }

    /** The document the import's open prompt picked: reads and previews it, then asks to confirm (see class KDoc). */
    fun onImportDocumentChosen(uri: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val confirm = resultOrNull {
                CustomTagListJson.decode(customTagListDocuments.read(uri))
                    ?.let { file -> ImportConfirm(file, customTagRepository.previewImport(file)) }
            }
            _uiState.update {
                if (confirm == null) {
                    it.copy(isBusy = false, message = COULD_NOT_READ)
                } else {
                    it.copy(isBusy = false, importConfirm = confirm)
                }
            }
        }
    }

    /** "Import" in the confirmation: replaces both lists with the file's. */
    fun onImportConfirm() {
        val state = _uiState.value
        val confirm = state.importConfirm ?: return
        if (state.isBusy) return
        _uiState.update { it.copy(importConfirm = null, isBusy = true) }
        viewModelScope.launch {
            // applyImport refuses only a file the codec would have refused; it then writes nothing.
            val imported = succeeds { customTagRepository.applyImport(confirm.file) }
            val message = if (imported) TAGS_IMPORTED else COULD_NOT_READ
            _uiState.update { it.copy(isBusy = false, message = message) }
        }
    }

    /** "Cancel" in the import confirmation: closes it and changes nothing. */
    fun onImportDismiss() {
        _uiState.update { it.copy(importConfirm = null) }
    }

    /** Clears a shown [ManageCustomTagsUiState.message]. */
    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Same rationale as TagEditorViewModel.onCleared: the scope is already cancelled, so delete synchronously. */
    override fun onCleared() {
        activeEditableCopy?.delete()
        activeEditableCopy = null
    }

    /** The paused file's recovery, clearing the prompt; null when no recovery prompt is pending. */
    private fun takeRecovery(): DeleteRecovery? {
        val recovery = pendingRecovery
        if (recovery == null || _uiState.value.recoveryRequest == null) return null
        pendingRecovery = null
        _uiState.update { it.copy(recoveryRequest = null) }
        return recovery
    }

    /** Closes the dialog on a saved name (or a rename whose entry is gone), else shows the reason. */
    private fun applyNameResult(result: CustomTagNameResult) {
        val error = result.refusalMessage()
        _uiState.update {
            if (error == null) {
                it.copy(nameDialog = null, renaming = null)
            } else {
                it.copy(nameDialog = it.nameDialog?.copy(error = error))
            }
        }
    }

    /**
     * Cleans the delete's files from [startIndex] on, one at a time, past any per-file failure. Returns early, without
     * finishing, when a file needs API 29 recovery; [pendingRecovery] then marks where to resume.
     */
    private suspend fun runLoop(startIndex: Int) {
        val run = deleteRun ?: return
        var index = startIndex
        while (index < run.songs.size) {
            val song = run.songs[index]
            val outcome = attempt(song, run.tag, isApi29 = sdkIntProvider() == Build.VERSION_CODES.Q)
            if (outcome is CleanupOutcome.NeedsRecovery) {
                pendingRecovery = DeleteRecovery(song, outcome.copy, resumeIndex = index + 1)
                _uiState.update { it.copy(recoveryRequest = outcome.exception) }
                return
            }
            finishFile(run, song, outcome)
            index++
        }
        finishDelete(run)
    }

    /** Deletes the file's copy, tallies it as updated or not, and republishes the progress. */
    private suspend fun finishFile(run: DeleteRun, song: Song, outcome: CleanupOutcome) {
        val copy = when (outcome) {
            is CleanupOutcome.Written -> outcome.copy.also {
                run.written += song
                run.updated++
            }
            is CleanupOutcome.Unchanged -> outcome.copy.also { run.updated++ }
            is CleanupOutcome.NotUpdated -> outcome.copy
            is CleanupOutcome.NeedsRecovery -> error("NeedsRecovery is handled by runLoop")
        }
        copy?.let {
            songFileResolver.deleteEditableCopy(it)
            if (activeEditableCopy == it) activeEditableCopy = null
        }
        run.done++
        _uiState.update { it.copy(progress = BatchProgress(done = run.done, total = run.songs.size)) }
    }

    /** Deletes the entry and its links, re-indexes the written songs, then reports a partial result. */
    private suspend fun finishDelete(run: DeleteRun) {
        customTagRepository.delete(run.tag.kind, run.tag.id)
        tagSaveFinalizer.onSongsSaved(run.written.toList())
        val message = if (run.updated < run.total) "Updated ${run.updated} of ${run.total} tracks." else null
        _uiState.update { it.copy(isBusy = false, progress = null, message = message) }
        deleteRun = null
    }

    /**
     * One file's copy, clean-up and persist. The copy is tracked the instant it exists, so a teardown mid-attempt can
     * still delete it. One try/catch covers every step, as SelectionArtworkViewModel's attempt does;
     * `RecoverableSecurityException` (API 29+) is caught unconditionally, which is unreachable below API 29 (see
     * TagEditorViewModel.persistAndFinish).
     */
    @Suppress("NewApi", "TooGenericExceptionCaught")
    private suspend fun attempt(song: Song, tag: CustomTag, isApi29: Boolean): CleanupOutcome {
        var copy: File? = null
        return try {
            val created = songFileResolver.createEditableCopy(song).also { activeEditableCopy = it }
            copy = created
            when {
                !canWriteCustomTags(created.extension) -> CleanupOutcome.NotUpdated(created)
                !removeId(created, tag) -> CleanupOutcome.Unchanged(created)
                else -> {
                    songFileResolver.persistEditedCopy(song, created)
                    CleanupOutcome.Written(created)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecoverableSecurityException) {
            val createdCopy = copy
            if (isApi29 && createdCopy != null) {
                CleanupOutcome.NeedsRecovery(createdCopy, e)
            } else {
                CleanupOutcome.NotUpdated(copy)
            }
        } catch (ignored: Exception) {
            CleanupOutcome.NotUpdated(copy)
        }
    }

    /**
     * Rewrites [copy]'s `ROLLA_TAGS` field without [tag]'s ID, keeping every other ID, known or not. Returns false,
     * writing nothing, when the field does not carry the ID. Throws what [RollaTagsFileIo] throws.
     */
    private suspend fun removeId(copy: File, tag: CustomTag): Boolean = withContext(ioDispatcher) {
        val inFile = rollaTagsFileIo.read(copy).customTagIds
        val kept = inFile.minusId(tag.kind, tag.id)
        if (kept == inFile) {
            false
        } else {
            rollaTagsFileIo.writeCustomTagIds(copy, kept)
            true
        }
    }
}

/** [kind]'s entries in library order with their track counts; empty when the table fails. */
private fun CustomTagRepository.managedTags(kind: CustomTagKind): Flow<List<ManagedTag>> =
    combine(observeCustomTags(kind), observeLinkCounts(kind)) { tags, counts ->
        tags.map { ManagedTag(tag = it, songCount = counts[it.id] ?: 0) }
    }.catch { emit(emptyList()) }

/** True when [block] completes; false when it throws anything but cancellation. */
private suspend fun succeeds(block: suspend () -> Unit): Boolean = resultOrNull { block() } != null

/** [block]'s result, or null when it throws anything but cancellation (a provider's own exceptions included). */
@Suppress("TooGenericExceptionCaught")
private suspend fun <T : Any> resultOrNull(block: suspend () -> T?): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (ignored: Exception) {
    null
}

private const val TAGS_EXPORTED = "Tags exported"
private const val COULD_NOT_EXPORT = "Couldn't export the tags."
private const val COULD_NOT_READ = "Couldn't read that file."
private const val TAGS_IMPORTED = "Tags imported"

/** Retries only the persist step for a file whose copy is already edited (API 29 recovery). */
@Suppress("TooGenericExceptionCaught")
private suspend fun retryPersist(songFileResolver: SongFileResolver, song: Song, copy: File): Boolean = try {
    songFileResolver.persistEditedCopy(song, copy)
    true
} catch (e: CancellationException) {
    throw e
} catch (ignored: Exception) {
    false
}
