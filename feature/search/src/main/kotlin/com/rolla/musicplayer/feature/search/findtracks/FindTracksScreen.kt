@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.search.findtracks

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.CircleIconButton
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.OneUiPillButton
import com.rolla.musicplayer.core.designsystem.component.OneUiTextField
import com.rolla.musicplayer.core.designsystem.component.RemovableChip
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.TagChipStyle
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.emptyState
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import com.rolla.musicplayer.core.ui.SongListItem
import com.rolla.musicplayer.core.ui.SongOptionsHost
import com.rolla.musicplayer.core.ui.customtags.CustomTagPickerSheet
import com.rolla.musicplayer.core.ui.customtags.CustomTagRows
import com.rolla.musicplayer.core.ui.tracksCountLabel

/**
 * Stateful entry point for Find tracks (custom tags phase 4): collects [FindTracksViewModel] and hands its state and
 * handlers to [FindTracksScreen]. [onEditTagsClick] is a result's ⋮ "Edit tags", which the app routes to the tag
 * editor.
 */
@Composable
fun FindTracksRoute(
    onNavigateUp: () -> Unit,
    onEditTagsClick: (Song) -> Unit,
    viewModel: FindTracksViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    FindTracksScreen(
        uiState = uiState,
        userPlaylists = userPlaylists,
        onNavigateUp = onNavigateUp,
        onFieldChange = remember(viewModel) { { field, value -> viewModel.onFieldChange(field, value) } },
        onRemoveTextFilter = remember(viewModel) { viewModel::onRemoveTextFilter },
        onRemoveCustomTag = remember(viewModel) { viewModel::onRemoveCustomTag },
        onToggleFilters = remember(viewModel) { viewModel::onToggleFilters },
        onOpenPicker = remember(viewModel) { viewModel::onOpenPicker },
        onPickerPageChange = remember(viewModel) { viewModel::onPickerPageChange },
        onPickerQueryChange = remember(viewModel) { viewModel::onPickerQueryChange },
        onPickerToggle = remember(viewModel) { viewModel::onPickerToggle },
        onPickerDone = remember(viewModel) { viewModel::onPickerDone },
        onSongClick = remember(viewModel) { viewModel::play },
        onShuffleClick = remember(viewModel) { viewModel::shuffleAll },
        onPlayClick = remember(viewModel) { viewModel::playAll },
        onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist },
        onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong },
        onEditTagsClick = onEditTagsClick,
        modifier = modifier,
    )
}

/** One text field's change, routed to its own handler (the screen reports every field through one event). */
private fun FindTracksViewModel.onFieldChange(field: FindTracksField, value: String) = when (field) {
    FindTracksField.TITLE -> onTitleChange(value)
    FindTracksField.ARTIST -> onArtistChange(value)
    FindTracksField.ALBUM -> onAlbumChange(value)
    FindTracksField.GENRE -> onGenreChange(value)
    FindTracksField.YEAR -> onYearChange(value)
}

/**
 * The stateless Find tracks screen (custom tags spec §5.5, mockup B): [OneUiDetailTopBar] "Find tracks", then a
 * [ContentPanel] holding one LazyColumn:
 * - the active-filter strip: a [RemovableChip] per active filter ("Title: love", …, "Year: 1999", then each tag
 *   (Tonal) and subtag (Outline) by name) in a FlowRow, so a large font wraps it instead of clipping it. A tag the
 *   lists do not name shows only while the lists are unavailable, as "Unavailable tag" / "Unavailable subtag"
 *   (controller Ruling 6); before the lists load it is skipped, so no nameless chip flashes;
 * - "Edit filters" / "Hide filters", a tonal pill that folds the fields card (a spring, or a snap under reduced
 *   motion): Title, Artist, Album, Genre, Year (number keyboard) and the Tags and Subtags rows, which open
 *   [CustomTagPickerSheet] without its "New" row and "Manage tags" link (a new tag carries no track, so it cannot
 *   match);
 * - the results: a [SortHeader] labelled with the count, whose Shuffle and Play cover the whole result, then the
 *   [SongListItem] rows (tap plays the result from that row, ⋮ opens [SongOptionsHost]), or "No tracks match these
 *   filters". Before the first answer ([FindTracksUiState.results] null) the results part is empty.
 *
 * Insets: the column takes the keyboard minus the navigation bar, and the list pads its bottom by
 * [homeListBottomPadding] (the pill plus the navigation bar), as on Search: with the keyboard open the shell drops the
 * pill inset, so the two add up to exactly the keyboard.
 */
