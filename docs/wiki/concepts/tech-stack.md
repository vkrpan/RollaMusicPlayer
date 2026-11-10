---
type: concept
summary: "Kotlin 2.2, Compose + M3, Media3 1.11.1, Room, Hilt 2.57.2, AGP 8.13.2, compileSdk 36."
aliases: ["Tech stack choices"]
tags: [build]
sources: ["[[sources/claude-md]]"]
updated: 2026-11-09
---
# Tech stack

Kotlin 2.2, Compose + M3, Media3 1.11.1, Room, Hilt 2.57.2, AGP 8.13.2, compileSdk 36.

Kotlin 2.2.21 (KSP2), Jetpack Compose with Material 3 ([[one-ui-design-system]]), MVVM, Gradle Kotlin DSL with a
version catalog and convention plugins ([[module-build-logic]]), AGP 8.13.2 / Gradle 8.13, Media3 1.11.1 ExoPlayer
([[module-core-media]]), Room ([[module-core-database]]), Hilt 2.57.2 (2.59.x needs AGP 9), coroutines, and Coil for images.
SDK levels: min 24, target 34, compile 36 (confirmed in `build-logic`).

## Related
[[claude-md]], [[one-ui-design-system]], [[module-build-logic]], [[module-core-media]], [[module-core-database]]
