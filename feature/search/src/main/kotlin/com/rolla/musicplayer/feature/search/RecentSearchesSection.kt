@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.component.OneUiIconButton
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader
import com.rolla.musicplayer.core.designsystem.theme.songTitle

/**
 * The recent-search history shown in [SearchUiState.Idle] (Phase 6b ruling 4): a header row ("Recent searches" and a
 * "Clear" action that wipes all history), then one row per recorded query, most-recent-first. The list pads its
 * bottom by [homeListBottomPadding] (ruling 3). Keys are the query strings themselves -- safe because the repository
 * dedupes to a single entry per exact query, so no two rows in this list ever share a key.
 *
 * Lives in its own file (rather than SearchScreen.kt, whose Idle state renders it) purely to keep
 * that file under detekt's TooManyFunctions file threshold.
 */
@Composable
internal fun RecentSearchesList(
    recentSearches: List<String>,
    onRecentSearchClick: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = homeListBottomPadding()),
    ) {
        item(key = "header-recent-searches") {
            RecentSearchesHeader(onClearRecentSearches = onClearRecentSearches)
        }
        items(items = recentSearches, key = { recentQuery -> recentQuery }) { recentQuery ->
            RecentSearchRow(
                query = recentQuery,
                onClick = { onRecentSearchClick(recentQuery) },
                onRemove = { onRemoveRecentSearch(recentQuery) },
            )
        }
    }
}

/**
 * "Recent searches" (sectionHeader, at [RollaDimens.listThumbStart]) paired with a trailing "Clear" text button for
 * the whole history; [RollaDimens.singleLineRowHeight] tall, like the results' section headers.
 */
@Composable
private fun RecentSearchesHeader(onClearRecentSearches: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.singleLineRowHeight)
            .padding(start = RollaDimens.listThumbStart, end = RollaDimens.listOverflowEnd),
    ) {
        Text(
            text = "Recent searches",
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        OneUiTextButton(
            text = "Clear",
            onClick = onClearRecentSearches,
            modifier = Modifier.semantics { contentDescription = "Clear recent searches" },
        )
    }
}

/**
 * One recent-search row, [RollaDimens.singleLineRowHeight] tall:
 * - a Search glyph (onSurfaceVariant) centred in a [RollaDimens.listThumb] box at [RollaDimens.listThumbStart];
 * - the query in songTitle at [RollaDimens.listTextStart], one line, ellipsized;
 * - a trailing Close button that removes only this query.
 *
 * The row body searches the query again. The Close button is its own clickable, so TalkBack reads it separately
 * and a tap on it never reaches the row.
 */
@Composable
private fun RecentSearchRow(query: String, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.singleLineRowHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Search again for $query" }
            .padding(start = RollaDimens.listThumbStart, end = RollaDimens.listOverflowEnd),
    ) {
        RecentSearchGlyph()
        Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
        Text(
            text = query,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        OneUiIconButton(
            icon = RollaIcons.Close,
            contentDescription = "Remove $query from recent searches",
            onClick = onRemove,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The decorative Search glyph (onSurfaceVariant), centred in a [RollaDimens.listThumb] box like a row thumbnail. */
@Composable
private fun RecentSearchGlyph() {
    Box(modifier = Modifier.size(RollaDimens.listThumb), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = RollaIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RollaDimens.iconSize),
        )
    }
}
