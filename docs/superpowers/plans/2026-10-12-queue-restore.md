# Queue Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the app process dies, opening the app shows the last queue, track, position, shuffle order and repeat mode, paused. A headset, Bluetooth or System UI Play resumes the same queue without opening the app.

**Architecture:**
- **Data:** Room v5 adds `playback_queue` (one row per queue slot) and a single-row `playback_state`.
  - `PlaybackQueueDao` writes the queue atomically and loads it with a `LEFT JOIN songs`, so tracks deleted since the save are still visible as missing.
  - `PlaybackStateRepository` (`:core:data`) turns the rows into a `SavedPlayback` (`:core:model`). It renumbers survivors and picks the next track when the current one is gone.
- **Save:** `QueuePersister`, a `Player.Listener` in `:core:media`, snapshots the player on the main thread. It writes through the repository from `serviceScope`, coalescing bursts into one write.
- **Restore:**
  - `RestoreCoordinator` loads once per service start. It applies the result to the player paused, unless something else set a queue first or a system resumption took it.
  - `PlaybackSessionCallback.onPlaybackResumption` serves Media3's resumption requests.
  - `RestoredShuffleOrder`, registered after `StartFirstShuffleEnforcer`, puts the exact saved shuffle order back.
  - The manifest gains Media3's `MediaButtonReceiver`.

**Tech Stack:** Kotlin 2.0.21, Media3 1.4.1 (ExoPlayer, session), Room 2.6.1 (KSP, exported schemas), Hilt, kotlinx-coroutines (test: `runTest`, `StandardTestDispatcher`), JUnit4 + MockK, Robolectric 4.13 with a real `ExoPlayer` (the `QueueAndShuffleExoPlayerTest` pattern), instrumented Room tests on the Samsung SM-S901B.

**Spec:** [`docs/superpowers/specs/2026-10-12-queue-restore-design.md`](../specs/2026-10-12-queue-restore-design.md). Read §2 (success criteria), §5 (data), §6 (save), §7 (restore), §8 (errors) and §9 (tests). Rulings R1–R8 below amend it; Task 7 writes the amendments into the spec.

## Global Constraints

- minSdk 24, targetSdk 34. Offline only: no network APIs or dependencies, and no new library. Media3 1.4.1 and Room 2.6.1 already provide everything.
- Room migrations are additive and tested. `fallbackToDestructiveMigration` must never be added. User data (playlists, favourites, play counts, Equaliser presets, folders) survives every migration.
- The model name is `Song`, never `Track`. This feature adds no UI copy and no visual values.
- `:core:testing` stays `testImplementation` only.
- Restore never starts playback by itself. Only a user's Play (in the app, on the widget, a headset or the System UI) does.
- Background work in `PlaybackService` never takes playback down: persistence failures are logged and dropped (`Log.e`), and `CancellationException` is always rethrown.
- **Mutation-proof policy.**
  - Every new test names the mutation that turns it RED. The implementer applies it, sees RED, quotes the decisive failure line in the report, and reverts byte-exact.
  - A mutant counts as equivalent only if *no observable output changes*. If the output changes but the test can't see it, the test is blind: move its probe.
  - A named mutation is a hypothesis about the framework. If it proves equivalent, show the evidence and substitute the closest fail-able mutation. An equivalence argument that leans on another component (Media3's event order, Room's codegen) must show that component's actual behaviour, not assume it.
  - Fixtures separate the quantities a test distinguishes: slot order ≠ shuffle order ≠ title order ≠ insertion order, and a saved position ≠ 0.
- Code blocks in this plan were written against the real files but never compiled. Implementers may make minimal mechanical fixes (imports, an `@OptIn`, a named argument) and must list each one in the report. A change of behaviour is not a mechanical fix: report it as a concern.
- **Agents never** run `git commit`, `git add` or `git stash`. Never `sed -i` (Spotless enforces CRLF; use the editor). Run `./gradlew spotlessApply` after edits. Don't write Kotlin through Bash heredocs.
- Instrumented (androidTest) code must compile here (`./gradlew :core:database:compileDebugAndroidTestKotlin`). It runs on the device in Task 9, by the controller only. List each androidTest mutation under "Device mutations" in the report, and do not apply them.
- detekt: `LongMethod` 30 lines, `TooManyFunctions` 15 per class and 12 per interface, `LongParameterList` 6. Prefer extracting a function over adding a `@Suppress`; any suppression needs a one-line reason.

## Rulings (decided at planning; Task 7 amends the spec)

1. **R1: no `ForwardingPlayer` gate (amends spec §7.3).** Media3 1.4.1's `MediaSessionImpl.handleMediaControllerPlayRequest` checks for a current media item. With none, it calls `Callback.onPlaybackResumption`, sets the returned items and plays. `MediaSessionStub` (our own `MediaController`, which means the app and the widget) and `MediaSessionLegacyStub` (media buttons, System UI) both route Play through it.
   - So a Play that arrives while the restore is loading already goes to `onPlaybackResumption`. That awaits the same first load (R6).
   - Cost if wrong: a widget Play right after a kill needs a second tap. Task 9 step 6 checks it on the device.
2. **R2: no foreign key on `playback_queue`; the load uses a `LEFT JOIN` (amends spec §5.1, §5.3, §7.4).** With `ON DELETE CASCADE`, a deleted current song loses its row and its shuffle rank, so §7.4's "next surviving song in playback order" can't be computed under shuffle. Without the foreign key, the missing song's slot and rank stay readable, and `LEFT JOIN` marks it missing (`song == null`).
   - Rows for deleted songs live only until the next queue write.
   - This also removes the dependence on `songs` being updated in place: there is nothing to cascade.
   - There is no `song_id` index: nothing joins *to* the queue.
3. **R3: the order of applying a restore (amends spec §7.2).** `arm(shuffleOrder)` → `repeatMode` → `shuffleModeEnabled` → `setMediaItems(items, index, position)` → `prepare()`.
   - Turning shuffle on while the player is empty makes the enforcer's shuffle-on trigger a no-op.
   - `setMediaItems` then fires `PLAYLIST_CHANGED`. The enforcer reshuffles first, and `RestoredShuffleOrder`, registered after it, replaces that order with the saved one.
4. **R4: the model's shape (amends spec §5.4).** `SavedPlayback(songs, shuffleOrder, state: SavedPlaybackState)`, and `saveQueue(songIds, shuffleOrder, state)`.
   - The persister only has media ids, not `Song`s.
   - Splitting out `SavedPlaybackState` keeps every signature under detekt's `LongParameterList`.
5. **R5: state saves skip an empty player.** Only a timeline change to empty enqueues a clear (spec §6.3). A state write with no queue would describe nothing.
6. **R6: resumption reloads once the restore has settled.** The cached first load serves only a resumption that arrives while it is still `LOADING`. After that, `onPlaybackResumption` reads the database again, so a queue cleared later is not resurrected.
7. **R7: the final save is bounded.** `onDestroy` writes the last state inside `runBlocking { withTimeoutOrNull(500) { … } }`, next to the existing bounded `runBlocking` there.
8. **R8: the load is one transaction and a pure function.** `PlaybackQueueDao.loadSnapshot()` reads the queue and the state in one `@Transaction`. `restoredPlayback(rows, state)` is a pure `internal` function in `:core:data` that unit tests call directly.
9. **R9: the persister has no `onMediaItemTransition` override (amends spec §6.1).** Every media-item transition arrives with either a position discontinuity (a seek, an auto-advance or a repeat) or a timeline change (a playlist change), and both already write.
   - The override could never be pinned by a test: deleting it is an equivalent mutant.
   - Cost if wrong: a transition with neither event would save its state at the next pause, discontinuity or 10-second tick instead.

## Review Focus

1. **Duplicate songs in a queue** (the same track queued twice): both entries survive save and restore, because the slot is the key, not the song id. Pinned in Task 1 (`loadQueue_keepsDuplicateSongsInTheirOwnSlots`) and Task 2 (`duplicateSongsKeepBothSlots`).
2. **A very large queue** (5,000 tracks from Shuffle on a big library): one coalesced write with 5,000 ids, not one per callback. Pinned in Task 4 (`aFiveThousandTrackQueueIsOneWrite`).
3. **Tracks deleted while the app was dead, including the current one:** survivors are renumbered, keep their relative shuffle order, and playback moves to the next survivor in playback order, at 0:00. Pinned in Task 2 (four tests).
4. **The user starts something before the restore lands:** their queue wins, and the saved one is discarded, not merged. Pinned in Task 5 (`discardsWhenAQueueArrivedFirst`).
5. **A corrupt saved row** (an unknown `repeat_mode` string, or duplicate shuffle ranks): the restore still produces a valid permutation and falls back to `RepeatMode.OFF`, never a crash. Pinned in Task 2 (`unknownRepeatModeFallsBackToOff`, `duplicateRanksStillGiveAPermutation`).

## File map

| File | Task | Change |
|---|---|---|
| `core/database/.../entity/PlaybackQueueEntity.kt` | 1 | Create |
| `core/database/.../entity/PlaybackStateEntity.kt` | 1 | Create |
| `core/database/.../relation/QueuedSongRow.kt` | 1 | Create (`QueuedSongRow`, `PlaybackSnapshot`) |
| `core/database/.../dao/PlaybackQueueDao.kt` | 1 | Create |
| `core/database/.../migration/Migration4To5.kt` | 1 | Create |
| `core/database/.../migration/Migrations.kt` | 1 | Append `MIGRATION_4_5` |
| `core/database/.../MusicDatabase.kt` | 1 | Two entities, `version = 5`, `playbackQueueDao()` |
| `core/database/.../di/DatabaseModule.kt` | 1 | `providePlaybackQueueDao`, comment |
| `core/database/schemas/.../5.json` | 1 | Generated |
| `core/database/src/androidTest/.../migration/MigrationTest.kt` | 1 | 4→5 test, chain test |
| `core/database/src/androidTest/.../dao/PlaybackQueueDaoTest.kt` | 1 | Create |
| `core/model/.../SavedPlayback.kt` | 2 | Create |
| `core/data/.../repository/PlaybackStateRepository.kt` | 2 | Create |
| `core/data/.../repository/PlaybackStateRepositoryImpl.kt` | 2 | Create |
| `core/data/.../repository/RestoredPlayback.kt` | 2 | Create (pure `restoredPlayback`) |
| `core/data/.../di/DataModule.kt` | 2 | Bind |
| `core/data/src/test/.../repository/RestoredPlaybackTest.kt` | 2 | Create |
| `core/data/src/test/.../repository/PlaybackStateRepositoryImplTest.kt` | 2 | Create |
| `core/testing/.../FakePlaybackStateRepository.kt` | 2 | Create |
| `core/media/.../MediaItems.kt` | 3 | Create (`Song.toMediaItem()`, repeat-mode mapping) |
| `core/media/.../PlaybackController.kt` | 3 | Use `toMediaItem()` |
| `core/media/.../RestoredShuffleOrder.kt` | 3 | Create |
| `core/media/src/test/.../MediaItemsTest.kt` | 3 | Create |
| `core/media/src/test/.../RestoredShuffleOrderTest.kt` | 3 | Create |
| `core/media/.../QueuePersister.kt` | 4 | Create (with `PendingWrite`) |
| `core/media/src/test/.../QueuePersisterTest.kt` | 4 | Create |
| `core/media/.../RestoreCoordinator.kt` | 5 | Create |
| `core/media/.../PlaybackSessionCallback.kt` | 5 | Create |
| `core/media/src/test/.../RestoreCoordinatorTest.kt` | 5 | Create |
| `core/media/src/test/.../PlaybackSessionCallbackTest.kt` | 5 | Create |
| `core/media/.../PlaybackService.kt` | 6 | Wire everything |
| `core/media/src/main/AndroidManifest.xml` | 6 | Receiver and the service's `MEDIA_BUTTON` action |
| `core/media/src/test/.../MediaButtonManifestTest.kt` | 6 | Create |
| Spec, CLAUDE.md, `.claude/rules/model-vocabulary.md` | 7 | Controller |

`...` is `src/main/kotlin/com/rolla/musicplayer/core/<module>` (tests: `src/test/kotlin/...`, `src/androidTest/kotlin/...`).

