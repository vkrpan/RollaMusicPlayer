package com.rolla.musicplayer.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.SettingsRepository
import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.datastore.EqualizerPreferences
import com.rolla.musicplayer.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RESCAN_FAILURE_MESSAGE = "Couldn't rescan the library. Please try again."

/**
 * ViewModel for the Settings screen.
 *
 * [uiState] is a single reactive combination of every persisted setting this screen surfaces:
 * [SettingsRepository]'s four `observeX()` streams (theme, dynamic color, playback speed, skip
 * silence), plus the equalizer's persisted enabled state. That last one is read directly from
 * [EqualizerPreferences] (`:core:datastore`) rather than through `:core:media`'s
 * `EqualizerRepository` -- this module deliberately does not depend on `:core:media` (this screen
 * only ever *reads* the enabled flag for the Equalizer row's On/Off value text; toggling the
 * effect itself happens on the Equalizer screen), and [EqualizerPreferences] is the lowest-level
 * contract that already exposes exactly that one bit of state.
 *
 * Rescan/about/privacy actions and navigation are intentionally absent -- those are plain
 * stateless callbacks the screen hoists itself; this ViewModel only owns persisted settings.
 *
 * [isDynamicColorAvailable] is computed from [sdkIntProvider] rather than [Build.VERSION.SDK_INT]
 * directly, mirroring `TagEditorViewModel.sdkIntProvider`'s test seam: `Build.VERSION.SDK_INT`
 * cannot be made to read as a specific API level in a plain JVM unit test (no Robolectric in this
 * module), so tests override this internal, non-constructor-injected property instead. Hilt never
 * sees it since it is a plain property, not a constructor parameter.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val equalizerPreferences: EqualizerPreferences,
    private val libraryIndexer: LibraryIndexer,
) : ViewModel() {

    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }

    /**
     * Transient state of the manual rescan action -- deliberately separate from the persisted
     * settings so a rescan doesn't ripple through the settings combine, then merged into
     * [uiState] below. Main-thread confined (all writers run on viewModelScope's main dispatcher),
     * so the check-then-set reentry guard in [onRescanClick] is race-free.
     */
    private val rescanStatus = MutableStateFlow(RescanStatus())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeThemeMode(),
        settingsRepository.observeUseDynamicColor(),
        settingsRepository.observePlaybackSpeed(),
        settingsRepository.observeSkipSilence(),
        equalizerPreferences.enabled,
    ) { themeMode, useDynamicColor, playbackSpeed, skipSilence, equalizerEnabled ->
        SettingsUiState(
            themeMode = themeMode,
            useDynamicColor = useDynamicColor,
            playbackSpeed = playbackSpeed,
            skipSilence = skipSilence,
            equalizerEnabled = equalizerEnabled,
            isDynamicColorAvailable = isDynamicColorAvailable(),
        )
    }.combine(rescanStatus) { settings, rescan ->
        settings.copy(isRescanning = rescan.isRescanning, rescanMessage = rescan.message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = SettingsUiState(isDynamicColorAvailable = isDynamicColorAvailable()),
    )

    /** Persists the selected [themeMode]. Best-effort -- see [persistBestEffort]'s KDoc. */
    fun onThemeModeSelected(themeMode: ThemeMode) {
        persistBestEffort { settingsRepository.setThemeMode(themeMode) }
    }

    /** Persists the Material You dynamic-color toggle. Best-effort -- see [persistBestEffort]'s KDoc. */
    fun onUseDynamicColorChanged(useDynamicColor: Boolean) {
        persistBestEffort { settingsRepository.setUseDynamicColor(useDynamicColor) }
    }

    /**
     * Persists [playbackSpeed]. [SettingsRepository.setPlaybackSpeed] clamps to
     * `MIN_PLAYBACK_SPEED..MAX_PLAYBACK_SPEED` itself, so the raw slider value is passed through
     * unchanged. Best-effort -- see [persistBestEffort]'s KDoc.
     */
    fun onPlaybackSpeedChanged(playbackSpeed: Float) {
        persistBestEffort { settingsRepository.setPlaybackSpeed(playbackSpeed) }
    }

    /** Persists the skip-silence toggle. Best-effort -- see [persistBestEffort]'s KDoc. */
    fun onSkipSilenceChanged(skipSilence: Boolean) {
        persistBestEffort { settingsRepository.setSkipSilence(skipSilence) }
    }

    /**
     * Runs a full manual library rescan ([LibraryIndexer.sync] -- safe to fire even while another
     * sync is in flight elsewhere; the indexer serializes concurrent calls internally). Re-entry
     * from this screen is ignored while a rescan is already running. On completion,
     * [SettingsUiState.rescanMessage] carries a result summary; a failure becomes a friendly
     * message rather than a crash (same best-effort stance as the setting writes).
     */
    fun onRescanClick() {
        if (rescanStatus.value.isRescanning) return
        rescanStatus.value = RescanStatus(isRescanning = true)
        viewModelScope.launch {
            try {
                val result = libraryIndexer.sync()
                rescanStatus.value = RescanStatus(
                    message = "Library rescanned: ${result.added} added or updated, ${result.removed} removed",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                rescanStatus.value = RescanStatus(message = RESCAN_FAILURE_MESSAGE)
            }
        }
    }

    /** Clears [SettingsUiState.rescanMessage] once the snackbar has shown it. */
    fun dismissRescanMessage() {
        rescanStatus.update { it.copy(message = null) }
    }

    /**
     * Launches [block] in [viewModelScope], dropping any non-cancellation failure. Mirrors
     * `SearchViewModel.onClearRecentSearches`'s idiom: a DataStore write failure (disk full ->
     * IOException) is cosmetic here -- the setting simply doesn't persist -- and must never crash
     * the process via viewModelScope's default uncaught-exception handling.
     */
    private fun persistBestEffort(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                // Dropped deliberately; see KDoc above.
            }
        }
    }

    /** True on API 31+ (Android 12), where Material You dynamic color exists. */
    private fun isDynamicColorAvailable(): Boolean = sdkIntProvider() >= Build.VERSION_CODES.S

    /** Transient manual-rescan state merged into [uiState]; see [rescanStatus]. */
    private data class RescanStatus(
        val isRescanning: Boolean = false,
        val message: String? = null,
    )
}
