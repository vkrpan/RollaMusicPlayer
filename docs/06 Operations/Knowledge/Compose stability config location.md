---
type: knowledge
status: active
date: 2026-07-04
source: "build setup, 2026-07-04"
tags: [build, compose]
related: ["[[Knowledge]]", "[[Multi-module architecture]]"]
---

# Compose stability config location

The Compose stability config lives at `config/compose/stability-configuration.conf` (wildcard
`com.rolla.musicplayer.core.model.*`). The `rolla.android.library.compose` and `rolla.android.application` convention
plugins apply it centrally. Never add per-module `composeCompiler {}` blocks.
