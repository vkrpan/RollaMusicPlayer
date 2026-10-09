package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The floating mini-player pill's measured footprint (its own margins included, navigation bar excluded), provided by
 * the app shell (spec §7.3); 0 dp while the pill is hidden or the IME is visible. It drops to 0 as soon as the pill
 * is told to hide, before its exit animation ends. Every scrollable list on Home adds it, plus the navigation-bar
 * inset, to its bottom contentPadding (see [homeListBottomPadding]), so content scrolls under the pill and the last
 * item can still be scrolled fully into view. Pushed screens must not add it: the app shell already pads them by it
 * (until Phase 6 moves that padding into each list's contentPadding).
 */
val LocalMiniPlayerInset = compositionLocalOf { 0.dp }

/**
 * The bottom contentPadding for Home's scrollable lists: [LocalMiniPlayerInset] plus the navigation-bar inset, so the
 * list scrolls under both the floating pill and the navigation bar while its last item can still be scrolled fully
 * into view above them (spec §7.3).
 *
 * Only Home's lists use it. Pushed screens must not: the app shell already pads them by the pill's inset and each
 * pushed `Scaffold` owns its navigation-bar inset, so adding this would pad them twice.
 */
@Composable
fun homeListBottomPadding(): Dp =
    LocalMiniPlayerInset.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
