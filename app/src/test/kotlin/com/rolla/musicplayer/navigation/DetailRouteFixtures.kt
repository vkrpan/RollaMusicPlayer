package com.rolla.musicplayer.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeAlbumRepository
import com.rolla.musicplayer.core.testing.FakeArtistRepository
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.feature.library.AlbumDetailViewModel
import com.rolla.musicplayer.feature.library.ArtistDetailViewModel
import com.rolla.musicplayer.feature.playlists.PlaylistDetailViewModel
import io.mockk.mockk

/**
 * Fixtures for hosting MainActivity's detail destinations (albumDetailDestination, artistDetailDestination and
 * playlistDetailDestinations) on the JVM. hiltViewModel() needs a Hilt activity, so each destination takes a ViewModel
 * provider: these build the real ViewModels from :core:testing fakes and a relaxed PlaybackController mock.
 */
internal object DetailRouteFixtures {
    const val ALBUM_ID = 4L
    const val ARTIST_NAME = "Queen"
    const val PLAYLIST_ID = 5L
    const val SMART_KIND = "FAVOURITES"

    // Song ids are offset from the row numbers, so an id taken from the wrong row or from an index cannot pass.
    private const val SONG_ID_BASE = 100

    fun songs(count: Int): List<Song> = (1..count).map { index ->
        Song(
            id = "${SONG_ID_BASE + index}",
            title = "Song $index",
            artist = ARTIST_NAME,
            album = "A Night at the Opera",
            albumId = ALBUM_ID,
            durationMs = 200_000L,
            trackNumber = index,
            year = 1975,
            contentUri = "",
            // Empty, so rows draw their placeholder and Coil never starts a load under Robolectric.
            artworkUri = "",
        )
    }

    fun albumViewModel(songs: List<Song>): AlbumDetailViewModel {
        val albums = FakeAlbumRepository().apply {
            emitAlbum(
                Album(
                    id = ALBUM_ID,
                    title = "A Night at the Opera",
                    artist = ARTIST_NAME,
                    songCount = songs.size,
                    artworkUri = "",
                ),
            )
            emitSongs(songs)
        }
        return AlbumDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("albumId" to ALBUM_ID)),
            albumRepository = albums,
            playbackController = playbackController(),
            playlistRepository = FakePlaylistRepository(),
        )
    }

    fun artistViewModel(songs: List<Song>): ArtistDetailViewModel {
        val artists = FakeArtistRepository().apply {
            emitArtist(Artist(id = 1L, name = ARTIST_NAME, albumCount = 0, songCount = songs.size))
            emitSongs(songs)
        }
        return ArtistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("artistName" to ARTIST_NAME)),
            artistRepository = artists,
            playbackController = playbackController(),
            playlistRepository = FakePlaylistRepository(),
        )
    }

    /** A user playlist, as `PlaylistDetail(playlistId)` seeds it. */
    fun playlistViewModel(songs: List<Song>): PlaylistDetailViewModel {
        val playlists = FakePlaylistRepository().apply {
            emitPlaylists(
                listOf(
                    Playlist(
                        id = PLAYLIST_ID,
                        name = "Road trip",
                        songCount = songs.size,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
            )
            emitPlaylistSongs(PLAYLIST_ID, songs)
        }
        return PlaylistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("playlistId" to PLAYLIST_ID)),
            playlistRepository = playlists,
            songRepository = FakeSongRepository(),
            playbackController = playbackController(),
        )
    }

    /** A smart playlist, as `SmartPlaylist(kind)` seeds it. */
    fun smartPlaylistViewModel(songs: List<Song>): PlaylistDetailViewModel = PlaylistDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("kind" to SMART_KIND)),
        playlistRepository = FakePlaylistRepository(),
        songRepository = FakeSongRepository().apply { emit(songs) },
        playbackController = playbackController(),
    )

    private fun playbackController(): PlaybackController = mockk(relaxed = true)
}

/**
 * The test graph: Home (the start), TagEditor (where Edit tags goes) and the [destinations] under test, under the pill
 * inset the shell's MiniPlayerHost would provide.
 */
@Composable
internal fun DetailRouteTestHost(
    navController: NavHostController,
    miniPlayerInset: Dp,
    destinations: NavGraphBuilder.() -> Unit,
) {
    RollaMusicPlayerTheme(darkTheme = true) {
        CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
            NavHost(navController = navController, startDestination = Home, modifier = Modifier.fillMaxSize()) {
                composable<Home> {}
                composable<TagEditor> {}
                destinations()
            }
        }
    }
}
