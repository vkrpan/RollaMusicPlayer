# Custom tags phase 4 (finding and showing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Find tracks (a filter screen with live AND results played as the queue) and tag chips on track rows and on
Now Playing.

**Architecture:** `:core:data` gains a song-to-tags map and a `SongFinder` that filters the library in memory against
a `SongFilter`. `:core:designsystem` gains small tag chips and a one-line "+N" chip line. `:core:ui`'s `SongListItem`
reads each song's tags from a composition local that `:app` provides once, so every track list shows chips without
threading data through each screen. Find tracks lives in `:feature:search` (package `findtracks`), reached from a
Search button on the Tracks tab's sort header. Phase 4 writes no files.

**Tech Stack:** Kotlin 2.2.21, Compose (BOM 2024.09.03, kit in `:core:designsystem`), Room v7 (no schema change),
Hilt 2.57.2, Navigation Compose type-safe routes, Robolectric, Macrobenchmark on `pixel6Api31Frames`.

**Spec:** [[2026-11-09-custom-tags-design]] (§3 goals 5–7, §5.3, §5.5 Find tracks / Now Playing / Track lists, §6
calls 5, 6, 9, 10, 11, §9 scroll cost). Roadmap: [[2026-11-09-custom-tags-roadmap]]. Consumes phases 1–3 (committed
`dce76dd`, `eb9ebcb`, `7f26949`).

## Global Constraints

- In code the audio item is `Song`, never `Track`; on screen "Tracks", "Tags", "Subtags" (`.claude/rules/model-vocabulary.md`). New model names get their vocabulary row first (the controller adds `SongFilter` and `SongCustomTags` before Task 1).
- UI from the kit and tokens only; every new size, style or colour is a token with its row in `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md` §5 before a screen uses it; every new colour pairing gets a `ContrastTest` row (text 4.5:1, non-text 3:1); British spelling in copy.
- `:feature → :core → :core:model`; features never depend on each other.
- No network permission or dependency.
- Text filters match any part of the text, ignoring case; Year matches exactly; every active filter must match (AND); results in library order (`sortedByLibraryName`, the Tracks tab's A–Z) (spec §5.3, §6 call 5).
- Home's quick search is unchanged (spec §6 call 6).
- Chips in lists: tags first, then subtags, one line, as many as fit plus "+N"; subtags take a visually distinct chip; rows without tags keep today's height (spec §6 call 9). Now Playing: the same chips under the title, centred, at most two lines (§6 call 10).
- Unknown IDs (in a file, not in the lists) are never shown and never counted (§6 call 4).
- Device steps follow [[Device safety]]: the agent never saves tags on the Motorola; benchmarks run on the managed emulator `pixel6Api31Frames`, never on a phone.

## Review Focus

1. A filter typed with spaces or another case (" LOVE ") finds "Lovesong"; a blank field is not a filter. → Task 1 `matches_trimsAndIgnoresCase`.
2. Artist "unknown" finds songs shown as "Unknown", including a blank raw artist (`artistDisplayName`). → Task 1 `matches_artistUsesDisplayName`.
3. A tag in the active filter is deleted through ⋮ Edit tags → Tags → Manage tags while Find tracks sits under it: on return its chip is gone and the results recompute; the lists' error fallback never drops a filter. → Task 5 `listsChange_prunesDeletedFilterTag`, `listsFail_keepsFilterTags`.
4. A result's tags are edited (⋮ Edit tags) so it no longer matches: on return it has left the results. → Task 1 `observeMatches_reEmitsWhenLinksChange`.
5. At 1.5× font on a 360 dp screen a row with many tags keeps its chips on one line with "+N" and never pushes the ⋮ off; a row with no tags has no chip line and today's height. → Task 3 `row_manyTagsAtLargeFont_staysOneLineWithPlusN`, `row_withoutTags_keepsTodaysHeight`.

## Decisions this plan makes (the user may overrule at review)

- **No filters shows the whole library** (live, library order); the strip then shows only "Edit filters".
- **Now Playing chips sit below the artist line** (the title block), so title and artist stay together; spec §6 call 10 says "under the title".
- **The picker in Find tracks offers no "New tag" row and no "Manage tags" link** (a new tag carries no track, so it cannot match): `CustomTagPickerSheet.onCreateClick` becomes nullable.
- **The results header label is the count** ("12 tracks", `tracksCountLabel`) next to Shuffle and Play.
- **Delivery of chips:** a composition local (`LocalSongCustomTags`) provided once in `MainActivity`; `Song` is unchanged.

## Phase-entry check (observation 0027)

Screens reachable from Find tracks and what they change under it: ⋮ Edit tags (tags of a result change → results
re-filter live, RF4), its picker's Manage tags (a filter tag can be deleted or renamed, or an import can replace the
lists → filter IDs are pruned to known IDs on each successful lists emission, names re-read, RF3), Now Playing (nothing).
Find tracks holds no file copy and no baseline, only the filter (saved in `SavedStateHandle`).

