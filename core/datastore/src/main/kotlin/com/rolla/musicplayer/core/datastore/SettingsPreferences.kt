package com.rolla.musicplayer.core.datastore

import com.rolla.musicplayer.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the app-wide appearance/playback settings surfaced on the Settings screen (see
 * `ui-style-guide.md` §6 and `PHASE6-SETTINGS.md`'s scope note).
 *
 * Deliberately narrow: no crossfade, gapless, or pure-black toggles -- those were rejected as
 * decorative (ExoPlayer has no crossfade API, gapless is automatic with nothing to switch, and the
 * dark background is already true-black with no elevated-surface alternative to toggle between).
 * `resumeOnHeadsetConnect` is also deferred to keep this phase lean. Do not add keys for any of
 * these without updating that scope note first.
 *
 * Every property is a live [Flow] over this module's shared "settings" Preferences DataStore (see
 * [EqualizerPreferencesImpl]'s `Context.dataStore` KDoc) -- reads never block the caller and
 * survive process death. [themeMode] is decoded to [ThemeMode] here (invalid/absent storage falls
 * back to [ThemeMode.SYSTEM]); [playbackSpeed] is returned as stored, unclamped -- range validation
 * is the consumer's job (mirrors [EqualizerPreferences.gainsMillibel] leaving band-count validation
 * to its consumer), see `:core:data`'s `SettingsRepository` for where that normalization happens.
 */
interface SettingsPreferences {

    /** Theme mode selection. Defaults to [ThemeMode.SYSTEM] when unset or unrecognized. */
    val themeMode: Flow<ThemeMode>

    /** Whether Material You dynamic color is applied. Defaults to `false` when unset. */
    val useDynamicColor: Flow<Boolean>

    /**
     * Playback speed multiplier as stored, e.g. `1.0f` for normal speed. Not clamped here -- see
     * this interface's class-level doc.
     */
    val playbackSpeed: Flow<Float>

    /** Whether ExoPlayer's silence-skipping is enabled. Defaults to `false` when unset. */
    val skipSilence: Flow<Boolean>

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setUseDynamicColor(useDynamicColor: Boolean)

    suspend fun setPlaybackSpeed(playbackSpeed: Float)

    suspend fun setSkipSilence(skipSilence: Boolean)
}
