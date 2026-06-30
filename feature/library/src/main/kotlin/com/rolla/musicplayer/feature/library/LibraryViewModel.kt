package com.rolla.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    songRepository: SongRepository,
    private val libraryIndexer: LibraryIndexer,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val songs: StateFlow<List<Song>> = songRepository.observeSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        playbackController.connect()
    }

    fun onPermissionGranted() {
        val current = _scanState.value
        if (current is ScanState.Scanning || current is ScanState.Done) return
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            _scanState.value = try {
                val result = libraryIndexer.sync()
                ScanState.Done(added = result.added, removed = result.removed)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                ScanState.Error(e.message ?: "Scan failed")
            }
        }
    }

    fun play(song: Song) {
        playbackController.play(song)
    }
}
