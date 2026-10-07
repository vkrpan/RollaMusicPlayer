package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * The floating mini-player's height, [com.rolla.musicplayer.core.designsystem.theme.RollaDimens.miniPlayerHeight],
 * while it is visible; 0.dp otherwise. The measured pill sits directly on the navigation bar with no bottom margin
 * (its only margins are horizontal), so nothing is added to the height. The app shell provides it (spec §7.3). Every
 * scrollable list adds it, plus the navigation-bar inset, to its bottom contentPadding, so content scrolls under the
 * pill and the last item can still be scrolled fully into view.
 */
val LocalMiniPlayerInset = compositionLocalOf { 0.dp }
