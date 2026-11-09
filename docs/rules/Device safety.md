---
type: rule
status: active
date: 2026-10-21
source: "user, device-check sessions 2026-10-12 to 2026-11-08"
tags: [device, safety, adb]
related: ["[[Rules]]", "[[Device tests run on the Motorola]]", "[[Motorola edge 70]]", "[[Samsung SM-S901B]]", "[[adb on the Motorola]]"]
---

# Device safety

**Rule:** the user's phones hold the user's real library and settings. On any device:
- Install only with `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark` (or `installDebug`) on the Motorola.
- Never uninstall, never clear app data, never revoke permissions, never change system settings (ask the user).
- Never touch the user's playlists and never save tags on the phone: tag writes on a device are done by the user's hand.
- Read-only probes (`dumpsys`, `content query`, `content read`, `adb pull` of a file copy) are fine.
- Before injected input, check where it lands (focus); before media keys, check the media-button receiver.
- The Samsung is read-only unless the user asks again.

**Why:** a wrong step wipes or changes data the user cares about, and nothing on the phone can be rolled back.

**How to apply:** pass `-s ZY22MBNVB4` (or `ANDROID_SERIAL`) on every call. In Git Bash set `MSYS_NO_PATHCONV=1` for
`adb pull /storage/...` (path mangling). Long-press drags need the user's finger: [[adb on the Motorola]].
