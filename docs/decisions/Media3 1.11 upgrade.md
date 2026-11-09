---
type: decision
status: active
date: 2026-10-14
source: "spec `docs/superpowers/specs/2026-10-14-media3-upgrade-design.md` (approved 2026-10-14, done 2026-10-15); plan `docs/superpowers/plans/2026-10-14-media3-upgrade.md`"
tags: [playback, build, toolchain]
related: ["[[Decisions]]", "[[Tech stack choices]]", "[[Panel resume targets stock Android]]", "[[AGP 8.13 lint gotchas]]"]
---

# Media3 1.11 upgrade

**Decision:**
- Media3 moved from 1.4.1 to **1.11.1** with the toolchain it needs: Kotlin 2.2.21 (KSP2), AGP 8.13.2 and Gradle 8.13.
  The approach was staged.
- No user-visible behaviour changed except where Media3 forced it.
- The database stayed at v5 with a byte-identical schema, and an upgrade install keeps all user data.
- Every queue-restore ruling that depended on 1.4.1 internals was re-checked against the 1.11.1 sources.

**Why:** panel resume needed 1.11's `onPlaybackResumption(isForPlayback)`.

**Consequence:** Hilt is pinned to 2.57.2, because 2.59 needs AGP 9 ([[Tech stack choices]]).
