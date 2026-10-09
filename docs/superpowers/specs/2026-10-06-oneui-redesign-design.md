# One UI redesign: design spec

- **Date:** 2026-10-06
- **Branch:** `feat/oneui-redesign`
- **Status:** **approved by the user on 2026-10-06** (spec and measurements). Next step: the implementation plan.
- **Reference:** the screenshots in `media player example/` (Samsung Music, One UI). Raw color and geometry
  data is in [`docs/design/oneui-measurements.md`](../../design/oneui-measurements.md).

## 1. Goal and success criteria

Make RollaMusicPlayer look and behave like the reference screenshots as closely as project constraints allow. That covers
colors, typography, shapes, tabs, buttons and controls, and every screen.

Done means all of the following:

1. Every screen with a reference screenshot passes the device calibration check in §14. On the same phone, key geometry
   is within ±2 dp and token colors match the measured values (or the documented AA-safe substitute).
2. Screens without a reference screenshot use only the new tokens and the component kit. Nowhere in the app is a visual value hardcoded.
3. The three formerly cut features ship working: the tab bar, Folders and the A–Z rail.
4. No dead controls. Every visible button does something real (§12).
5. All gates are green (§15), including the no-INTERNET release-manifest check. WCAG AA holds for every text pairing.

## 2. User decisions (binding)

| Decision | Choice |
|---|---|
| Approach | Tokens and components first, then screens, then new tabs |
| Formerly cut features | Build all three: center-weighted tab bar (replaces the M3 bottom nav), Folders tab, A–Z fast-scroll rail |
| Library tab label | **"Tracks"** in the UI, including counts such as "74 tracks". The domain model stays `Song`; only user-facing strings change. |
| Widget | Keeps prev · −15 s · play/pause · +15 s · next. Adopts the example's visual style only. |
| Icons | Custom thin-stroke `ImageVector`s, not Material icons |
| Accent blue | AA-safe **`#2F6FF0`** with white text (4.51:1) instead of the screenshot's `#377AFF` (3.9:1) |
| Calibration | Over adb, on the phone that took the screenshots |
| Queue button | Build a real queue sheet |
| App title | "Rolla Music". Never "Samsung Music" or any Samsung branding. |
| Touch targets | EQ slider columns (28.7 dp) and the A–Z rail (30 dp) are accepted below 48 dp to keep the exact look (§5.4) |
| Spelling | The UI says "Equaliser" (British, matching the reference). Code identifiers stay `Equalizer*`. |
| Font | System default. On the Samsung reference device that renders as One UI's own font. No bundled font. |
| Agents | The orchestrator is the main session, running Opus 5.5 by the user's choice (Fable was considered and not kept). All implementation and review subagents are dispatched with `model: "opus"`. The orchestrator verifies every result before accepting it. |
| Commits | Only when the user explicitly asks. This applies to this spec too. |

## 3. Non-goals

- No settings rows that exist only in the screenshot (sleep timer, crossfade, lock-screen control, queue settings,
  duplicate-song rule, manage tabs, external-device start). These follow the standing "no decorative toggles"
  principle. Only existing settings rows are restyled.
- No cast or "play on other device" icon on Now Playing. The app is offline.
- The "Name" sort label stays a static label. No sort menu.
- No palette-from-artwork color extraction. The Now Playing gradient is static tokens.
- No queue reordering or removal. The queue sheet is view plus tap-to-jump.
- SD-card volumes are not told apart in Folders (§9.4).
- The light theme gets full token parity but is not calibrated against a reference, since none exists.

## 4. Architecture overview

| Concern | Module | Notes |
|---|---|---|
| Color, type, shape and dimension tokens | `:core:designsystem` (`theme/`) | `Color.kt`, `Type.kt`, `Shape.kt`, new `Dimens.kt`, `Theme.kt` |
| Icon set | `:core:designsystem` (`icon/RollaIcons.kt`) | Model-agnostic `ImageVector`s |
| One UI component kit | `:core:designsystem` (`component/`) | Model-agnostic composables (§6) |
| Model-aware rows and cards | `:core:ui` | `SongListItem`, `PlaylistRow`, `AlbumRow`, `ArtistRow`, `AlbumCard`, new `FolderRow`, `FeatureCard`, `SongSelectionState` |
| Home shell (header, tab row, pager) | `:app` (`home/`) | Composes tab contents from the features. The only place allowed to know every feature. |
| Tracks / Albums / Artists / Folders tab content, FolderDetail | `:feature:library` | Each tab is a stateful composable with its own ViewModel |
| Favourites / Playlists tab content | `:feature:playlists` | Same |
| Mini-player, Now Playing, queue sheet | `:feature:player` | |
| Queue state, skip-to-index | `:core:media` (`PlaybackController`) | |
| Folder column, migration v4, new DAO queries | `:core:database` | |
| Folder path extraction, backfill | `:core:data` (scanner, `LibraryIndexer`) | |
| `Folder` model, `Artist.artworkUri` | `:core:model` | Update `model-vocabulary.md` at the same time |

The dependency rules are unchanged: features never depend on each other, and `:core` never depends on a feature.

## 5. Tokens

### 5.1 Colors

Dark is the primary theme. Light is derived parity. Every value below replaces the current one in `Color.kt`.
The WCAG notes in `Color.kt` must be rewritten to match these pairings.

**Material roles**

| Role | Dark | Light | Notes |
|---|---|---|---|
| `background` | `#000000` | `#F4F4F6` | |
| `onBackground` | `#FCFCFE` | `#111113` | |
| `surface` | `#000000` | `#F4F4F6` | Same as background in One UI |
| `onSurface` | `#FCFCFE` | `#111113` | |
| `surfaceVariant` | `#2D2D2F` | `#E8E8EA` | |
| `onSurfaceVariant` | `#9B9B9D` | `#646467` | Dark 6.45:1 on `#171719` and 4.95:1 on `#2D2D2F`. Light 5.37:1 on `#F4F4F6` and 4.82:1 on `#E8E8EA`. |
| `surfaceContainerLowest` | `#000000` | `#FFFFFF` | |
| `surfaceContainerLow` | `#0F0F10` | `#FAFAFB` | |
| `surfaceContainer` | **`#171719`** | `#FFFFFF` | Panel, cards, EQ card |
| `surfaceContainerHigh` | **`#2D2D2F`** | `#E8E8EA` | Circle buttons, unselected chips, sheets, dialogs, menus |
| `surfaceContainerHighest` | **`#333333`** | `#EBEBED` | A–Z rail track |
| `primary` | **`#2F6FF0`** | `#2F6FF0` | AA-safe substitute for `#377AFF`. White on it is 4.51:1. As a non-text element on `#171719` it is 3.97:1. |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | Replaces the dark navy `#001B3F` |
| `primaryContainer` / `onPrimaryContainer` | `#1E3A66` / `#D6E2FF` | `#D6E2FF` / `#001A41` | Unchanged |
| `secondary` / `onSecondary` | `#9B9B9D` / `#000000` | `#646467` / `#FFFFFF` | |
| `secondaryContainer` / `onSecondaryContainer` | `#2D2D2F` / `#FCFCFE` | `#E8E8EA` / `#111113` | |
| `tertiary` / `onTertiary` | `= primary / onPrimary` | same | |
| `outline` | `#5A5A5C` | `#8A8A8E` | |
| `outlineVariant` | **`#3A3A3C`** | `#DEDEE0` | Dividers |
| `error` / `onError` | `#FF5A5A` / `#000000` | `#BA1A1A` / `#FFFFFF` | Unchanged |

