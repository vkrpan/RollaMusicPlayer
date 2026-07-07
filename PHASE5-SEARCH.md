# Phase 5 — Search

Adds fast, **local** search across the library — songs, albums, and artists — with debounced, cancellable
queries and a results screen that reuses the existing `:core:ui` list items. No network, no online
suggestions: it queries only the Room library (offline).

Branch: `git checkout -b feature/search`. One prompt at a time → ✅ checkpoint → commit. Build UI from the
`:core:designsystem` tokens and `ui-style-guide.md` (search bar, section headers, list items).

---

## Step 0 — Re-sync (no code)

```
Starting search on branch feature/search. Read CLAUDE.md, OWNERSHIP.md, ui-style-guide.md, and
navigation-conventions.md. Inspect :core:database, :core:data, and :core:ui and report: (1) what search-
capable DAO queries exist today; (2) whether Album/Artist domain models + list components (AlbumCard,
ArtistRow) already exist from the library feature, or if search should return songs only for now; (3) a
recommendation on Room FTS4 vs LIKE queries given expected library size. Also confirm the Search route
pattern from navigation-conventions.md (Search(query: String? = null)). Don't write code — give me the
plan and the FTS-vs-LIKE call.
```
✅ **Checkpoint:** clear picture of existing queries/components + an FTS-vs-LIKE decision.

## Prompt 1 — Data-layer search (`:core:database` + `:core:data`)

```
Use the data-layer-agent. Implement library search:
- Songs: either a Room FTS4 mapping over SongEntity (title/artist/album) for scalable matching, OR
  indexed LIKE queries — per the Step 0 decision. If FTS, bump MusicDatabase with a TESTED migration.
- Albums/Artists: DISTINCT queries filtered by name (only if those models exist; otherwise songs-only).
- SearchRepository.search(query: String): Flow (or suspend) returning a grouped SearchResults(
  songs: List<Song>, albums: List<Album>, artists: List<Artist>), mapped to :core:model, on
  Dispatchers.IO. Empty/blank query returns empty results. Add DAO tests (and a migration test if FTS).
  Build.
```
✅ **Checkpoint:** search returns correct grouped results; DAO (+ migration, if FTS) tests pass.
`git commit -m "feat(core-data): library search (songs/albums/artists)"`

## Prompt 2 — SearchViewModel (`:feature:search`)

```
Use the viewmodel-architect agent. SearchViewModel exposes a query: StateFlow<String> and a
SearchUiState (Idle / Loading / Empty / Results). Pipe the query through debounce(~300ms) +
distinctUntilChanged + flatMapLatest { repository.search(it) } so a new keystroke cancels the previous
search (no stale results). Run query work off the main thread; keep emissions granular. Use
SavedStateHandle for an optional initial query. Build.
```
✅ **Checkpoint:** typing debounces; rapid edits cancel in-flight searches; states model idle/loading/empty/results.
`git commit -m "feat(feature-search): debounced, cancellable SearchViewModel"`

## Prompt 3 — Search screen (`:feature:search`)

```
Use the ui-builder agent. Build SearchScreen per ui-style-guide:
- A search field at top (M3 SearchBar/TextField) with a clear (✕) button, auto-focus on entry, and the
  IME "search" action; apply imePadding so the keyboard never covers results (implement-edge-to-edge-and-insets).
- Results grouped under section headers (sectionHeader token): Songs / Albums / Artists — reuse
  :core:ui SongListItem, AlbumCard/AlbumRow, ArtistRow (whichever exist). Stable keys.
- Empty state (prompt "Search your library"), and a distinct no-results state for a non-empty query.
- Tap a song → play; tap album/artist → its detail screen.
Tokens only; 48dp targets; content descriptions. Build.
```
✅ **Checkpoint:** searching filters live, grouped results render, keyboard insets correct, taps route/play.
`git commit -m "feat(feature-search): search screen"`

## Prompt 4 — Navigation + entry points (`:app`)

```
Use the navigation-agent. Add the type-safe Search(query: String? = null) route (navigation-conventions.md)
and register :feature:search's nav entry. Wire the search (🔍) action in the library and playlists top
bars → Search. Give it explicit back behavior. Optional: a navDeepLink<Search> (rollamusic://search) that
falls back gracefully on malformed args. Build and verify navigation both ways.
```
✅ **Checkpoint:** the search icon opens search from library/playlists; back returns correctly.
`git commit -m "feat(app): search route + entry points"`

## Prompt 5 — Recent searches (optional)

```
Use the viewmodel-architect + data-layer-agent with implement-datastore. Persist recent queries in
DataStore (a small capped list). Show them when the query is empty, tapping one re-runs it; include a
"clear recent searches" action. Keep it local (DataStore only). Build.
```
✅ **Checkpoint:** recent searches persist across launches and can be cleared.
`git commit -m "feat(feature-search): recent searches"`

## Prompt 6 — Review gate + tests

```
Run code-reviewer, compose-performance-auditor, and test-writer in parallel. Fix all CRITICAL/HIGH:
offline/no-network (queries hit Room only — no MediaStore/online suggestions), no main-thread queries,
stale-search cancellation, recomposition scope + stable keys in results, imePadding correct, and no
hardcoded visuals bypassing :core:designsystem / ui-style-guide.md. Coverage: SearchRepository/DAO search
tests (+ migration if FTS), and SearchViewModel debounce/cancellation with Turbine + a TestDispatcher.
Re-run ./gradlew check until green.
```
✅ **Checkpoint:** no CRITICAL/HIGH; `./gradlew check` green; search data + ViewModel debounce/cancel covered.
`git commit -m "test(feature-search): coverage + review fixes"`

Then merge `feature/search` and update CLAUDE.md "Current Status".

---

## Notes & gotchas
- **Cancel stale searches.** `flatMapLatest`/`collectLatest` on the debounced query is what prevents an
  earlier slow query from overwriting newer results — the #1 search bug.
- **Debounce ~300ms.** Enough to avoid querying on every keystroke without feeling laggy.
- **FTS vs LIKE.** FTS4 scales better and ranks matches but adds a virtual table + migration; indexed LIKE
  (`%query%`) is simpler and fine for modest libraries. Decide in Step 0 and keep it consistent.
- **Local only.** Search hits the Room library, never MediaStore live or any network — no online
  suggestions/autocomplete. Airplane-mode safe by construction.
- **Keyboard insets.** Use `imePadding()` so results aren't hidden behind the keyboard (edge-to-edge).
- **Reuse, don't rebuild.** Render results with the existing `:core:ui` items so search matches the rest of
  the app automatically.

## Next after search
`:feature:settings` (grouped cards per ui-style-guide §6, DataStore-backed — theme, playback options,
privacy), then `:feature:widget`, then baseline-profile performance polish. Tell me "search done" and I'll
write the settings phase.
```
