# One UI redesign, Phase 3: Now Playing, queue, mini-player, Equaliser, Settings

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The player surfaces, the Equaliser and Settings take the One UI look, and every control on them works:
- Now Playing: gradient background, thin seekbar, bare Play glyph, a working Volume button and ⋮ menu.
- Queue: a real queue sheet, opened from both the mini-player and Now Playing.
- Mini-player: restyled pill.
- Equaliser: One UI EQ card, grid and chips.
- Settings: One UI cards and rows.

**Architecture:**
- **`:core:media` owns the queue.**
  - A pure mapping turns the player's timeline into a `QueueState` in playback order.
  - `PlaybackService` publishes the queue through `PlaybackStateHolder`, the same pattern as every other playback
    state.
  - `PlaybackController` gains the queue, `skipToQueueIndex`, and shared Play/Shuffle/Play-from helpers. Every list
    screen uses these helpers, Phase 4a included.
- **`:feature:player` owns the UI.**
  - It gets `QueueSheet` and `QueueViewModel`, the restyled `MiniPlayer` and `NowPlayingScreen`, and a
    `SystemVolumePanel` seam.
  - The app shell hosts the mini-player's queue sheet outside the pill's `AnimatedVisibility`, so hiding the pill never
    disposes an open sheet.
- **`:feature:equalizer` and `:feature:settings` are rebuilt on the Phase 1 kit**, and their public APIs stay the same.

**Tech stack:**
- Kotlin 2.0.21
- Compose BOM 2024.09.03 (Foundation 1.7.3, M3 1.3.0)
- Media3 1.4.1
- Hilt
- Robolectric 4.13 for JVM Compose and ExoPlayer tests

**Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md):
- §5.4 (dimensions)
- §6 (kit)
- §8.3 (mini-player)
- §8.4 (Now Playing)
- §8.5 (Equaliser)
- §8.6 (Settings)
- §8.7 (sheets and menus)
- §11 (queue)
- §12 (dead controls)
- §15 (tests)

Read it with the roadmap [`2026-10-06-oneui-redesign-roadmap.md`](2026-10-06-oneui-redesign-roadmap.md) and the code as
it landed in Phase 2 (commit `00c75e9`).

## How to read this plan

- **New, self-contained units come as complete code.** These are:
  - the queue model and mapping
  - the controller helpers
  - `QueueViewModel` and `QueueSheet`
  - `OneUiSeekBar`
  - the background modifier node
  - the volume seam
  - the new `PlayerViewModel` members
- **Restyles of large existing files come as a target signature, a numbered transformation list and full test code.**
  The files are `MiniPlayer.kt`, `NowPlayingScreen.kt`, `EqualizerScreen.kt`, `SettingsRows.kt` and
  `SettingsScreen.kt`. Re-typing 600-line files here would only drift from the file you already have.
- **Code blocks have not been compiled.** You may make minimal mechanical fixes (imports, opt-ins, a missing `@OptIn`),
  and you must report each one as a deviation. Never change behaviour, signatures or assertions to get to green.
- **Every test names its mutation:** one realistic change to production code that should turn it RED. Apply it,
  confirm RED, revert byte-exact and record the decisive output line.
  - A named mutation is a hypothesis about the framework, not a fact (observer 0010). If it turns out to be an
    equivalent mutant, show it with evidence, substitute the closest fail-able mutation, and say so in your report.
  - A mutation that survives without that evidence is a defect.
- **Never prescribe a test host that duplicates production construction** (observer 0009). Tests call the production
  composable or factory itself.

## Global Constraints

- minSdk 24, compileSdk 34, JVM 17.
- No new runtime dependencies. Test-only dependencies come from the version catalog. The
  `rolla.android.robolectric` convention plugin adds Robolectric.
- Offline app: no network libraries and no INTERNET permission.
- Never hardcode visual values outside `:core:designsystem`. Screens use `RollaDimens`, the typography and color
  extensions, `MaterialTheme.shapes`/`RollaShapes` and `RollaIcons`. Preview scaffolding is the only exception.
- **Never use `primary` as a text or icon tint on Now Playing.** On the gradient's `washEnd` it measures 2.88:1 (Phase 1
  ledger). Active-state glyph tints use `accentText`. Blue text anywhere uses `accentText`. Every `TextButton` this phase
  touches passes explicit `accentText` content colors.
- UI copy:
  - The screen and every label say **"Equaliser"**. Code identifiers stay `Equalizer*`.
  - Counts say **"tracks"**.
  - The Settings title is **"Rolla Music settings"**. No Samsung branding.
- No dead controls. Every visible button does something real (spec §12).
- **Reduced motion.** Animations use `rollaSpringOrSnap(rememberReducedMotion())`, or snap when reduced motion is on.
  This phase deletes the module-local `isReducedMotion()` copies in `:feature:player` and `:feature:equalizer`, and
  sweeps every caller in both modules.
- **Robolectric tests:**
  - Run at SDK 34. Each module that gains the plugin also gets `src/test/resources/robolectric.properties` containing
    `sdk=34`. Pixel tests may pin `@Config(sdk = [30])` with a comment, as `OneUiTabRowTest` does.
  - Layout tests that need more than 320 dp use `@Config(qualifiers = "w360dp-h640dp")`.
  - Text geometry and pixel tests use `@GraphicsMode(NATIVE)`.
  - `captureToImage()` times out under Robolectric 4.13, so pixel tests draw the root view into a bitmap with
    `view.draw(Canvas(bitmap))`.
  - A dialog or sheet that contains a text field never idles under a PAUSED looper. Such tests take a method-level
    `@LooperMode(LEGACY)`.
- Spotless enforces CRLF. Run `./gradlew spotlessApply` after editing Kotlin. Never write a BOM. Edit with the
  Edit/Write tools, never `sed -i`.
- **Detekt:**
  - LongMethod: 30 lines.
  - LongParameterList: 6 parameters.
  - Property declarations are exempt from MagicNumber.
  - A narrow `@Suppress` with a one-line reason is accepted.
- **No `git commit`, `git add` or `git stash`.** Each task ends at a checkpoint, the orchestrator reviews the
  working-tree diff, and the user triggers commits.
- **The baseline-profile contract survives:**
  - The mini-player keeps the content description "Now playing: <title> by <artist>. Open player."
  - The Now Playing seekbar keeps `contentDescription = "Playback position"`. `BaselineProfileGenerator` waits on it.
  - The root keeps `testTagsAsResourceId = true`.

## Controller rulings carried into this plan

1. **The queue is published by the service, not polled by the controller.** `PlaybackService`'s listener writes
   `PlaybackStateHolder.queue` from `onTimelineChanged`, `onMediaItemTransition` and `onShuffleModeEnabledChanged`, on
   the player's main thread. `StartFirstShuffleEnforcer.setShuffleOrder` fires `onTimelineChanged`, so a reshuffle
   republishes itself.
2. **`QueueState.currentIndex` is a position in `items` (playback order). `QueueItem.index` is the Media3 window
   index**, which is what `skipToQueueIndex` takes. Spec §11 names both fields but leaves `currentIndex`'s meaning
   open. The sheet needs a list position, and the seek needs a window index.
3. **A queue tap plays.** `skipToQueueIndex` calls `seekToDefaultPosition(index)` and then `play()`. An out-of-range
   index is a no-op. Tapping a queue row means "play this", which is how Samsung Music behaves.
4. **Skipping inside a shuffled queue keeps the shuffle order.** This closes the Phase 2 carry-over. The enforcer
   reacts only to `PLAYLIST_CHANGED` transitions, so a SEEK transition never reshuffles. The real-ExoPlayer test in
   Task 2 pins this. There is no add-to-queue or remove-from-queue in the app (spec §3 non-goal), so the "item added
   before the current one" and "removal reshuffles" carry-overs have no trigger. They are recorded and closed.
5. **Tapping a track plays the whole list from that track** (Phase 2 carry-over L9; the user confirms at plan review).
   Today `TracksTab` plays the tapped song alone, a queue of one, which makes the new queue sheet pointless on Home.
   Album, Artist and PlaylistDetail already play the list. All four now use one helper, `PlaybackController.playFrom`.
6. **The artwork transition slows down** (user, 2026-10-09: "smooth but fast"). `artworkBoundsTransform` moves from
   `Spring.StiffnessMedium` to `Spring.StiffnessMediumLow`, and reduced motion still snaps. The user confirms it by eye at
   the gate; reverting is a one-line change.
7. **`LocalContentColor` defaults to `onBackground` in `RollaMusicPlayerTheme`** (Phase 2 review follow-up). Nothing
   above Home provides a content color, so every default-indication ripple was black, which is invisible in dark theme.
   This changes the meaning of every un-tinted `Text`/`Icon` rendered outside a `Surface`, so Task 1 includes a consumer
   audit.
8. **No art letterboxing fix.** The "letterboxed Now Playing art" carry-over is the embedded artwork itself. The 2026-10-09
   device check found the same bands in the Tracks thumbnails, and the code already uses `ContentScale.Crop`. It is
   closed with no change.
9. **The Now Playing background is full bleed.** It draws behind the status and navigation bars, as spec §8.4 says
   ("over the full screen").
10. **The EQ response curve composes only while a slider is being dragged.** It sits inside an `AnimatedVisibility`,
    with a fade, or no fade under reduced motion. That makes "visible only while dragging" (spec §8.5) testable through
    the node's existence.
11. **"Save as preset" stays conditional on the Custom state.** It shows only when no preset matches, as today. Saving a
    copy of a selected preset is pointless, and spec §8.5 doesn't say it must always show.

## Review Focus

These five failure modes are the most likely to bite a user, and no other task's tests cover them. Each line names the
task whose tests pin it.

1. **Opening the queue with nothing playing.** The sheet shows "Queue is empty" in `emptyState` style and does not crash.
   Task 4.
2. **The same song twice in one queue** (a playlist with duplicates). Each row is keyed by its window index, only the
   playing entry is marked current, and tapping either one jumps to that exact entry. Tasks 2 and 4.
3. **⋮ on a song that has left the library** (deleted or rescanned while playing). "Go to album" is disabled rather than
   navigating to album 0. "Edit tags" and "Go to artist" still work. Tasks 6 and 7.
4. **Now Playing at font scale 2.0 on a 640 dp tall screen.** The transport row stays reachable by scrolling, and nothing
   overlaps the seekbar. Task 7.
5. **Opening the queue from the mini-player and then navigating to a screen that hides the pill.** The sheet stays open
   and keeps working, because the app hosts it outside the pill's `AnimatedVisibility`. Task 5 states the structure;
   the gate device check exercises it.

---

### Task 1: Kit carry-overs (theme content color, `PillChip` long-press, `OneUiSeekBar`, background node, tokens)

**Owner:** m3-design-system-agent (`model: "opus"`)

**Files:**
- Modify:
  - `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Theme.kt`
  - `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Dimens.kt`
  - `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/PillChip.kt`
  - `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/NowPlayingBackground.kt`
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/OneUiSeekBar.kt`
- Test (create or extend), all under `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/`:
  - `theme/ThemeContentColorTest.kt`
  - `component/PillChipTest.kt` (extend the existing file if present)
  - `component/OneUiSeekBarTest.kt`
  - `component/NowPlayingBackgroundTest.kt`

**Interfaces:**
- **Produces:**
  - `RollaMusicPlayerTheme` provides `LocalContentColor` = `colorScheme.onBackground`.
  - `PillChip(label, selected, onClick, modifier, enabled, onLongClick: (() -> Unit)? = null)`.
  - `OneUiSeekBar(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null, contentDescription: String? = null)`, with `value` in 0f..1f.
  - `Modifier.nowPlayingBackground(gradient)`, now an equality-keyed `ModifierNodeElement`. The signature is unchanged.
  - New `RollaDimens` entries:
    - `miniPlayerArtTextGap`
    - `miniPlayerEnd`
    - `npArtGlyph`
    - `npTitleTopGap`
    - `npArtistTopGap`
    - `npTransportTopGap`
    - `npBottomGap`
    - `queueCurrentGlyph`

- [ ] **Step 1: Tokens.** Append the following to `RollaDimens`. Keep the existing entries exactly as they are.

```kotlin
    // Mini-player, not measured on the reference (Phase 7 calibrates).
    val miniPlayerArtTextGap = 12.dp
    val miniPlayerEnd = 4.dp

    // Now Playing vertical rhythm, derived from the measured glyph centres at 3.0 px/dp
    // (docs/design/oneui-measurements.md "Now Playing"); provisional until Phase 7.
    val npArtGlyph = 60.dp // placeholder note on the 168 dp artwork (spec §8.4)
    val npTitleTopGap = 25.dp // art bottom 298 → title line top ≈323
    val npArtistTopGap = 5.dp // title line bottom ≈351 → artist line top ≈356
    val npTransportTopGap = 10.dp // seek box bottom 613 → 64 dp transport box top 623
    val npBottomGap = 45.dp // transport box bottom 687 → navigation bar top 732

    // Queue sheet
    val queueCurrentGlyph = 16.dp // the small EqualizerBars glyph on the playing row; unmeasured
