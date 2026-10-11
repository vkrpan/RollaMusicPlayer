package com.rolla.musicplayer.navigation

import androidx.navigation.NavController

/**
 * The mini-player's tap. Now Playing's ⋮ pushes Album and Artist detail, and both show the pill again, so a plain push
 * would stack one more Now Playing on every round trip. Pop back to the Now Playing already on the back stack (which
 * drops the screens above it) and push only when there is none.
 */
internal fun NavController.openNowPlaying() {
    if (!popBackStack<NowPlaying>(inclusive = false)) {
        navigate(NowPlaying) { launchSingleTop = true }
    }
}
