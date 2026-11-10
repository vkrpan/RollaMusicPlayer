---
type: concept
summary: "Every path has one primary owner agent; cross-cutting files name required reviewers."
aliases: ["Ownership map", "CODEOWNERS"]
tags: [agents, ownership]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# Agent ↔ module ownership

Every path has one primary owner agent; cross-cutting files name required reviewers.

From [[ownership-map]] and the quick map in [[claude-md]]. Principles: one primary owner per path;
cross-cutting files list required reviewers; shared contracts are governed, not owned ([[core-model-governance]]);
features never reach across ([[multi-module-architecture]]).

| Module / path | Primary owner |
|---|---|
| [[module-app]] `navigation/**` | navigation-agent |
| [[module-app]] `RollaApp.kt` | build-tooling-agent (reviewers: audio-engineer, permissions-agent, ui-builder) |
| [[module-app]] `MainActivity.kt` | ui-builder (reviewers: navigation-agent, m3-design-system-agent) |
| [[module-core-model]] | none (shared contract) |
| [[module-core-common]] | code-reviewer (light governance) |
| [[module-core-database]], [[module-core-datastore]], [[module-core-data]] repositories | data-layer-agent |
| [[module-core-data]] `scanner/**` | media-scanning-agent |
| [[module-core-media]] playback / `equalizer/**` | audio-engineer / equalizer-agent |
| [[module-core-designsystem]] | m3-design-system-agent |
| [[module-core-ui]] | ui-builder |
| [[module-core-permissions]] | permissions-agent |
| [[module-core-testing]] | test-writer |
| [[module-feature-library]], [[module-feature-search]], [[module-feature-player]], [[module-feature-playlists]], [[module-feature-settings]] | ui-builder + viewmodel-architect |
| [[module-feature-equalizer]] | ui-builder (screen) |
| [[module-feature-tageditor]] | tag-editor-agent (IO), ui-builder (screens) |
| [[module-feature-widget]] | widget-agent |
| [[module-build-logic]], [[module-baselineprofile]], version catalog, CI | build-tooling-agent |

Review-only roles: compose-animation-agent (motion), compose-performance-auditor (recomposition) and code-reviewer
(every change). The manifest is split by section: permissions → permissions-agent, the playback `<service>` →
audio-engineer, the widget `<receiver>` → widget-agent.

## Related
[[claude-md]], [[ownership-map]], [[core-model-governance]], [[multi-module-architecture]], [[module-app]], [[module-core-model]], [[module-core-common]], [[module-core-database]], [[module-core-datastore]], [[module-core-data]], [[module-core-media]], [[module-core-designsystem]], [[module-core-ui]], [[module-core-permissions]], [[module-core-testing]], [[module-feature-library]], [[module-feature-search]], [[module-feature-player]], [[module-feature-playlists]], [[module-feature-settings]], [[module-feature-equalizer]], [[module-feature-tageditor]], [[module-feature-widget]], [[module-build-logic]], [[module-baselineprofile]]

## Disagreements & open questions
- The map's `:app/.../di/**` rows name DI modules in `:app` that do not exist there (see [[module-app]]). Code wins.
- The map suggests a dedicated app-shell agent if `:app` grows; none has been created.
