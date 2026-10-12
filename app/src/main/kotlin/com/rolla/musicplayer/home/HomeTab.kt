package com.rolla.musicplayer.home

/**
 * Home pager pages in spec order (§7.2). Only tabs that exist are listed (Phase 2 ruling: no dead tabs), so Folders
 * lands at the end in Phase 4b. Ordinals are pager page indices: Home opens on [TRACKS].
 */
enum class HomeTab(val title: String) {
    FAVOURITES("Favourites"),
    PLAYLISTS("Playlists"),
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
}
