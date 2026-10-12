package com.rolla.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.AlbumRepository
import com.rolla.musicplayer.core.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Home pager's Albums tab: every album, case-insensitively by title. null until Room answers (ruling 6). */
@HiltViewModel
class AlbumsTabViewModel @Inject constructor(
    albumRepository: AlbumRepository,
) : ViewModel() {

    val albums: StateFlow<List<Album>?> = albumRepository.observeAlbums()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null,
        )
}
