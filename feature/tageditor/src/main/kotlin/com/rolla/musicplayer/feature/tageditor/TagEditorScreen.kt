@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.tageditor

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.OneUiPillButton
import com.rolla.musicplayer.core.designsystem.component.OneUiTextField
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.ui.ArtworkThumbnail
import com.rolla.musicplayer.feature.tageditor.io.rememberMediaWriteRequester

private const val LOAD_FAILED_MESSAGE = "Couldn't load this track's tags."
private const val ARTWORK_REMOVED = "Artwork removed"

/**
 * Stateful entry point for the single-song tag editor (`TagEditor(songId: Long)`). Same
 * Route/Screen split as every other screen in the codebase (see EqualizerRoute in
 * feature:equalizer, PlaylistDetailRoute in feature:playlists).
 *
 * This is where the scoped-storage consent dance from [TagEditorViewModel]'s KDoc actually plays
 * out: [TagEditorUiState.consentRequest] and [TagEditorUiState.recoveryRequest] are one-shot
 * requests the ViewModel cannot resolve itself (they require an Activity result launcher, which
 * only [rememberMediaWriteRequester] can provide), so this Route observes them via
 * [LaunchedEffect] and calls back into the ViewModel once the system prompt resolves. No byte is
 * ever written from here directly -- this function only ever forwards consent outcomes.
 */
@Suppress("LongMethod")
@Composable
fun TagEditorRoute(
    onNavigateUp: () -> Unit,
    viewModel: TagEditorViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val requester = rememberMediaWriteRequester()

    // System PhotoPicker (falls back to the document picker pre-13): LOCAL images only, per the
    // offline contract -- there is no online artwork search anywhere in the app.
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onArtworkPicked(uri.toString())
    }

    LaunchedEffect(uiState.consentRequest) {
        val uris = uiState.consentRequest
        if (uris != null) {
            requester.ensureWritable(
                uris = uris.map(String::toUri),
                onGranted = viewModel::onConsentGranted,
                onDenied = viewModel::onConsentDenied,
            )
        }
    }

    LaunchedEffect(uiState.recoveryRequest) {
        val recovery = uiState.recoveryRequest
        if (recovery != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requester.recoverAndRetry(
                exception = recovery,
                onGranted = viewModel::onRecoveryGranted,
                onDenied = viewModel::onConsentDenied,
            )
        }
    }

    LaunchedEffect(uiState.isClosed) {
        if (uiState.isClosed) {
            onNavigateUp()
        }
    }

    // Leaving mid-save would cancel viewModelScope and skip TagSaveFinalizer for a file whose
    // bytes may already be rewritten on disk -- the library would silently go stale. So while
    // isSaving, system back is swallowed and Cancel/top-bar-back no-op; the isClosed effect above
    // remains the only exit.
    BackHandler(enabled = uiState.isSaving) {}
    val navigateUpUnlessSaving = remember(viewModel, onNavigateUp) {
        { if (!viewModel.uiState.value.isSaving) onNavigateUp() }
    }

    TagEditorScreen(
        uiState = uiState,
        onFieldChanged = remember(viewModel) { viewModel::onFieldChanged },
        onChangeArtworkClick = remember(artworkPicker) {
            {
                artworkPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        },
        onRemoveArtworkClick = remember(viewModel) { viewModel::onRemoveArtworkClick },
        onSaveClick = remember(viewModel) { viewModel::onSaveClick },
        onNavigateUp = navigateUpUnlessSaving,
        onDismissMessage = remember(viewModel) { viewModel::dismissMessage },
        modifier = modifier,
    )
}

