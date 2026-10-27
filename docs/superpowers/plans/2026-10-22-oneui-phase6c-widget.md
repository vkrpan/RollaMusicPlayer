# One UI redesign, Phase 6c: the home-screen widget — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Glance widget takes the One UI look of `media player example/Screenshot_Widget.jpg`: a pure-black 24 dp card, a 69 dp rounded artwork box, a 16 sp bold title over a 13 sp grey artist, a row of glyphs that mirror `RollaIcons`, and a 3 dp blue progress bar. The ±15 s controls and all of the widget's wiring stay as they are.

**Architecture:**
- **Look only.** `MusicWidget`, the five action callbacks, `WidgetStateProvider`, the update hook and `WidgetArtworkLoader` are unchanged (spec §13 "Unchanged"). The work is in `MusicWidgetContent.kt`, `MusicWidgetTheme.kt`, the widget resources and a new pure layout file.
- **Tokens.** Widget sizes become `RollaDimens.widget*` tokens in `:core:designsystem`, which `:feature:widget` now depends on. Colours stay widget-local, because Glance cannot read Compose colour tokens (spec §5.1, `widget*` row). A test keeps `colors.xml` and the Kotlin palette in step.
- **Layout math is pure.** `widgetLayoutFor(size, fontScale)` decides whether the artwork fits next to five 48 dp control cells, and how much vertical padding centres the content. JVM tests cover it; Glance unit tests cover the rendered tree.

**Tech Stack:** Kotlin 2.2.21, Glance 1.1.0 (`glance-appwidget`, `glance-material3`), new test-only `glance-appwidget-testing` 1.1.0, Robolectric 4.13 (convention plugin `rolla.android.robolectric`), Compose BOM 2024.09.03 for the icon parity test.

**Spec:** `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md` §13 (widget), §5.1 (colours, the `widget*` exemption), §5.4 (dimensions), §5.5 (icons), §14 (calibration tolerances). Roadmap: `docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md`, row 6c.

## Global Constraints

- Spec §2: the widget keeps prev · −15 s · play/pause · +15 s · next and "adopts the example's visual style only". The app name in copy is "Rolla Music", never "RollaMusicPlayer" or any Samsung branding.
- Spec §13 values, verbatim: card `#000000` in both day and night providers; corner radius 24 dp; artwork box 69 dp with a 12 dp radius; placeholder `#363636` with a white note; title 16 sp Bold `#FCFCFE`, one line; artist 13 sp `#B0B0B2`, one line; progress bar 3 dp, `#2F6FF0` fill. The track colour is ruling 1's `#646466`, not §13's `#333333`.
- Day == night: the widget stays dark whatever the system theme.
- Every control's touch box is at least 48 × 48 dp (spec §5.4: only the EQ columns and the A–Z rail are exceptions).
- No Material icons. Glyphs are XML vector drawables in `:feature:widget` that mirror `RollaIcons` geometry.
- No hardcoded dimension in widget Kotlin: sizes come from `RollaDimens`. Colours come from the widget palette (ruling 4).
- Offline: no new runtime dependency. The one new artifact is `testImplementation` only.
- Agents never commit, stash, reset, clean or check out. Commits happen only when the user asks.
- Kotlin files are edited with Edit/Write, never `sed -i` and never shell heredocs (Spotless enforces CRLF). New files: create a placeholder, then fill it with Edit.
- Every test carries its own control (an assertion that fails under the trivial stub) and names the mutation that turns it RED. Implementers record each mutation run with the `BUILD FAILED` line and the test's own failure message, clear stale test XML first, and revert with `cmp`. A prescribed mutation that turns out equivalent is proved so and replaced by the closest one that can fail.
- Gate: `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug`.

## Planning-time device probe (2026-10-22, read-only, Motorola edge 70 ZY22MBNVB4)

| Fact | Value | Source |
|---|---|---|
| API level | 36 | `getprop ro.build.version.sdk` |
| Density / font scale | 480 (3.0 px/dp) / 1.0 | `wm density`, `settings get system font_scale` |
| Screen | 1220 × 2712 px = 406.7 × 904 dp | `wm size` |
| Home app | `com.motorola.launcher3` | `cmd package resolve-activity … HOME` |
| System widget corner radius | 24.0 dp | `cmd overlay lookup android android:dimen/system_app_widget_background_radius` |
| Our provider | registered (`MusicWidgetReceiver`, `resizeMode=3`, `updatePeriodMillis=0`) | `dumpsys appwidget` |
| Our widget on a home screen | **none placed** | `dumpsys appwidget`, Widgets section |
| App install | `firstInstallTime=2026-10-21 15:42:36` | `dumpsys package` |

The radius matches spec §13, so a 24 dp `cornerRadius` agrees with the launcher's own clip. Cell sizes are still unknown, because nothing is placed: Task 0 measures them.

## Reference geometry (from `Screenshot_Widget.jpg`, 1080 px wide, 3.0 px/dp)

Coordinates are original-image pixels (the displayed image × 1.17). These feed the tokens in ruling 4.

| Element | px | dp |
|---|---|---|
| Card | x 54–1030, y 190–486 | 325 × 99 |
| Art | x 84–289, y 234–441 | 69 square; start inset 30 px = 10 dp; centred vertically (44 / 45 px) |
| Gap art → title | 289 → 321 | 10.7 → **10** |
| Five control cells | first centre 386, pitch ≈ 136 | cells 45.3 dp, starting at the title's x; end inset 32 px = 10.7 → **10** |
| Play glyph | 54 px tall | 18 dp |
| Skip glyph | ≈ 47 px tall | 15.6 dp |
| Note glyph in the art | ≈ 85 px tall | 28 dp |

## Rulings

