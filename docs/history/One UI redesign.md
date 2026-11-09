---
type: history
status: done
date: 2026-11-01
tags: [history, redesign, oneui]
related: ["[[Timeline]]", "[[One UI redesign decisions]]", "[[Backlog]]", "[[Ledgers]]"]
source: "CLAUDE.md Current Status (moved into the vault 2026-11-08)"
---

# One UI redesign (Phases 1–7)

Branch `feat/oneui-redesign`; final commit `15821b4` (Phase 7), pushed 2026-11-04, waiting for the user's merge into
`main`. Spec: `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`. Roadmap:
`docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md`. The user's decisions: [[One UI redesign decisions]].
Per-phase rulings: [[Ledgers]].

What the redesign shipped (moved verbatim from CLAUDE.md):

> **One UI redesign: Phases 1–7 complete on branch `feat/oneui-redesign`, pending the user's commit and merge.**
> The binding spec is `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`; the phases and their plans are in
> `docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md`. Where this file disagrees with the spec, the spec wins.
> What the redesign shipped:
> - **App identity:** the launcher label and every user-visible name is "Rolla Music"; an adaptive icon (white note on
>   `#2F6FF0`, with a monochrome layer for themed icons; a layer-list below API 26).
> - **Design system:** One UI tokens (`RollaDimens`, `RollaShapes`, semantic colours and type), `RollaIcons` (no
>   Material icons: detekt's `ForbiddenImport` plus the `checkNoMaterialIcons` scan, which also catches fully
>   qualified references) and the kit in `:core:designsystem/component/` (sheets, dialogs, menus, fields, pill buttons,
>   switch, round checkbox, sliders, tab row, content panel, A–Z rail). Calibrated against the reference screenshots on
>   the Motorola edge 70 in Phase 7 (`docs/design/oneui-measurements.md`); the remaining differences are recorded there
>   as accepted.
> - **Home** is the start destination: the "Rolla Music" header, the centre-weighted six-tab pager (Favourites ·
>   Playlists · Tracks · Albums · Artists · Folders), each tab in a content panel, and a floating mini-player pill.
>   Tracks, Favourites, Playlists (user rows), Artists and Folders have the A–Z rail; every Home list is in one library
>   order (`#` first, then A–Z, ignoring case and accents; `:core:common`'s `LibraryOrder.kt`). Everything below
>   Home's header ends at the navigation bar's top. No bottom navigation bar; `LibraryScreen` and `PlaylistsScreen`
>   are gone.
> - **Screens:** Now Playing (with a real queue sheet), Equaliser, Settings, Search, the tag editors, the permission
>   prompt, AlbumDetail, ArtistDetail, PlaylistDetail, SmartPlaylist and FolderDetail, all on the kit. UI copy says
>   "Tracks" and "Equaliser"; code says `Song` and `Equalizer*`. A missing artist shows as "Unknown"
>   (`artistDisplayName`), while data and routes keep the raw `<unknown>`.
> - **Data:** Room **v5**: v4 added `songs.folder_path` (`FolderDao`, `FolderRepository`), v5 the playback queue
>   (`playback_queue`, `playback_state`). All migrations are in one `ALL_MIGRATIONS` list that `MigrationTest` replays.
> - **Queue restore:** the queue, position, shuffle order and repeat mode are saved and restored paused on the next
>   start; a headset or Bluetooth Play resumes them. The stock-Android System UI resume card is designed for, not
>   verified (see "Data Storage Strategy"); Samsung One UI does not offer it.
> - **Widget:** the One UI look (spec §13), device-checked on the Motorola edge 70 (API 36) on 2026-10-23 and
>   2026-10-27: one row by default, a centred mode for taller cards (ruling P14), the artwork hiding below 339 dp.
>   Not checked on a device: a real artwork bitmap, a large system font, the light system theme, anything below API 31.
> - **Build hygiene (Phase 7):** no annotation-target or CDS warnings in builds and test logs; the detekt plugin version
>   is in the version catalog; a refreshed baseline profile covers the tab swipes and the rail's first jump.
