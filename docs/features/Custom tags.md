---
type: feature
status: active
date: 2026-11-09
source: "user, 2026-11-09: custom tags and subtags, assigned in the tag editor as a searchable checklist, saved into the file's metadata, used to search and filter"
tags: [tag-editor, search, feature, custom-tags]
related: ["[[Features]]", "[[Custom tag system is cut]]", "[[Tag editor]]", "[[Search]]", "[[Tag writes go through a cache copy]]", "[[Backlog]]"]
---

# Custom tags

**Status:** phase 1 (foundation) committed (`dce76dd`); phase 2 (assigning: picker, both editors) complete and
device-walked by the user, waiting for the user's commit (2026-11-11). Rulings: [[2026-11-09-custom-tags-phase1-foundation rulings]]. Phases 2–4 not started
([[2026-11-09-custom-tags-roadmap]]); phase 2 waits for the user's M4A decision.

## What the user asked for (2026-11-09)

- Create custom tags and subtags, grouped (tag groups), and save them.
- Assign them to a track in the tag editor: a list with a search bar at the top, as a checklist.
- Store the assigned tags **in the audio file's metadata** (the user said MP3), so another system can read them with the
  same mechanism.
- Search and filter tracks by tag in a track search screen.
- Reverses [[Custom tag system is cut]] (cut at v1.0).

## User's answers (2026-11-09)

- **Example:** a tag "Techno"; subtags "Hard", "Industrial".
- **Subtags are free:** not children of a tag; a track can carry a subtag without any tag.
- **Assigning:** pick a track in the library, open the tag editor, check tags and subtags.
- **Finding:** a "Search" button on the Tracks screen opens a search window with fields for name, artist, album, genre
  and year, plus checkbox lists of every tag and every subtag (multiple choice).
- **Playing a result:** tapping a result plays it with the **whole result list as the queue**.
- **Multiple choice is AND:** assigning adds every checked item; searching with one tag and two subtags finds only
  tracks that carry all three.
- **Storage format (user's request):** the IDs of the tags and subtags, written into a large text field of the file
  (the user suggested something like "Description") as JSON: `{ "tags": "2,3", "subTags": "1,3,4" }`.
- **Deleting** a tag or subtag from the collection removes its ID from every track that carries it.

- **Decided (2026-11-09):** files hold IDs only, in a dedicated `ROLLA_TAGS` field (not the comment). The tag and
  subtag lists (the "enums", ID → name) travel as their own JSON file, exported and imported, so any other system can
  read them.

- **Import replaces** the phone's whole tag and subtag list (2026-11-09). When an imported ID names something different
  and tracks carry it, the confirmation lists each clash ("2: Techno → Jazz, on 14 tracks") with Import or Cancel.
- **Proposed by Claude, not yet objected to:** `ROLLA_TAGS` on FLAC and M4A too; export/import through a file the
  user picks; IDs never reused after a delete; the library scan reads `ROLLA_TAGS` from new or changed files (so tags
  come back after a reinstall or on another phone once the list is imported).

- **Editor layout (picked from mockups, 2026-11-09):** the editor shows two rows, "Tags" and "Subtags", each with the
  chosen ones as chips (mockup A). Tapping either opens one sheet with a Tags | Subtags tab switch, one search box, a
  checklist and "+ New tag" (mockup B's picker), opening on the tab that was tapped (Claude's reading).

- **Track search layout (picked from mockups, 2026-11-09): option B.** The Tracks tab's header gets a Search button
  that opens one "Find tracks" screen. The active filters fold into a strip of removable chips (✕ drops one) above a live
  results list; "Edit filters" unfolds the fields (title, artist, album, genre, year, Tags, Subtags). Shuffle and Play
  cover the whole result, and tapping a track queues the whole result.

- **Batch editing adds only (2026-11-09):** ticking a tag in the multi-track editor adds it to every selected track.
- **Claude's calls the user did not object to (2026-11-09):** text filters match part of the text ignoring case, year
  exactly; results in library order; Home's quick search unchanged; a "Manage tags" screen (Home ⋮ and a link in the
  picker) with rename (no file writes), delete (confirm with the track count, then consent and file updates), and
  Export/Import in its ⋮ (import warns that it replaces).

- **Tags shown outside the editor (2026-11-09):** under the title on Now Playing, and as small chips in the track
  lists.
- **Formats:** MP3, FLAC and M4A only; for other formats the Tags and Subtags rows are disabled with a note.

## Design concerns raised with the user (2026-11-09)

