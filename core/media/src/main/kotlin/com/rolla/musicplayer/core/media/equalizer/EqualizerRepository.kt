package com.rolla.musicplayer.core.media.equalizer

import com.rolla.musicplayer.core.database.dao.EqualizerPresetDao
import com.rolla.musicplayer.core.database.entity.EqualizerPresetEntity
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.datastore.EqualizerPreferences
import com.rolla.musicplayer.core.model.EqualizerPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for everything equalizer-related that outlives a single playback session:
 * the *active* enabled/gains state (restores the effect on next launch) and named *presets* (saved
 * snapshots the user can recall later). These are two different persistence concerns:
 *
 * - **Active state** ([observeSettings] / [currentSettings] / [saveSettings]) lives in
 *   [EqualizerPreferences] (DataStore) — it is what [EqualizerController] re-applies on every
 *   attach (see the service-level wiring that reacts to `onAudioSessionIdChanged`).
 * - **Presets** ([observePresets] / [savePreset] / [deletePreset]) are named snapshots. The six
 *   built-in presets ([BUILT_IN_EQUALIZER_PRESETS]) are in-code constants, never written to Room;
 *   only user-saved presets are persisted, via [EqualizerPresetDao].
 *
 * Applying gains to the live effect is [EqualizerController]'s job, not this repository's — this
 * class only reads/writes persisted state.
 */
@Singleton
class EqualizerRepository @Inject constructor(
    private val presetDao: EqualizerPresetDao,
    private val preferences: EqualizerPreferences,
) {

    /**
     * The six built-in presets first (fixed display order), followed by the user's saved presets
     * in the DAO's `created_at ASC, id ASC` order.
     */
    fun observePresets(): Flow<List<EqualizerPreset>> =
        presetDao.observeAllPresets().map { userPresets: List<EqualizerPresetEntity> ->
            BUILT_IN_EQUALIZER_PRESETS + userPresets.map(EqualizerPresetEntity::toDomain)
        }

    /**
     * Saves [gainsMillibel] as a new, user-created preset named [name] and returns its new row id.
     * Always persisted with `isCustom = true` — built-ins are never written back to Room.
     */
    suspend fun savePreset(name: String, gainsMillibel: List<Short>): Long = withContext(Dispatchers.IO) {
        presetDao.insertPreset(
            EqualizerPresetEntity(
                id = 0L,
                name = name,
                isCustom = true,
                gainsMillibel = gainsMillibel,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Deletes the user preset with [presetId]. A no-op for built-in presets (`presetId <= 0`, see
     * [BUILT_IN_EQUALIZER_PRESETS]' negative-id scheme) — there is nothing in Room to delete for
     * those, and the built-in list itself is immutable.
     */
    suspend fun deletePreset(presetId: Long) = withContext(Dispatchers.IO) {
        if (presetId > 0) presetDao.deletePreset(presetId)
    }

    /**
     * The current *active* equalizer state, normalized against corruption/first-launch:
     * [EqualizerPreferences.gainsMillibel] is a schema-less flat list under the hood (DataStore
     * stores it as a CSV string), so anything that doesn't have exactly
     * [TARGET_FREQUENCIES_HZ].size entries — an empty list on first launch, or a corrupt/wrong-size
     * value from a stale/foreign write — falls back to [EqualizerSettings]'s all-zero default
     * instead of being applied as-is.
     */
    fun observeSettings(): Flow<EqualizerSettings> =
        combine(preferences.enabled, preferences.gainsMillibel) { enabled, gainsMillibel ->
            val normalizedGains = if (gainsMillibel.size == TARGET_FREQUENCIES_HZ.size) {
                gainsMillibel
            } else {
                EqualizerSettings().gainsMillibel
            }
            EqualizerSettings(enabled = enabled, gainsMillibel = normalizedGains)
        }

    /**
     * A one-shot snapshot of [observeSettings], for the service-side "apply persisted state on
     * attach" call (see [EqualizerController.attach] and the `onAudioSessionIdChanged` wiring).
     */
    suspend fun currentSettings(): EqualizerSettings = observeSettings().first()

    /** Persists [settings] as the new active enabled/gains state. */
    suspend fun saveSettings(settings: EqualizerSettings) = withContext(Dispatchers.IO) {
        preferences.setEnabled(settings.enabled)
        preferences.setGainsMillibel(settings.gainsMillibel)
    }
}
