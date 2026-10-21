# One UI redesign, Phase 6a: detail screens, sheets, dialogs and menus — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AlbumDetail, ArtistDetail and PlaylistDetail (user and smart) are rebuilt on the One UI kit, like FolderDetail. Every sheet, dialog and menu in the app is drawn by one set of new kit components. The ⋮ on album and artist tracks, which does nothing today, opens the shared options sheet.

**Architecture:**
- **New kit components in `:core:designsystem`.** `OneUiBottomSheet`, `OneUiDialog`, `OneUiTextButton`, `OneUiDropdownMenu`/`OneUiMenuItem`, `OneUiTextField` and `SheetActionRow`, plus a `Delete` icon and a `detailArtworkSize` token. They capture the styling Phase 3 hand-applied to the queue sheet, the theme and preset dialogs and the Now Playing menu, so every surface reads from one place.
- **Shared sheets in `:core:ui`.** The options sheet, the add-to-playlist sheet and the playlist-name dialog move onto the kit. The options sheet gains an optional "Remove from playlist" action and closes itself when its track leaves the list.
- **Detail screens.** The three screens use `OneUiDetailTopBar`, a `ContentPanel` with a hero header (`DetailArtworkHeader`, new in `:core:ui`), a `SortHeader` with Shuffle and Play, and rows whose ⋮ opens the options sheet.
- **Playlist creation helper.** One helper in `:core:data`, `createPlaylistWithSong`, replaces the four copied "create a playlist and add this track" bodies.
- **Bottom padding.** The detail screens stop being wrapped in `PushedScreen` and pad their own lists for the pill, as FolderDetail does.

**Tech Stack:**
- Kotlin 2.2.21.
- Compose BOM 2024.09.03 (Foundation 1.7.3, M3 1.3.0).
- Hilt.
- JUnit4, MockK and Turbine.
- Robolectric 4.13, with NATIVE graphics. `:core:testing`'s `PixelCapture` (`drawRoot`, `drawLatestDialog`, `captureWindow`, `assertColorNear`) for visual assertions.

**Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md).
- §5 tokens; §5.5 icons; §6 kit; §7.3 the pill inset; §8.7 other screens (binding for this phase); §12 no dead controls; §15 tests and gates.
- §17 row 6 exit criterion: "No Material icon or hardcoded value outside the documented exceptions."

The roadmap ([`2026-10-06-oneui-redesign-roadmap.md`](2026-10-06-oneui-redesign-roadmap.md)) splits Phase 6 into 6a (this plan), 6b (Search, the tag editors, About/Licenses/Privacy, the permission screen) and 6c (the widget), by the user's choice on 2026-10-18. Carry-overs come from the 2, 4a, 4b and 5 SDD ledgers and are listed under Rulings.

## Global Constraints

- **Platform:** minSdk 24, targetSdk 34, compileSdk 36.
- **Copy:** UI copy says "Tracks", never "songs", for counts and empty states (the model stays `Song`). British spelling throughout ("Favourites", "colour").
- **Blue text:** blue text uses `accentText`, never `primary`. Every text button goes through `OneUiTextButton`, which uses `accentText`, or `error` when destructive.
- **Icons:** icons come from `RollaIcons` only. No `androidx.compose.material.icons` import may remain in any file this phase touches.
- **No hardcoded visuals:** no hardcoded colours, type, shapes or dimensions outside `:core:designsystem`. New values become tokens first. A private `val … = N.dp` in a touched feature or `:core:ui` file is a breach.
- **No dead controls:** every visible button does something real (spec §12).
- **Offline only:** no network APIs or dependencies.
- **Tests:** tests the phase touches are updated, never deleted to go green.
- **Mutation-proof policy.**
  - Every new test names the mutation that turns it RED. The implementer applies it, sees RED, and reverts byte-exact.
  - A mutant counts as equivalent only if *no observable output changes*. If the output changes but the test can't see it, the test is blind: move its probe.
  - A named mutation is a hypothesis about the framework. It must be valid usage: a mutation that throws inside Compose or Room is RED for the wrong reason. If it proves equivalent, show the evidence and substitute the closest fail-able mutation.
  - **Every test carries its own control** (observation 0016): an assertion inside the same test that fails under the trivial stub ("never render", "always empty", a constant). An absence test first shows the thing present, then absent. A control in a sibling test does not count.
  - **A test for a visible outcome observes the rendering** (observation 0017). Colours, clipping, shapes and sizes are asserted with `PixelCapture` or bounds, not inferred from semantics.
  - A RED quotes the Gradle `BUILD FAILED` line and the test's own assertion message. "No tests found" or a compile error is not a RED.
  - Fixtures must separate the quantities a test distinguishes.
