---
name: implement-equalizer
description: "Step-by-step workflow for the 8-band graphic equalizer in RollaMusicPlayer — binding android.media.audiofx.Equalizer to the ExoPlayer audio session, mapping the 8 target frequencies, saving/loading presets to Room, and rendering the band sliders with real-time visualization. All processing is local and offline."
---

# Skill: Implement Equalizer

## Overview
A workflow for building the 8-band graphic equalizer. It binds Android's `audiofx.Equalizer` audio effect to ExoPlayer's audio session, maps the project's 8 target frequencies (40Hz–10kHz) onto the device equalizer's bands, applies and persists gains, manages presets in Room, and drives the band-slider UI. All audio effect processing happens locally on the device.

## When to Use
- Building the equalizer screen and effect engine (Phase 5)
- Wiring the equalizer to playback
- Adding preset save/load
- Debugging "equalizer has no audible effect"

## Prerequisites
- ExoPlayer/playback service exists and exposes its `audioSessionId` (audio-engineer owns this)
- Room database with an `EqualizerPresetEntity` + DAO (data-layer-agent owns the schema; see `add-room-database`)
- Hilt configured; Compose UI
- The 8 project frequencies: 40Hz, 80Hz, 160Hz, 315Hz, 630Hz, 1.25kHz, 2.5kHz, 5kHz, 10kHz

> The Equalizer effect attaches to an audio **session id**, not to ExoPlayer's API directly. Get the session id from the player and keep the effect's lifecycle tied to the player's lifecycle (audio-engineer coordinates the session id source).

## Workflow Steps

### Step 1: Define the Equalizer Domain Model
**Goal**: Represent bands and gains independent of the framework

**Implementation**:
```kotlin
// audio/equalizer/EqualizerModel.kt
// Project target frequencies (Hz), 8 logical bands
val TARGET_FREQUENCIES_HZ = intArrayOf(
    40, 80, 160, 315, 630, 1250, 2500, 5000, 10000
)

@Immutable
data class EqualizerSettings(
    val enabled: Boolean = false,
    val bandGainsMillibel: List<Short> = List(TARGET_FREQUENCIES_HZ.size) { 0 } // -1500..+1500 typical
)

@Immutable
data class EqualizerCapabilities(
    val minGainMillibel: Short,
    val maxGainMillibel: Short,
    val deviceBandCount: Short
)
```

> Note: the project specifies 9 frequency labels (40Hz…10kHz). The `audiofx.Equalizer` device usually exposes ~5 bands. Map the project's target frequencies onto the nearest device band via `getBand(frequencyMilliHz)`; multiple targets may resolve to the same device band. Decide the UX with audio-engineer: either show the device's real band count, or present the labelled targets and route them to the nearest device band.

### Step 2: Wrap the Framework Equalizer
**Goal**: A single class that owns the `Equalizer` effect bound to a session id

**Implementation**:
```kotlin
// audio/equalizer/EqualizerController.kt
class EqualizerController @Inject constructor() {

    private var equalizer: Equalizer? = null

    fun attach(audioSessionId: Int) {
        if (audioSessionId == AudioManager.AUDIO_SESSION_ID_GENERATE) return
        release()
        equalizer = Equalizer(/* priority = */ 0, audioSessionId)
    }

    fun capabilities(): EqualizerCapabilities? = equalizer?.let { eq ->
        val range = eq.bandLevelRange // [min, max] in millibel
        EqualizerCapabilities(
            minGainMillibel = range[0],
            maxGainMillibel = range[1],
            deviceBandCount = eq.numberOfBands
        )
    }

    fun setEnabled(enabled: Boolean) { equalizer?.enabled = enabled }

    /** Apply a gain (millibel) to the device band nearest the given target frequency. */
    fun setGainForFrequency(frequencyHz: Int, gainMillibel: Short) {
        val eq = equalizer ?: return
        val band = eq.getBand(frequencyHz * 1000) // expects milliHz
        eq.setBandLevel(band, gainMillibel)
    }

    fun currentGainForFrequency(frequencyHz: Int): Short {
        val eq = equalizer ?: return 0
        return eq.getBandLevel(eq.getBand(frequencyHz * 1000))
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
```

### Step 3: Tie the Effect to the Player Lifecycle
**Goal**: Attach when the session is ready, re-apply saved settings, release on teardown

**Implementation**:
```kotlin
// In the playback service / audio module (coordinate with audio-engineer)
player.addListener(object : Player.Listener {
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        equalizerController.attach(audioSessionId)
        applySavedEqualizerSettings()  // re-apply persisted gains + enabled
    }
})

// On service destroy:
equalizerController.release()
```

```kotlin
private fun applySavedEqualizerSettings() {
    val settings = equalizerRepository.currentSettings() // from Room/prefs
    equalizerController.setEnabled(settings.enabled)
    TARGET_FREQUENCIES_HZ.forEachIndexed { i, freq ->
        equalizerController.setGainForFrequency(freq, settings.bandGainsMillibel[i])
    }
}
```

### Step 4: Persist Presets in Room
**Goal**: Save/load custom presets locally

**Implementation** (entity owned by data-layer-agent — request if missing):
```kotlin
@Entity(tableName = "equalizer_presets")
data class EqualizerPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "gains_millibel") val gains: List<Short>, // TypeConverter (Short list)
    @ColumnInfo(name = "created_at") val createdAt: Long
)
```

