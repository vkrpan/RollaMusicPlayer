package com.rolla.musicplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.SettingsRepository
import com.rolla.musicplayer.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-root ViewModel. Its only job is bridging [SettingsRepository]'s appearance settings to
 * [com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme] at the one place the whole
 * Compose tree is themed -- [MainActivity]. No other app-root logic belongs here; every screen
 * keeps its own ViewModel.
 *
 * Both streams default to [ThemeMode.SYSTEM] / `false`, matching [SettingsRepository]'s own
 * documented defaults for an unset DataStore value.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS),
            initialValue = ThemeMode.SYSTEM,
        )

    val useDynamicColor: StateFlow<Boolean> = settingsRepository.observeUseDynamicColor()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS),
            initialValue = false,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
