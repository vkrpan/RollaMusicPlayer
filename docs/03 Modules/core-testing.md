---
type: module
status: active
gradle: ":core:testing"
code: "core/testing"
owners: ["[[test-writer]]"]
tags: [module]
related: ["[[Modules]]"]
---

# core-testing

`:core:testing` — Fakes, fixtures and test rules shared by module tests.

- **Owner agent(s):** [[test-writer]]
- **Features:** —
- **Decisions and rules:** —

## Changes
- 2026-11-09 (custom tags phase 1): audio fixtures `core.testing.audio.AudioFixtures` and `M4aFixture` (moved from
  [[feature-tageditor]]'s tests; `M4aFixture` can build freeform atoms and a title) and `FakeCustomTagDao` (per-kind
  counters that only rise; `seedSongs(...)` before linking; throws `SQLiteConstraintException` like Room, so tests that
  reach it need Robolectric). Used by [[core-tagio]] and [[core-data]] tests.