```kotlin
// audio/equalizer/EqualizerRepository.kt
class EqualizerRepository @Inject constructor(
    private val presetDao: EqualizerPresetDao,
    private val prefs: EqualizerPreferences  // stores the active EqualizerSettings
) {
    fun observePresets(): Flow<List<EqualizerPreset>> =
        presetDao.observeAllPresets().map { it.map(EqualizerPresetEntity::toDomain) }

    suspend fun savePreset(name: String, gains: List<Short>) =
        presetDao.insertPreset(
            EqualizerPresetEntity(
                name = name, isCustom = true, gains = gains,
                createdAt = System.currentTimeMillis()
            )
        )

    fun currentSettings(): EqualizerSettings = prefs.load()
    suspend fun saveSettings(settings: EqualizerSettings) = prefs.save(settings)
}
```

> Persist the **active** gains/enabled state (so the effect restores on next launch) separately from named **presets** (saved snapshots the user can recall). The active state can live in SharedPreferences; presets live in Room.

### Step 5: Build the Equalizer Screen (state-management owned by viewmodel-architect)
**Goal**: Vertical band sliders + preset controls, applying changes live

**Implementation**:
```kotlin
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val controller: EqualizerController,
    private val repository: EqualizerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerSettings())
    val uiState: StateFlow<EqualizerSettings> = _uiState.asStateFlow()

    val presets = repository.observePresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { _uiState.value = repository.currentSettings() }

    fun setEnabled(enabled: Boolean) {
        controller.setEnabled(enabled)
        _uiState.update { it.copy(enabled = enabled) }
        persist()
    }

    fun setBandGain(index: Int, gainMillibel: Short) {
        controller.setGainForFrequency(TARGET_FREQUENCIES_HZ[index], gainMillibel)
        _uiState.update { state ->
            state.copy(bandGainsMillibel = state.bandGainsMillibel.toMutableList()
                .also { it[index] = gainMillibel })
        }
        persist()
    }

    fun saveCurrentAsPreset(name: String) = viewModelScope.launch {
        repository.savePreset(name, _uiState.value.bandGainsMillibel)
    }

    private fun persist() = viewModelScope.launch { repository.saveSettings(_uiState.value) }
}
```

```kotlin
// presentation/equalizer/EqualizerScreen.kt — UI owned by ui-builder; theme by m3-design-system-agent
@Composable
fun EqualizerBands(
    gains: List<Short>,
    range: IntRange,                 // device min..max millibel
    onGainChange: (index: Int, gain: Short) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TARGET_FREQUENCIES_HZ.forEachIndexed { i, freq ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Vertical slider (rotate a Slider or use a custom vertical control)
                VerticalGainSlider(
                    value = gains[i].toFloat(),
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    onValueChange = { onGainChange(i, it.toInt().toShort()) }
                )
                Text(
                    text = freq.toFrequencyLabel(),  // "40Hz" / "1.25kHz"
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

> The slider rotation/visualization polish (drag feel, animated fill, waveform) is owned by compose-animation-agent — see `add-animations-transitions`. Keep this screen's logic thin and let that agent handle motion.

### Step 6: Optional Real-Time Visualization
**Goal**: Animate a waveform/spectrum while adjusting

**Notes**:
- Use `android.media.audiofx.Visualizer` bound to the same audio session id for FFT/waveform data, on a background thread, throttled.
- Visualizer requires `RECORD_AUDIO` permission on some OEMs even for local playback — confirm before adding; if it triggers a permission prompt that conflicts with the privacy positioning, prefer a decorative animation driven by band gains instead of real capture. Coordinate this decision with audio-engineer and code-reviewer.

### Step 7: Verify
**Checklist**:
- [ ] Effect attaches when `audioSessionId` becomes available and survives track changes
- [ ] Moving a band slider audibly changes output in real time
- [ ] Enable/disable toggles the whole effect
- [ ] Active gains + enabled state restore after app restart
- [ ] Presets save to Room and re-apply correctly
- [ ] Gains clamped to the device `bandLevelRange`
- [ ] `equalizer.release()` called on teardown (no leaked effect)
- [ ] Works fully offline (all processing local)
- [ ] Band labels use the 8 project frequencies; mapping to device bands documented
- [ ] Frequency labels use theme typography; sliders meet 48dp touch targets

## Related Files
- `audio/equalizer/EqualizerModel.kt` — frequencies, settings, capabilities
- `audio/equalizer/EqualizerController.kt` — framework Equalizer wrapper
- `audio/equalizer/EqualizerRepository.kt` — presets (Room) + active settings (prefs)
- `presentation/equalizer/EqualizerViewModel.kt` — state
- `presentation/equalizer/EqualizerScreen.kt` — band sliders + presets

## Notes
- `Equalizer` gains are **millibel** (1 dB = 100 mB); clamp to `bandLevelRange`.
- `getBand(frequencyMilliHz)` expects milliHz — multiply Hz by 1000.
- Device band count is usually < 9; the project's 9 labels map onto nearest device bands. Document this mapping; don't pretend the hardware has 9 independent bands.
- Releasing the effect is mandatory — leaked `Equalizer` instances cause audio glitches and resource warnings.
- No network anywhere; all effect data is local.

## Common Pitfalls
- ❌ Attaching the Equalizer before a valid `audioSessionId` exists — silently does nothing.
- ❌ Forgetting to re-apply settings after `onAudioSessionIdChanged` (session can change) — equalizer appears to "reset".
- ❌ Passing Hz instead of milliHz to `getBand` — wrong band selected.
- ❌ Not releasing on destroy — leaks and stutter (the audio-engineer DoD already flags this).