1. **Progress track `#646466`, not `#333333`** (deviation from §13, written into the spec by Task 4). Computed contrast on the `#000000` card:
   - fill `#2F6FF0`: L = 0.1827, **4.65:1** (≥ 3:1);
   - spec track `#333333`: L = 0.0331, **1.66:1**, under the 3:1 non-text floor that §5.1 applied to `sliderInactiveTrack` and `seekTrackInactive`, and that the 2026-07-15 widget audit applied to this very track (`#3A3A3C` → `#6E6E73`, "an early-in-track bar read as missing");
   - `#646466` (the app's dark `sliderInactiveTrack`): L = 0.1279, **3.56:1**.

   Fill against track is 1.31:1 with `#646466`, the same pairing the app's sliders already accept (`primary` on `sliderInactiveTrack`). Cost if wrong: the track is lighter than §13 asks; the reference has no bar to compare against.
2. **Touch boxes and the artwork threshold.** The five controls sit in equal-weight cells across the text column, and each cell is one clickable box 48 dp tall and as wide as the cell. A cell must be ≥ 48 dp wide, so:
   - with artwork the card must be ≥ `widgetCardPaddingStart + widgetArt + widgetArtGap + 5 × minTouchTarget + widgetCardPaddingEnd` = 10 + 69 + 10 + 240 + 10 = **339 dp**;
   - below 339 dp the artwork box is not drawn and the column takes the full width, which needs 10 + 240 + 10 = **260 dp**;
   - `music_widget_info.xml` sets `minWidth` and `minResizeWidth` to 260 dp (was 250 and 180).

   At the reference's own 325 dp card the cells would be 45.3 dp, so the reference look and the 48 dp rule cannot both hold at that width: the rule wins (spec §5.4 lists only two exceptions). Cost if wrong: on a launcher whose default width is under 339 dp the artwork hides; Task 0 records the Motorola's default width.
3. **Controls are never clipped; text gives way.** The text column is `Column(text block with defaultWeight(), controls row, progress bar)`, so the controls and the bar are measured first and the text block takes what is left. Vertical padding centres the content: `padding = max(0, (cardHeight − contentNominal) / 2)`, where `contentNominal = max(widgetArt, textNominal + minTouchTarget + widgetProgressHeight)` and `textNominal = (widgetTitleSize + widgetArtistSize) × 1.33 × fontScale` in dp. Values: fontScale 1.0 → 89.57 dp nominal, 10.21 dp padding in a 110 dp card; fontScale 2.0 → 128.14 dp nominal, padding 0. The 1.33 factor is the platform's default line height for these sizes, an estimate the device check confirms at fontScale 1.0. Cost if wrong: at large font sizes the title or artist is cut, never a control.
4. **Tokens.** Sizes become `RollaDimens` tokens; colours stay in the widget (spec §5.1 exempts `widget*`). New `RollaDimens` entries, grouped under a `// Widget (spec §13)` comment:

   | Token | Value | Source |
   |---|---|---|
   | `widgetCardRadius` | 24.dp | §13; matches the device's system radius |
   | `widgetCardPaddingStart` | 10.dp | reference art inset |
   | `widgetCardPaddingEnd` | 10.dp | reference control end inset |
   | `widgetArt` | 69.dp | §13 |
   | `widgetArtRadius` | 12.dp | §13 |
   | `widgetArtGap` | 10.dp | reference |
   | `widgetArtGlyph` | 44.dp | `MusicNote` spans 15.2 of 24 viewport units, so a 28 dp tall note needs a 44 dp box |
   | `widgetControlGlyph` | 28.dp | `Play` spans y 4.4–19.6 (15.2/24); 28 × 15.2/24 = 17.7 dp ≈ the measured 18 dp. Skip glyphs then come out 28 × 13/24 = 15.2 dp against the measured 15.6 |
   | `widgetProgressHeight` | 3.dp | §13 |
   | `widgetTitleSize` | 16.sp | §13 |
   | `widgetArtistSize` | 13.sp | §13 |

   The control cell height reuses `minTouchTarget`. The old private `WidgetDimens` object is deleted.

   Widget palette (`res/values/colors.xml` and `MusicWidgetTheme.kt`, kept equal by `WidgetPaletteTest`):

   | Colour resource | Kotlin constant | Value | Glance role |
   |---|---|---|---|
   | `widget_card` | `WidgetCard` | `#FF000000` | `surface`, `background`, `surfaceContainer*`, `inverseOnSurface` |
   | `widget_title` | `WidgetTitle` | `#FFFCFCFE` | `onSurface`, `onBackground`, `onPrimary*`, `onError`, `inverseSurface` |
   | `widget_artist` | `WidgetArtist` | `#FFB0B0B2` | `onSurfaceVariant` |
   | `widget_accent` | `WidgetAccent` | `#FF2F6FF0` | `primary`, `secondary`, `tertiary`, `inversePrimary` |
   | `widget_track` | `WidgetTrack` | `#FF646466` | `surfaceVariant` |
   | `widget_art_placeholder` | `WidgetArtPlaceholder` | `#FF363636` | `*Container` roles |
   | `widget_error` | `WidgetError` | `#FFFF5A5A` | `error` |

   The old `widget_surface_container`, `widget_surface_container_high`, `widget_on_surface`, `widget_on_surface_variant`, `widget_primary`, `widget_on_primary`, `widget_primary_container`, `widget_outline_variant` are removed. Acceptance grep (inventory and acceptance are the same command): `grep -rn "widget_surface_container\|widget_on_surface\|widget_primary\|widget_outline_variant\|1C1C1E\|4780FF\|3D7BFF" feature/widget/src --exclude-dir=build` → 0 hits.

   Computed contrast on `#000000` (Task 1 pins these): title 20.49:1; artist 9.70:1; glyph tint (`#FCFCFE`) 20.49:1; fill 4.65:1; track 3.56:1; the note `#FCFCFE` on the placeholder `#363636` 11.79:1. The placeholder box itself is decorative (1.74:1 on the card).
5. **Glyphs mirror `RollaIcons`.** `widget_ic_previous`, `widget_ic_next`, `widget_ic_play`, `widget_ic_pause` and `widget_ic_music_note` transcribe the geometry of `RollaIcons.SkipPrevious`, `SkipNext`, `Play`, `Pause` and `MusicNote` into `pathData` (`roundedRect` becomes four `A` arcs; `MusicNote` is a stroke of width 2.0 with round caps and joins). `widget_ic_skip_back_15` and `widget_ic_skip_forward_15` are new line icons (stroke 1.6, round caps and joins, no fill): a ring open at the top with an arrowhead, and the numerals "15" drawn as strokes inside the box x 8–16, y 9–16. Forward mirrors back's ring about x = 12; the numerals stay upright. This honours the Phase 1 ruling (phase 1 plan, line 989): `Replay15`/`Forward15` ship with the widget as XML, not in `RollaIcons`, because no Compose screen uses them.
6. **Corners.** The card keeps its shape-drawable background (rounded on every API, now `@color/widget_card` with 24 dp corners) and adds `GlanceModifier.cornerRadius(RollaDimens.widgetCardRadius)`, which clips on API 31+ and is ignored below. The art box gets a new rounded shape drawable `widget_art_placeholder.xml` (12 dp, `@color/widget_art_placeholder`) on every API and `cornerRadius(widgetArtRadius)`. A real artwork bitmap is therefore clipped round on API 31+ only, and drawn square below 31 (a documented degradation; the test device is API 36). Cost if wrong: square album-art corners on Android 7–11.
7. **Copy.**

   | String | Old | New |
   |---|---|---|
   | `widget_label` (new; the receiver's `android:label`) | literal `RollaMusicPlayer` | `Rolla Music` |
   | `widget_action_open_app` | Open RollaMusicPlayer | Open Rolla Music |
   | `widget_empty_artist` | Open RollaMusicPlayer to start playback | Open Rolla Music to start playback |
   | `widget_description` | Playback controls and artwork for RollaMusicPlayer on your home screen. Works fully offline. | Playback controls and artwork for Rolla Music on your home screen. Works fully offline. |

   All other strings stay. Acceptance: `grep -rn "RollaMusicPlayer" feature/widget/src/main/res feature/widget/src/main/AndroidManifest.xml` → 0 hits. The app's own launcher label (`android:label="RollaMusicPlayer"` in `app/src/main/AndroidManifest.xml`) is outside this phase: it is a Phase 7 carry-over.
8. **Cell height of the widget** is decided by Task 0's measurement (table in Task 0). Until then `targetCellHeight` stays 2 and `minHeight` 110 dp; `minResizeHeight` becomes 110 dp (was 90), the nominal content height plus padding at fontScale 1.0.
9. **Glance unit tests** use the test-only `androidx.glance:glance-appwidget-testing:1.1.0` (`runGlanceAppWidgetUnitTest`). No Glance version bump. The tests provide the size themselves with `CompositionLocalProvider(LocalSize provides size)` inside `provideComposable`, because the reference docs list `setAppWidgetSize` as added in 1.2.0. If a matcher used below is missing from 1.1.0 (`hasRunCallbackClickAction`, `hasStartActivityClickAction`), the implementer substitutes the closest one that exists (`hasClickAction()`) and records it; that is a mechanical fix, not a deviation. If the artifact does not resolve at 1.1.0, stop with BLOCKED.
10. **Earlier rulings honoured.** `exported="true"` on the receiver (v1.0 review-gate CRITICAL), the skill's three wiring facts (state observed inside `provideContent`, controller calls on the main thread, one `PlaybackStateHolder`), `SizeMode.Exact`, `updatePeriodMillis="0"` and day == night all stay. The 2026-07-15 track-contrast intent is kept by ruling 1.

## Review Focus

1. **Large system font (fontScale 1.3–2.0):** the five controls and the bar stay fully inside the card; the title or artist is cut instead. Pinned by `WidgetLayoutTest.largeFontKeepsControlsAndBarInsideTheCard` (Task 1). Changing the font size is a user-owned setting, so no device step.
2. **The user resizes the widget narrow:** below 339 dp the artwork hides and every control keeps a ≥ 48 dp box; at 260 dp all five still fit. Pinned by `WidgetLayoutTest` (Task 1) and `MusicWidgetContentTest.narrowWidgetHidesArtworkAndKeepsFiveControls` (Task 3), and measured on the device (Task 5 step 6).
3. **A long title or artist:** one ellipsised line each, and the controls do not move. A visible outcome, so it is observed on the device: Task 5 step 5 compares the controls' y position between a short-title and the longest-title track (equal within 1 px).
4. **Nothing playing (fresh start, after process death):** "Nothing playing", "Open Rolla Music to start playback", the placeholder art and all five controls. Pinned by `MusicWidgetContentTest.emptyStateShowsNothingPlayingCopy` (Task 3).
5. **System light theme:** the widget stays black with light text. Pinned by `WidgetPaletteTest.lightAndDarkResolveToTheSameDarkPalette` (Task 1).

## Files

| File | Task | Change |
|---|---|---|
| `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Dimens.kt` | 1 | Add the ruling 4 widget tokens |
| `feature/widget/build.gradle.kts` | 1, 3 | Apply `rolla.android.robolectric`; `implementation(project(":core:designsystem"))`; Task 3 adds `testImplementation(libs.glance.appwidget.testing)` |
| `gradle/libs.versions.toml` | 3 | `glance-appwidget-testing = { group = "androidx.glance", name = "glance-appwidget-testing", version.ref = "glance" }` |
| `feature/widget/src/main/res/values/colors.xml` | 1 | Rewrite to the ruling 4 palette |
| `feature/widget/src/main/kotlin/.../MusicWidgetTheme.kt` | 1 | Rewrite constants and role mapping; constants become `internal` for the parity test |
| `feature/widget/src/main/kotlin/.../WidgetLayout.kt` | 1 | New: `WidgetLayout`, `widgetLayoutFor`, `widgetArtworkMinWidth`, `widgetMinWidth` |
| `feature/widget/src/main/res/drawable/widget_ic_*.xml` | 2 | Seven glyphs redrawn (ruling 5) |
| `feature/widget/src/main/res/drawable/widget_art_placeholder.xml` | 2 | New 12 dp rounded shape |
| `feature/widget/src/main/res/drawable/widget_card_background.xml` | 2 | `@color/widget_card`, comment rewritten |
| `feature/widget/src/main/res/drawable/widget_preview.xml` | 2 | Regenerated (Task 2 geometry) |
| `feature/widget/src/main/kotlin/.../MusicWidgetContent.kt` | 3 | Rewritten layout |
| `feature/widget/src/main/res/values/strings.xml`, `AndroidManifest.xml`, `res/xml/music_widget_info.xml` | 3 | Copy, label, sizes |
| Tests under `feature/widget/src/test/kotlin/com/rolla/musicplayer/feature/widget/` | 1–3 | `WidgetPaletteTest`, `WidgetContrastTest`, `WidgetLayoutTest`, `WidgetIconParityTest`, `WidgetPreviewTest`, `MusicWidgetContentTest`, `MusicWidgetInfoTest` |
| Spec, roadmap, CLAUDE.md, `ui-style-guide.md`, widget agent and skill docs | 4 | Docs |

---

### Task 0: Device probe — how the Motorola launcher hosts the widget (controller, read-only)

**Who:** the controller, on the device. No code changes. Device rules: never uninstall or clear data; never change a system setting; check `mCurrentFocus` before any injected input (this task injects none); delete any screenshot that shows another app's notification.

- [ ] **Step 1:** Ask the user: "Please add the Rolla Music widget at its default size to a free spot on a home-screen page, and leave that page showing." Wait for the reply.
- [ ] **Step 2:** `adb shell dumpsys window | grep mCurrentFocus` shows `com.motorola.launcher3`. `dumpsys appwidget` lists a widget whose provider is `com.rolla.musicplayer/...MusicWidgetReceiver` and host `com.motorola.launcher3`; record its id.
- [ ] **Step 3:** `adb exec-out screencap -p > <scratchpad>/moto/6c-probe-default.png`, then `adb shell uiautomator dump /sdcard/6c.xml` and pull it. The node whose `package="com.rolla.musicplayer"` and the enclosing `AppWidgetHostView` give the bounds in px; dp = px ÷ 3. If `uiautomator dump` fails, measure the card from the screenshot instead (the 6b card is `#1C1C1E`). Record default W × H in dp, and whether the launcher draws anything around the card (padding, its own background, a different corner).
- [ ] **Step 4:** Ask the user to resize the widget to one row high (4 × 1), if the launcher allows it, and repeat Step 3 → `6c-probe-1row.png`; record `h1`. Then ask them to resize it as narrow as the launcher allows → record `wMin` (the 6b build allows 180 dp).
- [ ] **Step 5:** Append the facts to the ledger and rule, using this table:

   | Measurement | Ruling |
   |---|---|
   | `h1` ≥ 110 dp | `targetCellHeight="1"`, `minHeight="110dp"` |
   | 90 dp ≤ `h1` < 110 dp | `targetCellHeight="1"`, `minHeight` and `minResizeHeight` = ⌊`h1`⌋ dp (content nominal is 89.57 dp at fontScale 1.0) |
   | `h1` < 90 dp, or no 1-row size | keep `targetCellHeight="2"`, `minHeight="110dp"` |
   | default W ≥ 339 dp | artwork shows at the default size: expected |
   | default W < 339 dp | keep ruling 2; tell the user at phase end that the artwork hides at this launcher's default width |
   | launcher adds its own padding or background | record it; Task 5 measures geometry inside the launcher's frame |

   Carry the chosen `targetCellHeight`, `minHeight` and `minResizeHeight` into Task 3's brief. Leave the widget placed for Task 5; ask the user to resize it back to the default size.

### Task 1: Tokens, palette and layout math

**Owner:** widget-agent (`model: "opus"`). It adds the `RollaDimens` group in `:core:designsystem` as a token-only change.

**Files:** `Dimens.kt`; `feature/widget/build.gradle.kts`; `colors.xml`; `MusicWidgetTheme.kt`; create `WidgetLayout.kt`; tests `WidgetPaletteTest.kt`, `WidgetContrastTest.kt`, `WidgetLayoutTest.kt`.

**Interfaces:**
- Produces, in `:core:designsystem`: the eleven `RollaDimens.widget*` tokens of ruling 4 (`Dp`, except `widgetTitleSize` and `widgetArtistSize`, which are `TextUnit`).
- Produces, in `WidgetLayout.kt` (package `com.rolla.musicplayer.feature.widget`):
  ```kotlin
  internal data class WidgetLayout(val showArtwork: Boolean, val controlCellWidth: Dp, val verticalPadding: Dp)
  internal fun widgetLayoutFor(size: DpSize, fontScale: Float): WidgetLayout
  internal fun widgetArtworkMinWidth(): Dp   // 339.dp, derived from tokens
  internal fun widgetMinWidth(): Dp          // 260.dp, derived from tokens
  ```
- Produces, in `MusicWidgetTheme.kt`: `internal val WidgetCard`, `WidgetTitle`, `WidgetArtist`, `WidgetAccent`, `WidgetTrack`, `WidgetArtPlaceholder`, `WidgetError` (`Color`); `RollaWidgetColors: ColorProviders` keeps its name and type.

- [ ] **Step 1: Write the failing tests.**

  `WidgetLayoutTest` (JVM):
  - `artworkShowsFromThreeHundredThirtyNineDp`: `widgetLayoutFor(DpSize(339.dp, 110.dp), 1f)` → `showArtwork == true`, `controlCellWidth == 48.dp` (±0.01). `widgetLayoutFor(DpSize(338.9.dp, 110.dp), 1f)` → `showArtwork == false`, `controlCellWidth` ≈ 63.78 dp.
  - `everyWidthFromTheMinimumKeepsFortyEightDpCells`: for every width from 260.0 to 600.0 dp in 0.1 steps, `controlCellWidth ≥ 47.999.dp`; and `widgetLayoutFor(DpSize(400.dp, 110.dp), 1f).controlCellWidth` ≈ 60.2 dp.
  - `derivedWidthsComeFromTokens`: `widgetArtworkMinWidth() == 339.dp`, `widgetMinWidth() == 260.dp`.
  - `paddingCentresNominalContent`: height 110, fontScale 1.0 → `verticalPadding` ≈ 10.21 dp (±0.05); height 200 → ≈ 55.21 dp.
  - `largeFontKeepsControlsAndBarInsideTheCard`: height 110, fontScale 2.0 → `verticalPadding == 0.dp`; and for heights 110–300 dp and fontScale 0.85–2.0, `height − 2 × verticalPadding ≥ minTouchTarget + widgetProgressHeight`.

  `WidgetPaletteTest` (Robolectric):
  - `resourcesMatchKotlinPalette`: for each row of the ruling 4 palette table, `context.getColor(R.color.<name>) == <Constant>.toArgb()`, and the constant equals the table value.
  - `lightAndDarkResolveToTheSameDarkPalette`: under `@Config(qualifiers = "notnight")` and again `"night"` (two test methods sharing one assertion helper), `RollaWidgetColors.surface.getColor(context)` is `#000000`, `onSurface` `#FCFCFE`, `onSurfaceVariant` `#B0B0B2`, `primary` `#2F6FF0`, `surfaceVariant` `#646466`.

  `WidgetContrastTest` (JVM; a local WCAG relative-luminance helper):
  - `textPairsClearFourPointFive`: title on card 20.49 (±0.05), artist on card 9.70 (±0.05), both ≥ 4.5.
  - `nonTextPairsClearThree`: glyph tint 20.49, fill 4.65, track 3.56, note on placeholder 11.79 (each ±0.05, each ≥ 3.0).
  - `specTrackWouldFail`: `#333333` on `#000000` is 1.66 (±0.05) and < 3.0 (documents ruling 1, and is the control that the helper can return a failing ratio).

- [ ] **Step 2:** Run `./gradlew :feature:widget:testDebugUnitTest --tests "*WidgetLayoutTest" --tests "*WidgetPaletteTest" --tests "*WidgetContrastTest"`. Expected: compilation fails (`widgetLayoutFor`, `WidgetCard`, `R.color.widget_card` unresolved).
- [ ] **Step 3:** Add the tokens to `Dimens.kt`. In `feature/widget/build.gradle.kts` apply `id("rolla.android.robolectric")` and add `implementation(project(":core:designsystem"))`. Rewrite `colors.xml` and `MusicWidgetTheme.kt` to the ruling 4 palette and role mapping (every role the old scheme set is still set, to the column given). Write `WidgetLayout.kt` from ruling 2 and ruling 3, reading only `RollaDimens`; the line-height factor is a `private const val TEXT_LINE_HEIGHT_FACTOR = 1.33f` with a one-line comment.
- [ ] **Step 4:** Run the Step 2 command → PASS. Then the gate for the touched modules: `./gradlew :feature:widget:testDebugUnitTest :core:designsystem:testDebugUnitTest spotlessCheck detekt`.
- [ ] **Step 5:** Mutation runs (table below), then the acceptance grep of ruling 4 — it may still hit `widget_card_background.xml` and `widget_preview.xml`, which Task 2 rewrites; report the hits.

### Task 2: Glyphs, placeholder and picker preview

**Owner:** widget-agent (`model: "opus"`).

**Files:** the seven `widget_ic_*.xml`; create `widget_art_placeholder.xml`; `widget_card_background.xml`; `widget_preview.xml`; tests `WidgetIconParityTest.kt`, `WidgetPreviewTest.kt`. `feature/widget/build.gradle.kts`: `testImplementation(project(":core:testing"))` is already there (`PixelCapture.drawRoot()`).

**Interfaces:**
- Consumes: the Task 1 palette resources.
- Produces: drawables `widget_ic_previous`, `widget_ic_skip_back_15`, `widget_ic_play`, `widget_ic_pause`, `widget_ic_skip_forward_15`, `widget_ic_next`, `widget_ic_music_note` (24 × 24 viewport, white, tinted at the call site), `widget_art_placeholder` (shape, 12 dp corners), `widget_card_background` (shape, 24 dp corners, `@color/widget_card`).

**Preview geometry** (viewport 339 × 110, the artwork threshold width; every colour is a palette value):
card 0–339 × 0–110, 24 corners, `#000000`; art box x 10–79, y 20.5–89.5, 12 corners, `#363636`, with the `MusicNote` path scaled to a 44 box centred in it, `#FCFCFE`; title bar x 89–239, y 24–34, `#FCFCFE`; artist bar x 89–189, y 40–48, `#B0B0B2`; five glyphs (the real paths of prev, back 15, play, forward 15, next) at 28 units, centred in five 48-wide cells from x 89 to 329, centre y 72, `#FCFCFE`; progress track x 89–329, y 96–99, `#646466`, fill x 89–169, `#2F6FF0`.

- [ ] **Step 1: Write the failing tests** (Robolectric, `@GraphicsMode(NATIVE)`; each icon rendered with `Image(painter, null, Modifier.size(96 px))` in a `createComposeRule()` content and captured with `drawRoot()`; masks are pixels with alpha ≥ 128):
  - `transportGlyphsMatchRollaIcons`: for each pair (`widget_ic_previous`, `RollaIcons.SkipPrevious`), (`widget_ic_next`, `SkipNext`), (`widget_ic_play`, `Play`), (`widget_ic_pause`, `Pause`), (`widget_ic_music_note`, `MusicNote`), mask IoU ≥ 0.95. Control in the same test: IoU(`RollaIcons.Play`, `RollaIcons.Pause`) < 0.6.
  - `forwardRingMirrorsBackRing`: outside the numeral box (x 8–16, y 9–16 in viewport units), IoU(forward mask, horizontally flipped back mask) ≥ 0.95. Control: IoU(back mask, flipped back mask) < 0.9 (the ring is not symmetric, so the check can fail).
  - `fifteenStaysUpright`: inside the numeral box, IoU(forward, back) ≥ 0.95, and IoU(forward, flipped back) < 0.8.
  - `previewUsesThePalette` (`WidgetPreviewTest`): draw `widget_preview` at 4× (1356 × 440 px) into a bitmap. At viewport points (5, 55) `#000000`, (15, 25) `#363636`, (100, 97.5) `#2F6FF0`, (300, 97.5) `#646466`, each within ±3 per channel. Control: (200, 55) — between the text and the glyph row, on the card — is `#000000`, while (209, 72) (the play glyph centre; the five cell centres are x 113, 161, 209, 257, 305) is `#FCFCFE`.
- [ ] **Step 2:** Run `./gradlew :feature:widget:testDebugUnitTest --tests "*WidgetIconParityTest" --tests "*WidgetPreviewTest"`. Expected: FAIL on the IoU and colour assertions (the old Material glyphs and the `#1C1C1E` preview).
- [ ] **Step 3:** Redraw the drawables per ruling 5 and the preview geometry. Rewrite each file's header comment to say which `RollaIcons` entry it mirrors (or, for ±15, that it is widget-only per the Phase 1 ruling).
- [ ] **Step 4:** Run the Step 2 command → PASS; then `./gradlew :feature:widget:testDebugUnitTest spotlessCheck detekt lintDebug`. Run ruling 4's acceptance grep → 0 hits.
- [ ] **Step 5:** Mutation runs (table below). Record the old Material play path's IoU against `RollaIcons.Play`: if it is ≥ 0.95, raise the threshold to the smallest value that fails it while every correct pair still passes, and report both numbers.

### Task 3: The widget layout, copy and provider sizes

**Owner:** widget-agent (`model: "opus"`); the catalog line is a build-tooling change folded into this task.

**Files:** `MusicWidgetContent.kt`; `strings.xml`; `AndroidManifest.xml`; `music_widget_info.xml`; `gradle/libs.versions.toml`; `feature/widget/build.gradle.kts`; tests `MusicWidgetContentTest.kt`, `MusicWidgetInfoTest.kt`.

**Interfaces:**
- Consumes: `widgetLayoutFor`, `widgetMinWidth()` (Task 1); the Task 2 drawables; Task 0's `targetCellHeight`, `minHeight`, `minResizeHeight`.
- Produces: `internal fun MusicWidgetContent(context: Context, state: MusicWidgetState, artwork: Bitmap?)` — same signature; `MusicWidget` is not edited. Test tags (`internal const val` in `MusicWidgetContent.kt`): `WIDGET_CONTROL_TAG = "widget_control"` on each of the five control boxes.

**Layout** (all sizes from `RollaDimens`, all colours from `GlanceTheme.colors`):
`Box(fillMaxSize, background(widget_card_background), cornerRadius(widgetCardRadius), clickable(open app), contentDescription "Open Rolla Music")` → `Row(fillMaxSize, padding(start = widgetCardPaddingStart, end = widgetCardPaddingEnd, top/bottom = layout.verticalPadding), verticalAlignment = CenterVertically)` → [if `layout.showArtwork`: art `Box(size(widgetArt), background(widget_art_placeholder), cornerRadius(widgetArtRadius))` holding either the bitmap (`ContentScale.Crop`, `fillMaxSize`, description "Album artwork for %s") or the note (`size(widgetArtGlyph)`, tint `onSurface`, description "No album artwork"); `Spacer(width(widgetArtGap))`] → `Column(defaultWeight(), fillMaxHeight)`: text block `Column(fillMaxWidth, defaultWeight(), verticalAlignment = Bottom)` with title (`widgetTitleSize`, Bold, `onSurface`, `maxLines = 1`) and artist (`widgetArtistSize`, Normal, `onSurfaceVariant`, `maxLines = 1`); controls `Row(fillMaxWidth, height(minTouchTarget))` of five cells, each `Box(defaultWeight(), fillMaxHeight, clickable(callback), semantics { contentDescription; testTag = WIDGET_CONTROL_TAG }, contentAlignment = Center)` holding an `Image(size(widgetControlGlyph), contentDescription = null, tint onSurface)`; then `LinearProgressIndicator(fillMaxWidth, height(widgetProgressHeight), color = primary, backgroundColor = surfaceVariant)`. `fontScale` comes from `context.resources.configuration.fontScale`.

- [ ] **Step 1: Write the failing tests.**

  `MusicWidgetContentTest` (Robolectric + `runGlanceAppWidgetUnitTest`; content wrapped in `GlanceTheme(RollaWidgetColors)` and `CompositionLocalProvider(LocalSize provides size)`; default size 360 × 110 dp; `context = ApplicationProvider.getApplicationContext()`):
  - `playingStateShowsTitleArtistAndPause`: state `title = "Song A", artist = "Artist A", isPlaying = true` → nodes with text "Song A" and "Artist A" exist; a node `hasContentDescription("Pause") and hasRunCallbackClickAction(PlayPauseActionCallback::class.java)` exists; no node has description "Play".
  - `pausedStateShowsPlay`: same state with `isPlaying = false` → "Play" exists with the play/pause callback; "Pause" does not.
  - `controlsAreInTransportOrderWithTheirCallbacks`: `onAllNodes(hasTestTag(WIDGET_CONTROL_TAG))` has count 5, and node *i* matches description and callback of, in order: ("Previous track", `PreviousActionCallback`), ("Skip back 15 seconds", `SkipBack15ActionCallback`), ("Play", `PlayPauseActionCallback`), ("Skip forward 15 seconds", `SkipForward15ActionCallback`), ("Next track", `NextActionCallback`).
  - `emptyStateShowsNothingPlayingCopy`: default `MusicWidgetState()` and `artwork = null` → texts "Nothing playing" and "Open Rolla Music to start playback", description "No album artwork", and 5 control nodes.
  - `artworkBitmapReplacesThePlaceholder`: state title "Song A" with a 4 × 4 bitmap → description "Album artwork for Song A" exists and "No album artwork" does not; the same content with `artwork = null` shows the placeholder (control, second `runGlanceAppWidgetUnitTest` in the test).
  - `narrowWidgetHidesArtworkAndKeepsFiveControls`: at 338.9 × 110 dp neither "No album artwork" nor any "Album artwork for" description exists and the control count is 5; at 339 × 110 dp "No album artwork" exists (control).
  - `cardOpensRollaMusic`: a node with description "Open Rolla Music" has a start-activity click action (`hasStartActivityClickAction(ComponentName(packageName, "com.rolla.musicplayer.MainActivity"))`, or `hasClickAction()` per ruling 9).

  `MusicWidgetInfoTest` (Robolectric; parse `R.xml.music_widget_info` with `resources.getXml` and read attributes in the `android` namespace):
  - `minimumWidthFitsFiveTouchTargets`: `minWidth` and `minResizeWidth` both equal `widgetMinWidth()` (260 dp).
  - `heightsFollowTheDeviceProbe`: `minHeight`, `minResizeHeight` and `targetCellHeight` equal Task 0's ruled values; `updatePeriodMillis == 0`; `resizeMode` is horizontal|vertical.
  - `labelIsRollaMusic`: the receiver's label resolves to "Rolla Music" (`PackageManager.getReceiverInfo(ComponentName(context, MusicWidgetReceiver::class.java), 0).loadLabel(pm)`).

- [ ] **Step 2:** Run `./gradlew :feature:widget:testDebugUnitTest --tests "*MusicWidgetContentTest" --tests "*MusicWidgetInfoTest"`. Expected: compilation fails first (testing artifact missing); after Step 3's build changes only, the assertions fail (old copy, old sizes, no test tags).
- [ ] **Step 3:** Add the catalog entry and `testImplementation(libs.glance.appwidget.testing)`. Rewrite `MusicWidgetContent.kt` to the layout above (delete `WidgetDimens`, `artworkSizeFor`, `ARTWORK_MAX_WIDTH_FRACTION`; keep `MAIN_ACTIVITY_CLASS_NAME` and its KDoc; rewrite the file KDoc to spec §13). Apply ruling 7's copy, `android:label="@string/widget_label"`, and the sizes from ruling 2 and Task 0.
- [ ] **Step 4:** Run the Step 2 command → PASS, then the full gate: `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug`. Expected: BUILD SUCCESSFUL; quote the total unit-test count. Run ruling 7's acceptance grep → 0 hits.
- [ ] **Step 5:** Mutation runs (table below).

### Task 4: Docs

**Owner:** widget-agent (`model: "opus"`). Each named section is grepped after the edit to prove it changed; every completeness sentence is worded to the grep behind it.

- [ ] **Spec §13:** the track is `#646466` (ruling 1, with the computed ratios); the artwork hides below 339 dp and the widget's minimum width is 260 dp (ruling 2); real artwork corners are rounded on API 31+ only (ruling 6); "Amended 2026-10-22, Phase 6c" marker. **§5.4:** add the eleven widget tokens as rows. **§5.5:** `Replay15`/`Forward15` are widget drawables (`widget_ic_skip_back_15`/`_forward_15`), not `RollaIcons` entries.
- [ ] **`.claude/rules/ui-style-guide.md` §8:** a note that §13 of the spec is binding and the widget tokens live in `RollaDimens`, colours in the widget palette.
- [ ] **CLAUDE.md:** a "Phase 6c" bullet under the redesign list (the look, ruling 2's narrow behaviour, `RollaDimens.widget*`, palette parity test). Grep CLAUDE.md for `56dp artwork` and `§8 dark rounded card` and either leave them (they sit in the v1.0 "What's shipped" history table) or add "(v1.0)" if a line reads as current.
- [ ] **`.claude/agents/widget-agent.md` and `.claude/skills/implement-home-widget/SKILL.md`:** `grep -n "1C1C1E\|4780FF\|56dp\|56 dp\|ControlTouchSize\|surface_container" ` in both; update every hit that describes the current widget, and keep the touch-target rule (clickable on the full ≥ 48 dp node).
- [ ] **Roadmap:** row 6b → "Committed 3e86d8b (2026-10-22)"; row 6c → this plan's file name and "implemented; device check pending" (Task 5 updates it to "device-checked; pending commit").
- [ ] Verify: `grep -n "Amended 2026-10-22" docs/superpowers/specs/2026-10-06-oneui-redesign-design.md` → hits in §5.4, §5.5 and §13; `grep -n "Phase 6c" CLAUDE.md` → 1 hit.

### Task 5: Device check (controller)

Device rules as in Task 0, plus: install with `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark`; afterwards `firstInstallTime` must still read 2026-10-21 15:42:36. Never save tags, never touch the user's playlists. Before every injected tap, `dumpsys window | grep mCurrentFocus` shows the expected window; after each tap, a read-only check (`dumpsys media_session`) shows the effect.

- [ ] **Step 1:** Install; check `firstInstallTime`. Open the app from the launcher icon (ask the user, or tap it after the focus check) and start a track with artwork. Press HOME (key event to the system).
- [ ] **Step 2:** Screenshot → `6c-playing.png`. Measure, at 3.0 px/dp, ±2 dp (±6 px): artwork box 69 dp; art start inset 10 dp; art → title gap 10 dp; the play glyph about 18 dp tall; the control cells ≥ 48 dp wide (glyph-centre pitch ≥ 144 px). Sample colours, ±3 per channel: card `#000000`, progress fill `#2F6FF0`, track `#646466`; the brightest artist-text pixel `#B0B0B2`. Corners: the card's corner matches the launcher's 24 dp clip (no square corner showing).
- [ ] **Step 3:** Controls. For each of next, previous, +15, −15, pause, play: focus check → tap the glyph centre from Step 2 → `dumpsys media_session` shows the expected change (track title, position ± 15 s within 2 s, state 2/3). Then tap the card body away from the controls → focus becomes `com.rolla.musicplayer/.MainActivity`; press HOME.
- [ ] **Step 4:** No artwork and nothing-playing states. Play a track without artwork if the library has one (read-only check: `content query --uri content://media/external/audio/media --projection _id:title:album_id`, and the app's artwork placeholder in its list) → screenshot shows the `#363636` rounded box with a white note. Nothing-playing is covered by the JVM test (killing the process is not part of this check).
- [ ] **Step 5 (Review Focus 3):** Find the longest title (`content query … --projection title`), play it through the app's Search, return HOME, screenshot → `6c-long-title.png`. The title is one ellipsised line, and the control glyph centres' y equals Step 2's within 1 px.
- [ ] **Step 6 (Review Focus 2):** Ask the user to resize the widget to its narrowest width. Screenshot → `6c-narrow.png`: if the width is under 339 dp the artwork is gone, all five glyphs show, and the glyph-centre pitch is ≥ 144 px. Ask the user to resize it back.
- [ ] **Step 7:** Ask the user to open the widget picker at Rolla Music and say whether the preview shows the black card with the blue bar; or, with their go-ahead, screenshot it.
- [ ] **Step 8:** Ledger: PASS/FAIL per step with the numbers; delete screenshots that show other apps' notifications; roadmap row 6c → "device-checked 2026-10-xx; pending commit". Defects found here go to one fix dispatch and a scoped re-review, as in 6b.

## Mutation and control table

Every row: (a) the trivial stub, (b) the assertion in the same test that fails under it, (c) the named mutation, (d) why the observed value differs under (c).

| Test | (a) Trivial stub | (b) Failing assertion | (c) Mutation | (d) Why it differs |
|---|---|---|---|---|
| `artworkShowsFromThreeHundredThirtyNineDp` | `showArtwork = true` | 338.9 dp → `false` | threshold 330 dp | 338.9 then shows art |
| `everyWidthFromTheMinimumKeepsFortyEightDpCells` | constant 48 dp | 400 dp → 60.2 | threshold 330 dp | at 330 dp the cell is (330−99)/5 = 46.2 < 48 |
| `derivedWidthsComeFromTokens` | return 0.dp | `== 339.dp` | drop `widgetArtGap` from the sum | 329 ≠ 339 |
| `paddingCentresNominalContent` | padding 0 | 110 → 10.21 | factor 1.0 instead of 1.33 | nominal 80.0 gives 15.0 |
| `largeFontKeepsControlsAndBarInsideTheCard` | constant 10 dp | fontScale 2.0 → 0 | ignore `fontScale` | padding stays 10.21 |
| `resourcesMatchKotlinPalette` | — (values pinned) | `WidgetTrack == #646466` | set `widget_track` to `#333333` in colors.xml only | resource ≠ constant |
| `lightAndDarkResolveToTheSameDarkPalette` | — | `notnight` surface `#000000` | light side = `lightColorScheme()` | the light default surface is a near-white, not `#000000` |
| `textPairsClearFourPointFive` / `nonTextPairsClearThree` | ratio 21 always | `specTrackWouldFail` < 3.0 | luminance without the sRGB curve (raw channel / 255) | `#2F6FF0` then gives about 9.4:1 instead of 4.65, outside ±0.05 |
| `transportGlyphsMatchRollaIcons` | — | IoU ≥ 0.95 | keep the old Material play path | Material triangle is sharper and larger (Task 2 Step 5 records the value) |
| `forwardRingMirrorsBackRing` | forward = back | flipped IoU ≥ 0.95 | forward ring not mirrored | arrowhead sits on the other side |
| `fifteenStaysUpright` | — | IoU(forward, flipped back) < 0.8 | mirror the whole forward icon | numerals read "51" |
| `previewUsesThePalette` | — | (5, 55) `#000000` | keep `#FF1C1C1E` | 28 levels off |
| `playingStateShowsTitleArtistAndPause` / `pausedStateShowsPlay` | always the play icon | "Pause" exists while playing | invert `isPlaying` | descriptions swap |
| `controlsAreInTransportOrderWithTheirCallbacks` | — | node 0 = Previous + `PreviousActionCallback` | swap previous and next callbacks | node 0 pairs "Previous track" with `NextActionCallback` (description and callback are matched together, so a swap cannot pass) |
| `emptyStateShowsNothingPlayingCopy` | — | "Open Rolla Music to start playback" | keep the old string | text differs |
| `artworkBitmapReplacesThePlaceholder` | always placeholder | "Album artwork for Song A" exists | ignore `artwork` | placeholder shows |
| `narrowWidgetHidesArtworkAndKeepsFiveControls` | always art | 338.9 dp → no art | draw art unconditionally | placeholder present |
| `cardOpensRollaMusic` | — | the description "Open Rolla Music" | old string | text differs |
| `minimumWidthFitsFiveTouchTargets` | — | `minResizeWidth == 260dp` | keep 180 dp | 180 ≠ 260 |
| `heightsFollowTheDeviceProbe` | — | Task 0 values | keep 90 dp `minResizeHeight` | 90 ≠ ruled value |
| `labelIsRollaMusic` | — | label "Rolla Music" | literal `RollaMusicPlayer` | text differs |

## Self-review

- **Spec coverage (§13):** card colour and radius → ruling 6, Tasks 1–3; 69 dp art, 12 dp radius, placeholder → Tasks 2–3; title/artist sizes and colours → rulings 4, Task 3; transport row of `RollaIcons`-like glyphs → ruling 5, Task 2; 3 dp bar → rulings 1 and 4, Task 3; "Unchanged" items → ruling 10; picker preview → Task 2. §14 tolerances → Task 5 Step 2. §16 widget docs → Task 4.
- **Steps:** each test step names assertions and values; code steps give signatures and the layout tree; no function bodies beyond the formulas the spec does not fix.
- **Type consistency:** `widgetLayoutFor(size: DpSize, fontScale: Float): WidgetLayout`, `widgetMinWidth()`, `widgetArtworkMinWidth()`, `WIDGET_CONTROL_TAG` and the palette constant names are used identically in Tasks 1, 3 and the table.
- **Review Focus:** five lines, each with its test or device step in the owning task.
- **Proportion:** the spec section is short, but this phase carries the geometry the spec left to calibration; code blocks are limited to the Task 1 interface.
- **superpowers-extras pre-flight:**
  - 1 (dependent phases): written after 6b was committed (3e86d8b), from the landed widget code; 6c consumes only the 6a/1 tokens and `RollaIcons`.
  - 2a (value tables): §13's values are in Global Constraints verbatim; the one deviation (track) is ruling 1. Derived values are computed in rulings 1–4 (contrast ratios, 339/260 dp, 89.57 dp, glyph boxes).
  - 2b (consumers): `MusicWidgetContent` has one caller (`MusicWidget.provideGlance`), signature unchanged; the removed colour resources are swept by ruling 4's grep; `RollaDimens` only gains entries.
  - 2c (test level): device geometry and colours cannot be read by Glance unit tests, so they are measured on the device (Task 5); the font-scale case is JVM-only because the font size is a user setting.
  - 2d (can each test fail): the table above.
  - 3 (framework-validated output): Glance renders through `runGlanceAppWidgetUnitTest`, the framework's real composition; the launcher is checked in Tasks 0 and 5.
  - 4 (mutations are hypotheses): each is valid usage; Task 2 Step 5 handles a possibly equivalent icon mutation.
  - 5 (device steps): Task 0 runs first and is read-only; every tap names its target window and its read-only effect check; no process kill.
- **Open observations applied:** 0016 (table with column d), 0017 (Review Focus 3 observed on screen), 0018 (every derived value computed; preview probe colours differ by ≥ 12 levels from the mutated ones), 0019 (rulings 5 and 10 cite earlier rulings on these files), 0020 (inventories are greps that double as acceptance), 0022 (no text-entry screen in this phase; the per-screen inset question does not arise for a widget).

## Carry-overs to Phase 7

- The app's launcher label still reads "RollaMusicPlayer" (`app/src/main/AndroidManifest.xml`).
- The widget's light-theme behaviour is deliberately dark; Phase 7 calibration measures the widget against the reference on the Samsung if it is available.
