# RollaMusicPlayer — Visual Style Guide (Jetpack Compose)

The look-and-feel contract for RollaMusicPlayer: a One UI–style dark music player. `m3-design-system-agent` implements it
in `:core:designsystem`; `ui-builder` and `widget-agent` build against it in `:core:ui`, `:feature:*` and the widget.

> **Values live in the spec, not here.** Every colour, size, radius and type style is a token defined in
> [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../../docs/superpowers/specs/2026-10-06-oneui-redesign-design.md)
> §5 (tokens) and §6 (component kit), and implemented in `:core:designsystem` (`theme/`, `icon/`, `component/`,
> `motion/`). This guide names the tokens and the rules for using them; it carries no hex or dp values of its own, so it
> cannot drift from the spec. Where the two disagree, the spec wins.

---

## 1. Design Principles

1. **OLED-first dark.** True black background; dark is the primary, fully designed theme. Light is a first-class parity
   theme with its own values for the same roles.
2. **Soft, rounded surfaces.** Content sits inside large-radius containers inset from the screen edges. Nothing is sharp.
3. **One stable accent.** A single blue is the only accent: selection, active sliders, switches, checkboxes, links.
   Everything else is white or grey.
4. **Content-forward and calm.** Generous spacing, restrained colour; artwork and titles carry the screen.
5. **One UI structure.** A large bold screen title, a centre-weighted tab row (the selected tab is largest), a rounded
   content panel, and a floating pill mini-player.
6. **Line iconography.** Thin outline icons; the play glyph is the only solid control.

---

## 2. Colour tokens (spec §5.1)

- **Material roles** (`MaterialTheme.colorScheme`): `background`, `surfaceContainer` (the content panel, cards, the EQ
  card), `surfaceContainerHigh` (sheets, dialogs, menus, circle buttons, the selected-row highlight),
  `primary`/`onPrimary` (the accent fill and what sits on it),
  `onSurface`, `onSurfaceVariant`, `outlineVariant` (dividers), `error` (non-text only).
- **Semantic extensions** (`ColorScheme.xxx` in `Color.kt`), among them `accentText`, `destructiveText`,
  `labelSecondary`, `eqCaption`, `tabUnselected`, `artworkPlaceholder`/`artworkPlaceholderLarge`/
  `artworkPlaceholderGlyph`, `sheetArtworkPlaceholder`, `dialogFieldContainer`, `miniPlayerContainer`,
  `miniPlayerArtPlaceholder`/`miniPlayerArtGlyph`, `sliderInactiveTrack`, `switchThumb`, `fastScrollTrack`/
  `fastScrollIndex`, `eqGridLine`, `seekTrackActive`/`seekTrackInactive` and `nowPlayingGradient`.
- **Rules:**
  - Blue **text** is always `accentText`, never `primary` (`primary` fails 4.5:1 as text on the panels).
  - Destructive text and icons are always `destructiveText`, never `error`.
  - A placeholder inside a sheet or dialog takes `sheetArtworkPlaceholder` automatically (`OneUiBottomSheet` and
    `OneUiDialog` provide it); a dialog's name field is `OneUiDialogField` on `dialogFieldContainer`.
  - Every documented pairing meets WCAG AA (text 4.5:1, non-text 3:1); `ContrastTest` pins them. A new pairing is added
    there with its computed ratio before it ships.
  - Dynamic colour is an opt-in setting; the accent stays brand-stable by default.
  - The widget cannot read Compose tokens: it keeps its own palette (§8).

## 3. Typography (spec §5.2)

`MaterialTheme.typography` plus the semantic styles in `Type.kt`: `screenTitle`, `tabSelected`/`tabUnselected`,
`songTitle`, `artistName`, `sectionHeader`, `metadata`, `chipLabel`, `caption`, `emptyState`, `miniPlayerTitle` and
the Now Playing title styles. One sans-serif family (the system default). Never set a `fontSize` or `fontWeight` in a
screen; pick the style.

## 4. Shapes (spec §5.3)

`MaterialTheme.shapes` from `RollaShapes`: `small` for list thumbnails, `medium` for fields and inline cards, `large` for
the content panel, settings cards, sheets (square bottom corners) and dialogs, `extraLarge` and `featureCard` for the big
cards and the Now Playing artwork. Anything button-like that is a pill or a circle uses `CircleShape`.

## 5. Dimensions and touch targets (spec §5.4)

- Every size comes from `RollaDimens`; a screen never declares a private `dp` literal for something the table covers.
  A new size becomes a token, with its spec §5.4 row, before a screen uses it.
- Calibrated values (Phase 7, 2026-10-31 to 2026-11-01) carry "(calibrated …)" in the spec; the measured evidence is in
  `docs/design/oneui-measurements.md`.
- **Touch targets** are at least `minTouchTarget` (48 dp), with two documented exceptions the user accepted on 2026-10-06,
  both above WCAG 2.2 AA's 24 dp and both with full semantic actions:
  - **EQ slider columns:** the touch width is the column pitch (`eqColumnPitch` at minimum). Each band has increase and
    decrease actions.
  - **The A–Z rail:** the touch width is `fastScrollWidth + fastScrollEnd`. Each populated section has a "Jump to X"
    action.

## 6. Components (spec §6, `:core:designsystem/component/`)

Use the kit; never call the Material 3 component it wraps. detekt and the `checkNoMaterialIcons` scan enforce the icon
rule; review enforces the rest.

