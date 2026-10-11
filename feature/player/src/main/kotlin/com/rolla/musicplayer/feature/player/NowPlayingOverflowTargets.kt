package com.rolla.musicplayer.feature.player

import androidx.compose.runtime.Immutable

/**
 * Where the Now Playing ⋮ items lead (spec §12). A null target disables its item. The song and album ids come from
 * the library row: PlaybackService rebuilds the current Song from MediaItem metadata with albumId = 0, and a song that
 * left the library has nothing to edit.
 */
@Immutable
data class NowPlayingOverflowTargets(
    val songId: Long? = null,
    val albumId: Long? = null,
    val artistName: String? = null,
)
