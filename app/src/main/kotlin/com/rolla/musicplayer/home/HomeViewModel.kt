package com.rolla.musicplayer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.feature.library.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Moved verbatim from LibraryViewModel.onPermissionGranted (Phase 2 ruling 2): Home's MediaPermissionGate owns the
// first-grant library sync for every tab.
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryIndexer: LibraryIndexer,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

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
}