// The stateless half of FindTracksRoute: every input is a distinct piece of state or a distinct event. LongMethod: the
// column, the picker and the options host are flat argument forwarding.
@Suppress("LongParameterList", "LongMethod")
@Composable
fun FindTracksScreen(
    uiState: FindTracksUiState,
    userPlaylists: List<Playlist>,
    onNavigateUp: () -> Unit,
    onFieldChange: (FindTracksField, String) -> Unit,
    onRemoveTextFilter: (FindTracksField) -> Unit,
    onRemoveCustomTag: (CustomTagKind, Long) -> Unit,
    onToggleFilters: () -> Unit,
    onOpenPicker: (CustomTagKind) -> Unit,
    onPickerPageChange: (CustomTagKind) -> Unit,
    onPickerQueryChange: (CustomTagKind, String) -> Unit,
    onPickerToggle: (CustomTagKind, Long) -> Unit,
    onPickerDone: () -> Unit,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The keyboard's owner: the list below already pads by the navigation bar.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        OneUiDetailTopBar(title = "Find tracks", onNavigateUp = onNavigateUp)
        ContentPanel {
            FindTracksList(
                uiState = uiState,
                onFieldChange = onFieldChange,
                onRemoveTextFilter = onRemoveTextFilter,
                onRemoveCustomTag = onRemoveCustomTag,
                onToggleFilters = onToggleFilters,
                onOpenPicker = onOpenPicker,
                onSongClick = onSongClick,
                onSongMoreClick = { optionsSong = it },
                onShuffleClick = onShuffleClick,
                onPlayClick = onPlayClick,
            )
        }
    }
    uiState.picker?.let { picker ->
        CustomTagPickerSheet(
            state = picker,
            onPageChange = onPickerPageChange,
            onQueryChange = onPickerQueryChange,
            onToggle = onPickerToggle,
            onCreateClick = null,
            onDone = onPickerDone,
            onManageClick = null,
        )
    }
    SongOptionsHost(
        song = optionsSong,
        userPlaylists = userPlaylists,
        onDismiss = { optionsSong = null },
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
        // The rows' list, so the sheet closes when a tag edit or rescan takes its track out of the result (6a ruling
        // 10). Null before the first answer: nothing says what matches yet.
        currentSongs = uiState.results,
    )
}

/** The panel's one list: the strip, the filters toggle and card, then the results. */
// One flat LazyColumn; each section is a helper, the parameters are the events they forward.
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun FindTracksList(
    uiState: FindTracksUiState,
    onFieldChange: (FindTracksField, String) -> Unit,
    onRemoveTextFilter: (FindTracksField) -> Unit,
    onRemoveCustomTag: (CustomTagKind, Long) -> Unit,
    onToggleFilters: () -> Unit,
    onOpenPicker: (CustomTagKind) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
) {
    val chips = remember(uiState) { activeFilterChips(uiState) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(FIND_TRACKS_LIST_TAG),
        contentPadding = PaddingValues(bottom = homeListBottomPadding()),
    ) {
        // The strip and the toggle share one always-present item (review I1). LazyColumn anchors its scroll position on
        // the first visible item's key, so a strip item of its own, inserted above the toggle by the first keystroke,
        // would land out of view (an empty zero-height item does not count as visible, so it does not help). As part of
        // the first item, the strip grows in place and pushes the rest down.
        item(key = "head", contentType = "head") {
            Column {
                if (chips.isNotEmpty()) {
                    FilterStrip(
                        chips = chips,
                        onRemove = { chip ->
                            when (val target = chip.target) {
                                is StripTarget.Text -> onRemoveTextFilter(target.field)
                                is StripTarget.Custom -> onRemoveCustomTag(target.kind, target.id)
                            }
                        },
                    )
                }
                OneUiPillButton(
                    text = if (uiState.filtersExpanded) "Hide filters" else "Edit filters",
                    onClick = onToggleFilters,
                    tonal = true,
                    modifier = Modifier.padding(
                        horizontal = RollaDimens.listThumbStart,
                        vertical = RollaDimens.editorFieldGap,
                    ),
                )
            }
        }
        item(key = "fields", contentType = "fields") {
            FiltersCard(
                visible = uiState.filtersExpanded,
                uiState = uiState,
                chips = chips,
                onFieldChange = onFieldChange,
                onOpenPicker = onOpenPicker,
            )
        }
        uiState.results?.let { results ->
            resultsSection(
                results = results,
                onSongClick = onSongClick,
                onSongMoreClick = onSongMoreClick,
                onShuffleClick = onShuffleClick,
                onPlayClick = onPlayClick,
            )
        }
    }
}

