---
type: hub
status: active
date: 2026-11-13
source: "core/datastore/*Impl.kt, app/build.gradle.kts, gradle.properties, .obsidian/ (read 2026-11-13)"
tags: [operations, configuration]
related: ["[[00 Home]]", "[[Local Setup]]", "[[Deployment]]", "[[core-datastore]]", "[[Settings]]"]
---

# Configuration

Every setting the app or the build reads. Secrets are named here, never stored: the vault holds no passwords.

## App settings (DataStore `settings`, [[core-datastore]])
| Key | Type | Default | Purpose | Secret |
|---|---|---|---|---|
| `theme_mode` | string | system | light / dark / system ([[Settings]]) | no |
| `use_dynamic_color` | boolean | false | opt-in dynamic colour; the accent stays brand blue by default | no |
| `playback_speed` | float | 1.0 | applied by `PlaybackSettingsBinder` | no |
| `skip_silence` | boolean | false | applied by `PlaybackSettingsBinder` | no |
| `eq_enabled` | boolean | false | equaliser on/off ([[Equaliser]]) | no |
| `eq_gains` | string | flat | current band gains; named presets are in Room ([[EqualizerPreset]]) | no |
| `recent_search_queries` | string | empty | recent searches ([[Search]]) | no |

Everything else the app stores is in Room ([[Database Schema]]) or the artwork cache (`cacheDir/song_art`,
[[Per-track artwork]]). There is no remote config.

## Build
| Name | Where | Default | Purpose | Secret |
|---|---|---|---|---|
| `RELEASE_STORE_FILE` | `local.properties` or env | unset | release keystore, repo-root relative | path only |
| `RELEASE_STORE_PASSWORD` | `local.properties` or env | unset | keystore password | **yes** |
| `RELEASE_KEY_ALIAS` | `local.properties` or env | unset | key alias | no |
| `RELEASE_KEY_PASSWORD` | `local.properties` or env | unset | key password | **yes** |
| `sdk.dir` | `local.properties` | — | Android SDK path | no |
| `org.gradle.jvmargs` | `gradle.properties` | `-Xmx4096m` | Gradle heap | no |
| `org.gradle.parallel`, `org.gradle.caching` | `gradle.properties` | true | build speed | no |
| `org.gradle.configuration-cache` | `gradle.properties` | false | | no |
| `ANDROID_SERIAL` | env | unset | target device for install / connected tests | no |
| `core.hooksPath` | git config | — | `config/git-hooks` | no |

Without the signing keys the release build is unsigned with a warning ([[Release ships unsigned without a signing config]]).
SDK levels and versions: [[Tech Stack]]. Static-analysis config: `config/detekt/`, `config/compose/`
([[Compose stability config location]]).

## Obsidian vault (`.obsidian/` at the repo root)
| Setting | Value |
|---|---|
| Attachments | `docs/99 Meta/Attachments` |
| Templates folder | `docs/99 Meta/Templates` |
| Daily notes | `docs/08 Project/Daily`, template `daily-note-template.md` |
| Excluded from search and graph | build output, code modules, `media player example/`, calibration screenshots |
| Karpathy LLM Wiki plugin | `wikiFolder: docs/wiki`; its `data.json` is git-ignored (can hold a provider key) |
