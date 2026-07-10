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
- Real-time audio visualization during adjustment

### Music Library Management
- Automatic music library scanning and indexing
- Metadata extraction and display
- Advanced search and filtering (artists, albums, genres, tags)
- Album artwork extraction and caching
- Custom tag system for organization
- Full ID3 tag editing (title, artist, album, genre, year, track number, album artist, composer, custom tags)
- Batch tag editing for multiple files

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
├── :core:common        # Dispatchers, Result types, extensions, base utilities
├── :core:database      # Room: entities, DAOs, migrations, MusicDatabase
├── :core:datastore     # DataStore: app settings (theme/dynamic color/speed/skip-silence), equalizer active-state, recent searches
├── :core:data          # Repositories + media scanner
├── :core:media         # Media3 playback service, MediaSession, audio session, equalizer effect
├── :core:designsystem  # Theme (Color/Type/Shape) + model-agnostic components
├── :core:ui            # Model-aware shared composables (SongListItem, AlbumCard, AlbumArtwork)
├── :core:permissions   # Runtime media permission gate + flow
└── :core:testing       # Fakes, fixtures, test rules

:feature
├── :feature:library    # Songs/Albums/Artists/Genres lists
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

**Status**: Core Playback, Playlists, Equalizer, Tag Editor, Search, and Settings shipped (Phases 2, 4, 5 complete — custom tag system deferred; Phase 3 search done, detail screens + artwork caching remain; Phase 6 widget next)
**Last Updated**: 2026-07-10

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
| Unit tests | `066306b`+ | Turbine/MockK suites across player (44), playlists (ViewModel/DAO/repository + migration), equalizer (controller 19, repository 12, DataStore codec 11, ViewModel 15, session manager 7, plus androidTest DAO/migration/converter), tag editor (ViewModel 25, batch ViewModel 20, finalizer 6, 8×3 format contract tests), search (ViewModel 21 incl. flatMapLatest-cancellation proof, repository 8, recents codec 17, androidTest DAO 11), and settings (ViewModel 20 incl. gated rescan state machine, repository 14, prefs codec, PlaybackSettingsBinder 8, MainViewModel 6, LibraryIndexer 10 incl. mutex-serialization proofs) |

### What remains

- **Phase 3 leftover**: album/artist detail screens (search's album/artist taps are wired no-ops awaiting these routes; artist ids there must reconcile with search's synthetic FNV name-hash ids), artwork caching polish
- **Phase 5 leftover**: custom tag system (deferred — not part of the shipped tag editor)
- **Phase 6**: `:feature:widget` (Glance home screen widget), UI/UX polish
- **Phase 7**: Comprehensive testing, baseline profile, release prep (note: `MediaWriteRequester`'s SDK branching has no direct unit tests — needs Robolectric or `mockkStatic`; covered indirectly via ViewModel consent tests)

## Next Steps

1. Build `:feature:widget` (PHASE7-WIDGET.md exists) — Glance home screen widget per ui-style-guide §8 (playback state is ready: tag edits already propagate via `PlaybackStateHolder`)
2. Add album detail and artist detail screens to `:feature:library` — then wire search's dormant `onAlbumClick`/`onArtistClick` (MainActivity `composable<Search>`)
3. Implement album artwork caching in `:core:data`
4. Phase 7 prep: baseline profile, license-list build-time generation option, `MediaWriteRequester` SDK-branch tests (Robolectric)

## Notes

- Target Android API: 24 (Android 7.0) minimum, 34 (Android 14) target
- Use Material Design 3 components throughout
- Follow Kotlin coding conventions
- Write unit tests for business logic
- Use Coil for image loading
- Implement proper error handling and user feedback