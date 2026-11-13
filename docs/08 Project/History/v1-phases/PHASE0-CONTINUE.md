# Phase 0 — Continuation Prompts (resume after 3a)

You've completed **3a** (`:core:permissions` — the media permission flow). Since the original
`PHASE0-BOOTSTRAP.md` was written, the repo changed:

- The multi-module project is **scaffolded**.
- `:core:designsystem` now has **real tokens** — `Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`
  (`RollaMusicPlayerTheme`, semantic extensions `songTitle`, `artistName`, `miniPlayerContainer`,
  `sliderInactiveTrack`, `fastScrollIndex`).
- New binding rules: **`.claude/rules/ui-style-guide.md`** (visual contract) and
  **`.claude/rules/model-vocabulary.md`** (`Song`, never `Track`).

So the UI steps below explicitly build from the design-system tokens and `ui-style-guide.md`.
Same discipline as before: one prompt at a time, reach the **✅ checkpoint**, commit at each marker.

---

## Prompt R — Re-sync (run first, no code)

```
The repo has progressed since the original bootstrap. Read CLAUDE.md, OWNERSHIP.md, and
.claude/rules/ (especially ui-style-guide.md and model-vocabulary.md), then inspect the current
modules. Report: which modules/files exist, what's already implemented (scaffold, :core:designsystem
tokens, :core:permissions), and what's missing for the vertical slice. Confirm two things explicitly:
(1) :core:model defines `Song` (not Track) per model-vocabulary.md — if it doesn't exist yet, flag it;
(2) RollaMusicPlayerTheme + the design-system tokens are wired and ready for UI. Don't write code yet —
just give me the status and the exact next step.
```
✅ **Checkpoint:** accurate status; `:core:model` Song confirmed (or flagged to create first); design system confirmed wired. If `:core:model` is missing, run Step 2 from `PHASE0-BOOTSTRAP.md` before continuing.

---

## Prompt 3b — Database (`:core:database`)

```
Use the data-layer-agent and the add-room-database skill. In :core:database create SongEntity
(include media_store_id, date_modified, content_uri, artwork_uri), SongDao (observeAllSongs(): Flow,
upsertSongs, deleteByMediaStoreIds, getAllSongs), MusicDatabase v1, and the Hilt DatabaseModule.
Entities map to the :core:model Song (model-vocabulary.md — never Track). Add a DAO instrumentation
test. Build.
```
✅ **Checkpoint:** `:core:database` builds; DAO test passes.
`git commit -m "feat(core-database): Song entity, DAO, MusicDatabase v1"`

## Prompt 3c — Scanner (`:core:data`)

```
Use the media-scanning-agent and the implement-media-scanning skill. In :core:data implement
MediaScanner (MediaStore.Audio query, IS_MUSIC filter, stable content:// URIs, TRACK-number
normalization), the ScannedSong→SongEntity mapper, and LibraryIndexer.sync() doing an incremental
diff into Room (no full wipe). All on Dispatchers.IO, cursors closed. Build.
```
✅ **Checkpoint:** `:core:data` builds; scanner is background + cursor-safe.
`git commit -m "feat(core-data): MediaStore scanner + incremental indexer"`

## Prompt 3d — Repository (`:core:data`)

```
Use the data-layer-agent and the implement-repository-pattern skill. In :core:data add SongRepository
exposing observeSongs(): Flow<List<Song>> mapping entities to :core:model Song. Wire it through Hilt.
Build.
```
✅ **Checkpoint:** repository exposes a domain `Song` Flow; builds.
`git commit -m "feat(core-data): SongRepository"`

## Prompt 3e — Playback (`:core:media`)

