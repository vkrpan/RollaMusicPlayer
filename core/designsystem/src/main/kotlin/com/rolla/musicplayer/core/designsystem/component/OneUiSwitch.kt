package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack
import com.rolla.musicplayer.core.designsystem.theme.switchThumb

/**
 * The One UI switch: a [RollaDimens.switchTrackWidth]×[RollaDimens.switchTrackHeight] pill track (primary when on,
 * sliderInactiveTrack when off) with a white thumb that springs across. Pass onCheckedChange = null when a toggleable
 * row owns the click and semantics (SettingsToggleRow); the switch is then purely visual.
 *
 * Footprint: standalone, it occupies a [RollaDimens.minTouchTarget] square with the track centered in it; with
 * onCheckedChange = null, it occupies exactly the track.
 */
// Two animated states plus one flat track/thumb layout; splitting it would only add indirection.
@Suppress("LongMethod")
@Composable
fun OneUiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reducedMotion = rememberReducedMotion()
    val trackColor = animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.sliderInactiveTrack,
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "switchTrack",
    )
    val travel = RollaDimens.switchTrackWidth - RollaDimens.switchThumb - RollaDimens.switchThumbInset * 2
    val thumbOffset = animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .switchInteraction(checked, enabled, onCheckedChange)
            .size(width = RollaDimens.switchTrackWidth, height = RollaDimens.switchTrackHeight)
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA)
            .drawBehind { drawRoundRect(color = trackColor.value, cornerRadius = CornerRadius(size.height / 2f)) }
            .padding(RollaDimens.switchThumbInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.value.roundToPx(), 0) }
                .size(RollaDimens.switchThumb)
                .background(MaterialTheme.colorScheme.switchThumb, CircleShape),
        )
    }
}

private fun Modifier.switchInteraction(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
): Modifier = if (onCheckedChange == null) {
    this
} else {
    minimumInteractiveComponentSize()
        .clip(CircleShape)
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
}
