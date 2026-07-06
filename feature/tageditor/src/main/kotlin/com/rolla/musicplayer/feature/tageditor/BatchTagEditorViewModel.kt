package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val DENIED_MESSAGE = "Changes weren't saved — permission declined."
private const val NO_SONGS_MESSAGE = "None of the selected songs could be found."
private const val SONG_IDS_KEY = "songIds"

/** Where BatchTagEditorViewModel's per-song loop paused, waiting on an API 29 recovery prompt. */
private data class PendingRecovery(val song: Song, val copy: File, val resumeIndex: Int)

/** Outcome of attempting a single song's write+persist inside attemptWrite. */
private sealed interface SongWriteOutcome {
    data class Succeeded(val copy: File) : SongWriteOutcome
    data class Failed(val copy: File?) : SongWriteOutcome
    data class NeedsRecovery(val copy: File, val exception: RecoverableSecurityException) : SongWriteOutcome
}

/** The two IO collaborators attemptWrite needs, bundled to keep its parameter list short. */
private data class WriteCollaborators(val songFileResolver: SongFileResolver, val tagWriter: TagWriter)

/**
 * ViewModel for the batch tag editor screen (`BatchTagEditor(songIds: List<Long>)`).
 *
 * ## Reading [songIds]
 * Navigation Compose 2.8's typed routes encode a `List<Long>` route property via
 * `NavType.LongListType`, which stores it in the backing `Bundle` as a `LongArray` (there is no
 * `Bundle.putLongList`) -- so [SavedStateHandle.get] for this key returns a `LongArray` when this
 * ViewModel is actually constructed from real navigation. A plain-map-backed `SavedStateHandle`
 * (the kind `BatchTagEditorViewModelTest` constructs directly, with no `Bundle` involved at all)
 * can just as easily hold a `List<Long>` or any other collection type verbatim. [songIds] (the
 * private extension below) handles both, plus a couple of unlikely-but-cheap-to-support shapes
 * (`Array<*>`, element types boxed as `Int`/`String`), rather than assuming one specific runtime
 * type.
 *
 * ## Deferred loading (unlike the single-song editor)
 * [TagEditorViewModel] loads its song and its tags in `init` because it needs to show the current
 * values. This ViewModel does not: batch-editing N songs has no single "current value" per field
 * to show (see [BatchTagEditorUiState]'s KDoc), so every field starts blank and nothing needs to be
 * read from any song until there is something to write. Consequently, the actual
 * [Song]/[SongRepository] lookup happens inside [onSaveClick], not at construction time; ids that
 * no longer resolve to a song by then are silently dropped from the batch (not counted as
 * failures -- there is nothing to have failed, the id just doesn't exist anymore), while
 * [BatchTagEditorUiState.songCount] always reflects the original, synchronously-known [songIds]
 * count for display purposes throughout the screen's lifetime.
 *
 * ## The consent dance
 * Identical shape to [TagEditorViewModel]'s (see its KDoc for the full explanation of
 * [BatchTagEditorUiState.consentRequest]/[BatchTagEditorUiState.recoveryRequest]/
 * [BatchTagEditorUiState.isSaving]), except [BatchTagEditorUiState.consentRequest] carries every
 * resolved song's `content://` uri as ONE list -- [onSaveClick] requests write consent for the
 * whole batch in a single prompt, never once per file.
 *
 * ## The per-song loop and API 29 recovery
 * [onConsentGranted] starts `runBatch` at index 0, which processes the resolved songs one at a
 * time (create editable copy, [TagWriter.writeFields] with only the applied fields,
 * [SongFileResolver.persistEditedCopy]) and keeps going regardless of a per-file failure --
 * continue-on-error, so one bad file never aborts the rest of the batch. The one thing that DOES
 * pause the loop is `RecoverableSecurityException` on API 29 specifically (same platform quirk
 * [TagEditorViewModel] documents: there is no pre-flight consent API on Q, so the write itself is
 * the real gate): the loop records where it paused and surfaces
 * [BatchTagEditorUiState.recoveryRequest]. [onRecoveryGranted] retries only that one song's persist
 * (the tag write into its local copy already happened) then resumes the loop from the saved index.
 * [onConsentDenied] while a recovery prompt is pending is a deliberate, narrower decision than the
 * initial-consent decline: it counts only THAT song as failed and resumes the loop for the rest --
 * a user declining recovery for one stubborn file should not cost every other file in the batch,
 * unlike declining the batch's one upfront consent prompt (which means nothing has been written yet
 * at all, so the whole batch aborts cleanly).
 *
 * ## Completion: full success vs partial failure
 * These two terminal outcomes deliberately surface differently, mirroring how
 * [TagEditorViewModel] itself distinguishes its happy path from its generic-failure path:
 * - **All songs succeeded**: [BatchTagEditorUiState.isClosed] is set with no
 *   [BatchTagEditorUiState.message] -- there is nothing the user needs to read, so the screen can
 *   navigate away immediately, same as [TagEditorViewModel]'s happy path.
 * - **One or more songs failed**: [BatchTagEditorUiState.message] is set to "Edited X of N songs."
 *   and [BatchTagEditorUiState.isClosed] is left false. This mirrors [TagEditorViewModel]'s
 *   generic-IO-failure path (message shown, screen stays open) rather than its "song not found"
 *   path (message shown AND closed at once) -- because unlike a same-screen snackbar racing a
 *   same-screen navigation-away, this message is the ONLY signal the user gets that some of their
 *   edits did not take; setting [BatchTagEditorUiState.isClosed] at the same instant risks the
 *   screen tearing down before the message is actually read. The user dismisses it via
 *   [dismissMessage] and backs out manually (the screen's existing Cancel/back action) once
 *   acknowledged.
 *
 * ## Cleanup
 * `activeEditableCopy` tracks whichever temp file is currently "in flight" (assigned the instant
 * [SongFileResolver.createEditableCopy] returns, cleared the instant it is deleted or handed off)
 * so [onCleared] can always find and delete it even if this ViewModel is torn down mid-batch or
 * while a recovery prompt is pending -- no edit ever leaves a stray temp file in the app cache, the
 * same guarantee [TagEditorViewModel] makes for its single file.
 *
 * `sdkIntProvider` is the same `Build.VERSION.SDK_INT` test seam [TagEditorViewModel] uses, for the
 * same reason (API 29 cannot be simulated in a plain JVM unit test).
 *
 * No library re-scan/re-index happens after a successful save here either, for the same reason
 * documented on [TagEditorViewModel]: re-indexing would currently wipe favourites/play-count.
 */
