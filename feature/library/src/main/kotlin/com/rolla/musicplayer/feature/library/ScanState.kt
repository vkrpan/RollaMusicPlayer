package com.rolla.musicplayer.feature.library

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Done(val added: Int, val removed: Int) : ScanState
    data class Error(val message: String) : ScanState
}