---

### Task 0: Scroll baseline (controller)

- [ ] Run `ScrollBenchmark` on `pixel6Api31Frames` at `7f26949` twice:
  `./gradlew :baselineprofile:pixel6Api31FramesBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rolla.musicplayer.baselineprofile.ScrollBenchmark`.
  Record each run's `frameDurationCpuMs` P50/P90 in the ledger. (The seeded library has no `ROLLA_TAGS`, so this
  measures rows without tags, the spec §9 risk.)

### Task 1: Data — the song-to-tags map and `SongFinder`

**Files:**
- Modify: `core/model/src/main/kotlin/com/rolla/musicplayer/core/model/CustomTag.kt` (or a new `SongFilter.kt` beside it)
- Modify: `core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/CustomTagDao.kt`; `core/testing/.../FakeCustomTagDao.kt`
- Modify: `core/data/.../repository/CustomTagRepository.kt`, `CustomTagRepositoryImpl.kt`
- Create: `core/data/.../repository/SongFinder.kt` (interface, `SongFinderImpl`, the pure matcher); bind in the module that binds `SongRepository`
- Test: `CustomTagDaoTest` (androidTest), `CustomTagRepositoryImplTest`, `SongFinderImplTest`, `SongFilterMatchTest`

**Interfaces — Produces:**
```kotlin
// :core:model
data class SongCustomTags(val tags: List<CustomTag>, val subTags: List<CustomTag>) // known only, each in library order
data class SongFilter(
    val title: String = "", val artist: String = "", val album: String = "", val genre: String = "",
    val year: Int? = null, val tagIds: Set<Long> = emptySet(), val subTagIds: Set<Long> = emptySet(),
) { val isEmpty: Boolean } // blank text and null year and no IDs
// :core:database CustomTagDao
@Query("SELECT * FROM song_custom_tags") fun observeAllSongCustomTagLinks(): Flow<List<SongCustomTagCrossRef>>
// :core:data
fun CustomTagRepository.observeSongCustomTags(): Flow<Map<String, SongCustomTags>> // songs with ≥1 known tag only
interface SongFinder { fun observeMatches(filter: Flow<SongFilter>): Flow<List<Song>> }
internal fun SongFilter.matches(song: Song, genre: String?, tagIds: Set<Long>, subTagIds: Set<Long>): Boolean
```
`SongFinderImpl` combines `songDao.observeAllSongs()` (entities carry `genre`), `observeAllSongCustomTagLinks()` and the
filter flow, filters on the sort dispatcher, and returns library order exactly as `SongRepositoryImpl.observeSongs`
does (`sortedByLibraryName(SongTieBreak) { it.title }`). An empty filter returns the whole library.

**Matching rules (`matches`):** each text field is trimmed; blank = inactive; active = `contains(ignoreCase = true)`.
Title on `song.title`; album on `song.album`; artist on the raw name **or** `artistDisplayName(raw)`; genre on `genre`
(null never matches an active genre). Year: `song.year == filter.year`. Tags: `filter.tagIds ⊆ tagIds` and
`filter.subTagIds ⊆ subTagIds` (kinds separate: TAG 2 never satisfies SUBTAG 2).

