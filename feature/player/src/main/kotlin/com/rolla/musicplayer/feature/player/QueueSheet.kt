package com.rolla.musicplayer.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.OneUiBottomSheet
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.artistName
import com.rolla.musicplayer.core.designsystem.theme.emptyState
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.media.QueueItem
import com.rolla.musicplayer.core.media.QueueState
import com.rolla.musicplayer.core.ui.ArtworkThumbnail

const val QUEUE_LIST_TAG = "queue_list"

@Composable
fun QueueSheetRoute(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val onItemClick = remember(viewModel) { viewModel::skipTo }
    QueueSheet(queue = queue, onItemClick = onItemClick, onDismissRequest = onDismissRequest, modifier = modifier)
}

/** The queue (spec §11, §8.7): view plus tap-to-jump. Tapping a row plays it and keeps the sheet open. */
@Composable
fun QueueSheet(
    queue: QueueState,
    onItemClick: (QueueItem) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OneUiBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, title = "Queue") {
        if (queue.items.isEmpty()) {
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.emptyState,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(RollaDimens.screenEdge),
            )
        } else {
            QueueList(queue = queue, onItemClick = onItemClick)
        }
    }
}

@Composable
private fun QueueList(queue: QueueState, onItemClick: (QueueItem) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (queue.currentIndex >= 0) listState.scrollToItem(queue.currentIndex)
    }
    LazyColumn(state = listState, modifier = Modifier.testTag(QUEUE_LIST_TAG)) {
        itemsIndexed(queue.items, key = { _, item -> item.index }) { position, item ->
            QueueRow(item = item, isCurrent = position == queue.currentIndex, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, isCurrent: Boolean, onClick: () -> Unit) {
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.accentText else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.listRowHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isCurrent }
            .padding(start = RollaDimens.listThumbStart, end = RollaDimens.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkThumbnail(artworkUri = item.artworkUri, contentDescription = null)
        Spacer(Modifier.width(RollaDimens.listThumbTextGap))
        QueueRowText(item = item, titleColor = titleColor, modifier = Modifier.weight(1f))
        if (isCurrent) {
            Icon(
                RollaIcons.EqualizerBars,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.accentText,
                modifier = Modifier.size(RollaDimens.queueCurrentGlyph),
            )
        }
    }
}

/** Title over artist; the playing row's title takes [titleColor] (accentText). */
@Composable
private fun QueueRowText(item: QueueItem, titleColor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            item.title,
            style = MaterialTheme.typography.songTitle,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.artist,
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
