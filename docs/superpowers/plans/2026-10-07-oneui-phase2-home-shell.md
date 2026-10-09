# One UI redesign, Phase 2: Home shell, Tracks and Playlists tabs

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app starts on a One UI Home screen. A header, a center-weighted tab row and a horizontal pager of
edge-to-edge panels hold the restyled Tracks and Playlists tabs. The mini-player floats over the content, and the M3
bottom navigation is gone.

**Architecture:**
- `:app/home/` owns the shell: `HomeTab`, `HomeViewModel` (the permission-triggered library sync) and a stateless
  `HomeScreen` with a `pageContent` slot, so it can be tested without Hilt.
- Each tab is a stateful composable in its feature module (`TracksTab` in `:feature:library`, `PlaylistsTab` in
  `:feature:playlists`) with its own `hiltViewModel()`.
- Shared rows and cards live in `:core:ui`: `SongListItem`, `PlaylistRow`, and the new `FeatureCard` and
  `SongSelectionState`.
- The nav host's `Scaffold` becomes a `Box` with the mini-player overlaid, and the host provides `LocalMiniPlayerInset`.

**Tech stack:** Kotlin 2.0.21, Compose BOM 2024.09.03 (Foundation 1.7.3, M3 1.3.0), Navigation Compose with type-safe
routes, Hilt, and Robolectric 4.13 for JVM Compose tests.

**Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md):
- §7 (Home shell)
- §8.1–§8.2 (rows, Tracks and Playlists)
- §12 (Shuffle and Play)
- §15 (tests)

Read it with the roadmap [`2026-10-06-oneui-redesign-roadmap.md`](2026-10-06-oneui-redesign-roadmap.md) and the landed
Phase 1 kit (commit `3e148f9`).

## How to read this plan

- **New, self-contained units** come as complete code: the tokens, `SongSelectionState`, `FeatureCard`, `HomeTab`,
  `HomeViewModel`, the convention plugin and the mini-player visibility rule.
- **Refactors of large existing files** come as exact target signatures, a numbered transformation list against the
  current file's line numbers, and full test code. These are `LibraryScreen.kt` → `TracksTab.kt`,
  `PlaylistsScreen.kt` → `PlaylistsTab.kt`, and the nav host in `MainActivity.kt`. Re-typing 400+ lines here would
  duplicate the file the implementer already has, and Phase 1 showed that hand-written verbatim code drifts.
- **Code blocks have not been compiled.** Implementers may make minimal mechanical fixes (imports, opt-ins) and must
  report each one as a deviation. Never change behaviour, signatures or assertions to get to green.
- **Every test names its mutation:** one realistic code change that must turn it RED. Implementers apply it, confirm
  RED, revert, and record the output. A surviving mutation is a defect, not a pass.

## Global Constraints

- minSdk 24, compileSdk 34, JVM 17.
- No new runtime dependencies. Test-only dependencies come from the version catalog.
- Offline app: no network libraries, no INTERNET permission.
- Never hardcode visual values outside `:core:designsystem`. Screens use `RollaDimens`, the typography and color
  extensions, `RollaShapes` and `RollaIcons`. The only exception is preview scaffolding.
- **Never use `primary` as a text color.** Blue text uses `accentText`. Every `TextButton` and focused `TextField` this
  phase touches passes explicit `accentText` content colors.
- UI copy says **"Tracks"** (the "Tracks" tab, "N tracks"). Code identifiers stay `Song` (`model-vocabulary.md`).
- App title: **"Rolla Music"**. No Samsung branding.
- No dead controls. Every visible button does something real (spec §12).
- Reduced motion: animations use `rollaSpringOrSnap(rememberReducedMotion())`, or snap or scroll instantly when that
  is true.
- Robolectric tests:
  - Run at SDK 34. Each module gets `src/test/resources/robolectric.properties` containing `sdk=34`. The SDK 34 jar
    is cached; any other SDK would trigger a download.
  - Layout tests that need more than 320 dp of width use `@Config(qualifiers = "w360dp-h640dp")`.
  - Font-scale tests use `@GraphicsMode(NATIVE)`.
  - Footprints of merging controls are measured on a wrap-content host `Box`.
