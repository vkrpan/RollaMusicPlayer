# RollaMusicPlayer - Project Memory

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
- **Playback State**: Persisted locally for session restoration

### Technical Implications

- **No Retrofit/OkHttp**: No HTTP client libraries in dependencies
- **No Firebase**: No analytics, crash reporting, or cloud services
- **No Third-Party SDKs**: Avoid SDKs that collect data or require internet
- **Local Caching Only**: All caching strategies use device storage
- **Offline-First UI**: No loading states for network requests, no sync indicators

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel), multi-module (hybrid feature + core)
- **Build**: Gradle Kotlin DSL with version catalog + convention plugins (build-logic)
- **Visual style**: One UI–inspired dark (OLED black, blue accent, soft rounded surfaces). Binding spec: `.claude/rules/ui-style-guide.md`; tokens implemented in `:core:designsystem` (Color.kt / Type.kt / Shape.kt / Theme.kt)
- **Audio Playback**: Media3 ExoPlayer
- **Database**: Room for local data persistence
- **Dependency Injection**: Hilt
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
- Persistent playback state across app restarts

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
├── :core:common        # EMPTY placeholder (planned: shared dispatchers/Result types — today each feature declares its own dispatcher qualifier, e.g. TagEditorIoDispatcher/WidgetIoDispatcher)
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
| `:core:common` — utilities | foundation | Dispatchers, Result types, extensions |
| `:core:designsystem` — theme | foundation | Color, Typography, Shape tokens + semantic extensions (`songTitle`, `miniPlayerContainer`, `sliderInactiveTrack`, etc.) per ui-style-guide |
| `:core:permissions` — permission gate | foundation | Version-aware READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE, rationale UI, Settings routing |
| `:core:data` — repositories + scanner | foundation | MediaStore scanner, Song/Album/Artist repositories |
| `:core:media` — ExoPlayer service | `c0c507d`+ | `PlaybackService` (MediaSessionService), `PlaybackController`, `PlaybackStateHolder`, position ticker |
| `:feature:library` — library screens | foundation | Songs/Albums/Artists/Genres lists |
| `:feature:player` — Now Playing + Mini-player | `286867f`, `066306b` | Full Now Playing screen, persistent mini-player pill, shared-element artwork transition (spring physics, reduced-motion aware), `PlayerViewModel`, `MiniPlayerViewModel` |
| Navigation | `c0c507d` | Type-safe `@Serializable` routes, `SharedTransitionLayout` wiring |
| Audit fixes | `066306b` | artworkUri pipeline fixed, `ProcessLifecycleOwner` release, recomposition scope (position ticks scoped to seekbar only), stable lambdas, `PlayPauseButton` accessibility, `hasSong` StateFlow |
| `:feature:playlists` — playlists | `c532e58`…`55c4b01` | Smart playlists (Recently played / Favourites / Most played), user playlists, playlist detail with key-based drag-and-drop reorder (reduced-motion aware), add-to-playlist sheet, play-count tracking + favourite toggle, transactional DAO position integrity (append/remove/compact), Room v2 migration |
| Equalizer — `:core:media` engine + `:feature:equalizer` UI | `524f6e1`…`58b22e9` | `EqualizerController` (audiofx wrapper: nearest-band mapping for the 9 target frequencies, bandLevelRange clamping, thread-safe + exception-contained), service lifecycle binding (attach/re-apply persisted state on session-id change, guaranteed release on destroy), active state in DataStore + named presets in Room (DB v3, tested migration), 9 vertical spring sliders + preset chip grid + gains-driven response curve (real `Visualizer` rejected — requires RECORD_AUDIO, conflicts with privacy positioning), `Equalizer` route wired from Now Playing |
| `:feature:tageditor` — tag editor | `546154d`…`98868cc` | Offline jaudiotagger fork (`com.github.Adonai`, local IO only) + `SongTags` model, `TagReader`/`TagWriter` (MP3/FLAC/M4A contract-tested against real fixture files), scoped-storage write consent per SDK (`createWriteRequest` 30+ / `RecoverableSecurityException` recovery 29 / legacy WRITE_EXTERNAL_STORAGE maxSdk=28 requested at point of use), single-song editor + batch editing (per-field apply toggles, per-song outcomes, partial-failure summaries), copy-through-cache write via `SongFileResolver`, post-save `TagSaveFinalizer` (MediaStore re-scan → targeted Room re-sync preserving user state → now-playing metadata refresh via `replaceMediaItem`), local-image artwork embedding (PhotoPicker, `isAndroid` flag for FLAC on-device decode), cancel/back guarded mid-save, long-press entry points in library |
| `:feature:search` — library search | `8c22705`…`adc4577` | LIKE-based substring search over Room (deliberate FTS4 rejection — documented in `SearchDao`; wildcard-escaped, prefix-first relevance), grouped results Songs/Albums/Artists (albums/artists derived via `GROUP BY` over `songs` — no new tables; synthetic FNV-1a artist id documented as list-key-only), `SearchRepository` + `SearchResults` model, debounced (300ms) cancellable `SearchViewModel` (`flatMapLatest`, instant-Idle on blank, SavedStateHandle-seeded), SearchScreen (auto-focus field, IME-safe single-owner insets, sectioned results), shared `AlbumRow`/`ArtistRow` in `:core:ui`, `Search(query: String? = null)` route + library/playlists top-bar entries + `rollamusic://search` deep link, recent searches in DataStore (capped MRU 10, escaped codec, best-effort writes) |
| `:feature:settings` + settings vertical | `1b1a4a9`…`36b5366` | SettingsPreferences in the shared "settings" DataStore (theme mode enum codec, dynamic color, playback speed, skip silence — crossfade/gapless-toggle/pureBlack REJECTED as decorative, recorded in PHASE6-SETTINGS.md), SettingsRepository with two-sided speed clamping, PlaybackSettingsBinder (service-lifecycle binding: speed via Player API, skipSilence via ExoPlayer-only API w/ UnstableApi opt-in, persisted values re-applied on service start), settings-driven theme (MainViewModel → RollaMusicPlayerTheme; resolved-theme edge-to-edge bar contrast; LocalRollaDarkTheme fixing a forced-theme WCAG bug in miniPlayerContainer/sliderInactiveTrack), grouped-cards SettingsScreen per §6 (theme picker dialog, API-31-gated dynamic color row, optimistic write-on-release speed slider), Privacy/About/Licenses screens (all local: PackageInfo version, hand-curated OSS list), manual rescan with progress + result snackbar (LibraryIndexer now serializes concurrent syncs via internal Mutex — fixed a TOCTOU race), Settings/About/Licenses/Privacy routes + overflow (⋮) entries in Library and Playlists |
| `:feature:widget` — Glance home widget | `9fa3a73`…`4e91047` | §8 dark rounded card (GlanceTheme ColorProviders mapped from the §2 palette — deliberately day==night; Glance can't read the app's Compose tokens), 56dp artwork w/ single-entry memoized 256px two-pass downscale (review-gate HIGH: was re-decoding per 1s tick), title/artist, primary progress bar, previous · −15s · play/pause · +15s · next controls (all content-described) + body-tap → app, five stateless ActionCallbacks via WidgetEntryPoint driving the ONE PlaybackController (no second player; ±15s = seekTo clamped, pure-function tested), cold-tap-safe (controller commands queue on the connect future), push-updated via the PlaybackUpdateHook multibinding seam (:core:media dispatcher fires after state-holder writes on track change/play-pause/1s tick-while-playing; widget side coalesces via replay-1 DROP_OLDEST SharedFlow), updatePeriodMillis=0 (never polls), receiver exported=true (review-gate CRITICAL: false leaves the widget un-addable — platform-protected broadcasts, no attack surface), picker preview drawable |
| Unit tests | `066306b`+ | Turbine/MockK suites across player (44), playlists (ViewModel/DAO/repository + migration), equalizer (controller 19, repository 12, DataStore codec 11, ViewModel 15, session manager 7, plus androidTest DAO/migration/converter), tag editor (ViewModel 25, batch ViewModel 20, finalizer 6, 8×3 format contract tests), search (ViewModel 21 incl. flatMapLatest-cancellation proof, repository 8, recents codec 17, androidTest DAO 11), settings (ViewModel 20 incl. gated rescan state machine, repository 14, prefs codec, PlaybackSettingsBinder 8, MainViewModel 6, LibraryIndexer 10 incl. mutex-serialization proofs), and widget (state 5, provider 4, sample-size 8, loader 3, seek-clamp 10, update-hook coalescing 3, plus core-media dispatcher 5 + controller queuing 7) |
| **Phase 8 — Polish & Release** | | |
| `:feature:library` — Album/Artist detail | `23b6fb1`, `b31abda` | `AlbumDetail(albumId: Long)` and `ArtistDetail(artistName: String)` routes — artist is routed by name (the real `ArtistDao` `GROUP BY` key), deliberately *not* Search's synthetic FNV-1a hash id, which stays documented as list-key-only; artwork header + title/artist/counts + Shuffle/Play + song list w/ track numbers (Album), horizontal `AlbumCard` strip + all songs (Artist); wired from both `LibraryScreen` and Search's previously-dormant `onAlbumClick`/`onArtistClick` |
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
2. `AlbumArtworkCacheImpl.get`'s hit-path `file.exists()` check runs outside the write mutex, so two
   concurrent first-time requests for the same `albumId` could race and observe a partially-written
   file (self-heals as a placeholder flash, not a crash). Fix: write to a temp file and rename into
   place atomically.
3. `AlbumArtworkCache.invalidations: SharedFlow<Long>` has no production subscriber (only exercised in
   tests) — either wire a real consumer or trim it.

### What remains (post-1.0 backlog)

- The three deliberate cuts above (custom tag system, A–Z fast-scroll, tab bar/Folders) — revisit only on explicit ask
- The three review follow-ups above
- Optional consolidation: build `:core:common` for real (shared dispatcher qualifiers replacing the per-feature ones) or drop the module — still an empty `Placeholder.kt`

## Next Steps (post-1.0)

1. Triage the review follow-ups above (all low/medium severity, non-blocking)
2. Decide `:core:common`'s fate — consolidate shared dispatchers/Result types for real, or delete the placeholder module
3. If there's appetite for further scope: custom tag system, A–Z fast-scroll index, or the center-weighted tab bar/Folders browsing — each is a new feature, not a fix, so treat as a fresh ask

## Notes

- Target Android API: 24 (Android 7.0) minimum, 34 (Android 14) target
- Use Material Design 3 components throughout
- Follow Kotlin coding conventions
- Write unit tests for business logic
- Use Coil for image loading
- Implement proper error handling and user feedback