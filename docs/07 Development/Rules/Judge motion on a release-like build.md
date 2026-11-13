---
type: rule
status: active
date: 2026-10-11
source: "user, Phase 3 gate (2026-10-11); D3-2 shared-element investigation"
tags: [motion, testing, device]
related: ["[[Rules]]", "[[Shared element outside the NavHost]]"]
---

# Judge motion on a release-like build

**Rule:** animation, transition and jank verdicts come from a release-like build (`assembleBenchmark` /
`:app:installBenchmark`), never from debug.

**Why:** debug Compose roughly doubles first-frame stalls, so a smooth transition can look broken on debug.
