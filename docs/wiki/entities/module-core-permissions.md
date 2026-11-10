---
type: entity
summary: "The runtime media permission gate and flow (storage only)."
aliases: [":core:permissions"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :core:permissions

The runtime media permission gate and flow (storage only).

The version-aware media permission (`READ_MEDIA_AUDIO` on 13+, `READ_EXTERNAL_STORAGE` before), rationale UI and the
Compose permission gate. Storage only, per the [[offline-privacy-architecture]].

- **Owner:** permissions-agent ([[agent-module-ownership]]).

## Related
[[claude-md]], [[ownership-map]], [[offline-privacy-architecture]], [[agent-module-ownership]]
