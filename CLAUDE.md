# RollaMusicPlayer - Project Memory

Before the first tool call of any session — and before writing or
proposing a plan, not merely before executing one — invoke the
task-observer skill AND execute its Session Start Protocol (storage
check, frontmatter scan, review trigger). Loading the skill and running
the protocol are separate steps; a session that loads the file and stops
has activated nothing. Any turn that will involve a tool call counts; do
not classify the session as "too simple" from its opening message.

Select skills on the DECISION the request is about, not on the artefact it
arrived as. Name what the user is deciding, then match the installed skill
descriptions against that — a request handed over as a file to review
still needs the skill whose description names its subject.

After completing each task, list the observation records written this
session in the same turn and report a one-line summary from that listing
(ids and titles, or "none logged and why") — never from memory of
writing them. This is the activation backstop: it forces a look at the log,
so a session that silently skipped the protocol is discovered at the
first task boundary instead of never.

Loading a skill is not complete until you have queried the observation
log for OPEN observations naming it and read their bodies:
  find "C:/Users/VK/.claude/task-observer-workspace/skill-observations/observation-log" -maxdepth 1 \
    -name '*.md' -exec grep -l "skill:.*<skill-name>" {} +
(Use find, not a bare *.md glob. Under zsh an unmatched glob is an error,
so on an empty log the command never runs and the enclosing block aborts —
and 2>/dev/null does not help, because the redirection belongs to a
command that never starts. This is the first thing that fires on a fresh
install, in every session, for as long as the log is empty.)
Apply their insights to the current work — meaning: let them change what
you do in THIS task. Editing the skill file, or writing the rule into any
other file a later session reads, is acting on the observation and waits
for the review. See "Log, don't act" in SKILL.md: the test is whether the
action leaves a durable change outside the observation log. Run this at every skill load, however many skills load
in one session. The session-start scan does not cover it: that is a
frontmatter sweep over every observation at session start, this is a
body-level lookup for one skill at the moment its rules are applied.

The converse holds too: the grep, and any checkpoint line recording the
load, run only in the same batch as the Skill invocation, and take the
skill name from a load that has actually happened — never from a list of
skills you built to load. A checkpoint line written without the load is a
false record. Before writing to any file a skill reads at run time (a
state file), load that skill: a state file that describes itself is not a
substitute for the skill that owns it.

Whenever superpowers' writing-plans, subagent-driven-development, brainstorming or
test-driven-development loads, also load the user-owned companion skill
`superpowers-extras` in the same batch, if it is installed (staged by the
2026-10-17 observation review), and read its reference file for that host
skill before the host's first action.

The task-observer workspace for this project is:
  C:/Users/VK/.claude/task-observer-workspace
Every path the skill uses derives from that root and nothing else:
  C:/Users/VK/.claude/task-observer-workspace/skill-observations/observation-log/   (the log)
  C:/Users/VK/.claude/task-observer-workspace/skill-observations/cross-cutting-principles.md
  C:/Users/VK/.claude/task-observer-workspace/skill-updates/                        (staging root)
  C:/Users/VK/.claude/task-observer-workspace/skill-updates/PENDING.md              (staging manifest)
Never resolve any of them from the current working
directory — a cwd inside an ephemeral checkout (a git worktree, a temporary
clone) is torn down and takes the log with it. Never place the workspace
inside a skills-discovery directory or any path linked into one. If this
environment mints a separate project identity per checkout, or more than
one agent works this project, the pinned path above is the single shared
location; do not derive one per session, tool or project.

The user's repositories live under:
  [PROJECTS ROOT]
(the root the sibling check sweeps before declaring a skill absent;
observation-log.md, "Skill families and the sibling check").

A subagent dispatched by a session that already runs this protocol does
not run it again and does not write to the log. The controller observes,
because only it sees the whole task and its review; a subagent that
notices something worth logging says so in its final report, and the
controller writes it, running the id snippet per write. A start-up rule
in a project file reaches every agent the project ever dispatches.

An observation file comes into being only through
  bash "<skill directory>/scripts/new-observation.sh" <slug> "C:/Users/VK/.claude/task-observer-workspace"
which prints the path to write into. Never by copying another file's
header or counting a listing — including on the turn after a
compaction, when the skill body is out of context.

## Project Overview

**RollaMusicPlayer** is a feature-rich Android music player application built with modern Android development practices. The app focuses on providing an excellent local music playback experience with advanced features like equalizer, tag editing, and comprehensive library management.

## 🔒 Offline & Privacy Architecture

**RollaMusicPlayer is designed as a fully offline, privacy-first application.**

### Architectural Principles

1. **No Network Layer**: The application architecture intentionally excludes any networking components
2. **Local Data Only**: All data persistence uses local storage (Room database, DataStore, file system)
3. **Zero External Communication**: No analytics, crash reporting to external services, or data transmission
4. **Privacy by Design**: User data never leaves the device
5. **Minimal Permissions**: Only READ_MEDIA_AUDIO (Android 13+) or READ_EXTERNAL_STORAGE (older versions)

