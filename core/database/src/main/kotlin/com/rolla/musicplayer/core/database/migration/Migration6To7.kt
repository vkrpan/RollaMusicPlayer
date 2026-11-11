package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v6 -> v7: custom tags phase 2. Adds `songs.file_tags_generation`, the counter the scanner bumps whenever it resets a
 * row's file-read flag, so the file pass applies a read only if the row was not reset while it read the file.
 *
 * Additive: every existing song starts at generation 0, keeping its read flag, genre and custom tag links; nothing is
 * read again.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Mirrors the v7 schema Room exports (schemas/.../7.json); Room validates the result against it.
        database.execSQL("ALTER TABLE songs ADD COLUMN file_tags_generation INTEGER NOT NULL DEFAULT 0")
    }
}
