# Media3 1.11.1 upgrade (toolchain, Kotlin 2.2, Media3) — design

**Status:** Approved by the user on 2026-10-14 (staged approach, target Media3 1.11.1 with Kotlin 2.2). Implemented:
Tasks 1-5 are done, and the device check passed on 2026-10-15.
**Branch:** `feat/oneui-redesign`, which is merged to `main` at the end of the redesign.
**Follows:** queue restore (`docs/superpowers/specs/2026-10-12-queue-restore-design.md`, commit f6c6778).
**Followed by:** System UI panel resume. That is a separate spec, written after this upgrade is accepted, and it builds
on 1.11.1's `onPlaybackResumption` "for playback" parameter.

## 1. Goal

Move the app from Media3 1.4.1 to **Media3 1.11.1**, the latest stable release (2026-09-10). Upgrade the toolchain it
requires, and change no user-visible behaviour except where a Media3 change forces one. This spec names each such case
in §6.

## 2. Success criteria

1. Every Media3 module in the catalog (`media3-exoplayer`, `media3-session`, `media3-ui`) is on 1.11.1.
2. `./gradlew check assembleDebug` is green: unit tests, detekt, Spotless and lint. `:app:assembleBenchmark` builds,
   signed with the debug certificate.
3. On the SM-S901B, `:core:database:connectedDebugAndroidTest` passes. The database stays at **version 5**, and
   `schemas/.../5.json` is byte-identical.
4. An upgrade install over the current app keeps every piece of user data (playlists, favourites, play counts,
   Equaliser presets, settings, the saved queue), and `firstInstallTime` does not change.
5. Today's device regression passes on the new build (§8.2), including every queue-restore check from 2026-10-14.
6. Every queue-restore ruling that depends on Media3 1.4.1 internals has been re-checked against the 1.11.1 sources. The
   ledger marks each one kept, changed or retired (§6).

## 3. Non-goals

- The System UI panel resume. Its spec comes next.
- `targetSdk` stays **34** and `minSdk` stays **24**. Raising `targetSdk` changes runtime behaviour, so it is its own
  project.
- AGP 9.x. It is a major release with built-in Kotlin and DSL changes; this project uses the newest **8.x**.
- Kotlin newer than 2.2.x. 2.2 is the floor Media3 1.11.1 needs.
- Upgrading libraries that the targets above don't force, such as the Compose BOM, Coil, Glance, DataStore and
  Navigation. If a forced change requires one, that is recorded as a ruling.
- Pre-release (alpha, beta, RC) dependencies. If a stage can only be finished with one, the controller stops and asks
  the user.

## 4. Version targets

Verified on Google Maven and Maven Central on 2026-10-14. The plan re-checks each pin before use.

