# Settings Configuration Guide

This document describes the structure for `.claude/settings.json`, to be created when the project moves to the implementation phase.

## Purpose

The settings.json file configures:
- Which agents are available for specialized tasks
- Which skills can be invoked for complex workflows
- Which rules are enforced during development
- Project-specific permissions and hooks

## 🔒 Offline Configuration Note

**RollaMusicPlayer is a fully offline application.** The configuration reflects this:
- No network-related settings or configurations
- No API endpoints or cloud service configurations
- No analytics or tracking settings
- All configurations are for local development and offline operation

## Structure Overview

```json
{
  "projectName": "RollaMusicPlayer",
  "version": "1.0.0",
  "agents": {
    "default": "code-reviewer",
    "available": [
      "audio-engineer",
      "equalizer-agent",
      "data-layer-agent",
      "media-scanning-agent",
      "tag-editor-agent",
      "ui-builder",
      "m3-design-system-agent",
      "compose-animation-agent",
      "compose-performance-auditor",
      "viewmodel-architect",
      "navigation-agent",
      "permissions-agent",
      "widget-agent",
      "build-tooling-agent",
      "code-reviewer",
      "test-writer"
    ]
  },
  "skills": {
    "autoLoad": [
      "setup-modularization",
      "add-new-screen",
      "add-room-database",
      "add-dependency-injection-hilt",
      "implement-repository-pattern",
      "implement-use-cases",
      "implement-state-management",
      "implement-navigation-graph",
      "implement-datastore",
      "handle-runtime-permissions",
      "implement-media-scanning",
      "implement-equalizer",
      "implement-tag-editor",
      "implement-home-widget",
      "generate-baseline-profile",
      "implement-image-loading-coil",
      "implement-edge-to-edge-and-insets",
      "add-animations-transitions",
      "setup-static-analysis",
      "add-unit-testing",
      "add-ui-testing-compose",
      "debug-playback-issue",
      "release-build"
    ]
  },
  "rules": {
    "enforced": [
      "kotlin-style",
      "compose-conventions",
      "media3-playback",
      "navigation-conventions",
      "model-vocabulary"
    ]
  }
}
```

## Key Concepts

### Agents
Specialized sub-agents with domain expertise. Several are **exclusive owners** of a module/package to prevent concurrent-edit conflicts. See `OWNERSHIP.md` (CODEOWNERS-style) for the authoritative module-path ownership map.

**Audio & Playback**
- **audio-engineer**: `:core:media` — ExoPlayer, MediaSession, foreground service, audio focus, gapless playback. Exposes the audio session id for the equalizer.
- **equalizer-agent**: `:core:media` equalizer effect — band mapping, gains, presets.

**Data & Library**
- **data-layer-agent**: `:core:database` (+ repositories in `:core:data`) — entities, DAOs, migrations.
- **media-scanning-agent**: `:core:data` scanner — MediaStore querying and incremental Room indexing.
- **tag-editor-agent**: `:feature:tageditor` — ID3/metadata read-write with scoped-storage consent.

**UI & Design**
- **ui-builder**: `:core:ui` + `:feature:*` screens.
- **m3-design-system-agent**: `:core:designsystem` (exclusive) — colors, typography, shapes.
- **compose-animation-agent**: animations across features and `:core:ui`.
- **compose-performance-auditor**: read-only recomposition/jank analysis.

**Architecture & Navigation**
- **viewmodel-architect**: ViewModels within `:feature:*`.
- **navigation-agent**: `:app` NavHost + per-feature nav entries; type-safe `@Serializable` routes.

**Platform Features**
- **permissions-agent**: `:core:permissions` (exclusive).
- **widget-agent**: `:feature:widget` (exclusive) — Glance home screen widget.

**Build & Tooling**
- **build-tooling-agent**: `build-logic/`, root Gradle, `libs.versions.toml`, R8 rules, `:baselineprofile`, static-analysis/CI. The build-level gate for the no-network-dependency rule.

**Quality**
- **code-reviewer**: read-only review for MVVM compliance, offline/privacy, memory leaks, performance.
- **test-writer**: `:core:testing` + tests across modules.

### Skills
Multi-step workflows for common tasks, grouped by area.

**Architecture & Setup**: setup-modularization, add-new-screen, add-room-database, add-dependency-injection-hilt, implement-repository-pattern, implement-use-cases, implement-state-management, implement-navigation-graph, implement-datastore

**Feature Implementation**: handle-runtime-permissions, implement-media-scanning, implement-equalizer, implement-tag-editor, implement-home-widget

**Performance & UI Polish**: generate-baseline-profile, implement-image-loading-coil, implement-edge-to-edge-and-insets, add-animations-transitions

**Build, Testing & Ops**: setup-static-analysis, add-unit-testing, add-ui-testing-compose, debug-playback-issue, release-build

### Rules
Coding standards and conventions:
- **kotlin-style**: Kotlin coding conventions and offline checklist
- **compose-conventions**: Jetpack Compose best practices
- **media3-playback**: Media3 ExoPlayer patterns
- **navigation-conventions**: type-safe `@Serializable` route contract
- **model-vocabulary**: canonical domain model names (the codebase uses `Song`, never `Track`) and the framework terms that are NOT models

## Usage

This file will be created during the implementation phase to configure Claude's behavior for the RollaMusicPlayer project. The agents, skills, and rules defined here guide development and ensure consistency.

## Offline Development Guidelines

When implementing this configuration:
- Ensure no network-dependent tools or settings
- All file paths reference local storage only
- No external service integrations
- Focus on local development workflow
- Privacy-preserving configurations only
- The build-tooling-agent's `checkNoNetwork` gate enforces the no-network rule at build time