- [ ] **Step 1: Failing tests.**
  - `SongFilterMatchTest`: `matches_trimsAndIgnoresCase` (" LOVE " matches "Lovesong", "" and "  " match everything);
    `matches_artistUsesDisplayName` ("unknown" matches a song whose raw artist is blank `""`; "artist x" matches raw "Artist X");
    `matches_yearExact` (1999 ≠ 19990 ≠ 2000; null year filter matches a song with null year);
    `matches_genreNullNeverMatchesActiveGenre`; `matches_allTagsRequired` (filter {2,3} vs song {2} false, {2,3,9} true);
    `matches_kindsAreSeparate` (filter tag 2, song only subtag 2 → false); `matches_everyFieldAnd` (title ok, year wrong → false).
  - `SongFinderImplTest` (fakes): `observeMatches_emptyFilter_wholeLibraryInLibraryOrder`;
    `observeMatches_reEmitsWhenFilterChanges`; `observeMatches_reEmitsWhenLinksChange` (song carries 2, filter {2};
    links change to {} → result drops it).
  - `CustomTagRepositoryImplTest`: `observeSongCustomTags_knownOnlyInLibraryOrder` (song links TAG 3 "Techno",
    TAG 2 "Acid", TAG 99 unknown, SUBTAG 1 "Hard" → tags [Acid, Techno], subTags [Hard]; a song with only 99 is absent);
    `observeSongCustomTags_renameReEmits`.
  - `CustomTagDaoTest` (device): `observeAllSongCustomTagLinks_emitsEveryLinkIncludingUnknown`.
- [ ] **Step 2:** run `./gradlew :core:data:testDebugUnitTest` → the new tests fail (unresolved or assertion).
- [ ] **Step 3:** implement the interfaces above.
- [ ] **Step 4:** `./gradlew :core:model:testDebugUnitTest :core:data:testDebugUnitTest :core:database:compileDebugAndroidTestKotlin` → green. The device run is Task 7's.

### Task 2: Kit — tag chips, the chip line and the removable chip (`:core:designsystem`)

**Files:** `theme/Dimens.kt`, `theme/Type.kt`, `theme/Color.kt`, new `component/TagChips.kt`, `ContrastTest.kt`,
the redesign spec §5.1/§5.2/§5.4 rows, a `KitPreviews.kt` entry. Tests: `TagChipsTest`, `ContrastTest`.

