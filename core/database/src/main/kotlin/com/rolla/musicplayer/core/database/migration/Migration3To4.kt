package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 -> v4: adds `songs.folder_path` for the Folders tab (redesign Phase 4b, spec §9.4).
 *
 * Purely additive. Every existing row gets NULL, which means "not scanned yet": the next LibraryIndexer.sync() fills
 * it from MediaStore. No other table or column is touched, so playlists, favourites and play counts carry over as
 * they are.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE songs ADD COLUMN folder_path TEXT DEFAULT NULL")
    }
}
