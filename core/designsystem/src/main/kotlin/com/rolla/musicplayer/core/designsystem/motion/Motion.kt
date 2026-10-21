package com.rolla.musicplayer.core.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user turned animations off (ANIMATOR_DURATION_SCALE == 0; Accessibility > Remove animations).
 * Components snap instead of animating when this is true (ui-style-guide §9). The value is read once per call site.
 *
 * This replaced the module-local isReducedMotion() copies in :feature:equalizer, :feature:player and
 * :feature:playlists (the last one went in Phase 6a). Do not add another.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** The calm, non-bouncy spring every interactive component uses (same family as the v1.0 EQ and artwork motion). */
fun <T> rollaSpring(): FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

/** [rollaSpring], or an instant snap when [reducedMotion] is true. */
fun <T> rollaSpringOrSnap(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) snap() else rollaSpring()
