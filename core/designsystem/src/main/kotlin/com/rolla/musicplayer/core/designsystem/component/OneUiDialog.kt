package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.destructiveText

/** The One UI dialog (spec §8.7): surfaceContainerHigh, the large shape, an onSurface title, onSurfaceVariant text. */
// AlertDialog's slots, which the Phase 6a kit contract fixes (ruling 2); bundling them would only add a holder type.
@Suppress("LongParameterList")
@Composable
fun OneUiDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = text,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A dialog or bar text button: accentText (spec §5.1: never primary), or destructiveText when [destructive] (never
 * error as text: dark error is below 4.5:1 on surfaceContainerHigh).
 */
@Composable
fun OneUiTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val content = if (destructive) MaterialTheme.colorScheme.destructiveText else MaterialTheme.colorScheme.accentText
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = content),
    ) {
        Text(text = text)
    }
}

/**
 * A One UI text field (spec §8.7): filled, no indicator line, the medium (16 dp) shape, and [containerColor]. Pass a
 * container one step above the surface it sits on (surfaceContainerHighest inside a dialog). Its focused label and
 * cursor use accentText.
 *
 * [supportingText] is drawn under the field in onSurfaceVariant, or in destructiveText when [isError] (6a ruling P2:
 * never error as text); the label and cursor turn destructiveText too, and the field keeps its container and no
 * indicator line. Usage (a tag-editor year field on a surfaceContainer card):
 * ```
 * OneUiTextField(
 *     value = year, onValueChange = onYearChange, label = "Year",
 *     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
 *     isError = yearError != null, supportingText = yearError,
 * )
 * ```
 */
// TextField's own parameters plus the container and the field-error trio, which the kit contract fixes (6a ruling 2,
// 6b ruling 1).
@Suppress("LongParameterList")
@Composable
fun OneUiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(text = label) },
        supportingText = supportingText?.let { { Text(text = it) } },
        isError = isError,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
        colors = oneUiTextFieldColors(containerColor),
    )
}

@Composable
private fun oneUiTextFieldColors(containerColor: Color): TextFieldColors {
    val colors = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        disabledContainerColor = containerColor,
        errorContainerColor = containerColor,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedLabelColor = colors.accentText,
        unfocusedLabelColor = colors.onSurfaceVariant,
        errorLabelColor = colors.destructiveText,
        cursorColor = colors.accentText,
        errorCursorColor = colors.destructiveText,
        focusedSupportingTextColor = colors.onSurfaceVariant,
        unfocusedSupportingTextColor = colors.onSurfaceVariant,
        disabledSupportingTextColor = colors.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA),
        errorSupportingTextColor = colors.destructiveText,
    )
}
