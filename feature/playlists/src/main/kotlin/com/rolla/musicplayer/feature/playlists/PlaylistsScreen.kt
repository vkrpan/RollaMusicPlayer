@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.playlists

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.metadata
import com.rolla.musicplayer.core.designsystem.theme.screenTitle
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.ui.PlaylistNameDialog
import com.rolla.musicplayer.core.ui.PlaylistRow

private val SurfaceHorizontalMargin = 8.dp
private val SurfaceVerticalMargin = 8.dp
private val ContentVerticalPadding = 12.dp
private val FeatureCardRowPadding = 16.dp
private val FeatureCardGap = 12.dp
private val FeatureCardSize = 150.dp
private val FeatureCardTextGap = 8.dp
private val FeatureCardIconSize = 40.dp
private val CollageIconSize = 20.dp
private const val COLLAGE_QUADRANT_COUNT = 4
private const val COLLAGE_QUADRANTS_PER_ROW = 2

@Composable
fun PlaylistsRoute(
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    // Default no-op: wired by navigation-agent to the Search route -- same rationale as
    // LibraryRoute's onSearchClick.
    onSearchClick: () -> Unit = {},
    // Default no-op: wired by navigation-agent to the Settings route -- same rationale as
    // LibraryRoute's onSettingsClick.
    onSettingsClick: () -> Unit = {},
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val smartPlaylists by viewModel.smartPlaylists.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    PlaylistsScreen(
        smartPlaylists = smartPlaylists,
        userPlaylists = userPlaylists,
        onPlaylistClick = onPlaylistClick,
        onSmartPlaylistClick = onSmartPlaylistClick,
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        onCreatePlaylist = remember(viewModel) { viewModel::createPlaylist },
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
fun PlaylistsScreen(
    smartPlaylists: List<SmartPlaylistSummary>,
    userPlaylists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    // Default no-op: wired by navigation-agent to the Search route.
    onSearchClick: () -> Unit = {},
    // Default no-op: wired by navigation-agent to the Settings route.
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PlaylistsTopBar(
                onCreateClick = { showCreateDialog = true },
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        PlaylistsContent(
            smartPlaylists = smartPlaylists,
            userPlaylists = userPlaylists,
            onPlaylistClick = onPlaylistClick,
            onSmartPlaylistClick = onSmartPlaylistClick,
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistsTopBar(
    onCreateClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.screenTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        actions = {
            IconButton(onClick = onCreateClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create playlist",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
            }
            PlaylistsOverflowMenu(onSettingsClick = onSettingsClick)
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = modifier,
    )
}

/**
 * TopAppBar overflow (kebab) menu: today its only item opens Settings -- same shape as Library's
 * `LibraryOverflowMenu`. Kept as its own composable so the `expanded` state lives next to the
 * button/menu pair it controls.
 */
@Composable
private fun PlaylistsOverflowMenu(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    expanded = false
                    onSettingsClick()
                },
            )
        }
    }
}

@Composable
private fun PlaylistsContent(
    smartPlaylists: List<SmartPlaylistSummary>,
    userPlaylists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SurfaceHorizontalMargin, vertical = SurfaceVerticalMargin),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = ContentVerticalPadding)) {
            item {
                SmartPlaylistRow(smartPlaylists = smartPlaylists, onSmartPlaylistClick = onSmartPlaylistClick)
            }
            items(items = userPlaylists, key = { it.id }) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
            }
        }
    }
}

@Composable
private fun SmartPlaylistRow(
    smartPlaylists: List<SmartPlaylistSummary>,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = FeatureCardRowPadding, vertical = ContentVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(FeatureCardGap),
    ) {
        items(items = smartPlaylists, key = { it.kind }) { summary ->
            SmartPlaylistCard(summary = summary, onClick = { onSmartPlaylistClick(summary.kind) })
        }
    }
}

@Composable
private fun SmartPlaylistCard(
    summary: SmartPlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(FeatureCardSize).clickable(onClick = onClick)) {
        SmartPlaylistArtwork(previewArtworkUris = summary.previewArtworkUris)
        Spacer(modifier = Modifier.height(FeatureCardTextGap))
        Text(
            text = summary.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${summary.count} songs",
            style = MaterialTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmartPlaylistArtwork(
    previewArtworkUris: List<String>,
    modifier: Modifier = Modifier,
) {
    val hasArtwork = previewArtworkUris.any { it.isNotEmpty() }
    Box(
        modifier = modifier
            .size(FeatureCardSize)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (hasArtwork) {
            SmartPlaylistCollage(previewArtworkUris = previewArtworkUris)
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(FeatureCardIconSize),
            )
        }
    }
}

@Composable
private fun SmartPlaylistCollage(
    previewArtworkUris: List<String>,
    modifier: Modifier = Modifier,
) {
    val quadrants = remember(previewArtworkUris) {
        List(COLLAGE_QUADRANT_COUNT) { index -> previewArtworkUris.getOrElse(index) { "" } }
    }
    Column(modifier = modifier) {
        quadrants.chunked(COLLAGE_QUADRANTS_PER_ROW).forEach { rowUris ->
            Row(modifier = Modifier.weight(1f)) {
                rowUris.forEach { artworkUri ->
                    CollageQuadrant(artworkUri = artworkUri, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CollageQuadrant(
    artworkUri: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (artworkUri.isNotEmpty()) {
            val context = LocalContext.current
            val request = remember(artworkUri) {
                ImageRequest.Builder(context).data(artworkUri).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CollageIconSize),
            )
        }
    }
}

private fun previewSmartPlaylists(): List<SmartPlaylistSummary> = listOf(
    SmartPlaylistSummary(
        kind = SmartPlaylistKind.RECENTLY_PLAYED,
        label = "Recently played",
        count = 18,
        previewArtworkUris = listOf("content://preview/1", "", "content://preview/3", ""),
    ),
    SmartPlaylistSummary(
        kind = SmartPlaylistKind.FAVOURITES,
        label = "Favourites",
        count = 0,
        previewArtworkUris = emptyList(),
    ),
    SmartPlaylistSummary(
        kind = SmartPlaylistKind.MOST_PLAYED,
        label = "Most played",
        count = 42,
        previewArtworkUris = listOf("content://preview/4"),
    ),
    SmartPlaylistSummary(
        kind = SmartPlaylistKind.RECENTLY_ADDED,
        label = "Recently added",
        count = 7,
        previewArtworkUris = emptyList(),
    ),
)

private fun previewUserPlaylists(): List<Playlist> = listOf(
    Playlist(id = 1L, name = "Workout Mix", songCount = 24, createdAt = 0L, updatedAt = 0L),
    Playlist(id = 2L, name = "Chill Evenings", songCount = 12, createdAt = 0L, updatedAt = 0L),
    Playlist(id = 3L, name = "New Playlist", songCount = 0, createdAt = 0L, updatedAt = 0L),
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Playlists - Light")
@Preview(name = "Playlists - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewPlaylistsScreen() {
    RollaMusicPlayerTheme {
        PlaylistsScreen(
            smartPlaylists = previewSmartPlaylists(),
            userPlaylists = previewUserPlaylists(),
            onPlaylistClick = {},
            onSmartPlaylistClick = {},
            onCreatePlaylist = {},
        )
    }
}
