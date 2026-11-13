---
type: hub
status: active
date: 2026-11-13
source: "gradle/libs.versions.toml and build-logic/convention (read 2026-11-13)"
tags: [overview, tech-stack]
related: ["[[00 Home]]", "[[Tech stack choices]]", "[[Architecture Overview]]", "[[Configuration]]"]
---

# Tech Stack

Why these choices: [[Tech stack choices]]. Versions live in `gradle/libs.versions.toml`; this table is a reading aid,
and the catalog wins when they differ.

| Layer | Technology | Why / notes |
|---|---|---|
| Language | Kotlin 2.2.21, KSP2, JVM 17 | |
| Build | AGP 8.13.2, Gradle 8.13, version catalog, convention plugins | [[build-logic]], [[Multi-module architecture]] |
| SDK levels | min 24, target 34, compile 36 | `RollaAndroidApplicationPlugin` |
| UI | Jetpack Compose (BOM 2024.09.03), Material 3 wrapped by the One UI kit | [[UI style guide]], [[core-designsystem]] |
| Navigation | Navigation Compose 2.8.3, type-safe `@Serializable` routes | [[Navigation routes]], [[Navigation conventions]] |
| Playback | Media3 1.11.1 ExoPlayer, `MediaLibraryService`, `MediaSession` | [[core-media]], [[Media3 1.11 upgrade]] |
| Database | Room 2.8.5 (schema v7) | [[Database Schema]] |
| Settings | Preferences DataStore 1.1.1 | [[Configuration]] |
| DI | Hilt 2.57.2 (2.59 needs AGP 9) | |
| Async | Coroutines 1.8.1, Flow | |
| Images | Coil 2.7.0, per-track artwork cache | [[Per-track artwork]] |
| Tag IO | jaudiotagger 2.3.15 (a fork) | [[core-tagio]], [[Tag editor]] |
| Widget | Glance 1.1.0 | [[Home-screen widget]] |
| Serialization | kotlinx.serialization 1.7.3 | routes, `ROLLA_TAGS`, the tag list file |
| Static analysis | detekt 1.23.8, Spotless 6.25.0 (ktlint), `checkNoNetwork`, `checkNoMaterialIcons` | [[Conventions]] |
| Tests | JUnit 4, MockK, Turbine, Robolectric 4.13, Compose UI tests | [[Testing]] |
| Performance | Macrobenchmark 1.3.3, Baseline Profile | [[baselineprofile]] |

No network library anywhere in the graph (no Retrofit, OkHttp, Ktor or Firebase); `checkNoNetwork` and
`checkReleaseManifestNoInternet` enforce it ([[Offline and privacy first]],
[[Release build fails on INTERNET permission]]).
