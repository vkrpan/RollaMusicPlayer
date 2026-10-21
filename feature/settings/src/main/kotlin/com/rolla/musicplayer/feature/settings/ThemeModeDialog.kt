@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.OneUiDialog
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.ThemeMode

/**
 * The Appearance section Theme row opens this: three radio options (System / Light / Dark),
 * the ui-style-guide.md section 6 theme picker, now a OneUiDialog with three radio options (Phase 6a). Picking an
 * option both persists it (via [onSelect]) and closes the dialog (via [onDismiss]) immediately --
 * there is no separate confirm step, so the lone dialog button is a plain "Cancel" for closing
 * without changing anything.
 *
 * Dialog visibility itself is plain composable UI state owned by the caller (SettingsScreen uses
 * a rememberSaveable boolean), not ViewModel state -- it never needs to survive process death on
 * its own and has nothing to persist beyond the eventual [onSelect] call.
 */
@Composable
fun ThemeModeDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "Theme",
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    SettingsRadioOptionRow(
                        label = mode.displayLabel(),
                        selected = mode == selected,
                        onClick = {
                            onSelect(mode)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { OneUiTextButton(text = "Cancel", onClick = onDismiss) },
        modifier = modifier,
    )
}

/** User-facing label for each [ThemeMode] -- shared with the Appearance section Theme row value text. */
internal fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Theme Mode Dialog - Light")
@Preview(name = "Theme Mode Dialog - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewThemeModeDialog() {
    RollaMusicPlayerTheme {
        ThemeModeDialog(
            selected = ThemeMode.SYSTEM,
            onSelect = {},
            onDismiss = {},
        )
    }
}
