@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText

/**
 * Presentational M3 dialog for capturing a playlist name — reused for both "create playlist"
 * and "rename playlist" flows (and, in a follow-up round, the "New playlist…" option inside the
 * add-to-playlist sheet). Carries no ViewModel/repository knowledge: the caller supplies the
 * dialog copy and decides what happens with the trimmed name.
 *
 * The caller is responsible for dismissing the dialog after [onConfirm] fires (e.g. by flipping
 * a `showDialog` flag to false) — this composable does not call [onDismiss] on confirm itself,
 * since some callers (e.g. an add-to-playlist sheet) may want to keep other UI open afterward.
 */
@Suppress("LongParameterList", "LongMethod")
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
    val isNameBlank = name.trim().isEmpty()
    // Blue text is accentText, never primary (spec §5.1): both buttons and the focused field label.
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.accentText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Playlist name") },
                colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = MaterialTheme.colorScheme.accentText),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = !isNameBlank,
                colors = buttonColors,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = buttonColors) {
                Text("Cancel")
            }
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
