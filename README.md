# RollaMusicPlayer

**RollaMusicPlayer** is a feature-rich, fully offline Android music player, built with Kotlin and
Jetpack Compose. It plays local audio files, manages playlists, includes an 8-band equalizer and a
full ID3 tag editor, and ships a home-screen widget — with zero network access, by design.

**Status: v1.0.0 — feature-complete.** See [`CLAUDE.md`](CLAUDE.md) for full shipped-feature history,
architecture decisions, and the current backlog.

## 🔒 Offline & Privacy First

**RollaMusicPlayer operates entirely on your device. There is no server, no account, and no network
permission.**

- ✅ **100% Offline Operation** — no internet connection required, at any point, for any feature
- 🔒 **Complete Privacy** — zero data collection, no analytics, no crash reporting to external services
- 💾 **Local Storage Only** — music files, playlists, equalizer presets, and settings all stay on-device (Room database + DataStore)
- 🚫 **No Cloud Features** — no sync, no cloud backup, no online accounts
- 📱 **Minimal Permissions** — local audio read only (`READ_MEDIA_AUDIO` on Android 13+, `READ_EXTERNAL_STORAGE` on older versions); no `INTERNET` permission is requested, and a release-build Gradle check (`checkReleaseManifestNoInternet`) fails the build if one ever sneaks in transitively
- 🎵 **Full Control** — your music library, your device, nobody else's servers

This isn't a settings toggle — there is no networking library in the dependency graph (no
Retrofit/OkHttp, no Firebase, no analytics SDK), so there's nothing to turn off.

## Features

### Core
- Local playback: MP3, FLAC, WAV, OGG, M4A, AAC
- Playlist management (create, edit, delete, reorder) plus smart playlists — Recently played, Favourites, Most played
- Shuffle and repeat (off / one / all); background playback with notification controls; playback state restored across app restarts

