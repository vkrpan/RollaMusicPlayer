package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.seekTrackActive
import com.rolla.musicplayer.core.designsystem.theme.seekTrackInactive

/**
 * The Now Playing seekbar (spec §8.4): an [RollaDimens.npSeekThickness] track (seekTrackActive behind the thumb,
 * seekTrackInactive ahead of it) and a solid [RollaDimens.npSeekThumb] seekTrackActive thumb. It is an M3 Slider with
 * a custom track and thumb, so drag, keyboard and SetProgress semantics are the platform's own. [value] is a fraction
 * in 0..1. The caller owns drag-then-commit (see NowPlayingScreen). Occupies the M3 Slider's 48 dp touch height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        thumb = {
            Box(
                Modifier
                    .size(RollaDimens.npSeekThumb)
                    .background(colors.seekTrackActive, CircleShape),
            )
        },
        track = { state ->
            SliderTrackLine(
                state = state,
                thickness = RollaDimens.npSeekThickness,
                activeColor = colors.seekTrackActive,
                inactiveColor = colors.seekTrackInactive,
            )
        },
    )
}