**Semantic extensions.** These are `ColorScheme.xxx` getters that branch on `LocalRollaDarkTheme`, like the existing ones.

| Token | Dark | Light | Use |
|---|---|---|---|
| `accentText` | `#6094FF` | `#1F5FE0` | Blue value and link text (settings values, the slider value, active queue item). The measured `#5B8FFD` is nudged to `#6094FF`, because the measured value only reaches 4.45:1 on `#2D2D2F` sheets. Dark is 6.12:1 on `#171719` and 4.70:1 on `#2D2D2F`. Light is 5.57:1 on white and 4.55:1 on `#E8E8EA`. **Never use `primary` for text.** |
| `tabUnselected` | `#7E7E80` | `#646467` | Unselected tab labels. 5.18:1 on black, 5.37:1 on `#F4F4F6`. |
| `artworkPlaceholder` | `#454547` | `#E6E6E8` | List thumbnails, feature cards, folder thumbs |
| `artworkPlaceholderLarge` | `#3B3B3B` | `#E9E9EB` | Now Playing artwork placeholder |
| `artworkPlaceholderGlyph` | `#FCFCFE` | `#7A7A7E` | The note glyph on placeholders. Dark: 9.34:1. Light: 3.43:1 (white on light gray would only reach 1.4:1). |
| `miniPlayerContainer` | `#282035` | `#E9E4F2` | Mini-player pill |
| `miniPlayerArtPlaceholder` | `#FFFFFF` at 18 % alpha | `#000000` at 10 % alpha | Art circle over the pill |
| `miniPlayerArtGlyph` | `#FCFCFE` | `#707074` | Note glyph on the mini-player art circle. 8.53:1 dark and 3.16:1 light over the composited circle. White would only reach 1.6:1 in light (Phase 1 audit). |
| `sliderInactiveTrack` | **`#646466`** | `#8A8A8E` | EQ, seek-free sliders, settings slider, switch-off track. Raised from the measured `#5F5F61` so it clears the 3:1 non-text floor (3.03:1 on `#171719`). It still matches the measured switch-off track `#646368`. |
| `switchThumb` | `#FCFCFE` | `#FFFFFF` | One UI switch thumb. ≥3:1 on both the on and off tracks. |
| `fastScrollTrack` | `#333333` | `#F0F0F2` | A–Z rail |
| `fastScrollIndex` | `= onSurfaceVariant` | same | A–Z letters. Dark 4.55:1 on `#333333`; light 5.18:1 on `#F0F0F2`. |
| `eqGridLine` | `#1D1D1F` | `#EEEEF0` | Faint horizontal EQ grid |
| `seekTrackActive` | `#FCFCFE` | `#111113` | Now Playing seekbar active part and thumb |
| `seekTrackInactive` | `#FFFFFF` at **37 %** | `#000000` at **44 %** | Now Playing seekbar remainder. Raised from 30 % / 25 % (2.6–2.7:1 / 1.8:1 on the gradient) to clear 3:1 over both `middle` and `washEnd` (Phase 1 audit). |
| `nowPlayingGradient` | top `#000000` → `#120F16` at 55 % height. Bottom wash: left `#231C2C`, right `#293332`. | top `#FFFFFF` → `#F7F5FA` at 55 %. Bottom wash: left `#EFE9F5`, right `#E9F1EF`. | Exposed as a small data class with a `Modifier.nowPlayingBackground()` helper (§6) |
| `widget*` | see §13 | — | Glance cannot read Compose tokens. The widget keeps its own mapped `ColorProvider`s. |

`miniPlayerContainer` must be re-audited. Text on the pill is `onSurface` in both themes: dark `#FCFCFE` on `#282035` is 14.6:1, and light `#111113` on `#E9E4F2` is about 15:1.

### 5.2 Typography

The font family is `FontFamily.Default`. The material roles are reset to One UI sizes, and every semantic style below is
defined explicitly in `Type.kt` (not as an alias of a role) so call sites read `MaterialTheme.typography.<name>`.
Color is always applied at the call site. The sizes are **provisional** until calibration (§14).

| Semantic style | Size (sp) | Weight | Line height | Default color | Where |
|---|---|---|---|---|---|
| `appTitle` | 22 | Bold | 28 | onSurface | Home header "Rolla Music" |
| `screenTitle` | 22 | Bold | 28 | onSurface | Pushed screens (Equaliser, Settings, details) |
| `tabSelected` | 23 | Normal | 30 | onSurface | Selected tab |
| `tabUnselected` | 14 | Normal | 20 | tabUnselected | Other tabs. The tab row lerps between the two styles. |
| `sortLabel` | 14 | Medium | 20 | onSurfaceVariant | Sort header "Name" |
| `songTitle` | 17 | Normal | 22 | onSurface | All list row titles |
| `artistName` | 13 | Normal | 18 | onSurfaceVariant | All list row subtitles |
| `rowTrailing` | 13 | Normal | 18 | onSurfaceVariant | "0 tracks" trailing counts |
| `miniPlayerTitle` | 16 | SemiBold | 21 | onSurface | Mini-player title |
| `miniPlayerSubtitle` | 12 | Normal | 16 | onSurface | Mini-player artist (white) |
| `nowPlayingTitle` | 21 | Normal | 27 | onSurface | Now Playing title |
| `nowPlayingArtist` | 14 | Normal | 19 | onSurface | Now Playing artist (white) |
| `metadata` | 11 | Normal | 14 | onSurface | Seek time labels |
| `sectionHeader` | 13 | Medium | 18 | onSurfaceVariant | Settings and sheet section headers |
| `settingTitle` | 17 | Normal | 22 | onSurface | Settings row label |
| `settingValue` | 14 | Normal | 19 | accentText | Blue value under a label |
| `settingSubtitle` | 14 | Normal | 19 | onSurfaceVariant | Gray sub-label |
| `chipLabel` | 17 | Bold | 22 | onPrimary / onSurface | EQ preset chips |
| `caption` | 14 | Normal | 20 | onSurface | EQ preset description |
| `eqValue` | 14 | Normal | 18 | onSurfaceVariant | Band value above each slider |
| `eqFrequency` | 12 | Normal | 16 | onSurfaceVariant | Band frequency below each slider |
| `featureCardLabel` | 14 | Normal | 19 | onSurface | Playlist feature card label (centered) |
| `featureCardCount` | 12 | Normal | 16 | onSurfaceVariant | Feature card count (centered) |
| `fastScrollLetter` | 11 | Normal | 13 | fastScrollIndex | A–Z letters |
| `emptyState` | 17 | Normal | 22 | onSurfaceVariant | Empty-state messages |

