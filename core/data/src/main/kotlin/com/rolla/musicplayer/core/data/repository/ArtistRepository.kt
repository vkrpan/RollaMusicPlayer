package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

/** Local, offline artist-detail data — everything is derived from the on-device Room library. */
interface ArtistRepository {

    /** The artist summary for [artistName], or `null` if no songs by that artist exist. */
    fun observeArtist(artistName: String): Flow<Artist?>

    /** Every artist in the library, case-insensitively by name, each with its representative artwork (Artists tab). */
    fun observeArtists(): Flow<List<Artist>>

    /** The albums [artistName] appears on, in the library order by title (spec §9.0), ties broken by album id. */
    fun observeArtistAlbums(artistName: String): Flow<List<Album>>

    /** [artistName]'s songs, grouped by album then disc/track order (nulls last, then title). */
    fun observeArtistSongs(artistName: String): Flow<List<Song>>
}
