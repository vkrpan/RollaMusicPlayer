@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.tageditor

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.OneUiSwitch
import com.rolla.musicplayer.core.designsystem.component.OneUiTextField
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.metadata
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack
import com.rolla.musicplayer.core.ui.tracksCountLabel
import com.rolla.musicplayer.feature.tageditor.io.rememberMediaWriteRequester

private const val SWITCHED_ON_CAPTION = "Only the fields switched on are written to every selected track."

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

    // Leaving mid-batch would cancel viewModelScope between per-song writes: files already
    // rewritten on disk would never reach TagSaveFinalizer, silently desyncing the library. So
    // while isSaving, system back is swallowed and Cancel/top-bar-back no-op; the isClosed effect
    // above remains the only exit.
    BackHandler(enabled = uiState.isSaving) {}
    val navigateUpUnlessSaving = remember(viewModel, onNavigateUp) {
        { if (!viewModel.uiState.value.isSaving) onNavigateUp() }
    }

    BatchTagEditorScreen(
        uiState = uiState,
        onFieldValueChanged = remember(viewModel) { viewModel::onFieldValueChanged },
        onFieldApplyToggled = remember(viewModel) { viewModel::onFieldApplyToggled },
        onSaveClick = remember(viewModel) { viewModel::onSaveClick },
        onNavigateUp = navigateUpUnlessSaving,
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

    // "Edit N tracks" rather than the single editor's static "Edit tags": songCount is known synchronously from the
    // moment this screen opens (see BatchTagEditorUiState's KDoc), so the bar confirms the selection up front. Built
    // here, so the top bar's slot captures only this string, not the whole uiState.
    val title = "Edit ${tracksCountLabel(uiState.songCount)}"
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OneUiDetailTopBar(title = title, onNavigateUp = onNavigateUp) },
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

// One flat column: the fields card with its caption, the optional progress, then the buttons.
@Suppress("LongParameterList")
@Composable
private fun BatchTagEditorContent(
    uiState: BatchTagEditorUiState,
    onFieldValueChanged: (TagField, String) -> Unit,
    onFieldApplyToggled: (TagField, Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No separate count line above the card: the top bar carries the count (Phase 6b ruling 6).
    TagEditorScrollColumn(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(RollaDimens.settingsHeaderGap)) {
            BatchTagFieldsCard(
                fields = uiState.fields,
                yearError = uiState.yearError,
                trackNumberError = uiState.trackNumberError,
                enabled = !uiState.isSaving,
                onFieldValueChanged = onFieldValueChanged,
                onFieldApplyToggled = onFieldApplyToggled,
            )
            SwitchedOnCaption()
        }
        val progress = uiState.progress
        if (progress != null) {
            BatchSaveProgress(
                progress = progress,
                modifier = Modifier.padding(horizontal = RollaDimens.settingsRowPadding),
            )
        }
        TagEditorSaveCancelRow(
            canSave = uiState.canSave,
            isSaving = uiState.isSaving,
            onSaveClick = onSaveClick,
            onCancelClick = onNavigateUp,
        )
    }
}

/**
 * The 8 editable TagField rows, each pairing its [OneUiTextField] with an apply [OneUiSwitch]. Unlike the single
 * editor's fields card, year and track-number are NOT paired into one side-by-side row here: each field needs its own
 * switch, so every field gets a full-width row of its own.
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
    TagEditorCard(modifier = modifier) {
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

/**
 * One batch-editable field: its [OneUiTextField] (weight 1), then an apply [OneUiSwitch] in a
 * [RollaDimens.minTouchTarget] box that owns the toggle (Role.Switch, "Apply <label> to every selected track"). The
 * field is disabled whenever [BatchFieldState.applied] is false, so a switched-off field visibly will not be written.
 * Toggling never clears [BatchFieldState.value] -- the ViewModel guarantees that (see [BatchTagFields] KDoc); this
 * composable only ever renders whatever state it is given.
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
        OneUiTextField(
            value = state.value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.weight(1f),
            enabled = enabled && state.applied,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            isError = isError,
            supportingText = supportingText,
        )
        ApplySwitch(label = label, applied = state.applied, enabled = enabled, onAppliedChange = onAppliedChange)
    }
}

/**
 * A field's apply switch: a [RollaDimens.minTouchTarget] box that owns the toggle and its semantics (Role.Switch,
 * "Apply <label> to every selected track"), with a purely visual [OneUiSwitch] centred in it.
 */
@Composable
private fun ApplySwitch(
    label: String,
    applied: Boolean,
    enabled: Boolean,
    onAppliedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(RollaDimens.minTouchTarget)
            .clip(CircleShape)
            .toggleable(value = applied, enabled = enabled, role = Role.Switch, onValueChange = onAppliedChange)
            .semantics { contentDescription = "Apply $label to every selected track" },
        contentAlignment = Alignment.Center,
    ) {
        OneUiSwitch(checked = applied, onCheckedChange = null, enabled = enabled)
    }
}

/** The caption under the fields card, aligned with the card's content (settingsRowPadding in). */
@Composable
private fun SwitchedOnCaption(modifier: Modifier = Modifier) {
    Text(
        text = SWITCHED_ON_CAPTION,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = RollaDimens.settingsRowPadding),
    )
}

/** Shown only while [BatchTagEditorUiState.progress] is non-null, i.e. mid-save. */
@Composable
private fun BatchSaveProgress(progress: BatchProgress, modifier: Modifier = Modifier) {
    val fraction = if (progress.total > 0) progress.done / progress.total.toFloat() else 0f
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(RollaDimens.settingsHeaderGap),
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.sliderInactiveTrack,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Text(
            text = "${progress.done} / ${progress.total}",
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
