# Custom Tags, Phase 1 (Foundation) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app can store a track's custom tag and subtag IDs in the file's `ROLLA_TAGS` field (MP3, FLAC, M4A),
reads them and the genre back from files during the library scan into Room, and keeps the two user lists with stable,
never-reused IDs. No screen changes in this phase.

**Architecture:** A new `:core:tagio` module owns all jaudiotagger code shared below the feature layer: the existing
tag-access helpers (moved from `:feature:tageditor`), the `ROLLA_TAGS` JSON codec and the field's read/write per format.
Room v6 adds the two lists, a counter per list, the song links and two `songs` columns (`genre`, `file_tags_read`).
After its MediaStore diff, `LibraryIndexer` runs a `FileTagPass` that reads `ROLLA_TAGS` and the genre from every row
not yet read, in batches, outside the indexer's mutex. `CustomTagRepository` in `:core:data` is the one API the later
phases use.

**Tech Stack:** Kotlin 2.2, Room (5 → 6), Hilt 2.57.2, jaudiotagger (`com.github.Adonai:jaudiotagger:2.3.15`),
kotlinx-serialization-json (already in the catalog), JUnit4/MockK/Turbine/Robolectric.

**Spec:** [[2026-11-09-custom-tags-design]] (approved 2026-11-09). Roadmap: [[2026-11-09-custom-tags-roadmap]]. Where
this plan and the spec disagree, the spec wins; record a ruling.

## Global Constraints

- Offline only: no network, no new permission. The only new dependency edge is `:core:tagio` → jaudiotagger and
  kotlinx-serialization-json, both already in `gradle/libs.versions.toml` (lines 37, 114, 125).
  `checkReleaseManifestNoInternet` stays green.
- Field name `ROLLA_TAGS`, never the comment or any standard field (spec §5.1):
  MP3 → ID3v2 `TXXX` with description `ROLLA_TAGS`; FLAC → Vorbis comment `ROLLA_TAGS`; M4A → freeform atom, mean
  `com.apple.iTunes`, name `ROLLA_TAGS`.
- Value format, verbatim (spec §5.1): `{"tags":"2,3","subTags":"1,3,4"}`: comma-separated decimal IDs, ascending, no
  spaces; an empty list is `""`. A track with no IDs has no `ROLLA_TAGS` field.
- Formats: MP3, FLAC, M4A only, by file extension (`mp3`, `flac`, `m4a`, case-insensitive).
- IDs are never reused per list; the counter only rises: on create, and to the highest ID found in any file (spec §6
  calls 2 and 12). Tag 2 and subtag 2 are different entries.
- Unknown IDs (in a file, not in the list) are stored as links and kept; they are simply not named (spec §6 call 4).
- Names: trimmed, 1–40 characters (code points), unique per list ignoring case (`lowercase(Locale.ROOT)`); a tag and a
  subtag may share a name (spec §6 call 8). Lists sort in library order (`sortedByLibraryName`, `:core:common`), as
  playlists do (redesign ruling 1, `PlaylistRepositoryImplTest.observePlaylists_sortsInLibraryOrder`).
- Vocabulary: `Song` in code; new model names `CustomTag`, `CustomTagKind`, `CustomTagIds` get rows in
  `.claude/rules/model-vocabulary.md` before code uses them ([[Song in code, Tracks on screen]]).
- Module direction `:feature → :core → :core:model` ([[Module dependency direction]]). `:core:tagio` depends only on
  `:core:model`.
- No `sed -i` on sources; no Kotlin, Gradle or XML through shell heredocs
  ([[Edit files with Write and Edit, not the shell]]). Each task's last step means "stage the files and report ready to commit"; the user commits
  ([[Commits are user-triggered]]). Implementers may make mechanical fixes to this plan's code and API hints; it was
  never compiled.
- Device: Room DAO and migration tests are instrumented and run only as `:core:database:connectedDebugAndroidTest` on the
  Motorola `ZY22MBNVB4` ([[Device tests run on the Motorola]]); never the root `connectedDebugAndroidTest`. The agent
  never saves tags on a device ([[Device safety]]).

## Review Focus

1. **A `ROLLA_TAGS` value written by hand in another program** (`Techno`, `{"tags":"a,2"}`, `{"tags":2}`). The scan never
   crashes, links only the valid positive IDs, and never rewrites the file. Pinned by Task 2's `decode_*` tests and
   Task 5's `pass_undecodableValue_linksNothingAndMarksRead`.
2. **A file that cannot be opened during the pass** (deleted after the MediaStore query, unreadable, corrupt). That row is
   marked read, keeps its earlier links and genre, the failure is counted, and the pass goes on. Pinned by Task 5's
   `pass_readThrows_keepsOldLinksMarksReadAndContinues`.
3. **The first scan after the upgrade on a large library.** Rows appear in the lists before any file is read (the
   MediaStore upsert commits first) and the pass reads in batches. Pinned by Task 5's
   `sync_upsertsSongsBeforeTheFilePassRuns`.
4. **A file tagged on another phone with IDs this phone never issued.** The links are stored, and the next create in
   that list gets an ID above them. Pinned by Task 4's `applyFileTags_raisesCounterToHighestFileId` and Task 6's
   `create_afterFileCarriedHigherId_issuesAboveIt`.
5. **Names that differ only by case or surrounding spaces, and long names with emoji.** `" techno "` against `Techno`
   is a duplicate; a 40-emoji name is accepted and 41 is too long. Pinned by Task 6's `create_*` tests.

---

### Task 1: `:core:tagio` module, with the shared tag helpers and audio fixtures moved into place

Owner: build-tooling-agent (module) with tag-editor-agent (moved IO). A pure move plus wiring: no behaviour changes.