| Item | Now | Target | Why |
|---|---|---|---|
| Media3 (exoplayer, session, ui) | 1.4.1 | **1.11.1** | The goal. The AAR metadata says `minCompileSdk=36`, and `media3-session` 1.11.1 depends on `kotlin-stdlib` 2.2.10 |
| `compileSdk` | 34 | **36** | Media3 1.10+ `minCompileSdk` |
| AGP | 8.5.2 | **8.13.x** (newest 8.x, 8.13.2 on the check date) | Needed for `compileSdk 36`; stays on 8.x |
| Gradle wrapper | 8.9 | The minimum AGP 8.13 requires (the plan reads it from AGP's compatibility table) | Needed by AGP |
| Kotlin (and the compose and serialization plugins) | 2.0.21 | **2.2.21** | A 2.0 compiler can't read 2.2 stdlib metadata |
| KSP | 2.0.21-1.0.27 | **2.2.21-2.0.5** | Must match Kotlin. KSP2 |
| Room | 2.6.1 | **2.8.x** (newest stable, 2.8.5 on the check date) | Room's KSP2 support starts at 2.7. The schema must not change (§5) |
| Hilt | 2.52 | Newest stable that supports KSP2 and Kotlin 2.2 metadata (the plan pins it from the release notes) | KSP2 |
| detekt | 1.23.6 | **1.23.8**, keeping the current config | detekt 2.x is only alpha (non-goal). If 1.23.x can't run beside Kotlin 2.2, the plan uses detekt's documented Kotlin pin on its own configuration |
| Baseline-profile plugin, benchmark-macro | 1.3.x | The version matching AGP 8.13 | Plugin compatibility |
| kotlinx-coroutines | 1.8.1 | Whatever Room and Hilt resolve to, aligned in the catalog | Transitive. The test-timing rules (`runCurrent` and `advanceUntilIdle`) are re-checked |
| Robolectric | 4.13 | Unchanged unless a forced change requires otherwise | It runs tests at `targetSdk` 34 |

## 5. Stages

Each stage ends with `./gradlew check assembleDebug` green and its own review. A stage never carries a red build into
the next one.

1. **Platform.** AGP 8.13.x, the Gradle wrapper, `compileSdk 36` (catalog and both convention plugins), and the
   baseline-profile plugin. Fix new lint errors and handle deprecations without changing behaviour.
2. **Kotlin.**
   - Kotlin 2.2.21, KSP 2.2.21-2.0.5, Room 2.8.x and Hilt.
   - Keep detekt and Spotless working. Fix compile warnings that the new compiler turns into errors.
   - **Room invariant:** the database version stays 5, the exported `5.json` stays byte-identical (diffed), and
     `ALL_MIGRATIONS` is unchanged. A generated-code change that would alter the schema is a stop-and-ask.
3. **Media3.**
   - All Media3 modules move to 1.11.1. Fix compile and test failures.
   - `MediaSession.Callback.onPlaybackResumption` gains its "for playback" parameter. In this project our override
     takes it and keeps today's behaviour. The panel-resume spec gives it meaning.
   - Any test whose expected values change because Media3's behaviour changed is a ruling, not a quiet edit.
4. **Re-verify Media3-dependent rulings** (§6) against the 1.11.1 sources, extracted to the scratchpad as 1.4.1 was.
   - Each ruling is kept (its source fact still holds, cited at file:line), changed (code plus a test, with a mutation
     proof), or retired (Media3 now covers it, which a test or a source citation shows).
   - The baseline profile is not regenerated here. That happens at release, as before.
5. **Device check** (§8.2).

## 6. Media3-dependent rulings to re-verify

These were decided against Media3 1.4.1 behaviour. Known 1.5–1.11 changes that touch them are noted.

| Ruling | 1.4.1 fact it rests on | Changes in 1.5–1.11 to check against |
|---|---|---|
| R1 (Play on an empty player goes to `onPlaybackResumption`) | `MediaSessionImpl.handleMediaControllerPlayRequest` | 1.9: the new parameter. 1.11: stricter threading, and a read-only default `onConnect` for untrusted controllers (our app and widget controllers are in-process, so trusted since 1.9) |
| R3, `RestoredShuffleOrder` (arm, then set items, applying on `PLAYLIST_CHANGED`) | `setShuffleOrder` fires `onTimelineChanged(PLAYLIST_CHANGED)` and no transition; `ListenerSet` order | ExoPlayer timeline and event ordering |
| P2 (queue written only on `PLAYLIST_CHANGED`) | `SOURCE_UPDATE` fires on every item preparation | Timeline change reasons |
| P6, `MediaButtonStartFallback` (media-button start with nothing to resume) | `MediaButtonReceiver` starts a foreground service for PLAY, PLAY_PAUSE and HEADSETHOOK; no notification for an empty timeline | 1.6: `shouldStartForegroundService(Context)`. 1.9.1: FGS-not-allowed handling. 1.10: **stale foreground-start intents are detected** (which may retire part of P6 and I2). 1.11: `stopSelfSafely` |
| I2 (key UP starts the service; Media3 acts only on DOWN) | `MediaButtonReceiver.onReceive` filters by key code only; `onMediaButtonEvent` drops non-DOWN | Receiver and dispatch changes |
| P8 / D1 (STOP from the notification's deleteIntent restarts the service; stop it after the restore settles) | `DefaultMediaNotificationProvider` deleteIntent is COMMAND_STOP via `getService` | 1.6: notification kept on stop and error, and the **FGS kept 10 min after pause**. 1.8: **no automatic notification for an idle player that was never prepared or played** (`setShowNotificationForIdlePlayer`). 1.10: `ActionFactory` int commands |
| `onTaskRemoved` (swipe-away stops only when not playing) | `MediaSessionService.onTaskRemoved` | 1.5: a default implementation with `isPlaybackOngoing`. 1.6: `pause()` no longer stops the FGS (`pauseAllPlayersAndStopSelf`) |
| Restore shows a paused notification (declined-to-judge item) | 1.4.1 posts a notification for any non-empty timeline | 1.8 idle-player rule. Our restore prepares, so check what shows |
| `PlaybackService` lifecycle | `MediaSessionService` extends `Service` | 1.10: it extends `LifecycleService` (check the `onStartCommand` and `onDestroy` overrides and `super` calls) |

Every change found here goes in the ledger with its cost if wrong, as before. If one changes what the user sees, the
final report lists it.

## 7. Error handling and safety

- **No data path changes.** Room 2.8 runs the same version-5 schema and the same migrations. `MigrationTest` and
  `PlaybackQueueDaoTest` run on the device.
- **No new permissions.** `checkReleaseManifestNoInternet` stays green, and the merged manifest is diffed against
  today's. Any new permission or component from a library is a stop-and-ask.
- **R8:** the benchmark (release-like) build must start and play. The keep rules are re-checked for Media3, Room 2.8
  and Hilt.

## 8. Testing

### 8.1 JVM and static

- The full existing suite stays green, with the mutation-proof policy applied to every test this project adds or
  changes. A test edited only to match new Media3 behaviour names the behaviour change and its source.
- New or changed code from §6 gets tests that can fail: a named mutation, RED, then a byte-exact revert.
- The `5.json` diff is empty. The merged-manifest diff is reviewed.

### 8.2 Device check (SM-S901B; safety rules as in queue restore: no uninstall, no data clear, no settings changes)

1. `:core:database:connectedDebugAndroidTest` passes.
2. Benchmark build, certificate check, `install -r`, speed-profile compile, `firstInstallTime` unchanged; Playlists,
   Folders, favourites and Equaliser presets unchanged; crash buffer empty.
3. Playback: play, pause, next, previous, seek, shuffle, repeat. The notification controls work.
4. The widget, if one is placed (otherwise recorded as not exercised); the Equaliser applies; the speed setting
   applies.
5. Queue restore:
   - Kill while paused, then reopen: restored and prepared, with the real duration.
   - Force-stop while playing: within 10 s.
   - Swipe-away while playing keeps playing.
   - With our app as the last media-button receiver (checked first), kill, then headset Play: resumes without the
     activity, in the saved order.
   - The D1 loop watch: 30 s of logcat after a kill and after a close.
6. Any §6 ruling marked changed or retired gets its own device check where the device can show it.

## 9. Risks

| Risk | Mitigation |
|---|---|
| Room 2.8 generated code differs in behaviour | The schema diff, the device DAO and migration tests, and the JVM repository tests |
| detekt 1.23 can't run beside Kotlin 2.2 | detekt's documented Kotlin pin on the detekt configuration; stop and ask if that fails |
| Media3's foreground-service lifecycle changes break the headset fallback or D1 | §6 re-verification plus the device checks |
| Robolectric tests with a real ExoPlayer change timing | Fix the tests, with rulings; production code changes only for real behaviour changes |
| A forced library bump cascades further | Each extra bump is a ledger ruling. A pre-release or a `minSdk` raise is a stop-and-ask |

## 10. Docs

- `CLAUDE.md`:
  - Tech-stack lines: Media3 1.11.1, Kotlin 2.2, `compileSdk 36`.
  - The queue-restore banner: any §6 ruling that changed.
  - The Notes line, "Target Android API: 24 minimum, 34 target", stays and gains "compile 36".
- `.claude/rules/media3-playback.md`: unchanged unless an example uses a removed API.
- The queue-restore spec gets a short amendment for each ruling that §6 changed or retired.
