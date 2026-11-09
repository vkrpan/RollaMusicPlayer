---
type: knowledge
status: active
date: 2026-10-21
source: "Phase 5 and 6b, 2026-10-18 to 2026-10-21"
tags: [build, gradle, windows]
related: ["[[Knowledge]]", "[[Stale test XML fakes a RED]]"]
---

# Windows Gradle daemon locks

**Symptom:** a build fails mid-run because a module's `classes.jar` is locked.

**Fix:** `./gradlew --stop`. If that doesn't clear it (seen three times in Phase 6b), the lock is held by the separate
idle **Kotlin compile daemon**, which `--stop` leaves running; end that process.
