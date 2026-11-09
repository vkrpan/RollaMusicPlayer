# Phase 8 — Polish & Release

Feature work is done (Phases 1–7). This phase closes the remaining gaps and ships v1.0: the two
Phase-3-leftover detail screens (album/artist — search's taps are wired no-ops waiting on them),
artwork caching, a UI-polish + accessibility sweep, the outstanding test debt, a baseline profile,
and a signed, R8-minified release build verified in airplane mode.

**Scope decisions (record in CLAUDE.md when done):**
- **Custom tag system: CUT from v1.0** (was deferred in Phase 5). Revisit post-release.
- **Release = signed AAB/APK.** Store listing / distribution is out of scope for this phase.

Branch: `git checkout -b feature/polish-release` (after Step 0 merges the widget). One prompt at a
time → ✅ checkpoint → commit.

---

## Step 0 — Close out the widget + re-sync (no code)

The widget branch's last commit is Prompt 5 (`feat(core-media): push playback state...`) — Phase 7's
Prompt 6 (on-device verify + review gate + widget tests) has **no commit**, and `feature/widget` is
unmerged. Finish that first if it wasn't done.

```
Read CLAUDE.md and OWNERSHIP.md. First: confirm Phase 7 Prompt 6 (widget on-device verify, review
gate, tests) is actually done — if not, run it now, commit, then merge feature/widget to main and
update CLAUDE.md Current Status. Then survey for this phase and report (no code): (1) what
SongRepository/AlbumRepository expose for fetching an album's songs and an artist's albums+songs;
(2) how search's synthetic FNV-1a artist ids relate to :core:data's Artist.id — what should an
ArtistDetail route carry so BOTH library and search can navigate to it (MediaStore id? name string?);
(3) current artwork loading path (Coil direct-from-uri?) and where a disk cache would hook in;
(4) state of :baselineprofile module; (5) release build config today (minify? signing? version).
```
✅ **Checkpoint:** widget merged; answers to all five, including a recommendation for the artist-id problem.

## Prompt 1 — Album detail screen (`:feature:library` + nav)

```
navigation-agent: add @Serializable data class AlbumDetail(val albumId: Long) to Routes.kt +
composable<AlbumDetail> in the NavHost. viewmodel-architect + ui-builder: AlbumDetailViewModel
(album + songs by albumId from the repository) and AlbumDetailScreen per ui-style-guide — large
artwork header, title/artist/counts, Shuffle + Play buttons, song list (SongListItem with track
numbers), inside the 24dp surface panel. Wire entry points: album tap in the library Albums tab AND
search's dormant onAlbumClick (MainActivity composable<Search>). Tests: ViewModel + repository
query. Build.
```
✅ **Checkpoint:** album detail reachable from library and search; play/shuffle start the album queue.
`git commit -m "feat(library): album detail screen + route"`

## Prompt 2 — Artist detail screen (`:feature:library` + nav)

```
Use Step 0's recommendation for the id. Search's FNV-1a artist ids are documented list-key-only, so
either route by name (ArtistDetail(val artistName: String) — the actual GROUP BY key) or resolve to
the MediaStore artist id in :core:data before navigating — pick ONE and document why in Routes.kt.
navigation-agent: route + destination. viewmodel-architect + ui-builder: ArtistDetailScreen —
artist header (name, album/song counts), horizontal LazyRow of AlbumCards (tap → AlbumDetail), then
all songs. Wire library Artists tab + search's onArtistClick. Tests. Build.
```
✅ **Checkpoint:** artist detail works from both entry points; ids reconcile (no wrong-artist bugs).
`git commit -m "feat(library): artist detail screen + route"`

## Prompt 3 — Album artwork disk cache (`:core:data`)

```
data-layer-agent: implement local artwork caching — extract/downscale album art once into
app-specific storage (keyed by albumId), serve via Coil from the cache. Invalidate on: manual
rescan, and tag-editor artwork embedding (hook TagSaveFinalizer so an edited cover shows everywhere
immediately — now-playing, lists, widget). Bound the cache size; never block the UI thread; missing
art → existing placeholder. Tests for key/invalidation logic. Build and verify scroll performance
in the Albums tab improves or holds.
```
✅ **Checkpoint:** artwork loads from cache; tag-edit cover change propagates; rescan invalidates.
`git commit -m "feat(core-data): album artwork disk cache + invalidation"`

## Prompt 4 — UI polish sweep

```
Run code-reviewer + m3-design-system-agent + compose-performance-auditor in parallel over :core:ui
and every :feature:* screen against ui-style-guide.md: hardcoded colors/sizes/shapes (zero
tolerance), empty states on every list (library tabs, search, playlists, detail screens), divider
insets, 24dp panel insets, tab-switch motion per §9, reduced-motion respected, edge-to-edge insets
on every screen (incl. the two new detail screens). Fix everything CRITICAL/HIGH; list MEDIUMs and
fix the cheap ones. Do NOT add new features (e.g. skip A–Z fast-scroll if unbuilt — record it as a
conscious post-1.0 cut). Build.
```
✅ **Checkpoint:** no style-guide violations; every screen has a sane empty state.
`git commit -m "polish(ui): style-guide conformance sweep"`

## Prompt 5 — Accessibility pass

```
Full a11y audit, then fixes: TalkBack walk of every screen (library tabs, detail screens, search,
now-playing, mini-player, playlists, EQ, tag editor, settings, widget) — meaningful
contentDescriptions (purpose, not appearance; null on decorative), stateDescription on toggles /
play-pause / favorite, mergeDescendants on rows; ≥48dp touch targets everywhere (EQ sliders,
overflow buttons, widget controls); WCAG AA contrast in BOTH themes (re-check the
LocalRollaDarkTheme-dependent tokens); fontScale 1.5 — no clipped/overlapping text. Fix all
failures. Build.
```
✅ **Checkpoint:** TalkBack usable end-to-end; targets and contrast pass; 1.5x font scale survives.
`git commit -m "polish(a11y): TalkBack, touch targets, contrast, font scale"`

## Prompt 6 — Test debt (`test-writer`)

```
Close the known gaps: (1) MediaWriteRequester SDK-branch tests via Robolectric (or mockkStatic) —
the createWriteRequest 30+ / RecoverableSecurityException 29 / legacy ≤28 paths (currently only
covered indirectly); (2) any widget logic still untested from Phase 7 Prompt 6; (3) a small Compose
UI smoke suite (add-ui-testing-compose skill) for the critical paths: library → tap song → mini-player
→ expand now-playing; search query → tap result; add-to-playlist. Then ./gradlew check — green.
```
✅ **Checkpoint:** MediaWriteRequester branches tested; smoke suite passes; `check` green.
`git commit -m "test: MediaWriteRequester SDK branches + UI smoke suite"`

## Prompt 7 — Baseline profile (`:baselineprofile`, build-tooling-agent)

```
Follow the generate-baseline-profile skill. Journeys: cold start → library Songs list settle +
scroll → open now-playing. Generate on a managed device / physical device, commit the profile, and
verify it's packaged in the release build. Run the macrobenchmark before/after and report startup +
scroll-jank numbers.
```
✅ **Checkpoint:** profile generated + packaged; benchmark shows improvement (report numbers).
`git commit -m "perf: baseline profile (startup + library scroll)"`

## Prompt 8 — Release build (build-tooling-agent, `release-build` skill)

```
Follow the release-build skill. Release build type: minifyEnabled + shrinkResources with keep rules
verified for: kotlinx-serialization (@Serializable routes — nav breaks silently if stripped),
jaudiotagger (reflection-heavy), Media3, Glance receivers, Room, Hilt. Signing config from a local
keystore via local.properties/env — NEVER committed. versionCode 1, versionName "1.0.0". Confirm
checkNoNetwork + no INTERNET permission in the MERGED release manifest. Verify the hand-curated
Licenses list still matches final dependencies. Install the release build on device and regression-
test IN AIRPLANE MODE: play, EQ, tag edit (scoped-storage consent!), search, playlists, widget,
deep links (rollamusic://song, rollamusic://search), process-death state restore.
```
✅ **Checkpoint:** minified release build passes the full airplane-mode regression; nav + tag editor
survive R8; manifest clean.
`git commit -m "build: release config — R8, signing, v1.0.0"`

## Prompt 9 — Final gate + docs

```
code-reviewer: final pass over the phase's diff. Then update CLAUDE.md (Current Status → v1.0
feature-complete; record the custom-tag-system and fast-scroll cuts) and README (feature list,
build instructions, offline/privacy statement). Merge feature/polish-release, tag v1.0.0.
```
✅ **Checkpoint:** merged to main, tagged `v1.0.0`, docs current.
`git commit -m "docs: v1.0 status + README"` → `git tag v1.0.0`

---

## Notes & gotchas
- **R8 vs typed routes.** kotlinx-serialization route classes get stripped/renamed without keep
  rules — navigation then crashes only in release. Test every destination on the minified build.
- **R8 vs jaudiotagger.** The fork uses reflection; missing keeps = tag editor silently failing in
  release only. It's the #1 reason the airplane-mode regression must run on the RELEASE build.
- **Artist ids.** Search's FNV-1a ids must never be treated as MediaStore ids. One canonical key
  for ArtistDetail, documented at the route.
- **Artwork cache invalidation.** Tag-editor cover embedding already refreshes now-playing via
  replaceMediaItem — the cache is a new stale-data source; hook TagSaveFinalizer or edits look ignored.
- **Baseline profile needs release-like variant.** Generate against a non-debuggable build or the
  numbers are meaningless.
- **Keystore hygiene.** Keystore + passwords stay out of git; losing the keystore = losing the
  ability to update the app.
- **No scope creep.** Polish fixes what exists. New feature ideas → post-1.0 list in CLAUDE.md.