```

There is no test for this step: these are plain constants, and they are consumed and asserted in Tasks 5 and 7.

- [ ] **Step 2: Write the failing theme test** (`ThemeContentColorTest.kt`)

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeContentColorTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Mutation: delete the LocalContentColor provider in RollaMusicPlayerTheme → content is Color.Black → RED (dark).
    @Test
    fun contentColorDefaultsToOnBackgroundInBothThemes() {
        listOf(true, false).forEach { dark ->
            var content = Color.Unspecified
            var expected = Color.Magenta
            composeRule.setContent {
                RollaMusicPlayerTheme(darkTheme = dark, dynamicColor = false) {
                    content = LocalContentColor.current
                    expected = MaterialTheme.colorScheme.onBackground
                }
            }
            composeRule.waitForIdle()
            assertEquals("dark=$dark", expected, content)
        }
    }
}
```

`createComposeRule` allows only one `setContent` per test. If the loop throws on the second call, split the test into
`...InDarkTheme` and `...InLightTheme`, which is a mechanical fix, and keep the mutation. Check the
`RollaMusicPlayerTheme` parameter names (`dynamicColor`) against `Theme.kt` and adjust only the name.

- [ ] **Step 3: Confirm RED, then implement.** Inside `RollaMusicPlayerTheme`, wrap the `MaterialTheme`'s content:

```kotlin
MaterialTheme(colorScheme = colorScheme, typography = ..., shapes = ...) {
    // Nothing above Home provides a content color (no Surface/Scaffold), so M3's default black made every
    // default-indication ripple, and any un-tinted Text/Icon outside a Surface, invisible in dark theme.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
}
```

- [ ] **Step 4: Consumer audit (observer 0007b).** This changes what un-tinted content renders as.
  1. Run the audit grep:
     `grep -rnE "Text\(|Icon\(" --include=*.kt app feature core/ui | grep -v "color =\|tint ="`, and skim each hit that
     is not inside a `Surface`, `Scaffold`, `Card` or `ModalBottomSheet`. Those components provide their own content
     color, so they are unaffected.
  2. List each affected call site in the report and say whether it now renders as intended. White-on-black where black
     was invisible is the expected and desired change.
  3. If a site was relying on black deliberately, don't fix it here: report it.

- [ ] **Step 5: `PillChip` long-press: failing tests.** Add these to `PillChipTest.kt`, or create it with the same
  host the existing kit tests use:

```kotlin
    // Mutation: drop onLongClick from the combinedClickable → longClicks stays 0 → RED.
    @Test
    fun longPressCallsOnLongClick() {
        var longClicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PillChip(label = "Mine", selected = false, onClick = {}, onLongClick = { longClicks++ })
            }
        }
        composeRule.onNodeWithText("Mine").performTouchInput { longClick() }
        assertEquals(1, longClicks)
    }

    // Mutation: always pass onLongClick = {} to combinedClickable → an OnLongClick action appears → RED.
    @Test
    fun noLongClickActionWithoutOnLongClick() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) { PillChip(label = "Balanced", selected = true, onClick = {}) }
        }
        composeRule.onNodeWithText("Balanced").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }
```

- [ ] **Step 6: Implement.**
  - Add `onLongClick: (() -> Unit)? = null` as the last parameter.
  - Replace the chip's `selectable(...)`/`clickable(...)` with
    `combinedClickable(enabled = enabled, role = Role.RadioButton, onClick = onClick, onLongClick = onLongClick)`, and
    keep `Modifier.semantics { selected = selected }`. Keep the existing ripple clip order (`clip(CircleShape)` before
    the clickable).
  - The existing `PillChip` tests (role, selected state, disabled, footprint) must stay green unmodified.

- [ ] **Step 7: `OneUiSeekBar`: failing tests** (`OneUiSeekBarTest.kt`, `@GraphicsMode(NATIVE)`,
  `w360dp-h640dp`, using `createAndroidComposeRule<ComponentActivity>()`)

```kotlin
    // Mutation: drop the contentDescription semantics → RED (baseline-profile contract).
    @Test
    fun exposesProgressAndContentDescription() {
        setBar(value = 0.25f)
        composeRule.onNodeWithContentDescription("Playback position")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.25f, 0f..1f)))
    }

    // Mutation: call onValueChangeFinished from onValueChange too → finished > 1 → RED.
    @Test
    fun dragReportsValuesAndCommitsOnceOnRelease() {
        val values = mutableListOf<Float>()
        var finished = 0
        setBar(value = 0f, onValueChange = { values += it }, onFinished = { finished++ })
        composeRule.onNodeWithContentDescription("Playback position").performTouchInput {
            down(centerLeft); moveTo(center); moveTo(centerRight.copy(x = width * 0.75f)); up()
        }
        assertTrue(values.isNotEmpty())
        assertEquals(1, finished)
    }

    // Mutation: swap activeColor/inactiveColor in the track → the left pixel is the inactive color → RED.
    @Config(sdk = [30]) // view.draw renders the track reliably here; see OneUiTabRowTest for the precedent
    @Test
    fun activeTrackLeftOfThumbIsSeekTrackActive() {
        setBar(value = 0.5f)
        val bitmap = drawRoot()
        val bar = composeRule.onNodeWithContentDescription("Playback position").getBoundsInRoot()
        val y = with(composeRule.density) { ((bar.top + bar.bottom) / 2).roundToPx() }
        val leftX = with(composeRule.density) { (bar.left + (bar.right - bar.left) * 0.15f).roundToPx() }
        val rightX = with(composeRule.density) { (bar.left + (bar.right - bar.left) * 0.85f).roundToPx() }
        assertColorNear(seekTrackActive, bitmap.getPixel(leftX, y))
        assertColorNear(seekTrackInactiveOverBackground, bitmap.getPixel(rightX, y))
    }
```

  - `setBar` hosts the bar in `RollaMusicPlayerTheme(darkTheme = true)` inside a `Box(Modifier.fillMaxWidth()
    .background(MaterialTheme.colorScheme.background))`. It captures `seekTrackActive` and the inactive color composited
    over the background (`seekTrackInactive.compositeOver(background)`) from the theme.
  - `drawRoot()` draws `composeRule.activity.findViewById<View>(android.R.id.content)` into an `ARGB_8888` bitmap with
    `view.draw(Canvas(bitmap))` on the UI thread.
  - `assertColorNear` allows ±6 per channel, because the track is 3 dp and anti-aliased.

- [ ] **Step 8: Implement `OneUiSeekBar.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

/**
 * The Now Playing seekbar (spec §8.4): an [RollaDimens.npSeekThickness] track (seekTrackActive behind the thumb,
 * seekTrackInactive ahead of it) and a solid [RollaDimens.npSeekThumb] seekTrackActive thumb. It is an M3 Slider with
 * a custom track and thumb, so drag, keyboard and SetProgress semantics are the platform's own. [value] is a fraction
 * in 0..1. The caller owns drag-then-commit (see NowPlayingScreen). Occupies the M3 Slider's 48 dp touch height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        thumb = {
            Box(
                Modifier
                    .size(RollaDimens.npSeekThumb)
                    .background(colors.seekTrackActive, CircleShape),
            )
        },
        track = { state ->
            SliderTrackLine(
                state = state,
                thickness = RollaDimens.npSeekThickness,
                activeColor = colors.seekTrackActive,
                inactiveColor = colors.seekTrackInactive,
            )
        },
    )
}
```

  Check that `SliderTrackLine`'s actual parameter list in `SliderParts.kt` matches, and adapt only names.

- [ ] **Step 9: Background node: failing tests** (`NowPlayingBackgroundTest.kt`, NATIVE, `@Config(sdk = [30])`)

```kotlin
    // Mutation: make the element a plain class (identity equality) → RED. Equality is what keeps drawWithCache from
    // rebuilding its brushes on every caller recomposition (Phase 1 Task 4 minor).
    @Test
    fun modifiersWithEqualGradientsAreEqual() {
        val g = NowPlayingGradient(Color.Black, Color.DarkGray, Color.Red, Color.Blue)
        assertEquals(Modifier.nowPlayingBackground(g), Modifier.nowPlayingBackground(g.copy()))
    }

    // Mutation: delete the wash draw (or its DstIn fade) → the bottom corners are `middle`, not washStart/washEnd → RED.
    @Test
    fun drawsTopBaseAndBottomWash() {
        lateinit var g: NowPlayingGradient
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                g = MaterialTheme.colorScheme.nowPlayingGradient
                Box(Modifier.fillMaxSize().nowPlayingBackground(g))
            }
        }
        val bitmap = drawRoot()
        assertColorNear(g.top, bitmap.getPixel(bitmap.width / 2, 0))
        assertColorNear(g.washStart, bitmap.getPixel(0, bitmap.height - 1))
        assertColorNear(g.washEnd, bitmap.getPixel(bitmap.width - 1, bitmap.height - 1))
    }
```

  - Adapt the `NowPlayingGradient` constructor order to its real declaration in `Color.kt`; only the order changes.
  - Use a tolerance of ±6 per channel.
  - These tests replace the old `nowPlayingBackgroundLaysOutWithoutCrashing` test's purpose. Keep that test unless it
    becomes redundant, and say which you chose.

- [ ] **Step 10: Implement the node.** Keep the public function and the drawing math exactly as they are; only the
  carrier changes.

```kotlin
fun Modifier.nowPlayingBackground(gradient: NowPlayingGradient): Modifier = this then NowPlayingBackgroundElement(gradient)

private data class NowPlayingBackgroundElement(val gradient: NowPlayingGradient) :
    ModifierNodeElement<NowPlayingBackgroundNode>() {
    override fun create() = NowPlayingBackgroundNode(gradient)
    override fun update(node: NowPlayingBackgroundNode) { node.update(gradient) }
    override fun InspectorInfo.inspectableProperties() { name = "nowPlayingBackground"; properties["gradient"] = gradient }
}

private class NowPlayingBackgroundNode(private var gradient: NowPlayingGradient) : Modifier.Node(), DrawModifierNode {
    private var cachedSize = Size.Unspecified
    private var base: Brush? = null
    private var wash: Brush? = null
    private var fade: Brush? = null
    private val layerPaint = Paint()

    fun update(newGradient: NowPlayingGradient) {
        if (newGradient == gradient) return
        gradient = newGradient
        cachedSize = Size.Unspecified
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        if (size != cachedSize) rebuild(size)
        val washTop = size.height * WASH_START_FRACTION
        val washSize = Size(size.width, size.height - washTop)
        drawRect(base!!)
        drawContext.canvas.saveLayer(Rect(0f, washTop, size.width, size.height), layerPaint)
        drawRect(wash!!, topLeft = Offset(0f, washTop), size = washSize)
        drawRect(fade!!, topLeft = Offset(0f, washTop), size = washSize, blendMode = BlendMode.DstIn)
        drawContext.canvas.restore()
        drawContent()
    }

    private fun rebuild(size: Size) {
        val washTop = size.height * WASH_START_FRACTION
        base = Brush.verticalGradient(0f to gradient.top, WASH_START_FRACTION to gradient.middle, 1f to gradient.middle)
        wash = Brush.horizontalGradient(listOf(gradient.washStart, gradient.washEnd))
        fade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = washTop, endY = size.height)
        cachedSize = size
    }
}
```

  The old implementation never called `drawContent()` because it was `onDrawBehind`. The node must call it after the
  background, or the screen content disappears; the tests in Step 9 and Task 7 catch that. Avoid `!!` if detekt flags
  it: use `lateinit` brushes or local vals.

