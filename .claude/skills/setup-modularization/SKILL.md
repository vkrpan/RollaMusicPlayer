---
name: setup-modularization
description: "Step-by-step workflow for structuring RollaMusicPlayer as a hybrid feature + core multi-module Gradle project (Now in Android style) — the module graph, convention plugins, dependency rules, the agent-to-module ownership map, and the migration order. Build-tooling-agent owns the wiring."
---

# Skill: Setup Modularization (Feature + Core)

## Overview
A workflow for organizing RollaMusicPlayer as a multi-module project using the hybrid **feature + core** approach (Now in Android style). The app shell wires everything together; `:core:*` modules hold shared concerns (models, database, datastore, media, design system); `:feature:*` modules hold one screen-area each. This maximizes build parallelism, enforces clean boundaries, and maps almost 1:1 onto the existing agents. `build-tooling-agent` owns the Gradle wiring and convention plugins; this skill defines the graph and the rules everyone codes against.

## When to Use
- Bootstrapping the project structure before feature work
- Adding a new feature or core module
- Resolving where a piece of code belongs
- Wiring convention plugins and inter-module dependencies

## Prerequisites
- Decision made: hybrid feature + core (this skill)
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) — owned by build-tooling-agent
- `build-logic/` included build for convention plugins

## Module Graph

```
:app                          # Application, MainActivity, NavHost wiring, DI root, app theme entry

:core
├── :core:model               # Domain models + enums (Song, Album, Artist, Playlist, EqualizerPreset). Pure Kotlin.
├── :core:common              # Dispatchers, Result types, extensions, base utilities
├── :core:database            # Room: entities, DAOs, migrations, MusicDatabase
├── :core:datastore           # DataStore: settings + equalizer active-state
├── :core:data                # Repositories + media scanner (coordinates database + datastore + media)
├── :core:media               # Media3 playback service, MediaSession, audio session, equalizer effect
├── :core:designsystem        # Theme (Color/Type/Shape) + model-agnostic components
├── :core:ui                  # Model-aware shared composables (SongListItem, AlbumCard, AlbumArtwork)
├── :core:permissions         # Runtime media permission gate + flow
└── :core:testing             # Fakes, fixtures, test rules

:feature
├── :feature:library          # Songs/Albums/Artists/Genres lists
├── :feature:search           # Local search across the library
├── :feature:player           # Now-playing + mini-player
├── :feature:equalizer        # Equalizer screen (band sliders, presets UI)
├── :feature:playlists        # Playlist management + reordering
├── :feature:tageditor        # ID3 read/write screen + scoped-storage consent
├── :feature:settings         # App settings
└── :feature:widget           # Glance home screen widget

:baselineprofile              # Macrobenchmark + Baseline Profile generator (test module)
build-logic/                  # Convention plugins (included build)
```

## Dependency Rules (enforce strictly)

```
:app            → all :feature:*  + :core:* needed for DI/nav wiring
:feature:*      → :core:ui, :core:designsystem, :core:data, :core:model, :core:common (+ :core:media / :core:permissions as needed)
:feature:*      → NEVER another :feature:*        (features are siblings, never depend on each other)
:core:data      → :core:database, :core:datastore, :core:media, :core:model, :core:common
:core:ui        → :core:designsystem, :core:model
:core:database  → :core:model (+ :core:common)
:core:datastore → :core:model (+ :core:common)
:core:media     → :core:model (+ :core:common)
:core:model     → (pure Kotlin — depends on nothing)
:core:*         → NEVER a :feature:*               (core never knows about features)
```

Golden rules: dependencies point **inward** (feature → core → model); no cycles; features never see each other; cross-feature flows go through `:core:data` or navigation, not direct module deps.

## Workflow Steps

### Step 1: Create the Included Build for Convention Plugins
**Goal**: Share Gradle config instead of copy-pasting

**Implementation**:
```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "RollaMusicPlayer"

include(":app")
include(":core:model", ":core:common", ":core:database", ":core:datastore",
        ":core:data", ":core:media", ":core:designsystem", ":core:ui",
        ":core:permissions", ":core:testing")
include(":feature:library", ":feature:search", ":feature:player",
        ":feature:equalizer", ":feature:playlists", ":feature:tageditor",
        ":feature:settings", ":feature:widget")
include(":baselineprofile")
```

### Step 2: Define Convention Plugins (build-tooling-agent)
**Goal**: One plugin per module archetype

**Implementation**:
```kotlin
// build-logic/convention/build.gradle.kts registers plugins:
//   rolla.android.application        → :app
//   rolla.android.library            → non-Compose libraries (:core:model is a pure kotlin lib instead)
//   rolla.android.library.compose    → Compose-enabled libraries (:core:ui, :core:designsystem)
//   rolla.android.feature            → :feature:* (library + compose + hilt + viewmodel + nav)
//   rolla.android.hilt               → Hilt setup
//   rolla.android.room               → :core:database
//   rolla.jvm.library                → :core:model, :core:common (pure Kotlin)

// Example: a feature module build file is then tiny:
// feature/library/build.gradle.kts
plugins {
    id("rolla.android.feature")
    id("rolla.android.library.compose")
}
android { namespace = "com.rolla.musicplayer.feature.library" }
dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.core.model)
}
```

### Step 3: Create :core:model First
**Goal**: Lock the shared vocabulary before anything depends on it

**Implementation**:
```kotlin
// core/model/src/main/kotlin/com/rolla/musicplayer/core/model/Song.kt
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val contentUri: String,
    val artworkUri: String
)
// Album, Artist, Playlist, EqualizerPreset, RepeatMode, ShuffleMode ...
```
> Settle the names here once (this resolves the `Song` vs `Track` drift across agents — the codebase uses **`Song`**). Every other module imports from `:core:model`.

