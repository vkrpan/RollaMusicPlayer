package com.rolla.musicplayer.feature.tageditor

import android.app.RecoverableSecurityException
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.tagio.RollaTagsFileIo
import com.rolla.musicplayer.core.tagio.canWriteCustomTags
import com.rolla.musicplayer.core.tagio.isCustomTagExtension
import com.rolla.musicplayer.core.ui.customtags.CustomTagNameDialogState
import com.rolla.musicplayer.core.ui.customtags.CustomTagPickerState
import com.rolla.musicplayer.feature.tageditor.io.ArtworkLoader
import com.rolla.musicplayer.feature.tageditor.io.PickedArtwork
import com.rolla.musicplayer.feature.tageditor.io.SongFileResolver
import com.rolla.musicplayer.feature.tageditor.io.TagEditorIoDispatcher
import com.rolla.musicplayer.feature.tageditor.io.TagReader
import com.rolla.musicplayer.feature.tageditor.io.TagWriter
import com.rolla.musicplayer.feature.tageditor.io.supportsArtwork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /**
     * Whether Save can write the file's custom tags: a format `canWriteCustomTags` accepts (MP3 and FLAC) whose field
     * could be read at load. When false the Tags and Subtags rows show what the file carries (nothing, when the field
     * could not be read), but are disabled and say why.
     */
    val customTagsEditable: Boolean = true,
    /**
     * The custom tag picker: both lists in library order, the ticked IDs (known IDs only; an ID the lists do not know
     * is never shown and is kept on Save), the open page and each page's search text. The Tags and Subtags rows show
     * the names of the ticked entries.
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
 * ## Custom tags
 * Both lists are observed from [CustomTagRepository] for the screen's lifetime ([CustomTagEditingHandlers], shared with
 * the batch editor), so an entry created here appears in the picker at once. At load, after the fields, the copy's
 * `ROLLA_TAGS` field is read with [RollaTagsFileIo] (only for a format the scan reads it from, MP3, FLAC and M4A; any
 * other carries none); the IDs the lists know become the picker's ticks and the baseline [loadedCustomTagIds]. A tick
 * that differs from that baseline is an edit, like a field edit. The rows are editable only for a format
 * [canWriteCustomTags] accepts (MP3 and FLAC) whose field could be read; an M4A shows its tags read-only, and a field
 * that cannot be read never fails the load but disables the rows (ruling 8). A failing custom tag table reads as
 * empty lists rather than crashing the editor.
 *
 * Save writes the field only when the ticks differ from the baseline (ruling 7), so a save of other fields leaves it
 * byte for byte as it was. It runs after the fields and the artwork, on the same copy, before the single persist: the
 * ticked IDs plus the IDs in the copy's field that the lists do not know, re-read from the copy at save time, so a tag
 * this app cannot name is never dropped. A failure there is the ordinary save failure.
 *
 * "New tag" in the picker opens the name dialog ([TagEditorUiState.nameDialog]) with the search text; a saved entry
 * closes it and is ticked, a refused name keeps it open with the reason.
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
    private val customTagRepository: CustomTagRepository,
    private val rollaTagsFileIo: RollaTagsFileIo,
    @TagEditorIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val songId: String = checkNotNull(savedStateHandle.get<Long>("songId")) { "songId is required" }.toString()

    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    private val _uiState = MutableStateFlow(TagEditorUiState())
    val uiState: StateFlow<TagEditorUiState> = _uiState.asStateFlow()

    private var loadedSong: Song? = null
    private var loadedTags: SongTags? = null
    private var editableCopy: File? = null
    private var pendingArtwork: PickedArtwork? = null

    /**
     * The custom tag IDs read from the copy at load that the lists know: the picker's ticks at load and the baseline
     * the dirty check compares them with. Null until the load completes. Unknown IDs are not kept here; Save re-reads
     * them from the copy.
     */
    private var loadedCustomTagIds: CustomTagIds? = null

    /** Whether the loaded file itself embeds a picture (false when the check failed; see [hasArtworkOrFalse]). */
    private var fileHasArtwork: Boolean = false

    /** Bumped by every pick and every Remove; a pick's load applies only if it is still the latest (ruling P21). */
    private var pickGeneration: Int = 0

    /**
     * The picker handling shared with the batch editor: both lists observed into [TagEditorUiState.picker], and the
     * picker's and the name dialog's events. Constructed after the baselines above, which [customTagAccess] reads.
     */
    private val customTagEditing = CustomTagEditingHandlers(
        uiState = _uiState,
        scope = viewModelScope,
        repository = customTagRepository,
        access = customTagAccess(),
    )

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
            ).recomputeSaveState()
        }
    }

    /**
     * A tap on the Tags or Subtags row: opens the picker on [kind]'s page with empty searches. Ignored unless the
     * custom tags can be edited (loaded, not saving, a format Save can write).
     */
    fun onCustomTagRowsClick(kind: CustomTagKind) = customTagEditing.onRowsClick(kind)

    /** The picker's tab or pager moved to [kind]'s page; the sheet follows the state. */
    fun onPickerPageChange(kind: CustomTagKind) = customTagEditing.onPageChange(kind)

    /** The search text of [kind]'s page changed. */
    fun onPickerQueryChange(kind: CustomTagKind, query: String) = customTagEditing.onQueryChange(kind, query)

    /** Ticks or unticks entry [id] of [kind]'s list; written only on Save, and an edit like a field change. */
    fun onCustomTagToggle(kind: CustomTagKind, id: Long) = customTagEditing.onToggle(kind, id)

    /** "Done", or the sheet dismissed: closes the picker. Every tick has already been applied. */
    fun onPickerDone() = customTagEditing.onDone()

    /** "New tag"/"New subtag" in the picker: opens the name dialog for [kind], prefilled with [name] (the search). */
    fun onCreateCustomTagClick(kind: CustomTagKind, name: String) = customTagEditing.onCreateClick(kind, name)

    /**
     * "Create" in the name dialog: adds [name] to the dialog's list. A saved entry closes the dialog and is ticked; a
     * refused name keeps the dialog open with the reason. Ignored while an earlier Create runs.
     */
    fun onCreateCustomTagConfirm(name: String) = customTagEditing.onCreateConfirm(name)

    /** Closes the name dialog without creating anything. */
    fun onNameDialogDismiss() = customTagEditing.onNameDialogDismiss()

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
                        .recomputeSaveState()
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
            it.copy(pendingArtworkUri = null, isArtworkRemoved = true).recomputeSaveState()
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
            val copy = songFileResolver.createEditableCopy(song).also { editableCopy = it }
            val tags = tagReader.read(copy).also { loadedTags = it }
            val isArtworkSupported = copy.supportsArtwork()
            fileHasArtwork = isArtworkSupported && tagReader.hasArtworkOrFalse(copy)
            val knownCustomTagIds = loadKnownCustomTagIds(copy).also { loadedCustomTagIds = it ?: CustomTagIds.EMPTY }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    tags = tags,
                    isArtworkSupported = isArtworkSupported,
                    customTagsEditable = knownCustomTagIds != null && canWriteCustomTags(copy.extension),
                    picker = it.picker.copy(selected = knownCustomTagIds ?: CustomTagIds.EMPTY),
                ).recomputeSaveState()
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
            // Unchanged ticks leave the field exactly as it is (ruling 7): a value this app would rewrite, such as an
            // undecodable one or one with extra keys, survives a save that edits only other fields.
            val picker = _uiState.value.picker
            if (_uiState.value.customTagsEditable && picker.selected != loadedCustomTagIds) {
                writeCustomTags(copy, picker)
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
     * Writes the custom tags into [copy]'s `ROLLA_TAGS` field: the ticks in [picker] plus the IDs in the field that the
     * lists do not know, re-read from [copy] now, so a tag this app cannot name survives the save. Throws what
     * [RollaTagsFileIo] throws; the caller reports it as the ordinary save failure.
     */
    private suspend fun writeCustomTags(copy: File, picker: CustomTagPickerState) = withContext(ioDispatcher) {
        val inFile = rollaTagsFileIo.read(copy).customTagIds
        val knownTagIds = picker.tags.mapTo(HashSet()) { it.id }
        val knownSubTagIds = picker.subTags.mapTo(HashSet()) { it.id }
        val ids = CustomTagIds(
            tagIds = picker.selected.tagIds + (inFile.tagIds - knownTagIds),
            subTagIds = picker.selected.subTagIds + (inFile.subTagIds - knownSubTagIds),
        )
        rollaTagsFileIo.writeCustomTagIds(copy, ids)
    }

    /**
     * The IDs in [copy]'s `ROLLA_TAGS` field that the lists know (waiting for the lists' first answer): the picker's
     * ticks at load. Null when the field cannot be read: that must not fail the load (the fields still edit), and the
     * caller then disables the rows (ruling 8), so Save never writes a field it could not read. Cancellation is
     * rethrown.
     */
    private suspend fun loadKnownCustomTagIds(copy: File): CustomTagIds? {
        val inFile = if (isCustomTagExtension(copy.extension)) {
            try {
                withContext(ioDispatcher) { rollaTagsFileIo.read(copy).customTagIds }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                return null
            }
        } else {
            // A format the scan reads no field from (OGG, WAV) carries none here either: it is not opened.
            CustomTagIds.EMPTY
        }
        return inFile.knownIn(customTagEditing.lists.filterNotNull().first())
    }

    /**
     * How [customTagEditing] reaches this editor's state. The custom tags can be edited when loaded, not saving, and a
     * format Save can write; a changed tick recomputes the save state against this ViewModel's baselines.
     */
    private fun customTagAccess() = object : CustomTagEditingAccess<TagEditorUiState> {
        override fun read(state: TagEditorUiState) = CustomTagEditing(
            picker = state.picker,
            isPickerOpen = state.isPickerOpen,
            nameDialog = state.nameDialog,
        )

        override fun write(state: TagEditorUiState, editing: CustomTagEditing): TagEditorUiState {
            val written = state.copy(
                picker = editing.picker,
                isPickerOpen = editing.isPickerOpen,
                nameDialog = editing.nameDialog,
            )
            return if (editing.picker.selected == state.picker.selected) written else written.recomputeSaveState()
        }

        override fun canEdit(state: TagEditorUiState): Boolean =
            loadedTags != null && !state.loadFailed && !state.isSaving && state.customTagsEditable
    }

    /** [recomputeSaveState] against this ViewModel's baselines: the loaded fields, picture and custom tags. */
    private fun TagEditorUiState.recomputeSaveState(): TagEditorUiState =
        recomputeSaveState(loadedTags, fileHasArtwork, loadedCustomTagIds)

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

/** These IDs without any the [lists] do not know: what the picker may show. */
private fun CustomTagIds.knownIn(lists: CustomTagLists): CustomTagIds = CustomTagIds(
    tagIds = tagIds.filterTo(HashSet()) { id -> lists.tags.any { it.id == id } },
    subTagIds = subTagIds.filterTo(HashSet()) { id -> lists.subTags.any { it.id == id } },
)

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
 * ([fileHasArtwork]); removing only a pick from a picture-less file changes nothing on disk. Custom tag ticks that
 * differ from [loadedCustomTagIds] (the known IDs the file carried at load) are an edit too, and are recomputed after
 * every tick and every created entry.
 */
private fun TagEditorUiState.recomputeSaveState(
    loadedTags: SongTags?,
    fileHasArtwork: Boolean,
    loadedCustomTagIds: CustomTagIds?,
): TagEditorUiState {
    // dirty is only ever true when loadedTags is non-null (see the condition below), so it alone
    // is sufficient to gate canSave -- no separate "loadedTags != null" check is needed here.
    val artworkChanged = pendingArtworkUri != null || (isArtworkRemoved && fileHasArtwork)
    val customTagsChanged = loadedCustomTagIds != null && picker.selected != loadedCustomTagIds
    val dirty = loadedTags != null && (tags != loadedTags || artworkChanged || customTagsChanged)
    val hasErrors = yearError != null || trackNumberError != null
    val canRemove = isArtworkSupported && !isArtworkRemoved && (fileHasArtwork || pendingArtworkUri != null)
    return copy(
        isDirty = dirty,
        canSave = dirty && !hasErrors && !isSaving && !loadFailed,
        canRemoveArtwork = canRemove,
    )
}
