# System UI Panel Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the process dies, the System UI media card keeps showing the saved track, and its Play resumes the
saved queue without opening the app.

**Architecture:**
- `PlaybackService` becomes a `MediaLibraryService` serving a `MediaLibrarySession`, so Media3 answers the System UI's
  recent-root request.
- `onPlaybackResumption(..., isForPlayback = false)` is the System UI's metadata request. It becomes read-only, through
  a new `RestoreCoordinator.peekSaved()`.
- `isForPlayback = true` (Play) is unchanged.

**Tech Stack:** Kotlin 2.2.21, Media3 1.11.1 (`media3-session` `MediaLibraryService`), Robolectric `sdk=34`, MockK,
kotlinx-coroutines-test 1.8.1.

**Spec:** `docs/superpowers/specs/2026-10-15-panel-resume-design.md` (approved by the user on 2026-10-15). Read it
before any task.

## Global Constraints

**Scope and invariants**
- Room stays at version 5. No database, persister or saved-data change.
- Library browsing stays private.
  - `onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onSearch` and `onSubscribe` are NOT overridden; Media3's defaults
    return `ERROR_NOT_SUPPORTED`.
  - Only Media3's own System UI recent-root handling serves anything.
- The manifest gains no permission and no component. The only change is two `<action>`s on the existing
  `PlaybackService` intent filter: `androidx.media3.session.MediaLibraryService` and
  `android.media.browse.MediaBrowserService`.
- `isForPlayback = true` behaviour is unchanged: `savedForResumption`, then `prepareRestore`, then the items; on
  failure, `onNothingToResume`.
- `isForPlayback = false` never does any of these:
  - changes `RestoreCoordinator.outcome`;
  - calls `onSettled`;
  - arms `RestoredShuffleOrder`;
  - sets repeat or shuffle mode;
  - reads or writes `mediaSession.player`;
  - calls `onNothingToResume`.
- `targetSdk` stays 34, `minSdk` 24, Media3 1.11.1. No dependency changes.

**Agent rules**
- Agents never run `git commit`, `git add`, `git stash`, `git checkout` or `git reset`. The user commits.
- Never use `sed -i` on source files: Spotless enforces CRLF. Edit with the editor tool.
- Never write Kotlin through Bash heredocs.
- Run `./gradlew spotlessApply` after every Kotlin edit.
- detekt thresholds are unchanged: `LongMethod` 30, `TooManyFunctions` 15/12, `LongParameterList` 6. Any `@Suppress`
  needs a one-line reason.
- kotlinx-coroutines-test 1.8.1: use `runCurrent()`, not `advanceUntilIdle()`, for `backgroundScope` work.

**Mutation-proof policy** (every new or changed test)
- Apply the named mutation, run, quote the `BUILD` line and the decisive failure line, then revert byte-exact (check the
  sha256).
- A named mutation is a hypothesis about Media3 or Robolectric, not a fact. If it survives, prove it is equivalent:
  equivalent means **no observable output changes**. If an output changes that the test cannot see, move the test's
  probe instead. Then substitute the closest mutation that can fail, and say so in the report.
- Every test also needs a **control assertion**: something that shows the component under test actually acted in the
  unmutated run.

**Code blocks and builds**
- The code blocks below were not compiled. Implementers may make minimal mechanical fixes (imports, named arguments)
  and must report each one.
- Run Gradle with the Bash tool from the repo root, with a 600000 ms timeout for `check`.

## Review Focus

1. **A metadata request while the in-app restore is still loading.** The app must still open paused and prepared with
   the saved order; the request must not take the restore over. (Task 1:
   `notForPlayback_whileLoading_describesTheQueueAndTheAppStillRestores`.)
2. **A metadata request after the user started their own queue.** It must not arm a stale order or overwrite their
   shuffle and repeat modes. (Task 1: `notForPlayback_afterSettling_neitherArmsNorSetsModes`.)
3. **A metadata request when the save is missing or unreadable.** The future fails, the media-button fallback never
   runs, and nothing crashes. (Task 1: `notForPlayback_nothingSaved_failsWithoutTheFallback` and
   `notForPlayback_aFailingLoad_failsWithoutTheFallback`.)
4. **The app's own `MediaController` and the widget connecting to a `MediaLibrarySession`.** Play on an empty player
   still resumes. (Task 2: the two existing real-session tests, re-hosted on a `MediaLibrarySession`.)
