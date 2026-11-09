package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.tracksCountLabel
import com.rolla.musicplayer.feature.tageditor.io.ArtworkLoader
import com.rolla.musicplayer.feature.tageditor.io.PickedArtwork
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagWriter
import com.rolla.musicplayer.feature.tageditor.io.supportsArtwork
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
private const val ARTWORK_LOAD_FAILURE_MESSAGE = "Couldn't use that image as artwork."
private const val NO_SONGS_MESSAGE = "None of the selected tracks could be found."

/**
 * The Tracks selection's artwork write, as the selection bar sees it.
 *
 * - [isWorking]: true from Set's image load or Remove's confirmation until the write ends (or is declined); the bar
 *   shows a progress ring and locks ✕, Back and "Edit tags" meanwhile (spec §5.5: the selection stays until the end).
 * - [consentRequest] / [recoveryRequest]: the one write-permission prompt for every selected file, and API 29's
 *   per-file recovery prompt, resolved by [SelectionArtworkActions] with `MediaWriteRequester`.
 * - [message]: a one-shot line for the host's snackbar; [SelectionArtworkViewModel.dismissMessage] once shown.
 * - [result]: set once a write ends; the host keeps the failed tracks selected (or clears the selection when none
 *   failed), then [SelectionArtworkViewModel.consumeResult]. A declined prompt sets no result: the selection stays.
 */
@Immutable
data class SelectionArtworkUiState(
    val isWorking: Boolean = false,
    val consentRequest: List<String>? = null,
    val recoveryRequest: RecoverableSecurityException? = null,
    val message: String? = null,
    val result: SelectionArtworkResult? = null,
)

/** How a multi-track Set or Remove ended: the ids (Song.id) of the tracks not written, which stay selected. */
data class SelectionArtworkResult(val failedIds: Set<String>)

/** What the write does to every selected file. */
private sealed interface ArtworkEdit {
    class Write(val artwork: PickedArtwork) : ArtworkEdit
    data object Remove : ArtworkEdit
}

/** Where the per-song loop paused, waiting on an API 29 recovery prompt for [song]'s already-edited [copy]. */
private class ArtworkRecovery(val song: Song, val copy: File, val resumeIndex: Int)

/** One song's attempt: written and persisted, not written (with the copy to delete, if any), or paused for API 29. */
private sealed interface ArtworkOutcome {
    class Succeeded(val copy: File) : ArtworkOutcome
    class Failed(val copy: File?) : ArtworkOutcome
    class NeedsRecovery(val copy: File, val exception: RecoverableSecurityException) : ArtworkOutcome
}

/**
 * Set artwork / Remove artwork for the Home Tracks tab's selection (spec goal 5, §5.5; plan Task 8).
 *
 * ## Flow
 * [onSetArtwork] loads the picked image with [ArtworkLoader] (an unusable one ends in "Couldn't use that image as
 * artwork." and nothing else happens); [onRemoveArtwork] runs after the screen's confirmation dialog. Both then resolve
 * the ids to songs (ids no longer in the library are dropped) and ask, through
 * [SelectionArtworkUiState.consentRequest], for ONE write-permission prompt covering every selected file. Nothing is
 * written before [onConsentGranted]; [onConsentDenied] on that prompt writes nothing and keeps the selection.
 *
 * ## The per-song loop and API 29 recovery
 * New code modelled on [BatchTagEditorViewModel]'s loop (ruling R5: not extracted; the batch editor stays untouched).
 * One song at a time: [SongFileResolver.createEditableCopy]; a copy whose format cannot carry artwork
 * ([supportsArtwork]: MP3, FLAC, M4A only, spec goal 9) counts as not written; otherwise [TagWriter.writeArtwork] or
 * [TagWriter.removeArtwork], then [SongFileResolver.persistEditedCopy]; the copy is always deleted. A failure on one
 * file never stops the others. On API 29 a `RecoverableSecurityException` from the persist pauses the loop on that
 * song and surfaces [SelectionArtworkUiState.recoveryRequest]: [onRecoveryGranted] retries only that persist (the copy
 * is already edited) and resumes; declining it ([onConsentDenied]) counts only that song as not written and resumes.
 *
 * ## Completion
 * [TagSaveFinalizer.onSongsSaved] runs once for the written songs, BEFORE the terminal state, since it runs in
 * [viewModelScope]. Then [SelectionArtworkUiState.message] reads "Artwork set on N tracks" / "Artwork removed from N
 * tracks", or "… K of N tracks" when some were not written (N through [tracksCountLabel], so one reads "1 track"),
 * and [SelectionArtworkUiState.result] carries the ids not written.
 *
 * ## Cleanup
 * The copy in flight is tracked so [onCleared] deletes it even mid-loop or while a recovery prompt is pending.
 * `sdkIntProvider` is the same API 29 test seam [TagEditorViewModel] and [BatchTagEditorViewModel] use.
 */
