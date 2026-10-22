@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.search

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.component.OneUiSearchField
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.emptyState
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.SearchResults
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.AlbumRow
import com.rolla.musicplayer.core.ui.ArtistRow
import com.rolla.musicplayer.core.ui.SongListItem
import com.rolla.musicplayer.core.ui.SongOptionsHost

/**
 * Stateful entry point for the local search screen, mirroring the Route/Screen split used by
 * every other screen in the codebase (see TagEditorRoute in feature:tageditor, HomeRoute in
 * :app).
 *
 * onAlbumClick and onArtistClick are both wired by navigation-agent: onAlbumClick navigates by
 * Album.id (a real MediaStore-backed numeric id), while onArtistClick navigates by Artist.name --
 * artists have no durable id in the songs schema (see the ArtistDetail route in Routes.kt), so the
 * name is passed through instead.
 */
// The signature is Phase 6b Task 3's contract with :app: four navigation events, the ViewModel and the modifier.
@Suppress("LongParameterList")
@Composable
fun SearchRoute(
    onNavigateUp: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()

    SearchScreen(
        query = query,
        uiState = uiState,
        recentSearches = recentSearches,
        userPlaylists = userPlaylists,
        onQueryChanged = remember(viewModel) { viewModel::onQueryChanged },
        onNavigateUp = onNavigateUp,
        onSongClick = remember(viewModel) { viewModel::play },
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onRecentSearchClick = remember(viewModel) { viewModel::onRecentSearchClicked },
        onClearRecentSearches = remember(viewModel) { viewModel::onClearRecentSearches },
        onRemoveRecentSearch = remember(viewModel) { viewModel::onRemoveRecentSearch },
        onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist },
        onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong },
        onEditTagsClick = onEditTagsClick,
        modifier = modifier,
    )
}

/**
 * The stateless Search screen (spec §7.3, §7.4, §8.7; Phase 6b rulings 3–5). No Scaffold:
 * - a search bar (status-bar inset, [RollaDimens.headerHeight] tall): Navigate up, then [OneUiSearchField];
 * - [RollaDimens.panelTopGap], then a [ContentPanel] holding the state's content.
 *
 * Insets have one owner each (spec §7.4). The outer padding takes the keyboard minus the navigation bar, and every
 * list pads its bottom by [homeListBottomPadding] (the pill plus the navigation bar). With the keyboard closed the
 * outer padding is 0 and the last row clears the pill. With it open the app shell drops the pill inset to 0
 * (spec §7.3), so the two paddings add up to exactly the keyboard.
 *
 * A track row's ⋮ opens [SongOptionsHost] (Add to playlist, Edit tags), which closes itself when its track leaves the
 * results (6a ruling 10).
 */
// The stateless half of SearchRoute: every input is a distinct piece of state or a distinct event.
@Suppress("LongParameterList", "LongMethod")
@Composable
fun SearchScreen(
    query: String,
    uiState: SearchUiState,
    recentSearches: List<String>,
    userPlaylists: List<Playlist>,
    onQueryChanged: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The keyboard must open the instant this screen appears, there is nothing else to do on a
    // search screen until the user types, so focus is requested once, on first composition only.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    // IME Search only dismisses the keyboard: results already stream from the query as it changes.
    val keyboardController = LocalSoftwareKeyboardController.current
    var optionsSong by remember { mutableStateOf<Song?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The keyboard's owner (ruling 3): the lists below already pad by the navigation bar.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        SearchBar(
            query = query,
            onQueryChanged = onQueryChanged,
            onNavigateUp = onNavigateUp,
            onSearch = { keyboardController?.hide() },
            focusRequester = focusRequester,
        )
        Spacer(modifier = Modifier.height(RollaDimens.panelTopGap))
        ContentPanel {
            SearchPanelContent(
                uiState = uiState,
                recentSearches = recentSearches,
                onSongClick = onSongClick,
                onSongMoreClick = { optionsSong = it },
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onRecentSearchClick = onRecentSearchClick,
                onClearRecentSearches = onClearRecentSearches,
                onRemoveRecentSearch = onRemoveRecentSearch,
            )
        }
    }
    SongOptionsHost(
        song = optionsSong,
        userPlaylists = userPlaylists,
        onDismiss = { optionsSong = null },
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
        // The rows' list (6a ruling 10), tracked only in states that say what the query matches.
        currentSongs = sheetTrackedSongs(uiState),
    )
}

