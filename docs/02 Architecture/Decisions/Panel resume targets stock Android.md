---
type: decision
adr: "ADR-018"
status: active
date: 2026-10-17
source: "spec `docs/superpowers/specs/2026-10-15-panel-resume-design.md` (approved 2026-10-15, §10 device result); plan `docs/superpowers/plans/2026-10-15-panel-resume.md`"
tags: [playback, platform, system-ui]
related: ["[[Decisions]]", "[[Playback queue restore]]", "[[One UI has no media resume card]]", "[[Stock Android is the target platform]]", "[[Media3 1.11 upgrade]]"]
---

# Panel resume targets stock Android

**Decision:**
- `PlaybackService` became a `MediaLibraryService` with no browsable library.
- The System UI's metadata request (Media3 1.11's `isForPlayback = false`) is side-effect free.
- The goal: a System UI media card that keeps the saved track after the process has died, with a Play that resumes
  the queue the same way.

**Result:**
- The design follows stock Android's System UI (AOSP `MediaResumeListener`). It is **not yet device-verified**.
- On Samsung One UI (SM-S901B, Android 14) the card is not delivered, because One UI never adds new apps to its resume
  list ([[One UI has no media resume card]]).
- The user ruled that stock Android is the target ([[Stock Android is the target platform]]), so the Samsung gap is a
  known limitation.

**Open:** verify the card on a stock-Android device ([[Backlog]]).

**Exposure (spec §10 of the panel-resume spec):** Media3 answers the recent root's children to any connected Media3
browser, so an app can read the saved queue's titles, artists and albums.

**Resume-card artwork (user decision, 2026-11-08: "I want to show the white note"):** the resume card always shows the
white-note placeholder, never the track's own picture, so a photo the user set from their gallery is never handed to
another app through the recent root. The start item carries the placeholder as PNG bytes (Media3's legacy browse path
decodes only `artworkData`); the track's picture is never read for this request. Per-track artwork Task 4, rulings P12
and P14. The live notification, lock screen and live media card still show the track's own picture.
