---
type: rule
status: active
date: 2026-07-01
source: "CLAUDE.md 'Offline & Privacy Architecture' (project founding constraint)"
tags: [privacy, offline, product]
related: ["[[Rules]]", "[[Release build fails on INTERNET permission]]", "[[Equaliser visualizer rejected]]", "[[Permissions]]"]
---

# Offline and privacy first

**Rule:** Rolla Music is fully offline and collects nothing.
- No network layer: no Retrofit/OkHttp, no Firebase, no analytics or crash reporting, no SDK that phones home.
- No `INTERNET` permission. Permissions are `READ_MEDIA_AUDIO` (13+) or `READ_EXTERNAL_STORAGE` (older) only;
  `WRITE_EXTERNAL_STORAGE` (maxSdk 28) is requested only at the point of a tag write.
- All data stays on the device: Room, DataStore, app-specific caches. No online artwork or metadata lookups.
- No loading or sync states for network work, because there is none.

**Why:** privacy by design is the product's identity.

**How to apply:** reject any dependency, feature or permission that needs the network or a data-collecting SDK. The build
enforces part of this: [[Release build fails on INTERNET permission]]. Features that need a dangerous permission are
rejected the same way: [[Equaliser visualizer rejected]].
