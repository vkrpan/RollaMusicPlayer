# Phase 9 — Post-1.0 Maintenance

v1.0.0 shipped at the end of Phase 8 (plus two widget hot-fix commits: `33ef91a`, `cb44620`). This
phase works the post-1.0 backlog recorded in CLAUDE.md, in its stated priority order. It is
maintenance, not feature scope: the three deliberate cuts (custom tags, A–Z fast-scroll, tab
bar/Folders) stay cut unless explicitly asked for.

Branch: `claude/zen-pascal-oz10b8`. One prompt at a time → ✅ checkpoint → commit.

---

## Prompt 1 — Phase 8 review follow-ups ✅

```
Close the three non-blocking items from the Phase 8 final review gate (CLAUDE.md "Review
follow-ups"): (1) isolate TagSaveFinalizer's artwork invalidation from the playback refresh;
(2) make AlbumArtworkCacheImpl's miss-path write atomic so the unlocked hit-path exists() can't
see a partial file; (3) wire or trim AlbumArtworkCache.invalidations. Tests for each.
```

What landed:
1. **`TagSaveFinalizer`** — invalidation moved into `invalidateArtworkBestEffort`, its own
   per-album try/catch (same pattern as `SettingsViewModel.clearArtworkCacheBestEffort`). A cache
   failure no longer skips `refreshPlayback`, and one failing album no longer skips the others.
   The test that pinned the old "refresh is skipped" behavior now asserts the refresh still runs.
2. **`AlbumArtworkCacheImpl.get`** — a miss writes `{albumId}.jpg.tmp`, stamps it, then
   `renameTo`s it into `{albumId}.jpg` (atomic within one directory), so a file is only ever
   visible complete. It also re-checks `exists()` after taking the mutex, so the loser of a
   same-album race returns the winner's file instead of rewriting it (and bumping `lastModified`).
   A failed rename drops the temp file and returns null (placeholder), the same as undecodable art.
3. **`AlbumArtworkCache.invalidations`** — **trimmed.** Both consumers already detect staleness
   through the rewritten file's `lastModified` (Coil's file cache key, the widget's decode memo),
   and the widget loader's KDoc had already rejected subscribing as redundant. Removed from the
   interface + impl; the two emission tests became plain delete/no-op tests; `:core:data` no
   longer needs Turbine.

✅ **Checkpoint:** `./gradlew :core:data:testDebugUnitTest :feature:tageditor:testDebugUnitTest detekt`
green. *(Not run in the cloud session that made this change: it has no Android SDK, and the
network policy blocks downloading one. Run it locally before merging.)*

## Prompt 2 — Decide `:core:common`'s fate (needs a decision)

`:core:common` is still an empty `Placeholder.kt`, while three modules each declare their own IO
dispatcher qualifier (`CoreDataIoDispatcher`, `TagEditorIoDispatcher`, `WidgetIoDispatcher`) and
four build files declare a `:core:common` dependency that supplies nothing. Two options:

- **Consolidate:** one `@IoDispatcher` qualifier + `DispatchersModule` in `:core:common`, migrate
  the three per-module qualifiers onto it, delete the duplicates.
- **Delete:** remove the module and its `implementation(project(":core:common"))` edges; keep the
  per-feature qualifiers (they work, and they keep features independently testable).

```
build-tooling-agent + viewmodel-architect: inventory every dispatcher qualifier and every
:core:common dependency edge, then implement the chosen option. No behavior change; all unit
tests green; update CLAUDE.md's module table.
```

## Later (only on an explicit ask)

Custom tag system · A–Z fast-scroll index · center-weighted tab bar + Folders browsing. Each is a
new feature with its own phase doc, not a Phase 9 task.
