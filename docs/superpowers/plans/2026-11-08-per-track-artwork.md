# Per-track Artwork Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every track shows the picture stored in its own audio file (or the white-note placeholder), everywhere;
albums, artists and folders always show the placeholder; the tag editor and the Tracks tab's selection bar can set and
remove a file's picture, and every change shows at once.

**Architecture:** `Song.artworkUri` becomes an app-owned, per-track, versioned value
(`rolla-artwork://song/{songId}?v={dateModified}`) derived from the Room row at read time. One per-track disk cache in
`:core:data` decodes the file's embedded picture with `MediaMetadataRetriever` and remembers "no picture" too. Coil (an
interceptor), the widget loader and a Media3 `BitmapLoader` all resolve that value through the cache and nothing else, so
no screen can ever show MediaStore's per-album cover. The version in the value changes when the file changes, so every
consumer reloads on its own after a save.

**Tech Stack:** Kotlin 2.2, Compose, Media3 1.11.1, Room (no schema change), Hilt 2.57.2, Coil 2.7.0, jaudiotagger
(Adonai fork), JUnit4/MockK/Turbine/Robolectric.

**Spec:** `docs/superpowers/specs/2026-11-08-per-track-artwork-design.md` (approved by the user 2026-11-08). Where this
plan and the spec disagree, the spec wins; record a ruling.

## Global Constraints

- Offline only: no network, no new permission, no new dependency. `checkReleaseManifestNoInternet` stays green.
- A track never shows a picture that is not in its own file (spec §4 anti-goal). No code path may read
  `content://media/external/audio/albumart/…` or `…/audio/media/{id}/albumart` after Task 2.
- No picture → the existing placeholder (`ArtworkPlaceholder` in-app; the widget's own placeholder; Task 4's bitmap in
  the notification).
- Albums, artists and folders always show the placeholder (spec goal 3).
- Kit only: `OneUiPillButton`, `OneUiIconButton`, `OneUiDropdownMenu`/`OneUiMenuItem`, `OneUiDialog` +
  `OneUiTextButton(destructive = true)`; `RollaIcons` only; no hardcoded colour, type or dp in a screen
  (`.claude/rules/ui-style-guide.md`). Destructive text is `destructiveText`.
- Copy, verbatim (spec §5.7): "Change artwork", "Remove artwork", "Set artwork", "Remove artwork from N tracks?",
  "Remove", "Cancel", "Artwork set on N tracks", "Artwork set on K of N tracks", "Artwork removed from N tracks",
  "Artwork removed from K of N tracks". N and the K-of-N denominator use `tracksCountLabel` (so 1 reads "1 track").
  Reused copy: "Changes weren't saved — permission declined.", "Couldn't use that image as artwork."
- Artwork writes only for MP3, FLAC and M4A (spec goal 9), decided by the editable copy's file extension
  (`mp3`, `flac`, `m4a`, case-insensitive).
- Model vocabulary: `Song` in code, "Tracks" in UI copy (`.claude/rules/model-vocabulary.md`).
- No `sed -i` on sources; no Kotlin, Gradle or XML through shell heredocs. Commits only when the user asks: each task's
  commit step means "stage the files and report ready to commit". Implementers may make mechanical fixes to the plan's
  code; it was never compiled.
- Device: the agent never saves tags on the Motorola (ZY22MBNVB4). Installing there is
  `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark`; the Task 9 walk is done by the user's hand.

## Review Focus

1. **A file changed by another app, then a full sync.** `LibraryIndexer.sync` rewrites the row's `dateModified`, so the
   value's `v=` changes and the new picture shows without a manual rescan. Pinned by Task 1's
   `toDomain_versionFollowsDateModified` and Task 2's `get_newVersion_dropsTheOldFileAndDecodesAgain`.
2. **Two saves to one file inside one second** leave `dateModified` (seconds) unchanged, so the value does not change.
   The finalizer's explicit invalidate (Task 6) still makes the next load decode afresh; on-screen images already
   composed keep the old picture until they recompose. Accepted limit, recorded as ruling R6; no test can make the
   platform's clock coarser, so it is a ruling, not a test.
3. **A file whose picture cannot be decoded** (corrupt APIC, an image `BitmapFactory` rejects). The decoder throws,
   the cache stores nothing (no negative entry), the placeholder shows, and a later load tries again. Pinned by Task 2's
   `get_decoderThrows_returnsNullAndCachesNothing`.
4. **A multi-track Set where some files are WAV/OGG.** They count as not written ("Artwork set on 2 of 3 tracks") and
   stay selected. Pinned by Task 8's `setArtwork_unsupportedFormat_countsAsFailedAndStaysSelected`.
5. **The playing track's picture changes while Now Playing, the mini-player and the notification are visible.** The
   re-synced `Song` carries a new value; the visible images change. Pinned by Task 4's
   `updateSongMetadata_newVersion_loadsTheNewBitmap` and Task 5's pixel test `songRow_newVersion_drawsTheNewPicture`.

## Earlier rulings this plan touches (writing-plans companion check (g))

Grep: `grep -n -i "ruling" docs/superpowers/plans/*.md .superpowers/sdd/*/progress.md | grep -i -E "artwork|trackNumber|track number|AlbumCard|FeatureCard|selection"`.

