package com.rolla.musicplayer.feature.tageditor

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.OneUiDialog
import com.rolla.musicplayer.core.designsystem.component.OneUiDropdownMenu
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.component.OneUiMenuItem
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.ui.tracksCountLabel
import com.rolla.musicplayer.feature.tageditor.io.rememberMediaWriteRequester

/**
 * The Tracks selection bar's ⋮ (spec goal 5, §5.7; plan Task 8): a menu with "Set artwork" (the system photo picker,
 * local images only) and "Remove artwork" (destructive; confirmed by "Remove artwork from N tracks?"). Both write
 * every selected file through [SelectionArtworkViewModel] after ONE write-permission prompt, which this composable
 * resolves with `MediaWriteRequester` (and API 29's recovery prompt), as the tag editor routes do.
 *
 * While the write runs a progress ring described "Saving artwork" stands in for ⋮. The host reads the same
 * [viewModel]'s isWorking itself (Home obtains it, ruling P23) to freeze the selection and lock the bar. Each
 * [messages] line is for the host's snackbar; [onResult] reports the ids not written, which the host keeps selected.
 * Both are delivered once, message first, then forgotten by the ViewModel.
 */
// LongMethod: the picker, the two consent effects, the delivery effect and the ⋮ / ring / dialog, one flow.
@Suppress("LongMethod")
@Composable
fun SelectionArtworkActions(
    selectedIds: List<Long>,
    onResult: (SelectionArtworkResult) -> Unit,
    modifier: Modifier = Modifier,
    messages: (String) -> Unit = {},
    viewModel: SelectionArtworkViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val requester = rememberMediaWriteRequester()
    val currentIds by rememberUpdatedState(selectedIds)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentMessages by rememberUpdatedState(messages)
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    // System PhotoPicker (the document picker before Android 13): LOCAL images only, per the offline contract.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onSetArtwork(currentIds, uri.toString())
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

    // One effect, message first: a result that clears the selection removes this bar, which would cancel a second
    // effect still waiting to run.
    LaunchedEffect(uiState.message, uiState.result) {
        uiState.message?.let { message ->
            currentMessages(message)
            viewModel.dismissMessage()
        }
        uiState.result?.let { result ->
            currentOnResult(result)
            viewModel.consumeResult()
        }
    }

    Box(modifier = modifier) {
        if (uiState.isWorking) {
            Box(modifier = Modifier.size(RollaDimens.minTouchTarget), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = RollaDimens.settingsProgressStroke,
                    modifier = Modifier
                        .size(RollaDimens.iconSize)
                        .semantics { contentDescription = SAVING_ARTWORK },
                )
            }
        } else {
            OneUiIconButton(
                icon = RollaIcons.More,
                contentDescription = "More options",
                onClick = { menuExpanded = true },
            )
        }
        OneUiDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            OneUiMenuItem(
                text = "Set artwork",
                onClick = {
                    menuExpanded = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            OneUiMenuItem(
                text = "Remove artwork",
                onClick = {
                    menuExpanded = false
                    confirmRemove = true
                },
                destructive = true,
            )
        }
    }

    if (confirmRemove) {
        OneUiDialog(
            onDismissRequest = { confirmRemove = false },
            title = "Remove artwork from ${tracksCountLabel(selectedIds.size)}?",
            confirmButton = {
                OneUiTextButton(
                    text = "Remove",
                    onClick = {
                        confirmRemove = false
                        viewModel.onRemoveArtwork(currentIds)
                    },
                    destructive = true,
                )
            },
            dismissButton = { OneUiTextButton(text = "Cancel", onClick = { confirmRemove = false }) },
        )
    }
}

private const val SAVING_ARTWORK = "Saving artwork"
