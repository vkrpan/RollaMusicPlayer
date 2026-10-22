# One UI redesign, Phase 6b: Search, tag editors, info screens and the permission screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Search, the two tag editors and the permission prompt are rebuilt on the One UI kit, and every screen still wrapped in `PushedScreen` pads for the pill on its own, so `PushedScreen` is deleted. The phase also closes the 6b carry-overs: the dead ⋮ on Search's track rows, Home's dead + and selection bar while access is denied, the `HomeTabPage` extraction, one `openTagEditor` helper, and the unused Material icons dependency.

**Architecture:**
- **Kit (`:core:designsystem`).** Three additions:
  - `OneUiTextField` gains keyboard options, an error state and supporting text, so the tag editors can drop `OutlinedTextField`.
  - New `OneUiSearchField` (a pill with a search glyph and a clear button) and `OneUiPillButton` (filled primary or tonal, with a loading state).
  - `OneUiBottomSheet` gains `skipPartiallyExpanded`, and the queue sheet opens fully expanded (user decision, 2026-10-21).
- **Permission screen (`:core:permissions` → `:app`).** The gate's status is hoisted into a `MediaPermissionState`, so Home can hide the + and the selection bar while access is denied. The prompt sits in a `ContentPanel`, with a placeholder glyph and a filled pill.
- **Search (`:core:datastore`, `:core:data`, `:feature:search`).**
  - The screen drops its Scaffold: a search bar, then a `ContentPanel` whose lists pad for the pill.
  - Track rows open the shared options sheet.
  - Each recent search has its own remove button, backed by a new `remove` in the data source and repository.
- **Tag editors (`:feature:tageditor`).** `OneUiDetailTopBar`, cards on `surfaceContainer`, `OneUiTextField`s, pill buttons. The batch editor uses `OneUiSwitch` apply toggles. Copy says "tracks".
- **`:app`.**
  - The eight remaining `PushedScreen` wrappers go, and `PushedScreen` is deleted.
  - One `openTagEditor` helper replaces the six copied navigation bodies.
  - Search registers through a `NavGraphBuilder` extension, the 6a pattern, so a JVM test can drive it.

**Tech Stack:**
- Kotlin 2.2.21.
- Compose BOM 2024.09.03 (Foundation 1.7.3, M3 1.3.0).
- Hilt and DataStore.
- JUnit4, MockK and Turbine.
- Robolectric 4.13, through the `rolla.android.robolectric` convention plugin, with NATIVE graphics. `:core:testing`'s `PixelCapture` (`drawRoot`, `drawLatestDialog`, `captureWindow`, `assertColorNear`) for visual assertions.

**Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md).
- §2 copy and app title; §5 tokens; §6 kit; §7.2 Home and the permission gate; §7.3 the pill inset and its visibility; §7.4 insets; §8.6 info screens; §8.7 Search, the tag editors and sheets (binding for this phase); §12 no dead controls; §15 tests and gates.
- §17 row 6 exit criterion: "No Material icon or hardcoded value outside the documented exceptions."

The roadmap ([`2026-10-06-oneui-redesign-roadmap.md`](2026-10-06-oneui-redesign-roadmap.md)) puts Search, the tag editors, About/Licenses/Privacy and the permission screen in 6b, plus Home's permission-state carry-over and the `HomeTabPage` extraction. The 6a carry-overs come from `.superpowers/sdd/2026-10-18-oneui-phase6a-details-sheets/phase-6a-final-review.md` ("Phase 6b" line) and the 6a ledger's follow-ups.

## Global Constraints

- **Platform:** minSdk 24, targetSdk 34, compileSdk 36.
- **Copy:** UI copy says "tracks", never "songs" (spec §2; the model stays `Song`). The app is called "Rolla Music" in UI copy, never "RollaMusicPlayer" (spec §2). British spelling throughout.
- **Blue text:** blue text uses `accentText`, never `primary`. Text buttons go through `OneUiTextButton`.
- **Red text:** error and destructive text uses `destructiveText`, never `error` (6a ruling P2).
- **Icons:** icons come from `RollaIcons` only. After Task 6, no `androidx.compose.material.icons` import exists anywhere, and detekt forbids one.
- **No hardcoded visuals:** no hardcoded colours, type, shapes or dimensions outside `:core:designsystem`. New values become tokens first. A private `val … = N.dp` in a touched feature, `:core:ui`, `:core:permissions` or `:app` file is a breach.
- **No dead controls:** every visible button does something real (spec §12).
- **Offline only:** no network APIs or dependencies.
- **Sheets, dialogs and menus:** never call `ModalBottomSheet`, `AlertDialog`, `TextButton`, `TextField`, `OutlinedTextField`, `Button`, `OutlinedButton`, `Checkbox` or `DropdownMenu` directly outside `:core:designsystem`.
- **Tests:** tests the phase touches are updated, never deleted to go green. A test whose subject is deleted goes with it (ruling 9).
- **Mutation-proof policy.**
  - Every new test names the mutation that turns it RED. The implementer applies it, sees RED, and reverts byte-exact.
  - A mutant counts as equivalent only if *no observable output changes*. If the output changes but the test can't see it, the test is blind: move its probe.
  - A named mutation is a hypothesis about the framework. It must be valid usage: a mutation that throws inside Compose is RED for the wrong reason. If it proves equivalent, show the evidence and substitute the closest fail-able mutation.
  - **Every test carries its own control** (observation 0016). Each task's test table names, per test, the trivial stub and the assertion **inside that test** that fails under it. An absence test first shows the thing present, then absent, in one `setContent` with a state flip. A control in a sibling test does not count.
  - **A test for a visible outcome observes the rendering** (observation 0017). Colours, shapes, clipping and positions are asserted with `PixelCapture` or bounds, not with "is displayed". Robolectric's root here is 584 dp tall, so "is displayed" rarely fails.
  - A RED quotes the Gradle `BUILD FAILED` line and the test's own assertion message. "No tests found" or a compile error is not a RED.
  - Fixtures must separate the quantities a test distinguishes.
- **Robolectric:**
  - In library-module tests, targetSdk equals minSdk (24). Set the target SDK in any test that depends on platform behaviour gated by it.
  - Text geometry needs `@GraphicsMode(NATIVE)`.
  - Dialog tests with a text field may need the `@LooperMode(LEGACY)` workaround, with a comment.
