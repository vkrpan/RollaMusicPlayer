package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 -> v5: adds the saved playback queue and state (queue restore, spec §5.2). Both tables start empty, and no
 * existing table or row is touched. `playback_queue` has no foreign key to `songs` (ruling R2).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playback_queue (
                slot INTEGER NOT NULL,
                song_id TEXT NOT NULL,
                shuffle_rank INTEGER NOT NULL,
                PRIMARY KEY(slot)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playback_state (
                id INTEGER NOT NULL,
                current_slot INTEGER NOT NULL,
                position_ms INTEGER NOT NULL,
                shuffle_enabled INTEGER NOT NULL,
                repeat_mode TEXT NOT NULL,
                saved_at INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
    }
}
