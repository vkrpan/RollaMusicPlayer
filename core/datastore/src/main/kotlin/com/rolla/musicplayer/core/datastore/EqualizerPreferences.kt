package com.rolla.musicplayer.core.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Contract for the equalizer's *active* state: whether the effect is enabled, and the gain
 * currently applied per band.
 *
 * Named/saved presets are NOT part of this contract — those are structured records that live in
 * Room (see `:core:database`'s `EqualizerPresetDao`). This interface only tracks what's
 * currently applied, so the equalizer effect can be restored on app restart without the user
 * having to re-pick a preset.
 *
 * This module intentionally does not bake in a band count: [gainsMillibel] is a flat, ordered
 * list whose expected size is defined and validated by the consumer (the `:core:media` equalizer
 * effect). Repository/consumer code normalizes and validates band counts, not this contract.
 */
interface EqualizerPreferences {

    /** Whether the equalizer effect is currently enabled. Defaults to `false` when unset. */
    val enabled: Flow<Boolean>

    /** Per-band gain in millibel, in band order. Defaults to `emptyList()` when unset. */
    val gainsMillibel: Flow<List<Short>>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setGainsMillibel(gainsMillibel: List<Short>)
}