- **Agents never** run `git commit`, `git add`, `git stash`, `git checkout`, `git reset` or `git clean`. Never use `sed -i` (Spotless enforces CRLF; use the editor). Never write Kotlin through shell heredocs.
- **New files:** an agent with no Write tool creates a one-line placeholder with `printf`, then fills it with Edit.
- **Spotless:** run the root `./gradlew spotlessApply` after edits, and **before** taking any mutation backup.
- **Plan code** was never compiled. Implementers may make mechanical fixes (imports, an `@OptIn`, adapting to an existing helper's real name) and must report each one. They may not change behaviour, a test's assertions or its fixture.
- `:core:testing` stays `testImplementation` only.

## Rulings (decided at planning; the spec is amended in Task 7)

1. **Kit additions** (spec §6 is amended in Task 7 with these signatures):
   - `OneUiTextField(…, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, isError: Boolean = false, supportingText: String? = null)`. Supporting text is drawn in `destructiveText` when `isError`, otherwise in `onSurfaceVariant`.
   - `OneUiSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSearch: () -> Unit = {})`:
     - a pill (`CircleShape`), `minTouchTarget` tall, `surfaceContainerHigh`;
     - a leading `RollaIcons.Search` glyph in `onSurfaceVariant`, and a placeholder in `onSurfaceVariant`;
     - a trailing `RollaIcons.Close` `OneUiIconButton` ("Clear search"), shown only while the query is not empty, which calls `onQueryChange("")`;
     - an `accentText` cursor, no indicator, and IME action Search, which calls `onSearch`.
   - `OneUiPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tonal: Boolean = false, loading: Boolean = false, loadingDescription: String? = null)`:
     - `CircleShape`, at least `minTouchTarget` tall, label in `labelLarge`;
     - filled: `primary` container and `onPrimary` label; tonal: `surfaceContainerHigh` container and `onSurface` label;
     - `loading` swaps the label for a progress ring (`settingsProgressSize`, `settingsProgressStroke`, in the label colour), ignores taps, and sets the button's content description to `loadingDescription`.
   - `OneUiBottomSheet(…, skipPartiallyExpanded: Boolean = false)`, passed to `rememberModalBottomSheetState`. The queue sheet passes `true` (user decision, 2026-10-21); every other sheet keeps the default.
2. **Contrast pairings, computed at planning** (observation 0018; WCAG relative luminance):

   | Pairing | Dark | Light |
   |---|---|---|
   | `onPrimary` on `primary` (filled pill) | 4.51 | 4.51 |
   | `onSurface` on `surfaceContainerHigh` (tonal pill, search text) | 13.41 | 15.41 |
   | `onSurfaceVariant` on `surfaceContainerHigh` (search placeholder) | 4.95 | 4.82 |
   | `destructiveText` on `surfaceContainer` (field error text on a card) | 6.05 | 6.46 |
   | `onSurfaceVariant` on `surfaceContainer` (captions, recent rows) | 6.45 | 5.90 |
   | `accentText` on `surfaceContainer` ("Clear") | 6.12 | 5.57 |

   All clear 4.5:1. Task 1 adds the two new pairings (search placeholder, field error on a card) to `ContrastTest`.
3. **Search layout and insets** (spec §7.3, §7.4, §8.7):
   - No Scaffold. A `Column` with `background` and `windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))`.
   - Inside it: a search bar row (`statusBarsPadding`, `headerHeight` tall), then `panelTopGap`, then a `ContentPanel`.
     - The bar holds a `RollaIcons.ChevronBack` `OneUiIconButton` ("Navigate up") at `topBarEdge`, then `OneUiSearchField` (weight 1), ending `screenEdge` from the edge.
   - Every list in the panel uses bottom `contentPadding = homeListBottomPadding()`.
   - With the keyboard closed, the list pads by the pill plus the nav bar, and the outer padding is 0. With it open, the pill inset is 0 (spec §7.3), and the outer padding (keyboard minus nav bar) plus the list's nav-bar padding add up to exactly the keyboard. Each inset has one owner (spec §7.4).
   - Robolectric reports no IME inset, so the keyboard half is checked on the device in Task 8 (stated deviation).
4. **Search content:**
   - **Results.** Section headers "Tracks", "Albums" and "Artists" (`sectionHeader`, `onSurfaceVariant`, starting at `listThumbStart`, `singleLineRowHeight` tall). Then `SongListItem` / `AlbumRow` / `ArtistRow` rows with their default end insets.
   - **Options sheet.** Each track row's ⋮ opens `SongOptionsHost`, with `currentSongs = results.songs`: Add to playlist and Edit tags.
   - **Unchanged behaviour.**
     - A track tap still plays the whole results list from that track (Phase 3 gate decision 5).
     - Artists stay keyed by name (`searchArtistKey`, Phase 4a ruling 12).
     - The field still takes focus on open.
   - **Loading** renders nothing: no spinner flash, the 6a §8.7 rule.
   - **Idle with recents.**
     - A header row: "Recent searches" (`sectionHeader`) and a `OneUiTextButton("Clear")` (content description "Clear recent searches").
     - Then one row per query, `singleLineRowHeight` tall:
       - a `RollaIcons.Search` glyph (`onSurfaceVariant`), centred in a `listThumb` box at `listThumbStart`;
       - the query in `songTitle` at `listTextStart`, one line, ellipsized;
       - a trailing `RollaIcons.Close` `OneUiIconButton` in `onSurfaceVariant` ("Remove <query> from recent searches").
     - The row body keeps "Search again for <query>".
   - **Idle with no recents, and no results.** Both keep their copy and render in `emptyState` / `onSurfaceVariant` inside the panel.
5. **Recent-search removal** matches the query exactly, as `record` dedupes exact queries. `RecentSearchesDataSource.remove(query: String)`, `SearchRepository.removeRecentSearch(query: String)`, and a pure `internal fun withoutRecentSearch(current: List<String>, query: String): List<String>` next to `withRecordedSearch`.
6. **Tag editors** (spec §8.7: fields on `surfaceContainer` cards, restyled fields, primary pills, `OneUiSwitch` toggles):
   - Top bar: `OneUiDetailTopBar("Edit tags")`; batch: `OneUiDetailTopBar("Edit ${tracksCountLabel(n)}")`.
   - Body: one vertical scroll. Cards are `surfaceContainer`, `large` shape, inset `settingsCardInset`, `settingsRowPadding` inside, `settingsSectionGap` apart.
   - Single editor header card:
     - `ArtworkThumbnail(artworkUri, contentDescription = null, shape = extraLarge, large = true, placeholderGlyphSize = npArtGlyph)` at `detailArtworkSize`;
     - a tonal `OneUiPillButton("Change artwork")`;
     - title (`songTitle`) and artist (`artistName`), one line each.
   - Fields: `OneUiTextField`, with Year and Track number still side by side. Errors go through `isError` and `supportingText`.
   - Buttons: Cancel as a tonal pill and Save as a filled pill, `loading = isSaving`, `loadingDescription = "Saving"`. The error state's "Back" becomes a tonal pill.
   - Batch rows: the field (weight 1, enabled only while applied and not saving), then a `OneUiSwitch(checked = applied, onCheckedChange = null)`.
     - The switch sits inside a `minTouchTarget` box with `Modifier.toggleable(role = Role.Switch)`.
     - Content description: "Apply <label> to every selected track".
   - Batch: the "Editing N songs" line is dropped, because the top bar now carries the count. The progress bar stays.
   - The batch caption becomes "Only the fields switched on are written to every selected track."
   - Loading keeps its spinner: reading a file's tags is IO, not a list flash.
7. **Copy changes** (every hit of the grep `'"[^"]*\(song\|RollaMusicPlayer\)[^"]*"'` over the 6b files, run at planning):

   | File | Old | New |
   |---|---|---|
   | `SearchScreen.kt` | `"Songs"` | `"Tracks"` |
   | `BatchTagEditorScreen.kt` | `"Edit $songCount songs"` | `"Edit ${tracksCountLabel(songCount)}"` |
   | `BatchTagEditorScreen.kt` | `"Editing ${…} songs"` | removed (ruling 6) |
   | `BatchTagEditorScreen.kt` | `"Only checked fields will be written to all selected songs."` | `"Only the fields switched on are written to every selected track."` |
   | `BatchTagEditorScreen.kt` | `"Apply $label to all selected songs"` | `"Apply $label to every selected track"` |
   | `BatchTagEditorViewModel.kt` | `"None of the selected songs could be found."` | `"None of the selected tracks could be found."` |
   | `BatchTagEditorViewModel.kt` | `"Edited ${…} of $total songs."` | `"Edited ${…} of $total tracks."` |
   | `TagEditorViewModel.kt` | `"Song not found."` | `"Track not found."` |
   | `TagEditorScreen.kt` | `"Couldn't load this song's tags."` | `"Couldn't load this track's tags."` |
   | `AboutScreen.kt` | `"RollaMusicPlayer"` | `"Rolla Music"` |
   | `PrivacyScreen.kt` | `"RollaMusicPlayer is fully offline. …"` | `"Rolla Music is fully offline. …"` |
   | `MediaPermissionGate.kt` | `"RollaMusicPlayer needs access …"`, `"… so RollaMusicPlayer can load your music."` | `"Rolla Music needs access …"`, `"… so Rolla Music can load your music."` |

   The VM tests that assert the old strings (BatchTagEditorViewModelTest: 3, TagEditorViewModelTest: 1) are updated to the new ones.
8. **Permission state is hoisted** (the Phase 2 concern (b) carry-over):
   - **The problem.** After a restore with access denied, Home's + (when settled on Playlists) opens a dialog that `PlaylistsTab` never composes, which makes it a dead control. A restored selection likewise shows a selection bar over hidden rows.
   - **New API in `:core:permissions`.**
     - `@Stable class MediaPermissionState` with `val status: MediaPermissionStatus` and `val isGranted: Boolean`.
     - `@Composable fun rememberMediaPermissionState(): MediaPermissionState` owns the launcher, the resume observer and the status.
     - `@Composable fun MediaPermissionGate(state: MediaPermissionState, onGranted: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit)` replaces today's overload. Home is its only caller, by the grep `MediaPermissionGate` over `--include=*.kt`.
   - **Home's changes.** `HomeScreen` gains `accessGranted: Boolean = true`.
     - The + shows only while access is granted and the pager has settled on Playlists.
     - The selection bar and its BackHandler run only while access is granted.
     - `LaunchedEffect(accessGranted)` clears the selection when access is lost.
   - **The prompt.** It renders inside a `ContentPanel` (`:core:permissions` gains `implementation(project(":core:designsystem"))`):
     - `ArtworkPlaceholder(large = true, glyphSize = RollaDimens.npArtGlyph)` at `featureCardSize` with the `featureCard` shape;
     - the message in `bodyLarge` `onSurface`, centred;
     - a filled `OneUiPillButton`.
   - **Copy** per ruling 7. The button labels stay.
9. **`PushedScreen` is deleted** (its own KDoc: "Phase 6b moves the remaining wrapped screens the same way"). The grep `'PushedScreen {'` in `MainActivity.kt` found 8 wrappers, all removed: Equaliser, Search, TagEditor, BatchTagEditor, Settings, About, Licenses and Privacy.
   - Search pads itself (ruling 3).
   - The other seven hide the pill (`MiniPlayerVisibility.routesWithoutMiniPlayer`), so `LocalMiniPlayerInset` is 0 there and the wrapper was a no-op. Their Scaffolds keep owning the nav-bar inset.
   - `PushedScreenTest` is deleted with its subject. `SearchRouteInsetTest` (Task 5) covers the one route where the inset is not 0.
   - The KDocs that name `PushedScreen` are updated: `MainActivity.appDestinations`, `AppShell.kt:30`, `MiniPlayerInset.kt:17,28` and `EqualizerScreen.kt:147`.
10. **One `openTagEditor` helper** (6a T8 M2). The grep `'navigate(TagEditor('` found 6 hits, all in `MainActivity.kt`: lines 232 (Home), 260 (Now Playing, `Long` id), 288 (FolderDetail), 423 (playlist detail), 459 (AlbumDetail) and 487 (ArtistDetail).
    - The helper lives in `app/.../navigation/TagEditorNavigation.kt`:
      - `internal fun NavController.openTagEditor(songId: Long)`, which does `navigate(TagEditor(songId)) { launchSingleTop = true }`;
      - `internal fun NavController.openTagEditor(song: Song)`, which does `song.id.toLongOrNull()?.let(::openTagEditor)`.
    - All 6 sites, plus Search's new one, call it. After Task 5, the same grep returns exactly 1 hit, inside the helper.
    - The `launchSingleTop` pin (6a T8 M3) is a test in Task 5.
11. **`HomeTabPage` extraction** (4b T8). The `when (tab)` in `HomeRoute` moves to a `@Composable private fun HomeTabPage(tab: HomeTab, …)`. `HomeRoute` then loses its `LongMethod` suppression and keeps `LongParameterList`.
12. **The Material icons barrier** (spec §17 row 6). The grep `material.icons.extended` over the build files found 7 modules: `app`, `core/permissions`, `core/ui`, `feature/library`, `feature/playlists`, `feature/search` and `feature/tageditor`.
    - Task 6 removes the dependency from each one, and the catalog entry `androidx-compose-material-icons-extended`.
    - M3 still brings `material-icons-core` transitively, so removing the dependency is not enough. detekt's `ForbiddenImport` gets `imports: ['androidx.compose.material.icons.*']`, which turns the rule into a build failure (observation 0016's barrier principle).
