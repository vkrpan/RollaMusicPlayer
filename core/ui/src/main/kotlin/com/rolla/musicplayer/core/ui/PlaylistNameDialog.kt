@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.OneUiDialog
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.component.OneUiTextField
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme

/**
 * The One UI dialog for capturing a playlist name (spec §8.7), reused by the "create playlist" and "rename playlist"
 * flows and by the add-to-playlist sheet's "New playlist" entry. Carries no ViewModel/repository knowledge: the caller
 * supplies the dialog copy and decides what happens with the trimmed name. Both buttons are accentText
 * [OneUiTextButton]s, and the field sits one container step above the dialog's surfaceContainerHigh.
 *
 * The caller is responsible for dismissing the dialog after [onConfirm] fires (e.g. by flipping
 * a `showDialog` flag to false) — this composable does not call [onDismiss] on confirm itself,
 * since some callers (e.g. an add-to-playlist sheet) may want to keep other UI open afterward.
 */
@Suppress("LongParameterList")
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            OneUiTextButton(text = confirmLabel, onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank())
        },
        dismissButton = { OneUiTextButton(text = "Cancel", onClick = onDismiss) },
        text = {
            OneUiTextField(
                value = name,
                onValueChange = { name = it },
                label = "Playlist name",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        modifier = modifier,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Playlist Name Dialog - Light")
@Preview(name = "Playlist Name Dialog - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPlaylistNameDialogCreate() {
    RollaMusicPlayerTheme {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Playlist Name Dialog Rename - Light")
@Preview(name = "Playlist Name Dialog Rename - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPlaylistNameDialogRename() {
    RollaMusicPlayerTheme {
        PlaylistNameDialog(
            title = "Rename playlist",
            confirmLabel = "Rename",
            initialName = "Workout Mix",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