5. **Another app connecting as a `MediaBrowser`.** It gets no library root, not even a recent one, because it is not
   the System UI. (Task 2: `aBrowserThatIsNotSystemUi_getsNoLibraryRoot`.)

---

### Task 1: The System UI's metadata request is read-only

**Files:**
- Modify: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/RestoreCoordinator.kt` (add `peekSaved`).
- Modify: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/PlaybackSessionCallback.kt` (branch on
  `isForPlayback`, plus KDoc).
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/RestoreCoordinatorTest.kt` (3 new tests).
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/PlaybackSessionCallbackTest.kt`.
  - Replace `resumptionNotForPlayback_resumesTheSameSavedQueue` with 4 new tests.

**Interfaces:**
- Consumes: `RestoreCoordinator(repository, scope, mainDispatcher, onSettled)`, its `Outcome`, `firstLoad`,
  `loadOrNull()`, `savedForResumption()`, and `Player.prepareRestore(saved, restoredOrder)`. All exist today.
- Produces: `suspend fun RestoreCoordinator.peekSaved(): SavedPlayback?`, called on the main dispatcher.
  `PlaybackSessionCallback.onPlaybackResumption(session, controller, isForPlayback = false)` resolves the saved queue
  read-only. The class still implements `MediaSession.Callback`; Task 2 changes that.

- [ ] **Step 1: Write the failing coordinator tests.** Add these to `RestoreCoordinatorTest`, before
  `private companion object`:

```kotlin
    // Panel-resume spec §5.4. A peek while loading shares the first load and leaves the restore to the app. Control:
    // the in-app restore applies (APPLIED, 5 items). Mutation: implement peekSaved as savedForResumption() →
    // HANDED_TO_RESUMPTION and an empty player → RED.
    @Test
    fun aPeekWhileLoading_sharesTheFirstLoadAndTheAppStillRestores() = runTest {
        repository.saved = saved
        repository.loadGate = CompletableDeferred()
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()

        val peeked = async { coordinator.peekSaved() }
        settle()
        repository.loadGate!!.complete(Unit)
        settle()

        assertEquals(saved, peeked.await())
        assertEquals(Outcome.APPLIED, coordinator.outcome)
        assertEquals(5, player.mediaItemCount)
        assertEquals(listOf(3, 0, 4, 1, 2), player.queueState().items.map { it.index })
        assertEquals(1, repository.loadCount)
        assertEquals(1, settledCount)
    }

    // Spec §5.4 (as ruling R6). Once settled a peek reads again and never settles a second time. Mutations: always
    // return the first load → the stale queue → RED; call onSettled in peekSaved → settledCount 2 → RED.
    @Test
    fun aPeekAfterSettling_readsAgainWithoutSettling() = runTest {
        repository.saved = saved
        val coordinator = coordinator()
        coordinator.restoreInto(player, restoredOrder, holder)
        settle()
        assertEquals(Outcome.APPLIED, coordinator.outcome)

        val newer = saved.copy(songs = saved.songs.take(2), shuffleOrder = listOf(1, 0))
        repository.saved = newer

        assertEquals(newer, coordinator.peekSaved())
        assertEquals(2, repository.loadCount)
        assertEquals(Outcome.APPLIED, coordinator.outcome)
        assertEquals(1, settledCount)
    }

    // Spec §5.4. With no in-app restore started, a peek leaves the coordinator LOADING. Control: it returns the save.
    // Mutation: set outcome = HANDED_TO_RESUMPTION in peekSaved → RED.
    @Test
    fun aPeekAlone_leavesTheCoordinatorLoading() = runTest {
        repository.saved = saved
        val coordinator = coordinator()

        val peeked = async { coordinator.peekSaved() }
        settle()

        assertEquals(saved, peeked.await())
        assertEquals(Outcome.LOADING, coordinator.outcome)
        assertEquals(0, settledCount)
    }
```

- [ ] **Step 2: Run them and confirm they fail to compile.**
  - Run: `./gradlew :core:media:testDebugUnitTest --tests '*RestoreCoordinatorTest'`
  - Expected: `BUILD FAILED` with `Unresolved reference 'peekSaved'`.

- [ ] **Step 3: Add `peekSaved`** to `RestoreCoordinator`, directly after `savedForResumption`:

```kotlin
    /**
     * The System UI's metadata request (panel-resume spec §5.4): reads without settling [outcome]. While loading it
     * shares the first load, so the in-app restore still applies; once settled it reads again (ruling R6). It never
     * calls [onSettled]. Call it on [mainDispatcher].
     */
    suspend fun peekSaved(): SavedPlayback? = if (outcome == Outcome.LOADING) firstLoad.await() else loadOrNull()
```

- [ ] **Step 4: Run the coordinator tests.**
  - Run: `./gradlew :core:media:testDebugUnitTest --tests '*RestoreCoordinatorTest'`
  - Expected: `BUILD SUCCESSFUL`, with all 9 tests passing.
  - Apply each named mutation, record RED, and revert byte-exact.

- [ ] **Step 5: Replace the old `false` test** in `PlaybackSessionCallbackTest`.
  - Delete `resumptionNotForPlayback_resumesTheSameSavedQueue`, including its comment.
  - In its place add this fixture, a helper and 4 tests.
  - Add the imports `kotlinx.coroutines.CompletableDeferred` and `java.io.IOException` if they are missing.

```kotlin
    private val savedFour = SavedPlayback(
        songs = (0 until 4).map(::song),
        shuffleOrder = listOf(2, 0, 3, 1),
        state = SavedPlaybackState(currentIndex = 3, positionMs = 61_000L, shuffleEnabled = true, RepeatMode.ONE),
    )

    private fun TestScope.coordinatorAndCallback(
        onNothingToResume: () -> Unit,
    ): Pair<RestoreCoordinator, PlaybackSessionCallback> {
        val main = StandardTestDispatcher(testScheduler)
        val coordinator = RestoreCoordinator(repository, backgroundScope, main) {}
        val callback = PlaybackSessionCallback(backgroundScope, main, coordinator, restoredOrder, onNothingToResume)
        return coordinator to callback
    }

    private fun TestScope.settle() {
        runCurrent()
        idle()
        runCurrent()
    }

    // Panel-resume spec §5.3, Review Focus 1. The System UI asks while the in-app restore is loading: it gets the saved
    // queue, and the app still restores it paused with its order. Control: the in-app restore applies (APPLIED, 4
    // items, saved order). Mutation: route false through resumeSavedQueue (savedForResumption + prepareRestore) →
    // HANDED_TO_RESUMPTION, empty player → RED.
    @Test
    fun notForPlayback_whileLoading_describesTheQueueAndTheAppStillRestores() = runTest {
        assertNotEquals(3, media3Order(4, startIndex = 3).first())
        repository.saved = savedFour
        repository.loadGate = CompletableDeferred()
        var reports = 0
        val (coordinator, callback) = coordinatorAndCallback { reports++ }
        coordinator.restoreInto(player, restoredOrder, PlaybackStateHolder())
        settle()

        val future = callback.onPlaybackResumption(session, controller, isForPlayback = false)
        settle()
        repository.loadGate!!.complete(Unit)
        settle()

        val result = future.get()
        assertEquals((0 until 4).map { "s$it" }, result.mediaItems.map { it.mediaId })
        assertEquals(3, result.startIndex)
        assertEquals(61_000L, result.startPositionMs)
        assertEquals(RestoreCoordinator.Outcome.APPLIED, coordinator.outcome)
        assertEquals(4, player.mediaItemCount)
        assertEquals(listOf(2, 0, 3, 1), player.queueState().items.map { it.index })
        assertEquals(0, reports)
    }

    // Review Focus 2. The restore settled with nothing saved; the user's empty player and default modes must survive a
    // later metadata request, which reads the newer save. Control: the request returns that save. Mutations: call
    // prepareRestore on the false path → armed, shuffle on, repeat ONE → RED; return the first load (null) → the
    // future fails → RED.
    @Test
    fun notForPlayback_afterSettling_neitherArmsNorSetsModes() = runTest {
        val (coordinator, callback) = coordinatorAndCallback {}
        coordinator.restoreInto(player, restoredOrder, PlaybackStateHolder())
        settle()
        assertEquals(RestoreCoordinator.Outcome.DISCARDED, coordinator.outcome)
        repository.saved = savedFour

        val future = callback.onPlaybackResumption(session, controller, isForPlayback = false)
        settle()

        assertEquals((0 until 4).map { "s$it" }, future.get().mediaItems.map { it.mediaId })
        assertFalse(restoredOrder.isArmed)
        assertFalse(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
        assertEquals(0, player.mediaItemCount)
        assertEquals(RestoreCoordinator.Outcome.DISCARDED, coordinator.outcome)
    }

    // Review Focus 3. Nothing saved: the future fails, the media-button fallback (ruling P6) never runs, and the
    // coordinator is untouched. Control: the future is done (it failed). Mutations: call onNothingToResume on the
    // false path → reports 1 → RED; use savedForResumption → HANDED_TO_RESUMPTION → RED.
    @Test
    fun notForPlayback_nothingSaved_failsWithoutTheFallback() = runTest {
        var reports = 0
        val (coordinator, callback) = coordinatorAndCallback { reports++ }

        val future = callback.onPlaybackResumption(session, controller, isForPlayback = false)
        settle()

        assertTrue(future.isDone)
        assertTrue(runCatching { future.get() }.exceptionOrNull() is ExecutionException)
        assertEquals(0, reports)
        assertEquals(RestoreCoordinator.Outcome.LOADING, coordinator.outcome)
    }

    // Review Focus 3. A failing load is logged and treated as nothing saved (ruling P1): same outcome, still no
    // fallback. Control: the load ran (loadCount 1). Mutation: call onNothingToResume on the false path → RED.
    @Test
    fun notForPlayback_aFailingLoad_failsWithoutTheFallback() = runTest {
        repository.loadFailure = IOException("corrupt")
        var reports = 0
        val (_, callback) = coordinatorAndCallback { reports++ }

        val future = callback.onPlaybackResumption(session, controller, isForPlayback = false)
        settle()

        assertEquals(1, repository.loadCount)
        assertTrue(runCatching { future.get() }.exceptionOrNull() is ExecutionException)
        assertEquals(0, reports)
    }
```

  `assertFalse` comes from `org.junit.Assert.assertFalse`; add the import.

