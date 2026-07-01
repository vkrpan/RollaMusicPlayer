package com.rolla.musicplayer.core.database.relation

import androidx.room.Embedded
import com.rolla.musicplayer.core.database.entity.PlaylistEntity

/** Projection of a playlist plus its song count, derived via a LEFT JOIN + COUNT over playlist_songs. */
data class PlaylistWithCount(
    @Embedded
    val playlist: PlaylistEntity,
    val songCount: Int,
)
