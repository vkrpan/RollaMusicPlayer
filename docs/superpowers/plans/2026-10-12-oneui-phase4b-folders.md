# One UI redesign, Phase 4b: Folders — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home gains a sixth tab, Folders, listing every folder that directly holds songs, and a FolderDetail screen that plays them. Both rest on a new `songs.folder_path` column (Room v3 → v4) that the scanner fills and the indexer backfills.

**Architecture:**
- **Data:** Room v4 adds a nullable `folder_path` column: `NULL` means not scanned yet, `''` is the volume root.
  - The scanner derives the path from `RELATIVE_PATH` (API 29+) or `DATA` (API 24–28) through pure `FolderPaths` functions.
  - `LibraryIndexer` re-upserts any row whose stored folder differs from the scanned one. This backfills an upgraded install on its first sync.
  - A new `FolderDao` (not `SongDao`, which is at detekt's interface limit) groups songs by path. `FolderRepository` maps the rows to a new `Folder` model, sorted by display name.
- **UI:**
  - `:core:ui` gets `FolderRow`, built on a row scaffold extracted from `AlbumRow`/`ArtistRow`, with a new `FolderBadge` from `:core:designsystem`.
  - `:feature:library` gets `FoldersTab` and `FolderDetail` (a One UI pushed screen).
  - `:app` adds the tab, the `FolderDetail(folderPath)` route and the navigation.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.09.03 (Foundation 1.7.3, M3 1.3.0), Room 2.6.1 (KSP, exported schemas), Navigation Compose 2.8.3 type-safe routes, Hilt, Coil 2.7.0, JUnit4 + MockK + Turbine, Robolectric 4.13 (NATIVE graphics for geometry and pixels), instrumented Room tests on the Samsung SM-S901B.

**Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md). The relevant sections are §7.1 (route), §7.3 (pill and insets), §8.1 (`FolderRow`), §8.2 (Folders tab), §8.7 (FolderDetail), §9.4 (data layer), §15 (tests) and §17 (exit criteria: "Migration test and `FolderPaths` tests pass. An upgraded install backfills folders on the first sync."). The roadmap is [`2026-10-06-oneui-redesign-roadmap.md`](2026-10-06-oneui-redesign-roadmap.md). Phase 4a's carry-overs are in `.superpowers/sdd/2026-10-11-oneui-phase4a-library-tabs/progress.md`.

## Global Constraints

- minSdk 24, targetSdk 34. Code that differs by API branches on `Build.VERSION.SDK_INT`, and each branch has a test.
- Room migrations are additive and tested. There is no destructive fallback: `fallbackToDestructiveMigration` must never be added. User data (playlists, favourites, play counts, Equaliser presets) survives every migration.
- UI copy: tabs and counts say "Tracks" (the model stays `Song`). The new tab is "Folders" and the volume root is "Internal storage". Use British spelling ("Favourites", "colour", "Equaliser").
- Blue text uses `accentText`, never `primary`.
- Icons come from `RollaIcons` only. No Material icons in new or rewritten files.
- No dead controls. Every visible button does something real.
- No hardcoded colors, type, shapes or dimensions outside `:core:designsystem`. New values become tokens first.
- Offline only: no network APIs or dependencies.
- Tests the phase touches are updated, never deleted to go green.
- **Mutation-proof policy.**
  - Every new test names the mutation that turns it RED. The implementer applies it, sees RED, and reverts byte-exact.
  - A mutant counts as equivalent only if *no observable output changes*. If the output changes but the test can't see it, the test is blind: move its probe.
  - A named mutation is a hypothesis. If it proves equivalent, show the evidence and substitute the closest fail-able mutation.
  - Fixtures must separate the quantities a test distinguishes. Expected answers must not coincide with insertion, id, scan or path order.
- **Agents never** run `git commit`, `git add` or `git stash`. Never `sed -i` (Spotless enforces CRLF; use the editor). Run `./gradlew spotlessApply` after edits.
- Instrumented (androidTest) code must compile here (`compileDebugAndroidTestKotlin`). It runs on the device in Task 10, by the controller only.
- `:core:testing` stays `testImplementation` only.

## Rulings (decided at planning; the spec is amended in Task 9)

1. **`FolderDao`, not `SongDao`.** `SongDao` has 12 functions, which is detekt's `thresholdInInterfaces: 12` (this was a Phase 4a carry-over). The folder queries go in a new `FolderDao`, registered like `AlbumDao`/`ArtistDao`. Spec §9.4 names `SongDao.observeFolders()`.
2. **Folders sort by display name.**
   - Spec §9.4 orders by path, but the header reads "Name" and Phase 5's A–Z rail builds its sections from the displayed names. A path order would make the sections non-monotonic: "Music/Abba" shows as "Abba" but would sort under M.
   - `FolderRepositoryImpl` therefore sorts by name case-insensitively, then by path case-insensitively, then by path exactly. The DAO keeps a deterministic path order.
   - Like NOCASE, `String.CASE_INSENSITIVE_ORDER` does not fold accents. That is the Phase 5 Unicode-collation carry-over, which applies here too.
3. **Re-scan rule.**
   - Spec §9.4 marks a row stale when it is new, its `dateModified` changed, or `existing.folderPath == null`. This plan uses `existing.folderPath != scanned.folderPath` instead.
   - That covers the null backfill and also a file moved between folders without its mtime changing; moves usually keep the mtime.
   - Root songs (`''` == `''`) are still not rewritten on later syncs.
