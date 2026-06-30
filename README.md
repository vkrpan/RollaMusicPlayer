# RollaMusicPlayer - Development Plan

## Overview

This repository contains a comprehensive development plan for **RollaMusicPlayer**, a feature-rich, fully offline Android music player. The plan is structured to work with Claude Code and includes specialized agents, reusable skills, coding conventions, and quick commands.

## 🔒 Offline & Privacy First

**RollaMusicPlayer is designed as a fully offline, privacy-focused music player that operates entirely on your device.**

### Core Principles

- ✅ **100% Offline Operation** - No internet connection required at any time
- 🔒 **Complete Privacy** - Zero data collection, no analytics, no external communication
- 💾 **Local Storage Only** - All data stored on your device (music files, playlists, settings, equalizer presets)
- 🚫 **No Cloud Features** - No sync, no cloud backup, no online accounts
- 📱 **Minimal Permissions** - Local audio read only (READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE)
- 🎵 **Full Control** - You own and control your music library

### What This Means

- **No Internet Permission**: The app does not request or use internet connectivity
- **Local Data Only**: Music files, playlists, equalizer presets, and settings are stored locally
- **Privacy Guaranteed**: No user data is collected, transmitted, or shared
- **Works Anywhere**: Use the app in airplane mode or with no connectivity
- **Your Music, Your Device**: All music remains on your device under your control

## Project Structure

```
RollaMusicPlayer/
├── CLAUDE.md                              # Project memory (always loaded by Claude)
├── README.md                              # This file
└── .claude/                               # Claude configuration directory
    ├── settings-guide.md                  # Guide for settings.json configuration
    ├── agents/                            # Specialized AI agents
    │   ├── audio-engineer.md              # ExoPlayer, MediaSession, background playback
    │   ├── equalizer-agent.md             # 8-band equalizer effect & presets
    │   ├── data-layer-agent.md            # Room schema, DAOs, migrations (exclusive owner)
    │   ├── media-scanning-agent.md        # MediaStore indexing into Room
    │   ├── tag-editor-agent.md            # ID3/metadata read-write (scoped storage)
    │   ├── ui-builder.md                  # Jetpack Compose screens & components
    │   ├── m3-design-system-agent.md      # Theme tokens, colors, typography (exclusive owner)
    │   ├── compose-animation-agent.md     # Animations & transitions
    │   ├── compose-performance-auditor.md # Recomposition/perf analysis (read-only)
    │   ├── viewmodel-architect.md         # MVVM/StateFlow/UiState patterns
    │   ├── navigation-agent.md            # Navigation graph & type-safe routes (exclusive owner)
    │   ├── permissions-agent.md           # Runtime media permission flow
    │   ├── widget-agent.md                # Home screen widget (Glance, exclusive owner)
    │   ├── build-tooling-agent.md         # Gradle, version catalog, R8, CI (exclusive owner)
    │   ├── code-reviewer.md               # Code quality & offline compliance (read-only)
    │   └── test-writer.md                 # Unit, integration & UI tests
    ├── skills/                            # Multi-step workflows
    │   ├── add-new-screen/
    │   ├── add-room-database/
    │   ├── add-dependency-injection-hilt/
    │   ├── implement-repository-pattern/
    │   ├── implement-use-cases/
    │   ├── implement-state-management/
    │   ├── implement-navigation-graph/
    │   ├── setup-modularization/
    │   ├── bootstrap-project/
    │   ├── handle-runtime-permissions/
    │   ├── implement-media-scanning/
    │   ├── implement-equalizer/
    │   ├── implement-tag-editor/
    │   ├── implement-home-widget/
    │   ├── implement-datastore/
    │   ├── implement-image-loading-coil/
    │   ├── implement-edge-to-edge-and-insets/
    │   ├── generate-baseline-profile/
    │   ├── setup-static-analysis/
    │   ├── add-animations-transitions/
    │   ├── add-unit-testing/
    │   ├── add-ui-testing-compose/
    │   ├── debug-playback-issue/
    │   └── release-build/
    ├── rules/                             # Coding standards & conventions
    │   ├── kotlin-style.md
    │   ├── compose-conventions.md
    │   ├── media3-playback.md
    │   ├── navigation-conventions.md
    │   ├── model-vocabulary.md
    │   └── ui-style-guide.md
    └── commands/                          # Quick reference commands
        └── run-emulator.md
```

## Key Features

### 🎵 Core
- Fully offline, privacy-focused local playback (MP3, FLAC, WAV, OGG, M4A, AAC)
- Complete playlist management; shuffle and repeat modes
- Background playback with notification controls; persistent playback state

### 🎚️ Advanced Audio
- 8-band graphic equalizer (40Hz–10kHz) with preset management
- Real-time audio visualization

### 📚 Library Management
- Automatic music scanning and indexing (MediaStore → Room)
- Advanced search and filtering; album artwork extraction and caching
- Custom tag system; full ID3 tag editing (single and batch)