// One callback per editor action plus the Scaffold; at 31 lines splitting it would only move the parameter fan-out.
@Suppress("LongParameterList", "LongMethod")
@Composable
fun TagEditorScreen(
    uiState: TagEditorUiState,
    onFieldChanged: (TagField, String) -> Unit,
    onChangeArtworkClick: () -> Unit,
    onRemoveArtworkClick: () -> Unit,
    onSaveClick: () -> Unit,
    onNavigateUp: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OneUiDetailTopBar(title = "Edit tags", onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingContent(modifier = Modifier.padding(innerPadding))
            uiState.loadFailed -> ErrorContent(onNavigateUp = onNavigateUp, modifier = Modifier.padding(innerPadding))
            else -> TagEditorContent(
                uiState = uiState,
                onFieldChanged = onFieldChanged,
                onChangeArtworkClick = onChangeArtworkClick,
                onRemoveArtworkClick = onRemoveArtworkClick,
                onSaveClick = onSaveClick,
                onNavigateUp = onNavigateUp,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** Reading a file's tags is IO, not a list flash, so loading keeps its spinner (Phase 6b ruling 6). */
@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorContent(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(RollaDimens.screenEdge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = LOAD_FAILED_MESSAGE,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RollaDimens.settingsSectionGap))
        OneUiPillButton(text = "Back", onClick = onNavigateUp, tonal = true)
    }
}

// One flat column of the three blocks; splitting it would only move the uiState fan-out elsewhere.
@Suppress("LongParameterList")
@Composable
private fun TagEditorContent(
    uiState: TagEditorUiState,
    onFieldChanged: (TagField, String) -> Unit,
    onChangeArtworkClick: () -> Unit,
    onRemoveArtworkClick: () -> Unit,
    onSaveClick: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TagEditorScrollColumn(modifier = modifier) {
        TagEditorHeaderCard(
            // A validated pending pick previews immediately, a removal previews the placeholder; either is only
            // written to the file on Save.
            artworkUri = uiState.previewArtworkUri,
            songTitle = uiState.songTitle,
            artistName = uiState.artistName,
            isArtworkRemoved = uiState.isArtworkRemoved,
            showArtworkActions = uiState.isArtworkSupported,
            showRemoveArtwork = uiState.canRemoveArtwork,
            artworkActionsEnabled = !uiState.isSaving,
            onChangeArtworkClick = onChangeArtworkClick,
            onRemoveArtworkClick = onRemoveArtworkClick,
        )
        TagEditorFieldsCard(
            tags = uiState.tags,
            yearError = uiState.yearError,
            trackNumberError = uiState.trackNumberError,
            onFieldChanged = onFieldChanged,
        )
        TagEditorSaveCancelRow(
            canSave = uiState.canSave,
            isSaving = uiState.isSaving,
            onSaveClick = onSaveClick,
            onCancelClick = onNavigateUp,
        )
    }
}

/**
 * The editors' one vertical scroll (Phase 6b ruling 6): its blocks inset settingsCardInset from the screen edges and
 * settingsSectionGap apart, with settingsSectionGap above the first and below the last.
 *
 * It owns the keyboard inset (spec §7.4): [modifier] carries the Scaffold's inner padding, which already holds the
 * navigation bar, so the column adds only the keyboard's height above the navigation bar. With the keyboard open the
 * two add up to exactly the keyboard, and the focused field can be scrolled into view above it.
 */
@Composable
internal fun TagEditorScrollColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RollaDimens.settingsCardInset, vertical = RollaDimens.settingsSectionGap),
        verticalArrangement = Arrangement.spacedBy(RollaDimens.settingsSectionGap),
        content = content,
    )
}

/**
 * An editor card (spec §8.7, Phase 6b ruling 6): surfaceContainer in the large shape, settingsRowPadding inside, its
 * items editorFieldGap apart.
 */
@Composable
internal fun TagEditorCard(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(RollaDimens.settingsRowPadding),
            verticalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * Context at the top of the form: the track's artwork (current, the pending replacement pick, or
 * the placeholder once removed), title, and artist. Title/artist are never editable here; artwork
 * IS -- "Change artwork" opens the system PhotoPicker for a LOCAL image, which is embedded into
 * the file on Save, and "Remove artwork" (shown only while [showRemoveArtwork]) deletes the
 * embedded picture on Save (see [SongTags]'s KDoc on why artwork lives outside the text-field
 * model). Neither pill shows for a format that cannot carry artwork ([showArtworkActions] false).
 */
// The artwork, the artwork pills and the two text lines: one flat card.
@Suppress("LongParameterList")
@Composable
private fun TagEditorHeaderCard(
    artworkUri: String,
    songTitle: String,
    artistName: String,
    isArtworkRemoved: Boolean,
    showArtworkActions: Boolean,
    showRemoveArtwork: Boolean,
    artworkActionsEnabled: Boolean,
    onChangeArtworkClick: () -> Unit,
    onRemoveArtworkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TagEditorCard(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ArtworkThumbnail(
            artworkUri = artworkUri,
            // Decorative: the song title Text is announced two stops later; describing the
            // artwork made TalkBack read the title twice (a11y audit). Only a removal is described
            // (see artworkRemovedAnnouncement).
            contentDescription = null,
            shape = MaterialTheme.shapes.extraLarge,
            large = true,
            placeholderGlyphSize = RollaDimens.npArtGlyph,
            modifier = Modifier
                .size(RollaDimens.detailArtworkSize)
                .artworkRemovedAnnouncement(isArtworkRemoved),
        )
        if (showArtworkActions) {
            ArtworkActionsRow(
                showRemove = showRemoveArtwork,
                enabled = artworkActionsEnabled,
                onChangeClick = onChangeArtworkClick,
                onRemoveClick = onRemoveArtworkClick,
            )
        }
        HeaderTitles(songTitle = songTitle, artistName = artistName)
    }
}

/**
 * The header artwork is a polite live region that stays silent until a removal, then says [ARTWORK_REMOVED]: the
 * "Remove artwork" pill TalkBack was focused on has just vanished, so the change is announced instead of lost. The
 * region is present from the start because TalkBack announces a change to an existing live region, not a new node.
 */
private fun Modifier.artworkRemovedAnnouncement(isArtworkRemoved: Boolean): Modifier = semantics {
    liveRegion = LiveRegionMode.Polite
    if (isArtworkRemoved) contentDescription = ARTWORK_REMOVED
}

