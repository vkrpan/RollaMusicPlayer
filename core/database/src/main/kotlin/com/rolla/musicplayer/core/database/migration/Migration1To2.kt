package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds user playlists (playlists + playlist_songs) and play-tracking columns on `songs`
 * (is_favorite, play_count, last_played, date_added) needed by the Phase 2 smart/user playlists feature.
 *
 * Non-destructive: no existing `songs` rows or columns are dropped or altered in place.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        createPlaylistsTable(database)
        createPlaylistSongsTable(database)
        addSongPlayTrackingColumns(database)
    }

    private fun createPlaylistsTable(database: SupportSQLiteDatabase) {
        // New table: user playlists
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createPlaylistSongsTable(database: SupportSQLiteDatabase) {
        // New table: playlist <-> song many-to-many, with ordering
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_songs (
                playlist_id INTEGER NOT NULL,
                song_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY(playlist_id, song_id),
                FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                FOREIGN KEY(song_id) REFERENCES songs(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_playlist_songs_playlist_id ON playlist_songs(playlist_id)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_playlist_songs_song_id ON playlist_songs(song_id)",
        )
    }

    private fun addSongPlayTrackingColumns(database: SupportSQLiteDatabase) {
        // Play-tracking columns on songs
        database.execSQL("ALTER TABLE songs ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE songs ADD COLUMN play_count INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE songs ADD COLUMN last_played INTEGER")

        // date_added: SQLite ALTER TABLE ADD COLUMN only allows a constant default, so backfill in
        // a second statement from date_modified (the only signal available for pre-existing rows).
        database.execSQL("ALTER TABLE songs ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
        database.execSQL("UPDATE songs SET date_added = date_modified")
    }
}
