# One UI redesign, Phase 7: calibration, polish carry-overs, docs and the final gate — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The redesign meets spec §1's "done" criteria. Screens with a reference screenshot are calibrated against it. The carry-overs that a user can see are fixed. The app is called "Rolla Music" and has its own icon. The docs describe the shipped app. The baseline profile is regenerated. The final gate passes.

**Architecture:** There is no new subsystem. Calibration is a measure, compare, fix-tokens loop: §14 makes every fix a token or dimension change in `:core:designsystem`, never a per-screen literal. The carry-overs are small, independent fixes, grouped by module so that one implementer and one reviewer can own each group. The device tasks belong to the controller.

**Tech Stack:** Kotlin 2.2.21, Compose BOM 2024.09.03 (M3 1.3.0), Glance 1.1.0, Media3 1.11.1, Room v5, AGP 8.13.2 with the `:baselineprofile` Macrobenchmark module and its Gradle managed devices (`pixel6Api31`, `pixel6Api31Frames`), Robolectric 4.13.

**Spec:** `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`. The tasks implement these sections:
- §1, the done criteria;
- §14, calibration;
- §15, testing and gates;
- §16, docs;
- §17 row 7.

The roadmap is `docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md`, row 7. Carry-overs come from the 5, 6a, 6b and 6c SDD ledgers under `.superpowers/sdd/`.

## User decisions (2026-10-27)

1. **Calibration device.** The Samsung SM-S901B is connected once, read-only, to read its density, font scale and screen size. All screen captures and device checks then run on the Motorola edge 70 (ZY22MBNVB4).
2. **App icon.** A simple One UI adaptive icon: a white music note on the accent blue `#2F6FF0`, with a monochrome layer. It is built from the existing `MusicNote` geometry, with no new design.

## Global Constraints

- **Spec §2 is binding.**
  - The UI label is "Tracks"; the model stays `Song`.
  - Every user-visible name is "Rolla Music".
  - The UI says "Equaliser" (British); code identifiers stay `Equalizer*`.
  - The accent is `#2F6FF0`, and blue text uses `accentText`.
  - Destructive and error text uses `destructiveText`.
  - Icons come only from `RollaIcons`.
  - The widget keeps ±15 s.
- **Visual values.** No hardcoded colour, type, shape or dimension outside `:core:designsystem`. A new value becomes a token first. A calibrated value changes the token, the spec §5 table entry and `docs/design/oneui-measurements.md` in the same task.
- **WCAG AA holds for every documented pairing.** Text needs 4.5:1, non-text 3:1. A calibrated colour that breaks a pairing keeps the documented AA-safe substitute (§14 step 4).
- **No dead controls, no decorative toggles, offline only.** No new runtime dependency.
- **Commits and git.** Agents never run `git commit/add/stash/checkout/reset/clean`; the user commits.
- **Editing files.** Never `sed -i` a source file, and never write Kotlin, Gradle or XML through a shell heredoc. New files: create a placeholder, then fill it with Edit.
- **Tests.**
  - Every test carries its own control: an assertion in the same test that fails under the trivial stub.
  - Every test names the mutation that turns it RED. The RED evidence quotes `BUILD FAILED`, the test's own assertion message (copied from the result XML into the log) and a `cmp` revert check.
  - An invariant (property) assertion needs its own mutation under which its left side crosses the bound (observation 0016).
  - A visible outcome is observed by rendering or by device measurement, not by "is displayed" (observation 0017).
- **Device rules.** These cover the Motorola ZY22MBNVB4 and the Samsung SM-S901B.
  - Install only with `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark`, and confirm afterwards that `firstInstallTime` is still `2026-10-21 15:42:36`.
  - Never uninstall, never clear data, never change a system setting (the user toggles airplane mode, the theme or the font), never revoke a permission.
  - Never touch the user's playlists, and never save tags.
  - Check `mCurrentFocus` before every injected input.
  - Do not touch other apps' notifications, and delete screenshots that show them.
  - Use `MSYS_NO_PATHCONV=1` for `adb shell` paths under Git Bash.
  - The Samsung is read-only: no install, no input.
- **Connected tests run only from library modules.** `:app` has no `androidTest`, and this phase must not add one, because AGP uninstalls the tested application after a connected run, which would wipe the user's data. Library-module test APKs instrument themselves and are safe on the Motorola.

## Rulings

