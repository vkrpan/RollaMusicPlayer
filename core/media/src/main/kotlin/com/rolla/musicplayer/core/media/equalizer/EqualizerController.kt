package com.rolla.musicplayer.core.media.equalizer

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifecycle of a single [DeviceEqualizer] effect bound to the current playback audio
 * session, and applies/reads per-frequency gains against it.
 *
 * ### Session binding
 * This controller never creates a player, a `MediaSession`, or an audio session of its own — it
 * only ever binds to a session id handed to it via [attach]. That id is sourced from
 * `PlaybackStateHolder.audioSessionId` (owned by audio-engineer, fed from `PlaybackService`'s
 * `Player.Listener.onAudioSessionIdChanged`). Wiring this controller to that flow — calling
 * [attach] on every emission and re-applying persisted gains afterward — is service-level plumbing
 * done in a later step; this class only exposes the primitives that plumbing needs.
 *
 * ### Frequency -> device band mapping
 * See the KDoc on `TARGET_FREQUENCIES_HZ` (EqualizerBands.kt) for the full nearest-band mapping
 * write-up, including the last-write-wins consequence when multiple target frequencies share a
 * device band.
 *
 * ### Lifecycle
 * [attach] always releases any previously attached effect first, so it is safe to call repeatedly
 * (e.g. once per session-id change). [release] is mandatory on teardown (service destroy) to avoid
 * leaking the native effect, and is idempotent — safe to call multiple times, and safe to call when
 * never attached.
 */
@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class EqualizerController @Inject constructor(
    private val factory: DeviceEqualizer.Factory,
) {
    private var deviceEqualizer: DeviceEqualizer? = null

    /**
     * Attaches the equalizer effect to [audioSessionId].
     *
     * Releases any previously attached effect first, so this is safe to call again whenever the
     * session id changes. No-ops (remains detached) when [audioSessionId] is
     * [C.AUDIO_SESSION_ID_UNSET] — there is no valid session to bind to yet.
     *
     * `android.media.audiofx.Equalizer`'s constructor is documented to throw `RuntimeException` /
     * `UnsupportedOperationException` on devices/emulators that lack the effect; that failure is
     * caught here so a missing equalizer effect never crashes playback — the controller simply
     * stays detached and every other operation on it becomes a no-op.
     */
    @Suppress("TooGenericExceptionCaught")
    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        deviceEqualizer = try {
            factory.create(EFFECT_PRIORITY, audioSessionId)
        } catch (ignored: RuntimeException) {
            null
        }
    }

    /** Live device capabilities, or `null` while detached. */
    fun capabilities(): EqualizerCapabilities? = deviceEqualizer?.let { eq ->
        val range = eq.bandLevelRange
        EqualizerCapabilities(
            minGainMillibel = range[RANGE_MIN_INDEX],
            maxGainMillibel = range[RANGE_MAX_INDEX],
            deviceBandCount = eq.numberOfBands,
        )
    }

    /** Enables/disables the whole effect. No-op while detached. */
    fun setEnabled(enabled: Boolean) {
        deviceEqualizer?.enabled = enabled
    }

    /**
     * Applies [gainMillibel] to the device band nearest [frequencyHz], clamped to the device's
     * [DeviceEqualizer.bandLevelRange]. No-op while detached.
     *
     * See `TARGET_FREQUENCIES_HZ` (EqualizerBands.kt) for the frequency-to-band mapping and its
     * last-write-wins consequence when multiple target frequencies share a device band.
     */
    fun setGainForFrequency(frequencyHz: Int, gainMillibel: Short) {
        val eq = deviceEqualizer ?: return
        val band = eq.getBand(frequencyHz * MILLIHZ_PER_HZ)
        val range = eq.bandLevelRange
        val clamped = gainMillibel.coerceIn(range[RANGE_MIN_INDEX], range[RANGE_MAX_INDEX])
        eq.setBandLevel(band, clamped)
    }

    /** Current gain, in millibel, of the device band nearest [frequencyHz]; `0` while detached. */
    fun currentGainForFrequency(frequencyHz: Int): Short {
        val eq = deviceEqualizer ?: return 0
        return eq.getBandLevel(eq.getBand(frequencyHz * MILLIHZ_PER_HZ))
    }

    /** Releases the attached effect, if any. Mandatory on teardown; safe to call repeatedly. */
    fun release() {
        deviceEqualizer?.release()
        deviceEqualizer = null
    }

    private companion object {
        const val EFFECT_PRIORITY = 0
        const val MILLIHZ_PER_HZ = 1000
        const val RANGE_MIN_INDEX = 0
        const val RANGE_MAX_INDEX = 1
    }
}