---

### Task 1: Room v5: the playback tables, migration and `PlaybackQueueDao` (data-layer-agent)

**Files:**
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/PlaybackQueueEntity.kt`
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/PlaybackStateEntity.kt`
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/relation/QueuedSongRow.kt`
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/PlaybackQueueDao.kt`
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/migration/Migration4To5.kt`
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/migration/Migrations.kt`
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/MusicDatabase.kt`
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/di/DatabaseModule.kt`
- Generated: `core/database/schemas/com.rolla.musicplayer.core.database.MusicDatabase/5.json`
- Test (androidTest): `core/database/src/androidTest/kotlin/com/rolla/musicplayer/core/database/migration/MigrationTest.kt`
- Test (androidTest): `core/database/src/androidTest/kotlin/com/rolla/musicplayer/core/database/dao/PlaybackQueueDaoTest.kt`

**Interfaces:**
- Consumes: `SongEntity` (unchanged), `ALL_MIGRATIONS`, `MusicDatabase`.
- Produces:
  - `PlaybackQueueEntity(slot: Int, songId: String, shuffleRank: Int)`
  - `PlaybackStateEntity(id: Int = PlaybackStateEntity.SINGLE_ROW_ID, currentSlot: Int, positionMs: Long, shuffleEnabled: Boolean, repeatMode: String, savedAt: Long)`
  - `QueuedSongRow(slot: Int, shuffleRank: Int, songId: String, song: SongEntity?)`
  - `PlaybackSnapshot(queue: List<QueuedSongRow>, state: PlaybackStateEntity?)`
  - `PlaybackQueueDao`: `loadQueue()`, `loadState()`, `loadSnapshot()`, `upsertState(state)`, `replaceQueue(rows, state)`, `clear()`, all `suspend`
  - `MIGRATION_4_5`; `MusicDatabase.playbackQueueDao()`

- [ ] **Step 1: The entities and the relation**

`PlaybackQueueEntity.kt`:

```kotlin
package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved queue entry (queue-restore spec §5.1). [slot] is the Media3 window index, so the same song can sit in two
 * slots. [shuffleRank] is the slot's position in the shuffled playback order. There is deliberately no foreign key to
 * `songs` (ruling R2): a song deleted since the save must stay readable as "missing", with its rank, so the restore can
 * find the next survivor in playback order.
 */
@Entity(tableName = "playback_queue")
data class PlaybackQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "slot")
    val slot: Int,

    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "shuffle_rank")
    val shuffleRank: Int,
)
```

`PlaybackStateEntity.kt`:

```kotlin
package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The single saved playback-state row (queue-restore spec §5.1). [repeatMode] is a `RepeatMode` enum name. */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLE_ROW_ID,

    @ColumnInfo(name = "current_slot")
    val currentSlot: Int,

    @ColumnInfo(name = "position_ms")
    val positionMs: Long,

    @ColumnInfo(name = "shuffle_enabled")
    val shuffleEnabled: Boolean,

    @ColumnInfo(name = "repeat_mode")
    val repeatMode: String,

    @ColumnInfo(name = "saved_at")
    val savedAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
```

`relation/QueuedSongRow.kt`:

```kotlin
package com.rolla.musicplayer.core.database.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.SongEntity

/** A saved queue slot with its song, or `song == null` when the song is no longer in the library (ruling R2). */
data class QueuedSongRow(
    @ColumnInfo(name = "slot")
    val slot: Int,

    @ColumnInfo(name = "shuffle_rank")
    val shuffleRank: Int,

    @ColumnInfo(name = "queued_song_id")
    val songId: String,

    @Embedded
    val song: SongEntity?,
)

/** The saved queue and state, read in one transaction (ruling R8). */
data class PlaybackSnapshot(
    val queue: List<QueuedSongRow>,
    val state: PlaybackStateEntity?,
)
```

- [ ] **Step 2: The DAO**

`dao/PlaybackQueueDao.kt`:

```kotlin
package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.relation.PlaybackSnapshot
import com.rolla.musicplayer.core.database.relation.QueuedSongRow

/**
 * The saved playback queue and state (queue-restore spec §5.3). The insert and delete members exist so the
 * `@Transaction` default bodies below can compose them; callers use [replaceQueue], [upsertState], [loadSnapshot] and
 * [clear].
 */
@Dao
interface PlaybackQueueDao {

    /** Every saved slot in slot order, each with its song, or a null song when the song has left the library. */
    @Query(
        """
        SELECT q.slot AS slot, q.shuffle_rank AS shuffle_rank, q.song_id AS queued_song_id, s.*
        FROM playback_queue AS q
        LEFT JOIN songs AS s ON s.id = q.song_id
        ORDER BY q.slot ASC
        """,
    )
    suspend fun loadQueue(): List<QueuedSongRow>

    @Query("SELECT * FROM playback_state WHERE id = ${PlaybackStateEntity.SINGLE_ROW_ID}")
    suspend fun loadState(): PlaybackStateEntity?

    @Upsert
    suspend fun upsertState(state: PlaybackStateEntity)

    @Insert
    suspend fun insertQueue(rows: List<PlaybackQueueEntity>)

    @Query("DELETE FROM playback_queue")
    suspend fun deleteQueue()

    @Query("DELETE FROM playback_state")
    suspend fun deleteState()

    /** Replaces the whole queue and the state atomically: a failed insert leaves the previous save intact. */
    @Transaction
    suspend fun replaceQueue(rows: List<PlaybackQueueEntity>, state: PlaybackStateEntity) {
        deleteQueue()
        insertQueue(rows)
        upsertState(state)
    }

    @Transaction
    suspend fun loadSnapshot(): PlaybackSnapshot = PlaybackSnapshot(loadQueue(), loadState())

    @Transaction
    suspend fun clear() {
        deleteQueue()
        deleteState()
    }
}
```

- [ ] **Step 3: The migration**

`migration/Migration4To5.kt`:

```kotlin
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
```

`Migrations.kt`: change the array to `arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`.

- [ ] **Step 4: Register the database changes**

In `MusicDatabase.kt`:
- Add `PlaybackQueueEntity::class` and `PlaybackStateEntity::class` to `entities`.
- Set `version = 5`.
- Add, after `folderDao()`:

```kotlin
    // The saved playback queue and state (queue restore). Comes with a schema change: the v5 tables, added by
    // MIGRATION_4_5 (see PlaybackQueueDao's KDoc).
    abstract fun playbackQueueDao(): PlaybackQueueDao
```

In `DatabaseModule.kt`:
- Add `@Provides fun providePlaybackQueueDao(database: MusicDatabase): PlaybackQueueDao = database.playbackQueueDao()`.
- Fix the `SpreadOperator` comment's stale count. It becomes: `// addMigrations is vararg-only; the spread copies the small ALL_MIGRATIONS array once per process, at database creation.`

Run: `./gradlew :core:database:compileDebugKotlin`. Expected: BUILD SUCCESSFUL, and `core/database/schemas/com.rolla.musicplayer.core.database.MusicDatabase/5.json` exists.

Then check `5.json` against the migration SQL:
- `playback_queue`: three NOT NULL columns, primary key `slot`, no foreign keys, no indices.
- `playback_state`: six NOT NULL columns, primary key `id`.

If Room generated anything different (a default value, an affinity), make the migration SQL match the generated schema, and say so in the report.

- [ ] **Step 5: The migration tests**

In `MigrationTest.kt`, add after `migrate3To4_…`:

```kotlin
    // Mutations: (a) drop the playback_state CREATE → validation fails (missing table) → RED; (b) `song_id TEXT` without
    // NOT NULL → validation fails (nullability) → RED; (c) a migration that also runs `DELETE FROM songs` → the song row
    // is missing → RED.
    @Test
    fun migrate4To5_createsEmptyPlaybackTablesAndKeepsEverything() {
        helper.createDatabase(testDbName, 4).apply {
            execSQL(
                """
                INSERT INTO songs (
                    id, media_store_id, title, artist, album, album_id, duration_ms, track_number, year,
                    content_uri, artwork_uri, date_modified, date_added, is_favorite, play_count, last_played, folder_path
                ) VALUES (
                    '1', 1, 'Test Song', 'Test Artist', 'Test Album', 10, 180000, 1, 2024,
                    'content://media/external/audio/media/1', 'content://media/external/audio/albumart/10',
                    1500000000000, 1400000000000, 1, 7, 1600000000000, 'Music'
                )
                """.trimIndent(),
            )
            execSQL("INSERT INTO playlists (id, name, created_at, updated_at) VALUES (1, 'Road trip', 1000, 1000)")
            execSQL("INSERT INTO playlist_songs (playlist_id, song_id, position, added_at) VALUES (1, '1', 0, 2000)")
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(testDbName, 5, true, MIGRATION_4_5)

        migratedDb.query("SELECT title, play_count, folder_path FROM songs WHERE id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Song", cursor.getString(0))
            assertEquals(7, cursor.getInt(1))
            assertEquals("Music", cursor.getString(2))
        }
        migratedDb.query("SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = 1 AND song_id = '1'").use { cursor ->
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
```

In `allMigrations_openAVersion1DatabaseAtTheCurrentVersion`:
- Update its comment's mutation to: `drop MIGRATION_4_5 from ALL_MIGRATIONS → "A migration from 1 to 5 was required but not found" → RED`.
- Append at the end of the test:

```kotlin
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM playback_queue").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
```

- [ ] **Step 6: The DAO tests**

`dao/PlaybackQueueDaoTest.kt`:

