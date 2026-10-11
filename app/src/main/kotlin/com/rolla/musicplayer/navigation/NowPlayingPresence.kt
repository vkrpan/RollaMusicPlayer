package com.rolla.musicplayer.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Whether the Now Playing destination is still the NavHost's visible target, as its own enter/exit transition says.
 *
 * The back stack is not enough to time the pill. On a pop, the back stack (and so the route) changes in one frame,
 * but NavHost starts its transition a frame later (a LaunchedEffect animates its SeekableTransitionState), and that
 * frame composes the screen underneath. A pill shown on the route alone enters while Now Playing has not begun to
 * exit. Both artwork shared elements are then "visible": Now Playing's art stops drawing, the pill's art is laid out
 * at the 168 dp Now Playing size (on device: a large grey circle at the pill), and its bounds animation starts on the
 * pill's clock before the slow frame, so the flight is over before the next frame is drawn. Holding the pill until
 * Now Playing targets exit makes the pop match the forward path: the exiting side is in place first, then the entering
 * side picks up its bounds.
 *
 * Presence is kept per back-stack entry. Two Now Playing instances can be composed together: tapping the pill while
 * the popped Now Playing is still exiting pushes a new entry. A single flag would let the old instance, when it leaves,
 * clear the new one's presence.
 */
@Stable
internal class NowPlayingPresence {
    // A snapshot-state set of entry ids (runtime 1.7 has no mutableStateSetOf).
    private val visibleTargets = mutableStateMapOf<String, Unit>()

    /** True while any Now Playing entry is the NavHost's visible target. */
    val isTarget: Boolean get() = visibleTargets.isNotEmpty()

    internal fun report(entryId: String, isTarget: Boolean) {
        if (isTarget) visibleTargets[entryId] = Unit else visibleTargets.remove(entryId)
    }
}

/** The Now Playing destination. It reports its own transition target to [presence] (see [NowPlayingPresence]). */
internal fun NavGraphBuilder.nowPlayingDestination(
    presence: NowPlayingPresence,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<NowPlaying> { entry ->
        val entryId = entry.id
        val isTarget = transition.targetState == EnterExitState.Visible
        DisposableEffect(entryId, isTarget) {
            presence.report(entryId, isTarget)
            onDispose { presence.report(entryId, isTarget = false) }
        }
        content(entry)
    }
}