13. **Earlier rulings honoured on the files this phase touches** (observation 0019; grep of the earlier plans and ledgers for each file's name):
    - Phase 2 ruling 2: Home's gate owns the first-grant library sync. `onGranted` keeps calling `HomeViewModel.onPermissionGranted`.
    - Phase 3 gate decision 5: a Search track tap plays the results list from that track.
    - Phase 4a ruling 12: Search keys artists by name.
    - Phase 6a ruling P2: destructive and error text use `destructiveText`.
    - Phase 6a ruling 10: the options sheet takes `currentSongs`.
    - Phase 6a F1: `OneUiBottomSheet`'s window handling is unchanged. The new parameter only reaches `rememberModalBottomSheetState`.
    - Phase 3 ruling 2 (queue): the playing row is found by its position. The queue still scrolls to `currentIndex`.
14. **Deferred, not in 6b:**
    - F2 (light-theme sheet thumbnail contrast) and F3 (dialog autofocus): Phase 7.
    - The cold-start "No music found" flash: Phase 7.
    - `EqualizerResponseCurve.kt:184` `PreviewCurveHeight`: Phase 7.
    - The parked comment in `FastScrollIndexTest.kt`: its file is not touched here.

## Review Focus

1. **Search with the keyboard open.** The last result must sit directly above the keyboard, with no extra nav-bar gap and no rows under the keys. With the keyboard closed, it must clear the pill. Pinned in Task 3 (pill, JVM) and Task 8 (keyboard, device).
2. **Access lost while Home is on Playlists or mid-selection.** No + that opens nothing, and no selection bar acting on hidden rows. Pinned in Task 2.
3. **Removing one recent search removes only that entry**, including a query that differs only in case, which is not removed. Pinned in Task 3 (codec and screen).
4. **A switched-off batch field cannot be edited, and keeps its value** when switched back on. The ViewModel already guarantees the value; the screen must show the field disabled and the switch off. Pinned in Task 4.
5. **The Search options sheet on a track that leaves the results** (for example, Edit tags renames it and the query no longer matches) closes itself instead of acting on a ghost. Pinned in Task 3.

## File map

| File | Task | Change |
|---|---|---|
| `core/designsystem/.../component/OneUiDialog.kt` (`OneUiTextField`) | 1 | extend |
| `core/designsystem/.../component/OneUiSearchField.kt`, `OneUiPillButton.kt` | 1 | new |
| `core/designsystem/.../component/OneUiBottomSheet.kt` | 1 | + `skipPartiallyExpanded` |
| `core/designsystem/.../component/KitPreviews.kt` | 1 | previews for the new components |
| `core/designsystem/src/test/.../component/OneUiInputsTest.kt` | 1 | new |
| `core/designsystem/src/test/.../theme/ContrastTest.kt` | 1 | + 2 pairings |
| `feature/player/.../QueueSheet.kt` + `QueueSheetTest.kt` | 1 | full height |
| `core/permissions/build.gradle.kts`, `MediaPermissionGate.kt`, `PermissionPrompt.kt` | 2 | state + restyle |
| `core/permissions/src/test/.../PermissionContentTest.kt`, `MediaPermissionStateTest.kt` | 2 | new |
| `app/.../home/HomeScreen.kt` + `HomeScreenTest.kt` | 2 | access state, `HomeTabPage` |
| `core/datastore/.../RecentSearchesDataSource*.kt` + test | 3 | `remove` |
| `core/data/.../repository/SearchRepository*.kt` + test | 3 | `removeRecentSearch` |
| `feature/search/build.gradle.kts`, `SearchViewModel.kt`, `SearchScreen.kt`, `RecentSearchesSection.kt` | 3 | rebuild |
| `feature/search/src/test/.../SearchScreenTest.kt` (new), `SearchViewModelTest.kt`; `src/androidTest/.../SearchScreenSmokeTest.kt` | 3 | tests |
| `feature/tageditor/build.gradle.kts`, `TagEditorScreen.kt`, `BatchTagEditorScreen.kt`, the two ViewModels | 4 | restyle + copy |
| `feature/tageditor/src/test/.../TagEditorScreenTest.kt`, `BatchTagEditorScreenTest.kt` (new), the two VM tests | 4 | tests |
| `feature/settings/.../AboutScreen.kt`, `PrivacyScreen.kt` + `InfoScreensTest.kt` (new) | 4 | copy |
| `app/.../MainActivity.kt`, `navigation/TagEditorNavigation.kt` (new), `navigation/PushedScreen.kt` (deleted), `AppShell.kt` | 5 | wiring |
| `app/src/test/.../navigation/SearchRouteInsetTest.kt` (new), `DetailRoutesEditTagsTest.kt`, `PushedScreenTest.kt` (deleted) | 5 | tests |
| 7 `build.gradle.kts` files, `gradle/libs.versions.toml`, `config/detekt/detekt.yml` | 6 | icons barrier |
| spec, roadmap, CLAUDE.md, `ui-style-guide.md`, `navigation-conventions.md` | 7 | docs |

(`…` is `src/main/kotlin/com/rolla/musicplayer/<module path>`.)

Task order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8. Task 3 changes `SearchRoute`'s signature, so `:app` does not compile from Task 3 until Task 5. Tasks 3 and 4 verify with module tasks only, never `:app`.

---

### Task 1: Kit: search field, pill button, field errors, full-height sheets (m3-design-system-agent)

**Files:**
- Modify: `core/designsystem/.../component/OneUiDialog.kt` (`OneUiTextField`), `OneUiBottomSheet.kt`, `KitPreviews.kt`
- Create: `core/designsystem/.../component/OneUiSearchField.kt`, `OneUiPillButton.kt`
- Modify: `feature/player/.../QueueSheet.kt` (one argument)
- Test: `core/designsystem/src/test/.../component/OneUiInputsTest.kt` (new), `.../theme/ContrastTest.kt`, `feature/player/src/test/.../QueueSheetTest.kt`

**Interfaces:**
- Consumes: the 6a kit (`OneUiTextField`, `OneUiBottomSheet`, `OneUiIconButton`), `RollaIcons.Search` / `Close`, `RollaDimens.minTouchTarget` / `settingsProgressSize` / `settingsProgressStroke`.
- Produces: the ruling 1 signatures, exactly. Tasks 2, 3 and 4 rely on them.

- [ ] **Step 1: Write the failing tests.** `OneUiInputsTest` is Robolectric with `@GraphicsMode(NATIVE)` and `@Config(qualifiers = "w360dp-h640dp-xxhdpi")`, in the dark theme. Each test does one `setContent` and flips state.

  | Test | Trivial stub it must reject | Assertion inside the test that fails under the stub | Named mutation |
  |---|---|---|---|
  | `errorSupportingTextIsDestructiveTextAndPlainTextIsGray` | supporting text never drawn | after the flip `isError = true`, node "Bad year" exists, and its glyph pixels are near `destructiveText`. Before the flip (`isError = false`), the same text's pixels are near `onSurfaceVariant` | colour always `onSurfaceVariant` → RED on the error half |
  | `searchFieldShowsClearOnlyWithTextAndClearEmptiesIt` | clear button always shown | with query "" there is no "Clear search" node. After the flip to "abc" it exists; a click leaves `query == ""` | drop the `isNotEmpty` guard → RED on the first assertion |
  | `searchFieldIsAPill` | shape `medium` | a pixel probe at (6 dp, 6 dp) inside the field's top-left equals `background`; the probe at the field's centre-left glyph-free point equals `surfaceContainerHigh` (the control) | `shape = MaterialTheme.shapes.medium` → RED (probe reads the container) |
  | `filledAndTonalPillsUseTheirContainers` | one colour for both | the filled pill's probe beside its label is near `primary`; after the flip `tonal = true`, the same probe is near `surfaceContainerHigh` | tonal ignored → RED after the flip |
  | `loadingPillShowsProgressIgnoresTapsAndIsDescribed` | `loading` ignored | before the flip, "Save" exists and a click counts 1. After `loading = true`, "Save" is absent, a node with description "Saving" exists, and a click leaves the count at 1 | drop the loading branch → RED |
  | `skipPartiallyExpandedSheetOpensAtFullHeight` | parameter not passed to the state | with a sheet of 40 rows open under `skipPartiallyExpanded = true`, the title's top is within the top quarter of the root. A second sheet keyed by a flip to `false` puts its title below the root's half | not passing it to `rememberModalBottomSheetState` → RED on the first half |

  Geometry for `searchFieldIsAPill` (computed at planning): the field is 48 dp tall, so the pill's corner radius is 24 dp. The point (6, 6) lies 25.46 dp from the pill's corner centre (24, 24), which puts it outside the pill by 1.46 dp. It lies 14.14 dp from a `medium` 16 dp corner's centre (16, 16), which puts it inside that shape by 1.86 dp. At xxhdpi both margins exceed 4 px.

  For the error colour test, the dark `error` (#FF5A5A) and `destructiveText` (#FF6060) differ by 6 per channel, inside `assertColorNear`'s tolerance. Repaint `error` to a distinct colour in the test's `MaterialTheme` copy, as 6a Task 1 did.

  Extend `ContrastTest` with `onSurfaceVariant` on `surfaceContainerHigh` (4.5) and `destructiveText` on `surfaceContainer` (4.5), in both themes.

  Add `QueueSheetTest.queueSheetOpensAtFullHeight`. Use a 40-item queue; the "Queue" title's top lies in the top quarter of the root. Control: the title is present, asserted first. Mutation: drop `skipPartiallyExpanded = true` from `QueueSheet`. The existing `fullyExpandedSheetStopsBelowTheStatusBar` must stay green unedited.

- [ ] **Step 2: Run them; expect RED.** Run `./gradlew :core:designsystem:testDebugUnitTest --tests '*OneUiInputsTest*' --tests '*ContrastTest*' :feature:player:testDebugUnitTest --tests '*QueueSheetTest*'`. Expected: compile failure first (the new signatures), then, once stubs compile, the assertions above fail.
- [ ] **Step 3: Implement ruling 1** in the files above. `OneUiSearchField` and `OneUiPillButton` each get previews (dark, light, fontScale 1.5) in `KitPreviews.kt`. `QueueSheet` passes `skipPartiallyExpanded = true`.
- [ ] **Step 4: Run the tests; expect GREEN.** Then apply each named mutation, see RED with the test's own message, and revert byte-exact (cmp).
- [ ] **Step 5: Gate the modules.** Run `./gradlew spotlessApply`, then `./gradlew :core:designsystem:check :feature:player:check`. Expected: `BUILD SUCCESSFUL`.

---

### Task 2: The permission screen and Home's access state (permissions-agent, with the `:app` Home edits)

**Files:**
- Modify: `core/permissions/build.gradle.kts` (add `id("rolla.android.robolectric")`, `implementation(project(":core:designsystem"))`), `MediaPermissionGate.kt`, `PermissionPrompt.kt`
- Modify: `app/.../home/HomeScreen.kt`
- Test: `core/permissions/src/test/.../PermissionContentTest.kt`, `MediaPermissionStateTest.kt` (new); `app/src/test/.../home/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `OneUiPillButton` (Task 1), `ContentPanel`, `ArtworkPlaceholder`.
- Produces: `MediaPermissionState`, `rememberMediaPermissionState()`, `MediaPermissionGate(state, onGranted, modifier, content)` (ruling 8); `HomeScreen(…, accessGranted: Boolean = true, …)`; `internal fun PermissionContent(status, onRequest, onOpenSettings, modifier, content)` (made internal for the tests).

- [ ] **Step 1: Write the failing tests.**

  | Test | Trivial stub | Assertion inside the test that fails under it | Named mutation |
  |---|---|---|---|
  | `PermissionContentTest.eachStatusShowsItsMessageAndButton` | renders `content` for every status | NEEDS_REQUEST shows text containing "Rolla Music needs access" and "Allow access". The flip to SHOW_RATIONALE shows "Grant access". PERMANENTLY_DENIED shows "Open Settings" and text containing "so Rolla Music can load". GRANTED shows the content tag and no "Open Settings" | swap the SHOW_RATIONALE and PERMANENTLY_DENIED branches → RED |
  | `PermissionContentTest.buttonsCallTheirOwnAction` | buttons do nothing | the NEEDS_REQUEST button click makes `onRequest` 1. After the flip to PERMANENTLY_DENIED, a click makes `onOpenSettings` 1 and `onRequest` stays 1 | both buttons call `onRequest` → RED |
  | `PermissionContentTest.promptSitsInAPanelWithAFilledPill` | prompt drawn straight on the background | (NATIVE, dark) the pixel 4 dp above the message's top is near `surfaceContainer`, and the pixel beside the button label is near `primary` | drop the `ContentPanel` → RED on the first probe |
  | `MediaPermissionStateTest.grantIsReadAtStartAndOnResume` | status fixed at its first value | with the permission denied (`ShadowApplication.denyPermissions(MediaPermission.name)`), `isGranted` is false. After `grantPermissions` and an ON_PAUSE→ON_RESUME cycle of the activity scenario, it is true | remove the resume observer → RED |
  | `HomeScreenTest.createPlaylistIsHiddenWhileAccessIsDenied` | + always on Playlists | settled on Playlists with `accessGranted = true`, "Create playlist" exists. After the flip to false it does not | ignore `accessGranted` for + → RED |
  | `HomeScreenTest.deniedAccessClearsTheSelectionAndShowsTheHeader` | selection bar regardless of access | after a long-press selects a row, "1 selected" exists. After the flip `accessGranted = false`, "Rolla Music" exists and "1 selected" does not. After flipping back to true, "1 selected" still does not exist (cleared, not hidden) | hide the bar without clearing → RED on the last assertion |

  Use the existing `setHomeWithTappableRows` helper for the selection test, adding a `mutableStateOf` for `accessGranted`.

- [ ] **Step 2: Run them; expect RED.** Run `./gradlew :core:permissions:testDebugUnitTest :app:testDebugUnitTest --tests '*HomeScreenTest*'`.
- [ ] **Step 3: Implement ruling 8.**
  - `PermissionPrompt` (ruling 8 anatomy) uses tokens only. Its private dp vals and the `LibraryMusic` icon go.
  - `HomeRoute` creates the state with `rememberMediaPermissionState()`, passes `accessGranted = permission.isGranted`, and wraps the pager with `MediaPermissionGate(permission, onGranted, content = pager)`.
  - Extract `HomeTabPage` (ruling 11).
- [ ] **Step 4: Run the tests; expect GREEN.** Apply each mutation, see RED, and revert byte-exact.
- [ ] **Step 5: Check the result.**
  - `grep -n 'Suppress(' app/src/main/kotlin/com/rolla/musicplayer/home/HomeScreen.kt` must show no `LongMethod` on `HomeRoute`.
  - `grep -rn 'material.icons\|private val .*\.dp' core/permissions/src/main` must return nothing.
  - Then run `./gradlew spotlessApply` and `./gradlew :core:permissions:check :app:testDebugUnitTest :app:detekt`. Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Search on the One UI kit (ui-builder + viewmodel-architect; the data seam with it)

**Files:**
- Modify: `core/datastore/.../RecentSearchesDataSource.kt`, `RecentSearchesDataSourceImpl.kt` + its codec test
- Modify: `core/data/.../repository/SearchRepository.kt`, `SearchRepositoryImpl.kt` + its test
- Modify: `feature/search/build.gradle.kts` (add `id("rolla.android.robolectric")`), `SearchViewModel.kt`, `SearchScreen.kt`, `RecentSearchesSection.kt`
- Test: `feature/search/src/test/.../SearchScreenTest.kt` (new), `SearchViewModelTest.kt`; `feature/search/src/androidTest/.../SearchScreenSmokeTest.kt`

**Interfaces:**
- Consumes: `OneUiSearchField`, `OneUiIconButton`, `OneUiTextButton`, `ContentPanel`, `homeListBottomPadding()`, `SongOptionsHost(song, userPlaylists, onDismiss, onAddSongToPlaylist, onCreatePlaylistAndAddSong, onEditTagsClick, currentSongs = …)`, `PlaylistRepository.createPlaylistWithSong(name, songId)`.
- Produces:
  - `SearchRoute(onNavigateUp: () -> Unit, onAlbumClick: (Long) -> Unit, onArtistClick: (String) -> Unit, onEditTagsClick: (Song) -> Unit, viewModel: SearchViewModel = hiltViewModel(), modifier: Modifier = Modifier)`.
  - The stateless `SearchScreen(…)` gains `userPlaylists: List<Playlist>`, `onAddSongToPlaylist: (String, Long) -> Unit`, `onCreatePlaylistAndAddSong: (String, String) -> Unit`, `onEditTagsClick: (Song) -> Unit` and `onRemoveRecentSearch: (String) -> Unit`.
  - `SearchViewModel` gains `userPlaylists: StateFlow<List<Playlist>>`, `addSongToPlaylist(songId: String, playlistId: Long)`, `createPlaylistAndAddSong(name: String, songId: String)` and `onRemoveRecentSearch(query: String)`. Its constructor adds `PlaylistRepository`.
  - `SearchRepository.removeRecentSearch(query: String)`; `RecentSearchesDataSource.remove(query: String)`.

- [ ] **Step 1: Write the failing tests.**

  | Test | Trivial stub | Assertion inside the test that fails under it | Named mutation |
  |---|---|---|---|
  | `withoutRecentSearchRemovesOnlyThatExactQuery` (codec test) | returns `current` | `withoutRecentSearch(["a","b","c"], "b") == ["a","c"]`, and `withoutRecentSearch(["a","b"], "B") == ["a","b"]` | case-insensitive match → RED on the second |
  | `removeRecentSearchDelegatesToTheDataSource` (repository test) | no-op | after `removeRecentSearch("q")`, the fake data source's list lacks "q" and keeps "r" | no-op body → RED |
  | `SearchViewModelTest.removingARecentSearchRemovesIt` | no-op | after `onRemoveRecentSearch("jazz")` and `advanceUntilIdle()`, `recentSearches` no longer holds "jazz" and still holds "queen" | no-op → RED |
  | `SearchViewModelTest.optionsSheetActionsReachThePlaylistRepository` | no-ops | `addSongToPlaylist("1", 7L)` calls `addSongs(7L, listOf("1"))` once. `createPlaylistAndAddSong("Mix","1")` creates "Mix", then adds "1" to the new id | swap ids → RED |
  | `SearchScreenTest.resultsAreSectionedTracksAlbumsArtists` | renders nothing | "Tracks", "Albums" and "Artists" headers exist, and "Songs" does not exist | header text `"Songs"` → RED |
  | `SearchScreenTest.aTracksMoreOpensTheOptionsSheetAndEditTags` | ⋮ does nothing | before the click, "Edit tags" does not exist. After clicking "More options for Song One", "Add to playlist" and "Edit tags" exist; clicking "Edit tags" reports `Song One` | omit `onMoreClick` → RED |
  | `SearchScreenTest.theSheetClosesWhenItsTrackLeavesTheResults` | sheet ignores the results | with the sheet open, "Edit tags" exists. After the flip to results without Song One, it does not | `currentSongs = null` → RED |
  | `SearchScreenTest.recentRowsSearchAgainAndRemoveThemselves` | rows inert | clicking "Search again for queen" reports "queen". Clicking "Remove jazz from recent searches" reports "jazz", and the report list is exactly `["queen", "jazz"]` across the two channels | wire Close to `onRecentSearchClick` → RED |
  | `SearchScreenTest.loadingShowsNoSpinner` | spinner kept | with Results, a result row exists. After the flip to Loading, the row is gone and there are 0 nodes with indeterminate `ProgressBarRangeInfo` | keep `CircularProgressIndicator` → RED |
  | `SearchScreenTest.theLastResultClearsThePill` | no bottom padding | with `LocalMiniPlayerInset` providing 120 dp and 30 track results, after scrolling to the last row, its bottom ≤ the root's bottom − 120 dp (+1 dp tolerance). Control: before the scroll, the last row is not composed | drop the list's `contentPadding` → RED |
  | `SearchScreenTest.theFieldIsFocusedOnOpenAndBackNavigatesUp` | no focus request | the field node `assertIsFocused()`. Clicking "Navigate up" makes `onNavigateUp` 1 | remove `requestFocus()` → RED |

  `SearchScreenTest` uses Robolectric, dark theme and `@GraphicsMode(NATIVE)` for the geometry test.

  In `SearchScreenSmokeTest` (androidTest), `"Songs"` becomes `"Tracks"`. Compile-check it with `./gradlew :feature:search:compileDebugAndroidTestKotlin`.

- [ ] **Step 2: Run them; expect RED.** Run `./gradlew :core:datastore:testDebugUnitTest :core:data:testDebugUnitTest :feature:search:testDebugUnitTest`.
- [ ] **Step 3: Implement rulings 3, 4 and 5.**
  - The private dp vals in `SearchScreen.kt` and `RecentSearchesSection.kt` go.
  - Both files drop every `androidx.compose.material.icons` and `material3` component import except `MaterialTheme`, `Text` and `Icon`.
  - `SearchRoute` collects `userPlaylists` and wires the new callbacks to the ViewModel.
- [ ] **Step 4: Run the tests; expect GREEN.** Apply each mutation, see RED, and revert byte-exact.
- [ ] **Step 5: Gate the modules.** Run `./gradlew spotlessApply`, then `./gradlew :core:datastore:check :core:data:check :feature:search:check :feature:search:compileDebugAndroidTestKotlin`. Expected: `BUILD SUCCESSFUL`. `:app` will not compile until Task 5: `SearchRoute` gained a required parameter.

---

### Task 4: Tag editors on the kit, and the info screens' copy (ui-builder)

**Files:**
- Modify: `feature/tageditor/build.gradle.kts` (add `id("rolla.android.robolectric")`, drop the manual `testOptions` block and `testImplementation(libs.robolectric)` it duplicates), `TagEditorScreen.kt`, `BatchTagEditorScreen.kt`, `TagEditorViewModel.kt`, `BatchTagEditorViewModel.kt` (copy only)
- Modify: `feature/settings/.../AboutScreen.kt`, `PrivacyScreen.kt` (copy only)
- Test: `feature/tageditor/src/test/.../TagEditorScreenTest.kt`, `BatchTagEditorScreenTest.kt` (new), `TagEditorViewModelTest.kt`, `BatchTagEditorViewModelTest.kt` (copy assertions); `feature/settings/src/test/.../InfoScreensTest.kt` (new)

**Interfaces:**
- Consumes: `OneUiTextField(…, keyboardOptions, isError, supportingText)`, `OneUiPillButton`, `OneUiSwitch`, `OneUiDetailTopBar`, `ArtworkThumbnail(…, large = true, placeholderGlyphSize)`, `tracksCountLabel`.
- Produces: the unchanged public `TagEditorRoute` / `BatchTagEditorRoute` / `TagEditorScreen` / `BatchTagEditorScreen` signatures.

- [ ] **Step 1: Write the failing tests.**

  | Test | Trivial stub | Assertion inside the test that fails under it | Named mutation |
  |---|---|---|---|
  | `TagEditorScreenTest.fieldsAreFilledOneUiFields` | `OutlinedTextField` kept | (NATIVE, dark) the pixel 2 dp below the "Title" field's top edge, at its horizontal centre, is near `surfaceContainerHigh`. Control: the card pixel 4 dp above the field is near `surfaceContainer` | revert to `OutlinedTextField` → RED |
  | `TagEditorScreenTest.aYearErrorShowsItsMessage` | errors dropped | with `yearError = null`, the text "Year must be blank or up to 4 digits." does not exist. After the flip it exists | omit `supportingText` → RED |
  | `TagEditorScreenTest.savingShowsProgressAndLocksCancel` | `isSaving` ignored | before the flip, "Save" exists and Cancel is enabled. After `isSaving = true`, "Save" is absent, "Saving" (description) exists and Cancel `assertIsNotEnabled()` | pass `loading = false` → RED |
  | `TagEditorScreenTest.backAndCancelBothNavigateUp` | no callbacks | clicking "Navigate up" and then "Cancel" makes the count 2 | Cancel wired to save → RED |
  | `TagEditorScreenTest.aLoadFailureSaysTrackAndOffersBack` | renders the form | with `loadFailed = true`, "Couldn't load this track's tags." exists and "Title" does not. Clicking "Back" makes `onNavigateUp` 1 | old copy → RED |
  | `BatchTagEditorScreenTest.theTitleCountsTracks` | static title | `songCount = 1` shows "Edit 1 track". After the flip to 3, "Edit 3 tracks" shows, and no text containing "songs" exists | `"Edit $songCount songs"` → RED |
  | `BatchTagEditorScreenTest.aSwitchedOffFieldIsLockedAndItsSwitchTurnsItOn` | toggle inert | the Genre field (`applied = false`) `assertIsNotEnabled()`, and the node "Apply Genre to every selected track" has `Role.Switch` and is off. A click reports `(GENRE, true)`. After the flip to `applied = true`, the field is enabled and keeps its value "Rock" | a `Checkbox` (Role.Checkbox) → RED; field always enabled → RED |
  | `InfoScreensTest.aboutAndPrivacyNameTheAppRollaMusic` | old copy | About shows "Rolla Music" and "Version 1.0 (1)" (control), and no text containing "RollaMusicPlayer". After the flip to Privacy, text containing "Rolla Music is fully offline" exists | old About string → RED |

  Update the 4 VM-test copy assertions to ruling 7's strings. They fail RED against the old copy first.

- [ ] **Step 2: Run them; expect RED.** Run `./gradlew :feature:tageditor:testDebugUnitTest :feature:settings:testDebugUnitTest`.
- [ ] **Step 3: Implement rulings 6 and 7.**
  - The private dp vals, every `androidx.compose.material.icons` import, and every `Button`, `OutlinedButton`, `OutlinedTextField`, `Checkbox` and `TopAppBar` call leave both editor files.
  - The scoped-storage consent, artwork picker and BackHandler logic in the two Routes stay byte-identical.
- [ ] **Step 4: Run the tests; expect GREEN.** Apply each mutation, see RED, and revert byte-exact. The existing tag-editor IO and contract suites must stay green unedited.
- [ ] **Step 5: Gate the modules.** Run `./gradlew spotlessApply`, then `./gradlew :feature:tageditor:check :feature:settings:check`. Expected: `BUILD SUCCESSFUL`.

---

### Task 5: `:app` wiring: no `PushedScreen`, one `openTagEditor`, Search's Edit tags (navigation-agent)

**Files:**
- Create: `app/.../navigation/TagEditorNavigation.kt`
- Modify: `app/.../MainActivity.kt`, `navigation/AppShell.kt` (KDoc), `core/designsystem/.../component/MiniPlayerInset.kt` (KDoc), `feature/equalizer/.../EqualizerScreen.kt` (KDoc)
- Delete: `app/.../navigation/PushedScreen.kt`, `app/src/test/.../navigation/PushedScreenTest.kt`
- Test: `app/src/test/.../navigation/SearchRouteInsetTest.kt` (new), `DetailRoutesEditTagsTest.kt`

**Interfaces:**
- Consumes: `SearchRoute(…, onEditTagsClick)` (Task 3).
- Produces:
  - `internal fun NavController.openTagEditor(songId: Long)` and `internal fun NavController.openTagEditor(song: Song)` (ruling 10).
  - `internal fun NavGraphBuilder.searchDestination(navController: NavHostController, provideViewModel: @Composable () -> SearchViewModel = { hiltViewModel() })`, with the deep link kept: `rollamusic://search`.

- [ ] **Step 1: Write the failing tests.**

  | Test | Trivial stub | Assertion inside the test that fails under it | Named mutation |
  |---|---|---|---|
  | `SearchRouteInsetTest.theLastResultEndsExactlyOnePillAboveTheBottom` | no padding, or double | hosted through `searchDestination` in a real `NavHost`, with `LocalMiniPlayerInset` providing 120 dp and a fake-repository `SearchViewModel` returning 30 tracks. After scrolling to the end, the gap between the last row's bottom and the root's bottom is 120 dp ± 1 (240 would mean double padding). Control: before the scroll, the last row is not composed | wrap the route in `Box(Modifier.padding(bottom = LocalMiniPlayerInset.current))` → RED (gap 240) |
  | `DetailRoutesEditTagsTest.searchsEditTagsOpensTheTagEditorForThatTrack` | Edit tags unwired | after ⋮ → "Edit tags" on a Search result, `currentBackStackEntry` has route `TagEditor` with the song's id. Control: before, it is `Search` | pass `onEditTagsClick = {}` → RED |
  | `DetailRoutesEditTagsTest.editTagsTwiceKeepsOneTagEditor` | `launchSingleTop` dropped | after `openTagEditor(5L)` twice, the back stack holds exactly 1 `TagEditor` entry. Control: after the first call, `currentBackStackEntry` is `TagEditor` | remove `launchSingleTop = true` → RED (2 entries) |

- [ ] **Step 2: Run them; expect RED.** Run `./gradlew :app:testDebugUnitTest --tests '*SearchRouteInsetTest*' --tests '*DetailRoutesEditTagsTest*'`.
- [ ] **Step 3: Implement rulings 9 and 10.**
  - Remove the 8 wrappers, delete `PushedScreen` and its test, and add the helper.
  - Replace the 6 bodies, and register `searchDestination`.
  - Update the four KDocs that name `PushedScreen`.
- [ ] **Step 4: Run the tests; expect GREEN.** Apply each mutation, see RED, and revert byte-exact. `DetailRoutesInsetTest`, `MiniPlayerVisibilityTest`, `RouteClassTest` and `NowPlayingNavigationTest` must stay green unedited.
- [ ] **Step 5: Run the inventory greps.**
  - `grep -rn 'navigate(TagEditor(' --include=*.kt .` → exactly 1 hit, in `TagEditorNavigation.kt`.
  - `grep -rn 'PushedScreen' --include=*.kt .` → 0 hits.
  - Then run `./gradlew spotlessApply` and `./gradlew :app:check`. Expected: `BUILD SUCCESSFUL`.

---

### Task 6: The Material icons barrier (build-tooling-agent)

**Files:**
- Modify: the 7 `build.gradle.kts` files of ruling 12, `gradle/libs.versions.toml`, `config/detekt/detekt.yml`

- [ ] **Step 1: Run the inventory.**
  - `grep -rn 'androidx.compose.material.icons' --include=*.kt .` → 0 hits (Tasks 2, 3 and 4 removed the last five files).
  - `grep -rln 'material.icons.extended' --include=*.kts --include=*.toml .` → the 7 build files and the catalog.
- [ ] **Step 2: Remove the dependency** from each build file, and the catalog entry.
- [ ] **Step 3: Add the barrier.** Add the detekt rule, `style > ForbiddenImport: active: true` with `imports: [{ value: 'androidx.compose.material.icons.*', reason: 'Icons come from RollaIcons (spec §5.5)' }]`.
- [ ] **Step 4: Prove the barrier.**
  - Add `import androidx.compose.material.icons.Icons` to one `:feature:search` main file. `./gradlew :feature:search:detekt` must fail on `ForbiddenImport`. Quote the line.
  - Revert byte-exact (cmp).
- [ ] **Step 5: Run the gate.** Run `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug --continue`. Expected: `BUILD SUCCESSFUL`. Report the unit-test count from the test XMLs.

---

### Task 7: Docs, the phase gate and reviews (controller)

- [ ] **Spec.**
  - §6: the ruling 1 signatures.
  - §7.2: the permission state, and + and selection gated on access.
  - §7.3: no `PushedScreen`; Search pads its own list.
  - §8.7:
    - Search anatomy: rulings 3 and 4, with removable recents.
    - Tag editors: `OneUiTextField`, pills and `OneUiSwitch`.
    - The permission prompt.
    - The queue sheet opens at full height.
- [ ] **Roadmap.** Row 6a reads "Committed 7edb0a7 (2026-10-21)". Row 6b gets its status.
- [ ] **CLAUDE.md:**
  - a Phase 6b bullet;
  - the 6a bullet's sentence "`PushedScreen` still wraps Search, the tag editors…" is replaced;
  - re-grep every completeness claim before writing it (observation 0020).
- [ ] **`.claude/rules/ui-style-guide.md`:** the §6 kit note gains `OneUiSearchField`, `OneUiPillButton` and the "no `Button`/`OutlinedTextField`/`Checkbox`" line.
- [ ] **`.claude/rules/navigation-conventions.md`:** the `openTagEditor` helper and the detail-destination extension pattern.
- [ ] **Phase gate.** Run `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug --continue`, plus `./gradlew :feature:search:compileDebugAndroidTestKotlin :app:assembleBenchmark`.
- [ ] **Exit greps** (spec §17 row 6):
  - no `androidx.compose.material.icons` import;
  - `grep -rnE 'private val [A-Za-z]+ = [0-9.]+\.dp' --include=*.kt feature core/ui core/permissions app` → only `EqualizerResponseCurve.kt` `PreviewCurveHeight` (Phase 7);
  - no direct `ModalBottomSheet`, `AlertDialog`, `TextButton`, `TextField`, `OutlinedTextField`, `Button`, `OutlinedButton`, `Checkbox` or `DropdownMenu` outside `:core:designsystem`.
- [ ] **Reviews.** The final code review (code-reviewer, opus) over the phase diff, and the compose-performance-auditor over Search and the tag editors. Accept the phase at 0 Critical/High.

### Task 8: Device check on the Motorola edge 70 (controller)

Install with `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark`; `firstInstallTime` must stay 2026-10-21 15:42:36. Check `mCurrentFocus` before every injected input. Never save tags on the user's files, never revoke the app's permission, and never touch the user's playlists.

- [ ] **Search.**
  - The keyboard opens on entry.
  - Type a query that returns many results, then scroll to the end. The last row sits directly above the keyboard (Review Focus 1): no gap of a nav-bar height, no row under the keys.
  - Hide the keyboard: the last row clears the pill on 3-button navigation.
  - ⋮ opens the options sheet. Edit tags opens the editor, and back returns to Search.
  - Remove one recent search: only that row goes.
- [ ] **Tag editor.**
  - Open it from a track's options: the One UI cards, filled fields and pills.
  - Type a letter in Year: the red error appears. Cancel. Do not save.
- [ ] **Batch editor.** Select 2 tracks on Tracks, then Edit tags: the title reads "Edit 2 tracks", and the switches lock and unlock their fields. Cancel.
- [ ] **Queue sheet.** It opens at full height with the playing row visible.
- [ ] **About and Privacy:** "Rolla Music".
- [ ] **Permission prompt.** It is not checked on the device: that would need revoking the permission, a settings change the user owns. JVM tests cover it (Task 2). Offer the user the check if they want to revoke and re-grant themselves.

## Self-review

- **Spec coverage.**
  - §8.7 Search: Task 3. Tag editors: Task 4.
  - Permission prompt and §7.2 gate: Task 2.
  - §8.6 info screens: Task 4 (copy; their bar and cards landed in Phase 3, plan task 9).
  - §7.3 pill on Search and hidden elsewhere: Tasks 3 and 5.
  - §12 dead controls (Search ⋮, Home + while denied): Tasks 3 and 2.
  - §17 row 6 icons and hardcoded values: Tasks 2, 3, 4 and 6, with the barrier in 6.
  - The user's queue-sheet decision: Task 1.
- **Carry-overs:**
  - 6a T8 M2 and M3: Task 5.
  - The icons sweep: Task 6.
  - Search ⋮: Task 3.
  - Phase 2 concern (b): Task 2.
  - 4b `HomeTabPage`: Task 2.
  - The queue follow-up: Task 1.
- **Step scan:** every code step names its signature and its file. The bodies are left to the implementer, except the ruling 1 anatomy and ruling 7 copy, which the spec does not fix and the plan therefore pins.
- **Type consistency:**
  - `SearchRoute`'s new `onEditTagsClick: (Song) -> Unit` (Task 3) matches `openTagEditor(song: Song)` (Task 5).
  - `MediaPermissionGate(state, onGranted, …)` (Task 2) matches `HomeRoute`'s use.
  - `OneUiPillButton(…, tonal, loading, loadingDescription)` matches its use in Tasks 2 and 4.
- **Every test has its own control:** each row of each table names the assertion inside that test that a trivial stub fails (observation 0016). The absence tests (no + while denied, no spinner while loading, sheet gone) all assert presence first in the same `setContent`.
- **Visible outcomes are rendered:**
  - Review Focus 1 and the inset tests measure bounds.
  - The field shape, colour, pill and panel tests probe pixels.
  - None infers a visual result from semantics (observation 0017).
- **Derived values were computed:** the contrast table (ruling 2) and the pill-probe geometry (Task 1) are arithmetic written into the plan, not promises (observation 0018).
- **Earlier rulings:** ruling 13 lists every earlier ruling found on the touched files, and each one is honoured (observation 0019).
- **Inventories are search results:** the TagEditor navigation bodies (6), the `PushedScreen` wrappers (8), the icons dependency (7 modules) and the copy table each name the grep that produced them (observation 0020).
- **Proportion:** the plan carries signatures, tables and assertions, not bodies.
