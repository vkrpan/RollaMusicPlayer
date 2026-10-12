# One UI redesign, Phase 4a: Favourites, Albums and Artists tabs

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home gains three working tabs, in spec order: **Favourites · Playlists · Tracks · Albums · Artists**.
- Favourites lists the favourite tracks, with working Shuffle, Play and ⋮.
- Albums is a two-column grid of album cards. A tap opens `AlbumDetail`.
- Artists lists artists with a round avatar. A tap opens `ArtistDetail`.
- Album artwork that is already cached shows in the first frame instead of one frame late.

**Architecture:**
- **`:core:database` / `:core:data`:** two new grouped queries, `AlbumDao.observeAlbums` and `ArtistDao.observeArtists`,
  and the repository methods over them. `Artist` gains `artworkUri`, a representative cover chosen in SQL.
  Every Home list sorts case-insensitively.
- **`:core:ui`:** `AlbumRow`, `ArtistRow` and `AlbumCard` are rebuilt on the Phase 1 tokens. The per-track options
  sheet moves here from `TracksTab`, so Favourites can share it.
- **Features:**
  - `:feature:playlists` gets `FavouritesTab` with `FavouritesTabViewModel`.
  - `:feature:library` gets `AlbumsTab`/`AlbumsTabViewModel` and `ArtistsTab`/`ArtistsTabViewModel`.
  - Each tab is a stateful composable with its own `hiltViewModel()`, the same shape as `TracksTab`.
- **`:app`:** `HomeTab` grows to five entries, and `HomeRoute` and `MainActivity` wire the two detail routes.
- **Artwork:** `AlbumArtworkCache.peek` gives the Coil interceptor a fast path that does not suspend, so a
  memory-cache hit no longer waits for a hop to the IO dispatcher.

**Tech stack:**
- Kotlin 2.0.21
- Compose BOM 2024.09.03 (Foundation 1.7.3, M3 1.3.0)
- Room
- Coil 2.7.0
- Hilt
- Robolectric 4.13

**Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md):
- §7.2 (Home)
- §8.1 (rows)
- §8.2 (tabs)
- §9.1–§9.3 (data)
- §12 (dead controls)
- §15 (tests)
- §17 (Phase 4a exit: DAO tests on device, tabs render real data, navigation to the detail screens works)

Read it with the roadmap [`2026-10-06-oneui-redesign-roadmap.md`](2026-10-06-oneui-redesign-roadmap.md) and the code as
it landed in Phase 3 (commit `d3794ae`).

## How to read this plan

- **New units come as complete code:**
  - the queries and repository methods
  - the three tab ViewModels
  - the restyled `AlbumRow`, `ArtistRow` and `AlbumCard`
  - the cache `peek`
- **Restyles and moves of existing files come as a numbered transformation list plus full test code.** These are
  `TracksTab.kt`, the options-sheet move, `HomeScreen.kt` and `MainActivity.kt`.
- **Code blocks have not been compiled.** You may make minimal mechanical fixes (imports, opt-ins) and must report each
  one as a deviation. Never change behaviour, signatures or assertions to get to green.
- **Every test names its mutation:** one realistic production change that should turn it RED. Apply it, confirm RED,
  revert byte-exact, and record the decisive output line.
  - A named mutation is a hypothesis, not a fact (observer 0010).
  - A mutant counts as **equivalent only if no observable output changes**. If the output changes but the test can't
    see it, the test is blind: move its probe.
  - If two theme tokens happen to share a value, separate them in the test's theme instead of calling the mutant
    equivalent.
  - If a named mutation really is equivalent, show the evidence, substitute the closest mutation that can fail, and say
    so in your report.
- **Never prescribe a test host that duplicates production construction** (observer 0009). Tests call the production
  composable or factory.
- **Fixtures must separate the quantities a test distinguishes** (observer 0009, Phase 3 Task 4). Examples: album
  id against album title, artist name against list position, case-only differences.

## Global Constraints

- minSdk 24, compileSdk 34, JVM 17.
- No new runtime dependencies. Test-only dependencies come from the version catalog. The `rolla.android.robolectric`
  convention plugin adds Robolectric.
- Offline app: no network libraries and no INTERNET permission.
- **No hardcoded visual values outside `:core:designsystem`.** Screens use `RollaDimens`, the typography and color
  extensions, `MaterialTheme.shapes`/`featureCard`, and `RollaIcons`.
  - Preview scaffolding and test fixtures are the only exceptions.
  - So is the per-track options sheet: it moves verbatim in Task 6, and Phase 6 restyles every sheet.
- **No Material icons** in any file this phase rewrites (`AlbumRow`, `ArtistRow`, `AlbumCard`). Use `RollaIcons`.
- **Blue text always uses `accentText`, never `primary`.**
- UI copy:
  - Counts say "track"/"tracks" (`tracksCountLabel`) and "album"/"albums" (`albumsCountLabel`).
  - Tab titles: "Favourites", "Playlists", "Tracks", "Albums", "Artists".
  - Empty states:
    - "No favourite tracks yet"
    - "No albums found"
    - "No artists found"
    - Tracks keeps "No music found on this device".
- No dead controls. A disabled control is honest; a control that ignores its tap is dead (spec §12).
- **Robolectric tests:**
  - They run at SDK 34.
  - Layout tests take `@Config(qualifiers = "w360dp-h640dp")`.
  - Text geometry and pixel tests take `@GraphicsMode(NATIVE)`.
  - Pixel tests use the shared helpers that Task 3 moves into `:core:testing`. Use `drawRoot()` for plain trees and
    `captureWindow()` (with `HardwarePixelCopyRule`) for trees with a `sharedElement`.
  - `getBoundsInRoot()` clamps off-screen nodes, so call `performScrollTo()` before comparing below-the-fold geometry.
  - Box-scoped modifiers (`matchParentSize`, `align`) go on the Box's direct child only.
- Spotless enforces CRLF. Run `./gradlew spotlessApply` after editing Kotlin. Never write a BOM. Edit with the
  Edit/Write tools, never `sed -i`.
- **Detekt:**
  - LongMethod: 30 lines.
  - LongParameterList: 6 parameters.
  - TooManyFunctions: interfaces 12, classes 15, files 20.
  - A narrow `@Suppress` with a one-line reason is accepted.
- **No `git commit`, `git add` or `git stash`.** Each task ends at a checkpoint, the orchestrator reviews the
  working-tree diff, and the user triggers commits.
- **The baseline-profile contract survives:**
  - The Tracks list keeps `SONG_LIST_TEST_TAG = "song_list"`, and no other list uses that tag.
  - The root keeps `testTagsAsResourceId = true`.
  - The mini-player wording is unchanged.

## Controller rulings carried into this plan

1. **Favourites has no multi-select.** It has Shuffle, Play, row tap and the ⋮ options sheet (Add to playlist, Edit
   tags).
   - Why: Home's one `SongSelectionState` is pruned by `TracksTab` against the whole library. A second pruner on the
     adjacent pager page would drop non-favourite selections.
   - Batch editing stays on Tracks.
   - If this is wrong: hoisting a second selection state costs about one task.
2. **The options sheet moves to `:core:ui` as `SongOptionsHost`.** Features can't depend on each other, and
   Favourites is its second consumer. The move is verbatim. Its hardcoded `16.dp`/`12.dp` stay ledgered for the Phase 6
   sheet restyle (spec §8.7).
3. **`AlbumDao.observeAlbums` returns the existing `AlbumSearchRow` projection.** Spec §9.1 calls it `AlbumRow`, which would
   collide with `:core:ui`'s `AlbumRow` composable, and the columns are identical.
4. **`ArtistSearchRow` gains `artworkUri`, and all three artist queries project it**: `observeArtists`, `observeArtist`
   and `searchArtists`.
   - The representative cover is the one on the artist's alphabetically first album (spec §9.2), chosen with SQLite's
     documented bare-column rule: with exactly one `MIN()` aggregate, bare columns come from the minimum row.
   - A correlated subquery would scan the table once per artist.
   - Search results show the same avatars as the Artists tab.
5. **Every Home list sorts case-insensitively.**
   - `observeAllSongs` and `observeFavourites` move to `title COLLATE NOCASE`, and the new album and artist queries
     order the same way.
   - Phase 5's A–Z rail builds sections from uppercased first letters. A binary order puts "beta" after "Zappa", and
     the sections would stop being monotonic.
   - `getAllSongs` (the indexer) keeps its order.
6. **New tab ViewModels seed their `StateFlow` with `null`, meaning "not loaded yet".** Tabs render nothing until the first
   emission, so "No albums found" never flashes on first open.
   - Tracks keeps its `emptyList()` seed, because its selection-prune guard depends on it.
7. **Header Play and Shuffle are disabled, not hidden, while their list is empty or loading** (Phase 3 carry-over). A
   hidden pair would make the header jump when the list loads. `CircleIconButton` already supports `enabled`. This
   applies to Tracks and Favourites.
8. **`AlbumCard`:**
   - It gets a `subtitle` parameter, defaulting to the album's artist (spec §8.1). `ArtistDetail` passes the track
     count, because the artist's name under each of their own albums says nothing.
   - The card column is **not** clipped. A 20 dp clip would cut the first glyph of a left-aligned subtitle. Only the
     art box is clipped.
9. **`ArtworkThumbnail` and `ArtworkPlaceholder` gain `shape` and glyph parameters. The placeholder is drawn rectangular
   inside the thumbnail's clipped box.**
   - The box's clip is then the only thing that rounds the corners, so a corner pixel test pins it.
   - This changes no pixel for existing callers.
10. **The artwork first-frame fix:**
    - `AlbumArtworkCache.peek(albumId)` is synchronous: one stat call, and it never decodes.
    - The interceptor calls `peek` first and falls back to `get` only on a miss. A cached cover therefore reaches
      Coil's memory cache on the calling dispatcher, without a hop to IO.
    - `get` now writes to a temporary file and renames it into place. A file that `peek` can see is always complete.
      This also closes v1.0 review follow-up #2.
11. **The pixel-test helpers move into `:core:testing`** (Phase 3 carry-over L3). `:core:ui` becomes their third
    consumer this phase.
12. **Artist list items are keyed by name; album items by id.** The FNV artist id is a hash, and a collision would crash
    the list. The name is the `GROUP BY` key, so it is unique. Two albums can share a title.
13. **Deferred, with owners:**
    - The Home recompose stall on Back (about 30–55 ms on release). It is measured at this gate for regression and
      fixed in Phase 5, whose exit criteria already include a compose-performance-auditor pass.
    - `playFrom(songs, rowIndex)` for duplicate playlist entries goes to Phase 6, which rewrites `PlaylistDetail`.
    - The L7 queue-publish cost on large libraries goes to Phase 7, with the benchmarks.
    - The Album/Artist detail restyle and its "songs" copy go to Phase 6 (user, 2026-10-11).

## Review Focus

These five failure modes are the most likely to bite a user, and no other task's tests cover them. Each line names the
task whose tests pin it.