### Library
- Automatic scanning and indexing of local audio via MediaStore into a local Room database
- Songs list, plus **Album detail** and **Artist detail** screens (reached from Search or from a song's album/artist)
- Search across songs, albums, and artists (debounced, grouped results)
- Local album-artwork extraction with a bounded on-disk cache

### Advanced Audio
- 8-band graphic equalizer (40Hz, 80Hz, 160Hz, 315Hz, 630Hz, 1.25kHz, 2.5kHz, 5kHz, 10kHz) with save/load custom presets
- Gains-driven equalizer response curve while adjusting — visual only; there's no live audio capture (a real `Visualizer` would need `RECORD_AUDIO`, which conflicts with the privacy-first design, so it was deliberately rejected)

### Tag Editor
- Full ID3 / Vorbis / MP4 tag editing: title, artist, album, genre, year, track number, album artist, composer, and embedded artwork
- Single-song and batch editing, with scoped-storage write consent handled per Android version (`createWriteRequest` on 30+, `RecoverableSecurityException` recovery on 29, legacy `WRITE_EXTERNAL_STORAGE` below that)

### Home Screen Widget
- Album artwork, progress bar, previous / −15s / play-pause / +15s / next controls, tap to open the app — all driven by local playback state, no polling

### Accessibility
- TalkBack support across every screen, ≥48dp touch targets, WCAG AA contrast in both light and dark themes, and correct layout at 1.5x font scale

### Not in v1.0 (deliberate cuts, not defects)
A few things described in the design docs were consciously scoped out — see `CLAUDE.md`'s "Post-1.0
cuts" for the reasoning:
- A user-defined **custom tag system** on top of the standard ID3 fields above
- An **A–Z fast-scroll index** on list screens
- A **Songs/Albums/Artists/Folders tab bar** and **Folders browsing** (album/artist browsing works today via Search and the detail screens, just not via a library tab bar)

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material Design 3 (One UI–inspired dark theme — see [`ui-style-guide.md`](.claude/rules/ui-style-guide.md))
- **Architecture**: MVVM, multi-module (hybrid feature + core, Now in Android–style)
- **Audio**: Media3 ExoPlayer, `MediaSessionService`
- **Database**: Room (local SQLite)
- **Settings/state**: Jetpack DataStore
- **DI**: Hilt
- **Async**: Coroutines & Flow
- **Widget**: Jetpack Glance
- **Images**: Coil, with a local album-artwork disk cache
- **Testing**: JUnit, MockK, Turbine, Robolectric, Compose UI tests, Macrobenchmark/Baseline Profile
- **Build**: Gradle Kotlin DSL, a version catalog, and convention plugins (`build-logic/`)

No networking libraries anywhere in the dependency graph — fully offline architecture, enforced at
the build level (see `build-tooling-agent` and the static-analysis "no network dependency" check).

## Building the App

**Requirements**: JDK 17, Android Studio (or the command line), Android SDK with API 34 installed. Min SDK 24 (Android 7.0); target/compile SDK 34.

```bash
git clone https://github.com/vkrpan/RollaMusicPlayer.git
cd RollaMusicPlayer

# Debug build — installable as-is
./gradlew assembleDebug

# Run the full check suite: unit tests, detekt, ktlint (Spotless), the no-network gate
./gradlew check
```

### Release build (signed, R8-minified)

The release build type (`versionCode 1` / `versionName "1.0.0"`) is minified with
`shrinkResources`, with keep rules verified for kotlinx-serialization's `@Serializable` routes,
jaudiotagger's reflection-heavy tag I/O, Media3, Glance receivers, Room, and Hilt.

Signing reads from `local.properties` (never committed — see `.gitignore`) or matching environment
variables (for CI signing without a checked-in properties file):

```properties
RELEASE_STORE_FILE=keystore/rolla-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

```bash
./gradlew assembleRelease
```

If no signing config is present, the release build type still builds — **unsigned, with a Gradle
warning** — rather than silently falling back to debug signing (an obviously-unsigned APK is safer
than one that looks legitimate but isn't release-signed).

A `checkReleaseManifestNoInternet` task reads the real merged manifest of the release build and
fails if an `INTERNET` permission (or similar) is ever pulled in transitively; it's wired into
`./gradlew check`.

## Project Structure

Multi-module, hybrid feature + core (Now in Android style). The app shell wires everything;
`:core:*` modules hold shared concerns; `:feature:*` modules hold one screen-area each. Full graph,
convention plugins, and dependency rules: [`setup-modularization`](.claude/skills/setup-modularization/) skill.

```
:app                    # Application, MainActivity, NavHost wiring, DI root, app theme entry

:core
├── :core:model         # Domain models + enums (Song, Album, Artist, Playlist, ...). Pure Kotlin.
├── :core:common        # Empty placeholder — not yet consolidated (each feature declares its own dispatcher qualifier today)
├── :core:database      # Room: entities, DAOs, migrations, MusicDatabase
├── :core:datastore     # DataStore: app settings, equalizer active-state, recent searches
├── :core:data          # Repositories, media scanner, album artwork cache
├── :core:media         # Media3 playback service, MediaSession, audio session, equalizer effect
├── :core:designsystem  # Theme (Color/Type/Shape) + model-agnostic components
├── :core:ui            # Model-aware shared composables (SongListItem, AlbumCard, AlbumArtwork)
├── :core:permissions   # Runtime media permission gate + flow
└── :core:testing       # Fakes, fixtures, test rules

:feature
├── :feature:library    # Songs list, Album detail, Artist detail
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
core never depends on a feature; no cycles.

## Development Tooling (Claude Code)

This repo was built with [Claude Code](https://claude.com/claude-code), and the tooling that shaped
it lives on in `.claude/` — specialized agents, step-by-step skills, and binding coding-convention
rules. They're kept up to date because the codebase still uses them for ongoing work.

```
.claude/
├── settings-guide.md      # settings.json structure (agents, skills, rules, permissions)
├── agents/                # Specialized agents, several with exclusive ownership of a module
├── skills/                 # Multi-step workflows (add a screen, wire DI, generate a baseline profile, ...)
├── rules/                  # Binding coding conventions (Kotlin style, Compose conventions, Media3, navigation, model vocabulary, UI style guide)
└── commands/                # Quick references (e.g. run-emulator)
```

### Agents

Several agents are **exclusive owners** of a package or directory, to prevent concurrent-edit
conflicts on the same code:

| Area | Agent(s) |
|---|---|
| Audio/playback | `audio-engineer` (ExoPlayer, MediaSession, foreground service) · `equalizer-agent` (owns the equalizer effect + presets) |
| Data & library | `data-layer-agent` (exclusive owner of all Room components) · `media-scanning-agent` (MediaStore → Room) · `tag-editor-agent` (owns `tageditor/`) |
| UI & design | `ui-builder` · `m3-design-system-agent` (exclusive owner of the theme tokens) · `compose-animation-agent` · `compose-performance-auditor` (read-only) |
| Architecture & navigation | `viewmodel-architect` · `navigation-agent` (exclusive owner of the nav graph and typed routes) |
| Platform | `permissions-agent` (owns `permission/`) · `widget-agent` (exclusive owner of `widget/`) |
| Build & quality | `build-tooling-agent` (exclusive owner of Gradle/version catalog/R8/CI) · `code-reviewer` (read-only) · `test-writer` |

### Skills

Step-by-step workflows in `.claude/skills/` — architecture/setup (`add-new-screen`,
`add-room-database`, `setup-modularization`, `bootstrap-project`, ...), feature implementation
(`handle-runtime-permissions`, `implement-media-scanning`, `implement-equalizer`,
`implement-tag-editor`, `implement-home-widget`, ...), performance/UI polish
(`generate-baseline-profile`, `implement-image-loading-coil`,
`implement-edge-to-edge-and-insets`, ...), and build/testing/ops (`setup-static-analysis`,
`add-unit-testing`, `add-ui-testing-compose`, `debug-playback-issue`, `release-build`).

### Rules

Binding conventions in `.claude/rules/`:
- [`kotlin-style`](.claude/rules/kotlin-style.md) — naming, formatting, language features, offline checklist
- [`compose-conventions`](.claude/rules/compose-conventions.md) — composable structure, state, Material 3, accessibility
- [`media3-playback`](.claude/rules/media3-playback.md) — ExoPlayer, MediaSession, notifications, background playback
- [`navigation-conventions`](.claude/rules/navigation-conventions.md) — type-safe `@Serializable` routes, argument and back-stack rules
- [`model-vocabulary`](.claude/rules/model-vocabulary.md) — canonical domain model names (`Song`, never `Track`)
- [`ui-style-guide`](.claude/rules/ui-style-guide.md) — the visual contract: colors, typography, shapes, per-screen layout, widget style

## Contributing

1. Read `CLAUDE.md` for project context and current status.
2. Pick the agent that owns the area you're touching (see above) and respect exclusive-ownership boundaries.
3. Follow the relevant skill for multi-step work; apply the rules throughout.
4. Write tests; run `./gradlew check` before proposing a change.
5. Get a `code-reviewer` pass before merging — MVVM compliance, offline/privacy, memory leaks, performance.

## Resources

- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Media3 Documentation](https://developer.android.com/guide/topics/media/media3)
- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)

## License

[To be determined]

## Contact

[To be determined]
