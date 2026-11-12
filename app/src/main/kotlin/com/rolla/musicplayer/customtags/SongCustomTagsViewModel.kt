package com.rolla.musicplayer.customtags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.model.SongCustomTags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Every song's known custom tags, keyed by song ID (custom tags phase 4): what the chips in track lists and on Now
 * Playing show. MainActivity collects [tags] once at the content root and provides it to the whole tree as
 * `LocalSongCustomTags`. Empty until the first answer; a failing repository flow degrades to an empty map, so the
 * chips vanish and nothing else changes.
 */
@HiltViewModel
class SongCustomTagsViewModel @Inject constructor(
    repository: CustomTagRepository,
) : ViewModel() {

    val tags: StateFlow<Map<String, SongCustomTags>> = repository.observeSongCustomTags()
        .catch { emit(emptyMap()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