M3 base roles: `headlineMedium` = 22/Bold, `headlineSmall` = 21/Normal, `titleLarge` = 23/Normal,
`titleMedium` = 17/Normal, `bodyLarge` = 17/Normal, `bodyMedium` = 14/Normal, `bodySmall` = 13/Normal,
`labelLarge` = 14/Medium (buttons), `labelMedium` = 13/Medium, `labelSmall` = 11/Normal. Stock M3 components
(dialogs, menus, buttons) then render at One UI sizes without overrides.

### 5.3 Shapes

| Token | Radius | Applied to |
|---|---|---|
| `extraSmall` | 8 dp | Small inner elements |
| `small` | 11 dp | List thumbnails (48 dp box) |
| `medium` | 16 dp | Dialog inner elements |
| `large` | 26 dp | Content panel (top corners only), settings cards, EQ card |
| `extraLarge` | 26 dp | Now Playing artwork (168 dp box; measured ≈25 dp), plus stock M3 dialogs and sheets, which default to `extraLarge` |
| `featureCard` (semantic `Shapes` extension) | 20 dp | Playlist feature cards and album grid cards |
| `CircleShape` | 50 % | Chips, mini-player pill, circle buttons, A–Z rail, switch, thumbs |

### 5.4 Dimensions

A new `Dimens.kt` defines `object RollaDimens`. All sizes are provisional until calibration. Screens read these and
never declare private `dp` literals for anything this table covers.

| Name | Value | Name | Value |
|---|---|---|---|
| `screenEdge` | 24 dp | `headerTitleStart` | 24 dp |
| `headerHeight` | 64 dp | `tabRowHeight` | 56 dp |
| `tabSpacing` | 28 dp | `panelTopGap` | 12 dp |
| `panelRadius` | 26 dp | `sortHeaderHeight` | 56 dp |
| `sortHeaderStart` | 19 dp (box start: measured 22 dp glyph start − 3 dp glyph inset in the 24 dp icon box) | `circleButtonSize` | 34 dp |
| `circleButtonGap` | 14 dp | `circleButtonEnd` | 15 dp |
| `circleButtonGlyph` | 18 dp | `listThumb` | 48 dp |
| `listThumbStart` | 18 dp | `listTextStart` | 82 dp |
| `listRowHeight` | 70 dp | `singleLineRowHeight` | 56 dp |
| `dividerThickness` | 1 dp | `overflowGlyph` | 20 dp |
| `fastScrollWidth` | 22 dp | `fastScrollEnd` | 8 dp |
| `miniPlayerHeight` | 60 dp | `miniPlayerSideMargin` | 4 dp |
| `miniPlayerArt` | 38 dp | `miniPlayerArtStart` | 11 dp |
| `npArtSize` | 168 dp | `npArtTop` | 130 dp below the top of the screen content |
| `npSeekThickness` | 3 dp | `npSeekThumb` | 16 dp |
| `npSeekInset` | 29 dp | `npPlayGlyph` | 40 dp (inside a 64 dp touch target) |
| `npTransportGlyph` | 26 dp | `npActionGlyph` | 26 dp |
| `eqCardTop` | 16 dp below the top bar | `eqSliderTrack` | 3 dp |
| `eqSliderLength` | 237 dp | `eqThumbDiameter` | 13 dp |
| `sliderThumbRing` (both the EQ and settings sliders) | 2 dp | `eqColumnPitch` | 28.7 dp (measured, fixed; the 9 columns are centered as a group in the card) |
| `eqGridPitch` | 24 dp | `chipHeight` | 40 dp |
| `chipGap` | 17 dp | `chipGridStart` | 24 dp |
| `settingsCardInset` | 10 dp | `settingsRowPadding` | 19 dp |
| `settingsDividerInset` | 16 dp | `settingsSliderTrack` | 14 dp |
| `settingsSliderThumb` | 20 dp | `switchTrackWidth` | 35 dp |
| `switchTrackHeight` | 17 dp | `switchThumb` | 15 dp + `switchThumbInset` 1 dp (measured ≈16 dp; 16 + 2 × 1 would not fit the 17 dp track) |
| `featureCardSize` | 143 dp | `featureCardGap` | 17 dp |
| `featureCardGlyph` | 44 dp | `minTouchTarget` | 48 dp |
| `listOverflowEnd` | 26 dp (end of the ⋮ 48 dp box; reaches 4 dp into the rail strip) | `listTrailingEnd` | 43 dp (trailing text end) |
| `listDividerEnd` | = `fastScrollWidth` + `fastScrollEnd` (30 dp) | `featureCardLabelGap` | 8 dp (unmeasured; Phase 7) |
| `featureCardRowStart` | = `listThumbStart` (unmeasured; Phase 7) | | |

**Touch targets.** Glyphs keep their measured visual size, but every interactive element is at least 48 × 48 dp, through
`minimumInteractiveComponentSize()` or padding. Examples: the 34 dp circle buttons, the 40 dp chips and the 35 dp switch (whose
whole row is the target).

