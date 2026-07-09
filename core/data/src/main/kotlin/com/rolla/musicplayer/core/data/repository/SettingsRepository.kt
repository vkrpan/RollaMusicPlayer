package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * App-wide appearance and playback settings surfaced on the Settings screen (see
 * `ui-style-guide.md` §6 and `PHASE6-SETTINGS.md`'s scope note -- no crossfade/gapless/pure-black
 * toggles; those were rejected as decorative controls that wouldn't actually do anything).
 *
 * Every `observe*` stream is backed by `:core:datastore`'s `SettingsPreferences` (Preferences
 * DataStore) -- consumers (the app-root theme, `:core:media`'s playback service) observe rather
 * than read a one-shot snapshot, so a settings change takes effect immediately without needing a
 * restart. Mirrors this module's [SongRepository]/[PlaylistRepository] convention of `observeX()`
 * functions rather than eagerly-initialized properties.
 */
interface SettingsRepository {

    /** System/light/dark theme selection. Defaults to [ThemeMode.SYSTEM] when unset. */
    fun observeThemeMode(): Flow<ThemeMode>

    /** Whether Material You dynamic color is applied. Defaults to `false` (off) when unset. */
    fun observeUseDynamicColor(): Flow<Boolean>

    /**
     * Playback speed multiplier, always normalized to [MIN_PLAYBACK_SPEED]..[MAX_PLAYBACK_SPEED].
     * A stored value outside that range (corrupt store, foreign write) falls back to
     * [DEFAULT_PLAYBACK_SPEED] rather than being applied as-is.
     */
    fun observePlaybackSpeed(): Flow<Float>

    /** Whether ExoPlayer's silence-skipping is enabled. Defaults to `false` when unset. */
    fun observeSkipSilence(): Flow<Boolean>

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setUseDynamicColor(useDynamicColor: Boolean)

    /** Persists [playbackSpeed], clamped to [MIN_PLAYBACK_SPEED]..[MAX_PLAYBACK_SPEED] first. */
    suspend fun setPlaybackSpeed(playbackSpeed: Float)

    suspend fun setSkipSilence(skipSilence: Boolean)
}

/** Minimum allowed playback speed multiplier -- the Settings slider must not go below this. */
const val MIN_PLAYBACK_SPEED = 0.5f

/** Maximum allowed playback speed multiplier -- the Settings slider must not go above this. */
const val MAX_PLAYBACK_SPEED = 2.0f

/** Normal (unmodified) playback speed, and the fallback default for an invalid stored value. */
const val DEFAULT_PLAYBACK_SPEED = 1.0f