4. **Folder naming helpers live in `:core:model`.** `folderName(path)` and `folderDisplayPath(path)` are pure top-level functions in `FolderNames.kt`. The repository (name), `FolderRow` (display path) and `FolderDetailViewModel` (title from the route) share them.
5. **The folder badge is measured from the reference.**
   - The reference (`media player example/Screenshot_Folder_Tree.jpg`) shows a white folder outline over a black fill, about 14 × 11 dp, about 5 dp from the thumbnail's end and 6 dp from its bottom. These figures assume 3.0 px/dp; Phase 7 calibrates them.
   - New tokens: `RollaDimens.folderBadge = 20.dp` (the icon box), `RollaDimens.folderBadgeInset = 2.dp`, and colors `folderBadgeStroke` = `#FFFFFF` and `folderBadgeFill` = `#000000`. Both colors are the same in both themes, because the badge sits on artwork, not on a theme surface.
   - New solid icon `RollaIcons.FolderBadgeFill` (the outline's shape, filled). New component `FolderBadge` in `:core:designsystem`.
6. **Row scaffold extracted.** `ListRowScaffold` (in `:core:ui`) holds the two-line row anatomy that `AlbumRow`, `ArtistRow` and the new `FolderRow` share. This was a Phase 4a carry-over. The existing `AlbumRowTest` and `ArtistRowTest` (named in Task 5) are the safety net.
7. **FolderDetail is built on the One UI kit now:** `OneUiDetailTopBar`, `ContentPanel`, `SortHeader` with Shuffle and Play, `SongListItem` rows, and `SongOptionsHost`.
   - It is **not** wrapped in `PushedScreen`. Its list pads by `homeListBottomPadding()`, so it scrolls under the pill (spec §7.3).
   - The older detail screens keep `PushedScreen` until Phase 6.
8. **FolderDetail's ⋮ is real.** It opens the shared options sheet (Add to playlist, Edit tags).
   - `FolderDetailViewModel` copies `FavouritesTabViewModel`'s play and playlist actions. That is the third copy after Tracks and Favourites; extracting them is a Phase 6 carry-over.
   - Per the Phase 4a lesson, the precedent's tests are mirrored one-to-one (Task 7 lists them).
9. **The Folders tab's sort header has no Shuffle or Play**, matching the reference screenshot. Spec §8.2 lists only `SortHeader("Name")` too.
10. **Songs in a folder sort by `title COLLATE NOCASE`**, the same as Tracks. This extends Phase 4a's case-insensitive rule. Spec §9.4 says "orders by title".
11. **Robolectric for `:core:data`.** Add `testImplementation(libs.robolectric)` and `testImplementation(libs.androidx.junit)` directly, plus `src/test/resources/robolectric.properties` with `sdk=34`. Do not use the `rolla.android.robolectric` plugin: it adds Compose test dependencies to a non-Compose module. The android-all jars for API 28, 29 and 34 are already cached.
12. **Root route argument.** The root folder's path is `''`. Navigation 2.8.3 builds path arguments with `([^/]*?|)`, whose source comment says "allows for empty path arguments". Task 8 pins this with a round-trip test.
    - If that test is RED on `''`, stop and report BLOCKED with the failure.
    - Do not invent an encoding; the controller rules on it.
13. **Device upgrade safety (Task 10):** the order and the backup are spelled out there.
    - The connected `:core:database` tests, migration included, must pass on the Samsung before the 4b app is installed over the 4a build.
    - If the installed build is debuggable, back up the database first.
14. **Deferred:** `FakePlaylistDao` NOCASE fidelity goes to Phase 6. It is not Folders scope.
15. **Folders group by the exact path** (binary `GROUP BY folder_path`), and the lists key folders by path, which is unique.
16. **DATA prefixes.** Besides spec §9.4's three (`/storage/emulated/<n>/`, `/storage/<UUID>/`, `/sdcard/`), the parser accepts `/storage/self/primary/` and `/mnt/sdcard/`, which real devices report. A path with no known prefix maps to `''`, as the spec says.

## Review Focus

1. **Songs at the volume root** (`folder_path = ''`) appear as one "Internal storage" folder with the subtitle "/Internal storage". It opens FolderDetail (an empty-string route argument) listing exactly those songs, and later syncs never rewrite them. Pinned by:
   - `FolderNamesTest`, for the names
   - `FolderDaoTest`, root group and root songs
   - `LibraryIndexerTest.sync_rootSong_isNotRewrittenOnTheSecondSync`
   - `FolderDetailRouteTest`, the empty path
2. **An upgraded install with real data.** v3 → v4 keeps every song row, the user state and the playlists. `folder_path` starts as `NULL`, the app opens through the same migration list `DatabaseModule` installs, and the first sync fills it. Pinned by:
   - `MigrationTest.migrate3To4_*`
   - `MigrationTest.allMigrations_*`
   - `LibraryIndexerTest.sync_unscannedFolderPath_*`
   - the device install in Task 10
3. **Two folders with the same name** ("Music" and "Download/Music") are two rows that each open their own songs. The list does not crash on a duplicate key, and the order is deterministic. Pinned by:
   - `FoldersTabTest`, same-name folders
   - `FolderRepositoryImplTest`, the same-name tie-break
4. **A file moved to another folder without its mtime changing** shows in its new folder after the next sync. Pinned by `LibraryIndexerTest.sync_movedSongWithUnchangedMtime_takesItsNewFolder` (ruling 3).
5. **Folder names with spaces, `&`, `#`, `?`, `%` and non-ASCII letters** survive navigation. A folder emptied by a rescan while it is open says "No tracks in this folder", with Play and Shuffle disabled. Pinned by:
   - `FolderDetailRouteTest`, special characters
   - `FolderDetailScreenTest`, the emptied folder

---

## File map

| Module | Create | Modify |
|---|---|---|
| `:core:database` | `migration/Migration3To4.kt`, `migration/Migrations.kt`, `dao/FolderDao.kt`, `relation/FolderSummary.kt`, `schemas/…/4.json` (generated), androidTest `dao/FolderDaoTest.kt` | `entity/SongEntity.kt`, `MusicDatabase.kt`, `di/DatabaseModule.kt`, androidTest `migration/MigrationTest.kt` |
| `:core:data` | `scanner/FolderPaths.kt`, `repository/FolderRepository.kt`, `repository/FolderRepositoryImpl.kt`, test `scanner/FolderPathsTest.kt`, test `scanner/MediaScannerReadTest.kt`, test `repository/FolderRepositoryImplTest.kt`, `src/test/resources/robolectric.properties` | `scanner/MediaStoreColumns.kt`, `scanner/ScannedSong.kt`, `scanner/MediaScanner.kt`, `scanner/LibraryIndexer.kt`, `di/DataModule.kt`, `build.gradle.kts`, test `scanner/LibraryIndexerTest.kt` |
| `:core:model` | `Folder.kt`, `FolderNames.kt`, test `FolderNamesTest.kt` | — |
| `:core:testing` | `FakeFolderRepository.kt` | — |
| `:core:designsystem` | `component/FolderBadge.kt`, test `component/FolderBadgeTest.kt` | `theme/Color.kt`, `theme/Dimens.kt`, `icon/RollaIcons.kt`, `component/KitPreviews.kt`, tests `icon/RollaIconsTest.kt`, `theme/ContrastTest.kt`, `theme/ThemeTokensTest.kt` (if it pins palette values) |
| `:core:ui` | `ListRowScaffold.kt`, `FolderRow.kt`, test `FolderRowTest.kt` | `AlbumRow.kt`, `ArtistRow.kt`, `ArtworkThumbnail.kt` (KDoc) |
| `:feature:library` | `FoldersTab.kt`, `FoldersTabViewModel.kt`, `FolderDetailScreen.kt`, `FolderDetailViewModel.kt`, tests `FoldersTabTest.kt`, `FoldersTabViewModelTest.kt`, `FolderDetailScreenTest.kt`, `FolderDetailViewModelTest.kt` | — |
| `:app` | test `navigation/FolderDetailRouteTest.kt` | `home/HomeTab.kt`, `home/HomeScreen.kt`, `navigation/Routes.kt`, `navigation/MiniPlayerVisibility.kt`, `MainActivity.kt`, tests `home/HomeTabTest.kt`, `home/HomeScreenTest.kt`, `navigation/MiniPlayerVisibilityTest.kt` |
| docs | — | `CLAUDE.md`, spec §5.1/§5.4/§5.5/§8.1/§9.4, roadmap row 4b, `.claude/rules/model-vocabulary.md`, `.claude/rules/navigation-conventions.md` |

Paths below are relative to each module's `src/main/kotlin/com/rolla/musicplayer/<module path>/` unless written in full.

---

### Task 1: Room v4: the `folder_path` column, migration and `FolderDao` (data-layer-agent)

**Files:**
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/SongEntity.kt`
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/migration/Migration3To4.kt`
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/migration/Migrations.kt`
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/relation/FolderSummary.kt`
- Create: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/FolderDao.kt`
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/MusicDatabase.kt`
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/di/DatabaseModule.kt`
- Generated: `core/database/schemas/com.rolla.musicplayer.core.database.MusicDatabase/4.json`
- Test (androidTest, compile here, run in Task 10): `core/database/src/androidTest/kotlin/com/rolla/musicplayer/core/database/migration/MigrationTest.kt`, new `core/database/src/androidTest/kotlin/com/rolla/musicplayer/core/database/dao/FolderDaoTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `SongEntity.folderPath: String?` (last constructor parameter, default `null`)
  - `val MIGRATION_3_4: Migration`
  - `val ALL_MIGRATIONS: Array<Migration>`
  - `data class FolderSummary(val path: String, val songCount: Int, val artworkUri: String)`
  - `interface FolderDao { fun observeFolders(): Flow<List<FolderSummary>>; fun observeSongsInFolder(path: String): Flow<List<SongEntity>> }`
  - `MusicDatabase.folderDao()`
  - a Hilt `FolderDao` provider

- [ ] **Step 1: Add the column to `SongEntity`.** It is the last property, after `lastPlayed`:

```kotlin
    // The song's folder relative to its storage volume, "/"-separated with no leading or trailing slash ("Music/Chill").
    // "" is the volume root; null means not scanned since the v4 migration, and LibraryIndexer fills it (spec §9.4).
    @ColumnInfo(name = "folder_path", defaultValue = "NULL")
    val folderPath: String? = null,
```

`defaultValue = "NULL"` makes the exported schema match what `ALTER TABLE … DEFAULT NULL` creates, so `runMigrationsAndValidate` compares like with like. Leave `toDomain()` and `Song.toEntity(...)` unchanged: the domain `Song` does not gain the field (spec §9.4).

- [ ] **Step 2: Write the migration and the shared list.**

`migration/Migration3To4.kt`:

```kotlin
package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 -> v4: adds `songs.folder_path` for the Folders tab (redesign Phase 4b, spec §9.4).
 *
 * Purely additive. Every existing row gets NULL, which means "not scanned yet": the next LibraryIndexer.sync() fills it
 * from MediaStore. No other table or column is touched, so playlists, favourites and play counts carry over as they are.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE songs ADD COLUMN folder_path TEXT DEFAULT NULL")
    }
}
```

`migration/Migrations.kt`:

```kotlin
package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration

/** Every migration, oldest first. DatabaseModule installs exactly this list, and MigrationTest replays it from v1. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```

In `DatabaseModule.provideDatabase`, replace `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` with `.addMigrations(*ALL_MIGRATIONS)`, and drop the two now-unused imports.

- [ ] **Step 3: Write `FolderSummary` and `FolderDao`.**

`relation/FolderSummary.kt`:

```kotlin
package com.rolla.musicplayer.core.database.relation

/**
 * One row of FolderDao.observeFolders: a folder path (relative to its volume; "" is the root), how many songs sit
 * directly in it, and the cover of its alphabetically first song.
 */
data class FolderSummary(
    val path: String,
    val songCount: Int,
    val artworkUri: String,
)
```

`dao/FolderDao.kt`:

```kotlin
package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.FolderSummary
import kotlinx.coroutines.flow.Flow

/**
 * Folder queries over `songs.folder_path` (spec §9.4): the Folders tab and FolderDetail.
 *
 * A separate interface from [SongDao], which is at detekt's TooManyFunctions interface threshold, registered on
 * MusicDatabase like [AlbumDao] and [ArtistDao]. Rows whose folder_path is still NULL (not scanned since the v4
 * migration) belong to no folder until LibraryIndexer backfills them.
 */
@Dao
interface FolderDao {

    /**
     * Every folder that directly holds songs, grouped by exact path, ordered by path case-insensitively and then
     * exactly. artworkUri is the cover of the folder's alphabetically first song: the inner query has exactly one
     * MIN() aggregate, so SQLite takes the bare artwork_uri column from that row (the same documented behaviour
     * ArtistDao.observeArtists relies on).
     */
    @Query(
        """
        SELECT path, songCount, artworkUri FROM (
            SELECT
                folder_path AS path,
                COUNT(*) AS songCount,
                artwork_uri AS artworkUri,
                MIN(title COLLATE NOCASE) AS firstTitle
            FROM songs
            WHERE folder_path IS NOT NULL
            GROUP BY folder_path
        )
        ORDER BY path COLLATE NOCASE ASC, path ASC
        """,
    )
    fun observeFolders(): Flow<List<FolderSummary>>

    /** The songs directly in [path] (not in its subfolders), by title case-insensitively, the same order as Tracks. */
    @Query("SELECT * FROM songs WHERE folder_path = :path ORDER BY title COLLATE NOCASE ASC")
    fun observeSongsInFolder(path: String): Flow<List<SongEntity>>
}
```

In `MusicDatabase`:
- Set `version = 4`.
- Add `abstract fun folderDao(): FolderDao`, with a one-line comment in the style of the others. Note that this DAO comes *with* a schema change, the v4 column.

In `DatabaseModule`, add `@Provides fun provideFolderDao(database: MusicDatabase): FolderDao = database.folderDao()`.

- [ ] **Step 4: Build to export the schema.** Run `./gradlew :core:database:assembleDebug`. Expected: `core/database/schemas/com.rolla.musicplayer.core.database.MusicDatabase/4.json` exists, and its `songs` table lists `folder_path` with `"affinity": "TEXT"`, `"notNull": false` and `"defaultValue": "NULL"`. If `defaultValue` differs, report it; do not hand-edit the JSON.

- [ ] **Step 5: Write the migration tests** (append to `MigrationTest.kt`; add imports `androidx.room.Room` and `com.rolla.musicplayer.core.database.migration.ALL_MIGRATIONS` as needed). The v3 insert lists every v3 `songs` column. Read them from `3.json`, and fix the list if it differs from the one below.

```kotlin
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

    // Review Focus 2: the list DatabaseModule installs opens a v1 file at the current version. Mutation: drop
    // MIGRATION_3_4 from ALL_MIGRATIONS → "A migration from 3 to 4 was required but not found" → RED.
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

        database.openHelper.writableDatabase.query("SELECT title, folder_path FROM songs WHERE id = '1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Song", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
    }
```

- [ ] **Step 6: Write `FolderDaoTest`.** Copy `ArtistDaoTest`'s setUp/tearDown, with `folderDao = database.folderDao()`. Use this builder:

```kotlin
    private fun song(
        id: String,
        title: String,
        folderPath: String?,
        artworkUri: String = "art-$id",
    ): SongEntity = SongEntity(
        id = id,
        mediaStoreId = id.toLong(),
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 180_000L,
        trackNumber = 1,
        year = 2024,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = artworkUri,
        dateModified = 1_000_000L,
        folderPath = folderPath,
    )
```

These are the tests. Write each with `runTest`, `songDao.upsertSongs(...)`, then `folderDao.<query>().first()`.

```kotlin
    // Fixture: in "Music" the alphabetically first title ("alpha") is the MIDDLE insert, and binary order would pick
    // "Mid" ('M' < 'a'). Mutations: drop the MIN() column → the bare column comes from an arbitrary (last) row "Mid" →
    // RED; MIN(title) without COLLATE NOCASE → "Mid" → RED; COUNT(*) → COUNT(DISTINCT album_id) → 1 → RED.
    @Test
    fun observeFolders_countsSongsAndTakesTheFirstTitlesArtwork() = runTest {
        songDao.upsertSongs(
            listOf(
                song("1", "Zeta", "Music", artworkUri = "art-zeta"),
                song("2", "alpha", "Music", artworkUri = "art-alpha"),
                song("3", "Mid", "Music", artworkUri = "art-mid"),
            ),
        )
        assertEquals(listOf(FolderSummary("Music", 3, "art-alpha")), folderDao.observeFolders().first())
    }

    // Review Focus 1. Mutations: drop `WHERE folder_path IS NOT NULL` → a NULL-path group appears (or the mapping to a
    // non-null `path` throws) → RED; treat '' as NULL (`WHERE folder_path <> ''`) → the root group is missing → RED.
    @Test
    fun observeFolders_keepsTheRootAndSkipsUnscannedRows() = runTest {
        songDao.upsertSongs(
            listOf(
                song("1", "At root", "", artworkUri = "art-root"),
                song("2", "Unscanned", null),
                song("3", "In music", "Music", artworkUri = "art-music"),
            ),
        )
        assertEquals(
            listOf(FolderSummary("", 1, "art-root"), FolderSummary("Music", 1, "art-music")),
            folderDao.observeFolders().first(),
        )
    }

    // Fixture: insertion order is neither the expected order nor binary order. Mutation: drop COLLATE NOCASE from the
    // outer ORDER BY → [Alpha, Charlie, beta] → RED.
    @Test
    fun observeFolders_ordersPathsCaseInsensitively() = runTest {
        songDao.upsertSongs(
            listOf(song("1", "c", "Charlie"), song("2", "b", "beta"), song("3", "a", "Alpha")),
        )
        assertEquals(listOf("Alpha", "beta", "Charlie"), folderDao.observeFolders().first().map { it.path })
    }

    // Fixture: a sibling subfolder ("Music/Chill") and a same-prefix folder ("MusicBox") must stay out. Mutations:
    // `folder_path LIKE :path || '%'` → both leak in → RED; ORDER BY title (binary) → [Alpha, Charlie, beta] → RED.
    @Test
    fun observeSongsInFolder_returnsOnlyDirectChildrenByTitleCaseInsensitively() = runTest {
        songDao.upsertSongs(
            listOf(
                song("1", "Charlie", "Music"),
                song("2", "beta", "Music"),
                song("3", "Alpha", "Music"),
                song("4", "Chill one", "Music/Chill"),
                song("5", "Boxed", "MusicBox"),
            ),
        )
        assertEquals(
            listOf("Alpha", "beta", "Charlie"),
            folderDao.observeSongsInFolder("Music").first().map { it.title },
        )
    }

    // Review Focus 1. Mutation: `WHERE IFNULL(folder_path, '') = :path` → the unscanned song joins the root → RED.
    @Test
    fun observeSongsInFolder_rootHoldsOnlyRootSongs() = runTest {
        songDao.upsertSongs(listOf(song("1", "At root", ""), song("2", "Unscanned", null)))
        assertEquals(listOf("At root"), folderDao.observeSongsInFolder("").first().map { it.title })
    }
```

- [ ] **Step 7: Check that it compiles and the unit tests are green.**
  - Run `./gradlew :core:database:compileDebugAndroidTestKotlin :core:database:testDebugUnitTest :core:data:testDebugUnitTest :core:database:check`. Expected: BUILD SUCCESSFUL.
  - The instrumented tests are not run here. List every androidTest mutation above in the report as **device mutations** for Task 10. Apply none of them.

- [ ] **Step 8: Run `./gradlew spotlessApply`, then report.** Do not commit.

---

### Task 2: Scanner: `FolderPaths`, the folder column and the re-scan rule (media-scanning-agent)

**Files:**
- Create: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/scanner/FolderPaths.kt`
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/scanner/MediaStoreColumns.kt`
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/scanner/ScannedSong.kt`
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/scanner/MediaScanner.kt`
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/scanner/LibraryIndexer.kt`
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/src/test/resources/robolectric.properties` (`sdk=34`)
- Test: `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/scanner/FolderPathsTest.kt` (new, plain JUnit)
- Test: `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/scanner/MediaScannerReadTest.kt` (new, Robolectric)
- Test: `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/scanner/LibraryIndexerTest.kt` (extend)

**Interfaces:**
- Consumes: `SongEntity.folderPath: String?` (Task 1).
- Produces:
  - `internal object FolderPaths { fun fromRelativePath(String?): String; fun fromDataPath(String?): String; fun fromColumn(sdkInt: Int, value: String?): String }`
  - `ScannedSong.folderPath: String` (never null)
  - `MediaStoreColumns.FOLDER: String`
  - `MediaScanner.readSongs(cursor)`, which becomes `internal`

- [ ] **Step 1: Write the failing `FolderPathsTest`.**

```kotlin
package com.rolla.musicplayer.core.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPathsTest {

    // Mutation: drop the trim → "Music/Chill/" → RED.
    @Test
    fun relativePath_trimsTheTrailingSlash() {
        assertEquals("Music/Chill", FolderPaths.fromRelativePath("Music/Chill/"))
        assertEquals("Download", FolderPaths.fromRelativePath("Download/"))
    }

    // Review Focus 1. Mutation: `relativePath ?: "Unknown"` (or `!!`) → RED.
    @Test
    fun relativePath_nullOrEmptyIsTheRoot() {
        assertEquals("", FolderPaths.fromRelativePath(null))
        assertEquals("", FolderPaths.fromRelativePath(""))
        assertEquals("", FolderPaths.fromRelativePath("/"))
    }

    // Mutation: drop the "/+" collapse → "Music//Chill" → RED.
    @Test
    fun relativePath_collapsesRepeatedSlashes() {
        assertEquals("Music/Chill", FolderPaths.fromRelativePath("Music//Chill/"))
    }

    // Real AOSP and Samsung primary-user paths. Mutation: try the generic "/storage/<volume>/" prefix before the
    // emulated one → "0/Music/Chill" → RED.
    @Test
    fun dataPath_stripsThePrimaryVolumeAndFileName() {
        assertEquals("Music/Chill", FolderPaths.fromDataPath("/storage/emulated/0/Music/Chill/song.mp3"))
        assertEquals(
            "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio",
            FolderPaths.fromDataPath(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-2024.opus",
            ),
        )
    }

    // Mutation: `emulated/0/` instead of `emulated/\d+/` → the secondary user's path maps to "" → RED.
    @Test
    fun dataPath_handlesSecondaryUsersSdCardsAndLegacyMounts() {
        assertEquals("Download", FolderPaths.fromDataPath("/storage/emulated/10/Download/a.flac"))
        assertEquals("Music", FolderPaths.fromDataPath("/storage/1A2B-3C4D/Music/a.mp3"))
        assertEquals("Music", FolderPaths.fromDataPath("/storage/self/primary/Music/a.mp3"))
        assertEquals("Music", FolderPaths.fromDataPath("/sdcard/Music/a.mp3"))
        assertEquals("Music", FolderPaths.fromDataPath("/mnt/sdcard/Music/a.mp3"))
    }

    // Review Focus 1. Mutation: return the remainder without dropping the file name → "a.mp3" → RED.
    @Test
    fun dataPath_fileAtTheVolumeRootIsTheRoot() {
        assertEquals("", FolderPaths.fromDataPath("/storage/emulated/0/a.mp3"))
    }

    // Mutation: fall back to the raw path when no prefix matches → "data/media/0/Music" → RED.
    @Test
    fun dataPath_nullOrUnknownIsTheRoot() {
        assertEquals("", FolderPaths.fromDataPath(null))
        assertEquals("", FolderPaths.fromDataPath("/data/media/0/Music/a.mp3"))
        assertEquals("", FolderPaths.fromDataPath("a.mp3"))
    }

    // The API boundary is Q (29). Mutation: `sdkInt > Q` → API 29 parses "Music/" as a DATA path → "" → RED.
    @Test
    fun fromColumn_readsRelativePathFromApi29AndDataBelowIt() {
        assertEquals("Music", FolderPaths.fromColumn(29, "Music/"))
        assertEquals("Music", FolderPaths.fromColumn(28, "/storage/emulated/0/Music/a.mp3"))
    }
}
```

Run `./gradlew :core:data:testDebugUnitTest --tests "*FolderPathsTest"`. Expected: it fails to compile, because `FolderPaths` does not exist yet.

- [ ] **Step 2: Implement `FolderPaths`.**

```kotlin
package com.rolla.musicplayer.core.data.scanner

import android.os.Build

/**
 * Turns MediaStore's location columns into the `songs.folder_path` value (spec §9.4): the song's folder relative to
 * its storage volume, "/"-separated, with no leading or trailing slash. "" is the volume root.
 *
 * API 29+ reports RELATIVE_PATH ("Music/Chill/"); API 24–28 only reports the absolute DATA path
 * ("/storage/emulated/0/Music/Chill/song.mp3"). Volumes are not told apart (spec §3): the same relative path on two
 * volumes is one folder.
 */
internal object FolderPaths {

    // Volume roots as they start a DATA path, most specific first: "/storage/emulated/<user>/" and
    // "/storage/self/primary/" must be tried before the generic "/storage/<volume>/", which would otherwise take
    // "emulated" or "self" as the volume and leave "0/…" or "primary/…".
    private val volumePrefixes = listOf(
        Regex("^/storage/emulated/\\d+/"),
        Regex("^/storage/self/primary/"),
        Regex("^/storage/[^/]+/"),
        Regex("^(/mnt)?/sdcard/"),
    )

    private val repeatedSlashes = Regex("/+")

    /** RELATIVE_PATH ("Music/Chill/") to a folder path ("Music/Chill"). Null or blank is the volume root. */
    fun fromRelativePath(relativePath: String?): String = normalize(relativePath.orEmpty())

    /**
     * DATA ("/storage/emulated/0/Music/Chill/song.mp3") to a folder path ("Music/Chill"): strips the volume prefix and
     * the file name. Null, or a path with no known volume prefix, is the volume root.
     */
    fun fromDataPath(dataPath: String?): String {
        if (dataPath == null) return ""
        val prefix = volumePrefixes.firstNotNullOfOrNull { it.find(dataPath) } ?: return ""
        val withinVolume = dataPath.substring(prefix.range.last + 1)
        return normalize(withinVolume.substringBeforeLast('/', missingDelimiterValue = ""))
    }

    /** The folder path of one MediaStore row. [value] is RELATIVE_PATH on API 29+ and DATA below it. */
    fun fromColumn(sdkInt: Int, value: String?): String =
        if (sdkInt >= Build.VERSION_CODES.Q) fromRelativePath(value) else fromDataPath(value)

    private fun normalize(path: String): String = path.replace(repeatedSlashes, "/").trim('/')
}
```

Run the test. Expected: PASS. Then run each named mutation, see RED, and revert byte-exact.

- [ ] **Step 3: Wire the column through the scanner.**
  - `core/data/build.gradle.kts`: add `testImplementation(libs.robolectric)` and `testImplementation(libs.androidx.junit)` (ruling 11).
  - Create `core/data/src/test/resources/robolectric.properties` containing `sdk=34`.

`MediaStoreColumns`: add after `DATE_MODIFIED`, and append `FOLDER` to `PROJECTION`:

```kotlin
    /**
     * Where the scanner reads a song's folder from (spec §9.4): RELATIVE_PATH on API 29+, DATA below it, where
     * RELATIVE_PATH does not exist. FolderPaths.fromColumn parses either.
     */
    @Suppress("DEPRECATION") // DATA is the only folder source below API 29.
    val FOLDER: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
```

`ScannedSong`:
- Add `val folderPath: String,` as the last property.
- In `toEntity`, add `folderPath = folderPath,` as the last argument.
- Extend its KDoc with one sentence: "The folder path always comes from the scan, never from [existing]: a stored NULL or a moved file must take the scanned value."

`MediaScanner`:
- `readSongs` becomes `@VisibleForTesting internal fun readSongs(cursor: Cursor): List<ScannedSong>` (import `androidx.annotation.VisibleForTesting`).
- `ColumnIndices` gains `val folder: Int`, from `cursor.getColumnIndexOrThrow(MediaStoreColumns.FOLDER)`.
- The row mapping adds `folderPath = FolderPaths.fromColumn(Build.VERSION.SDK_INT, cursor.getString(cols.folder)),` (import `android.os.Build`).

- [ ] **Step 4: Write `MediaScannerReadTest`** (Robolectric). It proves each API branch reads the right column.

```kotlin
package com.rolla.musicplayer.core.data.scanner

import android.database.MatrixCursor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class MediaScannerReadTest {

    private val scanner = MediaScanner(ApplicationProvider.getApplicationContext())

    // Mutation: FOLDER = DATA on every API → getColumnIndexOrThrow(DATA) on a RELATIVE_PATH cursor throws → RED.
    @Test
    @Config(sdk = [29])
    fun api29ReadsTheFolderFromRelativePath() {
        val songs = scanner.readSongs(cursorWith(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Chill/"))
        assertEquals("Music/Chill", songs.single().folderPath)
    }

    // Mutations: FOLDER = RELATIVE_PATH on every API → throws → RED; pass SDK 29 to fromColumn → the DATA value is
    // trimmed as a relative path → "storage/emulated/0/Music/Chill/a.mp3" → RED.
    @Test
    @Config(sdk = [28])
    @Suppress("DEPRECATION")
    fun api28ReadsTheFolderFromData() {
        val songs = scanner.readSongs(cursorWith(MediaStore.Audio.Media.DATA, "/storage/emulated/0/Music/Chill/a.mp3"))
        assertEquals("Music/Chill", songs.single().folderPath)
    }

    // The MatrixCursor tests cannot see the projection: a cursor built by hand always has the column. Mutation: leave
    // FOLDER out of PROJECTION → RED here (on a device the real query would throw at getColumnIndexOrThrow).
    @Test
    @Config(sdk = [29])
    fun projectionAsksForTheFolderColumn() {
        assertTrue(MediaStoreColumns.PROJECTION.contains(MediaStore.Audio.Media.RELATIVE_PATH))
    }

    private fun cursorWith(folderColumn: String, folderValue: String): MatrixCursor {
        val columns = arrayOf(
            MediaStoreColumns.ID, MediaStoreColumns.TITLE, MediaStoreColumns.ARTIST, MediaStoreColumns.ALBUM,
            MediaStoreColumns.ALBUM_ID, MediaStoreColumns.DURATION, MediaStoreColumns.TRACK, MediaStoreColumns.YEAR,
            MediaStoreColumns.DATE_MODIFIED, folderColumn,
        )
        return MatrixCursor(columns).apply {
            addRow(arrayOf<Any?>(1L, "Title", "Artist", "Album", 10L, 200_000L, 1, 2020, 1_000L, folderValue))
        }
    }
}
```

Run `./gradlew :core:data:testDebugUnitTest --tests "*MediaScannerReadTest"`. Expected: PASS. Run each mutation and revert.

- [ ] **Step 5: Change the re-scan rule (ruling 3).** In `LibraryIndexer.sync()`, the filter becomes:

```kotlin
                .filter { song ->
                    val existingEntity = existing[song.mediaStoreId]
                    // Stale when new, when the file changed, or when its stored folder differs from the scanned one:
                    // NULL after the v4 migration (the one-time backfill) or a move that kept the mtime. Root songs
                    // store "" and scan "", so they are not rewritten on every sync (spec §9.4, Phase 4b ruling 3).
                    existingEntity == null ||
                        existingEntity.dateModified != song.dateModified ||
                        existingEntity.folderPath != song.folderPath
                }
```

Update the `sync()` KDoc's first sentence to mention the folder rule. `syncSongs` needs no logic change: it upserts unconditionally through `toEntity`, which now carries `folderPath`.

- [ ] **Step 6: Extend `LibraryIndexerTest`.**
  - Give the two helpers a folder parameter, defaulting to the same value: `scannedSong(…, folderPath: String = "Music")` passes `folderPath = folderPath`, and `entityWithUserState(mediaStoreId, dateModified, folderPath: String? = "Music")` sets `folderPath = folderPath`. With equal defaults, the existing "unchanged song is not re-upserted" test keeps its meaning.
  - Then add:

```kotlin
    // Review Focus 2. Mutation: drop the folder-path clause from the stale check → no upsert → RED.
    @Test
    fun sync_unscannedFolderPath_backfillsAndKeepsUserState() = runTest {
        val existing = entityWithUserState(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED, folderPath = null)
        coEvery { scanner.scan() } returns
            listOf(scannedSong(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED, folderPath = "Music/Chill"))
        coEvery { songDao.getAllSongs() } returns listOf(existing)

        indexer.sync()

        coVerify(exactly = 1) {
            songDao.upsertSongs(
                match { entities ->
                    val entity = entities.single()
                    entity.folderPath == "Music/Chill" && entity.preservesUserStateOf(existing, existing.title)
                },
            )
        }
    }

    // Review Focus 1. Mutation: stale when `existing.folderPath.isNullOrEmpty()` → the root song is re-upserted → RED.
    @Test
    fun sync_rootSong_isNotRewrittenOnTheSecondSync() = runTest {
        coEvery { scanner.scan() } returns
            listOf(scannedSong(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED, folderPath = ""))
        coEvery { songDao.getAllSongs() } returns
            listOf(entityWithUserState(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED, folderPath = ""))

        val result = indexer.sync()

        assertEquals(SyncResult(added = 0, removed = 0), result)
        coVerify(exactly = 0) { songDao.upsertSongs(any()) }
    }

    // Review Focus 4 (ruling 3). Mutation: the spec's original `existingEntity.folderPath == null` → no upsert → RED.
    @Test
    fun sync_movedSongWithUnchangedMtime_takesItsNewFolder() = runTest {
        coEvery { scanner.scan() } returns
            listOf(scannedSong(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED, folderPath = "Music/Chill"))
        coEvery { songDao.getAllSongs() } returns
            listOf(entityWithUserState(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED, folderPath = "Music"))

        indexer.sync()

        coVerify(exactly = 1) { songDao.upsertSongs(match { it.single().folderPath == "Music/Chill" }) }
    }

    // Fixture: the stored folder differs from the scanned one. Mutation: toEntity writes `existing?.folderPath ?:
    // folderPath` → "Music" → RED.
    @Test
    fun syncSongs_writesTheScannedFolderPath() = runTest {
        coEvery { scanner.scan(listOf(1L)) } returns listOf(scannedSong(mediaStoreId = 1L, folderPath = "Download"))
        coEvery { songDao.getAllSongs() } returns
            listOf(entityWithUserState(mediaStoreId = 1L, dateModified = OLD_DATE_MODIFIED, folderPath = "Music"))

        indexer.syncSongs(listOf(1L))

        coVerify(exactly = 1) { songDao.upsertSongs(match { it.single().folderPath == "Download" }) }
    }
```

- [ ] **Step 7: Run the module's full check.** The source sets changed, so the full module check applies, lint included. Run `./gradlew :core:data:check spotlessApply`. Expected: BUILD SUCCESSFUL. Report every mutation and its RED. Do not commit.

---

### Task 3: The `Folder` model, `FolderRepository` and the fake (data-layer-agent)

**Files:**
- Create: `core/model/src/main/kotlin/com/rolla/musicplayer/core/model/Folder.kt`
- Create: `core/model/src/main/kotlin/com/rolla/musicplayer/core/model/FolderNames.kt`
- Test: `core/model/src/test/kotlin/com/rolla/musicplayer/core/model/FolderNamesTest.kt`
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/relation/FolderSummary.kt` (add `toDomain`)
- Create: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/FolderRepository.kt`
- Create: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/FolderRepositoryImpl.kt`
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/di/DataModule.kt`
- Test: `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/repository/FolderRepositoryImplTest.kt`
- Create: `core/testing/src/main/kotlin/com/rolla/musicplayer/core/testing/FakeFolderRepository.kt`
- Modify: `.claude/rules/model-vocabulary.md`

**Interfaces:**
- Consumes: `FolderDao`, `FolderSummary` (Task 1).
- Produces:
  - `data class Folder(val path: String, val name: String, val songCount: Int, val artworkUri: String)`
  - `const val ROOT_FOLDER_NAME = "Internal storage"`, `fun folderName(path: String): String`, `fun folderDisplayPath(path: String): String`
  - `fun FolderSummary.toDomain(): Folder`
  - `interface FolderRepository { fun observeFolders(): Flow<List<Folder>>; fun observeFolderSongs(path: String): Flow<List<Song>> }`
  - `class FakeFolderRepository` with `emitFolders(List<Folder>)`, `emitFolderSongs(List<Song>)` and `requestedFolderPaths: MutableList<String>`

- [ ] **Step 1: Write the failing `FolderNamesTest`.**

```kotlin
package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNamesTest {

    // Mutation: substringBefore('/') → "Music" for "Music/Chill" → RED.
    @Test
    fun nameIsTheLastPathSegment() {
        assertEquals("Chill", folderName("Music/Chill"))
        assertEquals("Music", folderName("Music"))
    }

    // Review Focus 1. Mutation: drop the root branch → "" → RED.
    @Test
    fun theRootIsInternalStorage() {
        assertEquals("Internal storage", folderName(""))
    }

    // Review Focus 1. Mutation: drop the root branch in folderDisplayPath → "/Internal storage/" → RED.
    @Test
    fun displayPathIsRootedAtInternalStorage() {
        assertEquals("/Internal storage", folderDisplayPath(""))
        assertEquals("/Internal storage/Music/Chill", folderDisplayPath("Music/Chill"))
    }
}
```

- [ ] **Step 2: Implement the model.**

`Folder.kt`:

```kotlin
package com.rolla.musicplayer.core.model

/**
 * A folder that directly holds songs (spec §9.4). [path] is relative to its storage volume ("Music/Chill"; "" is the
 * root) and is the folder's identity. [name] is its display name (see [folderName]), and [artworkUri] is the cover of
 * its alphabetically first song.
 */
data class Folder(
    val path: String,
    val name: String,
    val songCount: Int,
    val artworkUri: String,
)
```

`FolderNames.kt`:

```kotlin
package com.rolla.musicplayer.core.model

/** The display name of the volume root, the folder whose path is "" (spec §9.4). */
const val ROOT_FOLDER_NAME = "Internal storage"

/** A folder's display name (spec §9.4): its last path segment, or [ROOT_FOLDER_NAME] for the root "". */
fun folderName(path: String): String = if (path.isEmpty()) ROOT_FOLDER_NAME else path.substringAfterLast('/')

/** A folder's display path (spec §9.4): "/Internal storage" for the root, "/Internal storage/Music/Chill" below it. */
fun folderDisplayPath(path: String): String = "/$ROOT_FOLDER_NAME" + if (path.isEmpty()) "" else "/$path"
```

Run `./gradlew :core:model:test`. Expected: PASS. Run the three mutations and revert.

- [ ] **Step 3: Add the mapping.** In `FolderSummary.kt`, import `com.rolla.musicplayer.core.model.Folder` and `com.rolla.musicplayer.core.model.folderName`, then add:

```kotlin
fun FolderSummary.toDomain(): Folder = Folder(
    path = path,
    name = folderName(path),
    songCount = songCount,
    artworkUri = artworkUri,
)
```

- [ ] **Step 4: Write the failing `FolderRepositoryImplTest`** (MockK DAO, the same style as `ArtistRepositoryImplTest`).

```kotlin
package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.FolderDao
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.FolderSummary
import com.rolla.musicplayer.core.model.Folder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderRepositoryImplTest {

    private val folderDao: FolderDao = mockk()
    private val repository = FolderRepositoryImpl(folderDao)

    // Ruling 2. The DAO hands rows in path order, which is not name order. Mutation: drop the sort →
    // [Internal storage, Alarms, Download, Abba] → RED; sort by path → the same → RED.
    @Test
    fun observeFolders_sortsByDisplayNameNotPath() = runTest {
        every { folderDao.observeFolders() } returns flowOf(
            listOf(
                FolderSummary("", 4, "art-root"),
                FolderSummary("Alarms", 1, "art-alarms"),
                FolderSummary("Download", 2, "art-download"),
                FolderSummary("Music/Abba", 3, "art-abba"),
            ),
        )
        assertEquals(
            listOf(
                Folder("Music/Abba", "Abba", 3, "art-abba"),
                Folder("Alarms", "Alarms", 1, "art-alarms"),
                Folder("Download", "Download", 2, "art-download"),
                Folder("", "Internal storage", 4, "art-root"),
            ),
            repository.observeFolders().first(),
        )
    }

    // Review Focus 3. The names tie case-insensitively ("Music", "music"), so the path decides. The DAO order puts the
    // later path first, so a stable sort without the tie-break keeps the wrong order. Mutation: drop the path
    // tie-break → [Music, Download/music] → RED.
    @Test
    fun observeFolders_breaksNameTiesByPath() = runTest {
        every { folderDao.observeFolders() } returns flowOf(
            listOf(FolderSummary("Music", 1, "a"), FolderSummary("Download/music", 1, "b")),
        )
        assertEquals(listOf("Download/music", "Music"), repository.observeFolders().first().map { it.path })
    }

    // Mutation: pass "" (or a hardcoded path) to the DAO → the stub doesn't match and MockK throws → RED.
    @Test
    fun observeFolderSongs_queriesThatPathAndMapsToDomain() = runTest {
        every { folderDao.observeSongsInFolder("Music/Chill") } returns flowOf(listOf(songEntity("7", "Seven")))
        val songs = repository.observeFolderSongs("Music/Chill").first()
        assertEquals(listOf("7" to "Seven"), songs.map { it.id to it.title })
        verify(exactly = 1) { folderDao.observeSongsInFolder("Music/Chill") }
    }

    private fun songEntity(id: String, title: String) = SongEntity(
        id = id,
        mediaStoreId = id.toLong(),
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 180_000L,
        trackNumber = 1,
        year = 2024,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "content://media/external/audio/albumart/10",
        dateModified = 1_000L,
        folderPath = "Music/Chill",
    )
}
```

- [ ] **Step 5: Implement the repository and bind it.**

`FolderRepository.kt`:

```kotlin
package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow

/** Local, offline folder data (the Folders tab and FolderDetail), derived from the songs' scanned folder paths. */
interface FolderRepository {

    /** Every folder that directly holds songs, by display name case-insensitively, then by path (ruling 2). */
    fun observeFolders(): Flow<List<Folder>>

    /** The songs directly in [path] (not its subfolders), by title case-insensitively. */
    fun observeFolderSongs(path: String): Flow<List<Song>>
}
```

`FolderRepositoryImpl.kt`:

```kotlin
package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.FolderDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.toDomain
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFolders()
            .map { rows -> rows.map { it.toDomain() }.sortedWith(folderOrder) }
            .distinctUntilChanged()

    override fun observeFolderSongs(path: String): Flow<List<Song>> =
        folderDao.observeSongsInFolder(path)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()

    private companion object {
        // Ruling 2: the Folders tab sorts by what it shows, the name, like every other "Name" list, so Phase 5's A–Z
        // sections stay monotonic. Same-name folders fall back to the path, case-insensitively and then exactly.
        val folderOrder: Comparator<Folder> =
            compareBy<Folder, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.path }
                .thenBy { it.path }
    }
}
```

In `DataModule`, add:

```kotlin
    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
