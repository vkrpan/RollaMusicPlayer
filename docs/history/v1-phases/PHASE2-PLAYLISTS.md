# Phase 2 — Playlists

Adds user playlists (create / rename / delete / reorder / add-remove songs) **and** the smart playlists
from the reference UI (Recently played · Favourites · Most played · Recently added). This is the first
feature that changes the **database schema** (many-to-many + play-tracking columns) — so it starts with a
Room migration, done carefully by data-layer-agent.

Branch: `git checkout -b feature/playlists`. One prompt at a time → ✅ checkpoint → commit. Build from the
`:core:designsystem` tokens and `ui-style-guide.md` (§6 Feature Cards / Playlist Row, §7 Playlists blueprint).

---

## Step 0 — Re-sync (no code)

```
Starting the playlists feature on branch feature/playlists. Read CLAUDE.md, OWNERSHIP.md, and
.claude/rules/ (ui-style-guide.md §6/§7, model-vocabulary.md, add-room-database patterns). Inspect
:core:model, :core:database, and :core:data and report: the current MusicDatabase version; whether
SongEntity has play_count / last_played / is_favorite / date_added columns; whether a Playlist model and
any playlist tables exist. Don't write code — list exactly what the playlists feature must add and the
migration needed.
```
✅ **Checkpoint:** clear inventory of current schema + a concrete migration plan.

## Prompt 1 — Schema + migration (`:core:database`)

```
Use the data-layer-agent and the add-room-database skill. Extend the schema (bump MusicDatabase to the
next version and write a tested Migration, never destructive):
- New tables: PlaylistEntity (id, name, created_at, updated_at) and PlaylistSongCrossRef
  (playlist_id, song_id, position, added_at) with indices + foreign keys (onDelete CASCADE), plus a
  PlaylistWithSongs @Relation.
- Add to SongEntity any missing play-tracking columns: is_favorite (default 0), play_count (default 0),
  last_played (nullable). (date_added should already exist from the scanner.)
- PlaylistDao: observe playlists with song counts, get PlaylistWithSongs, insert/rename/delete playlist,
  add/remove song, and a @Transaction reorder(playlistId, orderedSongIds) that rewrites positions.
Add DAO tests and a migration test (MigrationTestHelper) proving no data loss. Build.
```
✅ **Checkpoint:** migration test passes (existing songs preserved); DAO tests green.
`git commit -m "feat(core-database): playlist tables + play-tracking columns + migration"`

## Prompt 2 — Repositories (`:core:data`)

```
Use the data-layer-agent and the implement-repository-pattern skill.
- PlaylistRepository: observePlaylists(): Flow<List<Playlist>> (with counts), observePlaylistSongs(id):
  Flow<List<Song>>, create/rename/delete, addSongs/removeSong, reorder(id, orderedSongIds) — all mapping
  entities ↔ :core:model, on Dispatchers.IO, reorder in a single transaction.
- Extend SongRepository with: toggleFavorite(songId), and the smart-playlist queries returning
  Flow<List<Song>>: recentlyAdded (by date_added desc), recentlyPlayed (last_played desc),
  mostPlayed (play_count desc), favourites (is_favorite = 1). Wire Hilt. Build.
```
✅ **Checkpoint:** both repositories expose domain `Song`/`Playlist` Flows; reorder is transactional.
`git commit -m "feat(core-data): PlaylistRepository + smart-playlist queries + favourites"`

## Prompt 3 — Play tracking + favourite wiring (`:core:media` / `:feature:player`)

```
Use the audio-engineer and viewmodel-architect agents. When a song actually starts playing, increment
its play_count and set last_played (via a use case / SongRepository call on Dispatchers.IO — do NOT do
DB work on the player callback thread). Also wire the previously-stubbed ♥ favorite action in
NowPlaying (and the mini-player if present) to SongRepository.toggleFavorite, reflecting is_favorite
state. Build.
```
✅ **Checkpoint:** playing a song updates play_count/last_played; the heart toggles and persists.
`git commit -m "feat: play-count tracking + favourite toggle"`

## Prompt 4 — Playlists tab screen (`:feature:playlists`)

```
Use the ui-builder and viewmodel-architect agents. Build PlaylistsScreen per ui-style-guide §7
(Playlists blueprint) and §6:
- Top bar with a "+" (create playlist) action alongside search/overflow.
- A horizontal LazyRow of smart-playlist Feature Cards (§6 Feature Cards): Recently played, Favourites,
  Most played, Recently added — square 28dp cards, centered music-note glyph on surfaceContainerHigh or
  an art collage when populated, label (onSurface) + count (metadata token) below.
- Below, user playlists as Playlist Rows (§6): 56dp thumbnail + name (songTitle token) + trailing count
  (metadata token, e.g. "0 tracks").
PlaylistsViewModel exposes smart playlists + user playlists from the repositories. Tokens only. Build.
```
✅ **Checkpoint:** Playlists tab matches the blueprint; smart + user playlists populate live.
`git commit -m "feat(feature-playlists): playlists tab (smart cards + user rows)"`