**Derived layout tokens.** Each control occupies a 48 dp box, so the measured *visual* gaps and insets above are
reproduced by derived tokens in `RollaDimens`. Lay controls out with these; never use the visual values directly:
`sortHeaderEnd` = `circleButtonEnd` − (48 − `circleButtonSize`)/2 (the end padding before the last circle button's box);
`circleButtonLayoutGap` = `circleButtonGap` − (48 − `circleButtonSize`) (`spacedBy` between circle buttons); and
`chipRowLayoutGap` = `chipGap` − (48 − `chipHeight`) (the vertical spacing between chip rows; horizontal spacing stays
`chipGap` because chips are wider than 48 dp). Each one is clamped at 0, and `TokenInvariantsTest` guards the clamps.

There are **two documented exceptions**, where the reference layout leaves less than 48 dp of horizontal room:

1. EQ slider columns. The touch width equals `eqColumnPitch` (28.7 dp); the height is the full 237 dp track.
2. The A–Z rail. The touch width is `fastScrollWidth + fastScrollEnd` (30 dp), so it doesn't overlap the rows' ⋮ targets.

Both still exceed the WCAG 2.2 AA target minimum (24 dp), and both keep full semantic-action alternatives: per-band
increase/decrease, and per-section "Jump to X". This regresses the v1.0 "≥48 dp everywhere" rule for these two controls only.
**The user accepted this exception on 2026-10-06 in exchange for the exact reference look.** Record it in `ui-style-guide.md`
as a documented exception.

### 5.5 Icons (`RollaIcons`)

A new `:core:designsystem/icon/RollaIcons.kt` holds hand-authored `ImageVector`s on a 24 × 24 viewport.

- **Line icons:** 1.6 stroke, round caps and round joins, about 20 dp live area, no fill.
- **Solid glyphs:** filled paths. The reference shows only slight corner rounding; Phase 7 calibration decides whether
  the Skip triangles need it.
- **RTL:** `ChevronBack`, `Sort`, `Queue`, `PlaylistAdd` and `Volume` auto-mirror (parity with the v1.0
  `Icons.AutoMirrored` icons they replace). Transport glyphs, `Shuffle`, `Repeat*` and `PlayOrder` never mirror.

| Icon | Style | Icon | Style |
|---|---|---|---|
| `Search` | line | `More` (⋮, three dots: about 3 dp in the 24 dp top bar, about 2.2 dp at the 20 dp list `overflowGlyph`; Phase 7 calibrates) | solid |
| `Add` (+) | line | `Sort` (down arrow + three lines of decreasing length) | line |
| `Shuffle` | line | `Queue` (three lines + a note with a solid head) | line + solid head |
| `ChevronBack` (`<`, thin) | line | `ChevronDown` (`v`, thin) | line |
| `Volume` (speaker + two arcs) | line | `EqualizerBars` (five vertical bars of varying height) | line |
| `Heart` / `HeartFilled` | line / solid | `PlayOrder` ("A" over a right arrow) | line |
| `Repeat` / `RepeatOne` | line | `Play` / `Pause` | solid |
| `SkipPrevious` / `SkipNext` (bar + two triangles, ⏮ ⏭) | solid | `Replay15` / `Forward15` (widget, Now Playing ±15 if used) | line |
| `MusicNote` (placeholder glyph) | line, 2.0 stroke | `FolderBadge` (small folder outline) | line |
| `Close`, `Edit`, `Check`, `PlaylistAdd`, `Album`, `Person` | line | | |

Material icons stay only where no visible One UI equivalent exists, such as the tag-editor form affordances. Each one is listed in the
plan as an explicit exception.

## 6. Component kit (`:core:designsystem/component/`)

These are model-agnostic, take a `modifier`, read only tokens, and come with previews in dark, light and large font. The signatures are
indicative; the plan may refine names, but not responsibilities.

```kotlin
// Home header: bold title left, trailing action icons (each a 48 dp target)
@Composable fun OneUiTopBar(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {})

// Pushed-screen bar: thin "<" chevron + bold title (screenTitle), optional actions
@Composable fun OneUiDetailTopBar(title: String, onNavigateUp: () -> Unit, modifier: Modifier = Modifier,
                                  actions: @Composable RowScope.() -> Unit = {})

// Center-weighted tab row. Selected tab centered; label style lerps tabUnselected→tabSelected and color
// tabUnselected→onSurface using the pager position (current page + offset fraction), so a swipe animates continuously.
// Neighbors clip at the screen edges. No indicator. Tapping a tab animates the pager to it.
@Composable fun OneUiTabRow(titles: List<String>, pagerState: PagerState, onTabClick: (Int) -> Unit,
                            modifier: Modifier = Modifier)

// Edge-to-edge surfaceContainer with panelRadius on the top corners only; extends to the bottom of the screen
@Composable fun ContentPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)

// "⇅ Name" static label + optional trailing slot (circle buttons)
@Composable fun SortHeader(label: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {})

// 34 dp surfaceContainerHigh circle, onSurface glyph, 48 dp touch target
@Composable fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit,
                                 modifier: Modifier = Modifier)

// A–Z rail (§10). Pure index logic lives in FastScrollIndex.kt and is unit-tested.
@Composable fun FastScrollRail(sections: List<FastScrollSection>, onSectionSelected: (FastScrollSection) -> Unit,
                               modifier: Modifier = Modifier)

// Settings slider: 14 dp rounded track (primary active / sliderInactiveTrack), 20 dp thumb with background fill and a
// 2 dp primary ring. Built on M3 Slider with custom track/thumb so semantics and keyboard support are kept.
@Composable fun OneUiSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier,
                            valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0,
                            onValueChangeFinished: (() -> Unit)? = null)

// Vertical EQ slider: 3 dp track (primary below the thumb, sliderInactiveTrack above), 13 dp hollow ring thumb
// (2 dp primary ring, surfaceContainer center). Keeps the current a11y semantics (per-band labels, ±step actions).
@Composable fun EqVerticalSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>,
                                 contentDescription: String, modifier: Modifier = Modifier,
                                 onValueChangeFinished: (() -> Unit)? = null)

// One UI switch: 35×17 dp pill track (primary when on, sliderInactiveTrack when off), 16 dp white thumb, spring motion.
// Role.Switch semantics. Use inside toggleable rows with onCheckedChange = null.
@Composable fun OneUiSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier)

// EQ preset chip: 40 dp pill, chipLabel; selected = primary/onPrimary, unselected = surfaceContainerHigh/onSurface.
// Color animates with a spring (reduced-motion aware, like today).
@Composable fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)

// Gray rounded box with a centered note glyph (artworkPlaceholder + artworkPlaceholderGlyph)
@Composable fun ArtworkPlaceholder(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small,
                                   large: Boolean = false, glyphSize: Dp = 24.dp)

// Inset hairline in outlineVariant
@Composable fun InsetDivider(startInset: Dp, modifier: Modifier = Modifier, endInset: Dp = 0.dp)

// Now Playing background: vertical base + bottom left→right wash, drawn with drawBehind (no recomposition)
fun Modifier.nowPlayingBackground(gradient: NowPlayingGradient): Modifier
```

The settings rows in `:feature:settings` (`SettingsSectionHeader`, `SettingsSectionCard`, `SettingsToggleRow`,
`SettingsValueNavRow`, `SettingsSliderRow`, `SettingsRadioOptionRow`) are rebuilt on these components and keep their public API.

## 7. Home shell and navigation

### 7.1 Routes

- Add `@Serializable data object Home : Route` as the **start destination**.
- **Remove** the `Library` and `Playlists` routes and the M3 `RollaBottomNavigationBar`.
- Add `@Serializable data class FolderDetail(val folderPath: String) : Route`. It is reached from the Folders tab, and back is `navigateUp()`.
- Everything else is unchanged. The `rollamusic://search` deep link keeps its base path.

### 7.2 `HomeScreen` (`:app/home/`)

```
┌ status bar ─────────────────────────────────────┐
│ Rolla Music                        [+] 🔍  ⋮     │  OneUiTopBar (+ only on the Playlists tab)
│ vourites  Playlists   Tracks   Albums  Artists   │  OneUiTabRow (selected centered)
│ ╭──────── ContentPanel (edge to edge) ────────╮  │
│ │  HorizontalPager: one page per HomeTab      │  │
│ │  (each page: SortHeader + list + A–Z rail)  │  │
│ ╰──────────────────────────────────────────────╯ │
│ ╭── mini-player overlay (when a song is set) ──╮ │
└─────────────────────────────────────────────────┘
```

- `enum class HomeTab(val title: String) { FAVOURITES("Favourites"), PLAYLISTS("Playlists"), TRACKS("Tracks"),
  ALBUMS("Albums"), ARTISTS("Artists"), FOLDERS("Folders") }`. The initial page is `TRACKS`.
- `rememberPagerState(initialPage = TRACKS.ordinal)` saves itself, so the selected tab survives rotation, process death and
  returning from a pushed screen. Pages lay out lazily, with no pre-composition beyond the viewport.
- Each pager page puts a feature tab composable inside a `ContentPanel`:
  - `:feature:playlists`: `FavouritesTab(...)`, `PlaylistsTab(...)`
  - `:feature:library`: `TracksTab(...)`, `AlbumsTab(...)`, `ArtistsTab(...)`, `FoldersTab(...)`

  Each tab gets its own `hiltViewModel()`, takes navigation callbacks as lambdas, and takes the bottom content padding from §7.3.
- **Header actions:**
  - `+` is visible only while the Playlists page is settled. It fades and opens the existing new-playlist dialog. Hosting that dialog moves into `PlaylistsTab` through a hoisted `showCreateDialog` state.
  - Search opens `Search()`.
  - ⋮ opens a dropdown with "Settings".
- **Selection mode.**
  - A `SongSelectionState` (`:core:ui`, `rememberSaveable`-backed, the same derivedStateOf-per-row pattern as today) is created in `HomeScreen` and passed to `TracksTab`.
  - While a selection is active, the header becomes the existing selection bar ("N selected", close, Edit tags), back clears the selection, and `userScrollEnabled = false` on the pager.
- **Permission gate.** `MediaPermissionGate` wraps the whole pager area. `onGranted` triggers the existing library sync, the same call `LibraryRoute` makes today.

### 7.3 Mini-player as an overlay

- The outer `Scaffold`'s `bottomBar` goes away. `RollaNavHost` becomes a `Box`: the `NavHost` fills it, and the mini-player is aligned
  to the bottom with `navigationBarsPadding()`, keeping the same `AnimatedVisibility` slide and fade.
- `LocalMiniPlayerInset: ProvidableCompositionLocal<Dp>` lives in `:core:designsystem`. It equals the pill's measured footprint (its own margins included, the navigation bar excluded), reported by the
  host through `onSizeChanged`, while the pill is visible, and 0 dp while it is hidden or the IME is visible. Measuring keeps it right when
  Phase 3 resizes the pill to `miniPlayerHeight`. Every scrollable list adds `LocalMiniPlayerInset.current` plus the navigation-bar inset as bottom
  `contentPadding`, so content scrolls *under* the pill and the last item can still scroll fully into view.
- **Visibility.** The pill shows on Home, Search, AlbumDetail, ArtistDetail, PlaylistDetail, SmartPlaylist and FolderDetail. It is hidden on
  Now Playing, Equaliser, Settings, About, Licenses, Privacy, TagEditor and BatchTagEditor (the screenshots of EQ and Settings show no pill).
- The shared-element artwork transition between the mini-player and Now Playing must keep working. Both stay inside the same `SharedTransitionLayout`.

### 7.4 Insets

- Home: the header sits below the status bar (`statusBarsPadding`). The panel reaches the bottom edge, drawing behind the nav bar, and lists
  pad for it.
- Pushed screens: `OneUiDetailTopBar` owns the status-bar inset.
- The "single owner" rule from the edge-to-edge work stays: no screen consumes the same inset twice.

## 8. Screens

All values come from §5 tokens and §6 components. "Row" means `SongListItem` and its siblings, restyled.

### 8.1 Shared list rows (`:core:ui`)

- **`SongListItem`:**
  - Layout: row height `listRowHeight`, a 48 dp `small`-shape thumbnail at `listThumbStart` (`ArtworkPlaceholder` when there is no art), and text starting at `listTextStart`.
  - Text: `songTitle` and `artistName`, one line each, ellipsized.
  - Trailing: a gray ⋮ (`RollaIcons.More`, `onSurfaceVariant`, 48 dp target).
  - Divider: an `InsetDivider` from `listTextStart` to the rail.
  - The selected-state highlight and track-number mode keep working.
- **`PlaylistRow`:** a `singleLineRowHeight` row with a thumbnail, `songTitle` name, and a trailing count "N tracks" (`rowTrailing`).
- **`AlbumRow` / `ArtistRow`:** restyled to the same anatomy. `ArtistRow` uses a circular 48 dp avatar and the subtitle
  "N albums, M tracks".
- **`FeatureCard`** (new): a 143 dp `featureCard`-shape box (art, a 2×2 collage, or a placeholder with a 44 dp note), then the centered
  `featureCardLabel` and `featureCardCount`.
- **`AlbumCard`:** used by the Albums grid. A square `featureCard`-shape art box, then a left-aligned `featureCardLabel` title (one
  line) and a `featureCardCount` artist (one line).
- **`FolderRow`** (new): a 48 dp thumbnail showing the first song's art (or a placeholder) with a `FolderBadge` in the bottom-right corner,
  the folder name as `songTitle`, and the path as `artistName` ("/Internal storage/Music").

### 8.2 Tabs

| Tab | Content |
|---|---|
| Tracks | `SortHeader("Name")` with Shuffle and Play `CircleIconButton`s (shuffle-all / play-all, real, §12). All songs sorted by title. Rows, A–Z rail. Long-press selection and the options sheet (Add to playlist, Edit tags) are kept. |
| Favourites | Same as Tracks, over favourite songs. Empty state: "No favourite tracks yet". |
| Playlists | `SortHeader("Name")` with no buttons. A `LazyRow` of `FeatureCard`s: Recently added · Most played · Recently played · Favourite tracks (the "Favourites" label is renamed to match). Then user `PlaylistRow`s and the A–Z rail. Empty user list: "No playlists yet. Tap + to create one." |
| Albums | `SortHeader("Name")`. A 2-column `LazyVerticalGrid` of `AlbumCard`s with gap `featureCardGap`. Tap opens `AlbumDetail`. No A–Z rail. |
| Artists | `SortHeader("Name")`. `ArtistRow`s and the A–Z rail. Tap opens `ArtistDetail(artistName)`. |
| Folders | `SortHeader("Name")`. `FolderRow`s and the A–Z rail. Tap opens `FolderDetail(path)`. |

Scanning and empty states render inside the panel in `emptyState` style.

### 8.3 Mini-player

- **Container:** a `miniPlayerContainer` pill, `miniPlayerHeight` tall, with `miniPlayerSideMargin` on each side.
- **Content, left to right:**
  - A 38 dp circular art at `miniPlayerArtStart` (`miniPlayerArtPlaceholder` with a `miniPlayerArtGlyph` note when there is no art).
  - Title in `miniPlayerTitle` with a marquee (`Modifier.basicMarquee`, off under reduced motion), and the artist in `miniPlayerSubtitle` (white).
  - Transport, each a 48 dp target: solid `SkipPrevious`, `Play`/`Pause`, `SkipNext`, and line `Queue`, which opens the queue sheet (§11).
- **Behavior kept:** body tap opens Now Playing, plus the semantics merging and the shared-element artwork.

### 8.4 Now Playing

- **Background:** `Modifier.nowPlayingBackground(...)` over the full screen.
- **Top bar:** `ChevronDown` (collapse) on the left. On the right: `Volume` (opens the system volume panel, §12), `EqualizerBars` (opens Equaliser) and ⋮
  (a menu: Edit tags · Go to album · Go to artist · Settings, §12).
- **Artwork:** `npArtSize`, `extraLarge` shape, centered, with the shared-element source/target kept. `ArtworkPlaceholder(large = true)`
  with a 60 dp note when there is no art.
- **Text:** title in `nowPlayingTitle`, centered, one line with a marquee; artist in `nowPlayingArtist`, centered, white.
- **Action row:** spread to the edges (`npSeekInset` padding). `Queue` (opens the sheet), `Heart`/`HeartFilled` (favorite toggle; `accentText` is not used, the filled heart stays white) and `Add` (add to playlist).
- **Seekbar:** a custom thin bar. Track `npSeekThickness` (`seekTrackActive` / `seekTrackInactive`), a white circular thumb `npSeekThumb`, and the same drag/seek
  semantics as today (position ticks scoped to the seekbar only). Time labels go below at both ends in `metadata` white.
- **Transport row:** spaced evenly.
  - `Shuffle`: `onSurface` when off (as in the reference, which shows the default playback state), `accentText` when on, with
    `stateDescription` "Shuffle on" or "Shuffle off".
  - `SkipPrevious` (solid).
  - A bare **`Play`/`Pause` glyph** at `npPlayGlyph`, with no circle and a 64 dp target.
  - `SkipNext`.
  - Repeat: `PlayOrder` / `Repeat` / `RepeatOne` for OFF / ALL / ONE.
- **Accessibility kept:** play/pause semantics, state descriptions for shuffle and repeat, and reduced-motion handling.

### 8.5 Equaliser

- **Top bar:** `OneUiDetailTopBar("Equaliser")`. The UI copy uses British spelling to match the example. Code names stay `Equalizer*`.
- **Enable card:** a small `surfaceContainer` card (`large`, inset `settingsCardInset`) with a toggleable row: "Equaliser" plus a `OneUiSwitch`.
- **Slider card:** an edge-to-edge `surfaceContainer` card with `large` corners.
  - Faint `eqGridLine` horizontals every `eqGridPitch` across the slider area.
  - Nine `EqVerticalSlider`s at `eqColumnPitch`.
  - `eqValue` labels above each slider (dB with no unit; "0", "+3", "-2").
  - `eqFrequency` labels below: **40 · 80 · 160 · 315 · 630 · 1.25k · 2.5k · 5k · 10k**. These are the app's real bands; only the "Hz" suffix is dropped.
  - The gains-driven response curve stays, drawn inside the card behind the sliders. Its stroke is `primary` at 35 % alpha, it is visible only while
    a slider is being dragged, and it fades in and out (reduced-motion: no fade).
  - When the EQ is disabled, sliders and chips render at 38 % alpha and are not interactive.
- **Preset grid:** a 2-column grid of `PillChip`s (columns `chipGap` apart; rows `chipRowLayoutGap` apart, because each chip
  occupies a 48 dp touch height, which gives the measured `chipGap` visually; inset `chipGridStart`) in built-in order: Balanced, Bass boost, Smooth, Dynamic,
  Clear, Treble boost, Custom, then any saved user presets.
- **Below the grid:** a `caption` with the preset description, then a `TextButton("Save as preset")` in `accentText`, which keeps the existing dialog.

### 8.6 Settings

- **Top bar:** `OneUiDetailTopBar("Rolla Music settings")`.
- **Layout:** `sectionHeader` labels (aligned with row text, `settingsCardInset + settingsRowPadding` from the screen edge) above cards
  (`surfaceContainer`, `large`, inset `settingsCardInset`).
- **Rows:**
  - Text: `settingTitle` labels, with the value below in `settingValue` (blue) for value rows, or `settingSubtitle` (gray) for sub-labels.
  - Controls: toggle rows end with a `OneUiSwitch`. Slider rows show the label, a centered blue value ("1.0x") and a `OneUiSlider`.
  - Dividers: `InsetDivider` with `settingsDividerInset` on both sides between rows.
  - Navigation rows: no trailing chevron. One UI shows none in the reference; the whole row is clickable.
- **Content:** the existing sections and rows only (Appearance · Playback · Library · Privacy · About), with the same behavior.
- About, Licenses and Privacy use the same bar and card language.

### 8.7 Other screens (no reference screenshot)

All of these use `OneUiDetailTopBar`, `ContentPanel` or settings-style cards, the new rows, and `RollaIcons`:

- **Search:** the field sits in a `surfaceContainerHigh` pill with a `Search` glyph and `ChevronBack`. Results use section headers and the new rows inside a panel. Recent searches become rows with a `Close` glyph.
- **AlbumDetail / ArtistDetail / PlaylistDetail / SmartPlaylist / FolderDetail:**
  - Header: artwork or title block, then `SortHeader` with Shuffle and Play `CircleIconButton`s.
  - Body: rows in a panel.
  - ArtistDetail keeps its horizontal `AlbumCard` strip.
  - PlaylistDetail keeps drag-reorder and the accessibility move actions.
- **TagEditor / BatchTagEditor:** fields sit on `surfaceContainer` cards. M3 `OutlinedTextField`s are restyled with token colors (container `surfaceContainerHigh`, no outline, `large`-ish 16 dp radius). Buttons are `primary` pills, and the per-field apply toggles use `OneUiSwitch`.
- **Sheets** (song options, add-to-playlist, queue): `ModalBottomSheet` with `surfaceContainerHigh`, `large` top corners, a `sectionHeader` title and the new rows.
- **Dialogs** (new playlist, save preset, theme picker): `AlertDialog` with `surfaceContainerHigh`, `large` shape, and `accentText` text buttons.
- **Menus:** `DropdownMenu` with `surfaceContainerHigh`, `medium` shape, and `songTitle`-sized items.

## 9. Data layer

### 9.1 Albums

- Add `AlbumDao.observeAlbums(): Flow<List<AlbumRow>>`. It groups `songs` by `album_id` and orders by `album COLLATE NOCASE`. Artwork comes from the
  existing album artwork URI. It maps to the existing `Album` model.
- Add `AlbumRepository.observeAlbums()`.
- No schema change.

### 9.2 Artists

- Add `ArtistDao.observeArtists()`. It groups by `artist`, returns `COUNT(DISTINCT album_id)` and `COUNT(*)`, and takes one representative
  `artwork_uri` (the artist's alphabetically first album). It orders by `artist COLLATE NOCASE`.
- Add `ArtistRepository.observeArtists()`.
- **Model:** add `val artworkUri: String = ""` to `Artist` (and update `model-vocabulary.md`). Artist identity stays the **name** (routing uses
  `ArtistDetail(artistName)`), and the synthetic FNV id stays a list key only.

### 9.3 Favourites

- Reuse the existing `is_favorite = 1` query, through a new `SongRepository.observeFavouriteSongs()` if one doesn't already exist.

### 9.4 Folders

- **Schema:** Room **v3 → v4**. `Migration3To4` runs `ALTER TABLE songs ADD COLUMN folder_path TEXT DEFAULT NULL`.
  - `NULL` means *unknown, not yet scanned*.
  - `''` means the volume root.
  - Any other value is a path relative to the volume root, using `/` separators with no leading or trailing slash (`Music`, `Music/Chill`).
  - Export schema `4.json`. Add a `MigrationTestHelper` test that covers 3→4 (data preserved, column present and `NULL`).
- **Entity:** `SongEntity.folderPath: String?`. The domain `Song` is **unchanged**.
- **Scanner:**
  - **Columns:** `MediaStoreColumns` adds `RELATIVE_PATH` on API 29+ and `DATA` on API 24–28.
  - **Parsing:** pure functions in `FolderPaths.kt` (`:core:data`), unit-tested with real-world inputs:
    - `fromRelativePath(String?)`: trims the trailing `/`. Null input gives `''`.
    - `fromDataPath(String?)`: strips the volume prefix (`/storage/emulated/<n>/`, `/storage/<UUID>/`, `/sdcard/`), drops the
      filename and trims slashes. Null or unparseable input gives `''`.
  - **Mapping:** `ScannedSong` carries `folderPath`, and `toEntity` writes it.
- **Backfill:** in `LibraryIndexer.sync()` a row is stale when it is new, when `dateModified` changed, **or when `existing.folderPath == null`**.
  The first sync after upgrading fills every row once. Root-level songs get `''`, so they are not re-written forever.
  `syncSongs(ids)` (the tag-editor path) also writes the folder path.
- **Queries:**
  - `SongDao.observeFolders()` groups by `folder_path` where it is not null, and returns the path, song count and artwork of the
    alphabetically first song. Ordered by path, case-insensitive.
  - `SongDao.observeSongsInFolder(path)` orders by title.
- **Model:** add `data class Folder(val path: String, val name: String, val songCount: Int, val artworkUri: String)` to `:core:model`. `name` is the last
  path segment, or "Internal storage" for `''`.
- **Repository:** add `FolderRepository` (`observeFolders`, `observeFolderSongs`) in `:core:data`, bound in `DataModule`.
- **Display path:** `"/Internal storage" + (if (path.isEmpty()) "" else "/$path")`.
- **Known limitation:** the same relative path on two volumes merges into one folder. This is documented, not fixed.

## 10. A–Z fast-scroll rail

- **Sections:**
  - `FastScrollSection(label: String, firstIndex: Int)`, built by a pure `buildFastScrollSections(keys: List<String>)`.
  - **Keys:** each item's display name, uppercased, with the first character run through `Normalizer` to strip diacritics. `A`–`Z` maps to its letter; anything else maps to `#`.
  - **Labels:** "#" plus A–Z. Every letter is shown, even with no items. A letter with no items targets the next populated section, or the previous one at the end.
- **Fitting:** a letter needs 16 dp of rail height (`fastScrollLetter` at 11 sp plus spacing). When `railHeight / 16dp` < 27, the rail shows every
  k-th label, where k is the smallest value that fits. Dropped letters are still reachable by drag, since drag maps the y position over the full list.
  This matches the condensed Playlists rail in the reference.
- **Interaction:** tap or vertical drag on the rail calls `listState.scrollToItem(section.firstIndex)` (plus the header offset). A
  `HapticFeedbackType.TextHandleMove` tick fires on each section change. No bubble.
- **Accessibility:** the rail is one semantics node, "Fast scroll, letter index", with custom actions per populated section (for example "Jump to M")
  and `traversalIndex` after the list. TalkBack users can ignore it.
- **Where it appears:** Tracks, Favourites, Playlists (user rows only), Artists and Folders. It is hidden when the list fits on one screen.
- The list reserves `fastScrollWidth + fastScrollEnd` on the end side so dividers stop at the rail, as in the reference.

## 11. Queue

- **`:core:media`:**
  - `data class QueueItem(val index: Int, val mediaId: String, val title: String, val artist: String, val artworkUri: String)`.
  - `data class QueueState(val items: List<QueueItem>, val currentIndex: Int)`. The `items` are in **playback order**: when shuffle is on, the timeline is
    walked with `getFirstWindowIndex`/`getNextWindowIndex(shuffleModeEnabled = true)`.
  - `PlaybackController.queue: StateFlow<QueueState>` is updated from `onTimelineChanged`, `onMediaItemTransition` and
    `onShuffleModeEnabledChanged` on the main thread.
  - `PlaybackController.skipToQueueIndex(index)` calls `seekToDefaultPosition(index)`.
  - The timeline-to-`QueueState` mapping is a pure function with unit tests (shuffle on and off, empty, single item).
- **`:feature:player`:** `QueueSheet` (a `ModalBottomSheet`).
  - Title "Queue" (`sectionHeader`).
  - A `LazyColumn` of rows with a 48 dp thumbnail, title and artist. The current item's title is in `accentText`, with a small `EqualizerBars` glyph.
  - Tap jumps to the item and keeps the sheet open.
  - On open, it scrolls to the current item.
  - It opens from the mini-player's `Queue` button and Now Playing's `Queue` action.

## 12. Dead controls fixed

| Control | Today | New behavior |
|---|---|---|
| Tracks/Favourites/detail **Shuffle** | `onClick = {}` | `PlaybackController.setShuffle(ON)`, then `playAll(songs)` from a random start index |
| Tracks/Favourites/detail **Play** | `onClick = {}` | `setShuffle(OFF)`, then `playAll(songs, 0)` |
| Mini-player **Queue** | `remember { {} }` | Opens the `QueueSheet` |
| Now Playing **Queue** | `onClick = {}` | Opens the `QueueSheet` |
| Now Playing **Volume** | `onClick = {}` | `AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_SAME, FLAG_SHOW_UI)`, which shows the system volume panel |
| Now Playing **⋮** | `onClick = {}` | Menu: Edit tags → `TagEditor(songId)`, Go to album → `AlbumDetail`, Go to artist → `ArtistDetail`, Settings |

## 13. Widget (`:feature:widget`)

- **Card:**
  - Color: pure black (`#000000` in both day and night `ColorProvider`s). The widget stays dark by design, as today.
  - Corner radius: 24 dp on API 31+ via `cornerRadius`. API < 31 uses a rounded-rectangle drawable background.
- **Layout:**
  - A 69 dp rounded (12 dp) artwork box on the left (`#363636` placeholder with a white note).
  - A column on the right:
    - Title (16 sp Bold, `#FCFCFE`, one line) and artist (13 sp, `#B0B0B2`, one line).
    - The transport row: prev · −15 · play/pause · +15 · next, as white line or solid vector drawables mirroring `RollaIcons` (Glance needs
      XML drawables in `:feature:widget` resources).
    - A 3 dp progress bar under the transport row: `#2F6FF0` on `#333333`.
- **Unchanged:** the controls, action callbacks, update hook, artwork loader and picker preview. Regenerate the picker preview
  drawable to match.

## 14. Device calibration (over adb)

**Prerequisite:** the phone that took the reference screenshots is connected with USB debugging. ADB lives at
`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`; it is not on `PATH`.

1. Read `adb shell wm density` and `adb shell settings get system font_scale`, and record both in `oneui-measurements.md`.
2. Recompute the `RollaDimens` and `Type.kt` values from the measured px using the real density and font scale. This is a mechanical
   pass: dp = px ÷ density and sp = dp ÷ fontScale.
3. Install the debug build. Capture `adb exec-out screencap -p` for: the Tracks tab, Playlists tab, Folders tab, Now Playing, Equaliser,
   Settings (top), and the home-screen widget.
4. Run the same `measure.ps1` and `sample.ps1` probes on our captures and diff them against the reference numbers. Fix tokens or dimensions until
   geometry is within ±2 dp (±6 px at 3.0) and sampled flat colors are within ±3 per channel of the token, or exactly match the documented
   substitutes (`#2F6FF0`, `#646466`). Text glyph heights must be within ±1 sp.
5. Keep side-by-side overlay PNGs (reference | ours | 50 % blend) in `docs/design/calibration/` as evidence.

## 15. Testing and gates

**Unit tests (JVM):**

- `FastScrollIndex`: building, fitting and position mapping.
- `FolderPaths`: relative path and data-path parsing, including real Samsung and AOSP paths, root files, SD-card UUIDs and nulls.
- Queue mapping: shuffle on and off, empty, single item, current index.
- New ViewModels: `FavouritesTabViewModel`, `AlbumsTabViewModel`, `ArtistsTabViewModel`, `FoldersTabViewModel` and `FolderDetailViewModel`
  (Turbine and MockK, the project's existing style).
- `LibraryIndexer`: the null-`folderPath` backfill rule, with root songs not rewritten on the second sync.
- Shuffle-all and play-all wiring in the tab ViewModels.
- Now Playing overflow and volume actions: callbacks invoked; the volume call goes through an injectable seam.
- Every existing test the redesign touches gets updated, not deleted: the library, playlists, player, settings and equalizer suites.

**Instrumented tests (on the connected device):**

- `Migration3To4Test`.
- New DAO queries: albums, artists, folders, songs-in-folder.
- **Compose smoke suite:** rewritten for Home tabs.
  - The Tracks tab, tap a song, the mini-player, expand to Now Playing.
  - Swipe to Playlists, then `+`, then create.
  - Tap Folders, open a folder, and FolderDetail lists songs.
  - Search query, then tap a result.
  - Open the queue sheet, then tap an item.
- **Baseline-profile generator and benchmarks:** start on Home (Tracks) and keep the `song_list` test tag. The profile is
  regenerated at the end of the work.

**Gates:**

- Every phase ends with `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug` green. Phases that touch Room also need the
  connected migration and DAO tests.
- The final gate adds `connectedDebugAndroidTest`, `assembleRelease` and `checkReleaseManifestNoInternet`, plus manual airplane-mode verification on the
  device: play, every tab, folder detail, the queue sheet, EQ, settings, the widget, and process-death restore of the selected tab.
- After every phase, an Opus `code-reviewer` subagent reviews the phase diff. The orchestrator accepts the phase only when there are no CRITICAL or HIGH
  findings and the gates are green.

## 16. Docs to update

- **`.claude/rules/ui-style-guide.md`:** rewrite §2–§8 with the tokens, dimensions, components and screen blueprints from this spec. It
  stays the binding visual contract. Remove the "never 'Tracks'" note and replace it with the UI-label exception.
- **`.claude/rules/model-vocabulary.md`:**
  - Add a "UI label exception" section: user-facing strings say "Tracks" (tab, counts), while code identifiers stay `Song`.
  - Add `Folder`, plus `Artist.artworkUri`.
- **`.claude/rules/navigation-conventions.md`:** document the `Home` start destination, the removal of the bottom nav, and `FolderDetail`.
- **`CLAUDE.md`:**
  - Move the three "Post-1.0 cuts" to shipped.
  - Update the `:feature:library` and `:core:media` blurbs: tabs, folders, queue.
  - DB v4.
  - New screens.
  - Status.
- **Widget agent and skill docs:** only if the visual change touches documented behavior.
- **`docs/design/oneui-measurements.md`:** add the real density and font scale, plus the calibration results.

## 17. Phases

Each phase ends with green gates (§15), an Opus code review, the orchestrator's acceptance, and a "ready to commit" report. The user
triggers commits.

| # | Phase | Owner agents (all `model: "opus"`) | Exit criteria |
|---|---|---|---|
| 1 | Tokens (`Color`, `Type`, `Shape`, `Dimens`), `RollaIcons`, the component kit with previews | m3-design-system-agent, ui-builder | Kit previews render in dark, light and fontScale 1.5. Contrast unit test for every documented pairing. Existing screens still compile on the new tokens. |
| 2 | Home shell: routes, `HomeScreen`, `OneUiTabRow` + pager, `ContentPanel`, mini-player overlay and inset local, bottom nav removed. Tracks and Playlists tabs restyled. `SongSelectionState`. | navigation-agent, ui-builder, viewmodel-architect | App starts on Home > Tracks. Swipe and tap tabs. Selection mode works. Smoke-suite paths for Tracks and Playlists pass. Shared-element transition intact. |
| 3 | Now Playing (background, seekbar, transport, volume, overflow), queue (`:core:media` + `QueueSheet`), mini-player restyle, Equaliser, Settings | audio-engineer, ui-builder, equalizer-agent | Queue mapping tests, Now Playing and EQ ViewModel tests and settings tests pass. No dead controls left in these screens. |
| 4a | Favourites, Albums and Artists tabs (DAO queries, repositories, `Artist.artworkUri`, ViewModels, UI) | data-layer-agent, ui-builder, viewmodel-architect | DAO tests on device. Tabs render real data. Navigation to the detail screens works. |
| 4b | Folders: migration v4, scanner `FolderPaths`, indexer backfill, `FolderRepository`, Folders tab, `FolderDetail` route and screen | data-layer-agent, media-scanning-agent, navigation-agent, ui-builder | Migration test and `FolderPaths` tests pass. An upgraded install backfills folders on the first sync. |
| 5 | `FastScrollRail` on Tracks, Favourites, Playlists, Artists and Folders | ui-builder, compose-animation-agent | Index tests pass. Tap and drag jump correctly. TalkBack actions present. No jank (compose-performance-auditor pass). |
| 6 | Secondary screens (Search, details, tag editors, About/Licenses/Privacy, sheets, dialogs, menus) and the widget | ui-builder, widget-agent | No Material icon or hardcoded value outside the documented exceptions. Widget renders on the device. |
| 7 | Device calibration (§14), docs (§16), baseline profile regeneration, final gate, final review | m3-design-system-agent, build-tooling-agent, code-reviewer, test-writer | §1 criteria met. Calibration evidence saved. CLAUDE.md and rules updated. |

## 18. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Density and font scale differ from the 3.0 assumption | Calibration (§14) recomputes every dimension and type size from the measured px. Tokens are centralised, so it is a one-file change. |
| Pager horizontal swipes fight the A–Z rail drag or the drag-to-reorder | The rail consumes vertical drags only. Reorder lives in PlaylistDetail, not in the pager. Selection mode disables pager scroll. |
| The mini-player overlay hides the last list items | `LocalMiniPlayerInset` is applied as bottom `contentPadding` on every list. The smoke test scrolls to the end of the Tracks list and asserts the last row is visible. |
| The shared-element transition breaks when the mini-player moves out of `bottomBar` | Both endpoints stay inside the same `SharedTransitionLayout`. The phase 2 exit criteria include a manual check. |
| Migration backfill rewrites many rows on the first sync after upgrade | One-time cost, under the indexer's existing mutex. Rows only change when `folder_path IS NULL`. |
| The baseline profile goes stale after the UI rewrite | It is regenerated in phase 7 on the managed device. Startup and scroll benchmarks are compared with v1.0 numbers. |
| Many existing UI tests break | Each phase updates the tests it touches as part of its exit criteria. Tests are never deleted to go green. |
| AA regressions from the new palette | A contrast unit test in `:core:designsystem` checks every documented foreground/background pairing against its threshold (4.5 text, 3.0 non-text). |