- [ ] **Step 6: Run the callback tests and confirm the new ones fail.**
  - Run: `./gradlew :core:media:testDebugUnitTest --tests '*PlaybackSessionCallbackTest'`
  - Expected: `notForPlayback_whileLoading_…` fails (outcome `HANDED_TO_RESUMPTION`).
  - Expected: `notForPlayback_afterSettling_…` fails (the order is armed).
  - Expected: both `…failsWithoutTheFallback` tests fail (`reports` is 1).

- [ ] **Step 7: Branch the callback** in `PlaybackSessionCallback.kt`:
  - Replace `onPlaybackResumption` and `resumeSavedQueue` with the code below.
  - Keep the two "Accepted residual window" comment lines in the `true` lambda.
  - Add the import `com.rolla.musicplayer.core.model.SavedPlayback`.

```kotlin
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaItemsWithStartPosition> = if (isForPlayback) {
        savedQueueFuture(onNothing = onNothingToResume, load = coordinator::savedForResumption) { saved ->
            // Accepted residual window: the order is armed here, before Media3 sets the returned items.
            // Also accepted (inherent to Media3 resumption): an app queue that arrives while
            // savedForResumption is suspended is replaced anyway when Media3's onSuccess sets these items.
            mediaSession.player.prepareRestore(saved, restoredOrder)
        }
    } else {
        // The System UI's metadata request for its media card (panel-resume spec §5.3): read only.
        savedQueueFuture(onNothing = {}, load = coordinator::peekSaved) {}
    }

    private fun savedQueueFuture(
        onNothing: () -> Unit,
        load: suspend () -> SavedPlayback?,
        beforeReturn: (SavedPlayback) -> Unit,
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaItemsWithStartPosition>()
        scope.launch(mainDispatcher) {
            try {
                val saved = load()
                if (saved == null) {
                    onNothing()
                    future.setException(IllegalStateException("No saved queue to resume"))
                } else {
                    beforeReturn(saved)
                    future.set(
                        MediaItemsWithStartPosition(
                            saved.songs.map { it.toMediaItem() },
                            saved.state.currentIndex,
                            saved.state.positionMs,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Any load or mapping failure must fail the future, never leave Media3 waiting.
                onNothing()
                future.setException(e)
            }
        }.invokeOnCompletion { cause ->
            // Also covers a scope cancelled before the body ever ran: the future never stays pending.
            if (cause != null) future.cancel(false)
        }
        return future
    }
```

  Replace the KDoc's `isForPlayback` paragraph with:

