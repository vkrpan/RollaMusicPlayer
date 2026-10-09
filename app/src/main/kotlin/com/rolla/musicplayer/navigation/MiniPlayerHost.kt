package com.rolla.musicplayer.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap

/**
 * The floating mini-player overlay (spec §7.3, ruling 3). [content] fills the screen and the pill floats over its
 * bottom edge, above the navigation bar and clear of a horizontal display cutout. This host is the single owner of
 * the pill's system insets; it deliberately does not use safeDrawing, which would lift the pill above the IME.
 *
 * The pill reports its measured height through the [Modifier] [pill] receives (applied first in the pill's chain, so
 * it sits inside the host's inset padding and excludes the navigation bar). That inset padding wraps the pill inside
 * the animated content, so the enter/exit slide covers the bar too. [content] reads that height as
 * [LocalMiniPlayerInset] while the pill is shown, and 0 dp while it is hidden or the IME is visible. The inset drops
 * to 0 as soon as the pill is told to hide, before its exit animation ends, so the incoming screen lays out in its
 * final state from the first frame.
 */
@OptIn(ExperimentalLayoutApi::class) // WindowInsets.isImeVisible
@Composable
internal fun MiniPlayerHost(
    showPill: Boolean,
    pill: @Composable AnimatedVisibilityScope.(Modifier) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    // A boolean that flips only when the keyboard opens or closes, not on every IME animation frame.
    val imeVisible = WindowInsets.isImeVisible
    var pillHeight by remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(LocalMiniPlayerInset provides if (showPill && !imeVisible) pillHeight else 0.dp) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(
                visible = showPill,
                enter = slideInVertically(rollaSpringOrSnap(reducedMotion)) { it } +
                    fadeIn(rollaSpringOrSnap(reducedMotion)),
                exit = slideOutVertically(rollaSpringOrSnap(reducedMotion)) { it } +
                    fadeOut(rollaSpringOrSnap(reducedMotion)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // The inset padding lives inside the animated content, so the slide offsets by the pill plus the bar:
                // the pill slides fully off-screen instead of sliding out over the navigation bar.
                val visibilityScope = this
                Box(Modifier.windowInsetsPadding(PillInsets)) {
                    visibilityScope.pill(Modifier.onSizeChanged { pillHeight = with(density) { it.height.toDp() } })
                }
            }
        }
    }
}

private val PillInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