### Step 4: Build Core Bottom-Up
**Goal**: Stand up the dependency floor before features

**Order**: `:core:model` → `:core:common` → `:core:database` / `:core:datastore` / `:core:media` → `:core:data` → `:core:designsystem` → `:core:ui` → `:core:permissions`.

Each core module exposes a small public API (interfaces + models) and hides implementation. Repositories live in `:core:data` and depend on `:core:database` + `:core:datastore`; the scanner lives in `:core:data`; the playback service + equalizer effect live in `:core:media`.

### Step 5: Add Feature Modules
**Goal**: One screen-area per module, depending only on core

**Implementation**:
- Each `:feature:*` contains its screens, composables, and ViewModels.
- Each feature exposes its own navigation entry (a `NavGraphBuilder.xxxScreen(...)` extension + its routes), which `:app` composes into the `NavHost`. Features do **not** reference each other's routes directly — navigate via the typed routes wired in `:app` (navigation-agent owns the graph).

### Step 6: Wire :app
**Goal**: Compose features, host navigation, root DI

**Implementation**:
```kotlin
// app/build.gradle.kts
plugins { id("rolla.android.application"); id("rolla.android.hilt") }
dependencies {
    implementation(projects.feature.library)
    implementation(projects.feature.search)
    implementation(projects.feature.player)
    implementation(projects.feature.equalizer)
    implementation(projects.feature.playlists)
    implementation(projects.feature.tageditor)
    implementation(projects.feature.settings)
    implementation(projects.feature.widget)
    implementation(projects.core.designsystem)
    implementation(projects.core.media)      // service registration
    implementation(projects.core.permissions)
}
```
`:app` owns `RollaApp` (Application + Hilt + ImageLoaderFactory), `MainActivity` (enableEdgeToEdge + setContent), and the `NavHost` that stitches feature nav entries together.

### Step 7: Verify
**Checklist**:
- [ ] `./gradlew assembleDebug` builds the whole graph
- [ ] No `:feature` depends on another `:feature`
- [ ] No `:core` depends on any `:feature`
- [ ] No dependency cycles (`./gradlew :app:dependencies` is acyclic)
- [ ] `:core:model` is a pure Kotlin module with no Android deps
- [ ] Module build files are thin (logic in convention plugins)
- [ ] Each feature exposes its own nav entry; `:app` composes them
- [ ] Build is meaningfully parallel (independent modules compile concurrently)

## Agent ↔ Module Ownership

| Module | Owning agent(s) |
| --- | --- |
| `:app` (Application, MainActivity, NavHost, DI root) | navigation-agent (NavHost) + viewmodel-architect/ui-builder (wiring); build-tooling-agent (gradle) |
| `:core:model` | shared — changes reviewed by code-reviewer; treat as a contract |
| `:core:common` | shared utilities |
| `:core:database` | **data-layer-agent** (exclusive) |
| `:core:datastore` | data-layer-agent / implemented per `implement-datastore` |
| `:core:data` (repositories) | data-layer-agent; **scanner → media-scanning-agent** |
| `:core:media` (playback) | **audio-engineer**; **equalizer effect → equalizer-agent** |
| `:core:designsystem` | **m3-design-system-agent** (exclusive) |
| `:core:ui` (shared components) | ui-builder |
| `:core:permissions` | **permissions-agent** (exclusive) |
| `:core:testing` | test-writer |
| `:feature:library` / `:feature:search` | ui-builder + viewmodel-architect |
| `:feature:player` | ui-builder + viewmodel-architect (consumes `:core:media`) |
| `:feature:equalizer` (screen) | ui-builder; effect stays in `:core:media` (equalizer-agent) |
| `:feature:playlists` / `:feature:settings` | ui-builder + viewmodel-architect |
| `:feature:tageditor` | **tag-editor-agent** (reader/writer/consent) + ui-builder (screen) |
| `:feature:widget` | **widget-agent** (exclusive) |
| `:baselineprofile`, `build-logic/`, root Gradle, catalog | **build-tooling-agent** (exclusive) |
| cross-cutting (animations, perf, review, nav contracts) | compose-animation-agent, compose-performance-auditor, code-reviewer, navigation-agent |

## Related Files
- `settings.gradle.kts` — module includes
- `build-logic/` — convention plugins
- `gradle/libs.versions.toml` — versions (build-tooling-agent)
- each module's `build.gradle.kts`

## Notes
- Use type-safe project accessors (`projects.core.ui`) — enable `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` in settings.
- Keep `:core:model` pure Kotlin (a JVM library) so it stays dependency-light and fast to compile.
- `build-tooling-agent` owns the convention plugins and catalog; feature/core agents code against the module APIs, not the build logic.
- This structure is what the existing skills assume — e.g. `:core:database` for `add-room-database`, `:core:media` for the playback/equalizer skills, `:core:permissions` for `handle-runtime-permissions`, `:feature:widget` for `implement-home-widget`.
- Update each agent's "owned files" paths from single-module (`app/src/main/.../x`) to the module path above as you migrate.

## Common Pitfalls
- ❌ Feature-to-feature dependencies — the #1 modularization smell; route through `:core` or navigation.
- ❌ A "god" `:core` that everything depends on for everything — split by concern (the graph above).
- ❌ Putting business logic in module build files instead of convention plugins — drift across modules.
- ❌ Letting `:core` import a feature — inverts the dependency direction and creates cycles.
- ❌ Duplicating models per module instead of a single `:core:model` — reintroduces the Song/Track drift.
