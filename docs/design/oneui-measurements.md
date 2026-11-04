# One UI reference measurements

Raw measurements taken from the Samsung Music reference screenshots in `media player example/`.
They feed the One UI redesign spec
(`docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`). They are reference data, not a spec.

## How these were taken

- All screenshots are 1080×2340 px JPEGs from the same phone.
- Colors were sampled with `System.Drawing` (PowerShell) using one of three methods:
  - **avg:** the mean of a 5×5 block, used for flat fills.
  - **max:** the brightest pixel in a box, used for text, lines and glyphs. Antialiasing means the true color is at least this bright.
  - **bluest:** the pixel with the highest B−R value in a box, used for blue text and tracks.
- Geometry comes from scanning a pixel row or column for a target color (±tolerance). Text height is the bounding box of
  pixels above a luminance threshold. For words without descenders, that is roughly the ascender height (≈0.75 em in a grotesque).
- **Density confirmed (Phase 7 Task 0, 2026-10-27).** The reference phone, a Samsung SM-S901B on Android 14, reports
  `wm density` 480 (3.0 px/dp), `wm size` 1080×2340 (360 × 780 dp) and `font_scale` 1.0, read once over adb. So the
  dp and sp columns below, which assumed 3.0 px/dp and a font scale of 1.0, stand unchanged (Phase 7 ruling P6). The
  calibration captures came from the Motorola edge 70, also at 3.0 px/dp (406.7 dp wide), so the two are compared by
  distance from the nearer edge.
- Scripts: `measure.ps1` and `sample.ps1` in the session scratchpad. Re-run them if the screenshots change.

## Sampled colors

| Element | Screenshot | Method | Value |
|---|---|---|---|
| Screen background | all | avg | `#010101` (black) |
| Content panel / settings card / EQ card | Tracks, Settings, EQ | avg | `#171719` |
| Header circle buttons (shuffle/play) | Tracks | avg | `#2D2D2F` |
| Unselected EQ chip | EQ | avg | `#2D2D2F` |
| Selected EQ chip | EQ | avg | `#377AFF` |
| List thumbnail placeholder | Tracks, Playlists | avg | `#454547` |
| Feature card placeholder | Playlists | avg | `#454547` |
| Now Playing art placeholder | Now Playing | avg | `#3B3B3B` |
| Now Playing art edge highlight | Now Playing | max | `#5C5C5C` |
| Widget art placeholder | Widget | avg | `#363636` |
| Widget card | Widget | avg | `#010101` |
| Divider | Tracks, Settings | max | `#3A3A3C` (3 px ≈ 1 dp) |
| A–Z rail track | Tracks | avg | `#333333` |
| A–Z letter | Tracks | max | `#9D9D9D` |
| Primary text (titles, labels) | all | max | `#FCFCFE` |
| Secondary text (subtitles, counts, section headers) | Tracks, Settings, Playlists | max | `#9B9B9D` |
| Sort label "Name" | Tracks | max | `#A4A4A6` |
| EQ value / frequency labels | EQ | max | `#A4A4A6` |
| Unselected tab text | Tracks | max | `#7E7E80` |
| Overflow ⋮ dots | Tracks | max | `#9B9A9F` |
| Mini-player pill | Tracks | avg | `#282035` |
| Mini-player art circle | Tracks | avg | `#504C5A` (≈ white @ 18 % over the pill) |
| Mini-player subtitle "Unknown" | Tracks | max | `#FCFBFF` (white, not gray) |
| Now Playing bg: top | Now Playing | avg | `#080808` |
| Now Playing bg: middle (y≈1290 px) | Now Playing | avg | `#120F16` |
| Now Playing bg: y≈1520 px | Now Playing | avg | `#18151E` |
| Now Playing bg: bottom-left | Now Playing | avg | `#231C2C` (purple) |
| Now Playing bg: bottom-middle | Now Playing | avg | `#252A30` |
| Now Playing bg: bottom-right | Now Playing | avg | `#293332` (teal-gray) |
| Now Playing bg: right, mid-height | Now Playing | avg | `#151619` |
| Seek track and thumb | Now Playing | max | `#FBFAFF` (white) |
| Time labels | Now Playing | max | `#F6F6F8` |
| EQ grid lines | EQ | max | `#1D1D1F` |
| EQ inactive track | EQ | max | `#5F5F61` |
| EQ active track | EQ | bluest | `#377AFF` |
| EQ dimmed (disabled-band) active track | EQ | bluest | `#344F86` |
| EQ thumb ring | EQ | bluest | `#367AFF` |
| EQ thumb center | EQ | avg | `#171719` (hollow: shows the card color) |
| EQ caption | EQ | max | `#E6E6E6` |
| Settings value text ("Off", "Play all tracks") | Settings | bluest | `#5B8FFD` |
| Settings slider active | Settings | bluest | `#377AFF` |
| Settings slider inactive | Settings | avg | `#5E5D62` |
| Settings slider thumb center | Settings | avg | `#010101` (black) |
| Settings slider thumb ring | Settings | bluest | `#377AFF` |
| Switch off: track / thumb | Settings | avg | `#646368` / `#FCFCFE` |
| Switch on: track / thumb | Settings | avg | `#377AFF` / `#FCFCFE` |
| Widget artist text | Widget | max | `#B0B0B2` |

