# System UI panel resume — design

**Status:** Approved by the user on 2026-10-15 and implemented (Tasks 1-3); §10 records the device result. The user
approved the approach in conversation on 2026-10-14 (approach 1: convert to a `MediaLibraryService` and make the System
UI's metadata request free of side effects), then chose to upgrade Media3 first. This spec adapts that approach to
Media3 1.11.1's `isForPlayback` flag.
**Branch:** `feat/oneui-redesign`, which is merged to `main` at the end of the redesign.
**Follows:** queue restore (`docs/superpowers/specs/2026-10-12-queue-restore-design.md`, commit f6c6778) and the
Media3 1.11.1 upgrade (`docs/superpowers/specs/2026-10-14-media3-upgrade-design.md`, commit fd485ca).

## 1. Goal

After the app's process has died, the System UI media card for Rolla Music stays in the notification panel, showing the
saved track. Its Play resumes the saved queue (same track, position, order and modes) without opening the app.

Today, when the process dies, the system cancels the paused notification and the card disappears. Headset and Bluetooth
Play already resume the saved queue (queue-restore spec §7.6); this project gives the panel the same ability.

## 2. Success criteria

1. On the SM-S901B (Android 14, One UI), after the process dies while paused, by a kill after the 10-minute paused hold
   or after a paused swipe-away, a Rolla Music card is shown in the notification panel within 30 s, with the saved
   track's title and artist.
2. Play on that card resumes the saved queue: the saved track at the saved position (within 10 s), the same next track
   as the saved order, and the saved shuffle and repeat modes. The activity does not open.
3. The System UI's metadata request never changes playback state:
   - it does not hand the restore over to resumption;
   - it does not arm the saved shuffle order;
   - it does not stop the service through the media-button fallback.
   Opening the app afterwards still restores paused and prepared with the real duration.
4. Every existing behaviour holds: in-app restore, headset Play, force-stop restore, swipe-away while playing, the
   notification controls, the widget, and the D1/P8 loop watch (one restart per kill, then quiet).
5. No other app gains anything: browsing the library is still not offered to any controller, and the manifest gains no
   permission. The only manifest change is two intent-filter actions on the existing exported service.
6. `./gradlew check assembleDebug` is green, with the mutation-proof policy for every new or changed test.

## 3. Non-goals

- A browsable library for Android Auto, Wear, Assistant or any other app. `onGetLibraryRoot` keeps Media3's default
  (`ERROR_NOT_SUPPORTED`). Only System UI's recent root is served, and Media3 serves that itself.
- Starting playback, or posting a card, at device boot. The System UI decides when it asks; we only answer.
- Changing the in-app restore, the persister or the saved data. No database change (Room stays at version 5).
- Changing the System UI or device settings. If the phone's "show media player in quick panel" option is off, the
  device check stops and asks the user.
- Raising `targetSdk` or `minSdk`.

## 4. Background: how the panel resume works (Media3 1.11.1 sources, verified)

- **Who serves what.** Only a `MediaLibrarySession` answers the System UI's recent-root request.
  - `MediaLibrarySessionImpl.onGetLibraryRootOnHandler` (`:142-160`) answers it when `params.isRecent` and the caller is
    the System UI.
  - It returns a recent root only if `canResumePlaybackOnStart()`, which is true when the app declares
    `MediaButtonReceiver`. We already declare it.
- **Filling the card.** `onGetChildrenOnHandler` (`:163-195`) answers the System UI's children request for that root.
  - While the player is `STATE_IDLE` it calls `getRecentMediaItemAtDeviceBootTime`, which calls
    `callback.onPlaybackResumption(session, controller, isForPlayback = false)` (`:442-455`).
  - It then returns the item at the returned start index (`:457-468`). A failed future becomes `ERROR_UNKNOWN`, and no
    card item is returned (`:470-477`).
  - While the player is not idle, Media3 returns a placeholder playable item and the System UI uses the live session.
- **Play on the card.** The System UI connects to the browser service, then sends Play to the session.
  - Play on an empty player goes through `onPlayRequested`, then `onPlaybackResumption(..., isForPlayback = true)`
    (`MediaSessionImpl.java:1295-1389`). This is exactly the path headset Play uses today.
- **The service type.** A `MediaSessionService` cannot serve any of this. The service must be a `MediaLibraryService`
  whose session is a `MediaLibrarySession`.
  - Its manifest entry must declare `androidx.media3.session.MediaLibraryService` and
    `android.media.browse.MediaBrowserService`, so the System UI's legacy `MediaBrowser` can bind.
- **Libraries are refused by default.** The default `MediaLibrarySession.Callback.onGetLibraryRoot`
  (`MediaLibraryService.java:200-203`) returns `ERROR_NOT_SUPPORTED`. Keeping that default is what keeps the library
  private (§3).

The side-effect problem from the 2026-10-14 discussion is unchanged. `PlaybackSessionCallback` treats every resumption
as a Play:

- it calls `RestoreCoordinator.savedForResumption()`, which moves the coordinator to `HANDED_TO_RESUMPTION` so the
  in-app restore stands down;
- it arms the saved shuffle order;
- on failure it calls `onNothingToResume` (the media-button fallback, ruling P6).

A metadata request that did any of this would leave the app opening to an empty player, arm a stale order, or stop the
service. 1.11.1's `isForPlayback` now tells the two cases apart, so the fix is a branch instead of approach 1's
id-matching.

## 5. Design

### 5.1 `PlaybackService` becomes a `MediaLibraryService`

- `PlaybackService : MediaLibraryService()`. `MediaLibraryService` extends `MediaSessionService`, so the lifecycle,
  notification and foreground behaviour, and every override (`onStartCommand`, `onTaskRemoved`, `onDestroy`), stay as
  they are.
- `installQueueRestore()` builds `MediaLibrarySession.Builder(this, player, callback).build()` instead of
  `MediaSession.Builder`.
- `onGetSession(controllerInfo): MediaLibrarySession` returns it. The field type becomes `MediaLibrarySession`.
- `PlaybackController` and the widget connect with `MediaController` against the same `SessionToken`. A
  `MediaLibrarySession` accepts plain controllers, so nothing changes for them.

### 5.2 Manifest (`core/media/src/main/AndroidManifest.xml`)

- The service's existing intent filter gains two actions:
  - `androidx.media3.session.MediaLibraryService`
  - `android.media.browse.MediaBrowserService`
- The `androidx.media3.session.MediaSessionService` action stays.
- No new permission, component or `exported` change.
- The merged release manifest diff against the committed state shows only those two actions.

### 5.3 `PlaybackSessionCallback` implements `MediaLibrarySession.Callback`

It keeps every current responsibility, and `onPlaybackResumption` branches on `isForPlayback`:

- **`true` (Play: in-app controller, widget, headset, the panel card's Play):** unchanged. It takes the first load or
  reads again (`savedForResumption`), arms the order, returns the items, and calls `onNothingToResume` on failure.
- **`false` (the System UI's metadata request): read only.**
  - It reads the saved playback through a new `RestoreCoordinator.peekSaved()`.
  - It returns `MediaItemsWithStartPosition(items, currentIndex, positionMs)` built with the same `toMediaItem()`
    mapping, so the card gets the title, artist, album and artwork URI.
  - It does not arm the order, set modes, touch the player, or call `onNothingToResume`.
  - With nothing saved, or on a load failure, it fails the future with `IllegalStateException`. Media3 turns that into
    "no card item".

`onGetLibraryRoot` and the other library callbacks are not overridden (§3).

### 5.4 `RestoreCoordinator.peekSaved()`

```kotlin
/** The System UI's metadata request (panel-resume spec §5.3): reads without settling [outcome]. Main dispatcher. */
suspend fun peekSaved(): SavedPlayback? =
    if (outcome == Outcome.LOADING) firstLoad.await() else loadOrNull()
```

- While loading, it shares the first read and keeps `outcome` at `LOADING`, so the in-app restore still applies when the
  load finishes.
- Once settled, it reads the database again, as `savedForResumption` does (ruling R6), so the card shows the latest
  save.
- It never calls `onSettled`.

### 5.5 The service's own lifecycle when the System UI binds

- The System UI's browser bind creates the service if the process is dead.
  - `onCreate` runs the in-app restore as for any other bind.
  - The service is bound, not started. When the System UI unbinds, the service is destroyed as before (`onDestroy`
    → `saveFinal`), with the same state.
- `MediaButtonStartFallback` is untouched. A bind is not a start, so neither P6 nor P8 applies to it.
- **Open question for the device (risk K1, §8):**
  - A restore prepares the player, and a prepared paused session posts its notification (Media3 1.8+ shows one for a
    prepared player).
  - If the System UI reacts to that session ending by asking again, the result could be a bind/destroy cycle.
  - The device check watches for it (§7.2). The fallback design is in §8.

## 6. Error handling and privacy

- **Every failure path fails the future.** Media3 then answers `ERROR_UNKNOWN` and the card does not get an item. That
  means no crash and no stuck future: the existing `invokeOnCompletion` cancel covers a cancelled scope.
- **Artwork.** The card's artwork URI is the MediaStore album-art URI (`content://media/external/audio/albumart/{id}`),
  which the System UI loads with its own permissions. If the device shows no art, the card still shows title and
  artist. That is accepted, recorded as a ruling, and not worked around by exposing our cache files.
- **Offline.** No network, no new permission, and no new data leaves the app. The System UI receives only the one item
  it asks for: the current track's title, artist, album and artwork URI. It already received that today through the
  notification.

## 7. Testing

### 7.1 JVM (Robolectric, real `MediaLibrarySession`)

Extend `PlaybackResumptionThroughSessionTest`, or add a sibling, with a `MediaLibrarySession`:

1. **The `false` request is read-only.** Call the callback with `isForPlayback = false` while the coordinator is
   `LOADING`, then let the load finish.
   - Assert the returned items, index and position match the saved queue.
   - Assert the coordinator ends `APPLIED`, not `HANDED_TO_RESUMPTION`.
   - Assert the restored order is the saved one, applied by the in-app restore, not armed twice.
   - Assert `onNothingToResume` was not called.
   - Mutation: route `false` through `savedForResumption()`, which turns it RED.
2. **`false` with nothing saved** fails the future and does not call `onNothingToResume`.
   - Mutation: call the fallback on the `false` failure path, which turns it RED.
3. **`true` is unchanged.** The two existing real-session tests (saved queue, nothing saved) pass against the library
   session.
4. **The recent root is served.** A `MediaBrowser` connected as the System UI cannot be impersonated on Robolectric, so
   this is a device check (§7.2). The JVM side pins only the manifest: `MediaButtonManifestTest`'s style of merged
   manifest assertion, with both new actions present.
   - Mutation: remove one action, which turns it RED.
5. **`peekSaved` unit tests in `RestoreCoordinatorTest`:**
   - while loading, it returns the first load and leaves `LOADING`;
   - once settled, it reads again;
   - it never calls `onSettled`.

### 7.2 Device (SM-S901B; safety rules as before)

The safety rules are the same as before: no uninstall, no data clear, no settings changes, and media keys only after
`media_button_receiver` names our app.

1. Install over the app (`install -r`) and check that `firstInstallTime` is unchanged. Open the app: the restore is
   paused and prepared.
2. **Card after death, wait route:** pause, Home, wait out the 10-minute hold, `am kill`.
   - Within 30 s the panel shows the Rolla Music card with the saved title and artist. Take a screenshot, and record
     whether the artwork shows.
   - Logcat shows the System UI's browser bind (`Start proc … for service`), an `onPlaybackResumption` metadata request
     (a debug log line added for the check, removed before commit), and no `requestCode=86` loop beyond the one P8
     restart.
3. **Card Play:** tap Play on the card.
   - It resumes the saved track near the saved position, `topResumedActivity` is the launcher, and the first Next
     follows the saved order.
4. **Swipe route:** pause, swipe away, `am kill`, and repeat steps 2 and 3.
5. **Metadata request without Play:** after step 2, open the app from the launcher without touching the card.
   - The restore is paused and prepared, with the real duration and the saved order. This proves criterion 3 on the
     device.
6. **Bind/destroy watch (K1):** 60 s of logcat after the card appears, with no taps.
   - `Start proc` / `MediaSessionImpl Init` must not repeat more than once per System UI request.
7. **Regression:** headset Play after a kill, force-stop while playing, swipe-away while playing, notification
   controls, and the D1 30 s watch.

## 8. Risks

| Risk | Mitigation |
|---|---|
| **K1:** a System UI bind starts a restore, which posts a session notification; when the bind ends, the System UI asks again (a cycle) | Device watch §7.2 step 6. If it cycles, the fallback is a ruling: run the in-app restore on the first controller connection that is not the System UI, instead of in `onCreate`. That needs a plan amendment and its own tests |
| One UI's quick-panel media player does not keep resume cards, or hides them by a setting | Stop and ask the user. Never change the setting ourselves |
| The card's artwork does not load (the System UI cannot read the album-art URI) | Accept title and artist only (§6), and record it as a ruling |
| The panel's Play starts the foreground service from the background and Android refuses it | Media3 1.9.1+ handles the not-allowed case. The device check §7.2 step 3 shows whether playback starts; if it does not, stop and report it with logcat |
| `MediaLibraryService` changes how plain controllers connect | `PlaybackController` and widget tests, plus the device regression |

## 9. Docs

- **`CLAUDE.md`:**
  - The Data Storage "Playback State" line, the Essential Features line, and the queue-restore banner drop "its carousel
    does not resume the queue after the process has died (that needs a `MediaLibraryService`, a non-goal)". They say
    instead that the panel card resumes the saved queue after the process has died.
  - The `:core:media` row in "What's shipped" says `PlaybackService (MediaLibraryService)`.
- **The queue-restore spec §12:** P7 gets a one-line amendment pointing here.

## 10. Amendment: device result (2026-10-15)

- **Samsung One UI does not deliver criteria 1 and 2.** On the SM-S901B (One UI, Android 14) the card goes when the process
  dies. System UI's `MediaDataManager` has media resumption on (`useMediaResumption: true`), but its internal listeners are
  `MediaTimeoutListener` and `MediaSessionBasedFilter` only. AOSP's `MediaResumeListener`, which checks each new media app
  through `MediaBrowserService` and adds it to the resume list, is not in the pipeline. The resume list holds a single
  2023 entry, and no browser bind to our service ever happened, so this is a property of the device, not of this design.
- **What the device did show:**
  - criterion 3: opening the app after a kill restores paused and prepared;
  - criterion 4: the full regression passed, including headset Play after a kill with the launcher on top, and the
    panel card while the session is alive;
  - D1/P8 watch: one restart per kill, then quiet.
  - K1 (System UI bind/destroy cycle): not exercised; One UI never binds.
  - The widget was not exercised on the device (criterion 4): no widget is placed, and placing one would change the
    user's home screen.
- **Unverified risks (stock Android).** A boot race: at boot the System UI binds the browser, `onCreate` runs the in-app
  restore, and if the player is prepared before the children request arrives, Media3 returns its placeholder recent
  item, which has no title (`MediaLibrarySessionImpl.java:176-189`). AOSP's resumption loader is recalled, not
  verified, to drop an untitled item. The card after process death is unaffected, because the System UI keeps the live
  session's metadata. Recorded, not fixed: there is no stock device to observe it on, and a fix would change the in-app
  restore, a §3 non-goal.
- **Final review (2026-10-17).** Its real-session test found that the metadata request always failed: Media3 rejects
  items without isBrowsable/isPlayable (`LibraryResult.verifyMediaItem`), so `getChildren` on the recent root answered
  `RESULT_ERROR_UNKNOWN`. Fixed on the false path by `asPlayableLeaf()` (2026-10-17); the player's own items are
  unchanged.
- **Decision (user, 2026-10-15):** keep this design, since it is the standard mechanism and correct on a stock System UI.
  A separate spike looks at a Samsung alternative: our own plain resume notification that survives process death.
- **Privacy wording (task 2 review).** Media3 1.11.1 answers `getChildren` on the recent-root id for any connected
  browser, not only System UI (`MediaLibrarySessionImpl.java:169-190`, gated only by `canResumePlaybackOnStart`). A
  third-party browser can therefore read the saved current track's title, artist, album and artwork URI. Any read-only
  `MediaController` could already read the same data from this exported service while it runs, so nothing new is exposed.
