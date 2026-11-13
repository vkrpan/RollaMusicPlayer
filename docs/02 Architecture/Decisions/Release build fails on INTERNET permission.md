---
type: decision
adr: "ADR-009"
status: active
date: 2026-07-23
source: "v1 Phase 8 release build (`1f86d53`, `074e994`); CLAUDE.md shipped table"
tags: [release, build, privacy]
related: ["[[Decisions]]", "[[Offline and privacy first]]", "[[Release ships unsigned without a signing config]]", "[[build-tooling-agent]]"]
---

# Release build fails on INTERNET permission

**Decision:** the Gradle task `checkReleaseManifestNoInternet` reads the real **merged** release manifest and fails
`./gradlew check` if `INTERNET` (or a similar network permission) appears, including one pulled in by a dependency.

**Why:** [[Offline and privacy first]] must not depend on reviewers noticing a transitive permission.

**How it is used:** part of every release gate (`assembleRelease checkReleaseManifestNoInternet`).
