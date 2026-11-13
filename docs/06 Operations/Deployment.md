---
type: hub
status: active
date: 2026-11-13
tags: [operations, release, deployment]
related: ["[[00 Home]]", "[[Configuration]]", "[[v1.0 release]]", "[[Release ships unsigned without a signing config]]", "[[Release build fails on INTERNET permission]]", "[[build-tooling-agent]]"]
---

# Deployment

There is no server to deploy. "Deployment" is building an APK and installing it; distribution so far is a sideloaded,
release-signed APK. The full checklist is the `release-build` skill (`.claude/skills/release-build/SKILL.md`,
[[Project skills]]); owner: [[build-tooling-agent]].

## Build types
| Type | Use | Signing | Minified |
|---|---|---|---|
| `debug` | development, Robolectric and connected tests | debug key | no |
| `benchmark` | release-like checks, motion verdicts, Macrobenchmark ([[Judge motion on a release-like build]]) | debug key | yes |
| `release` | shipping | release keystore if configured, else unsigned with a warning | yes (R8 + `shrinkResources`) |

## Steps
1. Branch is green: `./gradlew check` ([[Testing]]) and the connected Room tests on the Motorola.
2. Bump `versionCode` / `versionName` in `RollaAndroidApplicationPlugin` (`build-logic`).
3. Regenerate the Baseline Profile when the code changed a lot (`:baselineprofile`, [[baselineprofile]]).
4. `./gradlew assembleRelease` with the signing keys from [[Configuration]].
   `checkReleaseManifestNoInternet` fails the build if any dependency pulls in `INTERNET`.
5. Install on the test device and walk the release ([[Device safety]]).
6. Merge into `main`, tag `vX.Y.Z`, push; add the version to [[Changelog]] and [[Timeline]]. Commits and pushes happen
   only when the user asks ([[Commits are user-triggered]]).

## Environments
One: the user's device. No staging, no store listing yet.

## Rollback
- **Code:** revert on `main` and re-tag; the previous tag (`v1.0.0`) stays buildable.
- **Installed app:** prefer shipping a fixed build forward. Room has migrations only upward and `DatabaseModule` sets
  no downgrade fallback, so an older APK cannot open a database a newer one migrated; going back means uninstalling,
  which deletes playlists, favourites, play counts and the saved queue. Custom tag assignments survive in the files'
  `ROLLA_TAGS` ([[ROLLA_TAGS field]]); the tag names survive only if the list was exported ([[Tag list file]]).
