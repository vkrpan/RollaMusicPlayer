@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.miniPlayerContainer
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.Song

private val PillHeight = 64.dp
private val PillHorizontalMargin = 8.dp
private val PillVerticalPadding = 4.dp
private val PillStartPadding = 8.dp
private val PillEndPadding = 4.dp
private val ArtworkSize = 48.dp
private val ArtworkToTextGap = 12.dp
private val IconSize = 24.dp

@Composable
fun MiniPlayerRoute(
    viewModel: MiniPlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    currentSong?.let { song ->
        MiniPlayer(
            song = song,
            isPlaying = isPlaying,
            onPrevious = viewModel::previous,
            onTogglePlayPause = viewModel::togglePlayPause,
            onNext = viewModel::next,
            onQueue = {},
            modifier = modifier,
        )
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = PillHorizontalMargin, vertical = PillVerticalPadding)
            .height(PillHeight)
            .background(MaterialTheme.colorScheme.miniPlayerContainer, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = {})
            .padding(start = PillStartPadding, end = PillEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPlayerArtwork(artworkUri = song.artworkUri, modifier = Modifier.size(ArtworkSize))
        Spacer(modifier = Modifier.width(ArtworkToTextGap))
        MiniPlayerInfo(title = song.title, artist = song.artist, modifier = Modifier.weight(1f))
        MiniPlayerControls(
            isPlaying = isPlaying,
            onPrevious = onPrevious,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onQueue = onQueue,
        )
    }
}

@Composable
private fun MiniPlayerInfo(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MiniPlayerArtwork(artworkUri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(artworkUri) {
        ImageRequest.Builder(context).data(artworkUri.ifEmpty { null }).crossfade(true).build()
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (artworkUri.isEmpty()) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

@Composable
private fun MiniPlayerControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MiniPlayerControlButton(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = "Previous track",
            onClick = onPrevious,
        )
        MiniPlayerControlButton(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onTogglePlayPause,
        )
        MiniPlayerControlButton(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "Next track",
            onClick = onNext,
        )
        MiniPlayerControlButton(
            imageVector = Icons.Default.QueueMusic,
            contentDescription = "Queue",
            onClick = onQueue,
        )
    }
}

@Composable
private fun MiniPlayerControlButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(IconSize),
        )
    }
}
