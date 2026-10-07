# One UI Redesign Phase 1: Tokens, Icons, Component Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Do not commit.** The user triggers commits (see Global Constraints).

**Goal:** Replace `:core:designsystem`'s tokens with the measured One UI palette, type scale, shapes and dimensions. Add a
custom thin-stroke icon set and the model-agnostic One UI component kit that Phases 2–6 build every screen from.

**Architecture:** All work lives in `:core:designsystem`.
- Colors move into an auditable internal `RollaPalette` (one instance per theme). It feeds both the M3 `ColorScheme` and
  the semantic `ColorScheme.xxx` extensions.
- Typography, shapes and dimensions become named constants (`Type.kt`, `Shape.kt`, `Dimens.kt`).
- The icon set and components live in new `icon/`, `component/` and `motion/` packages.
- Pure logic (contrast, tab geometry, slider fraction) is JVM-unit-tested. Composables are tested with Compose UI tests
  running on Robolectric.

**Tech Stack:** Kotlin 2.0.21, Compose BOM 2024.09.03 (Material3 1.3.0, Foundation 1.7.3), JUnit4, Robolectric 4.13,
androidx.compose.ui:ui-test-junit4.

**Spec:** [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../specs/2026-10-06-oneui-redesign-design.md)
(§5 tokens, §6 component kit). Raw data: [`docs/design/oneui-measurements.md`](../../design/oneui-measurements.md).
Roadmap: [`2026-10-06-oneui-redesign-roadmap.md`](2026-10-06-oneui-redesign-roadmap.md).

## Global Constraints

- Platform: `minSdk 24`, `compileSdk 34`, Java and JVM target 17. No new runtime dependencies. Test-only additions come from the
  existing version catalog (`libs.robolectric`, `libs.androidx.junit`, `libs.androidx.compose.ui.test.junit4`,
  `libs.androidx.compose.ui.test.manifest`).
- Offline app: no network libraries, no INTERNET permission.
- Robolectric tests are annotated `@Config(sdk = [34])`. The SDK 34 android-all jar is already cached locally; any other
  SDK would trigger a network download.
- Never hardcode visual values outside `:core:designsystem`. Inside it, values live only in `Color.kt`, `Type.kt`,
  `Shape.kt` and `Dimens.kt`.
- Accent: `primary = #2F6FF0` with `onPrimary = #FFFFFF`. Blue **text** uses `accentText` (`#6094FF` dark, `#1F5FE0` light), never `primary`.
- The domain model is `Song`. UI copy may say "Tracks". This phase has no user-facing copy except contentDescriptions.
- Detekt rules:
  - `LongMethod` threshold is 30 lines. Use `@Suppress("LongMethod")` on composables that need more, as the codebase already does.
  - `TooManyFunctions`: 20 per file, 10 per object.
  - `MagicNumber` is active, but literals inside property declarations are exempt. Icon path data gets `@file:Suppress("MagicNumber")`.
  - `FunctionNaming` exempts `@Composable` functions.
  - Private `@Preview` functions carry `@Suppress("UnusedPrivateMember")`.
- Formatting: Spotless enforces **CRLF** on this checkout. Run `./gradlew spotlessApply` after editing Kotlin files, before any
  gate. Never write a UTF-8 BOM.
- **No `git commit`.** End every task at its checkpoint step. The orchestrator reviews the working-tree diff (`git diff`,
  `git status --short`).
- Commands run from the repo root in Git Bash: `./gradlew …`.

## Review Focus

These five input classes are implied by the spec but untested elsewhere. Each has a test in the task that owns the code.

1. **Forced theme vs. system theme.** Dark forced while the system is light must resolve the dark semantic tokens. That is the
   v1.0 WCAG bug class. Tested in Task 1 (`ThemeTokensTest`).
2. **Large font scale (2.0×) in the tab row.** Labels must not be clipped vertically. Tested in Task 8.
3. **RTL layout direction.** The tab order mirrors, the horizontal slider fills from the right, and the EQ slider stays
   bottom-to-top. Tested in Tasks 7 and 8.
4. **Degenerate slider ranges** (start == end, value outside range). No crash, and the active track is clamped. Tested in Task 7.
5. **Disabled controls** (switch, chip, sliders) ignore input and report not-enabled. Tested in Tasks 6 and 7.

## File Structure

All paths are under `core/designsystem/`. "theme" means `src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/`;
"component", "icon" and "motion" are its sibling packages.

| File | Responsibility |
|---|---|
| `build.gradle.kts` (modify) | Add Robolectric + Compose UI test deps; `isIncludeAndroidResources` |
| theme/`Color.kt` (rewrite) | `RollaPalette` (dark/light), `NowPlayingGradient`, `DarkColorScheme`/`LightColorScheme`, semantic color extensions |
| theme/`Type.kt` (rewrite) | Named text styles, `RollaTypography`, semantic typography extensions |
| theme/`Shape.kt` (rewrite) | `RollaShapes`, `Shapes.featureCard`, `Shapes.panelTop` |
| theme/`Dimens.kt` (create) | `object RollaDimens`, all measured sizes |
| theme/`Theme.kt` | Unchanged |
| motion/`Motion.kt` (create) | `rememberReducedMotion()`, `rollaSpring()`, `rollaSpringOrSnap()` |
| icon/`RollaIcons.kt` (create) | 27 hand-authored `ImageVector`s |
| component/`MiniPlayerInset.kt` | `LocalMiniPlayerInset` |
| component/`ComponentDefaults.kt` | `DISABLED_CONTENT_ALPHA` |
| component/`InsetDivider.kt` | Inset hairline |
| component/`ArtworkPlaceholder.kt` | Gray box + note glyph |
| component/`NowPlayingBackground.kt` | `Modifier.nowPlayingBackground` |
| component/`IconButtons.kt` | `CircleIconButton`, `OneUiIconButton` |
| component/`TopBars.kt` | `OneUiTopBar`, `OneUiDetailTopBar` |
| component/`SortHeader.kt` | "⇅ Name" header with trailing slot |
| component/`ContentPanel.kt` | Edge-to-edge rounded-top panel |
| component/`OneUiSwitch.kt` | One UI switch |
| component/`PillChip.kt` | EQ preset chip |
| component/`SliderParts.kt` | `sliderFraction`, `SliderTrackLine`, `RingThumb` |
| component/`OneUiSlider.kt` | Settings slider |
| component/`EqVerticalSlider.kt` | Vertical EQ band slider |
| component/`OneUiTabRow.kt` | Center-weighted tab row + pure geometry |
| component/`KitPreviews.kt` | Dark / light / 1.5× previews of the whole kit |
| `src/test/kotlin/com/rolla/musicplayer/core/designsystem/...` | Tests per task |

---

### Task 1: Test infrastructure, measured palette and contrast audit

**Owner agent:** `m3-design-system-agent` (`model: "opus"`)

**Files:**
- Modify: `core/designsystem/build.gradle.kts`
- Rewrite: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Color.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/theme/ContrastTest.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/theme/ThemeTokensTest.kt`

**Interfaces:**
- Consumes: `LocalRollaDarkTheme` (internal, existing in `Theme.kt`).
- Produces:
  - `data class NowPlayingGradient(val top: Color, val middle: Color, val washStart: Color, val washEnd: Color)`.
  - `internal data class RollaPalette(...)`, with `internal val DarkPalette` and `internal val LightPalette`.
  - `val DarkColorScheme: ColorScheme` and `val LightColorScheme: ColorScheme`.
  - `@Composable` `ColorScheme` extensions: `accentText`, `tabUnselected`, `artworkPlaceholder`, `artworkPlaceholderLarge`,
    `artworkPlaceholderGlyph`, `miniPlayerContainer`, `miniPlayerArtPlaceholder`, `sliderInactiveTrack`, `switchThumb`,
    `fastScrollTrack`, `fastScrollIndex`, `eqGridLine`, `seekTrackActive`, `seekTrackInactive`, `nowPlayingGradient`.

- [ ] **Step 1: Add the test dependencies**

Replace `core/designsystem/build.gradle.kts` with:

```kotlin
plugins {
    id("rolla.android.library")
    id("rolla.android.library.compose")
}