```kotlin
package com.rolla.musicplayer.core.database.dao

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.SongEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var songDao: SongDao
    private lateinit var dao: PlaybackQueueDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        dao = database.playbackQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(id: String, title: String): SongEntity = SongEntity(
        id = id,
        mediaStoreId = id.hashCode().toLong(),
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 180_000L,
        trackNumber = 1,
        year = 2024,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "art-$id",
        dateModified = 1_000_000L,
        folderPath = "Music",
    )

    private fun state(currentSlot: Int, positionMs: Long, repeatMode: String = "ALL") = PlaybackStateEntity(
        currentSlot = currentSlot,
        positionMs = positionMs,
        shuffleEnabled = true,
        repeatMode = repeatMode,
        savedAt = 5_000L,
    )

    // Fixture: slot order (zulu, gone, alpha) ≠ title order ≠ insertion order (slot 2, 0, 1). Mutations:
    // (a) LEFT JOIN → JOIN → the missing slot disappears (size 2) → RED; (b) ORDER BY q.slot → ORDER BY s.title → the
    // order changes → RED; (c) select `q.song_id AS queued_song_id` from `s.id` instead → the missing row's songId is
    // null/"" → RED.
    @Test
    fun loadQueue_returnsEverySlotInSlotOrderAndMarksMissingSongs() = runTest {
        songDao.upsertSongs(listOf(song("z", "Zulu"), song("a", "Alpha")))
        dao.replaceQueue(
            listOf(
                PlaybackQueueEntity(slot = 2, songId = "a", shuffleRank = 1),
                PlaybackQueueEntity(slot = 0, songId = "z", shuffleRank = 2),
                PlaybackQueueEntity(slot = 1, songId = "gone", shuffleRank = 0),
            ),
            state(currentSlot = 0, positionMs = 83_000L),
        )

        val rows = dao.loadQueue()

        assertEquals(listOf(0, 1, 2), rows.map { it.slot })
        assertEquals(listOf(2, 0, 1), rows.map { it.shuffleRank })
        assertEquals(listOf("z", "gone", "a"), rows.map { it.songId })
        assertEquals(listOf("Zulu", null, "Alpha"), rows.map { it.song?.title })
    }

    // Review Focus 1. Mutation: make song_id the primary key (and slot a plain column) → the second insert conflicts
    // → RED.
    @Test
    fun loadQueue_keepsDuplicateSongsInTheirOwnSlots() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha")))
        dao.replaceQueue(
            listOf(
                PlaybackQueueEntity(slot = 0, songId = "a", shuffleRank = 1),
                PlaybackQueueEntity(slot = 1, songId = "a", shuffleRank = 0),
            ),
            state(currentSlot = 1, positionMs = 1_000L),
        )

        assertEquals(listOf("Alpha", "Alpha"), dao.loadQueue().map { it.song?.title })
    }

    // Mutation: drop deleteQueue() from replaceQueue → slot 2 of the longer first queue survives → RED.
    @Test
    fun replaceQueue_replacesTheWholeQueueAndTheState() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha"), song("b", "Bravo"), song("c", "Charlie")))
        dao.replaceQueue(
            listOf(
                PlaybackQueueEntity(0, "a", 0),
                PlaybackQueueEntity(1, "b", 1),
                PlaybackQueueEntity(2, "c", 2),
            ),
            state(currentSlot = 2, positionMs = 10L),
        )

        dao.replaceQueue(
            listOf(PlaybackQueueEntity(0, "c", 1), PlaybackQueueEntity(1, "a", 0)),
            state(currentSlot = 1, positionMs = 20L, repeatMode = "ONE"),
        )

        assertEquals(listOf("c", "a"), dao.loadQueue().map { it.songId })
        assertEquals(state(currentSlot = 1, positionMs = 20L, repeatMode = "ONE"), dao.loadState())
    }

    // Mutation: remove @Transaction from replaceQueue → the delete commits before the failing insert, so the first queue
    // is gone → RED.
    @Test
    fun replaceQueue_isAtomic_aFailedInsertKeepsThePreviousSave() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha"), song("b", "Bravo")))
        dao.replaceQueue(listOf(PlaybackQueueEntity(0, "a", 0)), state(currentSlot = 0, positionMs = 10L))

        try {
            dao.replaceQueue(
                listOf(PlaybackQueueEntity(0, "b", 0), PlaybackQueueEntity(0, "a", 1)),
                state(currentSlot = 0, positionMs = 99L),
            )
            fail("A duplicate slot must fail the insert")
        } catch (expected: SQLiteConstraintException) {
            // The transaction rolled back.
        }

        assertEquals(listOf("a"), dao.loadQueue().map { it.songId })
        assertEquals(10L, dao.loadState()?.positionMs)
    }

    // Mutation: `@Upsert` → `@Insert` on upsertState → the second call conflicts on id 0 → RED.
    @Test
    fun upsertState_keepsASingleRow() = runTest {
        dao.upsertState(state(currentSlot = 3, positionMs = 1L))
        dao.upsertState(state(currentSlot = 4, positionMs = 2L))

        assertEquals(state(currentSlot = 4, positionMs = 2L), dao.loadState())
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM playback_state").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    // Mutation: drop deleteState() from clear → the state row survives → RED.
    @Test
    fun clear_emptiesTheQueueAndTheState() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha")))
        dao.replaceQueue(listOf(PlaybackQueueEntity(0, "a", 0)), state(currentSlot = 0, positionMs = 1L))

        dao.clear()

        assertEquals(emptyList<Any>(), dao.loadQueue())
        assertNull(dao.loadState())
    }

    // Mutation: loadSnapshot returns PlaybackSnapshot(loadQueue(), null) → RED.
    @Test
    fun loadSnapshot_readsTheQueueAndTheState() = runTest {
        songDao.upsertSongs(listOf(song("a", "Alpha")))
        dao.replaceQueue(listOf(PlaybackQueueEntity(0, "a", 0)), state(currentSlot = 0, positionMs = 42L))

        val snapshot = dao.loadSnapshot()

        assertEquals(listOf("a"), snapshot.queue.map { it.songId })
        assertEquals(42L, snapshot.state?.positionMs)
    }
}
```

- [ ] **Step 7: Compile, check and report**

Run: `./gradlew :core:database:compileDebugAndroidTestKotlin :core:database:check spotlessApply`
Expected: BUILD SUCCESSFUL.

The androidTests run on the device in Task 9. List every mutation above under "Device mutations" in the report, and do not apply them.

---

### Task 2: `SavedPlayback`, `PlaybackStateRepository` and the fake (data-layer-agent)

**Files:**
- Create: `core/model/src/main/kotlin/com/rolla/musicplayer/core/model/SavedPlayback.kt`
- Create: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/PlaybackStateRepository.kt`
- Create: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/PlaybackStateRepositoryImpl.kt`
- Create: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/RestoredPlayback.kt`
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/di/DataModule.kt`
- Create: `core/testing/src/main/kotlin/com/rolla/musicplayer/core/testing/FakePlaybackStateRepository.kt`
- Test: `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/repository/RestoredPlaybackTest.kt`
- Test: `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/repository/PlaybackStateRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `PlaybackQueueDao`, `PlaybackQueueEntity`, `PlaybackStateEntity`, `QueuedSongRow`, `PlaybackSnapshot` (Task 1); `SongEntity.toDomain()`, `RepeatMode`.
- Produces:
  - `data class SavedPlaybackState(currentIndex: Int, positionMs: Long, shuffleEnabled: Boolean, repeatMode: RepeatMode)`
  - `data class SavedPlayback(songs: List<Song>, shuffleOrder: List<Int>, state: SavedPlaybackState)`
  - `interface PlaybackStateRepository`:
    - `suspend fun load(): SavedPlayback?`
    - `suspend fun saveQueue(songIds: List<String>, shuffleOrder: List<Int>, state: SavedPlaybackState)`
    - `suspend fun saveState(state: SavedPlaybackState)`
    - `suspend fun clear()`
  - `FakePlaybackStateRepository`, whose members are listed in Step 5.

- [ ] **Step 1: The model**

`SavedPlayback.kt`:

```kotlin
package com.rolla.musicplayer.core.model

/** Where playback stood when it was saved. [currentIndex] indexes the owning [SavedPlayback.songs]. */
data class SavedPlaybackState(
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
)

/**
 * A restorable queue (queue-restore spec §5.4): [songs] in queue order, and [shuffleOrder], a permutation of
 * `songs.indices` giving the shuffled playback order. It is kept even while shuffle is off, so turning shuffle on
 * after a restore gives back the same order.
 */
