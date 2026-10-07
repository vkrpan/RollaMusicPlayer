# RollaMusicPlayer — Visual Style Guide (Jetpack Compose)

> **SUPERSEDED (2026-10-06) for every token value and component spec.** The binding visual spec is now
> [`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`](../../docs/superpowers/specs/2026-10-06-oneui-redesign-design.md)
> §5 (tokens) and §6 (component kit), implemented in `:core:designsystem` (`theme/`, `icon/`, `component/`, `motion/`).
> Where this guide disagrees with the spec, the spec wins. In particular: the accent is `#2F6FF0`, and blue **text**
> always uses `accentText`, never `primary`; radii, sizes and type come from `RollaDimens` / `RollaShapes` / the semantic
> typography extensions; the library tab is labelled "Tracks" (the model stays `Song`). The principles in §1 still apply.

The look-and-feel spec for RollaMusicPlayer, derived from a One UI–style dark music UI. It defines the
visual language, tokens, and per-screen layouts that `m3-design-system-agent` implements in
`:core:designsystem` and that `ui-builder` builds against in `:core:ui` / `:feature:*`.

> This is a **visual contract**, not loose guidance. Every value here maps to a Material 3 theme token —
> components reference tokens, never hardcoded colors/sizes (enforced by m3-design-system-agent).
> Hex values are the starting palette; refine with the Material Theme Builder while preserving the roles.

---

## 1. Design Principles

1. **OLED-first dark.** True black (`#000000`) background; dark mode is the primary, fully-designed theme.
2. **Soft, rounded surfaces.** Content sits inside large-radius (24dp) dark containers inset from the screen edges. Nothing is sharp.
3. **One stable accent.** A single blue is the only accent — selection, active sliders, switches, links. Everything else is white/gray.
4. **Content-forward & calm.** Generous spacing, restrained color, art and titles carry the screen.
5. **One UI structure.** Large bold screen title, a center-weighted scrollable tab bar (the selected tab is largest), and a persistent pill **mini-player** anchored to the bottom.
6. **Line iconography.** Thin outline icons in white; the play glyph is the only solidly filled control.

---

## 2. Color Tokens (→ `MaterialTheme.colorScheme`)

Dark theme (primary). Map each to the Material 3 role; do not invent ad-hoc colors.

| Role (M3) | Dark value | Usage |
| --- | --- | --- |
| `background` | `#000000` | App background (true black, OLED) |
| `onBackground` | `#FFFFFF` | Primary text on background |
| `surface` | `#0E0E0E` | Base surface |
| `surfaceContainer` | `#1C1C1E` | The rounded content panel that holds lists/cards |
| `surfaceContainerHigh` | `#2A2A2C` | List-item thumbnails placeholder, raised chips, feature cards |
| `surfaceContainerHighest` | `#333335` | Pressed/hover state |
| `onSurface` | `#FFFFFF` | Titles, primary labels |
| `onSurfaceVariant` | `#9CA0A6` | Secondary text (artist, counts, paths, captions) |
| `primary` | `#4780FF` (WCAG AA audit 2026-07-15: lifted from `#3D7BFF`; dark-only value, see note below) | Accent: selected chip fill, active EQ sliders, switches ON, slider track, link/value text |
| `onPrimary` | `#001B3F` (WCAG AA audit 2026-07-15: deep navy, was `#FFFFFF`; dark-only value) | Text/icon on accent fills |
| `primaryContainer` | `#1E3A66` | Subtle accent surfaces if needed |
| `outlineVariant` | `#2C2C2E` | Hairline dividers between rows |
| `error` | `#FF5A5A` | Destructive only |

Custom semantic extensions (add to `Color.kt` as `ColorScheme.xxx` `@Composable get()`):

| Token | Dark | Usage |
| --- | --- | --- |
| `miniPlayerContainer` | `#241F2E` (faint purple-tinted dark) | The bottom mini-player pill |
| `fastScrollIndex` | full-opacity `onSurfaceVariant` (WCAG AA audit 2026-07-15: was `onSurfaceVariant` @ 60%, which only cleared ~3.2:1 against `surfaceContainer` — below the 4.5:1 text floor) | A–Z fast-scroll letters |
| `sliderInactiveTrack` | `#6E6E73` (WCAG AA audit 2026-07-15: raised from `#3A3A3C`, which only cleared ~1.5:1 against `surfaceContainer` — below the 3:1 component floor) | Inactive portion of EQ/seek/sliders |

