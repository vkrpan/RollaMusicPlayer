package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 -> v6: custom tags phase 1. Adds the two custom tag lists (`custom_tags`), their never-reused ID counters
 * (`custom_tag_counters`) and the song-to-tag links (`song_custom_tags`), all empty, plus `songs.genre` and
 * `songs.file_tags_read`.
 *
 * Additive: every existing song gets a null genre and an unread flag, so the next library scan's file pass reads every
 * file once. Playlists, favourites, play counts and the saved queue are untouched.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MIGRATION_5_6_STATEMENTS.forEach(database::execSQL)
    }
}

// Mirrors the v6 schema Room exports (schemas/.../6.json); Room validates the result against it.
private val MIGRATION_5_6_STATEMENTS = listOf(
    "ALTER TABLE songs ADD COLUMN genre TEXT DEFAULT NULL",
    "ALTER TABLE songs ADD COLUMN file_tags_read INTEGER NOT NULL DEFAULT 0",
    """
    CREATE TABLE IF NOT EXISTS custom_tags (
        kind TEXT NOT NULL,
        id INTEGER NOT NULL,
        name TEXT NOT NULL,
        name_key TEXT NOT NULL,
        PRIMARY KEY(kind, id)
    )
    """.trimIndent(),
    "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_tags_kind_name_key ON custom_tags (kind, name_key)",
    """
    CREATE TABLE IF NOT EXISTS custom_tag_counters (
        kind TEXT NOT NULL,
        highest_id INTEGER NOT NULL,
        PRIMARY KEY(kind)
    )
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS song_custom_tags (
        song_id TEXT NOT NULL,
        kind TEXT NOT NULL,
        tag_id INTEGER NOT NULL,
        PRIMARY KEY(song_id, kind, tag_id),
        FOREIGN KEY(song_id) REFERENCES songs(id) ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_song_custom_tags_kind_tag_id ON song_custom_tags (kind, tag_id)",
    "CREATE INDEX IF NOT EXISTS index_song_custom_tags_song_id ON song_custom_tags (song_id)",
)
