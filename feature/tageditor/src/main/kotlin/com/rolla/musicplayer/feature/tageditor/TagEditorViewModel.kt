package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagReader
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

/** One editable field of [SongTags], used by [TagEditorViewModel.onFieldChanged]. */
enum class TagField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, TRACK_NUMBER, COMPOSER }

private const val MAX_YEAR_DIGITS = 4
private const val DENIED_MESSAGE = "Changes weren't saved — permission declined."
private const val GENERIC_FAILURE_MESSAGE = "Couldn't save changes. Please try again."
private const val SONG_NOT_FOUND_MESSAGE = "Song not found."
private const val READ_FAILURE_MESSAGE = "Couldn't read tags for this file."

/**
 * Tag editor screen UI state.
 *
 * [tags] holds the current, possibly-edited field values; [isDirty]/[canSave] are derived from
 * comparing [tags] against the tags most recently loaded from disk (kept privately by the
 * ViewModel, not part of this snapshot -- the screen never needs to render the baseline itself).
 *
 * The consent dance surfaces as three fields that are never meaningfully non-null at the same
 * time:
 * - [consentRequest]: non-null while the screen must call `MediaWriteRequester.ensureWritable`
 *   with these uris (the song's own content:// uri, as a single-element batch) before any byte
 *   is written.
 * - [recoveryRequest]: non-null only on API 29 (Q), when the write itself was rejected and the
 *   screen must call `MediaWriteRequester.recoverAndRetry` with this exact exception.
 * - [isSaving]: true for the whole span from [TagEditorViewModel.onSaveClick] until either a
 *   terminal outcome (saved, denied, or a generic failure) is reached, including while
 *   [consentRequest]/[recoveryRequest] are being resolved by the screen.
 *
 * [isClosed] is the cue for the screen to navigate up: set either because the song no longer
 * exists ([message] explains why) or because the save completed successfully ([isSaved] is true
 * in that case). [message] is a one-shot, screen-dismissed piece of text: call
 * [TagEditorViewModel.dismissMessage] once it has been shown so it does not reappear.
 */
@Immutable
data class TagEditorUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val songTitle: String = "",
    val artistName: String = "",
    val artworkUri: String? = null,
    val tags: SongTags = SongTags(),
    val yearError: String? = null,
    val trackNumberError: String? = null,
    val isDirty: Boolean = false,
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val consentRequest: List<String>? = null,
    val recoveryRequest: RecoverableSecurityException? = null,
    val message: String? = null,
    val isSaved: Boolean = false,
    val isClosed: Boolean = false,
)

/**
 * ViewModel for the single-song tag editor screen (`TagEditor(songId: Long)`).
 *
 * ## Loading
 * [songId] is read off [SavedStateHandle] by its raw route-argument key ("songId"), converted
 * from the route's `Long` to the `String` id [SongRepository]/[Song] use, following the same
 * `SavedStateHandle` convention as `PlaylistDetailViewModel`. On init, the song is looked up with
 * [SongRepository.observeSong] via `first()` -- a one-shot read, not a continuous collection --
 * because the load pipeline that follows (copy the file, read its tags, seed the editable
 * [SongTags]) must run exactly once per screen visit; re-running it on a later, unrelated
 * emission of the same song (e.g. its favourite flag flipping elsewhere) would silently discard
 * in-progress user edits.
 *
 * ## The editable copy
 * [SongFileResolver.createEditableCopy] is called once, right after load, and the resulting
 * [File] is retained in [editableCopy] for the lifetime of this ViewModel (not recreated on every
 * save attempt): ordinary saves and retries after a declined/recovered consent prompt all reuse
 * it. It is only ever recreated if a prior attempt already deleted it (see
 * [handleGenericFailure]), and it is always deleted -- via [SongFileResolver.deleteEditableCopy]
 * on the happy path, or a direct, synchronous [File.delete] in [onCleared] -- so no edit ever
 * leaves a stray temp file in the app cache.
 *
 * ## The consent dance
 * [onSaveClick] never writes a byte itself: it just marks the state as saving and populates
 * [TagEditorUiState.consentRequest]. The actual write happens only from [onConsentGranted],
 * called by the screen after `MediaWriteRequester.ensureWritable` reports success. This is what
 * makes "never write before consent" true of this class, the same way it is true of
 * `MediaWriteRequester` itself (see its KDoc). [onConsentDenied] handles a decline from either
 * that initial prompt or the API 29 recovery prompt below.
 *
 * On API 29 specifically, [SongFileResolver.persistEditedCopy] can still throw
 * [RecoverableSecurityException] even after [onConsentGranted] proceeds (there is no pre-flight
 * consent API on that release -- see [SongFileResolver]'s KDoc). That case surfaces via
 * [TagEditorUiState.recoveryRequest]; the screen resolves it with
 * `MediaWriteRequester.recoverAndRetry` and reports back through [onRecoveryGranted] (retries the
 * persist only -- the tag write into the local copy already happened and does not need
 * repeating) or [onConsentDenied].
 *
 * [sdkIntProvider] exists purely as a test seam: [Build.VERSION.SDK_INT] cannot be made to read
 * as 29 in a plain JVM unit test (there is no Robolectric dependency in this module), so the one
 * branch that must behave differently on API 29 reads the device level through this overridable,
 * non-constructor-injected property instead of the `Build` field directly, mirroring the same
 * `sdkInt`-as-parameter seam `MediaWriteRequester.kt` already uses for
 * `supportsPreflightWriteRequest`. It is `internal` so `TagEditorViewModelTest` (same module) can
 * override it; Hilt never sees it since it is a plain property, not a constructor parameter.
 *
 * No library re-scan/re-index happens after a successful save in this step, deliberately: the
 * scanner's known `@Upsert` favourites/play-count wipe would erase per-song user state on every
 * tag edit, so re-indexing the saved song's row (or triggering a targeted re-sync) is deferred
 * until that scanner bug is fixed, rather than wired in now.
 */