```

Run `./gradlew :core:data:testDebugUnitTest --tests "*FolderRepositoryImplTest"`. Expected: PASS. Run each mutation and revert.

- [ ] **Step 6: Write the fake.** It follows `FakeArtistRepository`'s style.

```kotlin
package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.FolderRepository
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake [FolderRepository]. [emitFolders] drives [observeFolders]; [emitFolderSongs] drives
 * [observeFolderSongs] for whatever path is asked, and every path asked is recorded in [requestedFolderPaths], so a
 * test can assert which folder a ViewModel opened.
 */
class FakeFolderRepository : FolderRepository {

    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    private val folderSongsFlow = MutableStateFlow<List<Song>>(emptyList())

    /** Every `path` passed to [observeFolderSongs], in call order. */
    val requestedFolderPaths = mutableListOf<String>()

    fun emitFolders(folders: List<Folder>) {
        foldersFlow.value = folders
    }

    fun emitFolderSongs(songs: List<Song>) {
        folderSongsFlow.value = songs
    }

    override fun observeFolders(): Flow<List<Folder>> = foldersFlow.asStateFlow()

    override fun observeFolderSongs(path: String): Flow<List<Song>> {
        requestedFolderPaths += path
        return folderSongsFlow.asStateFlow()
    }
}
```

- [ ] **Step 7: Update `.claude/rules/model-vocabulary.md`.**
  - In the canonical models block, after `Artist`, add the `Folder` data class with a trailing comment: `// path relative to its volume ("" = root) is the identity; name = last segment or "Internal storage"`.
  - Add one line under the naming conventions: "A folder's identity is its `path`. Routes and list keys use the path; `name` is display-only."

