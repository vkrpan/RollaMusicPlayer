package com.rolla.musicplayer.feature.player

import android.provider.Settings
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal object NowPlayingTransitionKey {
    const val ARTWORK = "now_playing_artwork"
}

@Composable
internal fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal fun artworkBoundsTransform(reducedMotion: Boolean = false): BoundsTransform =
    BoundsTransform { _, _ ->
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        }
    }
