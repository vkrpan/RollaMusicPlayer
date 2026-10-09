package com.rolla.musicplayer.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Multi-select over song ids for list screens (spec §7.2). Rows read [isSelected] inside their own derivedStateOf,
 * so toggling one row recomposes only that row. Saved across process death as a list of ids.
 */
@Stable
class SongSelectionState internal constructor(initial: Set<String>) {
    var selectedIds: Set<String> by mutableStateOf(initial)
        private set

    val count: Int get() = selectedIds.size
    val isActive: Boolean get() = selectedIds.isNotEmpty()

    fun isSelected(songId: String): Boolean = songId in selectedIds

    fun toggle(songId: String) {
        selectedIds = if (songId in selectedIds) selectedIds - songId else selectedIds + songId
    }

    fun clear() {
        selectedIds = emptySet()
    }

    /**
     * Drops every selected id that is not in [ids], for example songs a rescan removed from the library. The survivors
     * keep their selection order.
     */
    fun retainAll(ids: Set<String>) {
        val retained = selectedIds.filterTo(LinkedHashSet()) { it in ids }
        if (retained.size != selectedIds.size) selectedIds = retained
    }

    companion object {
        val Saver: Saver<SongSelectionState, Any> = listSaver(
            save = { it.selectedIds.toList() },
            restore = { SongSelectionState(it.toSet()) },
        )
    }
}

@Composable
fun rememberSongSelectionState(): SongSelectionState =
    rememberSaveable(saver = SongSelectionState.Saver) { SongSelectionState(emptySet()) }