- [ ] **Step 8: Run the checks.** Run `./gradlew :core:model:check :core:data:check :core:database:check :core:testing:check spotlessApply`. Expected: BUILD SUCCESSFUL. Report. Do not commit.

---

### Task 4: Design system: `FolderBadge`, its tokens and icon (m3-design-system-agent)

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Color.kt`
- Modify: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Dimens.kt`
- Modify: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/icon/RollaIcons.kt`
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/FolderBadge.kt`
- Modify: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/KitPreviews.kt` (add the badge to the kit preview)
- Test: `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/icon/RollaIconsTest.kt`
- Test: `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/theme/ContrastTest.kt`
- Test: `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/theme/ThemeTokensTest.kt` (only if it pins palette values; grep it first)
- Test: `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/FolderBadgeTest.kt` (new)

**Interfaces:**
- Produces:
  - `RollaDimens.folderBadge: Dp = 20.dp`, `RollaDimens.folderBadgeInset: Dp = 2.dp`
  - `ColorScheme.folderBadgeStroke: Color`, `ColorScheme.folderBadgeFill: Color`
  - `RollaIcons.FolderBadgeFill: ImageVector`
  - `@Composable fun FolderBadge(modifier: Modifier = Modifier)`

- [ ] **Step 1: Add the tokens (ruling 5).**
  - **`Dimens.kt`**, next to the list tokens:

```kotlin
    // The folder mark on a FolderRow thumbnail (spec §8.1). Measured from the reference at 3.0 px/dp: a ~14 × 11 dp
    // glyph ~5 dp from the thumbnail's end and ~6 dp from its bottom. The 20 dp icon box puts the 24-grid glyph there
    // with a 2 dp inset. Provisional until Phase 7 calibration.
    val folderBadge = 20.dp
    val folderBadgeInset = 2.dp
