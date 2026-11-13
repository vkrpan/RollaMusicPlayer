---
type: hub
status: active
date: 2026-11-13
tags: [operations, setup]
related: ["[[00 Home]]", "[[Configuration]]", "[[Testing]]", "[[Troubleshooting]]", "[[Motorola edge 70]]"]
---

# Local Setup

## Prerequisites
- JDK 17 (the build targets Java 17).
- Android SDK with platform 36 (compileSdk); Android Studio optional.
- Windows checkout: Git Bash for the shell snippets; files are CRLF ([[Spotless and line endings]]).
- No network services, accounts or keys are needed. A release keystore is optional ([[Deployment]]).

## Steps
```bash
git clone https://github.com/vkrpan/RollaMusicPlayer.git
cd RollaMusicPlayer
git config core.hooksPath config/git-hooks      # pre-commit: spotlessCheck, detekt, checkNoNetwork
./gradlew assembleDebug                          # debug APK
./gradlew :app:installDebug                      # or installBenchmark for a release-like build
```

`local.properties` needs `sdk.dir`; on Windows escape the colon: `sdk.dir=C\:/Users/…/Android/Sdk`
([[AGP 8.13 lint gotchas]]).

## Verify
- `./gradlew check` passes: unit tests, detekt, Spotless, `checkNoNetwork`, `checkNoMaterialIcons` ([[Testing]]).
- On a device, grant the audio permission; the library scan fills the Tracks tab and a tap plays a track.
- Device work goes to the Motorola only ([[Device tests run on the Motorola]], [[Device safety]]):
  `ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :app:installBenchmark`. adb tips: [[adb on the Motorola]].

## Obsidian
Open the **repository root** as the vault (`.obsidian/` lives there). Notes are in `docs/`; code folders are excluded
from search and graph. Entry note: [[00 Home]]. Conventions: [[Vault conventions]].
