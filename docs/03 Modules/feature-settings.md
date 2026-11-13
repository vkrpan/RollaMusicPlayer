---
type: module
status: active
gradle: ":feature:settings"
code: "feature/settings"
owners: ["[[ui-builder]]", "[[viewmodel-architect]]"]
tags: [module]
related: ["[[Modules]]", "[[Settings]]", "[[Settings toggles rejected]]"]
---

# feature-settings

`:feature:settings` — Settings, Privacy, About, Licenses, manual rescan.

- **Owner agent(s):** [[ui-builder]], [[viewmodel-architect]]
- **Features:** [[Settings]]
- **Decisions and rules:** [[Settings toggles rejected]]

## Changes
- 2026-11-08 (per-track artwork Task 2): `SettingsViewModel` clears `SongArtworkCache` (not the album cache) after a
  successful rescan, best-effort.
- 2026-11-08: `PrivacyScreen` KDoc points at `docs/08 Project/History/v1-phases/PHASE6-SETTINGS.md` (vault move; line rewrapped for detekt).