```

  - **`Color.kt`:**
    - Add `val folderBadgeStroke: Color` and `val folderBadgeFill: Color` to `RollaPalette`, after `artworkPlaceholderGlyph`.
    - Set `folderBadgeStroke = Color(0xFFFFFFFF)` and `folderBadgeFill = Color(0xFF000000)` in **both** `DarkPalette` and `LightPalette`, each with a comment: `// On artwork, not a theme surface: the same in both themes (reference: white outline over black).`
    - Add the two `ColorScheme` extensions in the file's existing pattern (`@Composable @ReadOnlyComposable get() = currentPalette.…`).
  - **`RollaIcons.kt`:** add, right after `FolderBadge`, and append `FolderBadgeFill` to `all` after `FolderBadge`:

```kotlin
    /** FolderBadge's outline as one filled shape, drawn under the outline so the badge reads on any artwork. */
    val FolderBadgeFill: ImageVector by lazy {
        icon("FolderBadgeFill") {
            solid {
                moveTo(3.5f, 6.5f)
                lineTo(9f, 6.5f)
                lineTo(11f, 8.5f)
                lineTo(20.5f, 8.5f)
                lineTo(20.5f, 18.5f)
                lineTo(3.5f, 18.5f)
                close()
            }
        }
    }
```

- [ ] **Step 2: Write the component.**

```kotlin
@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.folderBadgeFill
import com.rolla.musicplayer.core.designsystem.theme.folderBadgeStroke

/**
 * The small folder mark on a folder thumbnail's bottom-end corner (spec §8.1): a white outline over a black fill in
 * both themes, because it sits on artwork rather than on a theme surface. Decorative: the row's text names the folder.
 */
@Composable
fun FolderBadge(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(RollaDimens.folderBadge)) {
        Icon(
            imageVector = RollaIcons.FolderBadgeFill,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.folderBadgeFill,
            modifier = Modifier.matchParentSize(),
        )
        Icon(
            imageVector = RollaIcons.FolderBadge,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.folderBadgeStroke,
            modifier = Modifier.matchParentSize(),
        )
    }
}
```

Add a `FolderBadge` sample to `KitPreviews.kt` in the file's existing style: one badge over an `ArtworkPlaceholder`.

- [ ] **Step 3: Update the existing tests.**
  - `RollaIconsTest`:
    - add `"FolderBadgeFill"` to `CONTRACT_NAMES`
    - add `"FolderBadgeFill"` to `SOLID_ONLY_NAMES`
    - change `assertEquals(27, names.size)` to `28`
  - `ContrastTest.pairingsOf`: add `Pairing("folderBadgeStroke on folderBadgeFill", p.folderBadgeStroke, p.folderBadgeFill, NON_TEXT),`.
  - `ThemeTokensTest`: grep it. If it pins every palette value, add the two new ones for both palettes. If it doesn't, leave it alone.
  - Mutations:
    - remove `FolderBadgeFill` from `all` → `iconNamesMatchTheContract` RED
    - give `FolderBadgeFill` a `line {}` → `strokeStyleMatchesSpec` RED
    - `folderBadgeStroke = Color(0xFF111111)` → the contrast pairing RED

- [ ] **Step 4: Write `FolderBadgeTest`** (Robolectric pixel test). Sample points are named in 24-grid units and converted through the badge's measured bounds:
  - The fill sample is (12, 13.5), the folder body's centre, at least 4.2 units from every outline.
  - The stroke sample is (3.0, 13.0). That is 0.5 units outside the fill's left edge (x = 3.5) and inside the 1.6-unit stroke, which spans x 2.7–4.3.

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholder
import com.rolla.musicplayer.core.testing.assertColorNear
import com.rolla.musicplayer.core.testing.drawRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BADGE_TAG = "badge"
private const val GRID = 24f

// SDK 30 + NATIVE: view.draw renders reliably there (OneUiTabRowTest precedent). xxhdpi makes the 1.6-unit stroke
// ~4 px wide, so a single sample pixel lands inside it.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderBadgeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Light theme: the placeholder (#E6E6E8) differs from both badge colours. Mutations: drop the fill Icon → the body
    // centre shows the placeholder → RED; drop the stroke Icon → the stroke sample shows the placeholder → RED; swap the
    // two tints → both samples flip → RED; a background behind the badge Box → the corner sample changes → RED.
    @Test
    fun whiteOutlineOverBlackFillWithNoBackingSquare() {
        lateinit var placeholder: Color
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = false) {
                placeholder = MaterialTheme.colorScheme.artworkPlaceholder
                Box(Modifier.fillMaxSize().background(placeholder), contentAlignment = Alignment.Center) {
                    FolderBadge(Modifier.testTag(BADGE_TAG))
                }
            }
        }
        val bounds = composeRule.onNodeWithTag(BADGE_TAG).fetchSemanticsNode().boundsInRoot
        val bitmap = composeRule.drawRoot()
        fun pixelAt(gridX: Float, gridY: Float): Int = bitmap.getPixel(
            (bounds.left + bounds.width * gridX / GRID).toInt(),
            (bounds.top + bounds.height * gridY / GRID).toInt(),
        )
        assertColorNear(Color.Black, pixelAt(12f, 13.5f))
        assertColorNear(Color.White, pixelAt(3.0f, 13.0f))
        assertColorNear(placeholder, pixelAt(1f, 1f))
    }
}
```

If a sample lands on an anti-aliased edge and the pixel misses `assertColorNear`'s tolerance, move it *inward* along the same axis, and say so in the report. Do not loosen the tolerance.

- [ ] **Step 5: Run the checks.** Run `./gradlew :core:designsystem:check spotlessApply`. Expected: BUILD SUCCESSFUL. Report each mutation's RED. Do not commit.

---

### Task 5: `:core:ui`: the row scaffold and `FolderRow` (ui-builder)

**Files:**
- Create: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/ListRowScaffold.kt`
- Modify: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/AlbumRow.kt`
- Modify: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/ArtistRow.kt`
- Modify: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/ArtworkThumbnail.kt` (KDoc: add FolderRow to the users)
- Create: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/FolderRow.kt`
- Test: `core/ui/src/test/kotlin/com/rolla/musicplayer/core/ui/FolderRowTest.kt` (new)
- Safety net (existing, run before and after the refactor, unchanged):
  - `AlbumRowTest`: `subtitle is artist and track count`, `title starts at the list text column`
  - `ArtistRowTest`: `subtitle counts albums then tracks`, `name starts at the list text column and the row is a two-line row`, `avatar is a circle`, `tapping the row calls onClick once`

**Interfaces:**
- Consumes: `Folder`, `folderDisplayPath` (Task 3); `FolderBadge`, `RollaDimens.folderBadgeInset` (Task 4).
- Produces:
  - `@Composable internal fun ListRowScaffold(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier, thumbnail: @Composable () -> Unit)`
  - `@Composable fun FolderRow(folder: Folder, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `internal const val FOLDER_THUMB_TAG`, `internal const val FOLDER_BADGE_TAG`

- [ ] **Step 1: Run the safety net first.** Run `./gradlew :core:ui:testDebugUnitTest --tests "*AlbumRowTest" --tests "*ArtistRowTest"`. Expected: PASS. Record the count.

- [ ] **Step 2: Extract the scaffold (ruling 6).** `ListRowScaffold.kt`:

```kotlin
@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * The two-line row anatomy AlbumRow, ArtistRow and FolderRow share (spec §8.1):
 * - a [RollaDimens.listRowHeight] row
 * - the 48 dp [thumbnail] at [RollaDimens.listThumbStart]
 * - [title] and [subtitle] at [RollaDimens.listTextStart], ending [RollaDimens.listTrailingEnd] before the row's end
 *   (the A–Z rail space)
 * - a hairline from the text column to the rail
 *
 * The whole row is one merged clickable node.
 */
