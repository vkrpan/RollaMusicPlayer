---
type: decision
adr: "ADR-006"
status: active
date: 2026-07-10
source: "v1 Phase 6 (settings); `docs/08 Project/History/v1-phases/PHASE6-SETTINGS.md` scope note; KDoc in `SettingsRepository`, `SettingsPreferences`, `PrivacyScreen`"
tags: [settings, product]
related: ["[[Decisions]]", "[[Settings]]", "[[No decorative toggles]]"]
---

# Settings toggles rejected

**Decision:** Settings has theme, dynamic colour, playback speed, skip silence, rescan, Privacy/About/Licenses — and
nothing that would be fake:
- **Crossfade** — rejected: ExoPlayer has no crossfade; it would need dual-player mixing.
- **Gapless toggle** — rejected: gapless is automatic, there is no API to turn it off.
- **Pure black / AMOLED** — rejected: the dark background is already `#000000`.
- **Analytics / crash-reporting switches** on the Privacy screen — never: there is nothing to opt into.

**Why:** [[No decorative toggles]].