### 📱 Home Screen Widget
- Album artwork, visual timeline/progress bar
- 15-second skip controls, previous/next, play/pause toggle, real-time updates

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Audio**: Media3 ExoPlayer
- **Database**: Room (local SQLite)
- **DI**: Hilt
- **Async**: Coroutines & Flow
- **Widget**: Jetpack Glance
- **Testing**: JUnit, MockK, Turbine, Compose Test
- **Storage**: Local file system only (no networking libraries)

**Note**: No networking libraries or internet permissions — fully offline architecture.

## Using the Development Plan

### 1. Project Memory (CLAUDE.md)

[`CLAUDE.md`](CLAUDE.md) is automatically loaded by Claude and contains the project overview, tech-stack decisions, development phases, and current status. **Keep it updated** as the project progresses.

### 2. Specialized Agents

Located in [`.claude/agents/`](.claude/agents/). Several agents are **exclusive owners** of a package or directory to prevent concurrent-edit conflicts.

#### Audio & Playback
- **[Audio Engineer](/.claude/agents/audio-engineer.md)** — ExoPlayer, MediaSession, foreground service, audio focus, gapless playback. Exposes the audio session id for the equalizer (does not own the effect).
- **[Equalizer Agent](/.claude/agents/equalizer-agent.md)** — owns `audio/equalizer/`. Binds the AudioFx Equalizer to the player's session, maps the 8 target bands, applies gains, and manages presets.

#### Data & Library
- **[Data Layer Agent](/.claude/agents/data-layer-agent.md)** — exclusive owner of all Room components (entities, DAOs, migrations, repositories).
- **[Media Scanning Agent](/.claude/agents/media-scanning-agent.md)** — owns `data/scanner/`. MediaStore querying and incremental indexing into Room.
- **[Tag Editor Agent](/.claude/agents/tag-editor-agent.md)** — owns `tageditor/`. ID3/metadata read-write with scoped-storage write consent and batch editing.

#### UI & Design
- **[UI Builder](/.claude/agents/ui-builder.md)** — Compose screens, components, and interactive controls.
- **[M3 Design System Agent](/.claude/agents/m3-design-system-agent.md)** — exclusive owner of `ui/theme/`. Colors, typography, shapes; zero hardcoded values.
- **[Compose Animation Agent](/.claude/agents/compose-animation-agent.md)** — animations, transitions, gesture-driven motion at 60fps.
- **[Compose Performance Auditor](/.claude/agents/compose-performance-auditor.md)** — read-only recomposition/jank analysis.

#### Architecture & Navigation
- **[ViewModel Architect](/.claude/agents/viewmodel-architect.md)** — StateFlow/UiState patterns, events, coroutine management.
- **[Navigation Agent](/.claude/agents/navigation-agent.md)** — exclusive owner of the navigation graph and type-safe `@Serializable` routes.

#### Platform Features
- **[Permissions Agent](/.claude/agents/permissions-agent.md)** — owns `permission/`. Version-aware media permission flow, rationale and Settings routing.
- **[Widget Agent](/.claude/agents/widget-agent.md)** — exclusive owner of `widget/`. The Glance home screen widget, driven by local playback state.

#### Build & Tooling
- **[Build Tooling Agent](/.claude/agents/build-tooling-agent.md)** — exclusive owner of the Gradle build files, `libs.versions.toml`, convention plugins, R8 rules, benchmark/static-analysis config, and CI. The build-level gate for the no-network-dependency rule.

#### Quality
- **[Code Reviewer](/.claude/agents/code-reviewer.md)** — read-only review for MVVM compliance, offline/privacy, memory leaks, performance.
- **[Test Writer](/.claude/agents/test-writer.md)** — unit, integration, and UI tests with offline verification.

### 3. Reusable Skills

Located in [`.claude/skills/`](.claude/skills/). Step-by-step workflows.

#### Architecture & Setup
- **add-new-screen** — full workflow for adding a screen (UI → state → ViewModel → nav → tests)
- **add-room-database** — entities, DAOs, migrations, type converters
- **add-dependency-injection-hilt** — Hilt modules and scoping
- **implement-repository-pattern** — repository interfaces and implementations
- **implement-use-cases** — domain/business-logic layer
- **implement-state-management** — StateFlow/UiState wiring
- **implement-navigation-graph** — type-safe navigation graph
- **implement-datastore** — DataStore for settings/equalizer active-state (replaces SharedPreferences)
- **setup-modularization** — hybrid feature + core multi-module structure (graph, convention plugins, ownership)
- **bootstrap-project** — run-once Phase 0: scaffold → `:core:model` → first vertical slice, end-to-end

