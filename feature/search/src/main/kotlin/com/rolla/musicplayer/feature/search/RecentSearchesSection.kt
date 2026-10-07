@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader

private val MinTouchTarget = 48.dp
private val SectionHeaderHorizontalPadding = 16.dp
private val SectionHeaderVerticalPadding = 12.dp
private val RecentSearchIconTextGap = 16.dp
private val RecentSearchRowHorizontalPadding = 16.dp

/**
 * The recent-search history shown in [SearchUiState.Idle]: a header row (title + a "Clear" action
 * that wipes all history) followed by one tappable row per recorded query, most-recent-first.
 * Keys are the query strings themselves -- safe because the repository dedupes to a single entry
 * per exact query, so no two rows in this list ever share a key.
 *
 * Lives in its own file (rather than SearchScreen.kt, whose Idle state renders it) purely to keep
 * that file under detekt's TooManyFunctions file threshold.
 */
@Composable
internal fun RecentSearchesList(
    recentSearches: List<String>,
    onRecentSearchClick: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "header-recent-searches") {
            RecentSearchesHeader(onClearRecentSearches = onClearRecentSearches)
        }
        items(items = recentSearches, key = { recentQuery -> recentQuery }) { recentQuery ->
            RecentSearchRow(
                query = recentQuery,
                onClick = { onRecentSearchClick(recentQuery) },
            )
        }
    }
}

/** "Recent searches" section title paired with a trailing "Clear" action for the whole history. */
@Composable
private fun RecentSearchesHeader(onClearRecentSearches: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SectionHeaderHorizontalPadding,
                top = SectionHeaderVerticalPadding,
                bottom = SectionHeaderVerticalPadding,
            ),
    ) {
        Text(
            text = "Recent searches",
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onClearRecentSearches,
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .semantics { contentDescription = "Clear recent searches" },
        ) {
            Text(text = "Clear", color = MaterialTheme.colorScheme.accentText)
        }
    }
}

/** One recent-search row: a history glyph and the query text, tappable to search it again. */
@Composable
private fun RecentSearchRow(query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Search again for $query" }
            .padding(horizontal = RecentSearchRowHorizontalPadding),
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(RecentSearchIconTextGap))
        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
