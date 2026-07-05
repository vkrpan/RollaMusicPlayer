package com.rolla.musicplayer.core.media.equalizer

import androidx.annotation.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Binds [EqualizerController] to the player's *current* audio session and re-applies the
 * persisted active state ([EqualizerRepository.currentSettings]) onto it.
 *
 * ### Why re-apply on every call, not just the first attach
 * [EqualizerController.attach] always creates a brand-new native `android.media.audiofx.Equalizer`
 * instance (see its KDoc: it releases and re-creates). A freshly created effect instance always
 * starts with flat/default gains and `enabled = false` — it has no memory of what the user
 * previously dialed in. The audio session id itself can also change during the life of the app
 * (the player re-initializes its session, e.g. after certain track/format transitions or service
 * restarts), which forces a fresh [EqualizerController.attach] with a new underlying effect. So
 * every time this manager attaches, it must push the persisted enabled/gains state back onto the
 * new effect — otherwise the equalizer would appear to silently "reset" whenever the session
 * changes, even though nothing about the user's saved settings changed.
 *
 * ### Intended call pattern (for audio-engineer's service-level wiring)
 * This class does not itself observe `PlaybackStateHolder.audioSessionId` — the intended caller
 * (service-level plumbing, outside this module's ownership) should `collectLatest` that
 * `StateFlow<Int>` on a coroutine scope that is **off** the player/callback thread (e.g. a
 * service-lifecycle `CoroutineScope` on `Dispatchers.Main.immediate` or similar — never block
 * inside `Player.Listener.onAudioSessionIdChanged` itself), calling
 * [attachAndApplyPersisted] with each emitted id:
 *
 * ```
 * scope.launch {
 *     playbackStateHolder.audioSessionId.collectLatest { sessionId ->
 *         equalizerSessionManager.attachAndApplyPersisted(sessionId)
 *     }
 * }
 * ```
 *
 * Because `audioSessionId` is a [kotlinx.coroutines.flow.StateFlow], equal consecutive values are
 * deduplicated automatically — an ordinary track change that keeps the *same* session id never
 * re-triggers [attachAndApplyPersisted]. The effect attached for the previous track simply survives
 * the transition untouched; this manager only does work when the session id actually changes.
 */
@Singleton
class EqualizerSessionManager @Inject constructor(
    private val controller: EqualizerController,
    private val repository: EqualizerRepository,
) {

    /**
     * Attaches the equalizer effect to [audioSessionId] (via [EqualizerController.attach], which
     * already guards [androidx.media3.common.C.AUDIO_SESSION_ID_UNSET], re-attachment, and effect
     * creation failure), then re-applies the persisted active state
     * ([EqualizerRepository.currentSettings]) onto the freshly attached effect: enabled flag first,
     * then a gain per [TARGET_FREQUENCIES_HZ] entry.
     *
     * Defensively iterates only `minOf(TARGET_FREQUENCIES_HZ.size, settings.gainsMillibel.size)`
     * entries. [EqualizerRepository.currentSettings] already normalizes the persisted gains list to
     * exactly [TARGET_FREQUENCIES_HZ].size entries, so this is a belt-and-suspenders guard against
     * that invariant ever breaking — it must never crash here even if it does.
     */
    suspend fun attachAndApplyPersisted(audioSessionId: Int) {
        controller.attach(audioSessionId)
        applySettings(repository.currentSettings())
    }

    /** Releases the attached effect. Mandatory on service teardown (`onDestroy`). */
    fun release() {
        controller.release()
    }

    /**
     * Pushes [settings] onto [controller]: the enabled flag, then a gain per
     * [TARGET_FREQUENCIES_HZ] entry, defensively bounded to
     * `minOf(TARGET_FREQUENCIES_HZ.size, settings.gainsMillibel.size)` so a corrupt/wrong-size
     * gains list (see [attachAndApplyPersisted]'s KDoc) can never index out of bounds.
     *
     * Split out from [attachAndApplyPersisted] and marked [VisibleForTesting] purely so tests can
     * exercise this defensive bound directly with a hand-built [EqualizerSettings], since
     * [EqualizerRepository.currentSettings] itself never actually returns a wrong-size list in
     * practice (it normalizes first). Not part of the public API audio-engineer integrates
     * against — [attachAndApplyPersisted] and [release] are.
     */
    @VisibleForTesting
    internal fun applySettings(settings: EqualizerSettings) {
        controller.setEnabled(settings.enabled)
        val gainCount = minOf(TARGET_FREQUENCIES_HZ.size, settings.gainsMillibel.size)
        for (index in 0 until gainCount) {
            controller.setGainForFrequency(TARGET_FREQUENCIES_HZ[index], settings.gainsMillibel[index])
        }
    }
}
