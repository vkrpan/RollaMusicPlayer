---
type: ledger
status: history
date: 2026-10-12
plan: "docs/superpowers/plans/2026-10-12-queue-restore.md"
rulings: 17
deferred_minors: 12
tags: [ledger, rulings]
related: ["[[One UI redesign]]", "[[Timeline]]"]
---

# Ledger rulings — Queue restore

Every `Ruling:` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-10-12-queue-restore/progress.md` (git-ignored scratch, archived here verbatim on 2026-11-08 so it survives the
workspace's deletion). Plan: `docs/superpowers/plans/2026-10-12-queue-restore.md`. Each ruling reads
"what was decided — why — what it costs if wrong".

- Ruling P1: a failed load does NOT clear the saved state (amends spec §8's "the saved state is cleared") — a transient I/O or lock error must not destroy a good save; the repository already clears when no saved song survives — cost if wrong: a permanently corrupt save is re-read (and logged) on every service start until the next queue write replaces it.
- Task 1: minor (deferred): plan fixture rule "slot order != insertion order" is cosmetic for INTEGER PRIMARY KEY tables (rowid storage erases insertion order) — pin order with order-changing mutations instead; candidate observation for writing-plans.
- Task 1: minor (deferred): MigrationTest.kt:470 is 121 chars (pre-existing 4b line; spotless/check pass) — out of scope.
- Task 2: minor (deferred): RestoredPlayback.kt:15-17 KDoc says "rank order" — now (rank, slot) order; wording only.
- Task 2: minor (deferred): a saved currentSlot naming no row falls back to playbackOrder.first() at 0 — untested.
- Task 3: minor (deferred): generated baseline-prof.txt/startup-prof.txt still list buildMediaItem (harmless; regenerate at release).
- Task 3: minor (deferred): PlaybackService.kt:139-143 duplicates Int→RepeatMode mapping (toRepeatMode()); DRY follow-up — carry to Task 6 dispatch as optional.
- Task 3: minor (deferred): itAppliesOnceThenTheEnforcerOwnsLaterQueues' "enforcer owns later queues" half is unpinned (Media3's re-derived order already starts at window 2); the new control test covers enforcer behaviour.
- Task 3: minor (deferred): RestoredShuffleOrderTest.kt:94 assertNotEquals redundant; optional inline precondition guard in theSavedOrderSurvivesTheEnforcer.
- Ruling P2: QueuePersister writes the queue only on TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED; empty timeline clears for any reason — SOURCE_UPDATE fires on every item preparation and must not rewrite a 5,000-row queue; setShuffleOrder/replaceMediaItem report PLAYLIST_CHANGED — cost if wrong: a queue change Media3 reports under another reason is saved only at the next playlist change (state writes continue).
- Ruling P3: QueuePersister.finalState() is replaced by suspend saveFinal(), which merges the final State into the pending write and drains it inline on the main thread inside onDestroy's bounded runBlocking — a pending Queue write must not be overtaken by a final state that indexes the new queue against the old saved one — cost if wrong: Task 6 wiring must call saveFinal (carried); one extra API change vs the plan.
- Ruling P4: accept QueuePersister's writeMutex serialising the consumer's drain and saveFinal's inline drain — without it an older in-flight write can land after the final state; the consumer runs on serviceScope (Dispatchers.Default), so saveFinal's runBlocking on main can't deadlock against it — cost if wrong: onDestroy waits up to the 500 ms bound behind a slow write.
- Task 4: minor (deferred): P2's "Clear on an empty timeline for any reason" is unpinned — a clear-only-on-PLAYLIST_CHANGED mutant survives; one-line direct onTimelineChanged(Timeline.EMPTY, SOURCE_UPDATE) test would pin it.
- Ruling P5: accept PlaybackSessionCallback's invokeOnCompletion that cancels the future if the launch is cancelled before its body runs — the brief's version could leave Media3 waiting on a future that never completes (spec §8: never take playback down) — cost if wrong: none beyond one extra test; revert is trivial.
- Task 5: minor (deferred): PlaybackUpdateDispatcher.kt:47 suppression has no reason (pre-existing, out of scope).
- Ruling P6: when PlaybackService was started by a media-button intent and onPlaybackResumption has nothing to resume (null or failure), the service posts a minimal silent notification (Media3 small icon, low-importance channel), startForeground(type mediaPlayback), then stopForeground(REMOVE) + stopSelf; never when started by bind/MediaController (31+ background FGS start would throw) — amends spec §7.6 ("Media3 then does nothing, which is correct" is wrong on API 26+) — cost if wrong: a brief silent notification flash on an empty-save headset Play; extra class + constructor param on PlaybackSessionCallback.
- Task 6: minor (deferred): PlaybackSessionCallback catch path — if onNothingToResume() throws, setException is skipped (future still cancelled via invokeOnCompletion; FGS timeout possible); optional wrap.
- Task 6: minor (deferred): PlaybackService onStartCommand override + onNothingToResume wiring have no JVM test — Task 9 device case (c) must run.
- Ruling: I1 fix — enable() enqueues a queue write when the timeline is non-empty — restore rewrites the queue once and drops dead rows — cost if wrong: one extra write per start.
- Ruling: M3 fix with I1 — a failed queue write marks the queue dirty so the next state write is upgraded to a queue write — same root as I1 — cost if wrong: one extra full write after a failure.
- Ruling: I2 fix — a non-DOWN media-button start with nothing ongoing satisfies the foreground contract at once, never stops an in-flight resumption, and does not leave the service started with an empty player — constraints: never take playback down — cost if wrong: a brief silent notification flash per key UP.
- Ruling: I3 docs only — reword CLAUDE.md and the spec to headset/Bluetooth plus System UI while the session lives; System UI carousel resumption after process death is NOT delivered (needs MediaLibraryService, spec §4 non-goal) — surface to user at finish as an unmet part of their "include system resume" choice, with the conversion offered as a follow-up — cost if wrong: user expected carousel resume and gets it only on request.
- Ruling: I4 — device Step 5 uses am force-stop then launcher reopen; carry (b) dropped as saveFinal pin (JVM tests pin it); swipe-away kept only as a no-crash check — cost if wrong: saveFinal has no device evidence.
- Ruling: M1 fix comment (bound covers the lock wait, not Room's write); M2 accepted (millisecond window) — cost if wrong: a just-cleared queue reappears on a resumption racing the clear.
- Ruling: T2 untested 'saved slot names no row' — add test (I1 makes path live); T2 KDoc 'rank order' word — fix. Other deferred minors stay per reviewer triage.
- Ruling: N1 parked — a state write enqueued while a failing queue write is in flight drains as a plain state write; only on an already-failed write, heals at the next trigger — cost if wrong: one restart after a failed DB write lands on the wrong track.
- Ruling: N2 parked — UP-first start then HANDED resumption that finds nothing leaves the service started with an empty player until Android's background limits stop it — cost if wrong: an idle started service for about a minute.
- Ruling: N3 fixed by controller — plan Task 9 step 6.2 reworded (key-UP branch with resumption in flight; cold UP-only path pinned by MediaButtonStartFallbackTest).
- Ruling: D1 fix — a media-button start that is the first start of a fresh service and is not a play key (e.g. the notification deleteIntent STOP) stops the service once the restore settles, when nothing is playing and no resumption is in flight (stopSelf(startId)); STOP semantics are not changed — the next app open then gets a fresh, prepared restore — cost if wrong: the app opened within ~0.3 s of a STOP restart still sees an unprepared player until Play.
