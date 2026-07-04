package com.rolla.musicplayer.core.media.equalizer

/**
 * Live device equalizer capabilities, queried from the currently attached effect.
 *
 * Returned by [EqualizerController.capabilities]; `null` while the controller is detached (no
 * attached audio session, or effect creation failed on this device).
 */
data class EqualizerCapabilities(
    val minGainMillibel: Short,
    val maxGainMillibel: Short,
    val deviceBandCount: Short,
)
