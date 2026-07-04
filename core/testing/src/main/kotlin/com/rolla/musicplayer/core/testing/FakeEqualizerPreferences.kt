package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.datastore.EqualizerPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory fake implementation of [EqualizerPreferences] backed by [MutableStateFlow]s. */
class FakeEqualizerPreferences : EqualizerPreferences {

    private val enabledFlow = MutableStateFlow(false)
    private val gainsMillibelFlow = MutableStateFlow<List<Short>>(emptyList())

    override val enabled: Flow<Boolean> = enabledFlow.asStateFlow()

    override val gainsMillibel: Flow<List<Short>> = gainsMillibelFlow.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        enabledFlow.value = enabled
    }

    override suspend fun setGainsMillibel(gainsMillibel: List<Short>) {
        gainsMillibelFlow.value = gainsMillibel
    }
}
