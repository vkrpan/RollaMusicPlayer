---
type: source
summary: "CODEOWNERS-style map: one primary owner agent per path, required reviewers for cross-cutting files."
aliases: ["Ownership map", "docs/03 Modules/Agents/Ownership map.md", "docs/agents/Ownership map.md"]
tags: [source]
sources: []
updated: 2026-11-13
---
# Source: Ownership map (formerly OWNERSHIP.md)

CODEOWNERS-style map: one primary owner agent per path, required reviewers for cross-cutting files.

**What it covers:** gives every path exactly one primary owner agent and names required reviewers for the
cross-cutting files (`RollaApp.kt`, `MainActivity.kt`, the manifest, `:core:model`, DI modules, `CLAUDE.md`). Moved
from the repo root `OWNERSHIP.md` into the vault on 2026-11-08.

**Key decisions it records:**
- One primary owner per path; cross-cutting files list required reviewers ([[agent-module-ownership]]).
- `:core:model` is governed, not owned ([[core-model-governance]]).
- Features never reach across ([[multi-module-architecture]]).
- The manifest is edited by section owners only.
- A dedicated app-shell agent only if `:app` grows; the map can generate a real `.github/CODEOWNERS` later.

## Mentions
[[agent-module-ownership]], [[core-model-governance]], [[multi-module-architecture]], [[song-not-track]], [[module-app]],
[[module-core-model]], [[module-core-common]], [[module-core-database]], [[module-core-datastore]], [[module-core-data]], [[module-core-media]],
[[module-core-designsystem]], [[module-core-ui]], [[module-core-permissions]], [[module-core-testing]], [[module-feature-library]], [[module-feature-search]],
[[module-feature-player]], [[module-feature-equalizer]], [[module-feature-playlists]], [[module-feature-tageditor]], [[module-feature-settings]],
[[module-feature-widget]], [[module-build-logic]], [[module-baselineprofile]]

## Related
[[agent-module-ownership]], [[core-model-governance]], [[multi-module-architecture]], [[song-not-track]], [[module-app]], [[module-core-model]], [[module-core-common]], [[module-core-database]], [[module-core-datastore]], [[module-core-data]], [[module-core-media]], [[module-core-designsystem]], [[module-core-ui]], [[module-core-permissions]], [[module-core-testing]], [[module-feature-library]], [[module-feature-search]], [[module-feature-player]], [[module-feature-equalizer]], [[module-feature-playlists]], [[module-feature-tageditor]], [[module-feature-settings]], [[module-feature-widget]], [[module-build-logic]], [[module-baselineprofile]]

## Disagreements & open questions
- The `:app/.../di/**` rows (AppModule, DatabaseModule, MediaModule, ImageModule, PreferencesModule) do not match the
  code: there is no `di/` package in `:app`, `DatabaseModule` is in [[module-core-database]], `PreferencesModule` in
  [[module-core-datastore]], and no `MediaModule` or `ImageModule` exists ([[module-app]]).
- The map's own note says some paths predate the One UI redesign.
