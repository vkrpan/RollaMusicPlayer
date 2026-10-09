@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.playlists

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.emptyState
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.ui.FeatureCard
import com.rolla.musicplayer.core.ui.PlaylistNameDialog
import com.rolla.musicplayer.core.ui.PlaylistRow
import com.rolla.musicplayer.core.ui.tracksCountLabel

/** Test tag on the horizontally scrolling row of smart-playlist feature cards. */
internal const val FEATURE_CARD_ROW_TEST_TAG = "playlists_feature_cards"

/**
 * The Home pager's Playlists tab (spec §8.2). Home owns the header (its + action raises [showCreateDialog], and
 * Search / Settings live there too); this tab owns the smart-playlist cards, the user playlists and the hoisted
 * "New playlist" dialog.
 */
// Each parameter is a distinct piece of hoisted state or a distinct event, plus the standard modifier / ViewModel.
@Suppress("LongParameterList")
@Composable
fun PlaylistsTab(
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val smartPlaylists by viewModel.smartPlaylists.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    PlaylistsTabContent(
        smartPlaylists = smartPlaylists,
        userPlaylists = userPlaylists,
        showCreateDialog = showCreateDialog,
        onDismissCreateDialog = onDismissCreateDialog,
        onCreatePlaylist = remember(viewModel) { viewModel::createPlaylist },
        onPlaylistClick = onPlaylistClick,
        onSmartPlaylistClick = onSmartPlaylistClick,
        modifier = modifier,
    )
}

/**
 * Stateless Playlists tab content; tests and previews host this. It fills the ContentPanel that Home provides. The
 * list scrolls under the floating mini-player and the navigation bar (spec §7.3).
 */
// The stateless half of PlaylistsTab: every input is a distinct piece of state or a distinct event.
@Suppress("LongParameterList")
@Composable
internal fun PlaylistsTabContent(
    smartPlaylists: List<SmartPlaylistSummary>,
    userPlaylists: List<Playlist>,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SortHeader(label = "Name")
        PlaylistsList(
            smartPlaylists = smartPlaylists,
            userPlaylists = userPlaylists,
            onPlaylistClick = onPlaylistClick,
            onSmartPlaylistClick = onSmartPlaylistClick,
        )
    }
    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                onCreatePlaylist(name)
                onDismissCreateDialog()
            },
            onDismiss = onDismissCreateDialog,
        )
    }
}

/** The feature-card row, then the user playlists or their empty hint, in one list. */
@Composable
private fun PlaylistsList(
    smartPlaylists: List<SmartPlaylistSummary>,
    userPlaylists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = homeListBottomPadding()),
    ) {
        item(key = "feature-cards") {
            FeatureCardRow(smartPlaylists = smartPlaylists, onSmartPlaylistClick = onSmartPlaylistClick)
        }
        if (userPlaylists.isEmpty()) {
            item(key = "empty-user-playlists") { EmptyUserPlaylistsState() }
        } else {
            items(items = userPlaylists, key = { it.id }) { playlist ->
                PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
            }
        }
    }
}

@Composable
private fun FeatureCardRow(
    smartPlaylists: List<SmartPlaylistSummary>,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().testTag(FEATURE_CARD_ROW_TEST_TAG),
        contentPadding = PaddingValues(horizontal = RollaDimens.featureCardRowStart),
        horizontalArrangement = Arrangement.spacedBy(RollaDimens.featureCardGap),
    ) {
        items(items = smartPlaylists, key = { it.kind }) { summary ->
            FeatureCard(
                label = summary.label,
                countLabel = tracksCountLabel(summary.count),
                artworkUris = summary.previewArtworkUris,
                onClick = { onSmartPlaylistClick(summary.kind) },
            )
        }
    }
}

@Composable
private fun EmptyUserPlaylistsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(RollaDimens.screenEdge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No playlists yet. Tap + to create one.",
            style = MaterialTheme.typography.emptyState,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun previewSmartPlaylists(): List<SmartPlaylistSummary> = listOf(
    SmartPlaylistSummary(
        kind = SmartPlaylistKind.RECENTLY_ADDED,
        label = smartPlaylistLabel(SmartPlaylistKind.RECENTLY_ADDED),
        count = 7,
        previewArtworkUris = emptyList(),
    ),
    SmartPlaylistSummary(
        kind = SmartPlaylistKind.MOST_PLAYED,
        label = smartPlaylistLabel(SmartPlaylistKind.MOST_PLAYED),
        count = 42,
        previewArtworkUris = listOf("content://preview/4"),
    ),
    SmartPlaylistSummary(
        kind = SmartPlaylistKind.RECENTLY_PLAYED,
        label = smartPlaylistLabel(SmartPlaylistKind.RECENTLY_PLAYED),
        count = 18,
        previewArtworkUris = listOf("content://preview/1", "content://preview/3"),
    ),
    SmartPlaylistSummary(
        kind = SmartPlaylistKind.FAVOURITES,
        label = smartPlaylistLabel(SmartPlaylistKind.FAVOURITES),
        count = 1,
        previewArtworkUris = emptyList(),
    ),
)

private fun previewUserPlaylists(): List<Playlist> = listOf(
    Playlist(id = 1L, name = "Workout Mix", songCount = 24, createdAt = 0L, updatedAt = 0L),
    Playlist(id = 2L, name = "Chill Evenings", songCount = 12, createdAt = 0L, updatedAt = 0L),
    Playlist(id = 3L, name = "New Playlist", songCount = 0, createdAt = 0L, updatedAt = 0L),
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Playlists Tab - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Playlists Tab - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewPlaylistsTabContent() {
    RollaMusicPlayerTheme {
        PlaylistsTabContent(
            smartPlaylists = previewSmartPlaylists(),
            userPlaylists = previewUserPlaylists(),
            showCreateDialog = false,
            onDismissCreateDialog = {},
            onCreatePlaylist = {},
            onPlaylistClick = {},
            onSmartPlaylistClick = {},
        )
    }
}
