package com.rolla.musicplayer.core.media.equalizer

/**
 * The equalizer's *active* state: whether the effect is enabled, and the gain applied per project
 * target frequency (see [TARGET_FREQUENCIES_HZ]), in the same order.
 *
 * This is distinct from a saved [com.rolla.musicplayer.core.model.EqualizerPreset] snapshot — this
 * is the state that gets re-applied to the live effect on every attach (see
 * [EqualizerRepository.observeSettings] / [EqualizerRepository.currentSettings]), not a named
 * preset the user picks from a list.
 *
 * `:core:media` is not a Compose module, so this is a plain data class (no `@Immutable`) — the
 * Compose-facing copy used by `:feature:equalizer`'s UI state is that feature's own concern.
 */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val gainsMillibel: List<Short> = List(TARGET_FREQUENCIES_HZ.size) { 0 },
)