| Need | Use | Never |
|---|---|---|
| Home header / pushed-screen bar | `OneUiTopBar`, `OneUiDetailTopBar` | `TopAppBar` |
| Tab row | `OneUiTabRow` (centre-weighted, no indicator) | `ScrollableTabRow` directly |
| Content panel | `ContentPanel` | an ad-hoc rounded `Box` |
| Sort / control header | `SortHeader` with circle Shuffle and Play | |
| Sheet, dialog, menu | `OneUiBottomSheet`, `OneUiDialog` + `OneUiTextButton` (`destructive = true` for Delete/Remove), `OneUiDropdownMenu`/`OneUiMenuItem` (`destructive = true` for Remove), `SheetActionRow` | `ModalBottomSheet`, `AlertDialog`, `TextButton`, `DropdownMenu` |
| Text entry | `OneUiTextField` (`isError`/`supportingText`/`trailingControl`), `OneUiDialogField` in dialogs, `OneUiSearchField` | `TextField`, `OutlinedTextField` |
| Buttons | `OneUiPillButton` (filled or tonal, `loading`, `destructive` on a tonal pill), `OneUiIconButton`, the circle buttons | `Button`, `OutlinedButton` |
| Toggles and selection | `OneUiSwitch`, `OneUiCheckbox` (round), `PillChip` | M3 `Switch`, `Checkbox`, `FilterChip` |
| Sliders | `OneUiSlider`, `OneUiSeekBar`, `EqVerticalSlider` | M3 `Slider` directly |
| Artwork stand-in | `ArtworkPlaceholder` | an `Icon` on a grey `Box` |
| A–Z index | `FastScrollContainer` / `FastScrollRail` | a custom index |
| Icons | `RollaIcons` only | `androidx.compose.material.icons`, imported or fully qualified |

Model-aware rows and cards live in `:core:ui`: `SongListItem` (selection mode shows `OneUiCheckbox`), `AlbumRow`,
`ArtistRow`, `FolderRow` and `PlaylistRow` on `ListRowScaffold`, `AlbumCard`, `FeatureCard`, `DetailArtworkHeader`,
and `SongOptionsHost` for the per-track options sheet. The last row of a list draws no divider.

## 7. Screen blueprints (spec §7–§8)

- **Home** (start destination): the "Rolla Music" header (Search, + on Playlists, ⋮), the centre-weighted tab row
  **Favourites · Playlists · Tracks · Albums · Artists · Folders**, then each tab inside a `ContentPanel` with a
  `SortHeader`. Everything below the header ends at the navigation bar's top, so the black background shows behind
  the bar; lists pad for the floating pill alone. Tracks, Favourites, Playlists (user rows), Artists and Folders carry the
  A–Z rail; Playlists starts with the feature-card row.
- **UI label exception:** the library tab and every count say "Tracks"; code and the model say `Song`
  (see `model-vocabulary.md`).
- **Mini-player:** a floating pill over the content, placed by the app shell above the navigation bar: round art, title
  and artist, previous · play/pause · next · queue. Tap opens Now Playing.
- **Now Playing:** chevron and actions bar (volume, Equaliser, ⋮), the artwork, title and artist, the action row
  (queue, heart, +), the seek bar with times, then shuffle · previous · play · next · play order.
- **Pushed screens** (AlbumDetail, ArtistDetail, PlaylistDetail, SmartPlaylist, FolderDetail, Search, Equaliser,
  Settings, the tag editors, About, Licenses, Privacy): `OneUiDetailTopBar`, then a `ContentPanel` or grouped cards.
- **Equaliser:** an enable card, the slider card (bands with value and frequency labels), the preset chip grid and its
  caption. UI copy says "Equaliser"; code says `Equalizer*`.
- **Settings:** grouped `large` cards under `sectionHeader` labels; values in `accentText` below their labels, no
  chevrons; switches end at `settingsTrailingEnd`.

## 8. Home-screen widget (spec §13)

The widget follows spec §13, which is binding. Its sizes are the `RollaDimens.widget*` tokens; its colours are the
widget's own palette in `feature/widget/src/main/res/values/colors.xml`, mirrored in `MusicWidgetTheme.kt` and kept equal
by `WidgetPaletteTest` (Glance cannot read the app's Compose tokens). It stays dark in the light system theme, keeps the
±15 s controls (previous · −15 s · play/pause · +15 s · next) and opens the app on a body tap.

---

## 9. Motion (summary — details owned by compose-animation-agent)

- Durations 200–300 ms, springs for interactive elements (`rollaSpringOrSnap`).
- Mini-player → Now Playing: shared-element artwork plus a crossfade of the metadata.
- Tab switch: the pager slides; the tab labels animate size and weight.
- Selection (tab, chip, switch): animate, never an abrupt swap. `OneUiCheckbox` swaps its disc and ring without
  an animation.
- Respect reduced motion: `rememberReducedMotion()` (springs become snaps).

---

## 10. Ownership & Enforcement

- **m3-design-system-agent** owns `:core:designsystem`: the tokens, the semantic extensions and the kit. Zero hardcoded
  values anywhere else.
- **ui-builder** builds the screens in `:core:ui` and `:feature:*` strictly from tokens and the kit.
- **widget-agent** applies §8.
- **compose-animation-agent** owns §9.
- **code-reviewer** flags any hardcoded colour, size or type, and any direct use of a wrapped M3 component.

When a needed value is missing, add the token and its spec §5 row first, then use it — never hardcode to "match the
spec".