1. **Two artists whose names differ only in case** ("Queen" and "queen"). Both appear, next to each other, and each
   opens its own songs. Tasks 1 (DAO order and grouping) and 7 (keys by name, both rendered).
2. **Two albums with the same title** ("Greatest Hits" by two artists). Both cards appear, and each opens its own
   album id. Tasks 1 and 7.
3. **Favourites is empty.** "No favourite tracks yet" shows, Play and Shuffle are disabled, and tapping them plays
   nothing. Task 6.
4. **First open of a new tab before Room answers.** No "No albums found" / "No artists found" / "No favourite tracks yet"
   flash. Tasks 5 (null seed) and 6/7 (null renders nothing).
5. **A very long album title next to a short one.** Each card stays one line per text, and both cards in the row keep
   the same height, so the grid stays level. Task 4.

---

### Task 1: Data layer: album and artist lists, artist artwork, case-insensitive order

**Owner:** data-layer-agent (`model: "opus"`)

**Files:**
- Modify:
  - `core/model/src/main/kotlin/com/rolla/musicplayer/core/model/Artist.kt`
  - `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/relation/ArtistSearchRow.kt`
  - `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/AlbumDao.kt`
  - `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/ArtistDao.kt`
  - `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/SearchDao.kt` (`searchArtists`)
  - `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/SongDao.kt` (`observeAllSongs` and
    `observeFavourites` order only)
  - `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/AlbumRepository.kt` and `AlbumRepositoryImpl.kt`
  - `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/ArtistRepository.kt` and `ArtistRepositoryImpl.kt`
  - `core/testing/src/main/kotlin/com/rolla/musicplayer/core/testing/FakeAlbumRepository.kt` and `FakeArtistRepository.kt`
- Test:
  - `core/database/src/androidTest/.../dao/AlbumDaoTest.kt`, `ArtistDaoTest.kt`, `SearchDaoTest.kt` and `SongDaoTest.kt`
    (instrumented; they run at the gate)
  - `core/data/src/test/.../repository/AlbumRepositoryImplTest.kt` (create it if absent), `ArtistRepositoryImplTest.kt`
    and `SearchRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  - `data class Artist(id: Long, name: String, albumCount: Int, songCount: Int, artworkUri: String = "")`
  - `AlbumRepository.observeAlbums(): Flow<List<Album>>`
  - `ArtistRepository.observeArtists(): Flow<List<Artist>>`
  - `FakeAlbumRepository.emitAlbums(List<Album>)`
  - `FakeArtistRepository.emitArtists(List<Artist>)`
- Unchanged: `SongRepository.observeFavourites()`. It already exists (spec §9.3), and only its SQL order changes.

- [ ] **Step 1: Model and projection.**

```kotlin
// Artist.kt
data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    /** The cover of the artist's alphabetically first album; "" when unknown. A list key never uses it. */
    val artworkUri: String = "",
)
```

  In `ArtistSearchRow`, add `val artworkUri: String` as the last constructor property, with no default (every query
  projects it), and pass `artworkUri = artworkUri` in `toDomain()`. Update the three test constructors in
  `ArtistRepositoryImplTest`/`SearchRepositoryImplTest` with explicit `artworkUri` values.

- [ ] **Step 2: Write the failing DAO tests** (instrumented; in each file's existing `buildSongEntity` style).

```kotlin
// AlbumDaoTest
// Mutations: (a) drop COLLATE NOCASE from ORDER BY → "beta" sorts after "Greatest Hits" → RED;
// (b) GROUP BY album instead of album_id → the two "Greatest Hits" merge into one row → RED (Review Focus 2).
@Test
fun observeAlbums_groupsByAlbumId_ordersCaseInsensitively() = runTest {
    songDao.upsertSongs(
        listOf(
            buildSongEntity(id = "1", mediaStoreId = 1L).copy(album = "beta", albumId = 20L, artist = "B"),
            buildSongEntity(id = "2", mediaStoreId = 2L).copy(album = "beta", albumId = 20L, artist = "B"),
            buildSongEntity(id = "3", mediaStoreId = 3L).copy(album = "Alpha", albumId = 10L, artist = "A"),
            buildSongEntity(id = "4", mediaStoreId = 4L).copy(album = "Greatest Hits", albumId = 40L, artist = "X"),
            buildSongEntity(id = "5", mediaStoreId = 5L).copy(album = "Greatest Hits", albumId = 30L, artist = "Y"),
        ),
    )

    val albums = albumDao.observeAlbums().first()

    assertEquals(listOf(10L, 20L, 30L, 40L), albums.map { it.albumId })
    assertEquals(listOf("Alpha", "beta", "Greatest Hits", "Greatest Hits"), albums.map { it.title })
    assertEquals(2, albums.single { it.albumId == 20L }.songCount)
}
```

```kotlin
// ArtistDaoTest
// Mutation: drop COLLATE NOCASE from the outer ORDER BY → "queen" sorts after "Zappa" → RED (Review Focus 1).
@Test
fun observeArtists_groupsByExactName_ordersCaseInsensitivelyThenBinary() = runTest {
    songDao.upsertSongs(
        listOf(
            buildSongEntity(id = "1", mediaStoreId = 1L).copy(artist = "Zappa", albumId = 1L, album = "Z1"),
            buildSongEntity(id = "2", mediaStoreId = 2L).copy(artist = "queen", albumId = 2L, album = "Q1"),
            buildSongEntity(id = "3", mediaStoreId = 3L).copy(artist = "Queen", albumId = 3L, album = "Q2"),
            buildSongEntity(id = "4", mediaStoreId = 4L).copy(artist = "Queen", albumId = 4L, album = "Q3"),
            buildSongEntity(id = "5", mediaStoreId = 5L).copy(artist = "ABBA", albumId = 5L, album = "A1"),
        ),
    )

    val artists = artistDao.observeArtists().first()

    assertEquals(listOf("ABBA", "Queen", "queen", "Zappa"), artists.map { it.name })
    assertEquals(2, artists.single { it.name == "Queen" }.albumCount)
    assertEquals(2, artists.single { it.name == "Queen" }.songCount)
}

// Mutation: MIN(album) without COLLATE NOCASE → binary min is "Zeta" ('Z' < 'a') → art/zeta → RED.
@Test
fun observeArtists_artworkComesFromTheAlphabeticallyFirstAlbum() = runTest {
    songDao.upsertSongs(
        listOf(
            buildSongEntity(id = "1", mediaStoreId = 1L)
                .copy(artist = "X", albumId = 1L, album = "Zeta", artworkUri = "art/zeta"),
            buildSongEntity(id = "2", mediaStoreId = 2L)
                .copy(artist = "X", albumId = 2L, album = "alpha", artworkUri = "art/alpha"),
        ),
    )

    assertEquals("art/alpha", artistDao.observeArtists().first().single().artworkUri)
    assertEquals("art/alpha", artistDao.observeArtist("X").first()?.artworkUri)
}
```

```kotlin
// SearchDaoTest
// Mutation: project '' AS artworkUri in searchArtists → RED.
@Test
fun searchArtists_projectsTheRepresentativeArtwork() = runTest {
    songDao.upsertSongs(
        listOf(
            buildSongEntity(id = "1", mediaStoreId = 1L)
                .copy(artist = "Air", albumId = 1L, album = "Zeta", artworkUri = "art/zeta"),
            buildSongEntity(id = "2", mediaStoreId = 2L)
                .copy(artist = "Air", albumId = 2L, album = "alpha", artworkUri = "art/alpha"),
        ),
    )

    assertEquals("art/alpha", searchDao.searchArtists("Air").first().single().artworkUri)
}
```

```kotlin
// SongDaoTest
// Mutation: restore ORDER BY title ASC (binary) in observeAllSongs → [Alpha, Charlie, beta] → RED.
@Test
fun observeAllSongs_ordersTitlesCaseInsensitively() = runTest {
    songDao.upsertSongs(
        listOf(
            buildSongEntity(id = "1", mediaStoreId = 1L, title = "Charlie"),
            buildSongEntity(id = "2", mediaStoreId = 2L, title = "beta"),
            buildSongEntity(id = "3", mediaStoreId = 3L, title = "Alpha"),
        ),
    )

    assertEquals(listOf("Alpha", "beta", "Charlie"), songDao.observeAllSongs().first().map { it.title })
}

// Mutation: restore binary order in observeFavourites → RED.
@Test
fun observeFavourites_ordersTitlesCaseInsensitively() = runTest {
    songDao.upsertSongs(
        listOf(
            buildSongEntity(id = "1", mediaStoreId = 1L, title = "Charlie").copy(isFavorite = true),
            buildSongEntity(id = "2", mediaStoreId = 2L, title = "beta").copy(isFavorite = true),
            buildSongEntity(id = "3", mediaStoreId = 3L, title = "Alpha").copy(isFavorite = true),
            buildSongEntity(id = "4", mediaStoreId = 4L, title = "aardvark").copy(isFavorite = false),
        ),
    )

    assertEquals(listOf("Alpha", "beta", "Charlie"), songDao.observeFavourites().first().map { it.title })
}
```

  These run on the device at the gate (Task 9). Here, run `./gradlew :core:database:compileDebugAndroidTestKotlin`,
  which must compile. The mutation proofs for instrumented tests run at the gate too. Record the commands in your report.

- [ ] **Step 3: Implement the queries.**

```kotlin
// AlbumDao
/**
 * Every album, one row per album_id, ordered by title case-insensitively (album id breaks ties, so two
 * "Greatest Hits" keep a stable order). Same projection as observeAlbum (see AlbumSearchRow for MIN(artist)).
 */
@Query(
    """
    SELECT
        album_id AS albumId,
        album AS title,
        MIN(artist) AS artist,
        COUNT(*) AS songCount,
        MAX(artwork_uri) AS artworkUri
    FROM songs
    GROUP BY album_id
    ORDER BY title COLLATE NOCASE ASC, albumId ASC
    """,
)
fun observeAlbums(): Flow<List<AlbumSearchRow>>
```

```kotlin
// ArtistDao: observeArtists, plus observeArtist rewritten to the same shape.
/**
 * Every artist (exact-name groups), ordered case-insensitively, then binary, so "Queen" and "queen" sit together in a
 * stable order. artworkUri is the cover of the artist's alphabetically first album: the inner query has exactly one
 * MIN() aggregate, so SQLite takes the bare artwork_uri column from that row (documented SQLite behaviour). One scan,
 * with no per-artist subquery.
 */