@HiltViewModel
class BatchTagEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val songFileResolver: SongFileResolver,
    private val tagWriter: TagWriter,
) : ViewModel() {

    private val songIds: List<Long> = savedStateHandle.songIds()

    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    private val _uiState = MutableStateFlow(BatchTagEditorUiState(songCount = songIds.size))
    val uiState: StateFlow<BatchTagEditorUiState> = _uiState.asStateFlow()

    // Populated once, at the start of onSaveClick's coroutine, and cleared again once the batch
    // reaches a terminal outcome (finishBatch, or an aborted initial-consent decline) -- see this
    // class's KDoc on why loading is deferred to save time rather than done in init.
    private var loadedSongs: List<Song> = emptyList()
    private var pendingFields: Map<TagField, String> = emptyMap()
    private var totalCount = 0
    private var succeededCount = 0
    private var failedCount = 0
    private var pendingRecovery: PendingRecovery? = null
    private var activeEditableCopy: File? = null

    /** Applies [value] to [field] and revalidates -- see [recomputeSaveState]. */
    fun onFieldValueChanged(field: TagField, value: String) {
        _uiState.update { current ->
            current.copy(fields = current.fields.with(field) { it.copy(value = value) }).recomputeSaveState()
        }
    }

    /** Flips whether [field] is written at all when this batch is saved -- see [recomputeSaveState]. */
    fun onFieldApplyToggled(field: TagField, applied: Boolean) {
        _uiState.update { current ->
            current.copy(fields = current.fields.with(field) { it.copy(applied = applied) }).recomputeSaveState()
        }
    }

    /**
     * Starts the save flow. A no-op unless [BatchTagEditorUiState.canSave] is true. Resolves
     * [songIds] to actual songs (see this class's KDoc on why that lookup is deferred to here),
     * then -- provided at least one resolved -- marks saving-in-progress and asks the screen (via
     * [BatchTagEditorUiState.consentRequest]) to obtain write consent for the WHOLE batch at once,
     * before any byte is written.
     */
    fun onSaveClick() {
        val state = _uiState.value
        if (!state.canSave) return
        val fields = state.fields.appliedValues()
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val songs = resolveSongs()
            if (songs.isEmpty()) {
                _uiState.update { it.copy(isSaving = false, message = NO_SONGS_MESSAGE, isClosed = true) }
                return@launch
            }
            loadedSongs = songs
            pendingFields = fields
            totalCount = songs.size
            succeededCount = 0
            failedCount = 0
            _uiState.update { it.copy(consentRequest = songs.map(Song::contentUri)) }
        }
    }

    /**
     * Called once the screen's MediaWriteRequester.ensureWritable reports consent granted.
     * [BatchTagEditorUiState.progress] stays null until the first song actually finishes (see
     * [recordDone]) -- it reports songs completed, 1..N, not "about to start".
     */
    fun onConsentGranted() {
        if (_uiState.value.consentRequest == null) return
        _uiState.update { it.copy(consentRequest = null) }
        viewModelScope.launch { runBatch(startIndex = 0) }
    }

    /**
     * Called on a declined consent prompt -- either the initial, whole-batch one, or a per-file
     * API 29 recovery prompt. These two decline the same batch, but at very different costs: see
     * this class's KDoc ("The per-song loop and API 29 recovery") for why an initial decline aborts
     * everything (nothing has been written yet) while a recovery decline only skips one file.
     */
    fun onConsentDenied() {
        val state = _uiState.value
        if (state.consentRequest != null) {
            loadedSongs = emptyList()
            pendingFields = emptyMap()
            _uiState.update {
                it.copy(consentRequest = null, isSaving = false, message = DENIED_MESSAGE)
            }
            return
        }
        val recovery = pendingRecovery
        if (recovery == null || state.recoveryRequest == null) return
        pendingRecovery = null
        _uiState.update { it.copy(recoveryRequest = null) }
        viewModelScope.launch {
            songFileResolver.deleteEditableCopy(recovery.copy)
            clearActiveCopy(recovery.copy)
            recordDone(succeeded = false)
            runBatch(recovery.resumeIndex)
        }
    }

    /** Called once the screen's MediaWriteRequester.recoverAndRetry reports consent granted. */
    fun onRecoveryGranted() {
        val recovery = pendingRecovery ?: return
        if (_uiState.value.recoveryRequest == null) return
        pendingRecovery = null
        _uiState.update { it.copy(recoveryRequest = null) }
        viewModelScope.launch {
            val succeeded = retryPersist(songFileResolver, recovery.song, recovery.copy)
            clearActiveCopy(recovery.copy)
            recordDone(succeeded)
            runBatch(recovery.resumeIndex)
        }
    }

    /** Clears a shown [BatchTagEditorUiState.message] so it is not re-displayed. */
    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Same rationale as TagEditorViewModel.onCleared: delete synchronously, no suspending call. */
    override fun onCleared() {
        activeEditableCopy?.delete()
        activeEditableCopy = null
    }

    /** Resolves [songIds] to songs, preserving order, dropping any id that no longer exists. */
    private suspend fun resolveSongs(): List<Song> {
        val byId = songRepository.observeSongs().first().associateBy { it.id }
        return songIds.mapNotNull { id -> byId[id.toString()] }
    }

    /**
     * Processes the resolved songs from [startIndex] onward, one at a time, continuing past a
     * per-file failure. Returns early -- without reaching [finishBatch] -- the moment a file needs
     * API 29 recovery, leaving [pendingRecovery] set so [onRecoveryGranted]/[onConsentDenied] can
     * resume this exact point later.
     */
    private suspend fun runBatch(startIndex: Int) {
        var index = startIndex
        val collaborators = WriteCollaborators(songFileResolver, tagWriter)
        while (index < loadedSongs.size) {
            val song = loadedSongs[index]
            val isApi29 = sdkIntProvider() == Build.VERSION_CODES.Q
            val outcome = attemptWrite(collaborators, song, pendingFields, isApi29) { activeEditableCopy = it }
            if (outcome is SongWriteOutcome.NeedsRecovery) {
                pendingRecovery = PendingRecovery(song, outcome.copy, resumeIndex = index + 1)
                _uiState.update { it.copy(recoveryRequest = outcome.exception) }
                return
            }
            finishSong(outcome)
            index++
        }
        finishBatch()
    }

    /** Cleans up and records the done-count for a [SongWriteOutcome] that is NOT [SongWriteOutcome.NeedsRecovery]. */
    private suspend fun finishSong(outcome: SongWriteOutcome) {
        when (outcome) {
            is SongWriteOutcome.Succeeded -> {
                songFileResolver.deleteEditableCopy(outcome.copy)
                clearActiveCopy(outcome.copy)
                recordDone(succeeded = true)
            }
            is SongWriteOutcome.Failed -> {
                outcome.copy?.let { copy ->
                    songFileResolver.deleteEditableCopy(copy)
                    clearActiveCopy(copy)
                }
                recordDone(succeeded = false)
            }
            is SongWriteOutcome.NeedsRecovery -> error("NeedsRecovery must be handled by the caller, not finishSong")
        }
    }

    private fun clearActiveCopy(copy: File) {
        if (activeEditableCopy == copy) activeEditableCopy = null
    }

    /** Bumps succeeded/failed counts and republishes [BatchTagEditorUiState.progress]. */
    private fun recordDone(succeeded: Boolean) {
        if (succeeded) succeededCount++ else failedCount++
        _uiState.update { it.copy(progress = BatchProgress(done = succeededCount + failedCount, total = totalCount)) }
    }

    /** Reaches a terminal state once every resolved song has been attempted. */
    private fun finishBatch() {
        val failed = failedCount
        val total = totalCount
        _uiState.update {
            if (failed == 0) {
                it.copy(isSaving = false, progress = null, isClosed = true)
            } else {
                it.copy(isSaving = false, progress = null, message = "Edited ${total - failed} of $total songs.")
            }
        }
        loadedSongs = emptyList()
        pendingFields = emptyMap()
        totalCount = 0
        succeededCount = 0
        failedCount = 0
    }
}

