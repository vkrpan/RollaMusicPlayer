@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.SongListItem

private val SurfaceHorizontalMargin = 8.dp
private val SurfaceVerticalMargin = 8.dp
private val ControlButtonSize = 48.dp
private val ControlIconSize = 24.dp
private val HeaderHorizontalPadding = 16.dp
private val HeaderVerticalPadding = 16.dp
private val ArtworkHorizontalPadding = 32.dp
private val ArtworkPlaceholderIconSize = 64.dp
private val EmptyStateHorizontalPadding = 32.dp

/**
 * Stateful entry point for the album detail screen, reached via `AlbumDetail(albumId: Long)` (that
 * route lives in `:app` and is owned by navigation-agent; it is intentionally never imported here).
 * Same Route/Screen split as every other screen in the codebase (see LibraryRoute in this module).
 * [onNavigateUp] / the `viewModel` default are a fixed public contract navigation-agent wires
 * `composable<AlbumDetail> { AlbumDetailRoute(onNavigateUp = { navController.navigateUp() }) }`
 * against, so this signature must not change shape.
 */
@Composable
fun AlbumDetailRoute(
    onNavigateUp: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AlbumDetailScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onPlayClick = remember(viewModel) { viewModel::onPlayClick },
        onShuffleClick = remember(viewModel) { viewModel::onShuffleClick },
        onSongClick = remember(viewModel) { viewModel::onSongClick },
    )
}

@Suppress("LongParameterList")
@Composable
fun AlbumDetailScreen(
    uiState: AlbumDetailUiState,
    onNavigateUp: () -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            AlbumDetailTopBar(
                title = uiState.album?.title.orEmpty(),
                onNavigateUp = onNavigateUp,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        AlbumDetailContent(
            uiState = uiState,
            onPlayClick = onPlayClick,
            onShuffleClick = onShuffleClick,
            onSongClick = onSongClick,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailTopBar(
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

@Composable
private fun AlbumDetailContent(
    uiState: AlbumDetailUiState,
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
        val album = uiState.album
        when {
            uiState.isLoading -> LoadingContent()
            // album == null && !isLoading is the documented not-found state on AlbumDetailUiState.
            album == null -> AlbumNotFoundContent()
            else -> AlbumDetailList(
                album = album,
                songs = uiState.songs,
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
private fun AlbumNotFoundContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Album not available",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = EmptyStateHorizontalPadding),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun AlbumDetailList(
    album: Album,
    songs: List<Song>,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item(key = "header") {
            AlbumDetailHeader(
                album = album,
                songCount = songs.size,
                onPlayClick = onPlayClick,
                onShuffleClick = onShuffleClick,
            )
        }
        if (songs.isEmpty()) {
            // album != null with an empty songs list is a transient window: observeAlbum and
            // observeAlbumSongs are independent Room queries that don't re-emit atomically. Show a
            // deliberate empty state rather than a header floating over a blank panel.
            item(key = "empty-songs") { AlbumSongsEmptyState() }
        } else {
            items(items = songs, key = { song -> song.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    trackNumber = song.trackNumber,
                )
            }
        }
    }
}

@Composable
private fun AlbumSongsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EmptyStateHorizontalPadding, vertical = HeaderVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No songs in this album",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun AlbumDetailHeader(
    album: Album,
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
        AlbumArtwork(
            artworkUri = album.artworkUri,
            contentDescription = "Album artwork for ${album.title}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ArtworkHorizontalPadding)
                .aspectRatio(1f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${album.artist} · ${songCountLabel(songCount)}",
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AlbumPlaybackControls(onShuffleClick = onShuffleClick, onPlayClick = onPlayClick)
    }
}

/** "1 song" / "N songs" -- kept as a one-liner so [AlbumDetailHeader] reads as a flat layout list. */
private fun songCountLabel(count: Int): String = if (count == 1) "1 song" else "$count songs"

@Composable
private fun AlbumArtwork(
    artworkUri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageRequest = remember(artworkUri) {
        ImageRequest.Builder(context).data(artworkUri).crossfade(true).build()
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri.isNotEmpty()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ArtworkPlaceholderIconSize),
            )
        }
    }
}

@Composable
private fun AlbumPlaybackControls(
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        ControlButton(
            icon = Icons.Default.Shuffle,
            contentDescription = "Shuffle album",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconTint = MaterialTheme.colorScheme.onSurface,
            onClick = onShuffleClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        ControlButton(
            icon = Icons.Default.PlayArrow,
            contentDescription = "Play album",
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

private fun previewAlbum(): Album = Album(
    id = 1L,
    title = "A Night at the Opera",
    artist = "Queen",
    songCount = 2,
    artworkUri = "",
)

private fun previewSongs(): List<Song> = listOf(
    Song(
        id = "1",
        title = "Death on Two Legs",
        artist = "Queen",
        album = "A Night at the Opera",
        albumId = 1L,
        durationMs = 223_000L,
        trackNumber = 1,
        year = 1975,
        contentUri = "content://media/1",
        artworkUri = "",
    ),
    Song(
        id = "2",
        title = "Lazing on a Sunday Afternoon",
        artist = "Queen",
        album = "A Night at the Opera",
        albumId = 1L,
        durationMs = 68_000L,
        trackNumber = 2,
        year = 1975,
        contentUri = "content://media/2",
        artworkUri = "",
    ),
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Album Detail - Light")
@Preview(name = "Album Detail - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewAlbumDetailScreen() {
    RollaMusicPlayerTheme {
        AlbumDetailScreen(
            uiState = AlbumDetailUiState(
                isLoading = false,
                album = previewAlbum(),
                songs = previewSongs(),
            ),
            onNavigateUp = {},
            onPlayClick = {},
            onShuffleClick = {},
            onSongClick = {},
        )
    }
}