@Query(
    """
    SELECT name, albumCount, songCount, artworkUri FROM (
        SELECT
            artist AS name,
            COUNT(DISTINCT album_id) AS albumCount,
            COUNT(*) AS songCount,
            artwork_uri AS artworkUri,
            MIN(album COLLATE NOCASE) AS firstAlbum
        FROM songs
        GROUP BY artist
    )
    ORDER BY name COLLATE NOCASE ASC, name ASC
    """,
)
fun observeArtists(): Flow<List<ArtistSearchRow>>
```

  - `observeArtist(artistName)` uses the same derived-table shape, with `WHERE artist = :artistName` inside the
    inner query and no outer ORDER BY.
  - `SearchDao.searchArtists(query)` uses the same shape, with its existing `LIKE … ESCAPE '\'` filter inside, and keeps
    its outer `ORDER BY name ASC` (Search ordering is Phase 6's).
  - `SongDao`: `observeAllSongs` and `observeFavourites` become `ORDER BY title COLLATE NOCASE ASC`. Leave
    `getAllSongs` unchanged.
  - Update each changed KDoc to say what the query now does.

- [ ] **Step 4: Repositories and fakes.**

```kotlin
// AlbumRepository
/** Every album in the library, case-insensitively by title (Albums tab). */
fun observeAlbums(): Flow<List<Album>>

// AlbumRepositoryImpl
override fun observeAlbums(): Flow<List<Album>> =
    albumDao.observeAlbums()
        .map { rows -> rows.map { it.toDomain() } }
        .distinctUntilChanged()

// ArtistRepository
/** Every artist in the library, case-insensitively by name, each with its representative artwork (Artists tab). */
fun observeArtists(): Flow<List<Artist>>

// ArtistRepositoryImpl
override fun observeArtists(): Flow<List<Artist>> =
    artistDao.observeArtists()
        .map { rows -> rows.map { it.toDomain() } }
        .distinctUntilChanged()
```

  - `FakeAlbumRepository` gains `private val albumsListFlow = MutableStateFlow<List<Album>>(emptyList())`,
    `fun emitAlbums(albums: List<Album>)` and `override fun observeAlbums() = albumsListFlow.asStateFlow()`.
  - `FakeArtistRepository` gains the same for `emitArtists`/`observeArtists`. Its existing `emitAlbums` is the
    artist's albums and keeps that meaning.
  - Add a KDoc line to each fake for the new member.

- [ ] **Step 5: Repository JVM tests** (in each file's existing MockK style).

```kotlin
// ArtistRepositoryImplTest
// Mutation: drop artworkUri from ArtistSearchRow.toDomain() → Artist.artworkUri is "" → RED.
@Test
fun `observeArtists maps rows including artwork`() = runTest {
    every { artistDao.observeArtists() } returns flowOf(
        listOf(ArtistSearchRow(name = "Queen", albumCount = 2, songCount = 5, artworkUri = "art/q")),
    )

    val artist = repository.observeArtists().first().single()

    assertEquals("Queen", artist.name)
    assertEquals("art/q", artist.artworkUri)
    assertEquals(2, artist.albumCount)
    assertEquals(5, artist.songCount)
}

// AlbumRepositoryImplTest
// Mutation: map with a hardcoded songCount = 0 → RED.
@Test
fun `observeAlbums maps rows to albums in order`() = runTest {
    every { albumDao.observeAlbums() } returns flowOf(
        listOf(
            AlbumSearchRow(albumId = 30L, title = "Greatest Hits", artist = "Y", songCount = 3, artworkUri = "a/30"),
            AlbumSearchRow(albumId = 40L, title = "Greatest Hits", artist = "X", songCount = 1, artworkUri = "a/40"),
        ),
    )

    val albums = repository.observeAlbums().first()

    assertEquals(listOf(30L, 40L), albums.map { it.id })
    assertEquals(listOf(3, 1), albums.map { it.songCount })
}
```

- [ ] **Step 6: GREEN, then checkpoint (no commit).**
  1. Run `./gradlew :core:data:testDebugUnitTest :core:database:compileDebugAndroidTestKotlin`, then apply the JVM
     mutations.
  2. Run `./gradlew spotlessApply :core:database:detekt :core:data:detekt :core:model:detekt`.
  3. **Consumer audit (observer 0007):** grep every caller of `observeSongs()` / `observeFavourites()` and every
     `Artist(` / `ArtistSearchRow(` constructor. Report each one and why the order change or the new field is safe
     there.

---

### Task 2: Artwork in the first frame (`peek`, atomic write, interceptor fast path)

**Owner:** general-purpose (`model: "opus"`). This task spans `:core:data` and `:app`, and the ownership map has no
single owner for the Coil pipeline.

**Files:**
- Modify:
  - `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/artwork/AlbumArtworkCache.kt` and
    `AlbumArtworkCacheImpl.kt`
  - `app/src/main/kotlin/com/rolla/musicplayer/artwork/AlbumArtworkInterceptor.kt`
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/MiniPlayer.kt`: only the comment above
    `showPlaceholder`, which claims every load is a frame late
  - Every other `AlbumArtworkCache` implementation, found with
    `grep -rn ": AlbumArtworkCache" --include=*.kt`. Test fakes in `:feature:tageditor`, `:feature:widget` and
    `:feature:settings` tests must implement `peek`.
- Test:
  - `core/data/src/test/.../artwork/AlbumArtworkCacheImplTest.kt`
  - create `app/src/test/kotlin/com/rolla/musicplayer/artwork/AlbumArtworkInterceptorTest.kt`

**Interfaces:**
- Produces: `AlbumArtworkCache.peek(albumId: Long): File?`. It does not suspend, never decodes, and returns null on a miss.

- [ ] **Step 1: Write the failing cache tests** (JVM; `TemporaryFolder` and `Dispatchers.Unconfined`, as the file does
  today).

```kotlin
// Mutation: peek returns fileFor(albumId) without the exists() check → non-null before any get → RED.
@Test
fun peek_beforeAnyGet_isNull() {
    assertNull(cache(FakeArtworkDecoder()).peek(1L))
}

// Mutation: peek always returns null → RED.
@Test
fun peek_afterGet_returnsTheCachedFileWithoutDecoding() = runTest {
    val decoder = FakeArtworkDecoder()
    val artworkCache = cache(decoder)
    val written = artworkCache.get(albumId = 1L, sourceUri = "content://a")
    val callsAfterGet = decoder.decodeCalls

    assertEquals(written, artworkCache.peek(1L))
    assertEquals("peek must never decode", callsAfterGet, decoder.decodeCalls)
}

// Mutation: invalidate skips the delete → peek still returns the file → RED.
@Test
fun peek_afterInvalidate_isNull() = runTest {
    val artworkCache = cache(FakeArtworkDecoder())
    artworkCache.get(albumId = 1L, sourceUri = "content://a")
    artworkCache.invalidate(1L)
    assertNull(artworkCache.peek(1L))
}

// Mutation: rename skipped (bytes written to the temp file and the temp returned) → "1.jpg" absent, a temp left → RED.
@Test
fun get_miss_publishesOnlyTheFinalFile() = runTest {
    val artworkCache = cache(FakeArtworkDecoder())
    artworkCache.get(albumId = 1L, sourceUri = "content://a")
    assertEquals(listOf(cacheFileName(1L)), tempFolder.root.list()!!.sorted())
}
```

  `decodeCalls` is the name used here. If `FakeArtworkDecoder` counts calls under another name, use that one; if it
  doesn't count them, add a counter. **Atomicity itself is not mutation-tested.** Seeing a write in progress would need a
  file-system seam, which costs more than it buys. The reviewer checks the temp-then-rename code. Say so in your report.

- [ ] **Step 2: Implement.**

```kotlin
// AlbumArtworkCache
/**
 * The cached file for [albumId] if a complete one is already on disk, else null. One stat call; never decodes and
 * never suspends, so an image request that hits can reach Coil's memory cache on the calling dispatcher (the
 * interceptor's fast path). Files appear only by an atomic rename, so a non-null result is never half written.
 */
fun peek(albumId: Long): File?
```

```kotlin
// AlbumArtworkCacheImpl
override fun peek(albumId: Long): File? = fileFor(albumId).takeIf { it.exists() }

// get(): the miss path writes a temp file and renames it into place, so peek() and a concurrent get() hit never see
// a partial JPEG (v1.0 review follow-up #2).
mutex.withLock {
    cacheDir.mkdirs()
    val temp = File(cacheDir, "${file.name}$TEMP_SUFFIX")
    temp.writeBytes(bytes)
    temp.setLastModified(System.currentTimeMillis())
    if (!temp.renameTo(file)) {
        temp.delete()
        return@withContext null
    }
    evictOldestWritten(protectedFile = file)
}
```

  - Add `const val TEMP_SUFFIX = ".tmp"` to the companion.
  - `clear()` already deletes every file, temps included.
  - Update the class KDoc: "a hit needs no lock" still holds, and the file is now published by rename.

```kotlin
// AlbumArtworkInterceptor.intercept
val albumId = albumIdOf(request.data)
// peek first: a cover already on disk resolves without suspending, so Coil's memory cache (checked after this
// interceptor, on the same dispatcher) can answer in the same frame. get() hops to IO and decodes only on a miss.
val cachedFile = albumId?.let { albumArtworkCache.peek(it) ?: albumArtworkCache.get(it, request.data.toString()) }
```

- [ ] **Step 3: Interceptor test** (`@RunWith(AndroidJUnit4::class)`. `:app` already runs Robolectric. MockK for the
  cache and the chain).

```kotlin
// Mutation: drop the peek() call (always get) → get is called on a peek hit → RED.
@Test
fun `a peek hit never calls get`() = runTest {
    val file = File("/cache/1.jpg")
    val cache = mockk<AlbumArtworkCache> { every { peek(1L) } returns file }
    val proceeded = slot<ImageRequest>()
    val chain = chainFor(albumArtRequest(albumId = 1L), proceeded)

    AlbumArtworkInterceptor(cache).intercept(chain)

    coVerify(exactly = 0) { cache.get(any(), any()) }
    assertEquals(file, proceeded.captured.data)
}

// Mutation: on a peek miss, proceed with the original request (skip get) → data stays the content uri → RED.
@Test
fun `a peek miss falls back to get`() = runTest {
    val file = File("/cache/2.jpg")
    val cache = mockk<AlbumArtworkCache> {
        every { peek(2L) } returns null
        coEvery { get(2L, any()) } returns file
    }
    val proceeded = slot<ImageRequest>()

    AlbumArtworkInterceptor(cache).intercept(chainFor(albumArtRequest(albumId = 2L), proceeded))

    assertEquals(file, proceeded.captured.data)
}

// Mutation: drop the prefix check (treat any data as album art) → peek is called for a file path → RED.
@Test
fun `non-albumart data touches neither peek nor get`() = runTest {
    val cache = mockk<AlbumArtworkCache>()
    val proceeded = slot<ImageRequest>()

    AlbumArtworkInterceptor(cache).intercept(chainFor(requestFor("content://media/external/images/9"), proceeded))

    verify(exactly = 0) { cache.peek(any()) }
    coVerify(exactly = 0) { cache.get(any(), any()) }
}
```

  Helpers in the test file:
  - `albumArtRequest(id)` builds `ImageRequest.Builder(context).data("content://media/external/audio/albumart/$id")`.
  - `requestFor(data)` builds the same request with any `data`.
  - `chainFor(request, slot)` returns a `mockk<Interceptor.Chain>` with `every { request } returns request`, and
    `coEvery { proceed(capture(slot)) } returns mockk<ErrorResult>()`.

- [ ] **Step 4: GREEN, then checkpoint (no commit).**
  1. Run `./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest --tests "*AlbumArtwork*"`, then the whole
     suite of every module whose fake gained `peek`. Then apply each mutation.
  2. Run `./gradlew spotlessApply` and detekt on the touched modules.
  3. The visible effect is checked on the device at the gate: list thumbnails do not flash when returning to Home.

---

### Task 3: Pixel-test helpers move into `:core:testing`

**Owner:** test-writer (`model: "opus"`)

**Files:**
- Create: `core/testing/src/main/kotlin/com/rolla/musicplayer/core/testing/PixelCapture.kt`
- Modify:
  - `core/testing/build.gradle.kts`: add the Compose BOM platform, `libs.androidx.compose.ui.test.junit4` and
    `libs.robolectric`, all as `implementation`. Every consumer takes `:core:testing` as `testImplementation` only, so
    nothing reaches a production or androidTest classpath.
  - `core/designsystem/build.gradle.kts` and `core/ui/build.gradle.kts`: add `testImplementation(project(":core:testing"))`
- Delete:
  - `core/designsystem/src/test/.../component/PixelTestSupport.kt`
  - `feature/player/src/test/.../player/PixelTestSupport.kt`
- Modify (imports only): every test that used them. Find them with
  `grep -rln "drawRoot\|captureWindow\|assertColorNear\|isColorNear\|pixelsIn\|pixelCopyRenderMode" --include=*.kt`.

**Interfaces:**
- Produces (public, package `com.rolla.musicplayer.core.testing`):
  - `fun AndroidComposeTestRule<*, out ComponentActivity>.drawRoot(): Bitmap`: the software draw of the content view
  - `fun AndroidComposeTestRule<*, out ComponentActivity>.captureWindow(): Bitmap`: PixelCopy, cropped to the content view
  - `fun isColorNear(expected: Color, actual: Int): Boolean`
  - `fun assertColorNear(expected: Color, actual: Int)`
  - `fun AndroidComposeTestRule<*, out ComponentActivity>.pixelsIn(bitmap: Bitmap, bounds: DpRect): List<Int>`
  - `class HardwarePixelCopyRule : TestWatcher`: sets `robolectric.pixelCopyRenderMode=hardware` in `starting` and clears
    it in `finished`. It replaces the `@Before`/`@After` pairs in the player pixel tests.
  - `const val PIXEL_CHANNEL_TOLERANCE = 6`

- [ ] **Step 1: Move.**
  1. Copy the two existing files' bodies into `PixelCapture.kt` unchanged, except for visibility (`internal` becomes
     public) and the shared tolerance constant.
  2. Add `HardwarePixelCopyRule`.
  3. Delete the two copies.
  4. Switch each consumer's imports, and replace its property `@Before`/`@After` with
     `@get:Rule val pixelCopyRule = HardwarePixelCopyRule()`. Watch the rule order: it must apply before the compose
     rule's activity launches. Use `@get:Rule(order = 0)`, and give the compose rule `order = 1`.
- [ ] **Step 2: Prove the shared helpers still catch defects.** Run
  `./gradlew :core:designsystem:testDebugUnitTest :feature:player:testDebugUnitTest`; it must be all green. Then re-run
  one recorded mutation from each module:
  - the tab-ripple clip mutation in `OneUiTabRowTest`
  - the "grey disc while loading" mutation in `MiniPlayerArtPixelTest`

  Confirm both turn RED through the moved helpers, and revert byte-exact.
- [ ] **Step 3: Checkpoint (no commit).** Run `./gradlew spotlessApply :core:testing:detekt`. Report the number of
  call sites switched.

---

### Task 4: `:core:ui` rows and cards (`AlbumRow`, `ArtistRow`, `AlbumCard`, thumbnail shape)

**Owner:** ui-builder (`model: "opus"`). The two additive `:core:designsystem` parameters are in scope.

**Files:**
- Modify:
  - `core/designsystem/.../component/ArtworkPlaceholder.kt`: add `glyph: ImageVector = RollaIcons.MusicNote`
  - `core/ui/.../ArtworkThumbnail.kt`: add `shape` and `placeholderGlyph` parameters (ruling 9)
  - `core/ui/.../TracksCountLabel.kt`: add `albumsCountLabel`
  - `core/ui/.../AlbumRow.kt`, `ArtistRow.kt` and `AlbumCard.kt`: rewrites
  - `feature/library/.../ArtistDetailScreen.kt`: the `AlbumCard` call only (ruling 8)
- Create: `core/ui/.../RowTexts.kt` (an internal two-line title/subtitle column)
- Test:
  - create `core/ui/src/test/.../AlbumCardTest.kt`, `ArtistRowTest.kt`, `AlbumRowTest.kt` and `CountLabelsTest.kt`
  - update any Search test that asserts the old "· N songs" copy, found with
    `grep -rn "songs\"" feature/search/src core/ui/src`

**Interfaces:**
- Produces:
  - `fun ArtworkThumbnail(artworkUri: String, contentDescription: String?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small, placeholderGlyph: ImageVector = RollaIcons.MusicNote)`
  - `fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String = album.artist)`
  - `fun AlbumRow(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier)`: the signature is unchanged
  - `fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier)`: the signature is unchanged
  - `fun albumsCountLabel(count: Int): String`: "1 album", otherwise "N albums"
  - test tags: `ALBUM_CARD_ART_TAG`, `ALBUM_CARD_PLACEHOLDER_TAG`, `ARTIST_AVATAR_TAG` (internal)

- [ ] **Step 1: Write the failing tests.** Every class is `@RunWith(AndroidJUnit4::class)`,
  `@Config(qualifiers = "w360dp-h640dp")` and `@GraphicsMode(NATIVE)`. Hosts wrap content in
  `RollaMusicPlayerTheme(darkTheme = true)` and a `Box(Modifier.background(MaterialTheme.colorScheme.background))`.

```kotlin
// CountLabelsTest
// Mutation: albumsCountLabel returns "$count albums" unconditionally → RED on 1.
@Test
fun `album counts pluralise`() {
    assertEquals("0 albums", albumsCountLabel(0))
    assertEquals("1 album", albumsCountLabel(1))
    assertEquals("2 albums", albumsCountLabel(2))
}
```

```kotlin
// ArtistRowTest
// Mutation: swap albumCount and songCount in the subtitle → "5 albums, 2 tracks" → RED.
@Test
fun `subtitle counts albums then tracks`() {
    setRow(Artist(id = 1L, name = "Queen", albumCount = 2, songCount = 5))
    composeRule.onNodeWithText("2 albums, 5 tracks").assertExists()
}