android {
    namespace = "com.rolla.musicplayer.core.designsystem"
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric Compose tests need the merged manifest (ComponentActivity from ui-test-manifest).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 2: Write the failing contrast test**

Create `ContrastTest.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audits every foreground/background pairing the UI uses (spec §5.1) against WCAG 2.x AA:
 * 4.5:1 for text, 3:1 for non-text UI parts. Any token change must keep this green.
 */
class ContrastTest {

    @Test
    fun contrastRatioMatchesWcagReferenceValues() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrastRatio(Color.Black, Color.Black), 0.001f)
    }

    @Test
    fun darkPaletteMeetsWcagAa() = assertAllPairingsPass(DarkPalette)

    @Test
    fun lightPaletteMeetsWcagAa() = assertAllPairingsPass(LightPalette)

    private fun assertAllPairingsPass(palette: RollaPalette) {
        val failures = pairingsOf(palette).filter { it.ratio < it.minimum }
        assertTrue("WCAG AA failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}

private const val TEXT = 4.5f
private const val NON_TEXT = 3.0f
private const val LUMINANCE_OFFSET = 0.05f

private fun contrastRatio(foreground: Color, background: Color): Float {
    val fg = foreground.compositeOver(background).luminance()
    val bg = background.luminance()
    return (maxOf(fg, bg) + LUMINANCE_OFFSET) / (minOf(fg, bg) + LUMINANCE_OFFSET)
}

private class Pairing(val name: String, foreground: Color, background: Color, val minimum: Float) {
    val ratio: Float = contrastRatio(foreground, background)
    override fun toString(): String = "$name: ${"%.2f".format(ratio)} < $minimum"
}

private fun pairingsOf(p: RollaPalette): List<Pairing> = listOf(
    Pairing("onSurface on background", p.onSurface, p.background, TEXT),
    Pairing("onSurface on surfaceContainer", p.onSurface, p.surfaceContainer, TEXT),
    Pairing("onSurface on surfaceContainerHigh", p.onSurface, p.surfaceContainerHigh, TEXT),
    Pairing("onSurfaceVariant on background", p.onSurfaceVariant, p.background, TEXT),
    Pairing("onSurfaceVariant on surfaceContainer", p.onSurfaceVariant, p.surfaceContainer, TEXT),
    Pairing("onSurfaceVariant on surfaceContainerHigh", p.onSurfaceVariant, p.surfaceContainerHigh, TEXT),
    Pairing("tabUnselected on background", p.tabUnselected, p.background, TEXT),
    Pairing("accentText on background", p.accentText, p.background, TEXT),
    Pairing("accentText on surfaceContainer", p.accentText, p.surfaceContainer, TEXT),
    Pairing("accentText on surfaceContainerHigh", p.accentText, p.surfaceContainerHigh, TEXT),
    Pairing("onPrimary on primary", p.onPrimary, p.primary, TEXT),
    Pairing("onSurface on miniPlayerContainer", p.onSurface, p.miniPlayerContainer, TEXT),
    Pairing("fastScrollIndex on fastScrollTrack", p.onSurfaceVariant, p.fastScrollTrack, TEXT),
    Pairing("onSurface on nowPlaying middle", p.onSurface, p.nowPlayingGradient.middle, TEXT),
    Pairing("onSurface on nowPlaying washEnd", p.onSurface, p.nowPlayingGradient.washEnd, TEXT),
    Pairing("primary on surfaceContainer", p.primary, p.surfaceContainer, NON_TEXT),
    Pairing("primary on surfaceContainerHigh", p.primary, p.surfaceContainerHigh, NON_TEXT),
    Pairing("sliderInactiveTrack on surfaceContainer", p.sliderInactiveTrack, p.surfaceContainer, NON_TEXT),
    Pairing("switchThumb on sliderInactiveTrack", p.switchThumb, p.sliderInactiveTrack, NON_TEXT),
    Pairing("switchThumb on primary", p.switchThumb, p.primary, NON_TEXT),
    Pairing("seekTrackActive on nowPlaying middle", p.seekTrackActive, p.nowPlayingGradient.middle, NON_TEXT),
    Pairing("placeholderGlyph on artworkPlaceholder", p.artworkPlaceholderGlyph, p.artworkPlaceholder, NON_TEXT),
    Pairing(
        "placeholderGlyph on artworkPlaceholderLarge",
        p.artworkPlaceholderGlyph,
        p.artworkPlaceholderLarge,
        NON_TEXT,
    ),
)
```

- [ ] **Step 3: Write the failing forced-theme test (Review Focus 1)**

Create `ThemeTokensTest.kt`:

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Semantic tokens must follow the theme RollaMusicPlayerTheme actually renders, not the system setting.
 * Robolectric's default configuration is light (not night) mode, so forcing dark here reproduces the
 * "Dark forced on a light-mode device" case that caused the v1.0 contrast bug.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeTokensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forcedDarkThemeResolvesDarkTokensOnLightSystem() {
        var accent: Color? = null
        var panel: Color? = null
        var pill: Color? = null
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                accent = MaterialTheme.colorScheme.accentText
                panel = MaterialTheme.colorScheme.surfaceContainer
                pill = MaterialTheme.colorScheme.miniPlayerContainer
            }
        }
        assertEquals(DarkPalette.accentText, accent)
        assertEquals(DarkPalette.surfaceContainer, panel)
        assertEquals(DarkPalette.miniPlayerContainer, pill)
    }

    @Test
    fun forcedLightThemeResolvesLightTokens() {
        var accent: Color? = null
        var placeholder: Color? = null
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = false) {
                accent = MaterialTheme.colorScheme.accentText
                placeholder = MaterialTheme.colorScheme.artworkPlaceholder
            }
        }
        assertEquals(LightPalette.accentText, accent)
        assertEquals(LightPalette.artworkPlaceholder, placeholder)
    }
}
```

- [ ] **Step 4: Run the tests and confirm they fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: compilation FAILS with `Unresolved reference: DarkPalette` (and `RollaPalette`, `accentText`, …).

- [ ] **Step 5: Rewrite `Color.kt`**

Replace the whole file with:

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens for the One UI redesign (spec §5.1, docs/superpowers/specs/2026-10-06-oneui-redesign-design.md).
 *
 * The values are measured from the Samsung Music reference screenshots (docs/design/oneui-measurements.md).
 * Three are documented AA-safe substitutes for what was measured:
 *  - primary #2F6FF0 (measured #377AFF; white text on it only reached 3.9:1)
 *  - accentText #6094FF (measured #5B8FFD; 4.45:1 on surfaceContainerHigh)
 *  - sliderInactiveTrack #646466 (measured #5F5F61; 2.8:1 on the panel)
 *
 * Every pairing the UI uses is asserted by ContrastTest. Change a value here only together with that test.
 * Owned by m3-design-system-agent. Never hardcode colors elsewhere: read MaterialTheme.colorScheme.* or the semantic
 * extensions at the bottom of this file.
 */

/** Static Now Playing background colors, drawn by Modifier.nowPlayingBackground (spec §5.1). */
@Immutable
data class NowPlayingGradient(
    val top: Color,
    val middle: Color,
    val washStart: Color,
    val washEnd: Color,
)

/** One scheme's complete palette: Material roles plus semantic extensions. Internal so ContrastTest can audit it. */
@Suppress("LongParameterList")
@Immutable
internal data class RollaPalette(
    val background: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val onSecondary: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val accentText: Color,
    val tabUnselected: Color,
    val artworkPlaceholder: Color,
    val artworkPlaceholderLarge: Color,
    val artworkPlaceholderGlyph: Color,
    val miniPlayerContainer: Color,
    val miniPlayerArtPlaceholder: Color,
    val sliderInactiveTrack: Color,
    val switchThumb: Color,
    val fastScrollTrack: Color,
    val eqGridLine: Color,
    val seekTrackActive: Color,
    val seekTrackInactive: Color,
    val nowPlayingGradient: NowPlayingGradient,
)

internal val DarkPalette = RollaPalette(
    background = Color(0xFF000000),
    onSurface = Color(0xFFFCFCFE),
    surfaceVariant = Color(0xFF2D2D2F),
    onSurfaceVariant = Color(0xFF9B9B9D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F0F10),
    surfaceContainer = Color(0xFF171719),
    surfaceContainerHigh = Color(0xFF2D2D2F),
    surfaceContainerHighest = Color(0xFF333333),
    primary = Color(0xFF2F6FF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E3A66),
    onPrimaryContainer = Color(0xFFD6E2FF),
    onSecondary = Color(0xFF000000),
    outline = Color(0xFF5A5A5C),
    outlineVariant = Color(0xFF3A3A3C),
    error = Color(0xFFFF5A5A),
    onError = Color(0xFF000000),
    accentText = Color(0xFF6094FF),
    tabUnselected = Color(0xFF7E7E80),
    artworkPlaceholder = Color(0xFF454547),
    artworkPlaceholderLarge = Color(0xFF3B3B3B),
    artworkPlaceholderGlyph = Color(0xFFFCFCFE),
    miniPlayerContainer = Color(0xFF282035),
    miniPlayerArtPlaceholder = Color(0x2EFFFFFF), // white @ 18 % over the pill = measured #504C5A
    sliderInactiveTrack = Color(0xFF646466),
    switchThumb = Color(0xFFFCFCFE),
    fastScrollTrack = Color(0xFF333333),
    eqGridLine = Color(0xFF1D1D1F),
    seekTrackActive = Color(0xFFFCFCFE),
    seekTrackInactive = Color(0x4DFFFFFF), // white @ 30 %
    nowPlayingGradient = NowPlayingGradient(
        top = Color(0xFF000000),
        middle = Color(0xFF120F16),
        washStart = Color(0xFF231C2C),
        washEnd = Color(0xFF293332),
    ),
)

internal val LightPalette = RollaPalette(
    background = Color(0xFFF4F4F6),
    onSurface = Color(0xFF111113),
    surfaceVariant = Color(0xFFE8E8EA),
    onSurfaceVariant = Color(0xFF646467),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE8E8EA),
    surfaceContainerHighest = Color(0xFFEBEBED),
    primary = Color(0xFF2F6FF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    onSecondary = Color(0xFFFFFFFF),
    outline = Color(0xFF8A8A8E),
    outlineVariant = Color(0xFFDEDEE0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    accentText = Color(0xFF1F5FE0),
    tabUnselected = Color(0xFF646467),
    artworkPlaceholder = Color(0xFFE6E6E8),
    artworkPlaceholderLarge = Color(0xFFE9E9EB),
    artworkPlaceholderGlyph = Color(0xFF7A7A7E),
    miniPlayerContainer = Color(0xFFE9E4F2),
    miniPlayerArtPlaceholder = Color(0x1A000000), // black @ 10 %
    sliderInactiveTrack = Color(0xFF8A8A8E),
    switchThumb = Color(0xFFFFFFFF),
    fastScrollTrack = Color(0xFFF0F0F2),
    eqGridLine = Color(0xFFEEEEF0),
    seekTrackActive = Color(0xFF111113),
    seekTrackInactive = Color(0x40000000), // black @ 25 %
    nowPlayingGradient = NowPlayingGradient(
        top = Color(0xFFFFFFFF),
        middle = Color(0xFFF7F5FA),
        washStart = Color(0xFFEFE9F5),
        washEnd = Color(0xFFE9F1EF),
    ),
)

// surfaceTint is transparent: One UI surfaces are flat, so M3 tonal elevation must not tint them blue.
private fun RollaPalette.toColorScheme(base: ColorScheme): ColorScheme = base.copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = onSurfaceVariant,
    onSecondary = onSecondary,
    secondaryContainer = surfaceContainerHigh,
    onSecondaryContainer = onSurface,
    tertiary = primary,
    onTertiary = onPrimary,
    background = background,
    onBackground = onSurface,
    surface = background,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    outline = outline,
    outlineVariant = outlineVariant,
    error = error,
    onError = onError,
    scrim = Color.Black,
)

val DarkColorScheme: ColorScheme = DarkPalette.toColorScheme(darkColorScheme())

val LightColorScheme: ColorScheme = LightPalette.toColorScheme(lightColorScheme())

/**
 * The palette of the theme RollaMusicPlayerTheme is actually rendering. It branches on LocalRollaDarkTheme, never
 * isSystemInDarkTheme(): the two diverge whenever the user forces Light or Dark in Settings (ThemeTokensTest).
 */
private val currentPalette: RollaPalette
    @Composable @ReadOnlyComposable
    get() = if (LocalRollaDarkTheme.current) DarkPalette else LightPalette

/** Blue value/link text. Never use `primary` for text (it is a fill color). */
val ColorScheme.accentText: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.accentText

val ColorScheme.tabUnselected: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.tabUnselected

val ColorScheme.artworkPlaceholder: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.artworkPlaceholder

val ColorScheme.artworkPlaceholderLarge: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.artworkPlaceholderLarge

val ColorScheme.artworkPlaceholderGlyph: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.artworkPlaceholderGlyph

/** The floating mini-player pill. Text on it is always `onSurface` (audited); never `primary`. */
val ColorScheme.miniPlayerContainer: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.miniPlayerContainer

val ColorScheme.miniPlayerArtPlaceholder: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.miniPlayerArtPlaceholder

val ColorScheme.sliderInactiveTrack: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.sliderInactiveTrack

val ColorScheme.switchThumb: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.switchThumb

val ColorScheme.fastScrollTrack: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.fastScrollTrack

/** A–Z letters: full-opacity onSurfaceVariant (4.55:1 dark / 5.18:1 light on fastScrollTrack). */
val ColorScheme.fastScrollIndex: Color
    @Composable @ReadOnlyComposable
    get() = onSurfaceVariant

val ColorScheme.eqGridLine: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.eqGridLine

val ColorScheme.seekTrackActive: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.seekTrackActive

val ColorScheme.seekTrackInactive: Color
    @Composable @ReadOnlyComposable
    get() = currentPalette.seekTrackInactive

val ColorScheme.nowPlayingGradient: NowPlayingGradient
    @Composable @ReadOnlyComposable
    get() = currentPalette.nowPlayingGradient
```

- [ ] **Step 6: Run the tests and confirm they pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS. Five tests run: `ContrastTest` ×3 and `ThemeTokensTest` ×2.

- [ ] **Step 7: Confirm the rest of the app still compiles against the new colors**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Existing call sites only use names that still exist: `miniPlayerContainer`,
`sliderInactiveTrack`, `fastScrollIndex` and the M3 roles.

- [ ] **Step 8: Checkpoint (no commit)**

Run: `./gradlew spotlessApply` and then `git status --short`.
Report the changed files to the orchestrator. Do not commit.

---

### Task 2: Typography, shapes and dimensions

**Owner agent:** `m3-design-system-agent` (`model: "opus"`)

