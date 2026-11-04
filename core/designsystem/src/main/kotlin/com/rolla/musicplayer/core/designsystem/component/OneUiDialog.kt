package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.destructiveText
import com.rolla.musicplayer.core.designsystem.theme.dialogFieldContainer
import kotlin.math.min

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
        text = text?.let { body -> { CompositionLocalProvider(LocalInRaisedSurface provides true) { body() } } },
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
 * The name field of a One UI dialog (the playlist name and preset name dialogs): a [OneUiTextField] on
 * dialogFieldContainer (white in light, where surfaceContainerHighest was 1.02:1 on the dialog; Phase 7 F2).
 *
 * An empty field takes focus when the dialog shows, so the keyboard opens without a tap (F3: New playlist, Save as
 * preset). A field that opens with a name (Rename) does not: a String field focused that way puts the cursor before
 * the name, so the first key would land in front of it, and the TextFieldValue alternative crashes Compose 1.7's text
 * layout on a whole-text replacement while focused. The user's tap places the cursor instead (Phase 7 ruling P37).
 */
@Composable
fun OneUiDialogField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val focusOnShow = remember { value.isEmpty() }
    OneUiTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        containerColor = MaterialTheme.colorScheme.dialogFieldContainer,
        modifier = modifier.focusRequester(focusRequester),
    )
    if (focusOnShow) {
        // Wait one frame: the dialog's text slot can run this effect before the field's focus node is attached, and
        // an unattached FocusRequester throws (seen in the add-to-playlist sheet → New playlist flow).
        LaunchedEffect(focusRequester) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
}

/**
 * A One UI text field (spec §8.7): filled, no indicator line, the medium (16 dp) shape, and [containerColor]. Pass a
 * container that stands apart from the surface it sits on; inside a dialog use [OneUiDialogField]
 * (dialogFieldContainer). Its focused label and
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
 *
 * [trailingControl], when given, sits outside the field at its end (the batch editor's apply switch), vertically
 * centred on the field's container, not on the container plus its supporting text, so it stays in line with the field
 * when an error message appears under it (Phase 6b Task 4 review M4).
 */
// TextField's own parameters plus the container, the field-error trio and the trailing control, which the kit contract
// fixes (6a ruling 2, 6b ruling 1, Phase 7 Task 5).
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
    trailingControl: (@Composable () -> Unit)? = null,
) {
    val colors = oneUiTextFieldColors(containerColor)
    val field = @Composable { fieldModifier: Modifier ->
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            enabled = enabled,
            label = { Text(text = label) },
            supportingText = supportingText?.let { { Text(text = it, modifier = Modifier.reportContainerBottom()) } },
            isError = isError,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
        )
    }
    if (trailingControl == null) {
        field(modifier)
    } else {
        FieldWithTrailingControl(field = { field(Modifier) }, trailingControl = trailingControl, modifier = modifier)
    }
}

/**
 * The bottom of a OneUiTextField's container, in the field's own coordinates. Material 3 places the supporting text
 * [RollaDimens.fieldSupportingTextGap] below the container, so the supporting text reports the line that far above its
 * own top, and the line reaches [FieldWithTrailingControl] through the field's layouts. Without supporting text the
 * line is absent and the container is the whole field.
 */
private val ContainerBottom = HorizontalAlignmentLine(merger = ::min)

private fun Modifier.reportContainerBottom(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val line = -RollaDimens.fieldSupportingTextGap.roundToPx()
    layout(placeable.width, placeable.height, mapOf(ContainerBottom to line)) { placeable.place(0, 0) }
}

/** The field, filling the width the trailing control leaves, then the control centred on the field's container. */
@Composable
private fun FieldWithTrailingControl(
    field: @Composable () -> Unit,
    trailingControl: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box { field() }
            Box { trailingControl() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val control = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val fieldMaxWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - control.width).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        val fieldMinWidth = (constraints.minWidth - control.width).coerceIn(0, fieldMaxWidth)
        val fieldPlaceable = measurables[0].measure(
            Constraints(minWidth = fieldMinWidth, maxWidth = fieldMaxWidth, maxHeight = constraints.maxHeight),
        )
        val reportedBottom = fieldPlaceable[ContainerBottom]
        val containerBottom = if (reportedBottom == AlignmentLine.Unspecified) fieldPlaceable.height else reportedBottom
        val controlY = ((containerBottom - control.height) / 2).coerceAtLeast(0)
        layout(fieldPlaceable.width + control.width, maxOf(fieldPlaceable.height, controlY + control.height)) {
            fieldPlaceable.placeRelative(0, 0)
            control.placeRelative(fieldPlaceable.width, controlY)
        }
    }
}

/**
 * The kit's field colours, remembered per colour scheme and container (6b perf audit minor 7): [TextFieldDefaults.colors]
 * with no arguments is Material's cached default for the current scheme, and the copy is rebuilt only when one of its
 * inputs changes, not on every recomposition.
 */
@Composable
private fun oneUiTextFieldColors(containerColor: Color): TextFieldColors {
    val base = TextFieldDefaults.colors()
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.accentText
    val destructive = scheme.destructiveText
    val gray = scheme.onSurfaceVariant
    return remember(base, containerColor, accent, destructive, gray) {
        base.copy(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedLabelColor = accent,
            unfocusedLabelColor = gray,
            errorLabelColor = destructive,
            cursorColor = accent,
            errorCursorColor = destructive,
            focusedSupportingTextColor = gray,
            unfocusedSupportingTextColor = gray,
            disabledSupportingTextColor = gray.copy(alpha = DISABLED_CONTENT_ALPHA),
            errorSupportingTextColor = destructive,
        )
    }
}