## Prompt 5 — Playlist detail + smart-playlist detail (`:feature:playlists` + `:app`)

```
Use the ui-builder, viewmodel-architect, and navigation-agent agents. Add type-safe routes
(navigation-conventions.md): PlaylistDetail(playlistId: Long) and a SmartPlaylist(kind: String) (or
similar) — pass IDs/keys only, never objects. Build PlaylistDetailScreen: reuse :core:ui SongListItem,
a sort/control header (§6) with Play + Shuffle, tap a song → play in that list's context, per-row remove,
and an informative empty state. The same screen backs both a user playlist and a smart playlist (the
ViewModel picks the source by route). Register the nav entry in :app and wire navigation from the tab.
Build and verify navigation both ways.
```
✅ **Checkpoint:** opening a playlist (user or smart) lists its songs, plays, and removes; back works.
`git commit -m "feat(feature-playlists): playlist detail + routes"`

## Prompt 6 — Drag-and-drop reorder

```
Use the ui-builder and compose-animation-agent agents. Add long-press drag-to-reorder to the user
PlaylistDetail list (smart playlists are not reorderable). Use a local, no-network reorderable approach
(a pure-Compose reorderable LazyColumn library vetted by build-tooling-agent for the offline rule, or a
manual detectDragGesturesAfterLongPress + animateItem implementation). Persist the new order via
PlaylistRepository.reorder in a transaction on drop. Keep stable keys; 60fps; respect reduced-motion.
Build.
```
✅ **Checkpoint:** reorder is smooth, persists across app restart, and only applies to user playlists.
`git commit -m "feat(feature-playlists): drag-and-drop reorder"`

## Prompt 7 — Create / rename / delete + add-to-playlist

```
Use the ui-builder and viewmodel-architect agents. Add:
- Create-playlist dialog (from the tab "+"), rename + delete (from a playlist's overflow) with a
  confirm on delete.
- An "Add to playlist" bottom sheet launched from the SongListItem overflow (library + playlist detail)
  and the NowPlaying "+" stub: lists user playlists + a "New playlist…" option, adds the song(s) via
  PlaylistRepository.addSongs. Respect the "don't allow duplicates" behavior.
All dialogs/sheets use M3 components + design tokens (ui-style-guide). Build.
```
✅ **Checkpoint:** full playlist lifecycle works; songs can be added from library, detail, and now-playing.
`git commit -m "feat(feature-playlists): create/rename/delete + add-to-playlist"`

## Prompt 8 — Review gate + tests

```
Run code-reviewer, compose-performance-auditor, and test-writer in parallel over the feature. Fix all
CRITICAL/HIGH: offline/no-network, transaction correctness for reorder, memory leaks, recomposition
scope, missing keys, main-thread DB/IO, and hardcoded visuals bypassing :core:designsystem /
ui-style-guide.md. Ensure coverage: migration test, PlaylistDao tests, PlaylistRepository tests
(create/add/remove/reorder), and PlaylistsViewModel / PlaylistDetailViewModel tests (Turbine). Re-run
./gradlew check until green.
```
✅ **Checkpoint:** no CRITICAL/HIGH; `./gradlew check` green; data + ViewModel coverage in place.
`git commit -m "test(feature-playlists): coverage + review fixes"`

Then merge `feature/playlists` and update CLAUDE.md "Current Status".

---

## Notes & gotchas
- **Migration is the risk point.** Insist on a passing `MigrationTestHelper` test before anything else —
  users may already have a populated v1 library from the scanner.
- **Smart vs user playlists:** smart playlists are derived queries (no rows in PlaylistEntity), so they're
  read-only — no reorder, no manual add/remove. Only user playlists get the full lifecycle.
- **Favourites** is just `is_favorite` on Song surfaced as a smart playlist — reuse the same toggle the
  now-playing heart uses.
- **Reorder dependency:** if you add a reorderable library, it must be local/offline — build-tooling-agent
  vets it and the `checkNoNetwork` gate must still pass.

## Next after playlists
`:feature:equalizer` (equalizer-agent effect on the audioSessionId + sliders/preset screen, active state
via implement-datastore) — tell me "playlists done" and I'll write that phase.
```