@Composable
internal fun ListRowScaffold(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit,
) {
    // The hairline overlays the row's bottom edge so the row pitch stays exactly listRowHeight.
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RollaDimens.listRowHeight)
                .clickable(onClick = onClick)
                .padding(end = RollaDimens.listTrailingEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(RollaDimens.listThumbStart))
            thumbnail()
            Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
            RowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        }
        InsetDivider(
            startInset = RollaDimens.listTextStart,
            endInset = RollaDimens.listDividerEnd,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
```

Rewrite `AlbumRow` and `ArtistRow` onto it, keeping their KDoc (shortened to point at `ListRowScaffold`), previews and `ARTIST_AVATAR_TAG`:

```kotlin
@Composable
fun AlbumRow(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRowScaffold(
        title = album.title,
        subtitle = "${album.artist}, ${tracksCountLabel(album.songCount)}",
        onClick = onClick,
        modifier = modifier,
    ) {
        // Decorative within the merged row: the row's texts already name the album.
        ArtworkThumbnail(album.artworkUri, null)
    }
}
```

```kotlin
@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRowScaffold(
        title = artist.name,
        subtitle = "${albumsCountLabel(artist.albumCount)}, ${tracksCountLabel(artist.songCount)}",
        onClick = onClick,
        modifier = modifier,
    ) {
        ArtworkThumbnail(
            artworkUri = artist.artworkUri,
            contentDescription = null,
            shape = CircleShape,
            placeholderGlyph = RollaIcons.Person,
            modifier = Modifier.testTag(ARTIST_AVATAR_TAG),
        )
    }
}
```

Drop the `LongMethod` suppression and the imports that became unused. Re-run Step 1's command. Expected: the same tests pass with the same count.

- [ ] **Step 3: Write the failing `FolderRowTest`.** Copy the class annotations from `ArtistRowTest`: `@RunWith(AndroidJUnit4::class)`, the same `@Config` qualifiers and `@GraphicsMode(NATIVE)`.

```kotlin
    // Mutations: subtitle = folder.path → "Music/Chill" → RED; title = folder.path → RED.
    @Test
    fun `shows the folder name over its display path`() {
        setRow(Folder(path = "Music/Chill", name = "Chill", songCount = 3, artworkUri = ""))
        composeRule.onNodeWithText("Chill").assertExists()
        composeRule.onNodeWithText("/Internal storage/Music/Chill").assertExists()
    }

    // Mutations: Alignment.TopStart → RED; drop the inset padding → both edges off by folderBadgeInset → RED;
    // FolderBadge outside the thumbnail Box (a sibling in the row) → its right edge is not the thumbnail's → RED.
    @Test
    fun `the badge sits inset in the thumbnail's bottom-end corner`() {
        setRow(Folder(path = "Music", name = "Music", songCount = 1, artworkUri = ""))
        val thumb = composeRule.onNodeWithTag(FOLDER_THUMB_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val badge = composeRule.onNodeWithTag(FOLDER_BADGE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(thumb.right - RollaDimens.folderBadgeInset, badge.right)
        assertEquals(thumb.bottom - RollaDimens.folderBadgeInset, badge.bottom)
        assertEquals(RollaDimens.folderBadge, badge.right - badge.left)
    }

    // Mutation: onClick = {} in FolderRow → 0 calls → RED.
    @Test
    fun `tapping the row calls onClick once`() {
        var clicks = 0
        setRow(Folder(path = "Music", name = "Music", songCount = 1, artworkUri = ""), onClick = { clicks++ })
        composeRule.onNodeWithText("Music").performClick()
        assertEquals(1, clicks)
    }
```

The `setRow` helper wraps `FolderRow` in `RollaMusicPlayerTheme(darkTheme = true)`. If the Dp `assertEquals` comparisons trip on float rounding, compare with the `assertIsEqualTo`-style tolerance `ArtistRowTest` already uses, and say so.

- [ ] **Step 4: Implement `FolderRow`.**

```kotlin
@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.FolderBadge
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.folderDisplayPath

/**
 * Model-aware row for a [Folder] (spec §8.1): the folder's first-song art (or a note placeholder) with a FolderBadge
 * in its bottom-end corner, the folder name, and its display path ("/Internal storage/Music"). The anatomy is
 * [ListRowScaffold]'s; [onClick] opens the folder.
 */
@Composable
fun FolderRow(folder: Folder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRowScaffold(
        title = folder.name,
        subtitle = folderDisplayPath(folder.path),
        onClick = onClick,
        modifier = modifier,
    ) {
        Box {
            // Decorative within the merged row: the row's texts already name the folder.
            ArtworkThumbnail(
                artworkUri = folder.artworkUri,
                contentDescription = null,
                modifier = Modifier.testTag(FOLDER_THUMB_TAG),
            )
            FolderBadge(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = RollaDimens.folderBadgeInset, bottom = RollaDimens.folderBadgeInset)
                    .testTag(FOLDER_BADGE_TAG),
            )
        }
    }
}

internal const val FOLDER_THUMB_TAG = "folder_thumb"
internal const val FOLDER_BADGE_TAG = "folder_badge"

@Suppress("UnusedPrivateMember")
@Preview(name = "Folder Row - Light")
@Preview(name = "Folder Row - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewFolderRow() {
    RollaMusicPlayerTheme {
        Column {
            FolderRow(folder = Folder(path = "Music", name = "Music", songCount = 12, artworkUri = ""), onClick = {})
            FolderRow(folder = Folder(path = "", name = "Internal storage", songCount = 2, artworkUri = ""), onClick = {})
        }
    }
}
```

Run `./gradlew :core:ui:testDebugUnitTest`. Expected: PASS. Run each mutation and revert. Update `ArtworkThumbnail`'s KDoc user list to include `FolderRow`.

- [ ] **Step 5: Run the checks.** Run `./gradlew :core:ui:check spotlessApply`. Expected: BUILD SUCCESSFUL. Report. Do not commit.

---

### Task 6: The Folders tab (ui-builder + viewmodel-architect)

**Files:**
- Create: `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/FoldersTabViewModel.kt`
- Create: `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/FoldersTab.kt`
- Test: `feature/library/src/test/kotlin/com/rolla/musicplayer/feature/library/FoldersTabViewModelTest.kt`
- Test: `feature/library/src/test/kotlin/com/rolla/musicplayer/feature/library/FoldersTabTest.kt`

**Interfaces:**
- Consumes: `FolderRepository`, `FakeFolderRepository` (Task 3); `FolderRow` (Task 5); the existing `ScanState`, `TabScanning`, `TabMessage`, `SortHeader` and `homeListBottomPadding`.
- Produces:
  - `class FoldersTabViewModel(folderRepository: FolderRepository)` with `val folders: StateFlow<List<Folder>?>`
  - `@Composable fun FoldersTab(scanState: ScanState, onFolderClick: (String) -> Unit, modifier: Modifier = Modifier, viewModel: FoldersTabViewModel = hiltViewModel())`
  - `internal fun FoldersTabContent(folders: List<Folder>?, scanState: ScanState, onFolderClick: (String) -> Unit, modifier: Modifier = Modifier)`
  - `internal const val FOLDER_LIST_TEST_TAG = "folder_list"`

- [ ] **Step 1: Write `FoldersTabViewModelTest`.** It mirrors `ArtistsTabViewModelTest`.

```kotlin
    // Mutation: seed stateIn with emptyList() → [] before any subscriber → RED (Phase 4a ruling 6).
    @Test
    fun `folders are null before the first emission`() {
        assertNull(FoldersTabViewModel(FakeFolderRepository()).folders.value)
    }

    // Two folders share a name: both must come through, in the repository's order. Mutation: upstream flowOf(emptyList())
    // → RED.
    @Test
    fun `folders mirror the repository while collected`() = runTest {
        val repository = FakeFolderRepository()
        val viewModel = FoldersTabViewModel(repository)
        val job = launch { viewModel.folders.collect {} }
        val folders = listOf(Folder("Download/Music", "Music", 1, "a"), Folder("Music", "Music", 4, "b"))

        repository.emitFolders(folders)
        advanceUntilIdle()

        assertEquals(folders, viewModel.folders.value)
        job.cancel()
    }
```

- [ ] **Step 2: Implement the ViewModel.**

```kotlin
package com.rolla.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.FolderRepository
import com.rolla.musicplayer.core.model.Folder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Home pager's Folders tab: every folder that holds songs, by display name. null until Room answers. */
@HiltViewModel
class FoldersTabViewModel @Inject constructor(
    folderRepository: FolderRepository,
) : ViewModel() {

    val folders: StateFlow<List<Folder>?> = folderRepository.observeFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null,
        )
}
```

- [ ] **Step 3: Write `FoldersTabTest`.** Copy `ArtistsTabTest`'s class annotations and its `setContent` helper, changed for folders. A `folder(path)` helper builds `Folder(path, folderName(path), 1, "")`.

```kotlin
    // Review Focus 3. Two folders named "Music". Mutations: key = { it.name } → duplicate key → crash → RED;
    // onFolderClick(folder.name) → ["Music", "Music"] → RED.
    @Test
    fun `same-name folders both show and open their own paths`() {
        val opened = mutableListOf<String>()
        setContent(folders = listOf(folder("Download/Music"), folder("Music")), onFolderClick = { opened += it })
        composeRule.onNodeWithText("/Internal storage/Music").performClick()
        composeRule.onNodeWithText("/Internal storage/Download/Music").performClick()
        assertEquals(listOf("Music", "Download/Music"), opened)
    }

    // Review Focus 1. Mutation: onFolderClick(folder.name) → "Internal storage" → RED.
    @Test
    fun `the root folder opens the empty path`() {
        val opened = mutableListOf<String>()
        setContent(folders = listOf(folder("")), onFolderClick = { opened += it })
        composeRule.onNodeWithText("Internal storage").performClick()
        assertEquals(listOf(""), opened)
    }

    // Mutation: treat null as empty → "No folders found" flashes → RED.
    @Test
    fun `nothing renders before the folders load`() {
        setContent(folders = null)
        composeRule.onNodeWithText("No folders found").assertDoesNotExist()
        composeRule.onNodeWithTag(FOLDER_LIST_TEST_TAG).assertDoesNotExist()
    }

    // Mutation: drop the Scanning branch → the empty message shows mid-scan → RED.
    @Test
    fun `scanning shows progress over an empty list`() {
        setContent(folders = emptyList(), scanState = ScanState.Scanning)
        composeRule.onNodeWithContentDescription("Scanning library").assertExists()
        composeRule.onNodeWithText("No folders found").assertDoesNotExist()
    }

    @Test
    fun `an empty library says no folders found`() {
        setContent(folders = emptyList(), scanState = ScanState.Done(0, 0))
        composeRule.onNodeWithText("No folders found").assertIsDisplayed()
    }

    // Mutation: drop the bottom contentPadding → RED (ArtistsTabTest's probe).
    @Test
    fun `the last row scrolls fully above the mini-player`() {
        val folders = List(20) { folder("Folder $it") }
        setContent(folders = folders, miniPlayerInset = 60.dp)
        composeRule.onNodeWithTag(FOLDER_LIST_TEST_TAG).performScrollToIndex(folders.lastIndex)
        val last = composeRule.onNodeWithText("/Internal storage/Folder 19").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= root.bottom - 60.dp)
    }
```

- [ ] **Step 4: Implement `FoldersTab`.** It mirrors `ArtistsTab` exactly, with these changes:
  - The header is `SortHeader(label = "Name")` with no trailing buttons (ruling 9).
  - The empty text is "No folders found". The list tag is `FOLDER_LIST_TEST_TAG = "folder_list"`.
  - Items are `items(items = folders, key = { folder -> folder.path })`, with the comment "Keyed by path, the folder's identity and unique; names repeat across parents (ruling 15)."
  - Each row is `FolderRow(folder = folder, onClick = { onFolderClick(folder.path) })`.
  - The KDoc says that a row tap opens FolderDetail through `onFolderClick` with the folder's path, `""` being the root.
  - Include Dark and Light previews, as in `ArtistsTab`.

Run `./gradlew :feature:library:testDebugUnitTest --tests "*FoldersTab*"`. Expected: PASS. Run each mutation and revert.

- [ ] **Step 5: Run the checks.** Run `./gradlew :feature:library:check spotlessApply`. Expected: BUILD SUCCESSFUL. Report. Do not commit.

---

### Task 7: FolderDetail screen and ViewModel (ui-builder + viewmodel-architect)

**Files:**
- Create: `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/FolderDetailViewModel.kt`
- Create: `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/FolderDetailScreen.kt`
- Test: `feature/library/src/test/kotlin/com/rolla/musicplayer/feature/library/FolderDetailViewModelTest.kt`
- Test: `feature/library/src/test/kotlin/com/rolla/musicplayer/feature/library/FolderDetailScreenTest.kt`

**Interfaces:**
- Consumes:
  - `FolderRepository`, `FakeFolderRepository`, `folderName` (Task 3)
  - `PlaybackController` helpers `connect`, `playFrom`, `playAllInOrder`, `shuffleAll(List<Song>, Random)`
  - `PlaylistRepository` (`observePlaylists`, `addSongs`, `createPlaylist`)
  - `SongListItem`, `SongOptionsHost` (`:core:ui`)
  - `OneUiDetailTopBar`, `ContentPanel`, `SortHeader`, `CircleIconButton`, `homeListBottomPadding`
  - `TabMessage`
- Produces:
  - `class FolderDetailViewModel`. It reads `SavedStateHandle["folderPath"]`, the route property name Task 8 defines.
  - `@Composable fun FolderDetailRoute(onNavigateUp: () -> Unit, onEditTagsClick: (Song) -> Unit, modifier: Modifier = Modifier, viewModel: FolderDetailViewModel = hiltViewModel())`
  - `internal const val FOLDER_SONG_LIST_TEST_TAG = "folder_song_list"`

- [ ] **Step 1: Write `FolderDetailViewModelTest`.**
  - Start from a copy of `feature/playlists/src/test/kotlin/com/rolla/musicplayer/feature/playlists/FavouritesTabViewModelTest.kt`.
  - The ViewModel is built as `FolderDetailViewModel(SavedStateHandle(mapOf("folderPath" to "Music/Chill")), folderRepository, playbackController, playlistRepository)`, with `folderRepository = FakeFolderRepository()`. `emitFolderSongs` replaces `emit`.
  - Mirror **each** of the precedent's eleven tests (ruling 8):
    1. songs are null before the first emission
    2. tapping a track plays the folder from it
    3. play all delegates to playAllInOrder
    4. shuffle all delegates with the view model's random
    5. play all before the first emission passes an empty list
    6. shuffle all before the first emission passes an empty list
    7. user playlists mirror the repository
    8. creating the view model connects the controller
    9. createPlaylistAndAddSong ignores a blank name
    10. createPlaylistAndAddSong adds the song to the new playlist
    11. addSongToPlaylist adds to the chosen playlist
  - Keep each one's mutation comment, adapted.
  - Then add:

```kotlin
    // Mutations: read savedStateHandle["path"] → checkNotNull throws → RED; observeFolderSongs("") → RED.
    @Test
    fun `it observes the route's folder`() {
        assertEquals(listOf("Music/Chill"), folderRepository.requestedFolderPaths)
    }

    // Review Focus 1. Mutations: title = folderPath → "Music/Chill" and "" → RED.
    @Test
    fun `the title is the folder's name from the route`() {
        assertEquals("Chill", viewModel.title)
        val root = FolderDetailViewModel(
            SavedStateHandle(mapOf("folderPath" to "")), FakeFolderRepository(), playbackController, playlistRepository,
        )
        assertEquals("Internal storage", root.title)
    }
```

- [ ] **Step 2: Implement the ViewModel.** It copies `FavouritesTabViewModel`'s action bodies verbatim (ruling 8).

```kotlin
package com.rolla.musicplayer.feature.library

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.FolderRepository
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.folderName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * FolderDetail (spec §8.7), reached via `FolderDetail(folderPath: String)`. That route lives in `:app` and is never
 * imported here: [folderPath] is read off [SavedStateHandle] by its property name, as AlbumDetailViewModel reads
 * `albumId`. "" is the volume root. [songs] is null until Room answers; the actions treat that as an empty list. The
 * play and playlist actions are FavouritesTabViewModel's (Phase 4b ruling 8).
 */