- **Agents never** run `git commit`, `git add`, `git stash`, `git checkout`, `git reset` or `git clean`. Never use `sed -i` (Spotless enforces CRLF; use the editor). Never write Kotlin through shell heredocs.
- **New files:** an agent with no Write tool creates a one-line placeholder with `printf`, then fills it with Edit.
- **Spotless:** run the root `./gradlew spotlessApply` after edits, and **before** taking any mutation backup.
- **Plan code** was never compiled. Implementers may make mechanical fixes (imports, a renamed helper, an `@OptIn`, adapting to an existing test helper's real name) and must report each one. They may not change behaviour, a test's assertions or its fixture.
- `:core:testing` stays `testImplementation` only.

## Rulings (decided at planning; the spec is amended in Task 9)

1. **Phase 6 is split into 6a, 6b and 6c** (user decision, 2026-10-18). 6a owns the detail screens and every sheet, dialog and menu. The `ThemeModeDialog`, the preset dialog, the queue sheet and the Now Playing menu were already restyled in Phase 3; 6a only moves them onto the shared components.
2. **New kit components.** Each one captures what Phase 3 hand-applied:
   - `OneUiBottomSheet`: `surfaceContainerHigh`, the `large` shape with square bottom corners, `statusBarsPadding()` (the `QueueSheet` lesson) and an optional `sectionHeader` title.
   - `OneUiDialog`: `surfaceContainerHigh`, the `large` shape, a `titleLarge` title in `onSurface`, and text in `onSurfaceVariant`.
   - `OneUiTextButton`: `accentText`, or `error` when `destructive`.
   - `OneUiDropdownMenu` and `OneUiMenuItem`: `surfaceContainerHigh`, the `medium` shape, and `songTitle` items.
   - `OneUiTextField`: a filled field with no indicator line, the `medium` 16 dp shape, a container passed by the caller (default `surfaceContainerHigh`), and an `accentText` focused label and cursor.
   - `SheetActionRow`: a `singleLineRowHeight` row. A 24 dp icon is centred in a `listThumb` box at `listThumbStart`, and the label starts at `listTextStart` in `songTitle`. Destructive rows tint the icon and label `error`.
3. **The `error` colour as text.** Destructive buttons and rows use `colorScheme.error` as text. Task 1 adds its pairings with `surfaceContainerHigh` to `ContrastTest` (≥ 4.5:1 in both themes). If a pairing fails, the token is not changed in this phase: the task reports BLOCKED.
4. **New tokens and an icon:**
   - `RollaDimens.detailArtworkSize: Dp get() = npArtSize` (168 dp; there is no reference screenshot, so it is unmeasured until Phase 7 calibration).
   - `RollaIcons.Delete`, a line trash can. The spec §5.5 table gains it.
5. **Detail screen anatomy (spec §8.7):**
   - **Top bar:** `OneUiDetailTopBar(title)`.
   - **Panel:** `ContentPanel`, holding a `LazyColumn` whose bottom `contentPadding` is `homeListBottomPadding()`. The route is no longer wrapped in `PushedScreen` (Task 8), the FolderDetail precedent.
   - **Hero:** AlbumDetail and ArtistDetail start the list with a `DetailArtworkHeader` (artwork plus one subtitle line), then the `SortHeader` row, then the tracks. Both scroll with the list. PlaylistDetail has no artwork. Its `SortHeader` stays fixed above the list, because the drag-reorder logic indexes list items directly.
   - **Sort labels** (static; spec §3 rules out a sort menu), each describing the real order:

     | Screen | Label |
     |---|---|
     | Album | "Track number" |
     | Artist | "Album" (`ArtistDao` orders by album, then track) |
     | User playlist | "Custom order" |
     | Smart playlist: Recently added | "Date added" |
     | Smart playlist: Most played | "Play count" |
     | Smart playlist: Recently played | "Last played" |
     | Smart playlist: Favourite tracks | "Name" |

   - **Loading:** nothing renders while loading (the FolderDetail precedent), so there is no spinner flash.
6. **⋮ on detail rows opens the shared options sheet.** Today the album and artist rows' ⋮ does nothing, a §12 breach. The playlist ⋮ removes a track with one tap behind a "more" glyph.
   - Every detail ⋮ now opens `SongOptionsHost`: Add to playlist, Edit tags, and, for user playlists only, "Remove from playlist", which is destructive.
   - The sheet is the deliberate step, so Remove has no extra confirm.
   - The routes gain `onEditTagsClick: (Song) -> Unit`.
7. **`createPlaylistWithSong`.** A public extension in `:core:data` replaces the four copied "create a playlist and add this track" bodies, in Tracks, Favourites, FolderDetail and PlaylistDetail. AlbumDetail and ArtistDetail use it too. Blank names write nothing.
   - The 4a carry-over asked about error handling. Room write failures propagate, as every other repository write does; the KDoc says so. There is no app-wide error channel to report to.
8. **ArtistDetail's albums strip uses the library order** (spec §9.0; 4a L5). `observeArtistAlbums` sorts with `sortedByLibraryName` by title (tie-break: id) on `@CoreDataDefaultDispatcher`, like the Home lists.
9. **`AlbumCard` polish** (4a T4):
   - The card clips its ripple to `featureCard`, so the ripple is no longer a sharp rectangle.
   - `ArtworkThumbnail` treats a blank URI as missing (`isNotBlank`), matching the card.
   - The "card width" item is dropped, because no ledger text says what is wrong with it.
10. **The options sheet closes when its track leaves the list** (4b T7). `SongOptionsHost` takes `currentSongs: List<Song>?`. When the list no longer contains the open sheet's track, it calls `onDismiss`. The playlist sheet that follows from it is closed the same way.
11. **The add-to-playlist sheet** (Phase 2 carry-overs):
    - It gets the title "Add to playlist".
    - The "New playlist" entry becomes a `SheetActionRow` with `RollaIcons.Add`, followed by an `InsetDivider`, and its private dp constants go.
    - `PlaylistRow` gains `reserveRailSpace: Boolean = true`. The sheet passes `false`: there is no rail there, so the divider runs to the edge and the trailing count ends at `listThumbStart`. This is 2-L7.
12. **Deferred, with reasons:**
    - Scoping the `userPlaylists` collection to the sheet (2-L6) goes to Phase 7, as a performance item; it changes four tab signatures for no visible gain.
    - Hiding "+" and the selection bar while access is denied, and the `HomeTabPage` extraction (4b T8), go to 6b, which reworks the permission screen and Home's permission wiring.
    - `QueueSheetTest`'s private compose-view helper (4a T3) goes to Phase 7 with the `:core:testing` guard.
    - "Duplicate tracks in a playlist play from the first copy" (4a T3) is not reachable: `addSongs` never duplicates (`PlaylistRepositoryImplTest.addSongs_reAddingExistingSongDoesNotDuplicate`), and the lists are keyed by song id.
13. **`isReducedMotion` in PlaylistDetail** becomes `rememberReducedMotion()` (Phase 1 cross-phase rule).

## Review Focus

1. **The options sheet stays open after its track disappears** (a rescan or a tag edit removes it). The sheet must close by itself, not act on a track that is gone. Pinned in Task 2.
2. **Remove from playlist on a smart playlist.** A smart playlist (Most played, Favourite tracks) must never offer Remove, because it has nothing to remove from. Pinned in Task 7.
3. **A destructive or blue action drawn in the wrong colour.** "Delete" must be `error` and "Cancel" `accentText`, never `primary` (the 4a carry-over). The test checks the dialog's rendered pixels. Pinned in Tasks 1 and 7.
4. **Long titles in the hero and the sheet title.** A 200-character album or track title stays on one ellipsized line and never pushes the Shuffle and Play buttons off screen. Pinned in Tasks 5 and 2.
5. **The last track on a detail screen is hidden under the mini-player.** Every detail list's last row scrolls fully above the pill, because the screen is no longer wrapped in `PushedScreen`. Pinned in Tasks 5, 6 and 7.

## File map

| File | Task | Change |
|---|---|---|
| `core/designsystem/.../component/OneUiBottomSheet.kt` | 1 | new |
| `core/designsystem/.../component/OneUiDialog.kt` | 1 | new (`OneUiDialog`, `OneUiTextButton`, `OneUiTextField`) |
| `core/designsystem/.../component/OneUiMenu.kt` | 1 | new (`OneUiDropdownMenu`, `OneUiMenuItem`) |
| `core/designsystem/.../component/SheetActionRow.kt` | 1 | new |
| `core/designsystem/.../icon/RollaIcons.kt` | 1 | + `Delete` |
| `core/designsystem/.../theme/Dimens.kt` | 1 | + `detailArtworkSize` |
| `core/designsystem/src/test/.../component/OneUiSurfacesTest.kt` | 1 | new |
| `core/designsystem/src/test/.../theme/ContrastTest.kt`, `.../icon/RollaIconsTest.kt` | 1 | extend |
| `core/ui/.../SongOptionsSheet.kt`, `AddToPlaylistSheet.kt`, `PlaylistNameDialog.kt`, `PlaylistRow.kt`, `ArtworkThumbnail.kt`, `AlbumCard.kt` | 2, 6 | rework |
| `core/ui/.../DetailArtworkHeader.kt` | 2 | new |
| `core/ui/src/test/.../SongOptionsHostTest.kt`, `AddToPlaylistSheetTest.kt` (new), `DetailArtworkHeaderTest.kt` (new), `FeatureCardAndPlaylistRowTest.kt`, `AlbumCardTest.kt` | 2, 6 | extend / new |
| `core/data/.../repository/PlaylistWrites.kt` | 3 | new |
| `core/data/.../repository/ArtistRepositoryImpl.kt` + test | 3 | library order for `observeArtistAlbums` |
| `feature/library/.../TracksViewModel.kt`, `FolderDetailViewModel.kt`, `feature/playlists/.../FavouritesTabViewModel.kt`, `PlaylistDetailViewModel.kt` | 3 | use the helper |
| `feature/player/.../QueueSheet.kt`, `NowPlayingScreen.kt`, `feature/settings/.../ThemeModeDialog.kt`, `feature/equalizer/.../EqualizerScreen.kt`, `app/.../home/HomeScreen.kt` | 4 | adopt the kit |
| `feature/library/.../AlbumDetailScreen.kt`, `AlbumDetailViewModel.kt` + tests | 5 | rebuild |
| `feature/library/.../ArtistDetailScreen.kt`, `ArtistDetailViewModel.kt` + tests | 6 | rebuild |
| `feature/playlists/.../PlaylistDetailScreen.kt`, `PlaylistDetailViewModel.kt` + tests | 7 | rebuild |
| `app/.../MainActivity.kt` + `app/src/test/.../navigation/*` | 8 | wiring |
| spec, roadmap, CLAUDE.md, `ui-style-guide.md` | 9 | docs |

(`…` is `src/main/kotlin/com/rolla/musicplayer/<module path>`.)

---

### Task 1: Design system: sheet, dialog, menu, text field and action-row kit (m3-design-system-agent)

**Files:**
- Create in `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/`: `OneUiBottomSheet.kt`, `OneUiDialog.kt`, `OneUiMenu.kt`, `SheetActionRow.kt`.
- Modify: `.../icon/RollaIcons.kt` (`Delete`, and add it to `all`), `.../theme/Dimens.kt` (`detailArtworkSize`).
- Tests:
  - new `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/OneUiSurfacesTest.kt`;
  - extend `.../theme/ContrastTest.kt` (error on `surfaceContainerHigh`, both themes);
  - extend `.../icon/RollaIconsTest.kt` (count 28 → 29, and `Delete` is not auto-mirrored).

**Interfaces:**
- Consumes: the existing tokens (`surfaceContainerHigh`, `accentText`, `error`, `onSurface`, `onSurfaceVariant`, `sectionHeader`, `songTitle`, `RollaDimens.singleLineRowHeight`, `listThumb`, `listThumbStart`, `listThumbTextGap`).
- Produces (package `com.rolla.musicplayer.core.designsystem.component`):
  - `@Composable fun OneUiBottomSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit)`
  - `@Composable fun OneUiDialog(onDismissRequest: () -> Unit, title: String, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null)`
  - `@Composable fun OneUiTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false)`
  - `@Composable fun OneUiTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh, singleLine: Boolean = true, enabled: Boolean = true)`
  - `@Composable fun OneUiDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`
  - `@Composable fun OneUiMenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`
  - `@Composable fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false)`
  - `RollaIcons.Delete: ImageVector`
  - `RollaDimens.detailArtworkSize: Dp`

- [ ] **Step 1: Write the failing tests.** `OneUiSurfacesTest.kt` follows the `SlidersTest` / `OneUiSeekBarTest` setup:
  - `createAndroidComposeRule<ComponentActivity>()`;
  - `@Config(sdk = [34], qualifiers = "w360dp-h640dp")`;
  - `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.

  Each test carries its own control.

```kotlin
package com.rolla.musicplayer.core.designsystem.component

// Imports: follow OneUiSeekBarTest / SwitchAndChipTest (PixelCapture: drawRoot, drawLatestDialog, assertColorNear,
// pixelsIn; compose-ui-test finders; RollaMusicPlayerTheme; colorScheme token extensions).

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OneUiSurfacesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Spec §8.7 sheets: the title is a heading. Control: the content renders. Mutation: drop the title Text → RED.
    @Test
    fun bottomSheetShowsItsTitleAsAHeadingAboveItsContent() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiBottomSheet(onDismissRequest = {}, title = "Add to playlist") { Text("Row content") }
            }
        }
        composeRule.onNodeWithText("Row content").assertExists()
        composeRule.onNode(hasText("Add to playlist") and isHeading()).assertExists()
    }

    // Review Focus / 0017: the sheet's container is drawn in surfaceContainerHigh. The probe samples the sheet window
    // under the content. Control: the same probe in light theme gives the light token. Mutation: containerColor =
    // surfaceContainer → RED.
    @Test
    fun bottomSheetContainerIsSurfaceContainerHighInBothThemes() {
        listOf(true, false).forEach { dark ->
            var expected = Color.Unspecified
            composeRule.setContent {
                RollaMusicPlayerTheme(darkTheme = dark) {
                    expected = MaterialTheme.colorScheme.surfaceContainerHigh
                    OneUiBottomSheet(onDismissRequest = {}) {
                        Box(Modifier.fillMaxWidth().height(200.dp).testTag("sheet-body"))
                    }
                }
            }
            composeRule.waitForIdle()
            val bitmap = composeRule.drawLatestDialog()
            val body = composeRule.onNodeWithTag("sheet-body").getUnclippedBoundsInRoot()
            assertColorNear(expected, composeRule.pixelsIn(bitmap, body).let { it[it.size / 2] })
        }
    }

    // Spec §5.1: blue text is accentText, never primary. Destructive is error. Control: both buttons render their
    // label. Mutation: drop the colours (stock TextButton → primary) → the accent probe fails → RED.
    @Test
    fun textButtonsDrawAccentTextAndDestructiveError() {
        var accent = Color.Unspecified
        var error = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                accent = MaterialTheme.colorScheme.accentText
                error = MaterialTheme.colorScheme.error
                Column {
                    OneUiTextButton(text = "MMMMMM", onClick = {}, modifier = Modifier.testTag("accent"))
                    OneUiTextButton(text = "WWWWWW", onClick = {}, destructive = true, modifier = Modifier.testTag("danger"))
                }
            }
        }
        val bitmap = composeRule.drawRoot()
        val accentInk = composeRule.pixelsIn(bitmap, composeRule.onNodeWithTag("accent").getUnclippedBoundsInRoot())
        val dangerInk = composeRule.pixelsIn(bitmap, composeRule.onNodeWithTag("danger").getUnclippedBoundsInRoot())
        assertTrue("accent glyphs drawn", accentInk.any { isColorNear(accent, it) })
        assertTrue("destructive glyphs drawn", dangerInk.any { isColorNear(error, it) })
        assertFalse("destructive must not use the accent", dangerInk.any { isColorNear(accent, it) })
    }

    // The dialog draws its container in surfaceContainerHigh and shows its title. Control: the confirm button exists.
    // Mutation: containerColor = surfaceContainer → RED.
    @Test
    fun dialogUsesSurfaceContainerHighAndShowsTitleAndButtons() {
        var expected = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                expected = MaterialTheme.colorScheme.surfaceContainerHigh
                OneUiDialog(
                    onDismissRequest = {},
                    title = "Delete playlist?",
                    confirmButton = { OneUiTextButton(text = "Delete", onClick = {}, destructive = true) },
                    dismissButton = { OneUiTextButton(text = "Cancel", onClick = {}) },
                )
            }
        }
        composeRule.onNodeWithText("Delete").assertExists()
        composeRule.onNodeWithText("Delete playlist?").assertExists()
        val bitmap = composeRule.drawLatestDialog()
        val title = composeRule.onNodeWithText("Delete playlist?").getUnclippedBoundsInRoot()
        // A pixel just left of the title, inside the dialog's padding, is container, not text.
        val probe = composeRule.pixelsIn(bitmap, DpRect(title.left - 8.dp, title.top, title.left - 4.dp, title.bottom))
        assertColorNear(expected, probe[probe.size / 2])
    }

    // The menu shows its items in songTitle and calls onClick. Control: the menu is closed before expanded = true.
    // Mutation: OneUiMenuItem ignores onClick → clicks stays 0 → RED.
    @Test
    fun dropdownMenuItemClicks() {
        var expanded by mutableStateOf(false)
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    OneUiMenuItem(text = "Settings", onClick = { clicks++ })
                }
            }
        }
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
        composeRule.runOnIdle { expanded = true }
        composeRule.onNodeWithText("Settings").performClick()
        assertEquals(1, clicks)
    }

    // SheetActionRow: singleLineRowHeight tall, label starts at listTextStart, destructive tints error. Control: the
    // normal row's label is onSurface. Mutations: (a) height = listRowHeight → RED; (b) destructive ignored → RED.
    @Test
    fun sheetActionRowGeometryAndDestructiveTint() {
        var onSurface = Color.Unspecified
        var error = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                onSurface = MaterialTheme.colorScheme.onSurface
                error = MaterialTheme.colorScheme.error
                Column {
                    SheetActionRow(RollaIcons.Edit, "MMMMMM", onClick = {}, modifier = Modifier.testTag("plain"))
                    SheetActionRow(RollaIcons.Delete, "WWWWWW", onClick = {}, destructive = true, modifier = Modifier.testTag("danger"))
                }
            }
        }
        val plain = composeRule.onNodeWithTag("plain").getUnclippedBoundsInRoot()
        assertEquals(RollaDimens.singleLineRowHeight.value, (plain.bottom - plain.top).value, 0.5f)
        val label = composeRule.onNodeWithText("MMMMMM").getUnclippedBoundsInRoot()
        assertEquals(RollaDimens.listTextStart.value, label.left.value, 1f)
        val bitmap = composeRule.drawRoot()
        assertTrue(composeRule.pixelsIn(bitmap, label).any { isColorNear(onSurface, it) })
        val dangerLabel = composeRule.onNodeWithText("WWWWWW").getUnclippedBoundsInRoot()
        assertTrue(composeRule.pixelsIn(bitmap, dangerLabel).any { isColorNear(error, it) })
    }

    // OneUiTextField: no indicator line, and the container is the passed colour. Control: the label renders.
    // Mutation: keep the default TextField indicator → a 1–2 dp line in onSurfaceVariant at the field bottom → RED.
    @Test
    fun textFieldHasNoIndicatorLineAndUsesItsContainer() {
        var container = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                container = MaterialTheme.colorScheme.surfaceContainerHighest
                OneUiTextField(
                    value = "",
                    onValueChange = {},
                    label = "Playlist name",
                    containerColor = container,
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        composeRule.onNodeWithText("Playlist name").assertExists()
        val bitmap = composeRule.drawRoot()
        val field = composeRule.onNodeWithTag("field").getUnclippedBoundsInRoot()
        val bottomRow = DpRect(field.left + 24.dp, field.bottom - 2.dp, field.right - 24.dp, field.bottom - 1.dp)
        assertTrue("no indicator line", composeRule.pixelsIn(bitmap, bottomRow).all { isColorNear(container, it) })
    }
}
```

`ContrastTest`: add `Pairing("error on surfaceContainerHigh", p.error, p.surfaceContainerHigh, TEXT)`. Follow the file's existing `Pairing` list shape for the dark and light palettes. Its mutation: set the threshold for that pairing to 7.0 temporarily, so it goes RED, then revert. If the pairing already fails at 4.5, stop and report BLOCKED (ruling 3).

`RollaIconsTest`: the count becomes 29. `Delete` is in the set and not auto-mirrored (it is symmetric).

- [ ] **Step 2: Run them to verify they fail.**
  - Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*OneUiSurfacesTest" --tests "*RollaIconsTest" --tests "*ContrastTest"`.
  - Expected: `BUILD FAILED`, with `Unresolved reference 'OneUiBottomSheet'`.