// Mutation: drop the leading Spacer(listThumbStart) → the name starts at 64 dp → RED.
@Test
fun `name starts at the list text column and the row is a two-line row`() {
    setRow(Artist(id = 1L, name = "Queen", albumCount = 1, songCount = 1))
    val name = composeRule.onNodeWithText("Queen").getUnclippedBoundsInRoot()
    assertEquals(RollaDimens.listTextStart.value, name.left.value, 0.5f)
    val row = composeRule.onNodeWithTag(ROW_HOST).getUnclippedBoundsInRoot()
    assertEquals(RollaDimens.listRowHeight.value, (row.bottom - row.top).value, 0.5f)
}

// Mutation: ArtworkThumbnail ignores `shape` (clips shapes.small, 11 dp) → the probe at (5 dp, 5 dp) lands inside the
// rounded square, so it reads the placeholder colour, not the background → RED. (5,5) is 2.9 dp outside a 48 dp circle.
@Test
fun `avatar is a circle`() {
    setRow(Artist(id = 1L, name = "Queen", albumCount = 1, songCount = 1, artworkUri = ""))
    val avatar = composeRule.onNodeWithTag(ARTIST_AVATAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val bitmap = composeRule.drawRoot()
    val probe = with(composeRule.density) {
        bitmap.getPixel((avatar.left + 5.dp).roundToPx(), (avatar.top + 5.dp).roundToPx())
    }
    val centre = with(composeRule.density) {
        bitmap.getPixel((avatar.left + 24.dp).roundToPx(), (avatar.top + 24.dp).roundToPx())
    }
    assertTrue("outside the circle must be the background", isColorNear(backgroundColor, probe))
    assertTrue("the centre must be the placeholder", isColorNear(placeholderColor, centre))
}

// Mutation: drop clickable from the row → performClick throws / count stays 0 → RED.
@Test
fun `tapping the row calls onClick once`() {
    var clicks = 0
    setRow(Artist(id = 1L, name = "Queen", albumCount = 1, songCount = 1), onClick = { clicks++ })
    composeRule.onNodeWithText("Queen").performClick()
    assertEquals(1, clicks)
}
```

  - `setRow` hosts `ArtistRow` inside `Box(Modifier.testTag(ROW_HOST))`.
  - `backgroundColor` and `placeholderColor` are read from the theme inside `setContent`: `colorScheme.background` and
    `colorScheme.artworkPlaceholder`.
  - The theme needs `darkTheme = true`, so the two colours differ. Assert that they differ before sampling, so a
    future token change can't make the test vacuous.

```kotlin
// AlbumRowTest
// Mutation: subtitle keeps the old "${artist} · ${songCount} songs" → RED.
@Test
fun `subtitle is artist and track count`() {
    setRow(Album(id = 7L, title = "Jazz", artist = "Queen", songCount = 12, artworkUri = ""))
    composeRule.onNodeWithText("Queen, 12 tracks").assertExists()
}

// Mutation: keep the old 56 dp thumbnail and 16 dp padding → text starts at 84 dp → RED.
@Test
fun `title starts at the list text column`() {
    setRow(Album(id = 7L, title = "Jazz", artist = "Queen", songCount = 1, artworkUri = ""))
    assertEquals(
        RollaDimens.listTextStart.value,
        composeRule.onNodeWithText("Jazz").getUnclippedBoundsInRoot().left.value,
        0.5f,
    )
}
```

```kotlin
// AlbumCardTest
// Mutation: default subtitle = tracksCountLabel(album.songCount) → "Queen" missing → RED.
@Test
fun `card shows title and artist by default`() {
    setCard(album(title = "Jazz", artist = "Queen"))
    composeRule.onNodeWithText("Jazz").assertExists()
    composeRule.onNodeWithText("Queen").assertExists()
}

// Mutation: ignore the subtitle parameter → RED.
@Test
fun `subtitle override replaces the artist`() {
    setCard(album(title = "Jazz", artist = "Queen"), subtitle = "12 tracks")
    composeRule.onNodeWithText("12 tracks").assertExists()
    composeRule.onNodeWithText("Queen").assertDoesNotExist()
}

// Mutation: drop aspectRatio(1f) from the art box → its height collapses to 0 (no image loads in the test) → RED.
@Test
fun `art box is square and fills the card width`() {
    setCard(album(), modifier = Modifier.width(150.dp))
    val art = composeRule.onNodeWithTag(ALBUM_CARD_ART_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
    assertEquals(150f, (art.right - art.left).value, 0.5f)
    assertEquals(150f, (art.bottom - art.top).value, 0.5f)
}

// Mutation: draw the placeholder only when artworkUri is blank → missing for a non-blank uri, and a failed load would
// show nothing → RED.
@Test
fun `placeholder always sits under the art`() {
    setCard(album(artworkUri = "content://media/external/audio/albumart/99"))
    composeRule.onNodeWithTag(ALBUM_CARD_PLACEHOLDER_TAG, useUnmergedTree = true).assertExists()
}

// Mutation: drop clip(featureCard) from the art box → the corner probe reads the placeholder colour → RED.
@Test
fun `art corners are rounded`() {
    setCard(album(), modifier = Modifier.width(150.dp))
    val art = composeRule.onNodeWithTag(ALBUM_CARD_ART_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val bitmap = composeRule.drawRoot()
    val corner = with(composeRule.density) {
        bitmap.getPixel((art.left + 1.dp).roundToPx(), (art.top + 1.dp).roundToPx())
    }
    assertTrue(isColorNear(backgroundColor, corner))
}

// Review Focus 5. Mutation: drop maxLines = 1 from the title → the long card grows taller → RED.
@Test
fun `a long title keeps both cards in a row level`() {
    composeRule.setContent {
        RollaMusicPlayerTheme(darkTheme = true) {
            Row {
                AlbumCard(album(id = 1L, title = "Short"), {}, Modifier.width(150.dp).testTag("a"))
                AlbumCard(
                    album(id = 2L, title = "A very long album title that would wrap onto several lines"),
                    {},
                    Modifier.width(150.dp).testTag("b"),
                )
            }
        }
    }
    val a = composeRule.onNodeWithTag("a").getUnclippedBoundsInRoot()
    val b = composeRule.onNodeWithTag("b").getUnclippedBoundsInRoot()
    assertEquals((a.bottom - a.top).value, (b.bottom - b.top).value, 0.5f)
}

// Mutation: drop clickable from the card → RED.
@Test
fun `tapping the card calls onClick`() {
    var clicks = 0
    setCard(album(), onClick = { clicks++ })
    composeRule.onNodeWithText("Jazz").performClick()
    assertEquals(1, clicks)
}
```

  The fixture `album(id = 1L, title = "Jazz", artist = "Queen", songCount = 3, artworkUri = "")` uses named defaults.

- [ ] **Step 2: Confirm RED, then implement.**

```kotlin
// ArtworkPlaceholder: new last parameter `glyph: ImageVector = RollaIcons.MusicNote`, used for the Icon's imageVector.

// ArtworkThumbnail (ruling 9)
@Composable
fun ArtworkThumbnail(
    artworkUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    placeholderGlyph: ImageVector = RollaIcons.MusicNote,
) {
    // The box's clip is the only rounding: the placeholder underneath is drawn rectangular, so every caller's shape
    // (rounded square for tracks and albums, a circle for artists) is decided here.
    Box(modifier = modifier.size(RollaDimens.listThumb).clip(shape)) {
        ArtworkPlaceholder(modifier = Modifier.matchParentSize(), shape = RectangleShape, glyph = placeholderGlyph)
        if (artworkUri.isNotEmpty()) {
            val context = LocalContext.current
            val imageRequest = remember(artworkUri) {
                ImageRequest.Builder(context).data(artworkUri).crossfade(true).build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
```

```kotlin
// TracksCountLabel.kt
/** The "N albums" count on artist rows: "1 album", otherwise "N albums" (UI copy). */
fun albumsCountLabel(count: Int): String = if (count == 1) "1 album" else "$count albums"
```

```kotlin
// RowTexts.kt
/** The two-line text column shared by the album and artist rows: title in songTitle, subtitle in artistName. */
@Composable
internal fun RowTexts(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.songTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

```kotlin
// ArtistRow.kt (whole composable; delete the private ArtistRowContent/ArtistInfo/ArtistThumbnail and the old dp vals)
/**
 * Model-aware row for an [Artist] (spec §8.1): a [RollaDimens.listRowHeight] row with a circular 48 dp avatar (the
 * artist's representative cover, or a Person placeholder) at [RollaDimens.listThumbStart], the name and
 * "N albums, M tracks" at [RollaDimens.listTextStart], and the text ending [RollaDimens.listTrailingEnd] before the
 * row's end (the A–Z rail space). The whole row is one merged clickable node.
 */
@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            ArtworkThumbnail(
                artworkUri = artist.artworkUri,
                contentDescription = null,
                shape = CircleShape,
                placeholderGlyph = RollaIcons.Person,
                modifier = Modifier.testTag(ARTIST_AVATAR_TAG),
            )
            Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
            RowTexts(
                title = artist.name,
                subtitle = "${albumsCountLabel(artist.albumCount)}, ${tracksCountLabel(artist.songCount)}",
                modifier = Modifier.weight(1f),
            )
        }
        InsetDivider(
            startInset = RollaDimens.listTextStart,
            endInset = RollaDimens.listDividerEnd,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

internal const val ARTIST_AVATAR_TAG = "artist_avatar"
```

  `AlbumRow` is the same composable with two differences:
  - The thumbnail is `ArtworkThumbnail(album.artworkUri, null)`, with the default shape and glyph and no tag.
  - The texts are `RowTexts(album.title, "${album.artist}, ${tracksCountLabel(album.songCount)}")`.

  Update its KDoc: the "album detail hasn't shipped yet" sentence is stale.

```kotlin
// AlbumCard.kt (whole file body; delete the old private vals, songCountLabel and the Material icon imports)
/**
 * Album card for the Albums grid and the artist detail strip (spec §8.1): a square art box in the featureCard shape
 * (art over a placeholder), then a left-aligned one-line title in featureCardLabel and a one-line [subtitle] in
 * featureCardCount. The card's width comes from the caller (the grid cell or the strip's fixed width). Only the art
 * box is clipped: clipping the whole column would cut the first glyph of the left-aligned subtitle (ruling 8).
 */
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = album.artist,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        AlbumCardArt(
            artworkUri = album.artworkUri,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag(ALBUM_CARD_ART_TAG),
        )
        Spacer(modifier = Modifier.height(RollaDimens.featureCardLabelGap))
        Text(
            text = album.title,
            style = MaterialTheme.typography.featureCardLabel,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.featureCardCount,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumCardArt(artworkUri: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(MaterialTheme.shapes.featureCard)) {
        ArtworkPlaceholder(
            modifier = Modifier.matchParentSize().testTag(ALBUM_CARD_PLACEHOLDER_TAG),
            shape = RectangleShape,
            large = true,
            glyphSize = RollaDimens.featureCardGlyph,
        )
        if (artworkUri.isNotBlank()) {
            val context = LocalContext.current
            val request = remember(artworkUri) {
                ImageRequest.Builder(context).data(artworkUri).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                // Decorative: the card's merged title and subtitle describe it.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

internal const val ALBUM_CARD_ART_TAG = "album_card_art"
internal const val ALBUM_CARD_PLACEHOLDER_TAG = "album_card_placeholder"
```

  - Keep each file's previews, with their `dp` scaffolding, and fix their arguments to the new signatures.
  - `ArtistDetailScreen`: change the strip call to
    `AlbumCard(album = album, onClick = { onAlbumClick(album.id) }, modifier = Modifier.width(RollaDimens.featureCardSize), subtitle = tracksCountLabel(album.songCount))`.
    Nothing else in that file changes.

- [ ] **Step 3: GREEN.**
  1. Run `./gradlew :core:ui:testDebugUnitTest :core:designsystem:testDebugUnitTest :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest`.
  2. Apply each mutation.
  3. **Consumer audit:**
     - grep `ArtworkThumbnail(`, `ArtworkPlaceholder(`, `AlbumRow(`, `ArtistRow(` and `AlbumCard(`
     - for each call site, say why its rendering is unchanged, or what intentionally changed (Search copy, the
       ArtistDetail strip)
     - confirm no `material.icons` import is left in the three rewritten files
- [ ] **Step 4: Checkpoint (no commit).** Run `./gradlew spotlessApply :core:ui:detekt :core:designsystem:detekt`.

---

### Task 5: Tab ViewModels (`FavouritesTabViewModel`, `AlbumsTabViewModel`, `ArtistsTabViewModel`)

**Owner:** viewmodel-architect (`model: "opus"`)

**Files:**
- Create:
  - `feature/playlists/src/main/kotlin/com/rolla/musicplayer/feature/playlists/FavouritesTabViewModel.kt`
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/AlbumsTabViewModel.kt`
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/ArtistsTabViewModel.kt`
- Test: the three matching `*Test.kt` files in each module's `src/test`.

**Interfaces:**
- Consumes:
  - Task 1: `AlbumRepository.observeAlbums()`, `ArtistRepository.observeArtists()` and the fakes' `emitAlbums`/`emitArtists`
  - Phase 3: `PlaybackController.playFrom`, `playAllInOrder`, `shuffleAll(songs, random)` and `connect()`
- Produces:
  - `FavouritesTabViewModel`:
    - `songs: StateFlow<List<Song>?>`
    - `userPlaylists: StateFlow<List<Playlist>>`
    - `play(song: Song)`, `playAll()`, `shuffleAll()`
    - `addSongToPlaylist(songId: String, playlistId: Long)`
    - `createPlaylistAndAddSong(name: String, songId: String)`
    - `internal var random: Random`
  - `AlbumsTabViewModel.albums: StateFlow<List<Album>?>`
  - `ArtistsTabViewModel.artists: StateFlow<List<Artist>?>`

- [ ] **Step 1: Write the failing tests** (in the `TracksViewModelTest` style: `MainDispatcherRule`, fakes, a relaxed
  `PlaybackController` mock, `launch { collect }` plus `advanceUntilIdle()`).

```kotlin
// AlbumsTabViewModelTest
// Mutation: seed stateIn with emptyList() → value is [] before any subscriber → RED (ruling 6).
@Test
fun `albums are null before the first emission`() {
    assertNull(AlbumsTabViewModel(FakeAlbumRepository()).albums.value)
}

// Mutation: observe albumRepository.observeAlbum(0L)-derived data or emptyFlow() instead of observeAlbums() → RED.
@Test
fun `albums mirror the repository while collected`() = runTest {
    val repository = FakeAlbumRepository()
    val viewModel = AlbumsTabViewModel(repository)
    val job = launch { viewModel.albums.collect {} }
    val albums = listOf(album(id = 30L, title = "Greatest Hits"), album(id = 40L, title = "Greatest Hits"))

    repository.emitAlbums(albums)
    advanceUntilIdle()

    assertEquals(albums, viewModel.albums.value)
    job.cancel()
}
```

  `ArtistsTabViewModelTest` has the same two tests against `FakeArtistRepository.emitArtists`. Its fixture is
  `[Artist(1L, "Queen", 2, 5, "a"), Artist(2L, "queen", 1, 1, "b")]`.

```kotlin
// FavouritesTabViewModelTest
// Mutation: seed with emptyList() → RED (ruling 6).
@Test
fun `songs are null before the first emission`() {
    assertNull(viewModel.songs.value)
}

// Mutation: play(song) calls playbackController.play(song) (queue of one) → RED.
@Test
fun `tapping a favourite plays the favourites from it`() = runTest {
    val job = launch { viewModel.songs.collect {} }
    songRepository.emit(listOf(songA, songB, songC))
    advanceUntilIdle()

    viewModel.play(songB)

    verify { playbackController.playFrom(listOf(songA, songB, songC), songB) }
    job.cancel()
}

// Mutation: playAll() calls playAll(list, 0) directly → playAllInOrder not called → RED.
@Test
fun `play all delegates to playAllInOrder`() = runTest {
    val job = launch { viewModel.songs.collect {} }
    songRepository.emit(listOf(songA, songB))
    advanceUntilIdle()

    viewModel.playAll()

    verify { playbackController.playAllInOrder(listOf(songA, songB)) }
    job.cancel()
}

// Mutation: pass Random.Default instead of the seam → RED.
@Test
fun `shuffle all delegates with the view model's random`() = runTest {
    val job = launch { viewModel.songs.collect {} }
    songRepository.emit(listOf(songA, songB))
    advanceUntilIdle()
    viewModel.random = Random(3)

    viewModel.shuffleAll()

    verify { playbackController.shuffleAll(listOf(songA, songB), viewModel.random) }
    job.cancel()
}

// The helper's own empty no-op is pinned in :core:media (Phase 3 Task 2). This pins that a not-yet-loaded list
// reaches it as empty instead of throwing. Mutation: songs.value!! → NullPointerException → RED.
@Test
fun `play all before the first emission passes an empty list`() {
    viewModel.playAll()
    verify { playbackController.playAllInOrder(emptyList()) }
}

// Mutation: drop playbackController.connect() from init → RED.
@Test
fun `creating the view model connects the controller`() {
    verify { playbackController.connect() }
}

// Mutation: drop the blank-name guard → a playlist named "   " is created → RED.
@Test
fun `createPlaylistAndAddSong ignores a blank name`() = runTest {
    viewModel.createPlaylistAndAddSong(name = "   ", songId = "song-1")
    advanceUntilIdle()
    assertTrue(playlistRepository.observePlaylists().first().isEmpty())
    assertTrue(playlistRepository.addSongsCalls.isEmpty())
}

// Mutation: add to playlist 0 instead of the created id → RED.
@Test
fun `createPlaylistAndAddSong adds the song to the new playlist`() = runTest {
    viewModel.createPlaylistAndAddSong(name = " My Mix ", songId = "song-1")
    advanceUntilIdle()
    val created = playlistRepository.observePlaylists().first().single { it.name == "My Mix" }
    assertEquals(listOf(created.id to listOf("song-1")), playlistRepository.addSongsCalls)
}

// Mutation: swap the arguments (addSongs(songId.toLong(), …)) or drop the call → RED.
@Test
fun `addSongToPlaylist adds to the chosen playlist`() = runTest {
    viewModel.addSongToPlaylist(songId = "song-1", playlistId = 9L)
    advanceUntilIdle()
    assertEquals(listOf(9L to listOf("song-1")), playlistRepository.addSongsCalls)
}
```

  - The `viewModel` is built in `@Before` from `FakeSongRepository` (its `observeFavourites()` is the `emit` flow),
    a relaxed `PlaybackController` mock and `FakePlaylistRepository`.
  - `songA/B/C` use distinct ids and titles.

- [ ] **Step 2: Confirm RED, then implement.**

```kotlin
// AlbumsTabViewModel.kt
/** The Home pager's Albums tab: every album, case-insensitively by title. null until Room answers (ruling 6). */
@HiltViewModel
class AlbumsTabViewModel @Inject constructor(
    albumRepository: AlbumRepository,
) : ViewModel() {

    val albums: StateFlow<List<Album>?> = albumRepository.observeAlbums()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null,
        )
}
```

  `ArtistsTabViewModel` is identical over `ArtistRepository.observeArtists()`, exposing `artists`.

```kotlin
// FavouritesTabViewModel.kt
/**
 * The Home pager's Favourites tab (spec §8.2): the favourite tracks, Play / Shuffle / play-from-row through the shared
 * PlaybackController helpers, and the "Add to playlist" actions of the per-track options sheet. [songs] is null until
 * Room answers (ruling 6); the actions treat that as an empty list.
 */
@HiltViewModel
class FavouritesTabViewModel @Inject constructor(
    songRepository: SongRepository,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val songs: StateFlow<List<Song>?> = songRepository.observeFavourites()
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

    /** Plays the favourites as the queue, starting at the tapped [song] (Phase 3 ruling 5). */
    fun play(song: Song) {
        playbackController.playFrom(songs.value.orEmpty(), song)
    }

    /** Plays the favourites in order from the first. The helper no-ops on an empty list. */
    fun playAll() {
        playbackController.playAllInOrder(songs.value.orEmpty())
    }

    /** Shuffles the favourites from a random start track (spec §12). The helper no-ops on an empty list. */
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

  The last two functions duplicate `TracksViewModel`'s by design. The features can't share code, and the
  duplication is 10 lines with its own tests.

- [ ] **Step 3: GREEN.** Run `./gradlew :feature:library:testDebugUnitTest :feature:playlists:testDebugUnitTest`, then
  apply each mutation.
- [ ] **Step 4: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:library:detekt :feature:playlists:detekt`.

---

### Task 6: Favourites tab UI, the shared options sheet, and disabled headers on empty lists

**Owner:** ui-builder (`model: "opus"`)

**Files:**
- Create:
  - `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/SongOptionsSheet.kt` (moved code, ruling 2)
  - `feature/playlists/src/main/kotlin/com/rolla/musicplayer/feature/playlists/FavouritesTab.kt`
- Modify:
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/TracksTab.kt`: use `SongOptionsHost`, and
    disable the header on an empty list
- Test:
  - create `feature/playlists/src/test/.../FavouritesTabTest.kt`
  - update `feature/library/src/test/.../TracksTabTest.kt`

**Interfaces:**
- Consumes (Task 5): `FavouritesTabViewModel`.
- Produces:
  - `fun SongOptionsHost(song: Song?, userPlaylists: List<Playlist>, onDismiss: () -> Unit, onAddSongToPlaylist: (String, Long) -> Unit, onCreatePlaylistAndAddSong: (String, String) -> Unit, onEditTagsClick: (Song) -> Unit)` in `:core:ui`
  - `fun FavouritesTab(onEditTagsClick: (Song) -> Unit, modifier: Modifier = Modifier, viewModel: FavouritesTabViewModel = hiltViewModel())`
  - `internal fun FavouritesTabContent(songs: List<Song>?, userPlaylists: List<Playlist>, onSongClick: (Song) -> Unit, onShuffleClick: () -> Unit, onPlayClick: () -> Unit, onAddSongToPlaylist: (String, Long) -> Unit, onCreatePlaylistAndAddSong: (String, String) -> Unit, onEditTagsClick: (Song) -> Unit, modifier: Modifier = Modifier)`
  - `internal const val FAVOURITES_LIST_TEST_TAG = "favourites_list"`

- [ ] **Step 1: Move the options sheet.**
  1. Move `TrackOptionsHost` (renamed `SongOptionsHost`, now public, with its KDoc), `AddToPlaylistFlow`,
     `SongOptionsSheet`, `SongOptionsTitle` and `SongOptionRow` from `TracksTab.kt` into `SongOptionsSheet.kt`,
     byte-for-byte apart from the rename and the visibility. The helpers stay `private`.
  2. Add a KDoc line: "Hardcoded paddings are moved verbatim; Phase 6 restyles every sheet (spec §8.7)."
  3. `TracksTab` calls `SongOptionsHost`.
  4. Existing `TracksTabTest` options-sheet tests must pass unchanged. That is the move's proof.
- [ ] **Step 2: Write the failing tests.**

```kotlin
// FavouritesTabTest (@Config(qualifiers = "w360dp-h640dp"), @GraphicsMode(NATIVE))
// Review Focus 4. Mutation: render the empty state for null too (songs.isNullOrEmpty()) → the text exists → RED.
@Test
fun `nothing renders before the favourites load`() {
    setContent(songs = null)
    composeRule.onNodeWithText("No favourite tracks yet").assertDoesNotExist()
    composeRule.onNodeWithTag(FAVOURITES_LIST_TEST_TAG).assertDoesNotExist()
}

// Review Focus 3. Mutation: enabled = true unconditionally → assertIsNotEnabled fails → RED.
@Test
fun `an empty list shows the empty state and disables Play and Shuffle`() {
    var plays = 0
    setContent(songs = emptyList(), onPlayClick = { plays++ }, onShuffleClick = { plays++ })
    composeRule.onNodeWithText("No favourite tracks yet").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Play favourite tracks").assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Shuffle favourite tracks").assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Play favourite tracks").performClick()
    assertEquals(0, plays)
}

// Mutation: wire Play to onShuffleClick → RED.
@Test
fun `Play and Shuffle call their own callbacks`() {
    var played = 0
    var shuffled = 0
    setContent(songs = listOf(songA, songB), onPlayClick = { played++ }, onShuffleClick = { shuffled++ })
    composeRule.onNodeWithContentDescription("Play favourite tracks").performClick()
    composeRule.onNodeWithContentDescription("Shuffle favourite tracks").performClick()
    assertEquals(1, played)
    assertEquals(1, shuffled)
}

// Mutation: the row's onClick passes the first song instead of the tapped one → RED.
@Test
fun `tapping a row plays that track`() {
    var tapped: Song? = null
    setContent(songs = listOf(songA, songB), onSongClick = { tapped = it })
    composeRule.onNodeWithText(songB.title).performClick()
    assertEquals(songB, tapped)
}

// Mutation: onMoreClick = {} (a dead ⋮) → the sheet never opens → RED.
@Test
fun `the overflow opens the options sheet and Edit tags reports the track`() {
    var edited: Song? = null
    setContent(songs = listOf(songA), onEditTagsClick = { edited = it })
    composeRule.onNodeWithContentDescription("More options for ${songA.title}").performClick()
    composeRule.onNodeWithText("Edit tags").performClick()
    assertEquals(songA, edited)
}

// Mutation: reuse SONG_LIST_TEST_TAG on this list → the favourites tag is missing → RED (baseline-profile contract).
@Test
fun `the list carries its own test tag`() {
    setContent(songs = listOf(songA))
    composeRule.onNodeWithTag(FAVOURITES_LIST_TEST_TAG).assertExists()
}
```

```kotlin
// TracksTabTest (add)
// Mutation: TracksSortHeader enabled = true unconditionally → RED.
@Test
fun `an empty library disables Play and Shuffle`() {
    // Host TracksTabContent with songs = emptyList() and ScanState.Done(0, 0), in the file's existing style.
    composeRule.onNodeWithContentDescription("Play all tracks").assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Shuffle all tracks").assertIsNotEnabled()
}
```

  `setContent` hosts `FavouritesTabContent` in `RollaMusicPlayerTheme`, passing `userPlaylists = emptyList()` and no-op
  defaults for every callback the test does not name.

- [ ] **Step 3: Implement `FavouritesTab`.** Use the `TracksTab` structure with these differences:
  - `FavouritesTab` collects `songs` and `userPlaylists` with `collectAsStateWithLifecycle()`, and passes
    `remember(viewModel) { viewModel::… }` references (the `TracksTab` idiom).
  - `FavouritesTabContent`:
    - A `Column(fillMaxSize)` holds `SortHeader(label = "Name")` with two `CircleIconButton`s:
      - `RollaIcons.Shuffle`, contentDescription "Shuffle favourite tracks"
      - `RollaIcons.Play`, contentDescription "Play favourite tracks"
      - both with `enabled = !songs.isNullOrEmpty()` (ruling 7)
    - Then the body:
      - `null` renders nothing.
      - Empty renders a centered "No favourite tracks yet" in `emptyState`/`onSurfaceVariant`, with horizontal padding
        `RollaDimens.screenEdge`.
      - Otherwise a `LazyColumn` tagged `FAVOURITES_LIST_TEST_TAG`, with
        `contentPadding = PaddingValues(bottom = homeListBottomPadding())`, items keyed by `song.id`, and
        `SongListItem(song, onClick = { onSongClick(song) }, onMoreClick = { optionsSong = song })`. There is no
        `onLongClick` and no selection (ruling 1).
    - Then `SongOptionsHost(song = optionsSong, …)`. `optionsSong` is a `remember { mutableStateOf<Song?>(null) }`.
  - Split private composables as needed to stay under LongMethod 30.
  - Add a dark and light `@Preview` in the file's style.
  - In `TracksTab`, `TracksSortHeader` gains `enabled: Boolean`, passed as `songs.isNotEmpty()` to both buttons.
- [ ] **Step 4: GREEN.** Run `./gradlew :feature:playlists:testDebugUnitTest :feature:library:testDebugUnitTest
  :core:ui:testDebugUnitTest`, then apply each mutation.
- [ ] **Step 5: Checkpoint (no commit).** Run `./gradlew spotlessApply` and detekt on the three modules.

---

### Task 7: Albums and Artists tab UI

**Owner:** ui-builder (`model: "opus"`)

**Files:**
- Create:
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/AlbumsTab.kt`
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/ArtistsTab.kt`
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/TabStates.kt`: internal
    `TabScanning()` and `TabMessage(text: String)`, moved from TracksTab's private `ScanningContent`/`EmptyTracksContent`
    (text becomes a parameter). TracksTab uses them.
- Test: create `AlbumsTabTest.kt` and `ArtistsTabTest.kt` in `feature/library/src/test/...`.

**Interfaces:**
- Consumes:
  - Task 4: `AlbumCard`, `ArtistRow`
  - Task 5: `AlbumsTabViewModel`, `ArtistsTabViewModel`
  - existing: `ScanState`
- Produces:
  - `fun AlbumsTab(scanState: ScanState, onAlbumClick: (Long) -> Unit, modifier: Modifier = Modifier, viewModel: AlbumsTabViewModel = hiltViewModel())`
  - `internal fun AlbumsTabContent(albums: List<Album>?, scanState: ScanState, onAlbumClick: (Long) -> Unit, modifier: Modifier = Modifier)`
  - `fun ArtistsTab(scanState: ScanState, onArtistClick: (String) -> Unit, modifier: Modifier = Modifier, viewModel: ArtistsTabViewModel = hiltViewModel())`
  - `internal fun ArtistsTabContent(artists: List<Artist>?, scanState: ScanState, onArtistClick: (String) -> Unit, modifier: Modifier = Modifier)`
  - `internal const val ALBUM_GRID_TEST_TAG = "album_grid"`
  - `internal const val ARTIST_LIST_TEST_TAG = "artist_list"`

- [ ] **Step 1: Write the failing tests** (`@Config(qualifiers = "w360dp-h640dp")`, `@GraphicsMode(NATIVE)`).

```kotlin
// AlbumsTabTest
// Mutation: GridCells.Fixed(1) → the second card sits below the first → RED.
// Mutation: drop horizontalArrangement → the gap is 0 → RED.
// Mutation: drop the start contentPadding → the first card starts at 0 → RED.
@Test
fun `albums lay out in two columns with the card gap`() {
    setContent(albums = listOf(album(1L, "A"), album(2L, "B"), album(3L, "C")))
    val first = composeRule.onNodeWithText("A").parentCardBounds()
    val second = composeRule.onNodeWithText("B").parentCardBounds()
    assertEquals(first.top.value, second.top.value, 0.5f)
    assertEquals(RollaDimens.featureCardRowStart.value, first.left.value, 0.5f)
    assertEquals(RollaDimens.featureCardGap.value, (second.left - first.right).value, 0.5f)
}

// Review Focus 2. Mutation: key = { it.title } → duplicate key → IllegalArgumentException → RED.
@Test
fun `two albums with the same title both show and open their own ids`() {
    val opened = mutableListOf<Long>()
    setContent(
        albums = listOf(album(30L, "Greatest Hits"), album(40L, "Greatest Hits")),
        onAlbumClick = { opened += it },
    )
    val cards = composeRule.onAllNodesWithText("Greatest Hits")
    cards.assertCountEquals(2)
    cards[1].performClick()
    assertEquals(listOf(40L), opened)
}

// Review Focus 4. Mutation: treat null as empty → "No albums found" exists → RED.
@Test
fun `nothing renders before the albums load`() {
    setContent(albums = null)
    composeRule.onNodeWithText("No albums found").assertDoesNotExist()
    composeRule.onNodeWithTag(ALBUM_GRID_TEST_TAG).assertDoesNotExist()
}

// Mutation: show the empty text while scanning → RED (the scanning spinner must win, as on Tracks).
@Test
fun `scanning shows progress, an empty library shows the message`() {
    setContent(albums = emptyList(), scanState = ScanState.Scanning)
    composeRule.onNodeWithContentDescription("Scanning library").assertExists()
    composeRule.onNodeWithText("No albums found").assertDoesNotExist()
}

@Test
fun `an empty library says no albums found`() {
    setContent(albums = emptyList(), scanState = ScanState.Done(0, 0))
    composeRule.onNodeWithText("No albums found").assertIsDisplayed()
}

// Spec §7.3. Mutation: drop the bottom contentPadding → the last card ends under the 60 dp pill → RED.
@Test
fun `the last card scrolls fully above the mini-player`() {
    val albums = List(20) { album(it.toLong(), "Album $it") }
    setContent(albums = albums, miniPlayerInset = 60.dp)
    composeRule.onNodeWithTag(ALBUM_GRID_TEST_TAG).performScrollToIndex(albums.lastIndex)
    val last = composeRule.onNodeWithText("Album 19").parentCardBounds()
    val root = composeRule.onRoot().getUnclippedBoundsInRoot()
    assertTrue(last.bottom <= root.bottom - 60.dp)
}
```

  Test-file helpers:
  - `setContent(albums, scanState = ScanState.Done(1, 0), onAlbumClick = {}, miniPlayerInset = 0.dp)` hosts
    `AlbumsTabContent` inside `CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset)` and the theme.
  - `parentCardBounds()` reads the clickable card node's bounds. The card's merged node is the one carrying the click
    action, so use `onNode(hasText(title) and hasClickAction())`, or tag each card in the test host.
    `getBoundsInRoot()` clamps off-screen nodes, so scroll first.
  - The fixture `album(id, title)` gives each album a distinct artist, `songCount = 1` and `artworkUri = ""`.

```kotlin
// ArtistsTabTest
// Review Focus 1. Mutation: key = { it.name.lowercase() } → duplicate key → crash → RED.
@Test
fun `artists differing only in case both show and open their own names`() {
    val opened = mutableListOf<String>()
    setContent(
        artists = listOf(artist(1L, "Queen"), artist(2L, "queen")),
        onArtistClick = { opened += it },
    )
    composeRule.onNodeWithText("queen").performClick()
    composeRule.onNodeWithText("Queen").performClick()
    assertEquals(listOf("queen", "Queen"), opened)
}

// Review Focus 4. Mutation: treat null as empty → RED.
@Test
fun `nothing renders before the artists load`() {
    setContent(artists = null)
    composeRule.onNodeWithText("No artists found").assertDoesNotExist()
    composeRule.onNodeWithTag(ARTIST_LIST_TEST_TAG).assertDoesNotExist()
}

@Test
fun `an empty library says no artists found`() {
    setContent(artists = emptyList(), scanState = ScanState.Done(0, 0))
    composeRule.onNodeWithText("No artists found").assertIsDisplayed()
}

// Mutation: drop the bottom contentPadding → RED (same probe as the albums test, on the last row).
@Test
fun `the last row scrolls fully above the mini-player`() {
    val artists = List(20) { artist(it.toLong(), "Artist $it") }
    setContent(artists = artists, miniPlayerInset = 60.dp)
    composeRule.onNodeWithTag(ARTIST_LIST_TEST_TAG).performScrollToIndex(artists.lastIndex)
    val last = composeRule.onNodeWithText("Artist 19").getUnclippedBoundsInRoot()
    val root = composeRule.onRoot().getUnclippedBoundsInRoot()
    assertTrue(last.bottom <= root.bottom - 60.dp)
}
```

- [ ] **Step 2: Implement.**
  - `AlbumsTab` collects `viewModel.albums`, then calls `AlbumsTabContent`.
  - `AlbumsTabContent`: `Column(fillMaxSize)` with `SortHeader(label = "Name")` (no trailing buttons, spec §8.2), then:
    - `scanState is ScanState.Scanning` gives `TabScanning()`
    - `albums == null` gives nothing
    - `albums.isEmpty()` gives `TabMessage("No albums found")`
    - otherwise:

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(ALBUM_GRID_COLUMNS),
    modifier = Modifier.fillMaxSize().testTag(ALBUM_GRID_TEST_TAG),
    contentPadding = PaddingValues(
        start = RollaDimens.featureCardRowStart,
        end = RollaDimens.featureCardRowStart,
        bottom = homeListBottomPadding(),
    ),
    horizontalArrangement = Arrangement.spacedBy(RollaDimens.featureCardGap),
    verticalArrangement = Arrangement.spacedBy(RollaDimens.featureCardGap),
) {
    items(items = albums, key = { album -> album.id }) { album ->
        AlbumCard(album = album, onClick = { onAlbumClick(album.id) })
    }
}

private const val ALBUM_GRID_COLUMNS = 2 // spec §8.2: a 2-column grid
```

  - `ArtistsTab` and `ArtistsTabContent` use the same structure:
    - `TabMessage("No artists found")`
    - a `LazyColumn` tagged `ARTIST_LIST_TEST_TAG`, with `contentPadding = PaddingValues(bottom = homeListBottomPadding())`
    - `items(items = artists, key = { artist -> artist.name })` (ruling 12), each rendering
      `ArtistRow(artist, onClick = { onArtistClick(artist.name) })`
  - `TabStates.kt` holds the moved `TabScanning` (the existing `CircularProgressIndicator` with "Scanning library"
    semantics; its `primary` colour is a progress indicator, not text) and `TabMessage(text)`. Delete TracksTab's
    private copies and point TracksTab at these.
  - Add dark and light `@Preview`s to both tab files.
- [ ] **Step 3: GREEN.** Run `./gradlew :feature:library:testDebugUnitTest`, then apply each mutation.
- [ ] **Step 4: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:library:detekt`.

---

### Task 8: Home wiring (five tabs, detail navigation)

**Owner:** navigation-agent (`model: "opus"`)

**Files:**
- Modify:
  - `app/src/main/kotlin/com/rolla/musicplayer/home/HomeTab.kt`
  - `app/src/main/kotlin/com/rolla/musicplayer/home/HomeScreen.kt` (`HomeRoute` only, plus its KDoc)
  - `app/src/main/kotlin/com/rolla/musicplayer/MainActivity.kt` (`composable<Home>`, plus the stale AlbumDetail and
    ArtistDetail route comments)
- Test:
  - create `app/src/test/kotlin/com/rolla/musicplayer/home/HomeTabTest.kt`
  - update `HomeScreenTest.kt` and `HomeScreenRestorationTest.kt`

**Interfaces:**
- Consumes (Tasks 6 and 7): `FavouritesTab(onEditTagsClick)`, `AlbumsTab(scanState, onAlbumClick)` and
  `ArtistsTab(scanState, onArtistClick)`.
- Produces:
  - `enum class HomeTab { FAVOURITES, PLAYLISTS, TRACKS, ALBUMS, ARTISTS }`
  - `HomeRoute(…, onAlbumClick: (Long) -> Unit, onArtistClick: (String) -> Unit, …)`
  - Phase 4b adds `FOLDERS` at the end, and `onFolderClick` the same way.

- [ ] **Step 1: Write the failing tests.**

```kotlin
// HomeTabTest
// Mutation: list ALBUMS before TRACKS, or keep the two-tab enum → RED.
@Test
fun `tabs follow the spec order and titles`() {
    assertEquals(
        listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists"),
        HomeTab.entries.map { it.title },
    )
}
```

```kotlin
// HomeScreenTest (add, in the file's existing pageContent-host style: each page renders "page-<NAME>")
// Mutation: rememberHomePagerState's initialPage = 0 → opens on Favourites → RED.
@Test
fun `home opens on Tracks with five tabs`() {
    // host HomeScreen with rememberHomePagerState() as the existing tests do
    composeRule.onNodeWithText("page-TRACKS").assertIsDisplayed()
    assertEquals(HomeTab.TRACKS.ordinal, pagerState.currentPage)
    assertEquals(HomeTab.entries.size, pagerState.pageCount)
}

// Mutation: swap ALBUMS and ARTISTS in the HomeTab enum → the first swipe lands on page-ARTISTS → RED. (The test host
// renders "page-<NAME>" itself, so HomeRoute's when-dispatch is not visible here; the device check covers it.)
@Test
fun `swiping left from Tracks reaches Albums then Artists`() {
    composeRule.onNodeWithText("page-TRACKS").performTouchInput { swipeLeft() }
    composeRule.onNodeWithText("page-ALBUMS").assertIsDisplayed()
    composeRule.onNodeWithText("page-ALBUMS").performTouchInput { swipeLeft() }
    composeRule.onNodeWithText("page-ARTISTS").assertIsDisplayed()
}
```

  `HomeScreenRestorationTest` keeps its Playlists case. Add the same restoration case for `HomeTab.ARTISTS`. Its
  mutation: drop `rememberSaveable` backing, which is already pinned. Name the existing mutation in a comment.

- [ ] **Step 2: Implement.**
  1. `HomeTab`:
     - entries `FAVOURITES("Favourites"), PLAYLISTS("Playlists"), TRACKS("Tracks"), ALBUMS("Albums"), ARTISTS("Artists")`
     - the KDoc says Folders lands in Phase 4b
  2. `HomeRoute`:
     - Add `onAlbumClick: (Long) -> Unit` and `onArtistClick: (String) -> Unit` after `onSmartPlaylistClick`.
     - Update the `@Suppress("LongParameterList")` reason (eight callbacks).
     - The `when (tab)` gains:
       - `HomeTab.FAVOURITES -> FavouritesTab(onEditTagsClick = onEditTagsClick)`
       - `HomeTab.ALBUMS -> AlbumsTab(scanState = scanState, onAlbumClick = onAlbumClick)`
       - `HomeTab.ARTISTS -> ArtistsTab(scanState = scanState, onArtistClick = onArtistClick)`
     - Update the KDoc's "the Playlists / Tracks tab row" wording.
  3. `MainActivity` `composable<Home>`:
     - `onAlbumClick = { id -> navController.navigate(AlbumDetail(albumId = id)) { launchSingleTop = true } }`
     - `onArtistClick = { name -> navController.navigate(ArtistDetail(artistName = name)) { launchSingleTop = true } }`
     - Each gets a one-line comment: no popUpTo, so back returns to Home on the same tab.
  4. Rewrite the AlbumDetail and ArtistDetail destination comments, which say "reachable today only from Search…
     a library Albums tab that doesn't exist yet". They are now reachable from Home's Albums/Artists tabs, Search,
     Now Playing ⋮ and ArtistDetail.
  5. Grep `MainActivity.kt` for any other comment this phase made false, and fix it.
- [ ] **Step 3: GREEN.** Run `./gradlew :app:testDebugUnitTest`, then apply each mutation.
- [ ] **Step 4: Checkpoint (no commit).** Run `./gradlew spotlessApply :app:detekt`, then `./gradlew assembleDebug`.

---

### Task 9: Phase gate, device check and phase review

**Owner:** orchestrator. test-writer (`model: "opus"`) handles instrumented fixes.

- [ ] **Step 1: Full gate.**
  1. Check free disk space.
  2. Run `./gradlew spotlessApply`.
  3. In a separate invocation, run `./gradlew check assembleDebug --continue` in the background, logging to a file.
  4. Expect BUILD SUCCESSFUL. Existing suites get updated, never deleted.
- [ ] **Step 2: Hardcoded-value and icon grep.**

```
grep -rnE "Color\(0x|[0-9]+\.dp|[0-9]+\.sp|material\.icons" \
  core/ui/src/main feature/library/src/main feature/playlists/src/main app/src/main/kotlin/com/rolla/musicplayer/home
```

  Expected hits:
  - `0.dp` defaults
  - preview scaffolding
  - `SongOptionsSheet.kt`'s moved paddings (ruling 2)
  - the untouched detail screens, which Phase 6 restyles

  Any hit in `AlbumRow`, `ArtistRow`, `AlbumCard`, `ArtworkThumbnail`, `FavouritesTab`, `AlbumsTab`, `ArtistsTab`,
  `TabStates` or `HomeScreen` is a defect.
- [ ] **Step 3: Project-instruction sweep (observer 0006).**
  1. `.claude/rules/model-vocabulary.md`: add `val artworkUri: String = ""` to the `Artist` block, with "the cover of
     the artist's alphabetically first album; identity stays the name" (spec §16).
  2. `CLAUDE.md`: extend the redesign banner with a Phase 4a line:
     - Favourites, Albums and Artists tabs
     - `SongOptionsHost` in `:core:ui`
     - `AlbumArtworkCache.peek`
     - pixel helpers in `:core:testing`
     - v1.0 follow-up #2 (atomic artwork write) closed

     Mark follow-up #2 closed in its list.
  3. Grep both files for statements this phase made false. For example, "Album/Artist surfaces are reached only
     through Search" is now false. Fix it in the banner or mark it. The full rewrite stays in Phase 7.
- [ ] **Step 4: Device check over adb.** The phone must be connected and unlocked. Never uninstall or clear data. Restore
  any favourite you toggle.
  1. Build `assembleBenchmark`, then `install -r app/build/outputs/apk/benchmark/app-benchmark.apk` and
     `cmd package compile -f -m speed-profile com.rolla.musicplayer`.
  2. **Tabs:** the five titles in order, opening on Tracks, with swipe and tap both working.
  3. **Favourites:** the list matches Now Playing's ♥ state. Play, Shuffle and row tap each play the favourites. ⋮
     offers Add to playlist and Edit tags. If the user has no favourites, Play and Shuffle are disabled: check this
     only by observation, without changing the user's favourites.
  4. **Albums:** the 2-column grid, rounded art, one-line title and artist. A tap opens AlbumDetail, and Back returns
     to Albums with the pill still there.
  5. **Artists:** round avatars, "N albums, M tracks". A tap opens ArtistDetail.
  6. **Artwork first frame (ruling 10):** open Now Playing from Tracks and press Back. The Tracks thumbnails must not
     flash grey. Scroll the Albums grid back and forth; covers already seen must not flash. Record a short screen
     recording if it is ambiguous.
  7. **Home Back stall (ruling 13):** on the benchmark build, capture `dumpsys gfxinfo com.rolla.musicplayer` frame stats
     for ten Now Playing → Back cycles. Compare with the Phase 3 figure (about 30–55 ms). A regression above that range is a
     High finding for this phase; otherwise ledger the figure for Phase 5.
  8. **Captures:** save screencaps in `docs/design/calibration/phase4a/`. They stay uncommitted.
  9. **Connected tests:**
     - Run `./gradlew :core:database:connectedDebugAndroidTest`, which holds Task 1's DAO tests, and run their mutation
       proofs.
     - Run `:feature:library:connectedDebugAndroidTest` (the Tracks smoke test).
- [ ] **Step 5: Phase review.**
  - The orchestrator dispatches `code-reviewer` (`model: "opus"`) over the whole-phase diff. Inputs: spec §7.2, §8.1,
    §8.2, §9.1–§9.3 and §12, this plan and the ledger.
  - **Next-phase consumer check (observer 0006).** Check this phase's Produces against Phase 4b's spec consumers
    (§7.1 `FolderDetail`, §8.1 `FolderRow`, §8.2 Folders, §9.4):
    - `HomeTab` can take `FOLDERS` last.
    - `HomeRoute` can take `onFolderClick` the same way.
    - `ArtworkThumbnail` can host the `FolderBadge` overlay.
    - `TabScanning`/`TabMessage` serve a Folders tab.
  - Also check against Phase 5 (§10): every rail list sorts case-insensitively (ruling 5).
  - Acceptance needs all of:
    - no Critical or High findings
    - green gates
    - the device check done
    - the connected DAO tests green
- [ ] **Step 6: Ready-to-commit report (no commit).** Report the files changed, test counts, the gate result and the
  device evidence, with this prepared message:

```
feat(library): One UI Favourites, Albums and Artists tabs

Home now has five tabs: Favourites, Playlists, Tracks, Albums and Artists. Favourites plays the favourite
tracks with working Shuffle, Play and options. Albums is a two-column card grid and Artists lists artists
with a round cover avatar; both open their detail screens. New grouped album and artist queries back them,
every Home list sorts case-insensitively, and the per-track options sheet moves to :core:ui. Cached album
covers now resolve without an IO hop, so they show in the first frame, and cache files are published by an
atomic rename.
Phase 4a of docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