| Earlier ruling | This plan |
|---|---|
| Phase 6a ruling 9: a blank `artworkUri` is missing art, no doomed load (`ArtworkThumbnail`, `AlbumCard`) | Honoured: blank still means placeholder |
| Phase 6a ruling 5 / spec §8.7: album detail's sort label "Track number", rows numbered | **Overruled** for the leading slot only (spec goal 2a, user 2026-11-08): rows show the picture, sort label and order unchanged |
| Phase 4a ruling 12 / model-vocabulary: `Artist.artworkUri` is the first album's cover | **Overruled** (spec §5.2): the field goes |
| `PlaylistsViewModel.summary` comment: art is per album, so skip blanks and duplicates | **Overruled** (spec goal 8): first four tracks, unfiltered |
| `AlbumArtworkCacheImpl` KDoc: never touch `lastModified` on a hit; publish by rename; mutex on writes | Honoured in `SongArtworkCacheImpl` |
| v1.0 follow-ups #1 (invalidate and refresh share a `try`) and #3 (`invalidations` has no subscriber) | #1 fixed in Task 6; #3 resolved by removing `invalidations` (the versioned value replaces it), Task 2 |

## Inventory (writing-plans companion check (f))

Every user of the album cache, which Tasks 2-6 replace. Acceptance grep after Task 6 (must print nothing):
`grep -rn -E "AlbumArtworkCache|albumArtworkCache|ArtworkDecoder\b|BitmapArtworkDecoder|AlbumArtworkInterceptor|ALBUM_ART_URI" --include=*.kt app core feature | grep -v /build/`

Hit list at 15821b4 (Kotlin): `RollaApp.kt`, `artwork/AlbumArtworkInterceptor.kt` (+Test), `core/data/artwork/*`
(5 files + `AlbumArtworkCacheImplTest`), `CoreDataIoDispatcher.kt` (KDoc), `DataModule.kt`, `MiniPlayer.kt` (KDoc),
`SettingsViewModel.kt` (+Test), `TagSaveFinalizer.kt` (+Test), `WidgetArtworkLoader.kt` (+Test),
`MediaScanner.kt`/`MediaStoreColumns.kt` (`ALBUM_ART_URI`). Docs: `CLAUDE.md`, `.claude/agents/widget-agent.md`,
`.claude/skills/implement-home-widget/SKILL.md` (Task 9).

Group artwork, which Task 5 removes. Acceptance grep after Task 5 (must print nothing):
`grep -rn -E "(album|artist|folder|Album|Artist|Folder)\.artworkUri|artworkUri = (album|artist|folder)" --include=*.kt app core feature | grep -v /build/`

## Once-stated concerns per screen (companion check (h)): insets

| Screen touched | Status bar | Navigation bar | Keyboard | Mini-player pill |
|---|---|---|---|---|
| Home (selection bar, new snackbar) | `OneUiDetailTopBar` (selection bar), as today | `HomeBelowHeader` | none (no text field) | lists pad by `homeListBottomPadding()`; the new `SnackbarHost` sits inside `HomeBelowHeader` with bottom padding `homeListBottomPadding()`, so it clears the bar and the pill |
| Tag editor | Scaffold top bar, as today | Scaffold inner padding | `TagEditorScrollColumn`, as today | hidden on this screen |
| Album detail | unchanged | unchanged | none | unchanged |

---

### Task 1: The per-track artwork value

**Files:**
- Create: `core/model/src/main/kotlin/com/rolla/musicplayer/core/model/SongArtwork.kt`
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/SongEntity.kt:77-88` (`toDomain`)
- Modify: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/scanner/MediaScanner.kt:65`, `MediaStoreColumns.kt:58-59` (delete `ALBUM_ART_URI`)
- Test: `core/model/src/test/kotlin/com/rolla/musicplayer/core/model/SongArtworkTest.kt`, the existing `SongEntity` mapping test (find it with `grep -rln "toDomain" core/database/src/test`), `MediaScannerTest`

**Interfaces:**
- Produces:
  - `data class SongArtworkRef(val songId: Long, val version: Long)`
  - `fun songArtworkUri(songId: String, version: Long): String` → `"rolla-artwork://song/$songId?v=$version"`
  - `fun parseSongArtworkUri(uri: String?): SongArtworkRef?` (null for anything else, including blank and MediaStore uris)
  - `SongEntity.toDomain()` sets `artworkUri = songArtworkUri(id, dateModified)` and ignores the `artwork_uri` column
    (spec §5.4, read-time derivation: no Room migration; ruling R1).

- [ ] **Step 1: Write the failing tests**

```kotlin
// SongArtworkTest
@Test fun roundTrip() = assertEquals(SongArtworkRef(9545, 1794133952), parseSongArtworkUri(songArtworkUri("9545", 1794133952)))
@Test fun value_isPerTrack() = assertNotEquals(songArtworkUri("9540", 5), songArtworkUri("9545", 5))
@Test fun rejectsMediaStoreAlbumArt() = assertNull(parseSongArtworkUri("content://media/external/audio/albumart/1540603544537963427"))
@Test fun rejectsMediaStorePerFileArt() = assertNull(parseSongArtworkUri("content://media/external/audio/media/9540/albumart"))
@Test fun rejectsBlankAndMalformed() { assertNull(parseSongArtworkUri("")); assertNull(parseSongArtworkUri(null))
    assertNull(parseSongArtworkUri("rolla-artwork://song/x?v=1")); assertNull(parseSongArtworkUri("rolla-artwork://song/1")) }
// SongEntity mapping test
@Test fun toDomain_ignoresStoredAlbumArtUri() {
    val song = entity(id = "9545", dateModified = 7, artworkUri = "content://media/external/audio/albumart/1").toDomain()
    assertEquals("rolla-artwork://song/9545?v=7", song.artworkUri) }
@Test fun toDomain_versionFollowsDateModified() =
    assertNotEquals(entity(id = "1", dateModified = 7).toDomain().artworkUri, entity(id = "1", dateModified = 8).toDomain().artworkUri)
```

