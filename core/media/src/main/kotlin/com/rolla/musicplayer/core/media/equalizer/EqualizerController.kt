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
 * `Player.Listener.onAudioSessionIdChanged`). `PlaybackService` collects that flow and calls
 * [attach] (via `EqualizerSessionManager`) on every emission, re-applying persisted gains after.
 *
 * ### Frequency -> device band mapping
 * See the KDoc on `TARGET_FREQUENCIES_HZ` (EqualizerBands.kt) for the full nearest-band mapping
 * write-up, including the last-write-wins consequence when multiple target frequencies share a
 * device band.
 *
 * ### Thread safety
 * This is a Hilt `@Singleton` mutated from two sides at once: `PlaybackService`'s
 * `Dispatchers.Default` collector ([attach]/[release] on session-id changes and teardown) and the
 * main thread (`EqualizerViewModel`'s [setEnabled]/[setGainForFrequency]/[capabilities] calls from
 * Compose callbacks). Every public method is therefore [Synchronized]: each operation observes a
 * fully attached-or-detached effect, never a torn intermediate state, and writes are visible
 * across threads. Individual native effect calls are cheap (microseconds), so the lock is never
 * held for meaningful time except during [attach]'s effect creation.
 *
 * ### Failure containment
 * `android.media.audiofx.Equalizer` throws `RuntimeException`s in two situations: creation can
 * fail on devices/emulators lacking the effect, and *any* method on an effect the system has
 * released/revoked underneath us throws `IllegalStateException`. Both are contained here: creation
 * failure leaves the controller detached, and a failed operation on a live effect releases it and
 * detaches (see [withAttachedEffect]) so every subsequent call becomes a safe no-op instead of a
 * crash. A missing or dying equalizer must never take playback (or the UI) down with it.
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
    @Synchronized
    @Suppress("TooGenericExceptionCaught")
    fun attach(audioSessionId: Int) {
        releaseCurrent()
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        deviceEqualizer = try {
            factory.create(EFFECT_PRIORITY, audioSessionId)
        } catch (ignored: RuntimeException) {
            null
        }
    }

    /** Live device capabilities, or `null` while detached. */
    @Synchronized
    fun capabilities(): EqualizerCapabilities? = withAttachedEffect { eq ->
        val range = eq.bandLevelRange
        EqualizerCapabilities(
            minGainMillibel = range[RANGE_MIN_INDEX],
            maxGainMillibel = range[RANGE_MAX_INDEX],
            deviceBandCount = eq.numberOfBands,
        )
    }

    /** Enables/disables the whole effect. No-op while detached. */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        withAttachedEffect { eq -> eq.enabled = enabled }
    }

    /**
     * Applies [gainMillibel] to the device band nearest [frequencyHz], clamped to the device's
     * [DeviceEqualizer.bandLevelRange]. No-op while detached.
     *
     * See `TARGET_FREQUENCIES_HZ` (EqualizerBands.kt) for the frequency-to-band mapping and its
     * last-write-wins consequence when multiple target frequencies share a device band.
     */
    @Synchronized
    fun setGainForFrequency(frequencyHz: Int, gainMillibel: Short) {
        withAttachedEffect { eq ->
            val band = eq.getBand(frequencyHz * MILLIHZ_PER_HZ)
            val range = eq.bandLevelRange
            val clamped = gainMillibel.coerceIn(range[RANGE_MIN_INDEX], range[RANGE_MAX_INDEX])
            eq.setBandLevel(band, clamped)
        }
    }

    /** Current gain, in millibel, of the device band nearest [frequencyHz]; `0` while detached. */
    @Synchronized
    fun currentGainForFrequency(frequencyHz: Int): Short =
        withAttachedEffect { eq -> eq.getBandLevel(eq.getBand(frequencyHz * MILLIHZ_PER_HZ)) } ?: 0

    /** Releases the attached effect, if any. Mandatory on teardown; safe to call repeatedly. */
    @Synchronized
    fun release() {
        releaseCurrent()
    }

    /**
     * Releases and clears the current effect, swallowing the `IllegalStateException` a
     * system-revoked (already dead) native effect throws from `release()` — the field is nulled
     * either way, which is all "released" means to this controller.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun releaseCurrent() {
        try {
            deviceEqualizer?.release()
        } catch (ignored: RuntimeException) {
            // The native effect was already invalidated by the system; nothing left to release.
        }
        deviceEqualizer = null
    }

    /**
     * Runs [block] against the attached effect, or returns `null` while detached. If the effect
     * throws (`IllegalStateException` from a system-released/revoked native instance — see the
     * class KDoc's failure-containment section), it is released and the controller detaches, so
     * all subsequent operations degrade to no-ops instead of repeating the crash.
     */
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> withAttachedEffect(block: (DeviceEqualizer) -> T): T? {
        val eq = deviceEqualizer ?: return null
        return try {
            block(eq)
        } catch (ignored: RuntimeException) {
            releaseCurrent()
            null
        }
    }

    private companion object {
        const val EFFECT_PRIORITY = 0
        const val MILLIHZ_PER_HZ = 1000
        const val RANGE_MIN_INDEX = 0
        const val RANGE_MAX_INDEX = 1
    }
}
