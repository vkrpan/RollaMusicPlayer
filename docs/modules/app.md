---
type: module
status: active
gradle: ":app"
owners: ["[[navigation-agent]]", "[[build-tooling-agent]]", "[[ui-builder]]"]
tags: [module]
related: ["[[Modules]]", "[[Home and library]]", "[[Now Playing and mini-player]]", "[[Home replaces the bottom navigation]]", "[[Navigation conventions]]"]
---

# app

`:app` — Application, `MainActivity`, the NavHost and routes (`navigation/Routes.kt`), the app shell (Home, mini-player host), DI root, Coil `ImageLoaderFactory`, the app theme entry.

- **Owner agent(s):** [[navigation-agent]], [[build-tooling-agent]], [[ui-builder]]
- **Features:** [[Home and library]], [[Now Playing and mini-player]]
- **Decisions and rules:** [[Home replaces the bottom navigation]], [[Navigation conventions]]

## Changes
- 2026-11-08 (per-track artwork Task 3): `artwork/SongArtworkInterceptor` replaces `AlbumArtworkInterceptor` in
  `RollaApp`'s Coil `ImageLoader`: a `rolla-artwork://` value resolves through `SongArtworkCache` (`peek`, then `get`);
  no picture or a cache failure returns an error result, so the `AsyncImage` placeholder shows; other data passes
  through unchanged.
- 2026-11-09 (per-track artwork Task 8): `HomeRoute` obtains `SelectionArtworkViewModel` and drives `selectionBusy`;
  `HomeScreen` gains the `selectionActions` slot (ruling P2), a `SnackbarHost` above the pill, and locks the selection
  (`SongSelectionState.isLocked`, set in a `SideEffect`), the pager, ✕, Back and Edit tags while busy.
- 2026-11-11 (custom tags phase 3, Task 5): route `ManageCustomTags` (16 routes; no mini-player), registered in
  `MainActivity` as a plain push; Home ⋮ "Manage tags" above "Settings"; both editor routes navigate there from the
  picker link (the sheet closes first).
