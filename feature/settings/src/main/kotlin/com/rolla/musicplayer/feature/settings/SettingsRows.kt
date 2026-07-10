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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack

private val SectionCardPadding = 16.dp
private val RowMinHeight = 56.dp
private val RowLabelGap = 16.dp
private val RowTextSpacing = 2.dp
private val ChevronIconSize = 24.dp
private val OptionRowMinHeight = 48.dp
private val OptionRowLabelGap = 12.dp
private val RowProgressSize = 20.dp
private val RowProgressStrokeWidth = 2.dp

/**
 * A small gray label ("Playback", "Privacy", ...) grouping the [SettingsSectionCard] beneath it --
 * ui-style-guide.md section 6 "Settings Screens": section headers use the sectionHeader token.
 * Marked as a semantic heading so TalkBack heading-navigation can jump between sections.
 */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.sectionHeader,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The rounded (shapes.large) surfaceContainer card that groups a section rows -- ui-style-guide.md
 * section 6. Callers stack SettingsToggleRow / SettingsValueNavRow / SettingsSliderRow (or any
 * other row) as [content]; this composable owns only the card chrome and inner padding.
 */
@Composable
fun SettingsSectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(SectionCardPadding), content = content)
    }
}

/**
 * Toggle row: label (plus optional [subLabel]) left, M3 Switch right (ui-style-guide.md section 6).
 * The whole row is toggleable, not just the switch, for a larger touch target; the Switch own
 * onCheckedChange is null so it renders purely as a visual indicator and never double-handles the
 * tap (this is the documented Material3 pattern for a row-wide toggle target).
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
            .heightIn(min = RowMinHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowLabel(label = label, subLabel = subLabel, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(RowLabelGap))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Value/navigation row: label (plus optional [subLabel]) left, either a primary-colored [value]
 * text or a trailing chevron right (ui-style-guide.md section 6) -- the whole row is clickable.
 * Pass [value] for settings whose current state is worth surfacing inline (Theme, Equalizer
 * On/Off); leave it null for pure navigation/action rows (Rescan library, Privacy, About), which
 * render the chevron instead.
 *
 * For in-flight action rows (Rescan library), [showProgress] swaps the trailing slot for a small
 * spinner and callers typically pass [enabled] = false alongside so the action can't re-fire
 * mid-run.
 */
@Suppress("LongParameterList", "LongMethod")
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
            .heightIn(min = RowMinHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowLabel(label = label, subLabel = subLabel, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(RowLabelGap))
        when {
            showProgress -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = RowProgressStrokeWidth,
                modifier = Modifier.size(RowProgressSize),
            )
            value != null -> Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ChevronIconSize),
            )
        }
    }
}

/**
 * Slider row: label, centered live value text, then a Slider with primary active track and
 * sliderInactiveTrack inactive track (ui-style-guide.md section 6).
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

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = valueLabel(displayedValue),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
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
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.sliderInactiveTrack,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}

/** Shared label (plus optional sub-label) column used by every row type above. */
@Composable
private fun SettingsRowLabel(label: String, subLabel: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = RowTextSpacing),
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
            .heightIn(min = OptionRowMinHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(OptionRowLabelGap))
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
            SettingsToggleRow(
                label = "Dynamic color",
                subLabel = "Use colors from your wallpaper",
                checked = true,
                onCheckedChange = {},
            )
            SettingsSliderRow(
                label = "Playback speed",
                value = 1.0f,
                valueRange = 0.5f..2.0f,
                valueLabel = { "1.0x" },
                onValueChangeFinished = {},
            )
            SettingsValueNavRow(label = "Rescan library", onClick = {})
        }
    }
}
