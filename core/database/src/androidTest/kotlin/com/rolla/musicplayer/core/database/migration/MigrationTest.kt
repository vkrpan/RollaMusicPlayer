package com.rolla.musicplayer.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.rolla.musicplayer.core.database.MusicDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MusicDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesExistingSongRowAndAddsPlaylistTablesAndColumns() {
        // Arrange: create a v1 database and insert a song using the exact v1 SongEntity column set.
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO songs (
                    id, media_store_id, title, artist, album, album_id, duration_ms,
                    track_number, year, content_uri, artwork_uri, date_modified
                ) VALUES (
                    '1', 1, 'Test Song', 'Test Artist', 'Test Album', 10, 180000,
                    1, 2024, 'content://media/external/audio/media/1',
                    'content://media/external/audio/albumart/10', 1500000000000
                )
                """.trimIndent(),
            )
            close()
        }

        // Act: migrate to v2.
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        // Assert: the pre-existing song row survived with all its original data intact.
        migratedDb.query("SELECT * FROM songs WHERE id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Song", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals("Test Artist", cursor.getString(cursor.getColumnIndexOrThrow("artist")))
            assertEquals("Test Album", cursor.getString(cursor.getColumnIndexOrThrow("album")))
            assertEquals(10L, cursor.getLong(cursor.getColumnIndexOrThrow("album_id")))
            assertEquals(180000L, cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("track_number")))
            assertEquals(2024, cursor.getInt(cursor.getColumnIndexOrThrow("year")))
            assertEquals(
                "content://media/external/audio/media/1",
                cursor.getString(cursor.getColumnIndexOrThrow("content_uri")),
            )
            assertEquals(1500000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("date_modified")))

            // New columns exist with the expected defaults.
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("play_count")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("last_played")))

            // date_added was backfilled from date_modified for this pre-existing row.
            assertEquals(1500000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("date_added")))
        }

        // New tables exist and accept inserts.
        migratedDb.execSQL(
            "INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'My Playlist', 1000, 1000)",
        )
        migratedDb.execSQL(
            """
            INSERT INTO playlist_songs (playlist_id, song_id, position, added_at)
            VALUES (1, '1', 0, 2000)
            """.trimIndent(),
        )

        migratedDb.query("SELECT * FROM playlists WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("My Playlist", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        migratedDb.query("SELECT * FROM playlist_songs WHERE playlist_id = 1 AND song_id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("position")))
        }

        migratedDb.close()
    }
}