- [ ] **Step 11: GREEN.** Run `./gradlew :core:designsystem:testDebugUnitTest`, then apply each mutation above.
- [ ] **Step 12: Checkpoint (no commit).** Run `./gradlew spotlessApply :core:designsystem:detekt assembleDebug`, then
  `git status --short`. `assembleDebug` proves every consumer of `PillChip` and `nowPlayingBackground` still compiles.

---

### Task 2: `:core:media` queue, controller helpers and a real-ExoPlayer test

**Owner:** audio-engineer (`model: "opus"`)

**Files:**
- Create, under `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/`:
  - `Queue.kt`: `QueueItem`, `QueueState`, `playbackOrder`, `queueStateOf`, `Player.queueState()`
- Modify, under the same directory:
  - `PlaybackStateHolder.kt`: add `queue` and `setQueue`
  - `PlaybackController.kt`: add `queue`, `skipToQueueIndex`, `playAllInOrder`, `shuffleAll` and `playFrom`; queue
    `cycleRepeatMode`
  - `PlaybackService.kt`: publish the queue
- Modify: `core/media/build.gradle.kts`: add `id("rolla.android.robolectric")`
- Create: `core/media/src/test/resources/robolectric.properties` (`sdk=34`)
- Test, under `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/`:
  - `QueueTest.kt` (pure)
  - `PlaybackControllerTest.kt` (extend)
  - `QueueAndShuffleExoPlayerTest.kt` (Robolectric, real ExoPlayer)

**Interfaces:**
- **Produces:**
  - `data class QueueItem(val index: Int, val mediaId: String, val title: String, val artist: String,
    val artworkUri: String)`
  - `data class QueueState(val items: List<QueueItem> = emptyList(), val currentIndex: Int = -1)` with
    `QueueState.EMPTY`
  - `PlaybackController.queue: StateFlow<QueueState>`
  - `PlaybackController.skipToQueueIndex(index: Int)`
  - `PlaybackController.playAllInOrder(songs: List<Song>)`
  - `PlaybackController.shuffleAll(songs: List<Song>, random: Random = Random.Default)`
  - `PlaybackController.playFrom(songs: List<Song>, song: Song)`

- [ ] **Step 1: Write the failing pure tests** (`QueueTest.kt`)

```kotlin
package com.rolla.musicplayer.core.media

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueTest {

    private fun item(index: Int) = QueueItem(index, "id$index", "T$index", "A$index", "")

    // Mutation: start the walk at 0 instead of firstIndex → [0, 1, 2] → RED.
    @Test
    fun walksFromFirstIndexFollowingNext() {
        val shuffled = intArrayOf(2, 0, 1)
        val next = { i: Int -> shuffled.indexOf(i).let { if (it == shuffled.lastIndex) C.INDEX_UNSET else shuffled[it + 1] } }
        assertEquals(listOf(2, 0, 1), playbackOrder(count = 3, firstIndex = 2, nextIndex = next))
    }

    // Mutation: delete the `order.size < count` guard → the walk never ends → RED by timeout.
    @Test(timeout = 2_000)
    fun aCyclicOrderStopsAfterCountSteps() {
        assertEquals(listOf(0, 1, 0), playbackOrder(count = 3, firstIndex = 0, nextIndex = { 1 - it }))
    }

    @Test
    fun emptyTimelineGivesEmptyOrder() {
        assertEquals(emptyList<Int>(), playbackOrder(count = 0, firstIndex = C.INDEX_UNSET, nextIndex = { 0 }))
    }

    // Mutation: currentIndex = currentWindowIndex (a window index, not a list position) → 0 instead of 1 → RED.
    @Test
    fun currentIndexIsThePositionInPlaybackOrder() {
        val state = queueStateOf(order = listOf(2, 0, 1), currentWindowIndex = 0, item = ::item)
        assertEquals(listOf(2, 0, 1), state.items.map { it.index })
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun aCurrentWindowOutsideTheOrderGivesMinusOne() {
        assertEquals(-1, queueStateOf(listOf(0, 1), currentWindowIndex = 5, item = ::item).currentIndex)
        assertEquals(-1, QueueState.EMPTY.currentIndex)
    }
}
```

The `aCyclicOrderStopsAfterCountSteps` walk starts at 0 and follows `1 - it`: 0, 1, 0, … It must stop at 3 entries.

- [ ] **Step 2: Confirm RED, then create `Queue.kt`**

```kotlin
package com.rolla.musicplayer.core.media

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/** One queue entry (spec §11). [index] is the Media3 window index: pass it to PlaybackController.skipToQueueIndex. */
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String,
)

/**
 * The queue in playback order: shuffle order when shuffle is on (spec §11). [currentIndex] is the current item's
 * position in [items], not a window index, or -1 when nothing is current.
 */
data class QueueState(val items: List<QueueItem> = emptyList(), val currentIndex: Int = -1) {
    companion object {
        val EMPTY = QueueState()
    }
}

/**
 * Window indices in playback order, from [firstIndex] following [nextIndex] until it returns [C.INDEX_UNSET] or
 * leaves `0 until count`. Stops after [count] entries, so a malformed order can never loop forever.
 */
internal fun playbackOrder(count: Int, firstIndex: Int, nextIndex: (Int) -> Int): List<Int> {
    val order = ArrayList<Int>(count.coerceAtLeast(0))
    var index = firstIndex
    while (index != C.INDEX_UNSET && index in 0 until count && order.size < count) {
        order += index
        index = nextIndex(index)
    }
    return order
}

internal fun queueStateOf(order: List<Int>, currentWindowIndex: Int, item: (Int) -> QueueItem): QueueState =
    QueueState(items = order.map(item), currentIndex = order.indexOf(currentWindowIndex))

/** Reads this player's timeline as a [QueueState]. Call it on the player's application thread. */
internal fun Player.queueState(): QueueState {
    val timeline = currentTimeline
    if (timeline.isEmpty) return QueueState.EMPTY
    val shuffle = shuffleModeEnabled
    val order = playbackOrder(
        count = timeline.windowCount,
        firstIndex = timeline.getFirstWindowIndex(shuffle),
        nextIndex = { timeline.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, shuffle) },
    )
    val window = Timeline.Window()
    return queueStateOf(order, currentMediaItemIndex) { index ->
        val mediaItem = timeline.getWindow(index, window).mediaItem
        val metadata = mediaItem.mediaMetadata
        QueueItem(
            index = index,
            mediaId = mediaItem.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUri = metadata.artworkUri?.toString().orEmpty(),
        )
    }
}
```

- [ ] **Step 3: Holder and service.**
  - **`PlaybackStateHolder`:**
    `private val _queue = MutableStateFlow(QueueState.EMPTY); val queue: StateFlow<QueueState> = _queue.asStateFlow(); fun setQueue(queue: QueueState) { _queue.value = queue }`.
  - **`PlaybackService.playerListener`:** add `private fun publishQueue() = playbackStateHolder.setQueue(player.queueState())`, and call it:
    - in a new `override fun onTimelineChanged(timeline: Timeline, reason: Int)`;
    - at the end of the existing `onMediaItemTransition`;
    - at the end of the existing `onShuffleModeEnabledChanged`.
  - **Order:** keep `playerListener` registered before `shuffleEnforcer`. A reshuffle then republishes through
    `onTimelineChanged` (ruling 1).

- [ ] **Step 4: Controller: failing tests.** Extend `PlaybackControllerTest`, using the existing future-mocking
  helpers and the same `testSong` factory.

```kotlin
    // Mutation: drop the isEmpty guard → setShuffle/setMediaItems run for an empty list → RED.
    @Test
    fun `playAllInOrder and shuffleAll on an empty list do nothing`() {
        resolveFuture()
        controller.playAllInOrder(emptyList())
        controller.shuffleAll(emptyList())
        verify(exactly = 0) { mediaController.setMediaItems(any(), any(), any()) }
        verify(exactly = 0) { mediaController.shuffleModeEnabled = any() }
    }

    // Mutation: call playAll before setShuffle → verifyOrder fails → RED.
    @Test
    fun `playAllInOrder turns shuffle off before starting at index 0`() {
        resolveFuture()
        val songs = listOf(testSong("a"), testSong("b"))
        controller.playAllInOrder(songs)
        verifyOrder {
            mediaController.shuffleModeEnabled = false
            mediaController.setMediaItems(match { it.size == 2 }, 0, any())
        }
    }

    // Mutation: start at 0 instead of random.nextInt(size) → RED.
    @Test
    fun `shuffleAll turns shuffle on and starts at the random index`() {
        resolveFuture()
        val songs = (1..10).map { testSong("s$it") }
        val expectedStart = Random(42).nextInt(songs.size)
        controller.shuffleAll(songs, Random(42))
        verifyOrder {
            mediaController.shuffleModeEnabled = true
            mediaController.setMediaItems(any(), expectedStart, any())
        }
    }

    // Mutation: always playAll(songs, 0) → RED; drop the fallback → the missing song is never played → RED.
    @Test
    fun `playFrom starts the list at the tapped song or plays it alone when absent`() {
        resolveFuture()
        val songs = listOf(testSong("a"), testSong("b"), testSong("c"))
        controller.playFrom(songs, testSong("c"))
        verify { mediaController.setMediaItems(match { it.size == 3 }, 2, any()) }
        controller.playFrom(songs, testSong("zz"))
        verify { mediaController.setMediaItem(match { it.mediaId == "zz" }) }
    }

    // Mutation: revert cycleRepeatMode to `controller ?: return` → nothing queued → RED.
    @Test
    fun `cycleRepeatMode before the future resolves is queued`() {
        every { mediaController.repeatMode } returns Player.REPEAT_MODE_OFF
        controller.cycleRepeatMode()
        resolveFuture()
        queuedRunnable.captured.run()
        verify { mediaController.repeatMode = Player.REPEAT_MODE_ALL }
    }

    // Mutations: drop play() → RED; drop the range check → seek(9) is called → RED.
    @Test
    fun `skipToQueueIndex seeks and plays, and ignores an out-of-range index`() {
        resolveFuture()
        every { mediaController.mediaItemCount } returns 3
        controller.skipToQueueIndex(9)
        verify(exactly = 0) { mediaController.seekToDefaultPosition(any()) }
        controller.skipToQueueIndex(2)
        verifyOrder {
            mediaController.seekToDefaultPosition(2)
            mediaController.play()
        }
    }

    // Mutation: expose a fresh MutableStateFlow instead of the holder's → RED.
    @Test
    fun `queue mirrors the state holder`() {
        val state = QueueState(listOf(QueueItem(0, "a", "A", "B", "")), currentIndex = 0)
        playbackStateHolder.setQueue(state)
        assertEquals(state, controller.queue.value)
    }
```

- [ ] **Step 5: Implement the controller.**

```kotlin
    val queue: StateFlow<QueueState> = playbackStateHolder.queue

    /** Plays [songs] in order from the first one, shuffle off (spec §12 Play). A no-op for an empty list. */
    fun playAllInOrder(songs: List<Song>) {
        if (songs.isEmpty()) return
        setShuffle(ShuffleMode.OFF)
        playAll(songs, startIndex = 0)
    }

    /** Shuffle on, then [songs] from a random start (spec §12 Shuffle). A no-op for an empty list. */
    fun shuffleAll(songs: List<Song>, random: Random = Random.Default) {
        if (songs.isEmpty()) return
        setShuffle(ShuffleMode.ON)
        playAll(songs, startIndex = random.nextInt(songs.size))
    }

    /** Plays [songs] as the queue starting at [song]; plays [song] alone when it is no longer in [songs]. */
    fun playFrom(songs: List<Song>, song: Song) {
        val index = songs.indexOfFirst { it.id == song.id }
        if (index == -1) play(song) else playAll(songs, startIndex = index)
    }

    /** Jumps to the queue entry at window [index] and plays it (ruling 3). Out-of-range indices are ignored. */
    fun skipToQueueIndex(index: Int) = withConnectedController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    fun cycleRepeatMode() = withConnectedController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
```

  Import `kotlin.random.Random`. The class already carries `@Suppress("TooManyFunctions")` with its justification, so
  keep it.

- [ ] **Step 6: Real-ExoPlayer test** (`QueueAndShuffleExoPlayerTest.kt`). This also covers the Phase 2 Minor 1
  carry-over: there was no real-player test of the enforcer's wiring.

