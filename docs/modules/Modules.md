---
type: index
tags: [index, modules]
related: ["[[Home]]", "[[Multi-module architecture]]", "[[Module dependency direction]]", "[[Ownership map]]"]
---

# Modules

Dependency direction: `:feature → :core → :core:model` ([[Module dependency direction]]). Full path ownership:
[[Ownership map]].

| Note | Gradle | Owner agent(s) |
|---|---|---|
| [[app]] | `:app` | [[navigation-agent]], [[build-tooling-agent]], [[ui-builder]] |
| [[core-model]] | `:core:model` | [[code-reviewer]] |
| [[core-common]] | `:core:common` | [[code-reviewer]] |
| [[core-database]] | `:core:database` | [[data-layer-agent]] |
| [[core-datastore]] | `:core:datastore` | [[data-layer-agent]] |
| [[core-data]] | `:core:data` | [[data-layer-agent]], [[media-scanning-agent]] |
| [[core-media]] | `:core:media` | [[audio-engineer]], [[equalizer-agent]] |
| [[core-designsystem]] | `:core:designsystem` | [[m3-design-system-agent]] |
| [[core-ui]] | `:core:ui` | [[ui-builder]] |
| [[core-permissions]] | `:core:permissions` | [[permissions-agent]] |
| [[core-testing]] | `:core:testing` | [[test-writer]] |
| [[core-tagio]] | `:core:tagio` | [[tag-editor-agent]] |
| [[feature-library]] | `:feature:library` | [[ui-builder]], [[viewmodel-architect]] |
| [[feature-search]] | `:feature:search` | [[ui-builder]], [[viewmodel-architect]] |
| [[feature-player]] | `:feature:player` | [[ui-builder]], [[viewmodel-architect]], [[compose-animation-agent]] |
| [[feature-equalizer]] | `:feature:equalizer` | [[equalizer-agent]], [[ui-builder]] |
| [[feature-playlists]] | `:feature:playlists` | [[ui-builder]], [[viewmodel-architect]] |
| [[feature-tageditor]] | `:feature:tageditor` | [[tag-editor-agent]], [[ui-builder]] |
| [[feature-settings]] | `:feature:settings` | [[ui-builder]], [[viewmodel-architect]] |
| [[feature-widget]] | `:feature:widget` | [[widget-agent]] |
| [[baselineprofile]] | `:baselineprofile` | [[build-tooling-agent]] |
| [[build-logic]] | `build-logic/` | [[build-tooling-agent]] |