@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    folderRepository: FolderRepository,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val folderPath: String = checkNotNull(savedStateHandle["folderPath"]) {
        "FolderDetailViewModel requires a folderPath SavedStateHandle argument"
    }

    /** The folder's display name, known from the route before Room answers. */
    val title: String = folderName(folderPath)

    val songs: StateFlow<List<Song>?> = folderRepository.observeFolderSongs(folderPath)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null,
        )

    /** User-created playlists, for the shared "Add to playlist" sheet. */
    val userPlaylists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    /** Picks Shuffle's start track. Tests swap in a seeded [Random]. */
    @VisibleForTesting
    internal var random: Random = Random.Default

    init {
        playbackController.connect()
    }

    /** Plays the folder as the queue, starting at the tapped [song] (Phase 3 ruling 5). */
    fun play(song: Song) {
        playbackController.playFrom(songs.value.orEmpty(), song)
    }

    /** Plays the folder in order from the first track. The helper no-ops on an empty list. */
    fun playAll() {
        playbackController.playAllInOrder(songs.value.orEmpty())
    }

    /** Shuffles the folder from a random start track (spec §12). The helper no-ops on an empty list. */
    fun shuffleAll() {
        playbackController.shuffleAll(songs.value.orEmpty(), random)
    }

    fun addSongToPlaylist(songId: String, playlistId: Long) {
        viewModelScope.launch { playlistRepository.addSongs(playlistId, listOf(songId)) }
    }

    fun createPlaylistAndAddSong(name: String, songId: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(trimmed)
            playlistRepository.addSongs(id, listOf(songId))
        }
    }
}
```

Run the ViewModel test. Expected: PASS. Run each mutation and revert.

- [ ] **Step 3: Write `FolderDetailScreenTest`** (Robolectric).
  - Use `@RunWith(AndroidJUnit4::class)`, `@Config(qualifiers = "w360dp-h640dp")` and `@GraphicsMode(NATIVE)`.
  - Host `FolderDetailContent` in `RollaMusicPlayerTheme(darkTheme = true)` inside `CompositionLocalProvider(LocalMiniPlayerInset provides inset)`.
  - Use `song(id, title)` fixtures as in `FavouritesTabTest`.

```kotlin
    // Mutation: OneUiDetailTopBar(onNavigateUp = {}) → 0 calls → RED.
    @Test
    fun `the bar shows the folder name and navigates up`() {
        var ups = 0
        setContent(title = "Chill", songs = listOf(song("1", "One")), onNavigateUp = { ups++ })
        composeRule.onNodeWithText("Chill").assertExists()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        assertEquals(1, ups)
    }

    // Mutation: onClick = { onSongClick(songs.first()) } → plays "One" for a tap on "Two" → RED.
    @Test
    fun `tapping a track plays from it`() {
        val played = mutableListOf<String>()
        setContent(songs = listOf(song("1", "One"), song("2", "Two")), onSongClick = { played += it.id })
        composeRule.onNodeWithText("Two").performClick()
        assertEquals(listOf("2"), played)
    }

    // Mutation: swap the two callbacks → RED.
    @Test
    fun `shuffle and play call through`() {
        val calls = mutableListOf<String>()
        setContent(
            songs = listOf(song("1", "One")),
            onShuffleClick = { calls += "shuffle" },
            onPlayClick = { calls += "play" },
        )
        composeRule.onNodeWithContentDescription("Shuffle tracks in this folder").performClick()
        composeRule.onNodeWithContentDescription("Play tracks in this folder").performClick()
        assertEquals(listOf("shuffle", "play"), calls)
    }

    // Review Focus 5. Mutations: enabled = true → RED; drop the empty branch → the message is missing → RED.
    @Test
    fun `an emptied folder says so and disables play and shuffle`() {
        setContent(songs = emptyList())
        composeRule.onNodeWithText("No tracks in this folder").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play tracks in this folder").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Shuffle tracks in this folder").assertIsNotEnabled()
    }

    // Mutation: treat null as empty → the message flashes → RED; enabled = songs?.isEmpty() != true → enabled → RED.
    @Test
    fun `nothing renders before the songs load`() {
        setContent(songs = null)
        composeRule.onNodeWithText("No tracks in this folder").assertDoesNotExist()
        composeRule.onNodeWithTag(FOLDER_SONG_LIST_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Play tracks in this folder").assertIsNotEnabled()
    }

    // No dead ⋮ (ruling 8). Mutation: onMoreClick = {} → the sheet never opens → "Edit tags" missing → RED.
    @Test
    fun `the overflow opens the options sheet and edit tags names the song`() {
        val edited = mutableListOf<String>()
        setContent(songs = listOf(song("1", "One"), song("2", "Two")), onEditTagsClick = { edited += it.id })
        composeRule.onNodeWithContentDescription("More options for Two").performClick()
        composeRule.onNodeWithText("Edit tags").performClick()
        assertEquals(listOf("2"), edited)
    }

    // Mutation: drop the list's bottom contentPadding → RED.
    @Test
    fun `the last row scrolls fully above the mini-player`() {
        val songs = List(20) { song("$it", "Track $it") }
        setContent(songs = songs, miniPlayerInset = 60.dp)
        composeRule.onNodeWithTag(FOLDER_SONG_LIST_TEST_TAG).performScrollToIndex(songs.lastIndex)
        val last = composeRule.onNodeWithText("Track 19").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= root.bottom - 60.dp)
    }
```

If the options sheet's "Edit tags" never idles under the PAUSED looper, follow `SongOptionsHostTest`'s precedent: a method-level `@LooperMode(LEGACY)` with a comment.

- [ ] **Step 4: Implement `FolderDetailScreen.kt`** (ruling 7).

```kotlin
@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.feature.library

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rolla.musicplayer.core.designsystem.component.CircleIconButton
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.OneUiDetailTopBar
import com.rolla.musicplayer.core.designsystem.component.SortHeader
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.SongListItem
import com.rolla.musicplayer.core.ui.SongOptionsHost

/** Test handle for FolderDetail's track list. Not `song_list`, which only the Tracks tab may carry (baseline profile). */
internal const val FOLDER_SONG_LIST_TEST_TAG = "folder_song_list"

/**
 * FolderDetail (spec §8.7), reached via `FolderDetail(folderPath)`. That route lives in `:app` and is owned by
 * navigation-agent. Back is [onNavigateUp]; "Edit tags" in the options sheet leaves through [onEditTagsClick].
 */
@Composable
fun FolderDetailRoute(
    onNavigateUp: () -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderDetailViewModel = hiltViewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    val onSongClick = remember(viewModel) { viewModel::play }
    val onShuffleClick = remember(viewModel) { viewModel::shuffleAll }
    val onPlayClick = remember(viewModel) { viewModel::playAll }
    val onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist }
    val onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong }
    FolderDetailContent(
        title = viewModel.title,
        songs = songs,
        userPlaylists = userPlaylists,
        onNavigateUp = onNavigateUp,
        onSongClick = onSongClick,
        onShuffleClick = onShuffleClick,
        onPlayClick = onPlayClick,
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
        modifier = modifier,
    )
}

/**
 * Stateless FolderDetail content; tests and previews host this.
 * - The bar owns the status-bar inset. The panel runs to the bottom edge, and the list pads by the pill and the
 *   navigation bar, so it scrolls under the pill (spec §7.3; ruling 7: no PushedScreen).
 * - [songs] is null until Room answers, and the body renders nothing then.
 * - An empty list means a rescan emptied the folder while it was open.
 */
// The stateless half of FolderDetailRoute: every input is a distinct piece of state or a distinct event.
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun FolderDetailContent(
    title: String,
    songs: List<Song>?,
    userPlaylists: List<Playlist>,
    onNavigateUp: () -> Unit,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optionsSong by remember { mutableStateOf<Song?>(null) }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OneUiDetailTopBar(title = title, onNavigateUp = onNavigateUp)
        ContentPanel {
            val enabled = !songs.isNullOrEmpty()
            SortHeader(label = "Name") {
                CircleIconButton(
                    icon = RollaIcons.Shuffle,
                    contentDescription = "Shuffle tracks in this folder",
                    onClick = onShuffleClick,
                    enabled = enabled,
                )
                CircleIconButton(
                    icon = RollaIcons.Play,
                    contentDescription = "Play tracks in this folder",
                    onClick = onPlayClick,
                    enabled = enabled,
                )
            }
            when {
                songs == null -> Unit
                songs.isEmpty() -> TabMessage("No tracks in this folder")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(FOLDER_SONG_LIST_TEST_TAG),
                    contentPadding = PaddingValues(bottom = homeListBottomPadding()),
                ) {
                    items(items = songs, key = { song -> song.id }) { song ->
                        SongListItem(
                            song = song,
                            onClick = { onSongClick(song) },
                            onMoreClick = { optionsSong = song },
                        )
                    }
                }
            }
        }
    }
    SongOptionsHost(
        song = optionsSong,
        userPlaylists = userPlaylists,
        onDismiss = { optionsSong = null },
        onAddSongToPlaylist = onAddSongToPlaylist,
        onCreatePlaylistAndAddSong = onCreatePlaylistAndAddSong,
        onEditTagsClick = onEditTagsClick,
    )
}
```

Add Dark and Light previews (`PreviewFolderDetailContent`) with six sample songs, as in `FavouritesTab`. Check `SongOptionsHost`'s real parameter names in `core/ui/.../SongOptionsSheet.kt` and match them exactly.

Run `./gradlew :feature:library:testDebugUnitTest --tests "*FolderDetail*"`. Expected: PASS. Run each mutation and revert.

- [ ] **Step 5: Run the checks.** Run `./gradlew :feature:library:check spotlessApply`. Expected: BUILD SUCCESSFUL. Report. Do not commit.

---

### Task 8: App wiring: the sixth tab, the `FolderDetail` route and navigation (navigation-agent)

**Files:**
- Modify: `app/src/main/kotlin/com/rolla/musicplayer/home/HomeTab.kt`
- Modify: `app/src/main/kotlin/com/rolla/musicplayer/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/rolla/musicplayer/navigation/Routes.kt`
- Modify: `app/src/main/kotlin/com/rolla/musicplayer/navigation/MiniPlayerVisibility.kt`
- Modify: `app/src/main/kotlin/com/rolla/musicplayer/MainActivity.kt`
- Test: `app/src/test/kotlin/com/rolla/musicplayer/home/HomeTabTest.kt`
- Test: `app/src/test/kotlin/com/rolla/musicplayer/home/HomeScreenTest.kt`
- Test: `app/src/test/kotlin/com/rolla/musicplayer/navigation/MiniPlayerVisibilityTest.kt`
- Test: `app/src/test/kotlin/com/rolla/musicplayer/navigation/FolderDetailRouteTest.kt` (new)

**Interfaces:**
- Consumes: `FoldersTab` (Task 6); `FolderDetailRoute` (Task 7).
- Produces:
  - `HomeTab.FOLDERS`
  - `HomeRoute(…, onFolderClick: (String) -> Unit, …)`
  - `@Serializable data class FolderDetail(val folderPath: String) : Route`

- [ ] **Step 1: Update the tests first.**
  - **`HomeTabTest`:** the expected list becomes `listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists", "Folders")`. Mutation: leave out FOLDERS → RED.
  - **`HomeScreenTest`:** rename `` `home shows all five tab titles` `` to `` `home shows all six tab titles` ``, and update its mutation comment ("drop the last title → "Folders" missing → RED").
    - Check that "Folders" is findable with `assertExists()` at the test's 360 dp width: `OneUiTabRow` must compose every title.
    - If it is not composed off-screen, report it. Do not weaken the test.
  - **`MiniPlayerVisibilityTest`:** `assertEquals(14, allRoutes.size)` becomes `15`. Where the test lists routes that show the pill, add `FolderDetail::class` (spec §7.3 lists FolderDetail as showing the pill). Mutation: add `FolderDetail::class` to `routesWithoutMiniPlayer` → RED.
  - **New `FolderDetailRouteTest`** (Robolectric, `createComposeRule()`). It pins ruling 12 and the `SavedStateHandle` key the ViewModel reads:

```kotlin
package com.rolla.musicplayer.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDetailRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Review Focus 1 (ruling 12): the root folder's empty path is a valid route argument.
    // Mutation: rename the route property (folderPath → path) → savedStateHandle["folderPath"] is null → RED.
    @Test
    fun theRootFoldersEmptyPathRoundTrips() = assertRoundTrip("")

    // Review Focus 5. Mutation: navigate with folderPath.trim('/') or a lossy encoding → RED.
    @Test
    fun aPathWithReservedAndNonAsciiCharactersRoundTrips() = assertRoundTrip("Música/Rock & Roll #1 ?%/x y")

    private fun assertRoundTrip(path: String) {
        lateinit var navController: NavHostController
        var routeArgument: String? = null
        var savedStateArgument: String? = null
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Home) {
                composable<Home> {}
                composable<FolderDetail> { entry ->
                    routeArgument = entry.toRoute<FolderDetail>().folderPath
                    savedStateArgument = entry.savedStateHandle.get<String>("folderPath")
                }
            }
        }
        composeRule.runOnIdle { navController.navigate(FolderDetail(folderPath = path)) }
        composeRule.waitForIdle()
        assertEquals(path, routeArgument)
        assertEquals(path, savedStateArgument)
    }
}
```

  If `theRootFoldersEmptyPathRoundTrips` is RED once Step 2 lands, stop and report BLOCKED with the exception (ruling 12).

- [ ] **Step 2: Wire the route and the tab.**
  - **`Routes.kt`:**
    - Fix the stale `ArtistDetail` comment (Phase 4a carry-over). It should read: "Routed by NAME, not id: the songs schema has no artist_id column (artists exist only as a GROUP BY artist grouping). Artist.id is a synthetic FNV-1a hash that is display-only; lists key artists by name (redesign Phase 4a ruling 12). The name is the real identity and ArtistRepository is keyed by it. Contrast AlbumDetail above (real album_id)."
    - Update the `Home` comment to "(the six library tabs)".
    - Add:

```kotlin
// Routed by the folder's path relative to its storage volume ("Music/Chill"); "" is the root ("Internal storage"),
// which Navigation 2.8 accepts as an empty path argument (FolderDetailRouteTest). The path is the folder's identity
// and FolderRepository is keyed by it. FolderDetailViewModel reads it by this property name.
@Serializable
data class FolderDetail(val folderPath: String) : Route
```

  - **`MiniPlayerVisibility.kt`:** add `FolderDetail::class` to `allRoutes`, after `ArtistDetail::class`. FolderDetail shows the pill, so it is *not* added to `routesWithoutMiniPlayer`.
  - **`HomeTab.kt`:** add `FOLDERS("Folders"),` last. Update the KDoc to say all six spec §7.2 tabs exist; drop "Folders lands at the end in Phase 4b".
  - **`HomeScreen.kt` (`HomeRoute`):**
    - Add the parameter `onFolderClick: (String) -> Unit,` after `onArtistClick`.
    - Add the branch `HomeTab.FOLDERS -> FoldersTab(scanState = scanState, onFolderClick = onFolderClick)`.
    - Update the KDoc ("…Albums / Artists / Folders tab row… hosting those six tabs… folder taps through [onFolderClick] (folder path)") and the suppression comment ("Nine navigation callbacks").
  - **`MainActivity.kt`:**
    - In `composable<Home>`'s `HomeRoute(…)`, after `onArtistClick`, add:

```kotlin
            onFolderClick = { path ->
                navController.navigate(FolderDetail(folderPath = path)) { launchSingleTop = true }
            },
```

    - Fold `onFolderClick` into the existing "No popUpTo on either detail route" comment ("on any detail route").
    - After `composable<ArtistDetail>`, add:

```kotlin
    // FolderDetail is reached from Home's Folders tab only. Back = navigateUp(), so it pops back to Home on the same
    // tab (the pager state is saveable). It is not wrapped in PushedScreen: its list pads by the pill itself and
    // scrolls under it (Phase 4b ruling 7). "Edit tags" pushes TagEditor like Home's options sheet does.
    composable<FolderDetail> {
        FolderDetailRoute(
            onNavigateUp = { navController.navigateUp() },
            onEditTagsClick = { song ->
                song.id.toLongOrNull()?.let { id ->
                    navController.navigate(TagEditor(id)) { launchSingleTop = true }
                }
            },
        )
    }
```

  - Grep `app/src/test` for every `HomeRoute(` call site and add `onFolderClick = {}`. Other `HomeRoute`/`HomeScreen` hosts must still compile.

- [ ] **Step 3: Run the app's tests and check.** Run `./gradlew :app:testDebugUnitTest :app:check spotlessApply`. Expected: BUILD SUCCESSFUL, with `RouteClassTest`, `HomeScreenRestorationTest` and `NowPlayingNavigationTest` green and unmodified. Run each named mutation and revert. Report. Do not commit.

---

### Task 9: Docs and the phase gate (controller)

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`
- Modify: `docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md`
- Modify: `.claude/rules/navigation-conventions.md`

- [ ] **Step 1: Amend the spec, citing each ruling ("amended 2026-10-12, Phase 4b ruling N").**
  - **§9.4:**
    - `FolderDao` replaces `SongDao.observeFolders()` (ruling 1).
    - The repository sorts by display name, then path (ruling 2).
    - The stale rule becomes `existing.folderPath != scanned.folderPath` (ruling 3).
    - The DATA prefixes add `/storage/self/primary/` and `/mnt/sdcard/` (ruling 16).
    - Songs in a folder sort by `title COLLATE NOCASE` (ruling 10).
  - **§5.1:** add `folderBadgeStroke` `#FFFFFF`/`#FFFFFF` and `folderBadgeFill` `#000000`/`#000000` (ruling 5).
  - **§5.4:** add `folderBadge` 20 dp and `folderBadgeInset` 2 dp.
  - **§5.5:** add `FolderBadgeFill` (solid).
  - **§8.1:** the FolderRow badge is "a white outline over a black fill, `folderBadgeInset` from the thumbnail's bottom-end corner".

- [ ] **Step 2: Sweep project instructions** (CLAUDE.md drift rule):
  - `grep -n "version = 3\|v3\|Folders\|folder" CLAUDE.md .claude/rules/*.md`
  - Add a **Phase 4b** bullet to CLAUDE.md's redesign banner covering:
    - the Folders tab (`FoldersTab`) and FolderDetail
    - Room **v4** (`songs.folder_path`, `MIGRATION_3_4`, `ALL_MIGRATIONS`)
    - `FolderDao`/`FolderRepository`/`Folder`
    - the scanner's `FolderPaths`
    - the re-scan rule
  - Note that every "DB v3" claim elsewhere in CLAUDE.md is now v4. Fix any such claim in place, or say the Phase 7 docs pass rewrites it.
  - In `.claude/rules/navigation-conventions.md`, add `FolderDetail(folderPath: String)` to the route examples, with one line: "`""` is the root folder; empty path arguments are supported (FolderDetailRouteTest)." The full Home and bottom-nav rewrite stays with Phase 7 (spec §16).

- [ ] **Step 3: Run the full gate** in the background, logging to the SDD workspace:
  - `./gradlew check assembleDebug :core:database:compileDebugAndroidTestKotlin --continue`
  - Expected: BUILD SUCCESSFUL. On failure, fix forward through the owning task's agent, never by deleting tests.
- [ ] **Step 4: Phase review.** Generate the review package from the phase base tree to head and dispatch the whole-phase Opus `code-reviewer`.
  - Include a **Phase 5 contract check** (observation 0006): the Folders list must sort by the same key the rail will index (the display name), and must be a `LazyColumn` whose state Phase 5 can hoist.
  - One fix wave, a scoped re-review, then adjudicate what remains.

---

### Task 10: Device check on the SM-S901B (controller)

> **Safety order, binding (ruling 13).**
> - Device: Samsung SM-S901B, serial `R3CTA03TZTY`, never the Motorola.
> - Never uninstall the app or clear its data. Never change system settings. Leave playlists, Equaliser presets and settings untouched, and restore anything a check changes.
> - adb is `MSYS_NO_PATHCONV=1 "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s R3CTA03TZTY`.

- [ ] **Step 1: Connected Room tests first.**
  - Run `./gradlew :core:database:connectedDebugAndroidTest`. It installs only the library's test APK, never the app.
  - Expected: every test passes, `MigrationTest.migrate3To4_*`, `MigrationTest.allMigrations_*` and `FolderDaoTest` included.
  - **If anything fails, stop here.** The app is not installed until this is green.
- [ ] **Step 2: Device mutation proofs.** Apply Task 1's androidTest mutations one at a time and re-run only the affected test class. Each must be RED, each source file is restored byte-exact afterwards, and `git diff` is clean against the gate tree.
- [ ] **Step 3: Back up the user's database before the upgrade.**
  - Check with `adb shell run-as com.rolla.musicplayer ls databases`.
  - If the installed build is debuggable, `exec-out run-as … cat` each of `music_database`, `music_database-wal` and `music_database-shm` into the SDD workspace (git-ignored, local only).
  - If it isn't debuggable (the 4a benchmark build may not be), record that no backup was possible. The connected migration tests from Step 1 are then the safety net.
- [ ] **Step 4: Install the upgrade.**
  - Build with `./gradlew :app:assembleBenchmark`.
  - Compare signing certs: `apksigner verify --print-certs` on the APK must show the debug cert (SHA-256 `2950a9a2…`), matching the installed app.
  - Then run `install -r app/build/outputs/apk/benchmark/app-benchmark.apk` and `cmd package compile -f -m speed-profile com.rolla.musicplayer`.
- [ ] **Step 5: Exit criteria on the device.** Capture each into `docs/design/calibration/phase4b/` (uncommitted).
  - The app opens, so the migration ran. The Playlists tab still shows the user's playlists, which proves user data survived (read-only check).
  - The Folders tab lists real folders after the first sync ("An upgraded install backfills folders on the first sync"). There is a badge on each thumbnail, and the paths read "/Internal storage/…".
  - Open a folder: FolderDetail lists its tracks. Play from a row, then pause. Use ⋮, then dismiss.
  - Back returns to Home on the Folders tab.
  - The pill floats over FolderDetail, and the last row scrolls above it.
  - Run a `dumpsys gfxinfo` scroll pass on the Folders tab, and record jank and p99.
- [ ] **Step 6: Restore** anything the check changed (pause playback, delete any screen recording from the device), then ledger the acceptance.

---

## Self-review

- **Spec coverage:**
  - §7.1 route: Task 8.
  - §7.3 pill: Tasks 7 and 8.
  - §8.1 FolderRow: Task 5, badge Task 4.
  - §8.2 Folders tab: Task 6.
  - §8.7 FolderDetail: Task 7.
  - §9.4:
    - schema, migration and `4.json`: Task 1
    - scanner columns and `FolderPaths`: Task 2
    - backfill: Task 2
    - queries: Task 1
    - model and repository: Task 3
    - display path: Task 3
    - known limitation: unchanged, documented in the spec
  - §15:
    - `FolderPaths` tests (Samsung, AOSP, root, SD UUID, null): Task 2
    - `FoldersTabViewModel` and `FolderDetailViewModel` tests: Tasks 6 and 7
    - `LibraryIndexer` backfill and the root rule: Task 2
    - `Migration3To4Test` and the new DAO queries: Task 1, run in Task 10
  - **Not covered here:** the app-level instrumented smoke path "Tap Folders, open a folder" is not in this phase. No app-level androidTest suite exists, and spec §15 schedules the smoke-suite rewrite with Phase 7's final gate. Task 10 exercises the same path by hand on the device, and the ledger carries the smoke test to Phase 7.
- **Value tables (observation 0007):** every new token (two dimensions, two colors, one icon) is listed in ruling 5 and amended into the spec tables by Task 9. No existing token value changes.
- **Consumers (observation 0007):**
  - Grep checks:
    - `SyncResult.added` (Home `ScanState.Done`, the Settings rescan message "added or updated"): the first post-upgrade sync reports every backfilled row as added or updated, which is accurate copy.
    - `ScannedSong.toEntity` is the only production `SongEntity` builder.
    - `SongEntity(` callers in tests compile, because the new parameter is defaulted.
    - `HomeTab.entries`, the `allRoutes` size and the `HomeRoute(` call sites are all handled in Task 8.
  - The ruling 3 change alters *when* rows re-upsert. `LibraryIndexerTest`'s existing "unchanged" test keeps its meaning through equal helper defaults.
- **Can each test fail (observation 0009):**
  - Every test names a mutation, and the fixtures separate what they distinguish:
    - first title in the middle of the scan, and NOCASE against binary
    - DAO path order against name order
    - same-name folders
    - root `''` against NULL
    - stored folder against scanned folder
  - Badge pixel samples are named in grid units, with the margin from each edge stated.
- **Type consistency:**
  - `folderPath` is the route property, the `SavedStateHandle` key and the entity property.
  - `FolderSummary` is the DAO row; `Folder` is the model.
  - `observeFolderSongs` is the repository name; `observeSongsInFolder` is the DAO name.
  - `FOLDER_LIST_TEST_TAG` and `FOLDER_SONG_LIST_TEST_TAG` are distinct, and neither is `song_list`.
