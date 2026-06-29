# Ownership Map (CODEOWNERS-style)

Purpose: give **every path exactly one primary owner**, and name **required reviewers** for the cross-cutting files that touch many domains — so nothing is in the "everyone edits it" or "no one owns it" state. Owners are the project's agents. This complements each agent's own scope and the module graph in `CLAUDE.md` / the `setup-modularization` skill.

## Principles

1. **One primary owner per path.** The primary makes and merges changes there.
2. **Cross-cutting files list required reviewers.** Files that span domains (manifest, app shell, shared model) can be *edited* by the primary but require sign-off from the listed reviewers for the parts they touch.
3. **Shared contracts are governed, not owned.** `:core:model` has no single editor; changes follow `model-vocabulary.md` and need code-reviewer + affected-consumer review.
4. **Features never reach across.** A feature module owner cannot edit another feature; cross-feature needs go through `:core:data` or navigation.

## Module → Primary Owner (authoritative path map)

This is also the up-to-date "owned files" mapping — it supersedes the older single-module `app/src/main/...` paths in individual agent files.

```
# ---- App shell ----
:app/                              app-shell  (see "Cross-cutting" below; NOT a single agent)
:app/.../navigation/**             navigation-agent      # NavHost + graph wiring
:app/.../di/AppModule.kt           build-tooling-agent    # generic app-level DI

# ---- Core ----
:core:model/**                     SHARED CONTRACT (governed — see below)
:core:common/**                    code-reviewer (light governance)
:core:database/**                  data-layer-agent
:core:datastore/**                 data-layer-agent
:core:data/** (repositories)       data-layer-agent
:core:data/**/scanner/**           media-scanning-agent
:core:media/** (playback)          audio-engineer
:core:media/**/equalizer/**        equalizer-agent
:core:designsystem/**              m3-design-system-agent
:core:ui/**                        ui-builder
:core:permissions/**               permissions-agent
:core:testing/**                   test-writer

# ---- Features ----
:feature:library/**                ui-builder + viewmodel-architect
:feature:search/**                 ui-builder + viewmodel-architect
:feature:player/**                 ui-builder + viewmodel-architect      # consumes :core:media
:feature:equalizer/**              ui-builder                            # screen only; effect is equalizer-agent in :core:media
:feature:playlists/**              ui-builder + viewmodel-architect
:feature:tageditor/**              tag-editor-agent                      # IO + consent; ui-builder for screen
:feature:settings/**               ui-builder + viewmodel-architect
:feature:widget/**                 widget-agent

# ---- Build & tooling ----
build-logic/**                     build-tooling-agent
gradle/libs.versions.toml          build-tooling-agent
settings.gradle.kts                build-tooling-agent
build.gradle.kts (root)            build-tooling-agent
**/proguard-rules.pro              build-tooling-agent
:baselineprofile/**                build-tooling-agent
config/detekt/**, .github/**       build-tooling-agent

# ---- Cross-feature concerns (review, not exclusive edit) ----
**/*Animation*, motion specs       compose-animation-agent  (reviewer)
recomposition/perf findings        compose-performance-auditor (read-only reviewer)
**                                 code-reviewer (read-only reviewer on every change)
```

## Cross-cutting files — explicit owners + reviewers

These are the files that were previously "no one's job." Each now has a primary and required reviewers.

| File / area | Primary owner | Required reviewers | Notes |
| --- | --- | --- | --- |
| `:app/RollaApp.kt` (Application: Hilt root, `ImageLoaderFactory`, startup init) | **build-tooling-agent** | audio-engineer (service init), permissions-agent, ui-builder (image loader) | Infra/wiring file. Each subsystem that adds init reviews its part. |
| `:app/MainActivity.kt` (`enableEdgeToEdge`, `setContent`, theme entry) | **ui-builder** | navigation-agent (hosts NavHost), m3-design-system-agent (theme) | Per `implement-edge-to-edge-and-insets`. |
| `:app/.../navigation/**` (NavHost, graph) | **navigation-agent** | feature owners (each registers a nav entry) | Features expose their own `NavGraphBuilder.xxx()`; navigation-agent composes them. |
| `:app/AndroidManifest.xml` | **split by section** | — | `<uses-permission>` → permissions-agent; `<service>` (playback) → audio-engineer; widget `<receiver>` → widget-agent; `<application>`/theme → ui-builder + build-tooling-agent. Edit only your section. |
| `:core:model/**` | **none — shared contract** | code-reviewer + every consuming agent | Governed by `model-vocabulary.md`. Adding/renaming a model is a deliberate, reviewed change. |
| `:app/.../di/**` (DI modules) | **subsystem agent** | build-tooling-agent | `DatabaseModule`→data-layer-agent, `MediaModule`→audio-engineer, `ImageModule`→ui-builder/build-tooling, `PreferencesModule`→data-layer-agent. |
| `CLAUDE.md`, `README.md`, this file | **whoever changes structure** | code-reviewer | Keep in sync when agents/skills/modules change. |

## The `:core:model` governance rule

`:core:model` is the most cross-cutting code in the app and intentionally has **no single owner**. Rules:
- Names must match `model-vocabulary.md` (the codebase uses **`Song`**, never `Track`).
- Any change (new model, new field, rename) requires review by **code-reviewer** plus the agents that consume it.
- No feature or core module redefines a model locally — import from `:core:model`.

## Recommended follow-up

If `:app` grows beyond shell wiring (multiple cross-feature flows, complex DI graphs), consider adding a dedicated **app-shell / integration agent** to own `:app` end-to-end. For now, the split above (build-tooling-agent for infra, ui-builder for MainActivity, navigation-agent for the graph) covers it without a new agent.

## Generating a real CODEOWNERS

When the repo is on GitHub, this maps directly to `.github/CODEOWNERS` once agents correspond to GitHub teams/users — e.g.:
```
/core/database/   @rolla/data-layer
/feature/widget/  @rolla/widget
/build-logic/     @rolla/build-tooling
```
build-tooling-agent owns generating and maintaining that file from this map.