```kotlin
 * Media3 1.11 adds `isForPlayback`. `true` is a Play (our controllers, a media button, the panel card's Play) and
 * resumes as above. `false` is the System UI asking what its media card should show (panel-resume spec §5.3): it reads
 * the saved queue through [RestoreCoordinator.peekSaved] and changes nothing, so it never takes the in-app restore,
 * arms the order, touches the player or calls [onNothingToResume].
```

- [ ] **Step 8: Run the module's tests.**
  - Run: `./gradlew spotlessApply` and then `./gradlew :core:media:testDebugUnitTest`.
  - Expected: `BUILD SUCCESSFUL`. Every existing `true`-path test passes unchanged.
  - Apply each named mutation in Step 5, record RED, and revert byte-exact.

- [ ] **Step 9: Run the static checks.**
  - Run: `./gradlew detekt spotlessCheck`
  - Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Report.** Write `task-1-report.md` (path given in the dispatch), with:
  - every mutation's `BUILD` line and its failure line;
  - any mechanical fixes to the plan's code;
  - the final test count for `:core:media`.

---

### Task 2: `PlaybackService` serves a `MediaLibrarySession`

**Files:**
- Modify: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/PlaybackService.kt`.
  - The superclass becomes `MediaLibraryService`.
  - The field type is `MediaLibrarySession`, and `installQueueRestore` builds a `MediaLibrarySession`.
  - `onGetSession` returns it.
- Modify: `core/media/src/main/kotlin/com/rolla/musicplayer/core/media/PlaybackSessionCallback.kt`: implements
  `MediaLibrarySession.Callback`.
- Modify: `core/media/src/main/AndroidManifest.xml`: two actions.
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/MediaButtonManifestTest.kt` (1 new test).
- Test: `core/media/src/test/kotlin/com/rolla/musicplayer/core/media/PlaybackResumptionThroughSessionTest.kt`.
  - Host the two existing tests on a `MediaLibrarySession`, and add 1 browser test.

**Interfaces:**
- Consumes: Task 1's `PlaybackSessionCallback` (constructor unchanged) and `RestoreCoordinator`.
- Produces: `PlaybackService : MediaLibraryService`, whose `onGetSession(controllerInfo): MediaLibrarySession` returns
  the one session. No other code changes: `PlaybackController` and the widget connect through a `SessionToken` for
  `PlaybackService` exactly as now.

- [ ] **Step 1: Write the failing manifest test.** Add to `MediaButtonManifestTest`:

```kotlin
    // Panel-resume spec §5.2. The System UI binds the legacy MediaBrowserService action, and Media3 looks the service
    // up by its MediaLibraryService action. Control: the existing MEDIA_BUTTON test proves the query sees this
    // service. Mutations: drop either action from the service's intent filter → RED.
    @Test
    fun theServiceIsALibraryAndALegacyBrowser() {
        val actions = listOf("androidx.media3.session.MediaLibraryService", "android.media.browse.MediaBrowserService")
        for (action in actions) {
            val services = context.packageManager.queryIntentServices(Intent(action).setPackage(context.packageName), 0)
            assertTrue(action, services.any { it.serviceInfo.name == PlaybackService::class.java.name })
        }
    }
```

- [ ] **Step 2: Run it and confirm it fails.**
  - Run: `./gradlew :core:media:testDebugUnitTest --tests '*MediaButtonManifestTest'`
  - Expected: FAIL, naming `androidx.media3.session.MediaLibraryService`.

- [ ] **Step 3: Add the two actions** to the service's `<intent-filter>` in `core/media/src/main/AndroidManifest.xml`,
  after the `MediaSessionService` action:

```xml
                <action android:name="androidx.media3.session.MediaLibraryService" />
                <action android:name="android.media.browse.MediaBrowserService" />
```

  Above the `<service>`, add the comment:

```xml
        <!-- Panel resume (panel-resume spec §5.2): the System UI binds the browser service to fill and resume its media
             card after the process died. Media3 serves only the System UI's recent root; other browsers get no library. -->
```

- [ ] **Step 4: Run it.** Same command as Step 2. Expected: PASS. Then remove one action, confirm RED, and revert.

