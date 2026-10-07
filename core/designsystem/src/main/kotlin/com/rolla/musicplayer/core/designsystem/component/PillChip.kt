package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.chipLabel

/**
 * The EQ preset chip (spec §8.5): a [RollaDimens.chipHeight] pill with a bold label. Selected is primary/onPrimary;
 * unselected is surfaceContainerHigh/onSurface. The color change springs, or snaps under reduced motion. It is
 * single-select (RadioButton role); the caller wraps a grid of these in Modifier.selectableGroup().
 *
 * Footprint: the pill is drawn at [RollaDimens.chipHeight] but occupies [RollaDimens.minTouchTarget] of layout height,
 * with the slack split evenly above and below. Callers must space chip rows with [RollaDimens.chipRowLayoutGap], not
 * [RollaDimens.chipGap].
 */
// Two animated colors plus one flat pill/label layout; splitting it would only add indirection.
@Suppress("LongMethod")
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reducedMotion = rememberReducedMotion()
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHigh,
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurface,
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "chipContent",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA) // before background, so the whole pill fades
            .heightIn(min = RollaDimens.chipHeight)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = RollaDimens.chipHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.chipLabel,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