/** The results header with its count, then the rows or the no-match message. */
// LongMethod: three flat lazy sections (header, message, rows), each one call with named arguments.
@Suppress("LongMethod")
private fun LazyListScope.resultsSection(
    results: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
) {
    item(key = "header", contentType = "header") {
        val enabled = results.isNotEmpty()
        SortHeader(label = tracksCountLabel(results.size)) {
            CircleIconButton(
                icon = RollaIcons.Shuffle,
                contentDescription = "Shuffle these tracks",
                onClick = onShuffleClick,
                enabled = enabled,
            )
            CircleIconButton(
                icon = RollaIcons.Play,
                contentDescription = "Play these tracks",
                onClick = onPlayClick,
                enabled = enabled,
            )
        }
    }
    if (results.isEmpty()) {
        item(key = "empty", contentType = "empty") {
            Text(
                text = NO_MATCH_MESSAGE,
                style = MaterialTheme.typography.emptyState,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(RollaDimens.screenEdge),
            )
        }
    } else {
        itemsIndexed(
            items = results,
            key = { _, song -> "song-${song.id}" },
            contentType = { _, _ -> "song" },
        ) { index, song ->
            SongListItem(
                song = song,
                onClick = { onSongClick(song) },
                onMoreClick = { onSongMoreClick(song) },
                // No hairline under the list's last row (Phase 7 calibration).
                showDivider = index < results.lastIndex,
            )
        }
    }
}

/** The active filters as removable chips, wrapping onto more lines rather than clipping (a FlowRow). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterStrip(chips: List<StripChip>, onRemove: (StripChip) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = RollaDimens.listThumbStart,
                end = RollaDimens.listThumbStart,
                top = RollaDimens.editorFieldGap,
            )
            .testTag(FIND_TRACKS_STRIP_TAG),
        horizontalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
        verticalArrangement = Arrangement.spacedBy(RollaDimens.chipRowLayoutGap),
    ) {
        chips.forEach { chip ->
            RemovableChip(
                label = chip.label,
                removeLabel = "Remove filter ${chip.label}",
                onRemove = { onRemove(chip) },
                style = chip.style,
            )
        }
    }
}

/**
 * The fields card, folded by "Edit filters": the five text fields, then the Tags and Subtags rows, whose chips name the
 * filter's tags as the strip does.
 */
