package com.rolla.musicplayer.core.media

import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateHolder @Inject constructor() {
    private val _audioSessionId = MutableStateFlow(C.AUDIO_SESSION_ID_UNSET)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    fun setAudioSessionId(id: Int) {
        _audioSessionId.value = id
    }
}