## Geometry (original px, then dp at 3.0 px/dp)

### Library (Tracks) screen

| Element | px | dp @3.0 |
|---|---|---|
| App title "Samsung Music" start x | 72 | 24 |
| App title glyph top / bottom (cap box) | 174–218 | 58–73 |
| Header search icon glyph | 52 tall, x 837–888 | glyph ≈17 |
| Selected tab "Tracks" glyph box | x 445–637, y 339–387 (h 49) | h ≈16 |
| Unselected tab "Albums" glyph box | y 348–376 (h 29) | h ≈10 |
| Panel top edge | y 465 | 155 |
| Panel corner radius | ≈75 (fitted at x = 2, 10, 30) | 25 |
| Panel horizontal extent | 0–1079 (edge to edge) | — |
| Sort icon start x | ≈67 | 22 |
| Sort label "Name" glyph box | x 153–253, y 536–563 (h 28) | text start 51 |
| Header circle buttons, each | ⌀ ≈101 | ⌀ ≈34 |
| Gap between shuffle and play circles | ≈43 | ≈14 |
| Play circle right edge | x 1034 (46 from screen edge) | 15 from edge |
| Circle glyphs (shuffle, play) | 38 tall | ≈13 |
| List thumbnail | 144 × 142, x 54–197 | 48 × 48, start 18 |
| List thumbnail corner radius | ≈32 | ≈11 |
| Row pitch | 209 | ≈70 |
| Row title start x | 247 | 82 |
| Row title glyph box "Aidan Rudd" | h 38 | ≈17 sp |
| Row subtitle glyph box "Unknown" | h 29 | ≈13 sp |
| Divider | x 246–989, 3 thick | from 82 to the rail; 1 |
| A–Z rail | x 990–1055 (w 66), y 642–1972 | w 22, right margin 8 |
| A–Z letter "A" glyph | h 23 | ≈11 sp |
| Overflow ⋮ glyph | x 926–933, h 46 | ≈15 tall |
| Mini-player pill height | y 2016–2195 (h 180) | 60 |
| Mini-player bottom to screen bottom | 145 (includes nav bar) | 48 (≈ nav bar) |
| Mini-player art circle | ⌀ 114, x 33–146 | ⌀ 38, start 11 |
| Mini title glyph box "Manalive" | h 35 | ≈16 sp SemiBold |
| Mini subtitle glyph box "Unknown" | h 25 | ≈12 sp |
| Mini transport glyphs (play, prev) | h 37–38 | ≈13 |