**Files:**
- Rewrite: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Type.kt`
- Rewrite: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Shape.kt`
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/theme/Dimens.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/theme/TokenInvariantsTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `object RollaDimens` (every name in Step 3).
  - `RollaTypography`.
  - `@Composable` `Typography` extensions: `appTitle`, `screenTitle`, `tabSelected`, `tabUnselected`, `sortLabel`, `songTitle`,
    `artistName`, `rowTrailing`, `miniPlayerTitle`, `miniPlayerSubtitle`, `nowPlayingTitle`, `nowPlayingArtist`, `metadata`,
    `sectionHeader`, `settingTitle`, `settingValue`, `settingSubtitle`, `chipLabel`, `caption`, `eqValue`, `eqFrequency`,
    `featureCardLabel`, `featureCardCount`, `fastScrollLetter`, `emptyState`, and the existing `typographyTokens`.
  - Internal style constants (`TabSelectedStyle`, `TabUnselectedStyle`, …).
  - `RollaShapes`, `Shapes.featureCard`, `Shapes.panelTop`.

- [ ] **Step 1: Write the failing invariants test**

These invariants guard the calibration pass in Phase 7: any re-measured value must still satisfy them.

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenInvariantsTest {

    @Test
    fun chipRowsLeaveRoomForFullTouchTargets() {
        // Each chip's 48 dp touch target must not overlap the next row's.
        assertTrue(RollaDimens.chipHeight + RollaDimens.chipGap >= RollaDimens.minTouchTarget)
    }

    @Test
    fun acceptedTouchTargetExceptionsStayAboveWcag22Minimum() {
        // Spec §5.4: EQ columns and the A–Z rail are accepted below 48 dp, but never below WCAG 2.2 AA's 24 dp.
        assertTrue(RollaDimens.eqColumnPitch >= 24.dp)
        assertTrue(RollaDimens.fastScrollWidth + RollaDimens.fastScrollEnd >= 24.dp)
    }

    @Test
    fun switchThumbFitsInsideTrack() {
        assertTrue(RollaDimens.switchThumb + RollaDimens.switchThumbInset * 2 <= RollaDimens.switchTrackHeight)
    }

    @Test
    fun listTextNeverOverlapsThumbnail() {
        assertTrue(RollaDimens.listTextStart >= RollaDimens.listThumbStart + RollaDimens.listThumb)
    }

    @Test
    fun circleButtonLayoutDerivesFromVisualMeasurements() {
        val touchSlackPerSide = (RollaDimens.minTouchTarget - RollaDimens.circleButtonSize) / 2
        assertTrue(RollaDimens.sortHeaderEnd + touchSlackPerSide == RollaDimens.circleButtonEnd)
    }

    @Test
    fun unselectedTabIsSmallerThanSelectedTab() {
        assertTrue(TabUnselectedStyle.fontSize.value < TabSelectedStyle.fontSize.value)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*TokenInvariantsTest"`
Expected: compilation FAILS with `Unresolved reference: RollaDimens`.

- [ ] **Step 3: Create `Dimens.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Measured One UI dimensions (spec §5.4, docs/design/oneui-measurements.md). They are PROVISIONAL at 3.0 px/dp
 * until Phase 7 recalibrates them against the reference device's real density and font scale.
 * Screens read these; they never declare private dp literals for anything listed here.
 */
object RollaDimens {
    // Shell
    val headerHeight = 64.dp
    val headerTitleStart = 24.dp
    val topBarEdge = 4.dp
    val detailTitleGap = 6.dp
    val iconSize = 24.dp
    val minTouchTarget = 48.dp

    // Tabs and panel
    val tabRowHeight = 56.dp
    val tabSpacing = 28.dp
    val tabLabelVerticalPadding = 8.dp
    val panelTopGap = 12.dp
    val panelRadius = 26.dp

    // Sort header and circle buttons. The visual values are measured; the layout values derive from them,
    // because each 34 dp circle sits centered in a 48 dp touch box.
    val sortHeaderHeight = 56.dp
    val sortHeaderStart = 19.dp
    val sortLabelGap = 8.dp
    val circleButtonSize = 34.dp
    val circleButtonGlyph = 18.dp
    val circleButtonEnd = 15.dp
    val circleButtonGap = 14.dp
    val sortHeaderEnd: Dp get() = (circleButtonEnd - (minTouchTarget - circleButtonSize) / 2).coerceAtLeast(0.dp)
    val circleButtonLayoutGap: Dp get() = (circleButtonGap - (minTouchTarget - circleButtonSize)).coerceAtLeast(0.dp)

    // Lists
    val listThumb = 48.dp
    val listThumbStart = 18.dp
    val listTextStart = 82.dp
    val listRowHeight = 70.dp
    val singleLineRowHeight = 56.dp
    val dividerThickness = 1.dp
    val placeholderGlyph = 24.dp
    val fastScrollWidth = 22.dp
    val fastScrollEnd = 8.dp

    // Mini-player
    val miniPlayerHeight = 60.dp
    val miniPlayerSideMargin = 4.dp
    val miniPlayerArt = 38.dp
    val miniPlayerArtStart = 11.dp

    // Now Playing
    val npArtSize = 168.dp
    val npArtTop = 130.dp
    val npSeekThickness = 3.dp
    val npSeekThumb = 16.dp
    val npSeekInset = 29.dp
    val npPlayGlyph = 40.dp
    val npPlayTouchTarget = 64.dp
    val npTransportGlyph = 26.dp
    val npActionGlyph = 26.dp

    // Equaliser
    val eqCardTop = 16.dp
    val eqSliderTrack = 3.dp
    val eqSliderLength = 237.dp
    val eqThumbDiameter = 13.dp
    val eqColumnPitch = 28.7.dp
    val eqGridPitch = 24.dp
    val sliderThumbRing = 2.dp

    // Chips
    val chipHeight = 40.dp
    val chipGap = 17.dp
    val chipGridStart = 24.dp
    val chipHorizontalPadding = 16.dp

    // Settings
    val settingsCardInset = 10.dp
    val settingsRowPadding = 19.dp
    val settingsDividerInset = 16.dp
    val settingsSliderTrack = 14.dp
    val settingsSliderThumb = 20.dp

    // Switch (thumb scaled to fit the 17 dp track with a 1 dp inset)
    val switchTrackWidth = 35.dp
    val switchTrackHeight = 17.dp
    val switchThumb = 15.dp
    val switchThumbInset = 1.dp

    // Feature cards
    val featureCardSize = 143.dp
    val featureCardGap = 17.dp
    val featureCardGlyph = 44.dp
}
```

- [ ] **Step 4: Rewrite `Type.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One UI typography (spec §5.2). The family is the system default, which renders as One UI's own font on Samsung
 * devices. Sizes are PROVISIONAL until the Phase 7 device calibration. Color is always applied at the call site.
 * Owned by m3-design-system-agent.
 */
private val RollaFontFamily = FontFamily.Default

private fun rollaStyle(size: Int, weight: FontWeight, lineHeight: Int) = TextStyle(
    fontFamily = RollaFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

internal val AppTitleStyle = rollaStyle(22, FontWeight.Bold, 28)
internal val ScreenTitleStyle = rollaStyle(22, FontWeight.Bold, 28)
internal val TabSelectedStyle = rollaStyle(23, FontWeight.Normal, 30)
internal val TabUnselectedStyle = rollaStyle(14, FontWeight.Normal, 20)
internal val SortLabelStyle = rollaStyle(14, FontWeight.Medium, 20)
internal val SongTitleStyle = rollaStyle(17, FontWeight.Normal, 22)
internal val ArtistNameStyle = rollaStyle(13, FontWeight.Normal, 18)
internal val RowTrailingStyle = rollaStyle(13, FontWeight.Normal, 18)
internal val MiniPlayerTitleStyle = rollaStyle(16, FontWeight.SemiBold, 21)
internal val MiniPlayerSubtitleStyle = rollaStyle(12, FontWeight.Normal, 16)
internal val NowPlayingTitleStyle = rollaStyle(21, FontWeight.Normal, 27)
internal val NowPlayingArtistStyle = rollaStyle(14, FontWeight.Normal, 19)
internal val MetadataStyle = rollaStyle(11, FontWeight.Normal, 14)
internal val SectionHeaderStyle = rollaStyle(13, FontWeight.Medium, 18)
internal val SettingTitleStyle = rollaStyle(17, FontWeight.Normal, 22)
internal val SettingValueStyle = rollaStyle(14, FontWeight.Normal, 19)
internal val SettingSubtitleStyle = rollaStyle(14, FontWeight.Normal, 19)
internal val ChipLabelStyle = rollaStyle(17, FontWeight.Bold, 22)
internal val CaptionStyle = rollaStyle(14, FontWeight.Normal, 20)
internal val EqValueStyle = rollaStyle(14, FontWeight.Normal, 18)
internal val EqFrequencyStyle = rollaStyle(12, FontWeight.Normal, 16)
internal val FeatureCardLabelStyle = rollaStyle(14, FontWeight.Normal, 19)
internal val FeatureCardCountStyle = rollaStyle(12, FontWeight.Normal, 16)
internal val FastScrollLetterStyle = rollaStyle(11, FontWeight.Normal, 13)
internal val EmptyStateStyle = rollaStyle(17, FontWeight.Normal, 22)

/** M3 roles reset to One UI sizes so stock components (dialogs, menus, buttons) match without overrides. */
val RollaTypography: Typography = Typography(
    headlineMedium = ScreenTitleStyle,
    headlineSmall = NowPlayingTitleStyle,
    titleLarge = TabSelectedStyle,
    titleMedium = SongTitleStyle,
    bodyLarge = rollaStyle(17, FontWeight.Normal, 22),
    bodyMedium = rollaStyle(14, FontWeight.Normal, 19),
    bodySmall = rollaStyle(13, FontWeight.Normal, 18),
    labelLarge = rollaStyle(14, FontWeight.Medium, 20),
    labelMedium = SectionHeaderStyle,
    labelSmall = MetadataStyle,
)

// ---- Semantic typography (spec §5.2). Read via MaterialTheme.typography.<name>; color comes from the caller. ----

val Typography.appTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = AppTitleStyle
val Typography.screenTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = ScreenTitleStyle
val Typography.tabSelected: TextStyle
    @Composable @ReadOnlyComposable
    get() = TabSelectedStyle
val Typography.tabUnselected: TextStyle
    @Composable @ReadOnlyComposable
    get() = TabUnselectedStyle
val Typography.sortLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = SortLabelStyle
val Typography.songTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = SongTitleStyle
val Typography.artistName: TextStyle
    @Composable @ReadOnlyComposable
    get() = ArtistNameStyle
val Typography.rowTrailing: TextStyle
    @Composable @ReadOnlyComposable
    get() = RowTrailingStyle
val Typography.miniPlayerTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MiniPlayerTitleStyle
val Typography.miniPlayerSubtitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MiniPlayerSubtitleStyle
val Typography.nowPlayingTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = NowPlayingTitleStyle
val Typography.nowPlayingArtist: TextStyle
    @Composable @ReadOnlyComposable
    get() = NowPlayingArtistStyle
val Typography.metadata: TextStyle
    @Composable @ReadOnlyComposable
    get() = MetadataStyle
val Typography.sectionHeader: TextStyle
    @Composable @ReadOnlyComposable
    get() = SectionHeaderStyle
val Typography.settingTitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = SettingTitleStyle
val Typography.settingValue: TextStyle
    @Composable @ReadOnlyComposable
    get() = SettingValueStyle
val Typography.settingSubtitle: TextStyle
    @Composable @ReadOnlyComposable
    get() = SettingSubtitleStyle
val Typography.chipLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = ChipLabelStyle
val Typography.caption: TextStyle
    @Composable @ReadOnlyComposable
    get() = CaptionStyle
val Typography.eqValue: TextStyle
    @Composable @ReadOnlyComposable
    get() = EqValueStyle
val Typography.eqFrequency: TextStyle
    @Composable @ReadOnlyComposable
    get() = EqFrequencyStyle
val Typography.featureCardLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = FeatureCardLabelStyle
val Typography.featureCardCount: TextStyle
    @Composable @ReadOnlyComposable
    get() = FeatureCardCountStyle
val Typography.fastScrollLetter: TextStyle
    @Composable @ReadOnlyComposable
    get() = FastScrollLetterStyle
val Typography.emptyState: TextStyle
    @Composable @ReadOnlyComposable
    get() = EmptyStateStyle

// Convenience accessor kept for existing callers: MaterialTheme.typographyTokens.songTitle.
val typographyTokens: Typography
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography
```

- [ ] **Step 5: Rewrite `Shape.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * One UI shape scale (spec §5.3). `large` and `extraLarge` share the measured 26 dp panel radius. Stock M3 dialogs
 * and sheets default to `extraLarge`, so they match the panels without overrides. Owned by m3-design-system-agent.
 */
val RollaShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(RollaDimens.panelRadius),
    extraLarge = RoundedCornerShape(RollaDimens.panelRadius),
)

private val FeatureCardShape = RoundedCornerShape(20.dp)
private val PanelTopShape = RoundedCornerShape(topStart = RollaDimens.panelRadius, topEnd = RollaDimens.panelRadius)

/** Playlist feature cards and album-grid cards (143 dp, 20 dp radius). */
val Shapes.featureCard: Shape
    get() = FeatureCardShape

/** The content panel: rounded top corners only; it runs to the bottom edge of the screen. */
val Shapes.panelTop: Shape
    get() = PanelTopShape
```

- [ ] **Step 6: Run the tests and confirm they pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS. All of Task 1 plus 6 invariant tests.

- [ ] **Step 7: Confirm the app still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Existing callers of `screenTitle`, `songTitle`, `artistName`, `sectionHeader`, `metadata`,
`tabSelected`, `tabUnselected`, `nowPlayingTitle` and `typographyTokens` still resolve.

- [ ] **Step 8: Checkpoint (no commit)**

Run: `./gradlew spotlessApply` and then `git status --short`. Report to the orchestrator.

---

### Task 3: RollaIcons, the thin-stroke icon set

**Owner agent:** `m3-design-system-agent` (`model: "opus"`)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/icon/RollaIcons.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/icon/RollaIconsTest.kt`

**Interfaces:**
- Produces: `object RollaIcons` with these `ImageVector` properties: `Search`, `More`, `Add`, `Sort`, `Shuffle`, `Queue`,
  `ChevronBack`, `ChevronDown`, `Volume`, `EqualizerBars`, `Heart`, `HeartFilled`, `PlayOrder`, `Repeat`, `RepeatOne`, `Play`,
  `Pause`, `SkipPrevious`, `SkipNext`, `MusicNote`, `FolderBadge`, `Close`, `Edit`, `Check`, `PlaylistAdd`, `Album`, `Person`.
  Also `internal val all: List<ImageVector>`.
- Line icons use a 1.6 stroke with round caps and joins. `MusicNote` uses a 2.0 stroke. Play, Pause, Skip*, More and
  HeartFilled are solid. Icons are drawn in black and tinted by `Icon(tint = …)`.
- Spec §5.5's `Replay15`/`Forward15` are deliberately not here. Only the Glance widget uses them, and Glance needs XML
  vector drawables, so they ship with the widget in Phase 6. Now Playing has no ±15 controls.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.rolla.musicplayer.core.designsystem.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollaIconsTest {

    @Test
    fun everyIconBuildsOnThe24GridWithAtLeastOnePath() {
        RollaIcons.all.forEach { icon ->
            assertEquals(icon.name, 24f, icon.viewportWidth, 0f)
            assertEquals(icon.name, 24f, icon.viewportHeight, 0f)
            assertTrue("${icon.name} has no paths", icon.root.size > 0)
        }
    }

    @Test
    fun iconSetIsCompleteAndUniquelyNamed() {
        val names = RollaIcons.all.map { it.name }
        assertEquals(27, names.size)
        assertEquals(names.size, names.toSet().size)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*RollaIconsTest"`
Expected: compilation FAILS with `Unresolved reference: RollaIcons`.

- [ ] **Step 3: Create `RollaIcons.kt`**

```kotlin
@file:Suppress("MagicNumber") // Vector path coordinates on the 24-unit grid.

package com.rolla.musicplayer.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val LINE_WIDTH = 1.6f
private const val NOTE_LINE_WIDTH = 2.0f

private inline fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "Rolla.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply(block).build()

private fun ImageVector.Builder.line(strokeWidth: Float = LINE_WIDTH, path: PathBuilder.() -> Unit) {
    addPath(
        pathData = PathData(path),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

private fun ImageVector.Builder.solid(path: PathBuilder.() -> Unit) {
    addPath(pathData = PathData(path), fill = SolidColor(Color.Black))
}

private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx + radius, cy)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - radius, y1 = cy)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + radius, y1 = cy)
    close()
}

private fun PathBuilder.roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right, y1 = top + radius)
    lineTo(right, bottom - radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right - radius, y1 = bottom)
    lineTo(left + radius, bottom)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left, y1 = bottom - radius)
    lineTo(left, top + radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left + radius, y1 = top)
    close()
}

private fun PathBuilder.heart() {
    moveTo(12f, 20f)
    curveTo(12f, 20f, 3f, 14.5f, 3f, 8.8f)
    curveTo(3f, 6.1f, 5.1f, 4f, 7.7f, 4f)
    curveTo(9.5f, 4f, 11f, 5f, 12f, 6.5f)
    curveTo(13f, 5f, 14.5f, 4f, 16.3f, 4f)
    curveTo(18.9f, 4f, 21f, 6.1f, 21f, 8.8f)
    curveTo(21f, 14.5f, 12f, 20f, 12f, 20f)
    close()
}

private fun PathBuilder.repeatLoop() {
    moveTo(17f, 4.5f)
    lineTo(20f, 7.5f)
    lineTo(17f, 10.5f)
    moveTo(20f, 7.5f)
    lineTo(8f, 7.5f)
    curveTo(5.8f, 7.5f, 4f, 9.3f, 4f, 11.5f)
    lineTo(4f, 12f)
    moveTo(7f, 19.5f)
    lineTo(4f, 16.5f)
    lineTo(7f, 13.5f)
    moveTo(4f, 16.5f)
    lineTo(16f, 16.5f)
    curveTo(18.2f, 16.5f, 20f, 14.7f, 20f, 12.5f)
    lineTo(20f, 12f)
}

/**
 * The One UI icon set (spec §5.5), hand-authored on a 24×24 grid. Line icons use a 1.6 stroke with round
 * caps and joins; transport glyphs are solid. Tint them through Icon(tint = …).
 * Owned by m3-design-system-agent.
 */
object RollaIcons {
    val Search: ImageVector by lazy {
        icon("Search") {
            line { circle(cx = 10.5f, cy = 10.5f, radius = 6.5f) }
            line {
                moveTo(15.3f, 15.3f)
                lineTo(20f, 20f)
            }
        }
    }

    val More: ImageVector by lazy {
        icon("More") {
            solid {
                circle(cx = 12f, cy = 5.5f, radius = 1.6f)
                circle(cx = 12f, cy = 12f, radius = 1.6f)
                circle(cx = 12f, cy = 18.5f, radius = 1.6f)
            }
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            line {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    /** Down arrow with a single left barb, then three lines that get shorter going down (the "⇅ Name" sort glyph). */
    val Sort: ImageVector by lazy {
        icon("Sort") {
            line {
                moveTo(6f, 4f)
                lineTo(6f, 20f)
                lineTo(3f, 17f)
                moveTo(10f, 5.5f)
                lineTo(21f, 5.5f)
                moveTo(10f, 12f)
                lineTo(18f, 12f)
                moveTo(10f, 18.5f)
                lineTo(14f, 18.5f)
            }
        }
    }

    val Shuffle: ImageVector by lazy {
        icon("Shuffle") {
            line {
                moveTo(3f, 7f)
                lineTo(6.5f, 7f)
                curveTo(9f, 7f, 10.5f, 8.5f, 12f, 12f)
                curveTo(13.5f, 15.5f, 15f, 17f, 17.5f, 17f)
                lineTo(20.5f, 17f)
                moveTo(3f, 17f)
                lineTo(6.5f, 17f)
                curveTo(9f, 17f, 10.5f, 15.5f, 12f, 12f)
                curveTo(13.5f, 8.5f, 15f, 7f, 17.5f, 7f)
                lineTo(20.5f, 7f)
                moveTo(18f, 4.5f)
                lineTo(20.5f, 7f)
                lineTo(18f, 9.5f)
                moveTo(18f, 14.5f)
                lineTo(20.5f, 17f)
                lineTo(18f, 19.5f)
            }
        }
    }

    /** Three lines (the top one runs full width) with a note at the bottom right. */
    val Queue: ImageVector by lazy {
        icon("Queue") {
            line {
                moveTo(3f, 5.5f)
                lineTo(21f, 5.5f)
                moveTo(3f, 11f)
                lineTo(12f, 11f)
                moveTo(3f, 16.5f)
                lineTo(9f, 16.5f)
                moveTo(17.4f, 17.3f)
                lineTo(17.4f, 9.5f)
                curveTo(18.8f, 9.7f, 20f, 10.6f, 20.5f, 11.8f)
            }
            solid { circle(cx = 15.2f, cy = 17.3f, radius = 2.3f) }
        }
    }

    val ChevronBack: ImageVector by lazy {
        icon("ChevronBack") {
            line {
                moveTo(15f, 4.5f)
                lineTo(8f, 12f)
                lineTo(15f, 19.5f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            line {
                moveTo(4.5f, 8.5f)
                lineTo(12f, 15.5f)
                lineTo(19.5f, 8.5f)
            }
        }
    }

    val Volume: ImageVector by lazy {
        icon("Volume") {
            line {
                moveTo(4f, 9.5f)
                lineTo(7.5f, 9.5f)
                lineTo(12f, 5.5f)
                lineTo(12f, 18.5f)
                lineTo(7.5f, 14.5f)
                lineTo(4f, 14.5f)
                close()
                moveTo(15f, 9f)
                curveTo(16.3f, 10.6f, 16.3f, 13.4f, 15f, 15f)
                moveTo(17.8f, 6.5f)
                curveTo(20.6f, 9.8f, 20.6f, 14.2f, 17.8f, 17.5f)
            }
        }
    }

    /** Five bottom-aligned bars of varying height (Now Playing's equaliser entry, queue "now playing" marker). */
    val EqualizerBars: ImageVector by lazy {
        icon("EqualizerBars") {
            line {
                moveTo(4f, 9f)
                lineTo(4f, 19.5f)
                moveTo(8f, 4.5f)
                lineTo(8f, 19.5f)
                moveTo(12f, 8.5f)
                lineTo(12f, 19.5f)
                moveTo(16f, 12f)
                lineTo(16f, 19.5f)
                moveTo(20f, 14.5f)
                lineTo(20f, 19.5f)
            }
        }
    }

    val Heart: ImageVector by lazy { icon("Heart") { line { heart() } } }

    val HeartFilled: ImageVector by lazy { icon("HeartFilled") { solid { heart() } } }

    /** One UI's "play in order" glyph: an "A" above a right arrow. It is the repeat-OFF state. */
    val PlayOrder: ImageVector by lazy {
        icon("PlayOrder") {
            line {
                moveTo(8.5f, 12.5f)
                lineTo(12f, 3.5f)
                lineTo(15.5f, 12.5f)
                moveTo(9.6f, 9.6f)
                lineTo(14.4f, 9.6f)
                moveTo(4f, 18f)
                lineTo(20f, 18f)
                moveTo(17f, 15f)
                lineTo(20f, 18f)
                lineTo(17f, 21f)
            }
        }
    }

    val Repeat: ImageVector by lazy { icon("Repeat") { line { repeatLoop() } } }

    val RepeatOne: ImageVector by lazy {
        icon("RepeatOne") {
            line {
                repeatLoop()
                moveTo(11.2f, 10.8f)
                lineTo(12.6f, 9.8f)
                lineTo(12.6f, 14.2f)
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") {
            solid {
                moveTo(7.5f, 5.2f)
                curveTo(7.5f, 4.4f, 8.4f, 3.9f, 9.1f, 4.4f)
                lineTo(19f, 11.1f)
                curveTo(19.6f, 11.5f, 19.6f, 12.5f, 19f, 12.9f)
                lineTo(9.1f, 19.6f)
                curveTo(8.4f, 20.1f, 7.5f, 19.6f, 7.5f, 18.8f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            solid {
                roundedRect(left = 6.5f, top = 5f, right = 10f, bottom = 19f, radius = 1f)
                roundedRect(left = 14f, top = 5f, right = 17.5f, bottom = 19f, radius = 1f)
            }
        }
    }

    /** A bar plus two solid triangles pointing left (|◀◀). */
    val SkipPrevious: ImageVector by lazy {
        icon("SkipPrevious") {
            solid {
                roundedRect(left = 3.5f, top = 5.5f, right = 5.5f, bottom = 18.5f, radius = 1f)
                moveTo(13f, 6.5f)
                lineTo(13f, 17.5f)
                lineTo(6.2f, 12f)
                close()
                moveTo(20.5f, 6.5f)
                lineTo(20.5f, 17.5f)
                lineTo(13.2f, 12f)
                close()
            }
        }
    }

    /** Two solid triangles pointing right plus a bar (▶▶|). */
    val SkipNext: ImageVector by lazy {
        icon("SkipNext") {
            solid {
                roundedRect(left = 18.5f, top = 5.5f, right = 20.5f, bottom = 18.5f, radius = 1f)
                moveTo(11f, 6.5f)
                lineTo(11f, 17.5f)
                lineTo(17.8f, 12f)
                close()
                moveTo(3.5f, 6.5f)
                lineTo(3.5f, 17.5f)
                lineTo(10.8f, 12f)
                close()
            }
        }
    }

    /** The placeholder note: a round head, a stem, and a flag that curls right. */
    val MusicNote: ImageVector by lazy {
        icon("MusicNote") {
            line(strokeWidth = NOTE_LINE_WIDTH) {
                circle(cx = 9f, cy = 17f, radius = 3.2f)
                moveTo(12.2f, 17f)
                lineTo(12.2f, 5f)
                curveTo(15.5f, 5f, 17.5f, 7.2f, 17.5f, 10.2f)
            }
        }
    }

    val FolderBadge: ImageVector by lazy {
        icon("FolderBadge") {
            line {
                moveTo(3.5f, 6.5f)
                lineTo(9f, 6.5f)
                lineTo(11f, 8.5f)
                lineTo(20.5f, 8.5f)
                lineTo(20.5f, 18.5f)
                lineTo(3.5f, 18.5f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            line {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    val Edit: ImageVector by lazy {
        icon("Edit") {
            line {
                moveTo(4f, 20f)
                lineTo(4.6f, 16.4f)
                lineTo(15.8f, 5.2f)
                curveTo(16.6f, 4.4f, 17.8f, 4.4f, 18.6f, 5.2f)
                lineTo(18.8f, 5.4f)
                curveTo(19.6f, 6.2f, 19.6f, 7.4f, 18.8f, 8.2f)
                lineTo(7.6f, 19.4f)
                close()
                moveTo(14f, 7f)
                lineTo(17f, 10f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            line {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 7f)
            }
        }
    }

    val PlaylistAdd: ImageVector by lazy {
        icon("PlaylistAdd") {
            line {
                moveTo(3.5f, 6f)
                lineTo(16f, 6f)
                moveTo(3.5f, 11f)
                lineTo(16f, 11f)
                moveTo(3.5f, 16f)
                lineTo(10f, 16f)
                moveTo(17f, 13f)
                lineTo(17f, 21f)
                moveTo(13f, 17f)
                lineTo(21f, 17f)
            }
        }
    }

    val Album: ImageVector by lazy {
        icon("Album") {
            line {
                circle(cx = 12f, cy = 12f, radius = 8.5f)
                circle(cx = 12f, cy = 12f, radius = 2.5f)
            }
        }
    }

    val Person: ImageVector by lazy {
        icon("Person") {
            line {
                circle(cx = 12f, cy = 8f, radius = 3.5f)
                moveTo(5f, 20f)
                curveTo(5f, 16.4f, 8.1f, 14f, 12f, 14f)
                curveTo(15.9f, 14f, 19f, 16.4f, 19f, 20f)
            }
        }
    }

    /** Every icon, for RollaIconsTest and the kit preview. */
    internal val all: List<ImageVector>
        get() = listOf(
            Search, More, Add, Sort, Shuffle, Queue, ChevronBack, ChevronDown, Volume, EqualizerBars, Heart,
            HeartFilled, PlayOrder, Repeat, RepeatOne, Play, Pause, SkipPrevious, SkipNext, MusicNote, FolderBadge,
            Close, Edit, Check, PlaylistAdd, Album, Person,
        )
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*RollaIconsTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Checkpoint (no commit)**

Run: `./gradlew spotlessApply` and then `git status --short`. Report to the orchestrator.

---

### Task 4: Motion helpers and visual primitives

**Owner agent:** `m3-design-system-agent` (`model: "opus"`)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/motion/Motion.kt`
- Create in `component/`: `ComponentDefaults.kt`, `MiniPlayerInset.kt`, `InsetDivider.kt`, `ArtworkPlaceholder.kt`,
  `NowPlayingBackground.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/PrimitivesTest.kt`

**Interfaces:**
- Consumes: `RollaIcons.MusicNote`, `RollaDimens`, and the color extensions from Task 1.
- Produces:
  - `@Composable fun rememberReducedMotion(): Boolean`
  - `fun <T> rollaSpring(): FiniteAnimationSpec<T>`
  - `fun <T> rollaSpringOrSnap(reducedMotion: Boolean): FiniteAnimationSpec<T>`
  - `internal const val DISABLED_CONTENT_ALPHA = 0.38f`
  - `val LocalMiniPlayerInset: ProvidableCompositionLocal<Dp>`
  - `@Composable fun InsetDivider(startInset: Dp, modifier: Modifier = Modifier, endInset: Dp = 0.dp)`
  - `@Composable fun ArtworkPlaceholder(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small, large: Boolean = false, glyphSize: Dp = RollaDimens.placeholderGlyph)`
  - `fun Modifier.nowPlayingBackground(gradient: NowPlayingGradient): Modifier`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingGradient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PrimitivesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun artworkPlaceholderIsDecorativeForAccessibility() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                ArtworkPlaceholder(modifier = Modifier.size(48.dp))
            }
        }
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(0)
    }

    @Test
    fun miniPlayerInsetDefaultsToZeroAndCanBeProvided() {
        var defaultInset: Dp? = null
        var providedInset: Dp? = null
        composeRule.setContent {
            defaultInset = LocalMiniPlayerInset.current
            CompositionLocalProvider(LocalMiniPlayerInset provides 72.dp) {
                providedInset = LocalMiniPlayerInset.current
            }
        }
        assertEquals(0.dp, defaultInset)
        assertEquals(72.dp, providedInset)
    }

    @Test
    fun nowPlayingBackgroundLaysOutWithoutCrashing() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.size(200.dp).nowPlayingBackground(MaterialTheme.colorScheme.nowPlayingGradient))
            }
        }
        composeRule.waitForIdle()
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*PrimitivesTest"`
Expected: compilation FAILS with `Unresolved reference: ArtworkPlaceholder` (and others).

