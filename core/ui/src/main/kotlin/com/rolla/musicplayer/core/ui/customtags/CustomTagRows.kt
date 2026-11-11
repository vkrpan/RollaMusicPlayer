@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui.customtags

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.DISABLED_CONTENT_ALPHA
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.component.LabelChip
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.settingSubtitle
import com.rolla.musicplayer.core.designsystem.theme.settingTitle

/**
 * The tag editors' custom tags card: a "Tags" row and a "Subtags" row, each showing its chosen names as chips that
 * wrap onto more lines (a FlowRow, so a large font never pushes one past the card), or "None". Tapping a row opens the
 * picker on that row's page; which page is the owner's call ([onTagsClick], [onSubTagsClick]).
 *
 * The card matches the editors' TagEditorCard (surfaceContainer, the large shape, settingsRowPadding inside). Each row
 * is one button for TalkBack ("Tags, Techno, Peak time, button"): the chips are the kit's display-only [LabelChip]s,
 * never their own targets, and a name too long for one line wraps inside its chip instead of ellipsizing.
 *
 * When not [enabled] (a file whose format cannot carry custom tags), both rows draw at [DISABLED_CONTENT_ALPHA], ignore
 * taps, report disabled and carry the note "Tags can be saved to MP3 and FLAC files only." as their state, so TalkBack
 * reads the reason with each row; the same note is shown under them at full emphasis.
 */
// Six parameters: the two lists, the enabled state, the two row actions (one per page) and the modifier.
@Suppress("LongParameterList")
@Composable
fun CustomTagRows(
    tagNames: List<String>,
    subTagNames: List<String>,
    enabled: Boolean,
    onTagsClick: () -> Unit,
    onSubTagsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CUSTOM_TAG_ROWS_TAG),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            CustomTagRow(label = "Tags", names = tagNames, enabled = enabled, onClick = onTagsClick)
            InsetDivider(startInset = RollaDimens.settingsDividerInset, endInset = RollaDimens.settingsDividerInset)
            CustomTagRow(label = "Subtags", names = subTagNames, enabled = enabled, onClick = onSubTagsClick)
            if (!enabled) {
                Text(
                    text = UNSUPPORTED_FORMAT_NOTE,
                    style = MaterialTheme.typography.settingSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = RollaDimens.settingsRowPadding,
                        end = RollaDimens.settingsRowPadding,
                        bottom = RollaDimens.settingsRowPadding,
                    ),
                )
            }
        }
    }
}

/** One row: the [label], then the [names] as chips under it, or "None". The whole row is the button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomTagRow(label: String, names: List<String>, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .unsupportedNote(enabled)
            .padding(RollaDimens.settingsRowPadding)
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA),
        verticalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
    ) {
        Text(text = label, style = MaterialTheme.typography.settingTitle, color = MaterialTheme.colorScheme.onSurface)
        if (names.isEmpty()) {
            Text(
                text = "None",
                style = MaterialTheme.typography.settingSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
                verticalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
            ) {
                names.forEach { name -> LabelChip(label = name, modifier = Modifier.testTag(CUSTOM_TAG_CHIP_TAG)) }
            }
        }
    }
}

/** A disabled row's state for TalkBack: the reason it is disabled, read with the row (Ruling 3). */
private fun Modifier.unsupportedNote(enabled: Boolean): Modifier =
    if (enabled) this else semantics { stateDescription = UNSUPPORTED_FORMAT_NOTE }

/** The note under the rows of a file that cannot carry custom tags. */
internal const val UNSUPPORTED_FORMAT_NOTE = "Tags can be saved to MP3 and FLAC files only."

/** Test tag on the [CustomTagRows] card. */
internal const val CUSTOM_TAG_ROWS_TAG = "custom_tag_rows"

/** Test tag on each chip of a [CustomTagRows] row. */
internal const val CUSTOM_TAG_CHIP_TAG = "custom_tag_chip"

@Suppress("UnusedPrivateMember")
@Preview(name = "Custom Tag Rows - Light")
@Preview(name = "Custom Tag Rows - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCustomTagRows() {
    RollaMusicPlayerTheme {
        CustomTagRows(
            tagNames = listOf("Techno", "Acid", "Minimal"),
            subTagNames = emptyList(),
            enabled = true,
            onTagsClick = {},
            onSubTagsClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Custom Tag Rows Disabled - Light")
@Preview(name = "Custom Tag Rows Disabled - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCustomTagRowsDisabled() {
    RollaMusicPlayerTheme {
        CustomTagRows(
            tagNames = listOf("Techno"),
            subTagNames = listOf("Peak time"),
            enabled = false,
            onTagsClick = {},
            onSubTagsClick = {},
        )
    }
}