### Now Playing

| Element | px | dp @3.0 |
|---|---|---|
| Collapse chevron glyph | x 81–134, y 187–214 | 18 × 9 at x 27 |
| EQ bars icon glyph | 45 tall | ≈15 |
| Artwork | 505 × 501, x 288–792, top y 390 | 168 square, top 130 |
| Artwork corner radius | ≈74 | ≈25 |
| Title glyph box "Devilfish - Manalive" | y 990–1035 (h 46) | ≈21 sp, centered y ≈337 |
| Artist glyph box "Unknown" | y 1080–1109 (h 30) | ≈14 sp, centered y ≈365 |
| Heart icon glyph | 50 tall, y 1601–1650 | ≈17, row center y ≈542 |
| Seek track | 9 thick, y 1762–1770, x ≈87–990 | 3 thick, y ≈589, inset 29 |
| Seek thumb | ⌀ ≈47 (visual estimate; the row scan returned 65 px because the white active track runs into the thumb) | ⌀ ≈16 |
| Time label glyph "5:54" | h 24, y 1800–1823, x 87 | ≈11 sp, y ≈604 |
| Play glyph (no circle) | 68 tall | ≈23 |
| Prev glyph | 39 tall | 13 |
| Shuffle glyph | 46 tall | ≈15 |
| Transport row center | y ≈1965 | ≈655 |

### Equaliser

| Element | px | dp @3.0 |
|---|---|---|
| Back chevron glyph | x 79–106, y 198–251 | 9 × 18 at x 26 |
| Title "Equaliser" glyph box (with descender) | x 173–432, y 202–261 | start 58, ≈22 sp Bold |
| Card | x 0–1079, y 328–1363 | edge to edge, top 109, h 345 |
| Card corner radius | ≈76 | ≈25 |
| Value label glyph "0" | h 28, y 389–416 | ≈14 sp, y ≈134 |
| Slider track length | y 461–1172 | ≈237 |
| Slider track width | 8–9 | 3 |
| Thumb ring outer diameter | 38 | ≈13 |
| Column centers | 195 … 883 px, pitch ≈86 | first 65, pitch 28.7 |
| Grid line pitch | ≈71 | ≈24 |
| Frequency label glyph "250" | h 23, y 1215–1237 | ≈12 sp, y ≈409 |
| Chip | ≈426 × 118, first at x 85 | 142 × 40, start 28 |
| Chip column gap / row pitch | ≈56 / ≈159 | ≈19 / 53 |
| Chip text glyph "Balanced" (Bold) | h 38 | ≈17 sp |
| Caption glyph line | h 31, x 157 | ≈14 sp, start 52 |

### Settings

| Element | px | dp @3.0 |
|---|---|---|
| Back chevron glyph | x 91–118, y 174–227 | 9 × 18 at x 30 |
| Title glyph box "Music" (in "Samsung Music settings") | h 48, title starts x ≈181 | ≈22 sp Bold, start 60 |
| Section header "General" glyph box | h 29, x 86 | ≈13 sp Medium, start 29 |
| Card | x 30–1049 | inset 10 each side, w 340 |
| Card text start | x 88 | 29 from screen (19 inside card) |
| Divider | x 78–1001 | inset 16 inside card |
| Row label "Dark mode" glyph box | h 38 | ≈17 sp |
| Value "Off" glyph box | h 29 | ≈14 sp |
| Slider track | 42 thick, x ≈152–927 | 14 thick, x 51–309 |
| Slider thumb | ⌀ ≈61 (visual estimate; the row scan returned 97 px because the blue active track runs into the thumb) | ⌀ ≈20, black fill, blue ring |
| Slider value "1.0x" glyph | h 28 | ≈14 sp, centered |
| Switch track (off) | 105 × 50, x 879–983 | 35 × 17 |
| Switch thumb | ⌀ 48 | ⌀ 16 |
| Sub-label glyph line | h 37 (with descenders) | ≈14 sp |

