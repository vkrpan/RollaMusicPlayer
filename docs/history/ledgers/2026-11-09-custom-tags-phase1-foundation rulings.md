---
type: ledger
status: history
date: 2026-11-09
plan: "docs/superpowers/plans/2026-11-09-custom-tags-phase1-foundation.md"
rulings: 13
deferred_minors: 6
tags: [ledger, rulings, custom-tags]
related: ["[[Custom tags]]", "[[Timeline]]", "[[Ledgers]]", "[[2026-11-09-custom-tags-phase1-foundation]]"]
---

# Ledger rulings — Custom tags phase 1 (foundation)

Every `Ruling:`, `minor (deferred)` and `parked` line from the execution ledger
`.superpowers/sdd/2026-11-09-custom-tags-phase1-foundation/progress.md` (git-ignored scratch, archived here verbatim on
2026-11-10 so it survives the workspace's deletion). Plan: [[2026-11-09-custom-tags-phase1-foundation]]; spec:
[[2026-11-09-custom-tags-design]]. Final review: ready with fixes; one fix wave, re-reviewed; device evidence complete
(117/117 on the Motorola, every planned mutation run, first pass `read 91 files (0 failed) in 3003 ms`).

## Pre-flight

- Ruling 1: T3 also produces `fun isCustomTagExtension(extension: String): Boolean` (mp3/flac/m4a, case-insensitive) in
  `:core:tagio`, and `File.supportsCustomTags()` delegates to it; T5's `SongFileSource` gains
  `suspend fun extensionOf(song: SongEntity): String?` (DISPLAY_NAME, else the DATA path), and `FileTagPass` checks it
  before `withReadableFile`. — the plan's own test requires deciding without opening the file; spec §6 call 1 limits
  formats by type. — Cost if wrong: one extra MediaStore query per unread row on the first pass.

## During execution

- Task 1: minor (deferred): M4 `Tag` import in TagWriter.kt kept for KDoc only (correct); M5 hilt plugin on tagio with
  no Hilt code yet (plan-mandated, Task 3 adds @Inject).
- Ruling 2: Task 1 Minors M1 (KDoc links in TagAccess.kt/AudioFixtures.kt to :feature:tageditor symbols → plain code
  spans), M2 (settings.gradle.kts JitPack comment → "used by :core:tagio and :feature:tageditor"), M3 (fixture
  @Suppress comments → present reason) are folded into Task 2's dispatch as a small cleanup — same module, Task 2's
  implementer is there anyway; [[Keep docs honest]]. — Cost if wrong: a few extra lines in Task 2's diff.
- Ruling 3: keep the extra test `decode_malformedJson_returnsNull`; `decode_notJson_returnsNull` uses the mutation
  "non-object returns EMPTY" — kotlinx reads bare `Techno` as a JSON primitive (no throw), so the plan's mutation
  "EMPTY on parse error" was not reachable from that input. — Cost if wrong: one redundant test.
- Task 2: minor (deferred): M3 ragged re-wrap AudioFixtures.kt:6-7; M4 vocabulary block lists isEmpty/EMPTY in a comment.
- Ruling 4: Task 2 Minors M1 (encode KDoc: callers delete the field for isEmpty instead of writing `{"tags":"","subTags":""}`)
  and M2 (pin the digits-only filter: add `"+5"` and an over-Long entry to decode_badEntries' input) fold into Task 3,
  which consumes the codec in the same module. — Cost if wrong: two small extra hunks in Task 3.
- Ruling 5: phase 1 keeps M4A **read** support (scan) and defers M4A **write** to a decision before phase 2.
  `:core:tagio` exposes `isCustomTagExtension` (readable: mp3/flac/m4a) and `canWriteCustomTags(extension)` (mp3/flac);
  `writeCustomTagIds` on an M4A throws `UnsupportedOperationException` with a clear message, tested. The M4A write tests
  become a read test on a hand-built M4A carrying the atom plus a write-refused test; a characterization test pins the
  suspected existing bug (`m4a_unknownFreeformAtom_blocksCommit`). — Why: phase 1 writes nothing in the app (writes
  start in phase 2), the spec's field format stays unchanged, and the options (own MP4 writer, fork upgrade, drop M4A)
  are the user's call since M4A was Claude's spec call 1. — Cost if wrong: if the user picks "drop M4A", the read path
  is a few unused lines; if "own writer", phase 2 adds it.
- Task 3: DONE_WITH_CONCERNS after Ruling 5 (tagio 28/28, tageditor 162/162, detekt+spotless green; mutations quoted,
  genre mutation substituted as equivalent). HEAD a8edd86cca2eea5f5f9dd4301f718f0c90ac69a7. Findings: the fork's M4A
  reader ignores unknown freeform atoms too, so M4A ROLLA_TAGS is read via the fork-bundled jcodec parser; EXISTING BUG
  pinned by `m4a_unknownFreeformAtom_isDroppedByCommit`: any jaudiotagger commit on an M4A silently drops unlisted
  freeform atoms (tag editor saves today erase other apps' custom atoms). Reviewer dispatched (opus).
- Task 3: review: Spec ✅ (with Rulings 1/4/5), Approved, 0 Critical/Important, 5 Minor. ⚠️1 plan row for
  read_genre amended (equivalent mutation). ⚠️2 vault knowledge note was already corrected after the reviewer's
  snapshot (reader limit + silent-drop wording); backlog bug item added. ⚠️3 test totals from report: accepted.
- Task 3: minor (deferred): M1 refusal message names Mp4TagCreator for any unwritable extension (RollaTagsFileIo.kt:80-83);
  M2 KDoc lacks @throws for unreadable/corrupt files (:56-61, :71-77); M3 untested: M4A without metadata read, FLAC
  read-never-writes, FLAC artwork kept on write; M4 jcodec coupling is to fork-added `MetaBox.getRdnsMeta`, M4A moov
  parsed 3× per read; M5 read KDoc gives wrong reason for editableTag().
- Ruling 6: the Motorola is not connected (adb devices empty, 2026-11-10). Task 4 proves RED/GREEN for its
  instrumented tests by compilation (`:core:database:compileDebugAndroidTestKotlin`) plus Room's schema export; the
  device runs of `:core:database:connectedDebugAndroidTest` move to Task 7 (and re-open Task 4's fix loop if they fail).
  Mutation evidence for the DAO/migration tests is deferred to that run. — Why: the plan allows compile evidence when
  the device is absent; Tasks 5–6 use FakeCustomTagDao, not Room. — Cost if wrong: a Room/SQL bug surfaces only at
  Task 7, then costs one fix round there.
- Task 4: DONE_WITH_CONCERNS (compile RED/GREEN; assembleDebug + 6.json; core:data 147/147; device + 11 mutations
  deferred to Task 7 per Ruling 6 — back up 6.json with the source for the unique-index mutation). Notes for Task 5/6:
  fake throws SQLiteConstraintException (needs Robolectric) on duplicate key or link to an unseeded song; call
  seedSongs first. @Suppress TooManyFunctions on DatabaseModule. Counter via INSERT OR REPLACE … MAX (no upsert on
  API 24). HEAD 687dba35ee98199089c925782db730f4b21de5cc. Reviewer dispatched (opus).
- Task 4: review: Spec ✅, Approved, 0 Critical/Important, 4 Minor; migration SQL = 6.json = entities checked column by
  column; generated impl confirms @Transaction defaults. ⚠️ device RED/GREEN + 11 mutations: deferred to Task 7 (Ruling 6).
- Task 4: minor (deferred): M1 CustomTagDaoTest.kt comment claims a rollback the assertion doesn't prove; M2 Migration5To6
  `database` vs supertype `db` parameter-name warning (same as earlier migrations); M3 songsNeedingFileTagRead has no
  index on (file_tags_read, date_modified) — revisit if Task 7 timing is slow; M4 FakeCustomTagDao has its own songs
  store (seedSongs), separate from FakeSongDao.
- Ruling 7: plan defect (the plan said the pass "never blocks the tag editor's post-save syncSongs upsert", but
  calling `run()` inline makes the caller wait for it; spec §9 "a cold start must not wait for it", anti-goal "scan
  noticeably slower"). Fix before review: `FileTagPass` gets a non-suspending `fun request()` that launches `run()` on
  an injected long-lived scope (`@FileTagPassScope CoroutineScope`, provided in DataModule as
  `CoroutineScope(SupervisorJob() + Dispatchers.IO)`); a request while a run is active coalesces into exactly one more
  run afterwards; an unexpected exception outside the per-row catch is logged, never crashes. `LibraryIndexer` calls
  `request()` after its upsert (both paths; not for an empty `syncSongs`). Plus concern 2: two API-30 genre tests in
  MediaScannerReadTest. — Cost if wrong: the post-save re-read of an edited file lands a moment after the editor
  closes instead of before; phase 2 must observe the song's links (Flow) rather than assume they are fresh on return.
- Task 5: review: Spec ✅ (with Rulings 1, 7), Approved, 0 Critical/Important, 5 Minor. ⚠️ AndroidSongFileSource on a
  device → Task 7 probe.
- Ruling 8: re-grade Minor 1 (a non-Exception Throwable from jaudiotagger on a corrupt file escapes both catches and
  kills the process; the row stays unread, so every later sync crashes again) to Important — it breaches Ruling 7's
  "never crashes" and would be a crash loop on the user's phone. Fix round 1: `readRow` catches `Throwable` (cancellation
  rethrown) and marks the row read; the provided scope gets a logging `CoroutineExceptionHandler`. — Cost if wrong:
  catching an Error such as OOM on one file instead of failing fast.
- Task 5: minor (deferred): M2 apply-after-reset race can lose a re-read (note for phase 2: conditional apply); M3
  leftover copies in cacheDir/file_tag_reads not swept; M4 stray-cancellation branch untested; M5 two MediaStore
  queries per row and full copies on API 29 (accepted).
- Task 5: fix round 1 sent (Ruling 8).
- Ruling 9: `rename` validates the name (Blank/TooLong) before looking up the ID, so a missing ID with an invalid name
  returns Blank/TooLong, not NotFound — the caller (phase 3's rename dialog) shows the name error either way, and a
  missing ID with a valid name still returns NotFound. — Cost if wrong: one reordered check.
- Task 6: minor (deferred): M1 tie-break `compareBy { id }` vs playlists' `compareBy { name }.thenBy { id }` ("Emile"
  vs "Émile" collate equal at primary strength); M2 rename's validate-before-lookup order undocumented in KDoc (Ruling 9);
  M3 41-emoji test doesn't assert nothing inserted; rename has no Blank/TooLong test.
- Ruling 10: ID ceiling `MAX_CUSTOM_TAG_ID = 999_999_999`, shared by decode (drops above) and create (new result
  `LimitReached`; insertNew guards against wrap). Narrows spec §6 call 12 to "the highest valid ID"; phase 3 import uses
  the same ceiling. — Cost if wrong: a list cannot exceed ~1e9 entries/IDs.
- Ruling 11: I3 is measured, not redesigned now: the Task 7 probe adds a Home scroll during the first pass (CPU/jank
  noted with N, F, T); a backlog item "batch the pass's writes if the probe shows a cost". M-d: Step 3 install is
  gated on Step 2 (device `:core:database:connectedDebugAndroidTest` incl. migrate5To6) passing. — Cost if wrong: a
  slower first pass on a huge library until batched.
- Final: parked — R8 may drop the write-only jaudiotagger logger field (release log noise only) — Ruling 12: recorded
  in Backlog and Custom tags "Known limits"; no second fix wave. — Cost if wrong: per-file INFO lines in release logcat.
- Final: parked — last-ID race between the LimitReached check and insertNew (needs counter 999,999,998; nothing calls
  create in phase 1) — Ruling 13: recorded for phase 3 (insertNew reports "full" in its transaction). — Cost if wrong:
  an ISE from create in a practically unreachable case.
- Final review: complete (residuals parked with rulings). Remaining: Task 7 Steps 2–3 on the Motorola.
- Task 7 Step 3 result: user "Everything went fine and smooth" (opened, scrolled Home; added more tracks first). Library
  now 97 MP3 + 1 WAV. logcat -s FileTagPass since 13:29: one line "11-10 13:39:11.426 … I FileTagPass: read 91 files
  (0 failed) in 3003 ms" (≈33 ms/file). 0 jaudiotagger log lines; no FATAL/AndroidRuntime crash from the app; `top`
  afterwards: com.rolla.musicplayer 0.0 % CPU, 257 MB RES. I3 (per-file transactions) shows no visible cost at this
  size: Ruling 11's batching stays a backlog item for large libraries. Next: the deferred Task 4 device mutations.
- Phase 1: complete — final review residuals parked with rulings 12–13; ready for the user's commit.
