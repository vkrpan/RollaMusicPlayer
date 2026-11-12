package com.rolla.musicplayer.navigation

import androidx.navigation.NavController

/**
 * Opens Find tracks (custom tags phase 4) from the Tracks tab's sort header. A plain push with no popUpTo, so back
 * returns to Home on the Tracks tab; launchSingleTop guards a double tap of the button.
 */
internal fun NavController.openFindTracks() {
    navigate(FindTracks) { launchSingleTop = true }
}
