@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui.customtags

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.OneUiDialog
import com.rolla.musicplayer.core.designsystem.component.OneUiDialogField
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.CustomTagKind

/**
 * The One UI dialog that names a new custom tag or subtag: titled "New tag"/"New subtag", a "Name" [OneUiDialogField]
 * and "Cancel"/"Create". Create is enabled while the name is not blank and hands [onCreate] the trimmed name; the
 * owner validates it and either closes the dialog or answers with [CustomTagNameDialogState.error], shown under the
 * field and read with it. The error hides as soon as the user edits the name, so it never describes a different one,
 * and shows again with the owner's answer to the next Create.
 *
 * Stateless towards its owner: like PlaylistNameDialog, it keeps only the text being typed, and does not dismiss
 * itself on Create.
 *
 * Manage tags renames an entry with the same dialog (custom tags phase 3): it passes [title] ("Rename tag"/"Rename
 * subtag") and [confirmLabel] ("Rename"), and [onCreate] then receives the new name.
 */
// Five parameters: the state, its two events, the modifier and the two rename labels.
@Suppress("LongParameterList")
@Composable
fun CustomTagNameDialog(
    state: CustomTagNameDialogState,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = state.kind.newEntryLabel(),
    confirmLabel: String = "Create",
) {
    var name by remember(state.kind, state.initialName) { mutableStateOf(state.initialName) }
    // True once the name changes after the last Create: the owner's error then describes a name no longer on screen.
    // Each Create resets it, so the owner's answer shows even when it repeats the previous error text.
    var editedSinceCreate by remember(state.kind) { mutableStateOf(false) }
    val error = state.error?.takeUnless { editedSinceCreate }
    val create = {
        editedSinceCreate = false
        onCreate(name.trim())
    }
    val edit = { text: String ->
        name = text
        editedSinceCreate = true
    }
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = { OneUiTextButton(text = confirmLabel, onClick = create, enabled = name.isNotBlank()) },
        dismissButton = { OneUiTextButton(text = "Cancel", onClick = onDismiss) },
        text = {
            OneUiDialogField(
                value = name,
                onValueChange = edit,
                label = "Name",
                isError = error != null,
                supportingText = error,
            )
        },
        modifier = modifier,
    )
}

/** "New tag" or "New subtag": the picker's create row and the dialog's title. */
internal fun CustomTagKind.newEntryLabel(): String = when (this) {
    CustomTagKind.TAG -> "New tag"
    CustomTagKind.SUBTAG -> "New subtag"
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Custom Tag Name Dialog - Light")
@Preview(name = "Custom Tag Name Dialog - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCustomTagNameDialog() {
    RollaMusicPlayerTheme {
        CustomTagNameDialog(
            state = CustomTagNameDialogState(kind = CustomTagKind.TAG, initialName = "Tech"),
            onCreate = {},
            onDismiss = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Custom Tag Name Dialog Error - Light")
@Preview(name = "Custom Tag Name Dialog Error - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCustomTagNameDialogError() {
    RollaMusicPlayerTheme {
        CustomTagNameDialog(
            state = CustomTagNameDialogState(
                kind = CustomTagKind.SUBTAG,
                initialName = "Peak time",
                error = "That name is already in the list",
            ),
            onCreate = {},
            onDismiss = {},
        )
    }
}
