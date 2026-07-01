package com.rolla.musicplayer.core.media

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackStateHolder @Inject constructor() {
    private val _audioSessionId = MutableStateFlow(C.AUDIO_SESSION_ID_UNSET)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffleMode = MutableStateFlow(ShuffleMode.OFF)
    val shuffleMode: StateFlow<ShuffleMode> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    fun setAudioSessionId(id: Int) {
        _audioSessionId.value = id
    }

    fun setCurrentSong(song: Song?) {
        _currentSong.value = song
    }

    fun setIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setPositionMs(position: Long) {
        _positionMs.value = position
    }

    fun setDurationMs(duration: Long) {
        _durationMs.value = duration
    }

    fun setShuffleMode(mode: ShuffleMode) {
        _shuffleMode.value = mode
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }
}
