package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack

/**
 * The One UI settings slider (spec §6): a thick [RollaDimens.settingsSliderTrack] rounded track (primary active,
 * sliderInactiveTrack inactive) and a [RollaDimens.settingsSliderThumb] thumb (background fill, a
 * [RollaDimens.sliderThumbRing] primary ring). It is built on M3 Slider, so drag, keyboard and SetProgress semantics
 * are the platform's own. When disabled it renders at [DISABLED_CONTENT_ALPHA] and ignores input.
 *
 * Footprint: M3 Slider takes at least [RollaDimens.minTouchTarget] of height, although the visible thumb is only
 * [RollaDimens.settingsSliderThumb] and the track [RollaDimens.settingsSliderTrack]. Callers laying out a settings
 * row must budget for the full touch height, not the drawn height.
 */
// Mirrors M3 Slider's own parameter list, so callers keep the platform API unchanged.
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = MaterialTheme.colorScheme
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA),
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        steps = steps,
        thumb = {
            RingThumb(
                diameter = RollaDimens.settingsSliderThumb,
                ringWidth = RollaDimens.sliderThumbRing,
                ringColor = colors.primary,
                fillColor = colors.background,
            )
        },
        track = { state ->
            SliderTrackLine(
                state = state,
                thickness = RollaDimens.settingsSliderTrack,
                activeColor = colors.primary,
                inactiveColor = colors.sliderInactiveTrack,
            )
        },
        valueRange = valueRange,
    )
}
