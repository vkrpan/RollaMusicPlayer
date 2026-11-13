---
type: hub
status: active
date: 2026-11-13
tags: [development, conventions]
related: ["[[00 Home]]", "[[Guides]]", "[[Rules]]", "[[Testing]]"]
---

# Conventions

A map, not a copy: the binding text lives in `.claude/rules/` ([[Guides]]) and the user's standing rules in
[[Rules]].

## Naming
- The audio item is `Song` in code, "track" on screen ([[Song in code, Tracks on screen]], [[Model vocabulary]]).
- Kotlin naming, formatting (4 spaces, 120 columns), null safety, coroutines: [[Kotlin style]].
- Composables: `XxxScreen` / `XxxRoute`, stateless content, `modifier` parameter, `PreviewXxx`:
  [[Compose conventions]].
- UI copy: "Rolla Music", "Equaliser", British spelling ([[UI copy and spelling]]).

## Code layout
- One Gradle module per concern; `:feature → :core → :core:model` ([[Module dependency direction]], [[Modules]]).
- Each module has an owning agent; edits go through it ([[Ownership map]], [[Agents]]).
- UI only from tokens and the One UI kit, icons only from `RollaIcons` ([[Build UI from tokens and the kit]],
  [[UI style guide]]).
- Navigation: type-safe routes only ([[Navigation conventions]], [[Navigation routes]]).
- Playback: [[Media3 playback]].
- No network code, ever ([[Offline and privacy first]]); every control must do something real
  ([[No decorative toggles]]).

## Enforcement
- `./gradlew check` and the pre-commit hook (`config/git-hooks/pre-commit`: `spotlessCheck`, `detekt`,
  `checkNoNetwork`); `checkNoMaterialIcons` runs with detekt ([[Testing]]).
- detekt thresholds are tight ([[Detekt thresholds and BOMs]]); Spotless enforces CRLF ([[Spotless and line endings]]).
- Edit files with the Write/Edit tools, never `sed -i` ([[Edit files with Write and Edit, not the shell]]).

## Branches and commits
- `main` holds releases; work happens on a feature branch (now `feat/oneui-redesign`) that the user merges.
- Commit messages: `type(scope): summary` (`feat(tags): …`, `fix(…)`, `docs(…)`), ending with the Co-Authored-By line.
- Commits and pushes only when the user asks ([[Commits are user-triggered]]), staged with
  [[Commit staging excludes local artefacts]].

## Review
- Every plan task gets a spec-compliance and a code-quality review; a phase gets a final review before the user's
  device walk ([[Spec wins over plan]], [[Ledgers]]).
- `code-reviewer` checks MVVM, offline, leaks and performance ([[code-reviewer]]).
- Docs are part of the change: [[Keep docs honest]], [[Record knowledge in the vault]],
  [[Document the project top-down]].
