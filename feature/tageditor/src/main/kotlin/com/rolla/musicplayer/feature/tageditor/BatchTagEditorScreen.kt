@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.tageditor

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.metadata
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack
import com.rolla.musicplayer.feature.tageditor.io.rememberMediaWriteRequester

private val ScreenHorizontalPadding = 16.dp
private val CardPadding = 16.dp
private val SectionSpacing = 24.dp
private val CaptionSpacing = 8.dp
private val BottomSpacing = 24.dp
private val FieldSpacing = 12.dp
private val FieldToCheckboxGap = 12.dp
private val ButtonSpacing = 12.dp
private val MinTouchTarget = 48.dp
private val SaveProgressSize = 20.dp
private val SaveProgressStrokeWidth = 2.dp
private val ProgressLabelSpacing = 8.dp

private const val ONLY_CHECKED_CAPTION = "Only checked fields will be written to all selected songs."

/**
 * Stateful entry point for the batch tag editor (BatchTagEditor(songIds: List<Long>)). Same
 * Route/Screen split, and the exact same three-LaunchedEffect consent dance, as [TagEditorRoute]
 * -- see that function KDoc for why the consent/recovery/close cues must be observed here rather
 * than resolved inside [BatchTagEditorViewModel] itself.
 *
 * The one behavioral difference from the single-song editor: on a partial failure,
 * [BatchTagEditorUiState.message] is set while [BatchTagEditorUiState.isClosed] stays false (see
 * [BatchTagEditorViewModel] KDoc, "Completion: full success vs partial failure"). That is handled
 * entirely by [BatchTagEditorUiState.isClosed] simply not flipping in that case -- this Route does
 * not need any special-case branching of its own for it, the isClosed-keyed LaunchedEffect below
 * just never fires, the message-driven snackbar in [BatchTagEditorScreen] shows on its own, and the
 * user backs out manually via Cancel/back once the message has been read.
 */
