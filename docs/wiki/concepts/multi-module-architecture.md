---
type: concept
summary: "Hybrid feature + core modules; features never depend on each other."
aliases: ["Project structure", "Module graph"]
tags: [architecture, modules]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# Multi-module architecture

Hybrid feature + core modules; features never depend on each other.

Now-in-Android style, hybrid feature + core ([[claude-md]]):

- [[module-app]] wires everything.
- `:core:*` hold shared concerns: [[module-core-model]], [[module-core-common]], [[module-core-database]], [[module-core-datastore]],
  [[module-core-data]], [[module-core-media]], [[module-core-designsystem]], [[module-core-ui]], [[module-core-permissions]], [[module-core-testing]].
- `:feature:*` hold one screen-area each: [[module-feature-library]], [[module-feature-search]], [[module-feature-player]],
  [[module-feature-equalizer]], [[module-feature-playlists]], [[module-feature-tageditor]], [[module-feature-settings]], [[module-feature-widget]].
- Tooling: [[module-build-logic]], [[module-baselineprofile]].

**Dependency direction:** `:feature → :core → :core:model`. Features never depend on each other, core never depends
on a feature, and there are no cycles; cross-feature flows go through [[module-core-data]] or navigation. Who owns each
module: [[agent-module-ownership]].

## Related
[[claude-md]], [[ownership-map]], [[module-app]], [[module-core-model]], [[module-core-common]], [[module-core-database]], [[module-core-datastore]], [[module-core-data]], [[module-core-media]], [[module-core-designsystem]], [[module-core-ui]], [[module-core-permissions]], [[module-core-testing]], [[module-feature-library]], [[module-feature-search]], [[module-feature-player]], [[module-feature-equalizer]], [[module-feature-playlists]], [[module-feature-tageditor]], [[module-feature-settings]], [[module-feature-widget]], [[module-build-logic]], [[module-baselineprofile]], [[agent-module-ownership]]
