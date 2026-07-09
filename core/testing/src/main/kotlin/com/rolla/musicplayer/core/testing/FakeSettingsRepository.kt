package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.DEFAULT_PLAYBACK_SPEED
import com.rolla.musicplayer.core.data.repository.MAX_PLAYBACK_SPEED
import com.rolla.musicplayer.core.data.repository.MIN_PLAYBACK_SPEED
import com.rolla.musicplayer.core.data.repository.SettingsRepository
import com.rolla.musicplayer.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [SettingsRepository], mirroring [FakeSongRepository] /
 * [FakeSearchRepository]'s shape, for use by `SettingsViewModel` and app-root theme tests.
 *
 * Each setter both updates its backing [MutableStateFlow] -- so the matching `observe*` function's
 * flow re-emits immediately, the same way the real DataStore-backed repository would once a
 * write completes -- and records the call in its `*Calls` list, so tests can assert both the
 * resulting state and that persistence was actually invoked (and with what argument).
 */
class FakeSettingsRepository : SettingsRepository {

    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    private val useDynamicColorFlow = MutableStateFlow(false)
    private val playbackSpeedFlow = MutableStateFlow(DEFAULT_PLAYBACK_SPEED)
    private val skipSilenceFlow = MutableStateFlow(false)

    /** Every [setThemeMode] argument, in call order. */
    val setThemeModeCalls: List<ThemeMode> get() = _setThemeModeCalls
    private val _setThemeModeCalls = mutableListOf<ThemeMode>()

    /** Every [setUseDynamicColor] argument, in call order. */
    val setUseDynamicColorCalls: List<Boolean> get() = _setUseDynamicColorCalls
    private val _setUseDynamicColorCalls = mutableListOf<Boolean>()

    /** Every [setPlaybackSpeed] argument, in call order -- pre-clamping, as received. */
    val setPlaybackSpeedCalls: List<Float> get() = _setPlaybackSpeedCalls
    private val _setPlaybackSpeedCalls = mutableListOf<Float>()

    /** Every [setSkipSilence] argument, in call order. */
    val setSkipSilenceCalls: List<Boolean> get() = _setSkipSilenceCalls
    private val _setSkipSilenceCalls = mutableListOf<Boolean>()

    override fun observeThemeMode(): Flow<ThemeMode> = themeModeFlow.asStateFlow()

    override fun observeUseDynamicColor(): Flow<Boolean> = useDynamicColorFlow.asStateFlow()

    override fun observePlaybackSpeed(): Flow<Float> = playbackSpeedFlow.asStateFlow()

    override fun observeSkipSilence(): Flow<Boolean> = skipSilenceFlow.asStateFlow()

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        _setThemeModeCalls += themeMode
        themeModeFlow.value = themeMode
    }

    override suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        _setUseDynamicColorCalls += useDynamicColor
        useDynamicColorFlow.value = useDynamicColor
    }

    override suspend fun setPlaybackSpeed(playbackSpeed: Float) {
        _setPlaybackSpeedCalls += playbackSpeed
        playbackSpeedFlow.value = playbackSpeed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
    }

    override suspend fun setSkipSilence(skipSilence: Boolean) {
        _setSkipSilenceCalls += skipSilence
        skipSilenceFlow.value = skipSilence
    }
}
