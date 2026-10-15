# Queue Restore Design

**Date:** 2026-10-12
**Branch:** `feat/oneui-redesign` (a side phase between redesign Phases 4b and 5; merges to `main` with the redesign)
**Status:** Approved 2026-10-12. Amended 2026-10-13 by the implementation rulings in §12; amended text cites its ruling.

## 1. Problem

The playback queue, the current track, the position, and the shuffle and repeat state live only inside
`PlaybackService`'s ExoPlayer. Nothing saves them. When the process dies (an app update, swiping the app away, a
force-stop, the OS reclaiming memory) they are gone: the mini-player is empty on the next launch and headset Play does
nothing. This was seen on the SM-S901B on 2026-10-12, when the Phase 4b `install -r` killed the process.

CLAUDE.md claims "Persistent playback state across app restarts" (Core Features) and "Playback State: Persisted locally
for session restoration" (Data Storage Strategy). Both are false today. This design makes them true.

## 2. Goal and success criteria

After the process dies and the app is opened again:

1. The mini-player shows the last track, **paused**, at the saved position (within about 10 s if the process died while
   playing).
2. The queue sheet shows the same queue in the same playback order, the exact shuffled order included.
3. Shuffle and repeat are as they were.
4. Pressing Play continues from that point.
5. A headset or Bluetooth Play, or the System UI media carousel, resumes the saved queue **without opening the app**.

**Gap (P7):** criterion 5 is met for a headset or Bluetooth Play only. The System UI controls work while the session is
alive, but its carousel does not resume the queue after the process has died (that needs a `MediaLibraryService`, §4).

The app never starts playing by itself when it is opened.

## 3. Decisions (from the 2026-10-12 conversation)

| Question | Decision |
|---|---|
| What is restored | Full state: queue, current track, position, shuffle on/off with the exact shuffled order, repeat mode |
| Resume from outside the app | Yes: `MediaButtonReceiver` + `MediaSession.Callback.onPlaybackResumption` |
| Position save cadence | On pause, seek, track change, queue change and mode change, plus every 10 s while playing |
| Storage | Room v5 (option A). DataStore (B) and saving Media3 item bundles (C) were rejected: B needs a hand-written list codec plus a new lookup DAO anyway; C restores stale titles after tag edits and keeps deleted tracks |
| Branch | `feat/oneui-redesign`, its own spec and plan, not a redesign roadmap phase |

## 4. Non-goals

- Restoring the playback **speed** or **skip silence**. These are settings and already persist in DataStore
  (`PlaybackSettingsBinder` re-applies them).
- Converting `PlaybackService` to a `MediaLibraryService`, or Android Auto browsing. A `MediaSessionService` with a
  media button receiver is enough for headset and Bluetooth resumption; without the library service, the System UI
  carousel does not resume after the process has died (P7).
- Restoring after the user **clears** the queue. An empty queue clears the saved state (§6.3).
- Any network, cloud or cross-device sync. Everything stays in the app's local Room database.

## 5. Data layer (`:core:database`, Room v5)

### 5.1 Tables

`playback_queue`: one row per queue entry.

| Column | Type | Notes |
|---|---|---|
| `slot` | `INTEGER NOT NULL PRIMARY KEY` | The Media3 window index, 0-based, contiguous when written |
| `song_id` | `TEXT NOT NULL` | The song's id. No foreign key and no index (R2): a song deleted since the save stays readable as missing |
| `shuffle_rank` | `INTEGER NOT NULL` | The slot's 0-based position in the shuffle order. A permutation of `0 until count` when written |

