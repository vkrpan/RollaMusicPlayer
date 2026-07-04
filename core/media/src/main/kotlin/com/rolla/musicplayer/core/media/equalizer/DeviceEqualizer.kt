package com.rolla.musicplayer.core.media.equalizer

/**
 * Thin seam between [EqualizerController] and the Android framework's
 * `android.media.audiofx.Equalizer`.
 *
 * `android.media.audiofx.Equalizer` cannot be exercised on the plain JVM unit test runtime (it
 * requires the on-device audio framework), so every framework call the controller needs is routed
 * through this interface instead of calling `android.media.audiofx.Equalizer` directly. Production
 * code is backed by [FrameworkDeviceEqualizer]; unit tests substitute a fake and never touch the
 * real effect. See [EqualizerController] for the mandatory attach/release lifecycle.
 */
interface DeviceEqualizer {

    /**
     * Number of discrete bands the *device* effect actually exposes. Usually far fewer than the
     * project's nine target frequencies (see `TARGET_FREQUENCIES_HZ` in EqualizerBands.kt) — never
     * assume a fixed count, always read this from the live effect.
     */
    val numberOfBands: Short

    /** `[min, max]` gain, in millibel, that [setBandLevel] will accept on this device. */
    val bandLevelRange: ShortArray

    /** Resolves the device band nearest [frequencyMilliHz]. Expects **milliHz** (Hz * 1000). */
    fun getBand(frequencyMilliHz: Int): Short

    /** Current gain, in millibel, of [band]. */
    fun getBandLevel(band: Short): Short

    /** Sets the gain, in millibel, of [band]. Callers must clamp to [bandLevelRange] first. */
    fun setBandLevel(band: Short, levelMillibel: Short)

    /** Enables/disables the whole effect. */
    var enabled: Boolean

    /** Releases the underlying native effect. Mandatory once the effect is no longer needed. */
    fun release()

    /**
     * Creates a [DeviceEqualizer] bound to an existing audio session id.
     *
     * Implementations must never create a player or an audio session of their own — the session id
     * always comes from the caller (ultimately, the playback session owned by audio-engineer).
     */
    fun interface Factory {
        fun create(priority: Int, audioSessionId: Int): DeviceEqualizer
    }
}
