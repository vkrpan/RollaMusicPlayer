package com.rolla.musicplayer.core.media.equalizer

import android.media.audiofx.Equalizer

/**
 * Production [DeviceEqualizer] backed by the real `android.media.audiofx.Equalizer` effect.
 *
 * This class (and [AudioFxDeviceEqualizerFactory], which constructs it) is the ONLY place in the
 * module that touches `android.media.audiofx.Equalizer` directly, so the framework dependency
 * cannot leak into [EqualizerController]'s JVM unit tests.
 */
class FrameworkDeviceEqualizer(private val equalizer: Equalizer) : DeviceEqualizer {

    override val numberOfBands: Short
        get() = equalizer.numberOfBands

    override val bandLevelRange: ShortArray
        get() = equalizer.bandLevelRange

    override fun getBand(frequencyMilliHz: Int): Short = equalizer.getBand(frequencyMilliHz)

    override fun getBandLevel(band: Short): Short = equalizer.getBandLevel(band)

    override fun setBandLevel(band: Short, levelMillibel: Short) {
        equalizer.setBandLevel(band, levelMillibel)
    }

    override var enabled: Boolean
        get() = equalizer.enabled
        set(value) {
            equalizer.enabled = value
        }

    override fun release() = equalizer.release()
}

/**
 * Production [DeviceEqualizer.Factory].
 *
 * `android.media.audiofx.Equalizer`'s constructor can throw `RuntimeException` /
 * `UnsupportedOperationException` on devices/emulators that lack the effect; callers (see
 * [EqualizerController.attach]) are responsible for catching that and staying detached rather than
 * crashing playback.
 */
class AudioFxDeviceEqualizerFactory : DeviceEqualizer.Factory {
    override fun create(priority: Int, audioSessionId: Int): DeviceEqualizer =
        FrameworkDeviceEqualizer(Equalizer(priority, audioSessionId))
}