```
Use the audio-engineer agent and the media3-playback rules. In :core:media implement the Media3
ExoPlayer playback service with MediaSession, audio focus, and a foreground notification, plus a
PlaybackController (play(song), togglePlayPause, next, previous). Play from content:// URIs and expose
the audioSessionId for the future equalizer. Register the service in the manifest with
foregroundServiceType="mediaPlayback". Build.
```
✅ **Checkpoint:** `:core:media` builds; service registered; plays a content URI.
`git commit -m "feat(core-media): ExoPlayer service + MediaSession + PlaybackController"`

## Prompt 3f — Library UI (`:feature:library` + `:core:ui`)

```
Use the ui-builder and viewmodel-architect agents. Build the library slice strictly from the
design-system tokens and ui-style-guide.md:
- In :core:ui create SongListItem per ui-style-guide §6 (56dp/12dp `small` artwork via the
  implement-image-loading-coil skill, two-line text using MaterialTheme.typography.songTitle /
  artistName, trailing overflow, hairline outlineVariant divider, stable key on song id).
- In :feature:library build LibraryScreen per ui-style-guide §7 (content surface panel, sort/control
  header) backed by LibraryViewModel (implement-state-management): triggers the scan on permission
  grant, exposes songs: StateFlow<List<Song>>. Tap → PlaybackController.play(song).
- Wrap the screen in the :core:permissions gate. Wrap everything in RollaMusicPlayerTheme.
- No hardcoded colors/type/shape — tokens only (ui-style-guide, enforced by code-reviewer). Build.
```
✅ **Checkpoint:** `:feature:library` builds; SongListItem matches the style guide; reads from the repository Flow.
`git commit -m "feat(feature-library): SongListItem + LibraryScreen from design tokens"`

## Prompt 3g — Wire the app end-to-end

```
Use the navigation-agent. Register :feature:library's nav entry as the start destination in the :app
NavHost, inject the playback service/controller, and run on a device IN AIRPLANE MODE. Verify
end-to-end: grant permission → library populates from local files → tap a song → it plays with working
notification controls. Confirm the screen renders edge-to-edge with correct insets
(implement-edge-to-edge-and-insets).
```
✅ **Checkpoint:** full path works on-device in airplane mode. **This is Phase 0 success.**

## Prompt 3h — Review gate

```
Run code-reviewer, compose-performance-auditor, and test-writer over the slice in parallel. Fix all
CRITICAL/HIGH findings — especially any network usage, memory leaks, missing list keys, main-thread
I/O, and any hardcoded color/type/shape that bypasses :core:designsystem / ui-style-guide.md. Add unit
tests for LibraryViewModel and SongRepository. Re-run ./gradlew check until green.
```
✅ **Checkpoint:** no CRITICAL/HIGH findings; `./gradlew check` green; ViewModel + repository tested.
`git commit -m "feat: vertical slice — scan, list, play one song (offline, end-to-end)"`

---

## After Phase 0 — feature loop

Phase 0 proves the agent mesh. Now build feature-by-feature, each on its own branch, reusing the loop
**skill → owning agent → build → review gate → commit**, and always from design-system tokens +
`ui-style-guide.md`:

1. **`:feature:player`** — now-playing (ui-style-guide §6/§7) + the persistent **mini-player pill**
   (`miniPlayerContainer` token); mini-player → now-playing shared-element transition
   (compose-animation-agent, §9).
2. **`:feature:playlists`** — feature cards + playlist rows.
3. **`:feature:equalizer`** — equalizer-agent effect in `:core:media` + screen (sliders card + preset
   chip grid, ui-style-guide §6); persist active state with implement-datastore.
4. **`:feature:tageditor`** — tag-editor-agent (scoped-storage write consent).
5. **`:feature:search`** — local search over the library.
6. **`:feature:widget`** — widget-agent, Glance, ui-style-guide §8 + 15s-skip controls.
7. **Polish** — generate-baseline-profile once startup + library scroll are stable; setup-static-analysis
   gates in CI if not already on.

Keep CLAUDE.md's "Current Status" updated, and let OWNERSHIP.md settle any "who edits this file" question.
```
