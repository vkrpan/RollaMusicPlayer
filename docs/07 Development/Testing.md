---
type: hub
status: active
date: 2026-11-13
tags: [development, testing]
related: ["[[00 Home]]", "[[Conventions]]", "[[test-writer]]", "[[core-testing]]", "[[Troubleshooting]]"]
---

# Testing

Owner agent: [[test-writer]]. Skills: `add-unit-testing`, `add-ui-testing-compose`, `generate-baseline-profile`
([[Project skills]]).

## How to run
```bash
./gradlew check                                   # JVM unit + Robolectric tests, detekt, Spotless, checkNoNetwork
./gradlew :feature:search:testDebugUnitTest       # one module
ANDROID_SERIAL=ZY22MBNVB4 ./gradlew :core:database:connectedDebugAndroidTest   # Room + migrations on the Motorola
./gradlew :baselineprofile:pixel6Api31FramesBenchmarkReleaseAndroidTest   # scroll / startup benchmarks (managed emulator)
```
Device runs follow [[Device safety]] and [[Device tests run on the Motorola]].

## What is covered
| Kind | Where | Tools |
|---|---|---|
| ViewModels, repositories, mappers, codecs | `src/test` in every module | JUnit 4, MockK, Turbine, coroutines-test, `MainDispatcherRule` |
| Compose screens and kit components (incl. geometry and pixels) | `src/test` with Robolectric `sdk=34` | Compose UI test, `@GraphicsMode(NATIVE)`, `PixelCapture` |
| Real ExoPlayer behaviour (queue, shuffle, restore) | [[core-media]] tests | Robolectric, a seeded shuffle |
| Tag IO on real formats | [[core-tagio]], [[feature-tageditor]] | jaudiotagger on minimal MP3/FLAC/M4A streams built in code (`AudioFixtures`, `M4aFixture`) |
| Room DAOs and every migration from v1 | `core/database/src/androidTest` (`MigrationTest`) | on the device |
| Smoke tests of screens | `androidTest` in `core:ui`, `feature:library`, `player`, `playlists`, `search` | on the device |
| Startup and scroll performance, the Baseline Profile | [[baselineprofile]] | Macrobenchmark, `MediaStoreSeeder` |
| Contrast of every token pairing | `ContrastTest` in [[core-designsystem]] | WCAG AA ratios |

Fakes for repositories and DAOs live in [[core-testing]] (`FakeSongRepository`, `FakeCustomTagDao`, …).

## What is not covered
- Tag writes on the user's real files (never done by Claude; [[Device safety]]).
- Drag gestures through adb (long-press drags cannot be injected: [[adb on the Motorola]]); the user walks them.
- The System UI resume card on stock Android (not yet device-verified: [[Panel resume targets stock Android]]).
- Known gaps per feature are in [[Known Issues]] and [[Backlog]].

## Practice
- A test counts only when it was seen failing for the right reason (mutation runs per task; quote the FAILED line:
  [[Stale test XML fakes a RED]]).
- Robolectric traps: [[Robolectric screens and pixel tests]], [[Robolectric looper, dialogs and debounce]],
  [[Compose test geometry traps]], [[Compose text overflow and Robolectric SDK in tests]],
  [[Seed Media3 shuffle in tests]].
- Motion is judged on a release-like build, never in tests alone ([[Judge motion on a release-like build]]).