- **IDs need their names to travel too.** A file holding `"tags": "2,3"` means nothing on another device unless the
  ID-to-name list travels with it, and another install may number its own tags differently.
- **Comment field vs a dedicated field.** ID3 has no "Description" field; the large standard one is the comment (COMM),
  which other apps and DJ tools also write. Proposed: a dedicated custom field (`TXXX` in MP3, a Vorbis comment in
  FLAC, a freeform atom in M4A), each unlimited in length and readable in Mp3tag or foobar2000.
- **Deleting rewrites files:** every affected file needs Android's write consent (one batched system prompt on
  Android 11+).

## Facts found before the design (2026-11-09)

- Only `:feature:tageditor` opens audio files (jaudiotagger: `TagReader`, `TagWriter`, `TagAccess`). The library scan
  in `:core:data` reads MediaStore only, and MediaStore does not index custom fields. Tags read back from files (for
  example, files tagged on another device) therefore need a new file read during or after the scan.
- Writes go through the cache-copy path and need write consent per file ([[Tag writes go through a cache copy]]).
- Tag editing covers MP3, FLAC and M4A only.

## Open questions

- **M4A writing — decided by the user 2026-11-10: MP3 and FLAC only.** The app's jaudiotagger fork cannot write the
  `ROLLA_TAGS` freeform atom ([[jaudiotagger cannot write unknown M4A freeform atoms]]). M4A tags stay readable; in the
  editors, M4A tracks get the disabled Tags/Subtags rows with a note, like WAV and OGG. M4A writing can come later.