@HiltViewModel
class SelectionArtworkViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val songFileResolver: SongFileResolver,
    private val tagWriter: TagWriter,
    private val artworkLoader: ArtworkLoader,
    private val tagSaveFinalizer: TagSaveFinalizer,
) : ViewModel() {

    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    private val _uiState = MutableStateFlow(SelectionArtworkUiState())
    val uiState: StateFlow<SelectionArtworkUiState> = _uiState.asStateFlow()

    // One write's working set, filled by requestConsent and cleared when the write ends or is declined.
    private var edit: ArtworkEdit? = null
    private var songs: List<Song> = emptyList()
    private val succeededSongs = mutableListOf<Song>()
    private val failedIds = LinkedHashSet<String>()
    private var pendingRecovery: ArtworkRecovery? = null
    private var activeEditableCopy: File? = null

    /**
     * Set artwork with the picked LOCAL image [imageUri] on [songIds]. Ignored while a write is running. An image
     * [ArtworkLoader] cannot use ends here with a message; a usable one leads to the one consent prompt.
     */
    fun onSetArtwork(songIds: List<Long>, imageUri: String) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val artwork = try {
                artworkLoader.load(imageUri)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                null
            }
            if (artwork == null) {
                _uiState.update { it.copy(isWorking = false, message = ARTWORK_LOAD_FAILURE_MESSAGE) }
            } else {
                requestConsent(songIds, ArtworkEdit.Write(artwork))
            }
        }
    }

    /** Remove artwork from [songIds], after the screen's confirmation. Ignored while a write is running. */
    fun onRemoveArtwork(songIds: List<Long>) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch { requestConsent(songIds, ArtworkEdit.Remove) }
    }

    /** Called once `MediaWriteRequester.ensureWritable` reports the one prompt granted: runs the per-song loop. */
    fun onConsentGranted() {
        if (_uiState.value.consentRequest == null) return
        _uiState.update { it.copy(consentRequest = null) }
        viewModelScope.launch { runLoop(startIndex = 0) }
    }

    /**
     * A declined prompt. The initial one writes nothing and keeps the selection (no result); an API 29 recovery
     * decline counts only that song as not written and resumes the loop (see the class KDoc).
     */
    fun onConsentDenied() {
        val state = _uiState.value
        if (state.consentRequest != null) {
            resetWrite()
            _uiState.update { it.copy(consentRequest = null, isWorking = false, message = DENIED_MESSAGE) }
            return
        }
        val recovery = pendingRecovery
        if (recovery == null || state.recoveryRequest == null) return
        pendingRecovery = null
        _uiState.update { it.copy(recoveryRequest = null) }
        viewModelScope.launch {
            finishSong(recovery.song, ArtworkOutcome.Failed(recovery.copy))
            runLoop(recovery.resumeIndex)
        }
    }

    /** Called once `MediaWriteRequester.recoverAndRetry` reports consent: retries only the paused song's persist. */
    fun onRecoveryGranted() {
        val recovery = pendingRecovery ?: return
        if (_uiState.value.recoveryRequest == null) return
        pendingRecovery = null
        _uiState.update { it.copy(recoveryRequest = null) }
        viewModelScope.launch {
            val persisted = retryArtworkPersist(songFileResolver, recovery.song, recovery.copy)
            val copy = recovery.copy
            finishSong(recovery.song, if (persisted) ArtworkOutcome.Succeeded(copy) else ArtworkOutcome.Failed(copy))
            runLoop(recovery.resumeIndex)
        }
    }

    /** Clears a shown [SelectionArtworkUiState.message]. */
    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Clears a delivered [SelectionArtworkUiState.result]. */
    fun consumeResult() {
        _uiState.update { it.copy(result = null) }
    }

    /** Same rationale as TagEditorViewModel.onCleared: the scope is already cancelled, so delete synchronously. */
    override fun onCleared() {
        activeEditableCopy?.delete()
        activeEditableCopy = null
    }

    /** Resolves [songIds] (dropping ids no longer in the library) and asks for the one prompt covering them all. */
    private suspend fun requestConsent(songIds: List<Long>, edit: ArtworkEdit) {
        val byId = songRepository.observeSongs().first().associateBy(Song::id)
        val resolved = songIds.mapNotNull { id -> byId[id.toString()] }
        if (resolved.isEmpty()) {
            // Nothing selected exists any more, so nothing stays selected either.
            _uiState.update {
                it.copy(isWorking = false, message = NO_SONGS_MESSAGE, result = SelectionArtworkResult(emptySet()))
            }
            return
        }
        this.edit = edit
        songs = resolved
        succeededSongs.clear()
        failedIds.clear()
        _uiState.update { it.copy(consentRequest = resolved.map(Song::contentUri)) }
    }

    /**
     * Writes [songs] from [startIndex] on, one at a time, past any per-file failure. Returns early, without finishing,
     * when a file needs API 29 recovery; [pendingRecovery] then marks where to resume.
     */
    private suspend fun runLoop(startIndex: Int) {
        val currentEdit = edit ?: return
        var index = startIndex
        while (index < songs.size) {
            val song = songs[index]
            val isApi29 = sdkIntProvider() == Build.VERSION_CODES.Q
            val outcome = attempt(song, currentEdit, isApi29)
            if (outcome is ArtworkOutcome.NeedsRecovery) {
                pendingRecovery = ArtworkRecovery(song, outcome.copy, resumeIndex = index + 1)
                _uiState.update { it.copy(recoveryRequest = outcome.exception) }
                return
            }
            finishSong(song, outcome)
            index++
        }
        finishWrite(currentEdit)
    }

    /** Deletes the song's copy and records it as written or not. */
    private suspend fun finishSong(song: Song, outcome: ArtworkOutcome) {
        val copy = when (outcome) {
            is ArtworkOutcome.Succeeded -> outcome.copy.also { succeededSongs += song }
            is ArtworkOutcome.Failed -> outcome.copy.also { failedIds += song.id }
            is ArtworkOutcome.NeedsRecovery -> error("NeedsRecovery is handled by runLoop")
        }
        copy?.let {
            songFileResolver.deleteEditableCopy(it)
            if (activeEditableCopy == it) activeEditableCopy = null
        }
    }

    /** Re-indexes the written songs, then publishes the message and the ids not written. */
    private suspend fun finishWrite(currentEdit: ArtworkEdit) {
        tagSaveFinalizer.onSongsSaved(succeededSongs.toList())
        val message = currentEdit.message(written = succeededSongs.size, total = songs.size)
        val result = SelectionArtworkResult(failedIds.toSet())
        _uiState.update { it.copy(isWorking = false, message = message, result = result) }
        resetWrite()
    }

    private fun resetWrite() {
        edit = null
        songs = emptyList()
        succeededSongs.clear()
        failedIds.clear()
    }

    /**
     * One song's copy, edit and persist. The copy is tracked the instant it exists, so a teardown mid-attempt can still
     * delete it. A copy whose format cannot carry artwork is not written. One try/catch covers every step, as
     * BatchTagEditorViewModel's attemptWrite does; `RecoverableSecurityException` (API 29+) is caught unconditionally,
     * which is unreachable below API 29 (see TagEditorViewModel.persistAndFinish).
     */
    @Suppress("NewApi", "TooGenericExceptionCaught")
    private suspend fun attempt(song: Song, edit: ArtworkEdit, isApi29: Boolean): ArtworkOutcome {
        var copy: File? = null
        return try {
            val created = songFileResolver.createEditableCopy(song).also { activeEditableCopy = it }
            copy = created
            if (created.supportsArtwork()) {
                when (edit) {
                    is ArtworkEdit.Write -> tagWriter.writeArtwork(created, edit.artwork)
                    ArtworkEdit.Remove -> tagWriter.removeArtwork(created)
                }
                songFileResolver.persistEditedCopy(song, created)
                ArtworkOutcome.Succeeded(created)
            } else {
                ArtworkOutcome.Failed(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecoverableSecurityException) {
            val createdCopy = copy
            if (isApi29 && createdCopy != null) {
                ArtworkOutcome.NeedsRecovery(createdCopy, e)
            } else {
                ArtworkOutcome.Failed(copy)
            }
        } catch (ignored: Exception) {
            ArtworkOutcome.Failed(copy)
        }
    }
}

/** Spec §5.7's copy: "Artwork set on N tracks", "Artwork removed from K of N tracks", N via [tracksCountLabel]. */
private fun ArtworkEdit.message(written: Int, total: Int): String {
    val count = if (written == total) tracksCountLabel(total) else "$written of ${tracksCountLabel(total)}"
    return when (this) {
        is ArtworkEdit.Write -> "Artwork set on $count"
        ArtworkEdit.Remove -> "Artwork removed from $count"
    }
}

/** Retries only the persist step for a song whose copy is already edited (API 29 recovery). */
@Suppress("TooGenericExceptionCaught")
private suspend fun retryArtworkPersist(songFileResolver: SongFileResolver, song: Song, copy: File): Boolean = try {
    songFileResolver.persistEditedCopy(song, copy)
    true
} catch (e: CancellationException) {
    throw e
} catch (ignored: Exception) {
    false
}