- [ ] **Step 3: Implement.**

`OneUiBottomSheet.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.component

/**
 * The One UI bottom sheet (spec §8.7): surfaceContainerHigh, the large shape with square bottom corners, and an
 * optional sectionHeader [title] that TalkBack reads as a heading. The sheet's window is edge to edge (M3 1.3.0), so
 * statusBarsPadding() keeps the expanded sheet below the status bar; it is composed, so it reads the sheet window's
 * insets (the QueueSheet lesson, Phase 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        modifier = modifier.statusBarsPadding(),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.sectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = RollaDimens.listThumbStart)
                    .semantics { heading() },
            )
        }
        content()
    }
}
```

`OneUiDialog.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.component

/** The One UI dialog (spec §8.7): surfaceContainerHigh, the large shape, an onSurface title, onSurfaceVariant text. */
@Composable
fun OneUiDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = text,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A dialog or bar text button: accentText (spec §5.1: never primary), or error when [destructive]. */
@Composable
fun OneUiTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val content = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.accentText
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = content),
    ) {
        Text(text = text)
    }
}

/**
 * A One UI text field (spec §8.7): filled, no indicator line, the medium (16 dp) shape, and [containerColor]. Pass a
 * container one step above the surface it sits on (surfaceContainerHighest inside a dialog). Its focused label and
 * cursor use accentText.
 */
@Composable
fun OneUiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(text = label) },
        singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = MaterialTheme.colorScheme.accentText,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.accentText,
        ),
    )
}
```

`OneUiMenu.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.component

/** The One UI dropdown menu (spec §8.7): surfaceContainerHigh, the medium shape. */
@Composable
fun OneUiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = content,
    )
}

/** A menu item in songTitle (spec §8.7). */
@Composable
fun OneUiMenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DropdownMenuItem(
        text = { Text(text = text, style = MaterialTheme.typography.songTitle) },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}
```

`SheetActionRow.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.component

/**
 * One action in a One UI sheet: a [RollaDimens.singleLineRowHeight] row with the 24 dp [icon] centred in a thumbnail-
 * sized box, so the [label] starts at listTextStart like every list row's text. [destructive] tints both error.
 */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.singleLineRowHeight)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(start = RollaDimens.listThumbStart, end = RollaDimens.listThumbStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(RollaDimens.listThumb), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
        Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
        Text(text = label, style = MaterialTheme.typography.songTitle, color = tint, maxLines = 1)
    }
}
```

`RollaIcons.kt`: add this next to `Close`, and add `Delete` to `all`:

```kotlin
    val Delete: ImageVector by lazy {
        icon("Delete") {
            line {
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(9f, 7f)
                lineTo(9f, 4f)
                lineTo(15f, 4f)
                lineTo(15f, 7f)
                moveTo(6f, 7f)
                lineTo(7f, 20f)
                lineTo(17f, 20f)
                lineTo(18f, 7f)
                moveTo(10f, 11f)
                lineTo(10f, 16f)
                moveTo(14f, 11f)
                lineTo(14f, 16f)
            }
        }
    }
```

`Dimens.kt`, next to `npArtSize`:

```kotlin
    /** Hero artwork on AlbumDetail/ArtistDetail (spec §8.7). No reference screenshot: borrows npArtSize until Phase 7. */
    val detailArtworkSize: Dp get() = npArtSize
```

Add previews of every new component (dark, light and fontScale 1.5) to `KitPreviews.kt`, following its pattern.

- [ ] **Step 4: Run the tests to verify they pass.**
  - Run the Step 2 command. Expected: PASS.
  - Run every named mutation, quote each RED, and revert byte-exact.
  - If a pixel probe lands on anti-aliased glyph edges and flakes, move it inside the token area and report it. Do not loosen the colour tolerance.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :core:designsystem:check spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report. Do not commit.

---

### Task 2: `:core:ui`: the options sheet, the playlist sheet and dialog, and the detail hero (ui-builder)

**Files:**
- Modify in `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/`: `SongOptionsSheet.kt`, `AddToPlaylistSheet.kt`, `PlaylistNameDialog.kt`, `PlaylistRow.kt`.
- Create: `DetailArtworkHeader.kt`.
- Tests in `core/ui/src/test/kotlin/com/rolla/musicplayer/core/ui/`:
  - extend `SongOptionsHostTest.kt` and `FeatureCardAndPlaylistRowTest.kt`;
  - new `AddToPlaylistSheetTest.kt` and `DetailArtworkHeaderTest.kt`.

**Interfaces:**
- Consumes: Task 1's `OneUiBottomSheet`, `OneUiDialog`, `OneUiTextButton`, `OneUiTextField`, `SheetActionRow`, `RollaIcons.Delete` and `RollaDimens.detailArtworkSize`.
- Produces:
  - `SongOptionsHost(song: Song?, userPlaylists: List<Playlist>, onDismiss: () -> Unit, onAddSongToPlaylist: (String, Long) -> Unit, onCreatePlaylistAndAddSong: (String, String) -> Unit, onEditTagsClick: (Song) -> Unit, onRemoveFromPlaylist: ((Song) -> Unit)? = null, currentSongs: List<Song>? = null)`. The first six parameters are unchanged; the two new ones have defaults, so every caller still compiles.
  - `PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier, reserveRailSpace: Boolean = true)`
  - `@Composable fun DetailArtworkHeader(artworkUri: String, subtitle: String, modifier: Modifier = Modifier, circular: Boolean = false, placeholderGlyph: ImageVector = RollaIcons.MusicNote)`
  - `AddToPlaylistSheet`, `AddToPlaylistSheetHost` and `PlaylistNameDialog` keep their signatures.

- [ ] **Step 1: Write the failing tests.**

`SongOptionsHostTest.kt`. Add these tests, reusing the file's existing setup and `song(id, title)` helper:

```kotlin
    // Ruling 6: Remove appears only when the caller passes onRemoveFromPlaylist, and it removes that track. Control:
    // Edit tags is present. Mutation: always show Remove → the second host shows it → RED.
    @Test
    fun removeFromPlaylistOnlyWhenOffered() {
        val removed = mutableListOf<String>()
        var offerRemove by mutableStateOf(true)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SongOptionsHost(
                    song = song("7", "Seven"),
                    userPlaylists = emptyList(),
                    onDismiss = {},
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                    onRemoveFromPlaylist = if (offerRemove) { s -> removed += s.id } else null,
                )
            }
        }
        composeRule.onNodeWithText("Edit tags").assertExists()
        composeRule.onNodeWithText("Remove from playlist").performClick()
        assertEquals(listOf("7"), removed)
        composeRule.runOnIdle { offerRemove = false }
        composeRule.onNodeWithText("Remove from playlist").assertDoesNotExist()
    }

    // Review Focus 1 / ruling 10: the sheet closes when its track leaves currentSongs. Control: while the track is
    // listed, onDismiss is not called. Mutation: ignore currentSongs → dismissed stays 0 → RED.
    @Test
    fun sheetClosesWhenItsTrackLeavesTheList() {
        var dismissed = 0
        var songs by mutableStateOf(listOf(song("7", "Seven"), song("8", "Eight")))
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SongOptionsHost(
                    song = song("7", "Seven"),
                    userPlaylists = emptyList(),
                    onDismiss = { dismissed++ },
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                    currentSongs = songs,
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, dismissed)
        composeRule.runOnIdle { songs = listOf(song("8", "Eight")) }
        composeRule.waitForIdle()
        assertEquals(1, dismissed)
    }

    // Review Focus 4: a 200-character title stays on one line in the sheet heading. Control: the heading exists.
    // Mutation: drop maxLines = 1 from the sheet title → its height exceeds one line → RED.
    @Test
    fun aLongTitleStaysOnOneLine() {
        val long = "A".repeat(200)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SongOptionsHost(
                    song = song("7", long),
                    userPlaylists = emptyList(),
                    onDismiss = {},
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                )
            }
        }
        val heading = composeRule.onNode(hasText(long) and isHeading()).fetchSemanticsNode()
        assertEquals(1, heading.config[SemanticsProperties.Text].size)
        val height = composeRule.onNode(hasText(long) and isHeading()).getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("one line, was $height", height < 40.dp)
    }
```

New `AddToPlaylistSheetTest.kt` (Robolectric, NATIVE, `w360dp-h640dp`):

```kotlin
    // Ruling 11: the sheet is titled and its "New playlist" entry is a single-line action row. Control: a playlist row
    // renders. Mutations: (a) drop the title → RED; (b) restore the 56 dp-thumbnail NewPlaylistRow → its height is not
    // singleLineRowHeight → RED.
    @Test
    fun titledSheetWithASingleLineNewPlaylistRow() {
        setSheet(listOf(playlist(1L, "Workout")))
        composeRule.onNode(hasText("Add to playlist") and isHeading()).assertExists()
        composeRule.onNodeWithText("Workout").assertExists()
        val row = composeRule.onNodeWithText("New playlist").onParent().getUnclippedBoundsInRoot()
        assertEquals(RollaDimens.singleLineRowHeight.value, (row.bottom - row.top).value, 1f)
    }

    // Ruling 11 / 2-L7: inside the sheet a playlist row's trailing count ends at listThumbStart, not before the rail.
    // Control: with reserveRailSpace = true (the default, in a plain host), it ends at listTrailingEnd. Mutation: ignore
    // reserveRailSpace → the sheet's count ends at listTrailingEnd → RED.
    @Test
    fun sheetRowsDoNotReserveTheRail() { /* host PlaylistRow twice, reserveRailSpace true/false, assert count's right edge */ }
```

The body of `sheetRowsDoNotReserveTheRail` is written by the implementer. It must:
- host two `PlaylistRow`s in a `Column` of width 360 dp, one with `reserveRailSpace = true` and one with `false`;
- find each row's "N tracks" text by `onAllNodesWithText`;
- assert the right edges `360 - listTrailingEnd` and `360 - listThumbStart`, within 1 dp.

`setSheet` and `playlist(id, name)` are local helpers. Use `drawLatestDialog` only if a pixel check is added.

`FeatureCardAndPlaylistRowTest.kt` keeps its existing `PlaylistRow` tests unchanged; they are the default-path safety net.

New `DetailArtworkHeaderTest.kt`:

```kotlin
    // Spec §8.7 hero: artwork at detailArtworkSize, subtitle one line below. Control: the subtitle is shown. Mutation:
    // size the artwork listThumb → RED.
    @Test
    fun artworkIsDetailArtworkSizeAndTheSubtitleIsOneLine() { /* setContent DetailArtworkHeader("", "Queen · 12 tracks");
       assert the artwork node (testTag DETAIL_ARTWORK_TEST_TAG) is detailArtworkSize square ±0.5 dp; subtitle exists */ }

    // 0017: the artist hero is a circle. Pixel probe: the artwork box's top-left corner pixel shows the background, not
    // the placeholder, when circular; the square variant's corner area 20 dp in is placeholder. Control: the centre is
    // placeholder in both. Mutation: ignore circular → the corner probe hits placeholder → RED.
    @Test
    fun circularHeroClipsItsCorners() { /* drawRoot; compare corner pixel of each variant */ }
```

`DETAIL_ARTWORK_TEST_TAG = "detail_artwork"` is `internal` in `DetailArtworkHeader.kt`. Write both test bodies as described. The corner probe sits 4 dp in from the box's top-left corner: that is outside a 168 dp circle, but inside a rounded square of radius 26 dp only beyond about 8 dp, so use 4 dp. Comment the geometry in the test.

- [ ] **Step 2: Run them to verify they fail.**
  - Run: `./gradlew :core:ui:testDebugUnitTest --tests "*SongOptionsHostTest" --tests "*AddToPlaylistSheetTest" --tests "*DetailArtworkHeaderTest"`.
  - Expected: `BUILD FAILED`, with unresolved `onRemoveFromPlaylist`, `currentSongs`, `reserveRailSpace` and `DetailArtworkHeader`.

- [ ] **Step 3: Implement.**

`SongOptionsSheet.kt`:
- Remove its private paddings (16/12 dp).
- `SongOptionsHost` gains the two parameters and this effect, placed before the sheet is shown:

```kotlin
    // Ruling 10: a rescan or tag edit that removes the track closes its sheet instead of leaving it acting on a ghost.
    LaunchedEffect(song?.id, currentSongs) {
        if (song != null && currentSongs != null && currentSongs.none { it.id == song.id }) onDismiss()
    }
```

