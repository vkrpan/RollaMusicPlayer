---
type: decision
status: active
date: 2026-10-12
source: "spec `docs/superpowers/specs/2026-10-12-queue-restore-design.md` (approved 2026-10-12, amended 2026-10-13); plan `docs/superpowers/plans/2026-10-12-queue-restore.md`"
tags: [playback, queue, database]
related: ["[[Decisions]]", "[[Playback and queue]]", "[[Panel resume targets stock Android]]", "[[core-media]]", "[[core-database]]", "[[Ledgers]]"]
---

# Playback queue restore

**Decision:**
- The queue, the current track, the position, the exact shuffle order and the repeat mode are saved in Room v5
  (`playback_queue`, `playback_state`).
- On the next start they are restored **paused**.
- A headset or Bluetooth Play resumes them without opening the app.

**Why:** they lived only in the service's ExoPlayer and were lost on every process death (an update, a swipe-away, a
force-stop, the OS reclaiming memory). CLAUDE.md claimed persistence it did not have ([[Keep docs honest]]).

**Gap (P7):** the System UI carousel could not resume after process death. That needed a `MediaLibraryService`: see
[[Panel resume targets stock Android]].

**Rulings:** [[2026-10-12-queue-restore rulings]].