- [ ] **Step 5: Re-host the real-session tests and add the browser test** in `PlaybackResumptionThroughSessionTest`:
  - Change `private var session: MediaSession? = null` to `private var session: MediaLibrarySession? = null`.
  - In both existing tests, replace `MediaSession.Builder(context, player).setCallback(callback).build()` with
    `MediaLibrarySession.Builder(context, player, callback).build()`.
  - Add the imports `androidx.media3.session.MediaLibraryService.MediaLibrarySession`,
    `androidx.media3.session.MediaLibraryService.LibraryParams`, `androidx.media3.session.MediaBrowser` and
    `androidx.media3.session.LibraryResult`.
  - Update the class KDoc's first sentence to say "a REAL Media3 [MediaLibrarySession] (as `PlaybackService` serves
    since panel resume)".
  - Then add:

```kotlin
    // Panel-resume spec §3 and §5.3, Review Focus 5. A browser that is not the System UI gets no root: neither the
    // plain root nor the recent root, which Media3 serves only to the System UI. Control: the browser connected (the
    // future resolved). Mutation: override onGetLibraryRoot in PlaybackSessionCallback to return
    // LibraryResult.ofItem(MediaItem.Builder().setMediaId("root").build(), params) → RED.
    @Test
    fun aBrowserThatIsNotSystemUi_getsNoLibraryRoot() {
        val main = Dispatchers.Main.immediate
        val coordinator = RestoreCoordinator(repository, serviceScope, main) {}
        val callback = PlaybackSessionCallback(serviceScope, main, coordinator, restoredOrder) {}
        val realSession = MediaLibrarySession.Builder(context, player, callback).build()
        session = realSession
        val browserFuture = MediaBrowser.Builder(context, realSession.token).buildAsync()
        idleUntil { browserFuture.isDone }
        val browser = browserFuture.get()
        controller = browser

        val plain = browser.getLibraryRoot(null)
        idleUntil { plain.isDone }
        val recent = browser.getLibraryRoot(LibraryParams.Builder().setRecent(true).build())
        idleUntil { recent.isDone }

        assertEquals(LibraryResult.RESULT_ERROR_NOT_SUPPORTED, plain.get().resultCode)
        assertEquals(LibraryResult.RESULT_ERROR_NOT_SUPPORTED, recent.get().resultCode)
    }
```

  - The `controller` field is a `MediaController?`. `MediaBrowser` extends `MediaController`, so `release()` in
    `tearDown` covers it.

- [ ] **Step 6: Run it and confirm it fails to compile.**
  - Run: `./gradlew :core:media:testDebugUnitTest --tests '*PlaybackResumptionThroughSessionTest'`
  - Expected: `BUILD FAILED`, because `PlaybackSessionCallback` is not a `MediaLibrarySession.Callback`
    (`MediaLibrarySession.Builder` needs one).

- [ ] **Step 7: Make the callback a library callback** in `PlaybackSessionCallback.kt`:
  - Change `) : MediaSession.Callback {` to `) : MediaLibrarySession.Callback {`.
  - Add the import `androidx.media3.session.MediaLibraryService.MediaLibrarySession`.
  - Add one KDoc sentence: "It is the service's `MediaLibrarySession.Callback`, and it overrides no library method:
    Media3's defaults refuse every browse, and Media3 itself answers the System UI's recent root (panel-resume spec
    §3)."

- [ ] **Step 8: Convert the service** in `PlaybackService.kt`:
  - Change `class PlaybackService : MediaSessionService()` to `class PlaybackService : MediaLibraryService()`.
  - Change `private lateinit var mediaSession: MediaSession` to `private lateinit var mediaSession: MediaLibrarySession`.
  - Change `installQueueRestore()`'s return type to `MediaLibrarySession`. Its builder line becomes
    `val session = MediaLibrarySession.Builder(this, player, callback).build()`.
  - Change `override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession` to
    `override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession`.
  - Replace the import `androidx.media3.session.MediaSessionService` with `androidx.media3.session.MediaLibraryService`,
    and add `androidx.media3.session.MediaLibraryService.MediaLibrarySession`. Keep `androidx.media3.session.MediaSession`
    (used by `ControllerInfo`).
  - Grep the file for any other `MediaSessionService` or `MediaSession` type use, and adjust only what fails to compile.

- [ ] **Step 9: Run the session tests.**
  - Run: `./gradlew spotlessApply` and then
    `./gradlew :core:media:testDebugUnitTest --tests '*PlaybackResumptionThroughSessionTest'`.
  - Expected: PASS for all 3.
  - Apply the browser test's mutation, record RED, and revert byte-exact.
  - The two re-hosted tests keep their existing mutations. Re-run one of them, dropping the arm in `prepareRestore`, to
    show the library session still exercises the Play path.