/**
 * The tonal "Change artwork" pill and, while [showRemove], the tonal destructive "Remove artwork" pill beside it. A
 * centred FlowRow (ruling P20): at a large font scale the second pill wraps under the first instead of ellipsizing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtworkActionsRow(
    showRemove: Boolean,
    enabled: Boolean,
    onChangeClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
    ) {
        OneUiPillButton(text = "Change artwork", onClick = onChangeClick, enabled = enabled, tonal = true)
        if (showRemove) {
            OneUiPillButton(
                text = "Remove artwork",
                onClick = onRemoveClick,
                enabled = enabled,
                tonal = true,
                destructive = true,
            )
        }
    }
}

/** The track's title (songTitle) over its artist (artistName), centred, one line each. */
@Composable
private fun HeaderTitles(songTitle: String, artistName: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = songTitle,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artistName,
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Eight fields in one card, Year and Track number side by side; each call is one field.
@Suppress("LongMethod")
@Composable
private fun TagEditorFieldsCard(
    tags: SongTags,
    yearError: String?,
    trackNumberError: String?,
    onFieldChanged: (TagField, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TagEditorCard(modifier = modifier) {
        TagTextField(label = "Title", value = tags.title, onValueChange = { onFieldChanged(TagField.TITLE, it) })
        TagTextField(
            label = "Artist",
            value = tags.artist,
            onValueChange = { onFieldChanged(TagField.ARTIST, it) },
        )
        TagTextField(label = "Album", value = tags.album, onValueChange = { onFieldChanged(TagField.ALBUM, it) })
        TagTextField(
            label = "Album artist",
            value = tags.albumArtist,
            onValueChange = { onFieldChanged(TagField.ALBUM_ARTIST, it) },
        )
        TagTextField(label = "Genre", value = tags.genre, onValueChange = { onFieldChanged(TagField.GENRE, it) })
        Row(horizontalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap)) {
            TagTextField(
                label = "Year",
                value = tags.year,
                onValueChange = { onFieldChanged(TagField.YEAR, it) },
                keyboardType = KeyboardType.Number,
                isError = yearError != null,
                supportingText = yearError,
                modifier = Modifier.weight(1f),
            )
            TagTextField(
                label = "Track number",
                value = tags.trackNumber,
                onValueChange = { onFieldChanged(TagField.TRACK_NUMBER, it) },
                keyboardType = KeyboardType.Number,
                isError = trackNumberError != null,
                supportingText = trackNumberError,
                modifier = Modifier.weight(1f),
            )
        }
        TagTextField(
            label = "Composer",
            value = tags.composer,
            onValueChange = { onFieldChanged(TagField.COMPOSER, it) },
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun TagTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OneUiTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
        supportingText = supportingText,
    )
}

/**
 * Cancel (a tonal pill, locked while saving) and Save (a filled pill that shows a progress ring described as "Saving"
 * while [isSaving]). Both take an equal share of the row, so the ring swapping in for the label never moves either.
 * Save stays at full strength while it shows the ring (the ViewModel drops canSave while saving); the pill itself
 * ignores taps while loading.
 */
@Composable
internal fun TagEditorSaveCancelRow(
    canSave: Boolean,
    isSaving: Boolean,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
    ) {
        OneUiPillButton(
            text = "Cancel",
            onClick = onCancelClick,
            modifier = Modifier.weight(1f),
            enabled = !isSaving,
            tonal = true,
        )
        OneUiPillButton(
            text = "Save",
            onClick = onSaveClick,
            modifier = Modifier.weight(1f),
            enabled = canSave || isSaving,
            loading = isSaving,
            loadingDescription = "Saving",
        )
    }
}

private fun previewUiState(): TagEditorUiState = TagEditorUiState(
    isLoading = false,
    songTitle = "Bohemian Rhapsody",
    artistName = "Queen",
    artworkUri = "",
    canRemoveArtwork = true,
    tags = SongTags(
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        albumArtist = "Queen",
        genre = "Rock",
        year = "1975",
        trackNumber = "11",
        composer = "Freddie Mercury",
    ),
    isDirty = true,
    canSave = true,
)

private fun previewValidationErrorUiState(): TagEditorUiState {
    val base = previewUiState()
    return base.copy(
        tags = base.tags.copy(year = "19755", trackNumber = "4x"),
        yearError = "Year must be blank or up to 4 digits.",
        trackNumberError = "Track number must be blank or digits only.",
        canSave = false,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Tag Editor - Light")
@Preview(name = "Tag Editor - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewTagEditorScreen() {
    RollaMusicPlayerTheme {
        TagEditorScreen(
            uiState = previewUiState(),
            onFieldChanged = { _, _ -> },
            onChangeArtworkClick = {},
            onRemoveArtworkClick = {},
            onSaveClick = {},
            onNavigateUp = {},
            onDismissMessage = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Tag Editor Validation Error - Light")
@Preview(name = "Tag Editor Validation Error - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewTagEditorScreenValidationError() {
    RollaMusicPlayerTheme {
        TagEditorScreen(
            uiState = previewValidationErrorUiState(),
            onFieldChanged = { _, _ -> },
            onChangeArtworkClick = {},
            onRemoveArtworkClick = {},
            onSaveClick = {},
            onNavigateUp = {},
            onDismissMessage = {},
        )
    }
}
