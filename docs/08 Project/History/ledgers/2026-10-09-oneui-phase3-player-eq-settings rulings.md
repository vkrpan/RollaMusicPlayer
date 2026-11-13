---
type: ledger
status: history
date: 2026-10-09
plan: "docs/superpowers/plans/2026-10-09-oneui-phase3-player-eq-settings.md"
rulings: 23
deferred_minors: 25
tags: [ledger, rulings]
related: ["[[One UI redesign]]", "[[Timeline]]"]
---

# Ledger rulings — One UI Phase 3 — player, Equaliser, Settings

Every `Ruling:` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-10-09-oneui-phase3-player-eq-settings/progress.md` (git-ignored scratch, archived here verbatim on 2026-11-08 so it survives the
workspace's deletion). Plan: `docs/superpowers/plans/2026-10-09-oneui-phase3-player-eq-settings.md`. Each ruling reads
"what was decided — why — what it costs if wrong".

- Task 1: minor (deferred): OneUiSeekBarTest:72 mutation comment names the wrong probe; add a mutation that skips the active drawRoundRect for the left probe
- Task 1: minor (deferred): NowPlayingBackgroundTest:35 comment still says drawWithCache
- Task 1: minor (deferred): OneUiSeekBar thumb is untested (color and size); add a pixel probe at the thumb centre
- Task 1: minor (deferred): OneUiSeekBar's unused interactionSource remember
- Task 2: TERMINATED (monthly spend limit, HTTP 429) after the implementation; last words: 25 new/extended tests GREEN (91 in module), mutations not yet run, no report. Work is in the tree (core/media: Queue.kt, tests, holder, controller, service, build.gradle.kts). Ruling: resume the same agent to finish the mutations, gates and report. Why: its context is intact and the partial work is its own. Cost if wrong: a fresh agent would need to re-derive it.
- Ruling: finding 3 extracts internal class QueuePublisher(player, holder) : Player.Listener, registered by the service before the enforcer and tested with the real ExoPlayer. Why: it is the only way to pin ruling 1 without a service harness, and it mirrors the StartFirstShuffleEnforcer precedent. Cost if wrong: one small class.
- Ruling: finding 4 drops the redundant `index != C.INDEX_UNSET` clause (the range check subsumes it) and adds the range-escape test. Why: a deliberately equivalent clause is a policy defect. Cost if wrong: none (identical behaviour).
- Task 2: minor (deferred): Queue.kt getNextWindowIndex repeat-mode mutant survives (add a REPEAT_ONE real-player test)
- Task 2: minor (deferred): the seed-1 kill is fragile across Media3 upgrades (add a precondition on the pre-enforcement order)
- Task 2: minor (deferred): the playFrom id-vs-equality mutant survives (use a copy with a different title)
- Task 2: minor (deferred): onDestroy does not reset the queue (stale queue outlives the service; same as currentSong today)
- Task 2: minor (deferred): queueState is O(N) 3-4x per list change on the main thread; measure at the gate with the full library
- Task 2: minor (deferred): cycleRepeatMode test covers only OFF->ALL
- Task 2 fix round 1 done (af05936a60709b496): 94/94 tests, 8 mutations RED; added step (d) shuffle-off restore. Ruling: accept (d). Why: it kills the onShuffleModeEnabledChanged deletion, which otherwise survives. Cost if wrong: none. Tree 2fcc5dd..359f44fe5746f8adcaafe7709979529cfe60a0aa
- Task 2: minor (deferred): the QueuePublisher test depends on MEDIA3_ORDER_SEED=1; add a comment that a seed change needs the onTimelineChanged mutation re-proved
- Task 3: minor (deferred): the empty-list tests' comments name only the playAll guard mutation, not the shuffleAll one
- Task 4: minor (deferred): accentText title and EqualizerBars glyph on the current row are untested
- Task 4: minor (deferred): QueueViewModel connect() is untested
- Task 4: minor (deferred): the current row's TalkBack cue is only "Selected"; consider stateDescription "Now playing"
- Ruling: Minor 3 (title ellipsis dropped, so a long title is clipped mid-glyph under reduced motion) is raised to Important and enters fix round 1. Why: it regresses behaviour that existed before this task (ellipsis), and the user-visible fix is one line. Cost if wrong: one line.
- Task 5: minor (deferred): the mergeDescendants substitution is accepted; merging itself is unpinned
- Task 5: minor (deferred): MainActivity.kt:211 single-line if without braces (kotlin-style)
- Task 5: minor (deferred): the reduced-motion marquee branch, art height and circle clip are untested
- Task 5: minor (deferred): reduced-motion test does not reset ANIMATOR_DURATION_SCALE in @After (Robolectric isolates it)
- Task 6: minor (deferred): the songRepository relaxed mock needs a default observeSong(any()) -> flowOf(null) stub (future-test trap)
- Task 6: minor (deferred): toggleFavorite and addCurrentSongToPlaylist read the WhileSubscribed currentSong cache (safe while the UI collects it)
- Ruling: accept spacing deviation (single fillMaxSize().verticalScroll Column + weighted spacer; BoxWithConstraints heightIn guard proven dead by mutation; controlsArePushedToTheBottom pins it). Why: simpler, pinned. Cost if wrong: one wrapper.
- Ruling: accept seekbar heightIn(min=minTouchTarget) + label offset from tokens instead of fixed 48 dp Box. Why: Review Focus 4 (font scale 2) put labels on the thumb; identical at scale 1. Cost if wrong: revert to Box.
- Ruling: accept substitution of album/artist callback swap (does not compile) with menu-label swap.
- Task 7: minor (deferred): metadata fade keeps tween(220), not rollaSpringOrSnap
- Ruling: Minors 1-6 go into fix round 1 anyway. Why: 1 guards a binding rule (accentText never primary; the old code used primary in exactly these spots), 3 is a coincident fixture (policy: distinguished quantities must differ), 4 is user-visible (menu left over the pushed screen); 2/5/6 are one-line assertions. Cost if wrong: one small test round.
- Task 7: minor (deferred): RepeatMode.ONE tint has no pixel test (covered by != OFF construction); PlayOrder/Repeat icon swap left to device check
- Ruling (Task 8): slider TalkBack descriptions stay byte-identical ("40Hz gain"...) via a separate list; dropping Hz is visual only. Why: brief's "40 Hz gain" guess + label change would silently change spoken text. Cost if wrong: one list.
- Ruling (Task 8): PillChip onLongClickLabel carry-over lands here ("Delete preset" on user chips). Why: Task 1 deferred minor; only consumer is EQ. Cost: one param.
- Ruling (Task 8): keep immediate long-press delete and no-op Custom chip tap; raise Custom tap (possible dead control) to the user at the gate. Why: behaviour change is out of a restyle's scope.
- Ruling: accept deviations (matchParentSize on AnimatedVisibility, curve fillMaxSize; EqualizerPresetGrid.kt split; curve fill/centre line/enabled removed, points at column centres; dialog buttons accentText; scroll-before-bounds). Why: brief as written sized the curve 0x0; rest follow binding rules.
- Ruling: Minors 1 (curve springs while thumb snaps, detaches on drag), 2 (overlay framed on labels: 0 dB line ~1 dp off, misses extreme thumbs at fontScale 2) and 3 (enable-row label/selectableGroup untested) join fix round 1. Why: 1-2 are user-visible on the one moment the curve exists; 3 is two assertions. Cost: small.
- Task 8: minor (deferred): report wording on snapping other bands mid-sweep (negligible)
- Ruling: accept slider-test substitution (swipe from centre; brief's swipeRight started outside the reported bounds). Accept Licenses LazyColumn -> Column (fixed 15 entries, one card), About top-aligned card, "Navigate up" default description.
- Task 9 review (ae6170af280efda8b): spec ✅; Changes requested. Ruling A: Change (borrowed tokens; add settingsSectionGap 24, settingsHeaderGap 8, settingsProgressSize 20, settingsProgressStroke 2, dialogOptionLabelGap 12, settingsRowVerticalPadding 16). Ruling B: Change (heightIn(min = singleLineRowHeight) + vertical settingsRowVerticalPadding). Important: slider test blind to committed value and optimistic pending value; ruling B needs a test. Minor: SettingsScreen gating/rescan/theme-dialog/heading tests; rename settingsRowPadding() ext; ThemeModeDialog Cancel uses primary.
- Ruling: accept reviewer rulings A and B; the Task 9 implementer may add exactly these six tokens to :core:designsystem Dimens.kt (KDoc'd) in this fix round. Why: one agent, no parallel edits; token adds are additive. Cost if wrong: token rename later.
- Ruling: Minors 1-3 join fix round 1 (ThemeModeDialog Cancel accentText is a binding colour rule). settingsTopGap 8 dp deferred to gate (look on device).
- Task 9: minor (deferred): single-line height check has ~1.5 dp margin against the drop-heightIn mutant (font-metric dependent)
- Ruling M1: pill tap pops back to an existing Now Playing (popBackStack<NowPlaying>(inclusive=false)) else pushes it. Why: avoids unbounded NP→Album→NP stacks; matches "return to the player" expectation. Cost if wrong: one lambda; tell the user at the gate.
- Ruling M2: songId comes from the library row (librarySong?.id), so Edit tags disables like Go to album. Why: an enabled item that opens an error screen is a dead control.
- Ruling: L2 (drop unused material-icons-extended from player/equalizer/settings) and L4 (dialog shape large; SavePreset text field accentText focus colours) join the one final fix dispatch.
- Ruling 6 jank investigation (systematic-debugging, Phase 1 evidence):
- Ruling 6 / Back transition defect D3-2 (user recording Screen_Recording_20261011_014300.mp4 in repo root, frames docs/design/calibration/phase3/back-user-recording-frames.png):
