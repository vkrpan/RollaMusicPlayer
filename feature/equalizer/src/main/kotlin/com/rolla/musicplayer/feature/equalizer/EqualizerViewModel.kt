package com.rolla.musicplayer.feature.equalizer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.media.equalizer.EqualizerCapabilities
import com.rolla.musicplayer.core.media.equalizer.EqualizerController
import com.rolla.musicplayer.core.media.equalizer.EqualizerRepository
import com.rolla.musicplayer.core.media.equalizer.EqualizerSettings
import com.rolla.musicplayer.core.media.equalizer.TARGET_FREQUENCIES_HZ
import com.rolla.musicplayer.core.model.EqualizerPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fallback minimum gain, in millibel, while EqualizerController is detached. See EqualizerUiState. */
private const val FALLBACK_MIN_GAIN_MILLIBEL: Short = -1500

/** Fallback maximum gain, in millibel, while EqualizerController is detached. See EqualizerUiState. */
private const val FALLBACK_MAX_GAIN_MILLIBEL: Short = 1500

/** How long EqualizerViewModel.setBandGain waits for drag activity to settle before persisting. */
private const val PERSIST_DEBOUNCE_MS = 250L

/**
 * Equalizer screen UI state.
 *
 * [gainsMillibel] is index-aligned with TARGET_FREQUENCIES_HZ (EqualizerBands.kt in :core:media)
 * -- nine entries, 40Hz..10kHz in order.
 *
 * [minGainMillibel]/[maxGainMillibel] mirror the attached [EqualizerController]'s live
 * [EqualizerCapabilities] range. The controller is only attached while the playback service holds
 * a live audio session (see [EqualizerController]'s KDoc); whenever it's detached (screen opened
 * before playback ever starts, or the effect failed to create on this device), capabilities()
 * returns null and this state falls back to a conservative +/-1500 millibel (+/-15dB) envelope --
 * the same envelope every built-in preset's gains are authored within (see
 * BuiltInEqualizerPresets.kt) -- so sliders still render a sane range.
 *
 * [selectedPresetId] is derived, never stored directly: it is the id of the first preset in
 * [presets] whose gains exactly equal [gainsMillibel], or null when no preset matches. The UI
 * renders that null case as a synthetic "Custom" chip.
 */
@Immutable
data class EqualizerUiState(
    val enabled: Boolean = false,
    val gainsMillibel: List<Short> = List(TARGET_FREQUENCIES_HZ.size) { 0 },
    val minGainMillibel: Short = FALLBACK_MIN_GAIN_MILLIBEL,
    val maxGainMillibel: Short = FALLBACK_MAX_GAIN_MILLIBEL,
    val presets: List<EqualizerPreset> = emptyList(),
    val selectedPresetId: Long? = null,
)

