---
type: knowledge
status: active
date: 2026-07-08
source: "build sessions 2026-07-04 to 2026-07-08; `config/detekt/detekt.yml`"
tags: [build, detekt]
related: ["[[Knowledge]]"]
---

# Detekt thresholds and BOMs

- **LongMethod is 30 lines.** This is tighter than the default; one added line can tip a composable over. The in-file
  idiom is `@Suppress("LongMethod")` on the composable.
- **TooManyFunctions:**
  - interfaces 12, classes 15, files 20. Split big Compose screens into several files from the start (the SearchScreen
    lesson).
  - `PlaylistDao`/`FakePlaylistDao` carry a justified suppression: Room `@Transaction` defaults need their `@Query`
    primitives as members.
- **A UTF-8 BOM in a `.kt` file** silently disables detekt's annotation handling for the whole file
  (`@file:Suppress`, `ignoreAnnotated: ['Composable']`), producing baffling findings. Strip the BOM.
- `./gradlew check` runs JVM tests, detekt, Spotless and lint, but **not** instrumented tests; compile androidTest
  sources with e.g. `:core:database:compileDebugAndroidTestKotlin`.
