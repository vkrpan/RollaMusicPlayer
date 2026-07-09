package com.rolla.musicplayer.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rolla.musicplayer.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Default playback speed multiplier -- normal (unmodified) speed. */
internal const val DEFAULT_PLAYBACK_SPEED = 1.0f

private object SettingsPreferencesKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
}

@Singleton
class SettingsPreferencesImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsPreferences {

    override val themeMode: Flow<ThemeMode> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> decodeThemeMode(preferences[SettingsPreferencesKeys.THEME_MODE]) }

    override val useDynamicColor: Flow<Boolean> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[SettingsPreferencesKeys.USE_DYNAMIC_COLOR] ?: false }

    override val playbackSpeed: Flow<Float> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[SettingsPreferencesKeys.PLAYBACK_SPEED] ?: DEFAULT_PLAYBACK_SPEED }

    override val skipSilence: Flow<Boolean> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[SettingsPreferencesKeys.SKIP_SILENCE] ?: false }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[SettingsPreferencesKeys.THEME_MODE] = encodeThemeMode(themeMode)
        }
    }

    override suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SettingsPreferencesKeys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }

    override suspend fun setPlaybackSpeed(playbackSpeed: Float) {
        context.dataStore.edit { preferences ->
            preferences[SettingsPreferencesKeys.PLAYBACK_SPEED] = playbackSpeed
        }
    }

    override suspend fun setSkipSilence(skipSilence: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SettingsPreferencesKeys.SKIP_SILENCE] = skipSilence
        }
    }
}

/** Encodes [themeMode] as its enum name, e.g. `"DARK"`. */
internal fun encodeThemeMode(themeMode: ThemeMode): String = themeMode.name

/**
 * Decodes a string previously produced by [encodeThemeMode].
 *
 * Defensive by design, mirroring [decodeGainsMillibel]/[decodeRecentSearches]: a `null`/absent
 * value, or any value that doesn't match a known [ThemeMode] name (corrupt store, foreign write,
 * or a value written by a future app version with an enum entry this build doesn't know about),
 * falls back to [ThemeMode.SYSTEM] rather than throwing or crashing the read flow.
 */
internal fun decodeThemeMode(value: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == value } ?: ThemeMode.SYSTEM