`MediaScannerTest`: the existing assertion on `artworkUri` changes to `songArtworkUri(id.toString(), dateModified)`.

- [ ] **Step 2: Run, expect FAIL** — `./gradlew :core:model:test :core:database:testDebugUnitTest :core:data:testDebugUnitTest`; Expected: unresolved reference `songArtworkUri`.
- [ ] **Step 3: Implement** the three functions (plain string parsing, no `android.net.Uri`: `:core:model` is pure
  Kotlin), `toDomain`, and the scanner writing `songArtworkUri(id.toString(), dateModified)` into the column so new rows
  agree with read-time derivation.
- [ ] **Step 4: Run, expect PASS** (same command). Then the consumer audit (companion check (b)): `grep -rn "artworkUri" --include=*.kt core/media core/data | grep -v /build/` and confirm `MediaItems.toMediaItem`/`toPlaybackSong` pass the string through unchanged (`MediaItemsTest` green).
- [ ] **Step 5: Commit** — `feat(model): per-track, versioned artwork value`.

### Task 2: The per-track artwork cache

**Files:**
- Create in `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/artwork/`: `SongArtworkCache.kt`,
  `SongArtworkCacheImpl.kt`, `SongArtworkCacheDir.kt` (qualifier), `EmbeddedArtworkDecoder.kt` (interface +
  `DecodedArtwork`), `RetrieverArtworkDecoder.kt`
- Delete: `AlbumArtworkCache.kt`, `AlbumArtworkCacheImpl.kt`, `AlbumArtworkCacheDir.kt`, `ArtworkDecoder.kt`,
  `BitmapArtworkDecoder.kt`, `AlbumArtworkCacheImplTest.kt` (keep `calculateInSampleSize`, moved into
  `RetrieverArtworkDecoder.kt`, with its existing test cases)
- Modify: `core/data/.../di/DataModule.kt:70-88`, `CoreDataIoDispatcher.kt` (KDoc),
  `feature/settings/.../SettingsViewModel.kt:110-155` (+ its test: `SongArtworkCache.clear()`)
- Test: `core/data/src/test/kotlin/com/rolla/musicplayer/core/data/artwork/SongArtworkCacheImplTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface DecodedArtwork { class Picture(val jpeg: ByteArray) : DecodedArtwork; data object None : DecodedArtwork }
  fun interface EmbeddedArtworkDecoder { suspend fun decode(songId: Long): DecodedArtwork } // throws on any read/decode failure
  interface SongArtworkCache {
      suspend fun get(ref: SongArtworkRef): File?   // null = no picture, or the read failed
      fun peek(ref: SongArtworkRef): File?          // one stat, never decodes, never suspends
      suspend fun invalidate(songId: Long)          // drops every version and the negative marker
      suspend fun clear()
  }
  ```
- Files: `{songId}-{version}.jpg` (picture) and `{songId}-{version}.none` (zero-byte negative marker). Budget 32 MiB,
  write-age eviction, publish by temp-file rename, writes under one `Mutex`, hits never touch `lastModified` — all
  carried over from `AlbumArtworkCacheImpl` (ruling R2: the budget stays 32 MiB; at about 40 KB per 512 px JPEG that
  is roughly 800 tracks before eviction, and an evicted picture simply decodes again).
