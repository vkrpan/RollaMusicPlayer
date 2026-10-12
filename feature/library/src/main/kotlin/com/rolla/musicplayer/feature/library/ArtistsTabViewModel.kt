package com.rolla.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.ArtistRepository
import com.rolla.musicplayer.core.model.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Home pager's Artists tab: every artist, case-insensitively by name. null until Room answers (ruling 6). */
@HiltViewModel
class ArtistsTabViewModel @Inject constructor(
    artistRepository: ArtistRepository,
) : ViewModel() {

    val artists: StateFlow<List<Artist>?> = artistRepository.observeArtists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null,
        )
}