```kotlin
package com.rolla.musicplayer.core.media

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class QueueAndShuffleExoPlayerTest {

    private lateinit var player: ExoPlayer

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun tearDown() = player.release()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun items(n: Int) = (0 until n).map {
        MediaItem.Builder().setMediaId("id$it").setUri("content://media/external/audio/media/$it")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("T$it").setArtist("A$it").build()).build()
    }

    // Mutation: read titles from the wrong window (e.g. `index + 1`) → RED.
    @Test
    fun shuffleOffQueueIsTimelineOrderWithCurrentPosition() {
        player.setMediaItems(items(5), 2, C.TIME_UNSET)
        idle()
        val queue = player.queueState()
        assertEquals(listOf(0, 1, 2, 3, 4), queue.items.map { it.index })
        assertEquals(2, queue.currentIndex)
        assertEquals("T2", queue.items[2].title)
    }

    // Mutation: StartFirstShuffleEnforcer.onMediaItemTransition ignores PLAYLIST_CHANGED → start item not first → RED.
    @Test
    fun enforcedShuffleQueueStartsAtTheCurrentItemAndHoldsEveryItem() {
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        player.shuffleModeEnabled = true
        player.setMediaItems(items(6), 3, C.TIME_UNSET)
        idle()
        val queue = player.queueState()
        assertEquals(3, queue.items.first().index)
        assertEquals(0, queue.currentIndex)
        assertEquals((0 until 6).toSet(), queue.items.map { it.index }.toSet())
    }

    // Mutation: the enforcer reacts to every transition reason → the seek re-pins the order → RED (ruling 4).
    @Test
    fun skippingInsideAShuffledQueueKeepsTheOrder() {
        player.addListener(StartFirstShuffleEnforcer(player, Random(7)))
        player.shuffleModeEnabled = true
        player.setMediaItems(items(6), 0, C.TIME_UNSET)
        idle()
        val before = player.queueState().items.map { it.index }
        player.seekToDefaultPosition(before[3])
        idle()
        val after = player.queueState()
        assertEquals(before, after.items.map { it.index })
        assertEquals(3, after.currentIndex)
    }

    // Review Focus 2: duplicates keep their own window index and only the current one is current.
    @Test
    fun duplicateMediaIdsKeepDistinctWindowIndices() {
        val dup = items(1).first()
        player.setMediaItems(listOf(dup, items(2)[1], dup), 2, C.TIME_UNSET)
        idle()
        val queue = player.queueState()
        assertEquals(listOf(0, 1, 2), queue.items.map { it.index })
        assertEquals(2, queue.currentIndex)
    }
}
```

  - The test doesn't call `prepare()`. Media3's masking timeline exposes the placeholder windows synchronously after
    `setMediaItems`, and `seekToDefaultPosition` masks too.
  - If ExoPlayer can't be built under Robolectric here (a missing native audio sink, for example), report the exact
    exception with evidence before substituting anything. Allowed substitutes:
    - the same assertions against a `SimpleBasePlayer`-free fake;
    - a test-only `TestExoPlayerBuilder` if it is already on the classpath.

    Do not delete these assertions.

- [ ] **Step 7: GREEN, then each mutation.** Run `./gradlew :core:media:testDebugUnitTest`.
- [ ] **Step 8: Checkpoint (no commit).** Run `./gradlew spotlessApply :core:media:detekt :core:media:lintDebug
  assembleDebug`.

---

### Task 3: List screens use the shared helpers; a track tap plays the list (ruling 5)

**Owner:** viewmodel-architect (`model: "opus"`)

**Files:**
- Modify:
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/TracksViewModel.kt`
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/AlbumDetailViewModel.kt`
  - `feature/library/src/main/kotlin/com/rolla/musicplayer/feature/library/ArtistDetailViewModel.kt`
  - `feature/playlists/src/main/kotlin/com/rolla/musicplayer/feature/playlists/PlaylistDetailViewModel.kt`
- Test: the four matching `*ViewModelTest.kt` files. Update them and do not delete any test.

**Interfaces:**
- Consumes (Task 2): `playAllInOrder(songs)`, `shuffleAll(songs, random)`, `playFrom(songs, song)`.
- Produces: the same public ViewModel functions as today, with their bodies delegating to the helpers.

- [ ] **Step 1: Write the failing tests first.** For each ViewModel, rewrite its Play, Shuffle and song-tap tests to
  verify delegation. Example for `TracksViewModelTest`, in its existing MockK and Turbine style:

```kotlin
    // Mutation: TracksViewModel.play(song) keeps calling playbackController.play(song) → RED (ruling 5).
    @Test
    fun `tapping a track plays the whole list from that track`() = runTest {
        // songs flow emits [a, b, c] as in the existing tests; collect it so the WhileSubscribed value is live
        viewModel.play(songC)
        verify { playbackController.playFrom(listOf(songA, songB, songC), songC) }
    }

    // Mutation: call playAll(list, 0) directly (skipping setShuffle(OFF)) → RED.
    @Test
    fun `play all delegates to playAllInOrder`() = runTest {
        viewModel.playAll()
        verify { playbackController.playAllInOrder(listOf(songA, songB, songC)) }
    }

    // Mutation: pass Random.Default instead of the injected random → RED.
    @Test
    fun `shuffle all delegates with the view model's random`() = runTest {
        viewModel.random = Random(3)
        viewModel.shuffleAll()
        verify { playbackController.shuffleAll(listOf(songA, songB, songC), viewModel.random) }
    }
```

  - The old "empty library does nothing" ViewModel tests now verify that the helper is called with an empty list. The
    no-op itself is pinned in Task 2's controller test. Say so in the test's comment.
  - Album, Artist and PlaylistDetail get the same three rewrites, against their own names (`onPlayClick`,
    `onShuffleClick`, `onSongClick`, `playAll`, `shuffleAll`).

- [ ] **Step 2: Confirm RED. Implement.** Replace each body with one delegation:
  - `TracksViewModel.play(song)` → `playbackController.playFrom(songs.value, song)`
  - `playAll()` → `playbackController.playAllInOrder(songs.value)`
  - `shuffleAll()` → `playbackController.shuffleAll(songs.value, random)`

  The other three ViewModels follow the same pattern. Delete the now-unused `ShuffleMode` imports. Keep each `random`
  test seam.

- [ ] **Step 3: GREEN.** Run `./gradlew :feature:library:testDebugUnitTest :feature:playlists:testDebugUnitTest`,
  then apply each mutation.
- [ ] **Step 4: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:library:detekt
  :feature:playlists:detekt`.

---

### Task 4: Queue sheet (`QueueViewModel`, `QueueSheet`, `QueueSheetRoute`)

**Owner:** ui-builder (`model: "opus"`)

**Files:**
- Modify:
  - `feature/player/build.gradle.kts`: add `id("rolla.android.robolectric")`
  - `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/SongListItem.kt`: move the private `ArtworkThumbnail` into
    a new public file and keep its call site
- Create:
  - `feature/player/src/test/resources/robolectric.properties` (`sdk=34`)
  - `core/ui/src/main/kotlin/com/rolla/musicplayer/core/ui/ArtworkThumbnail.kt`: the same body, now public, so the
    queue rows reuse it
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/QueueViewModel.kt`
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/QueueSheet.kt`
- Test (create):
  - `feature/player/src/test/kotlin/com/rolla/musicplayer/feature/player/QueueViewModelTest.kt`
  - `feature/player/src/test/kotlin/com/rolla/musicplayer/feature/player/QueueSheetTest.kt`

**Interfaces:**
- Consumes (Task 2): `PlaybackController.queue`, `skipToQueueIndex`, `QueueState`, `QueueItem`.
- **Produces:**
  - `@Composable fun QueueSheetRoute(onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    viewModel: QueueViewModel = hiltViewModel())`
  - `@Composable fun QueueSheet(queue: QueueState, onItemClick: (QueueItem) -> Unit, onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier)`
  - `const val QUEUE_LIST_TAG = "queue_list"`
  - `@Composable fun ArtworkThumbnail(artworkUri: String, contentDescription: String?, modifier: Modifier = Modifier)`,
    in `:core:ui`

- [ ] **Step 1: ViewModel test**

```kotlin
class QueueViewModelTest {
    private val queueFlow = MutableStateFlow(QueueState.EMPTY)
    private val controller: PlaybackController = mockk(relaxed = true) { every { queue } returns queueFlow }

    // Mutation: pass item.index + 1 (or the list position) → RED.
    @Test
    fun skipToJumpsToTheItemsWindowIndex() {
        val vm = QueueViewModel(controller)
        vm.skipTo(QueueItem(index = 4, mediaId = "x", title = "", artist = "", artworkUri = ""))
        verify { controller.skipToQueueIndex(4) }
    }

    // Mutation: expose a stateIn copy that starts EMPTY instead of the controller's flow → RED.
    @Test
    fun queueIsTheControllersFlow() {
        val state = QueueState(listOf(QueueItem(0, "a", "A", "B", "")), 0)
        queueFlow.value = state
        assertEquals(state, QueueViewModel(controller).queue.value)
    }
}
```

- [ ] **Step 2: Implement `QueueViewModel`**

```kotlin
@HiltViewModel
class QueueViewModel @Inject constructor(private val playbackController: PlaybackController) : ViewModel() {
    val queue: StateFlow<QueueState> = playbackController.queue

    init {
        playbackController.connect()
    }

    fun skipTo(item: QueueItem) = playbackController.skipToQueueIndex(item.index)
}
```

- [ ] **Step 3: Sheet tests** (`QueueSheetTest.kt`, `w360dp-h640dp`, `@GraphicsMode(NATIVE)`)

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QueueSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun queue(n: Int, current: Int, dupOf: Int? = null) = QueueState(
        items = (0 until n).map { QueueItem(it, if (dupOf != null && it == n - 1) "id$dupOf" else "id$it", "T$it", "A$it", "") },
        currentIndex = current,
    )

    private fun show(state: QueueState, onItem: (QueueItem) -> Unit = {}, onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) { QueueSheet(state, onItemClick = onItem, onDismissRequest = onDismiss) }
        }
    }

    // Mutation: mark index 0 as current regardless of currentIndex → T1 not selected → RED.
    @Test
    fun onlyTheCurrentRowIsSelected() {
        show(queue(3, current = 1))
        composeRule.onNodeWithText("Queue").assertIsDisplayed()
        composeRule.onNodeWithText("T1").assertIsSelected()
        composeRule.onNodeWithText("T0").assertIsNotSelected()
    }

    // Mutation: call onDismissRequest after onItemClick → dismissals == 1 → RED (spec §11: keeps the sheet open).
    @Test
    fun tappingARowReportsItAndKeepsTheSheetOpen() {
        var tapped: QueueItem? = null
        var dismissals = 0
        show(queue(3, current = 0), onItem = { tapped = it }, onDismiss = { dismissals++ })
        composeRule.onNodeWithText("T2").performClick()
        assertEquals(2, tapped?.index)
        assertEquals(0, dismissals)
    }

    // Mutation: delete the scroll-to-current effect → T45 is never composed → RED.
    @Test
    fun opensScrolledToTheCurrentItem() {
        show(queue(60, current = 45))
        composeRule.onNodeWithText("T45").assertIsDisplayed()
    }

    // Review Focus 1. Mutation: drop the empty branch (empty LazyColumn) → the text is missing → RED.
    @Test
    fun emptyQueueShowsTheEmptyState() {
        show(QueueState.EMPTY)
        composeRule.onNodeWithText("Queue is empty").assertIsDisplayed()
    }

    // Review Focus 2. Mutation: key rows by mediaId → duplicate key crash → RED; or select by mediaId → two selected.
    @Test
    fun duplicateSongsAreSeparateRowsWithOneCurrent() {
        show(queue(3, current = 2, dupOf = 0))
        composeRule.onNodeWithText("T2").assertIsSelected()
        composeRule.onNodeWithText("T0").assertIsNotSelected()
    }
}
```

  `ModalBottomSheet` renders in its own window, and compose-test finds nodes across roots. If the sheet's open
  animation never settles under the PAUSED looper, add a method-level `@LooperMode(LEGACY)` and report it.

- [ ] **Step 4: Implement `QueueSheet.kt`**

```kotlin
const val QUEUE_LIST_TAG = "queue_list"

@Composable
fun QueueSheetRoute(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val onItemClick = remember(viewModel) { viewModel::skipTo }
    QueueSheet(queue = queue, onItemClick = onItemClick, onDismissRequest = onDismissRequest, modifier = modifier)
}

