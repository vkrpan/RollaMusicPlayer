package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

/** Local, offline artist-detail data — everything is derived from the on-device Room library. */
interface ArtistRepository {

    /** The artist summary for [artistName], or `null` if no songs by that artist exist. */
    fun observeArtist(artistName: String): Flow<Artist?>

    /** The albums [artistName] appears on, alphabetical by title. */
    fun observeArtistAlbums(artistName: String): Flow<List<Album>>

    /** [artistName]'s songs, grouped by album then disc/track order (nulls last, then title). */
    fun observeArtistSongs(artistName: String): Flow<List<Song>>
}