**Tokens (exact):** `tagChipHeight = 18.dp`, `tagChipHorizontalPadding = 6.dp`, `tagChipGap = 4.dp`,
`tagChipLineTopGap = 2.dp`, `tagChipBorderWidth = 1.dp`; `Typography.tagChipLabel = rollaStyle(11, FontWeight.Medium, 14)`;
colours `tagChipContainer` (= `surfaceContainerHigh`'s value), `tagChipLabel` (= `onSurface`), `subTagChipBorder`
(= `onSurfaceVariant`), `subTagChipLabel` (= `onSurface`), both palettes. `ContrastTest` rows: `tagChipLabel` on
`tagChipContainer`; `subTagChipLabel` on `surfaceContainer`, on `background` and on the `nowPlayingGradient` stops;
`subTagChipBorder` on `surfaceContainer` and on `background` (non-text 3:1). If a pairing fails, take the nearest
passing role and record it in the spec row.

**Interfaces — Produces:**
```kotlin
enum class TagChipStyle { Tonal, Outline }          // Tonal = tag, Outline = subtag
@Immutable data class TagChipData(val label: String, val style: TagChipStyle)
@Composable fun TagChip(label: String, style: TagChipStyle, modifier: Modifier = Modifier) // one line, ellipsis, CircleShape
@Composable fun TagChipLine(chips: List<TagChipData>, modifier: Modifier = Modifier, maxLines: Int = 1, centred: Boolean = false)
@Composable fun RemovableChip(label: String, removeLabel: String, onRemove: () -> Unit, modifier: Modifier = Modifier, style: TagChipStyle = TagChipStyle.Tonal)
```
**`TagChipLine` algorithm** (a custom `Layout`): measure each chip at its intrinsic width; fill up to `maxLines` lines
left to right with `tagChipGap` between chips and lines; when chips remain, the last line must also fit a Tonal "+N"
chip (N = chips not placed), so drop chips from the end of the last line until it does. If the first chip of a line is
wider than the line on its own, measure it again with the remaining width (it ellipsizes). Lines are start-aligned, or
centred when `centred`. Zero chips = zero size. **`RemovableChip`:** the `LabelChip` look (`chipLabel`, `chipHeight`)
with a trailing `RollaIcons.Close`, the whole chip one button (`Role.Button`, `contentDescription = removeLabel`),
`minimumInteractiveComponentSize()`; Outline style draws the subtag border.

- [ ] **Step 1: Failing tests** (`TagChipsTest`, Robolectric, width 200 dp unless named):
  `line_allFit_noPlusN`; `line_overflow_showsPlusNWithHiddenCount` (8 chips "Tag 1".."Tag 8" at 120 dp → "+N" text
  equals 8 − visible count); `line_twoLines_wrapsBeforePlusN` (`maxLines = 2`); `line_longFirstChip_ellipsizesInsideWidth`
  (one 60-char label: chip right edge ≤ line right edge); `line_empty_hasZeroHeight`; `line_centred_centresEachLine`;
  `chip_outlineAndTonalDiffer` (pixel: Tonal fills, Outline does not); `removable_click_callsOnRemoveOnce_andAnnounces`.
  `ContrastTest`: the new rows.
- [ ] **Step 2:** `./gradlew :core:designsystem:testDebugUnitTest` → fail.
- [ ] **Step 3:** implement tokens, spec rows, components.
- [ ] **Step 4:** same command → green; `:core:designsystem:detekt` clean; `checkNoMaterialIcons` passes.

### Task 3: Chips in every track list (`:core:ui`, `:app`)

**Files:** new `core/ui/.../customtags/SongCustomTagChips.kt`; `core/ui/.../SongListItem.kt`;
`core/ui/.../customtags/CustomTagPickerSheet.kt` (nullable create); new `app/.../customtags/SongCustomTagsViewModel.kt`;
`app/.../MainActivity.kt`. Tests: `SongListItemTest`, `CustomTagPickerSheetTest`.

**Interfaces — Produces:**
```kotlin
// :core:ui
@Stable fun interface SongCustomTagsLookup { fun tagsFor(songId: String): SongCustomTags? }
val LocalSongCustomTags = staticCompositionLocalOf { SongCustomTagsLookup { null } }
const val SONG_TAG_CHIPS_TAG = "song_tag_chips" // testTag on SongCustomTagChips
@Composable fun SongCustomTagChips(tags: SongCustomTags, modifier: Modifier = Modifier, maxLines: Int = 1, centred: Boolean = false)
// CustomTagPickerSheet: onCreateClick: ((CustomTagKind, String) -> Unit)? — null hides the "New tag/subtag" row
// :app
@HiltViewModel class SongCustomTagsViewModel(repository: CustomTagRepository) { val tags: StateFlow<Map<String, SongCustomTags>> }
```
`SongCustomTagChips` maps tags → Tonal, subtags → Outline into one `TagChipLine`, and sets
`contentDescription = "Tags: A, B. Subtags: C."` (omit an empty part) with the chips themselves cleared from semantics.
`SongListItem` reads `LocalSongCustomTags.current.tagsFor(song.id)` and, when non-null, puts `SongCustomTagChips`
under the artist line with `tagChipLineTopGap`; no chip line and no gap otherwise. `MainActivity` collects the
ViewModel's map (`collectAsStateWithLifecycle`) and provides a lookup reading that `State` at the content root, so a
map change recomposes only rows that read it. The lists' error path in the repository yields an empty map (chips
vanish; nothing else changes).

- [ ] **Step 1: Failing tests.** `SongListItemTest`: `row_withoutTags_keepsTodaysHeight` (lookup returns null → no node tagged
  `SONG_TAG_CHIPS_TAG`, and at fontScale 2 the row height equals the value measured on today's code, recorded in the test); `row_withTags_showsChipsUnderArtist` (lookup gives Techno + Hard → both texts
  displayed, chip top ≥ artist bottom); `row_withTags_keepsListRowHeightAtDefaultFont`;
  `row_manyTagsAtLargeFont_staysOneLineWithPlusN` (fontScale 1.5, width 360 dp, 10 tags → one chip line, "+" chip
  shown, ⋮ button fully inside the row); `row_tagsDescription_readsTagsThenSubtags`.
  `CustomTagPickerSheetTest`: `picker_nullCreate_hidesNewRow`.
