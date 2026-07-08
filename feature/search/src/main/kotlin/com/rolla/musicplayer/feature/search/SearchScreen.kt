@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.search

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.SearchResults
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.AlbumRow
import com.rolla.musicplayer.core.ui.ArtistRow
import com.rolla.musicplayer.core.ui.SongListItem

private val SurfaceHorizontalMargin = 8.dp
private val SurfaceVerticalMargin = 8.dp
private val MinTouchTarget = 48.dp
private val EmptyStateHorizontalPadding = 32.dp
private val EmptyStateCaptionSpacing = 8.dp
private val SectionHeaderHorizontalPadding = 16.dp
private val SectionHeaderVerticalPadding = 12.dp

/**
 * Stateful entry point for the local search screen, mirroring the Route/Screen split used by
 * every other screen in the codebase (see TagEditorRoute in feature:tageditor, LibraryRoute in
 * feature:library).
 *
 * onAlbumClick and onArtistClick are hoisted no-ops today, since album/artist detail screens
 * have not shipped yet, left for navigation-agent to wire once they exist -- same convention as
 * LibraryRoute exposing onEditTagsClick while the tag editor route was still pending.
 */
@Composable
fun SearchRoute(
    onNavigateUp: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        query = query,
        uiState = uiState,
        onQueryChanged = remember(viewModel) { viewModel::onQueryChanged },
        onNavigateUp = onNavigateUp,
        onSongClick = remember(viewModel) { viewModel::play },
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        modifier = modifier,
    )
}

@Suppress("LongParameterList")
@Composable
fun SearchScreen(
    query: String,
    uiState: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The keyboard must open the instant this screen appears, there is nothing else to do on a
    // search screen until the user types, so focus is requested once, on first composition only.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        // Allows the whole Scaffold (top bar + results) to resize as the keyboard opens or
        // closes, so the IME can never cover the tail of the results list -- there is no other
        // scrollable container here for a plain per-content imePadding to attach to usefully.
        modifier = modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SearchTopBar(
                query = query,
                onQueryChanged = onQueryChanged,
                onNavigateUp = onNavigateUp,
                focusRequester = focusRequester,
            )
        },
    ) { innerPadding ->
        SearchContent(
            uiState = uiState,
            onSongClick = onSongClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onNavigateUp: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            SearchField(
                query = query,
                onQueryChanged = onQueryChanged,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
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

/**
 * The search text field itself: a pill/large-radius field on surfaceContainerHigh per
 * ui-style-guide.md section 6, with a trailing clear button that only appears once query is
 * non-empty. The IME action is Search, which just dismisses the keyboard; results already stream
 * reactively from SearchViewModel.uiState as query changes, so there is nothing left to trigger.
 */
@Suppress("LongMethod")
@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .focusRequester(focusRequester)
            .heightIn(min = MinTouchTarget),
        placeholder = { Text("Search your library") },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
    )
}

@Composable
private fun SearchContent(
    uiState: SearchUiState,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SurfaceHorizontalMargin, vertical = SurfaceVerticalMargin),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        when (uiState) {
            SearchUiState.Idle -> IdlePrompt()
            SearchUiState.Loading -> LoadingIndicator()
            SearchUiState.Empty -> NoResultsMessage()
            is SearchUiState.Results -> SearchResultsList(
                results = uiState.results,
                onSongClick = onSongClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
            )
        }
    }
}

/** Idle state: nothing typed yet -- a plain, single-line "type to search" prompt. */
@Composable
private fun IdlePrompt(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Search your library",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = EmptyStateHorizontalPadding),
        )
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Empty state: a query resolved but matched nothing. Deliberately a two-line, distinct message
 * (headline + caption) rather than reusing IdlePrompt's single line, so it reads as "we looked
 * and found nothing" instead of "nothing typed yet".
 */
@Composable
private fun NoResultsMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No results",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.heightIn(min = EmptyStateCaptionSpacing))
            Text(
                text = "Nothing in your library matched that search.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = EmptyStateHorizontalPadding),
            )
        }
    }
}

/**
 * Results state: one LazyColumn with up to three sections (Songs / Albums / Artists), each
 * present only when its list is non-empty. Keys are prefixed per section (song-/album-/artist-)
 * since Song.id is a String while Album.id and Artist.id are Longs from an entirely different id
 * space -- an unprefixed key could collide across types.
 */
@Suppress("LongParameterList")
@Composable
private fun SearchResultsList(
    results: SearchResults,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (results.songs.isNotEmpty()) {
            item(key = "header-songs") { SectionHeader(text = "Songs") }
            items(items = results.songs, key = { song -> "song-${song.id}" }) { song ->
                SongListItem(song = song, onClick = { onSongClick(song) })
            }
        }
        if (results.albums.isNotEmpty()) {
            item(key = "header-albums") { SectionHeader(text = "Albums") }
            items(items = results.albums, key = { album -> "album-${album.id}" }) { album ->
                AlbumRow(album = album, onClick = { onAlbumClick(album.id) })
            }
        }
        if (results.artists.isNotEmpty()) {
            item(key = "header-artists") { SectionHeader(text = "Artists") }
            items(items = results.artists, key = { artist -> "artist-${artist.id}" }) { artist ->
                ArtistRow(artist = artist, onClick = { onArtistClick(artist.id) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.sectionHeader,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SectionHeaderHorizontalPadding, vertical = SectionHeaderVerticalPadding),
    )
}

private fun previewSong(id: String, title: String, artist: String) = Song(
    id = id,
    title = title,
    artist = artist,
    album = "Sample Album",
    albumId = 1L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2001,
    contentUri = "content://media/external/audio/media/$id",
    artworkUri = "",
)

private fun previewResults() = SearchResults(
    songs = listOf(
        previewSong(id = "1", title = "Bohemian Rhapsody", artist = "Queen"),
        previewSong(id = "2", title = "Another One Bites the Dust", artist = "Queen"),
    ),
    albums = listOf(
        Album(id = 1L, title = "A Night at the Opera", artist = "Queen", songCount = 12, artworkUri = ""),
    ),
    artists = listOf(
        Artist(id = 1L, name = "Queen", albumCount = 15, songCount = 180),
    ),
)

@Suppress("UnusedPrivateMember")
@Preview(name = "Search - Results - Light")
@Preview(name = "Search - Results - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSearchScreenResults() {
    RollaMusicPlayerTheme {
        SearchScreen(
            query = "queen",
            uiState = SearchUiState.Results(previewResults()),
            onQueryChanged = {},
            onNavigateUp = {},
            onSongClick = {},
            onAlbumClick = {},
            onArtistClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Search - Idle - Light")
@Preview(name = "Search - Idle - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSearchScreenIdle() {
    RollaMusicPlayerTheme {
        SearchScreen(
            query = "",
            uiState = SearchUiState.Idle,
            onQueryChanged = {},
            onNavigateUp = {},
            onSongClick = {},
            onAlbumClick = {},
            onArtistClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Search - Empty - Light")
@Preview(name = "Search - Empty - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSearchScreenEmpty() {
    RollaMusicPlayerTheme {
        SearchScreen(
            query = "xyz123",
            uiState = SearchUiState.Empty,
            onQueryChanged = {},
            onNavigateUp = {},
            onSongClick = {},
            onAlbumClick = {},
            onArtistClick = {},
        )
    }
}