**Light theme parity:** invert neutrals (`background #FFFFFF`, `surfaceContainer #F2F3F5`, `onSurface #111111`,
`onSurfaceVariant #5F6368`). **`primary` is NOT the same hex as dark** — WCAG AA audit 2026-07-15 proved no
single blue hex can simultaneously clear 4.5:1 for white-on-it (button fill) AND 4.5:1 for it-as-text-on-dark-
`surfaceContainer` (the luminance windows required for each don't intersect). Both schemes keep the same
~221° brand hue but use per-scheme lightness (M3-canonical practice): light `primary` = `#0F5CFF`,
`onPrimary` stays `#FFFFFF` (this darker fill clears 4.5:1 with white text, and the primary itself clears
4.5:1 as text on `background`/`surfaceContainer`). `sliderInactiveTrack` in light is `#838890` (lowered from
`#C4C6CA`, which only cleared ~1.5:1 against `surfaceContainer`). Light must be first-class, but true-black is
dark-only. Full contrast ratio table and derivation: m3-design-system-agent WCAG audit, 2026-07-15.

**Dynamic color:** optional Material You may tint neutrals, but the **accent blue stays brand-stable** by default
(offer dynamic accent as a setting, off by default). All combinations must meet WCAG AA (≥4.5:1 for text).

---

## 3. Typography (→ `MaterialTheme.typography` + semantic extensions)

| Element | Style (M3 role) | Size / Weight | Color |
| --- | --- | --- | --- |
| Screen title ("Samsung Music"/section) | `headlineMedium` | ~28sp / Bold | onSurface |
| Tab — selected | `titleLarge` | ~22sp / Bold | onSurface |
| Tab — unselected | `titleMedium` | ~16sp / Medium | onSurfaceVariant |
| Now-playing title | `headlineSmall` | ~24sp / Bold | onSurface (centered) |
| List item title (song/playlist) | `bodyLarge`/`titleMedium` | ~16sp / Medium | onSurface |
| List item subtitle (artist/path/count) | `bodyMedium` | ~14sp / Regular | onSurfaceVariant |
| Settings section header | `labelMedium` | ~13sp / Medium | onSurfaceVariant |
| Setting value (blue) | `bodyMedium` | ~14sp | primary |
| Caption / EQ description | `bodySmall` | ~13sp | onSurfaceVariant |
| EQ frequency / count labels | `labelSmall` | ~12sp | onSurfaceVariant |

Semantic typography extensions to define: `screenTitle`, `tabSelected`/`tabUnselected`, `songTitle`,
`artistName`, `sectionHeader`, `metadata`. Single sans-serif family (system default / a One UI-like grotesque).

---

## 4. Shape Scale (→ `MaterialTheme.shapes`)

| Token | Radius | Applied to |
| --- | --- | --- |
| `extraSmall` | 8dp | Small inner elements |
| `small` | 12dp | List thumbnails, small art |
| `medium` | 16dp | Inline cards, dialogs |
| `large` | 24dp | The content surface panel, settings group cards |
| `extraLarge` | 28dp | Big feature cards (Recently played), now-playing artwork |
| Pill / `CircleShape` | 50% | Chips, mini-player container, the round Play button, fast-scroll track |

Rule: containers and cards are **24dp+**; thumbnails **12dp**; anything button-like that's a "pill" is fully rounded.

---

## 5. Spacing & Sizing

- **Screen edge padding:** 16dp. The content **surface panel** is inset ~8–12dp from screen edges, radius 24dp, with 8–12dp inner padding.
- **List row:** height ~72dp; vertical padding 12dp; leading thumbnail **56dp** (12dp radius); 12–16dp gap to text; trailing 3-dot overflow (48dp touch target).
- **Dividers:** hairline `outlineVariant`, inset to start after the thumbnail.
- **Section spacing:** 24dp between groups; 16dp between cards.
- **Icons:** 24dp standard; large Play 56–64dp; mini-player transport 24dp.
- **Touch targets:** ≥48dp always.
- **Mini-player:** full-width minus 8dp side margins, height ~64dp, pill radius, sits above the nav-bar inset.
- **Feature cards (Recently played, etc.):** ~150×150dp square, 28dp radius, horizontally scrollable row with 12dp gaps.

---

## 6. Core Components (anatomy → Compose)

### TopAppBar
Large bold title left; trailing actions right (`Search`, optional `Add (+)`, `Overflow (⋮)`); a small accent
dot may sit on overflow for "new". Transparent over `background`. Use M3 `TopAppBar`/`LargeTopAppBar` with
`screenTitle` typography and `onSurface` icons.

### Tab Bar (One UI center-weighted)
Horizontally scrollable; the **selected tab is centered and largest (bold)**, neighbors smaller and gray.
No underline indicator — size/weight conveys selection. Build on `ScrollableTabRow` with a custom indicator
(none) and per-tab `tabSelected`/`tabUnselected` styles; animate size/weight on change.
App tabs: **Favourites · Playlists · Songs · Albums · Artists · Folders** (note: the library item is **Songs**,
the app's canonical model — never "Tracks").

### Content Surface Panel
The rounded `surfaceContainer` (24dp) that wraps each tab's content, inset from edges.

### Sort / Control Header (top of a list)
Left: sort icon + current sort label ("Name"). Right: circular **Shuffle** and **Play** buttons (Play =
`primary`-on-fill or white filled triangle in a circle). Row sits inside the surface panel, above the list.

### SongListItem
```
[ 56dp art ]  Title (songTitle, onSurface)            ⋮
   12dp r     Artist (artistName, onSurfaceVariant)
──────────────────────── hairline divider ────────────
```
Leading 56dp/12dp artwork (Coil; placeholder = music-note glyph on `surfaceContainerHigh`), two-line text
(1-line ellipsized each), trailing overflow. Stable `key` on the song id. Whole row clickable → play / open.

### Fast-Scroll A–Z Index
Vertical alphabet pinned to the right edge of the list, `fastScrollIndex` color, ~12sp; drag jumps to section.

### Feature Cards (Recently played / Favourites / Most played)
Square 28dp cards; either a centered large music-note glyph on `surfaceContainerHigh`, or an album-art
collage when populated; label (onSurface) + count (onSurfaceVariant) below. Horizontal `LazyRow`.

### Playlist Row
56dp thumbnail + name (onSurface) + trailing count ("0 tracks", onSurfaceVariant).

### Mini-Player (persistent)
Pill (`miniPlayerContainer`, full radius, 8dp margins). Left: circular album art. Center: title + "Unknown"/
artist (two lines, ellipsized). Right: transport icons (prev · play/pause · next · queue). Tap body → expand
to Now-Playing (shared-element artwork — coordinate with compose-animation-agent).

### Now-Playing Screen
Top bar: collapse chevron (down) left; right actions (cast/▷, volume, **equalizer (bars)**, overflow).
Centered large artwork (extraLarge radius). Centered title + artist. Action row (queue/lyrics · ♥ favorite · +).
Seekbar: `primary` active track, circular thumb, time labels (`metadata`) at both ends. Transport row:
shuffle · prev · **Play (large)** · next · repeat. Subtle bottom gradient wash is acceptable (keep within tokens).

### Equalizer Screen
A `surfaceContainer` card holding **9 vertical sliders** with value labels above (`labelSmall`) and frequency
labels below (63·125·250·500·1k·2k·4k·8k·16k). Active sliders `primary`, inactive `sliderInactiveTrack`.
Below: a **2-column pill chip grid** of presets (Balanced, Bass boost, Smooth, Dynamic, Clear, Treble boost,
Custom) — selected = `primary` filled / `onPrimary` text; unselected = `surfaceContainerHigh` / onSurface.
Caption (`bodySmall`) describes the selected preset.
> Engineering note: the app's canonical bands are 40Hz–10kHz (see `implement-equalizer`); use the app's band
> set and labels, this layout is the visual reference. Resolve label set with equalizer-agent.

### Settings Screens
Grouped **rounded cards** (`large`) under small gray **section headers** (Playback · Playlists · General · Privacy).
Row types:
- **Toggle row:** label (+ optional sub-label) left, M3 `Switch` right (ON = `primary`, OFF = gray).
- **Value/navigation row:** label left, blue value text below (`primary`) or chevron — tap opens detail.
- **Slider row:** label, centered value (e.g. "1.0x"/"Off"), `Slider` with `primary` active track.

### Chips, Sliders, Switches
Chips: pill, selected `primary`-filled. Sliders: `primary` active / `sliderInactiveTrack` inactive / circular
thumb. Switches: `primary` ON.

---

## 7. Screen Layout Blueprints

**Library — Songs (also Albums/Artists/Folders share this skeleton):**
```
┌───────────────────────────────────────────────┐
│ Samsung-style Title            🔍   ⋮          │  ← TopAppBar
│ Favourites  Playlists   SONGS   Albums  Artists│  ← center-weighted tabs
│ ┌── surfaceContainer (24dp) ───────────────┐   │
│ │  ⇅ Name                        🔀  ▶      │   │  ← sort/control header
│ │  [art] Title                         ⋮  A │   │
│ │        Artist                  ────────  B │   │  ← rows + A–Z index
│ │  [art] Title                         ⋮  C │   │
│ │        Artist                            …│   │
│ └──────────────────────────────────────────┘   │
│ ╭── mini-player (pill) ───────────────────╮     │
│ │ ◔ Title / Unknown      ⏮  ▶  ⏭   ☰       │     │
│ ╰─────────────────────────────────────────╯     │
└───────────────────────────────────────────────┘
```

**Playlists:** TopAppBar (+ Add) → tabs → two big feature cards (Recently played / Favourites) in a `LazyRow`
→ playlist rows with trailing counts → mini-player.

**Now-Playing:** chevron + actions bar → centered artwork → centered title/artist → action row (queue/♥/+) →
seekbar with times → transport row.

**Equalizer:** back + title → sliders card → preset chip grid → description caption.

**Settings:** back + title → section header → grouped card of rows → repeat per section.

---

## 8. Home Screen Widget (Glance) Style

Mirror the mini-player / notification card:
- **Container:** `surfaceContainer` (`#1C1C1E`), 24dp rounded, subtle/no elevation.
- **Layout:** album art (rounded 12dp) left; title (`onSurface`, 1 line) + artist (`onSurfaceVariant`) stacked;
  transport row of white line icons.
- **Controls (app spec):** previous · **−15s** · play/pause · **+15s** · next, plus a visual progress bar
  (`primary` filled) — keep the app's 15-second-skip controls (see `implement-home-widget`/widget-agent) while
  adopting this visual style; the Samsung shuffle/repeat layout is just the look reference.
- **Theme:** use `GlanceTheme` colors mapped to the same roles; artwork is a pre-downscaled local bitmap.
- Tap body → open Now-Playing.

---

## 9. Motion (summary — details owned by compose-animation-agent)

- Durations 200–300ms, spring for interactive elements.
- Mini-player → Now-Playing: **shared-element** album art + crossfade of metadata.
- Tab switch: horizontal slide + fade of content.
- Selection (tab, chip, switch): animate size/weight/color, not abrupt swaps.
- Respect reduced-motion preference.

---

## 10. Ownership & Enforcement

- **m3-design-system-agent** owns `:core:designsystem` — implements the color/typography/shape tokens and the
  semantic extensions above. Zero hardcoded values anywhere else.
- **ui-builder** builds the components/screens in `:core:ui` and `:feature:*` strictly from these tokens.
- **widget-agent** applies §8 via `GlanceTheme`.
- **compose-animation-agent** owns §9.
- **code-reviewer** flags any hardcoded color/size as a violation of this guide.

When a needed value is missing, add a token here first, then implement it — never hardcode to "match the spec".
```