- [ ] **Step 10: Run the full gate and compare the manifest.**
  - Run: `./gradlew check assembleDebug`. Expected: `BUILD SUCCESSFUL`.
  - Then run `./gradlew :app:processReleaseMainManifest`, and diff
    `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml` against
    `.superpowers/sdd/2026-10-14-media3-upgrade/baseline-merged-manifest.xml`, the committed upgrade's merged
    release manifest. If the path differs, find it with
    `find app/build/intermediates -path '*release*' -name AndroidManifest.xml` and report the path used.
  - The only service-level change may be the two new `<action>`s. No new `<uses-permission>`, `<service>`,
    `<receiver>`, `<activity>` or `<provider>`.
  - Paste the relevant diff hunk in the report.

- [ ] **Step 11: Report** to `task-2-report.md`, with the mutation evidence, the manifest diff hunk and the test counts.

---

### Task 3: Device check and docs

The controller does this task itself with the user's phone (Samsung SM-S901B, serial `R3CTA03TZTY`), plus one docs
edit. Use `A="/c/Users/VK/AppData/Local/Android/Sdk/platform-tools/adb.exe -s R3CTA03TZTY"`.

**Files:**
- Modify: `CLAUDE.md` (lines 106, 139, 332-335 and 381; exact text in Step 9).
- Modify: `docs/superpowers/specs/2026-10-12-queue-restore-design.md` §12, P7 (one amendment line; Step 9).
- Screenshots go in `docs/design/calibration/panel-resume/`. Never commit that folder.

**Device safety (binding):**
- Never uninstall, never clear data, and never change any setting (including the quick-panel media-player option).
- Leave silent mode on.
- Media keys only after `settings get secure media_button_receiver` names `com.rolla.musicplayer`. Otherwise play in the
  app first.
- Touch only our app and the notification panel. If a different device is attached, stop and ask.

**Where input lands:**
- `cmd statusbar expand-notifications` and `collapse` only open and close the shade.
- Taps on the card go to the System UI and then to our session.
- Read the card's coordinates from a screenshot before each tap. Never tap the shade blind: other apps' cards are there
  too (Samsung Music's card was present on 2026-10-15).

- [ ] **Step 1: Install over the app.**
  - `./gradlew :app:assembleBenchmark`.
  - Check the APK's certificate digest matches the installed app's (`apksigner verify --print-certs` against
    `dumpsys package com.rolla.musicplayer | grep -i signatures`). The upgrade used the debug certificate `2950a9a2…`.
  - `$A install -r app/build/outputs/apk/benchmark/app-benchmark.apk`, then
    `$A shell cmd package compile -m speed-profile -f com.rolla.musicplayer`.
  - `firstInstallTime` must stay `2026-10-09 14:19:50`.
  - Open the app with `monkey -p com.rolla.musicplayer -c android.intent.category.LAUNCHER 1`.
  - `dumpsys media_session` shows Rolla `PAUSED` with a buffered position (prepared). Record the track and position.
  - Screenshot the queue sheet as `00-queue-before.png`.

- [ ] **Step 2: Card after death, wait route.**
  - Press Home. `dumpsys activity services com.rolla.musicplayer` shows `isForeground=true`.
  - Wait 11 minutes in a background command, then check that the `isForeground` line is **absent**. It disappears; it
    never reads `false`.
  - `$A logcat -c`, then `$A shell am kill com.rolla.musicplayer`.
  - Capture 60 s of `logcat -v time` filtered to `Start proc.*com.rolla|Received SERVICE intent.*com.rolla|`
    `MediaSessionImpl|ExoPlayerImpl|Killing.*com.rolla|ResumeMediaBrowser|MediaResumeListener|MediaBrowser`. Save it to
    the scratchpad.
  - Expected:
    - one `requestCode=86` restart, released about 200 ms later (P8);
    - possibly a second `Start proc … for service` from the System UI's browser bind;
    - no repeating pattern.
  - Run the capture in the background. 30 s after the kill (spec criterion 1), while it is still running,
    `cmd statusbar expand-notifications`, screenshot `01-card-wait.png`, and `cmd statusbar collapse`.
  - Plan ruling against spec §7.2 step 2: no temporary debug log line is added. The card showing the saved title after
    the process died can only come from the `isForPlayback = false` path, so the screenshot is the evidence, and no
    code needs reverting before commit.
  - Pass: a Rolla Music card shows the saved title and artist. Record whether artwork shows.
  - If there is no card at all, stop and ask the user whether the phone's quick-panel media-player option is on. Do not
    open settings.

- [ ] **Step 3: Metadata request without Play.**
  - Without touching the card, open the app from the launcher.
  - `dumpsys media_session` shows `PAUSED` at the saved position, with a buffered position.
  - The queue order matches `00-queue-before.png`.
  - This is spec criterion 3 on the device.

- [ ] **Step 4: Card Play, wait route again.**
  - Re-create the dead state: pause, Home, wait 11 minutes, `am kill`.
  - Expand the shade, screenshot, find the card's Play from the screenshot, and tap it.
  - Within 5 s, `dumpsys media_session` shows Rolla `PLAYING`, on the saved track, near the saved position (within 10 s).
  - `dumpsys activity activities | grep topResumedActivity` must not name `com.rolla.musicplayer`.
  - Check `media_button_receiver` names our app, then send `KEYCODE_MEDIA_NEXT`. The next track must be the second row
    of `00-queue-before.png`.
  - `KEYCODE_MEDIA_PAUSE`. Collapse the shade.

- [ ] **Step 5: Swipe route.**
  - Open the app, pause in the app, open Recents, and swipe the Rolla card up from its centre (read the coordinates
    from a screenshot; never tap "Close all").
  - Home, `logcat -c`, `am kill`.
  - Repeat Step 2's 60 s capture and card screenshot (`02-card-swipe.png`), then Step 4's Play check.

- [ ] **Step 6: K1 watch** (spec §5.5 and §8).
  - After a card has appeared (Step 2 or Step 5), capture 60 s more of logcat with no taps.
  - Count `Start proc.*com.rolla` and `MediaSessionImpl.*Init`.
  - Pass: no growth after the first System UI request.
  - If they repeat, stop: K1 has happened. Record the logcat, and report to the user with spec §8's fallback (restore on
    the first non-System UI controller connection instead of in `onCreate`) as a proposed plan amendment.

