package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 -> v3: adds the `equalizer_presets` table for saved/custom equalizer presets (Phase 5).
 *
 * Purely additive: no existing tables, columns, or rows are touched. This table holds *named*
 * presets only; the equalizer's *active* state (enabled flag + currently-applied gains) is a
 * separate concern owned by `:core:datastore`'s `EqualizerPreferences`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS equalizer_presets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                is_custom INTEGER NOT NULL,
                gains_millibel TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