- [ ] **Step 3: Create `motion/Motion.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user turned animations off (ANIMATOR_DURATION_SCALE == 0; Accessibility > Remove animations).
 * Components snap instead of animating when this is true (ui-style-guide §9). The value is read once per call site.
 *
 * This replaces the private isReducedMotion() copies in :feature:equalizer, :feature:player and :feature:playlists.
 * Each copy is migrated in the phase that rewrites its file (roadmap).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** The calm, non-bouncy spring every interactive component uses (same family as the v1.0 EQ and artwork motion). */
fun <T> rollaSpring(): FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

/** [rollaSpring], or an instant snap when [reducedMotion] is true. */
fun <T> rollaSpringOrSnap(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) snap() else rollaSpring()
```

- [ ] **Step 4: Create `component/ComponentDefaults.kt` and `component/MiniPlayerInset.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

/** Content alpha for disabled kit controls (the M3 disabled-content convention). */
internal const val DISABLED_CONTENT_ALPHA = 0.38f
```

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * The floating mini-player's height plus margins while it is visible; 0.dp otherwise. The app shell provides it
 * (spec §7.3). Every scrollable list adds it to its bottom contentPadding, so content scrolls under the pill and the
 * last item can still be scrolled fully into view.
 */
val LocalMiniPlayerInset = compositionLocalOf { 0.dp }
```

- [ ] **Step 5: Create `component/InsetDivider.kt` and `component/ArtworkPlaceholder.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/** The One UI hairline between rows: outlineVariant, inset from the start (and optionally the end). */
@Composable
fun InsetDivider(startInset: Dp, modifier: Modifier = Modifier, endInset: Dp = 0.dp) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset, end = endInset),
        thickness = RollaDimens.dividerThickness,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