/**
 * The list the options sheet checks its track against (6a ruling 10):
 * - Results: the result tracks, so a track that leaves them closes the sheet.
 * - Empty: no tracks, so a tag edit that leaves the query matching nothing closes it too.
 * - Idle and Loading: null, which means "do not track". Returning after more than 5 s in the background restarts the
 *   WhileSubscribed search, which passes through Loading. The open sheet, or a half-typed playlist name, must survive
 *   that.
 */
private fun sheetTrackedSongs(uiState: SearchUiState): List<Song>? = when (uiState) {
    is SearchUiState.Results -> uiState.results.songs
    SearchUiState.Empty -> emptyList()
    SearchUiState.Idle, SearchUiState.Loading -> null
}

/** Navigate up at [RollaDimens.topBarEdge], then the search pill, ending [RollaDimens.screenEdge] from the edge. */
@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = RollaDimens.headerHeight)
            .padding(start = RollaDimens.topBarEdge, end = RollaDimens.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneUiIconButton(icon = RollaIcons.ChevronBack, contentDescription = "Navigate up", onClick = onNavigateUp)
        OneUiSearchField(
            query = query,
            onQueryChange = onQueryChanged,
            placeholder = "Search your library",
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            onSearch = onSearch,
        )
    }
}

/** The panel's body for each [SearchUiState]. Loading renders nothing: no spinner flash (6a §8.7). */
@Suppress("LongParameterList")
@Composable
private fun SearchPanelContent(
    uiState: SearchUiState,
    recentSearches: List<String>,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
) {
    when (uiState) {
        SearchUiState.Idle -> if (recentSearches.isEmpty()) {
            // Nothing typed yet and no history, e.g. the very first launch.
            PanelMessage(lines = listOf("Search your library"))
        } else {
            RecentSearchesList(
                recentSearches = recentSearches,
                onRecentSearchClick = onRecentSearchClick,
                onClearRecentSearches = onClearRecentSearches,
                onRemoveRecentSearch = onRemoveRecentSearch,
            )
        }
        SearchUiState.Loading -> Unit
        // Two lines, so it reads as "we looked and found nothing" rather than "nothing typed yet".
        SearchUiState.Empty -> PanelMessage(
            lines = listOf("No results", "Nothing in your library matched that search."),
        )
        is SearchUiState.Results -> SearchResultsList(
            results = uiState.results,
            onSongClick = onSongClick,
            onSongMoreClick = onSongMoreClick,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
        )
    }
}

/**
 * A centred message in the panel, one Text per line, in emptyState / onSurfaceVariant (ruling 4), the lines
 * [RollaDimens.settingsHeaderGap] apart.
 */
@Composable
private fun PanelMessage(lines: List<String>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RollaDimens.settingsHeaderGap),
            modifier = Modifier.padding(horizontal = RollaDimens.screenEdge),
        ) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.emptyState,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * LazyColumn key for an artist result. It is the artist's name, which is the `GROUP BY` key and
 * therefore unique. [Artist.id] is a synthetic FNV hash that is display-only: two names can hash to
 * the same id, and a duplicate key would crash the list.
 */
internal fun searchArtistKey(artist: Artist): String = "artist-${artist.name}"

/** Test handle for the results list. Not `song_list`, which only the Tracks tab may carry (baseline profile). */
internal const val SEARCH_RESULTS_LIST_TEST_TAG = "search_results_list"

// The results list's content types, so the LazyColumn reuses a composition only for a row of the same kind.
private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_SONG = "song"
private const val CONTENT_TYPE_ALBUM = "album"
private const val CONTENT_TYPE_ARTIST = "artist"