- [ ] **Step 2:** `./gradlew :core:ui:testDebugUnitTest` → fail.
- [ ] **Step 3:** implement; wire `:app`.
- [ ] **Step 4:** `./gradlew :core:ui:testDebugUnitTest :app:testDebugUnitTest :app:hiltJavaCompileDebug` → green.

### Task 4: Chips on Now Playing (`:feature:player`)

**Files:** `feature/player/.../NowPlayingScreen.kt`. Test: `NowPlayingScreenTest`.
Below `NowPlayingTitleArtist`, inside the same faded metadata column, `SongCustomTagChips(maxLines = 2, centred = true)`
for the current song from `LocalSongCustomTags`, with `tagChipLineTopGap` above; nothing when the song has none.
The weighted spacer absorbs the height; at large font the column scrolls (existing behaviour).

- [ ] **Step 1: Failing tests:** `nowPlaying_showsTagChipsUnderArtist`; `nowPlaying_noTags_noChipLine` (no node tagged
  `SONG_TAG_CHIPS_TAG`); `nowPlaying_manyTags_atMostTwoLinesWithPlusN`.
- [ ] **Step 2–4:** `./gradlew :feature:player:testDebugUnitTest` fail → implement → green.

### Task 5: Find tracks ViewModel (`:feature:search`, package `findtracks`)

**Files:** create `FindTracksUiState.kt`, `FindTracksViewModel.kt`; test `FindTracksViewModelTest`.

