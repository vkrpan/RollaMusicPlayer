package com.rolla.musicplayer.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
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

    // Derived, so a reader of isActive recomposes only when selection starts or ends, not on every toggle in between.
    val isActive: Boolean by derivedStateOf { selectedIds.isNotEmpty() }

    /**
     * While true the selection is frozen for the user: [toggle] (a row's tap or long press) is ignored. Home sets it
     * while the selected tracks' artwork is being written (spec §5.5: the selection stays until the write ends, ruling
     * P23). [clear] and [retainAll] still apply: the write's own result, rescan pruning and access loss use them. Not
     * saved: the owner sets it again from its busy state.
     */
    var isLocked: Boolean by mutableStateOf(false)

    fun isSelected(songId: String): Boolean = songId in selectedIds

    /** Adds or removes [songId]; ignored while [isLocked]. */
    fun toggle(songId: String) {
        if (isLocked) return
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