data class SavedPlayback(
    val songs: List<Song>,
    val shuffleOrder: List<Int>,
    val state: SavedPlaybackState,
)
```

- [ ] **Step 2: Write the failing restore tests**

`RestoredPlaybackTest.kt`:

```kotlin
package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.QueuedSongRow
import com.rolla.musicplayer.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoredPlaybackTest {

    private fun entity(id: String) = SongEntity(
        id = id,
        mediaStoreId = 1L,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        durationMs = 1_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "",
        dateModified = 0L,
    )

    /** Slot i holds song "s<i>" with rank ranks[i]; slots listed in [missing] have no song. */
    private fun rows(ranks: List<Int>, missing: Set<Int> = emptySet()) = ranks.mapIndexed { slot, rank ->
        QueuedSongRow(slot, rank, "s$slot", if (slot in missing) null else entity("s$slot"))
    }

    private fun state(currentSlot: Int, positionMs: Long = 83_000L, shuffle: Boolean = true, repeat: String = "ALL") =
        PlaybackStateEntity(
            currentSlot = currentSlot,
            positionMs = positionMs,
            shuffleEnabled = shuffle,
            repeatMode = repeat,
            savedAt = 0L,
        )

    // Ranks [2,0,3,1]: rank order is slots 1,3,0,2, unlike slot order. Mutations: shuffleOrder from slot order →
    // [0,1,2,3] → RED; positionMs = 0 → RED; repeat parsed as OFF → RED.
    @Test
    fun everySongSurvives_keepsQueueShuffleOrderCurrentAndPosition() {
        val saved = restoredPlayback(rows(listOf(2, 0, 3, 1)), state(currentSlot = 2))!!

        assertEquals(listOf("s0", "s1", "s2", "s3"), saved.songs.map { it.id })
        assertEquals(listOf(1, 3, 0, 2), saved.shuffleOrder)
        assertEquals(2, saved.state.currentIndex)
        assertEquals(83_000L, saved.state.positionMs)
        assertEquals(true, saved.state.shuffleEnabled)
        assertEquals(RepeatMode.ALL, saved.state.repeatMode)
    }

    // Slots 1 and 3 missing; survivors are slots 0, 2, 4 with ranks 4, 2, 3 → new indices 0, 1, 2 → shuffle order
    // [1, 2, 0]. Mutation: build the order from the old slot numbers (`sortedBy rank, map slot`) → [2, 4, 0] → RED.
    @Test
    fun missingSongsAreDropped_survivorsRenumberedAndKeepTheirRelativeShuffleOrder() {
        val saved = restoredPlayback(rows(listOf(4, 0, 2, 1, 3), missing = setOf(1, 3)), state(currentSlot = 4))!!

        assertEquals(listOf("s0", "s2", "s4"), saved.songs.map { it.id })
        assertEquals(listOf(1, 2, 0), saved.shuffleOrder)
        assertEquals(2, saved.state.currentIndex)
        assertEquals(83_000L, saved.state.positionMs)
    }

    // Review Focus 3. Ranks [3,1,0,2]; the current slot 1 (rank 1) is missing and shuffle is on. Shuffle playback order:
    // slot 2 (r0), slot 1 (r1, missing), slot 3 (r2), slot 0 (r3) → next survivor is slot 3 → new index 2 (survivors
    // 0, 2, 3). Slot order would give slot 2 → index 1. Mutations: always use slot order → 1 → RED; keep the saved
    // position → RED.
    @Test
    fun missingCurrentWithShuffleOn_movesToTheNextSurvivorInShuffleOrderAtZero() {
        val saved = restoredPlayback(rows(listOf(3, 1, 0, 2), missing = setOf(1)), state(currentSlot = 1))!!

        assertEquals(2, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
    }

    // Same rows, shuffle off: next survivor in slot order is slot 2 → index 1 (rank order would give index 2).
    // Mutation: always use rank order → 2 → RED.
    @Test
    fun missingCurrentWithShuffleOff_movesToTheNextSlot() {
        val saved = restoredPlayback(
            rows(listOf(3, 1, 0, 2), missing = setOf(1)),
            state(currentSlot = 1, shuffle = false),
        )!!

        assertEquals(1, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
    }

    // Ranks [1,2,0]; the current slot 1 holds the last rank and is missing → wrap to the first survivor in shuffle order,
    // slot 2 (r0) → index 1 (survivors 0, 2). Mutation: wrap to index 0 instead of playbackOrder.first() → RED.
    @Test
    fun missingCurrentAtTheEnd_wrapsToTheFirstSurvivorInPlaybackOrder() {
        val saved = restoredPlayback(rows(listOf(1, 2, 0), missing = setOf(1)), state(currentSlot = 1))!!

        assertEquals(1, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
    }

    // Mutation: return a SavedPlayback with empty songs instead of null → RED.
    @Test
    fun noSurvivors_restoreNothing() {
        assertNull(restoredPlayback(rows(listOf(0, 1), missing = setOf(0, 1)), state(currentSlot = 0)))
    }

    // Review Focus 1. Mutation: deduplicate songs by id (`distinctBy { it.song?.id }`) → one song → RED.
    @Test
    fun duplicateSongsKeepBothSlots() {
        val duplicate = listOf(
            QueuedSongRow(0, 1, "s0", entity("s0")),
            QueuedSongRow(1, 0, "s0", entity("s0")),
        )

        val saved = restoredPlayback(duplicate, state(currentSlot = 1))!!

        assertEquals(listOf("s0", "s0"), saved.songs.map { it.id })
        assertEquals(listOf(1, 0), saved.shuffleOrder)
        assertEquals(1, saved.state.currentIndex)
    }

    // Review Focus 5. Mutation: `RepeatMode.valueOf(state.repeatMode)` → IllegalArgumentException → RED.
    @Test
    fun unknownRepeatModeFallsBackToOff() {
        val saved = restoredPlayback(rows(listOf(0, 1)), state(currentSlot = 0, repeat = "SHUFFLE"))!!

        assertEquals(RepeatMode.OFF, saved.state.repeatMode)
    }

    // Review Focus 5: ranks [1,1,0] (corrupt). The order is still a permutation, with ties broken by slot.
    // Mutation: build the order with `distinctBy { rank }` → a 2-element order → RED.
    @Test
    fun duplicateRanksStillGiveAPermutation() {
        val saved = restoredPlayback(rows(listOf(1, 1, 0)), state(currentSlot = 0))!!

        assertEquals(listOf(2, 0, 1), saved.shuffleOrder)
    }
}
```

Run: `./gradlew :core:data:testDebugUnitTest --tests "*RestoredPlaybackTest*"`. Expected: compile failure, because `restoredPlayback` does not exist.

- [ ] **Step 3: The pure restore function**

`RestoredPlayback.kt`:

```kotlin
package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.QueuedSongRow
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState

/**
 * Turns saved rows ([rows] in slot order, missing songs included as `song == null`) into a [SavedPlayback] (queue-restore
 * spec §7.4, ruling R2), or null when no song survived.
 *
 * Survivors are renumbered `0 until n` in slot order. The shuffle order sorts them by their saved rank (a stable sort,
 * so duplicate ranks fall back to slot order). When the current song itself is gone, playback moves to the next
 * survivor after it in playback order (rank order with shuffle on, slot order with it off), wrapping to the first, at
 * position 0.
 */
internal fun restoredPlayback(rows: List<QueuedSongRow>, state: PlaybackStateEntity): SavedPlayback? {
    val survivors = rows.filter { it.song != null }
    if (survivors.isEmpty()) return null
    val songs = survivors.mapNotNull { it.song?.toDomain() }
    val shuffleOrder = survivors.indices.sortedBy { survivors[it].shuffleRank }
    val playbackOrder = if (state.shuffleEnabled) shuffleOrder else survivors.indices.toList()
    val orderKey: (QueuedSongRow) -> Int = if (state.shuffleEnabled) { row -> row.shuffleRank } else { row -> row.slot }

    val savedIndex = survivors.indexOfFirst { it.slot == state.currentSlot }
    val currentIndex: Int
    val positionMs: Long
    if (savedIndex >= 0) {
        currentIndex = savedIndex
        positionMs = state.positionMs.coerceAtLeast(0L)
    } else {
        val missingCurrent = rows.firstOrNull { it.slot == state.currentSlot }
        val next = missingCurrent?.let { gone ->
            playbackOrder.firstOrNull { orderKey(survivors[it]) > orderKey(gone) }
        }
        currentIndex = next ?: playbackOrder.first()
        positionMs = 0L
    }

    return SavedPlayback(
        songs = songs,
        shuffleOrder = shuffleOrder,
        state = SavedPlaybackState(
            currentIndex = currentIndex,
            positionMs = positionMs,
            shuffleEnabled = state.shuffleEnabled,
            repeatMode = repeatModeOf(state.repeatMode),
        ),
    )
}

/** An unknown or corrupt name restores as [RepeatMode.OFF] (Review Focus 5). */
internal fun repeatModeOf(name: String): RepeatMode = RepeatMode.entries.firstOrNull { it.name == name } ?: RepeatMode.OFF
```

Run: `./gradlew :core:data:testDebugUnitTest --tests "*RestoredPlaybackTest*"`. Expected: PASS (9 tests). Apply each named mutation, see RED, and revert byte-exact.

- [ ] **Step 4: The repository and its tests**

`PlaybackStateRepository.kt`:

```kotlin
package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState

/** The saved playback queue (queue-restore spec §5.4). Every call is main-safe. */
interface PlaybackStateRepository {

    /** The saved queue, ready to restore, or null when nothing is saved or no saved song survives (then it clears). */
    suspend fun load(): SavedPlayback?

    /**
     * Replaces the saved queue: [songIds] in queue order, [shuffleOrder] a permutation of `songIds.indices`, and
     * [state] with `currentIndex` indexing [songIds].
     */
    suspend fun saveQueue(songIds: List<String>, shuffleOrder: List<Int>, state: SavedPlaybackState)

    /** Updates only the position, current index and modes of the saved queue. */
    suspend fun saveState(state: SavedPlaybackState)

    suspend fun clear()
}
```

`PlaybackStateRepositoryImpl.kt`:

```kotlin
package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.PlaybackQueueDao
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import javax.inject.Inject

class PlaybackStateRepositoryImpl @Inject constructor(
    private val dao: PlaybackQueueDao,
) : PlaybackStateRepository {

    override suspend fun load(): SavedPlayback? {
        val snapshot = dao.loadSnapshot()
        val state = snapshot.state ?: return null
        val saved = restoredPlayback(snapshot.queue, state)
        if (saved == null) dao.clear()
        return saved
    }

    override suspend fun saveQueue(songIds: List<String>, shuffleOrder: List<Int>, state: SavedPlaybackState) {
        require(shuffleOrder.sorted() == songIds.indices.toList()) { "shuffleOrder must be a permutation of the slots" }
        val ranks = IntArray(songIds.size)
        shuffleOrder.forEachIndexed { rank, slot -> ranks[slot] = rank }
        val rows = songIds.mapIndexed { slot, songId -> PlaybackQueueEntity(slot, songId, ranks[slot]) }
        dao.replaceQueue(rows, state.toEntity())
    }

    override suspend fun saveState(state: SavedPlaybackState) = dao.upsertState(state.toEntity())

    override suspend fun clear() = dao.clear()

    private fun SavedPlaybackState.toEntity() = PlaybackStateEntity(
        currentSlot = currentIndex,
        positionMs = positionMs,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode.name,
        savedAt = System.currentTimeMillis(),
    )
}
```

`PlaybackStateRepositoryImplTest.kt`:

```kotlin
package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.PlaybackQueueDao
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.relation.PlaybackSnapshot
import com.rolla.musicplayer.core.database.relation.QueuedSongRow
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlaybackState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStateRepositoryImplTest {

    private val dao = mockk<PlaybackQueueDao>(relaxed = true)
    private val repository = PlaybackStateRepositoryImpl(dao)
    private val state = SavedPlaybackState(currentIndex = 2, positionMs = 83_000L, shuffleEnabled = true, RepeatMode.ALL)

    // shuffleOrder [2,0,1] is not its own inverse: ranks are slot0→1, slot1→2, slot2→0. Mutation: rank = shuffleOrder[slot]
    // (the inverse mix-up) → ranks [2,0,1] → RED.
    @Test
    fun saveQueue_writesEachSlotWithItsShuffleRankAndTheState() = runTest {
        val rows = slot<List<PlaybackQueueEntity>>()
        val written = slot<PlaybackStateEntity>()
        coEvery { dao.replaceQueue(capture(rows), capture(written)) } returns Unit

        repository.saveQueue(listOf("a", "b", "c"), listOf(2, 0, 1), state)

        assertEquals(
            listOf(PlaybackQueueEntity(0, "a", 1), PlaybackQueueEntity(1, "b", 2), PlaybackQueueEntity(2, "c", 0)),
            rows.captured,
        )
        assertEquals(2, written.captured.currentSlot)
        assertEquals(83_000L, written.captured.positionMs)
        assertEquals(true, written.captured.shuffleEnabled)
        assertEquals("ALL", written.captured.repeatMode)
    }

    // Mutation: write repeatMode = "OFF" → RED.
    @Test
    fun saveState_upsertsTheStateRow() = runTest {
        val written = slot<PlaybackStateEntity>()
        coEvery { dao.upsertState(capture(written)) } returns Unit

        repository.saveState(state.copy(repeatMode = RepeatMode.ONE))

        assertEquals("ONE", written.captured.repeatMode)
        assertEquals(PlaybackStateEntity.SINGLE_ROW_ID, written.captured.id)
    }

    // Mutation: drop the dao.clear() call in load → RED.
    @Test
    fun load_whenNoSongSurvives_returnsNullAndClears() = runTest {
        coEvery { dao.loadSnapshot() } returns PlaybackSnapshot(
            queue = listOf(QueuedSongRow(0, 0, "gone", null)),
            state = PlaybackStateEntity(currentSlot = 0, positionMs = 1L, shuffleEnabled = false, repeatMode = "OFF", savedAt = 0L),
        )

        assertNull(repository.load())
        coVerify(exactly = 1) { dao.clear() }
    }

    // Mutation: treat a missing state as "restore with defaults" (state ?: default entity) → a non-null result → RED.
    @Test
    fun load_withoutAStateRow_returnsNull() = runTest {
        coEvery { dao.loadSnapshot() } returns PlaybackSnapshot(queue = emptyList(), state = null)

        assertNull(repository.load())
    }
}
```

Bind in `DataModule`, after `bindFolderRepository`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindPlaybackStateRepository(impl: PlaybackStateRepositoryImpl): PlaybackStateRepository
```

Match the annotations the neighbouring `@Binds` entries use. If they carry no `@Singleton`, leave it off.

- [ ] **Step 5: The fake**

`FakePlaybackStateRepository.kt`:

```kotlin
package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.PlaybackStateRepository
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory fake [PlaybackStateRepository]. [saved] is what [load] returns. [loadGate] (when set) holds every [load]
 * until completed, and [loadFailure] makes the next [load] throw. Writes are recorded in [queueWrites], [stateWrites]
 * and [clearCount]; [failNextWrite] makes the next save throw once.
 */
class FakePlaybackStateRepository : PlaybackStateRepository {

    data class QueueWrite(val songIds: List<String>, val shuffleOrder: List<Int>, val state: SavedPlaybackState)

    var saved: SavedPlayback? = null
    var loadGate: CompletableDeferred<Unit>? = null
    var loadFailure: Throwable? = null
    var failNextWrite: Throwable? = null

    var loadCount = 0
        private set
    var clearCount = 0
        private set
    val queueWrites = mutableListOf<QueueWrite>()
    val stateWrites = mutableListOf<SavedPlaybackState>()

    override suspend fun load(): SavedPlayback? {
        loadCount++
        loadGate?.await()
        loadFailure?.let { loadFailure = null; throw it }
        return saved
    }

    override suspend fun saveQueue(songIds: List<String>, shuffleOrder: List<Int>, state: SavedPlaybackState) {
        throwIfFailing()
        queueWrites += QueueWrite(songIds, shuffleOrder, state)
    }

    override suspend fun saveState(state: SavedPlaybackState) {
        throwIfFailing()
        stateWrites += state
    }

    override suspend fun clear() {
        clearCount++
        saved = null
    }

    private fun throwIfFailing() {
        failNextWrite?.let { failNextWrite = null; throw it }
    }
}
```

- [ ] **Step 6: Run and report**

Run: `./gradlew :core:model:check :core:data:check :core:testing:check spotlessApply`. Expected: BUILD SUCCESSFUL, with the 9 restore tests and 4 repository tests passing. Each named mutation must be RED and then reverted.

---

### Task 3: `Song.toMediaItem()` and `RestoredShuffleOrder` (audio-engineer)

**Files:**
- Create: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/MediaItems.kt`
- Modify: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/PlaybackController.kt` (delete `buildMediaItem` and call `toMediaItem()`)
- Create: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/RestoredShuffleOrder.kt`
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/MediaItemsTest.kt`
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/RestoredShuffleOrderTest.kt`

**Interfaces:**
- Consumes: `Song`, `RepeatMode`, `StartFirstShuffleEnforcer`, `Player.queueState()` (existing).
- Produces:
  - `internal fun Song.toMediaItem(): MediaItem`
  - `internal fun RepeatMode.toPlayerRepeatMode(): Int`
  - `internal fun Int.toRepeatMode(): RepeatMode`
  - `internal class RestoredShuffleOrder(player: ExoPlayer, seed: () -> Long = { Random.nextLong() }) : Player.Listener`, with `fun arm(order: List<Int>)` and `val isArmed: Boolean`

- [ ] **Step 1: The media item helpers**

`MediaItems.kt`. Move the body of `PlaybackController.buildMediaItem` here unchanged:

```kotlin
package com.rolla.musicplayer.core.media

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.Song

/** The one way a [Song] becomes a Media3 item: PlaybackController, the restore and playback resumption share it. */
internal fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri.ifEmpty { null }?.toUri())
                .build(),
        )
        .build()

internal fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
}