#### Feature Implementation
- **handle-runtime-permissions** — version-aware media permission + Compose gate
- **implement-media-scanning** — MediaStore query and incremental Room indexing
- **implement-equalizer** — 8-band equalizer effect, presets, visualization
- **implement-tag-editor** — ID3 read/write with scoped-storage consent and batch edits
- **implement-home-widget** — Glance home screen widget with playback controls

#### Performance & UI Polish
- **generate-baseline-profile** — Macrobenchmark + Baseline Profiles for startup/scroll
- **implement-image-loading-coil** — tuned Coil ImageLoader for album artwork
- **implement-edge-to-edge-and-insets** — edge-to-edge drawing and window insets
- **add-animations-transitions** — Compose animation patterns

#### Build, Testing & Ops
- **setup-static-analysis** — detekt + ktlint with a no-network build gate
- **add-unit-testing** — ViewModel/repository/use-case tests
- **add-ui-testing-compose** — Compose UI tests
- **debug-playback-issue** — systematic audio debugging
- **release-build** — production release preparation and signing

### 4. Coding Rules

Located in [`.claude/rules/`](.claude/rules/):

- **[kotlin-style](/.claude/rules/kotlin-style.md)** — naming, formatting, language features, offline checklist
- **[compose-conventions](/.claude/rules/compose-conventions.md)** — composable structure, state, Material 3, accessibility
- **[media3-playback](/.claude/rules/media3-playback.md)** — ExoPlayer, MediaSession, notifications, background playback
- **[navigation-conventions](/.claude/rules/navigation-conventions.md)** — type-safe `@Serializable` routes, argument and back-stack rules
- **[model-vocabulary](/.claude/rules/model-vocabulary.md)** — canonical domain model names (`Song`, never `Track`)
- **[ui-style-guide](/.claude/rules/ui-style-guide.md)** — visual contract: colors, typography, shapes, per-screen layout, widget style (implemented in `:core:designsystem`)

### 5. Quick Commands

Located in [`.claude/commands/`](.claude/commands/):

- **[run-emulator](/.claude/commands/run-emulator.md)** — start emulators, install apps, view logs, troubleshoot

## Development Phases

### Phase 1: Foundation (Weeks 1-2)
- Project setup and dependencies; basic Compose structure; Room schema; media scanning

### Phase 2: Core Playback (Weeks 3-4)
- ExoPlayer integration; playback service with MediaSession; notification controls; player UI

### Phase 3: Library Management (Weeks 5-6)
- Library screens; search and filtering; album artwork; navigation

### Phase 4: Playlists (Week 7)
- Playlist creation/management; drag-and-drop reordering

### Phase 5: Advanced Features (Weeks 8-9)
- 8-band equalizer and presets; visualization; tag editor (single + batch); custom tags

### Phase 6: Widget & Polish (Week 10)
- Home screen widget; UI/UX refinements; performance optimization

### Phase 7: Testing & Release (Weeks 11-12)
- Comprehensive testing; documentation; release preparation

## Getting Started

### For Development

1. **Review the project memory** — read `CLAUDE.md` for context.
2. **Choose the right agent** for the task:
   - Audio/playback → Audio Engineer; equalizer → Equalizer Agent
   - Database → Data Layer Agent; scanning → Media Scanning Agent; tags → Tag Editor Agent
   - UI → UI Builder; theme → M3 Design System; motion → Compose Animation; perf → Performance Auditor
   - State → ViewModel Architect; navigation → Navigation Agent
   - Permissions → Permissions Agent; widget → Widget Agent
   - Review → Code Reviewer; tests → Test Writer
3. **Follow the relevant skill** for multi-step tasks.
4. **Apply the coding rules** throughout.

### For Claude Code

1. **CLAUDE.md is auto-loaded** — it provides project context.
2. **Reference agents** for specialized expertise; respect exclusive-ownership boundaries.
3. **Use skills** for step-by-step guidance.
4. **Follow rules** for consistency.
5. **Use commands** for quick reference.

## Configuration

See [`.claude/settings-guide.md`](.claude/settings-guide.md) for the `settings.json` structure (agents, skills, rules, permissions). It reflects the full offline configuration.

## Best Practices

### When Starting a New Feature
1. Review the relevant agent documentation
2. Follow the appropriate skill workflow
3. Apply coding rules throughout
4. Write tests as you go
5. Request code review before completion

### Before Release
1. Follow the Release Build skill completely
2. Run all tests; perform manual testing
3. Review with the Code Reviewer agent
4. Verify offline operation in airplane mode

## Contributing

When extending this plan:
1. **New Agents** → `.claude/agents/` with clear scope and ownership boundaries
2. **New Skills** → `.claude/skills/<name>/SKILL.md` with step-by-step workflows
3. **New Rules** → `.claude/rules/` with examples and anti-patterns
4. **New Commands** → `.claude/commands/` with usage examples

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

---

**Note**: This is a planning repository. The actual Android project will be created in the implementation phase following this plan.