/**
 * ViewModel for the equalizer screen: hydrates persisted enabled/gain state and the live preset
 * list on start, applies every gain/enabled change to the live [EqualizerController] in real time,
 * and persists changes via [EqualizerRepository].
 *
 * [uiState] is deliberately an eagerly-updated private [MutableStateFlow], not a plain
 * `combine(repository.observeSettings(), repository.observePresets()) { ... }.stateIn(...)` (the
 * more common pattern in this codebase -- see PlayerViewModel/PlaylistsViewModel). Gain changes
 * arrive once per slider-drag frame: they must be reflected in [uiState] immediately for a
 * responsive slider, but must NOT hit DataStore once per frame (see [setBandGain]). Deriving state
 * straight from [EqualizerRepository.observeSettings] would tie those two together and force a
 * choice between a laggy slider and a DataStore write storm. Instead, [uiState] is hydrated once
 * from persisted settings and continuously from the live preset list, then mutated locally and
 * immediately by every intent below, with persistence debounced separately underneath.
 *
 * Attach/release of the underlying device effect are owned by the playback service (see
 * [EqualizerController]'s KDoc) -- this ViewModel never calls either.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val controller: EqualizerController,
    private val repository: EqualizerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = repository.currentSettings()
            _uiState.update { it.withSettings(settings).withCapabilities(controller.capabilities()) }
        }
        viewModelScope.launch {
            repository.observePresets().distinctUntilChanged().collect { presets ->
                _uiState.update { it.withPresets(presets) }
            }
        }
    }

    /** Enables/disables the effect in real time and persists the new state immediately. */
    fun setEnabled(enabled: Boolean) {
        controller.setEnabled(enabled)
        val capabilities = controller.capabilities()
        _uiState.update { it.copy(enabled = enabled).withCapabilities(capabilities) }
        persistNow()
    }

    /**
     * Applies [gainMillibel] to the band nearest `TARGET_FREQUENCIES_HZ[index]` in real time,
     * updates [uiState] immediately, and schedules a debounced persist.
     *
     * Called once per slider-drag frame -- see the class KDoc for why persistence is debounced
     * separately from the (immediate) state update and controller call. Out-of-range [index]
     * values are ignored.
     */
    fun setBandGain(index: Int, gainMillibel: Short) {
        if (index !in TARGET_FREQUENCIES_HZ.indices) return
        controller.setGainForFrequency(TARGET_FREQUENCIES_HZ[index], gainMillibel)
        val capabilities = controller.capabilities()
        _uiState.update { current ->
            val updatedGains = current.gainsMillibel.toMutableList().apply { this[index] = gainMillibel }
            current.withGains(updatedGains).withCapabilities(capabilities)
        }
        schedulePersist()
    }

    /**
     * Applies every gain in [preset] to the controller in real time, updates [uiState], and
     * persists immediately. Leaves [EqualizerUiState.enabled] untouched.
     */
    fun selectPreset(preset: EqualizerPreset) {
        preset.gainsMillibel.forEachIndexed { index, gain ->
            if (index in TARGET_FREQUENCIES_HZ.indices) {
                controller.setGainForFrequency(TARGET_FREQUENCIES_HZ[index], gain)
            }
        }
        val capabilities = controller.capabilities()
        _uiState.update { it.withGains(preset.gainsMillibel).withCapabilities(capabilities) }
        persistNow()
    }

    /** Saves the current gains as a new user preset named [name]. Ignored when [name] is blank. */
    fun saveCurrentAsPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.savePreset(trimmed, _uiState.value.gainsMillibel)
        }
    }

    /** Deletes the user preset with [presetId]. A no-op for built-in presets (repository-enforced). */
    fun deletePreset(presetId: Long) {
        viewModelScope.launch {
            repository.deletePreset(presetId)
        }
    }

    /** Cancels any pending debounced persist and saves the current enabled/gains state right away. */
    private fun persistNow() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            saveCurrentSettings()
        }
    }

    /**
     * Cancels any pending persist and schedules a new one [PERSIST_DEBOUNCE_MS] out. Only the last
     * call in a rapid-fire burst (one slider-drag frame after another) actually reaches
     * [EqualizerRepository.saveSettings] -- earlier, superseded calls are cancelled before their
     * delay elapses.
     */
    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            saveCurrentSettings()
        }
    }

    private suspend fun saveCurrentSettings() {
        val state = _uiState.value
        repository.saveSettings(EqualizerSettings(enabled = state.enabled, gainsMillibel = state.gainsMillibel))
    }
}

private fun EqualizerUiState.withSettings(settings: EqualizerSettings): EqualizerUiState =
    withGains(settings.gainsMillibel).copy(enabled = settings.enabled)

private fun EqualizerUiState.withGains(gains: List<Short>): EqualizerUiState =
    copy(gainsMillibel = gains, selectedPresetId = matchingPresetId(gains, presets))

private fun EqualizerUiState.withPresets(presets: List<EqualizerPreset>): EqualizerUiState =
    copy(presets = presets, selectedPresetId = matchingPresetId(gainsMillibel, presets))

private fun EqualizerUiState.withCapabilities(capabilities: EqualizerCapabilities?): EqualizerUiState =
    copy(
        minGainMillibel = capabilities?.minGainMillibel ?: FALLBACK_MIN_GAIN_MILLIBEL,
        maxGainMillibel = capabilities?.maxGainMillibel ?: FALLBACK_MAX_GAIN_MILLIBEL,
    )

private fun matchingPresetId(gains: List<Short>, presets: List<EqualizerPreset>): Long? =
    presets.firstOrNull { it.gainsMillibel == gains }?.id