The same effect closes the add-to-playlist flow when `addToPlaylistSong` leaves `currentSongs` (it sets the flow's state to `null`).

The sheet becomes:

```kotlin
@Composable
private fun SongOptionsSheet(
    song: Song,
    onAddToPlaylistClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onRemoveClick: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    OneUiBottomSheet(onDismissRequest = onDismissRequest, title = song.title) {
        SheetActionRow(icon = RollaIcons.PlaylistAdd, label = "Add to playlist", onClick = onAddToPlaylistClick)
        SheetActionRow(icon = RollaIcons.Edit, label = "Edit tags", onClick = onEditTagsClick)
        if (onRemoveClick != null) {
            SheetActionRow(
                icon = RollaIcons.Delete,
                label = "Remove from playlist",
                onClick = onRemoveClick,
                destructive = true,
            )
        }
    }
}
```

The host passes `onRemoveClick = onRemoveFromPlaylist?.let { remove -> { onDismiss(); remove(song) } }`.

`AddToPlaylistSheet.kt`:
- Delete every private dp constant and the Material `Icons` import.
- The sheet becomes `OneUiBottomSheet(onDismissRequest, title = "Add to playlist")`.
- Inside it, a `LazyColumn`: `item { SheetActionRow(RollaIcons.Add, "New playlist", onCreateNewPlaylist); InsetDivider(startInset = RollaDimens.listTextStart) }`, then `items(playlists, key = { it.id }) { PlaylistRow(it, onClick = { onPlaylistSelected(it.id) }, reserveRailSpace = false) }`.
- The label changes from "New playlist…" to "New playlist". The ellipsis signalled a follow-up dialog; One UI sheets don't use it.
- Update the preview.

`PlaylistRow.kt`:
- Add `reserveRailSpace: Boolean = true`.
- When it is `false`, the `InsetDivider`'s `endInset` is `0.dp`, and the trailing count's end padding is `RollaDimens.listThumbStart` instead of `RollaDimens.listTrailingEnd`.

`PlaylistNameDialog.kt`:

```kotlin
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initialName) }
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            OneUiTextButton(text = confirmLabel, onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank())
        },
        dismissButton = { OneUiTextButton(text = "Cancel", onClick = onDismiss) },
        text = {
            OneUiTextField(
                value = name,
                onValueChange = { name = it },
                label = "Playlist name",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        modifier = modifier,
    )
}
```

`DetailArtworkHeader.kt`:

```kotlin
package com.rolla.musicplayer.core.ui

internal const val DETAIL_ARTWORK_TEST_TAG = "detail_artwork"

/**
 * The hero at the top of AlbumDetail/ArtistDetail (spec §8.7): the artwork at detailArtworkSize (a circle for an
 * artist, the extraLarge rounded square otherwise), then one ellipsized subtitle line. The title lives in the top bar.
 */
@Composable
fun DetailArtworkHeader(
    artworkUri: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    placeholderGlyph: ImageVector = RollaIcons.MusicNote,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = RollaDimens.panelTopGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkThumbnail(
            artworkUri = artworkUri,
            contentDescription = null, // Decorative: the title in the top bar names it (a11y audit precedent).
            shape = if (circular) CircleShape else MaterialTheme.shapes.extraLarge,
            placeholderGlyph = placeholderGlyph,
            modifier = Modifier.size(RollaDimens.detailArtworkSize).testTag(DETAIL_ARTWORK_TEST_TAG),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.artistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = RollaDimens.panelTopGap, start = RollaDimens.screenEdge, end = RollaDimens.screenEdge),
        )
    }
}
```

`ArtworkThumbnail` sizes itself `listThumb` internally. The caller's `size(detailArtworkSize)` comes first in the chain, so it fixes the constraints and wins. If the bounds test shows otherwise, add a `size: Dp = RollaDimens.listThumb` parameter to `ArtworkThumbnail` instead, and report it as a mechanical fix. While here, change `ArtworkThumbnail`'s URI check to `isNotBlank()` (ruling 9).

- [ ] **Step 4: Run the tests to verify they pass.**
  - Run: `./gradlew :core:ui:testDebugUnitTest`. Expected: PASS, the whole module, with the existing `SongOptionsHostTest` and row tests unchanged.
  - Run each named mutation and revert.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :core:ui:check :feature:library:compileDebugKotlin :feature:playlists:compileDebugKotlin spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report. Do not commit.

---

### Task 3: `:core:data`: `createPlaylistWithSong` and the artist albums order (data-layer-agent)

**Files:**
- Create: `core/data/src/main/kotlin/com/rolla/musicplayer/core/data/repository/PlaylistWrites.kt`
- Modify: `core/data/.../repository/ArtistRepositoryImpl.kt`
- Modify: the four ViewModels in the file map (Task 3 rows)
- Tests:
  - new `core/data/src/test/.../repository/PlaylistWritesTest.kt`;
  - extend `ArtistRepositoryImplTest.kt`;
  - the four ViewModel tests stay green unchanged.

**Interfaces:**
- Consumes: `PlaylistRepository.createPlaylist(name): Long` and `addSongs(playlistId, songIds)` (existing); Phase 5's `sortedByLibraryName` and `@CoreDataDefaultDispatcher`.
- Produces: `suspend fun PlaylistRepository.createPlaylistWithSong(name: String, songId: String): Long?` (package `com.rolla.musicplayer.core.data.repository`).

- [ ] **Step 1: Write the failing tests.**

`PlaylistWritesTest.kt` (MockK):

```kotlin
class PlaylistWritesTest {
    private val repository: PlaylistRepository = mockk(relaxed = true)

    // The name is trimmed, the playlist created, the track added to that id. Control: addSongs gets the created id
    // (42), not a constant. Mutation: addSongs(0L, …) → RED.
    @Test
    fun createsTrimmedPlaylistAndAddsTheTrack() = runTest {
        coEvery { repository.createPlaylist("Road trip") } returns 42L
        assertEquals(42L, repository.createPlaylistWithSong("  Road trip  ", "7"))
        coVerify(exactly = 1) { repository.addSongs(42L, listOf("7")) }
    }

    // A blank name writes nothing. Control: the call returns null. Mutation: drop the blank check → createPlaylist("")
    // is called → RED.
    @Test
    fun aBlankNameWritesNothing() = runTest {
        assertNull(repository.createPlaylistWithSong("   ", "7"))
        coVerify(exactly = 0) { repository.createPlaylist(any()) }
        coVerify(exactly = 0) { repository.addSongs(any(), any()) }
    }
}
```

`ArtistRepositoryImplTest`: add a test, using the file's existing album-row helper. Construct the repository with the test dispatcher as in Phase 5.

```kotlin
    // Ruling 8: the albums strip uses the library order (the DAO gives binary title order). Control: three albums come
    // back. Mutation: drop the sort → ["Zed", "abba", "Éclair"] binary order → RED.
    @Test
    fun observeArtistAlbums_sortsInLibraryOrder() = runTest {
        every { artistDao.observeArtistAlbums("Queen") } returns flowOf(listOf(albumRow(1L, "Zed"), albumRow(2L, "abba"), albumRow(3L, "Éclair")))
        val titles = repository.observeArtistAlbums("Queen").first().map { it.title }
        assertEquals(3, titles.size)
        assertEquals(listOf("abba", "Éclair", "Zed"), titles)
    }
```

- [ ] **Step 2: Run them to verify they fail.**
  - Run: `./gradlew :core:data:testDebugUnitTest --tests "*PlaylistWritesTest" --tests "*ArtistRepositoryImplTest"`.
  - Expected: `BUILD FAILED`, with `Unresolved reference 'createPlaylistWithSong'`.

- [ ] **Step 3: Implement.**

`PlaylistWrites.kt`:

```kotlin
package com.rolla.musicplayer.core.data.repository

/**
 * Creates a playlist called [name] (trimmed) holding [songId], and returns its id. A blank name writes nothing and
 * returns null. Room write failures propagate, like every other repository write; callers run it in viewModelScope.
 * This replaces the "New playlist" bodies the track lists used to copy (Phase 6a ruling 7).
 */
suspend fun PlaylistRepository.createPlaylistWithSong(name: String, songId: String): Long? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val id = createPlaylist(trimmed)
    addSongs(id, listOf(songId))
    return id
}
```

`ArtistRepositoryImpl.observeArtistAlbums`:

```kotlin
            .map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()
            .map { albums -> albums.sortedByLibraryName(compareBy<Album> { it.title }.thenBy { it.id }) { it.title } }
            .flowOn(sortDispatcher)
```

In each of `TracksViewModel`, `FolderDetailViewModel`, `FavouritesTabViewModel` and `PlaylistDetailViewModel`, the body of `createPlaylistAndAddSong(name, songId)` becomes:

```kotlin
        viewModelScope.launch { playlistRepository.createPlaylistWithSong(name, songId) }
```

Keep the public method, so their screens and tests stay unchanged. The existing blank-name and create-then-add tests in those four ViewModel test files are the safety net. They must stay green and unedited.

- [ ] **Step 4: Run the tests to verify they pass.**
  - Run: `./gradlew :core:data:testDebugUnitTest :feature:library:testDebugUnitTest :feature:playlists:testDebugUnitTest`. Expected: PASS.
  - Run each named mutation and revert.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :core:data:check :feature:library:check :feature:playlists:check spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report. Do not commit.

---

### Task 4: Move the Phase 3 sheets, dialogs and menus and the Home menu onto the kit (ui-builder)

This is a batch of small edits of the same shape. The behaviour is unchanged.

**Files:**
- `feature/player/.../QueueSheet.kt`
- `feature/player/.../NowPlayingScreen.kt` (`NowPlayingOverflowMenu`, `OverflowMenuItem`)
- `feature/settings/.../ThemeModeDialog.kt`
- `feature/equalizer/.../EqualizerScreen.kt` (the save-preset dialog and its text button at about lines 505–565)
- `app/.../home/HomeScreen.kt` (`OverflowMenu`, `SelectionBar`)

**Interfaces:**
- Consumes: Task 1's components.
- Produces: nothing new.

- [ ] **Step 1: Capture the safety net.**
  - Run: `./gradlew :feature:player:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:equalizer:testDebugUnitTest :app:testDebugUnitTest`.
  - Record the pass counts. These are the tests that must stay green unedited, for example `QueueSheetTest`, the Now Playing overflow tests, `ThemeModeDialog` tests, the Equaliser dialog tests and `HomeScreenTest`.

- [ ] **Step 2: Edit each file.**
  - **`QueueSheet`:** `ModalBottomSheet(…)` plus its title `Text` becomes `OneUiBottomSheet(onDismissRequest, modifier, title = "Queue") { … }`. Delete the local `largeTop`, the `statusBarsPadding` comment block and the heading `Text`.
  - **`NowPlayingOverflowMenu`:** `DropdownMenu(…)` becomes `OneUiDropdownMenu(expanded, onDismissRequest)`. `OverflowMenuItem` calls `OneUiMenuItem(text = label, onClick = { if (target != null) onClick(target) }, enabled = target != null)`.
  - **`ThemeModeDialog`:** `AlertDialog` becomes `OneUiDialog`. Its `TextButton` becomes `OneUiTextButton`. The radio rows stay.
  - **The Equaliser save-preset dialog:** `AlertDialog` becomes `OneUiDialog`. Its `OutlinedTextField` becomes `OneUiTextField(containerColor = surfaceContainerHighest)`. Both `TextButton`s, and the screen's other accent `TextButton` at about line 510, become `OneUiTextButton`.
  - **Home `OverflowMenu`:** becomes `OneUiDropdownMenu` + `OneUiMenuItem("Settings")`.
  - **Home `SelectionBar`:** its `TextButton` becomes `OneUiTextButton("Edit tags", onEditTagsClick)`.
  - **Clean-up:** remove every import these edits leave unused, including `ButtonDefaults`, `AlertDialog`, `DropdownMenu`, `ModalBottomSheet` and `OutlinedTextField`.

- [ ] **Step 3: Add a guard test for the Home menu** (it had no rendering check). In `HomeScreenTest` (`:app`), using its existing setup:

```kotlin
    // Ruling 2: the Home ⋮ menu is the One UI menu: its item is drawn in songTitle and opens Settings. Control: the
    // menu is closed before the tap. Mutation: OneUiMenuItem → stock DropdownMenuItem with default text style → the
    // item's text height differs from songTitle's line height → RED.
    @Test
    fun `the overflow menu is the one ui menu and opens settings`() { /* tap "More options"; assert "Settings" exists;
       assert its text node height ≈ songTitle line height (bounds); click → onSettingsClick called once */ }
```

The implementer writes the body as described. If the text-height probe cannot tell `songTitle` from the default style (they can coincide), substitute a probe that can, and say so: for example, use `fetchSemanticsNode().layoutInfo` to read the text style, or compare the item's rendered height with `singleLineRowHeight`.

- [ ] **Step 4: Run the tests.**
  - Run the Step 1 command. Expected: PASS, with the same counts plus 1 new test, and every pre-existing test unedited.
  - Run the Step 3 mutation and revert.
  - Then grep the repo: `grep -rn "ModalBottomSheet(\|AlertDialog(\|DropdownMenu(\|DropdownMenuItem(" --include=*.kt app core feature | grep /main/ | grep -v core/designsystem`. Expected: only `PlaylistDetailScreen.kt`, which Task 7 converts.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :feature:player:check :feature:settings:check :feature:equalizer:check :app:check spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report. Do not commit.

---

### Task 5: AlbumDetail on the One UI kit (ui-builder + viewmodel-architect)

**Files:**
- Modify: `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/AlbumDetailScreen.kt` (rewrite) and `AlbumDetailViewModel.kt`.
- Tests: new `feature/library/src/test/kotlin/com/rolla/musicplayer/feature/library/AlbumDetailScreenTest.kt`; extend `AlbumDetailViewModelTest.kt`.

**Interfaces:**
- Consumes:
  - Task 1's kit;
  - Task 2's `DetailArtworkHeader` and `SongOptionsHost(…, currentSongs)`;
  - Task 3's `createPlaylistWithSong`;
  - `TabMessage` (`feature/library/TabStates.kt`) and `tracksCountLabel` (`:core:ui`).
- Produces:
  - `fun AlbumDetailRoute(onNavigateUp: () -> Unit, onEditTagsClick: (Song) -> Unit, viewModel: AlbumDetailViewModel = hiltViewModel())`. This **changes** the route: Task 8 wires it.
  - `internal const val ALBUM_SONG_LIST_TEST_TAG = "album_song_list"`
  - `AlbumDetailViewModel` gains `userPlaylists: StateFlow<List<Playlist>>`, `addSongToPlaylist(songId: String, playlistId: Long)` and `createPlaylistAndAddSong(name: String, songId: String)`, and takes `PlaylistRepository` in its constructor. `AlbumDetailUiState` is unchanged.

- [ ] **Step 1: Write the failing tests.** The new `AlbumDetailScreenTest.kt` uses Robolectric, NATIVE graphics and `w360dp-h640dp`, and hosts `AlbumDetailContent`. Its helpers are `setContent(uiState, …callbacks, miniPlayerInset)`, `album()` and `songs(n)`.

```kotlin
    // Spec §12 / ruling 6: the ⋮ on a track opens the options sheet (it was dead). Control: the row exists. Mutation:
    // onMoreClick = {} → no "Add to playlist" → RED.
    @Test
    fun `a track's more button opens the options sheet`() {
        setContent(loaded(songs(3)))
        composeRule.onAllNodesWithContentDescription("More options for Track 1")[0].performClick()
        composeRule.onNodeWithText("Add to playlist").assertExists()
        composeRule.onNodeWithText("Edit tags").assertExists()
    }

    // Ruling 6: Edit tags hands the tapped track out. Control: nothing is passed before the tap. Mutation: pass songs
    // .first() → RED for track 2.
    @Test
    fun `edit tags passes the tapped track`() { /* open sheet on "Track 2"; click "Edit tags"; assert passed id == "2" */ }

    // Ruling 5 / spec §8.7: the hero subtitle reads "<artist> · N tracks" and the sort label is "Track number".
    // Control: the title is in the top bar. Mutation: songCountLabel "songs" copy → RED.
    @Test
    fun `the header says artist and track count`() {
        setContent(loaded(songs(2)))
        composeRule.onNodeWithText("A Night at the Opera").assertExists()
        composeRule.onNodeWithText("Queen · 2 tracks").assertExists()
        composeRule.onNodeWithText("Track number").assertExists()
    }

    // Shuffle and Play are real (spec §12). Control: both callbacks start at 0. Mutation: swap the callbacks → RED.
    @Test
    fun `shuffle and play call their actions`() { /* click "Shuffle album" then "Play album"; assert [shuffle, play] */ }

    // Review Focus 5: the last track scrolls fully above the pill. Control: the list exists. Mutation: drop the bottom
    // contentPadding → RED.
    @Test
    fun `the last track scrolls fully above the mini-player`() { /* 20 songs, inset 60 dp, performScrollToIndex(last
       list index), assert "Track 20" bottom <= root bottom - 60 dp */ }

    // Review Focus 4: a 200-character album title keeps Shuffle and Play on screen. Control: Play is displayed.
    // Mutation: drop maxLines from the top bar title → the bar grows and pushes the panel → Play's top moves below
    // 640 dp → RED. (OneUiDetailTopBar already ellipsizes; if this mutant is equivalent because the bar clips, probe the
    // hero subtitle with a 200-character artist instead and say so.)
    @Test
    fun `a very long title keeps the controls visible`() { /* album title = "A".repeat(200); assert Play displayed */ }

    // Not found: a gone album says so. Control: the loading state renders nothing. Mutation: treat null album as
    // loading → message missing → RED.
    @Test
    fun `a missing album says it is not available`() {
        setContent(AlbumDetailUiState(isLoading = true))
        composeRule.onNodeWithText("Album not available").assertDoesNotExist()
        setContent(AlbumDetailUiState(isLoading = false, album = null))
        composeRule.onNodeWithText("Album not available").assertExists()
    }
