package com.rolla.musicplayer.core.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.rolla.musicplayer.core.database.entity.PlaylistEntity
import com.rolla.musicplayer.core.database.entity.PlaylistSongCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity

/**
 * A playlist together with its songs.
 *
 * Room's `@Relation` cannot express `ORDER BY` on the junction table, so [songs] arrives in an
 * unspecified order and [crossRefs] (loaded in the same call, carrying [PlaylistSongCrossRef.position])
 * is what callers sort by — see [orderedByPosition], used by `PlaylistDao.getPlaylistWithSongs`.
 */
data class PlaylistWithSongs(
    @Embedded
    val playlist: PlaylistEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlist_id",
            entityColumn = "song_id",
        ),
    )
    val songs: List<SongEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id",
    )
    val crossRefs: List<PlaylistSongCrossRef> = emptyList(),
)

/** Sorts [PlaylistWithSongs.songs] by [PlaylistSongCrossRef.position] found in [PlaylistWithSongs.crossRefs]. */
fun PlaylistWithSongs.orderedByPosition(): PlaylistWithSongs {
    val positionBySongId = crossRefs.associate { it.songId to it.position }
    return copy(songs = songs.sortedBy { positionBySongId[it.id] ?: Int.MAX_VALUE })
}