- `RetrieverArtworkDecoder`: `MediaMetadataRetriever().setDataSource(context, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId))`;
  `embeddedPicture == null` → `None`; otherwise a two-pass sampled `BitmapFactory` decode to a 512 px longer edge,
  JPEG quality 85 (the old decoder's values); a null bitmap from non-null bytes throws (Review Focus 3). Release the
  retriever in `finally`.
- DataModule binds the impl and decoder; `@SongArtworkCacheDir` = `File(context.cacheDir, "song_art")`. The old
  `album_art` directory is left for the OS to reclaim (ruling R3: deleting it would be main-thread IO at injection).

- [ ] **Step 1: Write the failing tests** (`SongArtworkCacheImplTest`, temp dir + a fake decoder that counts calls)

```kotlin
@Test fun get_picture_writesAndReturnsTheFile()            // decode Picture(A) → file bytes == A, decodeCalls == 1
@Test fun get_hit_doesNotDecodeAgainNorTouchLastModified() // second get → same file, decodeCalls == 1, lastModified unchanged
@Test fun get_none_returnsNullAndRemembersIt()             // decode None → null; second get → null with decodeCalls == 1
@Test fun get_decoderThrows_returnsNullAndCachesNothing()  // first get throws-decoder → null; no file in dir; second get decodes again (decodeCalls == 2)
@Test fun get_newVersion_dropsTheOldFileAndDecodesAgain()  // get(ref v1) then get(ref v2) → decodeCalls == 2, only "{id}-2.jpg" remains
@Test fun twoSongs_neverShareAFile()                       // get(1,v) and get(2,v) with different bytes → different files, each its own bytes
@Test fun peek_onlyAfterAPublishedPicture()                // peek before → null; after get Picture → file; after get None → null
@Test fun invalidate_dropsPictureAndNegativeMarker()       // after invalidate(id) the next get decodes again (both cases)
@Test fun clear_wipesEverything()
@Test fun eviction_keepsTheJustWrittenFileAndStaysUnderBudget() // ported from the album test, MAX_CACHE_BYTES as before
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew :core:data:testDebugUnitTest --tests "*SongArtworkCacheImplTest"`; Expected: unresolved `SongArtworkCacheImpl`.
- [ ] **Step 3: Implement** the files above; delete the album cache files; rewire DataModule and `SettingsViewModel`.
- [ ] **Step 4: Run, expect PASS** — `./gradlew :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest`.
- [ ] **Step 5: Commit** — `feat(data): per-track embedded-artwork cache`.

### Task 3: Coil and the widget read through the cache

**Files:**
- Create: `app/src/main/kotlin/com/rolla/musicplayer/artwork/SongArtworkInterceptor.kt` (+ `SongArtworkInterceptorTest.kt`)
- Delete: `AlbumArtworkInterceptor.kt` and its test
- Modify: `app/.../RollaApp.kt:16-50`, `feature/widget/.../WidgetArtworkLoader.kt` (+ `WidgetArtworkLoaderTest.kt`)

**Interfaces:**
- Consumes: Task 1 `parseSongArtworkUri`, Task 2 `SongArtworkCache`.
- `SongArtworkInterceptor(cache)`: `data` parses to a ref → `cache.peek(ref) ?: cache.get(ref)`; a file → proceed with
  `data(file)`; null → return `ErrorResult(null, request, NoArtworkException)` without proceeding (the placeholder
  under `AsyncImage` shows). Data that does not parse → proceed unchanged (non-artwork images).
- `WidgetArtworkLoader.load(artworkPath)`: parses to a ref → `cache.get(ref)` → the existing memoized file decode;
  anything else → `null` with no content-resolver read (the `DirectUri` branch and `decodeContentUri` go).

- [ ] **Step 1: Failing tests**
  - Interceptor: `hit_peek_rewritesDataToTheFile` (fake cache peek → file; assert proceeded request `data == file`);
    `miss_get_rewritesDataToTheFile` (peek null, get → file); `noPicture_returnsErrorWithoutProceeding` (get null;
    assert the chain's `proceed` was never called — control: the hit test proves `proceed` is observable);
    `mediaStoreAlbumUri_isNotIntercepted` (data `content://media/external/audio/albumart/1` → proceeds unchanged,
    cache untouched).
  - Widget: `songRef_decodesTheCacheFile` (fake cache returns a file; decode lambda called once);
    `nonRefPath_returnsNullWithoutReadingAnything` (`content://media/external/audio/albumart/1` → null; mocked
    `ContentResolver.openInputStream` never called; control: `songRef_…` proves the cache path is reachable).
- [ ] **Step 2: Run, expect FAIL** — `./gradlew :app:testDebugUnitTest --tests "*SongArtworkInterceptorTest" :feature:widget:testDebugUnitTest`.
- [ ] **Step 3: Implement** both.
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** — `feat(artwork): Coil and widget load per-track artwork`.

### Task 4: Notification, lock screen and media card

**Files:**
- Create: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/SongArtworkBitmapLoader.kt`,
  `core/media/src/main/res/drawable/artwork_placeholder_note.xml` (copy of `feature/widget/src/main/res/drawable/widget_ic_music_note.xml`'s path, tinted `#FCFCFE`)
- Modify: `core/media/.../PlaybackService.kt:225` (`.setBitmapLoader(...)`; inject `SongArtworkCache`)
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/SongArtworkBitmapLoaderTest.kt`, and a case in
  `PlaybackResumptionThroughSessionTest` (or a sibling using its setup) through the real session

**Interfaces:**
- `class SongArtworkBitmapLoader(context: Context, cache: SongArtworkCache, scope: CoroutineScope, fallback: BitmapLoader = DataSourceBitmapLoader(context)) : BitmapLoader`
  - `loadBitmap(uri)`: parses to a ref → future of `cache.get(ref)?.let(BitmapFactory::decodeFile) ?: placeholderBitmap()`;
    otherwise `fallback.loadBitmap(uri)`. `decodeBitmap`/`supportsMimeType` delegate to `fallback`.
  - `placeholderBitmap()`: 512×512, background `0xFF3B3B3B` (`artworkPlaceholderLarge`, dark), the note drawable centred
    at the fraction `npArtGlyph / npArtSize` = 72 dp / 168 dp = 0.4286 of the side (219 px of 512; `Dimens.kt:127,299`),
    in `0xFFFCFCFE` (`artworkPlaceholderGlyph`); glyph on
    background computes to **10.93:1** (script output, ruling R4). Built once and reused.

- [ ] **Step 1: Failing tests** (Robolectric)
  - `songRef_withPicture_returnsTheDecodedFile` (fake cache → a 4×4 red PNG file; bitmap pixel (2,2) is red).
  - `songRef_noPicture_returnsThePlaceholder` (cache → null; bitmap is 512×512, pixel (2,2) == `0xFF3B3B3B`, centre
    pixel == `0xFFFCFCFE`; control: the first test shows a non-placeholder result is possible).
  - `otherUri_delegatesToFallback` (fake fallback records the uri).
  - Through the session: `session.bitmapLoader.loadBitmap(songArtworkUri("1", 1).toUri())` returns the placeholder
    with a cache that returns null (proves `PlaybackService`'s builder installs the loader; framework check, companion §3).
  - `updateSongMetadata_newVersion_loadsTheNewBitmap`: loading `v=1` then `v=2` for one song with a cache whose file
    changes between calls returns two different pixels (Media3's `CacheBitmapLoader` keys on the uri, so a changed
    `v` must bypass it).
- [ ] **Step 2: Run, expect FAIL** — `./gradlew :core:media:testDebugUnitTest --tests "*SongArtworkBitmapLoaderTest"`.
- [ ] **Step 3: Implement**; the loader's scope is the service's `serviceScope`; the decode runs on `Dispatchers.IO`.
- [ ] **Step 4: Run, expect PASS** — `./gradlew :core:media:testDebugUnitTest`.
- [ ] **Step 5: Commit** — `feat(media): notification artwork from the track's own file`.

### Task 5: Groups show the placeholder; album rows, playlist cards

**Files:**
- Modify `:core:model`: `Album.kt`, `Artist.kt`, `Folder.kt` (remove `artworkUri`)
- Modify `:core:database`: `AlbumDao.kt`, `ArtistDao.kt`, `FolderDao.kt`, `SearchDao.kt` (drop the `artwork_uri`
  projections), `relation/AlbumSearchRow.kt`, `ArtistSearchRow.kt`, `FolderSummary.kt` (+ any album/artist row class the
  DAOs map from; follow the compiler)
- Modify `:core:ui`: `AlbumCard.kt`, `AlbumRow.kt`, `ArtistRow.kt`, `FolderRow.kt`, `DetailArtworkHeader.kt` (drop the
  `artworkUri` parameter: always the placeholder)
- Modify `:feature:library`: `AlbumDetailScreen.kt:109-145` (header without art; rows without `trackNumber`),
  `ArtistDetailScreen.kt:130`; `:feature:search` `SearchScreen.kt`; `:feature:playlists` `PlaylistsViewModel.kt:84-96`
- Test: DAO tests and every test constructing `Album(`, `Artist(`, `Folder(` with `artworkUri` (the compiler lists them);
  new `core/ui/src/test/kotlin/com/rolla/musicplayer/core/ui/TrackArtworkPixelTest.kt`; `PlaylistsViewModelTest`;
  `AlbumDetailScreenTest`

**Interfaces:**
- `PlaylistsViewModel.summary`: `previewArtworkUris = songs.take(PREVIEW_ARTWORK_LIMIT).map(Song::artworkUri)` (no
  filter, no `distinct`); `FeatureCard` already draws a placeholder tile for a blank uri and a failed load.
- `SongListItem`'s `trackNumber` parameter stays (other callers); `AlbumDetailScreen` stops passing it.

- [ ] **Step 1: Failing tests**
  - `TrackArtworkPixelTest` (Robolectric, the `MiniPlayerArtPixelTest` technique: `Coil.setImageLoader` with an
    interceptor that returns a solid colour per `data`): two `SongListItem`s of one album (same `albumId`, ids 9540 and
    9545) draw **different** colours at their thumbnail centres; `songRow_newVersion_drawsTheNewPicture` (same id,
    `v=1` red then `v=2` blue after recomposition → centre turns blue); an `AlbumCard` draws the placeholder colour
    (`artworkPlaceholder`) at its art centre and the fake interceptor records **no** request (absence test; its
    presence control is the song rows' recorded requests in the same test class).
  - `PlaylistsViewModelTest.summary_takesTheFirstFourTracksUnfiltered`: songs with art `a, "", a, b, c` → preview
    `[a, "", a, b]` (control: the old code yields `[a, b, c]`).
  - `AlbumDetailScreenTest.rows_showArtworkNotTrackNumbers`: no node with text "1"; each row has the thumbnail tag.
- [ ] **Step 2: Run, expect FAIL** — `./gradlew :core:ui:testDebugUnitTest --tests "*TrackArtworkPixelTest" :feature:playlists:testDebugUnitTest :feature:library:testDebugUnitTest`.
- [ ] **Step 3: Implement**; then run the Task 5 acceptance grep (Inventory) — it must print nothing.
- [ ] **Step 4: Run, expect PASS** — `./gradlew testDebugUnitTest :core:database:connectedDebugAndroidTest` is the
  device-backed DAO check; if no device is attached, `:core:database:testDebugUnitTest` plus a ledgered note.
- [ ] **Step 5: Commit** — `feat(library): albums, artists and folders show the placeholder`.

### Task 6: Tag IO — remove artwork, detect it, formats; finalizer

**Files:**
- Modify: `feature/tageditor/.../io/TagWriter.kt` (`removeArtwork`), `io/TagReader.kt` (`hasArtwork`), new top-level
  `fun File.supportsArtwork(): Boolean` in `io/TagWriter.kt`; `TagSaveFinalizer.kt`
- Test: `io/TagReaderWriterContractTest.kt` (runs for MP3, FLAC, M4A), `TagSaveFinalizerTest.kt`

**Interfaces:**
- `suspend fun TagWriter.removeArtwork(file: File)`: `deleteArtworkField()` then commit, same in-place strategy as `writeArtwork`.
- `suspend fun TagReader.hasArtwork(file: File): Boolean` (`tag?.firstArtwork != null`).
- `fun File.supportsArtwork(): Boolean` = `extension.lowercase() in setOf("mp3", "flac", "m4a")`.
- `TagSaveFinalizer(…, songArtworkCache: SongArtworkCache)`: after `syncSongs`, `invalidate(songId)` for each saved
  song **each in its own try/catch**, then `refreshPlayback` in its own try (v1.0 follow-up #1).

- [ ] **Step 1: Failing tests**
  - Contract: `removeArtwork_afterWriteArtwork_leavesNoPicture` (write PNG_A, remove → `tag.artworkList` empty;
    control: `writeArtwork_onUntaggedFile_embedsExactBytes` shows a picture is detectable);
    `removeArtwork_leavesEveryTextFieldIntact`; `hasArtwork_falseUntaggedTrueAfterWrite`.
  - `SupportsArtworkTest`: `song.MP3`, `a.flac`, `b.m4a` true; `c.wav`, `d.ogg`, `e.aac`, `noext` false.
  - Finalizer: `invalidatesEverySavedSongById` (two songs, one album → `invalidate(9540)` and `invalidate(9545)`);
    `refreshRunsEvenWhenInvalidateThrows` (cache throws → `updateSongMetadata` still called; control: the first test
    shows invalidate is reached).
- [ ] **Step 2: Run, expect FAIL** — `./gradlew :feature:tageditor:testDebugUnitTest`.
- [ ] **Step 3: Implement.** Then the Inventory's first acceptance grep must print nothing.
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** — `feat(tageditor): remove artwork and per-track invalidation`.

### Task 7: Edit tags — Remove artwork

**Files:**
- Modify: `TagEditorViewModel.kt`, `TagEditorScreen.kt:215-330` (header card)
- Test: `TagEditorViewModelTest.kt`, `TagEditorScreenTest.kt`

**Interfaces:**
- `TagEditorUiState` gains `isArtworkRemoved: Boolean = false`, `canRemoveArtwork: Boolean = false`,
  `isArtworkSupported: Boolean = true`. The header previews `""` when removed, else `pendingArtworkUri ?: artworkUri`.
- Private `fileHasArtwork` from `tagReader.hasArtwork(copy)` at load; `isArtworkSupported = copy.supportsArtwork()`.
- `canRemoveArtwork = isArtworkSupported && !isArtworkRemoved && (fileHasArtwork || pendingArtworkUri != null)`.
- `fun onRemoveArtworkClick()`: clears the pending pick, sets `isArtworkRemoved = true`, recomputes dirty
  (dirty also when `isArtworkRemoved && fileHasArtwork`). `onArtworkPicked` clears `isArtworkRemoved` (last choice wins).
- `writeAndPersist`: after `write`, either `writeArtwork(pending)` or, if removed, `removeArtwork(copy)`.
- Header: `Row` of `OneUiPillButton("Change artwork", tonal = true)` and, when `canRemoveArtwork`,
  `OneUiPillButton("Remove artwork", tonal = true, destructive = true)` — add `destructive: Boolean = false` to
  `OneUiPillButton` (label in `destructiveText` when tonal; KDoc; `KitPreviews`). Both hidden when
  `!isArtworkSupported`.

- [ ] **Step 1: Failing tests**
  - VM: `remove_thenSave_callsRemoveArtworkNotWrite` (fake writer records calls);
    `remove_previewsPlaceholderAndMarksDirty`; `pickAfterRemove_writesThePick`; `removeAfterPick_removes`;
    `noPictureInFile_cannotRemove` (`hasArtwork` false, no pick → `canRemoveArtwork` false; near-miss control: the
    same with a pick → true); `wavFile_hidesArtworkActions` (`isArtworkSupported` false).
  - Screen: `removeButton_shownOnlyWhenRemovable` (node "Remove artwork" exists when true, does not when false — the
    presence half runs first); `removeButton_usesDestructiveText` (text colour == `destructiveText`).
- [ ] **Step 2: Run, expect FAIL** — `./gradlew :feature:tageditor:testDebugUnitTest :core:designsystem:testDebugUnitTest`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** — `feat(tageditor): Remove artwork in Edit tags`.

### Task 8: Tracks tab — Set artwork / Remove artwork for the selection

**Files:**
- Create: `feature/tageditor/.../SelectionArtworkViewModel.kt`, `SelectionArtworkActions.kt` (the ⋮ button, menu,
  picker, confirm dialog, consent effects)
- Modify: `app/.../home/HomeScreen.kt:75-128, 184-253` (selection bar trailing content, snackbar host, result handling)
- Test: `feature/tageditor/src/test/.../SelectionArtworkViewModelTest.kt`, `app/src/test/.../home/HomeScreenTest.kt`

**Interfaces:**
- `@HiltViewModel class SelectionArtworkViewModel(songRepository, songFileResolver, tagWriter, artworkLoader, tagSaveFinalizer)`:
  - `fun onSetArtwork(songIds: List<Long>, imageUri: String)` — loads the image with `ArtworkLoader` (null → message
    "Couldn't use that image as artwork.", nothing else happens), then sets `consentRequest` to every selected song's
    `contentUri`.
  - `fun onRemoveArtwork(songIds: List<Long>)` — sets `consentRequest` directly (the dialog already confirmed).
  - `onConsentGranted()`, `onConsentDenied()`, `onRecoveryGranted()`, `dismissMessage()`, `consumeResult()` — the batch
    editor's consent and API 29 recovery semantics (`BatchTagEditorViewModel` KDoc "The per-song loop and API 29
    recovery"); the loop is new code modelled on it (ruling R5: not extracted, the batch editor stays untouched).
  - Per song: `createEditableCopy` → skip as failed if `!copy.supportsArtwork()` → `writeArtwork`/`removeArtwork` →
    `persistEditedCopy` → delete copy. Then `tagSaveFinalizer.onSongsSaved(succeeded)` once.
  - `data class SelectionArtworkUiState(val isWorking: Boolean = false, val consentRequest: List<String>? = null, val recoveryRequest: RecoverableSecurityException? = null, val message: String? = null, val result: SelectionArtworkResult? = null)`
  - `data class SelectionArtworkResult(val failedIds: Set<String>)`; message per Global Constraints copy.
- `@Composable fun SelectionArtworkActions(selectedIds: List<Long>, onResult: (SelectionArtworkResult) -> Unit, modifier: Modifier = Modifier, viewModel: SelectionArtworkViewModel = hiltViewModel())`:
  `OneUiIconButton(icon = RollaIcons.More, contentDescription = "More options", onClick = …)` (`RollaIcons.kt:111`,
  `IconButtons.kt:65`) → `OneUiDropdownMenu` with `OneUiMenuItem("Set artwork")`
  and `OneUiMenuItem("Remove artwork", destructive = true)` (add `destructive: Boolean = false` to `OneUiMenuItem`);
  Set opens `PickVisualMedia(ImageOnly)`; Remove opens `OneUiDialog` "Remove artwork from N tracks?" with
  `OneUiTextButton("Cancel")` and `OneUiTextButton("Remove", destructive = true)`. While `isWorking` it draws a
  progress ring (`CircularProgressIndicator`, `primary`, `RollaDimens.iconSize`, content description
  "Saving artwork") instead of ⋮. Exposes `message` through a `messages: (String) -> Unit` callback the host shows.
- Home: `SelectionBar` trailing = "Edit tags" + `SelectionArtworkActions`; while working, ✕ and Back are disabled and
  "Edit tags" is disabled. On result: `failedIds.isEmpty()` → `selection.clear()`, else `selection.retainAll(failedIds)`.
  A `SnackbarHost` per the insets table.

- [ ] **Step 1: Failing tests**
  - VM (fakes for resolver/writer/finalizer, `StandardTestDispatcher`):
    `setArtwork_allSucceed_messageAndEmptyFailedSet` ("Artwork set on 3 tracks", `failedIds` empty, finalizer called
    once with 3 songs); `setArtwork_oneFails_kOfNAndKeepsTheFailed` (persist throws for 9544 → "Artwork set on 2 of 3
    tracks", `failedIds == {"9544"}`); `setArtwork_unsupportedFormat_countsAsFailedAndStaysSelected` (copy `x.wav` →
    not written, in `failedIds`); `removeArtwork_singleTrack_usesSingular` ("Artwork removed from 1 track");
    `declined_writesNothingAndKeepsSelection` (no writer call; message "Changes weren't saved — permission declined.";
    `result == null`); `unusableImage_noConsentRequested`; `api29Recovery_retriesOnlyThatSong`.
  - Home: `selectionBar_menuOffersSetAndRemove`; `remove_confirmDialogNamesTheCount` ("Remove artwork from 2
    tracks?"); `result_keepsOnlyFailedSelected` (drive `onResult(SelectionArtworkResult(setOf("2")))` with 1 and 2
    selected → only 2 selected; control: empty set clears).
- [ ] **Step 2: Run, expect FAIL** — `./gradlew :feature:tageditor:testDebugUnitTest :app:testDebugUnitTest --tests "*HomeScreenTest"`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** — `feat(home): Set and Remove artwork for selected tracks`.

### Task 9: Docs, gate and the device walk

**Files:** `CLAUDE.md` (Data Storage "Album Artwork" line, the `:core:data` artwork-cache row, Review follow-ups #1 and
#3 closed, a short "Per-track artwork" status bullet), `.claude/rules/model-vocabulary.md` (`Album`/`Artist`/`Folder`
without `artworkUri`; `Song.artworkUri` described as the per-track value; remove the "Artist.artworkUri" sentence),
`.claude/agents/widget-agent.md`, `.claude/skills/implement-home-widget/SKILL.md` (album-cache mentions →
`SongArtworkCache`). Each completeness sentence is worded to the Inventory greps and those greps are re-run first.

- [ ] **Step 1:** Gate: `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleRelease checkReleaseManifestNoInternet`
  → BUILD SUCCESSFUL; `./gradlew connectedDebugAndroidTest` on the Motorola if attached.
- [ ] **Step 2:** Final review (fresh reviewer, most capable model) with this plan's Review Focus verbatim.
- [ ] **Step 3: Device walk** — install with `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark`; then the
  **user** does, by hand, spec §6's walk: the four tracks show four different pictures before any edit; set a picture
  on Blackwater → Tracks, Now Playing, the notification and the widget change at once while "sunrise house mix" keeps
  its own; Remove → the white note in all four, the notification included; multi-select two tracks → Set, then Remove;
  Albums shows the white note for "Download" and "albumm". The agent's part is read-only: before and after each step,
  `adb shell dumpsys media_session | grep -i -A3 "metadata"` for the notification metadata, and the app's own screens.
- [ ] **Step 4:** Report ready to commit.

## Rulings in this plan

- **R1** Read-time derivation (spec §5.4): the `artwork_uri` column stays, unused by reads; no Room v6. Cost if wrong:
  a dead column until a later migration drops it.
- **R2** Cache budget stays 32 MiB, per track. Cost if wrong: more re-decodes on very large libraries.
- **R3** The old `album_art` cache dir is not deleted by code. Cost if wrong: up to 32 MiB of stale cache until the OS
  reclaims it.
- **R4** Notification placeholder = Now Playing's dark large placeholder (`#3B3B3B` with `#FCFCFE` note, 10.93:1), the
  user's "white note on black". Cost if wrong: a colour constant.
- **R5** The multi-track loop is new code modelled on the batch editor's, not extracted. Cost if wrong: two loops to
  keep in step.
- **R6** Same-second double saves keep the old picture on screens already composed until they recompose (Review
  Focus 2). Cost if wrong: a rare stale thumbnail until the next scroll or navigation.

## Test-control table (writing-plans companion check (d))

Column (d) values were evaluated by reading the code paths named, not run; implementers re-check each with the
mutation and quote the failing line (`subagent-driven-development` companion §2).

| Test | (a) Trivial stub | (b) Assertion that fails under it | (c) Mutation | (d) Why the mutated value differs |
|---|---|---|---|---|
| `SongArtworkTest.value_isPerTrack` | `songArtworkUri` returns a constant | `assertNotEquals(…9540…, …9545…)` | drop `$songId` from the format | both become `rolla-artwork://song/?v=5`, equal |
| `toDomain_ignoresStoredAlbumArtUri` | `toDomain` keeps the column | `assertEquals("rolla-artwork://song/9545?v=7", …)` | `artworkUri = artworkUri` (old line) | yields the stored `content://…/albumart/1` |
| `toDomain_versionFollowsDateModified` | version constant 0 | `assertNotEquals(v7, v8)` | pass `0` for version | both `…?v=0` |
| `get_none_returnsNullAndRemembersIt` | `get` always decodes | `decodeCalls == 1` after two gets | skip writing the `.none` marker | second get decodes, `decodeCalls == 2` |
| `get_decoderThrows_returnsNullAndCachesNothing` | throw → write `.none` | `decodeCalls == 2` and dir empty | catch exception as `None` | marker written, second get skips decode, `decodeCalls == 1` |
| `get_newVersion_dropsTheOldFileAndDecodesAgain` | key by songId only | `decodeCalls == 2`, only `{id}-2.jpg` | file name without `-{version}` | v2 hits v1's file, `decodeCalls == 1` |
| `twoSongs_neverShareAFile` | key by version only | each file holds its own bytes | name `{version}.jpg` | song 2 gets song 1's file and bytes |
| `noPicture_returnsErrorWithoutProceeding` | proceed with original request | `proceed` never called | `?: return chain.proceed(request)` | `proceed` called with the `rolla-artwork` data |
| `nonRefPath_returnsNullWithoutReadingAnything` | keep `decodeContentUri` fallback | `openInputStream` never called | restore the `DirectUri` branch | resolver opened for `…/albumart/1` |
| `songRef_noPicture_returnsThePlaceholder` | return null bitmap | pixel (2,2) == `0xFF3B3B3B` | return `fallback.loadBitmap(uri)` | fallback fails on the custom scheme, future throws |
| through-session loader test | builder without `.setBitmapLoader` | placeholder returned | delete the `.setBitmapLoader` call | default loader cannot open `rolla-artwork:`, throws |
| `updateSongMetadata_newVersion_loadsTheNewBitmap` | loader memoizes by songId | two different pixels | cache key `songId` only in the loader | second load returns the first bitmap |
| `TrackArtworkPixelTest` two rows differ | thumbnails use `albumId` | centres differ | `SongEntity.toDomain` uses `albumId` in the value | both rows request the same data, same colour |
| `TrackArtworkPixelTest` `AlbumCard` placeholder | card loads first song's art | no request recorded; placeholder colour | keep `AlbumCardArt` loading a uri | a request is recorded |
| `summary_takesTheFirstFourTracksUnfiltered` | keep filter + distinct | `[a, "", a, b]` | restore `.filter { isNotBlank() }` | input `a, "", a, b, c` filtered → `[a, a, b, c]`, not `[a, "", a, b]` |
| `rows_showArtworkNotTrackNumbers` | keep `trackNumber = song.trackNumber` | no node "1" | restore that argument | `TrackNumberLabel` draws "1" |
| `removeArtwork_afterWriteArtwork_leavesNoPicture` | `removeArtwork` no-op | `artworkList` empty | drop `commit()` | the file keeps PNG_A |
| `refreshRunsEvenWhenInvalidateThrows` | one shared try | `updateSongMetadata` called | wrap invalidate and refresh in one try | throw skips refresh |
| `noPictureInFile_cannotRemove` | `canRemoveArtwork = true` | false without a pick; true with one | drop `fileHasArtwork` from the condition | stays true on the near-miss's twin |
| `remove_thenSave_callsRemoveArtworkNotWrite` | save ignores removal | `removeArtwork` recorded | skip the removed branch | no `removeArtwork` call |
| `setArtwork_oneFails_kOfNAndKeepsTheFailed` | report all succeeded | "2 of 3", `{"9544"}` | count attempts, not successes | "Artwork set on 3 tracks", empty set |
| `setArtwork_unsupportedFormat_…` | no format check | `x.wav` in `failedIds` | delete the `supportsArtwork()` skip | wav written and counted as success |
| `declined_writesNothingAndKeepsSelection` | write on decline | no writer call, `result == null` | call the loop from `onConsentDenied` | writer called |
| `result_keepsOnlyFailedSelected` | always `clear()` | only "2" selected | always clear | nothing selected |
