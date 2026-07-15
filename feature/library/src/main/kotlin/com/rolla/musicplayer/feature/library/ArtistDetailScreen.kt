@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.metadata
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.AlbumCard
import com.rolla.musicplayer.core.ui.SongListItem

private val SurfaceHorizontalMargin = 8.dp
private val SurfaceVerticalMargin = 8.dp
private val ControlButtonSize = 48.dp
private val ControlIconSize = 24.dp
private val HeaderHorizontalPadding = 16.dp
private val HeaderVerticalPadding = 16.dp
private val EmptyStateHorizontalPadding = 32.dp
private val AlbumsSectionLabelPadding = 16.dp
private val AlbumsRowGap = 12.dp
private val AlbumsRowVerticalPadding = 8.dp

/**
 * Stateful entry point for the artist detail screen, reached via `ArtistDetail(artistName: String)`
 * (that route lives in `:app` and is owned by navigation-agent; it is intentionally never imported
 * here). Same Route/Screen split as every other screen in the codebase (see AlbumDetailRoute in
 * this module). [onNavigateUp], [onAlbumClick] and the `viewModel` default are a fixed public
 * contract navigation-agent wires against, so this signature must not change shape.
 */
@Composable
fun ArtistDetailRoute(
    onNavigateUp: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistDetailScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onAlbumClick = onAlbumClick,
        onPlayClick = remember(viewModel) { viewModel::onPlayClick },
        onShuffleClick = remember(viewModel) { viewModel::onShuffleClick },
        onSongClick = remember(viewModel) { viewModel::onSongClick },
    )
}