```

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholder
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderGlyph
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderLarge

/**
 * Missing-artwork stand-in: a gray rounded box with a centered note glyph. It is decorative (no contentDescription);
 * the row or card that owns it describes the item.
 */
@Composable
fun ArtworkPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    large: Boolean = false,
    glyphSize: Dp = RollaDimens.placeholderGlyph,
) {
    val container = if (large) {
        MaterialTheme.colorScheme.artworkPlaceholderLarge
    } else {
        MaterialTheme.colorScheme.artworkPlaceholder
    }
    Box(modifier = modifier.clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = RollaIcons.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.artworkPlaceholderGlyph,
            modifier = Modifier.size(glyphSize),
        )
    }
}
```

- [ ] **Step 6: Create `component/NowPlayingBackground.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import com.rolla.musicplayer.core.designsystem.theme.NowPlayingGradient

// Fraction of the height where the base gradient settles and the bottom wash begins to fade in (measured: ~55 %).
private const val WASH_START_FRACTION = 0.55f

/**
 * The Now Playing background (spec §5.1). A vertical base runs from `top` to `middle`. A left-to-right wash
 * (`washStart` purple to `washEnd` teal) fades in toward the bottom edge. Drawing only; never recomposes.
 */
fun Modifier.nowPlayingBackground(gradient: NowPlayingGradient): Modifier = drawWithCache {
    val washTop = size.height * WASH_START_FRACTION
    val washSize = Size(size.width, size.height - washTop)
    val base = Brush.verticalGradient(
        0f to gradient.top,
        WASH_START_FRACTION to gradient.middle,
        1f to gradient.middle,
    )
    val wash = Brush.horizontalGradient(listOf(gradient.washStart, gradient.washEnd))
    val fade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = washTop, endY = size.height)
    onDrawBehind {
        drawRect(base)
        drawContext.canvas.saveLayer(Rect(0f, washTop, size.width, size.height), Paint())
        drawRect(wash, topLeft = Offset(0f, washTop), size = washSize)
        drawRect(fade, topLeft = Offset(0f, washTop), size = washSize, blendMode = BlendMode.DstIn)
        drawContext.canvas.restore()
    }
}
```