internal fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    else -> RepeatMode.OFF
}
```

In `PlaybackController.kt`:
- Delete `private fun buildMediaItem` and its now-unused imports (`toUri`, `MediaMetadata`).
- Replace its three uses:
  - `play`: `val item = song.toMediaItem()`
  - `playAll`: `songs.map { it.toMediaItem() }`
  - `updateSongMetadata`: `val item = song.toMediaItem()`

Consumer audit: `grep -rn "buildMediaItem" core feature app` must return nothing afterwards, and `PlaybackControllerTest` must still pass unchanged.

- [ ] **Step 2: The helper tests**

`MediaItemsTest.kt` (Robolectric: `toUri` needs `android.net.Uri`):

```kotlin
package com.rolla.musicplayer.core.media

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaItemsTest {

    private val song = Song(
        id = "42",
        title = "Title",
        artist = "Artist",
        album = "Album",
        albumId = 7L,
        durationMs = 1_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/42",
        artworkUri = "content://media/external/audio/albumart/7",
    )

    // Mutations: drop setArtworkUri → null artwork → RED; setMediaId(title) → RED.
    @Test
    fun toMediaItem_carriesTheIdUriAndMetadata() {
        val item = song.toMediaItem()

        assertEquals("42", item.mediaId)
        assertEquals("content://media/external/audio/media/42", item.localConfiguration?.uri.toString())
        assertEquals("Title", item.mediaMetadata.title)
        assertEquals("Artist", item.mediaMetadata.artist)
        assertEquals("Album", item.mediaMetadata.albumTitle)
        assertEquals("content://media/external/audio/albumart/7", item.mediaMetadata.artworkUri.toString())
    }

    // Mutation: drop `.ifEmpty { null }` → an empty Uri instead of null → RED.
    @Test
    fun toMediaItem_withoutArtwork_hasNoArtworkUri() {
        assertNull(song.copy(artworkUri = "").toMediaItem().mediaMetadata.artworkUri)
    }

    // Mutation: swap ONE and ALL in either mapping → RED.
    @Test
    fun repeatModesRoundTrip() {
        for (mode in RepeatMode.entries) {
            assertEquals(mode, mode.toPlayerRepeatMode().toRepeatMode())
        }
        assertEquals(Player.REPEAT_MODE_ALL, RepeatMode.ALL.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ONE, RepeatMode.ONE.toPlayerRepeatMode())
    }
}
```

- [ ] **Step 3: Write the failing `RestoredShuffleOrder` tests**

`RestoredShuffleOrderTest.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class RestoredShuffleOrderTest {

    private lateinit var player: ExoPlayer
    private lateinit var restored: RestoredShuffleOrder

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setShuffleOrder(DefaultShuffleOrder(0, MEDIA3_ORDER_SEED))
        // Production order (PlaybackService): the enforcer first, then RestoredShuffleOrder.
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        restored = RestoredShuffleOrder(player) { 99L }
        player.addListener(restored)
    }

    @After
    fun tearDown() = player.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun items(n: Int) = (0 until n).map {
        MediaItem.Builder().setMediaId("id$it").setUri("content://media/external/audio/media/$it")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("T$it").build()).build()
    }

    private fun order() = player.queueState().items.map { it.index }

    // Ruling R3's sequence. The saved order does not start at the current window (1), so the enforcer alone would
    // reorder it. Mutations: register RestoredShuffleOrder BEFORE the enforcer (in setUp) → the enforcer's start-first
    // order wins → RED; drop the setShuffleOrder call → RED.
    @Test
    fun theSavedOrderSurvivesTheEnforcer() {
        restored.arm(listOf(3, 0, 4, 1, 2))
        player.shuffleModeEnabled = true
        player.setMediaItems(items(5), 1, 83_000L)
        idle()

        assertEquals(listOf(3, 0, 4, 1, 2), order())
        assertEquals(3, player.queueState().currentIndex)
        assertFalse(restored.isArmed)
    }

    // One-shot: the next queue the user starts gets the enforcer's start-first order again. Mutation: never clear
    // `pending` → the second same-size queue gets the saved order → RED.
    @Test
    fun itAppliesOnceThenTheEnforcerOwnsLaterQueues() {
        restored.arm(listOf(3, 0, 4, 1, 2))
        player.shuffleModeEnabled = true
        player.setMediaItems(items(5), 1, C.TIME_UNSET)
        idle()

        player.setMediaItems(items(5), 2, C.TIME_UNSET)
        idle()

        assertEquals(2, order().first())
    }

    // Only a playlist replacement consumes it. Mutation: drop the PLAYLIST_CHANGED check → the seek applies the armed
    // order → RED.
    @Test
    fun aSeekDoesNotConsumeIt() {
        player.shuffleModeEnabled = true
        player.setMediaItems(items(5), 0, C.TIME_UNSET)
        idle()
        val before = order()

        restored.arm(listOf(4, 3, 2, 1, 0))
        player.seekToDefaultPosition(2)
        idle()

        assertEquals(before, order())
    }

    private companion object {
        const val MEDIA3_ORDER_SEED = 1L
    }
}
```

Run: `./gradlew :core:media:testDebugUnitTest --tests "*RestoredShuffleOrderTest*"`. Expected: compile failure.

- [ ] **Step 4: Implement `RestoredShuffleOrder`**

`RestoredShuffleOrder.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random

/**
 * Puts a saved shuffle order back after a restore (queue-restore spec §7.5, ruling R3). [arm] it just before the
 * restore sets its items; on the next playlist replacement it applies the order once and disarms.
 *
 * Register it AFTER [StartFirstShuffleEnforcer]. Both react to `MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED`, and the
 * later listener's `setShuffleOrder` is the one that sticks. `setShuffleOrder` fires no media-item transition, so
 * nothing re-enters.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class RestoredShuffleOrder(
    private val player: ExoPlayer,
    private val seed: () -> Long = { Random.nextLong() },
) : Player.Listener {

    private var pending: IntArray? = null

    val isArmed: Boolean get() = pending != null

    fun arm(order: List<Int>) {
        pending = order.toIntArray()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        val order = pending ?: return
        pending = null
        try {
            player.setShuffleOrder(DefaultShuffleOrder(order, seed()))
        } catch (e: IllegalArgumentException) {
            // The restored list no longer matches the order's length; keep the enforcer's order rather than crash.
            Log.w(TAG, "Restored shuffle order skipped", e)
        }
    }

    private companion object {
        const val TAG = "RestoredShuffleOrder"
    }
}
```

- [ ] **Step 5: Run and report**

Run: `./gradlew :core:media:check spotlessApply`. Expected: BUILD SUCCESSFUL. `PlaybackControllerTest` and `QueueAndShuffleExoPlayerTest` are unchanged and green, and every named mutation is RED and then reverted.

---

### Task 4: `QueuePersister` (audio-engineer)

**Files:**
- Create: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/QueuePersister.kt`
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/QueuePersisterTest.kt`

**Interfaces:**
- Consumes:
  - `PlaybackStateRepository`, `SavedPlaybackState`, `FakePlaybackStateRepository` (Task 2)
  - `Int.toRepeatMode()` (Task 3)
  - `playbackOrder(...)` (existing, `Queue.kt`)
- Produces: `internal class QueuePersister(player: Player, repository: PlaybackStateRepository, scope: CoroutineScope, elapsedRealtime: () -> Long = SystemClock::elapsedRealtime) : Player.Listener`, with these members:
  - `fun enable()`
  - `fun onPlayingTick()`
  - `fun finalState(): SavedPlaybackState?`
- Also produces `internal sealed interface PendingWrite` and `internal fun PendingWrite?.mergedWith(next: PendingWrite): PendingWrite`.

- [ ] **Step 1: Write the failing tests**

`QueuePersisterTest.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlaybackState
import com.rolla.musicplayer.core.testing.FakePlaybackStateRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class QueuePersisterTest {

    private lateinit var player: ExoPlayer
    private val repository = FakePlaybackStateRepository()
    private var now = 0L

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() = player.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun items(n: Int) = (0 until n).map {
        MediaItem.Builder().setMediaId("id$it").setUri("content://media/external/audio/media/$it").build()
    }

    private fun TestScope.persister(enabled: Boolean = true): QueuePersister =
        QueuePersister(player, repository, backgroundScope) { now }.also {
            player.addListener(it)
            if (enabled) it.enable()
        }

    private fun TestScope.settle() {
        idle()
        advanceUntilIdle()
    }

    // Spec §6.3. Mutation: ignore `enabled` → the new queue is written → RED.
    @Test
    fun aDisabledPersisterWritesNothing() = runTest {
        persister(enabled = false)
        player.setMediaItems(items(3), 1, 5_000L)
        settle()

        assertEquals(0, repository.queueWrites.size)
        assertEquals(0, repository.stateWrites.size)
    }

    // The shuffle order [2,0,3,1] is not slot order, and shuffle stays OFF: the ranks come from the shuffle walk whatever
    // the mode (spec §6.1). Mutations: walk with `shuffleModeEnabled` (false) → [0,1,2,3] → RED; write ids in shuffle
    // order → RED.
    @Test
    fun aNewQueueWritesItsIdsShuffleOrderAndState() = runTest {
        persister()
        player.setMediaItems(items(4), 1, 5_000L)
        player.setShuffleOrder(DefaultShuffleOrder(intArrayOf(2, 0, 3, 1), 1L))
        settle()

        val write = repository.queueWrites.last()
        assertEquals(listOf("id0", "id1", "id2", "id3"), write.songIds)
        assertEquals(listOf(2, 0, 3, 1), write.shuffleOrder)
        assertEquals(SavedPlaybackState(1, 5_000L, shuffleEnabled = false, RepeatMode.OFF), write.state)
    }

    // Spec §6.2 and Review Focus 2: a burst is one queue write carrying the latest state. Mutations: write every event
    // (no conflation) → 2+ queue writes → RED; `mergedWith` lets a State replace a pending Queue → no queue write → RED.
    @Test
    fun aFiveThousandTrackQueueIsOneWrite() = runTest {
        persister()
        player.setMediaItems(items(3), 0, C.TIME_UNSET)
        player.setMediaItems(items(5_000), 10, 7_000L)
        player.seekTo(11, 9_000L)
        settle()

        assertEquals(1, repository.queueWrites.size)
        val write = repository.queueWrites.single()
        assertEquals(5_000, write.songIds.size)
        assertEquals((0 until 5_000).toSet(), write.shuffleOrder.toSet())
        assertEquals(11, write.state.currentIndex)
        assertEquals(9_000L, write.state.positionMs)
    }

    // A seek inside the current item fires only a position discontinuity (no transition, no timeline change), and a
    // seek to another item fires one too (ruling R9). Mutations (each deletes one override): onPositionDiscontinuity →
    // neither seek writes → RED; onShuffleModeEnabledChanged → RED; onRepeatModeChanged → RED.
    @Test
    fun seeksAndModeChangesWriteTheState() = runTest {
        persister()
        player.setMediaItems(items(4), 0, C.TIME_UNSET)
        settle()

        player.seekTo(0, 7_000L)
        settle()
        assertEquals(SavedPlaybackState(0, 7_000L, false, RepeatMode.OFF), repository.stateWrites.last())

        player.seekTo(2, 3_000L)
        settle()
        assertEquals(SavedPlaybackState(2, 3_000L, false, RepeatMode.OFF), repository.stateWrites.last())

        player.shuffleModeEnabled = true
        settle()
        assertEquals(true, repository.stateWrites.last().shuffleEnabled)

        player.repeatMode = Player.REPEAT_MODE_ALL
        settle()
        assertEquals(RepeatMode.ALL, repository.stateWrites.last().repeatMode)
    }

    // A pause writes the state; starting to play does not. Mutation: write on `true` as well → 2 writes → RED.
    @Test
    fun onlyAPauseWritesTheState() = runTest {
        val persister = persister()
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()
        val before = repository.stateWrites.size

        persister.onIsPlayingChanged(true)
        settle()
        persister.onIsPlayingChanged(false)
        settle()

        assertEquals(before + 1, repository.stateWrites.size)
    }

    // Spec §6.3. Mutation: treat an empty timeline like any other (skip it) → clearCount 0 → RED.
    @Test
    fun anEmptiedQueueClearsTheSave() = runTest {
        persister()
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()

        player.clearMediaItems()
        settle()

        assertEquals(1, repository.clearCount)
    }

    // Spec §6.1, every 10 s while playing. Mutation: an interval of 1_000 → the 9_999 tick writes → RED.
    @Test
    fun aPlayingTickWritesAtMostEveryTenSeconds() = runTest {
        val persister = persister()
        now = 0L
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()
        val before = repository.stateWrites.size

        now = 9_999L
        persister.onPlayingTick()
        settle()
        assertEquals(before, repository.stateWrites.size)

        now = 10_000L
        persister.onPlayingTick()
        settle()
        assertEquals(before + 1, repository.stateWrites.size)
    }

    // Spec §8: a failed write is logged and the next one still happens. Mutation: remove the catch in the drain loop →
    // the consumer dies (the test fails on the uncaught IOException, or the second write never lands) → RED.
    @Test
    fun aFailedWriteDoesNotStopLaterWrites() = runTest {
        persister()
        repository.failNextWrite = IOException("disk full")
        player.setMediaItems(items(2), 0, C.TIME_UNSET)
        settle()

        player.seekTo(1, 1_000L)
        settle()

        assertEquals(1, repository.stateWrites.last().currentIndex)
    }

    // Ruling R7: the service's final save. Mutations: return the state while disabled → RED; return a state for an
    // empty player → RED.
    @Test
    fun finalStateIsNullWhileDisabledOrEmpty() = runTest {
        val persister = persister(enabled = false)
        player.setMediaItems(items(2), 1, 4_000L)
        idle()
        assertNull(persister.finalState())

        persister.enable()
        assertEquals(SavedPlaybackState(1, 4_000L, false, RepeatMode.OFF), persister.finalState())

        player.clearMediaItems()
        idle()
        assertNull(persister.finalState())
    }
}
```

Run: `./gradlew :core:media:testDebugUnitTest --tests "*QueuePersisterTest*"`. Expected: compile failure.

- [ ] **Step 2: Implement**

`QueuePersister.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.rolla.musicplayer.core.data.repository.PlaybackStateRepository
import com.rolla.musicplayer.core.model.SavedPlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** A write waiting for the persister's single consumer (queue-restore spec §6.2). */
internal sealed interface PendingWrite {
    data class Queue(val songIds: List<String>, val shuffleOrder: List<Int>, val state: SavedPlaybackState) : PendingWrite

