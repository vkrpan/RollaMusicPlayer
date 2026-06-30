@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.permissions.MediaPermissionGate
import com.rolla.musicplayer.core.ui.SongListItem

private val SurfaceHorizontalMargin = 8.dp
private val SurfaceVerticalMargin = 8.dp
private val ControlButtonSize = 44.dp
private val ControlIconSize = 22.dp

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    MediaPermissionGate(onGranted = viewModel::onPermissionGranted) {
        LibraryScreen(
            songs = songs,
            scanState = scanState,
            onSongClick = viewModel::play,
        )
    }
}

@Composable
fun LibraryScreen(
    songs: List<Song>,
    scanState: ScanState,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { LibraryTopBar() },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        LibraryContent(
            songs = songs,
            scanState = scanState,
            onSongClick = onSongClick,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(modifier: Modifier = Modifier) {
    TopAppBar(
        title = {
            Text(
                text = "Music",
                style = MaterialTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        actions = {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier,
    )
}

@Composable
private fun LibraryContent(
    songs: List<Song>,
    scanState: ScanState,
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
        Column {
            SortControlHeader(songCount = songs.size)
            when {
                scanState is ScanState.Scanning -> ScanningContent()
                songs.isEmpty() -> EmptySongsContent()
                else -> SongListContent(songs = songs, onSongClick = onSongClick)
            }
        }
    }
}

@Composable
private fun SortControlHeader(songCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortLabel()
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$songCount songs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        PlaybackControls()
    }
}

@Composable
private fun SortLabel(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Name",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaybackControls(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        ControlButton(
            icon = Icons.Default.Shuffle,
            contentDescription = "Shuffle all songs",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconTint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        ControlButton(
            icon = Icons.Default.PlayArrow,
            contentDescription = "Play all songs",
            containerColor = MaterialTheme.colorScheme.primary,
            iconTint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    iconTint: Color,
) {
    FilledIconButton(
        onClick = {},
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

@Composable
private fun ScanningContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptySongsContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No music found on this device",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun SongListContent(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(items = songs, key = { song -> song.id }) { song ->
            SongListItem(song = song, onClick = { onSongClick(song) })
        }
    }
}
