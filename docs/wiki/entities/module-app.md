---
type: entity
summary: "App shell: Application, MainActivity, the NavHost, the DI root and the Home screen."
aliases: [":app"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :app

App shell: Application, MainActivity, the NavHost, the DI root and the Home screen.

The shell that wires everything: `RollaApp` (Hilt root, Coil `ImageLoader` with the [[per-track-artwork]]
interceptor), `MainActivity` (edge-to-edge, theme entry), the navigation graph and the Home screen with its six tabs.

- **Depends on:** every `:core:*` and `:feature:*` module; nothing depends on it ([[multi-module-architecture]]).
- **Owners:** split. [[agent-module-ownership]] gives `RollaApp.kt` to build-tooling-agent, `MainActivity.kt` to
  ui-builder and `navigation/**` to navigation-agent; the manifest is split by section.
- **Routes:** type-safe `@Serializable` routes in `navigation/Routes.kt`; `Home` is the start destination.

## Related
[[claude-md]], [[ownership-map]], [[per-track-artwork]], [[multi-module-architecture]], [[agent-module-ownership]], [[module-core-database]], [[module-core-datastore]]

## Disagreements & open questions
- [[ownership-map]] lists `:app/.../di/AppModule.kt` and `:app/.../di/**` (DatabaseModule, MediaModule,
  ImageModule, PreferencesModule). The code has no `di/` package in `:app`: `DatabaseModule` lives in
  [[module-core-database]], `PreferencesModule` in [[module-core-datastore]], there is no `MediaModule` or `ImageModule`, and Coil
  is configured in `RollaApp`. Code wins; the map's DI rows are stale.
