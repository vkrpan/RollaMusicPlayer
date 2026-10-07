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
- **Density is not known yet.** The dp columns assume **3.0 px/dp**, the value the 3-button nav bar, panel radius
  and type scale agree on. Text sp values assume a font scale of 1.0. Both get replaced by the real
  `adb shell wm density` and `settings get system font_scale` values during device calibration (spec §11).
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
