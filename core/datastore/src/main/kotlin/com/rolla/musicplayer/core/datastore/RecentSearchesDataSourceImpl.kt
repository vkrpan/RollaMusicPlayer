package com.rolla.musicplayer.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reuses the same "settings" Preferences DataStore file as [EqualizerPreferencesImpl] -- see that
 * file's `Context.dataStore` KDoc for why a second `preferencesDataStore(name = "settings")`
 * delegate must never be declared. This class only adds a new key to the shared store.
 */
private object RecentSearchesKeys {
    val QUERIES = stringPreferencesKey("recent_search_queries")
}

@Singleton
class RecentSearchesDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecentSearchesDataSource {

    override val recentSearches: Flow<List<String>> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> decodeRecentSearches(preferences[RecentSearchesKeys.QUERIES]) }

    override suspend fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        context.dataStore.edit { preferences ->
            val current = decodeRecentSearches(preferences[RecentSearchesKeys.QUERIES])
            preferences[RecentSearchesKeys.QUERIES] = encodeRecentSearches(withRecordedSearch(current, trimmed))
        }
    }

    override suspend fun clear() {
        context.dataStore.edit { preferences -> preferences.remove(RecentSearchesKeys.QUERIES) }
    }
}

/**
 * Computes the new most-recent-first list after recording [trimmedQuery] against [current].
 *
 * [trimmedQuery] is expected to already be trimmed and non-blank (see [RecentSearchesDataSourceImpl
 * .record]'s guard). An exact match already present in [current] is removed from its old position
 * so it doesn't end up duplicated once re-inserted at the front. The result is capped at
 * [MAX_RECENT_SEARCHES] entries, dropping the oldest ones beyond it.
 */
internal fun withRecordedSearch(current: List<String>, trimmedQuery: String): List<String> =
    (listOf(trimmedQuery) + current.filterNot { it == trimmedQuery }).take(MAX_RECENT_SEARCHES)

/**
 * Encodes [queries] as a comma-separated string, most-recent-first, e.g. `"metal,rock"`.
 *
 * Unlike [encodeGainsMillibel]'s numeric tokens, recent-search queries are free-form user text and
 * may themselves contain the `,` delimiter or the `\` escape character, so each query is escaped
 * before joining: `\` becomes `\\` and `,` becomes `\,`. Backslash must be escaped first, or it
 * would double-escape the marker added for `,` (mirrors [SearchRepositoryImpl]'s
 * `escapeForLike` ordering, just for a different delimiter/consumer).
 */
internal fun encodeRecentSearches(queries: List<String>): String =
    queries.joinToString(",") { query -> query.replace("\\", "\\\\").replace(",", "\\,") }

/**
 * Decodes a string previously produced by [encodeRecentSearches].
 *
 * Defensive by design, mirroring [decodeGainsMillibel]: a blank/unset value decodes to an empty
 * list. Escaped delimiters (`\,`) and escaped backslashes (`\\`) are unescaped while splitting, so
 * a query that itself contained a literal comma round-trips correctly instead of being split into
 * two entries.
 */
internal fun decodeRecentSearches(value: String?): List<String> {
    if (value.isNullOrEmpty()) return emptyList()

    val queries = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char == '\\' && index + 1 < value.length -> {
                current.append(value[index + 1])
                index += 2
            }
            char == ',' -> {
                queries += current.toString()
                current.clear()
                index++
            }
            else -> {
                current.append(char)
                index++
            }
        }
    }
    queries += current.toString()
    return queries
}