### Data Storage Strategy

- **Music Files**: Accessed from device storage via MediaStore API
- **Library Database**: Room SQLite database for metadata indexing
- **Playlists**: Stored in local Room database
- **Equalizer Presets**: Stored in local Room database
- **Settings**: DataStore (Preferences) for app configuration and equalizer active-state
- **Album Artwork**: Cached locally in app-specific storage
- **Playback State**: The queue, current track, position, shuffle order and repeat mode are saved in Room (v5 `playback_queue`/`playback_state`) and restored, paused, on the next start; Play from a headset or Bluetooth resumes them without opening the app. A System UI media card that keeps the saved track after the process has died, with a Play that resumes the queue the same way, is designed for the stock-Android System UI (AOSP `MediaResumeListener`) and is **not yet device-verified** (`PlaybackService` is a `MediaLibraryService`; no library is browsable). On Samsung One UI (SM-S901B, Android 14) it is not delivered, because One UI never adds new apps to its media resume list, so on that phone the card disappears when the process dies

### Technical Implications

- **No Retrofit/OkHttp**: No HTTP client libraries in dependencies
- **No Firebase**: No analytics, crash reporting, or cloud services
- **No Third-Party SDKs**: Avoid SDKs that collect data or require internet
- **Local Caching Only**: All caching strategies use device storage
- **Offline-First UI**: No loading states for network requests, no sync indicators

## Tech Stack

- **Language**: Kotlin 2.2 (2.2.21, KSP2)
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel), multi-module (hybrid feature + core)
- **Build**: Gradle Kotlin DSL with version catalog + convention plugins (build-logic); AGP 8.13.2, Gradle 8.13, `compileSdk 36`
- **Visual style**: One UI–inspired dark (OLED black, blue accent, soft rounded surfaces). Binding spec: `.claude/rules/ui-style-guide.md`; tokens implemented in `:core:designsystem` (Color.kt / Type.kt / Shape.kt / Theme.kt)
- **Audio Playback**: Media3 1.11.1 ExoPlayer
- **Database**: Room for local data persistence
- **Dependency Injection**: Hilt 2.57.2 (2.59.x needs AGP 9)
- **Coroutines**: For asynchronous operations
- **Media Session**: For playback control and notification integration
- **Foreground Service**: For background playback

## Core Features

### Essential Features
- **100% Offline Operation** - No internet connection required
- **Privacy Focused** - Zero data collection or external communication
- Local music file playback (MP3, FLAC, WAV, OGG, M4A, AAC)
- Complete playlist management (create, edit, delete, reorder)
- Shuffle and repeat modes (off, all, one)
- Background playback with notification controls
- Persistent playback queue across app restarts (restored paused; resumable from a headset or Bluetooth Play; a System UI media card that resumes it after the process has died is designed for the stock-Android System UI and not yet device-verified; Samsung One UI never adds new apps to its resume list, so it does not keep the card)

### Advanced Audio Features
- 8-band graphic equalizer (40Hz, 80Hz, 160Hz, 315Hz, 630Hz, 1.25kHz, 2.5kHz, 5kHz, 10kHz)
- Equalizer preset management (save/load custom presets)
- Gains-driven response curve visualization while adjusting (no live audio capture — a real
  `Visualizer` was deliberately rejected; it requires `RECORD_AUDIO`, which conflicts with the
  privacy-first positioning)