```

`AlbumDetailViewModelTest.kt` mirrors `FolderDetailViewModelTest`'s playlist tests one to one, the 4b precedent:
- `userPlaylists` emits the repository's playlists;
- `addSongToPlaylist` calls `addSongs(playlistId, listOf(songId))`;
- `createPlaylistAndAddSong` with a blank name writes nothing;
- `createPlaylistAndAddSong` with a name creates the playlist and adds the track.

Each test names its mutation and carries its own control.

- [ ] **Step 2: Run them to verify they fail.**
  - Run: `./gradlew :feature:library:testDebugUnitTest --tests "*AlbumDetail*"`.
  - Expected: `BUILD FAILED` on unresolved names (`AlbumDetailContent`, the new ViewModel members).

- [ ] **Step 3: Implement.** Rewrite `AlbumDetailScreen.kt` following `FolderDetailScreen.kt`'s structure and comments:

```kotlin
internal const val ALBUM_SONG_LIST_TEST_TAG = "album_song_list"

@Composable
fun AlbumDetailRoute(
    onNavigateUp: () -> Unit,
    onEditTagsClick: (Song) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userPlaylists by viewModel.userPlaylists.collectAsStateWithLifecycle()
    AlbumDetailContent(
        uiState = uiState,
        userPlaylists = userPlaylists,
        onNavigateUp = onNavigateUp,
        onSongClick = remember(viewModel) { viewModel::onSongClick },
        onShuffleClick = remember(viewModel) { viewModel::onShuffleClick },
        onPlayClick = remember(viewModel) { viewModel::onPlayClick },
        onAddSongToPlaylist = remember(viewModel) { viewModel::addSongToPlaylist },
        onCreatePlaylistAndAddSong = remember(viewModel) { viewModel::createPlaylistAndAddSong },
        onEditTagsClick = onEditTagsClick,
    )
}

/**
 * Stateless AlbumDetail (spec §8.7); tests and previews host this. The bar owns the status-bar inset; the list pads by
 * the pill and the navigation bar, so it scrolls under the pill (ruling 5: no PushedScreen). Nothing renders while
 * loading; a null album after loading is the not-found state (the album's last track was removed).
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun AlbumDetailContent(
    uiState: AlbumDetailUiState,
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
    val album = uiState.album
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OneUiDetailTopBar(title = album?.title.orEmpty(), onNavigateUp = onNavigateUp)
        ContentPanel {
            when {
                uiState.isLoading -> Unit
                album == null -> TabMessage("Album not available")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(ALBUM_SONG_LIST_TEST_TAG),
                    contentPadding = PaddingValues(bottom = homeListBottomPadding()),
                ) {
                    item(key = "hero") {
                        DetailArtworkHeader(
                            artworkUri = album.artworkUri,
                            subtitle = "${album.artist} · ${tracksCountLabel(uiState.songs.size)}",
                        )
                    }
                    item(key = "sort") {
                        val enabled = uiState.songs.isNotEmpty()
                        SortHeader(label = "Track number") {
                            CircleIconButton(RollaIcons.Shuffle, "Shuffle album", onShuffleClick, enabled = enabled)
                            CircleIconButton(RollaIcons.Play, "Play album", onPlayClick, enabled = enabled)
                        }
                    }
                    if (uiState.songs.isEmpty()) {
                        item(key = "empty") { TabMessage("No tracks in this album") }
                    } else {
                        items(items = uiState.songs, key = { it.id }) { song ->
                            SongListItem(
                                song = song,
                                onClick = { onSongClick(song) },
                                trackNumber = song.trackNumber,
                                onMoreClick = { optionsSong = song },
                            )
                        }
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
        currentSongs = uiState.songs,
    )
}
```

Delete the old `Scaffold`, the `TopAppBar`, the private dp constants, the Material icons, `ControlButton`, `LoadingContent` and the other helpers it no longer uses. Keep a dark and light preview of `AlbumDetailContent`.

In `AlbumDetailViewModel`:
- add `private val playlistRepository: PlaylistRepository` to the constructor;
- add `userPlaylists`, the same `stateIn` as `FolderDetailViewModel`;
- add `addSongToPlaylist`, which launches `playlistRepository.addSongs`;
- add `createPlaylistAndAddSong`, which launches `playlistRepository.createPlaylistWithSong`.

- [ ] **Step 4: Run the tests to verify they pass.**
  - Run: `./gradlew :feature:library:testDebugUnitTest`. Expected: PASS.
  - Run each named mutation and revert.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :feature:library:check spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - `:app` does not compile until Task 8, because `AlbumDetailRoute`'s signature changed. That is expected. Say so in the report and do not edit `:app`.
  - Report. Do not commit.

---

### Task 6: ArtistDetail on the One UI kit, and `AlbumCard` polish (ui-builder + viewmodel-architect)

**Files:**
- Modify: `feature/library/.../ArtistDetailScreen.kt` (rewrite) and `ArtistDetailViewModel.kt`.
- Modify: `core/ui/.../AlbumCard.kt` (ruling 9).
- Tests:
  - new `feature/library/src/test/.../ArtistDetailScreenTest.kt`;
  - extend `ArtistDetailViewModelTest.kt`;
  - extend `core/ui/src/test/.../AlbumCardTest.kt`.

**Interfaces:**
- Consumes: as Task 5, plus `AlbumCard` and `albumsCountLabel`.
- Produces:
  - `fun ArtistDetailRoute(onNavigateUp: () -> Unit, onAlbumClick: (Long) -> Unit, onEditTagsClick: (Song) -> Unit, viewModel: ArtistDetailViewModel = hiltViewModel())`. Keep the existing `onAlbumClick` name if it differs; read the current signature.
  - `internal const val ARTIST_SONG_LIST_TEST_TAG = "artist_song_list"`
  - `ArtistDetailViewModel` gains the same playlist members as Task 5.

- [ ] **Step 1: Write the failing tests.** `ArtistDetailScreenTest.kt` (Robolectric, NATIVE, `w360dp-h640dp`). Its tests:
  - **The ⋮ opens the options sheet.** The same shape as Task 5's test.
  - **Edit tags passes the tapped track.**
  - **The header and the sort label.** The hero subtitle is `"${albumsCountLabel(2)}, ${tracksCountLabel(3)}"`, i.e. "2 albums, 3 tracks". The sort label is "Album". The "Albums" section heading exists. Mutation: the old "·"/"songs" label → RED.
  - **The hero is circular.** Covered in `DetailArtworkHeaderTest` (Task 2). Here, assert only that the artist screen passes `circular = true`. Probe the hero's top-left 4 dp corner pixel against the panel colour, with the screen at the top. Control: the centre pixel is placeholder. Mutation: `circular = false` → RED.
  - **An album card opens its album.** Tap the "Jazz" card, and the album id is passed. Control: no id before the tap. Mutation: pass the artist's first album → RED.
  - **The last track scrolls fully above the pill.**
  - **A missing artist says "Artist not available".** The same shape as Task 5's test.

  `ArtistDetailViewModelTest.kt`: the four playlist tests, mirrored as in Task 5.

  `AlbumCardTest.kt`. Add one test (ruling 9):

```kotlin
    // Ruling 9: the card's ripple is clipped to featureCard, so a pressed card's corner stays the panel colour.
    // Pixel probe at the card's top-left 3 dp corner while pressed (performTouchInput { down(center) } then
    // mainClock.advanceTimeBy(200)). Control: the card centre shows the pressed ripple over the art. Mutation: drop the
    // clip → the corner shows ripple tint → RED. Use @Config(sdk = [30]) for ripples (build-gotchas: ripples need sdk 30).
    @Test
    fun pressedCardRippleIsClippedToTheCardShape() { /* as described */ }
```

- [ ] **Step 2: Run them to verify they fail.**
  - Run: `./gradlew :feature:library:testDebugUnitTest --tests "*ArtistDetail*" :core:ui:testDebugUnitTest --tests "*AlbumCardTest"`.
  - Expected: `BUILD FAILED` or test failures, on unresolved names or the ripple probe.

- [ ] **Step 3: Implement.**

**`ArtistDetailScreen.kt`.** Rewrite it with the same skeleton as Task 5. The list is:

```kotlin
                    item(key = "hero") {
                        DetailArtworkHeader(
                            artworkUri = artist.artworkUri,
                            subtitle = "${albumsCountLabel(uiState.albums.size)}, ${tracksCountLabel(uiState.songs.size)}",
                            circular = true,
                            placeholderGlyph = RollaIcons.Person,
                        )
                    }
                    if (uiState.albums.isNotEmpty()) {
                        item(key = "albums") { ArtistAlbumsStrip(albums = uiState.albums, onAlbumClick = onAlbumClick) }
                    }
                    item(key = "sort") {
                        val enabled = uiState.songs.isNotEmpty()
                        SortHeader(label = "Album") {
                            CircleIconButton(RollaIcons.Shuffle, "Shuffle artist", onShuffleClick, enabled = enabled)
                            CircleIconButton(RollaIcons.Play, "Play artist", onPlayClick, enabled = enabled)
                        }
                    }
                    if (uiState.songs.isEmpty()) {
                        item(key = "empty") { TabMessage("No tracks by this artist") }
                    } else {
                        items(items = uiState.songs, key = { it.id }) { song ->
                            SongListItem(song = song, onClick = { onSongClick(song) }, onMoreClick = { optionsSong = song })
                        }
                    }
