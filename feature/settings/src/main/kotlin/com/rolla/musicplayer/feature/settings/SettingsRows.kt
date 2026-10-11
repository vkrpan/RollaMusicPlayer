@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.component.OneUiSlider
import com.rolla.musicplayer.core.designsystem.component.OneUiSwitch
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader
import com.rolla.musicplayer.core.designsystem.theme.settingSubtitle
import com.rolla.musicplayer.core.designsystem.theme.settingTitle
import com.rolla.musicplayer.core.designsystem.theme.settingValue

/**
 * A small gray label ("Playback", "Privacy", ...) above its [SettingsSectionCard] (spec §8.6). It starts where the
 * card's row text starts (settingsCardInset + settingsRowPadding from the screen edge) and owns the gap above it and
 * the gap down to its card, so a screen only stacks headers and cards. Marked as a semantic heading so TalkBack
 * heading navigation can jump between sections.
 */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.sectionHeader,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(
                start = RollaDimens.settingsCardInset + RollaDimens.settingsRowPadding,
                top = RollaDimens.settingsSectionGap,
                bottom = RollaDimens.settingsHeaderGap,
            )
            .semantics { heading() },
    )
}

/**
 * The rounded (shapes.large) surfaceContainer card that groups a section's rows, inset settingsCardInset from the
 * screen edges (spec §8.6). It has no inner padding: every row pads itself, so ripples fill the card and dividers
 * measure their insets from its edges. Separate rows with [SettingsRowDivider].
 */
@Composable
fun SettingsSectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .padding(horizontal = RollaDimens.settingsCardInset)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(content = content)
    }
}

/** The hairline between two rows of a [SettingsSectionCard]: settingsDividerInset in from both card edges. */
@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) {
    InsetDivider(
        startInset = RollaDimens.settingsDividerInset,
        modifier = modifier,
        endInset = RollaDimens.settingsDividerInset,
    )
}

/**
 * Toggle row: label (plus optional [subLabel]) left, a [OneUiSwitch] right (spec §8.6). The whole row is the single
 * toggle target; the switch gets onCheckedChange = null, so it is purely visual and adds no second toggleable node.
 */
@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .settingsRowLayout(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(label = label, subLabel = subLabel, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(RollaDimens.listTrailingGap))
        OneUiSwitch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Value/navigation row (spec §8.6): the label, then an optional gray [subLabel], then an optional blue [value]
 * (settingValue in accentText) on its own line below, all start-aligned. There is no trailing chevron; the whole row
 * is clickable. Pass [value] for settings whose current state is worth surfacing inline (Theme, Equaliser On/Off).
 *
 * For in-flight action rows (Rescan library), [showProgress] adds a small trailing accentText spinner, and callers
 * typically pass [enabled] = false alongside so the action can't re-fire mid-run.
 */
// Each parameter is a distinct, defaulted slot of the row; grouping them would only add a holder type.
@Suppress("LongParameterList")
@Composable
fun SettingsValueNavRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    showProgress: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .settingsRowLayout(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(label = label, subLabel = subLabel, value = value, modifier = Modifier.weight(1f))
        if (showProgress) {
            Spacer(Modifier.width(RollaDimens.listTrailingGap))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.accentText,
                strokeWidth = RollaDimens.settingsProgressStroke,
                modifier = Modifier.size(RollaDimens.settingsProgressSize),
            )
        }
    }
}

/**
 * Slider row (spec §8.6): the label, a centered blue value ("1.0x"), then a [OneUiSlider].
 *
 * Deliberately does NOT call [onValueChangeFinished] on every drag tick -- that would mean a
 * DataStore write per pixel of drag movement. Instead the in-drag position is kept in local
 * remembered state (isDragging / dragValue) purely for live visual feedback (slider thumb plus
 * value text), and [onValueChangeFinished] (with the final settled value) fires exactly once, when
 * the user lifts their finger -- mirroring NowPlayingScreen seek bar (drag-then-commit) rather
 * than persisting mid-gesture.
 *
 * The commit is displayed OPTIMISTICALLY: on release, [pendingValue] holds the just-committed
 * value on screen until the external [value] round-trips back through the (async, best-effort)
 * DataStore write and converges -- without it, the thumb would visibly revert to the pre-drag
 * value for a few frames on every single adjustment before jumping forward again.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun SettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    var pendingValue by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(value) {
        if (value == pendingValue) pendingValue = null
    }
    val displayedValue = when {
        isDragging -> dragValue
        else -> pendingValue ?: value
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .settingsRowLayout(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.settingTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = valueLabel(displayedValue),
            style = MaterialTheme.typography.settingValue,
            color = MaterialTheme.colorScheme.accentText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        OneUiSlider(
            value = displayedValue,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                pendingValue = dragValue
                isDragging = false
                onValueChangeFinished(dragValue)
            },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}

/**
 * Every row's layout: at least the single-line row height (above the 48 dp touch floor), then settingsRowPadding at
 * the sides and settingsRowVerticalPadding above and below the content.
 */
internal fun Modifier.settingsRowLayout(): Modifier = this
    .heightIn(min = RollaDimens.singleLineRowHeight)
    .padding(horizontal = RollaDimens.settingsRowPadding, vertical = RollaDimens.settingsRowVerticalPadding)

/**
 * A read-only block of prose inside a [SettingsSectionCard] (About, Privacy), padded like a row (settingsRowPadding at
 * the sides, settingsRowVerticalPadding above and below), so its text starts where row text starts.
 */
@Composable
internal fun SettingsTextBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = RollaDimens.settingsRowPadding, vertical = RollaDimens.settingsRowVerticalPadding),
        content = content,
    )
}

/** The label, then the optional gray sub-label, then the optional blue value: stacked and start-aligned. */
@Composable
private fun SettingsRowText(
    label: String,
    subLabel: String?,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.settingTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                style = MaterialTheme.typography.settingSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.settingValue,
                color = MaterialTheme.colorScheme.accentText,
            )
        }
    }
}

/**
 * One radio option inside ThemeModeDialog. The whole row is selectable (not just the RadioButton),
 * and RadioButton own onClick is null for the same row-wide-target reason documented on
 * SettingsToggleRow.
 */
@Composable
internal fun SettingsRadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.minTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(RollaDimens.dialogOptionLabelGap))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Settings Rows - Light")
@Preview(name = "Settings Rows - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSettingsRows() {
    RollaMusicPlayerTheme {
        SettingsSectionCard {
            SettingsValueNavRow(label = "Theme", value = "System", onClick = {})
            SettingsRowDivider()
            SettingsToggleRow(
                label = "Dynamic colour",
                subLabel = "Use colours from your wallpaper",
                checked = true,
                onCheckedChange = {},
            )
            SettingsRowDivider()
            SettingsSliderRow(
                label = "Playback speed",
                value = 1.0f,
                valueRange = 0.5f..2.0f,
                valueLabel = { "1.0x" },
                onValueChangeFinished = {},
            )
            SettingsRowDivider()
            SettingsValueNavRow(label = "Rescan library", onClick = {}, showProgress = true)
        }
    }
}