### Playlists

| Element | px | dp @3.0 |
|---|---|---|
| Feature card | ≈431 × 430 | 143 square |
| Gap between feature cards | ≈50 | ≈17 |
| Feature card corner radius | ≈61 | ≈20 |
| Big note glyph in card | 131 tall | ≈44 |
| Card label "Favourite tracks" glyph box | h 30, centered | ≈14 sp white |
| Card count "5 tracks" glyph box | h 26 | ≈12 sp gray |
| Playlist row pitch (single-line rows) | ≈167 | ≈56 |
| Playlist row name "Light" glyph box (with descender) | h 47 | ≈17 sp |
| Trailing count "0 tracks" glyph box | h 29, right edge x 951 | ≈13 sp gray, ends 43 from the screen edge (before the rail) |
| Header "+" icon glyph | 55 tall | ≈18 |

### Widget

| Element | px | dp @3.0 |
|---|---|---|
| Card | x ≈54–1030 (display 46–880) | ≈24 radius, pure black |
| Art | 207 × 203, x 83 | 69 square |
| Title glyph box | h 35 | ≈15–16 sp Bold |
| Artist glyph box | h 30 | ≈13 sp, `#B0B0B2` |
| Play glyph | 54 tall | 18 |

## Observations that are not numbers

- Selected tab weight is **Regular**, not Bold. Size and color alone carry the selection. There is no underline indicator.
- Tabs are clipped at both screen edges ("avourites", "Album"), and the selected tab is horizontally centered.
- The Playlists A–Z rail shows a condensed alphabet (A B D F I K M O Q S V X Z). The Tracks rail shows almost every letter.
  The rail drops letters to fit its height.
- The panel continues under the mini-player. The last list row is partly hidden behind the pill.
- Mini-player: the title marquees (it starts mid-word in the screenshots). The subtitle is **white**.
- Now Playing: the play control is a bare solid triangle, with no circular container. Prev and next are solid "bar + triangle" glyphs.
  Shuffle, queue, ♥ and + are line icons.
- The fifth transport icon is One UI's "A→" play-order glyph. Repeat-all and repeat-one take its place when active.
- EQ: the first two bands render dimmed (gray ring and a dark-blue track). That is a device-specific state, not part of the design.
- Settings rows: the label sits on top and the blue value below it. Switch rows center the switch vertically with the label block.
- Widget: pure black (`#010101`) card, no border. The example's controls are shuffle · prev · ▶ · next · A→. RollaMusicPlayer keeps its ±15 s
  controls (a user decision) and adopts only the visual style.

## Phase 7 calibration (2026-10-31)

The Phase 7 device comparison (reference: Samsung SM-S901B, 3.0 px/dp, font scale 1.0; ours: Motorola edge 70 at
3.0 px/dp, dark theme) measured 298 rows. Its per-row evidence, overlays and the full OUT list are in the Phase 7
workspace (`calibration-diffs.md`) and `docs/design/calibration/phase7/`. Horizontal positions are distances from the
nearer screen edge; vertical positions are below the status bar. The rows below are the token changes it produced
(Task 3a). Structural fixes that consume these tokens (Now Playing arrangement, EQ columns, slider insets, the last-row
divider, the navigation-bar scrim, the widget ±15 drawables) land in Task 3b.

Re-measures that correct this document: the settings switch track is **60 px (20 dp)** tall, not 50 px; the EQ card is
y 321–1370 (350 dp), not 345 dp; the mini-player pill has **no side margin** (it spans x 0–1079).

