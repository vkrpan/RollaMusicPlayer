@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.CircleIconButton
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.AlbumCard
import com.rolla.musicplayer.core.ui.DetailArtworkHeader
import com.rolla.musicplayer.core.ui.SongListItem
import com.rolla.musicplayer.core.ui.SongOptionsHost
import com.rolla.musicplayer.core.ui.albumsCountLabel
import com.rolla.musicplayer.core.ui.tracksCountLabel

/**
 * Test handle for ArtistDetail's track list. Not `song_list`, which only the Tracks tab may carry (baseline profile).
 */
internal const val ARTIST_SONG_LIST_TEST_TAG = "artist_song_list"

/**
 * ArtistDetail (spec §8.7), reached via `ArtistDetail(artistName: String)`. That route lives in `:app` and is owned by
 * navigation-agent. Back is [onNavigateUp]; an album card opens its album through [onAlbumClick]; "Edit tags" in the
 * options sheet leaves through [onEditTagsClick].
 */
@Composable
fun ArtistDetailRoute(
    onNavigateUp: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    ArtistDetailContent(
        uiState = uiState,
        userPlaylists = userPlaylists,
        onNavigateUp = onNavigateUp,
        onAlbumClick = onAlbumClick,
        onSongClick = remember(viewModel) { viewModel::onSongClick },
        onShuffleClick = remember(viewModel) { viewModel::onShuffleClick },
        onPlayClick = remember(viewModel) { viewModel::onPlayClick },
        onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist },
        onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong },
        onEditTagsClick = onEditTagsClick,
    )
}

/**
 * Stateless ArtistDetail (spec §8.7); tests and previews host this. It has AlbumDetailContent's shape:
 * - The bar owns the status-bar inset. The panel runs to the bottom edge, and the list pads by the pill and the
 *   navigation bar, so it scrolls under the pill (ruling 5: no outer pill padding).
 * - The circular hero, the albums strip and the sort row are the list's first items, so they scroll with the tracks.
 *   The strip is left out for an artist with no album rows.
 * - Nothing renders while loading; a null artist after loading is the not-found state (the artist's last track was
 *   removed).
 * - An artist with no tracks is the transient window between the artist and track queries, which Room does not
 *   re-emit atomically.
 * - The options sheet's [SongOptionsHost] gets the same list the rows render, so it closes when its track leaves
 *   (ruling 10). The open track is plain `remember` state: a saved one could outlive the list it came from.
 */
// The stateless half of ArtistDetailRoute: every input is a distinct piece of state or a distinct event.
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun ArtistDetailContent(
    uiState: ArtistDetailUiState,
    userPlaylists: List<Playlist>,
    onNavigateUp: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    val artist = uiState.artist
    val heroSubtitle = "${albumsCountLabel(uiState.albums.size)}, ${tracksCountLabel(uiState.songs.size)}"
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OneUiDetailTopBar(title = artist?.name.orEmpty(), onNavigateUp = onNavigateUp)
        ContentPanel {
            when {
                uiState.isLoading -> Unit
                artist == null -> TabMessage("Artist not available")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(ARTIST_SONG_LIST_TEST_TAG),
                    contentPadding = PaddingValues(bottom = homeListBottomPadding()),
                ) {
                    item(key = "hero", contentType = "hero") {
                        DetailArtworkHeader(
                            artworkUri = artist.artworkUri,
                            subtitle = heroSubtitle,
                            circular = true,
                            placeholderGlyph = RollaIcons.Person,
                        )
                    }
                    if (uiState.albums.isNotEmpty()) {
                        item(key = "albums", contentType = "albums") {
                            ArtistAlbumsStrip(albums = uiState.albums, onAlbumClick = onAlbumClick)
                        }
                    }
                    item(key = "sort", contentType = "sort") {
                        val enabled = uiState.songs.isNotEmpty()
                        SortHeader(label = "Album") {
                            CircleIconButton(
                                icon = RollaIcons.Shuffle,
                                contentDescription = "Shuffle artist",
                                onClick = onShuffleClick,
                                enabled = enabled,
                            )
                            CircleIconButton(
                                icon = RollaIcons.Play,
                                contentDescription = "Play artist",
                                onClick = onPlayClick,
                                enabled = enabled,
                            )
                        }
                    }
                    if (uiState.songs.isEmpty()) {
                        item(key = "empty", contentType = "empty") { TabMessage("No tracks by this artist") }
                    } else {
                        items(items = uiState.songs, key = { song -> song.id }, contentType = { "song" }) { song ->
                            SongListItem(
                                song = song,
                                onClick = { onSongClick(song) },
                                onMoreClick = { optionsSong = song },
                            )
                        }
                    }
                }
            }
        }
    }
    SongOptionsHost(
        song = optionsSong,
        userPlaylists = userPlaylists,
        onDismiss = { optionsSong = null },
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
        currentSongs = uiState.songs,
    )
}

/**
 * The artist's albums under an "Albums" section heading: a horizontal row of [AlbumCard]s in library order (ruling 8),
 * aligned with the list thumbnails. Each card's subtitle is its track count, because the artist's name under each of
 * their own albums says nothing (Phase 4a ruling 8), and [Album] carries no year.
 */
@Composable
private fun ArtistAlbumsStrip(albums: List<Album>, onAlbumClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Albums",
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = RollaDimens.listThumbStart, top = RollaDimens.panelTopGap)
                .semantics { heading() },
        )
        LazyRow(
            contentPadding = PaddingValues(
                horizontal = RollaDimens.featureCardRowStart,
                vertical = RollaDimens.panelTopGap,
            ),
            horizontalArrangement = Arrangement.spacedBy(RollaDimens.featureCardGap),
        ) {
            items(items = albums, key = { album -> album.id }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album.id) },
                    modifier = Modifier.width(RollaDimens.featureCardSize),
                    subtitle = tracksCountLabel(album.songCount),
                )
            }
        }
    }
}

private val PreviewArtist = Artist(id = 1L, name = "Queen", albumCount = 2, songCount = 3)

private val PreviewArtistAlbums = listOf(
    Album(id = 1L, title = "A Night at the Opera", artist = "Queen", songCount = 2, artworkUri = ""),
    Album(id = 2L, title = "News of the World", artist = "Queen", songCount = 1, artworkUri = ""),
)

private val PreviewArtistSongs = listOf(
    previewSong("1", "Death on Two Legs", album = "A Night at the Opera", albumId = 1L),
    previewSong("2", "Lazing on a Sunday Afternoon", album = "A Night at the Opera", albumId = 1L),
    previewSong("3", "We Will Rock You", album = "News of the World", albumId = 2L),
)

private fun previewSong(id: String, title: String, album: String, albumId: Long): Song = Song(
    id = id,
    title = title,
    artist = "Queen",
    album = album,
    albumId = albumId,
    durationMs = 200_000L,
    trackNumber = null,
    year = 1975,
    contentUri = "",
    artworkUri = "",
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Artist Detail - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Artist Detail - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewArtistDetailContent() {
    RollaMusicPlayerTheme {
        ArtistDetailContent(
            uiState = ArtistDetailUiState(
                isLoading = false,
                artist = PreviewArtist,
                albums = PreviewArtistAlbums,
                songs = PreviewArtistSongs,
            ),
            userPlaylists = emptyList(),
            onNavigateUp = {},
            onAlbumClick = {},
            onSongClick = {},
            onShuffleClick = {},
            onPlayClick = {},
            onAddSongToPlaylist = { _, _ -> },
            onCreatePlaylistAndAddSong = { _, _ -> },
            onEditTagsClick = {},
        )
    }
}
