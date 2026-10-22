package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * The One UI pill button (spec §8.7; Phase 6b ruling 1): a CircleShape at least [RollaDimens.minTouchTarget] tall,
 * with its [text] in labelLarge. Filled (the default) is primary with an onPrimary label (4.51:1 in both themes);
 * [tonal] is surfaceContainerHigh with an onSurface label (13.41:1 dark / 15.41:1 light). [loading] swaps the label for
 * a progress ring in the label colour, ignores taps, and describes the button as [loadingDescription] for TalkBack
 * ([text] when it is null, so a loading pill always has an accessible name).
 * When disabled it renders at [DISABLED_CONTENT_ALPHA] and ignores input.
 *
 * Usage (the tag editor's buttons):
 * ```
 * OneUiPillButton(text = "Cancel", onClick = onCancel, tonal = true)
 * OneUiPillButton(text = "Save", onClick = onSave, loading = isSaving, loadingDescription = "Saving")
 * ```
 */
// The ruling 1 contract: label, action, modifier, and the four states it fixes (enabled, tonal, loading and its
// description). A holder type would only add indirection.
@Suppress("LongParameterList")
@Composable
fun OneUiPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    loading: Boolean = false,
    loadingDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (tonal) colors.surfaceContainerHigh else colors.primary
    val content = if (tonal) colors.onSurface else colors.onPrimary
    Box(
        modifier = modifier
            .heightIn(min = RollaDimens.minTouchTarget)
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA) // before background, so the whole pill fades
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            // With no loadingDescription the label stays the accessible name: the ring alone would leave none.
            .loadingDescription(if (loading) loadingDescription ?: text else null)
            .padding(horizontal = RollaDimens.chipHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        PillButtonContent(text = text, loading = loading, color = content)
    }
}

/** While loading, the merged button node is described as [description] (the label is no longer drawn). */
private fun Modifier.loadingDescription(description: String?): Modifier =
    if (description == null) this else semantics { contentDescription = description }

/** The label in labelLarge, or while [loading] a progress ring in the same [color]. */
@Composable
private fun PillButtonContent(text: String, loading: Boolean, color: Color) {
    if (loading) {
        CircularProgressIndicator(
            color = color,
            strokeWidth = RollaDimens.settingsProgressStroke,
            modifier = Modifier.size(RollaDimens.settingsProgressSize),
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