- [ ] **Step 7: Run the test and confirm it passes**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*PrimitivesTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: Checkpoint (no commit)**

Run: `./gradlew spotlessApply` and then `git status --short`. Report to the orchestrator.

---

### Task 5: Icon buttons, top bars, sort header and content panel

**Owner agent:** `m3-design-system-agent` (`model: "opus"`)

**Files:**
- Create in `component/`: `IconButtons.kt`, `TopBars.kt`, `SortHeader.kt`, `ContentPanel.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/BarsAndButtonsTest.kt`

**Interfaces:**
- Consumes: `RollaIcons` (Task 3); `RollaDimens`, `appTitle`, `screenTitle`, `sortLabel` and `Shapes.panelTop` (Task 2).
- Produces:
  - `@Composable fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`
  - `@Composable fun OneUiIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface)`
  - `@Composable fun OneUiTopBar(title: String, modifier: Modifier = Modifier, windowInsets: WindowInsets = WindowInsets.statusBars, actions: @Composable RowScope.() -> Unit = {})`
  - `@Composable fun OneUiDetailTopBar(title: String, onNavigateUp: () -> Unit, modifier: Modifier = Modifier, windowInsets: WindowInsets = WindowInsets.statusBars, actions: @Composable RowScope.() -> Unit = {})` (its back button's contentDescription is "Navigate up")
  - `@Composable fun SortHeader(label: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {})`
  - `@Composable fun ContentPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BarsAndButtonsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun circleIconButtonClicksAndHasA48dpTouchTarget() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CircleIconButton(icon = RollaIcons.Shuffle, contentDescription = "Shuffle all", onClick = { clicks++ })
            }
        }
        composeRule.onNodeWithContentDescription("Shuffle all")
            .assertHasClickAction()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun detailTopBarNavigatesUpAndExposesTitleAsHeading() {
        var ups = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiDetailTopBar(title = "Equaliser", onNavigateUp = { ups++ }, windowInsets = WindowInsets(0))
            }
        }
        composeRule.onNode(isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Equaliser").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        assertEquals(1, ups)
    }

    @Test
    fun topBarShowsTitleAndActions() {
        var searches = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiTopBar(title = "Rolla Music", windowInsets = WindowInsets(0)) {
                    OneUiIconButton(icon = RollaIcons.Search, contentDescription = "Search", onClick = { searches++ })
                }
            }
        }
        composeRule.onNodeWithText("Rolla Music").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").performClick()
        assertEquals(1, searches)
    }

    @Test
    fun sortHeaderShowsLabelAndTrailingContent() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                ContentPanel {
                    SortHeader(label = "Name") { Text("trailing") }
                }
            }
        }
        composeRule.onNodeWithText("Name").assertIsDisplayed()
        composeRule.onNodeWithText("trailing").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*BarsAndButtonsTest"`
Expected: compilation FAILS with `Unresolved reference: CircleIconButton` (and others).

- [ ] **Step 3: Create `component/IconButtons.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * The 34 dp dark circle buttons in list headers (Shuffle / Play, spec §6). The circle is drawn at the measured size,
 * and the touch target is 48 dp.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(RollaDimens.circleButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(RollaDimens.circleButtonGlyph),
        )
    }
}

/** A plain 48 dp icon button tinted onSurface, for top-bar actions and transport controls. */
@Composable
fun OneUiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(RollaDimens.iconSize),
        )
    }
}
```

- [ ] **Step 4: Create `component/TopBars.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.appTitle
import com.rolla.musicplayer.core.designsystem.theme.screenTitle

/**
 * Home header: the bold app title on the left and trailing action icons on the right (spec §7.2). It owns the
 * status-bar inset by default.
 */
@Composable
fun OneUiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .heightIn(min = RollaDimens.headerHeight)
            .padding(start = RollaDimens.headerTitleStart, end = RollaDimens.topBarEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.appTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/**
 * Bar for pushed screens: a thin "<" chevron, then the bold title (spec §6).
 * It owns the status-bar inset by default.
 */
@Composable
fun OneUiDetailTopBar(
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .heightIn(min = RollaDimens.headerHeight)
            .padding(horizontal = RollaDimens.topBarEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneUiIconButton(icon = RollaIcons.ChevronBack, contentDescription = "Navigate up", onClick = onNavigateUp)
        Spacer(modifier = Modifier.width(RollaDimens.detailTitleGap))
        Text(
            text = title,
            style = MaterialTheme.typography.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}
```

- [ ] **Step 5: Create `component/SortHeader.kt` and `component/ContentPanel.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sortLabel

/**
 * The static "⇅ Name" list header with an optional trailing slot (usually two CircleIconButtons). The label is
 * deliberately not clickable: there is no sort menu (spec §3), and a clickable label would be a fake control.
 */
@Composable
fun SortHeader(label: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.sortHeaderHeight)
            .padding(start = RollaDimens.sortHeaderStart, end = RollaDimens.sortHeaderEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = RollaIcons.Sort,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RollaDimens.iconSize),
        )
        Spacer(modifier = Modifier.width(RollaDimens.sortLabelGap))
        Text(
            text = label,
            style = MaterialTheme.typography.sortLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(RollaDimens.circleButtonLayoutGap),
            verticalAlignment = Alignment.CenterVertically,
            content = trailing,
        )
    }
}
```

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.rolla.musicplayer.core.designsystem.theme.panelTop

/**
 * The edge-to-edge surfaceContainer panel that holds each tab's content. Its top corners are rounded, and it runs to
 * the bottom of the screen (spec §7.2).
 */
@Composable
fun ContentPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.panelTop)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}
```

- [ ] **Step 6: Run the test and confirm it passes**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*BarsAndButtonsTest"`
Expected: PASS (4 tests).

- [ ] **Step 7: Checkpoint (no commit)**

Run: `./gradlew spotlessApply` and then `git status --short`. Report to the orchestrator.

---

### Task 6: OneUiSwitch and PillChip

**Owner agent:** `m3-design-system-agent` (`model: "opus"`)

**Files:**
- Create in `component/`: `OneUiSwitch.kt`, `PillChip.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/SwitchAndChipTest.kt`

**Interfaces:**
- Consumes: `rememberReducedMotion` and `rollaSpringOrSnap` (Task 4); `DISABLED_CONTENT_ALPHA`; `switchThumb`,
  `sliderInactiveTrack` and `chipLabel`.
- Produces:
  - `@Composable fun OneUiSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true)`.
    Pass `onCheckedChange = null` inside a `toggleable` row; the row then owns the semantics.
  - `@Composable fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`,
    with `Role.RadioButton` semantics.

- [ ] **Step 1: Write the failing test (including Review Focus 5, disabled controls)**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwitchAndChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standaloneSwitchTogglesWithSwitchRole() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                var checked by remember { mutableStateOf(false) }
                OneUiSwitch(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.testTag("sw"))
            }
        }
        composeRule.onNodeWithTag("sw")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithTag("sw").assertIsOn()
    }

    @Test
    fun disabledSwitchIgnoresClicks() {
        var changes = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiSwitch(
                    checked = false,
                    onCheckedChange = { changes++ },
                    enabled = false,
                    modifier = Modifier.testTag("sw"),
                )
            }
        }
        composeRule.onNodeWithTag("sw").assertIsNotEnabled().performClick()
        assertEquals(0, changes)
    }

    @Test
    fun chipReportsSelectionAndClicks() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Column {
                    PillChip(label = "Balanced", selected = true, onClick = {})
                    PillChip(label = "Bass boost", selected = false, onClick = { clicks++ })
                }
            }
        }
        composeRule.onNodeWithText("Balanced").assertIsSelected()
        composeRule.onNodeWithText("Bass boost").assertIsNotSelected().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledChipIgnoresClicks() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PillChip(label = "Smooth", selected = false, onClick = { clicks++ }, enabled = false)
            }
        }
        composeRule.onNodeWithText("Smooth").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }
}
```

Note: the selection semantics live on the chip's `selectable` node, which merges its `Text` child, so
`onNodeWithText` resolves to the selectable node.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*SwitchAndChipTest"`
Expected: compilation FAILS with `Unresolved reference: OneUiSwitch`.

- [ ] **Step 3: Create `component/OneUiSwitch.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack
import com.rolla.musicplayer.core.designsystem.theme.switchThumb

/**
 * The One UI switch: a 35×17 dp pill track (primary when on, sliderInactiveTrack when off) with a white thumb that
 * springs across. Pass onCheckedChange = null when a toggleable row owns the click and semantics (SettingsToggleRow);
 * the switch is then purely visual.
 */
@Composable
fun OneUiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reducedMotion = rememberReducedMotion()
    val trackColor = animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.sliderInactiveTrack,
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "switchTrack",
    )
    val travel = RollaDimens.switchTrackWidth - RollaDimens.switchThumb - RollaDimens.switchThumbInset * 2
    val thumbOffset = animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .switchInteraction(checked, enabled, onCheckedChange)
            .size(width = RollaDimens.switchTrackWidth, height = RollaDimens.switchTrackHeight)
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA)
            .drawBehind { drawRoundRect(color = trackColor.value, cornerRadius = CornerRadius(size.height / 2f)) }
            .padding(RollaDimens.switchThumbInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.value.roundToPx(), 0) }
                .size(RollaDimens.switchThumb)
                .background(MaterialTheme.colorScheme.switchThumb, CircleShape),
        )
    }
}

private fun Modifier.switchInteraction(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
): Modifier = if (onCheckedChange == null) {
    this
} else {
    minimumInteractiveComponentSize()
        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
}
```

- [ ] **Step 4: Create `component/PillChip.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.chipLabel

/**
 * The EQ preset chip (spec §8.5): a 40 dp pill with a bold label. Selected is primary/onPrimary; unselected is
 * surfaceContainerHigh/onSurface. The color change springs, or snaps under reduced motion. It is single-select
 * (RadioButton role); the caller wraps a grid of these in Modifier.selectableGroup().
 */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reducedMotion = rememberReducedMotion()
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHigh,
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurface,
        animationSpec = rollaSpringOrSnap(reducedMotion),
        label = "chipContent",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA) // before background, so the whole pill fades
            .heightIn(min = RollaDimens.chipHeight)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = RollaDimens.chipHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.chipLabel,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*SwitchAndChipTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Checkpoint (no commit)**

Run: `./gradlew spotlessApply` and then `git status --short`. Report to the orchestrator.

---

### Task 7: Slider parts, OneUiSlider and EqVerticalSlider

**Owner agent:** `m3-design-system-agent` (`model: "opus"`)

**Files:**
- Create in `component/`: `SliderParts.kt`, `OneUiSlider.kt`, `EqVerticalSlider.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/SliderFractionTest.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/SlidersTest.kt`

**Interfaces:**
- Consumes: `DISABLED_CONTENT_ALPHA`, `sliderInactiveTrack`, `RollaDimens`.
- Produces:
  - `internal fun sliderFraction(value: Float, valueRange: ClosedFloatingPointRange<Float>): Float`
  - `internal fun SliderTrackLine(state: SliderState, thickness: Dp, activeColor: Color, inactiveColor: Color, modifier: Modifier = Modifier)` (composable)
  - `internal fun RingThumb(diameter: Dp, ringWidth: Dp, ringColor: Color, fillColor: Color, modifier: Modifier = Modifier)` (composable)
  - `@Composable fun OneUiSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null, interactionSource: MutableInteractionSource = remember { MutableInteractionSource() })`
  - `@Composable fun EqVerticalSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, contentDescription: String, modifier: Modifier = Modifier, enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null, interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }, thumbScale: () -> Float = { 1f })`.
    Phase 3's Equaliser drives the drag-time thumb scale and the curve visibility through `interactionSource` and `thumbScale`.

- [ ] **Step 1: Write the failing pure-function test (Review Focus 4)**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderFractionTest {

    @Test
    fun fractionIsLinearInsideTheRange() {
        assertEquals(0.25f, sliderFraction(0.25f, 0f..1f), 0.0001f)
        assertEquals(0.5f, sliderFraction(0f, -1500f..1500f), 0.0001f)
    }

    @Test
    fun fractionClampsValuesOutsideTheRange() {
        assertEquals(0f, sliderFraction(-3f, 0f..1f), 0.0001f)
        assertEquals(1f, sliderFraction(7f, 0f..1f), 0.0001f)
    }

    @Test
    fun degenerateRangeYieldsZeroInsteadOfNaN() {
        assertEquals(0f, sliderFraction(5f, 5f..5f), 0.0001f)
    }
}
```

