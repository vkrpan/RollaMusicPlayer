---
type: decision
adr: "ADR-002"
status: active
date: 2026-07-01
source: "CLAUDE.md Key Design Decisions and Tech Stack"
tags: [architecture, tech-stack]
related: ["[[Decisions]]", "[[Multi-module architecture]]", "[[Media3 1.11 upgrade]]"]
---

# Tech stack choices

**Decision:**
- **Media3 ExoPlayer** over `MediaPlayer` (better API, active development); playback runs in a foreground
  `MediaLibraryService` with a `MediaSession`.
- **Jetpack Compose** + Material 3 as the base, wrapped by the One UI kit ([[Build UI from tokens and the kit]]).
- **MVVM** with `StateFlow` UI state; **Room** for the library, playlists, presets and the saved queue; **DataStore**
  for settings, equaliser active state and recent searches; **Hilt** for DI; **Coil 2** for images; coroutines.
- Kotlin 2.2.21 (KSP2), AGP 8.13.2, Gradle 8.13, compileSdk 36, minSdk 24, targetSdk 34.
- **Hilt stays on 2.57.2**: 2.59.x needs AGP 9.

**Why:** the official, maintained Android stack, all of it offline-capable ([[Offline and privacy first]]).

**Rules out:** network libraries, Firebase, data-collecting SDKs.