@Suppress("LongMethod")
@Composable
fun BatchTagEditorRoute(
    onNavigateUp: () -> Unit,
    viewModel: BatchTagEditorViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val requester = rememberMediaWriteRequester()

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

    BatchTagEditorScreen(
        uiState = uiState,
        onFieldValueChanged = remember(viewModel) { viewModel::onFieldValueChanged },
        onFieldApplyToggled = remember(viewModel) { viewModel::onFieldApplyToggled },
        onSaveClick = remember(viewModel) { viewModel::onSaveClick },
        onNavigateUp = onNavigateUp,
        onDismissMessage = remember(viewModel) { viewModel::dismissMessage },
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
fun BatchTagEditorScreen(
    uiState: BatchTagEditorUiState,
    onFieldValueChanged: (TagField, String) -> Unit,
    onFieldApplyToggled: (TagField, Boolean) -> Unit,
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
        topBar = { BatchTagEditorTopBar(songCount = uiState.songCount, onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BatchTagEditorContent(
            uiState = uiState,
            onFieldValueChanged = onFieldValueChanged,
            onFieldApplyToggled = onFieldApplyToggled,
            onSaveClick = onSaveClick,
            onNavigateUp = onNavigateUp,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * Title reads "Edit N songs" (rather than the single editor static "Edit tags") since [songCount]
 * is known synchronously from the moment this screen opens (see [BatchTagEditorUiState] KDoc) --
 * showing it up front confirms to the user which selection is about to be bulk-edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchTagEditorTopBar(songCount: Int, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = "Edit $songCount songs",
                style = MaterialTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun BatchTagEditorContent(
    uiState: BatchTagEditorUiState,
    onFieldValueChanged: (TagField, String) -> Unit,
    onFieldApplyToggled: (TagField, Boolean) -> Unit,
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
        Text(
            text = "Editing ${uiState.songCount} songs",
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SectionSpacing))
        BatchTagFieldsCard(
            fields = uiState.fields,
            yearError = uiState.yearError,
            trackNumberError = uiState.trackNumberError,
            enabled = !uiState.isSaving,
            onFieldValueChanged = onFieldValueChanged,
            onFieldApplyToggled = onFieldApplyToggled,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(CaptionSpacing))
        Text(
            text = ONLY_CHECKED_CAPTION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val progress = uiState.progress
        if (progress != null) {
            Spacer(Modifier.height(SectionSpacing))
            BatchSaveProgress(progress = progress, modifier = Modifier.fillMaxWidth())
        }
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
 * The 8 editable TagField rows, each pairing an apply-toggle [Checkbox] with its
 * [OutlinedTextField]. Unlike the single editor fields card, year and track-number are NOT paired
 * into one side-by-side row here: each field needs its own leading checkbox, so every field gets a
 * full-width row of its own.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun BatchTagFieldsCard(
    fields: BatchTagFields,
    yearError: String?,
    trackNumberError: String?,
    enabled: Boolean,
    onFieldValueChanged: (TagField, String) -> Unit,
    onFieldApplyToggled: (TagField, Boolean) -> Unit,
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
            BatchTagRow(
                label = "Title",
                state = fields.title,
                enabled = enabled,
                onValueChange = { onFieldValueChanged(TagField.TITLE, it) },
                onAppliedChange = { onFieldApplyToggled(TagField.TITLE, it) },
            )
            BatchTagRow(
                label = "Artist",
                state = fields.artist,
                enabled = enabled,
                onValueChange = { onFieldValueChanged(TagField.ARTIST, it) },
                onAppliedChange = { onFieldApplyToggled(TagField.ARTIST, it) },
            )
            BatchTagRow(
                label = "Album",
                state = fields.album,
                enabled = enabled,
                onValueChange = { onFieldValueChanged(TagField.ALBUM, it) },
                onAppliedChange = { onFieldApplyToggled(TagField.ALBUM, it) },
            )
            BatchTagRow(
                label = "Album artist",
                state = fields.albumArtist,
                enabled = enabled,
                onValueChange = { onFieldValueChanged(TagField.ALBUM_ARTIST, it) },
                onAppliedChange = { onFieldApplyToggled(TagField.ALBUM_ARTIST, it) },
            )
            BatchTagRow(
                label = "Genre",
                state = fields.genre,
                enabled = enabled,
                onValueChange = { onFieldValueChanged(TagField.GENRE, it) },
                onAppliedChange = { onFieldApplyToggled(TagField.GENRE, it) },
            )
            BatchTagRow(
                label = "Year",
                state = fields.year,
                enabled = enabled,
                keyboardType = KeyboardType.Number,
                isError = yearError != null,
                supportingText = yearError,
                onValueChange = { onFieldValueChanged(TagField.YEAR, it) },
                onAppliedChange = { onFieldApplyToggled(TagField.YEAR, it) },
            )
            BatchTagRow(
                label = "Track number",
                state = fields.trackNumber,
                enabled = enabled,
                keyboardType = KeyboardType.Number,
                isError = trackNumberError != null,
                supportingText = trackNumberError,
                onValueChange = { onFieldValueChanged(TagField.TRACK_NUMBER, it) },
                onAppliedChange = { onFieldApplyToggled(TagField.TRACK_NUMBER, it) },
            )
            BatchTagRow(
                label = "Composer",
                state = fields.composer,
                enabled = enabled,
                onValueChange = { onFieldValueChanged(TagField.COMPOSER, it) },
                onAppliedChange = { onFieldApplyToggled(TagField.COMPOSER, it) },
            )
        }
    }
}

/**
 * One batch-editable field: a leading apply-[Checkbox] (relies on Material 3 built-in
 * minimumInteractiveComponentSize for its 48dp touch target -- no manual sizing needed) plus its
 * [OutlinedTextField], disabled whenever [BatchFieldState.applied] is false so an unpicked field
 * visibly will not be written. Toggling the checkbox never clears [BatchFieldState.value] -- the
 * ViewModel guarantees that (see [BatchTagFields] KDoc); this composable only ever renders whatever
 * state it is given.
 */
@Suppress("LongParameterList")
@Composable
private fun BatchTagRow(
    label: String,
    state: BatchFieldState,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onAppliedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.applied,
            onCheckedChange = onAppliedChange,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Apply $label to all selected songs" },
        )
        Spacer(Modifier.width(FieldToCheckboxGap))
        OutlinedTextField(
            value = state.value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled && state.applied,
            isError = isError,
            supportingText = supportingText?.let { msg ->
                { Text(text = msg, color = MaterialTheme.colorScheme.error) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Shown only while [BatchTagEditorUiState.progress] is non-null, i.e. mid-save. */
@Composable
private fun BatchSaveProgress(progress: BatchProgress, modifier: Modifier = Modifier) {
    val fraction = if (progress.total > 0) progress.done / progress.total.toFloat() else 0f
    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { fraction },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.sliderInactiveTrack,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.height(ProgressLabelSpacing))
        Text(
            text = "${progress.done} / ${progress.total}",
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
                .heightIn(min = MinTouchTarget),
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

private fun previewUiState(): BatchTagEditorUiState = BatchTagEditorUiState(
    songCount = 12,
    fields = BatchTagFields(
        genre = BatchFieldState(value = "Rock", applied = true),
        year = BatchFieldState(value = "1975", applied = true),
        albumArtist = BatchFieldState(value = "Various Artists", applied = false),
    ),
    canSave = true,
)

private fun previewInProgressUiState(): BatchTagEditorUiState = previewUiState().copy(
    isSaving = true,
    canSave = false,
    progress = BatchProgress(done = 5, total = 12),
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Batch Tag Editor - Light")
@Preview(name = "Batch Tag Editor - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewBatchTagEditorScreen() {
    RollaMusicPlayerTheme {
        BatchTagEditorScreen(
            uiState = previewUiState(),
            onFieldValueChanged = { _, _ -> },
            onFieldApplyToggled = { _, _ -> },
            onSaveClick = {},
            onNavigateUp = {},
            onDismissMessage = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Batch Tag Editor In Progress - Light")
@Preview(name = "Batch Tag Editor In Progress - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewBatchTagEditorScreenInProgress() {
    RollaMusicPlayerTheme {
        BatchTagEditorScreen(
            uiState = previewInProgressUiState(),
            onFieldValueChanged = { _, _ -> },
            onFieldApplyToggled = { _, _ -> },
            onSaveClick = {},
            onNavigateUp = {},
            onDismissMessage = {},
        )
    }
}