- [ ] **Step 2: Write the failing composable test (Review Focus 3 and 5)**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SlidersTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSliderReportsSetProgress() {
        var reported = -1f
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiSlider(
                    value = 1f,
                    onValueChange = { reported = it },
                    valueRange = 0.5f..2f,
                    steps = 5,
                    modifier = Modifier.width(300.dp).semantics { contentDescription = "Playback speed" },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Playback speed")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.25f) }
        assertEquals(1.25f, reported, 0.001f)
    }

    @Test
    fun eqSliderReportsSetProgressInRtl() {
        var reported = 0f
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    Box(Modifier.size(width = 29.dp, height = 237.dp)) {
                        EqVerticalSlider(
                            value = 0f,
                            onValueChange = { reported = it },
                            valueRange = -1500f..1500f,
                            contentDescription = "40 Hz gain",
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("40 Hz gain")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(600f) }
        assertEquals(600f, reported, 0.5f)
    }

    @Test
    fun disabledEqSliderIsNotEnabled() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.size(width = 29.dp, height = 237.dp)) {
                    EqVerticalSlider(
                        value = 0f,
                        onValueChange = {},
                        valueRange = -1500f..1500f,
                        contentDescription = "80 Hz gain",
                        enabled = false,
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("80 Hz gain").assertIsNotEnabled()
    }
}
```

- [ ] **Step 3: Run the tests and confirm they fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*Slider*"`
Expected: compilation FAILS with `Unresolved reference: sliderFraction` (and `OneUiSlider`, `EqVerticalSlider`).

- [ ] **Step 4: Create `component/SliderParts.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** Position of [value] within [valueRange] as 0..1. Values are clamped, and an empty range yields 0 instead of NaN. */
internal fun sliderFraction(value: Float, valueRange: ClosedFloatingPointRange<Float>): Float {
    val span = valueRange.endInclusive - valueRange.start
    return if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
}

/**
 * Rounded slider track: inactive full width, active from the start edge to the thumb center. It mirrors in RTL.
 * The state is read in the draw phase only, so dragging redraws and never recomposes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SliderTrackLine(
    state: SliderState,
    thickness: Dp,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(thickness)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = inactiveColor, cornerRadius = radius)
        val activeWidth = size.width * sliderFraction(state.value, state.valueRange)
        if (activeWidth > 0f) {
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - activeWidth else 0f
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(left, 0f),
                size = Size(activeWidth, size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** A circular thumb: a [fillColor] disc with a [ringWidth] ring of [ringColor]. */
@Composable
internal fun RingThumb(
    diameter: Dp,
    ringWidth: Dp,
    ringColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .background(fillColor, CircleShape)
            .border(ringWidth, ringColor, CircleShape),
    )
}
```

- [ ] **Step 5: Create `component/OneUiSlider.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack

/**
 * The One UI settings slider (spec §6): a thick 14 dp rounded track (primary active, sliderInactiveTrack inactive)
 * and a 20 dp thumb (background fill, 2 dp primary ring). It is built on M3 Slider, so drag, keyboard and SetProgress
 * semantics are the platform's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = MaterialTheme.colorScheme
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_CONTENT_ALPHA),
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        steps = steps,
        thumb = {
            RingThumb(
                diameter = RollaDimens.settingsSliderThumb,
                ringWidth = RollaDimens.sliderThumbRing,
                ringColor = colors.primary,
                fillColor = colors.background,
            )
        },
        track = { state ->
            SliderTrackLine(
                state = state,
                thickness = RollaDimens.settingsSliderTrack,
                activeColor = colors.primary,
                inactiveColor = colors.sliderInactiveTrack,
            )
        },
        valueRange = valueRange,
    )
}
```

- [ ] **Step 6: Create `component/EqVerticalSlider.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sliderInactiveTrack

private const val VERTICAL_ROTATION_DEGREES = 270f

/**
 * One vertical EQ band slider (spec §8.5). It has a 3 dp track (primary below the thumb, sliderInactiveTrack above)
 * and a 13 dp hollow ring thumb. It is a rotated M3 Slider, the same proven technique as the v1.0 VerticalGainSlider,
 * so SetProgress and keyboard semantics are kept. [contentDescription] must name the band ("40 Hz gain"); without it,
 * TalkBack announces all nine bands identically.
 *
 * The touch width equals the column the caller gives it (28.7 dp), an accepted exception below 48 dp (spec §5.4).
 * The layout is pinned to LTR so RTL mirroring can't flip the band upside down.
 */
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumbScale: () -> Float = { 1f },
) {
    val colors = MaterialTheme.colorScheme
    val activeColor = if (enabled) colors.primary else colors.sliderInactiveTrack
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .verticalSlider()
                .semantics { this.contentDescription = contentDescription },
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            thumb = {
                RingThumb(
                    diameter = RollaDimens.eqThumbDiameter,
                    ringWidth = RollaDimens.sliderThumbRing,
                    ringColor = activeColor,
                    fillColor = colors.surfaceContainer,
                    modifier = Modifier.graphicsLayer {
                        val scale = thumbScale()
                        scaleX = scale
                        scaleY = scale
                    },
                )
            },
            track = { state ->
                SliderTrackLine(
                    state = state,
                    thickness = RollaDimens.eqSliderTrack,
                    activeColor = activeColor,
                    inactiveColor = colors.sliderInactiveTrack,
                )
            },
            valueRange = valueRange,
        )
    }
}

/** Rotates a horizontal slider so its start sits at the bottom, swapping the measured width and height. */
private fun Modifier.verticalSlider(): Modifier = graphicsLayer {
    rotationZ = VERTICAL_ROTATION_DEGREES
    transformOrigin = TransformOrigin(0f, 0f)
}.layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        ),
    )
    layout(placeable.height, placeable.width) {
        placeable.place(-placeable.width, 0)
    }
}
```

- [ ] **Step 7: Run the tests and confirm they pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*Slider*"`
Expected: PASS (`SliderFractionTest` ×3, `SlidersTest` ×3).

- [ ] **Step 8: Checkpoint (no commit)**

Run: `./gradlew spotlessApply` and then `git status --short`. Report to the orchestrator.

---

### Task 8: OneUiTabRow, the center-weighted tab row

**Owner agent:** `m3-design-system-agent` (`model: "opus"`)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/OneUiTabRow.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/TabGeometryTest.kt`
- Test (create): `core/designsystem/src/test/kotlin/com/rolla/musicplayer/core/designsystem/component/OneUiTabRowTest.kt`

**Interfaces:**
- Consumes: `tabSelected`, `tabUnselected`, `tabUnselected` color and `RollaDimens.tabRowHeight/tabSpacing/tabLabelVerticalPadding`.
- Produces:
  - `@Composable fun OneUiTabRow(titles: List<String>, pagerState: PagerState, onTabClick: (Int) -> Unit, modifier: Modifier = Modifier)`
  - Pure geometry, internal and tested:
    - `fun tabEmphasis(position: Float, index: Int): Float`
    - `fun tabScale(minScale: Float, emphasis: Float): Float`
    - `fun interpolateAnchor(centers: List<Float>, position: Float): Float`
    - `fun tabOffsets(widths: List<Int>, spacing: Float, minScale: Float, position: Float, rowWidth: Int): List<Int>`
- Behavior:
  - Labels are measured at `tabSelected` and drawn scaled toward `tabUnselected` by `graphicsLayer`. Color lerps in the
    draw phase.
  - The interpolated current tab is centered, and neighbors clip at the edges.
  - Swiping the pager animates continuously through layout and draw only, with no recomposition per frame.
  - Placement uses `placeRelative`, so RTL mirrors the order.

- [ ] **Step 1: Write the failing geometry test**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class TabGeometryTest {

    @Test
    fun emphasisIsFullOnTheCurrentPageAndFadesLinearly() {
        assertEquals(1f, tabEmphasis(position = 2f, index = 2), 0.0001f)
        assertEquals(0f, tabEmphasis(position = 2f, index = 3), 0.0001f)
        assertEquals(0.5f, tabEmphasis(position = 2.5f, index = 3), 0.0001f)
        assertEquals(0f, tabEmphasis(position = 0f, index = 5), 0.0001f)
    }

    @Test
    fun scaleInterpolatesBetweenMinAndOne() {
        assertEquals(0.5f, tabScale(minScale = 0.5f, emphasis = 0f), 0.0001f)
        assertEquals(1f, tabScale(minScale = 0.5f, emphasis = 1f), 0.0001f)
        assertEquals(0.75f, tabScale(minScale = 0.5f, emphasis = 0.5f), 0.0001f)
    }

    @Test
    fun anchorInterpolatesBetweenNeighbourCentersAndClamps() {
        val centers = listOf(10f, 30f, 70f)
        assertEquals(30f, interpolateAnchor(centers, 1f), 0.0001f)
        assertEquals(50f, interpolateAnchor(centers, 1.5f), 0.0001f)
        assertEquals(70f, interpolateAnchor(centers, 9f), 0.0001f)
        assertEquals(10f, interpolateAnchor(centers, -2f), 0.0001f)
        assertEquals(0f, interpolateAnchor(emptyList(), 0f), 0.0001f)
    }

    @Test
    fun selectedTabIsCenteredInTheRow() {
        // widths 100 each, half-size neighbours: scaled widths 50 / 100 / 50, spacing 10.
        val offsets = tabOffsets(
            widths = listOf(100, 100, 100),
            spacing = 10f,
            minScale = 0.5f,
            position = 1f,
            rowWidth = 400,
        )
        assertEquals(200, offsets[1] + 100 / 2) // selected label centered on 400 / 2
        assertEquals(listOf(65, 150, 235), offsets)
    }

    @Test
    fun singleTabIsCenteredAndEmptyRowYieldsNoOffsets() {
        assertEquals(listOf(150), tabOffsets(listOf(100), 10f, 0.5f, 0f, 400))
        assertEquals(emptyList<Int>(), tabOffsets(emptyList(), 10f, 0.5f, 0f, 400))
    }
}
```

- [ ] **Step 2: Write the failing composable test (Review Focus 2 and 3)**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val TITLES = listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists", "Folders")

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OneUiTabRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentPageIsSelectedAndTappingANeighbourReportsItsIndex() {
        var clicked = -1
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                OneUiTabRow(
                    titles = TITLES,
                    pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                    onTabClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Tracks").assertIsSelected()
        composeRule.onNodeWithText("Albums").assertIsNotSelected().performClick()
        assertEquals(3, clicked)
    }

    @Test
    fun largeFontScaleDoesNotClipLabelsVertically() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    OneUiTabRow(
                        titles = TITLES,
                        pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                        onTabClick = {},
                    )
                }
            }
        }
        val row = composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .getUnclippedBoundsInRoot()
        val label = composeRule.onNodeWithText("Tracks").getUnclippedBoundsInRoot()
        assertTrue("label top ${label.top} above row top ${row.top}", label.top >= row.top)
        assertTrue("label bottom ${label.bottom} below row bottom ${row.bottom}", label.bottom <= row.bottom)
    }

    @Test
    fun rtlMirrorsTabOrder() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RollaMusicPlayerTheme(darkTheme = true) {
                    OneUiTabRow(
                        titles = TITLES,
                        pagerState = rememberPagerState(initialPage = 2) { TITLES.size },
                        onTabClick = {},
                    )
                }
            }
        }
        val tracks = composeRule.onNodeWithText("Tracks").getUnclippedBoundsInRoot()
        val albums = composeRule.onNodeWithText("Albums").getUnclippedBoundsInRoot()
        assertTrue("in RTL the next tab sits to the left", albums.right <= tracks.left)
    }
}
```

- [ ] **Step 3: Run the tests and confirm they fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*Tab*"`
Expected: compilation FAILS with `Unresolved reference: tabEmphasis` (and `OneUiTabRow`).