/** The queue (spec §11, §8.7): view plus tap-to-jump. Tapping a row plays it and keeps the sheet open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: QueueState,
    onItemClick: (QueueItem) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeTop = MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = largeTop,
        modifier = modifier,
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.sectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = RollaDimens.listThumbStart).semantics { heading() },
        )
        if (queue.items.isEmpty()) {
            Text(
                text = "Queue is empty",
                style = MaterialTheme.typography.emptyState,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(RollaDimens.screenEdge),
            )
        } else {
            QueueList(queue = queue, onItemClick = onItemClick)
        }
    }
}

@Composable
private fun QueueList(queue: QueueState, onItemClick: (QueueItem) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (queue.currentIndex >= 0) listState.scrollToItem(queue.currentIndex)
    }
    LazyColumn(state = listState, modifier = Modifier.testTag(QUEUE_LIST_TAG)) {
        itemsIndexed(queue.items, key = { _, item -> item.index }) { position, item ->
            QueueRow(item = item, isCurrent = position == queue.currentIndex, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun QueueRow(item: QueueItem, isCurrent: Boolean, onClick: () -> Unit) {
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.accentText else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.listRowHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { selected = isCurrent }
            .padding(start = RollaDimens.listThumbStart, end = RollaDimens.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkThumbnail(artworkUri = item.artworkUri, contentDescription = null)
        Spacer(Modifier.width(RollaDimens.listThumbTextGap))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.songTitle, color = titleColor, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(item.artist, style = MaterialTheme.typography.artistName,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isCurrent) {
            Icon(RollaIcons.EqualizerBars, contentDescription = null, tint = MaterialTheme.colorScheme.accentText,
                modifier = Modifier.size(RollaDimens.queueCurrentGlyph))
        }
    }
}
```

  On the `surfaceContainerHigh` sheet, `accentText` measures 4.70:1 (spec §5.1).

- [ ] **Step 5: GREEN, then each mutation.** Run `./gradlew :feature:player:testDebugUnitTest
  :core:ui:testDebugUnitTest`. The existing `SongListItem` tests must stay green after the thumbnail moves.
- [ ] **Step 6: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:player:detekt :core:ui:detekt`.

---

### Task 5: Mini-player restyle and app wiring

**Owner:** ui-builder (`model: "opus"`)

**Files:**
- Modify:
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/MiniPlayer.kt`
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/NowPlayingTransitionKey.kt`: delete
    `isReducedMotion()`; the artwork spring changes (ruling 6)
  - `app/src/main/kotlin/com/rolla/musicplayer/MainActivity.kt` (`RollaNavHost`)
  - `app/src/test/kotlin/com/rolla/musicplayer/navigation/MiniPlayerHostTest.kt`: derive the footprint from tokens
  - `feature/player/src/androidTest/kotlin/com/rolla/musicplayer/feature/player/MiniPlayerSmokeTest.kt`: new
    parameters
- Test (create): `feature/player/src/test/kotlin/com/rolla/musicplayer/feature/player/MiniPlayerTest.kt`

**Interfaces:**
- Consumes: `QueueSheetRoute` (Task 4) and the Task 1 tokens.
- **Produces:**

```kotlin
@Composable
fun MiniPlayerRoute(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBodyClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
)
```

  This order fixes carry-over L10: the required parameters and lambdas come first, then `modifier`, then the defaulted
  `viewModel`. `MiniPlayer(...)` keeps its parameters, `onQueue` included.
- `internal const val MINI_PLAYER_ART_TAG = "mini_player_art"`

- [ ] **Step 1: Failing tests** (`MiniPlayerTest.kt`, `w360dp-h640dp`, NATIVE). The host is the same as
  `MiniPlayerSmokeTest`'s: `SharedTransitionLayout` and `AnimatedContent(targetState = Unit)` around the production
  `MiniPlayer`.

```kotlin
    // Mutation: bring back the 4 dp vertical padding or the old 64 dp min height → RED.
    @Test
    fun pillIsMiniPlayerHeightTallWithSideMargins() {
        setMiniPlayer()
        val body = composeRule.onNodeWithContentDescription(OPEN_PLAYER, substring = true).getBoundsInRoot()
        body.height.assertIsEqualTo(RollaDimens.miniPlayerHeight, "pill height", tolerance = 0.5.dp)
        body.left.assertIsEqualTo(RollaDimens.miniPlayerSideMargin, "pill start", tolerance = 0.5.dp)
    }

    // Mutation: keep the old 48 dp artwork or 8 dp start padding → RED.
    @Test
    fun artIsA38DpCircleAtTheMeasuredStart() {
        setMiniPlayer()
        val art = composeRule.onNodeWithTag(MINI_PLAYER_ART_TAG, useUnmergedTree = true).getBoundsInRoot()
        art.width.assertIsEqualTo(RollaDimens.miniPlayerArt, "art size", tolerance = 0.5.dp)
        art.left.assertIsEqualTo(
            RollaDimens.miniPlayerSideMargin + RollaDimens.miniPlayerArtStart, "art start", tolerance = 0.5.dp,
        )
    }

    // Mutation: leave the Queue button's onClick as {} → RED.
    @Test
    fun queueButtonCallsOnQueue() {
        var queued = 0
        setMiniPlayer(onQueue = { queued++ })
        composeRule.onNodeWithContentDescription("Queue").performClick()
        assertEquals(1, queued)
    }

    // Mutation: drop mergeDescendants or change the wording → RED (baseline-profile contract).
    @Test
    fun bodyKeepsTheOpenPlayerDescription() {
        setMiniPlayer()
        composeRule.onNodeWithContentDescription("Now playing: Song title by Artist. Open player.").assertExists()
    }
```

- [ ] **Step 2: Restyle `MiniPlayer.kt`.** Apply these changes in order.
  1. **Constants.** Delete every private `dp` constant (`PillHeight` through `IconSize`), along with the material-icons
     imports.
  2. **Container.** The modifier chain becomes:
     `fillMaxWidth().padding(horizontal = RollaDimens.miniPlayerSideMargin).heightIn(min = RollaDimens.miniPlayerHeight)
     .background(miniPlayerContainer, CircleShape).clip(CircleShape).clickable(onClick = onBodyClick)
     .semantics(mergeDescendants = true) { ... }.padding(start = RollaDimens.miniPlayerArtStart, end = RollaDimens.miniPlayerEnd)`.
     There is no vertical padding: the measured pill sits directly on the navigation bar.
  3. **Art.**
     - Size and tag: `Modifier.size(RollaDimens.miniPlayerArt).testTag(MINI_PLAYER_ART_TAG)`.
     - Background: `miniPlayerArtPlaceholder`, behind the image.
     - Placeholder glyph: `RollaIcons.MusicNote`, tinted `miniPlayerArtGlyph`, at `RollaDimens.placeholderGlyph`, shown
       only when `artworkUri` is empty.
     - Keep the `sharedElement` block and `ContentScale.Crop` as they are.
  4. **Gap.** Use `Spacer(Modifier.width(RollaDimens.miniPlayerArtTextGap))`.
  5. **Text.**
     - Title: `miniPlayerTitle`, `onSurface`, `maxLines = 1`, with
       `Modifier.then(if (reducedMotion) Modifier else Modifier.basicMarquee())`. Add `@OptIn(ExperimentalFoundationApi::class)`
       if 1.7.3 still marks `basicMarquee` experimental.
     - Subtitle: `miniPlayerSubtitle`, `onSurface` (white, spec §8.3), ellipsized.
  6. **Transport.** Four `OneUiIconButton`s:
     - `RollaIcons.SkipPrevious` "Previous track"
     - `RollaIcons.Play`/`RollaIcons.Pause` "Play"/"Pause"
     - `RollaIcons.SkipNext` "Next track"
     - `RollaIcons.Queue` "Queue"

     Use the default 24 dp `iconSize` and the 48 dp target.
  7. **Route.** Rewrite `MiniPlayerRoute` to the Produces signature, and pass `onQueue = onQueueClick`. Delete
     `val onQueue = remember { {} }`.
  8. **Reduced motion.** Call `rememberReducedMotion()` from `:core:designsystem` everywhere this module called
     `isReducedMotion()`, then delete `isReducedMotion()` from `NowPlayingTransitionKey.kt`. Sweep the module with
     `grep -rn isReducedMotion feature/player`; the grep must come back empty.
  9. **Artwork spring (ruling 6).** In `artworkBoundsTransform`, change `Spring.StiffnessMedium` to
     `Spring.StiffnessMediumLow` and keep the snap branch.

- [ ] **Step 3: App wiring (`RollaNavHost`).**
  1. Add `var showQueue by rememberSaveable { mutableStateOf(false) }`.
  2. In the `pill` slot, call `MiniPlayerRoute(sharedTransitionScope = ..., animatedVisibilityScope = this,
     onBodyClick = { navController.navigate(NowPlaying) { launchSingleTop = true } }, onQueueClick = { showQueue = true },
     modifier = measure, viewModel = miniPlayerViewModel)`.
  3. Inside `SharedTransitionLayout`, after the `MiniPlayerHost(...) { ... }` call and outside the pill's
     `AnimatedVisibility` (Review Focus 5), add `if (showQueue) QueueSheetRoute(onDismissRequest = { showQueue = false })`.
     Add a comment saying why it lives there: hiding the pill, for example by opening Now Playing, would otherwise
     dispose an open sheet.
- [ ] **Step 4: Footprint from tokens.**
  - In `MiniPlayerHostTest`, replace `val REAL_PILL_FOOTPRINT = 72.dp` with
    `val REAL_PILL_FOOTPRINT get() = RollaDimens.miniPlayerHeight` and a comment: "the pill has no vertical margin
    (measured: it sits on the nav bar)".
  - Mutation: re-add `vertical = 4.dp` padding in `MiniPlayer` → RED.
- [ ] **Step 5: Smoke test.** Update `MiniPlayerSmokeTest` for the parameter changes only, and keep its assertions.
- [ ] **Step 6: GREEN, then each mutation.** Run `./gradlew :feature:player:testDebugUnitTest :app:testDebugUnitTest
  :feature:player:assembleDebugAndroidTest`.
- [ ] **Step 7: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:player:detekt :app:detekt`.

---

### Task 6: `PlayerViewModel`: volume seam, ⋮ targets and shuffle toggle

**Owner:** viewmodel-architect (`model: "opus"`)

**Files:**
- Create:
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/SystemVolumePanel.kt`: interface,
    AudioManager implementation and Hilt module
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/NowPlayingOverflowTargets.kt`
- Modify:
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/PlayerViewModel.kt`
  - `feature/player/src/test/kotlin/com/rolla/musicplayer/feature/player/PlayerViewModelTest.kt`: extend it, and update
    its constructor call

**Interfaces:**
- Consumes: `SongRepository.observeSong(songId: String): Flow<Song?>`.
- **Produces:**
  - `interface SystemVolumePanel { fun show() }`
  - `@Immutable data class NowPlayingOverflowTargets(val songId: Long? = null, val albumId: Long? = null,
    val artistName: String? = null)`
  - `PlayerViewModel.overflowTargets: StateFlow<NowPlayingOverflowTargets>`
  - `PlayerViewModel.showVolumePanel()`
  - `PlayerViewModel.toggleShuffle()`

- [ ] **Step 1: Failing tests.** Add these to `PlayerViewModelTest`, using its existing fakes and mocks. The
  constructor gains `volumePanel`, so pass a `FakeVolumePanel` that counts `show()` calls.

```kotlin
    // Mutation: showVolumePanel() does nothing → RED.
    @Test
    fun `volume shows the system panel through the seam`() {
        viewModel.showVolumePanel()
        assertEquals(1, fakeVolumePanel.shows)
    }

    // Mutation: albumId taken from the playback Song (always 0L) instead of the library song → RED.
    @Test
    fun `overflow targets resolve the album from the library song`() = runTest {
        currentSongFlow.value = playbackSong(id = "42", artist = "Artist") // albumId 0L, as PlaybackService builds it
        every { songRepository.observeSong("42") } returns flowOf(librarySong(id = "42", albumId = 7L, artist = "Artist"))
        viewModel.overflowTargets.test {
            assertEquals(NowPlayingOverflowTargets(songId = 42L, albumId = 7L, artistName = "Artist"), expectMostRecentItem())
        }
    }

    // Review Focus 3. Mutation: fall back to the playback Song's albumId 0L → albumId 0 instead of null → RED.
    @Test
    fun `a song missing from the library disables only go to album`() = runTest {
        currentSongFlow.value = playbackSong(id = "42", artist = "Artist")
        every { songRepository.observeSong("42") } returns flowOf(null)
        viewModel.overflowTargets.test {
            assertEquals(NowPlayingOverflowTargets(songId = 42L, albumId = null, artistName = "Artist"), expectMostRecentItem())
        }
    }

    // Mutation: treat a blank artist as valid → artistName "" → RED.
    @Test
    fun `nothing playing gives no targets, and a blank artist gives no artist target`() = runTest {
        viewModel.overflowTargets.test {
            assertEquals(NowPlayingOverflowTargets(), awaitItem())
            currentSongFlow.value = playbackSong(id = "x", artist = " ")
            every { songRepository.observeSong("x") } returns flowOf(null)
            assertEquals(NowPlayingOverflowTargets(songId = null, albumId = null, artistName = null), expectMostRecentItem())
        }
    }

    // Mutation: always setShuffle(ON) → RED on the second call.
    @Test
    fun `toggleShuffle flips the current mode`() {
        shuffleFlow.value = ShuffleMode.OFF
        viewModel.toggleShuffle()
        verify { playbackController.setShuffle(ShuffleMode.ON) }
        shuffleFlow.value = ShuffleMode.ON
        viewModel.toggleShuffle()
        verify { playbackController.setShuffle(ShuffleMode.OFF) }
    }
```

  Name the existing flows and factories in the test file (`currentSongFlow`, `shuffleFlow`, `playbackSong`,
  `librarySong`) after whatever the file actually uses. Adapt only the names.

- [ ] **Step 2: Implement.**

```kotlin
// SystemVolumePanel.kt
/** Shows the system volume panel (spec §12 Now Playing Volume). A seam, so the ViewModel test needs no AudioManager. */
interface SystemVolumePanel {
    fun show()
}

