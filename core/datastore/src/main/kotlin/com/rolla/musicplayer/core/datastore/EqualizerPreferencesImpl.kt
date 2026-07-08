package com.rolla.musicplayer.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single "settings" Preferences DataStore instance backing this whole module.
 *
 * Named "settings" (not "equalizer_prefs") because this same DataStore file also backs other app
 * settings per CLAUDE.md's storage strategy -- currently equalizer active-state here and recent
 * search history in [RecentSearchesDataSourceImpl]. Per the `by preferencesDataStore` delegate
 * contract, a given [Context] must only ever resolve one DataStore instance per file name, so this
 * is deliberately `internal` (not `private`) and must stay the ONLY
 * `preferencesDataStore(name = "settings")` delegate anywhere in the codebase -- other files in
 * this module reuse this same property instead of declaring their own.
 */
internal val Context.dataStore by preferencesDataStore(name = "settings")

private object EqualizerPreferencesKeys {
    val ENABLED = booleanPreferencesKey("eq_enabled")
    val GAINS = stringPreferencesKey("eq_gains")
}

@Singleton
class EqualizerPreferencesImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : EqualizerPreferences {

    override val enabled: Flow<Boolean> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[EqualizerPreferencesKeys.ENABLED] ?: false }

    override val gainsMillibel: Flow<List<Short>> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> decodeGainsMillibel(preferences[EqualizerPreferencesKeys.GAINS]) }

    override suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[EqualizerPreferencesKeys.ENABLED] = enabled }
    }

    override suspend fun setGainsMillibel(gainsMillibel: List<Short>) {
        context.dataStore.edit { preferences ->
            preferences[EqualizerPreferencesKeys.GAINS] = encodeGainsMillibel(gainsMillibel)
        }
    }
}

/** Encodes [gains] as a comma-separated string of millibel values, e.g. `"300,-100,0"`. */
internal fun encodeGainsMillibel(gains: List<Short>): String = gains.joinToString(",")

/**
 * Decodes a CSV string previously produced by [encodeGainsMillibel].
 *
 * Defensive by design: a blank/unset value decodes to an empty list, and if ANY token fails to
 * parse as a [Short], the entire stored value is treated as corrupt and [emptyList] is returned
 * rather than silently dropping just the bad token.
 */
internal fun decodeGainsMillibel(value: String?): List<Short> {
    if (value.isNullOrBlank()) return emptyList()
    val parsedTokens = value.split(",").map { it.toShortOrNull() }
    return if (parsedTokens.any { it == null }) emptyList() else parsedTokens.filterNotNull()
}