**Files:**
- Modify: `settings.gradle.kts` (add `":core:tagio"` to the core `include(...)` block)
- Create: `core/tagio/build.gradle.kts`, `core/tagio/src/main/AndroidManifest.xml` only if the library convention
  needs one (copy `:core:common`'s layout)
- Move (`git mv`): `feature/tageditor/src/main/kotlin/com/rolla/musicplayer/feature/tageditor/io/TagAccess.kt` →
  `core/tagio/src/main/kotlin/com/rolla/musicplayer/core/tagio/TagAccess.kt`
- Modify: `feature/tageditor/.../io/TagWriter.kt` (move `Tag.setFieldIfNotBlank` out to `TagAccess.kt`),
  `feature/tageditor/.../io/TagReader.kt` (imports), `feature/tageditor/build.gradle.kts`
- Move (`git mv`): `feature/tageditor/src/test/.../io/AudioFixtures.kt` and `M4aFixture.kt` →
  `core/testing/src/main/kotlin/com/rolla/musicplayer/core/testing/audio/`
- Modify: `FlacTagReaderWriterTest.kt`, `M4aTagReaderWriterTest.kt`, `Mp3TagReaderWriterTest.kt` (imports only)

Inventory (grep run 2026-11-09, `grep -rln "editableTag\|hasAnyArtwork\|deleteEveryArtwork\|setFieldIfNotBlank"
--include=*.kt`): `TagAccess.kt`, `TagReader.kt`, `TagWriter.kt`. Fixture users (`grep -rln "AudioFixtures\|M4aFixture"`):
the two fixture files and the three `*TagReaderWriterTest.kt` files. Both greps, excluding `build/`, are the task's
acceptance check: afterwards the helpers resolve only from `com.rolla.musicplayer.core.tagio` and the fixtures only from
`com.rolla.musicplayer.core.testing.audio`.

**Interfaces:**
- Produces (package `com.rolla.musicplayer.core.tagio`, all public, KDoc and rulings P16–P19 comments kept verbatim):
  `fun AudioFile.editableTag(): Tag`, `fun Tag.hasAnyArtwork(): Boolean`, `fun Tag.deleteEveryArtwork()`,
  `fun Tag.setFieldIfNotBlank(key: FieldKey, value: String)`.
- Produces (package `com.rolla.musicplayer.core.testing.audio`, public): `object AudioFixtures` and the M4A fixture,
  same members as today.
- `core/tagio/build.gradle.kts`: plugins `rolla.android.library`, `rolla.android.hilt`; namespace
  `com.rolla.musicplayer.core.tagio`; `implementation(project(":core:model"))`, `api(libs.jaudiotagger)` (the helpers
  expose jaudiotagger types), `implementation(libs.kotlinx.serialization.json)`; tests: `junit`,
  `kotlinx.coroutines.test`, `testImplementation(project(":core:testing"))` (the same test-only loop `:core:data` already
  has with `:core:testing`).
- `:feature:tageditor` adds `implementation(project(":core:tagio"))` and keeps `implementation(libs.jaudiotagger)`.

- [ ] **Step 1: Run the existing tag-IO suite to record the baseline**

Run: `./gradlew :feature:tageditor:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Quote the result line.

- [ ] **Step 2: Create the module, move the files, fix imports and dependencies**

- [ ] **Step 3: Run the suite again, plus the new module's build and static analysis**

Run: `./gradlew :feature:tageditor:testDebugUnitTest :core:tagio:assembleDebug :core:testing:assembleDebug spotlessCheck detekt`
Expected: BUILD SUCCESSFUL with the same test count as Step 1. Run both acceptance greps and paste their hit lists.

- [ ] **Step 4: Stage and report ready to commit** (`refactor(tagio): move the shared tag helpers into :core:tagio`)

---

### Task 2: Model names and the `ROLLA_TAGS` JSON codec

Owner: data-layer-agent (model) with tag-editor-agent (codec).

**Files:**
- Create: `core/model/src/main/kotlin/com/rolla/musicplayer/core/model/CustomTag.kt`
- Modify: `.claude/rules/model-vocabulary.md` (canonical models block and naming conventions: the three names below)
- Create: `core/tagio/src/main/kotlin/com/rolla/musicplayer/core/tagio/RollaTagsJson.kt`
- Test: `core/tagio/src/test/kotlin/com/rolla/musicplayer/core/tagio/RollaTagsJsonTest.kt`

**Interfaces:**
- Produces (`:core:model`):
  ```kotlin
  enum class CustomTagKind { TAG, SUBTAG }
  data class CustomTag(val kind: CustomTagKind, val id: Long, val name: String)
  data class CustomTagIds(val tagIds: Set<Long>, val subTagIds: Set<Long>) {
      val isEmpty: Boolean
      companion object { val EMPTY: CustomTagIds }
  }
  ```
- Produces (`:core:tagio`): `object RollaTagsJson { const val FIELD_NAME = "ROLLA_TAGS"; fun encode(ids: CustomTagIds): String; fun decode(value: String): CustomTagIds? }`.
  `decode` parses with `Json.parseToJsonElement`; returns null when the value is not a JSON object; a missing or
  non-string `tags`/`subTags` reads as empty; each comma-separated entry is trimmed, and entries that are not a positive
  decimal `Long` are dropped.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun encode_sortsAscendingWithoutSpaces() =
    assertEquals("""{"tags":"2,3","subTags":"1,3,4"}""", RollaTagsJson.encode(CustomTagIds(setOf(3, 2), setOf(4, 1, 3))))
@Test fun encode_emptyListsAreEmptyStrings() =
    assertEquals("""{"tags":"","subTags":"5"}""", RollaTagsJson.encode(CustomTagIds(emptySet(), setOf(5))))
@Test fun decode_userFormat_roundTrips() =
    assertEquals(CustomTagIds(setOf(2, 3), setOf(1, 3, 4)), RollaTagsJson.decode("""{ "tags": "2, 3", "subTags":"1,3,4"}"""))
@Test fun decode_notJson_returnsNull() = assertNull(RollaTagsJson.decode("Techno"))
@Test fun decode_jsonArray_returnsNull() = assertNull(RollaTagsJson.decode("[1,2]"))
@Test fun decode_badEntries_keepsOnlyPositiveIds() =
    assertEquals(CustomTagIds(setOf(2, 7), emptySet()), RollaTagsJson.decode("""{"tags":"a, 2,,-1,0, 7 ","subTags":5}"""))
@Test fun decode_missingKey_readsAsEmpty() =
    assertEquals(CustomTagIds(emptySet(), setOf(9)), RollaTagsJson.decode("""{"subTags":"9"}"""))
@Test fun customTagIds_isEmpty() {
    assertTrue(CustomTagIds.EMPTY.isEmpty)
    assertFalse(CustomTagIds(setOf(1), emptySet()).isEmpty)
    assertFalse(CustomTagIds(emptySet(), setOf(1)).isEmpty)
}
```

(`CustomTagIds_isEmpty` lives in the same test class; `:core:model` has no Android test set.)

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :core:tagio:testDebugUnitTest --tests "*RollaTagsJsonTest"`
Expected: FAIL (compilation: `RollaTagsJson` / `CustomTagIds` unresolved).

- [ ] **Step 3: Implement the model file and `RollaTagsJson`**

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :core:tagio:testDebugUnitTest --tests "*RollaTagsJsonTest" :core:model:test`
Expected: PASS.

- [ ] **Step 5: Stage and report ready to commit** (`feat(tagio): custom tag model and the ROLLA_TAGS codec`)

---

### Task 3: Read and write `ROLLA_TAGS` (and read the genre) on MP3, FLAC and M4A

Owner: tag-editor-agent. This task is the spec §9 format round-trip risk: it runs first among the IO tasks and its
contract tests go through real jaudiotagger reads and commits on fixture files (superpowers-extras writing-plans rule 3).

**Files:**
- Create: `core/tagio/src/main/kotlin/com/rolla/musicplayer/core/tagio/RollaTagsFileIo.kt`
- Test: `core/tagio/src/test/kotlin/com/rolla/musicplayer/core/tagio/RollaTagsFileIoTest.kt`

**Interfaces:**
- Consumes: Task 1's `editableTag()`, Task 2's `RollaTagsJson`, `CustomTagIds`.
- Produces:
  ```kotlin
  data class FileTags(val customTagIds: CustomTagIds, val genre: String?)
  fun File.supportsCustomTags(): Boolean            // extension in mp3, flac, m4a, case-insensitive
  class RollaTagsFileIo @Inject constructor() {
      fun read(file: File): FileTags                // never writes; absent or undecodable field → CustomTagIds.EMPTY; blank genre → null
      fun writeCustomTagIds(file: File, ids: CustomTagIds)  // ids.isEmpty → removes the field; commits in place
  }
  ```
  Plain blocking functions: callers choose the dispatcher. `read` reads the tag as on disk through `editableTag()` (so an
  ID3v1-only MP3's genre is found) and never commits. `writeCustomTagIds` sets
  `TagOptionSingleton.getInstance().isPreserveFileIdentity = true` before reading, edits through `editableTag()` and
  replaces any existing `ROLLA_TAGS` value instead of adding a second one.
- API hints (verify; substitute an equivalent if the fork differs): MP3: `TXXX` frames via `tag.getFields("TXXX")`,
  matching `(frame.body as FrameBodyTXXX).description == "ROLLA_TAGS"`, ISO-8859-1 text (the value is ASCII); FLAC:
  `(tag as FlacTag).vorbisCommentTag` `setField`/`deleteField("ROLLA_TAGS")`; M4A: `Mp4TagReverseDnsField` with issuer
  `com.apple.iTunes`, descriptor `ROLLA_TAGS`, key `----:com.apple.iTunes:ROLLA_TAGS`.

- [ ] **Step 1: Write the failing tests** (fixtures from `com.rolla.musicplayer.core.testing.audio`, written to a
  `TemporaryFolder` with the right extension)

```kotlin
private val ids = CustomTagIds(setOf(2, 3), setOf(1, 3, 4))
private val expected = """{"tags":"2,3","subTags":"1,3,4"}"""

@Test fun mp3_untagged_roundTrips()            // write ids, then read(file).customTagIds == ids
@Test fun mp3_id3v1Only_roundTripsAndKeepsV1Text() // after write: read == ids, and AudioFileIO.read(file).tag title/artist/album == the v1 values
@Test fun mp3_id3v24_roundTrips()              // fixture with an ID3v2.4 tag
@Test fun mp3_writesTxxxWithExactValue()      // the TXXX frame with description ROLLA_TAGS has text == expected
@Test fun mp3_keepsCommentOtherTxxxAndArtwork() // seed COMMENT "my notes", a TXXX "MIK_ENERGY"="6" and a picture; after write all three unchanged
@Test fun mp3_secondWriteReplacesNotDuplicates() // write ids, write CustomTagIds(setOf(9), emptySet()); exactly one ROLLA_TAGS TXXX, value {"tags":"9","subTags":""}
@Test fun mp3_writeEmptyRemovesField()        // write ids then EMPTY: no TXXX with description ROLLA_TAGS remains; read == EMPTY
@Test fun flac_roundTripsAndKeepsComment()    // Vorbis ROLLA_TAGS == expected; COMMENT unchanged
@Test fun flac_writeEmptyRemovesField()
@Test fun m4a_roundTripsUnderAppleMean()      // tag.getFirst("----:com.apple.iTunes:ROLLA_TAGS") == expected
@Test fun m4a_writeEmptyRemovesField()
@Test fun read_undecodableValue_returnsEmpty() // seed TXXX ROLLA_TAGS = "Techno" by hand; read == EMPTY; file bytes unchanged (compare before/after)
@Test fun read_genre_fromV2AndFromV1Only()    // "Techno" from an ID3v2 TCON; "Rock" from an ID3v1-only fixture with genre 17
@Test fun read_blankGenre_isNull()
@Test fun supportsCustomTags_byExtension()    // MP3/flac/M4a true; wav, ogg, aac, "" false
```

Each test asserts the stated values; "unchanged" compares the seeded value read back with jaudiotagger after the
commit.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :core:tagio:testDebugUnitTest --tests "*RollaTagsFileIoTest"`
Expected: FAIL (compilation: `RollaTagsFileIo` unresolved).

- [ ] **Step 3: Implement `RollaTagsFileIo` and `supportsCustomTags`**

If a format cannot hold the field through jaudiotagger (most likely M4A), stop and report BLOCKED with the failing
assertion: the spec's field format is user-visible and is not changed by the implementer.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :core:tagio:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Stage and report ready to commit** (`feat(tagio): read and write the ROLLA_TAGS field`)

---

### Task 4: Room v6 — the lists, counters, song links, genre and the read flag

Owner: data-layer-agent.

**Files:**
- Create: `core/database/.../entity/CustomTagEntity.kt`, `CustomTagCounterEntity.kt`, `SongCustomTagCrossRef.kt`
- Modify: `core/database/.../entity/SongEntity.kt` (two columns), `MusicDatabase.kt` (entities, `version = 6`,
  `customTagDao()`), `di/DatabaseModule.kt` (provider), `migration/Migrations.kt` (`ALL_MIGRATIONS` gains
  `MIGRATION_5_6`)
- Create: `core/database/.../migration/Migration5To6.kt`, `core/database/.../dao/CustomTagDao.kt`
- Create: `core/database/schemas/.../6.json` (generated by the build)
- Create: `core/testing/src/main/kotlin/com/rolla/musicplayer/core/testing/FakeCustomTagDao.kt` (follow
  `FakePlaylistDao`'s style)
- Test: `core/database/src/androidTest/.../migration/MigrationTest.kt` (new test),
  `core/database/src/androidTest/.../dao/CustomTagDaoTest.kt`

**Interfaces:**
- Schema (the migration's SQL and the entities agree; Room validates):
  - `custom_tags(kind TEXT NOT NULL, id INTEGER NOT NULL, name TEXT NOT NULL, name_key TEXT NOT NULL, PRIMARY KEY(kind, id))`,
    unique index `index_custom_tags_kind_name_key` on `(kind, name_key)`. `kind` stores `CustomTagKind.name`.
  - `custom_tag_counters(kind TEXT NOT NULL PRIMARY KEY, highest_id INTEGER NOT NULL)`.
  - `song_custom_tags(song_id TEXT NOT NULL, kind TEXT NOT NULL, tag_id INTEGER NOT NULL, PRIMARY KEY(song_id, kind, tag_id),
    FOREIGN KEY(song_id) REFERENCES songs(id) ON DELETE CASCADE)`, index on `(kind, tag_id)` and on `song_id`.
  - `songs` gains `genre TEXT DEFAULT NULL` and `file_tags_read INTEGER NOT NULL DEFAULT 0`; `SongEntity` gains
    `val genre: String? = null` and `val fileTagsRead: Boolean = false`. `toDomain()` is unchanged in this phase.
- Produces `CustomTagDao`:
  ```kotlin
  fun observeCustomTags(kind: CustomTagKind): Flow<List<CustomTagEntity>>
  suspend fun findByNameKey(kind: CustomTagKind, nameKey: String): CustomTagEntity?
  suspend fun findById(kind: CustomTagKind, id: Long): CustomTagEntity?
  @Transaction suspend fun insertNew(kind: CustomTagKind, name: String, nameKey: String): Long   // id = counter + 1, then counter = id
  suspend fun rename(kind: CustomTagKind, id: Long, name: String, nameKey: String): Int
  @Transaction suspend fun applyFileTags(songId: String, ids: CustomTagIds, genre: String?)    // replaces the song's links, raises each counter to the max ID, sets genre and file_tags_read = 1
  suspend fun markFileTagsRead(songId: String): Int                                            // sets file_tags_read = 1 only
  suspend fun songsNeedingFileTagRead(limit: Int): List<SongEntity>                            // file_tags_read = 0 ORDER BY date_modified DESC LIMIT :limit
  fun observeSongCustomTagIds(songId: String): Flow<List<SongCustomTagCrossRef>>
  ```
  "Counter" absent reads as 0. `insertNew` and `applyFileTags` raise it with an upsert of
  `MAX(highest_id, :candidate)`, never lower it.
- Earlier ruling honoured: `:core:database` has no unit-test source set (per-track artwork ruling P3), so DAO and
  migration tests are instrumented; anything testable without Room goes in `:core:data`.

- [ ] **Step 1: Write the failing instrumented tests**

```kotlin
// MigrationTest
@Test fun migrate5To6_keepsSongRowAndAddsTablesAndColumns()
// seed a v5 song row; migrate with MIGRATION_5_6 and validateDroppedTables = true;
// assert: the row's title and is_favorite unchanged, genre IS NULL, file_tags_read == 0,
// and the three new tables exist and are empty (SELECT COUNT(*) == 0).

// CustomTagDaoTest (in-memory MusicDatabase)
@Test fun insertNew_issuesOneThenTwoPerKind()                 // TAG: 1, 2; SUBTAG: 1 (separate counters)
@Test fun insertNew_afterDeleteOfTopRow_neverReusesId()       // insert 1, 2; delete row 2 by raw SQL; next insert == 3
@Test fun insertNew_duplicateNameKey_throws()                 // SQLiteConstraintException for the same (kind, name_key); a SUBTAG with that key succeeds
@Test fun applyFileTags_replacesLinksAndSetsGenreAndReadFlag()// links {TAG 2, SUBTAG 1} then {TAG 3}: only TAG 3 remains; genre "Techno"; fileTagsRead true
@Test fun applyFileTags_raisesCounterToHighestFileId()        // file carries TAG 40; next insertNew(TAG) == 41
@Test fun applyFileTags_lowerIdsNeverLowerCounter()           // counter at 41, file carries TAG 3; next insertNew == 42
@Test fun songDelete_cascadesLinks()                          // delete the song row; observeSongCustomTagIds(songId) emits empty
@Test fun songUpsert_keepsLinks()                             // SongDao.upsertSongs on the same id; links still there
@Test fun songsNeedingFileTagRead_newestFirstAndLimited()     // three unread rows with date_modified 1, 3, 2, one read row; limit 2 → ids for 3, 2
@Test fun markFileTagsRead_keepsLinksAndGenre()
```

- [ ] **Step 2: Run to verify they fail**

Run: `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest`
Expected: FAIL (compilation). If the Motorola is not connected, run `./gradlew :core:database:compileDebugAndroidTestKotlin`
for the RED and say the device run is pending.

- [ ] **Step 3: Implement the entities, migration, DAO, provider and the fake**

- [ ] **Step 4: Run to verify they pass**

Run: `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest` (all of the module's instrumented
tests, the existing 104 included) and `./gradlew :core:data:testDebugUnitTest` (callers of `SongEntity` still compile
and pass).
Expected: PASS. Quote the device run's result line, or report the device run as pending with the compile evidence.

- [ ] **Step 5: Stage and report ready to commit** (`feat(database): v6 custom tag tables, genre and the file read flag`)

---

### Task 5: The scan reads tags and genre from files

Owner: media-scanning-agent.

**Files:**
- Modify: `core/data/build.gradle.kts` (`implementation(project(":core:tagio"))`)
- Modify: `core/data/.../scanner/MediaStoreColumns.kt` (`GENRE` in the projection on API 30+ only), `MediaScanner.kt`
  (reads it, null below 30 or blank), `ScannedSong.kt` (field + merge), `LibraryIndexer.kt` (runs the pass)
- Create: `core/data/.../scanner/SongFileSource.kt`, `core/data/.../scanner/FileTagPass.kt`
- Test: `core/data/src/test/.../scanner/FileTagPassTest.kt`, `LibraryIndexerTest.kt` (new tests),
  `ScannedSongMergeTest.kt`, `SongFileSourceTest.kt`

**Interfaces:**
- Consumes: Task 3's `RollaTagsFileIo`, `supportsCustomTags`; Task 4's `CustomTagDao`, `SongEntity` columns.
- `ScannedSong` gains `val genre: String?` (MediaStore's, API 30+). `toEntity(existing: SongEntity?, rereadFileTags: Boolean = false)`:
  `fileTagsRead = !rereadFileTags && existing?.fileTagsRead == true && existing.dateModified == dateModified`;
  `genre = if (that kept the flag) existing.genre else genre`. This stays the single merge point (its KDoc says why);
  add the two columns to that KDoc. `LibraryIndexer.sync` calls it with `false`; `syncSongs` with `true` (the caller
  knows the file changed, same reason as its unconditional upsert, `LibraryIndexer.kt` KDoc of `syncSongs`).
- Produces:
  ```kotlin
  interface SongFileSource { suspend fun <T> withReadableFile(song: SongEntity, block: (File) -> T): T }   // throws when the file cannot be opened
  class AndroidSongFileSource @Inject constructor(@ApplicationContext context: Context) : SongFileSource
  data class FileTagPassResult(val read: Int, val failed: Int, val elapsedMs: Long)
  @Singleton class FileTagPass @Inject constructor(songFileSource: SongFileSource, fileIo: RollaTagsFileIo, customTagDao: CustomTagDao) {
      suspend fun run(): FileTagPassResult
  }
  ```
  - `AndroidSongFileSource`: the extension comes from the row's display name (query `DISPLAY_NAME` on the song's uri).
    On API ≤ 28 and on 30+, when MediaStore's `DATA` path is readable (`File(path).canRead()`), it reads that file in
    place; otherwise (API 29 without legacy storage, or an unreadable path) it copies the uri's bytes into
    `cacheDir/file_tag_reads/<id>.<ext>` and deletes the copy after `block`. The choice is a pure function
    `internal fun chooseReadMode(sdk: Int, dataPath: String?, canRead: Boolean): ReadMode` (`IN_PLACE`, `COPY`).
  - `FileTagPass.run`: its own `Mutex`; loops `songsNeedingFileTagRead(limit = 50)` until empty. Per row: unsupported
    extension → `applyFileTags(id, CustomTagIds.EMPTY, row.genre)`; else read via `withReadableFile` and
    `applyFileTags(id, fileTags.customTagIds, fileTags.genre ?: row.genre)`; any exception (not cancellation) →
    `markFileTagsRead(id)`, `failed++`. Runs on `Dispatchers.IO`. Logs one line,
    `Log.i("FileTagPass", "read $read files ($failed failed) in $elapsedMs ms")`, when `read + failed > 0`.
  - `LibraryIndexer` gains a `FileTagPass` constructor parameter and calls `fileTagPass.run()` **after** releasing its
    mutex, at the end of both `sync()` and `syncSongs()` (non-empty). Its KDoc gains one paragraph: the pass is outside
    the mutex because it never diffs MediaStore and has its own lock, so a long first read never blocks the tag
    editor's post-save `syncSongs` upsert.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ScannedSongMergeTest
@Test fun toEntity_unchangedFile_keepsReadFlagAndGenre()      // existing read=true, genre "Techno", same dateModified, scanned genre "Pop" → read true, "Techno"
@Test fun toEntity_changedFile_resetsReadFlagTakesScannedGenre() // different dateModified → read false, genre "Pop"
@Test fun toEntity_reread_resetsFlagEvenWhenUnchanged()       // rereadFileTags = true, same dateModified → read false

// FileTagPassTest (FakeCustomTagDao from :core:testing; SongFileSource and RollaTagsFileIo as fakes/mockk)
@Test fun pass_readsUnreadRowsAndAppliesIdsAndGenre()         // row mp3 with file ids {TAG 2}, genre "Techno" → applyFileTags(id, ids, "Techno"); result read == 1
@Test fun pass_fileWithoutGenre_keepsMediaStoreGenre()        // file genre null, row genre "Pop" → genre "Pop"
@Test fun pass_unsupportedFormat_appliesEmptyWithoutOpeningFile() // ".wav" row → applyFileTags(id, EMPTY, row.genre); withReadableFile never called
@Test fun pass_undecodableValue_linksNothingAndMarksRead()    // fileIo returns EMPTY (the codec's undecodable path) → links empty, read flag set
@Test fun pass_readThrows_keepsOldLinksMarksReadAndContinues()// first row throws, second reads: first keeps its seeded links and genre, read flag set; second applied; result (read 1, failed 1)
@Test fun pass_loopsBatchesUntilNoneLeft()                    // 120 unread rows → all 120 applied; songsNeedingFileTagRead called 4 times (50, 50, 20, then empty)
@Test fun pass_cancellationPropagates()                       // block throws CancellationException → run throws it; the row is not marked read

// LibraryIndexerTest
@Test fun sync_upsertsSongsBeforeTheFilePassRuns()            // coVerifyOrder { songDao.upsertSongs(any()); fileTagPass.run() }
@Test fun syncSongs_rereadsTheSavedFiles()                    // upserted entity has fileTagsRead == false although dateModified is unchanged
@Test fun syncSongs_empty_doesNotRunThePass()

// SongFileSourceTest (pure function)
@Test fun chooseReadMode_api29AlwaysCopies()                  // (29, "/storage/x.mp3", true) → COPY
@Test fun chooseReadMode_readablePathReadsInPlace()           // (33, path, true) → IN_PLACE; (28, path, true) → IN_PLACE
@Test fun chooseReadMode_noOrUnreadablePathCopies()           // (33, null, false) and (33, path, false) → COPY
```

Existing `LibraryIndexerTest` tests keep passing with a relaxed `FileTagPass` mock added to the constructor.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*FileTagPassTest" --tests "*LibraryIndexerTest" --tests "*ScannedSongMergeTest" --tests "*SongFileSourceTest"`
Expected: FAIL (compilation).

- [ ] **Step 3: Implement the projection, merge, source, pass and the indexer wiring; bind `SongFileSource` in
  `DataModule`**

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS (whole module).

- [ ] **Step 5: Stage and report ready to commit** (`feat(scanner): read ROLLA_TAGS and genre from files during the scan`)

---

### Task 6: `CustomTagRepository`

Owner: data-layer-agent.

**Files:**
- Create: `core/data/.../repository/CustomTagRepository.kt`, `CustomTagRepositoryImpl.kt`
- Modify: `core/data/.../di/DataModule.kt` (bind)
- Test: `core/data/src/test/.../repository/CustomTagRepositoryImplTest.kt`

**Interfaces:**
- Consumes: Task 4's `CustomTagDao` (and `FakeCustomTagDao`), Task 2's model.
- Produces:
  ```kotlin
  sealed interface CustomTagNameResult {
      data class Saved(val tag: CustomTag) : CustomTagNameResult
      data object Blank : CustomTagNameResult
      data object TooLong : CustomTagNameResult
      data object Duplicate : CustomTagNameResult
      data object NotFound : CustomTagNameResult
  }
  interface CustomTagRepository {
      fun observeCustomTags(kind: CustomTagKind): Flow<List<CustomTag>>     // library order (sortedByLibraryName on name)
      suspend fun create(kind: CustomTagKind, name: String): CustomTagNameResult
      suspend fun rename(kind: CustomTagKind, id: Long, name: String): CustomTagNameResult   // renaming to itself in another case is allowed
      fun observeSongCustomTagIds(songId: String): Flow<CustomTagIds>       // includes unknown IDs
  }
  const val CUSTOM_TAG_NAME_MAX_LENGTH = 40                                  // code points
  ```
  The DAO's unique index is the last line of defence: a `SQLiteConstraintException` from a race maps to `Duplicate`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun create_trimsAndIssuesIds()                          // create(TAG, "  Techno ") → Saved(CustomTag(TAG, 1, "Techno")); next → id 2
@Test fun create_blank_isRejected()                           // "   " → Blank; nothing inserted
@Test fun create_duplicateIgnoringCase_isRejected()           // "Techno" then " techno" → Duplicate
@Test fun create_sameNameInOtherList_isAllowed()              // TAG "Hard" and SUBTAG "Hard" both Saved
@Test fun create_fortyEmoji_isSavedAndFortyOne_isTooLong()    // "😀".repeat(40) → Saved; repeat(41) → TooLong
@Test fun create_afterFileCarriedHigherId_issuesAboveIt()     // fake DAO applyFileTags with TAG 40 → create(TAG, "New") id 41
@Test fun rename_changesCaseOfItself()                        // "techno" → "Techno" Saved
@Test fun rename_toAnotherEntrysName_isDuplicate()
@Test fun rename_missing_isNotFound()
@Test fun observeCustomTags_libraryOrder()                    // created "Zebra", "Émile", "apple" → apple, Émile, Zebra
@Test fun observeSongCustomTagIds_splitsByKindAndKeepsUnknownIds() // links TAG 2, TAG 99 (no such entry), SUBTAG 1 → CustomTagIds({2, 99}, {1})
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*CustomTagRepositoryImplTest"`
Expected: FAIL (compilation).

- [ ] **Step 3: Implement the repository and bind it**

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*CustomTagRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 5: Stage and report ready to commit** (`feat(data): custom tag repository`)

---

### Task 7: Whole-phase verification and the device probe

Owner: the controller.

- [ ] **Step 1: Full JVM gate**

Run: `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleRelease checkReleaseManifestNoInternet`
Expected: BUILD SUCCESSFUL. Quote the result line.

- [ ] **Step 2: Instrumented database tests on the Motorola**

Run: `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest`
Expected: all pass (the existing 104 plus Task 4's).

- [ ] **Step 3: Device probe (install allowed; the user launches the app; the agent only reads logs)**

Gate (final review M-d, SDD Ruling 11): run this step only after Step 2 passed, including `migrate5To6`. A Room
validation mismatch would crash every launch on the user's real database. During the first pass also ask the user to
scroll Home's Tracks list, and record CPU (`adb shell top -b -n 1 | grep rolla`) and whether scrolling stutters,
alongside N, F and T; check `logcat` for jaudiotagger noise.

1. `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark` (upgrades in place: the v5 → v6 migration runs on the
   user's real database).
2. Ask the user to open the app once and wait on Home for a minute.
3. Read-only: `adb -s ZY22MBNVB4 logcat -d -s FileTagPass` (adb at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`,
   `MSYS_NO_PATHCONV=1`). Expected: one line `read N files (F failed) in T ms`, with N close to the device's
   MP3/FLAC/M4A count (`content query --uri content://media/external/audio/media --projection mime_type --where
   'is_music!=0'`, counted by MIME type). Record N, F and T in the [[Custom tags]] note; F above 1 % of N is a finding.
4. Ask the user to confirm the library and playback look as before.

- [ ] **Step 4: Vault and docs** (the controller writes them; [[Record knowledge in the vault]]):
  [[Custom tags]] (progress, the probe numbers), a new module note `core-tagio` plus [[Modules]],
  [[core-database]] (v6), [[core-data]] (the pass, the repository), [[feature-tageditor]] (helpers moved),
  [[Ownership map]] and `CLAUDE.md`'s module tree (`:core:tagio` → tag-editor-agent), [[Timeline]], [[Backlog]].
  Then report ready to commit.

---

## Self-review artefacts

### Test-control table

Every row: (a) the trivial stub, (b) the assertion that fails under it, (c) a named mutation, (d) why the mutated value
differs (evaluated on the test's literal input).

| Test | (a) stub | (b) fails because | (c) mutation | (d) mutated value |
|---|---|---|---|---|
| encode_sortsAscendingWithoutSpaces | `""` | `assertEquals(expected, …)` | drop `sorted()` | sets `{3,2}` iterate 3,2 → `"3,2"` ≠ `"2,3"` |
| encode_emptyListsAreEmptyStrings | `""` | equals | omit empty keys | `{"subTags":"5"}` ≠ expected |
| decode_userFormat_roundTrips | `EMPTY` | equals `{2,3}` | don't trim entries | `" 3".toLongOrNull()` is null → `{2}` ≠ `{2,3}` |
| decode_notJson_returnsNull | `EMPTY` | `assertNull` | a non-object returns EMPTY (amended in Task 2: kotlinx parses bare `Techno` as a primitive without throwing, so "EMPTY on parse error" cannot fire here) | EMPTY ≠ null |
| decode_malformedJson_returnsNull (added in Task 2) | `EMPTY` | `assertNull` on `{"tags":` | return EMPTY on parse error | EMPTY ≠ null |
| decode_jsonArray_returnsNull | `EMPTY` | `assertNull` | treat any element as object | `[1,2]` cast fails or EMPTY ≠ null |
| decode_badEntries_keepsOnlyPositiveIds | `EMPTY` | equals `{2,7}` | keep `toLongOrNull()` without `> 0` | `{2,-1,0,7}` ≠ `{2,7}` |
| decode_missingKey_readsAsEmpty | `null` | equals | return null when a key is missing | null ≠ `({}, {9})` |
| customTagIds_isEmpty | `true` | third `assertFalse` | `isEmpty = tagIds.isEmpty()` only | `({}, {1})` → `true` ≠ false |
| mp3_* / flac_* / m4a_* round trips | `read` returns EMPTY | equals `ids` | write to COMMENT instead of TXXX/Vorbis/atom | `mp3_writesTxxxWithExactValue` finds no TXXX; `mp3_keepsComment…` sees "my notes" overwritten |
| mp3_id3v1Only_roundTripsAndKeepsV1Text | ids ok, v1 text lost | title == v1 title | use `getTagOrCreateDefault()` instead of `editableTag()` | empty ID3v2 hides v1 text → title "" (knowledge note [[jaudiotagger and ID3v1-only MP3s]]) |
| mp3_secondWriteReplacesNotDuplicates | adds a frame | exactly one frame | `tag.addField` instead of replace | two `ROLLA_TAGS` frames → count 2 |
| *_writeEmptyRemovesField | writes `{"tags":"","subTags":""}` | no field present | always `setField` | field present with empty-list JSON |
| read_undecodableValue_returnsEmpty | returns null/throws | equals EMPTY and bytes equal | `read` commits a normalised value | file bytes differ |
| read_genre_fromV2AndFromV1Only | null | equals "Rock" | read through `getTagOrCreateAndSetDefault()` instead of `editableTag()` (amended in Task 3: the planned "`audioFile.tag`" mutation is equivalent, since `ID3v1Tag.getFirst(GENRE)` already maps 17 to "Rock") | new empty v2 tag → null ≠ "Rock" |
| read_blankGenre_isNull | `""` | `assertNull` | return raw value | `""` ≠ null |
| supportsCustomTags_byExtension | `true` | `assertFalse(wav)` | compare case-sensitively | `"MP3"` → false ≠ true |
| migrate5To6_keepsSongRowAndAddsTablesAndColumns | migration that only adds tables | Room validation: missing columns → `IllegalStateException` | drop the `ALTER TABLE songs ADD COLUMN file_tags_read` | validation fails |
| insertNew_issuesOneThenTwoPerKind | always 1 | second insert `== 2` | one shared counter for both kinds | SUBTAG first id 3 ≠ 1 |
| insertNew_afterDeleteOfTopRow_neverReusesId | `MAX(id)+1` | `== 3` | compute from `MAX(id)` | 2 ≠ 3 |
| insertNew_duplicateNameKey_throws | no unique index | `assertThrows` | index without `unique = true` | no throw |
| applyFileTags_replacesLinksAndSetsGenreAndReadFlag | inserts only | only TAG 3 remains | skip the delete | {2, 3} ≠ {3} |
| applyFileTags_raisesCounterToHighestFileId | no raise | `== 41` | skip the raise | next id 1 ≠ 41 |
| applyFileTags_lowerIdsNeverLowerCounter | set counter = max file id | `== 42` | `highest_id = :candidate` | 4 ≠ 42 |
| songDelete_cascadesLinks | no FK | emits empty | FK without `ON DELETE CASCADE` | delete fails (FK) or links remain |
| songUpsert_keepsLinks | — (control) | links non-empty after upsert | `@Insert(REPLACE)` instead of `@Upsert` | REPLACE deletes the row → cascade empties links |
| songsNeedingFileTagRead_newestFirstAndLimited | all rows | equals ids for 3, 2 | `ORDER BY date_modified ASC` | 1, 2 ≠ 3, 2 |
| markFileTagsRead_keepsLinksAndGenre | `applyFileTags(EMPTY)` | links and genre still present | implement as applyFileTags(EMPTY, null) | links empty, genre null |
| toEntity_unchangedFile_keepsReadFlagAndGenre | flag false | `assertTrue(read)`, genre "Techno" | always take scanned genre | "Pop" ≠ "Techno" |
| toEntity_changedFile_resetsReadFlagTakesScannedGenre | flag from existing | `assertFalse` | ignore dateModified | true ≠ false |
| toEntity_reread_resetsFlagEvenWhenUnchanged | ignore param | `assertFalse` | drop `!rereadFileTags` | true ≠ false |
| pass_readsUnreadRowsAndAppliesIdsAndGenre | no-op | applied ids `{TAG 2}` | skip apply | no links |
| pass_fileWithoutGenre_keepsMediaStoreGenre | pass file genre through | "Pop" | `fileTags.genre` without `?: row.genre` | null ≠ "Pop" |
| pass_unsupportedFormat_appliesEmptyWithoutOpeningFile | open every file | `verify(exactly = 0)` | drop the extension check | source called once |
| pass_undecodableValue_linksNothingAndMarksRead | leave unread | read flag true | skip apply when ids are EMPTY | the row stays unread; the fake returns it at most 3 times, then the flag is still false |
| pass_readThrows_keepsOldLinksMarksReadAndContinues | rethrow | second row applied, result (1, 1) | let the exception escape | run throws → assertion fails |
| pass_loopsBatchesUntilNoneLeft | one batch | 120 applied | single query | 50 ≠ 120 |
| pass_cancellationPropagates | swallow all | `assertThrows<CancellationException>` | `catch (e: Exception)` without rethrow | no throw |
| sync_upsertsSongsBeforeTheFilePassRuns | run pass first | `coVerifyOrder` | call `fileTagPass.run()` before upsert | order violated |
| syncSongs_rereadsTheSavedFiles | `rereadFileTags = false` | `assertFalse(fileTagsRead)` | pass `false` | existing flag true kept |
| syncSongs_empty_doesNotRunThePass | always run | `coVerify(exactly = 0)` | run before the empty check | 1 call |
| chooseReadMode_* | always COPY | `IN_PLACE` expected | ignore `canRead` | (33, path, false) → IN_PLACE ≠ COPY |
| create_trimsAndIssuesIds | Saved untrimmed | name "Techno" | skip `trim()` | "  Techno " ≠ "Techno" |
| create_blank_isRejected | Saved("") | Blank | check `isEmpty()` before trim | "   " not empty → Saved |
| create_duplicateIgnoringCase_isRejected | Saved | Duplicate | key = name (no lowercase) | "techno" ≠ "Techno" → Saved |
| create_sameNameInOtherList_isAllowed | Duplicate | both Saved | look up key across kinds | SUBTAG "Hard" → Duplicate |
| create_fortyEmoji_* | `length` | 40 emoji Saved | use `String.length` (UTF-16 units) | 80 > 40 → TooLong |
| create_afterFileCarriedHigherId_issuesAboveIt | id 1 | `== 41` | repository computes id itself from list size | 1 ≠ 41 |
| rename_changesCaseOfItself | Duplicate | Saved | duplicate check without excluding itself | "techno" key matches itself → Duplicate |
| rename_toAnotherEntrysName_isDuplicate | Saved | Duplicate | skip the check **and** map the unique-index exception to Saved (amended in Task 6: skipping the check alone is equivalent, since the index exception also maps to Duplicate) | Saved |
| create_lostRaceToUniqueIndex_isDuplicate (added in Task 6) | Saved | Duplicate | map `SQLiteConstraintException` to Saved | Saved |
| rename_lostRaceToUniqueIndex_isDuplicate (added in Task 6) | Saved | Duplicate | same | Saved |
| rename_missing_isNotFound | Saved | NotFound | ignore rename's row count 0 | Saved |
| observeCustomTags_libraryOrder | DAO order | apple, Émile, Zebra | `sortedBy { name }` | "Zebra", "apple", "Émile" |
| observeSongCustomTagIds_splitsByKindAndKeepsUnknownIds | join on `custom_tags` | `{2, 99}` | inner join to the list | 99 dropped |

### Per-screen concerns

This phase touches no screen; insets, focus, TalkBack and motion tables start in phase 2.

### Earlier rulings checked (grep of `docs/history/ledgers/` and the files' KDoc, 2026-11-09)

- `ScannedSong.toEntity` KDoc: the single merge point for both sync paths. Honoured: the new columns merge there.
- `LibraryIndexer` KDoc: one mutex serialises both entry points. Honoured; the pass runs outside it with its own lock,
  and the KDoc says why (Task 5).
- `syncSongs` KDoc: unconditional upsert because `DATE_MODIFIED` has second granularity. Honoured and extended: it also
  forces the re-read.
- Phase 4b ruling 3 (root songs store `""` and are not rewritten every sync): merge condition unchanged.
- Per-track artwork R1 (`artwork_uri` derived at read time) and P3 (no unit-test set in `:core:database`): honoured.
- Per-track artwork P16–P19 (`editableTag`, FLAC Vorbis pictures): the helpers move verbatim with their comments.
- Redesign ruling 1 (lists sorted in library order): applied to both custom tag lists.

### Claims about existing code (cited)

- `LibraryIndexer.sync` upserts the diff then deletes (`LibraryIndexer.kt` 49–73); `syncSongs` upserts unconditionally
  (98+).
- `SongDao.upsertSongs` is `@Upsert` (`SongDao.kt` 19–20), so a re-scan updates rows in place and does not cascade.
- Room is at version 5 (`MusicDatabase.kt` 31); `ALL_MIGRATIONS` lists four migrations (`Migrations.kt`).
- No genre in `Song`/`SongEntity`: `grep -rn -i genre core/model core/database core/data` (main) hit nothing
  (2026-11-09).
- Only `:feature:tageditor` uses jaudiotagger in production (`grep -rln "AudioFileIO\|jaudiotagger" --include=*.kt`
  over `src/main`: the tageditor `io/` files and the settings licence list).
