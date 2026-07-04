package com.rolla.musicplayer.core.database.converter

import androidx.room.TypeConverter

/**
 * Room [TypeConverter] for `List<Short>` columns (e.g. per-band equalizer gains), stored as a
 * comma-separated string column.
 *
 * Decode is defensive: a blank column value decodes to an empty list, and if ANY token fails to
 * parse as a [Short], the entire stored value is treated as corrupt and [emptyList] is returned
 * rather than partially-parsed data. This mirrors the codec contract used by
 * `:core:datastore`'s `EqualizerPreferences` for the same shape of data (the two are kept
 * independent — a Room `TypeConverter` cannot depend on `:core:datastore` — but behave
 * identically on corrupt input).
 */
class ShortListConverter {

    @TypeConverter
    fun fromShortList(value: List<Short>): String = value.joinToString(",")

    @TypeConverter
    fun toShortList(value: String): List<Short> {
        if (value.isBlank()) return emptyList()
        val parsedTokens = value.split(",").map { it.toShortOrNull() }
        return if (parsedTokens.any { it == null }) emptyList() else parsedTokens.filterNotNull()
    }
}
