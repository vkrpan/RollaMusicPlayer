# Media3 1.11.1 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move RollaMusicPlayer from Media3 1.4.1 to 1.11.1, together with the toolchain it requires, without changing what the user sees.

**Architecture:** Five staged tasks. Each one leaves `./gradlew check assembleDebug` green. The tasks are: platform (AGP, Gradle, compileSdk), Kotlin 2.2 (with KSP2, Room and Hilt), the Media3 bump, a source re-check of every queue-restore ruling that rests on Media3 1.4.1 internals, and a device regression on the Samsung.

**Tech Stack:**
- Android Gradle Plugin 8.13.2 and Gradle 8.13
- Kotlin 2.2.21 and KSP 2.2.21-2.0.5
- Room 2.8.5 and Hilt 2.57.2
- Media3 1.11.1 and detekt 1.23.8
- Robolectric 4.13 (unchanged)

**Spec:** `docs/superpowers/specs/2026-10-14-media3-upgrade-design.md`. The queue-restore spec, `docs/superpowers/specs/2026-10-12-queue-restore-design.md`, holds the rulings that Task 4 re-checks.

## Global Constraints

- Target versions, verified on Google Maven and Maven Central on 2026-10-14:

  | Component | Version |
  |---|---|
  | Media3 (exoplayer, session, ui) | **1.11.1** |
  | `compileSdk` | **36** |
  | AGP | **8.13.2** |
  | Gradle | **8.13** |
  | Kotlin | **2.2.21** |
  | KSP | **2.2.21-2.0.5** |
  | Room | **2.8.5** |
  | Hilt | **2.57.2** (controller ruling: 2.59.x's Gradle plugin requires AGP 9) |
  | detekt | **1.23.8** |

- `targetSdk` stays **34** and `minSdk` stays **24**. Do not use AGP 9.x, Kotlin above 2.2.x, or any alpha, beta or RC dependency.
  - If a step cannot be finished without a pre-release dependency, a `minSdk` raise, or a change to the database schema, stop and report BLOCKED.
- Do not upgrade any library that these targets don't force: the Compose BOM, Coil, Glance, DataStore, Navigation and the like.
  - Each forced extra bump is reported as a concern, with its reason.
- **Database invariant:** the database stays at version 5, and `core/database/schemas/com.rolla.musicplayer.core.database.MusicDatabase/5.json` stays byte-identical. Check it with `git diff --exit-code` on that path.
- **Manifest invariant:** the merged release manifest gains no permission and no new exported component.
  - `checkReleaseManifestNoInternet` stays green.
  - Diff it against the baseline that Task 1 captures.
- Offline app: no network APIs and no analytics. Build-time downloads of Gradle artifacts are fine.
- **Agents never run `git commit`, `git add` or `git stash`.**
  - Never `sed -i` a source file: Spotless enforces CRLF. Use the editor.
  - Don't write Kotlin through Bash heredocs.
  - Run `./gradlew spotlessApply` after every edit.
- **Mutation-proof policy**, for every test that is added, or changed because behaviour changed:
  - Name the mutation that turns the test RED. Apply it and run it.
  - A verdict counts only with positive evidence that the build ran: the `BUILD` line plus the test's decisive failure line, quoted.
  - Revert byte-exact.
  - "No results" is an error, never a verdict.
- When a test changes only because Media3's behaviour changed, the report names the Media3 change: the release-notes version plus a source file and line.
- detekt thresholds are unchanged: `LongMethod` 30, `TooManyFunctions` 15/12, `LongParameterList` 6. Any `@Suppress` needs a one-line reason.
- kotlinx-coroutines-test 1.8.1: `advanceUntilIdle()` does not run `backgroundScope` work; use `runCurrent()`. Room 2.8.5 resolves coroutines 1.8.1, so this still holds.
- Use the Bash tool for Gradle: `./gradlew …` from the repo root. Use a 600000 ms timeout for `check`.

## Review Focus

1. **An upgrade install over a phone that has data.**
   - Room 2.8's generated code must open the existing version-5 database with no migration, and no row may be lost.
   - Pinned by: the `5.json` byte-identity check (Tasks 2 and 3), `MigrationTest` and `PlaybackQueueDaoTest` on the device, and the `firstInstallTime`/data checks in Task 5.
2. **The R8-minified benchmark build** with Kotlin 2.2, Hilt 2.57 and Media3 1.11 crashing at startup or at the first Play because a keep rule is missing.
   - Pinned by: Task 3 Step 6 (`:app:assembleBenchmark`) and Task 5 Step 3 (launch and play the benchmark build).
3. **A headset Play on a cold process under Media3's new foreground-service lifecycle.**
   - The changes are 1.6's 10-minute foreground-service hold, 1.10's stale-intent detection, and 1.11's `stopSelfSafely`. Together they may crash, double-start, or orphan the service.
   - Pinned by: Task 4 rows P6 and I2, and Task 5 Step 6.
4. **The notification after an in-app restore.**
   - Media3 1.8 no longer posts a notification for an idle player that was never prepared or played. Our restore prepares, but whether a paused notification still shows, and whether P8's deleteIntent STOP restart still happens, must be re-established.
   - Pinned by: Task 4 rows P8/D1 and "Restore notification", and Task 5 Step 6.
5. **Media3 1.11's stricter threading.**
   - `MediaSession` getters throw `IllegalStateException` off the application looper.
   - Any player or session access from `Dispatchers.Default` (`serviceScope`) would now crash.
   - Pinned by: Task 3 Step 4 (an audit of `serviceScope` blocks, plus a test for any access found).

---

### Task 1: Platform (AGP 8.13.2, Gradle 8.13, compileSdk 36)

**Files:**
- Modify: `gradle/libs.versions.toml` (`agp`, `compileSdk`)
- Modify: `gradle/wrapper/gradle-wrapper.properties` and `gradle/wrapper/gradle-wrapper.jar` (through the wrapper task)
- Modify: `build-logic/convention/src/main/kotlin/RollaAndroidLibraryPlugin.kt:16` and `RollaAndroidApplicationPlugin.kt:24` (`compileSdk = 34` → `36`)
- Possibly modify: `gradle/libs.versions.toml` (`benchmarkMacro`, `baselineprofile`). Only do this if Step 5 fails on the baseline-profile plugin.

**Interfaces:**
- Consumes: nothing.
- Produces: a green build on AGP 8.13.2 and compileSdk 36. It also produces `.superpowers/sdd/<plan workspace>/baseline-merged-manifest.xml`, which Tasks 2, 3 and 5 diff against.

- [ ] **Step 1: Capture the merged-manifest baseline (before any change)**

Run: `./gradlew :app:processReleaseMainManifest`
Then copy `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` to the plan workspace as `baseline-merged-manifest.xml`. If the path differs, locate it with `find app/build/intermediates -path '*release*' -name AndroidManifest.xml`, and report the path you used.

- [ ] **Step 2: Bump AGP and compileSdk**

In `gradle/libs.versions.toml`:
```toml
agp                = "8.13.2"
compileSdk         = "36"
```
In `RollaAndroidLibraryPlugin.kt` and `RollaAndroidApplicationPlugin.kt`, change `compileSdk = 34` to `compileSdk = 36`. Leave `targetSdk = 34` exactly as it is.

- [ ] **Step 3: Update the Gradle wrapper**

Run: `./gradlew wrapper --gradle-version 8.13 --distribution-type bin`, then run it a second time, `./gradlew wrapper --gradle-version 8.13`. The second run regenerates the wrapper jar with the new Gradle.
Expected: `gradle-wrapper.properties` has `distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip`.

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.
- If AGP rejects the `kotlinOptions` extension: it belongs to Task 2. If and only if Task 1 cannot build without it, move the migration code from Task 2 Step 3 into this task and report that.
- If the baseline-profile or benchmark plugin 1.3.3 fails with AGP 8.13, set both `benchmarkMacro` and `baselineprofile` to `1.4.1`, and report it as a forced bump.

- [ ] **Step 5: Full gate**

Run: `./gradlew check assembleDebug :app:assembleBenchmark --console=plain` (timeout 600000)
Expected: `BUILD SUCCESSFUL`.
- Lint errors that compileSdk 36 introduces are fixed in code, not baselined, unless the fix would change behaviour. In that case report them as a concern.
- Every test passes unchanged. A test that needs a change in this task is a concern to report, not something to edit quietly.

- [ ] **Step 6: Manifest invariant**

Run: `./gradlew :app:processReleaseMainManifest`, then diff the result against `baseline-merged-manifest.xml`.
Expected: no added `<uses-permission>`, and no new `exported="true"` component. Quote the diff (it may be empty) in the report.

---

### Task 2: Kotlin 2.2.21, KSP2, Room 2.8.5, Hilt 2.57.2, detekt 1.23.8

**Files:**
- Modify: `gradle/libs.versions.toml` (`kotlin`, `ksp`, `room`, `hilt`, `detekt`)
- Modify: `build-logic/convention/build.gradle.kts` (the hard-coded `detekt-gradle-plugin:1.23.6` → `1.23.8`)
- Modify: `build-logic/convention/src/main/kotlin/RollaAndroidLibraryPlugin.kt`, `RollaAndroidApplicationPlugin.kt` (`kotlinOptions` → `compilerOptions`)
- Modify: `build-logic/convention/src/main/kotlin/RollaStaticAnalysisPlugin.kt` (detekt's Kotlin pin), only if Step 5 needs it
- Modify: production or test Kotlin files only where the 2.2 compiler reports an error

**Interfaces:**
- Consumes: Task 1's green platform.
- Produces: a green build on Kotlin 2.2.21 and KSP2. The `5.json` file is byte-identical.

- [ ] **Step 1: Bump the versions**

```toml
kotlin             = "2.2.21"
ksp                = "2.2.21-2.0.5"
room               = "2.8.5"
hilt               = "2.57.2"
detekt             = "1.23.8"
```
In `build-logic/convention/build.gradle.kts`, change `compileOnly("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")` to `1.23.8`.

- [ ] **Step 2: Build once to collect the errors**

Run: `./gradlew assembleDebug --console=plain`. Expect failures, and record each distinct error. The `kotlinOptions` DSL is an error in Kotlin 2.2; Step 3 fixes it.

- [ ] **Step 3: Migrate `kotlinOptions` to `compilerOptions`**

In both `RollaAndroidLibraryPlugin.kt` and `RollaAndroidApplicationPlugin.kt`:
- Delete the `kotlinOptions { jvmTarget = "17" }` block inside `configure<…Extension>`.
- Delete the file-level `fun …Extension.kotlinOptions(...)` helper.
- After the `extensions.configure<LibraryExtension>` block (or `ApplicationExtension`), add:

```kotlin
            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
```
Keep `compileOptions { sourceCompatibility/targetCompatibility = JavaVersion.VERSION_17 }` unchanged. Use imports instead of fully-qualified names if the file's style prefers them.

- [ ] **Step 4: Fix the remaining compile errors**

Rebuild. Fix each error with the smallest change that keeps behaviour, and list every edit in the report under "Kotlin 2.2 fixes".
- If Hilt 2.59.2's Gradle plugin rejects AGP 8.13.2, fall back to Hilt **2.57.2**. That version is built with Kotlin 2.1.21 and KSP 2.1.21-2.0.2, which a 2.2 compiler reads. Report the fallback. (History: this fallback was taken, so 2.57.2 is now the target above.)
- `room-ktx` stays in the catalog. In Room 2.8 it is still published.

- [ ] **Step 5: detekt beside Kotlin 2.2**

Run: `./gradlew detekt --console=plain`
- If it fails with a Kotlin version mismatch, for example "detekt was compiled with Kotlin 2.0.21 but is currently running with 2.2.21", add this to `RollaStaticAnalysisPlugin.apply` after `pluginManager.apply("io.gitlab.arturbosch.detekt")`. This is detekt's documented pin for its own configuration:

```kotlin
            configurations.matching { it.name == "detekt" }.configureEach {
                resolutionStrategy.eachDependency {
                    if (requested.group == "org.jetbrains.kotlin") {
                        useVersion("2.0.21")
                    }
                }
            }
```
- If detekt still cannot run, stop and report BLOCKED. Do not switch to detekt 2.x alpha, and do not disable detekt.

- [ ] **Step 6: Schema invariant**

Run: `./gradlew :core:database:kspDebugKotlin --rerun-tasks`, then `git diff --exit-code -- core/database/schemas/`
Expected: exit code 0, with no diff. A diff means the build is BLOCKED: report it verbatim and do not edit `5.json`.

- [ ] **Step 7: Full gate**

Run: `./gradlew check assembleDebug :app:assembleBenchmark :core:database:compileDebugAndroidTestKotlin --console=plain` (timeout 600000)
Expected: `BUILD SUCCESSFUL`, and the unit-test count equals Task 1's. Quote both counts. Repeat Task 1 Step 6's manifest diff.

---

### Task 3: Media3 1.4.1 → 1.11.1

**Files:**
- Modify: `gradle/libs.versions.toml` (`media3 = "1.11.1"`)
- Modify: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/PlaybackSessionCallback.kt` (the `onPlaybackResumption` signature)
- Modify: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/PlaybackSessionCallbackTest.kt` (its call sites)
- Modify: any other `:core:media`, `:feature:widget` or `:app` file that 1.11.1 breaks at compile time

**Interfaces:**
- Consumes: Task 2's green Kotlin 2.2 build.
- Produces: `PlaybackSessionCallback` overrides Media3 1.11.1's `onPlaybackResumption` with its new boolean parameter. For every value of the parameter, the behaviour is exactly what it is today. The panel-resume project gives the parameter meaning later.

- [ ] **Step 1: Bump the version and fetch the sources for Task 4**

Set `media3 = "1.11.1"` in the catalog.

Download the 1.11.1 `-sources.jar` for `media3-session`, `media3-exoplayer` and `media3-common` into a **new empty directory** under the scratchpad. The URLs are `https://dl.google.com/android/maven2/androidx/media3/<artifact>/1.11.1/<artifact>-1.11.1-sources.jar`. Unzip them there, and report the path, which Task 4 reads.

Never run anything from inside that directory.

- [ ] **Step 2: Build and record the errors**

Run: `./gradlew assembleDebug --console=plain`, and record each error.

- [ ] **Step 3: Adopt the new `onPlaybackResumption` signature**

Read `MediaSession.java` in the 1.11.1 sources for the exact new signature and the parameter's name.
- If the old two-argument overload still exists and the new one delegates to it, override the **new** one anyway, so the panel-resume project has the hook.
- Delegate to unchanged logic. Rename the current override's body into a private function, and call it from the new override whatever the boolean says.
- Update `PlaybackSessionCallbackTest` to call the new signature. Existing tests run with the parameter `true`.
- Add one test: the same saved queue resumes identically with the parameter `false`. Mutation: make the `false` branch fail the future. That must go RED.

- [ ] **Step 4: Threading audit (Review Focus 5)**

List every `serviceScope.launch {}` / `withContext(Dispatchers.Default|IO)` block in `:core:media` main code, and mark each one: does it touch `player`, `mediaSession` or a `MediaController`?
- `PlaybackService.kt` lines ~101, 105, 128, 141, 262, 286 and 301 are starting points, not the full list.
- For each block that does touch one off the application looper:
  - Move the access onto `Dispatchers.Main.immediate`.
  - Add a test that fails with Media3 1.11's `IllegalStateException` if the access goes back to the background dispatcher.
- Report the full table, including "none found" rows.

- [ ] **Step 5: Tests**

Run: `./gradlew :core:media:testDebugUnitTest :feature:widget:testDebugUnitTest --console=plain`
- A test that fails only because Media3's behaviour changed must not be edited to match until you have cited the change: the release-notes version plus a source file and line. Name it in the report under "Behaviour changes".
- Never weaken an assertion to pass.

- [ ] **Step 6: Full gate**

Run: `./gradlew check assembleDebug :app:assembleBenchmark --console=plain` (timeout 600000)
Expected: `BUILD SUCCESSFUL`. Repeat the manifest diff and the `5.json` invariant.

---

### Task 4: Re-verify the Media3-dependent rulings against 1.11.1

**Files:**
- Read: the 1.11.1 sources from Task 3 Step 1. The 1.4.1 sources are at `C:\Users\VK\AppData\Local\Temp\claude\c--Users-VK-Desktop-RollaMusicPlayer\720b2325-89d9-40f3-b1e3-eb2e785ebcef\scratchpad\media3src`.
- Possibly modify: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/{MediaButtonStartFallback,PlaybackService,RestoredShuffleOrder,QueuePersister,PlaybackSessionCallback,RestoreCoordinator}.kt` and their tests
- Modify: `docs/superpowers/specs/2026-10-12-queue-restore-design.md` (an amendment line for each changed or retired ruling)
- Modify: `CLAUDE.md` (tech stack and the queue-restore banner, per spec §10)

**Interfaces:**
- Consumes: Task 3's 1.11.1 build and sources path.
- Produces: a ruling table in the report, and code changes only where a ruling no longer holds.

- [ ] **Step 1: The table**

For each row, quote the 1.11.1 source (file:line) that the ruling now rests on, and give a verdict: **kept**, **changed** or **retired**.

| # | Ruling | 1.4.1 fact | What to look at in 1.11.1 |
|---|---|---|---|
| 1 | R1 | `MediaSessionImpl.handleMediaControllerPlayRequest` routes Play on an empty player to `onPlaybackResumption` (both stubs) | The same method, `MediaSessionStub.playForControllerInfo`, `MediaSessionLegacyStub.onPlay`, and `isForPlayback` |
| 2 | R3, `RestoredShuffleOrder` | `setShuffleOrder` fires `onTimelineChanged(PLAYLIST_CHANGED)` and no transition; `ListenerSet` delivers in registration order | `ExoPlayerImpl.setShuffleOrder` and `ListenerSet` |
| 3 | P2 | `TIMELINE_CHANGE_REASON_SOURCE_UPDATE` fires on item preparation | `ExoPlayerImpl` timeline-change reasons |
| 4 | P6 | `MediaButtonReceiver` starts a foreground service for PLAY, PLAY_PAUSE and HEADSETHOOK; no notification for an empty timeline | `MediaButtonReceiver.onReceive`, `MediaNotificationManager.shouldShowNotification`, and 1.10's stale-foreground-intent detection (find it in `MediaSessionService`) |
| 5 | I2 | The receiver filters on key code only; `onMediaButtonEvent` drops non-DOWN | The same two places |
| 6 | P8/D1 | The notification deleteIntent is COMMAND_STOP via `getService`; the STOP-started service stays started | `DefaultMediaNotificationProvider` deleteIntent, `DefaultActionFactory`, 1.6's 10-minute foreground hold, and 1.8's `setShowNotificationForIdlePlayer` |
| 7 | Restore notification | 1.4.1 posts a notification for any non-empty timeline | 1.8's idle-player rule: does a *prepared* paused restore post one? |
| 8 | `onTaskRemoved` | It stops only when playback is not ongoing | 1.5's default implementation and 1.6's `pauseAllPlayersAndStopSelf` |
| 9 | Lifecycle | `MediaSessionService extends Service` | 1.10's `LifecycleService` base. Check that our `onStartCommand`/`onDestroy` overrides call `super` correctly, and whether `onBind` changed |

- [ ] **Step 2: Act on each non-kept row**

- **changed:** change the code, and add or adjust a test with a named mutation. Apply RED, then revert byte-exact.
- **retired:** remove our code only if a 1.11.1 source citation shows Media3 now does the same thing, **and** an existing or new test still passes against Media3's behaviour. Otherwise keep the code (it is harmless) and mark the row "kept: redundant, harmless", with the citation.
- A change that alters what the user sees, such as whether a paused notification shows after a restore, is reported under "User-visible" with its cost if wrong.

- [ ] **Step 3: Docs**

- `CLAUDE.md`:
  - Tech stack: Media3 1.11.1, Kotlin 2.2.
  - Notes: "Target Android API: 24 (Android 7.0) minimum, 34 (Android 14) target, compile 36".
  - The queue-restore banner: amend any changed or retired component.
- Queue-restore spec §12: one line per changed or retired ruling, as `**R/Px (Media3 1.11.1)** …`.
- These files are CRLF (`CLAUDE.md`) and LF (the specs). Keep each file's existing line endings, and use the Edit tool only.

- [ ] **Step 4: Full gate**

Run: `./gradlew check assembleDebug :app:assembleBenchmark :core:database:compileDebugAndroidTestKotlin --console=plain` (timeout 600000)
Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Device regression on the SM-S901B (controller only)

Safety rules, as in queue restore:
- Never uninstall, clear data, or change settings.
- Use only serial R3CTA03TZTY. Stop and ask if another device is attached.
- **Before injecting any media key**, confirm that `settings get secure media_button_receiver` names `com.rolla.musicplayer` (observation 0013). If it doesn't, play in the app first.

- [ ] **Step 1:** Run `ANDROID_SERIAL=R3CTA03TZTY ./gradlew :core:database:connectedDebugAndroidTest`. Every test passes. Confirm the fresh XML lists `PlaybackQueueDaoTest` and `migrate4To5…`.
- [ ] **Step 2:** Record `firstInstallTime` and the Playlists, Folders and Favourites counts *before* installing.
- [ ] **Step 3:** Run `./gradlew :app:assembleBenchmark`, then check the certificate with `apksigner verify --print-certs` (SHA-256 `2950a9a2…`).
  - Run `install -r`, then `cmd package compile -f -m speed-profile com.rolla.musicplayer`.
  - `firstInstallTime` must be unchanged and the counts identical.
  - Launch and play; the crash buffer must be empty.
- [ ] **Step 4:** Playback controls:
  - In the app: play, pause, next, previous, seek, shuffle, repeat.
  - In the notification: play/pause, next, previous.
  - The Equaliser applies a preset; the speed setting applies. Restore both to their prior values afterwards.
  - Exercise the widget only if one is placed (check `dumpsys appwidget`). Otherwise record "not exercised".
- [ ] **Step 5:** Queue restore:
  1. Shuffle the Music folder, skip twice, seek, pause, and screenshot the queue.
  2. Go Home, then `am kill`. Reopen: the same track, PAUSED and prepared (the real duration shows, and `dumpsys media_session` reports PAUSED), in the same order.
     - On Media3 1.6+ a paused service stays in the foreground for 10 minutes (`DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS`), and `am kill` cannot kill it in that time. Pick one route and record which. Both routes check P8 (Task 4 row 6):
       - **Wait route:** `dumpsys activity services com.rolla.musicplayer` shows `isForeground=true foregroundId=1001` right after the pause. Wait until the `isForeground` line disappears; it does not read `false`, and on the SM-S901B this took 11 minutes. The ServiceRecord stays, with `startRequested=false`, and the session stays PAUSED. Then `am kill`.
       - **Swipe route:** pause in the app, then swipe the task away from Recents (`onTaskRemoved` → `pauseAllPlayersAndStopSelf`). The hold ends, but the service is not destroyed: the app-scoped `PlaybackController`'s in-process binding keeps it. Expect the ServiceRecord to remain (`startRequested=false`, not foreground) and the paused notification 1001 to stay posted until the kill. Then `am kill`.
     - After the kill, expect exactly one `requestCode=86` restart (`Received SERVICE intent … MEDIA_BUTTON … PlaybackService … requestCode=86 from uid 1000`, then `Start proc … for service PlaybackService`). P8 stops it about 200 ms later. After 30 s there must be no ServiceRecord, no active notification and no further starts.
  3. Play for 15 s, then `am force-stop` while playing. Reopen: within 10 s of the stop position.
  4. Swipe the app away while playing: playback continues.
  5. Pause and kill, confirm the media-button receiver, then `input keyevent KEYCODE_MEDIA_PLAY`. Playback resumes with `topResumedActivity` still the launcher, and NEXT follows the saved order.
     - Kill by the swipe route or the wait route from 5.2; a plain `am kill` within 10 minutes of the pause does not kill the process. Either way, expect the single `requestCode=86` P8 restart and its stop first. Before the key, wait for that stop: `dumpsys activity services com.rolla.musicplayer` lists no ServiceRecord. The restarted process may still be cached; `am kill` it again if `pidof` shows it.
     - The "nothing saved" case (Task 4 row 4(b)) cannot be run on the device: the app has no clear-queue UI and clearing data is forbidden. It is pinned on the JVM only.
- [ ] **Step 6:** Process death and system reactions (Review Focus 3 and 4; observation 0013):
  - After the Step 5.2 kill, capture `adb logcat` for 30 s, and record every `Start proc …com.rolla`, `Received SERVICE intent …com.rolla` and `requestCode=` line.
  - Check whether a paused notification shows after the in-app restore (Task 4 row 7). Then check that the result matches Task 4's verdicts for rows 4, 6 and 7.
  - Repeat the 30 s watch after opening the app without playing and pressing Back and then Home. There must be no repeated starts.
- [ ] **Step 7:** Pause playback. Save the screenshots to `docs/design/calibration/media3-upgrade/` (uncommitted). Ledger the acceptance.

## Self-review

**Spec coverage:**

| Spec | Task |
|---|---|
| §2.1 Media3 1.11.1 | 3 |
| §2.2 green check and benchmark | 1–4 gates |
| §2.3 DB tests, version 5, identical `5.json` | 2 Step 6, 3 Step 6, 5 Step 1 |
| §2.4 data kept on upgrade | 5 Steps 2–3 |
| §2.5 device regression | 5 |
| §2.6 ruling re-check | 4 |
| §4 version table | Global Constraints, 1–3 |
| §5 stages 1–5 | Tasks 1–5 |
| §6 rulings | 4 Step 1, rows 1–9 |
| §7 no new permissions, R8 | 1 Step 6 (and repeated in 2–3), 3 Step 6, 5 Step 3 |
| §8.1 tests, mutation policy | Global Constraints, 3 Steps 3–5, 4 Step 2 |
| §8.2 device steps | 5 |
| §10 docs | 4 Step 3 |

**Value tables (observation 0007):**
- The version table appears once, in Global Constraints. Tasks 1–3 repeat the exact strings in their toml blocks, and they match: `8.13.2`, `36`, `2.2.21`, `2.2.21-2.0.5`, `2.8.5`, `2.57.2`, `1.23.8`, `1.11.1`.
- The fallbacks are named once each: Hilt 2.57.2, and baseline-profile/benchmark 1.4.1.

**Plan code that was never run (observation 0009):**
- This plan prescribes three code blocks: the `compilerOptions` migration, the detekt pin, and the TOML lines. None has been compiled.
- The implementer treats them as hypotheses. Mechanical fixes (imports, names) are allowed and must be reported. A behaviour change is a concern.
- No test code is prescribed. Each new test's mutation is named so its RED can be observed, and an equivalent mutant must be shown with evidence (observation 0010).

**Review Focus:**
- Every line names its pinning step: 1 → Tasks 2, 3 and 5; 2 → Task 3 Step 6 and Task 5 Step 3; 3 → Task 4 rows 4–5 and Task 5 Step 6; 4 → Task 4 rows 6–7 and Task 5 Step 6; 5 → Task 3 Step 4.
