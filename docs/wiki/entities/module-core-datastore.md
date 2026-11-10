---
type: entity
summary: "DataStore: app settings, equaliser active-state and recent searches."
aliases: [":core:datastore"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :core:datastore

DataStore: app settings, equaliser active-state and recent searches.

Preferences DataStore for app settings (theme, dynamic colour, speed, skip silence), the equaliser's active state and
recent searches. Its Hilt `PreferencesModule` lives here.

- **Owner:** data-layer-agent ([[agent-module-ownership]]).
- Part of the [[offline-privacy-architecture]]: settings never leave the device.

## Related
[[claude-md]], [[ownership-map]], [[agent-module-ownership]], [[offline-privacy-architecture]]
