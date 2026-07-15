@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.tageditor

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.feature.tageditor.io.rememberMediaWriteRequester

private val ScreenHorizontalPadding = 16.dp
private val CardPadding = 16.dp
private val SectionSpacing = 24.dp
private val CaptionSpacing = 8.dp
private val BottomSpacing = 24.dp
private val FieldSpacing = 12.dp
private val ButtonSpacing = 12.dp
private val MinTouchTarget = 48.dp
private val ArtworkSize = 180.dp
private val ArtworkPlaceholderIconSize = 64.dp
private val SaveProgressSize = 20.dp
private val SaveProgressStrokeWidth = 2.dp
private val ErrorContentPadding = 32.dp

private const val LOAD_FAILED_MESSAGE = "Couldn't load this song's tags."

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
        onSaveClick = remember(viewModel) { viewModel::onSaveClick },
        onNavigateUp = navigateUpUnlessSaving,
        onDismissMessage = remember(viewModel) { viewModel::dismissMessage },
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
fun TagEditorScreen(
    uiState: TagEditorUiState,
    onFieldChanged: (TagField, String) -> Unit,
    onChangeArtworkClick: () -> Unit,
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
        topBar = { TagEditorTopBar(onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingContent(modifier = Modifier.padding(innerPadding))
            uiState.loadFailed -> ErrorContent(onNavigateUp = onNavigateUp, modifier = Modifier.padding(innerPadding))
            else -> TagEditorContent(
                uiState = uiState,
                onFieldChanged = onFieldChanged,
                onChangeArtworkClick = onChangeArtworkClick,
                onSaveClick = onSaveClick,
                onNavigateUp = onNavigateUp,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagEditorTopBar(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = "Edit tags",
                style = MaterialTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier,
    )
}

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
            .padding(ErrorContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = LOAD_FAILED_MESSAGE,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SectionSpacing))
        OutlinedButton(onClick = onNavigateUp, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text("Back")
        }
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun TagEditorContent(
    uiState: TagEditorUiState,
    onFieldChanged: (TagField, String) -> Unit,
    onChangeArtworkClick: () -> Unit,
    onSaveClick: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(CaptionSpacing))
        TagEditorHeaderCard(
            // A validated pending pick previews immediately; it is only written to the file on Save.
            artworkUri = uiState.pendingArtworkUri ?: uiState.artworkUri,
            songTitle = uiState.songTitle,
            artistName = uiState.artistName,
            onChangeArtworkClick = onChangeArtworkClick,
            changeArtworkEnabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SectionSpacing))
        TagEditorFieldsCard(
            tags = uiState.tags,
            yearError = uiState.yearError,
            trackNumberError = uiState.trackNumberError,
            onFieldChanged = onFieldChanged,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(SectionSpacing))
        SaveCancelRow(
            canSave = uiState.canSave,
            isSaving = uiState.isSaving,
            onSaveClick = onSaveClick,
            onCancelClick = onNavigateUp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(BottomSpacing))
    }
}

/**
 * Context at the top of the form: the song's artwork (current, or the pending replacement pick),
 * title, and artist. Title/artist are never editable here; artwork IS -- "Change artwork" opens
 * the system PhotoPicker for a LOCAL image, which is embedded into the file on Save (see
 * [SongTags]'s KDoc on why artwork lives outside the text-field model).
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
private fun TagEditorHeaderCard(
    artworkUri: String?,
    songTitle: String,
    artistName: String,
    onChangeArtworkClick: () -> Unit,
    changeArtworkEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TagEditorArtwork(
                artworkUri = artworkUri.orEmpty(),
                // Decorative: the song title Text is announced two stops later; describing the
                // artwork made TalkBack read the title twice (a11y audit).
                contentDescription = null,
                modifier = Modifier
                    .size(ArtworkSize)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
            Spacer(Modifier.height(CaptionSpacing))
            OutlinedButton(
                onClick = onChangeArtworkClick,
                enabled = changeArtworkEnabled,
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                Text("Change artwork")
            }
            Spacer(Modifier.height(CaptionSpacing))
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
}

@Composable
private fun TagEditorArtwork(artworkUri: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(artworkUri) {
        ImageRequest.Builder(context).data(artworkUri.ifEmpty { null }).crossfade(true).build()
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (artworkUri.isEmpty()) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ArtworkPlaceholderIconSize),
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun TagEditorFieldsCard(
    tags: SongTags,
    yearError: String?,
    trackNumberError: String?,
    onFieldChanged: (TagField, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(FieldSpacing),
        ) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing)) {
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { message ->
            { Text(text = message, color = MaterialTheme.colorScheme.error) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

@Suppress("LongMethod")
@Composable
private fun SaveCancelRow(
    canSave: Boolean,
    isSaving: Boolean,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ButtonSpacing)) {
        OutlinedButton(
            onClick = onCancelClick,
            enabled = !isSaving,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = MinTouchTarget),
        ) {
            Text("Cancel")
        }
        Button(
            onClick = onSaveClick,
            enabled = canSave && !isSaving,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = MinTouchTarget)
                // While saving, the label Text is swapped for a bare spinner and the (still
                // focusable, disabled) button would otherwise lose its accessible name entirely.
                .semantics { if (isSaving) contentDescription = "Saving" },
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SaveProgressSize),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = SaveProgressStrokeWidth,
                )
            } else {
                Text("Save")
            }
        }
    }
}

private fun previewUiState(): TagEditorUiState = TagEditorUiState(
    isLoading = false,
    songTitle = "Bohemian Rhapsody",
    artistName = "Queen",
    artworkUri = "",
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
            onSaveClick = {},
            onNavigateUp = {},
            onDismissMessage = {},
        )
    }
}
