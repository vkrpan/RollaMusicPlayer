---
type: decision
adr: "ADR-010"
status: active
date: 2026-07-23
source: "v1 Phase 8 release build; CLAUDE.md shipped table"
tags: [release, build, signing]
related: ["[[Decisions]]", "[[Release build fails on INTERNET permission]]", "[[build-tooling-agent]]"]
---

# Release ships unsigned without a signing config

**Decision:**
- The release build is R8-minified with `shrinkResources`. The keep rules are verified for kotlinx-serialization
  routes, jaudiotagger reflection, Media3, Glance receivers, Room and Hilt.
- Signing reads a local keystore from `local.properties` or environment variables, never committed.
- With no signing config, the release builds **unsigned, with a warning**. It never falls back silently to debug
  signing.
- v1.0.0 is `versionCode 1` / `versionName "1.0.0"`.

**Why:** a silently debug-signed "release" can't be told apart from a real one, and secrets must never enter the repo.

**Note:** the user's Motorola holds the release-signed v1.0, sideloaded. A debug or benchmark build can't install
over it without an uninstall, which wipes data. See [[Motorola edge 70]].
