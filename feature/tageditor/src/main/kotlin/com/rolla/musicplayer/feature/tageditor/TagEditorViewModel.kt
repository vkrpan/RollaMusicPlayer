package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.feature.tageditor.io.ArtworkLoader
import com.rolla.musicplayer.feature.tageditor.io.PickedArtwork
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagReader
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
private const val GENERIC_FAILURE_MESSAGE = "Couldn't save changes. Please try again."
private const val SONG_NOT_FOUND_MESSAGE = "Track not found."
private const val READ_FAILURE_MESSAGE = "Couldn't read tags for this file."
private const val ARTWORK_LOAD_FAILURE_MESSAGE = "Couldn't use that image as artwork."

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
    /**
     * Local image uri the user picked as replacement artwork, once it has been validated by
     * `ArtworkLoader` -- non-null means "there is artwork waiting to be written on Save", and the
     * header previews this uri instead of [artworkUri]. Cleared only by leaving the screen; like
     * field edits, an artwork pick is discarded by Cancel and committed by Save. Cleared by
     * [TagEditorViewModel.onRemoveArtworkClick] (the last artwork choice wins).
     */
    val pendingArtworkUri: String? = null,
    /**
     * True once the user chose "Remove artwork": Save deletes every embedded picture from the file and the header
     * previews the placeholder. Cleared by a later artwork pick (the last choice wins).
     */
    val isArtworkRemoved: Boolean = false,
    /**
     * Whether "Remove artwork" is offered: the file supports artwork, no removal is pending, and there is a picture to
     * remove -- one in the file or a pending pick.
     */
    val canRemoveArtwork: Boolean = false,
    /**
     * Whether the file's format can carry artwork (MP3, FLAC, M4A; spec goal 9). When false the header offers neither
     * "Change artwork" nor "Remove artwork".
     */
    val isArtworkSupported: Boolean = true,
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
) {
    /** What the header previews: the placeholder (`""`) once removed, else the pending pick, else the song's own. */
    val previewArtworkUri: String
        get() = if (isArtworkRemoved) "" else pendingArtworkUri ?: artworkUri.orEmpty()
}

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
 * ## Artwork replacement
 * [onArtworkPicked] receives a LOCAL image uri from the screen's PhotoPicker launcher, validates
 * and loads it via [ArtworkLoader] (bad picks surface as a message, never a crash), and holds the
 * loaded bytes privately until Save -- an artwork pick alone marks the state dirty exactly like a
 * field edit, and rides the same consent-then-write flow: [writeAndPersist] writes the fields and
 * then embeds the pending artwork into the same editable copy before the single persist. Cancel
 * discards it along with everything else. The batch editor screen has no artwork fields; setting or
 * removing artwork for many tracks lives in the Tracks tab's selection menu (SelectionArtworkViewModel).
 *
 * [onRemoveArtworkClick] is the opposite choice: Save then deletes every embedded picture from the
 * copy ([TagWriter.removeArtwork]) after the field write, instead of embedding one. A pick and a
 * removal each cancel the other, so the last choice wins. Whether the file holds a picture is read
 * once at load ([TagReader.hasArtwork]; a failure there reads as "no picture" and never fails the
 * load), and neither action is offered for a format that cannot carry artwork
 * ([TagEditorUiState.isArtworkSupported], MP3/FLAC/M4A only).
 *
 * ## After a successful save
 * [TagSaveFinalizer.onSongsSaved] runs after the persist succeeds and BEFORE
 * [TagEditorUiState.isSaved]/[TagEditorUiState.isClosed] are set: it notifies MediaStore of the
 * changed file, targeted-re-syncs the song's Room row (the indexer's merge preserves
 * favourite/play-count state), and refreshes the now-playing metadata if this song is current.
 * Ordering it before the close cue is deliberate -- the work runs in [viewModelScope], so closing
 * the screen first would cancel it mid-flight and return the user to a stale library list. It is
 * best-effort: a re-index failure never turns the already-successful file write into a
 * user-facing save error (see [TagSaveFinalizer]'s KDoc).
 */
// LongParameterList: Hilt constructor injection; each collaborator is a distinct IO concern, a holder would hide it.
// TooManyFunctions: one public handler per screen event plus the save pipeline's steps, all one consent flow.
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class TagEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val songFileResolver: SongFileResolver,
    private val tagReader: TagReader,
    private val tagWriter: TagWriter,
    private val artworkLoader: ArtworkLoader,
    private val tagSaveFinalizer: TagSaveFinalizer,
) : ViewModel() {

    private val songId: String = checkNotNull(savedStateHandle.get<Long>("songId")) { "songId is required" }.toString()

    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    private val _uiState = MutableStateFlow(TagEditorUiState())
    val uiState: StateFlow<TagEditorUiState> = _uiState.asStateFlow()

    private var loadedSong: Song? = null
    private var loadedTags: SongTags? = null
    private var editableCopy: File? = null
    private var pendingArtwork: PickedArtwork? = null

    /** Whether the loaded file itself embeds a picture (false when the check failed; see [hasArtworkOrFalse]). */
    private var fileHasArtwork: Boolean = false

    /** Bumped by every pick and every Remove; a pick's load applies only if it is still the latest (ruling P21). */
    private var pickGeneration: Int = 0

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
            ).recomputeSaveState(loadedTags, fileHasArtwork)
        }
    }

    /**
     * Receives a LOCAL image uri from the screen's PhotoPicker launcher and loads it as pending
     * artwork -- written only on Save, alongside the field edits. An unusable pick (unreadable,
     * not an image, oversized -- see [ArtworkLoader]) surfaces as a message and changes nothing.
     * Ignored while a save is in flight or when the baseline never loaded (no trustworthy state
     * to edit -- same gate [TagEditorUiState.canSave] applies to field edits), and for a file
     * whose format cannot carry artwork ([TagEditorUiState.isArtworkSupported]). A usable pick
     * cancels a pending "Remove artwork" (the last artwork choice wins).
     *
     * The last tap wins (ruling P21): the load applies only if no later pick or Remove happened while it ran
     * ([pickGeneration]) and no save is running when it completes; otherwise its result (image or failure) is dropped.
     */
    fun onArtworkPicked(imageUri: String) {
        val state = _uiState.value
        val isEditable = !state.isSaving && !state.loadFailed && loadedTags != null
        if (!isEditable || !state.isArtworkSupported) return
        val generation = ++pickGeneration
        viewModelScope.launch {
            val artwork = try {
                artworkLoader.load(imageUri)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                null
            }
            if (generation != pickGeneration || _uiState.value.isSaving) return@launch
            if (artwork == null) {
                _uiState.update { it.copy(message = ARTWORK_LOAD_FAILURE_MESSAGE) }
            } else {
                pendingArtwork = artwork
                _uiState.update {
                    it.copy(pendingArtworkUri = imageUri, isArtworkRemoved = false)
                        .recomputeSaveState(loadedTags, fileHasArtwork)
                }
            }
        }
    }

    /**
     * "Remove artwork": drops any pending pick and marks every embedded picture for removal on
     * Save; the header then previews the placeholder. The last artwork choice wins, so a later
     * [onArtworkPicked] cancels the removal. Removing the file's own picture is an edit (dirty);
     * removing only a pick from a picture-less file returns to clean. Ignored while saving or when
     * [TagEditorUiState.canRemoveArtwork] is false.
     */
    fun onRemoveArtworkClick() {
        val state = _uiState.value
        if (state.isSaving || !state.canRemoveArtwork) return
        pickGeneration++ // a pick still loading is older than this tap (ruling P21)
        pendingArtwork = null
        _uiState.update {
            it.copy(pendingArtworkUri = null, isArtworkRemoved = true).recomputeSaveState(loadedTags, fileHasArtwork)
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
            val isArtworkSupported = copy.supportsArtwork()
            fileHasArtwork = isArtworkSupported && tagReader.hasArtworkOrFalse(copy)
            _uiState.update {
                it.copy(isLoading = false, tags = tags, isArtworkSupported = isArtworkSupported)
                    .recomputeSaveState(tags, fileHasArtwork)
            }
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
            // A pick and a removal never coexist (each clears the other), so at most one of these runs.
            val artwork = pendingArtwork
            when {
                artwork != null -> tagWriter.writeArtwork(copy, artwork)
                _uiState.value.isArtworkRemoved -> tagWriter.removeArtwork(copy)
            }
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
            finishSaved(song, copy)
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

    private suspend fun finishSaved(song: Song, copy: File) {
        songFileResolver.deleteEditableCopy(copy)
        editableCopy = null
        // Re-index + now-playing refresh must finish before isClosed tears this scope down --
        // see this class's "After a successful save" KDoc section.
        tagSaveFinalizer.onSongsSaved(listOf(song))
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

/**
 * [TagReader.hasArtwork] for [file], with any failure other than cancellation read as "no picture": a picture the
 * reader cannot inspect must not fail the whole load (the fields still edit; only "Remove artwork" is withheld).
 */
private suspend fun TagReader.hasArtworkOrFalse(file: File): Boolean = try {
    hasArtwork(file)
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
    false
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
 * baseline captured right after a successful load) and [TagEditorUiState.canRemoveArtwork]:
 * called after every field edit, after a validated artwork pick, after "Remove artwork", and once
 * right after a successful load completes. A pending artwork pick counts as dirty exactly like a
 * field edit -- it too is only written on Save -- and so does removing a picture the file holds
 * ([fileHasArtwork]); removing only a pick from a picture-less file changes nothing on disk.
 */
private fun TagEditorUiState.recomputeSaveState(loadedTags: SongTags?, fileHasArtwork: Boolean): TagEditorUiState {
    // dirty is only ever true when loadedTags is non-null (see the condition below), so it alone
    // is sufficient to gate canSave -- no separate "loadedTags != null" check is needed here.
    val artworkChanged = pendingArtworkUri != null || (isArtworkRemoved && fileHasArtwork)
    val dirty = loadedTags != null && (tags != loadedTags || artworkChanged)
    val hasErrors = yearError != null || trackNumberError != null
    val canRemove = isArtworkSupported && !isArtworkRemoved && (fileHasArtwork || pendingArtworkUri != null)
    return copy(
        isDirty = dirty,
        canSave = dirty && !hasErrors && !isSaving && !loadFailed,
        canRemoveArtwork = canRemove,
    )
}