| Element | Reference | Before | After | Token |
|---|---|---|---|---|
| Home header ⋮ centre from end | 36.3 dp | 27.8 dp (`topBarEdge` 4 dp) | 35 dp | `RollaDimens.topBarEdge` 4 → 11 dp |
| Now Playing chevron start / ⋮ centre from end | 27.0 / 36.3 dp | 19.7 / 27.8 dp | 26.7 / 35 dp | `RollaDimens.topBarEdge` 4 → 11 dp |
| Settings chevron start | 30.3 dp | 23.3 dp | 30.3 dp | `RollaDimens.topBarEdge` 4 → 11 dp |
| Settings chevron end → title start | 21.3 dp | 28.0 dp | title stays at 60.7 dp | `RollaDimens.detailTitleGap` 6 → 0 dp |
| Tab label gap (glyph to glyph) | 23.6 dp | 29.0 dp | ≈ 23 dp | `RollaDimens.tabSpacing` 28 → 22 dp |
| Selected tab cap top / tab centre (content) | 78.7 / 86.3 dp | 84.3 / 92 dp | tab centre 88 dp | `RollaDimens.tabRowHeight` 56 → 48 dp |
| Panel top (content) | 120.3 dp | 132.0 dp | 120 dp | `RollaDimens.panelTopGap` 12 → 8 dp (with `tabRowHeight` 48) |
| Thumbnail note glyph height | 14.7 dp | 17.3 dp | ≈ 14.4 dp | `RollaDimens.placeholderGlyph` 24 → 20 dp |
| Feature card note glyph height | 43.7 dp | 31.7 dp | ≈ 43.7 dp | `RollaDimens.featureCardGlyph` 44 → 61 dp |
| Now Playing art note glyph height | 51.3 dp | 43.0 dp | ≈ 51.6 dp | `RollaDimens.npArtGlyph` 60 → 72 dp |
| Widget art note glyph height | 28.3 dp | 31.3 dp | ≈ 28.7 dp | `RollaDimens.widgetArtGlyph` 44 → 40 dp |
| Widget art radius | 14.9 dp | 12.4 dp | 15 dp | `RollaDimens.widgetArtRadius` 12 → 15 dp |
| Mini-player side margin | 0 dp | 4 dp | 0 dp | `RollaDimens.miniPlayerSideMargin` 4 → 0 dp |
| Mini-player art start (screen) | 11.0 dp | 15.0 dp | 11 dp | follows `miniPlayerSideMargin` (`miniPlayerArtStart` 11 dp unchanged) |
| Mini-player transport end distance | passes today | 8 dp (4 margin + 4 end) | 8 dp | `RollaDimens.miniPlayerEnd` 4 → 8 dp |
| Mini-player art end → subtitle start | 5.7 dp | 12.7 dp | ≈ 5.7 dp | `RollaDimens.miniPlayerArtTextGap` 12 → 5 dp |
| Mini-player Play glyph height / Queue glyph width | 12.7 / 15.0 dp | 16.0 / 19.3 dp | ≈ 12.7 / 15 dp | new `RollaDimens.miniTransportCompactIcon` 19 dp (was `iconSize` 24 dp) |
| Playlists header + glyph | 18.3 dp | 15.7 dp | ≈ 18 dp | new `RollaDimens.headerActionIconLarge` 28 dp (was `iconSize` 24 dp) |
| Now Playing action Queue / Heart glyph | 17.7 / 16.7 × 18.7 dp | 21.3 / 19.0 × 21.3 dp | ≈ 18.8 / 16.8 × 18.8 dp | `RollaDimens.npActionGlyph` 26 → 23 dp |
| Now Playing Shuffle / Play order glyph | 15.3 × 17.3 / 15.3 × 17.0 dp | 18.0 × 20.7 / 20.7 × 19.3 dp | ≈ 15.2 × 17.5 / 17.5 × 16.3 dp (the play-order glyph's height stays 2.2 dp over: its path is taller than the reference's) | `RollaDimens.npTransportGlyph` 26 → 22 dp |
| Now Playing action row and transport end centres from the edges | 43 dp | 52.8 / 68 dp | 43 dp | new `RollaDimens.npControlEdgeInset` 19 dp |
| Now Playing prev / next centre from the nearer edge | 111.0 / 111.5 dp | 131.8 dp | 111 dp | new `RollaDimens.npTransportInnerCentre` 111 dp |
| Now Playing artwork top (content) | 94.7 dp | 84.7 dp (`npArtTop` 130 dp from the screen top) | 95 dp | new `RollaDimens.npArtTopGap` 95 dp below the status bar |
| EQ first / last column centre from the edge | 65.2 dp | 88.5 dp | 65 dp | new `RollaDimens.eqColumnEdgeInset` 65 dp (ruling P12; `eqColumnPitch` 28.7 dp becomes the minimum) |
| EQ caption start | 52.3 dp | 25.0 dp | 52 dp | new `RollaDimens.eqCaptionStart` 52 dp |
| EQ frequency label bottom → card bottom | 44.0 dp | 22.7 dp | 44 dp (from the labels' baseline) | new `RollaDimens.eqCardBottomPadding` 44 dp |
| EQ value label bottom → track top | 14.7 dp | 11.0 dp | 14.7 dp (from the labels' baseline; the half-thumb inset counts inside) | new `RollaDimens.eqValueLabelTrackGap` 14.7 dp (Task 3b fix round 1, ruling P20) |
| EQ chip start / end inset | 28.3 dp | 24.0 dp | 28 dp | `RollaDimens.chipGridStart` 24 → 28 dp |
| EQ chip row gap | 14.0 dp | 17.0 dp | 14 dp | new `RollaDimens.chipRowGap` 14 dp; `chipRowLayoutGap` 9 → 6 dp |
| Settings title baseline → first header; card bottom → next header | 42.0 / 18.0 dp | 53.3 / 29.0 dp | ≈ 42 / 18 dp | `RollaDimens.settingsSectionGap` 24 → 13 dp |
| Settings switch track height / thumb | 20.0 / 16.0 dp | 17.0 / 15.0 dp | 20 / 16 dp | `RollaDimens.switchTrackHeight` 17 → 20 dp, `switchThumb` 15 → 16 dp, `switchThumbInset` 1 → 2 dp |
| Settings switch end inset | 32.0 dp | 29.0 dp | 32 dp | new `RollaDimens.settingsTrailingEnd` 22 dp |
| Settings slider track start / end inset | 50.7 / 51.0 dp | 39.0 / 39.0 dp | 51 dp | new `RollaDimens.settingsSliderInset`, 41 dp inside the card (first set to 31 dp by an arithmetic slip, corrected in the device re-measure round, ruling P22) |
| Sort label, EQ value and frequency label colour (max) | `#A4A3A9` | `#9B9B9D` (`onSurfaceVariant`) | `#A4A4A6` (7.19:1 on `#171719`) | new `labelSecondary` (light = `#646467`) |
| EQ caption colour (max) | `#E6E6E6` | `#FCFCFE` (`onSurface`) | `#E6E6E6` (16.83:1 on `#000000`) | new `eqCaption` (light = `#111113`) |
| Mini-player art colour (avg) | `#504C5A` | `#4F4859` (white @ 18 %) | `#504C5A`, opaque (note `#FCFCFE` on it 8.12:1) | `miniPlayerArtPlaceholder` (dark; light unchanged) |
| Now Playing background top (avg) | `#080808` | `#020203` (`#000000` stop) | `#080808` | `nowPlayingGradient.top` (dark; light unchanged) |
| Mini-player art note glyph | 11.7 dp tall, also on a failed load | absent on a failed load (`placeholderGlyph` box when shown) | ≈ 11.5 dp, whenever the placeholder shows | new `RollaDimens.miniPlayerArtGlyph` 16 dp (Task 3b, ruling P19) |

Task 3b wired these tokens into the screens: the Now Playing artwork anchor (`npArtTop` is gone), the action-row and
transport insets, the seek track's full span, the EQ columns (ruling P12), the full-length EQ track, the EQ card's bottom
padding (from the frequency labels' baseline, where the digits end), the caption start, the settings slider and switch
insets, the missing divider under a list's last row, the mini-player's note on a failed load, a transparent navigation
bar with no contrast scrim (ruling P14), and the widget's ±15 glyphs (scaled by 0.84 to about 18 dp) and 15 dp artwork
box corners. The value label → track top gap took its own token in the fix round (`eqValueLabelTrackGap`, ruling P20).
The EQ slider card then lays out 347 dp tall at 360 dp (JVM layout), against the reference's 350 dp: 3 dp short, an
accepted deviation under ruling P20 (no second token), unless the device re-measure finds it within ±2 dp.

Device re-measure round (ruling P22, on build 812b05c; the per-row evidence is the workspace's `calibration-recheck.md`):

| Element | Reference | Re-measured | After | Token |
|---|---|---|---|---|
| Now Playing action + glyph | 18.5 dp | 15 dp (`npActionGlyph` 23 dp) | ≈ 18.3 dp | new `RollaDimens.npActionAddGlyph` 28 dp (Queue and the heart keep `npActionGlyph` 23 dp) |
| Now Playing previous / next glyph width | 17.7 dp | 15.3 dp (`npTransportGlyph` 22 dp) | ≈ 17.3 dp | new `RollaDimens.npSkipGlyph` 24 dp (shuffle and play-order keep `npTransportGlyph` 22 dp) |
| Widget art note glyph width / height | 25.7 / 28.3 dp | 22.7 dp wide (40 dp box) | ≈ 23.8 / 30.1 dp | `RollaDimens.widgetArtGlyph` 40 → 42 dp |
| Selected tab cap bottom → panel top; panel top (content) | 26.3 / 120.3 dp | 24.0 / 120 dp | ≈ 26 / 122 dp | `RollaDimens.panelTopGap` 8 → 10 dp (the re-capture must confirm both rows) |
| Home navigation-bar area | black (`#010101`) | the panel `#171719` | the black background | Home's panel ends at the navigation bar's top (no token; spec §7.4) |
| Settings slider track start | 50.7 dp | 41.0 dp (`settingsSliderInset` 31 dp) | 51 dp | `RollaDimens.settingsSliderInset` 31 → 41 dp |

Confirmed on the device (2026-11-01, build e16f8d7 and later, Tracks, Playlists, Now Playing, Settings and the widget
re-captured in the dark theme): every row above passes. The tab-to-panel gap, the panel top, both navigation-bar
colour rows (now `#000000` against `#010101`), the + glyph's height and width, the previous glyph's width, the slider
track's start and end inset, and the widget note's width all moved from OUT to PASS. No row that passed before failed.
Two rows differ only because of what the captures show: the mini-player title cap height (the title in this capture has
no M or P to measure) and the play / pause glyph height (the player was paused).

Accepted deviations from the re-measure (ruling P22), recorded and not changed:
- `MusicNote`'s width, like the play-order glyph's height (ruling P18): the feature card note draws 34.7 dp wide against
  the reference's 39.7 dp, and the Now Playing art note 41.0 dp against 46.3 dp. Both heights pass; the difference is
  the path's aspect, not its box.
- The Equaliser title start (60.7 dp against 57.7 dp) is covered by ruling P11(b): that bar is SoundAlive's, and the
  Settings bar of the same shared top bar passes.

Recorded, not changed (Phase 7 ruling P11): the widget card radius (20.6 vs 24 dp; spec §13 keeps the launcher's system
radius), the EQ top-bar rows (that bar is SoundAlive's), the first settings header → card gap (the reference's two
sections disagree), the EQ chip width (chips fill the wider row), the selected-chip text (`#FFFFFF`; `#FCFCFE` on
`#2F6FF0` is 4.40:1), the Now Playing time-label colour (JPEG noise), and the Now Playing gradient's right-side
darkening and mid-bottom wash shape (decorative, Δ ≤ 13 per channel).
