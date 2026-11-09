---
type: knowledge
status: active
date: 2026-10-21
source: "Phase 6b Task 3, 2026-10-21"
tags: [testing, gradle, mutation]
related: ["[[Knowledge]]", "[[Windows Gradle daemon locks]]"]
---

# Stale test XML fakes a RED

**Symptom:** a mutation run "fails" as expected, but the failure is from the previous run.

**Cause:** when Gradle fails before the test task runs (e.g. a locked `classes.jar`), `build/test-results/<task>/*.xml`
still holds the old failure.

**Fix:** clear those XMLs before each mutation run, and quote a RED only when the log shows
`> Task …testDebugUnitTest FAILED` plus the failing assertion line.
