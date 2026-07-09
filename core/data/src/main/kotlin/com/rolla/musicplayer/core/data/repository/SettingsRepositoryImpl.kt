package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.datastore.SettingsPreferences
import com.rolla.musicplayer.core.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val preferences: SettingsPreferences,
) : SettingsRepository {

    override fun observeThemeMode(): Flow<ThemeMode> = preferences.themeMode

    override fun observeUseDynamicColor(): Flow<Boolean> = preferences.useDynamicColor

    /**
     * Normalizes whatever [SettingsPreferences.playbackSpeed] returns: an out-of-range value
     * (corrupt store, foreign write) falls back to [DEFAULT_PLAYBACK_SPEED] instead of being
     * applied as-is -- mirrors `EqualizerRepository.observeSettings`'s wrong-size-gains fallback
     * in `:core:media`.
     */
    override fun observePlaybackSpeed(): Flow<Float> = preferences.playbackSpeed.map { storedSpeed ->
        if (storedSpeed in MIN_PLAYBACK_SPEED..MAX_PLAYBACK_SPEED) storedSpeed else DEFAULT_PLAYBACK_SPEED
    }

    override fun observeSkipSilence(): Flow<Boolean> = preferences.skipSilence

    override suspend fun setThemeMode(themeMode: ThemeMode) = withContext(Dispatchers.IO) {
        preferences.setThemeMode(themeMode)
    }

    override suspend fun setUseDynamicColor(useDynamicColor: Boolean) = withContext(Dispatchers.IO) {
        preferences.setUseDynamicColor(useDynamicColor)
    }

    override suspend fun setPlaybackSpeed(playbackSpeed: Float) = withContext(Dispatchers.IO) {
        preferences.setPlaybackSpeed(playbackSpeed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED))
    }

    override suspend fun setSkipSilence(skipSilence: Boolean) = withContext(Dispatchers.IO) {
        preferences.setSkipSilence(skipSilence)
    }
}