- **The existing M4A save bug** (a tag-editor save drops other apps' freeform atoms): the user chose to leave it in the
  [[Backlog]] for now (2026-11-10).

## Progress

| Date | Step |
|---|---|
| 2026-11-09 | Asked; vault read; brainstorming started |
| 2026-11-09 | Editor and Find tracks layouts picked from browser mockups (`.superpowers/brainstorm/`, git-ignored) |
| 2026-11-09 | Spec drafted: [[2026-11-09-custom-tags-design]]; builder check done (genre, M4A field and counter settled as Claude's calls; import clash asked) |
| 2026-11-09 | Import clash: warn with each clash and its track count, then Import or Cancel (user). Spec approved. Decision [[Custom tags live in the file's ROLLA_TAGS field]] |
| 2026-11-09 | Roadmap [[2026-11-09-custom-tags-roadmap]] (4 phases) and phase 1 plan [[2026-11-09-custom-tags-phase1-foundation]] written; waiting for the user's review |
| 2026-11-09 | User approved the plan, subagent-driven. Task 1 (`:core:tagio` module, helpers and fixtures moved) and Task 2 (model names, `ROLLA_TAGS` codec) done and reviewed |
| 2026-11-09 | Task 3: MP3 and FLAC read/write work; M4A write blocked by the jaudiotagger fork; M4A kept read-only (Ruling 5) |
| 2026-11-10 | Task 4: Room v6 (lists, per-kind counters, song links, `genre`, `file_tags_read`); instrumented tests compile, device run owed (Motorola not connected) |
| 2026-11-10 | Task 5: the scan's `FileTagPass` reads tags and genre from files in the background (never blocks the caller, never crashes; rulings 7–8) |
| 2026-11-10 | Task 6: `CustomTagRepository` (create, rename, observe lists and per-song IDs) |
| 2026-11-10 | Full JVM gate green (`spotlessCheck detekt lint testDebugUnitTest assembleRelease checkReleaseManifestNoInternet`). Final review: ready with fixes — ID ceiling (`MAX_CUSTOM_TAG_ID` = 999 999 999, Ruling 10), jaudiotagger logging, vault catch-up; first-pass write cost to be measured on the device (Ruling 11) |
| 2026-11-10 | Final fix wave done (ID ceiling, jaudiotagger logging, review fold-ins; all unit tests green). Paused at the user's request, then resumed |
| 2026-11-10 | Fix wave re-reviewed: all code findings addressed; vault residuals fixed; R8 logger keep rule and the last-ID race recorded below. Run ledger: `.superpowers/sdd/2026-11-09-custom-tags-phase1-foundation/progress.md` (git-ignored, local) |
| 2026-11-10 | **Device (Motorola edge 70, Android 16):** `:core:database:connectedDebugAndroidTest` 117/117 (incl. the 5→6 migration and every custom tag DAO test). Benchmark build installed over the user's app: the v6 upgrade ran on the real library; first pass `read 91 files (0 failed) in 3003 ms` (≈33 ms per file; library 97 MP3 + 1 WAV); no crash, no jaudiotagger log noise, CPU idle afterwards; the user found Home and scrolling smooth. Batching the pass's writes stays a backlog item for large libraries |
| 2026-11-10 | Every planned mutation of the device tests run on the Motorola and caught (one equivalent under `@Transaction`, covered by its combined form); final run 117/117. Phase 1 complete; ready for the user's commit |

| 2026-11-10 | Committed `dce76dd`. User decided M4A: MP3 and FLAC only; M4A save bug stays in the backlog. Phase 2 plan written: [[2026-11-10-custom-tags-phase2-assigning]] (generation guard for the race, picker sheet, both editors); waiting for the user's review |

| 2026-11-10 | Phase 2 Tasks 1–5 built and reviewed: v7 generation guard (a stale background read never overwrites a save; device 121/121), picker state, picker sheet + rows + name dialog (kit `LabelChip`; chips wrap at large font), single editor (writes only when ticks change; unreadable field disables the rows), batch editor (adds only; unwritable formats fail before any write) |

| 2026-11-11 | Phase 2 final review: ready with fixes; fix wave (shared `CustomTagEditing.kt` helper for both editors, comment fixes, no `ROLLA_TAGS` read for OGG/WAV) re-reviewed clean. JVM gate green |
| 2026-11-11 | Device: Room 121/121 on the Motorola; benchmark build installed (v6 → v7 on the real library). The user's walk passed every step: create and tick "Techno" and "Hard", save, reopen (ticks kept), title-only save keeps the tags, batch add "Edited N of N tracks.", M4A/WAV rows disabled with the note, largest font (chips wrap, picker readable, Done above the keyboard). No crash. Phase 2 complete; rulings [[2026-11-10-custom-tags-phase2-assigning rulings]]; ready for the user's commit |
| 2026-11-11 | LLM wiki ingest of the spec, roadmap and phase 1–2 plans (`docs/wiki/`: concept `custom-tags`, entity `module-core-tagio`, four source pages; module pages updated). Phase 2 committed at the user's request |

## Known limits and notes for later phases

- **M4A writes:** decided 2026-11-10, MP3 and FLAC only.
- **Apply-after-reset race:** fixed in phase 2 Task 1 (Room v7 `file_tags_generation`; a stale read is dropped and
  read again).
- **Save-time merge:** done in phase 2 — both editors merge unknown IDs from the editable copy at save time, never
  from Room. The single editor writes only when the ticks changed; the batch editor only adds.
- **The re-read after a save is asynchronous** (phase 1 Ruling 7): screens observe a song's links; they never assume
  the links are fresh when `syncSongs` returns.
- **Phase 3 hand-off (final review of phase 2):**
  - The picker needs a "Manage tags" link: an optional `onManageClick` on `CustomTagPickerSheet`.
  - Reaching Manage tags from an open editor makes its editable copy and "unknown ID" baseline stale: a delete's file
    clean-up of this track could be undone, and an import could make a kept-unknown ID drop. Fix then: write the user's
    diff `(inFile − (loaded − selected)) ∪ selected`, refresh the copy after Manage, prune ticks to known IDs when the
    lists change (both editors).
  - A new tag can take an ID that a not-yet-read file carries during the first pass: add a `reserveIds` that raises
    the counters (editor load, import).
- **Phase 4 hand-off:** `LabelChip` never ellipsizes by design; list rows need one line plus "+N" and a distinct subtag
  chip, so plan a new kit chip variant.
- **Picker rows** ellipsize long names (one line); check at 1.5× on the device.
- **First-pass cost:** one Room transaction per file, so every `songs` observer re-queries per file during the one-time
  first pass; batch the writes if the device probe shows a cost ([[Backlog]]).
- **FLAC artwork kept on a `ROLLA_TAGS` write** is not yet tested: add it before phase 2 ships writes.
- **Last-ID race (phase 3, when the UI calls `create`):** `create` checks the ceiling, then `insertNew` runs in its own
  transaction, so a concurrent create or a `FileTagPass` raising the counter in between can make `insertNew` throw
  `IllegalStateException` out of `create`. Needs the counter at 999 999 998 (only a hand-edited file). Fix then:
  `insertNew` reports "full" inside its transaction instead of throwing.
- **Release logging:** R8 may remove the write-only static logger field in `RollaTagsFileIo`, leaving the
  `org.jaudiotagger` logger held weakly, so its WARNING level could be lost and per-file INFO lines return to logcat.
  Fix: a `-keepclassmembers` rule (in `:core:tagio`'s consumer rules) ([[Backlog]]).
