---
type: knowledge
status: active
date: 2026-10-14
source: "Media3 upgrade Task 1, 2026-10-14"
tags: [build, lint, agp]
related: ["[[Knowledge]]", "[[Media3 1.11 upgrade]]"]
---

# AGP 8.13 lint gotchas

- Lint 31.13 (K2 UAST) crashes Compose 1.7.x's `ModifierDeclarationDetector` with an NPE on a `data class` that is a
  `ModifierNodeElement`. Use a plain class with explicit `equals`/`hashCode`.
- An unescaped Windows `sdk.dir=C:/…` in `local.properties` raises a `PropertyEscape` error; write `C\:/…`.
- Gradle 8.9 cannot configure AGP 8.13: set the wrapper URL by hand before running the wrapper task.