@HiltViewModel
class TagEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val songFileResolver: SongFileResolver,
    private val tagReader: TagReader,
    private val tagWriter: TagWriter,
) : ViewModel() {

    private val songId: String = checkNotNull(savedStateHandle.get<Long>("songId")) { "songId is required" }.toString()

    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    private val _uiState = MutableStateFlow(TagEditorUiState())
    val uiState: StateFlow<TagEditorUiState> = _uiState.asStateFlow()

    private var loadedSong: Song? = null
    private var loadedTags: SongTags? = null
    private var editableCopy: File? = null

    init {
        viewModelScope.launch { loadSong() }
    }

    /** Applies [value] to [field] in the current [SongTags], revalidates, and recomputes dirtiness. */
    fun onFieldChanged(field: TagField, value: String) {
        _uiState.update { current ->
            val updatedTags = current.tags.withField(field, value)
            current.copy(
                tags = updatedTags,
                yearError = validateYear(updatedTags.year),
                trackNumberError = validateTrackNumber(updatedTags.trackNumber),
            ).recomputeSaveState(loadedTags)
        }
    }

    /**
     * Starts the save flow. A no-op unless [TagEditorUiState.canSave] is true. Never writes
     * anything itself -- only marks saving-in-progress and asks the screen (via
     * [TagEditorUiState.consentRequest]) to obtain write consent first.
     */
    fun onSaveClick() {
        val song = loadedSong ?: return
        if (!_uiState.value.canSave) return
        _uiState.update { it.copy(isSaving = true, consentRequest = listOf(song.contentUri)) }
    }

    /** Called once the screen's `MediaWriteRequester.ensureWritable` reports consent granted. */
    fun onConsentGranted() {
        val song = loadedSong ?: return
        if (_uiState.value.consentRequest == null) return
        _uiState.update { it.copy(consentRequest = null) }
        viewModelScope.launch { writeAndPersist(song) }
    }

    /** Called on a declined initial consent prompt, or a declined API 29 recovery prompt. */
    fun onConsentDenied() {
        val state = _uiState.value
        if (state.consentRequest == null && state.recoveryRequest == null) return
        _uiState.update {
            it.copy(consentRequest = null, recoveryRequest = null, isSaving = false, message = DENIED_MESSAGE)
        }
    }

    /** Called once the screen's `MediaWriteRequester.recoverAndRetry` reports consent granted. */
    fun onRecoveryGranted() {
        val song = loadedSong
        val copy = editableCopy
        if (song == null || copy == null || _uiState.value.recoveryRequest == null) return
        _uiState.update { it.copy(recoveryRequest = null) }
        viewModelScope.launch { persistAndFinish(song, copy) }
    }

    /** Clears a shown [TagEditorUiState.message] so it is not re-displayed. */
    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Deletes the retained editable copy synchronously. Called after [viewModelScope] is already
     * cancelled (see [androidx.lifecycle.ViewModel.clear]'s ordering), so the suspending
     * [SongFileResolver.deleteEditableCopy] cannot be launched here: a direct [File.delete] is
     * the pragmatic fallback for this single, fast, local file-system operation.
     */
    override fun onCleared() {
        editableCopy?.delete()
        editableCopy = null
    }

    private suspend fun loadSong() {
        val song = songRepository.observeSong(songId).first()
        if (song == null) {
            _uiState.update { it.copy(isLoading = false, isClosed = true, message = SONG_NOT_FOUND_MESSAGE) }
            return
        }
        loadedSong = song
        _uiState.update { it.copy(songTitle = song.title, artistName = song.artist, artworkUri = song.artworkUri) }
        try {
            val copy = songFileResolver.createEditableCopy(song)
            editableCopy = copy
            val tags = tagReader.read(copy)
            loadedTags = tags
            _uiState.update { it.copy(isLoading = false, tags = tags).recomputeSaveState(tags) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            _uiState.update { it.copy(isLoading = false, loadFailed = true, message = READ_FAILURE_MESSAGE) }
        }
    }

    private suspend fun writeAndPersist(song: Song) {
        val copy = ensureEditableCopy(song)
        if (copy == null) {
            handleGenericFailure(null)
            return
        }
        try {
            tagWriter.write(copy, _uiState.value.tags)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            handleGenericFailure(copy)
            return
        }
        persistAndFinish(song, copy)
    }

    /**
     * Catching [RecoverableSecurityException] (an API 29+ type) unconditionally, even though this
     * module's minSdk is 24, is the same defensive pattern the implement-tag-editor skill
     * documents for this exact scenario: on API <=28 [SongFileResolver.persistEditedCopy] can
     * never actually throw it (the codepath that constructs it doesn't exist below API 29), so
     * this catch clause is unreachable dead code there rather than a live crash risk -- the
     * platform lazily resolves a catch clause's exception type only if a matching exception is
     * actually thrown through it. `@Suppress("NewApi")` silences lint's static (and, here,
     * overly conservative) flag on that same clause.
     */
    @Suppress("NewApi")
    private suspend fun persistAndFinish(song: Song, copy: File) {
        try {
            songFileResolver.persistEditedCopy(song, copy)
            finishSaved(copy)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecoverableSecurityException) {
            if (sdkIntProvider() == Build.VERSION_CODES.Q) {
                _uiState.update { it.copy(recoveryRequest = e) }
            } else {
                handleGenericFailure(copy)
            }
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            handleGenericFailure(copy)
        }
    }

    private suspend fun finishSaved(copy: File) {
        songFileResolver.deleteEditableCopy(copy)
        editableCopy = null
        _uiState.update { it.copy(isSaving = false, isSaved = true, isClosed = true) }
    }

    private suspend fun handleGenericFailure(copy: File?) {
        copy?.let { songFileResolver.deleteEditableCopy(it) }
        editableCopy = null
        _uiState.update {
            it.copy(isSaving = false, consentRequest = null, recoveryRequest = null, message = GENERIC_FAILURE_MESSAGE)
        }
    }

    private suspend fun ensureEditableCopy(song: Song): File? {
        editableCopy?.let { return it }
        return try {
            songFileResolver.createEditableCopy(song).also { editableCopy = it }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            null
        }
    }
}

private fun validateYear(year: String): String? = when {
    year.isBlank() -> null
    year.length > MAX_YEAR_DIGITS || !year.all { it.isDigit() } -> "Year must be blank or up to 4 digits."
    else -> null
}

private fun validateTrackNumber(trackNumber: String): String? = when {
    trackNumber.isBlank() -> null
    !trackNumber.all { it.isDigit() } -> "Track number must be blank or digits only."
    else -> null
}

private fun SongTags.withField(field: TagField, value: String): SongTags = when (field) {
    TagField.TITLE -> copy(title = value)
    TagField.ARTIST -> copy(artist = value)
    TagField.ALBUM -> copy(album = value)
    TagField.ALBUM_ARTIST -> copy(albumArtist = value)
    TagField.GENRE -> copy(genre = value)
    TagField.YEAR -> copy(year = value)
    TagField.TRACK_NUMBER -> copy(trackNumber = value)
    TagField.COMPOSER -> copy(composer = value)
}

/**
 * Recomputes [TagEditorUiState.isDirty]/[TagEditorUiState.canSave] against [loadedTags] (the
 * baseline captured right after a successful load): called after every field edit and once
 * right after a successful load completes.
 */
private fun TagEditorUiState.recomputeSaveState(loadedTags: SongTags?): TagEditorUiState {
    // dirty is only ever true when loadedTags is non-null (see the condition below), so it alone
    // is sufficient to gate canSave -- no separate "loadedTags != null" check is needed here.
    val dirty = loadedTags != null && tags != loadedTags
    val hasErrors = yearError != null || trackNumberError != null
    return copy(isDirty = dirty, canSave = dirty && !hasErrors && !isSaving && !loadFailed)
}
