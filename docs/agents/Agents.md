---
type: index
tags: [index, agents]
related: ["[[Home]]", "[[Ownership map]]", "[[Subagents run on Opus]]"]
---

# Agents

The project's subagents, defined in `.claude/agents/` (the definitions stay there; the harness loads them). Path-level
ownership and required reviewers: [[Ownership map]]. Every dispatch uses `model: opus` ([[Subagents run on Opus]]).
Plugin agents used alongside them: general-purpose (builder checks, reviews), Explore, the superpowers code reviewer.

| Agent | Modules |
|---|---|
| [[audio-engineer]] | [[core-media]] |
| [[build-tooling-agent]] | [[build-logic]], [[baselineprofile]], [[app]] |
| [[code-reviewer]] | [[core-model]], [[core-common]] |
| [[compose-animation-agent]] | [[feature-player]] |
| [[compose-performance-auditor]] | — |
| [[data-layer-agent]] | [[core-database]], [[core-datastore]], [[core-data]] |
| [[equalizer-agent]] | [[core-media]], [[feature-equalizer]] |
| [[m3-design-system-agent]] | [[core-designsystem]] |
| [[media-scanning-agent]] | [[core-data]] |
| [[navigation-agent]] | [[app]] |
| [[permissions-agent]] | [[core-permissions]] |
| [[tag-editor-agent]] | [[feature-tageditor]] |
| [[test-writer]] | [[core-testing]] |
| [[ui-builder]] | [[core-ui]], [[feature-library]], [[feature-search]], [[feature-player]], [[feature-playlists]], [[feature-settings]], [[feature-tageditor]], [[app]] |
| [[viewmodel-architect]] | [[feature-library]], [[feature-search]], [[feature-player]], [[feature-playlists]], [[feature-settings]] |
| [[widget-agent]] | [[feature-widget]] |