- [ ] **Step 7: Regression.** Run the upgrade's device steps again:
  - force-stop while playing restores within 10 s;
  - swipe-away while playing keeps playing;
  - headset Play after a kill resumes with the launcher on top;
  - notification Pause, Next, Previous and Play;
  - Repeat cycles.
  - Run a 30 s D1 watch after open → Back → Home. The crash buffer must be empty.

- [ ] **Step 8: Leave the phone paused**, and ledger every result with its logcat line.

- [ ] **Step 9: Docs** (only after Steps 2-7 pass).
  - `CLAUDE.md:106`: replace "The System UI media controls work while the session is alive, but its carousel does not
    resume the queue after the process has died (that needs a `MediaLibraryService`, a non-goal)" with "After the
    process has died, the System UI media card keeps the saved track, and its Play resumes the queue the same way
    (`PlaybackService` is a `MediaLibraryService`; no library is browsable)".
  - `CLAUDE.md:139`: replace "the System UI controls work while the session is alive, but its carousel does not resume
    after the process has died" with "the System UI media card resumes it after the process has died".
  - `CLAUDE.md:334-335`, in the banner: replace "The System UI controls work while the session is alive, but its
    carousel does not resume after the process has died: that needs a `MediaLibraryService` (spec §12 P7)." with
    "**Panel resume** (spec `docs/superpowers/specs/2026-10-15-panel-resume-design.md`): `PlaybackService` is a
    `MediaLibraryService`, so the System UI media card survives process death; its metadata request
    (`isForPlayback = false`) reads through `RestoreCoordinator.peekSaved()` and changes nothing, and its Play resumes
    like a headset Play."
  - `CLAUDE.md:381`: change "`PlaybackService` (MediaSessionService)" to "`PlaybackService` (MediaLibraryService)".
  - If the device showed no artwork, add "(no artwork on the card)" after "keeps the saved track" at line 106.
  - Queue-restore spec P7: append the line "  Delivered by the panel-resume project
    (`docs/superpowers/specs/2026-10-15-panel-resume-design.md`, 2026-10-15)." below the P7 bullet. Keep the file's LF
    line endings.
  - `CLAUDE.md` is CRLF. Edit with the editor tool and check `grep -c $'\r' CLAUDE.md` equals the line count afterwards.

- [ ] **Step 10: Ledger** the acceptance, and list the user-visible change: the media card stays after the app is killed.