1. **The calibration comparison is in dp, not px.**
   - Reference px convert with the Samsung's measured density (Task 0). Ours convert at the Motorola's 3.0.
   - The screens differ in width (Motorola 406.7 dp; the Samsung's width comes from Task 0), so geometry is compared as distances from the nearer edge (start-anchored or end-anchored), never as absolute x.
   - Vertical positions are measured from the top of the window content, below the status bar.
   - Tolerances (§14): geometry ±2 dp; flat colours ±3 per channel, or exactly the documented substitute (`#2F6FF0`, `#646466`, `accentText`, `destructiveText`); text glyph height ±1 sp.
2. **Calibration fixes are batched.**
   - Task 2 measures and writes `calibration-diffs.md` in the workspace: one row per out-of-tolerance item, with screen, element, reference value, our value, the token that owns it and the proposed value.
   - Task 3's brief is that table. A row whose fix would breach WCAG AA, or that the user accepted as an exception (spec §5.4), is recorded and not changed.
   - A difference caused by the device rather than a token (font metrics: the reference renders One UI's font, the Motorola renders Roboto; spec §2) is recorded as such and not chased.
3. **Unknown artist.** MediaStore stores a missing artist as the literal `<unknown>` (`MediaStore.UNKNOWN_STRING`), and the app shows it raw (device-observed in Phase 6c). A pure function in `:core:model` maps it for display, and data, routes and keys keep the raw value (Phase 4a ruling 12: artists are keyed by name).
   - Signature: `fun artistDisplayName(raw: String): String`. It returns `"Unknown"` when `raw` is blank or equals `"<unknown>"`, and `raw` otherwise.
   - `"Unknown"` is the reference mini-player's own subtitle (measurements, "Observations").
   - Every display site in the inventory below uses it. The tag editor does not: it shows the file's own tags, not MediaStore's.
4. **The app identity.**
   - The launcher label becomes `@string/app_name` = "Rolla Music".
   - The theme's resource name `Theme.RollaMusicPlayer` is an identifier, not copy, and stays.
   - The icon is an adaptive icon (`mipmap-anydpi-v26`) with three layers:
     - background `@color/ic_launcher_background` = `#2F6FF0`;
     - foreground: `MusicNote`'s path, white, stroke `2.0 × 108/24`, scaled into the 66 dp safe zone of the 108 dp canvas;
     - a monochrome layer: the same path, for themed icons on API 33+.
   - API 24–25 gets `mipmap/ic_launcher.xml`, a layer-list of a `#2F6FF0` oval and the note vector.
   - Computed contrast: white `#FFFFFF` on `#2F6FF0` is 4.51:1 (spec §5.1).
5. **Smoke suite (§15).** Five paths are listed. Three exist: Tracks, mini-player/Now Playing and search. The missing paths are added in the library modules that own the screens (the `:app` exclusion above):
   - "swipe to Playlists, +, create" becomes a `PlaylistsTab` create-flow smoke test in `:feature:playlists`;
   - "Folders, open, lists songs" becomes a `FoldersTab` → `FolderDetail` content smoke test in `:feature:library`;
   - "queue sheet, tap an item" becomes a `QueueSheet` smoke test in `:feature:player`.

   Cross-module navigation is not instrumented: the JVM `NavHost` tests from 6a and 6b cover it (a stated deviation from §15, for the reason above).
6. **Baseline profile.**
   - The generator and benchmarks run on the managed devices (`pixel6Api31` for generation, `pixel6Api31Frames` for frame benchmarks), never on the user's phones.
   - The journey gains Home tab swipes and an A–Z rail jump on Tracks, the Phase 5 carry-over, so the rail's first-jump composition is profiled.
   - v1.0 numbers: the benchmarks run on tag `v1.0.0` in a temporary `git worktree` (removed afterwards) and on this branch, on the same managed device. A startup median regression of more than 15 %, or a frame-duration P90 regression of more than 15 %, is a finding for the final review, not a stop.
7. **What is fixed and what is backlog.**
   - Fixed in this phase: carry-overs a user can see or hear, and gate hygiene that would hide a regression (Tasks 4–8).
   - Recorded in CLAUDE.md "What remains" as backlog (Task 9): pure test-hygiene minors from the ledgers (test names, duplicated helpers, unpinned KDocs, environment log noise), with their ledger line.
   - The full triage table is in Task 9.
8. **Earlier rulings honoured on the files this phase touches** (observation 0019). Each task's brief carries the ledger lines it must read:
   - Phase 5 rulings 9 and 10: the rail is drawn above the list and its slots;
   - Phase 5's Playlists `railTopPadding`;
   - 6a ruling P2: `destructiveText`;
   - 6b rulings 3–6: Search insets and content; P4: the `SongListItem` checkbox stays a selection affordance;
   - 6c rulings P12 and P14: the widget's modes.

## Review Focus

1. **A calibrated token that also feeds a screen with no reference** (Search, the tag editors, the sheets) moves those screens too. Task 3 re-runs every screen's JVM geometry and pixel tests, and Task 11's device walk covers the screens with no reference.
2. **An "Unknown" artist in the Artists tab and on ArtistDetail.** The row shows "Unknown", and tapping it still opens the right artist, keyed by the raw `<unknown>`. Pinned by `ArtistRowUnknownTest` and `ArtistDetailRouteUnknownTest` (Task 4).
3. **Themed icons on Android 13+.** With the user's themed-icons setting on, the launcher draws the monochrome layer. That is a device setting the controller does not change, so the user is asked to look (Task 11). JVM: `LauncherIconTest` pins that the monochrome layer exists.
4. **Search re-entry.** Coming back to Search with a query puts the cursor at the end of the query, and the keyboard does not cover the last result. Pinned by `SearchScreenTest.reEntryPutsTheCursorAtTheEnd` (Task 5), and seen on the device (Task 11).
5. **Airplane mode, end to end.** Play, every tab, folder detail, the queue sheet, EQ, settings, the widget, and process-death restore of the selected tab, with no network. Device-only (Task 11), with the user toggling airplane mode.

## Files

| Area | Task | Files |
|---|---|---|
| Probes and measurement data | 0, 1 | `docs/design/oneui-measurements.md`; `RollaDimens`/`Type.kt` only if the Samsung's density ≠ 480 |
| Calibration | 2, 3 | evidence in `docs/design/calibration/phase7/` (git-ignored by the user's commit rule); `:core:designsystem` `theme/` tokens; spec §5 tables |
| App identity, unknown artist, copy | 4 | `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml` (new), `res/mipmap*`, `res/drawable/ic_launcher_*`; `core/model/.../ArtistNames.kt` (new); the display sites (inventory in Task 4); `feature/settings/.../PrivacyScreen.kt` |
| Search and editor polish | 5 | `feature/search`, `feature/tageditor`, `core/designsystem` (`editorFieldGap`) |
| Rail polish | 6 | `core/designsystem/.../component/FastScrollRail.kt`, `FastScrollContainer` |
| Kit and screen polish | 7 | `core/designsystem` (`OneUiCheckbox`, F2 light tokens), `core/ui/SongListItem.kt`, the name dialogs, `app/.../home/HomeScreen.kt` (cold-start), `feature/equalizer/.../EqualizerResponseCurve.kt` |
| Build hygiene and smoke suite | 8 | `build-logic/`, `config/detekt/detekt.yml`, `gradle/libs.versions.toml`, new androidTests in `feature/playlists`, `feature/library`, `feature/player` |
| Docs | 9 | §16 files plus CLAUDE.md "What remains" |
| Baseline profile | 10 | `baselineprofile/`, `app/src/release/generated/baselineProfiles/baseline-prof.txt` |
| Final gate | 11 | none (controller) |

---

### Task 0: Device probes (controller, read-only)

- [ ] **Step 1 (Samsung).**
  - Ask the user to connect the Samsung SM-S901B with USB debugging on.
  - `adb devices -l` names its serial. With that `ANDROID_SERIAL`, run `wm density`, `wm size`, `settings get system font_scale` and `getprop ro.build.version.sdk`.
  - No other command, no install, no input.
  - Record all four in the ledger and in `oneui-measurements.md` §"How these were taken". The Motorola's values are known: 480, 1220 × 2712, 1.0, 36.
- [ ] **Step 2 (managed device).** Run `./gradlew :baselineprofile:pixel6Api31Setup` (it downloads the system image if needed: dev tooling, not app runtime). Expected: BUILD SUCCESSFUL. If it fails, rule on whether Task 10 runs on the Motorola instead, which is allowed only for benchmarks that do not uninstall the app; record the reason.
- [ ] **Step 3 (connected-test safety).**
  - `find . -path '*/src/androidTest' -not -path '*/build/*'` must list only library modules: `core/common`, `core/database`, `core/ui`, `feature/library`, `feature/player`, `feature/search`, and after Task 8 also `feature/playlists`.
  - Any `app/src/androidTest` stops Task 11's connected step until it is ruled on.

### Task 1: Rescale the measurements (m3-design-system-agent, only if needed)

- [ ] **Step 1.** If the Samsung's density is 480 and its font scale is 1.0, the dp and sp columns already hold. Record "density confirmed" in the measurements doc and mark the task complete with no code change.
- [ ] **Step 2 (otherwise).** Use d = density / 160 and f = font scale. Recompute every dp column (dp = px / d) and every sp value (sp = px / d / f) in the measurements doc. Then recompute every `RollaDimens` and `Type.kt` value that the spec §5 tables mark as measured.
  - "Provisional" or "unmeasured" values and the documented exceptions (the `eqColumnPitch` touch rule, `minTouchTarget`) stay.
  - Each changed value updates its spec §5.2/§5.4 row with "(calibrated 2026-10-27 at d = …)".
- [ ] **Step 3 (test).** `TokenInvariantsTest`, `ContrastTest`, and every module's `testDebugUnitTest` pass. A changed token that moves a pixel or geometry test is fixed in that test's expected value only when the test's own comment ties the value to the token. Otherwise it is a finding for the review.

### Task 2: Capture and compare (controller, device)

**Screens (§14 step 3):** Tracks tab, Playlists tab, Folders tab, Now Playing, Equaliser, Settings (top) and the widget, all on the Motorola in the dark theme.

- [ ] **Step 1.** Install the current build and confirm `firstInstallTime`.
  - Navigate to each screen by tapping, with a focus check before every input.
  - Capture with `adb exec-out screencap -p` into `docs/design/calibration/phase7/ours-<screen>.png`.
  - Now Playing is captured while a track plays, and the widget at its default one-row size.
- [ ] **Step 2.** Measure each element that `oneui-measurements.md` lists for that screen.
  - Use the same methods the doc defines (avg, max and bluest colour; scanned geometry; text glyph boxes), with the scratchpad scripts `measure_widget.py`/`scan_widget.py` generalised into `calibrate.py`. That script is controller tooling in the workspace, never committed.
  - Convert per ruling 1.
- [ ] **Step 3.** Write `calibration-diffs.md` (ruling 2): every element, our dp or colour, the reference value, the tolerance, and PASS or OUT. For every OUT row, name the owning token and the proposed value. The table and the side-by-side overlays (reference | ours | 50 % blend, §14 step 5) go in `docs/design/calibration/phase7/`.

### Task 3: Calibration fixes (m3-design-system-agent)

**Brief:** `calibration-diffs.md` (OUT rows only) plus ruling 2.

- [ ] **Step 1.** For each OUT row, change the token, not the screen. Update the spec §5 row and the measurements doc.
- [ ] **Step 2.** Re-run `:core:designsystem:testDebugUnitTest` (`TokenInvariantsTest`, `ContrastTest`). A pairing under its floor reverts that row and records it as an accepted substitute.
- [ ] **Step 3.** Re-run every module's `testDebugUnitTest`. Each test whose expected geometry changed with a token cites the token in its diff.
- [ ] **Step 4 (controller).** Re-capture the changed screens and re-measure the OUT rows. Every row is now PASS or recorded as accepted. This is the device gate: an OUT row that stays OUT opens one fix round.

### Task 4: App identity, unknown artist and copy (ui-builder; the manifest and resources are folded in)

**Interfaces:**
- Produces: `fun artistDisplayName(raw: String): String` in `core/model/src/main/kotlin/com/rolla/musicplayer/core/model/ArtistNames.kt`. It is pure, with `UNKNOWN_ARTIST_LABEL = "Unknown"` and `MEDIASTORE_UNKNOWN = "<unknown>"` as internal constants.
- Inventory (grep `\.artist\b` over `core/ui feature app core/media` main sources, run at planning; the implementer re-runs it and adds any new hit). Display sites:
  - `core/ui`: `AlbumCard`, `AlbumRow`, `SongListItem`, and `ArtistRow` (`Artist.name`);
  - `feature/library`: `AlbumDetailScreen`, `ArtistDetailScreen` (the title);
  - `feature/player`: `MiniPlayer`, `NowPlayingScreen`, `QueueSheet`;
  - `feature/widget`: `WidgetStateProvider`, which maps into `MusicWidgetState.artist`;
  - `core/media`: `PlaybackService`/`Queue`, the `MediaMetadata.artist` shown by the notification and the System UI card.

  Excluded, because they are not display: `PlayerViewModel` (navigation to `ArtistDetail(raw)`) and the tag editor files.

- [ ] **Step 1: Failing tests.**
  - `ArtistNamesTest` (JVM):
    - `artistDisplayName("<unknown>") == "Unknown"`;
    - `artistDisplayName("") == "Unknown"`;
    - `artistDisplayName("  ") == "Unknown"`;
    - `artistDisplayName("Sade") == "Sade"`;
    - control: `artistDisplayName("<Unknown> band") == "<Unknown> band"`, which is not an exact match.
    - Mutation: `contains("unknown")` turns the control RED.
  - `ArtistRowUnknownTest` (`:core:ui`, Robolectric):
    - an `Artist(name = "<unknown>")` row shows the text "Unknown" and not "<unknown>";
    - control: an `Artist(name = "Sade")` row shows "Sade".
  - `ArtistDetailRouteUnknownTest` (`:app`, the 6a `artistDetailDestination` NavHost harness): tapping the "Unknown" row on the Artists tab content navigates to `ArtistDetail(artistName = "<unknown>")`. Mutation: route with the display name, and the route arg differs.
  - `WidgetStateProviderTest`: a holder song whose artist is `<unknown>` maps to `artist == "Unknown"`.
  - `LauncherIconTest` (`:app`, Robolectric, SDK 34):
    - `R.mipmap.ic_launcher` loads as an `AdaptiveIconDrawable` with non-null background, foreground and monochrome layers;
    - `context.getColor(R.color.ic_launcher_background) == 0xFF2F6FF0`;
    - the application label from `PackageManager` is "Rolla Music".
  - `InfoScreensTest`: the Privacy copy contains "equaliser presets" and not "equalizer".
- [ ] **Step 2.** Run them. Expected: RED (unresolved symbols, then assertion failures).
- [ ] **Step 3.** Implement ruling 3 at every inventory site, ruling 4's resources and label, and the Privacy copy "equaliser presets".
- [ ] **Step 4.** Re-run the tests; then run the gate for the touched modules.
- [ ] **Step 5.** Acceptance greps, where the inventory and acceptance commands are the same:
  - `grep -rn 'android:label="RollaMusicPlayer"' app/src/main` gives 0 hits;
  - `grep -rln "\.artist\b" --include=*.kt core/ui/src/main feature/*/src/main core/media/src/main | grep -v /build/` gives every listed file, each with its `artistDisplayName` call or a one-line note saying why not.

### Task 5: Search and tag-editor polish (ui-builder)

Ledger lines to read first: 6b `progress.md` lines 52, 61–62, 66, 72–73, 81, 98, 105 and 115. 6b rulings 3–6 bind.

- [ ] **Step 1: Failing tests.**
  - `SearchScreenTest.reEntryPutsTheCursorAtTheEnd`: render with the query "jazz", recreate (state restoration) and assert the field's `TextRange` selection is `TextRange(4)`. Control: before re-entry, typing "x" appends at the end. Mutation: the plain-`String` field (the cursor at 0) turns it RED. The implementation moves the field to `TextFieldValue` state held in the screen, with the ViewModel query unchanged.
  - `SearchViewModelTest.aFailingSearchShowsEmptyNotCrash` (6b final review Minor 7, ViewModel exception containment): the fake repository throws `IllegalStateException` on search, and the state becomes `Empty` for that query while the flow keeps collecting (a second query still works). Control: without the throw it is `Results`.
  - `TagEditorViewModelTest.aReadFailureShowsTheErrorState`: a reader that throws gives `Error("Couldn't load this track's tags.")`, not a crash. Check first whether this already exists; if so, cite it and skip.
  - `BatchTagEditorScreenTest.switchStaysCentredOnAnErrorRow` (6b T4 M4): render a row in error. The switch's vertical centre equals the field's text-box centre, ±1 dp, measured from the bounds under `@GraphicsMode(NATIVE)`. Control: a row without an error shows the same.
- [ ] **Step 2.** Add the `editorFieldGap` token (8 dp, 6b T4 M3) and replace the borrowed `settingsHeaderGap` in both editors. Remember the `OneUiTextField` colours (6b deferral). Fix the stale `sheetTrackedSongs` KDoc (6b L1). Fix the Empty → Loading → Empty one-frame blank on refinement from Empty (6b L2): keep showing Empty until the new result arrives, with a test that flips the query from one empty result to another and asserts "No results" never disappears (the frame-by-frame state list never contains Loading after Empty).
- [ ] **Step 3.** Run green, then the gate for `:feature:search`, `:feature:tageditor` and `:core:designsystem`.

### Task 6: A–Z rail polish (ui-builder; compose-performance-auditor reviews)

Ledger lines to read first: Phase 5 `progress.md` lines 73, 76, 84, 97, 113 and 143–145. Phase 5 rulings 9 and 10 bind.

- [ ] **Step 1: Failing tests** (`FastScrollRailTest`).
  - `dragSelectsTheLetterUnderTheDownPoint` (T4 M4): a drag that starts on "M" selects "M" first, not the letter at the touch-slop point. The target is sized from `ViewConfiguration.scaledTouchSlop` (the Robolectric slop is 16 dp; see the memory gotcha). Control: a tap on "M" selects "M". Mutation: the current slop-point behaviour turns it RED.
  - `endLabelsStayInsideTheRail` (T4 M7): a 96 dp rail at fontScale 2.0, rendered under `@GraphicsMode(NATIVE)`, keeps the "#" and "Z" glyph bounds inside the rail's pill bounds. Control: fontScale 1.0 also passes. Mutation: `wrapContentHeight(unbounded = true)` without clipping turns it RED.
- [ ] **Step 2.** Implement:
  - the down-point recorder (an Initial-pass pointer down);
  - end labels clipped or inset to the pill;
  - `FastScrollContainer` measured with `Modifier.layout` instead of `BoxWithConstraints` (T5 M10). The existing container tests stay green; a test that the list composes outside a subcomposition is optional, and if it is not feasible, the auditor's review covers it.
  - The Playlists rail sliver on short viewports (T5 fix 1): the rail is hidden when the space below the cards is less than `fastScrollMinRailHeight`, with a test at a 640 dp screen showing the rail gone and at a 900 dp screen showing it present.
- [ ] **Step 3.** Gate for `:core:designsystem`, `:feature:library` and `:feature:playlists`. A compose-performance-auditor pass is part of the review.

### Task 7: Kit and screen polish (m3-design-system-agent for the kit, ui-builder for the screens)

Ledger lines to read first: 6a `progress.md` lines 136 and 151–154; 6b ruling P4 (line 93).

- [ ] **F2 (light theme).** These pairings are nearly invisible:
  - `artworkPlaceholder` `#E6E6E8` on the sheet's `#E8E8EA`: 1.02:1;
  - the dialog field `surfaceContainerHighest` `#EBEBED` on `#E8E8EA`.

  Fix: a new token `sheetArtworkPlaceholder`, light `#FFFFFF`, dark equal to `artworkPlaceholder`. Computed: the white box against the `#E8E8EA` sheet is 1.22:1, against 1.02:1 today, and its glyph `artworkPlaceholderGlyph` `#7A7A7E` on white is 4.27:1, which clears 3:1. A darker grey box was rejected: `#D5D5D8` gives only 1.20:1 against the sheet and 2.92:1 for the glyph, under 3:1. Sheets and dialogs use the new token. The dialog field uses `surfaceContainerLowest` (`#FFFFFF`, 1.22:1 against the dialog) in light. Add both pairings to `ContrastTest`.
- [ ] **F3.** Name dialogs (`PlaylistNameDialog`, save preset) focus the field and open the keyboard on show. Test: the field has focus after `setContent`. The Robolectric `@LooperMode(LEGACY)` caveat from the memory applies.
- [ ] **`OneUiCheckbox`** (6b P4): a kit checkbox. It is round, `primary` when checked, `outline` when unchecked, 20 dp with a 48 dp target, and `Role.Checkbox`. `SongListItem`'s selection checkbox uses it, and no direct M3 `Checkbox(` is left outside the kit (acceptance grep `grep -rn "Checkbox(" --include=*.kt core/ui feature app | grep -v build` gives 0). Contrast: `primary` on `surfaceContainer` is 3.97:1 (non-text, ≥ 3); `outline` `#5A5A5C` on `#171719` is 2.60:1, which is under 3. So the unchecked ring uses `onSurfaceVariant` (6.45:1). Pin both in `ContrastTest`.
- [ ] **Cold-start "No music found" flash** (6a deferral):
  - Home shows its empty state only after the library's first emission.
  - Test (Robolectric): a never-emitting fake library shows no "No music found" node; after it emits an empty list, the node shows.
  - Control: an emitting fake shows rows.
- [ ] **`PreviewCurveHeight`** (`EqualizerResponseCurve.kt:184`): a token, or `RollaDimens.eqSliderLength / 3` if that is exact. Acceptance grep: `grep -rn "private val .* = [0-9.]*\.dp" --include=*.kt app core feature | grep /main/ | grep -v core/designsystem` gives 0.
- [ ] Gate for the touched modules.

### Task 8: Build hygiene and the smoke suite (build-tooling-agent for the build, test-writer for the tests)

- [ ] **The annotation-target warning** (Phase 5 T2 M1). Add `-Xannotation-default-target=param-property` to the convention plugins' Kotlin compiler options. Acceptance: `./gradlew assembleDebug 2>&1 | grep -c "annotation-default-target\|This annotation is currently applied"` gives 0.
- [ ] **The CDS warning** (6b T5 M1). Add `jvmArgs("-Xshare:off")` to unit-test tasks in the Robolectric convention plugin. Acceptance: no "Sharing is only supported" line in a `testDebugUnitTest` log.
- [ ] **The inline detekt plugin version** (6b T6 M4) moves to the version catalog.
- [ ] **Fully qualified icon references** (6b T6 M2) cannot hide from `ForbiddenImport`. Add a detekt `ForbiddenMethodCall`, or a custom `ForbiddenImport` pattern on `androidx.compose.material.icons`, that also catches fully qualified references. Test: a fixture file in the plugin's test sources with `androidx.compose.material.icons.Icons.Default.Add` fails detekt, and the same file with a `RollaIcons` reference passes.
- [ ] **Smoke suite (ruling 5).** Add:
  - `PlaylistsTabSmokeTest` (`:feature:playlists`): + → name → Create → the new row shows;
  - `FoldersSmokeTest` (`:feature:library`): the Folders tab content → tap a folder → FolderDetail lists its songs;
  - `QueueSheetSmokeTest` (`:feature:player`): open → tap the second item → the fake controller receives `seekTo(index = 1)`.

  Each one uses fakes from `:core:testing`, and no MediaStore or real database. They compile with `compileDebugAndroidTestKotlin` here and run in Task 11.
- [ ] Gate: `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug`, plus `compileDebugAndroidTestKotlin` for every module with an androidTest.

### Task 9: Docs (spec §16) and the backlog record

- [ ] **`ui-style-guide.md`.** Rewrite §2–§8 to the spec's tokens, dimensions, components and screen blueprints. Point to spec §5/§6 for values, with no stale hex. Remove "never 'Tracks'" and state the UI-label exception. Record the two touch-target exceptions (already there) and the widget (§8, already noted).
- [ ] **`model-vocabulary.md`.** Add a "UI label exception" section ("Tracks" in UI, `Song` in code), plus `Folder`, `Artist.artworkUri`, and the display rule `artistDisplayName` (ruling 3).
- [ ] **`navigation-conventions.md`.** Rewrite the examples to the real graph:
  - the `Home` start destination;
  - no bottom nav;
  - `SongDetail`-style names for route examples;
  - `FolderDetail`, the `openTagEditor` helper and the `NavGraphBuilder` destination pattern.

  Remove the "examples predate" banner.
- [ ] **CLAUDE.md.**
  - Move the three "Post-1.0 cuts" (tab bar, Folders, A–Z rail) to shipped.
  - Rewrite the `:feature:library` and `:core:media` blurbs: tabs, folders, queue, `MediaLibraryService`.
  - Room is v5 (state it; the spec's "DB v4" item is superseded by the queue-restore phase).
  - Add the new screens and the app name and icon.
  - Collapse the "One UI redesign in progress" block into a "shipped" summary.
  - Update "Last Updated" and Status.
  - Under "What remains", list the backlog of ruling 7 (the triage table below).
  - Every completeness sentence is worded to the grep or the device fact behind it (observation 0020).
- [ ] **`oneui-measurements.md`.** The real densities and font scales (Task 0), and the calibration results table (Task 2/3).
- [ ] **Roadmap row 7.** Link this plan, and set the status line after Task 11.
- [ ] **Triage table** (ruling 7). Fixed here: Tasks 4–8. Backlog (CLAUDE.md "What remains"), each with its ledger pointer:

  | Ledger | Items |
  |---|---|
  | Phase 5 | T1 M1, M2; T2 M3; T3 M1, M2, M4; T4 M1, M6; T5 M3, M6–M9 |
  | 6a | T1 M1–M6; T2 M2–M6; T3 M1–M5; T4 M1–M3; T5 M2, M4–M7; T6 M1–M5; T7 M1, M2 |
  | 6b | T1 M5; T2 M3–M6; T3 M3–M5; T5 M3; the WhileSubscribed re-record |
  | 6c | ±15 not pinned as stroked; preview `VectorRaster` lint; ink mask colour; widget layout values unpinned (M-4); clearance zero margin |

  The ±15 glyph weight (21 vs 18 dp) is fixed in Task 3 if calibration flags it; otherwise it goes to the backlog.

### Task 10: Baseline profile and benchmarks (build-tooling-agent)

- [ ] **Step 1.** `LibraryJourney` gains Home tab swipes (Tracks → Albums → Artists → Folders → back) and one A–Z rail jump on Tracks. It uses the `song_list` resource id and the rail's semantics node "Fast scroll, letter index" with its "Jump to M" action through UiAutomator. The `MediaStoreSeeder` fixture must hold at least 60 tracks across 10 or more letters so that the rail shows.
- [ ] **Step 2.** `./gradlew :baselineprofile:pixel6Api31BenchmarkReleaseAndroidTest -P android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile` regenerates `app/src/release/generated/baselineProfiles/baseline-prof.txt`. The diff must contain rail and tab-row classes (`FastScrollRail`, `OneUiTabRow`).
- [ ] **Step 3 (ruling 6).** Run `StartupBenchmark` and `ScrollBenchmark` on this branch and on `v1.0.0` (in a temporary worktree, removed afterwards) on `pixel6Api31Frames`. Write the medians and P90s to `benchmarks-v1-vs-phase7.md` in the workspace, and the summary line to the roadmap row.

### Task 11: Final gate and device walk (controller)

- [ ] **Step 1 (gate).** Run:
  - `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug assembleRelease checkReleaseManifestNoInternet`;
  - `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew connectedDebugAndroidTest`. Library modules only (Task 0 step 3 re-run first).

  Quote each result line and the test counts.
- [ ] **Step 2 (device walk on the Motorola, benchmark build).** Install it and confirm `firstInstallTime`. Then check:
  - each Home tab, FolderDetail, the queue sheet, the Equaliser, Settings, Search, a tag editor (Cancel only) and the widget;
  - the launcher icon and label (screenshot of the home page);
  - an "Unknown" artist where the library has one;
  - Search re-entry with the cursor at the end (Review Focus 4).

  The user then:
  - looks at the themed icon, if their themed-icons setting is on (Review Focus 3); nothing is changed for them;
  - turns airplane mode on, and the controller repeats play, tabs, folder detail, queue, EQ, settings and the widget (Review Focus 5);
  - the controller swipes the app away from recents (asking first), reopens it, and checks that the selected tab and the queue restore;
  - turns airplane mode off.
- [ ] **Step 3.** Ledger: PASS/FAIL per check with screenshots in `docs/design/calibration/phase7/` (other apps' notifications removed). A FAIL opens one fix round. Roadmap row 7 then reads "Accepted …; pending commit".

## Mutation and control table

| Test | Trivial stub | Failing assertion (same test) | Mutation | Why the value differs |
|---|---|---|---|---|
| `ArtistNamesTest` | identity | `"<unknown>"` gives `"Unknown"` | `contains("unknown")` | the control `"<Unknown> band"` becomes `"Unknown"` |
| `ArtistRowUnknownTest` | raw name | text "Unknown" shown | skip the mapping in `ArtistRow` | "<unknown>" is shown |
| `ArtistDetailRouteUnknownTest` | — | route arg `"<unknown>"` | route with `artistDisplayName(name)` | the arg is "Unknown" |
| `LauncherIconTest` | default robot icon | the monochrome layer is not null | drop `<monochrome>` | null layer |
| `reEntryPutsTheCursorAtTheEnd` | a `String` field | `TextRange(4)` | the `String` field | the selection is 0 |
| `aFailingSearchShowsEmptyNotCrash` | — | the second query gives `Results` | no `catch` | the flow terminates; no second emission |
| `switchStaysCentredOnAnErrorRow` | — | centre delta ≤ 1 dp | the old `Alignment.Top` | the delta equals the supporting text height |
| `dragSelectsTheLetterUnderTheDownPoint` | — | first selection "M" | the current slop-point code | the first selection is the next letter |
| `endLabelsStayInsideTheRail` | — | "Z" glyph bottom ≤ pill bottom | `unbounded = true` without clip | the glyph extends past |
| Playlists rail hidden on short screens | — | rail absent at 640 dp | no height check | rail present |
| Cold-start no flash | always show empty | no "No music found" before the first emission | `initialValue = emptyList()` | the node shows |
| F3 focus | — | the field is focused | no `requestFocus` | not focused |
| Fully qualified icon detekt fixture | — | detekt fails on the fixture | old rule | detekt passes |

## Self-review

- **Spec coverage.**
  - §1 criteria: calibration in Tasks 0–3; "no hardcoded value" by the acceptance greps in Tasks 4 and 7; cut features shipped (done earlier, docs in Task 9); dead controls (Task 11 walk); gates (Task 11).
  - §14 steps 1–5: Tasks 0–3.
  - §15: unit tests are already in place; instrumented tests in Task 8 and Task 11 (smoke suite with the ruling 5 deviation); the baseline in Task 10; the final gate in Task 11.
  - §16: Task 9.
  - §17 row 7 agents: m3-design-system-agent, build-tooling-agent, code-reviewer, test-writer.
- **Step scan.** The calibration fixes cannot be written before measuring, so Task 3's brief is generated from Task 2's table (ruling 2). Every other code step names files, signatures, tokens and tests.
- **Type consistency.** `artistDisplayName(raw: String): String`, `editorFieldGap`, `sheetArtworkPlaceholder` and `OneUiCheckbox` are named the same everywhere.
- **Review Focus.** Five lines, each with its owning test or device step.
- **Proportion.** Long, because the phase merges calibration, about 20 carry-overs, docs, performance and the gate. Code blocks are limited to tables.
- **superpowers-extras pre-flight.**
  - **1** (phases): written after 6c was committed (8f4ce3a), from the landed code and the four ledgers.
  - **2a** (values):
    - computed with a script this session: white on `#2F6FF0` 4.51:1; the light sheet placeholder (white vs `#E8E8EA` 1.22:1, its glyph 4.27:1; the first draft's `#D5D5D8` failed at 2.92:1 for the glyph); `outline` on the panel 2.60:1, which drove the `onSurfaceVariant` choice at 6.45:1;
    - pairs not computed here go through `ContrastTest`, with the floor as the stop.
  - **2b** (consumers): the inventories for the artist display sites and the label, each with its grep.
  - **2c** (test level): §15's cross-module instrumented paths become library-module and JVM `NavHost` tests, because `:app` connected tests uninstall the app (Global Constraints). Airplane mode, themed icons and a large font are device-only.
  - **2d:** the mutation table.
  - **3** (framework-validated output): `LauncherIconTest` loads the real adaptive icon through `Resources`, and the detekt fixture runs through detekt.
  - **4** (mutations are hypotheses): each one is valid usage; implementers may substitute per the rules.
  - **5** (device): every tap names its focus check; the user owns airplane mode, the theme, the font and themed icons; process death goes by swipe from recents, asked first. Before the device task, Task 0 reads what later rulings consume: density and font scale, managed-device availability, and the androidTest module list (observation 0023).
- **Open observations applied:** 0016 (the invariant row rule, in the table), 0017 (visible outcomes by render or device), 0018 (the contrast values above), 0019 (ruling 8 and the per-task ledger lines), 0020 (inventory = acceptance greps), 0022 (no new text-entry screen; Search's inset owner is unchanged), 0023 (Task 0 reads the consumed quantities).