internal class AudioManagerVolumePanel @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemVolumePanel {
    override fun show() {
        context.getSystemService(AudioManager::class.java)
            ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SystemVolumePanelModule {
    @Binds
    abstract fun bindSystemVolumePanel(impl: AudioManagerVolumePanel): SystemVolumePanel
}
```

```kotlin
// NowPlayingOverflowTargets.kt
/**
 * Where the Now Playing ⋮ items lead (spec §12). A null target disables its item. The album id comes from the
 * library row, because PlaybackService rebuilds the current Song from MediaItem metadata with albumId = 0.
 */
@Immutable
data class NowPlayingOverflowTargets(
    val songId: Long? = null,
    val albumId: Long? = null,
    val artistName: String? = null,
)
```

```kotlin
// PlayerViewModel additions (constructor gains `private val volumePanel: SystemVolumePanel`)
    @OptIn(ExperimentalCoroutinesApi::class)
    val overflowTargets: StateFlow<NowPlayingOverflowTargets> = playbackController.currentSong
        .flatMapLatest { song ->
            if (song == null) {
                flowOf(NowPlayingOverflowTargets())
            } else {
                songRepository.observeSong(song.id).map { librarySong ->
                    NowPlayingOverflowTargets(
                        songId = song.id.toLongOrNull(),
                        albumId = librarySong?.albumId,
                        artistName = song.artist.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), NowPlayingOverflowTargets())

    fun showVolumePanel() = volumePanel.show()

    fun toggleShuffle() =
        setShuffle(if (shuffleMode.value == ShuffleMode.ON) ShuffleMode.OFF else ShuffleMode.ON)
```

  In the blank-artist test, the expected `songId` is null because `"x".toLongOrNull()` is null. That is intended: Song
  ids are MediaStore numeric strings, and a non-numeric id can't open the tag editor.

- [ ] **Step 3: GREEN, then each mutation.** Run `./gradlew :feature:player:testDebugUnitTest`.
- [ ] **Step 4: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:player:detekt assembleDebug`.
  `assembleDebug` proves Hilt can build the new binding.

---

### Task 7: Now Playing restyle and app wiring

**Owner:** ui-builder (`model: "opus"`)

**Files:**
- Modify:
  - `feature/player/src/main/kotlin/com/rolla/musicplayer/feature/player/NowPlayingScreen.kt`
  - `app/src/main/kotlin/com/rolla/musicplayer/MainActivity.kt` (`composable<NowPlaying>`)
  - `feature/player/src/androidTest/kotlin/com/rolla/musicplayer/feature/player/NowPlayingScreenSmokeTest.kt`: new
    parameters only
- Test (create): `feature/player/src/test/kotlin/com/rolla/musicplayer/feature/player/NowPlayingScreenTest.kt`

**Interfaces:**
- Consumes:
  - Task 1: `OneUiSeekBar`, `nowPlayingBackground`, the `np*` tokens
  - Task 4: `QueueSheetRoute`
  - Task 6: `overflowTargets`, `showVolumePanel`, `toggleShuffle`
- **Produces:**

```kotlin
@Suppress("LongParameterList") // six navigation callbacks, the two transition scopes, modifier and viewModel
@Composable
fun NowPlayingRoute(
    onNavigateUp: () -> Unit,
    onEqualizerClick: () -> Unit,
    onEditTagsClick: (songId: Long) -> Unit,
    onAlbumClick: (albumId: Long) -> Unit,
    onArtistClick: (artistName: String) -> Unit,
    onSettingsClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
)
```

  `NowPlayingScreen` stays stateless. It gains these parameters:
  - `overflowTargets: NowPlayingOverflowTargets`
  - `onVolumeClick: () -> Unit`
  - `onQueueClick: () -> Unit`
  - `onEditTagsClick: (Long) -> Unit`
  - `onAlbumClick: (Long) -> Unit`
  - `onArtistClick: (String) -> Unit`
  - `onSettingsClick: () -> Unit`

  The test tags are `internal const val NOW_PLAYING_ARTWORK_TAG = "now_playing_artwork"` and
  `internal const val NOW_PLAYING_TRANSPORT_TAG = "now_playing_transport"`.

- [ ] **Step 1: Failing tests** (`NowPlayingScreenTest.kt`, `w360dp-h640dp`, NATIVE). The host mints an
  `AnimatedContentScope` with `SharedTransitionLayout { AnimatedContent(Unit) { NowPlayingScreen(...) } }`, as the
  smoke test does, and calls the production `NowPlayingScreen`.

```kotlin
    // Mutations: swap the album/artist callbacks → RED; wire Settings to onEqualizerClick → RED.
    @Test
    fun overflowItemsOpenTheirTargets() {
        val calls = mutableListOf<String>()
        setScreen(
            targets = NowPlayingOverflowTargets(songId = 42L, albumId = 7L, artistName = "Artist"),
            onEditTags = { calls += "tags:$it" }, onAlbum = { calls += "album:$it" },
            onArtist = { calls += "artist:$it" }, onSettings = { calls += "settings" },
        )
        listOf("Edit tags", "Go to album", "Go to artist", "Settings").forEach { item ->
            composeRule.onNodeWithContentDescription("More options").performClick()
            composeRule.onNodeWithText(item).performClick()
        }
        assertEquals(listOf("tags:42", "album:7", "artist:Artist", "settings"), calls)
    }

    // Review Focus 3. Mutation: ignore a null target (always enabled) → RED.
    @Test
    fun overflowItemsWithoutATargetAreDisabled() {
        setScreen(targets = NowPlayingOverflowTargets(songId = 42L, albumId = null, artistName = "Artist"))
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Go to album").assertIsNotEnabled()
        composeRule.onNodeWithText("Edit tags").assertIsEnabled()
    }

    // Mutations: leave Volume or Queue as onClick = {} → RED (spec §12).
    @Test
    fun volumeAndQueueAreWired() {
        var volume = 0
        var queue = 0
        setScreen(onVolume = { volume++ }, onQueue = { queue++ })
        composeRule.onNodeWithContentDescription("Volume").performClick()
        composeRule.onNodeWithContentDescription("Queue").performClick()
        assertEquals(1, volume)
        assertEquals(1, queue)
    }

    // Mutation: put the state into contentDescription ("Shuffle on") instead of stateDescription → RED.
    @Test
    fun shuffleAndRepeatExposeStateDescriptions() {
        setScreen(shuffle = ShuffleMode.ON, repeat = RepeatMode.ONE)
        composeRule.onNodeWithContentDescription("Shuffle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Shuffle on"))
        composeRule.onNodeWithContentDescription("Repeat")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Repeat one"))
    }

    // Mutation: keep the old fillMaxWidth().aspectRatio(1f) artwork → RED.
    @Test
    fun artworkIsNpArtSizeAndCentered() {
        setScreen()
        val art = composeRule.onNodeWithTag(NOW_PLAYING_ARTWORK_TAG, useUnmergedTree = true).getBoundsInRoot()
        art.width.assertIsEqualTo(RollaDimens.npArtSize, "art width", tolerance = 0.5.dp)
        ((art.left + art.right) / 2).assertIsEqualTo(180.dp, "art centre x", tolerance = 0.5.dp)
    }

    // Mutation: keep the 72 dp primary circle (or a 48 dp IconButton) → RED.
    @Test
    fun playIsABareGlyphWithA64DpTarget() {
        setScreen()
        val play = composeRule.onNodeWithContentDescription("Play").getBoundsInRoot()
        (play.right - play.left).assertIsEqualTo(RollaDimens.npPlayTouchTarget, "play target", tolerance = 0.5.dp)
    }

    // Mutation: rename the seekbar description → RED (BaselineProfileGenerator waits on it).
    @Test
    fun seekbarKeepsItsContentDescription() {
        setScreen()
        composeRule.onNodeWithContentDescription("Playback position").assertExists()
    }

    // Review Focus 4. Mutation: drop verticalScroll from the content column → performScrollTo fails → RED.
    @Test
    fun transportRowIsReachableAtFontScale2() {
        setScreen(fontScale = 2f)
        composeRule.onNodeWithTag(NOW_PLAYING_TRANSPORT_TAG).performScrollTo().assertIsDisplayed()
    }
```

  `setScreen` wraps the content in `CompositionLocalProvider(LocalDensity provides Density(density, fontScale))`
  for the font-scale case, and passes `MutableStateFlow` position and duration flows.

- [ ] **Step 2: Restyle `NowPlayingScreen.kt`.** Apply these changes in order.
  1. **Root.** `Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets.navigationBars,
     modifier = modifier.nowPlayingBackground(MaterialTheme.colorScheme.nowPlayingGradient))`. The background is full
     bleed (ruling 9).
  2. **Top bar.** Replace `TopAppBar` with a `Row` that has `windowInsetsPadding(WindowInsets.statusBars)`,
     `heightIn(min = RollaDimens.headerHeight)` and `padding(horizontal = RollaDimens.topBarEdge)`.
     - Leading: `OneUiIconButton(RollaIcons.ChevronDown, "Close player", onNavigateUp)`.
     - `Spacer(Modifier.weight(1f))`.
     - `OneUiIconButton(RollaIcons.Volume, "Volume", onVolumeClick)`.
     - `OneUiIconButton(RollaIcons.EqualizerBars, "Equaliser", onEqualizerClick)`.
     - An overflow `Box`: `OneUiIconButton(RollaIcons.More, "More options") { expanded = true }`, plus a
       `DropdownMenu(containerColor = surfaceContainerHigh, shape = MaterialTheme.shapes.medium)` with four
       `DropdownMenuItem`s whose text uses `songTitle`:
       - "Edit tags", enabled when `songId != null`
       - "Go to album", enabled when `albumId != null`
       - "Go to artist", enabled when `artistName != null`
       - "Settings", always enabled

       Each item closes the menu and then calls its callback with the target.
  3. **Content column.**
     - Container: `Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
       .padding(horizontal = RollaDimens.npSeekInset), horizontalAlignment = CenterHorizontally)`.
     - Top spacer: `(RollaDimens.npArtTop - statusBarTop - RollaDimens.headerHeight).coerceAtLeast(0.dp)`, where
       `statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()`, so the artwork lands
       `npArtTop` below the screen top.
  4. **Artwork.**
     - Size: `Modifier.size(RollaDimens.npArtSize)`, tagged `NOW_PLAYING_ARTWORK_TAG`, with the same `sharedElement(...)`.
     - Shape: `.clip(MaterialTheme.shapes.extraLarge)`.
     - Placeholder: `ArtworkPlaceholder(large = true, shape = extraLarge, glyphSize = RollaDimens.npArtGlyph)` under
       the `AsyncImage`. Delete the 80 dp Material note.
  5. **Text.**
     - Before the title: `Spacer(Modifier.height(RollaDimens.npTitleTopGap))`.
     - Title: `nowPlayingTitle`, `onSurface`, one line, centered, `basicMarquee()` unless reduced motion.
     - Then `Spacer(Modifier.height(RollaDimens.npArtistTopGap))`.
     - Artist: `nowPlayingArtist`, `onSurface`, one line, centered.
     - Keep the metadata alpha fade.
  6. **Spacing.** `Spacer(Modifier.weight(1f))` pushes the controls to the bottom. Inside `verticalScroll`, `weight`
     is invalid, so either wrap the column content in `Column(Modifier.heightIn(min = maxHeight))` from a
     `BoxWithConstraints`, or put the spacer in a `Column` with `Modifier.height(IntrinsicSize.Max)`. Report which you
     used. The font-scale test must still scroll.
  7. **Action row.** `Row(Modifier.fillMaxWidth(), horizontalArrangement = SpaceBetween)` containing:
     - `OneUiIconButton(RollaIcons.Queue, "Queue", onQueueClick, iconSize = RollaDimens.npActionGlyph)`
     - the Heart, `HeartFilled` when favourite, tinted `onSurface` in both states (spec §8.4; never `primary`), with
       the existing "Add to favourites"/"Remove from favourites" descriptions
     - `OneUiIconButton(RollaIcons.Add, "Add to playlist", onAddToPlaylist, iconSize = npActionGlyph)`
  8. **Seekbar.** A `Box(Modifier.fillMaxWidth().height(RollaDimens.minTouchTarget))` containing:
     - `OneUiSeekBar(value = fraction, onValueChange = ..., onValueChangeFinished = ...,
       contentDescription = "Playback position")`, keeping the drag-then-commit logic and the position ticks scoped to
       this composable;
     - the time-labels `Row`, aligned `BottomCenter`, using `metadata` in `onSurface`.
  9. **Transport row.**
     - Before it: `Spacer(Modifier.height(RollaDimens.npTransportTopGap))`.
     - Layout: `Row(Modifier.fillMaxWidth().testTag(NOW_PLAYING_TRANSPORT_TAG), horizontalArrangement = SpaceEvenly)`.
     - **Shuffle:** `RollaIcons.Shuffle` with `contentDescription = "Shuffle"`. Its `semantics { stateDescription }` is
       "Shuffle on" or "Shuffle off". Tint `accentText` when on and `onSurface` when off.
     - **Previous:** `SkipPrevious`.
     - **Play/Pause:** a bare `RollaIcons.Play`/`Pause` at `npPlayGlyph` inside a
       `Box(Modifier.size(RollaDimens.npPlayTouchTarget).clip(CircleShape).clickable(role = Role.Button))`, with
       `contentDescription` "Play"/"Pause". There is no circle fill.
     - **Next:** `SkipNext`.
     - **Repeat:** `RollaIcons.PlayOrder`, `Repeat` or `RepeatOne` for OFF, ALL and ONE, with `contentDescription =
       "Repeat"`. Its `stateDescription` is "Repeat off", "Repeat all" or "Repeat one". Tint `accentText` when not OFF.
     - The transport glyphs use `npTransportGlyph`.
     - After the row: `Spacer(Modifier.height(RollaDimens.npBottomGap))`.
  10. **Route.**
      - Collect `overflowTargets`.
      - Hold `var showQueue by rememberSaveable { mutableStateOf(false) }`, pass `onQueueClick = { showQueue = true }`,
        and render `if (showQueue) QueueSheetRoute(onDismissRequest = { showQueue = false })`.
      - Wire `onVolumeClick` to `viewModel::showVolumePanel` and shuffle to `viewModel::toggleShuffle`. Delete the
        inline shuffle lambda.
  11. **Cleanup.** Delete every material-icons import and every private `dp` literal.

- [ ] **Step 3: App wiring.** In `composable<NowPlaying>`, add the four callbacks, each with `launchSingleTop = true`:
  - `onEditTagsClick = { id -> navController.navigate(TagEditor(id)) { ... } }`
  - `onAlbumClick = { id -> navController.navigate(AlbumDetail(id)) { ... } }`
  - `onArtistClick = { name -> navController.navigate(ArtistDetail(name)) { ... } }`
  - `onSettingsClick = { navController.navigate(Settings) { ... } }`

  Each is a plain push, so back returns to Now Playing. Add a one-line comment saying so.
- [ ] **Step 4: Smoke test.** Update `NowPlayingScreenSmokeTest` for the new parameters, keeping its assertions.
  "Add to favourites" and "Play" keep their descriptions.
- [ ] **Step 5: GREEN, then each mutation.** Run `./gradlew :feature:player:testDebugUnitTest :app:testDebugUnitTest
  :feature:player:assembleDebugAndroidTest`.
- [ ] **Step 6: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:player:detekt :app:detekt
  :feature:player:lintDebug`.

---

### Task 8: Equaliser restyle

**Owner:** equalizer-agent (`model: "opus"`)

**Files:**
- Modify:
  - `feature/equalizer/build.gradle.kts`: add `id("rolla.android.robolectric")`
  - `feature/equalizer/src/main/kotlin/com/rolla/musicplayer/feature/equalizer/EqualizerScreen.kt`
  - `feature/equalizer/src/main/kotlin/com/rolla/musicplayer/feature/equalizer/EqualizerResponseCurve.kt`
- Create: `feature/equalizer/src/test/resources/robolectric.properties` (`sdk=34`)
- Test (create): `feature/equalizer/src/test/kotlin/com/rolla/musicplayer/feature/equalizer/EqualizerScreenTest.kt`

**Interfaces:**
- Consumes: `OneUiDetailTopBar`, `OneUiSwitch`, `EqVerticalSlider`, `PillChip(onLongClick)` (Task 1),
  `rememberReducedMotion`.
- Produces: the same `EqualizerRoute` and `EqualizerScreen` signatures, plus these tags:
  - `internal const val EQ_CURVE_TAG = "eq_response_curve"`
  - `internal const val EQ_ENABLE_ROW_TAG = "eq_enable_row"`

- [ ] **Step 1: Failing tests** (`EqualizerScreenTest.kt`, `w360dp-h640dp`, NATIVE). The tests call the production
  `EqualizerScreen` with an `EqualizerUiState` built from `BUILT_IN_EQUALIZER_PRESETS` plus one user preset
  `EqualizerPreset(id = 100, name = "Mine", isCustom = true, gainsMillibel = ...)`.

```kotlin
    // Mutation: keep the old "40Hz" … "10kHz" labels → RED (spec §8.5 drops the Hz suffix).
    @Test
    fun frequencyLabelsDropTheHzSuffix() {
        setScreen()
        listOf("40", "80", "160", "315", "630", "1.25k", "2.5k", "5k", "10k").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }
        composeRule.onAllNodesWithText("Hz", substring = true).assertCountEquals(0)
    }

    // Mutation: keep the old order (user presets before Custom) → "Mine" takes Custom's slot → RED.
    @Test
    fun customChipFollowsTheBuiltInsAndPrecedesUserPresets() {
        setScreen()
        val trebleBoost = bounds("Treble boost")
        val custom = bounds("Custom")
        val mine = bounds("Mine")
        assertTrue(custom.top > trebleBoost.top) // row 4 vs row 3
        assertEquals(custom.top.value, mine.top.value, 1f) // same row
        assertTrue(custom.left < mine.left) // Custom in the first column
    }

    // Mutation: leave the chips enabled when the EQ is off → RED (spec §8.5).
    @Test
    fun disabledEqualiserDisablesSlidersAndChips() {
        setScreen(enabled = false)
        composeRule.onNodeWithText("Balanced").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("40 Hz gain").assertIsNotEnabled()
    }

    // Mutation: pass onCheckedChange to OneUiSwitch as well as the row → two toggleable nodes → RED.
    @Test
    fun enableRowIsOneToggleTarget() {
        var enabledTo: Boolean? = null
        setScreen(enabled = false, onSetEnabled = { enabledTo = it })
        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onNodeWithTag(EQ_ENABLE_ROW_TAG).performClick()
        assertEquals(true, enabledTo)
    }

    // Mutation: compose the curve unconditionally → it exists before any drag → RED (ruling 10).
    @Test
    fun responseCurveAppearsOnlyWhileDragging() {
        setScreen()
        composeRule.onNodeWithTag(EQ_CURVE_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("40 Hz gain").performTouchInput { down(center); moveBy(Offset(0f, -40f)) }
        composeRule.onNodeWithTag(EQ_CURVE_TAG).assertExists()
        composeRule.onNodeWithContentDescription("40 Hz gain").performTouchInput { up() }
    }

    // Mutation: drop onLongClick from the user chip, or delete built-ins too → RED.
    @Test
    fun longPressDeletesOnlyUserPresets() {
        val deleted = mutableListOf<Long>()
        setScreen(onDelete = { deleted += it })
        composeRule.onNodeWithText("Mine").performTouchInput { longClick() }
        composeRule.onNodeWithText("Balanced").performTouchInput { longClick() }
        assertEquals(listOf(100L), deleted)
    }

    // Mutation: keep the "Equalizer" title → RED.
    @Test
    fun titleIsEqualiserHeading() {
        setScreen()
        composeRule.onNode(hasText("Equaliser") and isHeading()).assertExists()
    }
```

  - `bounds(text)` is `onNodeWithText(text).getBoundsInRoot()`.
  - The slider's content description must be "40 Hz gain", the existing per-band wording. Check
    `EqualizerBandColumn` and adapt the test string to the real one, keeping one string per band.
  - The drag must emit a `DragInteraction` from M3's rotated Slider (Phase 1 M4 confirmed it on `swipeUp`). If
    `moveBy` doesn't start a drag, use `swipeUp` with a held pointer.

- [ ] **Step 2: Restyle `EqualizerScreen.kt`.** Apply these changes in order.
  1. **Constants.** Delete every private `dp` constant, `ThumbScaleSpring`, `PresetChipColorSpring`,
     `rememberPresetChipColors`, the private `PresetChip` and `isReducedMotion()`. Use `PillChip` instead.
  2. **Top bar.** Replace `Scaffold`'s `TopAppBar` with `OneUiDetailTopBar(title = "Equaliser", onNavigateUp = ...)`.
  3. **Content.** `Column(verticalScroll)` with no horizontal padding, because the slider card is edge to edge. It
     contains, in order:
     1. **Enable card:** a `surfaceContainer` card with `MaterialTheme.shapes.large`, padded horizontally by
        `RollaDimens.settingsCardInset`. Inside is a row tagged `EQ_ENABLE_ROW_TAG`:
        - It is `toggleable(value = enabled, role = Role.Switch, onValueChange = onSetEnabled)`,
          `heightIn(min = RollaDimens.minTouchTarget)` and padded by `settingsRowPadding`.
        - It holds `Text("Equaliser", style = settingTitle)` and `OneUiSwitch(checked = enabled, onCheckedChange = null)`.
     2. `Spacer(height = RollaDimens.eqCardTop)`.
     3. **Slider card:** full width, `surfaceContainer`, `shapes.large`.
        - **Grid lines:** `Modifier.drawBehind` draws `eqGridLine` horizontals every `RollaDimens.eqGridPitch` across
          the slider area.
        - **Columns:** nine columns of width `RollaDimens.eqColumnPitch`, centered as a group with
          `Arrangement.Center`. Each column holds:
          - an `eqValue` label, the existing `gainLabel`, in `onSurfaceVariant`;
          - `EqVerticalSlider(..., enabled = enabled, interactionSource = sharedSource,
            modifier = Modifier.height(RollaDimens.eqSliderLength))`;
          - an `eqFrequency` label.
        - **Labels:** `FREQUENCY_LABELS = listOf("40", "80", "160", "315", "630", "1.25k", "2.5k", "5k", "10k")`.
          Update its KDoc.
        - **Curve:** behind the sliders, in a `Box` layered under the slider row,
          `AnimatedVisibility(visible = isDragging, enter = fadeIn(spec), exit = fadeOut(spec))` holds
          `EqualizerResponseCurve(..., modifier = Modifier.matchParentSize().testTag(EQ_CURVE_TAG))`.
          - `isDragging` comes from `sharedSource.collectIsDraggedAsState()`, where `sharedSource` is one
            `remember { MutableInteractionSource() }` shared by all nine sliders.
          - `spec` is `rollaSpringOrSnap(reducedMotion)`. Under reduced motion, pass `EnterTransition.None` and
            `ExitTransition.None`.
          - The curve's stroke is `primary` at 35 % alpha. Change the stroke color inside `EqualizerResponseCurve`,
            and drop its fill if that keeps the curve faint. Report the choice.
     4. **Preset grid:** `Column(Modifier.padding(horizontal = RollaDimens.chipGridStart).selectableGroup(),
        verticalArrangement = spacedBy(RollaDimens.chipRowLayoutGap))` of rows with
        `horizontalArrangement = spacedBy(RollaDimens.chipGap)`. Each row holds two
        `PillChip(label, selected, onClick, Modifier.weight(1f), enabled = enabled, onLongClick = ...)` cells.
        - **Order:** `presets.filterNot { it.isCustom }`, then the synthetic Custom chip, then
          `presets.filter { it.isCustom }`.
        - **Long-press:** `onLongClick` is set only for user presets; the others pass null.
        - **Odd row:** an odd last row gets a `Spacer(Modifier.weight(1f))`.
     5. **Caption and save:** `Text(presetCaption(...), style = caption, color = onSurface)`, padded by `chipGridStart`.
        Then, under the existing Custom-only condition (ruling 11), `SaveAsPresetButton` with
        `ButtonDefaults.textButtonColors(contentColor = accentText)`.
  4. **Reduced motion.** Migrate `EqualizerResponseCurve.kt` to `rememberReducedMotion()`. Run
     `grep -rn isReducedMotion feature/equalizer`; it must come back empty.

- [ ] **Step 3: GREEN, then each mutation.** Run `./gradlew :feature:equalizer:testDebugUnitTest`. The existing
  `EqualizerViewModelTest` must stay green unmodified.
- [ ] **Step 4: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:equalizer:detekt
  :feature:equalizer:lintDebug`. The save-preset dialog has a text field, so any test that opens it takes a
  method-level `@LooperMode(LEGACY)` (Phase 2 carry-in).

---

### Task 9: Settings, About, Licenses and Privacy restyle

**Owner:** ui-builder (`model: "opus"`)

**Files:**
- Modify:
  - `feature/settings/build.gradle.kts`: add `id("rolla.android.robolectric")`
  - Under `feature/settings/src/main/kotlin/com/rolla/musicplayer/feature/settings/`: `SettingsRows.kt`,
    `SettingsScreen.kt`, `AboutScreen.kt`, `LicensesScreen.kt`, `PrivacyScreen.kt`
- Create: `feature/settings/src/test/resources/robolectric.properties` (`sdk=34`)
- Test (create): `feature/settings/src/test/kotlin/com/rolla/musicplayer/feature/settings/SettingsRowsTest.kt`

**Interfaces:**
- Produces:
  - the same public row APIs: `SettingsSectionHeader`, `SettingsSectionCard`, `SettingsToggleRow`,
    `SettingsValueNavRow` and `SettingsSliderRow`. `SettingsRadioOptionRow` stays internal.
  - new: `@Composable fun SettingsRowDivider(modifier: Modifier = Modifier)`, an `InsetDivider` with
    `settingsDividerInset` on both sides.

- [ ] **Step 1: Failing tests** (`SettingsRowsTest.kt`, `w360dp-h640dp`, NATIVE)

```kotlin
    // Mutation: render the value trailing (old layout) → value.top < label.bottom → RED (spec §8.6).
    @Test
    fun valueSitsBelowItsLabelAlignedToIt() {
        setRows { SettingsValueNavRow(label = "Theme", value = "System default", onClick = {}) }
        val label = bounds("Theme")
        val value = bounds("System default")
        assertTrue(value.top >= label.bottom)
        assertEquals(label.left.value, value.left.value, 0.5f)
    }

    // Mutation: give OneUiSwitch the onCheckedChange too → two toggleable nodes → RED.
    @Test
    fun toggleRowIsOneSwitchTarget() {
        var checked = false
        setRows { SettingsToggleRow(label = "Skip silence", checked = checked, onCheckedChange = { checked = it }) }
        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onNodeWithText("Skip silence").performClick()
        assertTrue(checked)
    }

    // Mutation: commit inside onValueChange → several commits per drag → RED.
    @Test
    fun sliderRowCommitsOnceOnRelease() {
        var commits = 0
        setRows {
            SettingsSliderRow(label = "Playback speed", value = 1f, valueRange = 0.5f..2f,
                valueLabel = { "%.1fx".format(it) }, onValueChangeFinished = { commits++ })
        }
        composeRule.onNodeWithContentDescription("Playback speed").performTouchInput { swipeRight() }
        assertEquals(1, commits)
    }

    // Mutation: header padded by a literal 16 dp instead of settingsCardInset + settingsRowPadding → RED.
    @Test
    fun sectionHeaderAlignsWithRowText() {
        setRows(withHeader = true) { SettingsValueNavRow(label = "About", onClick = {}) }
        val inset = RollaDimens.settingsCardInset + RollaDimens.settingsRowPadding
        bounds("Playback").left.assertIsEqualTo(inset, "header start", tolerance = 0.5.dp)
        bounds("About").left.assertIsEqualTo(inset, "row text start", tolerance = 0.5.dp)
    }
```

  `setRows(withHeader)` hosts the production composables the way `SettingsScreen` lays them out:
  - `SettingsSectionHeader("Playback")`, when `withHeader` is true;
  - then `SettingsSectionCard { content() }`.

  If the header start is set by `SettingsScreen` rather than by `SettingsSectionHeader`, move that padding into
  `SettingsSectionHeader` and `SettingsSectionCard`, so the test exercises production. Never copy the screen's padding
  into the test (observer 0009).

- [ ] **Step 2: Rebuild the rows.**
  - **Card:** `SettingsSectionCard` is a `surfaceContainer` `Surface` with `shapes.large`, with
    `padding(horizontal = RollaDimens.settingsCardInset)` outside and no inner padding.
  - **Header:** `SettingsSectionHeader` uses
    `padding(start = RollaDimens.settingsCardInset + RollaDimens.settingsRowPadding)` and the `sectionHeader` style.
  - **Rows:** every row pads `horizontal = RollaDimens.settingsRowPadding` and keeps `heightIn(min = minTouchTarget)`.
    - Labels use `settingTitle`, and sub-labels use `settingSubtitle`.
    - The value is a second line in `settingValue` (`accentText`). There is no trailing chevron.
    - `showProgress` keeps a trailing spinner tinted `accentText`.
  - **Toggle:** `SettingsToggleRow` uses `OneUiSwitch(checked, onCheckedChange = null)`.
  - **Slider:** `SettingsSliderRow` puts the label in `settingTitle`, then the centered value in `settingValue`, then
    `OneUiSlider` with the same drag-then-commit and optimistic-pending logic.
  - **Constants and icons:** delete every private `dp` constant and the material chevron import.

- [ ] **Step 3: Restyle the screens.**
  - **`SettingsScreen`:** the bar is `OneUiDetailTopBar(title = "Rolla Music settings", ...)`. Rows inside a card are
    separated by `SettingsRowDivider()`. The Equaliser row reads "Equaliser". The sections and the behaviour stay the
    same.
  - **`AboutScreen`, `LicensesScreen` and `PrivacyScreen`:** use `OneUiDetailTopBar` and `SettingsSectionCard`
    language, with no other changes.
  - **Device check:** the Phase 2 carry-over (the speed slider's dot was clipped at the top) is re-checked on the device
    at the gate, now that `OneUiSlider` is in place.

- [ ] **Step 4: GREEN, then each mutation.** Run `./gradlew :feature:settings:testDebugUnitTest`.
  `SettingsViewModelTest` must stay green unmodified.
- [ ] **Step 5: Checkpoint (no commit).** Run `./gradlew spotlessApply :feature:settings:detekt
  :feature:settings:lintDebug`.

---

### Task 10: Phase gate, device check and phase review

**Owner:** orchestrator. test-writer (`model: "opus"`) handles instrumented fixes.

- [ ] **Step 1: Full gate.**
  1. Check free disk space.
  2. Run `./gradlew spotlessApply`.
  3. In a separate invocation, run `./gradlew check assembleDebug --continue` in the background, logging to a file.
  4. Expect BUILD SUCCESSFUL.
  5. Existing suites get updated, never deleted.
- [ ] **Step 2: Hardcoded-value and icon grep.**

```
grep -rnE "Color\(0x|[0-9]+\.dp|[0-9]+\.sp|material\.icons" \
  feature/player/src/main feature/equalizer/src/main feature/settings/src/main
```

  Expected hits: only `0.dp` defaults and preview scaffolding. A `material.icons` hit is a defect unless a spec §5.5
  exception is listed for it.

- [ ] **Step 3: Project-instruction sweep (observer 0006).**
  1. Grep `CLAUDE.md` and `.claude/rules/` for names this phase deleted or renamed: `isReducedMotion`, "Equalizer" as
     UI copy, the mini-player's 64 dp height and the trailing settings chevrons.
  2. Fix any statement that is now false, or extend the redesign banner. The full rewrite stays in Phase 7.
- [ ] **Step 4: Device check over adb.** Connect with `MSYS_NO_PATHCONV=1`, `pm ... --user 0`.
  1. Install the build and play a track from the Tracks tab. The queue should hold the whole list (ruling 5).
  2. **Mini-player:** the pill is 60 dp tall on the nav bar, the title marquees, and Queue opens the sheet.
  3. **Queue sheet:** the current row is blue with the bars glyph. Tapping a row plays it and the sheet stays open.
     With the sheet open from the mini-player, open Now Playing: the sheet is not lost (Review Focus 5).
  4. **Now Playing:** gradient, 168 dp art, bare Play glyph and thin seekbar, plus:
     - Volume shows the system panel.
     - ⋮ Edit tags, Go to album, Go to artist and Settings each navigate, and back returns to Now Playing.
     - Shuffle and repeat tints.
  5. **Artwork transition at the new stiffness (ruling 6):** ask the user to confirm it by eye.
  6. **Equaliser:**
     - The card, grid lines, nine sliders, labels without Hz and the chip order.
     - The curve appears only while dragging.
     - Off renders at 38 % and does not respond.
     - Long-press deletes a user preset.
  7. **Settings:** the title, cards, blue values below their labels, `OneUiSwitch`es, and that the speed-slider dot is
     no longer clipped.
  8. **Ripples (ruling 7):** the chips, switch rows, icon buttons and feature cards now show a visible press highlight
     in dark theme.
  9. **Captures:** store screencaps in `docs/design/calibration/phase3/` next to the references. They stay
     uncommitted, as in Phase 2.
  10. **Connected tests:** run `./gradlew :feature:player:connectedDebugAndroidTest`.
- [ ] **Step 5: Phase review.**
  - The orchestrator dispatches `code-reviewer` (`model: "opus"`) over the whole-phase diff. Inputs: spec §8.3–§8.7,
    §11 and §12, this plan and the ledger.
  - The review also checks this phase's Produces against Phase 4a's spec consumers (§8.2 Favourites, Albums and
    Artists, and §9). The Favourites tab must be able to use `playAllInOrder`, `shuffleAll` and `playFrom` as they are.
  - Acceptance needs all of:
    - no Critical or High findings
    - green gates
    - the device check done
    - the user's by-eye confirmation of ruling 6
- [ ] **Step 6: Ready-to-commit report (no commit).** Report the files changed, test counts, the gate result and the
  device evidence, with this prepared message:

```
feat(player): One UI Now Playing, queue sheet, mini-player, Equaliser and Settings

Now Playing gets the gradient background, a thin seekbar, a bare Play glyph and working Volume and ⋮ menu
controls. A real queue sheet (playback order, tap to jump) opens from the mini-player and Now Playing, backed
by a QueueState that PlaybackService publishes. The mini-player, Equaliser and Settings are rebuilt on the One
UI kit. Play/Shuffle/Play-from are shared PlaybackController helpers, and tapping a track now plays the whole
list from it.
Phase 3 of docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```
