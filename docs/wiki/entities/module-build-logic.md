---
type: entity
summary: "Convention plugins (an included build) plus the version catalog and root Gradle files."
aliases: ["build-logic"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# build-logic

Convention plugins (an included build) plus the version catalog and root Gradle files.

Convention plugins (`RollaAndroidApplicationPlugin`, `RollaAndroidLibraryPlugin`, …) in an included build, with
`gradle/libs.versions.toml`, the root build files, ProGuard rules and detekt config. Sets `compileSdk 36`,
`minSdk 24` and `targetSdk 34` ([[tech-stack]]).

- **Owner:** build-tooling-agent ([[agent-module-ownership]]).
- Enforces the no-network rule at build level ([[offline-privacy-architecture]]).

## Related
[[claude-md]], [[ownership-map]], [[tech-stack]], [[agent-module-ownership]], [[offline-privacy-architecture]]