```

**`ArtistAlbumsStrip`:**

```kotlin
@Composable
private fun ArtistAlbumsStrip(albums: List<Album>, onAlbumClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Albums",
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = RollaDimens.listThumbStart, top = RollaDimens.panelTopGap)
                .semantics { heading() },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = RollaDimens.featureCardRowStart, vertical = RollaDimens.panelTopGap),
            horizontalArrangement = Arrangement.spacedBy(RollaDimens.featureCardGap),
        ) {
            items(items = albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album.id) },
                    modifier = Modifier.width(RollaDimens.featureCardSize),
                    subtitle = album.year?.toString() ?: "",
                )
            }
        }
    }
}
```

If `Album` has no `year`, use `tracksCountLabel(album.songCount)` as the subtitle. Read the model, and report which one you used.

**`ArtistDetailViewModel`:** the same playlist additions as Task 5.

**`AlbumCard.kt`:** clip the ripple. Change `Column(modifier = modifier.clickable(onClick = onClick))` to `Column(modifier = modifier.clip(MaterialTheme.shapes.featureCard).clickable(onClick = onClick))`.

- [ ] **Step 4: Run the tests to verify they pass.**
  - Run: `./gradlew :feature:library:testDebugUnitTest :core:ui:testDebugUnitTest`. Expected: PASS.
  - Run each named mutation and revert.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :feature:library:check :core:ui:check spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - As in Task 5, `:app` waits for Task 8.
  - Report. Do not commit.

---

### Task 7: PlaylistDetail (user and smart) on the One UI kit (ui-builder + viewmodel-architect)

**Files:**
- Modify: `feature/playlists/.../PlaylistDetailScreen.kt` (rewrite of the chrome; the drag-reorder logic is kept) and `PlaylistDetailViewModel.kt`.
- Tests: new `feature/playlists/src/test/.../PlaylistDetailScreenTest.kt`; extend `PlaylistDetailViewModelTest.kt`.

**Interfaces:**
- Consumes: Tasks 1, 2 and 3.
- Produces:
  - `fun PlaylistDetailRoute(onNavigateUp: () -> Unit, onEditTagsClick: (Song) -> Unit, viewModel: PlaylistDetailViewModel = hiltViewModel())`.
  - `PlaylistDetailUiState` gains `val sortLabel: String = ""`. The ViewModel fills it per ruling 5.
  - `internal const val PLAYLIST_SONG_LIST_TEST_TAG = "playlist_song_list"`

- [ ] **Step 1: Write the failing tests.** `PlaylistDetailScreenTest.kt` hosts the stateless `PlaylistDetailContent`:

```kotlin
    // Review Focus 2 / ruling 6: a user playlist's ⋮ offers Remove; a smart playlist's never does. Control: Edit tags
    // is in both sheets. Mutation: pass onRemoveFromPlaylist for smart playlists too → RED on the second half.
    @Test
    fun `only a user playlist offers remove`() { /* user: open sheet on "Track 1" → "Remove from playlist" exists and
       click → removed == ["1"]; then smart (isUserPlaylist = false): open sheet → "Edit tags" exists, Remove absent */ }

    // Review Focus 3 / 4a carry-over: the delete confirmation draws "Delete" in error and "Cancel" in accentText, never
    // primary. Pixel probe via drawLatestDialog over each button's text bounds. Control: both labels exist. Mutation:
    // Cancel → stock TextButton → primary ink → RED.
    @Test
    fun `the delete dialog colours its buttons correctly`() { /* open ⋮ → "Delete playlist" → probe as described */ }

    // Delete confirms, then leaves. Control: Cancel neither deletes nor leaves. Mutation: drop onNavigateUp after delete
    // → RED.
    @Test
    fun `confirming delete deletes and navigates up`() { /* Cancel first (control), then Delete */ }

    // Rename opens the name dialog with the current title. Control: the dialog is absent before the tap. Mutation:
    // initialName = "" → the field lacks the title → RED.
    @Test
    fun `rename opens the dialog with the current name`() { /* ⋮ → "Rename" → field text == title */ }

    // Ruling 5: sort labels. Control: the user label shows "Custom order". Mutation: one label for all → RED.
    @Test
    fun `the sort label describes the order`() { /* uiState.sortLabel drives it; assert both labels render */ }

    // 4a/Phase 2 carry-over: header and empty-state copy. Control: a non-empty playlist shows no empty message.
    // Mutation: "songs" copy → RED.
    @Test
    fun `an empty playlist says so in tracks copy`() { /* empty → `No tracks in "Road trip" yet.` */ }

    // The accessible reorder actions survive the rewrite. Control: a smart playlist has none. Mutation: drop the
    // customActions modifier → RED.
    @Test
    fun `user playlist rows keep move up and move down`() { /* row 2 has "Move up" and "Move down" */ }

    // Review Focus 5: the last row scrolls fully above the pill.
    @Test
    fun `the last track scrolls fully above the mini-player`() { /* as Task 5 */ }
```

`PlaylistDetailViewModelTest.kt`. Add:
- **`sortLabel`.** The state carries "Custom order" for a user playlist, and per ruling 5 for each `SmartPlaylistKind` (one parametrised test over the four kinds). Control: the title is set. Mutation: drop one `when` branch (compile error), so instead swap two labels → RED.

The existing `PlaylistDetailViewModelTest` cases stay green, unchanged.

- [ ] **Step 2: Run them to verify they fail.**
  - Run: `./gradlew :feature:playlists:testDebugUnitTest --tests "*PlaylistDetail*"`.
  - Expected: `BUILD FAILED` on the unresolved names.

- [ ] **Step 3: Implement.**

**`PlaylistDetailViewModel`:**

```kotlin
internal fun sortLabelFor(kind: SmartPlaylistKind?): String = when (kind) {
    null -> "Custom order"
    SmartPlaylistKind.RECENTLY_ADDED -> "Date added"
    SmartPlaylistKind.MOST_PLAYED -> "Play count"
    SmartPlaylistKind.RECENTLY_PLAYED -> "Last played"
    SmartPlaylistKind.FAVOURITES -> "Name"
}
```

Set `sortLabel = sortLabelFor(smartKind)` wherever the ui state is built. `createPlaylistAndAddSong` already uses the helper (Task 3).

**`PlaylistDetailScreen.kt`:**
- **Route.** `PlaylistDetailRoute` collects `uiState` and `userPlaylists`. It hosts `SongOptionsHost` with:
  - `onRemoveFromPlaylist = if (uiState.isUserPlaylist) viewModel::removeSong else null`;
  - `currentSongs = uiState.songs`;
  - `onEditTagsClick`.

  Delete the old `AddToPlaylistSheetHost` and `PlaylistNameDialog` block: `SongOptionsHost` owns that flow now.
- **`PlaylistDetailContent`:** `Column(background) { OneUiDetailTopBar(title, onNavigateUp) { if (isUserPlaylist) PlaylistOverflowMenu(...) }; ContentPanel { SortHeader(label = uiState.sortLabel) { Shuffle; Play }; when { isLoading -> Unit; songs.isEmpty() -> EmptyPlaylistContent(title); else -> PlaylistSongList(...) } } }`. The `SortHeader` stays outside the list (ruling 5). `PlaylistSongList`'s `LazyColumn` gets `contentPadding = PaddingValues(bottom = homeListBottomPadding())` and `testTag(PLAYLIST_SONG_LIST_TEST_TAG)`.
- **Row ⋮.** Each row's `onMoreClick = { optionsSong = song }`, which is hoisted to the route through an `onMoreClick: (Song) -> Unit` parameter. Drop the custom `moreContentDescription`: the ⋮ now opens a menu, so the default "More options for X" is honest again.
- **`PlaylistOverflowMenu`.** `OneUiIconButton(RollaIcons.More, "More options")` plus `OneUiDropdownMenu`, with `OneUiMenuItem("Rename")` opening `PlaylistNameDialog(title = "Rename playlist", confirmLabel = "Rename", initialName = title)` and `OneUiMenuItem("Delete playlist")` opening the delete dialog.
- **The delete dialog:**

```kotlin
    OneUiDialog(
        onDismissRequest = onDismiss,
        title = "Delete \"$title\"?",
        text = { Text("This playlist will be permanently deleted. This can't be undone.") },
        confirmButton = { OneUiTextButton(text = "Delete", onClick = onConfirm, destructive = true) },
        dismissButton = { OneUiTextButton(text = "Cancel", onClick = onDismiss) },
    )
