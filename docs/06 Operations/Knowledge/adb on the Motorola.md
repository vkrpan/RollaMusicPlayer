---
type: knowledge
status: active
date: 2026-11-08
source: "device sessions 2026-10-21 to 2026-11-08"
tags: [device, adb, testing]
related: ["[[Knowledge]]", "[[Motorola edge 70]]", "[[Device safety]]"]
---

# adb on the Motorola

- **adb isn't on PATH** in Git Bash: use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`.
- **Path mangling:** Git Bash rewrites `/storage/...` arguments. Set `MSYS_NO_PATHCONV=1` for `adb pull`/`shell`
  paths.
- **Long-press drags don't work:** adb-injected long-press drags don't start Compose
  `detectDragGesturesAfterLongPress` (`input motionevent` and `input draganddrop` both fail). Ask the user's finger
  for drag checks.
- **`uiautomator dump` fails** ("could not get idle state") while the mini-player marquee runs. Tap by screenshot
  coordinates (3.0 px/dp).
- **`am kill` only ends a cached process.** Right after Home the app isn't cached yet; wait ≈15 s (procState 15).
  Swiping from Recents leaves it cached but discards saved instance state (the tab resets by design).
- **Read-only probes that work:**
  - `content query --uri content://media/external/audio/media --projection _id:title:album:album_id:_data`
  - `content read --uri <content uri> > file`
  - `adb pull <file>`