    data class State(val state: SavedPlaybackState) : PendingWrite

    data object Clear : PendingWrite
}

/** The latest write wins, except that a newer state folds into a pending queue write instead of replacing it. */
internal fun PendingWrite?.mergedWith(next: PendingWrite): PendingWrite =
    if (this is PendingWrite.Queue && next is PendingWrite.State) copy(state = next.state) else next

/**
 * Saves the player's queue and state (queue-restore spec §6). It reads the player on its application thread (every
 * callback, [onPlayingTick] and [finalState] run there) and writes through [repository] from [scope], one write at a
 * time, conflating bursts. It writes nothing until [enable] (spec §6.3: not while the restore is loading).
 */
internal class QueuePersister(
    private val player: Player,
    private val repository: PlaybackStateRepository,
    scope: CoroutineScope,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : Player.Listener {

    private val lock = Any()
    private var pending: PendingWrite? = null
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var enabled = false
    private var lastStateSaveAt = 0L

    init {
        scope.launch {
            for (ignored in signal) drain()
        }
    }

    fun enable() {
        enabled = true
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (timeline.isEmpty) enqueue(PendingWrite.Clear) else enqueueQueue()
    }

    // No onMediaItemTransition (ruling R9): every transition also brings a discontinuity or a timeline change.

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) enqueueState()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) = enqueueState()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = enqueueState()

    override fun onRepeatModeChanged(repeatMode: Int) = enqueueState()

    /** Called every second while playing; writes the position at most every [STATE_SAVE_INTERVAL_MS]. */
    fun onPlayingTick() {
        if (elapsedRealtime() - lastStateSaveAt >= STATE_SAVE_INTERVAL_MS) enqueueState()
    }

    /** The state to save as the service stops (ruling R7), or null when disabled or empty. */
    fun finalState(): SavedPlaybackState? =
        if (enabled && !player.currentTimeline.isEmpty) currentState() else null

    private fun enqueueQueue() {
        val timeline = player.currentTimeline
        val count = timeline.windowCount
        val window = Timeline.Window()
        val songIds = (0 until count).map { timeline.getWindow(it, window).mediaItem.mediaId }
        val walk = playbackOrder(count, timeline.getFirstWindowIndex(true)) {
            timeline.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, true)
        }
        val shuffleOrder = if (walk.size == count) walk else (0 until count).toList()
        enqueue(PendingWrite.Queue(songIds, shuffleOrder, currentState()))
    }

    private fun enqueueState() {
        if (player.currentTimeline.isEmpty) return
        enqueue(PendingWrite.State(currentState()))
    }

    private fun enqueue(write: PendingWrite) {
        if (!enabled) return
        if (write !is PendingWrite.Clear) lastStateSaveAt = elapsedRealtime()
        synchronized(lock) { pending = pending.mergedWith(write) }
        signal.trySend(Unit)
    }

    private fun currentState() = SavedPlaybackState(
        currentIndex = player.currentMediaItemIndex,
        positionMs = player.currentPosition.coerceAtLeast(0L),
        shuffleEnabled = player.shuffleModeEnabled,
        repeatMode = player.repeatMode.toRepeatMode(),
    )

    private suspend fun drain() {
        val write = synchronized(lock) { pending.also { pending = null } } ?: return
        try {
            when (write) {
                is PendingWrite.Queue -> repository.saveQueue(write.songIds, write.shuffleOrder, write.state)
                is PendingWrite.State -> repository.saveState(write.state)
                PendingWrite.Clear -> repository.clear()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Saving the playback queue failed", e)
        }
    }

    private companion object {
        const val TAG = "QueuePersister"
        const val STATE_SAVE_INTERVAL_MS = 10_000L
    }
}
```

detekt may flag `TooGenericExceptionCaught` on `catch (e: Exception)`. `PlaybackService` already logs and drops background failures this way. Match whatever suppression or pattern the existing service code uses for the same rule, and name it in the report.

- [ ] **Step 3: Run and report**

Run: `./gradlew :core:media:testDebugUnitTest --tests "*QueuePersisterTest*"`, then `./gradlew :core:media:check spotlessApply`. Expected: PASS (9 tests) and BUILD SUCCESSFUL, with every named mutation RED and then reverted.

If a test is flaky because ExoPlayer delivers some event on a later looper turn, use `settle()` (looper idle plus `advanceUntilIdle`). Don't weaken the assertion.

---

### Task 5: `RestoreCoordinator` and `PlaybackSessionCallback` (audio-engineer)

**Files:**
- Create: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/RestoreCoordinator.kt`
- Create: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/PlaybackSessionCallback.kt`
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/RestoreCoordinatorTest.kt`
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/PlaybackSessionCallbackTest.kt`

**Interfaces:**
- Consumes:
  - `PlaybackStateRepository`, `SavedPlayback`, `FakePlaybackStateRepository` (Task 2)
  - `Song.toMediaItem()`, `RepeatMode.toPlayerRepeatMode()`, `RestoredShuffleOrder` (Task 3)
  - `PlaybackStateHolder` (existing)
- Produces:
  - `internal class RestoreCoordinator(repository: PlaybackStateRepository, scope: CoroutineScope, mainDispatcher: CoroutineDispatcher, onSettled: () -> Unit)`, with these members:
    - `val outcome: Outcome`, where `enum class Outcome { LOADING, APPLIED, DISCARDED, HANDED_TO_RESUMPTION }`
    - `fun restoreInto(player: Player, restoredOrder: RestoredShuffleOrder, holder: PlaybackStateHolder): Job`
    - `suspend fun savedForResumption(): SavedPlayback?`
  - `internal fun Player.prepareRestore(saved: SavedPlayback, restoredOrder: RestoredShuffleOrder)`, which arms the order and sets repeat and shuffle (R3)
  - `internal class PlaybackSessionCallback(scope: CoroutineScope, mainDispatcher: CoroutineDispatcher, coordinator: RestoreCoordinator, restoredOrder: RestoredShuffleOrder) : MediaSession.Callback`

- [ ] **Step 1: Write the failing coordinator tests**

`RestoreCoordinatorTest.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.media.RestoreCoordinator.Outcome
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaybackStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class RestoreCoordinatorTest {

    private lateinit var player: ExoPlayer
    private lateinit var restoredOrder: RestoredShuffleOrder
    private val repository = FakePlaybackStateRepository()
    private val holder = PlaybackStateHolder()
    private var settledCount = 0

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setShuffleOrder(DefaultShuffleOrder(0, 1L))
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        restoredOrder = RestoredShuffleOrder(player) { 99L }
        player.addListener(restoredOrder)
    }

    @After
    fun tearDown() = player.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun song(i: Int) = Song(
        id = "s$i",
        title = "T$i",
        artist = "A",
        album = "B",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/$i",
        artworkUri = "",
    )

    private val saved = SavedPlayback(
        songs = (0 until 5).map(::song),
        shuffleOrder = listOf(3, 0, 4, 1, 2),
        state = SavedPlaybackState(currentIndex = 1, positionMs = 83_000L, shuffleEnabled = true, RepeatMode.ALL),
    )

    private fun TestScope.coordinator(): RestoreCoordinator {
        val main = StandardTestDispatcher(testScheduler)
        return RestoreCoordinator(repository, backgroundScope, main) { settledCount++ }
    }

    private fun TestScope.settle() {
        advanceUntilIdle()
        idle()
        advanceUntilIdle()
    }

    // Spec §2 and §7.2. Mutations: skip `arm` → the enforcer's start-first order → RED; call play() → playWhenReady →
    // RED; drop the start position → RED; skip holder.setPositionMs → RED.
    @Test
    fun appliesTheSavedQueuePausedWithItsOrderPositionAndModes() = runTest {
        repository.saved = saved
        val coordinator = coordinator()

        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        assertEquals(Outcome.APPLIED, coordinator.outcome)
        assertEquals((0 until 5).map { "s$it" }, (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(83_000L, player.currentPosition)
        assertEquals(listOf(3, 0, 4, 1, 2), player.queueState().items.map { it.index })
        assertEquals(true, player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        assertFalse(player.playWhenReady)
        assertEquals(83_000L, holder.positionMs.value)
        assertEquals(1, settledCount)
    }

    // Review Focus 4: the user's queue wins. Mutation: drop the `mediaItemCount > 0` check → the saved queue replaces
    // theirs → RED.
    @Test
    fun discardsWhenAQueueArrivedFirst() = runTest {
        repository.saved = saved
        repository.loadGate = CompletableDeferred()
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        player.setMediaItems(listOf(MediaItem.fromUri("content://media/external/audio/media/77")), 0, C.TIME_UNSET)
        repository.loadGate!!.complete(Unit)
        settle()

        assertEquals(Outcome.DISCARDED, coordinator.outcome)
        assertEquals(1, player.mediaItemCount)
        assertEquals(1, settledCount)
    }

    // Mutation: don't call onSettled on DISCARDED → settledCount 0 (the persister would never start) → RED.
    @Test
    fun nothingSaved_discardsAndStillSettles() = runTest {
        val coordinator = coordinator()

        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        assertEquals(Outcome.DISCARDED, coordinator.outcome)
        assertEquals(0, player.mediaItemCount)
        assertEquals(1, settledCount)
    }

    // Spec §8. Mutation: no catch around the load → the restore job fails and never settles → RED.
    @Test
    fun aFailingLoad_discardsAndStillSettles() = runTest {
        repository.loadFailure = IOException("corrupt")
        val coordinator = coordinator()

        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        assertEquals(Outcome.DISCARDED, coordinator.outcome)
        assertEquals(1, settledCount)
    }

    // Ruling R1/R6: a resumption that arrives while loading takes the first load, and the in-app restore stands down.
    // Mutation: restoreInto ignores HANDED_TO_RESUMPTION → it also applies → 5 items → RED.
    @Test
    fun aResumptionWhileLoadingTakesTheRestore() = runTest {
        repository.saved = saved
        repository.loadGate = CompletableDeferred()
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        val resumed = async { coordinator.savedForResumption() }
        settle()
        repository.loadGate!!.complete(Unit)
        settle()

        assertEquals(saved, resumed.await())
        assertEquals(Outcome.HANDED_TO_RESUMPTION, coordinator.outcome)
        assertEquals(0, player.mediaItemCount)
        assertEquals(1, repository.loadCount)
        assertEquals(1, settledCount)
    }

    // Ruling R6. Mutation: always return the first load → the stale queue → RED.
    @Test
    fun aResumptionAfterSettlingReloads() = runTest {
        repository.saved = saved
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        val newer = saved.copy(songs = saved.songs.take(2), shuffleOrder = listOf(1, 0))
        repository.saved = newer

        assertEquals(newer, coordinator.savedForResumption())
        assertEquals(2, repository.loadCount)
    }
}
```

- [ ] **Step 2: Implement the coordinator**

`RestoreCoordinator.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import android.util.Log
import androidx.media3.common.Player
import com.rolla.musicplayer.core.data.repository.PlaybackStateRepository
import com.rolla.musicplayer.core.model.SavedPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Restores the saved queue once per service start (queue-restore spec §7.1). It reads the database once
 * ([firstLoad]); the in-app restore ([restoreInto]) and a resumption arriving while loading ([savedForResumption])
 * share that read. [outcome] is read and written only on [mainDispatcher]. [onSettled] runs once, when it leaves
 * [Outcome.LOADING].
 */
internal class RestoreCoordinator(
    private val repository: PlaybackStateRepository,
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val onSettled: () -> Unit,
) {
    enum class Outcome { LOADING, APPLIED, DISCARDED, HANDED_TO_RESUMPTION }

    var outcome: Outcome = Outcome.LOADING
        private set

    private val firstLoad: Deferred<SavedPlayback?> = scope.async { loadOrNull() }

    /** The in-app restore (spec §7.2): applies paused unless a queue arrived first or a resumption took it. */
    fun restoreInto(player: Player, restoredOrder: RestoredShuffleOrder, holder: PlaybackStateHolder): Job =
        scope.launch {
            val saved = firstLoad.await()
            withContext(mainDispatcher) {
                if (outcome != Outcome.LOADING) return@withContext
                outcome = if (saved == null || player.mediaItemCount > 0) {
                    Outcome.DISCARDED
                } else {
                    player.prepareRestore(saved, restoredOrder)
                    player.setMediaItems(saved.songs.map { it.toMediaItem() }, saved.state.currentIndex, saved.state.positionMs)
                    player.prepare()
                    holder.setPositionMs(saved.state.positionMs)
                    Outcome.APPLIED
                }
                onSettled()
            }
        }

    /**
     * What [PlaybackSessionCallback.onPlaybackResumption] resumes. While loading it takes the first load and the
     * in-app restore stands down; once settled it reads the database again (ruling R6). Call it on [mainDispatcher].
     */
    suspend fun savedForResumption(): SavedPlayback? {
        if (outcome != Outcome.LOADING) return loadOrNull()
        outcome = Outcome.HANDED_TO_RESUMPTION
        onSettled()
        return firstLoad.await()
    }

    private suspend fun loadOrNull(): SavedPlayback? = try {
        repository.load()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Loading the saved playback queue failed", e)
        null
    }

    private companion object {
        const val TAG = "RestoreCoordinator"
    }
}

/** Ruling R3: arm the saved order and set the modes BEFORE the items, so the saved order lands after the enforcer's. */
internal fun Player.prepareRestore(saved: SavedPlayback, restoredOrder: RestoredShuffleOrder) {
    restoredOrder.arm(saved.shuffleOrder)
    repeatMode = saved.state.repeatMode.toPlayerRepeatMode()
    shuffleModeEnabled = saved.state.shuffleEnabled
}
```

Spec §8 says a failed load clears the saved state. `loadOrNull` doesn't clear: a transient I/O error must not destroy a good save. The repository already clears when no song survives. Record this as a deviation note in the report; the controller rules on it in Task 7.

Run: `./gradlew :core:media:testDebugUnitTest --tests "*RestoreCoordinatorTest*"`. Expected: PASS (6 tests). Apply and revert each mutation.

- [ ] **Step 3: Write the failing session-callback tests**

`PlaybackSessionCallbackTest.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaybackStateRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ExecutionException
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackSessionCallbackTest {

    private lateinit var player: ExoPlayer
    private lateinit var restoredOrder: RestoredShuffleOrder
    private val repository = FakePlaybackStateRepository()
    private val session = mockk<MediaSession>()
    private val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setShuffleOrder(DefaultShuffleOrder(0, 1L))
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        restoredOrder = RestoredShuffleOrder(player) { 99L }
        player.addListener(restoredOrder)
        every { session.player } returns player
    }

    @After
    fun tearDown() = player.release()

    private fun song(i: Int) = Song(
        id = "s$i",
        title = "T$i",
        artist = "A",
        album = "B",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/$i",
        artworkUri = "",
    )

    private fun TestScope.callback(): PlaybackSessionCallback {
        val main = StandardTestDispatcher(testScheduler)
        val coordinator = RestoreCoordinator(repository, backgroundScope, main) {}
        return PlaybackSessionCallback(backgroundScope, main, coordinator, restoredOrder)
    }

    // Spec §7.6. After Media3 sets the returned items (simulated as MediaSessionImpl does), the saved order and modes hold.
    // Mutations: startIndex 0 → RED; skip prepareRestore (no arm) → the enforcer's order → RED; drop the position → RED.
    @Test
    fun resumptionReturnsTheSavedQueueAndRestoresItsOrderAndModes() = runTest {
        repository.saved = SavedPlayback(
            songs = (0 until 4).map(::song),
            shuffleOrder = listOf(2, 0, 3, 1),
            state = SavedPlaybackState(currentIndex = 3, positionMs = 61_000L, shuffleEnabled = true, RepeatMode.ONE),
        )

        val future = callback().onPlaybackResumption(session, controller)
        advanceUntilIdle()
        val result = future.get()
        player.setMediaItems(result.mediaItems, result.startIndex, result.startPositionMs)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals((0 until 4).map { "s$it" }, result.mediaItems.map { it.mediaId })
        assertEquals(3, result.startIndex)
        assertEquals(61_000L, result.startPositionMs)
        assertEquals(listOf(2, 0, 3, 1), player.queueState().items.map { it.index })
        assertEquals(true, player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
    }

    // Mutation: return an empty MediaItemsWithStartPosition instead of failing → future.get() succeeds → RED.
    @Test
    fun nothingSaved_failsTheFuture() = runTest {
        val future = callback().onPlaybackResumption(session, controller)
        advanceUntilIdle()

        val failure = runCatching { future.get() }.exceptionOrNull()
        assertTrue(failure is ExecutionException)
    }
}
```

- [ ] **Step 4: Implement the callback**

`PlaybackSessionCallback.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Serves Media3's playback resumption (queue-restore spec §7.6, ruling R1). Media3 1.4.1 calls it for any Play on an
 * empty player: from our own MediaController (the app, the widget), a media button, or the System UI. It then sets
 * the returned items and plays.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackSessionCallback(
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val coordinator: RestoreCoordinator,
    private val restoredOrder: RestoredShuffleOrder,
) : MediaSession.Callback {

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaItemsWithStartPosition>()
        scope.launch(mainDispatcher) {
            try {
                val saved = coordinator.savedForResumption()
                if (saved == null) {
                    future.setException(IllegalStateException("No saved queue to resume"))
                } else {
                    mediaSession.player.prepareRestore(saved, restoredOrder)
                    future.set(
                        MediaItemsWithStartPosition(
                            saved.songs.map { it.toMediaItem() },
                            saved.state.currentIndex,
                            saved.state.positionMs,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
```

- [ ] **Step 5: Run and report**

Run: `./gradlew :core:media:check spotlessApply`. Expected: BUILD SUCCESSFUL with 8 new tests, and every named mutation RED and then reverted. If `SettableFuture` is not on the compile classpath, report it. Don't add a dependency: Guava comes with media3-session.

---

### Task 6: Wire it into `PlaybackService` and the manifest (audio-engineer)

**Files:**
- Modify: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/PlaybackService.kt`
- Modify: `core/media/src/main/AndroidManifest.xml`
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/MediaButtonManifestTest.kt`

**Interfaces:**
- Consumes everything from Tasks 2–5.
- Produces no new API.

- [ ] **Step 1: The manifest test (failing)**

`MediaButtonManifestTest.kt`:

```kotlin
package com.rolla.musicplayer.core.media

import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaButtonReceiver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaButtonManifestTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mediaButton = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(context.packageName)

    // Spec §7.6. Mutation: delete the <receiver> → RED.
    @Test
    fun mediaButtonsReachMedia3sReceiver() {
        val receivers = context.packageManager.queryBroadcastReceivers(mediaButton, 0)
        assertTrue(receivers.any { it.activityInfo.name == MediaButtonReceiver::class.java.name })
    }

    // MediaButtonReceiver forwards to the service that handles MEDIA_BUTTON. Mutation: drop that action from the
    // service's intent filter → RED.
    @Test
    fun theServiceHandlesMediaButtons() {
        val services = context.packageManager.queryIntentServices(mediaButton, 0)
        assertTrue(services.any { it.serviceInfo.name == PlaybackService::class.java.name })
    }
}
```

Run it. Expected: both FAIL.

- [ ] **Step 2: The manifest**

`core/media/src/main/AndroidManifest.xml`. In the service's `<intent-filter>`, add:

```xml
                <action android:name="android.intent.action.MEDIA_BUTTON" />
```

Then, inside `<application>` after the service, add:

```xml
        <!-- Playback resumption (queue restore, spec §7.6): a headset or Bluetooth Play after the process died is
             delivered here, and Media3 starts PlaybackService and calls onPlaybackResumption. -->
        <receiver
            android:name="androidx.media3.session.MediaButtonReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MEDIA_BUTTON" />
            </intent-filter>
        </receiver>
```

Run the test. Expected: PASS.

- [ ] **Step 3: Wire the service**

In `PlaybackService.kt`:

1. Inject the repository:

```kotlin
    @Inject
    lateinit var playbackStateRepository: PlaybackStateRepository
```

2. Add the fields next to `shuffleEnforcer`:

```kotlin
    // Queue restore (spec 2026-10-12): restoredShuffleOrder is registered after the enforcer (ruling R3).
    private lateinit var restoredShuffleOrder: RestoredShuffleOrder
    private lateinit var queuePersister: QueuePersister
    private lateinit var restoreCoordinator: RestoreCoordinator
```

3. In `onCreate`, replace `mediaSession = MediaSession.Builder(this, player).build()` with a call to a new function, keeping everything else in place:

```kotlin
        player.addListener(shuffleEnforcer)
        mediaSession = installQueueRestore()
        startPositionTicker()
```

```kotlin
    // Listener order matters: the enforcer is already registered, so restoredShuffleOrder runs after it (ruling R3).
    // The persister starts writing only once the restore settles (spec §6.3).
    private fun installQueueRestore(): MediaSession {
        restoredShuffleOrder = RestoredShuffleOrder(player)
        player.addListener(restoredShuffleOrder)
        queuePersister = QueuePersister(player, playbackStateRepository, serviceScope)
        player.addListener(queuePersister)
        val main = Dispatchers.Main.immediate
        restoreCoordinator = RestoreCoordinator(playbackStateRepository, serviceScope, main, queuePersister::enable)
        val session = MediaSession.Builder(this, player)
            .setCallback(PlaybackSessionCallback(serviceScope, main, restoreCoordinator, restoredShuffleOrder))
            .build()
        restoreCoordinator.restoreInto(player, restoredShuffleOrder, playbackStateHolder)
        return session
    }
```

4. In `startPositionTicker`, inside the `withContext(Dispatchers.Main.immediate)` block's playing branch, after `playbackStateHolder.setPositionMs(...)`, add:

```kotlin
                        queuePersister.onPlayingTick()
```

5. In `onDestroy`:
   - Add `player.removeListener(restoredShuffleOrder)` and `player.removeListener(queuePersister)` beside the other `removeListener` calls.
   - Read the final state before them: `val finalState = queuePersister.finalState()`. It must be read while the persister is still attached and the player still holds its items.
   - Inside the existing `runBlocking { … }`, before `equalizerBindJob?.cancelAndJoin()`, add:

```kotlin
            // Ruling R7: the last position, bounded so a slow disk can't hold up the service teardown.
            finalState?.let { state ->
                withTimeoutOrNull(FINAL_SAVE_TIMEOUT_MS) {
                    runCatching { playbackStateRepository.saveState(state) }
                        .onFailure { Log.e(TAG, "Final playback save failed", it) }
                }
            }
```

   - Add `private const val FINAL_SAVE_TIMEOUT_MS = 500L` to the companion object, and import `kotlinx.coroutines.withTimeoutOrNull` and the repository.

`runCatching` swallows `CancellationException` inside `withTimeoutOrNull`. Here that is harmless: on timeout `withTimeoutOrNull` returns null either way. If detekt objects, use the explicit try/catch pattern from `QueuePersister.drain`.

- [ ] **Step 4: Consumer audit**

- `grep -rn "MediaSession.Builder" core feature app` must show only `installQueueRestore`.
- `grep -rn "addListener" core/media/src/main/kotlin/com/rolla/musicplayer/core/media/PlaybackService.kt` must show this order: `playerListener`, `queuePublisher`, `shuffleEnforcer`, `restoredShuffleOrder`, `queuePersister`.
- Check that `onCreate` and `onDestroy` stay within detekt's `LongMethod` (30) without a new suppression.
- `:feature:widget`'s `MusicWidgetActions` calls `controller.connect()` and then a transport command. On an empty player, its Play now reaches `onPlaybackResumption` (ruling R1). There is no code change there; note it in the report.

- [ ] **Step 5: Run and report**

Run: `./gradlew :core:media:check :app:assembleDebug spotlessApply`. Expected: BUILD SUCCESSFUL. `checkReleaseManifestNoInternet` must still pass, and the receiver adds no permission.

---

### Task 7: Docs (controller)

- [ ] **Step 1: Amend the spec** (`docs/superpowers/specs/2026-10-12-queue-restore-design.md`):
  - §5.1 and §5.3: no foreign key and no `song_id` index; `loadQueue` is a `LEFT JOIN`; add `loadSnapshot`; drop the cascade paragraph and the `@Upsert` dependence (R2, R8).
  - §5.4: the `SavedPlayback`/`SavedPlaybackState` shape and `saveQueue(songIds, …)` (R4).
  - §6.1: drop the `onMediaItemTransition` row (R9).
  - §7.2: the apply order (R3).
  - §7.3: replace the gate with ruling R1's Media3 routing.
  - §7.4: computed from `LEFT JOIN` rows (R2).
  - §7.6: resumption reloads after settling (R6).
  - §8: rule on Task 5's note about not clearing on a failed load.
  - §9: drop the gate and the `upsertSongs`-keeps-the-row tests, and add the tests this plan adds.
  - Cite the ruling at each change.
- [ ] **Step 2: CLAUDE.md**
  - Line "Playback State": "Queue, current track, position, shuffle order and repeat mode are saved in Room (v5 `playback_queue`/`playback_state`) and restored paused on the next start; Play from a headset, Bluetooth or the System UI resumes them without opening the app."
  - Core Features: "Persistent playback queue across app restarts (restored paused; resumable from headset/system controls)".
  - Redesign banner: add a "Queue restore (side phase)" bullet. It names Room **v5**, `PlaybackQueueDao`, `PlaybackStateRepository`, `QueuePersister`, `RestoreCoordinator`, `RestoredShuffleOrder`, `PlaybackSessionCallback` and `MediaButtonReceiver`.
  - Update every remaining "Room v4" mention that describes the current version.
- [ ] **Step 3: `.claude/rules/model-vocabulary.md`:** add `SavedPlayback` and `SavedPlaybackState` to the canonical models block.
- [ ] **Step 4: Ledger:** record the spec amendments and the R-rulings in the SDD ledger.

### Task 8: Gate (controller)

- [ ] Run `./gradlew check assembleDebug :core:database:compileDebugAndroidTestKotlin --continue` in the background, logged to the workspace. Expected: BUILD SUCCESSFUL. Fix forward through the owning task's implementer if not.

### Task 9: Device check on the SM-S901B (controller)

Safety order, the same as Phase 4b:
- Never uninstall the app or clear its data.
- Never touch the user's playlists, Equaliser presets or settings.
- Use only the Samsung (serial R3CTA03TZTY). Stop and ask if a different device is attached.

- [ ] **Step 1:** Run `./gradlew :core:database:connectedDebugAndroidTest` (`ANDROID_SERIAL=R3CTA03TZTY`). It installs only the test APK. Every test must pass; stop if anything fails.
- [ ] **Step 2:** Device mutation proofs: Task 1's listed mutations, one at a time, restored byte-exact (the Phase 4b `device_mutations.py` approach). The working tree must then equal its pre-mutation snapshot.
- [ ] **Step 3:** Run `./gradlew :app:assembleBenchmark`. Check the certificate with `apksigner verify --print-certs` (it must be the debug cert, SHA-256 `2950a9a2…`). Then `install -r` the benchmark APK and run `cmd package compile -f -m speed-profile com.rolla.musicplayer`. `firstInstallTime` must be unchanged. Launch the app: Playlists (74/74, no user playlists) and Folders ("Music") are unchanged, and the crash buffer is empty.
- [ ] **Step 4, in-app restore:**
  1. Open the Music folder and tap Shuffle.
  2. Skip forward twice, open Now Playing, seek to about 1:23, and pause.
  3. Screenshot the queue sheet.
  4. Press Home and wait until the process is cached (`dumpsys activity processes` shows a cached state), then `am kill com.rolla.musicplayer`. Confirm with `pidof` that the PID changed or is gone.
     - If `am kill` can't kill it because the media service keeps it alive, swipe the app from Recents first. Playback is paused here, so Media3's `onTaskRemoved` stops the service. Don't use `force-stop` for this step: a stopped app ignores media buttons.
  5. Reopen the app. The mini-player shows the same track paused, Now Playing shows about 1:23, and the queue sheet's order matches the screenshot.
  6. Check the deleteIntent STOP restart (spec §12 P8). After the kill while paused (4.4), the reopened app's Now Playing shows the track's real duration, not 0:00, because the player is prepared, and `dumpsys media_session` reports the session state as PAUSED, not NONE.
     - Before reopening, a few seconds after the kill, `dumpsys activity services com.rolla.musicplayer` should list no started `PlaybackService`: the STOP restart stops itself once its restore settles.
- [ ] **Step 5, the 10-second save:** Play, wait 15 s, then run `am force-stop com.rolla.musicplayer` *while playing* (`am kill` cannot kill a process that holds a foreground service). Reopen the app from the launcher, which clears the stopped state. The position is within 10 s of where it was. A force-stop skips `onDestroy`, so this checks the 10-second tick, not `saveFinal` (the JVM tests pin `saveFinal`).
  - Swipe-away while playing: play, then swipe the app from Recents. Check only that nothing crashes and playback continues. Media3's `onTaskRemoved` stops the service only when playback is not ongoing, so this does not reach `saveFinal`.
- [ ] **Step 6, resume from outside the app:**
  1. Pause, then kill the app as in step 4.4 (not `force-stop`: a stopped app ignores media buttons).
  2. Run `adb shell input keyevent KEYCODE_MEDIA_PLAY`. Playback resumes without the app's activity opening (check with `dumpsys activity activities | grep topResumedActivity`), in the saved shuffled order (`dumpsys media_session`). `input keyevent` sends a key DOWN and a key UP back to back, so this exercises the key-UP branch with a resumption in flight: the crash buffer must stay empty. The cold UP-only crash path (final review I2: nothing saved, warm process, an UP arriving after the fallback stopped the service) cannot be timed with `input keyevent`; `MediaButtonStartFallbackTest` pins it.
  3. Pause.
  4. Then, with the app killed again, place nothing new, open the app's widget (if it is on a home screen), and tap Play. Playback resumes (ruling R1). If no widget is placed, record that this step was not exercised.
- [ ] **Step 7, clean up:** pause playback. Save the screenshots in `docs/design/calibration/queue-restore/` (uncommitted). Then ledger the acceptance.

## Self-review

**Spec coverage:**

| Spec | Task |
|---|---|
| §2 criteria 1–4 (paused, position, queue order, modes) | 5 (coordinator), 6 (wiring), 9 steps 4–5 |
| §2 criterion 5 (resume from outside) | 5 (callback), 6 (manifest), 9 step 6 |
| §5.1/5.2 tables and migration | 1 (as amended by R2) |
| §5.3 DAO | 1 |
| §5.4 model and repository | 2 (as amended by R4) |
| §6.1 triggers | 4 (every row of the table) |
| §6.2 coalescing | 4 (`aFiveThousandTrackQueueIsOneWrite`) |
| §6.3 rules (empty clears, nothing while loading, failures dropped) | 4 |
| §7.1 one shared restore | 5 |
| §7.2 in-app restore, holder position | 5 |
| §7.3 Play during restore | R1 (Media3 routes it); 9 step 6 |
| §7.4 missing songs | 2 |
| §7.5 exact shuffle order | 3, 5 |
| §7.6 resumption and manifest | 5, 6 |
| §8 error handling | 2 (corrupt rows), 4 (write failure), 5 (load failure), 6 (bounded final save) |
| §9 tests and device acceptance | 1–6, 9 |
| §10 docs | 7 |

**Value tables (observation 0007a):**
- Spec §5.1's columns appear verbatim in Task 1's entities and SQL, except the `song_id` foreign key and index, which R2 removes.
- §6.1's triggers are each an override or a call in Task 4: `onTimelineChanged`, `onIsPlayingChanged(false)`, `onPositionDiscontinuity`, `onShuffleModeEnabledChanged`, `onRepeatModeChanged`, `onPlayingTick`, plus `finalState` for `onDestroy`.
- `onMediaItemTransition` is deliberately absent (R9). Task 7 amends §6.1's table.

**Consumers (observation 0007b):**
- Task 3 moves `buildMediaItem`; `PlaybackController` is its only consumer (audited by grep).
- Task 6 changes the session's callback. The widget and every `MediaController` now resume through it on an empty player; that is noted, and checked in Task 9 step 6.
- `ALL_MIGRATIONS` consumers (`DatabaseModule`, `MigrationTest`) are both updated in Task 1.

**Can each test fail? (observation 0009):** every test above names the single change that turns it RED. Two tests could be blind, and they were checked:
- `onlyAPauseWritesTheState` calls the listener directly, so it pins the `isPlaying` branch but not Media3's delivery of `onIsPlayingChanged`. That delivery is Media3's contract, and the device check covers the end-to-end path.
- `MediaButtonManifestTest` depends on Robolectric merging the library manifest (`isIncludeAndroidResources = true`, set by `rolla.android.robolectric`). If it passes with the receiver deleted, it is blind: move the probe to `:app`'s merged manifest.

**Placeholders:** none. Every code step has its code, and every test step has its test code and its mutation.

**Type consistency:** these names were checked across Tasks 1–6:
- `PlaybackQueueEntity(slot, songId, shuffleRank)` and `PlaybackStateEntity(id, currentSlot, positionMs, shuffleEnabled, repeatMode, savedAt)`
- `QueuedSongRow(slot, shuffleRank, songId, song)`, `PlaybackSnapshot(queue, state)` and `restoredPlayback(rows, state)`
- `SavedPlayback(songs, shuffleOrder, state)` and `SavedPlaybackState(currentIndex, positionMs, shuffleEnabled, repeatMode)`
- `saveQueue(songIds, shuffleOrder, state)`
- `RestoredShuffleOrder.arm/isArmed` and `QueuePersister.enable/onPlayingTick/finalState`
- `RestoreCoordinator.restoreInto/savedForResumption/outcome` and `prepareRestore`
- `PlaybackSessionCallback(scope, mainDispatcher, coordinator, restoredOrder)`