/**
 * Attempts a song's full write+persist: create an editable copy (invoking [onCopyCreated]
 * synchronously the instant it exists, before any further suspension -- see
 * [BatchTagEditorViewModel]'s "Cleanup" KDoc section), write only [fields] via
 * [TagWriter.writeFields], then [SongFileResolver.persistEditedCopy]. A top-level,
 * instance-independent function (rather than a private member of [BatchTagEditorViewModel]) so it
 * does not count against that class's function budget; it takes its collaborators bundled in
 * [collaborators] (rather than as separate parameters) to stay under the function parameter limit.
 *
 * All three steps share ONE try/catch (rather than one per step) so there is exactly one `return`
 * and one non-cancellation catch clause in this function: [copy] is tracked in a local `var` so
 * whichever step fails, the catch clauses can still report which (if any) temp file needs cleanup.
 */
@Suppress("NewApi", "TooGenericExceptionCaught")
private suspend fun attemptWrite(
    collaborators: WriteCollaborators,
    song: Song,
    fields: Map<TagField, String>,
    isApi29: Boolean,
    onCopyCreated: (File) -> Unit,
): SongWriteOutcome {
    var copy: File? = null
    return try {
        val createdCopy = collaborators.songFileResolver.createEditableCopy(song).also(onCopyCreated)
        copy = createdCopy
        collaborators.tagWriter.writeFields(createdCopy, fields)
        collaborators.songFileResolver.persistEditedCopy(song, createdCopy)
        SongWriteOutcome.Succeeded(createdCopy)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RecoverableSecurityException) {
        val createdCopy = copy
        if (isApi29 && createdCopy != null) {
            SongWriteOutcome.NeedsRecovery(createdCopy, e)
        } else {
            SongWriteOutcome.Failed(copy)
        }
    } catch (ignored: Exception) {
        SongWriteOutcome.Failed(copy)
    }
}

/** Retries only the persist step for a song whose write already happened -- see [attemptWrite]. */
@Suppress("TooGenericExceptionCaught")
private suspend fun retryPersist(songFileResolver: SongFileResolver, song: Song, copy: File): Boolean = try {
    songFileResolver.persistEditedCopy(song, copy)
    true
} catch (e: CancellationException) {
    throw e
} catch (ignored: Exception) {
    false
}

/**
 * Reads the songIds route argument robustly rather than assuming one exact runtime type -- see
 * [BatchTagEditorViewModel]'s KDoc ("Reading songIds") for why more than one shape can legitimately
 * arrive here.
 */
private fun SavedStateHandle.songIds(): List<Long> = when (val raw = get<Any?>(SONG_IDS_KEY)) {
    is LongArray -> raw.toList()
    is IntArray -> raw.map { it.toLong() }
    is List<*> -> raw.mapNotNull { it.toLongOrNullFlexible() }
    is Array<*> -> raw.mapNotNull { it.toLongOrNullFlexible() }
    else -> emptyList()
}

private fun Any?.toLongOrNullFlexible(): Long? = when (this) {
    is Long -> this
    is Int -> this.toLong()
    is String -> this.toLongOrNull()
    else -> null
}
