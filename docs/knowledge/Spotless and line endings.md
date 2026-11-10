---
type: knowledge
status: active
date: 2026-10-07
source: "build sessions 2026-07-04, 2026-07-07, 2026-10-07"
tags: [build, spotless, windows]
related: ["[[Knowledge]]", "[[Edit files with Write and Edit, not the shell]]"]
---

# Spotless and line endings

**Symptom:** `spotlessKotlinCheck` fails with a wall of line-ending diffs.

**Cause:** Spotless enforces **CRLF** on this Windows checkout (from git attributes). Files written with LF fail;
`sed -i` in Git Bash silently converts a CRLF file to LF (2026-10-07: a one-word edit turned all 48 lines of
`TokenInvariantsTest.kt` to LF).

**Fix:** edit with the Edit/Write tools; run `./gradlew spotlessApply` before `check`. New files the Write tool
creates are LF too, so run `spotlessApply` after creating one.

**Root-only tasks (custom tags phase 1, 2026-11-10):** Spotless is configured at the root only, so
`./gradlew :core:data:spotlessCheck` fails with "task not found". Use the root `spotlessCheck`/`spotlessApply`, or
`spotlessApply -PspotlessIdeHook=<absolute path>` to format a single file.

**Related flake:** "Cannot fingerprint input property 'lineEndingsPolicy' … cannot be serialized" when `spotlessApply`
and `check` run back-to-back in one shell call. Not a formatting failure: re-run `spotlessCheck` in a fresh call.
After Spotless runs, repo-wide greps also match cache copies under `build/spotless/`; exclude `build`.
