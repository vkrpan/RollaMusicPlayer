package com.rolla.musicplayer.core.database.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.rolla.musicplayer.core.database.MusicDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Each migration test seeds a whole schema version in raw SQL, then checks every table it touches; splitting one would
// only scatter its fixture. (detekt reaches androidTest since the Phase 6b final fix wave.)
@Suppress("LongMethod")
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

    @Test
    fun migrate1To2_preservesMultiplePreExistingSongRows() {
        // Arrange: a v1 database with three pre-existing song rows (a batch, not just one).
        helper.createDatabase(testDbName, 1).apply {
            (1..3).forEach { index ->
                execSQL(
                    """
                    INSERT INTO songs (
                        id, media_store_id, title, artist, album, album_id, duration_ms,
                        track_number, year, content_uri, artwork_uri, date_modified
                    ) VALUES (
                        '$index', $index, 'Song $index', 'Artist $index', 'Album $index', $index,
                        180000, $index, 2024, 'content://media/external/audio/media/$index',
                        'content://media/external/audio/albumart/$index', 1500000000000
                    )
                    """.trimIndent(),
                )
            }
            close()
        }

        // Act
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        // Assert: all three rows survived the migration untouched.
        migratedDb.query("SELECT COUNT(*) FROM songs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM songs WHERE date_added = date_modified").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("date_added must be backfilled from date_modified for every row", 3, cursor.getInt(0))
        }

        migratedDb.close()
    }

    @Test
    fun migrate1To2_onEmptyDatabase_succeedsWithoutPreExistingRows() {
        // Arrange: a v1 database with no song rows at all — the migration must not assume any
        // pre-existing data is present.
        helper.createDatabase(testDbName, 1).apply { close() }

        // Act
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        // Assert
        migratedDb.query("SELECT COUNT(*) FROM songs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM playlists").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        migratedDb.close()
    }

    @Test
    fun migrate1To2_createsIndicesOnPlaylistSongsTable() {
        // Arrange
        helper.createDatabase(testDbName, 1).apply { close() }

        // Act
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        // Assert: both indices declared on PlaylistSongCrossRef (playlist_id, song_id) were
        // actually created by the migration SQL, not just present in the entity annotation.
        val indexNames = mutableSetOf<String>()
        migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'playlist_songs'",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
        }

        assertTrue(
            "index_playlist_songs_playlist_id must exist after migration, found: $indexNames",
            indexNames.contains("index_playlist_songs_playlist_id"),
        )
        assertTrue(
            "index_playlist_songs_song_id must exist after migration, found: $indexNames",
            indexNames.contains("index_playlist_songs_song_id"),
        )

        migratedDb.close()
    }

    @Test
    fun migrate1To2_declaresCascadingForeignKeysOnPlaylistSongsTable() {
        // Arrange
        helper.createDatabase(testDbName, 1).apply { close() }

        // Act
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        // Assert: both foreign keys (playlist_id -> playlists.id, song_id -> songs.id) exist with
        // ON DELETE CASCADE, matching PlaylistSongCrossRef's @ForeignKey declarations.
        val cascadeTargets = mutableSetOf<String>()
        migratedDb.query("PRAGMA foreign_key_list(playlist_songs)").use { cursor ->
            while (cursor.moveToNext()) {
                val table = cursor.getString(cursor.getColumnIndexOrThrow("table"))
                val onDelete = cursor.getString(cursor.getColumnIndexOrThrow("on_delete"))
                if (onDelete.equals("CASCADE", ignoreCase = true)) {
                    cascadeTargets += table
                }
            }
        }

        assertTrue("playlists FK must cascade on delete, found: $cascadeTargets", cascadeTargets.contains("playlists"))
        assertTrue("songs FK must cascade on delete, found: $cascadeTargets", cascadeTargets.contains("songs"))

        migratedDb.close()
    }

    @Test
    fun migrate2To3_preservesExistingSongsPlaylistsAndCrossRefs_andAddsEqualizerPresetsTable() {
        // Arrange: a v2 database with a pre-existing song, playlist, and cross-ref row.
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                """
                INSERT INTO songs (
                    id, media_store_id, title, artist, album, album_id, duration_ms,
                    track_number, year, content_uri, artwork_uri, date_modified,
                    is_favorite, play_count, last_played, date_added
                ) VALUES (
                    '1', 1, 'Test Song', 'Test Artist', 'Test Album', 10, 180000,
                    1, 2024, 'content://media/external/audio/media/1',
                    'content://media/external/audio/albumart/10', 1500000000000,
                    0, 0, NULL, 1500000000000
                )
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'My Playlist', 1000, 1000)",
            )
            execSQL(
                """
                INSERT INTO playlist_songs (playlist_id, song_id, position, added_at)
                VALUES (1, '1', 0, 2000)
                """.trimIndent(),
            )
            close()
        }

        // Act: migrate to v3.
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_2_3)

        // Assert: pre-existing rows across all three v2 tables survived untouched.
        migratedDb.query("SELECT * FROM songs WHERE id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Song", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        }
        migratedDb.query("SELECT * FROM playlists WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("My Playlist", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        migratedDb.query("SELECT * FROM playlist_songs WHERE playlist_id = 1 AND song_id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("position")))
        }

        // Assert: the new equalizer_presets table exists and accepts inserts matching the entity's
        // exact column set.
        migratedDb.execSQL(
            """
            INSERT INTO equalizer_presets (id, name, is_custom, gains_millibel, created_at)
            VALUES (1, 'Rock', 0, '300,200,100,0,-100,-200,0,100', 5000)
            """.trimIndent(),
        )
        migratedDb.query("SELECT * FROM equalizer_presets WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Rock", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("is_custom")))
            assertEquals(
                "300,200,100,0,-100,-200,0,100",
                cursor.getString(cursor.getColumnIndexOrThrow("gains_millibel")),
            )
            assertEquals(5000L, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
        }

        migratedDb.close()
    }

    @Test
    fun migrate2To3_onEmptyDatabase_succeedsAndEqualizerPresetsTableStartsEmpty() {
        // Arrange: a v2 database with no rows at all — the migration must not assume any
        // pre-existing data is present.
        helper.createDatabase(testDbName, 2).apply { close() }

        // Act
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_2_3)

        // Assert
        migratedDb.query("SELECT COUNT(*) FROM equalizer_presets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        migratedDb.close()
    }

    @Test
    fun migrate1To3_allMigrationsChain_preservesPreExistingSongAndCreatesAllNewTables() {
        // Arrange: a v1 database (pre-playlists, pre-equalizer) with one pre-existing song row.
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

        // Act: run the full migration chain 1 -> 2 -> 3 in one call.
        val migratedDb = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        // Assert: the original song survived both migrations, with play-tracking defaults applied,
        // and every table introduced across the chain exists and is empty/queryable.
        migratedDb.query("SELECT * FROM songs WHERE id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Song", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")))
            assertEquals(1500000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("date_added")))
        }
        migratedDb.query("SELECT COUNT(*) FROM playlists").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM playlist_songs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM equalizer_presets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        migratedDb.close()
    }

    // Review Focus 2. Mutations: (a) `ADD COLUMN folder_path TEXT DEFAULT ''` → validation fails (default '' vs NULL)
    // and isNull is false → RED; (b) `ADD COLUMN folder_path INTEGER` → validation fails → RED; (c) a migration that
    // recreates `songs` without copying rows → the row is missing → RED.
    @Test
    fun migrate3To4_addsANullFolderPathAndKeepsSongsUserStateAndPlaylists() {
        helper.createDatabase(testDbName, 3).apply {
            execSQL(
                """
                INSERT INTO songs (
                    id, media_store_id, title, artist, album, album_id, duration_ms, track_number, year,
                    content_uri, artwork_uri, date_modified, date_added, is_favorite, play_count, last_played
                ) VALUES (
                    '1', 1, 'Test Song', 'Test Artist', 'Test Album', 10, 180000, 1, 2024,
                    'content://media/external/audio/media/1', 'content://media/external/audio/albumart/10',
                    1500000000000, 1400000000000, 1, 7, 1600000000000
                )
                """.trimIndent(),
            )
            execSQL("INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'Road trip', 1000, 1000)")
            execSQL("INSERT INTO playlist_songs (playlist_id, song_id, position, added_at) VALUES (1, '1', 0, 2000)")
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(testDbName, 4, true, MIGRATION_3_4)

        migratedDb.query("SELECT * FROM songs WHERE id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Song", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(1400000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("date_added")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")))
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("play_count")))
            assertEquals(1600000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("last_played")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("folder_path")))
        }
        migratedDb.query("SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = 1 AND song_id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migratedDb.close()
    }

    // Mutations: (a) drop the playback_state CREATE → validation fails (missing table) → RED; (b) `song_id TEXT`
    // without NOT NULL → validation fails (nullability) → RED; (c) a migration that also runs `DELETE FROM songs` →
    // the song row is missing → RED; (d) a migration that also runs `UPDATE songs SET is_favorite = 0` → is_favorite
    // is 0 → RED; (e) a migration that also runs `DELETE FROM equalizer_presets` → the preset count is 0 → RED.
    @Test
    fun migrate4To5_createsEmptyPlaybackTablesAndKeepsEverything() {
        helper.createDatabase(testDbName, 4).apply {
            execSQL(
                """
                INSERT INTO songs (
                    id, media_store_id, title, artist, album, album_id, duration_ms, track_number, year,
                    content_uri, artwork_uri, date_modified, date_added, is_favorite, play_count, last_played,
                    folder_path
                ) VALUES (
                    '1', 1, 'Test Song', 'Test Artist', 'Test Album', 10, 180000, 1, 2024,
                    'content://media/external/audio/media/1', 'content://media/external/audio/albumart/10',
                    1500000000000, 1400000000000, 1, 7, 1600000000000, 'Music'
                )
                """.trimIndent(),
            )
            execSQL("INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'Road trip', 1000, 1000)")
            execSQL("INSERT INTO playlist_songs (playlist_id, song_id, position, added_at) VALUES (1, '1', 0, 2000)")
            execSQL(
                """
                INSERT INTO equalizer_presets (id, name, is_custom, gains_millibel, created_at)
                VALUES (1, 'Rock', 1, '300,200,100,0,-100,-200,0,100,200', 5000)
                """.trimIndent(),
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(testDbName, 5, true, MIGRATION_4_5)

        migratedDb.query("SELECT title, play_count, folder_path, is_favorite FROM songs WHERE id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Song", cursor.getString(0))
            assertEquals(7, cursor.getInt(1))
            assertEquals("Music", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
        }
        migratedDb.query("SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = 1 AND song_id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM equalizer_presets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        for (table in listOf("playback_queue", "playback_state")) {
            migratedDb.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(table, 0, cursor.getInt(0))
            }
        }
        migratedDb.close()
    }

    // Review Focus 2: the list DatabaseModule installs opens a v1 file at the current version. Mutation: drop
    // MIGRATION_4_5 from ALL_MIGRATIONS → "A migration from 1 to 5 was required but not found" → RED.
    @Test
    fun allMigrations_openAVersion1DatabaseAtTheCurrentVersion() {
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

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MusicDatabase::class.java,
            testDbName,
        ).addMigrations(*ALL_MIGRATIONS).build()
        helper.closeWhenFinished(database)

        val query = "SELECT title, folder_path FROM songs WHERE id = '1'"
        database.openHelper.writableDatabase.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Song", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM playback_queue").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