/**
 * Results state: one LazyColumn with up to three sections (Tracks / Albums / Artists), each
 * present only when its list is non-empty. Keys are prefixed per section (song-/album-/artist-)
 * so keys from different sections can never collide: songs and albums are keyed by id, artists by
 * name ([searchArtistKey], Phase 4a ruling 12). A track tap plays the results from it (Phase 3 gate decision 5);
 * its ⋮ opens the options sheet. The list pads its bottom by [homeListBottomPadding] (ruling 3).
 */
@Suppress("LongParameterList")
@Composable
private fun SearchResultsList(
    results: SearchResults,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(SEARCH_RESULTS_LIST_TEST_TAG),
        contentPadding = PaddingValues(bottom = homeListBottomPadding()),
    ) {
        if (results.songs.isNotEmpty()) {
            item(key = "header-songs", contentType = CONTENT_TYPE_HEADER) { SearchSectionHeader(text = "Tracks") }
            items(results.songs, key = { song -> "song-${song.id}" }, contentType = { CONTENT_TYPE_SONG }) { song ->
                SongListItem(song = song, onClick = { onSongClick(song) }, onMoreClick = { onSongMoreClick(song) })
            }
        }
        if (results.albums.isNotEmpty()) {
            item(key = "header-albums", contentType = CONTENT_TYPE_HEADER) { SearchSectionHeader(text = "Albums") }
            items(results.albums, key = { "album-${it.id}" }, contentType = { CONTENT_TYPE_ALBUM }) { album ->
                AlbumRow(album = album, onClick = { onAlbumClick(album.id) })
            }
        }
        if (results.artists.isNotEmpty()) {
            item(key = "header-artists", contentType = CONTENT_TYPE_HEADER) { SearchSectionHeader(text = "Artists") }
            items(results.artists, key = ::searchArtistKey, contentType = { CONTENT_TYPE_ARTIST }) { artist ->
                ArtistRow(artist = artist, onClick = { onArtistClick(artist.name) })
            }
        }
    }
}

/**
 * A section header (ruling 4): [text] in sectionHeader / onSurfaceVariant, starting at [RollaDimens.listThumbStart]
 * and [RollaDimens.singleLineRowHeight] tall. TalkBack hears it as a heading.
 */
@Composable
private fun SearchSectionHeader(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.singleLineRowHeight)
            .padding(start = RollaDimens.listThumbStart, end = RollaDimens.listTrailingEnd),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
    }
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
    SearchScreenPreview(query = "queen", uiState = SearchUiState.Results(previewResults()))
}

/** One preview host, so each preview names only the state it shows. */
@Composable
private fun SearchScreenPreview(query: String, uiState: SearchUiState, recentSearches: List<String> = emptyList()) {
    RollaMusicPlayerTheme {
        SearchScreen(
            query = query,
            uiState = uiState,
            recentSearches = recentSearches,
            userPlaylists = emptyList(),
            onQueryChanged = {},
            onNavigateUp = {},
            onSongClick = {},
            onAlbumClick = {},
            onArtistClick = {},
            onRecentSearchClick = {},
            onClearRecentSearches = {},
            onRemoveRecentSearch = {},
            onAddSongToPlaylist = { _, _ -> },
            onCreatePlaylistAndAddSong = { _, _ -> },
            onEditTagsClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Search - Idle - Light")
@Preview(name = "Search - Idle - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSearchScreenIdle() {
    SearchScreenPreview(query = "", uiState = SearchUiState.Idle)
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Search - Idle With Recents - Light")
@Preview(name = "Search - Idle With Recents - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSearchScreenIdleWithRecents() {
    SearchScreenPreview(
        query = "",
        uiState = SearchUiState.Idle,
        recentSearches = listOf("queen", "bohemian rhapsody", "jazz"),
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Search - Empty - Light")
@Preview(name = "Search - Empty - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewSearchScreenEmpty() {
    SearchScreenPreview(query = "xyz123", uiState = SearchUiState.Empty)
}