`playback_state`: a single row.

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER NOT NULL PRIMARY KEY` | Always `0` |
| `current_slot` | `INTEGER NOT NULL` | The current item's slot |
| `position_ms` | `INTEGER NOT NULL` | Position within the current item, ≥ 0 |
| `shuffle_enabled` | `INTEGER NOT NULL` | 0 or 1 |
| `repeat_mode` | `TEXT NOT NULL` | The `RepeatMode` enum name (`OFF`, `ONE`, `ALL`) |
| `saved_at` | `INTEGER NOT NULL` | Epoch millis of the last write, for diagnostics |

(R2) There is no foreign key. When a rescan deletes a song, its queue row stays, with its slot and shuffle rank, and the
load's `LEFT JOIN` reports it as missing (`song == null`). That keeps §7.4's "next survivor in playback order" computable
when the current song itself was deleted. Rows of deleted songs live only until the next queue write, and slot gaps are
closed on restore (§7.4), not in the database.

### 5.2 Migration

`MIGRATION_4_5` creates both tables, empty (no index, R2). No existing table or row is touched. It is
appended to `ALL_MIGRATIONS`. `MusicDatabase` goes to `version = 5` and the exported schema `5.json` is committed.

### 5.3 DAO

A new `PlaybackQueueDao`. `SongDao` is at detekt's interface function threshold.

- `replaceQueue(rows: List<PlaybackQueueEntity>, state: PlaybackStateEntity)`: one `@Transaction` that deletes every
  queue row, inserts `rows`, and upserts `state`.
- `upsertState(state: PlaybackStateEntity)`.
- `loadQueue(): List<QueuedSongRow>`: `playback_queue LEFT JOIN songs ON songs.id = playback_queue.song_id ORDER BY
  slot` (R2). Each result carries its `slot`, `shuffle_rank`, `song_id` and the `SongEntity`, or null when the song is
  gone.
- `loadSnapshot(): PlaybackSnapshot`: the queue and the state in one `@Transaction` (R8).
- `loadState(): PlaybackStateEntity?`.
- `clear()`: deletes both tables' rows in one transaction.

### 5.4 Repository and model (`:core:data`, `:core:model`)

`SavedPlayback` (`:core:model`):
- `songs: List<Song>`, in queue (slot) order
- `shuffleOrder: List<Int>`: a permutation of `songs.indices`
- `state: SavedPlaybackState(currentIndex: Int, positionMs: Long, shuffleEnabled: Boolean, repeatMode: RepeatMode)` (R4)

`PlaybackStateRepository` (`:core:data`, bound in `DataModule`):
- `suspend fun load(): SavedPlayback?`: null when nothing is saved or every saved song is gone. Applies §7.4.
- `suspend fun saveQueue(songIds: List<String>, shuffleOrder: List<Int>, state: SavedPlaybackState)`: the whole queue
  plus its state, via `replaceQueue` (R4: the persister holds media ids, not `Song`s). Throws if `shuffleOrder` is not a
  permutation of the slots.
- `suspend fun saveState(state: SavedPlaybackState)`.
- `load()` reads through `loadSnapshot()` and the pure `restoredPlayback(rows, state)` (R8). A load that throws is not
  cleared (P1).
- `suspend fun clear()`.

## 6. Save path (`:core:media`)

### 6.1 `QueuePersister`

A `Player.Listener` that `PlaybackService` registers beside `QueuePublisher`. It reads player state on the player's
application thread (main) and writes through `PlaybackStateRepository` on `serviceScope` (Dispatchers.Default).

| Event | Write |
|---|---|
| `onTimelineChanged` with reason `PLAYLIST_CHANGED` (a new queue, add/remove/reorder, a new shuffle order) (P2) | Queue and state |
| `onTimelineChanged` to an empty timeline, any reason (P2) | Clear |
| `onIsPlayingChanged(false)` | State |
| `onPositionDiscontinuity` (seek, auto-advance) | State |
| `onShuffleModeEnabledChanged`, `onRepeatModeChanged` | State |
| Every 10 s while playing (driven from the existing 1 s position ticker) | State |
| `PlaybackService.onDestroy`, before `player.release()` | `saveFinal()`: the final state folds into any pending write, drained inline under a write mutex, bounded to 500 ms (R7, P3, P4) |

The shuffle order is read from the timeline: `shuffle_rank` of slot *i* is *i*'s index in the shuffled playback order
(`Timeline.getFirstWindowIndex(true)` then `getNextWindowIndex(…, REPEAT_MODE_OFF, true)`, the same walk `queueState()`
already does).

### 6.2 Coalescing

Writes are conflated per kind: while a queue write is pending or running, newer queue writes replace it, and the same
for state writes. Playing a 5,000-track list therefore costs one queue write, not one per callback. A queue write
includes the state, so a pending state write that is older than a queued queue write is dropped.

### 6.3 Rules

- An **empty** timeline clears the saved state, so a deliberately emptied queue stays empty after a restart.
- The persister writes nothing while the restore (§7) is `Loading`. Any settled state (`Applied`, `Discarded` or
  `HandedToResumption`) enables it. Otherwise the service's initial empty player could clear the saved state before it
  is read.
- Enabling writes the queue already in the player at once (final review I1): the applied restore, which has dropped
  the slots of missing songs, or a queue that arrived while the restore was loading. Later states index the player's
  queue, so the saved rows must be that queue, not the older save.
- A failed queue write marks the queue dirty, and the next state write (a trigger or `saveFinal`) is upgraded to a
  queue write; a queue write that lands clears the flag (final review M3).
- Any write failure is logged and dropped, like the service's other background work. It never affects playback.

## 7. Restore path (`:core:media`)

### 7.1 One shared restore

`PlaybackService.onCreate` starts one restore, owned by a `RestoreCoordinator`. It loads `SavedPlayback` once
(a `Deferred`, off the main thread). Both consumers below await that same result, so the database is read once per
service start.

The coordinator's states: `Loading` → one of `Applied`, `Discarded` or `HandedToResumption`. The transition is decided
on the main thread.

### 7.2 Opening the app (in-app restore)

When the load completes, on the main thread:

- If the player already has items (anything set a queue first) or a resumption is in flight → `Discarded`. **The user's
  action wins.**
- If the load returned null → `Discarded`.
- Otherwise → `Applied`, in this order:
  1. Arm `RestoredShuffleOrder` with the saved order (§7.5)
  2. `repeatMode`, `shuffleModeEnabled` (shuffle on an empty player makes the enforcer's shuffle trigger a no-op)
  3. `setMediaItems(items, currentIndex, positionMs)`, in the same main-thread block as step 1
  4. `prepare()`. Never `play()`.
  (R3: arming and the modes come before the items, so the saved order lands after the enforcer's reshuffle.)
- `PlaybackStateHolder` gets the restored position directly, because `setMediaItems` with a start position does not
  report a discontinuity to the 1 s ticker while paused.

The mini-player then shows the restored track, paused. Queue sheet, widget and Now Playing read the same holder, so they
follow.

### 7.3 Play pressed during the restore

(R1, replaces the `ForwardingPlayer` gate.) Media3 1.4.1's `MediaSessionImpl.handleMediaControllerPlayRequest` checks for
a current media item; with none, it calls `Callback.onPlaybackResumption`, sets the returned items and plays. Our own
`MediaController` (the app, the widget), media buttons and the System UI controls (while the session is alive; see P7)
all reach it. So a Play that arrives while the
restore is loading goes to `onPlaybackResumption`, which takes the shared first load (§7.6) and marks the coordinator
`HandedToResumption`; the in-app restore then stands down. No gate is needed.

### 7.4 Missing songs

The `LEFT JOIN` marks rows whose song no longer exists (R2). `load()` then:

1. Renumbers the surviving songs `0 until n` in slot order.
2. Rebuilds the shuffle order from the surviving rows' `shuffle_rank`, in ascending order, which keeps their relative
   order.
3. If the saved current song survived, keeps it at the saved position. Otherwise the current item becomes the next
   surviving song in **playback order** (shuffle order when shuffle is on, slot order when it is off), at position 0.
   With shuffle on, "next" compares (rank, slot), the same tie-break as the restored shuffle order.
   If none follows, it becomes the first surviving song in playback order.
4. If no song survived, it returns null and clears the saved state.

A saved position past the end of the current file (the file was replaced) is left to Media3, which clamps it.

### 7.5 Keeping the exact shuffle order

`StartFirstShuffleEnforcer` reorders the shuffle order to start at the current item whenever the playlist is replaced
or shuffle turns on. A restored mid-queue position almost never starts the order, so the enforcer would overwrite the
saved order.

The restored order is therefore applied by a one-shot `RestoredShuffleOrder` listener, **registered after the
enforcer**. When a restore (in-app or resumption) is about to set items, it is armed with the saved order. On the next
`onMediaItemTransition(PLAYLIST_CHANGED)` it applies `DefaultShuffleOrder(savedOrder, seed)` with `setShuffleOrder` and
disarms; a length mismatch makes Media3 throw `IllegalArgumentException`, which it logs, keeping the enforcer's order. Being registered after the enforcer, it runs
after it, so the saved order wins. `setShuffleOrder` fires no media-item transition, so nothing re-enters. The enforcer
itself is unchanged and keeps working for every later queue the user starts.

### 7.6 Resuming from outside the app

- Manifest (`:core:media`):
  - Add `androidx.media3.session.MediaButtonReceiver`, exported, with the `android.intent.action.MEDIA_BUTTON` action.
  - Add `android.intent.action.MEDIA_BUTTON` to `PlaybackService`'s intent filter.
- `MediaSession.Builder` gets a `Callback` whose `onPlaybackResumption` marks the coordinator `HandedToResumption`,
  awaits the shared load, arms `RestoredShuffleOrder`, sets `repeatMode` and `shuffleModeEnabled` on the player, and
  returns `MediaItemsWithStartPosition(items, currentIndex, positionMs)`. Media3 sets the items and starts playback.
- When nothing is saved, it returns a failed future, and Media3 plays nothing. If the service was started by a media
  button, `MediaButtonStartFallback` first enters and leaves the foreground with a silent notification and stops the
  service, because Media3 posts no notification for an empty player and the foreground-start contract would otherwise
  crash it on API 26+ (P6).
- A resumption arriving after the restore has settled reloads from the database rather than reusing the first load, so
  a queue cleared later is not resurrected (R6). The future is always completed: cancelled if its coroutine is
  cancelled before it runs (P5).

- `MediaButtonReceiver` filters on the key code only, so a headset's key UP also starts the service, while Media3 acts
  only on ACTION_DOWN. A non-DOWN key start with no playback ongoing enters and leaves the foreground at once, never
  stops a service an earlier start owns (a DOWN's resumption may be running), and, if it made the service's first
  start, stops the service with its own start id once the restore settles with nothing to play (final review I2).
- A DOWN of any other key (not Play, Play/Pause or Headset hook) arrives through `startService`: the media
  notification's deleteIntent STOP, which the system fires when it cancels a dead process's notification. It owes no
  foreground and never calls `startForeground`, and it leaves the media-button record alone. If it made the service's
  first start, the service stops with that start id once the restore settles with nothing playing and no resumption in
  flight; an `APPLIED` restore still stops, so the next app open creates a fresh service with a prepared restore (P8).

A headset or Bluetooth Play reaches the service through `MediaButtonReceiver` even after the process has died. The
System UI media carousel does **not** resume the queue after process death (P7): it binds
`android.media.browse.MediaBrowserService` and asks for a recent root, which only a `MediaLibraryService` answers, and
that conversion is a non-goal (§4). Its controls work while the session is alive.

## 8. Error handling

| Failure | Behaviour |
|---|---|
| Load throws (a corrupt row, an I/O error) | Log it; the coordinator goes to `Discarded`; the app starts with an empty queue; the saved state is **kept** (P1: a transient error must not destroy a good save) |
| Save throws | Log it and drop it; the next event writes again |
| A restored file fails to play (deleted outside MediaStore since the last scan) | Media3's existing error path; no new handling |
| The `onDestroy` save outlives its bound | Abandoned; at most the last ~10 s of position is lost |

## 9. Testing

Every test names its mutation, the mutation is proved RED and reverted byte-exact (project policy). Fixtures separate
every quantity a test distinguishes: slot order ≠ shuffle order ≠ title order ≠ insertion order.

**Instrumented (`:core:database` androidTest), run on the SM-S901B before the upgrade install:**
- `MigrationTest`: `migrate4To5_createsEmptyPlaybackTablesAndKeepsEverything`, and the v1→v5 chain test updated.
- `PlaybackQueueDaoTest`:
  - `replaceQueue` replaces atomically; a failing insert leaves the previous queue intact.
  - The join orders by slot, not by insertion or title.
  - A missing song comes back as a slot with no song (R2); duplicate songs keep their own slots.
  - Favourites and Equaliser presets survive 4→5 (constraints).
  - The state row stays single.
  - `clear` empties both tables.

**JVM / Robolectric:**
- `PlaybackStateRepository`:
  - Mapping both ways.
  - §7.4 renumbering, shuffle-rank compaction, and the current-song fallback with shuffle on and off.
  - The all-missing case returns null.
- `QueuePersister`:
  - Each trigger in §6.1 writes the right kind.
  - Coalescing (§6.2).
  - An empty timeline clears.
  - Nothing is written before the restore settles (§6.3).
- `RestoreCoordinator`:
  - Applied, Discarded when items arrived first, Discarded on null, HandedToResumption.
  - A discarded restore leaves no trace (nothing armed, modes untouched); a resumption after settling reloads (R6);
    a failed load is not cleared (P1).
- `PlaybackSessionCallback`: returns the saved items, index and position and restores order and modes; fails the future
  when nothing is saved; cancels it when its coroutine is cancelled (P5).
- `RestoredShuffleOrder`: the saved order survives the enforcer (both listeners on one player, in production order,
  with a control proving the enforcer fired); a seek does not consume it; it is one-shot.
- (R1 removed the held-Play gate and its tests.)

**Device acceptance (SM-S901B), captured to `docs/design/calibration/queue-restore/`:**
1. Run the connected `:core:database` tests; all must pass before installing.
2. Install with `install -r` over the 4b build. Playlists, favourites and the Folders tab are unchanged.
3. Shuffle the Music folder, skip twice, seek to about 1:23, pause, then `am kill com.rolla.musicplayer`. Reopen: the
   same track, paused at about 1:23; the queue sheet shows the same order.
4. Play, wait 15 s, kill while playing. Reopen: the position is within 10 s of where it was.
5. Kill again, then `input keyevent KEYCODE_MEDIA_PLAY`: playback resumes without the app opening, in the saved
   shuffled order.
6. Clean up: pause playback.

## 10. Documentation

- CLAUDE.md: reword line 106 ("Playback State") and line 139 (Core Features) to say what is saved: queue, current
  track, position, shuffle order, and repeat; restored paused; resumable from a headset or the system media controls.
  Add a "Queue restore" bullet to the redesign status banner.
- `.claude/rules/model-vocabulary.md`: add `SavedPlayback`.
- The Room version references (v4 → v5) in CLAUDE.md.

## 11. Constraints

- Fully offline. No new dependency beyond what Media3 1.4.1 and Room 2.6.1 already provide.
- `Song` stays the model name. "Tracks" applies only to UI copy, and this feature adds no UI copy.
- No hardcoded visual values. This feature changes no UI.
- `:core:media` already depends on `:core:data`; `:feature:*` modules do not change.

## 12. Implementation rulings (2026-10-13)

Decided while planning and executing; each amends the section that cites it.

- **R1** No `ForwardingPlayer` gate; Media3 routes a Play on an empty player to `onPlaybackResumption` (§7.3).
- **R2** No foreign key on `playback_queue`; `LEFT JOIN` at load (§5.1, §5.3, §7.4).
- **R3** Restore order: arm, repeat, shuffle, items, prepare (§7.2).
- **R4** `SavedPlayback(songs, shuffleOrder, state: SavedPlaybackState)`; `saveQueue(songIds, shuffleOrder, state)` (§5.4).
- **R5** State saves skip an empty player; only an empty timeline clears.
- **R6** A resumption after the restore has settled reloads from the database (§7.6).
- **R7** The final save in `onDestroy` is bounded to 500 ms.
- **R8** `loadSnapshot()` reads in one transaction; `restoredPlayback` is a pure function (§5.3, §5.4).
- **R9** No `onMediaItemTransition` trigger in the persister: every transition also brings a discontinuity or a
  timeline change (§6.1).
- **P1** A failed load does not clear the saved state (§8).
- **P2** The queue is written only on `TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED`; `SOURCE_UPDATE` fires on every item
  preparation and must not rewrite the queue (§6.1).
- **P3** `saveFinal()` folds the final state into any pending write and drains it inline (§6.1).
- **P4** A write mutex serialises the persister's consumer and `saveFinal`; the consumer runs on `Dispatchers.Default`.
- **P5** The resumption future is cancelled if its coroutine is cancelled before running (§7.6).
- **P6** When the service was started by a media button and there is nothing to resume, it briefly enters the
  foreground with a silent notification on Media3's channel and stops itself; Media3 1.4.1 would otherwise leave the
  foreground-service contract unmet and crash on API 26+ (amends §7.6's "Media3 then does nothing").
- **P7** System UI carousel resumption after process death is not delivered. System UI binds
  `android.media.browse.MediaBrowserService` and asks for a recent root (`BrowserRoot.EXTRA_RECENT`); in Media3 1.4.1
  only `MediaLibrarySessionImpl` answers it (`MediaLibrarySessionImpl.java:135`, `params.isRecent`), and
  `PlaybackService` is a `MediaSessionService` (§4 non-goal). Headset and Bluetooth Play resume through
  `MediaButtonReceiver`; the System UI controls work while the session is alive (amends §2 criterion 5, §7.3, §7.6).
- **P8** A notification-delete STOP restart stops the restarted service instead of changing STOP (device check D1).
  When the process dies while paused, the system cancels its media notification and fires the deleteIntent, which
  Media3 1.4.1's `DefaultActionFactory` builds with `PendingIntent.getService` as an `ACTION_MEDIA_BUTTON`
  `KEYCODE_MEDIA_STOP` DOWN. That start creates a fresh `PlaybackService`; the restore applies (`setMediaItems`,
  `prepare`), then Media3 dispatches the STOP (`MediaSessionImpl.onMediaButtonEvent` → `stopForControllerInfo` →
  `player.stop()`). The player stayed IDLE with its items, and the service stayed started in the background, so a later
  app open bound to it and showed a 0:00 duration until Play. STOP keeps Media3's default meaning (a STOP while
  playing still stops). Instead, a media-button first start whose key is not a play key, with nothing playing and no
  resumption in flight (the coordinator is not `HANDED_TO_RESUMPTION`; `APPLIED` does not block it), calls
  `stopSelf(thatStartId)` once the restore settles, so a later start keeps the service alive. Such a start never calls
  `startForeground` (it came through `startService`; on API 31+ a background `startForeground` throws). `onDestroy`
  then runs `saveFinal`, and the next open creates a fresh service whose restore is prepared. If the stop's destroy
  runs before Media3's posted STOP, `MediaSessionImpl.release()` removes that posted dispatch (amends §7.6).
- **R1, R3, P2, P6, P8 (Media3 1.11.1)** Re-verified on 2026-10-15 against the 1.11.1 sources; none changed or retired,
  and the 1.4.1 facts they cite still hold. A Play on an empty player still reaches `onPlaybackResumption`, now with
  `isForPlayback = true` and after `onPlayRequested`, which resolves true for an empty player on every API level
  (`MediaSessionImpl.java:1295-1345`, `MediaSessionService.java:995-1008`). `MediaButtonReceiver` now drops a key UP
  itself (`MediaButtonReceiver.java:144-150`), so the key-UP branch of `MediaButtonStartFallback` (§7.6, final review
  I2) is redundant for the receiver; it stays for a direct start of the exported service. The deleteIntent is still a
  `getService` STOP and now also marks the notification dismissed (`DefaultActionFactory.java:116-123`). Since Media3
  1.6 a paused service stays in the foreground for 10 minutes (`MediaNotificationManager.java:275-286`,
  `MediaSessionService.java:186`). P8's trigger, the deleteIntent STOP on process death, therefore needs a process the
  system can kill: after that hold has expired, or straight after the task is swiped away while paused. The swipe ends
  the hold (`pauseAllPlayersAndStopSelf`), but the app's in-process MediaController binding keeps the service and its
  paused notification alive, so a later kill still fires the deleteIntent. The SM-S901B showed one `requestCode=86`
  restart on both routes, stopped by P8. A Robolectric test drives the resumption through a real `MediaSession`
  (`PlaybackResumptionThroughSessionTest`).
