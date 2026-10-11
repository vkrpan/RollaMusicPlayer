package com.rolla.musicplayer.feature.player

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring

internal object NowPlayingTransitionKey {
    const val ARTWORK = "now_playing_artwork"
}

// Ruling 6 (user, 2026-10-09: "smooth but fast"): MediumLow, not Medium, so the artwork's flight between the pill and
// Now Playing reads as a deliberate move. Reduced motion (rememberReducedMotion() at the call sites) still snaps.
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun artworkBoundsTransform(reducedMotion: Boolean = false): BoundsTransform =
    BoundsTransform { _, _ ->
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        }
    }