**Interfaces — Produces:**
```kotlin
data class FindTracksUiState(
    val title: String, val artist: String, val album: String, val genre: String, val year: String,
    val tags: List<CustomTag>, val subTags: List<CustomTag>, val filter: SongFilter,
    val results: List<Song>?, val filtersExpanded: Boolean, val picker: CustomTagPickerState?,
)
class FindTracksViewModel(savedStateHandle, songFinder: SongFinder, customTagRepository, playbackController) {
    val uiState: StateFlow<FindTracksUiState>
    fun onTitleChange(value: String); fun onArtistChange(value: String); fun onAlbumChange(value: String)
    fun onGenreChange(value: String); fun onYearChange(value: String)     // digits only, at most 4; else ignored
    fun onRemoveTextFilter(field: FindTracksField); fun onRemoveCustomTag(kind: CustomTagKind, id: Long)
    fun onToggleFilters(); fun onOpenPicker(kind: CustomTagKind); fun onPickerPageChange(kind: CustomTagKind)
    fun onPickerQueryChange(kind: CustomTagKind, query: String); fun onPickerToggle(kind: CustomTagKind, id: Long)
    fun onPickerDone(); fun play(song: Song); fun playAll(); fun shuffleAll()
}
enum class FindTracksField { TITLE, ARTIST, ALBUM, GENRE, YEAR }
```
The five texts, the two ID sets and `filtersExpanded` live in `SavedStateHandle`; `filter` is derived (`year =
toIntOrNull()`). `filtersExpanded` starts true when the filter is empty. Lists come from `observeCustomTags(TAG/SUBTAG)`
with a `catch` fallback to empty that **sets a flag and never prunes**; every other lists emission prunes the filter's
IDs (and an open picker's ticks) to known IDs. Picker ticks apply to the filter on Done. `play(song)` →
`playbackController.playFrom(results, song)`; `playAll` / `shuffleAll` over `results` like `TracksViewModel`.

- [ ] **Step 1: Failing tests:** `initial_emptyFilter_showsWholeLibrary_expanded`; `typing_updatesResultsLive`;
  `year_rejectsNonDigitsAndFiveDigits`; `removeTextFilter_clearsThatFieldOnly`; `picker_toggleAppliesOnlyOnDone`
  (toggles leave `filter` unchanged until Done; reopening shows the applied ticks); `listsChange_prunesDeletedFilterTag`; `listsFail_keepsFilterTags`;
  `restore_fromSavedStateHandle_rebuildsFilter`; `play_usesWholeResultAsQueue` (fake controller receives the results
  list and the tapped song); `shuffleAll_emptyResults_isNoOp`.
- [ ] **Step 2–4:** `./gradlew :feature:search:testDebugUnitTest` fail → implement → green.

### Task 6: Find tracks screen, route and the Tracks header button

**Files:** create `feature/search/.../findtracks/FindTracksScreen.kt` (`FindTracksRoute` + stateless
`FindTracksScreen`); modify `feature/library/.../TracksTab.kt` (Search button), the Home wiring that passes
`onFindTracksClick`, `app/.../navigation/Routes.kt` (`@Serializable data object FindTracks : Route`),
`MiniPlayerVisibility.kt` (`allRoutes` + 1; Find tracks **shows** the mini-player), `MainActivity.kt`
(`internal fun NavGraphBuilder.findTracksDestination(navController, provideViewModel = { hiltViewModel() })`).
Tests: `FindTracksScreenTest`, `TracksTabTest`, `MiniPlayerVisibilityTest`, `FindTracksDestinationTest` (app).

**Screen (top to bottom, one `LazyColumn`, `imePadding`):** `OneUiDetailTopBar("Find tracks")`; the active-filter strip
(`FlowRow` of `RemovableChip`: "Title: love", "Artist: …", "Album: …", "Genre: …", "Year: 1999", then each tag
(Tonal) and subtag (Outline) by name; `removeLabel = "Remove filter <label>"`); an "Edit filters" / "Hide filters"
tonal `OneUiPillButton`; when expanded, a card with `OneUiTextField`s Title, Artist, Album, Genre, Year (number
keyboard) and `CustomTagRows` opening `CustomTagPickerSheet(onCreateClick = null, onManageClick = null)`; then the
results panel: `SortHeader(label = tracksCountLabel(n))` with Shuffle ("Shuffle these tracks") and Play ("Play these
tracks") circle buttons, `SongListItem` rows (tap = `play`, ⋮ = `SongOptionsHost` with `currentSongs = results` and
Edit tags via `openTagEditor`), "No tracks match these filters" when empty; bottom padding `homeListBottomPadding()`.
**Tracks header:** a third `CircleIconButton(RollaIcons.Search, "Find tracks")` before Shuffle, always enabled.

- [ ] **Step 1: Failing tests:** `FindTracksScreenTest`: `strip_showsEachActiveFilter_andRemoves`;
  `editFilters_togglesFieldsCard`; `results_tapPlays_headerShowsCount`; `empty_showsNoMatchMessage`;
  `largeFont_stripWraps_noOverflow` (1.5×, 360 dp, six filters: every chip `hasVisualOverflow == false`).
  `TracksTabTest`: `header_findTracksButton_calls` (and the button is present with an empty library).
  `MiniPlayerVisibilityTest`: `miniPlayer_shownOnFindTracks` + the route count. `FindTracksDestinationTest`: the Tracks
  button navigates to `FindTracks` (`hasRoute<FindTracks>()`).
- [ ] **Step 2–4:** `./gradlew :feature:search:testDebugUnitTest :feature:library:testDebugUnitTest :app:testDebugUnitTest :app:hiltJavaCompileDebug` fail → implement → green.

### Task 7: Gate, benchmark, device walk and vault (controller)

- [ ] **Step 1:** `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleRelease checkReleaseManifestNoInternet`
  → BUILD SUCCESSFUL; `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest` → all pass.
- [ ] **Step 2:** `ScrollBenchmark` twice on `pixel6Api31Frames` (Task 0's command). Pass: each run's P90
  `frameDurationCpuMs` ≤ the Task 0 median P90 × 1.05; a miss on both runs goes to a system trace before any ruling.
- [ ] **Step 3:** `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark`. The user's walk (read-only for files):
  1. Tracks: rows of tagged tracks show chips (tags filled, subtags outlined), untagged rows look as before; at the
     largest font a many-tag row shows "+N".
  2. Play a tagged track: Now Playing shows its chips under the title block, centred.
  3. Tracks header → Search → Find tracks: type "techno"-free text filters, tick Techno + a subtag; the results update
     live; ✕ on a chip drops it; Year rejects letters.
  4. Tap a result: playback starts and the queue is the whole result in order. Shuffle and Play work.
  5. With Techno in the filter: ⋮ on a result → Edit tags → Tags → Manage tags is reachable; rename Techno → back
     twice: the filter chip shows the new name. (No delete and no save needed.)
- [ ] **Step 4:** Vault: [[Custom tags]] (progress, walk, feature complete), [[core-data]], [[core-database]],
  [[core-designsystem]], [[core-ui]], [[feature-search]], [[feature-library]], [[feature-player]], [[app]], [[Search]],
  [[Timeline]], [[Backlog]]; CLAUDE.md status and feature list; ledger rulings archived; wiki ingest of this plan;
  report ready to commit.

---

## Self-review artefacts

### Test-control table (one mutation per test)

| Test | Mutation | Mutated result on the literal input |
|---|---|---|
| matches_trimsAndIgnoresCase | drop `trim()` | " LOVE " not in "Lovesong" → false |
| matches_artistUsesDisplayName | match the raw name only | raw "" does not contain "unknown" → false |
| matches_yearExact | compare `toString().contains` | 19990 contains 1999 → true |
| matches_genreNullNeverMatchesActiveGenre | `genre == null \|\| genre.contains(f, true)` | null genre, filter "rock" → true |
| matches_allTagsRequired | `any` instead of `all` | {2} vs {2,3} → true |
| matches_kindsAreSeparate | check tag filter against tag ∪ subtag IDs | subtag 2 satisfies tag 2 → true |
| matches_everyFieldAnd | `\|\|` between fields | title ok → true |
| observeMatches_emptyFilter_wholeLibraryInLibraryOrder | skip the sort | Room order (title NOCASE) ≠ library order for "Ä…"/"The …" fixture |
| observeMatches_reEmitsWhenFilterChanges | `filter.first()` once | second filter never applied |
| observeMatches_reEmitsWhenLinksChange | read links once (`first()`) | dropped song stays |
| observeSongCustomTags_knownOnlyInLibraryOrder | include unknown IDs as "#99" | 99 appears / song with only 99 present |
| observeSongCustomTags_renameReEmits | lists read once | old name stays |
| observeAllSongCustomTagLinks_emitsEveryLinkIncludingUnknown | `INNER JOIN custom_tags` | link 99 missing |
| line_allFit_noPlusN | always add "+0" | "+0" found |
| line_overflow_showsPlusNWithHiddenCount | N = total | text mismatch |
| line_twoLines_wrapsBeforePlusN | ignore `maxLines` (1) | second line empty |
| line_longFirstChip_ellipsizesInsideWidth | no re-measure | chip right edge > line |
| line_empty_hasZeroHeight | reserve chip height | height 18 dp |
| line_centred_centresEachLine | ignore `centred` | left offset 0 |
| chip_outlineAndTonalDiffer | Outline uses the Tonal fill | pixels equal |
| removable_click_callsOnRemoveOnce_andAnnounces | no `contentDescription` | node not found by label |
| row_withoutTags_keepsTodaysHeight | always compose an empty chip line (gap + `tagChipHeight`) | node exists; height + 20 dp |
| row_withTags_showsChipsUnderArtist | never read the local | texts missing |
| row_withTags_keepsListRowHeightAtDefaultFont | chip height 32 dp | 40 + 2 + 32 = 74 dp > 70 |
| row_manyTagsAtLargeFont_staysOneLineWithPlusN | `maxLines = 2` | two lines |
| row_tagsDescription_readsTagsThenSubtags | subtags first | text mismatch |
| picker_nullCreate_hidesNewRow | show row when null | "New tag" found |
| nowPlaying_showsTagChipsUnderArtist | omit the line | texts missing |
| nowPlaying_noTags_noChipLine | always compose the line | node exists |
| nowPlaying_manyTags_atMostTwoLinesWithPlusN | `maxLines = 1` | one line |
| initial_emptyFilter_showsWholeLibrary_expanded | start collapsed | flag false |
| typing_updatesResultsLive | debounce until Done | results unchanged |
| year_rejectsNonDigitsAndFiveDigits | accept any | "19a9" stored |
| removeTextFilter_clearsThatFieldOnly | clear all | artist cleared |
| picker_toggleAppliesOnlyOnDone | apply on every toggle | filter changes before Done |
| listsChange_prunesDeletedFilterTag | no prune | 2 stays |
| listsFail_keepsFilterTags | prune on fallback | filter emptied |
| restore_fromSavedStateHandle_rebuildsFilter | keep the filter in a field | lost |
| play_usesWholeResultAsQueue | play the single song | queue size 1 |
| shuffleAll_emptyResults_isNoOp | call anyway | controller called |
| strip_showsEachActiveFilter_andRemoves | omit Year chip | "Year: 1999" missing |
| editFilters_togglesFieldsCard | always expanded | fields visible after hide |
| results_tapPlays_headerShowsCount | static label | "2 tracks" missing |
| empty_showsNoMatchMessage | show nothing | message missing |
| largeFont_stripWraps_noOverflow | `Row` instead of `FlowRow` | overflow true |
| header_findTracksButton_calls | not wired | 0 calls |
| miniPlayer_shownOnFindTracks | add to `routesWithoutMiniPlayer` | hidden |
| FindTracksDestinationTest | navigate to `Search()` | route mismatch |

(Each row was evaluated on the test's literal input while writing (superpowers-extras check 2d); implementers may substitute an equivalent
mutation, proving equivalence, per superpowers-extras SDD rule 2.)

### Per-screen concerns

| Surface | Status bar | Navigation bar | Keyboard | TalkBack | Reduced motion |
|---|---|---|---|---|---|
| Find tracks | `OneUiDetailTopBar` | `homeListBottomPadding()` (pill shown) | `imePadding`, Year number keyboard | chips "Remove filter …"; results rows as everywhere; header buttons labelled | "Edit filters" expand: kit spring or snap |
| Track rows | — | — | — | row reads title, artist, "Tags: …. Subtags: …." | — |
| Now Playing chips | existing | existing | — | one description for the line | fades with the metadata (existing) |
| Tracks header button | — | — | — | "Find tracks" | — |

### Claims about existing code (cited)

- `SongListItem` callers: `TracksTab`, `AlbumDetailScreen`, `ArtistDetailScreen`, `FolderDetailScreen`,
  `FavouritesTab`, `PlaylistDetailScreen`, `SearchScreen` (grep 2026-11-12); all render under `MainActivity`'s content.
- `SongListItem` row: `heightIn(min = RollaDimens.listRowHeight)` (70 dp); title `songTitle` 22 sp line, artist
  `artistName` 18 sp line: 40 dp of text, so 2 + 18 dp of chips fits at 1×.
- `CustomTagPickerSheet(state, onPageChange, onQueryChange, onToggle, onCreateClick, onDone, modifier, onManageClick)`;
  `onCreateClick` callers: the two editors (`TagEditorScreen`, `BatchTagEditorScreen`).
- `TracksViewModel.play/playAll/shuffleAll` use `PlaybackController.playFrom/playAllInOrder/shuffleAll`.
- `SongRepositoryImpl.observeSongs` sorts with `sortedByLibraryName(SongTieBreak) { it.title }` on `sortDispatcher`.
- `SongEntity.genre` (v6) exists; `Song` has no genre.
- `NowPlayingTitleArtist` sits in a faded metadata column followed by `Spacer(Modifier.weight(1f))` inside a
  `verticalScroll` column (`NowPlayingScreen.kt` ~302–329).
- `RollaIcons` has `Search` and `Close`; `tracksCountLabel` is in `:core:ui`.
- `:feature:search` depends on `:core:media`, `:core:data`, `:core:ui`, `:core:designsystem`, `:core:model`.

### Earlier rulings and hand-offs checked

- Phase 2 hand-off "LabelChip never ellipsizes; list rows need one line plus +N and a distinct subtag chip": Task 2.
- Phase 3: writes none here; the Manage-tags entry from Find tracks' editor is handled by RF3.
- Observation 0028: the lists' fallback emission is named in Tasks 3 and 5 (chips vanish; filters never pruned).
- Observation 0026: no step writes user data; each Review Focus line names its owning test above.