```

- **`EmptyPlaylistContent`:** the `emptyState` style, `onSurfaceVariant`, `No tracks in "$title" yet.`.
- **Reduced motion:** replace `isReducedMotion()` with `rememberReducedMotion()` (ruling 13) and delete the private function.
- **Clean-up:** delete the private dp constants (keep `DragLiftElevation` only if no token fits; if you keep it, move it to `RollaDimens` as `dragLiftElevation`), the Material icons, `ControlButton`, `PlaybackControls`, `SortControlHeader` and `LoadingContent`.

- [ ] **Step 4: Run the tests to verify they pass.**
  - Run: `./gradlew :feature:playlists:testDebugUnitTest`. Expected: PASS.
  - Run each named mutation and revert.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :feature:playlists:check :core:designsystem:check spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report. Do not commit.

---

### Task 8: `:app` wiring: the detail routes leave `PushedScreen` and pass Edit tags (navigation-agent)

**Files:**
- Modify: `app/src/main/kotlin/com/rolla/musicplayer/MainActivity.kt` (the `composable<PlaylistDetail>`, `composable<SmartPlaylist>`, `composable<AlbumDetail>` and `composable<ArtistDetail>` entries).
- Tests: `app/src/test/kotlin/com/rolla/musicplayer/navigation/` gets a new `DetailRoutesInsetTest.kt`. Keep `PushedScreenTest`, `MiniPlayerVisibilityTest`, `AppShellTest`, `NowPlayingNavigationTest` and `RouteClassTest` green and unmodified.

**Interfaces:**
- Consumes: the route signatures from Tasks 5–7.
- Produces: nothing new.

- [ ] **Step 1: Write the failing test.** `DetailRoutesInsetTest.kt` uses the same harness as `FolderDetailRouteTest`/`PushedScreenTest`; read them first.
  - Build the `NavHost` from `MainActivity`'s graph function if it is reachable. Otherwise, assert on the `composable<…>` blocks by hosting each route lambda the way `FolderDetailRouteTest` does.
  - **What it asserts:** for each of AlbumDetail, ArtistDetail and PlaylistDetail, with `LocalMiniPlayerInset` at 60 dp, the screen's own list's last row ends at most 60 dp above the bottom. It must not end 120 dp above, which would mean both the list and `PushedScreen` padded it.
  - **Control:** the row exists.
  - **Mutation:** keep the `PushedScreen` wrapper → the double padding gap → RED.
  - If the graph cannot be hosted in a JVM test (Hilt ViewModels), assert the **source wiring** instead. Build a tiny `@Composable` test double of each entry, extracted as an `internal` function per route in `MainActivity.kt`: for example `internal fun AlbumDetailEntry(navController, …)`, which the graph calls. The test hosts that entry with fake ViewModels through the routes' stateless content. Report the approach.

- [ ] **Step 2: Run it to verify it fails.**
  - Run: `./gradlew :app:testDebugUnitTest --tests "*DetailRoutesInsetTest"`.
  - Expected: `BUILD FAILED`. `:app` does not compile against the new route signatures yet, and the inset assertion fails.

- [ ] **Step 3: Implement.**
  - Remove `PushedScreen { … }` around the four entries.
  - Pass `onEditTagsClick = { song -> song.id.toLongOrNull()?.let { id -> navController.navigate(TagEditor(id)) { launchSingleTop = true } } }` to `AlbumDetailRoute`, `ArtistDetailRoute` and `PlaylistDetailRoute` (both playlist entries). This is the FolderDetail entry's shape.
  - Update the comments that say these screens are padded by `PushedScreen` (`MainActivity.kt` around line 203, `AppShell.kt:30`). `PushedScreen` stays for Search, the tag editors, Equaliser, Settings, About, Licenses and Privacy until 6b.

- [ ] **Step 4: Run the tests to verify they pass.**
  - Run: `./gradlew :app:testDebugUnitTest`. Expected: PASS, with every existing `:app` test green and unmodified.
  - Run the mutation and revert.

- [ ] **Step 5: Run the checks.**
  - Run: `./gradlew :app:check assembleDebug spotlessApply`. Expected: `BUILD SUCCESSFUL`.
  - Report. Do not commit.

---

### Task 9: Docs, the phase gate and reviews (controller)

- [ ] **Step 1: Amend the spec.** Mark each amendment "(amended 2026-10-18, Phase 6a ruling N)".
  - **§5.4:** add the `detailArtworkSize` row.
  - **§5.5:** add the `Delete` icon (line, not mirrored).
  - **§6:** add the signatures of `OneUiBottomSheet`, `OneUiDialog`, `OneUiTextButton`, `OneUiTextField`, `OneUiDropdownMenu`, `OneUiMenuItem` and `SheetActionRow`.
  - **§8.7:**
    - the detail anatomy and sort labels (ruling 5);
    - ⋮ opens the options sheet, with Remove for user playlists (ruling 6);
    - the add-to-playlist sheet (ruling 11);
    - the options sheet closes when its track leaves the list (ruling 10);
    - "New playlist" without the ellipsis.
  - **§9.2:** the artist albums strip is in library order (ruling 8).
- [ ] **Step 2: Update the other docs.**
  - **CLAUDE.md:** the banner gains a Phase 6a bullet. Remove "until Phase 6 moves that padding" wording where it no longer holds for these routes. This is in `MiniPlayerInset.kt`'s KDoc too: Task 8's implementer updates code KDocs; the controller checks.
  - **`ui-style-guide.md` §6:** point the sheets, dialogs and menus at the kit components.
  - **Roadmap:** split row 6 into 6a, 6b and 6c. 6a links this plan.
- [ ] **Step 3: Grep for stale names.** Run `grep -rn "PushedScreen\|isReducedMotion\|New playlist…\|AddToPlaylistSheetHost" CLAUDE.md .claude docs/superpowers/specs app core feature --include=*.kt --include=*.md`, and fix every hit that describes old behaviour. `AddToPlaylistSheetHost` stays if Task 7 left callers; report any that remain.
- [ ] **Step 4: Run the phase gate.**
  - Run: `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug --continue` (timeout 600000 ms). Expected: `BUILD SUCCESSFUL`. Quote the result and the unit-test total.
  - Then run the exit-criterion greps over `main` sources:
    - `grep -rln "material.icons" --include=*.kt app core feature | grep /main/`. Expected: only the 6b files (`PermissionPrompt`, Search, the tag editors).
    - The private dp constants: `grep -rn "private val .* = [0-9.]*\.dp" --include=*.kt app core feature | grep /main/ | grep -v core/designsystem`. Expected: only 6b and 6c files.
- [ ] **Step 5: Run the reviews.** Each runs on `model: opus`, given the phase diff from 6a's BASE as a review-package file:
  - a **`code-reviewer`** against this plan and the spec; a breach of a Global Constraint is at least Important;
  - a **`compose-performance-auditor`** focused on the three detail screens (`LazyColumn` hero items, `SongOptionsHost`'s `LaunchedEffect` keys, `PlaylistSongList`'s drag path after the chrome rewrite).

  The phase is accepted with no Critical or High findings from either, and the gate green.

### Task 10: Device check on the SM-S901B (controller)

The device rules apply as in Phase 5:
- Never uninstall or clear data, and never change settings.
- Leave silent mode on, and send no media keys.
- Confirm the window focus before every injected input.
- Do not create, rename or delete the user's playlists. Playlist-mutating steps run only if the user creates a test playlist themselves.

- [ ] **Step 1: Install and open.** Check `adb devices` (R3CTA03TZTY), then `./gradlew :app:installBenchmark` (the build type the phone carries; `firstInstallTime` must not change). Open AlbumDetail from Albums, ArtistDetail from Artists, and a smart playlist (Recently added) from Playlists. Take a screenshot and a uiautomator dump of each:
  - the One UI top bar, the hero (album: rounded; artist: circle), the sort label and Shuffle/Play;
  - the rows reach the pill, and the last row scrolls above it;
  - no Material arrow icon.
- [ ] **Step 2: The options sheet.** On AlbumDetail, tap a row's ⋮. The sheet shows the track title and the actions "Add to playlist" and "Edit tags", with no Remove. Dismiss it with Back, without choosing anything.
- [ ] **Step 3: The Home menu and the sheet colours.** Open the Home ⋮ menu and the queue sheet (from the mini-player), take screenshots, and dismiss both. They are drawn by the kit, so their colours match the Phase 3 screenshots.
- [ ] **Step 4: Ask the user.** Ask them to open a playlist of their own, if they want to, and try ⋮ → Remove on a track they don't mind removing. Their call: it changes their data.
- [ ] **Step 5: Report.** Report, then say "ready to commit" with a prepared message.

## Self-review

**Spec coverage (§8.7 items owned by 6a):**

| Spec item | Where |
|---|---|
| The detail screens (Album, Artist, Playlist, SmartPlaylist) | Tasks 5–7 (FolderDetail landed in 4b) |
| ArtistDetail keeps its album strip | Task 6 |
| PlaylistDetail keeps drag-reorder and the move actions | Task 7, with a test |
| Sheets (song options, add to playlist, queue) | Tasks 2 and 4 |
| Dialogs (new playlist, save preset, theme picker, plus delete and rename) | Tasks 2, 4 and 7 |
| Menus | Tasks 1, 4 and 7 |
| §12 no dead controls (the album and artist ⋮) | Tasks 5 and 6 |
| §17 exit greps for the 6a files | Task 9 |
| Owned by 6b | Search, the tag editors, About/Licenses/Privacy, the permission prompt |
| Owned by 6c | The widget |

**Carry-overs:**
- **Covered:**
  - the sheet paddings (2), the NewPlaylistRow (2), 2-L7, the PlaylistDetail Cancel colour and header/empty test (2), the `isReducedMotion` migration (Phase 1 rule);
  - 4a: T4 ripple and `isNotBlank`, T5 KDoc, L5;
  - 4b: P1 extraction, T7 stale sheet.
- **Ruled to later phases:** 2-L6 → 7; the Phase 2 permission concern (b) and 4b T8 → 6b; 4a T3 test helper → 7.
- **Ruled not applicable:** 4a T3 duplicates, 4a T4 "card width".

**Placeholders:**
- The test bodies marked `/* … */` describe exact assertions, fixtures and mutations for the implementer to write. Observation 0016 asks for the control to be stated inside each test, and every one states it.
- No production code step is a placeholder.

**Type consistency:** these names and signatures match from the task that produces them to the tasks that use them:
- `SongOptionsHost(…, onRemoveFromPlaylist, currentSongs)` (Task 2 → Tasks 5–7);
- `DetailArtworkHeader(artworkUri, subtitle, modifier, circular, placeholderGlyph)` (Task 2 → Tasks 5 and 6);
- `createPlaylistWithSong(name, songId): Long?` (Task 3 → Tasks 5–7);
- the route signatures (Tasks 5–7 → Task 8);
- `OneUi*` and `SheetActionRow` (Task 1 → all).

**Companion checks (superpowers-extras writing-plans, plus open observations 0016 and 0017):**
- **(1) Dependent phases:** written after Phase 5 was accepted, from the landed code. 6b and 6c consume the Task 1 kit.
- **(2a) Value tables:** the spec §8.7 tokens are used verbatim: `surfaceContainerHigh` sheets and dialogs, the `large` and `medium` shapes, `sectionHeader` titles, `accentText` buttons, the 16 dp field radius (`shapes.medium`). The one deviation is the sort labels, which the spec doesn't give (ruling 5).
- **(2b) Consumers:**
  - the four `createPlaylistAndAddSong` ViewModels keep their public API (Task 3);
  - the `SongOptionsHost` callers compile, because the new parameters have defaults;
  - the route changes are wired in Task 8, and Tasks 5–7 state that `:app` waits for it.
- **(2c) Test level:** the dialog and sheet colours are tested by pixels through `drawLatestDialog` (0017), not inferred from parameters.
- **(2d) Can each test fail:** every test names a mutation, and has an in-test control (0016). The absence tests (`Remove` not offered, the dialog absent before a tap, the album not found) all show presence first.
- **(3) Framework-validated output:** real `ModalBottomSheet`, `AlertDialog` and `DropdownMenu` windows are driven through Robolectric, not mocked.
- **(4) Mutations:** all are valid usage. Two have explicit substitution clauses: the Task 4 text-height probe and the Task 5 long-title probe.
- **(5) Device:**
  - every input lands on our own window, with a focus check first;
  - there are no process kills;
  - no step changes user data; the one data-changing check is the user's choice (Task 10 Step 4).
  - No Task 0 probe is needed, because no outcome in 6a is drawn by OS or vendor UI. The widget (launcher-drawn) is 6c, and 6c starts with a probe.