@Suppress("LongParameterList")
@Composable
fun ArtistDetailScreen(
    uiState: ArtistDetailUiState,
    onNavigateUp: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            ArtistDetailTopBar(
                title = uiState.artist?.name.orEmpty(),
                onNavigateUp = onNavigateUp,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        ArtistDetailContent(
            uiState = uiState,
            onAlbumClick = onAlbumClick,
            onPlayClick = onPlayClick,
            onShuffleClick = onShuffleClick,
            onSongClick = onSongClick,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistDetailTopBar(
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
private fun ArtistDetailContent(
    uiState: ArtistDetailUiState,
    onAlbumClick: (Long) -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SurfaceHorizontalMargin, vertical = SurfaceVerticalMargin),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        val artist = uiState.artist
        when {
            uiState.isLoading -> LoadingContent()
            // artist == null && !isLoading is the documented not-found state on ArtistDetailUiState.
            artist == null -> ArtistNotFoundContent()
            else -> ArtistDetailList(
                artist = artist,
                albums = uiState.albums,
                songs = uiState.songs,
                onAlbumClick = onAlbumClick,
                onPlayClick = onPlayClick,
                onShuffleClick = onShuffleClick,
                onSongClick = onSongClick,
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ArtistNotFoundContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Artist not available",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = EmptyStateHorizontalPadding),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun ArtistDetailList(
    artist: Artist,
    albums: List<Album>,
    songs: List<Song>,
    onAlbumClick: (Long) -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item(key = "header") {
            ArtistDetailHeader(
                artist = artist,
                songCount = songs.size,
                onPlayClick = onPlayClick,
                onShuffleClick = onShuffleClick,
            )
        }
        if (albums.isNotEmpty()) {
            item(key = "albums") {
                ArtistAlbumsSection(albums = albums, onAlbumClick = onAlbumClick)
            }
        }
        if (songs.isEmpty()) {
            // artist != null with empty songs is a transient window: observeArtist/observeArtistSongs
            // are independent Room queries that don't re-emit atomically. Show a deliberate empty
            // state for the primary songs list rather than a blank panel below the header. (The
            // Albums section above is supplementary and correctly just hides when empty.)
            item(key = "empty-songs") { ArtistSongsEmptyState() }
        } else {
            items(items = songs, key = { song -> song.id }) { song ->
                SongListItem(song = song, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun ArtistSongsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EmptyStateHorizontalPadding, vertical = HeaderVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No songs for this artist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun ArtistDetailHeader(
    artist: Artist,
    songCount: Int,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HeaderHorizontalPadding, vertical = HeaderVerticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = artist.name,
            style = MaterialTheme.typography.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = albumAndSongCountLabel(artist.albumCount, songCount),
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ArtistPlaybackControls(onShuffleClick = onShuffleClick, onPlayClick = onPlayClick)
    }
}

/** "N albums · N songs" -- kept as a one-liner so [ArtistDetailHeader] reads as a flat layout list. */
private fun albumAndSongCountLabel(albumCount: Int, songCount: Int): String {
    val albums = if (albumCount == 1) "1 album" else "$albumCount albums"
    val songs = if (songCount == 1) "1 song" else "$songCount songs"
    return "$albums · $songs"
}

/**
 * Horizontal [AlbumCard] row under a small "Albums" section label, per `ui-style-guide.md` §6/§7.
 * Only rendered when the artist has at least one album (see [ArtistDetailList]) -- an artist
 * derived purely from songs with no album metadata would otherwise show an empty, pointless section.
 */
@Composable
private fun ArtistAlbumsSection(
    albums: List<Album>,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Albums",
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AlbumsSectionLabelPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(
                horizontal = AlbumsSectionLabelPadding,
                vertical = AlbumsRowVerticalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(AlbumsRowGap),
        ) {
            items(items = albums, key = { album -> album.id }) { album ->
                AlbumCard(album = album, onClick = { onAlbumClick(album.id) })
            }
        }
    }
}

@Composable
private fun ArtistPlaybackControls(
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        ControlButton(
            icon = Icons.Default.Shuffle,
            contentDescription = "Shuffle artist",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconTint = MaterialTheme.colorScheme.onSurface,
            onClick = onShuffleClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        ControlButton(
            icon = Icons.Default.PlayArrow,
            contentDescription = "Play artist",
            containerColor = MaterialTheme.colorScheme.primary,
            iconTint = MaterialTheme.colorScheme.onPrimary,
            onClick = onPlayClick,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(ControlButtonSize),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(ControlIconSize),
            tint = iconTint,
        )
    }
}

private fun previewArtist(): Artist = Artist(
    id = 1L,
    name = "Queen",
    albumCount = 2,
    songCount = 3,
)

private fun previewAlbums(): List<Album> = listOf(
    Album(
        id = 1L,
        title = "A Night at the Opera",
        artist = "Queen",
        songCount = 2,
        artworkUri = "",
    ),
    Album(
        id = 2L,
        title = "News of the World",
        artist = "Queen",
        songCount = 1,
        artworkUri = "",
    ),
)

private fun previewSong(id: String, title: String, album: String, albumId: Long, trackNumber: Int): Song =
    Song(
        id = id,
        title = title,
        artist = "Queen",
        album = album,
        albumId = albumId,
        durationMs = 200_000L,
        trackNumber = trackNumber,
        year = 1975,
        contentUri = "content://media/$id",
        artworkUri = "",
    )

private fun previewSongs(): List<Song> = listOf(
    previewSong("1", "Death on Two Legs", album = "A Night at the Opera", albumId = 1L, trackNumber = 1),
    previewSong("2", "Lazing on a Sunday Afternoon", album = "A Night at the Opera", albumId = 1L, trackNumber = 2),
    previewSong("3", "We Will Rock You", album = "News of the World", albumId = 2L, trackNumber = 1),
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Artist Detail - Light")
@Preview(name = "Artist Detail - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewArtistDetailScreen() {
    RollaMusicPlayerTheme {
        ArtistDetailScreen(
            uiState = ArtistDetailUiState(
                isLoading = false,
                artist = previewArtist(),
                albums = previewAlbums(),
                songs = previewSongs(),
            ),
            onNavigateUp = {},
            onAlbumClick = {},
            onPlayClick = {},
            onShuffleClick = {},
            onSongClick = {},
        )
    }
}