- [ ] **Step 4: Create `component/OneUiTabRow.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.tabSelected
import com.rolla.musicplayer.core.designsystem.theme.tabUnselected
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One UI's center-weighted tab row (spec §6/§7.2). The current tab is centered, large and onSurface; its neighbors
 * shrink toward tabUnselected and gray out, and clip at the screen edges. There is no indicator. All motion follows
 * the pager's position (current page + offset fraction): layout handles placement, and draw handles scale and color,
 * so a swipe animates continuously without recomposing.
 */
@Composable
fun OneUiTabRow(
    titles: List<String>,
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedStyle = MaterialTheme.typography.tabSelected
    val minScale = MaterialTheme.typography.tabUnselected.fontSize.value / selectedStyle.fontSize.value
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val unselectedColor = MaterialTheme.colorScheme.tabUnselected
    val position: () -> Float = { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    Layout(
        content = {
            titles.forEachIndexed { index, title ->
                TabLabel(
                    title = title,
                    selected = pagerState.currentPage == index,
                    emphasis = { tabEmphasis(position(), index) },
                    minScale = minScale,
                    style = selectedStyle,
                    colors = TabLabelColors(selected = selectedColor, unselected = unselectedColor),
                    onClick = { onTabClick(index) },
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.tabRowHeight)
            .clipToBounds()
            .selectableGroup(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val height = maxOf(constraints.minHeight, placeables.maxOfOrNull { it.height } ?: 0)
        layout(constraints.maxWidth, height) {
            val offsets = tabOffsets(
                widths = placeables.map { it.width },
                spacing = RollaDimens.tabSpacing.toPx(),
                minScale = minScale,
                position = position(),
                rowWidth = constraints.maxWidth,
            )
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = offsets[index], y = (height - placeable.height) / 2)
            }
        }
    }
}

private data class TabLabelColors(val selected: Color, val unselected: Color)

@Suppress("LongParameterList")
@Composable
private fun TabLabel(
    title: String,
    selected: Boolean,
    emphasis: () -> Float,
    minScale: Float,
    style: TextStyle,
    colors: TabLabelColors,
    onClick: () -> Unit,
) {
    BasicText(
        text = title,
        modifier = Modifier
            .graphicsLayer {
                val scale = tabScale(minScale, emphasis())
                scaleX = scale
                scaleY = scale
            }
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = RollaDimens.tabLabelVerticalPadding),
        style = style,
        maxLines = 1,
        color = { lerp(colors.unselected, colors.selected, emphasis()) },
    )
}

/** 1 on the current page, falling linearly to 0 one page away. */
internal fun tabEmphasis(position: Float, index: Int): Float = (1f - abs(position - index)).coerceIn(0f, 1f)

/** Label scale for an emphasis: [minScale] when unselected, 1 when selected. */
internal fun tabScale(minScale: Float, emphasis: Float): Float = minScale + (1f - minScale) * emphasis

/** The x the row centers on: interpolated between the two tab centers around [position], clamped to the ends. */
internal fun interpolateAnchor(centers: List<Float>, position: Float): Float {
    if (centers.isEmpty()) return 0f
    val clamped = position.coerceIn(0f, (centers.size - 1).toFloat())
    val lower = floor(clamped).toInt()
    val upper = min(lower + 1, centers.size - 1)
    return centers[lower] + (centers[upper] - centers[lower]) * (clamped - lower)
}

/**
 * Left edge (px, LTR) of each label's unscaled box. Labels are laid out by their scaled widths plus [spacing], then
 * shifted so the interpolated current tab sits at the row center. graphicsLayer scales around the box center, so the
 * box is placed with its center on the scaled slot's center.
 */
internal fun tabOffsets(
    widths: List<Int>,
    spacing: Float,
    minScale: Float,
    position: Float,
    rowWidth: Int,
): List<Int> {
    if (widths.isEmpty()) return emptyList()
    val centers = ArrayList<Float>(widths.size)
    var cursor = 0f
    widths.forEachIndexed { index, width ->
        val scaled = width * tabScale(minScale, tabEmphasis(position, index))
        centers += cursor + scaled / 2f
        cursor += scaled + spacing
    }
    val shift = rowWidth / 2f - interpolateAnchor(centers, position)
    return widths.mapIndexed { index, width -> (shift + centers[index] - width / 2f).roundToInt() }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*Tab*"`
Expected: PASS (`TabGeometryTest` ×5, `OneUiTabRowTest` ×3).

If `rtlMirrorsTabOrder` fails because the bounds report pre-mirroring coordinates, **stop and report it to the
orchestrator**. Do not weaken the assertion; RTL order is a Review Focus requirement.

- [ ] **Step 6: Checkpoint (no commit)**

Run: `./gradlew spotlessApply` and then `git status --short`. Report to the orchestrator.

---

### Task 9: Kit previews and the Phase 1 gate

**Owner agent:** `m3-design-system-agent` (`model: "opus"`); review by `code-reviewer` (`model: "opus"`)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component/KitPreviews.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–8.
- Produces: previews only. No API.

- [ ] **Step 1: Create `component/KitPreviews.kt`**

```kotlin
package com.rolla.musicplayer.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingGradient

private val PreviewTabs = listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists", "Folders")

@Suppress("UnusedPrivateMember")
@Preview(name = "Kit - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 1100)
@Preview(name = "Kit - Light", uiMode = Configuration.UI_MODE_NIGHT_NO, heightDp = 1100)
@Preview(name = "Kit - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, heightDp = 1300)
@Composable
private fun PreviewOneUiKit() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                KitHeaderSection()
                KitPanelSection()
                KitControlsSection()
                KitIconsSection()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .nowPlayingBackground(MaterialTheme.colorScheme.nowPlayingGradient),
                )
            }
        }
    }
}

@Composable
private fun KitHeaderSection() {
    OneUiTopBar(title = "Rolla Music", windowInsets = WindowInsets(0)) {
        OneUiIconButton(icon = RollaIcons.Add, contentDescription = "Add", onClick = {})
        OneUiIconButton(icon = RollaIcons.Search, contentDescription = "Search", onClick = {})
        OneUiIconButton(icon = RollaIcons.More, contentDescription = "More", onClick = {})
    }
    OneUiTabRow(
        titles = PreviewTabs,
        pagerState = rememberPagerState(initialPage = 2) { PreviewTabs.size },
        onTabClick = {},
    )
    OneUiDetailTopBar(title = "Equaliser", onNavigateUp = {}, windowInsets = WindowInsets(0))
}

@Composable
private fun KitPanelSection() {
    ContentPanel(modifier = Modifier.height(170.dp)) {
        SortHeader(label = "Name") {
            CircleIconButton(icon = RollaIcons.Shuffle, contentDescription = "Shuffle", onClick = {})
            CircleIconButton(icon = RollaIcons.Play, contentDescription = "Play", onClick = {})
        }
        Row(Modifier.padding(start = RollaDimens.listThumbStart)) {
            ArtworkPlaceholder(modifier = Modifier.size(RollaDimens.listThumb))
        }
        InsetDivider(startInset = RollaDimens.listTextStart, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun KitControlsSection() {
    var checked by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var gain by remember { mutableFloatStateOf(300f) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OneUiSwitch(checked = checked, onCheckedChange = { checked = it })
            OneUiSwitch(checked = !checked, onCheckedChange = { checked = !it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RollaDimens.chipGap)) {
            PillChip(label = "Balanced", selected = true, onClick = {}, modifier = Modifier.width(142.dp))
            PillChip(label = "Bass boost", selected = false, onClick = {}, modifier = Modifier.width(142.dp))
        }
        OneUiSlider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2f)
        Box(Modifier.size(width = RollaDimens.eqColumnPitch, height = 160.dp)) {
            EqVerticalSlider(
                value = gain,
                onValueChange = { gain = it },
                valueRange = -1500f..1500f,
                contentDescription = "40 Hz gain",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KitIconsSection() {
    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RollaIcons.all.forEach { icon ->
            Icon(imageVector = icon, contentDescription = icon.name, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
```

- [ ] **Step 2: Run the full Phase 1 gate**

Run these in two separate shell invocations (back to back, they can hit the Spotless fingerprint flake):

```bash
./gradlew spotlessApply
```

```bash
./gradlew check assembleDebug
```

Expected: BUILD SUCCESSFUL.
- `check` runs every module's unit tests, detekt, Spotless and lint. All 30+ new `:core:designsystem` tests pass, and every
  existing suite stays green.
- If an existing feature test asserts an old token value (for example a hardcoded `#1C1C1E` or navy `onPrimary`), update the
  expectation to the new token **by name** and list the change in the report. Never delete the test.

- [ ] **Step 3: Confirm there are no stray hardcoded values in the new code**

Run: `grep -rnE "Color\(0x|[0-9]+\.dp" core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/component core/designsystem/src/main/kotlin/com/rolla/musicplayer/core/designsystem/motion`
Expected output: only `KitPreviews.kt` lines (preview scaffolding sizes) and the `0.dp` defaults
(`InsetDivider endInset`, `OneUiSwitch` thumb rest). No color literals.

- [ ] **Step 4: Phase review (orchestrator)**

The orchestrator dispatches `code-reviewer` (`model: "opus"`) over `git diff` for `core/designsystem`, with spec §5/§6 and
this plan. Acceptance needs four things:
1. No CRITICAL or HIGH findings.
2. The gate from Step 2 is green.
3. The Interfaces blocks of Tasks 1–8 match the code exactly (name for name, so Phase 2 can rely on them).
4. The orchestrator's own read of every new file against spec §5/§6 (token names, values, signatures).

On-screen visual verification is **not** part of Phase 1. The kit first renders inside the app in Phase 2, whose plan
includes an on-device screenshot comparison over adb. The user may also open `KitPreviews.kt` in Android Studio at any
time.

- [ ] **Step 5: Ready-to-commit report (no commit)**

The orchestrator reports to the user: the files changed, the test counts, the gate result, and this prepared message:

```
feat(designsystem): One UI tokens, icon set and component kit

Measured One UI palette (AA-audited by ContrastTest), type scale, shapes and
dimensions; 27 thin-stroke RollaIcons; component kit (top bars, tab row,
content panel, sort header, circle buttons, switch, chip, sliders, artwork
placeholder, Now Playing background, mini-player inset local). Phase 1 of
docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

Then stop and wait for the user to commit, or to tell the orchestrator to commit.