### Music Library Management
- Automatic music library scanning and indexing
- Metadata extraction and display
- Search across songs, albums, and artists (grouped results; substring match over Room)
- Album artwork extraction and local disk caching
- Full ID3 tag editing (title, artist, album, genre, year, track number, album artist, composer, artwork)
- Batch tag editing for multiple files
- Album detail and artist detail screens (reached via search or a library song's artist/album)

> A **custom user-defined tag system** was scoped for Phase 5 but cut from v1.0 — see "Post-1.0 cuts" under Current Status.

### Home Screen Widget
- Album artwork display with high-quality rendering
- Visual timeline/progress bar
- 15-second skip backward/forward buttons
- Previous/next track buttons
- Play/pause toggle
- Real-time updates during playback
- Tappable to open main app

## Project Structure

**Architecture: multi-module, hybrid feature + core (Now in Android style).** The app shell wires
everything; `:core:*` modules hold shared concerns; `:feature:*` modules hold one screen-area each.
See the `setup-modularization` skill for the full graph, convention plugins, and dependency rules.

```
:app                    # Application, MainActivity, NavHost wiring, DI root, app theme entry

:core
├── :core:model         # Domain models + enums (Song, Album, Artist, Playlist, ...). Pure Kotlin.
├── :core:common        # The library order (text/LibraryOrder.kt: indexLetter, sortedByLibraryName), shared by :core:data and :core:designsystem. Dispatcher qualifiers are still per module (e.g. CoreDataIoDispatcher, TagEditorIoDispatcher, WidgetIoDispatcher)
├── :core:database      # Room: entities, DAOs, migrations, MusicDatabase
├── :core:datastore     # DataStore: app settings (theme/dynamic color/speed/skip-silence), equalizer active-state, recent searches
├── :core:data          # Repositories + media scanner
├── :core:media         # Media3 playback service, MediaSession, audio session, equalizer effect
├── :core:designsystem  # Theme (Color/Type/Shape) + model-agnostic components
├── :core:ui            # Model-aware shared composables (SongListItem, AlbumCard, AlbumArtwork)
├── :core:permissions   # Runtime media permission gate + flow
└── :core:testing       # Fakes, fixtures, test rules

:feature
├── :feature:library    # Songs list + Album detail + Artist detail screens (no tab bar/Genres/Folders — see Post-1.0 cuts)
├── :feature:search     # Local search across the library
├── :feature:player     # Now-playing + mini-player
├── :feature:equalizer  # Equalizer screen (band sliders, presets UI)
├── :feature:playlists  # Playlist management + reordering
├── :feature:tageditor  # ID3 read/write screen + scoped-storage consent
├── :feature:settings   # App settings
└── :feature:widget     # Glance home screen widget

:baselineprofile        # Macrobenchmark + Baseline Profile generator (test module)
build-logic/            # Convention plugins (included build)
```

**Dependency direction**: `:feature → :core → :core:model`. Features never depend on each other;
core never depends on a feature; no cycles. Cross-feature flows go through `:core:data` or navigation.

### Agent ↔ Module Ownership (quick map)

- `:core:database` → data-layer-agent · `:core:data` scanner → media-scanning-agent
- `:core:media` → audio-engineer · equalizer effect → equalizer-agent
- `:core:designsystem` → m3-design-system-agent · `:core:ui` & `:feature:*` screens → ui-builder
- `:core:permissions` → permissions-agent · `:feature:widget` → widget-agent
- `:feature:tageditor` IO → tag-editor-agent · `:feature:*` ViewModels → viewmodel-architect
- `:app` NavHost + per-feature nav entries → navigation-agent
- `build-logic/`, root Gradle, version catalog, `:baselineprofile` → build-tooling-agent
- `:core:model` is a shared contract — settle model names once (the codebase uses **`Song`**, not `Track`)

## Development Phases

### Phase 1: Foundation (Weeks 1-2)
- Project setup and dependencies
- Basic UI structure with Compose
- Room database schema
- Media scanning implementation

### Phase 2: Core Playback (Weeks 3-4)
- ExoPlayer integration
- Playback service with MediaSession
- Notification controls
- Basic player UI

### Phase 3: Library Management (Weeks 5-6)
- Library screens (songs, albums, artists, genres)
- Search and filtering
- Album artwork handling
- Navigation implementation

### Phase 4: Playlists (Week 7)
- Playlist creation and management
- Playlist UI
- Drag-and-drop reordering

### Phase 5: Advanced Features (Weeks 8-9)
- 8-band equalizer implementation
- Preset management
- Audio visualization
- Tag editor (single and batch)
- Custom tag system

### Phase 6: Widget & Polish (Week 10)
- Home screen widget implementation
- UI/UX refinements
- Performance optimization
- Bug fixes

### Phase 7: Testing & Release (Weeks 11-12)
- Comprehensive testing
- Documentation
- Release preparation

### Phase 8: Polish & Release (post-Phase-7 closeout)
- Album detail and artist detail screens (closing out the Phase 3 leftover)
- Album artwork disk cache
- UI style-guide conformance sweep and accessibility (TalkBack, touch targets, contrast, font scale) pass
- Test debt closure (`MediaWriteRequester` SDK branches, Compose UI smoke suite)
- Baseline profile (startup + library scroll)
- Signed, R8-minified release build verified in airplane mode — **v1.0.0**

## Key Design Decisions

1. **Media3 over MediaPlayer**: Better API, more features, active development
2. **Jetpack Compose**: Modern UI toolkit, declarative approach
3. **MVVM Architecture**: Clear separation of concerns, testability
4. **Room Database**: Type-safe, compile-time verification
5. **Hilt for DI**: Official Android DI solution, good Compose integration
6. **Foreground Service**: Required for background playback on modern Android
7. **One UI–inspired dark design system**: True-black OLED, single blue accent, heavily rounded surfaces. Defined in `.claude/rules/ui-style-guide.md` and implemented as tokens in `:core:designsystem`. All UI agents/skills build from these tokens — never hardcode colors, type, or shapes.

## Important Considerations

- **Offline Operation**: No internet permission requested or used; all features work without connectivity
- **Privacy**: No analytics, no crash reporting to external services, no data collection
- **Local Storage**: All data stored on device; efficient caching and indexing strategies
- **Permissions**: READ_MEDIA_AUDIO (Android 13+), READ_EXTERNAL_STORAGE (older versions) - storage only
- **Scoped Storage**: Handle Android 10+ storage restrictions properly
- **Battery Optimization**: Proper service lifecycle management for background playback
- **Memory Management**: Efficient bitmap loading for album art from local storage
- **Audio Focus**: Handle audio focus changes properly for local playback
- **MediaSession**: Proper integration for system controls (no Android Auto streaming)

## Current Status

> **One UI redesign in progress (branch `feat/oneui-redesign`).** The binding spec is
> `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`, and the phases are in
> `docs/superpowers/plans/2026-10-06-oneui-redesign-roadmap.md`. Where this file disagrees with the spec, the spec wins.
> Landed so far:
> - **Phase 1:** design-system tokens, `RollaIcons` and the component kit.
> - **Phase 2:** Home is the start destination. It has a "Rolla Music" header, a center-weighted tab row, and a pager
>   with the Playlists and Tracks tabs (`PlaylistsTab`, `TracksTab`). `LibraryScreen`, `PlaylistsScreen`, the
>   `Library`/`Playlists` routes and the bottom nav are gone. The mini-player floats over content
>   (`MiniPlayerHost`, `LocalMiniPlayerInset`).
> - **Phase 3:** Now Playing, the mini-player (60 dp pill), Equaliser and Settings are rebuilt on the One UI kit. There is a
>   real queue sheet (`QueueSheet`/`QueueViewModel`), backed by a `QueueState` that `PlaybackService` publishes
>   (`QueuePublisher`). Tapping a track plays the whole list from it, via the `PlaybackController` helpers `playFrom`,
>   `playAllInOrder` and `shuffleAll`. Now Playing's Volume opens the system panel (`SystemVolumePanel`), and ⋮ offers
>   Edit tags, Go to album, Go to artist and Settings. UI copy says "Equaliser"; code names stay `Equalizer*`. Reduced
>   motion uses `rememberReducedMotion()`; no module-local `isReducedMotion` copy is left (`:feature:playlists`
>   lost the last one in Phase 6a). Settings values sit in blue below their labels,
>   with no chevrons.
> - **Phase 4a:** Home has five tabs: Favourites · Playlists · Tracks · Albums · Artists. Favourites (`FavouritesTab`,
>   `:feature:playlists`) plays the favourite tracks, with no multi-select. Albums is a 2-column `AlbumCard` grid and
>   Artists is an `ArtistRow` list with round cover avatars (`Artist.artworkUri`). Both open AlbumDetail/ArtistDetail,
>   which are now also reached from Home, not only from Search. The per-track options sheet lives in
>   `:core:ui` (`SongOptionsHost`). `AlbumArtworkCache.peek` gives Coil a fast path that does
>   not suspend, and cache files are published by an atomic rename. The Robolectric pixel-test helpers live in
>   `:core:testing` (`PixelCapture.kt`). `:core:testing` is test-only and must stay `testImplementation`.
> - **Phase 4b:** Home has six tabs; Folders (`FoldersTab`) lists every folder that directly holds songs, by display name,
>   and opens `FolderDetail(folderPath)`. That is a One UI pushed screen that pads its own list for the pill, not `PushedScreen`. The
>   database gained **Room v4**: `songs.folder_path` (NULL = not scanned yet, `''` = the volume root), `MIGRATION_3_4`, and one
>   `ALL_MIGRATIONS` list that `DatabaseModule` installs and `MigrationTest` replays. Folder queries live in `FolderDao`
>   (`SongDao` is at detekt's interface limit); `FolderRepository` maps them to `Folder` (`:core:model`, with `folderName`
>   and `folderDisplayPath`). The scanner reads RELATIVE_PATH (API 29+) or DATA (24–28) through `FolderPaths`, and
>   `LibraryIndexer` re-upserts a row whenever its stored folder differs from the scanned one, which backfills an upgraded
>   install on its first sync. Rows share `ListRowScaffold` (`AlbumRow`, `ArtistRow`, `FolderRow`).
> - **Phase 5:** Tracks, Favourites, Playlists (user rows), Artists and Folders have the One UI **A–Z rail**.
>   - **The rail:** tap or drag jumps the list, with a haptic tick; TalkBack sees one node, "Fast scroll, letter index",
>     with a "Jump to X" action per populated letter.
>   - **Components:** `FastScrollRail` and `FastScrollContainer` live in `:core:designsystem`, the pure index logic in
>     `FastScrollIndex.kt`. The container shows the rail only while the list can scroll (hidden when it fits); on Playlists the rail starts below
>     the feature cards.
>   - **Library order:** every Home list is in one library order (spec §9.0): `#` first, then A–Z, ignoring case and
>     accents, so "Émile" sorts and jumps under E. `:core:common`'s `LibraryOrder.kt` (`indexLetter`,
>     `sortedByLibraryName`) defines it, and the repositories apply it on `@CoreDataDefaultDispatcher`, off the main
>     thread. The DAOs keep NOCASE as a pre-order.
> - **Phase 6a:** the detail screens, sheets, dialogs and menus are on the One UI kit.
>   - **Kit:** `OneUiBottomSheet`, `OneUiDialog`, `OneUiTextButton`, `OneUiTextField`, `OneUiDropdownMenu`/`OneUiMenuItem`
>     and `SheetActionRow` (`:core:designsystem`) back every sheet, dialog and menu (queue, options, add-to-playlist,
>     theme, save preset, Home ⋮, Now Playing ⋮). Destructive text uses the `destructiveText` token, never `error`.
>   - **Detail screens:** AlbumDetail, ArtistDetail, PlaylistDetail and SmartPlaylist have a `DetailArtworkHeader` hero
>     (album/artist), a static sort label, and a ⋮ on every row that opens `SongOptionsHost` (Add to playlist, Edit tags,
>     and Remove on user playlists). Like FolderDetail, they pad their own lists for the pill instead of being wrapped
>     in `PushedScreen`; `:app` registers them through `NavGraphBuilder` extensions in `MainActivity.kt`.
>     `PushedScreen` still wraps Search, the tag editors, Equaliser, Settings, About, Licenses and Privacy (6b).
>   - **Options sheet:** every caller passes `currentSongs`, so the sheet closes when its track leaves the list.
>     "Create a playlist with this track" is one `PlaylistRepository.createPlaylistWithSong` extension.
> - **Queue restore (side phase, spec `docs/superpowers/specs/2026-10-12-queue-restore-design.md`):** the database is
>   **Room v5** (`MIGRATION_4_5`): `playback_queue` (slot, song_id with no foreign key, shuffle_rank) and a single-row
>   `playback_state`, read through `PlaybackQueueDao.loadSnapshot()` and mapped by `PlaybackStateRepository` to
>   `SavedPlayback`. In `:core:media`, `QueuePersister` saves the queue on playlist changes and the state on pause, seek,
>   mode changes and every 10 s while playing, plus `saveFinal()` in `PlaybackService.onDestroy`. `RestoreCoordinator`
>   restores the queue paused when the service starts, and `RestoredShuffleOrder` (registered after
>   `StartFirstShuffleEnforcer`) puts the exact shuffle order back. `PlaybackSessionCallback.onPlaybackResumption` plus
>   Media3's `MediaButtonReceiver` resume from a headset or Bluetooth Play. **Panel resume** (spec
>   `docs/superpowers/specs/2026-10-15-panel-resume-design.md`): `PlaybackService` is a `MediaLibraryService`, so that a
>   stock-Android System UI media card (AOSP `MediaResumeListener`) can survive process death; this is designed for,
>   not yet verified on, a stock device. Its metadata request (`isForPlayback = false`) reads through
>   `RestoreCoordinator.peekSaved()`, changes nothing and returns `asPlayableLeaf()` items, and its Play resumes like a
>   headset Play. On Samsung One UI (SM-S901B, Android 14) it is not delivered: One UI never adds new apps to its media
>   resume list, so there the card goes when the process dies.
>   `MediaButtonStartFallback` keeps a media-button start with nothing to resume, or a key UP that Media3 ignores, from
>   crashing the foreground-service contract. It also stops a service that a non-play key started cold (the paused
>   notification's delete intent sends STOP when the process dies), so the next app open restores prepared (P8).
>
> The tab bar, Folders browsing and the A–Z rail listed under "Post-1.0 cuts" below are being built by this redesign
> at the user's explicit request, so that section and the `:feature:library` blurb above are out of date until the
> Phase 7 docs pass.

**Status**: **v1.0 feature-complete.** All planned phases (1–7) plus the Phase 8 polish/release pass
are shipped: Core Playback, Library (Songs list + Album/Artist detail), Playlists, Equalizer, Tag
Editor, Search, Settings, Home Widget, plus an accessibility pass, a baseline profile, and a signed,
R8-minified release build (`versionCode 1` / `versionName "1.0.0"`) regression-tested in airplane mode.
Final code-review gate passed 2026-07-23 with no blocking findings (see "Review follow-ups" below).
**Last Updated**: 2026-07-23

### Post-1.0 cuts (deliberate, not defects)

Three things described elsewhere in this repo's specs were consciously scoped out of v1.0. If a
future prompt flags them as "missing," they're deferred, not broken — don't rebuild them without an
explicit ask:

- **Custom tag system** — deferred during Phase 5, formally cut here. The shipped tag editor covers
  the full standard field set (title/artist/album/genre/year/track/album artist/composer + artwork)
  for MP3/FLAC/M4A; a user-defined custom-tag layer on top of that is the only piece not built.
- **A–Z fast-scroll index** (`ui-style-guide.md` §6) — spec'd, never built. Confirmed unbuilt by the
  Phase 8 UI audit (2026-07-15: code-reviewer + m3-design-system-agent + compose-performance-auditor).
- **Center-weighted scrollable tab bar** (Favourites · Playlists · Songs · Albums · Artists · Folders,
  §6/§7) and **Folders browsing** — also spec'd, also never built. `:feature:library`'s `LibraryScreen`
  is a flat Songs list with a plain `TopAppBar`; Album/Artist surfaces are reached only through
  Search's `onAlbumClick`/`onArtistClick` and the two detail screens (not a library tab bar).

All three were net-new-feature scope, not polish, so the Phase 8 pass deliberately left them for a
post-1.0 ask rather than building them under a "polish" mandate.

### What's shipped (on `main`)

| Area | Commits | Notes |
|---|---|---|
| Project scaffold, multi-module build | foundation | Convention plugins, version catalog, detekt + Spotless pre-commit hook |
| `:core:model` — domain models | foundation | `Song`, `Album`, `Artist`, `Playlist`, `EqualizerPreset`, `RepeatMode`, `ShuffleMode` |
| `:core:database` — Room | foundation | Entities, DAOs, `MusicDatabase` |
| `:core:datastore` — DataStore | foundation | Settings + equalizer active-state |
| `:core:common` — utilities | foundation; redesign Phase 5 | Empty at v1.0. The redesign's Phase 5 adds the library order (`LibraryOrder.kt`). Dispatcher qualifiers stay per module |
| `:core:designsystem` — theme | foundation | Color, Typography, Shape tokens + semantic extensions (`songTitle`, `miniPlayerContainer`, `sliderInactiveTrack`, etc.) per ui-style-guide |
| `:core:permissions` — permission gate | foundation | Version-aware READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE, rationale UI, Settings routing |
| `:core:data` — repositories + scanner | foundation | MediaStore scanner, Song/Album/Artist repositories |
| `:core:media` — ExoPlayer service | `c0c507d`+ | `PlaybackService` (MediaLibraryService), `PlaybackController`, `PlaybackStateHolder`, position ticker |
| `:feature:library` — library screens | foundation | Songs/Albums/Artists/Genres lists |
| `:feature:player` — Now Playing + Mini-player | `286867f`, `066306b` | Full Now Playing screen, persistent mini-player pill, shared-element artwork transition (spring physics, reduced-motion aware), `PlayerViewModel`, `MiniPlayerViewModel` |
| Navigation | `c0c507d` | Type-safe `@Serializable` routes, `SharedTransitionLayout` wiring |
| Audit fixes | `066306b` | artworkUri pipeline fixed, `ProcessLifecycleOwner` release, recomposition scope (position ticks scoped to seekbar only), stable lambdas, `PlayPauseButton` accessibility, `hasSong` StateFlow |
| `:feature:playlists` — playlists | `c532e58`…`55c4b01` | Smart playlists (Recently played / Favourites / Most played), user playlists, playlist detail with key-based drag-and-drop reorder (reduced-motion aware), add-to-playlist sheet, play-count tracking + favourite toggle, transactional DAO position integrity (append/remove/compact), Room v2 migration |
| Equalizer — `:core:media` engine + `:feature:equalizer` UI | `524f6e1`…`58b22e9` | `EqualizerController` (audiofx wrapper: nearest-band mapping for the 9 target frequencies, bandLevelRange clamping, thread-safe + exception-contained), service lifecycle binding (attach/re-apply persisted state on session-id change, guaranteed release on destroy), active state in DataStore + named presets in Room (DB v3, tested migration), 9 vertical spring sliders + preset chip grid + gains-driven response curve (real `Visualizer` rejected — requires RECORD_AUDIO, conflicts with privacy positioning), `Equalizer` route wired from Now Playing |
| `:feature:tageditor` — tag editor | `546154d`…`98868cc` | Offline jaudiotagger fork (`com.github.Adonai`, local IO only) + `SongTags` model, `TagReader`/`TagWriter` (MP3/FLAC/M4A contract-tested against real fixture files), scoped-storage write consent per SDK (`createWriteRequest` 30+ / `RecoverableSecurityException` recovery 29 / legacy WRITE_EXTERNAL_STORAGE maxSdk=28 requested at point of use), single-song editor + batch editing (per-field apply toggles, per-song outcomes, partial-failure summaries), copy-through-cache write via `SongFileResolver`, post-save `TagSaveFinalizer` (MediaStore re-scan → targeted Room re-sync preserving user state → now-playing metadata refresh via `replaceMediaItem`), local-image artwork embedding (PhotoPicker, `isAndroid` flag for FLAC on-device decode), cancel/back guarded mid-save, long-press entry points in library |
| `:feature:search` — library search | `8c22705`…`adc4577` | LIKE-based substring search over Room (deliberate FTS4 rejection — documented in `SearchDao`; wildcard-escaped, prefix-first relevance), grouped results Songs/Albums/Artists (albums/artists derived via `GROUP BY` over `songs` — no new tables; synthetic FNV-1a artist id, display-only since redesign Phase 4a; lists key artists by name), `SearchRepository` + `SearchResults` model, debounced (300ms) cancellable `SearchViewModel` (`flatMapLatest`, instant-Idle on blank, SavedStateHandle-seeded), SearchScreen (auto-focus field, IME-safe single-owner insets, sectioned results), shared `AlbumRow`/`ArtistRow` in `:core:ui`, `Search(query: String? = null)` route + library/playlists top-bar entries + `rollamusic://search` deep link, recent searches in DataStore (capped MRU 10, escaped codec, best-effort writes) |
| `:feature:settings` + settings vertical | `1b1a4a9`…`36b5366` | SettingsPreferences in the shared "settings" DataStore (theme mode enum codec, dynamic color, playback speed, skip silence — crossfade/gapless-toggle/pureBlack REJECTED as decorative, recorded in PHASE6-SETTINGS.md), SettingsRepository with two-sided speed clamping, PlaybackSettingsBinder (service-lifecycle binding: speed via Player API, skipSilence via ExoPlayer-only API w/ UnstableApi opt-in, persisted values re-applied on service start), settings-driven theme (MainViewModel → RollaMusicPlayerTheme; resolved-theme edge-to-edge bar contrast; LocalRollaDarkTheme fixing a forced-theme WCAG bug in miniPlayerContainer/sliderInactiveTrack), grouped-cards SettingsScreen per §6 (theme picker dialog, API-31-gated dynamic color row, optimistic write-on-release speed slider), Privacy/About/Licenses screens (all local: PackageInfo version, hand-curated OSS list), manual rescan with progress + result snackbar (LibraryIndexer now serializes concurrent syncs via internal Mutex — fixed a TOCTOU race), Settings/About/Licenses/Privacy routes + overflow (⋮) entries in Library and Playlists |
| `:feature:widget` — Glance home widget | `9fa3a73`…`4e91047` | §8 dark rounded card (GlanceTheme ColorProviders mapped from the §2 palette — deliberately day==night; Glance can't read the app's Compose tokens), 56dp artwork w/ single-entry memoized 256px two-pass downscale (review-gate HIGH: was re-decoding per 1s tick), title/artist, primary progress bar, previous · −15s · play/pause · +15s · next controls (all content-described) + body-tap → app, five stateless ActionCallbacks via WidgetEntryPoint driving the ONE PlaybackController (no second player; ±15s = seekTo clamped, pure-function tested), cold-tap-safe (controller commands queue on the connect future), push-updated via the PlaybackUpdateHook multibinding seam (:core:media dispatcher fires after state-holder writes on track change/play-pause/1s tick-while-playing; widget side coalesces via replay-1 DROP_OLDEST SharedFlow), updatePeriodMillis=0 (never polls), receiver exported=true (review-gate CRITICAL: false leaves the widget un-addable — platform-protected broadcasts, no attack surface), picker preview drawable |
| Unit tests | `066306b`+ | Turbine/MockK suites across player (44), playlists (ViewModel/DAO/repository + migration), equalizer (controller 19, repository 12, DataStore codec 11, ViewModel 15, session manager 7, plus androidTest DAO/migration/converter), tag editor (ViewModel 25, batch ViewModel 20, finalizer 6, 8×3 format contract tests), search (ViewModel 21 incl. flatMapLatest-cancellation proof, repository 8, recents codec 17, androidTest DAO 11), settings (ViewModel 20 incl. gated rescan state machine, repository 14, prefs codec, PlaybackSettingsBinder 8, MainViewModel 6, LibraryIndexer 10 incl. mutex-serialization proofs), and widget (state 5, provider 4, sample-size 8, loader 3, seek-clamp 10, update-hook coalescing 3, plus core-media dispatcher 5 + controller queuing 7) |
| **Phase 8 — Polish & Release** | | |
| `:feature:library` — Album/Artist detail | `23b6fb1`, `b31abda` | `AlbumDetail(albumId: Long)` and `ArtistDetail(artistName: String)` routes — artist is routed by name (the real `ArtistDao` `GROUP BY` key), deliberately *not* Search's synthetic FNV-1a hash id, which is display-only (lists key artists by name since redesign Phase 4a); artwork header + title/artist/counts + Shuffle/Play + song list w/ track numbers (Album), horizontal `AlbumCard` strip + all songs (Artist); wired from both `LibraryScreen` and Search's previously-dormant `onAlbumClick`/`onArtistClick` |
| `:core:data` — album artwork disk cache | `7a61bd3` | `AlbumArtworkCache`/`AlbumArtworkCacheImpl`, albumId-keyed and downscaled into app-specific storage, bounded (32MiB, LRU by write-timestamp), mutex-serialized writes off the main thread, served through a Coil `ImageLoaderFactory` interceptor; invalidated on manual rescan and on tag-editor cover-embed (hooked into `TagSaveFinalizer` so an edited cover shows everywhere immediately); missing/undecodable art falls back to the existing placeholder; shared by the widget's artwork loader |
| UI style-guide audit | `9fbde91` | Insets (NowPlaying nav-bar clearance, removed a double status-bar inset), empty states added (AlbumDetail/ArtistDetail songs, Playlists user list), 48dp control touch targets, `AddToPlaylistSheet` divider, recomposition scoping (Library multi-select and equalizer drag via `derivedStateOf` + `@Immutable`) — audit confirmed the fast-scroll/tab-bar/Folders gaps above are conscious cuts, not bugs |
| Accessibility pass | `89ae1da` | Full TalkBack walk of every screen; meaningful contentDescriptions/stateDescriptions, `mergeDescendants` on rows; custom accessibility actions ("Move up"/"Move down") added to `PlaylistDetailScreen` as an accessible equivalent to the drag-based reorder; ≥48dp touch targets everywhere (EQ sliders, overflow, widget controls); WCAG AA contrast re-verified in both themes; fontScale 1.5 survives without clipping |
| Test debt closure | `4d5f2eb` | `MediaWriteRequester` SDK-branch tests (`createWriteRequest` 30+ / `RecoverableSecurityException` 29 / legacy ≤28) via Robolectric/mockkStatic; Compose UI smoke suite covering library → tap song → mini-player → expand now-playing, search query → tap result, and add-to-playlist |
| Baseline profile | `e0b2636` | `:baselineprofile` Macrobenchmark module: cold-start and library-scroll journeys generated on a frames-capable managed device; profile committed and packaged into the release build |
| Release build | `1f86d53`, `074e994` | R8 `minifyEnabled` + `shrinkResources` with verified keep rules (kotlinx-serialization `@Serializable` routes, jaudiotagger reflection, Media3, Glance receivers, Room, Hilt); local keystore signing read only from `local.properties`/env, never committed — the release build type ships **unsigned with a warning** if signing config is absent rather than silently falling back to debug signing; `versionCode 1` / `versionName "1.0.0"`; a `checkReleaseManifestNoInternet` Gradle task reads the real merged manifest and fails `./gradlew check` if `INTERNET` (or similar) ever sneaks in; regression-tested on the release build in airplane mode (play, EQ, tag edit + scoped-storage consent, search, playlists, widget, deep links, process-death restore) |

### Review follow-ups (non-blocking, from the Phase 8 final gate — 2026-07-23)

code-reviewer's final pass before the v1.0 merge found no CRITICAL/HIGH issues. Three narrow,
self-healing items were logged as backlog rather than fixed pre-merge:

1. `TagSaveFinalizer.onSongsSaved` puts `albumArtworkCache.invalidate(it)` in the same try block as
   `refreshPlayback(songs)` — if invalidation throws, the metadata refresh is silently skipped too.
   `SettingsViewModel.clearArtworkCacheBestEffort` already isolates the same cache's `clear()` call in
   its own try/catch for exactly this reason; `TagSaveFinalizer` should match that pattern.
2. **Closed in redesign Phase 4a.** `AlbumArtworkCacheImpl.get`'s hit-path `file.exists()` check ran outside the write
   mutex, so a reader could see a partially written file. The cache now writes a temp file and renames it into place, so
   a visible file is always complete.
3. `AlbumArtworkCache.invalidations: SharedFlow<Long>` has no production subscriber (only exercised in
   tests) — either wire a real consumer or trim it.

### What remains (post-1.0 backlog)

- The custom tag system cut above — revisit only on explicit ask (the A–Z fast-scroll, tab bar and Folders are being built by the One UI redesign, Phases 2–5)
- The two open review follow-ups above (#2 was closed in redesign Phase 4a)
- Optional consolidation: move the per-module dispatcher qualifiers into `:core:common` (it now holds the library order, since redesign Phase 5)

## Next Steps (post-1.0)

1. Triage the review follow-ups above (all low/medium severity, non-blocking)
2. Decide whether `:core:common` should also take the shared dispatcher qualifiers (it is no longer a placeholder: it holds the library order)
3. If there's appetite for further scope: the custom tag system is a new feature, not a fix, so treat it as a fresh ask (the A–Z fast-scroll index and the tab bar/Folders browsing are built by the One UI redesign)

## Notes

- Target Android API: 24 (Android 7.0) minimum, 34 (Android 14) target, compile 36
- Use Material Design 3 components throughout
- Follow Kotlin coding conventions
- Write unit tests for business logic
- Use Coil for image loading
- Implement proper error handling and user feedback