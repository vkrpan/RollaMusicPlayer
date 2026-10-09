package com.rolla.musicplayer.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset

/**
 * Ruling 4: a destination pushed on top of Home. The host pads it at the bottom by [LocalMiniPlayerInset] only, so
 * nothing sits under the floating pill. The wrapped screen owns the navigation-bar inset itself (its own Scaffold's
 * contentWindowInsets). Phase 6 replaces this padding with list contentPadding.
 */
@Composable
internal fun PushedScreen(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(bottom = LocalMiniPlayerInset.current)) {
        content()
    }
}
