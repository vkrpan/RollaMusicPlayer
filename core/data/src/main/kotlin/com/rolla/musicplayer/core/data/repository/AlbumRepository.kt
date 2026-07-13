package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

/** Local, offline album-detail data — everything is derived from the on-device Room library. */
interface AlbumRepository {

    /** The album summary for [albumId], or `null` if no songs with that album id exist. */
    fun observeAlbum(albumId: Long): Flow<Album?>

    /** The album's songs, disc/track order (track_number ascending, nulls last, then title). */
    fun observeAlbumSongs(albumId: Long): Flow<List<Song>>
}