// LongMethod: the fold animation and one flat call per field and row.
@Suppress("LongMethod")
@Composable
private fun FiltersCard(
    visible: Boolean,
    uiState: FindTracksUiState,
    chips: List<StripChip>,
    onFieldChange: (FindTracksField, String) -> Unit,
    onOpenPicker: (CustomTagKind) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val sizeSpec = rollaSpringOrSnap<IntSize>(reducedMotion)
    val fadeSpec = rollaSpringOrSnap<Float>(reducedMotion)
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) EnterTransition.None else expandVertically(sizeSpec) + fadeIn(fadeSpec),
        exit = if (reducedMotion) ExitTransition.None else shrinkVertically(sizeSpec) + fadeOut(fadeSpec),
    ) {
        Column(modifier = Modifier.fillMaxWidth().testTag(FIND_TRACKS_FIELDS_TAG)) {
            Column(
                modifier = Modifier.padding(horizontal = RollaDimens.listThumbStart),
                verticalArrangement = Arrangement.spacedBy(RollaDimens.editorFieldGap),
            ) {
                FilterField("Title", uiState.title, FindTracksField.TITLE, onFieldChange)
                FilterField("Artist", uiState.artist, FindTracksField.ARTIST, onFieldChange)
                FilterField("Album", uiState.album, FindTracksField.ALBUM, onFieldChange)
                FilterField("Genre", uiState.genre, FindTracksField.GENRE, onFieldChange)
                FilterField(
                    label = "Year",
                    value = uiState.year,
                    field = FindTracksField.YEAR,
                    onFieldChange = onFieldChange,
                    // The last field: Done closes the keyboard (the text field's default Done action).
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
            Spacer(modifier = Modifier.height(RollaDimens.editorFieldGap))
            CustomTagRows(
                tagNames = remember(chips) { chips.customTagLabels(CustomTagKind.TAG) },
                subTagNames = remember(chips) { chips.customTagLabels(CustomTagKind.SUBTAG) },
                enabled = true,
                onTagsClick = { onOpenPicker(CustomTagKind.TAG) },
                onSubTagsClick = { onOpenPicker(CustomTagKind.SUBTAG) },
            )
        }
    }
}

/**
 * One text field of the filter. By default its keyboard action is Next, so the fields chain Title → Artist → Album →
 * Genre → Year (the text field's default Next action moves focus to the next field).
 */
@Composable
private fun FilterField(
    label: String,
    value: String,
    field: FindTracksField,
    onFieldChange: (FindTracksField, String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
) {
    OneUiTextField(
        value = value,
        onValueChange = { onFieldChange(field, it) },
        label = label,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = keyboardOptions,
    )
}

/** What a strip chip's ✕ removes: a text field, or one tag or subtag ID. */
internal sealed interface StripTarget {
    data class Text(val field: FindTracksField) : StripTarget

    data class Custom(val kind: CustomTagKind, val id: Long) : StripTarget
}

/** One chip of the active-filter strip: its [label], its [style] (subtags Outline) and what its ✕ removes. */
@Immutable
internal data class StripChip(val label: String, val style: TagChipStyle, val target: StripTarget)

/**
 * The strip's chips for [state], in order: Title, Artist, Album, Genre (each when not blank, trimmed), Year (when it is
 * a number), then the tags and subtags of [FindTracksUiState.activeCustomTagFilters]. A tag the lists do not name is
 * shown as "Unavailable tag" / "Unavailable subtag" only while [FindTracksUiState.listsUnavailable]; otherwise it is
 * skipped (the lists have not answered yet, or are about to prune it).
 */
internal fun activeFilterChips(state: FindTracksUiState): List<StripChip> = buildList {
    addTextChip("Title", state.title, FindTracksField.TITLE)
    addTextChip("Artist", state.artist, FindTracksField.ARTIST)
    addTextChip("Album", state.album, FindTracksField.ALBUM)
    addTextChip("Genre", state.genre, FindTracksField.GENRE)
    if (state.filter.year != null) {
        add(StripChip("Year: ${state.year}", TagChipStyle.Tonal, StripTarget.Text(FindTracksField.YEAR)))
    }
    state.activeCustomTagFilters.forEach { entry ->
        val label = entry.name ?: unavailableLabel(entry.kind).takeIf { state.listsUnavailable }
        if (label != null) {
            val style = if (entry.kind == CustomTagKind.TAG) TagChipStyle.Tonal else TagChipStyle.Outline
            add(StripChip(label, style, StripTarget.Custom(entry.kind, entry.id)))
        }
    }
}

private fun MutableList<StripChip>.addTextChip(name: String, value: String, field: FindTracksField) {
    if (value.isNotBlank()) add(StripChip("$name: ${value.trim()}", TagChipStyle.Tonal, StripTarget.Text(field)))
}

private fun unavailableLabel(kind: CustomTagKind): String = when (kind) {
    CustomTagKind.TAG -> "Unavailable tag"
    CustomTagKind.SUBTAG -> "Unavailable subtag"
}

/** The labels of the strip's [kind] chips, for the Tags or Subtags row. */
private fun List<StripChip>.customTagLabels(kind: CustomTagKind): List<String> =
    filter { (it.target as? StripTarget.Custom)?.kind == kind }.map { it.label }

/** Shown in the results panel when nothing matches. */
internal const val NO_MATCH_MESSAGE = "No tracks match these filters"

/** Test tag on the screen's LazyColumn. */
internal const val FIND_TRACKS_LIST_TAG = "find_tracks_list"

/** Test tag on the active-filter strip. */
internal const val FIND_TRACKS_STRIP_TAG = "find_tracks_strip"

/** Test tag on the folded fields card. */
internal const val FIND_TRACKS_FIELDS_TAG = "find_tracks_fields"

private val PreviewSongs = List(4) { index ->
    Song(
        id = "$index",
        title = "Track ${index + 1}",
        artist = "Surgeon",
        album = "Force + Form",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = index + 1,
        year = 1999,
        contentUri = "",
        artworkUri = "",
    )
}

// LongMethod: one state and one no-op per event of the stateless screen.
@Suppress("UnusedPrivateMember", "LongMethod")
@Preview(name = "Find tracks - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 640)
@Preview(name = "Find tracks - Light", widthDp = 360, heightDp = 640)
@Composable
private fun PreviewFindTracksScreen() {
    val tags = listOf(CustomTag(CustomTagKind.TAG, 2L, "Techno"))
    RollaMusicPlayerTheme {
        FindTracksScreen(
            uiState = FindTracksUiState(
                title = "",
                artist = "Surgeon",
                album = "",
                genre = "",
                year = "",
                tags = tags,
                subTags = emptyList(),
                filter = SongFilter(artist = "Surgeon", tagIds = setOf(2L)),
                results = PreviewSongs,
                filtersExpanded = false,
                picker = null,
                listsUnavailable = false,
                activeCustomTagFilters = listOf(ActiveCustomTagFilter(CustomTagKind.TAG, 2L, "Techno")),
            ),
            userPlaylists = emptyList(),
            onNavigateUp = {},
            onFieldChange = { _, _ -> },
            onRemoveTextFilter = {},
            onRemoveCustomTag = { _, _ -> },
            onToggleFilters = {},
            onOpenPicker = {},
            onPickerPageChange = {},
            onPickerQueryChange = { _, _ -> },
            onPickerToggle = { _, _ -> },
            onPickerDone = {},
            onSongClick = {},
            onShuffleClick = {},
            onPlayClick = {},
            onAddSongToPlaylist = { _, _ -> },
            onCreatePlaylistAndAddSong = { _, _ -> },
            onEditTagsClick = {},
        )
    }
}