- Spotless enforces CRLF. Run `./gradlew spotlessApply` after editing Kotlin. Never write a BOM. Edit with the
  Write/Edit tools, never `sed -i` (Git Bash's sed strips the CRs).
- Detekt:
  - LongMethod: 30 lines.
  - `LongParameterList`: 6 parameters for functions.
  - `MagicNumber`: property declarations are exempt.
  - A narrow `@Suppress` with a one-line reason is accepted.
- **No `git commit`, `git add` or `git stash`.** Each task ends at its checkpoint, the orchestrator reviews the
  working-tree diff, and the user triggers commits.
- The baseline-profile contract survives:
  - the Tracks list keeps `testTag("song_list")`;
  - the root keeps `Modifier.semantics { testTagsAsResourceId = true }`;
  - the mini-player keeps the "…Open player." content description;
  - Home opens on Tracks.

## Controller rulings carried into this plan

1. **No dead tabs.** In this phase `HomeTab` lists only the tabs that exist: `PLAYLISTS("Playlists")` and
   `TRACKS("Tracks")`, in spec order. Phase 4a inserts `FAVOURITES` before `PLAYLISTS` and appends `ALBUMS` and
   `ARTISTS`; Phase 4b appends `FOLDERS`. The initial page is always `HomeTab.TRACKS.ordinal`.
2. **Library sync moves to `HomeViewModel`.** Spec §7.2 puts `MediaPermissionGate` around the whole pager, so the
   sync and `ScanState` move from `LibraryViewModel` into `:app`'s `HomeViewModel`. `TracksTab` receives `scanState`
   as a parameter. The three `onPermissionGranted` tests move with the code and are not deleted.
3. **The mini-player inset is measured, not assumed.** The overlay reports its height through `onSizeChanged`,
   excluding the nav-bar inset, and provides it as `LocalMiniPlayerInset`. It is 0 dp while the pill is hidden. This
   stays correct when Phase 3 resizes the pill to `miniPlayerHeight`. The `navigationBarsPadding()` inside `MiniPlayer`
   moves out to the host, which becomes its single owner.
4. **Pushed screens keep working without the Scaffold padding.** Until each screen's own phase restyles it, the nav host
   pads every non-Home destination:
   - at the bottom, by `LocalMiniPlayerInset` only (as landed in Task 8: every pushed screen is a `Scaffold` that
     already owns its navigation-bar inset, so adding it again would double it);
   - horizontally, by the safe-drawing horizontal insets.

   Home pads only its lists, so content scrolls under the pill (spec §7.3). Phase 6 replaces the per-destination
   padding with list `contentPadding`.
5. **Row trailing geometry is reserved for the A–Z rail now.** Rows end where the measured reference rows end (the rail
   arrives in Phase 5), so the rail later overlays without moving any row.
6. **The selection highlight becomes `surfaceContainerHigh`** (it was `primaryContainer`), and the checked checkbox is
   `primary` with an `onPrimary` check. The old primary-on-primaryContainer pairing measured 2.51:1 and failed AA in
   dark (phase 1 review M4).
7. **The tab ripple stays as it is.** The user hasn't yet chosen between no ripple and a pill-shaped ripple, so the
   orchestrator raises it again at this phase's acceptance.

## Review Focus

1. **Process death or rotation on Home** restores the selected tab and an active selection. Tested with
   `StateRestorationTester` in Task 7 (the tab) and Task 3 (`SongSelectionState`).
2. **The last row hidden behind the mini-player:** with a 72 dp inset provided, the last Tracks row scrolls fully above
   it. Tested in Task 5.
3. **Back during selection** clears the selection and stays on Home. Tested in Task 7.
4. **Swiping while selecting** must not change the page, because pager scroll is disabled. Tested in Task 7.
5. **Shuffle or Play on an empty library** is a no-op, not a crash and not an empty queue. Tested in Task 5 (the
   ViewModel).

---

### Task 1: Robolectric test convention plugin

**Owner:** build-tooling-agent (general-purpose, `model: "opus"`)

**Files:**
- Create: `build-logic/convention/src/main/kotlin/RollaAndroidRobolectricPlugin.kt`
- Modify:
  - `build-logic/convention/build.gradle.kts`: register `rolla.android.robolectric`.
  - `core/designsystem/build.gradle.kts`: replace its manual test wiring at :11–16 and :29–35 with the plugin.
  - `core/ui/build.gradle.kts`, `feature/library/build.gradle.kts`, `feature/playlists/build.gradle.kts` and
    `app/build.gradle.kts`: apply the plugin.
- Create: `src/test/resources/robolectric.properties` (a single line, `sdk=34`) in `core/ui`, `feature/library`,
  `feature/playlists` and `app`.

**Interfaces:**
- Produces: the plugin id `rolla.android.robolectric`. On an Android library or application module it:
  - sets `testOptions.unitTests.isIncludeAndroidResources = true`;
  - adds these `testImplementation` dependencies: `junit`, `robolectric`, `androidx-junit`,
    `platform(androidx-compose-bom)`, `androidx-compose-ui-test-junit4` and `androidx-compose-ui-test-manifest`.

- [ ] **Step 1: Write the plugin**

```kotlin
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * JVM (Robolectric) Compose tests. ui-test-manifest is testImplementation, not debugImplementation, because `check`
 * also runs testReleaseUnitTest, which needs ComponentActivity in the merged manifest.
 */
class RollaAndroidRobolectricPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { testOptions.unitTests.isIncludeAndroidResources = true }
            }
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { testOptions.unitTests.isIncludeAndroidResources = true }
            }
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
                add("testImplementation", libs.findLibrary("androidx-junit").get())
                add("testImplementation", platform(libs.findLibrary("androidx-compose-bom").get()))
                add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
                add("testImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
            }
        }
    }
}
```

Register it next to the others:

```kotlin
register("rollaAndroidRobolectric") {
    id = "rolla.android.robolectric"
    implementationClass = "RollaAndroidRobolectricPlugin"
}
```

Read the real aliases in `gradle/libs.versions.toml` first. If one differs (for example the BOM alias), use the real
alias and record the change as a deviation.

- [ ] **Step 2: Apply it**
  - In `:core:designsystem`, add `id("rolla.android.robolectric")`. Then delete the `testOptions` block and the six
    `testImplementation` lines the plugin now provides.
  - Add the plugin id to `:core:ui`, `:feature:library`, `:feature:playlists` and `:app`.
  - Create the four `robolectric.properties` files.
  - Leave existing junit, mockk and turbine lines alone; duplicates are deduped.

- [ ] **Step 3: Prove the wiring**
  - `./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:testReleaseUnitTest` must show 60/60 in both
    variants, unchanged.
  - **Mutation:** comment out the ui-test-manifest line in the plugin. `:core:designsystem:testReleaseUnitTest` must
    then FAIL (no `ComponentActivity`). Revert.

- [ ] **Step 4: Checkpoint (no commit)**
  - `./gradlew spotlessApply`
  - `./gradlew :core:ui:testDebugUnitTest :feature:library:testDebugUnitTest :feature:playlists:testDebugUnitTest :app:testDebugUnitTest`.
    Existing suites stay green.
  - `./gradlew detekt`
  - `git status --short`

---

### Task 2: Kit carry-overs from the Phase 1 review (tokens, line height, contrast)

**Owner:** m3-design-system-agent (general-purpose, `model: "opus"`)

**Files:**
- Modify: `core/designsystem/.../theme/Dimens.kt` (the Lists and Feature cards blocks) and `.../theme/Type.kt`
  (`rollaStyle`).
- Modify (tests): `core/designsystem/src/test/.../theme/TokenInvariantsTest.kt` and `.../theme/ContrastTest.kt`.
- Create (test): `.../theme/TypographyTokensTest.kt`.

**Interfaces:**
- Produces:
  - `RollaDimens.listOverflowEnd = 26.dp`
  - `RollaDimens.listTrailingEnd = 43.dp`
  - `RollaDimens.listDividerEnd` (get: `fastScrollWidth + fastScrollEnd`)
  - `RollaDimens.featureCardLabelGap = 8.dp`
  - `RollaDimens.featureCardRowStart = 18.dp`
  - Every style built by `rollaStyle` carries
    `lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)`.

- [ ] **Step 1: Write the failing tests**

Add to `TokenInvariantsTest`:

```kotlin
@Test
fun rowTrailingContentStaysClearOfTheFastScrollRail() {
    // Rows end where the measured reference rows end; Phase 5's rail sits in the space after them.
    assertTrue(RollaDimens.listTrailingEnd >= RollaDimens.fastScrollWidth + RollaDimens.fastScrollEnd)
    assertTrue(RollaDimens.listDividerEnd == RollaDimens.fastScrollWidth + RollaDimens.fastScrollEnd)
}
```

Mutation: `listTrailingEnd = 20.dp` turns it RED.

Create `TypographyTokensTest.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.text.style.LineHeightStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTokensTest {

    private val expected = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    @Test
    fun everyMaterialRoleUsesCenteredUntrimmedLineHeight() {
        val t = RollaTypography
        listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        ).forEachIndexed { index, style -> assertEquals("role #$index", expected, style.lineHeightStyle) }
    }
}
```

Mutation: removing `lineHeightStyle` from `rollaStyle` turns it RED. If `RollaTypography` or `rollaStyle` has a
different name in the landed `Type.kt`, use the real name and record the deviation.

In `ContrastTest`'s shared pairing table, add rows in the existing `Pairing(...)` style. They cover both themes:
- `onSurfaceVariant on surfaceContainerHigh` at TEXT 4.5 (the selected-row subtitle).
- `primary on surfaceContainerHigh` at NON_TEXT 3.0 (the checked checkbox on a selected row). Skip it if it is
  already present.

Mutation: pointing light `surfaceContainerHigh` at `onSurfaceVariant`'s value temporarily turns the new row RED.

- [ ] **Step 2: Confirm RED.** Run `./gradlew :core:designsystem:testDebugUnitTest`; it should fail to compile on the
  missing tokens.

- [ ] **Step 3: Implement**

In `Dimens.kt`, Lists block:

```kotlin
// Row trailing geometry, measured from the panel's end edge on the reference (Tracks/Playlists).
// The space beyond each value is the A–Z rail's (Phase 5); rows never move when the rail lands.
val listOverflowEnd = 26.dp // end of the ⋮ 48 dp touch box (glyph centre ≈ x 310 of 360)
val listTrailingEnd = 43.dp // end of trailing text such as "0 tracks"
val listDividerEnd: Dp get() = fastScrollWidth + fastScrollEnd
```

In the Feature cards block:

```kotlin
// Not measured on the reference (no vertical px for the card text); Phase 7 calibrates.
val featureCardLabelGap = 8.dp
val featureCardRowStart = 18.dp // aligns the first card with the list thumbnails (listThumbStart)
```

In `Type.kt`, add `lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)` to
the `TextStyle` that `rollaStyle` builds. This is M3's own default: text sits centered in its declared line height
inside fixed-height rows.

- [ ] **Step 4: GREEN, then each mutation.** Run
  `./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:testReleaseUnitTest`.
- [ ] **Step 5: Checkpoint (no commit).** Run spotlessApply, `:core:designsystem:detekt`,
  `./gradlew :app:compileDebugKotlin`, then `git status --short`.

---

### Task 3: `SongSelectionState` and the restyled `SongListItem`

**Owner:** ui-builder (general-purpose, `model: "opus"`)

**Files:**
- Create: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/SongSelectionState.kt`
- Modify: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/SongListItem.kt` (restyle it and keep its signature)
- Test (create): `core/ui/src/test/kotlin/com/rolla/musicplayer/core/ui/SongSelectionStateTest.kt` and
  `.../SongListItemTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Stable
class SongSelectionState internal constructor(initial: Set<String>) {
    val selectedIds: Set<String>          // snapshot-state backed
    val count: Int
    val isActive: Boolean                 // count > 0
    fun isSelected(songId: String): Boolean
    fun toggle(songId: String)
    fun clear()
    companion object { val Saver: Saver<SongSelectionState, Any> }
}
@Composable fun rememberSongSelectionState(): SongSelectionState   // rememberSaveable(saver = Saver)
```

- `SongListItem(...)` keeps exactly the signature at `SongListItem.kt:69-84`.

- [ ] **Step 1: Write the failing `SongSelectionStateTest`** (Robolectric, because of the restoration test)

```kotlin
package com.rolla.musicplayer.core.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongSelectionStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun toggleAddsThenRemovesAndClearEmpties() {
        val state = SongSelectionState(emptySet())
        state.toggle("a")
        state.toggle("b")
        assertEquals(setOf("a", "b"), state.selectedIds)
        assertTrue(state.isActive)
        state.toggle("a")
        assertFalse(state.isSelected("a"))
        assertEquals(1, state.count)
        state.clear()
        assertFalse(state.isActive)
    }

    @Test
    fun selectionSurvivesRecreation() {
        val tester = StateRestorationTester(composeRule)
        lateinit var state: SongSelectionState
        tester.setContent { state = rememberSongSelectionState() }
        composeRule.runOnIdle { state.toggle("42") }
        tester.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { assertTrue(state.isSelected("42")) }
    }
}
```

Mutations:
- Make `toggle` add only: the first test goes RED.
- Use `remember` instead of `rememberSaveable`: the second test goes RED.

- [ ] **Step 2: Write the failing `SongListItemTest`** (Robolectric, class-level `@Config(qualifiers = "w360dp-h640dp")`,
  `RollaMusicPlayerTheme(darkTheme = true)`, an inline `Song` fixture as in `LibraryViewModelTest`)
  1. `rowIsListRowHeightTall`
     - Host: `Box(Modifier.testTag("host")) { SongListItem(song, onClick = {}) }`.
     - Assert: the host height equals `RollaDimens.listRowHeight`.
     - Mutation: put the old 12 dp vertical padding back, or remove `heightIn`. RED.
  2. `overflowEndsAtTheMeasuredInset`
     - Host: a 360 dp Box.
     - Assert: the node with contentDescription `"More options for <title>"` has `boundsInRoot.right` at
       `360.dp − RollaDimens.listOverflowEnd` (±0.5 dp).
     - Mutation: drop the end padding. RED.
  3. `titleStartsAtListTextStart`
     - Assert: the left of `onNodeWithText(song.title, useUnmergedTree = true)` is at `RollaDimens.listTextStart`
       (±0.5 dp).
     - Mutation: change the thumb-to-text spacer. RED.
  4. `selectedRowInSelectionModeReportsSelectedAndShowsCheckbox`
     - Set `selected = true, selectionModeActive = true`.
     - Assert: the merged row is selected, and with `useUnmergedTree = true` a `Role.Checkbox` node exists with
       `ToggleableState.On`.
     - Mutation: drop `selected` from the semantics. RED.
  5. `trackNumberModeReplacesArtwork`
     - Set `trackNumber = 7`.
     - Assert: a node with contentDescription `"Track 7"` exists.
     - Mutation: ignore `trackNumber`. RED.

- [ ] **Step 3: Implement `SongSelectionState.kt`**

```kotlin
package com.rolla.musicplayer.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Multi-select over song ids for list screens (spec §7.2). Rows read [isSelected] inside their own derivedStateOf,
 * so toggling one row recomposes only that row. Saved across process death as a list of ids.
 */
@Stable
class SongSelectionState internal constructor(initial: Set<String>) {
    var selectedIds: Set<String> by mutableStateOf(initial)
        private set

    val count: Int get() = selectedIds.size
    val isActive: Boolean get() = selectedIds.isNotEmpty()

    fun isSelected(songId: String): Boolean = songId in selectedIds

    fun toggle(songId: String) {
        selectedIds = if (songId in selectedIds) selectedIds - songId else selectedIds + songId
    }

    fun clear() {
        selectedIds = emptySet()
    }

    companion object {
        val Saver: Saver<SongSelectionState, Any> = listSaver(
            save = { it.selectedIds.toList() },
            restore = { SongSelectionState(it.toSet()) },
        )
    }
}

@Composable
fun rememberSongSelectionState(): SongSelectionState =
    rememberSaveable(saver = SongSelectionState.Saver) { SongSelectionState(emptySet()) }
```

- [ ] **Step 4: Restyle `SongListItem.kt`.** Keep the public signature and apply these changes:
  1. Delete the private dp constants (:49-53) and use tokens:
     - Row: `heightIn(min = RollaDimens.listRowHeight)`, vertically centered, with no vertical padding.
     - Leading space: `Spacer(width = listThumbStart)`.
     - Thumbnail: `size(listThumb)` with `clip(shapes.small)`.
     - Thumb-to-text spacer: `listTextStart − listThumbStart − listThumb` wide.
     - End: `padding(end = RollaDimens.listOverflowEnd)` on the row content, not on the background, so the highlight
       still runs edge to edge.
  2. **Placeholder.** Use `ArtworkPlaceholder(Modifier.size(listThumb), shapes.small)` with the Coil `AsyncImage`
     drawn on top when there is art.
  3. **Overflow.** Use a 48 dp target with `RollaIcons.More` tinted `onSurfaceVariant`, sized
     `RollaDimens.overflowGlyph`. `OneUiIconButton(icon = RollaIcons.More, …, tint = onSurfaceVariant,
     iconSize = RollaDimens.overflowGlyph)` does all of this.
  4. **Divider.** Use `InsetDivider(startInset = RollaDimens.listTextStart, endInset = RollaDimens.listDividerEnd)` in
     place of `HorizontalDivider(padding(start = 84.dp))`.
  5. **Selection (ruling 6).**
     - The selected background is `surfaceContainerHigh`.
     - The subtitle stays `onSurfaceVariant` in every state; delete the `onPrimaryContainer` branch.
     - The Checkbox uses `CheckboxDefaults.colors(checkedColor = primary, uncheckedColor = onSurfaceVariant,
       checkmarkColor = onPrimary)`.
  6. **Track-number mode.** The label box is `width(listThumb)`, placed at `listThumbStart`, so the text still starts at
     `listTextStart`. Its style is `artistName` and its color `onSurfaceVariant`.
  7. **Unchanged.** Keep `combinedClickable`, the selection semantics, `mergeDescendants` and the content descriptions
     exactly as they are.

- [ ] **Step 5: GREEN, then each mutation.** Run `./gradlew :core:ui:testDebugUnitTest`.
- [ ] **Step 6: Consumer audit.** `SongListItem` also renders on AlbumDetail (track numbers), ArtistDetail,
  PlaylistDetail/SmartPlaylist and Search.
  - Grep every `SongListItem(` call site and list them in the report.
  - Run `./gradlew :feature:library:testDebugUnitTest :feature:playlists:testDebugUnitTest :feature:search:testDebugUnitTest`.
  - Note that these screens get the new 26 dp end inset, which includes the rail space, until Phase 6 restyles them.
    That is accepted interim.
- [ ] **Step 7: Checkpoint (no commit).** Run spotlessApply, `:core:ui:detekt` and
  `./gradlew :core:ui:assembleDebugAndroidTest` (the existing androidTest must compile), then `git status --short`.

---

### Task 4: `FeatureCard` and the restyled `PlaylistRow`

**Owner:** ui-builder (general-purpose, `model: "opus"`)

**Files:**
- Create: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/FeatureCard.kt`
- Modify: `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/PlaylistRow.kt`
- Test (create): `core/ui/src/test/kotlin/com/rolla/musicplayer/core/ui/FeatureCardAndPlaylistRowTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Composable fun FeatureCard(
    label: String,
    countLabel: String,
    artworkUris: List<String>,       // 0 → placeholder note, 1 → single art, ≥ 2 → 2×2 collage of the first four
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- `PlaylistRow(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier)` keeps its signature.

- [ ] **Step 1: Write the failing tests.** These are Robolectric tests with `w360dp-h640dp` and the dark theme.
  1. `featureCardIsOneClickableNodeWithLabelAndCount`
     - Render `FeatureCard("Favourite tracks", "5 tracks", emptyList(), onClick = { clicks++ })`.
     - Expect one merged node carrying both texts and a click action. One click gives `clicks == 1`.
     - Mutation: remove the merge or the clickable. The test goes RED.
  2. `featureCardArtBoxIsTheMeasuredSize`
     - The art box has testTag `"feature_card_art"`. Its width and height equal `RollaDimens.featureCardSize`.
     - Mutation: `size(150.dp)` turns it RED.
  3. `playlistRowIsSingleLineHeightWithTrailingCount`
     - Render `Playlist(id = 1, name = "Light", songCount = 0, …)` in a 360 dp Box. Check the real constructor first.
     - The host height equals `singleLineRowHeight`.
     - The "0 tracks" node's right edge is at `360.dp − RollaDimens.listTrailingEnd`, within ±0.5 dp.
     - Mutation: dropping the end padding turns it RED.

- [ ] **Step 2: Confirm RED.**

- [ ] **Step 3: Implement `FeatureCard.kt`**

```kotlin
package com.rolla.musicplayer.core.ui

// imports: foundation layout, clickable, semantics, testTag, Text, MaterialTheme, AsyncImage, ContentScale,
// ArtworkPlaceholder, RollaDimens, the featureCard shape, featureCardLabel/featureCardCount typography.

/**
 * One UI feature card (spec §8.1): a [RollaDimens.featureCardSize] square art box in the featureCard shape, with the
 * label and count centered below. Art is a placeholder note, a single image, or a 2×2 collage of the first four.
 * The whole card is one TalkBack node ("<label>, <count>").
 */
@Composable
fun FeatureCard(
    label: String,
    countLabel: String,
    artworkUris: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(RollaDimens.featureCardSize)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeatureCardArt(artworkUris = artworkUris, modifier = Modifier.testTag("feature_card_art"))
        Spacer(Modifier.height(RollaDimens.featureCardLabelGap))
        Text(
            text = label,
            style = MaterialTheme.typography.featureCardLabel,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = countLabel,
            style = MaterialTheme.typography.featureCardCount,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun FeatureCardArt(artworkUris: List<String>, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.featureCard
    Box(modifier = modifier.size(RollaDimens.featureCardSize).clip(shape)) {
        ArtworkPlaceholder(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            large = true,
            glyphSize = RollaDimens.featureCardGlyph,
        )
        when {
            artworkUris.size >= 2 -> Collage(artworkUris.take(4))
            artworkUris.size == 1 -> AsyncImage(
                model = artworkUris.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
```

`Collage` is a private 2×2 grid of cropped `AsyncImage`s, each with `weight(1f)`. Cells with no image (when there are
only 2 or 3) show the placeholder behind them. Port the existing `SmartPlaylistCollage` logic from `PlaylistsScreen.kt`.
If the typography or shape extension names differ, read `Type.kt` and `Shape.kt` and use the real ones.

- [ ] **Step 4: Restyle `PlaylistRow.kt`, keeping the signature.**
  - Row: `heightIn(min = singleLineRowHeight)`.
  - Leading: `ArtworkPlaceholder(size(listThumb), shapes.small)` at `listThumbStart`, with the text at `listTextStart`.
  - Name: `songTitle` style, `onSurface`, one line.
  - Trailing: `"${playlist.songCount} tracks"` in `rowTrailing` (or `metadata`, if that style doesn't exist), colored
    `onSurfaceVariant`, with `padding(end = listTrailingEnd)`.
  - Delete the private dp constants.
  - Keep the row as one merged clickable node.

- [ ] **Step 5: GREEN, then each mutation.**
- [ ] **Step 6: Consumer audit.** Grep every `PlaylistRow(` call site (expected: Playlists only; check the add-to-playlist
  sheet) and list them in the report. Run the owning modules' tests.
- [ ] **Step 7: Checkpoint (no commit).** Run spotlessApply and `:core:ui:detekt`, then `git status --short`.

---

### Task 5: `TracksTab` (`:feature:library`)

**Owner:** ui-builder with viewmodel-architect (general-purpose, `model: "opus"`)

**Files:**
- `feature/library/.../LibraryViewModel.kt` becomes `TracksViewModel.kt`. Drop the sync and `scanState` (they move
  to `HomeViewModel` in Task 7) and add `shuffleAll()`/`playAll()`.
- `feature/library/.../LibraryScreen.kt` is replaced by `TracksTab.kt`.
- `feature/library/src/test/.../LibraryViewModelTest.kt` becomes `TracksViewModelTest.kt`.
  - Copy the three `onPermissionGranted_*` tests to a scratch file for Task 7 before removing them here.
  - Add the shuffle and play tests.
- `feature/library/src/androidTest/.../LibraryScreenSmokeTest.kt` becomes `TracksTabSmokeTest.kt`, hosting
  `TracksTabContent`.
- Test (create): `feature/library/src/test/.../TracksTabTest.kt` (Robolectric).

**Interfaces:**
- Consumes:
  - `SongSelectionState` and `SongListItem` (Task 3), and `RollaDimens` (Task 2).
  - From Phase 1: `SortHeader`, `CircleIconButton`, `LocalMiniPlayerInset` and `RollaIcons.Shuffle`/`Play`.
  - `ScanState`, which stays in `:feature:library`.
- Produces:

```kotlin
// TracksTab.kt
const val SONG_LIST_TEST_TAG = "song_list"

@Composable fun TracksTab(
    selection: SongSelectionState,
    scanState: ScanState,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = hiltViewModel(),
)

@Composable internal fun TracksTabContent(        // stateless; tests and previews host this
    songs: List<Song>,
    scanState: ScanState,
    selection: SongSelectionState,
    userPlaylists: List<Playlist>,
    onSongClick: (Song) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCreatePlaylistAndAddSong: (String, String) -> Unit,
    onEditTagsClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
)

// TracksViewModel.kt
@HiltViewModel class TracksViewModel @Inject constructor(
    songRepository: SongRepository,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    val songs: StateFlow<List<Song>>
    val userPlaylists: StateFlow<List<Playlist>>
    fun play(song: Song)
    fun playAll()          // no-op if songs empty; setShuffle(OFF) then playAll(songs, 0)
    fun shuffleAll()       // no-op if songs empty; setShuffle(ON) then playAll(songs, 0)
    fun addSongToPlaylist(songId: String, playlistId: Long)
    fun createPlaylistAndAddSong(name: String, songId: String)
}
```

If the androidTest source set can't see `internal`, make `TracksTabContent` public, add a KDoc line saying it is
visible for tests and previews, and record the change.

- [ ] **Step 1: Write the failing ViewModel tests** in `TracksViewModelTest`, in the existing file's style:

```kotlin
@Test
fun `playAll_turnsShuffleOffAndPlaysFromTheFirstSong`() = runTest {
    val songs = listOf(song("1"), song("2"))
    val job = launch { viewModel.songs.collect {} }
    fakeRepository.emit(songs)
    advanceUntilIdle()
    viewModel.playAll()
    verifyOrder {
        playbackController.setShuffle(ShuffleMode.OFF)
        playbackController.playAll(songs, 0)
    }
    job.cancel()
}

@Test
fun `shuffleAll_turnsShuffleOnAndPlaysAll`() = runTest {
    val songs = listOf(song("1"), song("2"))
    val job = launch { viewModel.songs.collect {} }
    fakeRepository.emit(songs)
    advanceUntilIdle()
    viewModel.shuffleAll()
    verifyOrder {
        playbackController.setShuffle(ShuffleMode.ON)
        playbackController.playAll(songs, 0)
    }
    job.cancel()
}

@Test
fun `playAll_and_shuffleAll_onEmptyLibrary_doNothing`() = runTest {      // Review Focus 5
    viewModel.playAll()
    viewModel.shuffleAll()
    verify(exactly = 0) { playbackController.playAll(any(), any()) }
    verify(exactly = 0) { playbackController.setShuffle(any()) }
}
```

Add a private `song(id)` fixture helper modeled on the existing inline `Song(...)`.

Mutations:
- Swapping OFF and ON turns the first two RED.
- Dropping the empty guard turns the third RED.

- [ ] **Step 2: Write the failing `TracksTabTest`.**
  - **Setup:** Robolectric, `w360dp-h640dp`, dark theme.
  - **Host:** `TracksTabContent` with 30 fixture songs and `ScanState.Done(0, 0)`.
  - **Selection:** create it with `rememberSongSelectionState()` inside `setContent`; its constructor is internal to
    `:core:ui`.

  Tests:
  1. `sortHeaderButtonsInvokeShuffleAndPlay`
     - Click "Shuffle all tracks", then "Play all tracks". Each callback runs once.
     - Mutation: wire `{}`. RED.
  2. `songListKeepsTheBaselineProfileTag`
     - `onNodeWithTag(SONG_LIST_TEST_TAG)` exists.
     - Mutation: drop the tag. RED.
  3. `lastRowScrollsFullyAboveTheMiniPlayer` (Review Focus 2)
     - Wrap the content in `CompositionLocalProvider(LocalMiniPlayerInset provides 72.dp)` inside
       `Box(Modifier.height(400.dp))`.
     - Call `performScrollToIndex(29)` on the list.
     - The last song's row bottom is at most `400.dp − 72.dp`, within ±0.5 dp.
     - Mutation: omit the inset from `contentPadding`. RED.
  4. `longPressStartsSelectionAndTapTogglesInSelectionMode`
     - Long-press row 0: id0 is selected. Tap row 1: it is selected too. Tap row 0 again: it is deselected.
     - `onSongClick` is never called.
     - Mutation: always call `onSongClick` on tap. RED.
  5. `emptyLibraryShowsTheEmptyState`
     - With `songs = emptyList()` and `Done`, "No music found on this device" is displayed.
     - Mutation: render the list anyway. RED.
  6. `scanningShowsProgress`
     - With `ScanState.Scanning`, a `ProgressBarRangeInfo` node exists and no `song_list` node does.
     - Mutation: ignore `scanState`. RED.

- [ ] **Step 3: Confirm RED.**

- [ ] **Step 4: Implement `TracksViewModel.kt`.** Rename `LibraryViewModel`, delete `libraryIndexer`, `_scanState`,
  `scanState` and `onPermissionGranted()`, then add:

```kotlin
fun playAll() {
    val list = songs.value
    if (list.isEmpty()) return
    playbackController.setShuffle(ShuffleMode.OFF)
    playbackController.playAll(list, startIndex = 0)
}

fun shuffleAll() {
    val list = songs.value
    if (list.isEmpty()) return
    playbackController.setShuffle(ShuffleMode.ON)
    playbackController.playAll(list, startIndex = 0)
}
```

`songs` uses `WhileSubscribed`, and `TracksTab` collects it while it is visible, so `songs.value` is current when a
button is tapped. This mirrors `AlbumDetailViewModel.onPlayClick`/`onShuffleClick`.

- [ ] **Step 5: Build `TracksTab.kt` from `LibraryScreen.kt`.** Apply these changes against the current file:
  1. **Wrapper.**
     - `LibraryRoute` becomes `TracksTab(selection, scanState, onEditTagsClick, modifier, viewModel)`.
     - Delete `MediaPermissionGate` (Home owns it), `onSearchClick` and `onSettingsClick` (Home's header owns them),
       and `onEditTagsForSelection` (Home's selection bar owns it).
     - Collect `songs` and `userPlaylists` with `collectAsStateWithLifecycle()` and pass lists, not `StateFlow`s.
  2. **Content.** `LibraryScreen` becomes `TracksTabContent` (signature above). Delete:
     - the inner `Scaffold`, `LibraryTopBar`, `LibraryOverflowMenu`, `LibrarySelectionTopBar` and `EditTagsAction`;
     - the screen-local selection state (`SelectedSongIdsSaver`, `selectedSongIdsState`), replaced by `selection`;
     - the local `BackHandler`.
  3. **Layout.**
     - Content fills the `ContentPanel` that Home provides. Delete the local `Surface`, its 8 dp margins and the
       private dp constants (:76-83).
     - Column: `SortHeader(label = "Name")`, with trailing `CircleIconButton(RollaIcons.Shuffle, "Shuffle all tracks",
       onShuffleClick)` and `CircleIconButton(RollaIcons.Play, "Play all tracks", onPlayClick)`.
     - Then a `when`:
       - `Scanning`: a centered `CircularProgressIndicator(color = primary)`.
       - Empty: a centered `Text("No music found on this device", style = emptyState, color = onSurfaceVariant)`. If
         `emptyState` doesn't exist, use the closest semantic style and record it.
       - Otherwise: the list.
     - Drop the old "N songs" count; the spec header has none.
  4. **List.**
     - `LazyColumn(Modifier.testTag(SONG_LIST_TEST_TAG))` with
       `contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current + navBarBottom)`.
       `navBarBottom` comes from `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`.
     - `items(songs, key = { it.id })`.
     - Per row: `val isSelected by remember(song.id) { derivedStateOf { selection.isSelected(song.id) } }`.
     - Then `SongListItem(..., selected = isSelected, selectionModeActive = selection.isActive,
       onClick = { if (selection.isActive) selection.toggle(song.id) else onSongClick(song) },
       onLongClick = { selection.toggle(song.id) })`.
  5. **Sheets and dialogs.** Keep `SongOptionsSheet`, `AddToPlaylistSheetHost`, `PlaylistNameDialog` and their local
     `remember` state, with no behaviour change. Any `TextButton` in this file gets `accentText` content colors.
  6. **Previews.** Retarget them to `TracksTabContent`. If a composable exceeds 30 lines, split it into private
     sub-composables rather than suppressing LongMethod.

- [ ] **Step 6: Retarget the smoke test.** Rename it to `TracksTabSmokeTest`. Host `TracksTabContent` with
  `rememberSongSelectionState()`. Keep its three tests and their assertions. Then
  `./gradlew :feature:library:assembleDebugAndroidTest` must compile. The test runs on device in Task 9.

- [ ] **Step 7: GREEN, then each mutation.** Run `./gradlew :feature:library:testDebugUnitTest`.
- [ ] **Step 8: Checkpoint (no commit).** Run spotlessApply, then `:feature:library:detekt`. `:app` won't compile until
  Task 7/8 (it still calls `LibraryRoute`). Confirm that is the only breakage, then run `git status --short`.

---

### Task 6: `PlaylistsTab` (`:feature:playlists`)

**Owner:** ui-builder (general-purpose, `model: "opus"`)

**Files:**
- `feature/playlists/.../PlaylistsScreen.kt` is replaced by `PlaylistsTab.kt`.
- Modify:
  - `PlaylistsViewModel.kt`: card order and labels.
  - `PlaylistDetailViewModel.kt:109-113`: the label becomes "Favourite tracks".
  - `PlaylistsViewModelTest.kt`, and `PlaylistDetailViewModelTest.kt` if it asserts the label.
- Test (create): `feature/playlists/src/test/.../PlaylistsTabTest.kt` (Robolectric).

**Interfaces:**
- Consumes:
  - `FeatureCard` and `PlaylistRow` (Task 4)
  - `SortHeader` and `LocalMiniPlayerInset` (Phase 1)
  - `PlaylistNameDialog` (`:core:ui`)
- Produces:

```kotlin
@Composable fun PlaylistsTab(
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
)

@Composable internal fun PlaylistsTabContent(
    smartPlaylists: List<SmartPlaylistSummary>,
    userPlaylists: List<Playlist>,
    showCreateDialog: Boolean,
    onDismissCreateDialog: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    modifier: Modifier = Modifier,
)
```

- `smartPlaylists` order (spec §8.2):
  1. `RECENTLY_ADDED`, "Recently added"
  2. `MOST_PLAYED`, "Most played"
  3. `RECENTLY_PLAYED`, "Recently played"
  4. `FAVOURITES`, "Favourite tracks"

- [ ] **Step 1: Update the ViewModel tests first (RED).**
  - Expected kinds: `RECENTLY_ADDED, MOST_PLAYED, RECENTLY_PLAYED, FAVOURITES`.
  - Expected labels: `listOf("Recently added", "Most played", "Recently played", "Favourite tracks")`.
  - Update the assertion messages to match.
  - If `PlaylistDetailViewModelTest` asserts the "Favourites" title, it becomes "Favourite tracks".

- [ ] **Step 2: Write `PlaylistsTabTest`.** Robolectric, `w360dp-h640dp`, dark theme, hosting `PlaylistsTabContent`.
  1. `featureCardsShowInSpecOrderWithTrackCounts`
     - Give the four summaries counts 1, 2, 3 and 4.
     - The texts "Recently added", "Most played", "Recently played" and "Favourite tracks" have increasing left edges.
     - "4 tracks" exists.
     - Mutation: label counts "songs". RED.
  2. `tappingAFeatureCardReportsItsKind`
     - Clicking "Most played" calls `onSmartPlaylistClick(MOST_PLAYED)`.
     - Mutation: pass a fixed kind. RED.
  3. `emptyUserListShowsTheCreateHint`
     - With no user playlists, "No playlists yet. Tap + to create one." is displayed.
     - Mutation: drop the empty item. RED.
  4. `createDialogFollowsTheHoistedFlag`
     - With `showCreateDialog = true`, "New playlist" is displayed.
     - Typing "Road" and clicking "Create" calls `onCreatePlaylist("Road")`, then `onDismissCreateDialog()`.
     - With `false`, there is no dialog.
     - Mutation: ignore the flag. RED.
  5. `userPlaylistTapReportsItsId`
     - Clicking `PlaylistRow` "Light" (id 7) calls `onPlaylistClick(7)`.
     - Mutation: pass `id + 1`. RED.

- [ ] **Step 3: Implement.**
  - **ViewModel.** Reorder `smartPlaylists`' `listOf(...)` to the spec order and rename "Favourites" to "Favourite
    tracks". Use the same label in `PlaylistDetailViewModel`.
  - **`PlaylistsTab.kt`, built from `PlaylistsScreen.kt`:**
    1. **Wrapper.** `PlaylistsRoute` becomes `PlaylistsTab(...)`.
       - Delete `onSearchClick` and `onSettingsClick`; Home's header owns them.
       - Set `onCreatePlaylist = remember(viewModel) { viewModel::createPlaylist }`.
    2. **Content.** `PlaylistsScreen` becomes `PlaylistsTabContent`. Delete:
       - the inner `Scaffold`, `PlaylistsTopBar` and `PlaylistsOverflowMenu`;
       - the local `showCreateDialog`, which is replaced by the hoisted parameters;
       - the `Surface` with its margins, and the private dp constants (:64-75).
    3. **Layout.** `SortHeader(label = "Name")` with no trailing content, then a `LazyColumn` with
       `contentPadding = PaddingValues(bottom = LocalMiniPlayerInset.current + navBarBottom)`. It contains:
       - `item("feature-cards")`: a `LazyRow` with
         `contentPadding = PaddingValues(horizontal = RollaDimens.featureCardRowStart)` and
         `horizontalArrangement = spacedBy(RollaDimens.featureCardGap)`. It shows
         `items(smartPlaylists, key = { it.kind })`, each as
         `FeatureCard(label, "${count} tracks", previewArtworkUris, onClick = { onSmartPlaylistClick(kind) })`.
       - Then the empty state, or `items(userPlaylists, key = { it.id }) { PlaylistRow(...) }`.
    4. **Dialog.** When `showCreateDialog` is true, show
       `PlaylistNameDialog(title = "New playlist", confirmLabel = "Create",
       onConfirm = { onCreatePlaylist(it); onDismissCreateDialog() }, onDismiss = onDismissCreateDialog)`.
       Check the real parameter names. If its TextButtons don't use `accentText`, fix that in
       `:core:ui/PlaylistNameDialog.kt` (cross-phase rule).
    5. **Cleanup.** Delete `SmartPlaylistCard`, `SmartPlaylistArtwork` and `SmartPlaylistCollage`; `FeatureCard`
       replaces them. Keep the preview and retarget it.

- [ ] **Step 4: GREEN, then each mutation.** Run `./gradlew :feature:playlists:testDebugUnitTest`.
- [ ] **Step 5: Checkpoint (no commit).** Run spotlessApply, then `:feature:playlists:detekt`, then
  `git status --short`.

---

### Task 7: Home shell (`:app/home/`): `HomeTab`, `HomeViewModel`, `HomeScreen`, and the route swap

**Owner:** ui-builder with viewmodel-architect (general-purpose, `model: "opus"`)

**Files:**
- Create:
  - `app/src/main/kotlin/com/rolla/musicplayer/home/HomeTab.kt`
  - `app/src/main/kotlin/com/rolla/musicplayer/home/HomeViewModel.kt`
  - `app/src/main/kotlin/com/rolla/musicplayer/home/HomeScreen.kt`
- Modify:
  - `app/src/main/kotlin/com/rolla/musicplayer/navigation/Routes.kt`: add `Home`; delete `Library` and `Playlists`.
  - `MainActivity.kt`'s `AppNavGraph`: start at `Home`, and replace the Library and Playlists entries with one
    `composable<Home>`.
  - `app/proguard-rules.pro:27-29`: the route-list comment.
- Test (create):
  - `app/src/test/kotlin/com/rolla/musicplayer/home/HomeViewModelTest.kt`: the moved sync tests plus one new test.
  - `app/src/test/kotlin/com/rolla/musicplayer/home/HomeScreenTest.kt` and `HomeScreenRestorationTest.kt`
    (Robolectric).

**Interfaces:**
- Consumes:
  - From Phase 1: `OneUiTopBar`, `OneUiDetailTopBar(navigationIcon, navigationContentDescription)`,
    `OneUiIconButton`, `OneUiTabRow`, `ContentPanel`, `RollaIcons`, `rememberReducedMotion` and
    `RollaDimens.panelTopGap`.
  - `SongSelectionState` (Task 3), `TracksTab` (Task 5) and `PlaylistsTab` (Task 6).
  - `ScanState`, `LibraryIndexer` and `MediaPermissionGate`.
- Produces:

```kotlin
enum class HomeTab(val title: String) { PLAYLISTS("Playlists"), TRACKS("Tracks") }

@HiltViewModel class HomeViewModel @Inject constructor(private val libraryIndexer: LibraryIndexer) : ViewModel() {
    val scanState: StateFlow<ScanState>
    fun onPermissionGranted()
}

@Composable fun HomeRoute(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditTagsClick: (Song) -> Unit,
    onEditTagsForSelection: (List<Long>) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onSmartPlaylistClick: (SmartPlaylistKind) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
)

@Composable internal fun HomeScreen(
    pagerState: PagerState,
    selection: SongSelectionState,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onEditTagsForSelection: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (HomeTab) -> Unit,
)

@Serializable data object Home : Route
```

- [ ] **Step 1: Move the sync tests.** Move `onPermissionGranted_transitionsToScanningThenDone`, `…_ignoredWhileScanning`
  and `…_ignoredWhenDone` into `HomeViewModelTest`. Keep the bodies verbatim from the Task 5 scratch copy, but
  construct `HomeViewModel(libraryIndexer)`. Then add:

```kotlin
@Test
fun `onPermissionGranted_syncFailure_setsError`() = runTest {
    coEvery { libraryIndexer.sync() } throws IllegalStateException("boom")
    viewModel.onPermissionGranted()
    advanceUntilIdle()
    assertEquals(ScanState.Error("boom"), viewModel.scanState.value)
}
```

Mutations:
- Dropping the `Done` guard turns `ignoredWhenDone` RED.
- Swallowing the error and setting `Done` turns `syncFailure` RED.

- [ ] **Step 2: Write `HomeScreenTest`.**
  - Robolectric with `w360dp-h640dp`, dark theme, and `createAndroidComposeRule<ComponentActivity>()`.
  - Host `HomeScreen` with:
    - `pagerState = rememberPagerState(initialPage = HomeTab.TRACKS.ordinal) { HomeTab.entries.size }`;
    - `selection = rememberSongSelectionState()`;
    - `pageContent` rendering `Text("page-${tab.name}")`, plus `Button(onClick = { selection.toggle("x") }) { Text("select") }`
      on TRACKS.
  - Assign the pager state and the selection to `lateinit var`s inside `setContent`.

  1. `startsOnTracksAndTitleIsRollaMusic`
     - "page-TRACKS" is displayed. "Rolla Music" is displayed and is a heading. Tab "Tracks" is selected.
     - Mutation: initial page 0. RED.
  2. `addButtonOnlyOnPlaylistsAndOpensCreate`
     - On Tracks, there is no "Create playlist" node.
     - Click tab "Playlists" and wait for idle: "page-PLAYLISTS" is shown.
     - "Create playlist" is displayed, and clicking it calls `onCreatePlaylistClick` once.
     - Mutation: always show +. RED.
  3. `selectionTurnsHeaderIntoSelectionBarAndBackClears` (Review Focus 3)
     - Click "select": "1 selected" and contentDescription "Close selection" are displayed, and "Rolla Music" is not.
     - `composeRule.activity.onBackPressedDispatcher.onBackPressed()` inside `runOnUiThread` sets
       `selection.isActive` to false, and "Rolla Music" is back.
     - Mutation: drop the `BackHandler`. RED.
  4. `editTagsInSelectionBarInvokesCallback`
     - Select, then click "Edit tags": `onEditTagsForSelection` is called once.
     - Mutation: wire `{}`. RED.
  5. `swipeIsDisabledWhileSelecting` (Review Focus 4)
     - Select, then `performTouchInput { swipeRight() }` on the "page-TRACKS" node's parent pager. Use
       `onNodeWithText("page-TRACKS").performTouchInput`; the gesture reaches the pager.
     - Wait for idle: `pagerState.currentPage == TRACKS.ordinal`.
     - Clear the selection and swipe right again: the page becomes `PLAYLISTS.ordinal`.
     - Mutation: `userScrollEnabled = true`. RED.
  6. `overflowOpensSettings`
     - Click "More options", then "Settings": `onSettingsClick` is called once.
     - Mutation: wire `{}`. RED.

  Then `HomeScreenRestorationTest` (`createComposeRule()` with `StateRestorationTester`), covering Review Focus 1:

  7. `selectedTabSurvivesRecreation`
     - Same host. Click tab "Playlists" and wait for idle, then call `emulateSavedInstanceStateRestore()`.
     - The restored pager's `currentPage` is `PLAYLISTS.ordinal`.
     - Mutation: hoist a plain `remember { PagerState… }` in the host. RED.
     - The host lives in the test, so this test pins `HomeRoute`'s use of `rememberPagerState`. Mirror `HomeRoute`'s
       exact construction in the test host and say so in a comment.

- [ ] **Step 3: Confirm RED.**

- [ ] **Step 4: Implement `HomeTab.kt` and `HomeViewModel.kt`.**

```kotlin
package com.rolla.musicplayer.home

/** Home pager pages in spec order (§7.2). Only tabs that exist are listed (Phase 2 ruling: no dead tabs). */
enum class HomeTab(val title: String) {
    PLAYLISTS("Playlists"),
    TRACKS("Tracks"),
}
```

```kotlin
package com.rolla.musicplayer.home

// Moved verbatim from LibraryViewModel.onPermissionGranted (Phase 2 ruling 2): Home's MediaPermissionGate owns the
// first-grant library sync for every tab.
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryIndexer: LibraryIndexer,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    fun onPermissionGranted() {
        val current = _scanState.value
        if (current is ScanState.Scanning || current is ScanState.Done) return
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            _scanState.value = try {
                val result = libraryIndexer.sync()
                ScanState.Done(added = result.added, removed = result.removed)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                ScanState.Error(e.message ?: "Scan failed")
            }
        }
    }
}
```

- [ ] **Step 5: Implement `HomeScreen.kt`.** Split it into private composables so each stays at 30 lines or fewer.

  **Root.** `Column(Modifier.fillMaxSize().background(colorScheme.background))`, with:
  1. **Header, while a selection is active.** It shows the selection bar:

     ```
     OneUiDetailTopBar(title = "${selection.count} selected", onNavigateUp = selection::clear,
         navigationIcon = RollaIcons.Close, navigationContentDescription = "Close selection") {
         TextButton(onClick = onEditTagsForSelection,
             colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.accentText)) { Text("Edit tags") }
     }
     ```

  2. **Header, otherwise.** `OneUiTopBar(title = "Rolla Music")`, with these actions:
     - `AnimatedVisibility(visible = pagerState.settledPage == HomeTab.PLAYLISTS.ordinal, …)` wrapping
       `OneUiIconButton(RollaIcons.Add, "Create playlist", onClick = onCreatePlaylistClick)`. Fade it in and out with
       `fadeIn()`/`fadeOut()`, or `snap()` under reduced motion.
     - `OneUiIconButton(RollaIcons.Search, "Search", onClick = onSearchClick)`.
     - An overflow `Box`: `OneUiIconButton(RollaIcons.More, "More options") { expanded = true }`, plus a
       `DropdownMenu` with one item, "Settings", that calls `onSettingsClick` and closes the menu.
  3. **Tab row.**

     ```
     OneUiTabRow(titles = HomeTab.entries.map { it.title }, pagerState = pagerState,
         onTabClick = { i -> if (!selection.isActive) scope.launch {
             if (reducedMotion) pagerState.scrollToPage(i) else pagerState.animateScrollToPage(i) } })
     ```

  4. `Spacer(Modifier.height(RollaDimens.panelTopGap))`.
  5. **Pager.**

     ```
     HorizontalPager(state = pagerState, userScrollEnabled = !selection.isActive,
         key = { HomeTab.entries[it].name }, modifier = Modifier.weight(1f)) { page ->
         ContentPanel { pageContent(HomeTab.entries[page]) }
     }
     ```

  **Back.** Add `BackHandler(enabled = selection.isActive) { selection.clear() }`.

  **Insets.** The header owns the status bar, through the bars' default `WindowInsets.statusBars`. The pager doesn't pad
  for the nav bar; the lists do (ruling 4).

  **`HomeRoute` wiring:**
  - `val scanState by viewModel.scanState.collectAsStateWithLifecycle()`.
  - `val pagerState = rememberPagerState(initialPage = HomeTab.TRACKS.ordinal) { HomeTab.entries.size }`.
  - `val selection = rememberSongSelectionState()`.
  - `var showCreateDialog by rememberSaveable { mutableStateOf(false) }`.
  - The selection-bar callback maps `selection.selectedIds.mapNotNull { it.toLongOrNull() }`. If the result is
    non-empty it calls `onEditTagsForSelection(ids)`, then `selection.clear()`.
  - Page content:

    ```
    when (tab) {
        HomeTab.TRACKS -> TracksTab(selection, scanState, onEditTagsClick)
        HomeTab.PLAYLISTS -> PlaylistsTab(showCreateDialog, { showCreateDialog = false },
            onPlaylistClick, onSmartPlaylistClick)
    }
    ```

  - **Permission gate.** It wraps the pager area, with `onGranted = viewModel::onPermissionGranted`.
    - Read `MediaPermissionGate`. If it renders its own full-screen rationale when denied, keep it around the pager only.
      Add an optional `pagerContainer: @Composable (@Composable () -> Unit) -> Unit = { it() }` slot to `HomeScreen`,
      which `HomeRoute` fills with the gate.
    - If the gate is fine wrapping everything, wrap `HomeScreen` entirely instead.
    - Either way, record the choice.

- [ ] **Step 6: Route swap.**
  - **Routes.** In `Routes.kt`, delete `Library` and `Playlists`, add `Home`, and change the `Settings` KDoc to
    "Reachable from the Home header overflow menu".
  - **Nav graph.** In `AppNavGraph`, set `startDestination = Home` and replace the two entries with:

    ```
    composable<Home> {
        HomeRoute(
            onSearchClick = { navController.navigate(Search()) { launchSingleTop = true } },
            onSettingsClick = { navController.navigate(Settings) { launchSingleTop = true } },
            onEditTagsClick = { song -> song.id.toLongOrNull()?.let { id ->
                navController.navigate(TagEditor(id)) { launchSingleTop = true } } },
            onEditTagsForSelection = { ids -> navController.navigate(BatchTagEditor(ids)) { launchSingleTop = true } },
            onPlaylistClick = { id -> navController.navigate(PlaylistDetail(playlistId = id)) },
            onSmartPlaylistClick = { kind -> navController.navigate(SmartPlaylist(kind = kind.name)) },
        )
    }
    ```

  - **Bottom nav (temporary).** Until Task 8 deletes it, `RollaBottomNavigationBar` doesn't compile without
    `Library`/`Playlists`. Delete it and its `isTopLevelDestination` use here. Task 8 then owns the overlay.
  - **Comments.** Update every comment that mentions Library or Playlists entry points (:281-283, :289-292, :347-348,
    :391-394, :424-431, :435-438, :464-465) and the `proguard-rules.pro` route list.

- [ ] **Step 7: GREEN, then each mutation.** `./gradlew :app:testDebugUnitTest` must pass all tests:
  `MainViewModelTest`, `HomeViewModelTest`, `HomeScreenTest` and `HomeScreenRestorationTest`.
- [ ] **Step 8: Checkpoint (no commit).** Run spotlessApply, then `:app:detekt` and `./gradlew assembleDebug`, then
  `git status --short`.

---

### Task 8: Mini-player overlay and `LocalMiniPlayerInset`

**Owner:** navigation-agent (general-purpose, `model: "opus"`)

**Files:**
- Modify:
  - `app/src/main/kotlin/com/rolla/musicplayer/MainActivity.kt` (`RollaNavHost` and `AppNavGraph`)
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/MiniPlayer.kt:112`: remove
    `.navigationBarsPadding()`, because the host now owns that inset.
- Create: `app/src/main/kotlin/com/rolla/musicplayer/navigation/MiniPlayerVisibility.kt`
- Test (create): `app/src/test/kotlin/com/rolla/musicplayer/navigation/MiniPlayerVisibilityTest.kt`

**Interfaces:**
- Produces:
  - `internal val allRoutes: List<KClass<out Route>>`
  - `internal fun showsMiniPlayer(routeClass: KClass<out Route>?): Boolean`
  - `internal fun NavDestination?.routeClass(): KClass<out Route>?`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.rolla.musicplayer.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerVisibilityTest {

    @Test
    fun shownOnBrowsingScreens() {
        listOf(Home::class, Search::class, AlbumDetail::class, ArtistDetail::class, PlaylistDetail::class,
            SmartPlaylist::class).forEach { assertTrue(it.simpleName, showsMiniPlayer(it)) }
    }

    @Test
    fun hiddenOnPlayerSettingsAndEditorScreens() { // spec §7.3
        listOf(NowPlaying::class, Equalizer::class, Settings::class, About::class, Licenses::class, Privacy::class,
            TagEditor::class, BatchTagEditor::class).forEach { assertFalse(it.simpleName, showsMiniPlayer(it)) }
    }

    @Test
    fun hiddenWhenThereIsNoDestination() {
        assertFalse(showsMiniPlayer(null))
    }

    @Test
    fun everyRouteIsClassified() {
        // A new route must be added to allRoutes (and decided for the mini-player) before this passes.
        assertEquals(14, allRoutes.size)
    }
}
```

Count the real routes after Task 7's swap and set the number (14 is the expected count: 15 old − 2 + 1). Mutations:
- Dropping `Settings` from the hidden set turns `hiddenOnPlayerSettingsAndEditorScreens` RED.
- Returning true on null turns `hiddenWhenThereIsNoDestination` RED.
- Removing one entry from `allRoutes` turns `everyRouteIsClassified` RED.

- [ ] **Step 2: Confirm RED.**

- [ ] **Step 3: Implement `MiniPlayerVisibility.kt`**

```kotlin
package com.rolla.musicplayer.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/** Every route in the graph. Keep in sync with Routes.kt (MiniPlayerVisibilityTest counts it). */
internal val allRoutes: List<KClass<out Route>> = listOf(
    Home::class, NowPlaying::class, Equalizer::class, PlaylistDetail::class, SmartPlaylist::class,
    AlbumDetail::class, ArtistDetail::class, TagEditor::class, BatchTagEditor::class, Search::class,
    Settings::class, About::class, Licenses::class, Privacy::class,
)

/** Spec §7.3: the pill is hidden on the player itself and on settings, editor and info screens. */
private val routesWithoutMiniPlayer: Set<KClass<out Route>> = setOf(
    NowPlaying::class, Equalizer::class, Settings::class, About::class, Licenses::class, Privacy::class,
    TagEditor::class, BatchTagEditor::class,
)

internal fun showsMiniPlayer(routeClass: KClass<out Route>?): Boolean =
    routeClass != null && routeClass !in routesWithoutMiniPlayer

internal fun NavDestination?.routeClass(): KClass<out Route>? =
    this?.let { destination -> allRoutes.firstOrNull { destination.hasRoute(it) } }
```

- [ ] **Step 4: Overlay.** Rewrite `RollaNavHost`. Keep `MiniPlayerViewModel` and the `hasSong` collection as they are.

```
SharedTransitionLayout {
    val density = LocalDensity.current
    var pillHeight by remember { mutableStateOf(0.dp) }
    val showPill = hasSong && showsMiniPlayer(currentDestination.routeClass())
    CompositionLocalProvider(LocalMiniPlayerInset provides if (showPill) pillHeight else 0.dp) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .semantics { testTagsAsResourceId = true }) {
            AppNavGraph(navController, this@SharedTransitionLayout, Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
            AnimatedVisibility(visible = showPill, enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                MiniPlayerRoute(sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this, viewModel = miniPlayerViewModel,
                    modifier = Modifier.onSizeChanged { pillHeight = with(density) { it.height.toDp() } },
                    onBodyClick = { navController.navigate(NowPlaying) { launchSingleTop = true } })
            }
        }
    }
}
```

- Delete the old `Scaffold`, its `innerPadding` plumbing and any remaining bottom-nav imports.
- Delete `.navigationBarsPadding()` from `MiniPlayer.kt:112`.

- [ ] **Step 5: Pushed screens (ruling 4).**
  - In `AppNavGraph`, wrap every destination except `Home` and `NowPlaying` in
    `@Composable private fun PushedScreen(content: @Composable () -> Unit)`. It applies
    `Modifier.fillMaxSize().padding(bottom = navBarBottom + LocalMiniPlayerInset.current)`, with
    `navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`.
  - `NowPlaying` already owns `contentWindowInsets = WindowInsets.navigationBars`, so leave it unwrapped.
  - For every other screen, check whether it already pads the nav-bar inset itself (for example Search's
    `ScaffoldDefaults.contentWindowInsets.exclude(ime)`). If it does, don't double-pad: give `PushedScreen` a
    `padNavigationBar: Boolean = true` parameter. Record the decision for each screen in the report.

- [ ] **Step 6: GREEN, then each mutation.** Run `./gradlew :app:testDebugUnitTest`.
- [ ] **Step 7: Checkpoint (no commit).**
  - `./gradlew spotlessApply :app:detekt :feature:player:detekt`
  - `./gradlew assembleDebug :feature:player:assembleDebugAndroidTest :feature:library:assembleDebugAndroidTest`.
    `MiniPlayerSmokeTest` and `TracksTabSmokeTest` must compile.
  - `git status --short`

---

### Task 9: Phase gate and on-device check

**Owner:** orchestrator, with test-writer for any instrumented fixes (general-purpose, `model: "opus"`)

- [ ] **Step 1: Full gate.**
  - Run `./gradlew spotlessApply`.
  - In a separate invocation, run `./gradlew check assembleDebug --continue` in the background, logging to a file.
  - Expect BUILD SUCCESSFUL. Existing suites get updated, never deleted.
  - Before running, check free disk space. Gradle outputs filled the disk once already (2026-10-07).

- [ ] **Step 2: Hardcoded-value grep.**

```
grep -rnE "Color\(0x|[0-9]+\.dp|[0-9]+\.sp" app/src/main/kotlin/com/rolla/musicplayer/home \
  feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/TracksTab.kt \
  feature/playlists/src/main/kotlin/com/rolla/musicplayer/feature/playlists/PlaylistsTab.kt \
  core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/SongListItem.kt \
  core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/PlaylistRow.kt \
  core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/FeatureCard.kt
```

Expected hits: only `0.dp` defaults and preview scaffolding.

- [ ] **Step 3: Device check over adb** (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`; confirm with
  `adb devices`).
  1. Install the debug build and launch it. It should open on Home > Tracks.
  2. Capture `adb exec-out screencap -p` for:
     - Home Tracks
     - Home Playlists, with the + visible
     - selection mode
     - the mini-player over the list, with the last row scrolled up
     - Search and Settings, checking the bottom insets and that nothing is padded twice
  3. Store each capture beside its matching reference from `media player example/` in `docs/design/calibration/phase2/`.
     This is a sanity check, not calibration (that is Phase 7).
  4. Check by hand:
     - swiping and tapping tabs
     - long-press selection, then Edit tags, which should open BatchTagEditor
     - Back clears the selection
     - Shuffle and Play play real queues
     - tapping the mini-player body opens Now Playing, and the shared-element artwork transition still animates
     - after rotating on Playlists, the tab is kept
  5. Run `./gradlew :feature:library:connectedDebugAndroidTest :feature:player:connectedDebugAndroidTest`.

  If no device is connected, record "device check deferred" and ask the user to connect the phone before Phase 2 is
  reported as accepted. JVM evidence alone cannot accept the shared-element check (spec §17 exit criteria).

- [ ] **Step 4: Phase review.**
  - The orchestrator dispatches `code-reviewer` (`model: "opus"`) over the whole-phase diff. Inputs: spec §7, §8.1,
    §8.2 and §12, this plan, and the ledger.
  - The review also checks this phase's Produces APIs against Phase 3's spec consumers (§8.3 Mini-player, §8.4 Now
    Playing, §11 Queue). This is observer 0006's next-phase consumer check.
  - Acceptance requires all of:
    - no Critical or High findings
    - green gates
    - the device check done, or explicitly deferred by the user
    - each Produces block matching the landed code

- [ ] **Step 5: Ready-to-commit report (no commit).** Report the files changed, test counts, the gate result, device
  evidence and the open user decision (the tab ripple), with this prepared message:

```
feat(home): One UI Home shell with Tracks and Playlists tabs

Home is the start destination: header, center-weighted tab row and pager of edge-to-edge panels. The M3 bottom
navigation is gone; the mini-player floats over content and provides LocalMiniPlayerInset. Tracks: restyled rows,
working Shuffle/Play, Home-level multi-select. Playlists: feature cards and restyled rows, + in the header.
Phase 2 of docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
